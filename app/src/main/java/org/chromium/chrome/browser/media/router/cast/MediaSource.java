// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.router.cast;

import android.support.v7.media.MediaRouteSelector;

import com.google.android.gms.cast.CastMediaControlIntent;

import org.chromium.base.VisibleForTesting;

import java.util.Locale;

import javax.annotation.Nullable;

/**
 * Abstracts parsing the Cast application id and other parameters from the source URN.
 */
public class MediaSource {
    private static final String CAST_SOURCE_URN_PREFIX = "https://google.com/cast#";
    private static final String CAST_SOURCE_URN_PARAMETER_SEPARATOR = "/";
    private static final String CAST_SOURCE_URN_APPLICATION_ID_PREFIX = "__castappid__=";

    private final String mSourceUrn;
    private String mApplicationId;

    /**
     * Initializes the media source from its source URN.
     * @param sourceUrn the source urn for the Cast media source
     * @return an initialized media source if the URN is valid or null.
     */
    @Nullable
    public static MediaSource from(String sourceUrn) {
        String applicationId = getCastApplicationId(sourceUrn);
        if (applicationId == null) return null;
        return new MediaSource(sourceUrn, applicationId);
    }

    /**
     * Returns a new {@link MediaRouteSelector} to use for Cast device filtering for this
     * particular media source.
     * @return an initialized route selector.
     */
    public MediaRouteSelector buildRouteSelector() {
        return new MediaRouteSelector.Builder()
                .addControlCategory(CastMediaControlIntent.categoryForCast(mApplicationId))
                .build();
    }

    /**
     * @return the Cast application id corresponding to the source.
     */
    public String getApplicationId() {
        return mApplicationId;
    }

    @VisibleForTesting
    public MediaSource(String sourceUrn, String applicationId) {
        mSourceUrn = sourceUrn;
        mApplicationId = applicationId;
    }

    /**
     * @param sourceUrn the URN identifying the media source.
     * @return the corresponding Cast application id or null.
     */
    @Nullable
    private static String getCastApplicationId(String sourceUrn) {
        String canonicalSourceUrn = null;
        if (sourceUrn != null) canonicalSourceUrn = sourceUrn.trim().toLowerCase(Locale.US);
        if (canonicalSourceUrn == null
                || !canonicalSourceUrn.startsWith(CAST_SOURCE_URN_PREFIX)) {
            return null;
        }
        String[] parameters = canonicalSourceUrn
                .substring(CAST_SOURCE_URN_PREFIX.length())
                .split(CAST_SOURCE_URN_PARAMETER_SEPARATOR);
        for (String parameter : parameters) {
            if (parameter.startsWith(CAST_SOURCE_URN_APPLICATION_ID_PREFIX)) {
                String applicationId = parameter.substring(
                        CAST_SOURCE_URN_APPLICATION_ID_PREFIX.length()).toUpperCase(Locale.US);
                if (!applicationId.isEmpty()) return applicationId;
            }
        }
        return null;
    }

    /**
     * @return the URN identifying the media source
     */
    public String getUrn() {
        return mSourceUrn;
    }
}

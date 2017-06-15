// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.instantapps;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;

import org.chromium.content_public.browser.WebContents;

/**
 * Encapsulates information needed to display an {@link InstantAppsInfoBar}.
 */
public class InstantAppsBannerData {
    private final String mAppName;
    private final Bitmap mAppIcon;
    private final String mUrl;
    private final Intent mIntent;
    private final WebContents mWebContents;
    private final Uri mReferrer;
    private final String mPrimaryActionLabel;

    public InstantAppsBannerData(String appName, Bitmap icon, String url, Uri referrer,
            Intent intent, String primaryActionLabel, WebContents webContents) {
        mAppName = appName;
        mAppIcon = icon;
        mUrl = url;
        mIntent = intent;
        mPrimaryActionLabel = primaryActionLabel;
        mWebContents = webContents;
        mReferrer = referrer;
    }

    /** @return The name of the Instant App. */
    public String getAppName() {
        return mAppName;
    }

    /** @return The badged Instant App icon. */
    public Bitmap getIcon() {
        return mAppIcon;
    }

    /** @return The host name for the URL. */
    public String getUrl() {
        return mUrl;
    }

    /** @return The intent to launch on "Open App" button click. */
    public Intent getIntent() {
        return mIntent;
    }

    /** @return The current web contents. */
    public WebContents getWebContents() {
        return mWebContents;
    }

    /** @return The referrer page for the Instant App. */
    public Uri getReferrer() {
        return mReferrer;
    }

    /** @return The label for the button that will launch the Instant App. */
    public String getPrimaryActionLabel() {
        return mPrimaryActionLabel;
    }
}

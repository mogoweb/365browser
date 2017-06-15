// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.feature_engagement_tracker;

import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.components.feature_engagement_tracker.FeatureEngagementTracker;

/**
 * This factory creates FeatureEngagementTracker for the given {@link Profile}.
 */
public final class FeatureEngagementTrackerFactory {
    // Don't instantiate me.
    private FeatureEngagementTrackerFactory() {}

    /**
     * A factory method to build a {@link FeatureEngagementTracker} object. Each Profile only ever
     * has a single {@link FeatureEngagementTracker}, so the first this method is called (or from
     * native), the {@link FeatureEngagementTracker} will be created, and later calls will return
     * the already created instance.
     * @return The {@link FeatureEngagementTracker} for the given profile object.
     */
    public static FeatureEngagementTracker getFeatureEngagementTrackerForProfile(Profile profile) {
        return nativeGetFeatureEngagementTrackerForProfile(profile);
    }

    private static native FeatureEngagementTracker nativeGetFeatureEngagementTrackerForProfile(
            Profile profile);
}

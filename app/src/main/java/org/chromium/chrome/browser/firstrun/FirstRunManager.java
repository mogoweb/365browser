// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.firstrun;

import android.content.Context;
import android.content.pm.ApplicationInfo;

import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.signin.SigninManager;
import org.chromium.chrome.browser.util.FeatureUtilities;

/**
 * Manages the First Run Experience.
 */
public class FirstRunManager {
    @VisibleForTesting
    public static final String LAUNCH_CHROME_WITH_FRE_EXTRA = "Launch Chrome with FRE";

    FirstRunManager() {
    }

    /**
     * A version of checkAnyUserHasSeenToS() above that could be overridden in tests.
     * @param context Context for the app.
     * @return Whether or not the the ToS has been seen.
     */
    @VisibleForTesting
    boolean testableCheckAnyUserHasSeenToS(Context context) {
        return ToSAckedReceiver.checkAnyUserHasSeenToS(context);
    }

    /**
     * Checks if sync can be turned on.
     * @param context Context for the app.
     * @return Whether sync is available.
     */
    @VisibleForTesting
    boolean checkIsSyncAllowed(Context context) {
        return FeatureUtilities.canAllowSync(context)
                && !SigninManager.get(context).isSigninDisabledByPolicy();
    }

    /**
     * Check whether Chrome has been installed as part of the system image.
     * @param context Context for the app.
     * @return Whether Chrome is in the system image.
     */
    @VisibleForTesting
    boolean checkIsSystemInstall(Context context) {
        return ((context.getApplicationInfo().flags & ApplicationInfo.FLAG_SYSTEM) != 0);
    }
}

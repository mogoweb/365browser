// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.customtabs;

import org.chromium.base.ThreadUtils;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.chrome.browser.profiles.Profile;

/**
 * Interface to the loading predictor.
 *
 * Allows chrome to hint at a likely future navigation.
 */
@JNINamespace("predictors")
class LoadingPredictor {
    private static boolean sInitializationStarted;

    private final Profile mProfile;

    /**
     * @param profile The profile used to get the loading predictor.
     */
    public LoadingPredictor(Profile profile) {
        mProfile = profile;
    }

    /**
     * Starts the asynchronous initialization of the loading predictor.
     */
    public boolean startInitialization() {
        ThreadUtils.assertOnUiThread();
        sInitializationStarted = true;
        return nativeStartInitialization(mProfile);
    }

    /**
     * Hints at a future navigation to a URL.
     *
     * @param url The URL to prepare.
     * @return false in case the LoadingPredictor is not usable.
     */
    public boolean prepareForPageLoad(String url) {
        ThreadUtils.assertOnUiThread();
        if (!sInitializationStarted) {
            throw new RuntimeException("startInitialization() not called.");
        }
        return nativePrepareForPageLoad(mProfile, url);
    }

    /**
     * Indicates that a page load hint is no longer active.
     *
     * @param url The hinted URL.
     * @return false in case the LoadingPredictor is not usable.
     */
    public boolean cancelPageLoadHint(String url) {
        ThreadUtils.assertOnUiThread();
        return nativeCancelPageLoadHint(mProfile, url);
    }

    private static native boolean nativeStartInitialization(Profile profile);
    private static native boolean nativePrepareForPageLoad(Profile profile, String url);
    private static native boolean nativeCancelPageLoadHint(Profile profile, String url);
}

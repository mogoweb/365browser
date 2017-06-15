// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.webapps;

import android.os.StrictMode;

import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.library_loader.LibraryLoader;
import org.chromium.chrome.browser.AppHooks;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.GooglePlayInstallState;
import org.chromium.chrome.browser.externalauth.ExternalAuthUtils;
import org.chromium.chrome.browser.externalauth.UserRecoverableErrorHandler;
import org.chromium.chrome.browser.preferences.ChromePreferenceManager;
import org.chromium.webapk.lib.client.WebApkValidator;

/**
 * Contains functionality needed for Chrome to host WebAPKs.
 */
public class ChromeWebApkHost {
    private static final String TAG = "ChromeWebApkHost";

    /** Whether installing WebAPks from Google Play is possible. */
    private static Integer sGooglePlayInstallState;

    private static Boolean sEnabledForTesting;

    public static void init() {
        WebApkValidator.init(
                ChromeWebApkHostSignature.EXPECTED_SIGNATURE, ChromeWebApkHostSignature.PUBLIC_KEY);
    }

    public static void initForTesting(boolean enabled) {
        sEnabledForTesting = enabled;
        sGooglePlayInstallState = enabled ? GooglePlayInstallState.SUPPORTED
                                          : GooglePlayInstallState.NO_PLAY_SERVICES;
    }

    public static boolean isEnabled() {
        if (sEnabledForTesting != null) return sEnabledForTesting;

        return isEnabledInPrefs();
    }

    /** Computes the GooglePlayInstallState. */
    private static int computeGooglePlayInstallState() {
        if (!ExternalAuthUtils.getInstance().canUseGooglePlayServices(
                    ContextUtils.getApplicationContext(),
                    new UserRecoverableErrorHandler.Silent())) {
            return GooglePlayInstallState.NO_PLAY_SERVICES;
        }

        GooglePlayWebApkInstallDelegate delegate =
                AppHooks.get().getGooglePlayWebApkInstallDelegate();
        if (delegate == null) {
            return GooglePlayInstallState.DISABLED_OTHER;
        }

        return GooglePlayInstallState.SUPPORTED;
    }

    /** Returns whether installing WebAPKs is possible. */
    @CalledByNative
    private static boolean canInstallWebApk() {
        return isEnabled() && getGooglePlayInstallState() == GooglePlayInstallState.SUPPORTED;
    }

    @CalledByNative
    private static int getGooglePlayInstallState() {
        if (sGooglePlayInstallState == null) {
            sGooglePlayInstallState = computeGooglePlayInstallState();
        }
        return sGooglePlayInstallState;
    }

    /* Returns whether launching renderer in WebAPK process is enabled by Chrome. */
    public static boolean canLaunchRendererInWebApkProcess() {
        return isEnabled() && LibraryLoader.isInitialized()
                && nativeCanLaunchRendererInWebApkProcess();
    }

    /**
     * Check the cached value to figure out if the feature is enabled. We have to use the cached
     * value because native library may not yet been loaded.
     * @return Whether the feature is enabled.
     */
    private static boolean isEnabledInPrefs() {
        // Will go away once the feature is enabled for everyone by default.
        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskReads();
        try {
            return ChromePreferenceManager.getInstance().getCachedWebApkRuntimeEnabled();
        } finally {
            StrictMode.setThreadPolicy(oldPolicy);
        }
    }

    /**
     * Once native is loaded we can consult the command-line (set via about:flags) and also finch
     * state to see if we should enable WebAPKs.
     */
    public static void cacheEnabledStateForNextLaunch() {
        ChromePreferenceManager preferenceManager = ChromePreferenceManager.getInstance();

        boolean wasEnabled = isEnabledInPrefs();
        boolean isEnabled = ChromeFeatureList.isEnabled(ChromeFeatureList.IMPROVED_A2HS);
        if (isEnabled != wasEnabled) {
            Log.d(TAG, "WebApk setting changed (%s => %s)", wasEnabled, isEnabled);
            preferenceManager.setCachedWebApkRuntimeEnabled(isEnabled);
        }
    }

    private static native boolean nativeCanLaunchRendererInWebApkProcess();
}

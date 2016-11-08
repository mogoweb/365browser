// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.webapps;

import android.os.StrictMode;

import org.chromium.base.CommandLine;
import org.chromium.base.ContextUtils;
import org.chromium.base.FieldTrialList;
import org.chromium.base.Log;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.browser.ChromeSwitches;
import org.chromium.chrome.browser.preferences.ChromePreferenceManager;
import org.chromium.webapk.lib.client.WebApkValidator;

/**
 * Contains functionality needed for Chrome to host WebAPKs.
 */
public class ChromeWebApkHost {
    // The public key to verify whether a WebAPK is signed by WebAPK Minting
    // Server.
    // TODO(hanxi): Update {@link EXPECTED_SIGNATURE} when the real signature is
    // available.
    // TODO(yfriedman): Move this to resoures (so it can vary by channel)
    private static final byte[] EXPECTED_SIGNATURE = new byte[] {
            48, -126, 3, -121, 48, -126, 2, 111, -96, 3, 2, 1, 2, 2, 4, 20, -104, -66, -126, 48, 13,
            6, 9, 42, -122, 72, -122, -9, 13, 1, 1, 11, 5, 0, 48, 116, 49, 11, 48, 9, 6, 3, 85, 4,
            6, 19, 2, 67, 65, 49, 16, 48, 14, 6, 3, 85, 4, 8, 19, 7, 79, 110, 116, 97, 114, 105,
            111, 49, 17, 48, 15, 6, 3, 85, 4, 7, 19, 8, 87, 97, 116, 101, 114, 108, 111, 111, 49,
            17, 48, 15, 6, 3, 85, 4, 10, 19, 8, 67, 104, 114, 111, 109, 105, 117, 109, 49, 17, 48,
            15, 6, 3, 85, 4, 11, 19, 8, 67, 104, 114, 111, 109, 105, 117, 109, 49, 26, 48, 24, 6, 3,
            85, 4, 3, 19, 17, 67, 104, 114, 111, 109, 105, 117, 109, 32, 67, 104, 114, 111, 109,
            105, 117, 109, 48, 30, 23, 13, 49, 53, 49, 48, 49, 54, 49, 53, 49, 54, 52, 52, 90, 23,
            13, 52, 51, 48, 51, 48, 51, 49, 53, 49, 54, 52, 52, 90, 48, 116, 49, 11, 48, 9, 6, 3,
            85, 4, 6, 19, 2, 67, 65, 49, 16, 48, 14, 6, 3, 85, 4, 8, 19, 7, 79, 110, 116, 97, 114,
            105, 111, 49, 17, 48, 15, 6, 3, 85, 4, 7, 19, 8, 87, 97, 116, 101, 114, 108, 111, 111,
            49, 17, 48, 15, 6, 3, 85, 4, 10, 19, 8, 67, 104, 114, 111, 109, 105, 117, 109, 49, 17,
            48, 15, 6, 3, 85, 4, 11, 19, 8, 67, 104, 114, 111, 109, 105, 117, 109, 49, 26, 48, 24,
            6, 3, 85, 4, 3, 19, 17, 67, 104, 114, 111, 109, 105, 117, 109, 32, 67, 104, 114, 111,
            109, 105, 117, 109, 48, -126, 1, 34, 48, 13, 6, 9, 42, -122, 72, -122, -9, 13, 1, 1, 1,
            5, 0, 3, -126, 1, 15, 0, 48, -126, 1, 10, 2, -126, 1, 1, 0, -115, -118, -64, 73, -61,
            -41, -60, 63, -118, -20, -103, 21, -12, -36, -7, 5, 122, -21, 82, 115, -64, -47, 0, 92,
            50, -56, 122, -22, -28, -10, 9, 29, -43, -88, 23, 45, -84, 89, 47, 84, 54, -110, 35, 10,
            25, 30, 56, -105, 93, 57, -81, 27, 125, 93, 127, -91, 97, -56, 24, -107, 125, 30, 38,
            -2, 41, -49, 16, -60, 119, -125, -79, -6, 52, -107, 81, -21, 25, -125, 121, 37, -78, 37,
            90, 14, 11, 63, -73, 67, 103, -22, 112, 41, -126, 1, 33, -106, -92, -65, 64, 57, 94,
            -75, 106, 29, -15, -76, 25, 94, -87, 46, 35, -49, -51, 65, 30, -110, -51, 35, 7, -44,
            48, 25, -63, -101, -64, -114, -50, 114, -21, 112, 83, -97, -8, 23, -128, -10, 32, 109,
            58, 18, 10, 33, -74, 63, 104, 82, -115, -103, -100, -14, -59, 4, 41, 37, 39, -49, 12,
            -26, -37, -35, 61, 88, 81, -54, 82, -77, 50, 66, -9, 82, 37, -123, 34, 28, -114, -40,
            41, 88, 16, -54, 17, -17, 80, 39, 106, 60, 125, -17, -87, -29, 17, 10, -10, 89, -80, 38,
            -22, 125, 100, 92, -39, 82, -42, 29, -28, 13, -32, -16, -74, 94, -122, 1, -17, -92, 100,
            31, -60, 114, 46, 50, 25, 21, -102, -127, 107, -2, -99, 119, 45, 124, -127, -83, 47, 1,
            -37, 103, 88, -5, 84, 66, -5, -69, -16, 3, 54, -36, 17, -97, -3, 126, -118, 68, -24, 63,
            122, -67, 2, 3, 1, 0, 1, -93, 33, 48, 31, 48, 29, 6, 3, 85, 29, 14, 4, 22, 4, 20, -59,
            -107, -50, 44, 101, 0, 40, 110, 43, -89, -126, -109, -40, 22, 68, -50, 51, -110, 85, 84,
            48, 13, 6, 9, 42, -122, 72, -122, -9, 13, 1, 1, 11, 5, 0, 3, -126, 1, 1, 0, 117, -37,
            63, 71, -73, 27, -83, -39, -32, -107, 86, 17, -123, -95, -107, 24, -88, 93, 61, -94, -4,
            65, -61, -50, -85, 79, 110, 90, -91, -40, 72, -74, 117, -106, 64, 124, 3, -10, 74, 60,
            4, -78, -11, 86, -23, -108, 61, 35, 17, 69, -92, -78, 83, 76, 102, -59, 106, -42, 125,
            -85, 53, -57, -73, -30, -65, -62, 119, -82, 46, 21, 83, 126, 44, 3, 121, -66, -49, -46,
            71, -114, -82, -23, 114, -81, 96, 100, -110, -48, -70, -69, 39, -118, -85, -22, -96, 7,
            40, -47, 1, -59, 97, 10, 12, -16, -6, 99, 64, 98, 96, 68, -83, 118, 71, -106, -114, -58,
            -24, 75, 42, -89, -57, 13, -19, -73, -127, -66, 50, -52, 113, -71, -99, 51, -39, -77,
            101, -98, -110, -50, -11, -65, 77, -74, -10, -98, 30, -91, 22, -29, 37, 75, 113, 23, 64,
            123, -87, 5, -54, -54, -70, 44, 27, -69, 32, -6, 4, -95, -51, -101, -67, -52, -85, -91,
            -55, 117, 72, -103, 101, -47, 13, -69, 36, 98, 6, 50, -111, -46, -110, 88, -19, -15, 27,
            -87, 96, 47, 94, -13, 124, 77, 67, 99, 38, -61, -62, 75, -15, 3, -108, 82, 106, -11,
            -35, 85, -14, 10, 94, -72, 31, -117, -42, 60, 50, -7, 15, -111, -2, -120, -114, -38, 95,
            53, -87, -10, -81, 106, 56, 92, 62, 67, 62, 30, 90, -94, -4, 14, 14, 9, 50, 45, 109, 5,
            -125, -31, -18, -52, 49, -73
    };

    private static final String TAG = "ChromeWebApkHost";

    /** Finch experiment name. */
    private static final String WEBAPK_DISABLE_EXPERIMENT_NAME = "WebApkKillSwitch";

    /** Finch experiment group which forces WebAPKs off. */
    private static final String WEBAPK_RUNTIME_DISABLED = "Disabled";

    private static Boolean sEnabledForTesting;

    public static void init() {
        WebApkValidator.initWithBrowserHostSignature(EXPECTED_SIGNATURE);
    }

    public static void initForTesting(boolean enabled) {
        sEnabledForTesting = enabled;
    }

    public static boolean isEnabled() {
        if (sEnabledForTesting != null) return sEnabledForTesting;

        return isEnabledInPrefs();
    }

    @CalledByNative
    private static boolean areWebApkEnabled() {
        return ChromeWebApkHost.isEnabled();
    }

    /**
     * Check the cached value to figure out if the feature is enabled. We have
     * to use the cached value because native library may not yet been loaded.
     *
     * @return Whether the feature is enabled.
     */
    private static boolean isEnabledInPrefs() {
        // Will go away once the feature is enabled for everyone by default.
        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskReads();
        try {
            return ChromePreferenceManager.getInstance(
                    ContextUtils.getApplicationContext()).getCachedWebApkRuntimeEnabled();
        } finally {
            StrictMode.setThreadPolicy(oldPolicy);
        }
    }

    /**
     * Once native is loaded we can consult the command-line (set via about:flags) and also finch
     * state to see if we should enable WebAPKs.
     */
    public static void cacheEnabledStateForNextLaunch() {
        boolean wasEnabled = isEnabledInPrefs();
        CommandLine instance = CommandLine.getInstance();
        String experiment = FieldTrialList.findFullName(WEBAPK_DISABLE_EXPERIMENT_NAME);
        boolean isEnabled = (!WEBAPK_RUNTIME_DISABLED.equals(experiment)
                && instance.hasSwitch(ChromeSwitches.ENABLE_WEBAPK));

        if (isEnabled != wasEnabled) {
            Log.d(TAG, "WebApk setting changed (%s => %s)", wasEnabled, isEnabled);
            ChromePreferenceManager.getInstance(ContextUtils.getApplicationContext())
                    .setCachedWebApkRuntimeEnabled(isEnabled);
        }
    }
}

// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import org.chromium.base.CommandLine;
import org.chromium.base.annotations.SuppressFBWarnings;

import java.io.File;

/**
 * Provides implementation of command line initialization for Chrome for Android.
 */
public final class ChromeCommandLineInitUtil {

    private static final String TAG = "ChromeCommandLineInitUtil";

    /**
     * The location of the command line file needs to be in a protected
     * directory so requires root access to be tweaked, i.e., no other app in a
     * regular (non-rooted) device can change this file's contents.
     * See below for debugging on a regular (non-rooted) device.
     */
    private static final String COMMAND_LINE_FILE_PATH = "/data/local";

    /**
     * This path (writable by the shell in regular non-rooted "user" builds) is used when:
     * 1) The "debug app" is set to chrome
     * and
     * 2) ADB is enabled.
     *
     */
    private static final String COMMAND_LINE_FILE_PATH_DEBUG_APP = "/data/local/tmp";
    private static final String COMMAND_LINE_FILE = "chrome-command-line";

    private ChromeCommandLineInitUtil() {
    }

    @SuppressFBWarnings("DMI_HARDCODED_ABSOLUTE_FILENAME")
    public static void initChromeCommandLine(Context context) {
        if (!CommandLine.isInitialized()) {
            File commandLineFile = getAlternativeCommandLinePath(context);
            if (commandLineFile == null) {
                commandLineFile = new File(COMMAND_LINE_FILE_PATH, COMMAND_LINE_FILE);
            }
            CommandLine.initFromFile(commandLineFile.getPath());
        }
    }

    /**
     * Use an alternative path if adb is enabled and the debug app is chrome.
     */
    @SuppressFBWarnings("DMI_HARDCODED_ABSOLUTE_FILENAME")
    private static File getAlternativeCommandLinePath(Context context) {
        File alternativeCommandLineFile =
                new File(COMMAND_LINE_FILE_PATH_DEBUG_APP, COMMAND_LINE_FILE);
        if (!alternativeCommandLineFile.exists()) return null;
        try {
            String debugApp = Build.VERSION.SDK_INT < 17
                    ? getDebugAppPreJBMR1(context) : getDebugAppJBMR1(context);

            if (debugApp != null
                    && debugApp.equals(context.getApplicationContext().getPackageName())) {
                Log.i(TAG, "Using alternative command line file in "
                        + alternativeCommandLineFile.getPath());
                return alternativeCommandLineFile;
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "Unable to detect alternative command line file");
        }

        return null;
    }

    @SuppressLint("NewApi")
    private static String getDebugAppJBMR1(Context context) {
        boolean adbEnabled = Settings.Global.getInt(context.getContentResolver(),
                Settings.Global.ADB_ENABLED, 0) == 1;
        if (adbEnabled) {
            return Settings.Global.getString(context.getContentResolver(),
                    Settings.Global.DEBUG_APP);
        }
        return null;
    }

    @SuppressWarnings("deprecation")
    private static String getDebugAppPreJBMR1(Context context) {
        boolean adbEnabled = Settings.System.getInt(context.getContentResolver(),
                Settings.System.ADB_ENABLED, 0) == 1;
        if (adbEnabled) {
            return Settings.System.getString(context.getContentResolver(),
                    Settings.System.DEBUG_APP);
        }
        return null;
    }
}

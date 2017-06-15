// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.ui.base;

import android.util.DisplayMetrics;
import android.view.Display;

import org.chromium.base.BuildConfig;
import org.chromium.base.ContextUtils;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.ui.display.DisplayAndroidManager;

import java.util.Arrays;

/**
 * This class provides the resource bundle related methods for the native
 * library.
 */
@JNINamespace("ui")
final class ResourceBundle {
    private ResourceBundle() {}

    @CalledByNative
    private static String getLocalePakResourcePath(String locale) {
        if (Arrays.binarySearch(BuildConfig.UNCOMPRESSED_LOCALES,
                "stored-locales/" + locale) >= 0) {
            return "assets/stored-locales/" + locale + ".pak";
        }
        return null;
    }

    @CalledByNative
    private static float getPrimaryDisplayScale() {
        Display primaryDisplay = DisplayAndroidManager.getDefaultDisplayForContext(
                ContextUtils.getApplicationContext());
        DisplayMetrics displayMetrics = new DisplayMetrics();
        primaryDisplay.getMetrics(displayMetrics);
        return displayMetrics.density;
    }
}

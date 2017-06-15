// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.vr_shell;

import android.support.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Abstracts away the VrCoreVersionCheckerImpl class, which may or may not be present at runtime
 * depending on compile flags.
 */
public interface VrCoreVersionChecker {
    public static final int VR_NOT_SUPPORTED = 0;
    public static final int VR_NOT_AVAILABLE = 1;
    public static final int VR_OUT_OF_DATE = 2;
    public static final int VR_READY = 3;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({VR_NOT_AVAILABLE, VR_OUT_OF_DATE, VR_READY})
    public @interface VrCoreCompatibility {}

    public static final String VR_CORE_PACKAGE_ID = "com.google.vr.vrcore";

    /**
     * Check if VrCore is installed or if installed version is compatible with Chromium.
     */
    int getVrCoreCompatibility();
}

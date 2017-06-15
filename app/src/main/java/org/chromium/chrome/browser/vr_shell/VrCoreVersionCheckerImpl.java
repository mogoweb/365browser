// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.vr_shell;

import android.os.Build;

import com.google.vr.ndk.base.Version;
import com.google.vr.vrcore.base.api.VrCoreNotAvailableException;
import com.google.vr.vrcore.base.api.VrCoreUtils;

import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.base.PackageUtils;

import org.chromium.chrome.browser.ChromeFeatureList;

/**
 * Helper class to check if VrCore version is compatible with Chromium.
 */
public class VrCoreVersionCheckerImpl implements VrCoreVersionChecker {
    private static final String TAG = "VrCoreVersionChecker";

    private static final String MIN_SDK_VERSION_PARAM_NAME = "min_sdk_version";

    @Override
    public int getVrCoreCompatibility() {
        // Supported Build version is determined by the webvr cardboard support feature.
        // Default is KITKAT unless specified via server side finch config.
        if (Build.VERSION.SDK_INT < ChromeFeatureList.getFieldTrialParamByFeatureAsInt(
                                            ChromeFeatureList.WEBVR_CARDBOARD_SUPPORT,
                                            MIN_SDK_VERSION_PARAM_NAME,
                                            Build.VERSION_CODES.KITKAT)) {
            return VrCoreVersionChecker.VR_NOT_SUPPORTED;
        }
        try {
            String vrCoreSdkLibraryVersionString = VrCoreUtils.getVrCoreSdkLibraryVersion(
                    ContextUtils.getApplicationContext());
            Version vrCoreSdkLibraryVersion = Version.parse(vrCoreSdkLibraryVersionString);
            Version targetSdkLibraryVersion =
                    Version.parse(com.google.vr.ndk.base.BuildConstants.VERSION);
            if (!vrCoreSdkLibraryVersion.isAtLeast(targetSdkLibraryVersion)) {
                return VrCoreVersionChecker.VR_OUT_OF_DATE;
            }
            return VrCoreVersionChecker.VR_READY;
        } catch (VrCoreNotAvailableException e) {
            Log.i(TAG, "Unable to find VrCore.");
            // Old versions of VrCore are not integrated with the sdk library version check and will
            // trigger this exception even though VrCore is installed.
            // Double check package manager to make sure we are not telling user to install
            // when it should just be an update.
            if (PackageUtils.getPackageVersion(
                        ContextUtils.getApplicationContext(), VR_CORE_PACKAGE_ID)
                    != -1) {
                return VrCoreVersionChecker.VR_OUT_OF_DATE;
            }
            return VrCoreVersionChecker.VR_NOT_AVAILABLE;
        }
    }
}

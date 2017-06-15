// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.vr_shell;

import android.app.Activity;
import android.content.Context;
import android.os.StrictMode;

import com.google.vr.ndk.base.AndroidCompat;

import org.chromium.base.Log;
import org.chromium.base.annotations.UsedByReflection;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;

/**
 * Builder class to create all VR related classes. These VR classes are behind the same build time
 * flag as this class. So no reflection is necessary when create them.
 */
@UsedByReflection("VrShellDelegate.java")
public class VrClassesWrapperImpl implements VrClassesWrapper {
    private static final String TAG = "VrClassesWrapperImpl";

    @UsedByReflection("VrShellDelegate.java")
    public VrClassesWrapperImpl() {}

    @Override
    public NonPresentingGvrContext createNonPresentingGvrContext(ChromeActivity activity) {
        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskReads();
        try {
            return new NonPresentingGvrContextImpl(activity);
        } catch (Exception ex) {
            Log.e(TAG, "Unable to instantiate NonPresentingGvrContextImpl", ex);
            return null;
        } finally {
            StrictMode.setThreadPolicy(oldPolicy);
        }
    }

    @Override
    public VrShell createVrShell(
            ChromeActivity activity, VrShellDelegate delegate, TabModelSelector tabModelSelector) {
        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskReads();
        try {
            return new VrShellImpl(activity, delegate, tabModelSelector);
        } catch (Exception ex) {
            Log.e(TAG, "Unable to instantiate VrShellImpl", ex);
            return null;
        } finally {
            StrictMode.setThreadPolicy(oldPolicy);
        }
    }

    @Override
    public VrDaydreamApi createVrDaydreamApi(Activity activity) {
        return new VrDaydreamApiImpl(activity);
    }

    @Override
    public VrDaydreamApi createVrDaydreamApi(Context context) {
        return new VrDaydreamApiImpl(context);
    }

    @Override
    public VrCoreVersionChecker createVrCoreVersionChecker() {
        return new VrCoreVersionCheckerImpl();
    }

    @Override
    public void setVrModeEnabled(Activity activity, boolean enabled) {
        AndroidCompat.setVrModeEnabled(activity, enabled);
    }
}

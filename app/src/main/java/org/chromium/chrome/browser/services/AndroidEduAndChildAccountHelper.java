// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.services;

import android.app.Activity;

import org.chromium.chrome.browser.ChromeApplication;
import org.chromium.chrome.browser.childaccounts.ChildAccountService;

/**
 * A helper for Android EDU and child account checks.
 * Usage:
 * new AndroidEduAndChildAccountHelper() { override onParametersReady() }.start(activity).
 */
public abstract class AndroidEduAndChildAccountHelper
        implements ChildAccountService.HasChildAccountCallback, AndroidEduOwnerCheckCallback {
    private Boolean mIsAndroidEduDevice;
    private Boolean mHasChildAccount;

    /** The callback called when Android EDU and child account parameters are known. */
    public abstract void onParametersReady();

    /** @return Whether the device is Android EDU device. */
    public boolean isAndroidEduDevice() {
        return mIsAndroidEduDevice;
    }

    /** @return Whether the device has a child account. */
    public boolean hasChildAccount() {
        return mHasChildAccount;
    }

    /**
     * Starts fetching the Android EDU and child accounts information.
     * Calls onParametersReady() once the information is fetched.
     * @param activity The context.
     */
    public void start(Activity activity) {
        android.util.Log.i("AndroidEduAndChildAccountHelper", "before checking child and EDU");
        ChildAccountService.getInstance(activity).checkHasChildAccount(this);
        ((ChromeApplication) activity.getApplication()).checkIsAndroidEduDevice(this);
        // TODO(aruslan): Should we start a watchdog to kill if Child/Edu stuff takes too long?
        android.util.Log.i("AndroidEduAndChildAccountHelper", "returning from start");
    }

    private void checkDone() {
        if (mIsAndroidEduDevice == null || mHasChildAccount == null) return;
        android.util.Log.i("AndroidEduAndChildAccountHelper", "parameters are ready");
        onParametersReady();
    }

    // AndroidEdu.OwnerCheckCallback:
    @Override
    public void onSchoolCheckDone(boolean isAndroidEduDevice) {
        android.util.Log.i("AndroidEduAndChildAccountHelper", "onSchoolCheckDone");
        mIsAndroidEduDevice = isAndroidEduDevice;
        checkDone();
    }

    // ChildAccountManager.HasChildAccountCallback:
    @Override
    public void onChildAccountChecked(boolean hasChildAccount) {
        android.util.Log.i("AndroidEduAndChildAccountHelper", "onChildAccountChecked");
        mHasChildAccount = hasChildAccount;
        checkDone();
    }
}

// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.profiles;

import android.content.Context;

import org.chromium.base.ContextUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.browser.cookies.CookiesFetcher;

/**
 * Wrapper that allows passing a Profile reference around in the Java layer.
 */
public class Profile {

    /** Whether this wrapper corresponds to an off the record Profile. */
    private final boolean mIsOffTheRecord;

    /** Pointer to the Native-side ProfileAndroid. */
    private long mNativeProfileAndroid;
// SWE-feature-password-encryption
    private EncryptionHelper mEncryptionHelper;
// SWE-feature-password-encryption

    private Profile(long nativeProfileAndroid) {
        mNativeProfileAndroid = nativeProfileAndroid;
        mIsOffTheRecord = nativeIsOffTheRecord(mNativeProfileAndroid);
// SWE-feature-password-encryption
        mEncryptionHelper = new EncryptionHelper(ContextUtils.getApplicationContext());
// SWE-feature-password-encryption
    }

    public static Profile getLastUsedProfile() {
        return (Profile) nativeGetLastUsedProfile();
    }

    /**
     * Destroys the Profile.  Destruction is delayed until all associated
     * renderers have been killed, so the profile might not be destroyed upon returning from
     * this call.
     */
    public void destroyWhenAppropriate() {
        nativeDestroyWhenAppropriate(mNativeProfileAndroid);
    }

    public Profile getOriginalProfile() {
        return (Profile) nativeGetOriginalProfile(mNativeProfileAndroid);
    }

    public Profile getOffTheRecordProfile() {
        return (Profile) nativeGetOffTheRecordProfile(mNativeProfileAndroid);
    }

    public boolean hasOffTheRecordProfile() {
        return nativeHasOffTheRecordProfile(mNativeProfileAndroid);
    }

    public boolean isOffTheRecord() {
        return mIsOffTheRecord;
    }

    /**
     * @return Whether or not the native side profile exists.
     */
    @VisibleForTesting
    public boolean isNativeInitialized() {
        return mNativeProfileAndroid != 0;
    }

    @CalledByNative
    private static Profile create(long nativeProfileAndroid) {
        return new Profile(nativeProfileAndroid);
    }

    @CalledByNative
    private void onNativeDestroyed() {
        mNativeProfileAndroid = 0;

        if (mIsOffTheRecord) {
            Context context = ContextUtils.getApplicationContext();
            CookiesFetcher.deleteCookiesIfNecessary(context);
        }
    }

    @CalledByNative
    private long getNativePointer() {
        return mNativeProfileAndroid;
    }

// SWE-feature-password-encryption
    @CalledByNative
    public byte[] encrypt(byte[] data) {
        return mEncryptionHelper.encrypt(data);
    }

    @CalledByNative
    public byte[] decrypt(byte[] data) {
        return mEncryptionHelper.decrypt(data);
    }
// SWE-feature-password-encryption

    private static native Object nativeGetLastUsedProfile();
    private native void nativeDestroyWhenAppropriate(long nativeProfileAndroid);
    private native Object nativeGetOriginalProfile(long nativeProfileAndroid);
    private native Object nativeGetOffTheRecordProfile(long nativeProfileAndroid);
    private native boolean nativeHasOffTheRecordProfile(long nativeProfileAndroid);
    private native boolean nativeIsOffTheRecord(long nativeProfileAndroid);
}

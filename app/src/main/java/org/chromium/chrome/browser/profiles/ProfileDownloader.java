// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.profiles;

import android.graphics.Bitmap;

import org.chromium.base.ObserverList;
import org.chromium.base.ThreadUtils;
import org.chromium.base.annotations.CalledByNative;

/**
 * Android wrapper of the ProfileDownloader which provides access from the Java layer.
 * The native ProfileDownloader requires its access to be in the UI thread.
 * See chrome/browser/profiles/profile_downloader.h/cc for more details.
 */
public class ProfileDownloader {
    private static final ObserverList<Observer> sObservers = new ObserverList<Observer>();

    /**
     * Interface for receiving notifications on account information updates.
     */
    public interface Observer {
        /**
         * Notifies that an account data in the profile has been updated.
         * @param accountId An account ID.
         * @param fullName A full name.
         * @param givenName A given name.
         * @param bitmap A user picture.
         */
        void onProfileDownloaded(String accountId, String fullName, String givenName,
                Bitmap bitmap);
    }

    /**
     * Add an observer.
     * @param observer An observer.
     */
    public static void addObserver(Observer observer) {
        sObservers.addObserver(observer);
    }

    /**
     * Remove an observer.
     * @param observer An observer.
     */
    public static void removeObserver(Observer observer) {
        sObservers.removeObserver(observer);
    }

    /**
     * Starts fetching the account information for a given account.
     * @param profile Profile associated with the request
     * @param accountId Account name to fetch the information for
     * @param imageSidePixels Request image side (in pixels)
     */
    public static void startFetchingAccountInfoFor(
            Profile profile, String accountId, int imageSidePixels, boolean isPreSignin) {
        ThreadUtils.assertOnUiThread();
        nativeStartFetchingAccountInfoFor(profile, accountId, imageSidePixels, isPreSignin);
    }

    @CalledByNative
    private static void onProfileDownloadSuccess(String accountId, String fullName,
            String givenName, Bitmap bitmap) {
        ThreadUtils.assertOnUiThread();
        for (Observer observer : sObservers) {
            observer.onProfileDownloaded(accountId, fullName, givenName, bitmap);
        }
    }

    /**
     * @param profile Profile
     * @return The profile full name if cached, or null.
     */
    public static String getCachedFullName(Profile profile) {
        return nativeGetCachedFullNameForPrimaryAccount(profile);
    }

    /**
     * @param profile Profile
     * @return The profile given name if cached, or null.
     */
    public static String getCachedGivenName(Profile profile) {
        return nativeGetCachedGivenNameForPrimaryAccount(profile);
    }

    /**
     * @param profile Profile
     * @return The profile avatar if cached, or null.
     */
    public static Bitmap getCachedAvatar(Profile profile) {
        return nativeGetCachedAvatarForPrimaryAccount(profile);
    }

    // Native methods.
    private static native void nativeStartFetchingAccountInfoFor(
            Profile profile, String accountId, int imageSidePixels, boolean isPreSignin);
    private static native String nativeGetCachedFullNameForPrimaryAccount(Profile profile);
    private static native String nativeGetCachedGivenNameForPrimaryAccount(Profile profile);
    private static native Bitmap nativeGetCachedAvatarForPrimaryAccount(Profile profile);
}

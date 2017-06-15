// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.webapps;

import android.support.annotation.IntDef;

import org.chromium.base.Callback;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Defines an interface for installing WebAPKs via Google Play.
 */
public interface GooglePlayWebApkInstallDelegate {
    /**
     * The app state transitions provided by Google Play during download and installation process.
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({INVALID, DOWNLOAD_PENDING, DOWNLOADING, DOWNLOAD_CANCELLED, DOWNLOAD_ERROR,
             INSTALLING, INSTALL_ERROR, INSTALLED})
    public @interface InstallerPackageEvent {}
    public static final int INVALID = -1;
    public static final int DOWNLOAD_PENDING = 0;
    public static final int DOWNLOADING = 1;
    public static final int DOWNLOAD_CANCELLED = 2;
    public static final int DOWNLOAD_ERROR = 3;
    public static final int INSTALLING = 4;
    public static final int INSTALL_ERROR = 5;
    public static final int INSTALLED = 6;

    /**
     * Uses Google Play to install WebAPK asynchronously.
     * @param packageName The package name of WebAPK to install.
     * @param version The version of WebAPK to install.
     * @param title The title of the WebAPK to display during installation.
     * @param token The token from WebAPK Minter Server.
     * @param url The start URL of the WebAPK to install.
     * @param callback The callback to invoke when the install completes, times out or fails.
     */
    void installAsync(String packageName, int version, String title, String token, String url,
            Callback<Integer> callback);

    /**
     * Uses Google Play to update WebAPK asynchronously.
     * @param packageName The package name of WebAPK to update.
     * @param version The version of WebAPK to update.
     * @param title The title of the WebAPK to display during update.
     * @param token The token from WebAPK Minter Server.
     * @param url The start URL of the WebAPK to update.
     * @param callback The callback to invoke when the update completes, times out or fails.
     */
    void updateAsync(String packageName, int version, String title, String token, String url,
            Callback<Integer> callback);
    /**
     * Calls the callback once the installation either succeeded or failed.
     * @param packageName The package name of WebAPK for the installation.
     * @param event The result of the install.
     */
    void onGotInstallEvent(String packageName, @InstallerPackageEvent int event);
}

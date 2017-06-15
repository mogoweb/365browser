// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.webapps;

import android.content.pm.PackageManager;

import org.chromium.base.Callback;
import org.chromium.base.ContextUtils;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.browser.AppHooks;
import org.chromium.chrome.browser.banners.InstallerDelegate;
import org.chromium.chrome.browser.metrics.WebApkUma;
import org.chromium.webapk.lib.common.WebApkConstants;

/**
 * Java counterpart to webapk_installer.h
 * Contains functionality to install / update WebAPKs.
 * This Java object is created by and owned by the native WebApkInstaller.
 */
public class WebApkInstaller {
    private static final String TAG = "WebApkInstaller";

    /** Weak pointer to the native WebApkInstaller. */
    private long mNativePointer;

    /** Talks to Google Play to install WebAPKs. */
    private final GooglePlayWebApkInstallDelegate mInstallDelegate;

    private WebApkInstaller(long nativePtr) {
        mNativePointer = nativePtr;
        mInstallDelegate = AppHooks.get().getGooglePlayWebApkInstallDelegate();
    }

    @CalledByNative
    private static WebApkInstaller create(long nativePtr) {
        return new WebApkInstaller(nativePtr);
    }

    @CalledByNative
    private void destroy() {
        mNativePointer = 0;
    }

    /**
     * Installs a WebAPK and monitors the installation.
     * @param packageName The package name of the WebAPK to install.
     * @param version The version of WebAPK to install.
     * @param title The title of the WebAPK to display during installation.
     * @param token The token from WebAPK Server.
     * @param url The start URL of the WebAPK to install.
     */
    @CalledByNative
    private void installWebApkAsync(final String packageName, int version, String title,
            String token, String url, final int source) {
        // Check whether the WebAPK package is already installed. The WebAPK may have been installed
        // by another Chrome version (e.g. Chrome Dev). We have to do this check because the Play
        // install API fails silently if the package is already installed.
        if (isWebApkInstalled(packageName)) {
            notify(WebApkInstallResult.SUCCESS);
            return;
        }

        if (mInstallDelegate == null) {
            notify(WebApkInstallResult.FAILURE);
            WebApkUma.recordGooglePlayInstallResult(
                    WebApkUma.GOOGLE_PLAY_INSTALL_FAILED_NO_DELEGATE);
            return;
        }

        Callback<Integer> callback = new Callback<Integer>() {
            @Override
            public void onResult(Integer result) {
                WebApkInstaller.this.notify(result);
                if (result == WebApkInstallResult.FAILURE) return;

                // Stores the source info of WebAPK in WebappDataStorage.
                WebappRegistry.getInstance().register(
                        WebApkConstants.WEBAPK_ID_PREFIX + packageName,
                        new WebappRegistry.FetchWebappDataStorageCallback() {
                            @Override
                            public void onWebappDataStorageRetrieved(WebappDataStorage storage) {
                                storage.updateSource(source);
                                storage.updateTimeOfLastCheckForUpdatedWebManifest();
                            }
                        });
            }
        };
        mInstallDelegate.installAsync(packageName, version, title, token, url, callback);
    }

    private void notify(@WebApkInstallResult int result) {
        if (mNativePointer != 0) {
            nativeOnInstallFinished(mNativePointer, result);
        }
    }

    /**
     * Updates a WebAPK installation.
     * @param packageName The package name of the WebAPK to install.
     * @param version The version of WebAPK to install.
     * @param title The title of the WebAPK to display during installation.
     * @param token The token from WebAPK Server.
     * @param url The start URL of the WebAPK to install.
     */
    @CalledByNative
    private void updateAsync(
            String packageName, int version, String title, String token, String url) {
        if (mInstallDelegate == null) {
            notify(WebApkInstallResult.FAILURE);
            return;
        }

        Callback<Integer> callback = new Callback<Integer>() {
            @Override
            public void onResult(Integer result) {
                WebApkInstaller.this.notify(result);
            }
        };
        mInstallDelegate.updateAsync(packageName, version, title, token, url, callback);
    }

    private boolean isWebApkInstalled(String packageName) {
        PackageManager packageManager = ContextUtils.getApplicationContext().getPackageManager();
        return InstallerDelegate.isInstalled(packageManager, packageName);
    }

    private native void nativeOnInstallFinished(
            long nativeWebApkInstaller, @WebApkInstallResult int result);
}

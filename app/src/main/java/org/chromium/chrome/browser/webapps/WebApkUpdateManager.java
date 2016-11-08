// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.webapps;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;

import org.chromium.base.ContextUtils;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.webapps.WebappRegistry.FetchWebappDataStorageCallback;
import org.chromium.webapk.lib.common.WebApkConstants;

import java.util.concurrent.TimeUnit;

/**
 * WebApkUpdateManager manages when to check for updates to the WebAPK's Web Manifest, and sends
 * an update request to the WebAPK Server when an update is needed.
 */
public class WebApkUpdateManager implements ManifestUpgradeDetector.Callback {
    /** Number of milliseconds between checks for whether the WebAPK's Web Manifest has changed. */
    static final long FULL_CHECK_UPDATE_INTERVAL = TimeUnit.DAYS.toMillis(3L);

    /**
     * Number of milliseconds to wait before re-requesting an updated WebAPK from the WebAPK
     * server if the previous update attempt failed.
     */
    static final long RETRY_UPDATE_DURATION = TimeUnit.HOURS.toMillis(12L);

    private ManifestUpgradeDetector mUpgradeDetector;

    /**
     * Checks whether the WebAPK's Web Manifest has changed. Requests an updated WebAPK if the
     * Web Manifest has changed. Skips the check if the check was done recently.
     * @param tab  The tab of the WebAPK.
     * @param info The WebappInfo of the WebAPK.
     */
    public void updateIfNeeded(Tab tab, WebappInfo info) {
        mUpgradeDetector = new ManifestUpgradeDetector(tab, info, this);

        WebappRegistry.FetchWebappDataStorageCallback callback =
                new WebappRegistry.FetchWebappDataStorageCallback() {
                    @Override
                    public void onWebappDataStorageRetrieved(WebappDataStorage storage) {
                        long sinceLastCheckDuration = System.currentTimeMillis()
                                - storage.getLastCheckForWebManifestUpdateTime();
                        long sinceLastRequestDuration = System.currentTimeMillis()
                                - storage.getLastWebApkUpdateRequestCompletionTime();
                        if (sinceLastCheckDuration > FULL_CHECK_UPDATE_INTERVAL
                                || (sinceLastRequestDuration > RETRY_UPDATE_DURATION
                                && !storage.getDidLastWebApkUpdateRequestSucceed())) {
                            if (mUpgradeDetector.start()) {
                                // crbug.com/636525. The timestamp of the last check for updated
                                // Web Manifest should be updated after the detector finds the
                                // Web Manifest, not when the detector is started.
                                storage.updateTimeOfLastCheckForUpdatedWebManifest();
                            }
                        }
                    }
        };
        WebappRegistry.getWebappDataStorage(
                ContextUtils.getApplicationContext(), info.id(), callback);
    }

    @Override
    public void onUpgradeNeededCheckFinished(boolean needsUpgrade, WebappInfo newInfo) {
        if (needsUpgrade) updateAsync(newInfo);
        if (mUpgradeDetector != null) {
            mUpgradeDetector.destroy();
        }
        mUpgradeDetector = null;
    }

    /**
     * Sends request to WebAPK Server to update WebAPK.
     * @param webappInfo The new fetched Web Manifest data of the WebAPK.
     */
    public void updateAsync(WebappInfo webappInfo) {
        int webApkVersion = getVersionFromMetaData(webappInfo.webApkPackageName());
        nativeUpdateAsync(webappInfo.uri().toString(), webappInfo.scopeUri().toString(),
                webappInfo.name(), webappInfo.shortName(), "", webappInfo.icon(),
                webappInfo.displayMode(), webappInfo.orientation(), webappInfo.themeColor(),
                webappInfo.backgroundColor(), mUpgradeDetector.getManifestUrl(),
                webappInfo.webApkPackageName(), webApkVersion);
    }

    public void destroy() {
        if (mUpgradeDetector != null) {
            mUpgradeDetector.destroy();
        }
        mUpgradeDetector = null;
    }

    private static int getVersionFromMetaData(String webApkPackage) {
        try {
            PackageManager packageManager =
                    ContextUtils.getApplicationContext().getPackageManager();
            PackageInfo info = packageManager.getPackageInfo(webApkPackage, 0);
            return info.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return 1;
    }

    /**
     * Called after either a request to update the WebAPK has been sent or the update process
     * fails.
     */
    @CalledByNative
    private static void onBuiltWebApk(final boolean success, String webapkPackage) {
        WebappRegistry.getWebappDataStorage(ContextUtils.getApplicationContext(),
                WebApkConstants.WEBAPK_ID_PREFIX + webapkPackage,
                new FetchWebappDataStorageCallback() {
                    @Override
                    public void onWebappDataStorageRetrieved(WebappDataStorage storage) {
                        // Update the request time and result together. It prevents getting a
                        // correct request time but a result from the previous request.
                        storage.updateTimeOfLastWebApkUpdateRequestCompletion();
                        storage.updateDidLastWebApkUpdateRequestSucceed(success);
                    }
                });
    }

    private static native void nativeUpdateAsync(String startUrl, String scope, String name,
            String shortName, String iconUrl, Bitmap icon, int displayMode, int orientation,
            long themeColor, long backgroundColor, String manifestUrl, String webApkPackage,
            int webApkVersion);
}

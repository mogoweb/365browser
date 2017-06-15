// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.webapps;

import static org.chromium.webapk.lib.common.WebApkConstants.WEBAPK_PACKAGE_PREFIX;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.text.TextUtils;

import org.chromium.base.ActivityState;
import org.chromium.base.ApplicationStatus;
import org.chromium.base.CommandLine;
import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.browser.ChromeSwitches;
import org.chromium.chrome.browser.metrics.WebApkUma;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.util.UrlUtilities;
import org.chromium.webapk.lib.client.WebApkVersion;

import java.util.Map;

/**
 * WebApkUpdateManager manages when to check for updates to the WebAPK's Web Manifest, and sends
 * an update request to the WebAPK Server when an update is needed.
 */
public class WebApkUpdateManager implements WebApkUpdateDataFetcher.Observer {
    private static final String TAG = "WebApkUpdateManager";

    /**
     * Number of times to wait for updating the WebAPK after it is moved to the background prior
     * to doing the update while the WebAPK is in the foreground.
     */
    private static final int MAX_UPDATE_ATTEMPTS = 3;

    /** Whether updates are enabled. Some tests disable updates. */
    private static boolean sUpdatesEnabled = true;

    /** Data extracted from the WebAPK's launch intent and from the WebAPK's Android Manifest. */
    private WebApkInfo mInfo;

    /**
     * The cached data for a pending update request which needs to be sent after the WebAPK isn't
     * running in the foreground.
     */
    private PendingUpdate mPendingUpdate;

    /** The WebApkActivity which owns the WebApkUpdateManager. */
    private final WebApkActivity mActivity;

    /** The WebappDataStorage with cached data about prior update requests. */
    private final WebappDataStorage mStorage;

    private WebApkUpdateDataFetcher mFetcher;

    /**
     * Contains all the data which is cached for a pending update request once the WebAPK is no
     * longer running foreground.
     */
    private static class PendingUpdate {
        public WebApkInfo mUpdateInfo;
        public String mBestIconUrl;
        public boolean mIsManifestStale;

        public PendingUpdate(WebApkInfo info, String bestIconUrl, boolean isManifestStale) {
            mUpdateInfo = info;
            mBestIconUrl = bestIconUrl;
            mIsManifestStale = isManifestStale;
        }
    }

    public WebApkUpdateManager(WebApkActivity activity, WebappDataStorage storage) {
        mActivity = activity;
        mStorage = storage;
    }

    /**
     * Checks whether the WebAPK's Web Manifest has changed. Requests an updated WebAPK if the Web
     * Manifest has changed. Skips the check if the check was done recently.
     * @param tab  The tab of the WebAPK.
     * @param info The WebApkInfo of the WebAPK.
     */
    public void updateIfNeeded(Tab tab, WebApkInfo info) {
        mInfo = info;

        if (!shouldCheckIfWebManifestUpdated(mInfo)) return;

        mFetcher = buildFetcher();
        mFetcher.start(tab, mInfo, this);
    }

    /**
     * It sends the pending update request to the WebAPK server if exits.
     * @return Whether a pending update request is sent to the WebAPK server.
     */
    public boolean requestPendingUpdate() {
        if (mPendingUpdate != null) {
            updateAsync(mPendingUpdate.mUpdateInfo, mPendingUpdate.mBestIconUrl,
                    mPendingUpdate.mIsManifestStale);
            return true;
        }
        return false;
    }

    public void destroy() {
        destroyFetcher();
    }

    public boolean getHasPendingUpdateForTesting() {
        return mPendingUpdate != null;
    }

    public static void setUpdatesEnabledForTesting(boolean enabled) {
        sUpdatesEnabled = enabled;
    }

    @Override
    public void onWebManifestForInitialUrlNotWebApkCompatible() {
        onGotManifestData(null, null);
    }

    @Override
    public void onGotManifestData(WebApkInfo fetchedInfo, String bestIconUrl) {
        mStorage.updateTimeOfLastCheckForUpdatedWebManifest();

        boolean gotManifest = (fetchedInfo != null);
        boolean needsUpgrade = isShellApkVersionOutOfDate(mInfo)
                || (gotManifest && needsUpdate(mInfo, fetchedInfo, bestIconUrl));
        Log.v(TAG, "Got Manifest: " + gotManifest);
        Log.v(TAG, "WebAPK upgrade needed: " + needsUpgrade);

        // If the Web Manifest was not found and an upgrade is requested, stop fetching Web
        // Manifests as the user navigates to avoid sending multiple WebAPK update requests. In
        // particular:
        // - A WebAPK update request on the initial load because the Shell APK version is out of
        //   date.
        // - A second WebAPK update request once the user navigates to a page which points to the
        //   correct Web Manifest URL because the Web Manifest has been updated by the Web
        //   developer.
        //
        // If the Web Manifest was not found and an upgrade is not requested, keep on fetching
        // Web Manifests as the user navigates. For instance, the WebAPK's start_url might not
        // point to a Web Manifest because start_url redirects to the WebAPK's main page.
        if (gotManifest || needsUpgrade) {
            destroyFetcher();
        }

        if (!needsUpgrade) {
            if (!mStorage.didPreviousUpdateSucceed()) {
                recordUpdate(mStorage, WebApkInstallResult.SUCCESS, false /* relaxUpdates */);
            }
            return;
        }

        // Set WebAPK update as having failed in case that Chrome is killed prior to
        // {@link onBuiltWebApk} being called.
        recordUpdate(mStorage, WebApkInstallResult.FAILURE, false /* relaxUpdates*/);

        if (fetchedInfo != null) {
            scheduleUpdate(fetchedInfo, bestIconUrl, false /* isManifestStale */);
            return;
        }

        // Tell the server that the our version of the Web Manifest might be stale and to ignore
        // our Web Manifest data if the server's Web Manifest data is newer. This scenario can
        // occur if the Web Manifest is temporarily unreachable.
        scheduleUpdate(mInfo, "" /* bestIconUrl */, true /* isManifestStale */);
    }

    /**
     * Builds {@link WebApkUpdateDataFetcher}. In a separate function for the sake of tests.
     */
    protected WebApkUpdateDataFetcher buildFetcher() {
        return new WebApkUpdateDataFetcher();
    }

    /**
     * Sends update request to WebAPK Server if the WebAPK is running in the background; caches the
     * fetched WebApkInfo otherwise.
     */
    protected void scheduleUpdate(WebApkInfo info, String bestIconUrl, boolean isManifestStale) {
        int numberOfUpdateRequests = mStorage.getUpdateRequests();
        boolean forceUpdateNow =  numberOfUpdateRequests >= MAX_UPDATE_ATTEMPTS;
        if (!isInForeground() || forceUpdateNow) {
            updateAsync(info, bestIconUrl, isManifestStale);
            WebApkUma.recordUpdateRequestSent(WebApkUma.UPDATE_REQUEST_SENT_FIRST_TRY);
            return;
        }

        mStorage.recordUpdateRequest();
        // The {@link numberOfUpdateRequests} can never exceed 2 here (otherwise we'll have taken
        // the branch above and have returned before reaching this statement).
        WebApkUma.recordUpdateRequestQueued(numberOfUpdateRequests);
        mPendingUpdate = new PendingUpdate(info, bestIconUrl, isManifestStale);
    }

    /** Returns whether the associated WebApkActivity is running in foreground. */
    protected boolean isInForeground() {
        int state = ApplicationStatus.getStateForActivity(mActivity);
        return (state != ActivityState.STOPPED && state != ActivityState.DESTROYED);
    }

    /**
     * Sends update request to the WebAPK Server and cleanup.
     */
    private void updateAsync(WebApkInfo info, String bestIconUrl, boolean isManifestStale) {
        updateAsyncImpl(info, bestIconUrl, isManifestStale);
        mStorage.resetUpdateRequests();
        mPendingUpdate = null;
    }

    /**
     * Sends update request to the WebAPK Server.
     */
    protected void updateAsyncImpl(WebApkInfo info, String bestIconUrl, boolean isManifestStale) {
        if (info == null) {
            return;
        }

        int versionCode = readVersionCodeFromAndroidManifest(info.webApkPackageName());
        int size = info.iconUrlToMurmur2HashMap().size();
        String[] iconUrls = new String[size];
        String[] iconHashes = new String[size];
        int i = 0;
        for (Map.Entry<String, String> entry : info.iconUrlToMurmur2HashMap().entrySet()) {
            iconUrls[i] = entry.getKey();
            String iconHash = entry.getValue();
            iconHashes[i] = iconHash != null ? iconHash : "";
            i++;
        }
        nativeUpdateAsync(info.id(), info.manifestStartUrl(), info.scopeUri().toString(),
                info.name(), info.shortName(), bestIconUrl, info.icon(), iconUrls, iconHashes,
                info.displayMode(), info.orientation(), info.themeColor(), info.backgroundColor(),
                info.manifestUrl(), info.webApkPackageName(), versionCode, isManifestStale);
    }

    /**
     * Destroys {@link mFetcher}. In a separate function for the sake of tests.
     */
    protected void destroyFetcher() {
        if (mFetcher == null) return;

        mFetcher.destroy();
        mFetcher = null;
    }

    /**
     * Reads the WebAPK's version code. Returns 0 on failure.
     */
    private int readVersionCodeFromAndroidManifest(String webApkPackage) {
        try {
            PackageManager packageManager =
                    ContextUtils.getApplicationContext().getPackageManager();
            PackageInfo packageInfo = packageManager.getPackageInfo(webApkPackage, 0);
            return packageInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return 0;
    }

    /**
     * Whether there is a new version of the //chrome/android/webapk/shell_apk code.
     */
    private static boolean isShellApkVersionOutOfDate(WebApkInfo info) {
        return info.shellApkVersion() < WebApkVersion.CURRENT_SHELL_APK_VERSION;
    }

    /**
     * Returns whether the Web Manifest should be refetched to check whether it has been updated.
     * TODO: Make this method static once there is a static global clock class.
     * @param info Meta data from WebAPK's Android Manifest.
     * True if there has not been any update attempts.
     */
    private boolean shouldCheckIfWebManifestUpdated(WebApkInfo info) {
        if (!sUpdatesEnabled) {
            return false;
        }

        if (CommandLine.getInstance().hasSwitch(
                    ChromeSwitches.CHECK_FOR_WEB_MANIFEST_UPDATE_ON_STARTUP)) {
            return true;
        }

        if (!info.webApkPackageName().startsWith(WEBAPK_PACKAGE_PREFIX)) {
            return false;
        }

        if (isShellApkVersionOutOfDate(info)
                && WebApkVersion.CURRENT_SHELL_APK_VERSION
                        > mStorage.getLastRequestedShellApkVersion()) {
            return true;
        }

        return mStorage.shouldCheckForUpdate();
    }

    /**
     * Updates {@link WebappDataStorage} with the time of the latest WebAPK update and whether the
     * WebAPK update succeeded.
     */
    private static void recordUpdate(
            WebappDataStorage storage, @WebApkInstallResult int result, boolean relaxUpdates) {
        // Update the request time and result together. It prevents getting a correct request time
        // but a result from the previous request.
        storage.updateTimeOfLastWebApkUpdateRequestCompletion();
        storage.updateDidLastWebApkUpdateRequestSucceed(result == WebApkInstallResult.SUCCESS);
        storage.setRelaxedUpdates(relaxUpdates);
    }

    /**
     * Checks whether the WebAPK needs to be updated.
     * @param info               Meta data from WebAPK's Android Manifest.
     * @param fetchedInfo        Fetched data for Web Manifest.
     * @param bestFetchedIconUrl The icon URL in {@link fetchedInfo#iconUrlToMurmur2HashMap()} best
     *                           suited for use as the launcher icon on this device.
     */
    private boolean needsUpdate(WebApkInfo info, WebApkInfo fetchedInfo, String bestIconUrl) {
        // We should have computed the Murmur2 hash for the bitmap at the best icon URL for
        // {@link fetchedInfo} (but not the other icon URLs.)
        String fetchedBestIconMurmur2Hash = fetchedInfo.iconUrlToMurmur2HashMap().get(bestIconUrl);
        String bestIconMurmur2Hash =
                findMurmur2HashForUrlIgnoringFragment(mInfo.iconUrlToMurmur2HashMap(), bestIconUrl);

        return !TextUtils.equals(bestIconMurmur2Hash, fetchedBestIconMurmur2Hash)
                || !urlsMatchIgnoringFragments(
                           mInfo.scopeUri().toString(), fetchedInfo.scopeUri().toString())
                || !urlsMatchIgnoringFragments(
                           mInfo.manifestStartUrl(), fetchedInfo.manifestStartUrl())
                || !TextUtils.equals(mInfo.shortName(), fetchedInfo.shortName())
                || !TextUtils.equals(mInfo.name(), fetchedInfo.name())
                || mInfo.backgroundColor() != fetchedInfo.backgroundColor()
                || mInfo.themeColor() != fetchedInfo.themeColor()
                || mInfo.orientation() != fetchedInfo.orientation()
                || mInfo.displayMode() != fetchedInfo.displayMode();
    }

    /**
     * Returns the Murmur2 hash for entry in {@link iconUrlToMurmur2HashMap} whose canonical
     * representation, ignoring fragments, matches {@link iconUrlToMatch}.
     */
    private String findMurmur2HashForUrlIgnoringFragment(
            Map<String, String> iconUrlToMurmur2HashMap, String iconUrlToMatch) {
        for (Map.Entry<String, String> entry : iconUrlToMurmur2HashMap.entrySet()) {
            if (urlsMatchIgnoringFragments(entry.getKey(), iconUrlToMatch)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Returns whether the urls match ignoring fragments. Canonicalizes the URLs prior to doing the
     * comparison.
     */
    protected boolean urlsMatchIgnoringFragments(String url1, String url2) {
        return UrlUtilities.urlsMatchIgnoringFragments(url1, url2);
    }

    /**
     * Called after either a request to update the WebAPK has been sent or the update process
     * fails.
     */
    @CalledByNative
    private static void onBuiltWebApk(
            String id, @WebApkInstallResult int result, boolean relaxUpdates) {
        WebappDataStorage storage = WebappRegistry.getInstance().getWebappDataStorage(id);
        if (storage == null) return;

        recordUpdate(storage, result, relaxUpdates);
        storage.updateLastRequestedShellApkVersion(WebApkVersion.CURRENT_SHELL_APK_VERSION);
    }

    private static native void nativeUpdateAsync(String id, String startUrl, String scope,
            String name, String shortName, String bestIconUrl, Bitmap bestIcon, String[] iconUrls,
            String[] iconHashes, int displayMode, int orientation, long themeColor,
            long backgroundColor, String manifestUrl, String webApkPackage, int webApkVersion,
            boolean isManifestStale);
}

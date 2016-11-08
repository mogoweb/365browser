// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.webapps;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.TextUtils;

import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.chrome.browser.ShortcutHelper;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.webapk.lib.common.WebApkMetaDataKeys;

/**
 * This class checks whether the WebAPK needs to be re-installed and sends a request to re-install
 * the WebAPK if it needs to be re-installed.
 */
public class ManifestUpgradeDetector implements ManifestUpgradeDetectorFetcher.Callback {
    /**
     * Called when the process of checking Web Manifest update is complete.
     */
    interface Callback {
        // TODO(hanxi): crbug.com/639000. Pass the icon url and icon murmur2 hash to the caller.
        // Change the interface by using {@link FetchedManifestData} instead of {@link WebappInfo}.
        public void onUpgradeNeededCheckFinished(boolean isUpgraded, WebappInfo newInfo);
    }

    private static final String TAG = "cr_UpgradeDetector";

    /**
     * Fetched Web Manifest data.
     */
    private static class FetchedManifestData {
        public String startUrl;
        public String scopeUrl;
        public String name;
        public String shortName;
        public String iconUrl;

        // Hash of untransformed icon bytes. The hash should have been taken prior to any
        // encoding/decoding.
        public long iconMurmur2Hash;

        public Bitmap icon;
        public int displayMode;
        public int orientation;
        public long themeColor;
        public long backgroundColor;
    }

    /** The WebAPK's tab. */
    private final Tab mTab;

    /**
     * Web Manifest data at time that the WebAPK was generated.
     */
    private WebappInfo mWebappInfo;
    private String mManifestUrl;
    private String mStartUrl;
    private String mIconUrl;
    private long mIconMurmur2Hash;

    /**
     * Fetches the WebAPK's Web Manifest from the web.
     */
    private ManifestUpgradeDetectorFetcher mFetcher;
    private Callback mCallback;

    /**
     * Gets the long value from a Bundle. The long should be terminated with 'L'.
     * According to https://developer.android.com/guide/topics/manifest/meta-data-element.html
     * numeric <meta-data> values can only be retrieved via {@link Bundle#getInt()} and
     * {@link Bundle#getFloat()}. We cannot use {@link Bundle#getFloat()} due to loss of precision.
     * Returns 0 if the value cannot be parsed.
     */
    private static long getLongFromBundle(Bundle bundle, String key) {
        String value = bundle.getString(key);
        if (value == null || !value.endsWith("L")) {
            return 0;
        }
        try {
            return Long.parseLong(value.substring(0, value.length() - 1));
        } catch (NumberFormatException e) {
        }
        return 0;
    }

    public ManifestUpgradeDetector(Tab tab, WebappInfo info, Callback callback) {
        mTab = tab;
        mWebappInfo = info;
        mCallback = callback;
        getMetaDataFromAndroidManifest();
    }

    public String getManifestUrl() {
        return mManifestUrl;
    }

    /**
     * Starts fetching the web manifest resources.
     */
    public boolean start() {
        if (mFetcher != null) return false;

        if (TextUtils.isEmpty(mManifestUrl)) {
            return false;
        }

        mFetcher = createFetcher(mTab, mWebappInfo.scopeUri().toString(), mManifestUrl);
        mFetcher.start(this);
        return true;
    }

    /**
     * Creates ManifestUpgradeDataFetcher.
     */
    protected ManifestUpgradeDetectorFetcher createFetcher(Tab tab, String scopeUrl,
            String manifestUrl) {
        return new ManifestUpgradeDetectorFetcher(tab, scopeUrl, manifestUrl);
    }

    private void getMetaDataFromAndroidManifest() {
        try {
            ApplicationInfo appInfo =
                    ContextUtils.getApplicationContext().getPackageManager().getApplicationInfo(
                            mWebappInfo.webApkPackageName(), PackageManager.GET_META_DATA);
            Bundle metaData = appInfo.metaData;
            mManifestUrl = metaData.getString(WebApkMetaDataKeys.WEB_MANIFEST_URL);
            mStartUrl = metaData.getString(WebApkMetaDataKeys.START_URL);
            mIconUrl = metaData.getString(WebApkMetaDataKeys.ICON_URL);
            mIconMurmur2Hash = getLongFromBundle(metaData, WebApkMetaDataKeys.ICON_MURMUR2_HASH);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }

    /**
     * Puts the object in a state where it is safe to be destroyed.
     */
    public void destroy() {
        if (mFetcher != null) {
            mFetcher.destroy();
        }
        mFetcher = null;
    }

    /**
     * Called when the updated Web Manifest has been fetched.
     */
    @Override
    public void onGotManifestData(String startUrl, String scopeUrl, String name, String shortName,
            String iconUrl, long iconMurmur2Hash, Bitmap iconBitmap, int displayMode,
            int orientation, long themeColor, long backgroundColor) {
        mFetcher.destroy();
        mFetcher = null;

        if (TextUtils.isEmpty(scopeUrl)) {
            scopeUrl = ShortcutHelper.getScopeFromUrl(startUrl);
        }

        FetchedManifestData fetchedData = new FetchedManifestData();
        fetchedData.startUrl = startUrl;
        fetchedData.scopeUrl = scopeUrl;
        fetchedData.name = name;
        fetchedData.shortName = shortName;
        fetchedData.iconUrl = iconUrl;
        fetchedData.iconMurmur2Hash = iconMurmur2Hash;
        fetchedData.icon = iconBitmap;
        fetchedData.displayMode = displayMode;
        fetchedData.orientation = orientation;
        fetchedData.themeColor = themeColor;
        fetchedData.backgroundColor = backgroundColor;

        // TODO(hanxi): crbug.com/627824. Validate whether the new WebappInfo is
        // WebAPK-compatible.
        boolean upgradeRequired = requireUpgrade(fetchedData);
        WebappInfo newInfo = null;
        if (upgradeRequired) {
            newInfo = WebappInfo.create(mWebappInfo.id(), startUrl, scopeUrl,
                    "", name, shortName, displayMode, orientation,
                    mWebappInfo.source(), themeColor, backgroundColor,
                    mWebappInfo.isIconGenerated(), mWebappInfo.webApkPackageName());
        }
        mCallback.onUpgradeNeededCheckFinished(upgradeRequired, newInfo);
    }

    /**
     * Checks whether the WebAPK needs to be upgraded provided the fetched manifest data.
     */
    private boolean requireUpgrade(FetchedManifestData fetchedData) {
        /**
         * Only check whether icon URL differs if Chrome was able to fetch the bitmap at the icon
         * URL (no 404).
         */
        if (fetchedData.icon != null) {
            if (!TextUtils.equals(mIconUrl, fetchedData.iconUrl)
                    || mIconMurmur2Hash != fetchedData.iconMurmur2Hash) {
                return true;
            }
        }

        boolean scopeMatch = mWebappInfo.scopeUri().toString().equals(fetchedData.scopeUrl);
        if (!scopeMatch) {
            // Sometimes the scope doesn't match due to a missing "/" at the end of the scope URL.
            // Print log to find such cases.
            Log.d(TAG, "Needs to request update since the scope from WebappInfo (%s) doesn't match"
                    + "the one fetched from Web Manifest(%s).", mWebappInfo.scopeUri().toString(),
                    fetchedData.scopeUrl);
            return true;
        }

        if (!TextUtils.equals(mStartUrl, fetchedData.startUrl)
                || !TextUtils.equals(mWebappInfo.shortName(), fetchedData.shortName)
                || !TextUtils.equals(mWebappInfo.name(), fetchedData.name)
                || mWebappInfo.backgroundColor() != fetchedData.backgroundColor
                || mWebappInfo.themeColor() != fetchedData.themeColor
                || mWebappInfo.orientation() != fetchedData.orientation
                || mWebappInfo.displayMode() != fetchedData.displayMode) {
            return true;
        }

        return false;
    }
}

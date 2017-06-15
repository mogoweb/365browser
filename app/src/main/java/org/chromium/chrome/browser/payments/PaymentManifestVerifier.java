// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.payments;

import android.content.pm.PackageInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.Signature;
import android.support.annotation.Nullable;

import org.chromium.base.Log;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.components.payments.PaymentManifestDownloader;
import org.chromium.components.payments.PaymentManifestDownloader.ManifestDownloadCallback;
import org.chromium.components.payments.PaymentManifestParser;
import org.chromium.components.payments.PaymentManifestParser.ManifestParseCallback;
import org.chromium.payments.mojom.WebAppManifestSection;

import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Formatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Verifies that the discovered native Android payment apps have the sufficient privileges
 * to handle a single payment method. Downloads and parses the manifest to compare package
 * names, versions, and signatures to the apps.
 *
 * Spec:
 * https://docs.google.com/document/d/1izV4uC-tiRJG3JLooqY3YRLU22tYOsLTNq0P_InPJeE/edit#heading=h.cjp3jlnl47h5
 */
public class PaymentManifestVerifier
        implements ManifestDownloadCallback, ManifestParseCallback,
                   PaymentManifestWebDataService.PaymentManifestWebDataServiceCallback {
    private static final String TAG = "cr_PaymentManifest";

    /** Interface for the callback to invoke when finished verification. */
    public interface ManifestVerifyCallback {
        /**
         * Enables invoking the given native Android payment app for the given payment method.
         * Called when the app has been found to have the right privileges to handle this payment
         * method.
         *
         * @param methodName  The payment method name that the payment app offers to handle.
         * @param resolveInfo Identifying information for the native Android payment app.
         */
        void onValidPaymentApp(URI methodName, ResolveInfo resolveInfo);

        /**
         * Disables invoking the given native Android payment app for the given payment method.
         * Called when the app has been found to not have the right privileges to handle this
         * payment app.
         *
         * @param methodName  The payment method name that the payment app offers to handle.
         * @param resolveInfo Identifying information for the native Android payment app.
         */
        void onInvalidPaymentApp(URI methodName, ResolveInfo resolveInfo);

        /**
         * Disables invoking any native Android payment app for the given payment method. Called if
         * unable to download or parse the payment method manifest.
         *
         * @param methodName The payment method name that has an invalid payment method manifest.
         */
        void onInvalidManifest(URI methodName);

        /**
         * Called when all the operations are done. After this call, the caller can release
         * resources used by this class.
         *
         * @param verifier The finished verifier.
         */
        void onVerifyFinished(PaymentManifestVerifier verifier);
    }

    /** Identifying information about an installed native Android payment app. */
    private static class AppInfo {
        /** Identifies a native Android payment app. */
        public ResolveInfo resolveInfo;

        /** The version code for the native Android payment app, e.g., 123. */
        public long version;

        /**
         * The SHA256 certificate fingerprints for the native Android payment app, .e.g,
         * ["308201dd30820146020101300d06092a864886f70d010105050030"].
         */
        public Set<String> sha256CertFingerprints;
    }

    private final PaymentManifestDownloader mDownloader;
    private final URI mMethodName;

    /** A mapping from the package name to the application that matches the method name.  */
    private final Map<String, AppInfo> mMatchingApps;

    /** A list of package names of the apps that have been verified by using the cached manifest. */
    private final List<String> mVerifiedAppPackageNamesByCachedManifest;

    /** A set of package names of the apps which support the payment method. */
    private final Set<String> mSupportedAppPackageNames;

    /** A list of manifests of the apps which support the payment method. */
    private final List<WebAppManifestSection[]> mSupportedAppParsedManifests;

    private final PaymentManifestWebDataService mWebDataService;
    private final PaymentManifestParser mParser;
    private final PackageManagerDelegate mPackageManagerDelegate;
    private final ManifestVerifyCallback mCallback;
    private final MessageDigest mMessageDigest;
    private int mPendingWebAppManifestsCount;
    private boolean mAtLeastOneManifestFailedToDownloadOrParse;

    /** Whether the manifest cache is stale (unusable). */
    private boolean mIsManifestCacheStaleOrUnusable;

    /**
     * Builds the manifest verifier.
     *
     * @param methodName             The name of the payment method name that apps offer to handle.
     *                               Must be an absolute URI with HTTPS scheme.
     * @param matchingApps           The identifying information for the native Android payment apps
     *                               that offer to handle this payment method.
     * @param webDataService         The web data service to cache manifest.
     * @param downloader             The manifest downloader.
     * @param parser                 The manifest parser.
     * @param packageManagerDelegate The package information retriever.
     * @param callback               The callback to be notified of verification result.
     */
    public PaymentManifestVerifier(URI methodName, List<ResolveInfo> matchingApps,
            PaymentManifestWebDataService webDataService, PaymentManifestDownloader downloader,
            PaymentManifestParser parser, PackageManagerDelegate packageManagerDelegate,
            ManifestVerifyCallback callback) {
        assert methodName.isAbsolute();
        assert UrlConstants.HTTPS_SCHEME.equals(methodName.getScheme());
        assert !matchingApps.isEmpty();

        mMethodName = methodName;
        mMatchingApps = new HashMap<>();
        for (int i = 0; i < matchingApps.size(); i++) {
            AppInfo appInfo = new AppInfo();
            appInfo.resolveInfo = matchingApps.get(i);
            mMatchingApps.put(appInfo.resolveInfo.activityInfo.packageName, appInfo);
        }

        mDownloader = downloader;
        mWebDataService = webDataService;
        mParser = parser;
        mPackageManagerDelegate = packageManagerDelegate;
        mCallback = callback;

        MessageDigest md = null;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // Intentionally ignore.
            Log.d(TAG, "Unable to generate SHA-256 hashes.");
        }
        mMessageDigest = md;

        mVerifiedAppPackageNamesByCachedManifest = new ArrayList<>();
        mSupportedAppPackageNames = new HashSet<>();
        mSupportedAppParsedManifests = new ArrayList<>();
    }

    /**
     * Verifies that the discovered native Android payment apps have the sufficient
     * privileges to handle this payment method.
     */
    public void verify() {
        if (mMessageDigest == null) {
            mCallback.onInvalidManifest(mMethodName);
            mCallback.onVerifyFinished(this);
            return;
        }

        List<String> invalidAppsToRemove = new ArrayList<>();
        for (Map.Entry<String, AppInfo> entry : mMatchingApps.entrySet()) {
            String packageName = entry.getKey();
            AppInfo appInfo = entry.getValue();

            PackageInfo packageInfo =
                    mPackageManagerDelegate.getPackageInfoWithSignatures(packageName);
            if (packageInfo == null) {
                mCallback.onInvalidPaymentApp(mMethodName, appInfo.resolveInfo);
                invalidAppsToRemove.add(packageName);
                continue;
            }

            appInfo.version = packageInfo.versionCode;
            appInfo.sha256CertFingerprints = new HashSet<>();
            Signature[] signatures = packageInfo.signatures;
            for (int i = 0; i < signatures.length; i++) {
                mMessageDigest.update(signatures[i].toByteArray());

                // The digest is reset after completing the hash computation.
                appInfo.sha256CertFingerprints.add(byteArrayToString(mMessageDigest.digest()));
            }
        }

        for (int i = 0; i < invalidAppsToRemove.size(); i++) {
            mMatchingApps.remove(invalidAppsToRemove.get(i));
        }

        if (mMatchingApps.isEmpty()) {
            mCallback.onInvalidManifest(mMethodName);
            mCallback.onVerifyFinished(this);
            return;
        }

        // Try to fetch manifest from the cache first.
        if (!mWebDataService.getPaymentMethodManifest(mMethodName.toString(), this)) {
            mIsManifestCacheStaleOrUnusable = true;
            mDownloader.downloadPaymentMethodManifest(mMethodName, this);
        }
    }

    /**
     * Formats bytes into a string for easier comparison as a member of a set.
     *
     * @param input Input bytes.
     * @return A string representation of the input bytes, e.g., "0123456789abcdef".
     */
    private static String byteArrayToString(byte[] input) {
        if (input == null) return null;

        StringBuilder builder = new StringBuilder(input.length * 2);
        Formatter formatter = new Formatter(builder);
        for (byte b : input) {
            formatter.format("%02x", b);
        }

        String result = builder.toString();
        formatter.close();
        return result;
    }

    @Override
    public void onPaymentMethodManifestFetched(String[] appPackageNames) {
        Set<String> fetchedApps = new HashSet<>(Arrays.asList(appPackageNames));
        Set<String> matchingApps = mMatchingApps.keySet();
        // The cache may be stale if it doesn't contain all matching apps, so switch to download the
        // manifest online immediately.
        if (!fetchedApps.containsAll(matchingApps)) {
            mIsManifestCacheStaleOrUnusable = true;
            mDownloader.downloadPaymentMethodManifest(mMethodName, this);
            return;
        }

        mPendingWebAppManifestsCount = matchingApps.size();
        for (int i = 0; i < appPackageNames.length; i++) {
            if (!mWebDataService.getPaymentWebAppManifest(appPackageNames[i], this)) {
                mIsManifestCacheStaleOrUnusable = true;
                mPendingWebAppManifestsCount = 0;
                mDownloader.downloadPaymentMethodManifest(mMethodName, this);
                return;
            }
        }
    }

    @Override
    public void onPaymentWebAppManifestFetched(WebAppManifestSection[] manifest) {
        if (mIsManifestCacheStaleOrUnusable) return;

        if (manifest == null || manifest.length == 0) {
            mIsManifestCacheStaleOrUnusable = true;
            mPendingWebAppManifestsCount = 0;
            mDownloader.downloadPaymentMethodManifest(mMethodName, this);
            return;
        }

        String verifiedAppPackageName = verifyAppWithWebAppManifest(manifest);
        if (verifiedAppPackageName != null) {
            // Do not notify onValidPaymentApp immediately in case of fetching the other web app's
            // manifest failed. Switch to download manifest online in that case immediately.
            mVerifiedAppPackageNamesByCachedManifest.add(verifiedAppPackageName);
        }

        mPendingWebAppManifestsCount--;
        if (mPendingWebAppManifestsCount != 0) return;

        for (int i = 0; i < mVerifiedAppPackageNamesByCachedManifest.size(); i++) {
            String appPackageName = mVerifiedAppPackageNamesByCachedManifest.get(i);
            mCallback.onValidPaymentApp(mMethodName, mMatchingApps.get(appPackageName).resolveInfo);
            mMatchingApps.remove(appPackageName);
        }

        for (Map.Entry<String, AppInfo> entry : mMatchingApps.entrySet()) {
            mCallback.onInvalidPaymentApp(mMethodName, entry.getValue().resolveInfo);
        }

        // Download and parse manifest to refresh cache.
        mDownloader.downloadPaymentMethodManifest(mMethodName, this);
    }

    @Override
    public void onPaymentMethodManifestDownloadSuccess(String content) {
        mParser.parsePaymentMethodManifest(content, this);
    }

    @Override
    public void onPaymentMethodManifestParseSuccess(URI[] webAppManifestUris) {
        assert webAppManifestUris != null;
        assert webAppManifestUris.length > 0;
        assert !mAtLeastOneManifestFailedToDownloadOrParse;
        assert mPendingWebAppManifestsCount == 0;

        mPendingWebAppManifestsCount = webAppManifestUris.length;
        for (int i = 0; i < webAppManifestUris.length; i++) {
            if (mAtLeastOneManifestFailedToDownloadOrParse) return;
            assert webAppManifestUris[i] != null;
            mDownloader.downloadWebAppManifest(webAppManifestUris[i], this);
        }
    }

    @Override
    public void onWebAppManifestDownloadSuccess(String content) {
        if (mAtLeastOneManifestFailedToDownloadOrParse) return;
        mParser.parseWebAppManifest(content, this);
    }

    @Override
    public void onWebAppManifestParseSuccess(WebAppManifestSection[] manifest) {
        assert manifest != null;
        assert manifest.length > 0;

        if (mAtLeastOneManifestFailedToDownloadOrParse) return;

        for (int i = 0; i < manifest.length; i++) {
            mSupportedAppPackageNames.add(manifest[i].id);
        }
        mSupportedAppParsedManifests.add(manifest);

        // Do not verify payment app if it has already been verified by cached manifest.
        if (mIsManifestCacheStaleOrUnusable) {
            String verifiedAppPackageName = verifyAppWithWebAppManifest(manifest);
            if (verifiedAppPackageName != null) {
                mCallback.onValidPaymentApp(
                        mMethodName, mMatchingApps.get(verifiedAppPackageName).resolveInfo);
                mMatchingApps.remove(verifiedAppPackageName);
            }
        }

        mPendingWebAppManifestsCount--;
        if (mPendingWebAppManifestsCount != 0) return;

        // Do not notify onInvalidPaymentApp if it has already be notified when checking by cached
        // manifest.
        if (mIsManifestCacheStaleOrUnusable) {
            for (Map.Entry<String, AppInfo> entry : mMatchingApps.entrySet()) {
                mCallback.onInvalidPaymentApp(mMethodName, entry.getValue().resolveInfo);
            }
        }

        // Cache supported apps' package names.
        mWebDataService.addPaymentMethodManifest(mMethodName.toString(),
                mSupportedAppPackageNames.toArray(new String[mSupportedAppPackageNames.size()]));

        // Cache supported apps' parsed manifests.
        for (int i = 0; i < mSupportedAppParsedManifests.size(); i++) {
            mWebDataService.addPaymentWebAppManifest(mSupportedAppParsedManifests.get(i));
        }

        mCallback.onVerifyFinished(this);
    }

    @Nullable
    private String verifyAppWithWebAppManifest(WebAppManifestSection[] manifest) {
        List<Set<String>> sectionsFingerprints = new ArrayList<>();
        for (int i = 0; i < manifest.length; i++) {
            WebAppManifestSection section = manifest[i];
            Set<String> fingerprints = new HashSet<>();
            for (int j = 0; j < section.fingerprints.length; j++) {
                fingerprints.add(byteArrayToString(section.fingerprints[j]));
            }
            sectionsFingerprints.add(fingerprints);
        }

        for (int i = 0; i < manifest.length; i++) {
            WebAppManifestSection section = manifest[i];
            AppInfo appInfo = mMatchingApps.get(section.id);
            if (appInfo != null && appInfo.version >= section.minVersion
                    && appInfo.sha256CertFingerprints != null
                    && appInfo.sha256CertFingerprints.equals(sectionsFingerprints.get(i))) {
                return section.id;
            }
        }

        return null;
    }

    @Override
    public void onManifestDownloadFailure() {
        if (mAtLeastOneManifestFailedToDownloadOrParse) return;
        mAtLeastOneManifestFailedToDownloadOrParse = true;

        if (mIsManifestCacheStaleOrUnusable) mCallback.onInvalidManifest(mMethodName);
        mCallback.onVerifyFinished(this);
    }

    @Override
    public void onManifestParseFailure() {
        if (mAtLeastOneManifestFailedToDownloadOrParse) return;
        mAtLeastOneManifestFailedToDownloadOrParse = true;

        if (mIsManifestCacheStaleOrUnusable) mCallback.onInvalidManifest(mMethodName);
        mCallback.onVerifyFinished(this);
    }
}

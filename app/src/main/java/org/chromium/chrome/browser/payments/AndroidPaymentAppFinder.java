// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.payments;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.text.TextUtils;

import org.chromium.base.Log;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.chrome.browser.payments.PaymentAppFactory.PaymentAppCreatedCallback;
import org.chromium.chrome.browser.payments.PaymentManifestVerifier.ManifestVerifyCallback;
import org.chromium.components.payments.PaymentManifestDownloader;
import org.chromium.components.payments.PaymentManifestParser;
import org.chromium.content_public.browser.WebContents;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Finds installed native Android payment apps and verifies their signatures according to the
 * payment method manifests. The manifests are located based on the payment method name, which is a
 * URI that starts with "https://". The W3C-published non-URI payment method names are exceptions:
 * these are common payment method names that do not have a manifest and can be used by any payment
 * app.
 */
public class AndroidPaymentAppFinder implements ManifestVerifyCallback {
    private static final String TAG = "cr_PaymentAppFinder";

    /** The maximum number of payment method manifests to download. */
    private static final int MAX_NUMBER_OF_MANIFESTS = 10;

    /** The name of the intent for the service to check whether an app is ready to pay. */
    /* package */ static final String ACTION_IS_READY_TO_PAY =
                          "org.chromium.intent.action.IS_READY_TO_PAY";

    /**
     * Meta data name of an app's supported payment method names.
     */
    /* package */ static final String META_DATA_NAME_OF_PAYMENT_METHOD_NAMES =
            "org.chromium.payment_method_names";

    /**
     * Meta data name of an app's supported default payment method name.
     */
    /* package */ static final String META_DATA_NAME_OF_DEFAULT_PAYMENT_METHOD_NAME =
            "org.chromium.default_payment_method_name";

    private final WebContents mWebContents;
    private final Set<String> mNonUriPaymentMethods;
    private final Set<URI> mUriPaymentMethods;
    private final PaymentManifestDownloader mDownloader;
    private final PaymentManifestWebDataService mWebDataService;
    private final PaymentManifestParser mParser;
    private final PackageManagerDelegate mPackageManagerDelegate;
    private final PaymentAppCreatedCallback mCallback;
    private final boolean mIsIncognito;

    /**
     * A map of payment method names to the list of (yet) unverified Android apps that claim to
     * handle these methods. Example payment method names in this data structure:
     * "https://bobpay.com", "https://android.com/pay". Items in the supportedNonUriPaymentMethods
     * are excluded.
     */
    private final Map<URI, Set<ResolveInfo>> mPendingApps;

    /**
     * List of payment manifest verifiers. Note that each non basic card payment method has a
     * dedicated payment manifest verifier.
     */
    private final List<PaymentManifestVerifier> mManifestVerifiers;

    /** A map of Android package name to the payment app. */
    private final Map<String, AndroidPaymentApp> mResult;

    /**
     * Finds native Android payment apps.
     *
     * @param webContents            The web contents that invoked the web payments API.
     * @param methods                The list of payment methods requested by the merchant. For
     *                               example, "https://bobpay.com", "https://android.com/pay",
     *                               "basic-card".
     * @param webDataService         The web data service to cache manifest.
     * @param downloader             The manifest downloader.
     * @param parser                 The manifest parser.
     * @param packageManagerDelegate The package information retriever.
     * @param callback               The asynchronous callback to be invoked (on the UI thread) when
     *                               all Android payment apps have been found.
     */
    public static void find(WebContents webContents, Set<String> methods,
            PaymentManifestWebDataService webDataService, PaymentManifestDownloader downloader,
            PaymentManifestParser parser, PackageManagerDelegate packageManagerDelegate,
            PaymentAppCreatedCallback callback) {
        new AndroidPaymentAppFinder(webContents, methods, webDataService, downloader, parser,
                packageManagerDelegate, callback)
                .findAndroidPaymentApps();
    }

    private AndroidPaymentAppFinder(WebContents webContents, Set<String> methods,
            PaymentManifestWebDataService webDataService, PaymentManifestDownloader downloader,
            PaymentManifestParser parser, PackageManagerDelegate packageManagerDelegate,
            PaymentAppCreatedCallback callback) {
        mWebContents = webContents;

        // For non-URI payment method names, only names published by W3C should be supported.
        Set<String> supportedNonUriPaymentMethods = new HashSet<>();
        // https://w3c.github.io/webpayments-methods-card/
        supportedNonUriPaymentMethods.add("basic-card");
        // https://w3c.github.io/webpayments/proposals/interledger-payment-method.html
        supportedNonUriPaymentMethods.add("interledger");

        mNonUriPaymentMethods = new HashSet<>();
        mUriPaymentMethods = new HashSet<>();
        for (String method : methods) {
            assert !TextUtils.isEmpty(method);
            if (supportedNonUriPaymentMethods.contains(method)) {
                mNonUriPaymentMethods.add(method);
            } else if (method.startsWith(UrlConstants.HTTPS_URL_PREFIX)) {
                URI uri;
                try {
                    // Don't use java.net.URL, because it performs a synchronous DNS lookup in
                    // the constructor.
                    uri = new URI(method);
                } catch (URISyntaxException e) {
                    continue;
                }

                if (uri.isAbsolute()) {
                    assert UrlConstants.HTTPS_SCHEME.equals(uri.getScheme());
                    mUriPaymentMethods.add(uri);
                }
            }
        }

        mDownloader = downloader;
        mWebDataService = webDataService;
        mParser = parser;
        mPackageManagerDelegate = packageManagerDelegate;
        mCallback = callback;
        mPendingApps = new HashMap<>();
        mManifestVerifiers = new ArrayList<>();
        mResult = new HashMap<>();
        ChromeActivity activity = ChromeActivity.fromWebContents(mWebContents);
        mIsIncognito = activity != null && activity.getCurrentTabModel() != null
                && activity.getCurrentTabModel().isIncognito();
    }

    private void findAndroidPaymentApps() {
        Intent payIntent = new Intent(AndroidPaymentApp.ACTION_PAY);
        List<ResolveInfo> apps =
                mPackageManagerDelegate.getActivitiesThatCanRespondToIntentWithMetaData(payIntent);
        if (apps.isEmpty()) {
            onSearchFinished();
            return;
        }

        List<Set<String>> appSupportedMethods = new ArrayList<>();
        for (int i = 0; i < apps.size(); i++) {
            appSupportedMethods.add(getPaymentMethodNames(apps.get(i).activityInfo));
        }

        for (URI uriMethodName : mUriPaymentMethods) {
            List<ResolveInfo> supportedApps =
                    filterAppsByMethodName(apps, appSupportedMethods, uriMethodName.toString());
            if (supportedApps.isEmpty()) continue;

            // Start the parser utility process as soon as possible, once we know that a
            // manifest file needs to be parsed. The startup can take up to 2 seconds.
            if (!mParser.isUtilityProcessRunning()) mParser.startUtilityProcess();

            mManifestVerifiers.add(
                    new PaymentManifestVerifier(uriMethodName, supportedApps, mWebDataService,
                            mDownloader, mParser, mPackageManagerDelegate, this /* callback */));
            mPendingApps.put(uriMethodName, new HashSet<>(supportedApps));

            if (mManifestVerifiers.size() == MAX_NUMBER_OF_MANIFESTS) {
                Log.d(TAG, "Reached maximum number of allowed payment app manifests.");
                break;
            }
        }

        for (String nonUriMethodName : mNonUriPaymentMethods) {
            List<ResolveInfo> supportedApps =
                    filterAppsByMethodName(apps, appSupportedMethods, nonUriMethodName);
            for (int i = 0; i < supportedApps.size(); i++) {
                // Chrome does not verify app manifests for non-URI payment method support.
                onValidPaymentApp(nonUriMethodName, supportedApps.get(i));
            }
        }

        if (mManifestVerifiers.isEmpty()) {
            onSearchFinished();
            return;
        }

        for (int i = 0; i < mManifestVerifiers.size(); i++) {
            mManifestVerifiers.get(i).verify();
        }
    }

    @Nullable
    private Set<String> getPaymentMethodNames(ActivityInfo activityInfo) {
        Set<String> result = new HashSet<>();
        if (activityInfo.metaData == null) return result;

        String defaultMethodName =
                activityInfo.metaData.getString(META_DATA_NAME_OF_DEFAULT_PAYMENT_METHOD_NAME);
        if (!TextUtils.isEmpty(defaultMethodName)) result.add(defaultMethodName);

        int resId = activityInfo.metaData.getInt(META_DATA_NAME_OF_PAYMENT_METHOD_NAMES);
        if (resId == 0) return result;

        Resources resources =
                mPackageManagerDelegate.getResourcesForApplication(activityInfo.applicationInfo);
        if (resources == null) return result;

        String[] methodNames = resources.getStringArray(resId);
        if (methodNames == null) return result;

        for (int i = 0; i < methodNames.length; i++) {
            result.add(methodNames[i]);
        }

        return result;
    }

    private static List<ResolveInfo> filterAppsByMethodName(
            List<ResolveInfo> apps, List<Set<String>> methodNames, String targetMethodName) {
        assert apps.size() == methodNames.size();

        // Note that apps and  methodNames must have the same size. The information at the same
        // index must correspond to the same app.
        List<ResolveInfo> supportedApps = new ArrayList<>();
        for (int i = 0; i < apps.size(); i++) {
            if (methodNames.get(i).contains(targetMethodName)) {
                supportedApps.add(apps.get(i));
                continue;
            }
        }
        return supportedApps;
    }

    @Override
    public void onValidPaymentApp(URI methodName, ResolveInfo resolveInfo) {
        onValidPaymentApp(methodName.toString(), resolveInfo);
        removePendingApp(methodName, resolveInfo);
    }

    /** Same as above, but also works for non-URI method names, e.g., "basic-card". */
    private void onValidPaymentApp(String methodName, ResolveInfo resolveInfo) {
        String packageName = resolveInfo.activityInfo.packageName;
        AndroidPaymentApp app = mResult.get(packageName);
        if (app == null) {
            CharSequence label = mPackageManagerDelegate.getAppLabel(resolveInfo);
            if (TextUtils.isEmpty(label)) {
                Log.d(TAG,
                        String.format(Locale.getDefault(), "Skipping '%s' because of empty label.",
                                packageName));
                return;
            }
            app = new AndroidPaymentApp(mWebContents, packageName, resolveInfo.activityInfo.name,
                    label.toString(), mPackageManagerDelegate.getAppIcon(resolveInfo),
                    mIsIncognito);
            mResult.put(packageName, app);
        }
        app.addMethodName(methodName);
    }

    @Override
    public void onInvalidPaymentApp(URI methodName, ResolveInfo resolveInfo) {
        removePendingApp(methodName, resolveInfo);
    }

    /** Removes the (method, app) pair from the list of pending information to be verified. */
    private void removePendingApp(URI methodName, ResolveInfo resolveInfo) {
        Set<ResolveInfo> pendingAppsForMethod = mPendingApps.get(methodName);
        pendingAppsForMethod.remove(resolveInfo);
        if (pendingAppsForMethod.isEmpty()) mPendingApps.remove(methodName);
        if (mPendingApps.isEmpty()) onSearchFinished();
    }

    @Override
    public void onInvalidManifest(URI methodName) {
        mPendingApps.remove(methodName);
        if (mPendingApps.isEmpty()) onSearchFinished();
    }

    @Override
    public void onVerifyFinished(PaymentManifestVerifier verifier) {
        mManifestVerifiers.remove(verifier);
        if (!mManifestVerifiers.isEmpty()) return;

        assert mPendingApps.isEmpty();
        mWebDataService.destroy();
        if (mParser.isUtilityProcessRunning()) mParser.stopUtilityProcess();
    }

    /**
     * Checks for IS_READY_TO_PAY service in each valid payment app and returns the valid apps
     * to the caller. Called when finished verifying all payment methods and apps.
     */
    private void onSearchFinished() {
        assert mPendingApps.isEmpty();

        if (!mIsIncognito) {
            List<ResolveInfo> resolveInfos =
                    mPackageManagerDelegate.getServicesThatCanRespondToIntent(
                            new Intent(ACTION_IS_READY_TO_PAY));
            for (int i = 0; i < resolveInfos.size(); i++) {
                ResolveInfo resolveInfo = resolveInfos.get(i);
                AndroidPaymentApp app = mResult.get(resolveInfo.serviceInfo.packageName);
                if (app != null) app.setIsReadyToPayAction(resolveInfo.serviceInfo.name);
            }
        }

        for (Map.Entry<String, AndroidPaymentApp> entry : mResult.entrySet()) {
            mCallback.onPaymentAppCreated(entry.getValue());
        }

        mCallback.onAllPaymentAppsCreated();
    }
}

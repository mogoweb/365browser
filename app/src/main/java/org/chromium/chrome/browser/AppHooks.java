// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.app.Notification;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import org.chromium.base.Callback;
import org.chromium.base.ContextUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.browser.banners.AppDetailsDelegate;
import org.chromium.chrome.browser.customtabs.CustomTabsConnection;
import org.chromium.chrome.browser.datausage.ExternalDataUseObserver;
import org.chromium.chrome.browser.externalauth.ExternalAuthUtils;
import org.chromium.chrome.browser.feedback.EmptyFeedbackReporter;
import org.chromium.chrome.browser.feedback.FeedbackReporter;
import org.chromium.chrome.browser.gsa.GSAHelper;
import org.chromium.chrome.browser.help.HelpAndFeedback;
import org.chromium.chrome.browser.historyreport.AppIndexingReporter;
import org.chromium.chrome.browser.init.ProcessInitializationHandler;
import org.chromium.chrome.browser.instantapps.InstantAppsHandler;
import org.chromium.chrome.browser.locale.LocaleManager;
import org.chromium.chrome.browser.media.VideoPersister;
import org.chromium.chrome.browser.metrics.VariationsSession;
import org.chromium.chrome.browser.multiwindow.MultiWindowUtils;
import org.chromium.chrome.browser.net.qualityprovider.ExternalEstimateProviderAndroid;
import org.chromium.chrome.browser.offlinepages.CCTRequestStatus;
import org.chromium.chrome.browser.omaha.RequestGenerator;
import org.chromium.chrome.browser.physicalweb.PhysicalWebBleClient;
import org.chromium.chrome.browser.policy.PolicyAuditor;
import org.chromium.chrome.browser.preferences.LocationSettings;
import org.chromium.chrome.browser.rlz.RevenueStats;
import org.chromium.chrome.browser.services.AndroidEduOwnerCheckCallback;
import org.chromium.chrome.browser.signin.GoogleActivityController;
import org.chromium.chrome.browser.sync.GmsCoreSyncListener;
import org.chromium.chrome.browser.tab.AuthenticatorNavigationInterceptor;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.webapps.GooglePlayWebApkInstallDelegate;
import org.chromium.components.signin.AccountManagerDelegate;
import org.chromium.components.signin.SystemAccountManagerDelegate;
import org.chromium.policy.AppRestrictionsProvider;
import org.chromium.policy.CombinedPolicyProvider;

/**
 * Base class for defining methods where different behavior is required by downstream targets.
 * The correct version of {@link AppHooksImpl} will be determined at compile time via build rules.
 * See http://crbug/560466.
 */
public abstract class AppHooks {
    private static AppHooksImpl sInstance;

    /**
     * Sets a mocked instance for testing.
     */
    @VisibleForTesting
    public static void setInstanceForTesting(AppHooksImpl instance) {
        sInstance = instance;
    }

    @CalledByNative
    public static AppHooks get() {
        if (sInstance == null) {
            sInstance = new AppHooksImpl();
        }
        return sInstance;
    }

    /**
     * Initiate AndroidEdu device check.
     * @param callback Callback that should receive the results of the AndroidEdu device check.
     */
    public void checkIsAndroidEduDevice(final AndroidEduOwnerCheckCallback callback) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                callback.onSchoolCheckDone(false);
            }
        });
    }

    /**
     * Creates a new {@link AccountManagerDelegate}.
     * @return the created {@link AccountManagerDelegate}.
     */
    public AccountManagerDelegate createAccountManagerDelegate() {
        return new SystemAccountManagerDelegate();
    }

    /**
     * @return An instance of AppDetailsDelegate that can be queried about app information for the
     *         App Banner feature.  Will be null if one is unavailable.
     */
    public AppDetailsDelegate createAppDetailsDelegate() {
        return null;
    }

    /**
     * Creates a new {@link AppIndexingReporter}.
     * @return the created {@link AppIndexingReporter}.
     */
    public AppIndexingReporter createAppIndexingReporter() {
        return new AppIndexingReporter();
    }

    /**
     * Return a {@link AuthenticatorNavigationInterceptor} for the given {@link Tab}.
     * This can be null if there are no applicable interceptor to be built.
     */
    public AuthenticatorNavigationInterceptor createAuthenticatorNavigationInterceptor(Tab tab) {
        return null;
    }

    /**
     * @return An instance of {@link CustomTabsConnection}. Should not be called
     * outside of {@link CustomTabsConnection#getInstance()}.
     */
    public CustomTabsConnection createCustomTabsConnection() {
        return new CustomTabsConnection(((ChromeApplication) ContextUtils.getApplicationContext()));
    }

    /**
     * @return An instance of ExternalAuthUtils to be installed as a singleton.
     */
    public ExternalAuthUtils createExternalAuthUtils() {
        return new ExternalAuthUtils();
    }

    /**
     * @return An external observer of data use.
     * @param nativePtr Pointer to the native ExternalDataUseObserver object.
     */
    public ExternalDataUseObserver createExternalDataUseObserver(long nativePtr) {
        return new ExternalDataUseObserver(nativePtr);
    }

    /**
     * @return A provider of external estimates.
     * @param nativePtr Pointer to the native ExternalEstimateProviderAndroid object.
     */
    public ExternalEstimateProviderAndroid createExternalEstimateProviderAndroid(long nativePtr) {
        return new ExternalEstimateProviderAndroid(nativePtr) {};
    }

    /**
     * @return An instance of {@link FeedbackReporter} to report feedback.
     */
    public FeedbackReporter createFeedbackReporter() {
        return new EmptyFeedbackReporter();
    }

    /**
     * @return An instance of GmsCoreSyncListener to notify GmsCore of sync encryption key changes.
     *         Will be null if one is unavailable.
     */
    public GmsCoreSyncListener createGmsCoreSyncListener() {
        return null;
    }

    /**
     * @return An instance of GoogleActivityController.
     */
    public GoogleActivityController createGoogleActivityController() {
        return new GoogleActivityController();
    }

    /**
     * @return An instance of {@link GSAHelper} that handles the start point of chrome's integration
     *         with GSA.
     */
    public GSAHelper createGsaHelper() {
        return new GSAHelper();
    }

    /**
     * Returns a new instance of HelpAndFeedback.
     */
    public HelpAndFeedback createHelpAndFeedback() {
        return new HelpAndFeedback();
    }

    public InstantAppsHandler createInstantAppsHandler() {
        return new InstantAppsHandler();
    }

    /**
     * @return An instance of {@link LocaleManager} that handles customized locale related logic.
     */
    public LocaleManager createLocaleManager() {
        return new LocaleManager();
    }

    /**
     * Returns an instance of LocationSettings to be installed as a singleton.
     */
    public LocationSettings createLocationSettings() {
        // Using an anonymous subclass as the constructor is protected.
        // This is done to deter instantiation of LocationSettings elsewhere without using the
        // getInstance() helper method.
        return new LocationSettings() {};
    }

    /**
     * @return An instance of MultiWindowUtils to be installed as a singleton.
     */
    public MultiWindowUtils createMultiWindowUtils() {
        return new MultiWindowUtils();
    }

    /**
     * @return An instance of RequestGenerator to be used for Omaha XML creation.  Will be null if
     *         a generator is unavailable.
     */
    public RequestGenerator createOmahaRequestGenerator() {
        return null;
    }

    /**
     * @return A new {@link PhysicalWebBleClient} instance.
     */
    public PhysicalWebBleClient createPhysicalWebBleClient() {
        return new PhysicalWebBleClient();
    }

    /**
     * @return a new {@link ProcessInitializationHandler} instance.
     */
    public ProcessInitializationHandler createProcessInitializationHandler() {
        return new ProcessInitializationHandler();
    }

    /**
     * @return An instance of RevenueStats to be installed as a singleton.
     */
    public RevenueStats createRevenueStatsInstance() {
        return new RevenueStats();
    }

    /**
     * Returns a new instance of VariationsSession.
     */
    public VariationsSession createVariationsSession() {
        return new VariationsSession();
    }

    /**
     * @return An instance of VideoPersister to be installed as a singleton.
     */
    public VideoPersister createVideoPersister() {
        return new VideoPersister();
    }

    /** Returns the singleton instance of GooglePlayWebApkInstallDelegate. */
    public GooglePlayWebApkInstallDelegate getGooglePlayWebApkInstallDelegate() {
        return null;
    }

    /**
     * @return An instance of PolicyAuditor that notifies the policy system of the user's activity.
     * Only applicable when the user has a policy active, that is tracking the activity.
     */
    public PolicyAuditor getPolicyAuditor() {
        // This class has a protected constructor to prevent accidental instantiation.
        return new PolicyAuditor() {};
    }

    public void registerPolicyProviders(CombinedPolicyProvider combinedProvider) {
        combinedProvider.registerProvider(
                new AppRestrictionsProvider(ContextUtils.getApplicationContext()));
    }

    /**
     * Starts a service from {@code intent} with the expectation that it will make itself a
     * foreground service with {@link android.app.Service#startForeground(int, Notification)}.
     *
     * @param intent The {@link Intent} to fire to start the service.
     */
    @SuppressWarnings("Unused")
    public void startForegroundService(Intent intent) {
        ContextUtils.getApplicationContext().startService(intent);
    }

    /**
     * @return Whether the renderer should detect whether video elements are in fullscreen. The
     * detection results can be retrieved through
     * {@link WebContents.hasActiveEffectivelyFullscreenVideo()}.
     */
    @CalledByNative
    public boolean shouldDetectVideoFullscreen() {
        return false;
    }

    /**
     * @return A callback that will be run each time an offline page is saved in the custom tabs
     * namespace.
     */
    @CalledByNative
    public Callback<CCTRequestStatus> getOfflinePagesCCTRequestDoneCallback() {
        return null;
    }
}

// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.View;

import com.google.ipc.invalidation.external.client.android.service.AndroidLogger;

import org.chromium.base.ActivityState;
import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.ApplicationState;
import org.chromium.base.ApplicationStatus;
import org.chromium.base.ApplicationStatus.ApplicationStateListener;
import org.chromium.base.CommandLine;
import org.chromium.base.CommandLineInitUtil;
import org.chromium.base.ContextUtils;
import org.chromium.base.FileUtils;
import org.chromium.base.PathUtils;
import org.chromium.base.ResourceExtractor;
import org.chromium.base.ThreadUtils;
import org.chromium.base.TraceEvent;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.base.library_loader.ProcessInitException;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.accessibility.FontSizePrefs;
import org.chromium.chrome.browser.banners.AppBannerManager;
import org.chromium.chrome.browser.banners.AppDetailsDelegate;
import org.chromium.chrome.browser.browsing_data.BrowsingDataType;
import org.chromium.chrome.browser.browsing_data.TimePeriod;
import org.chromium.chrome.browser.customtabs.CustomTabsConnection;
import org.chromium.chrome.browser.datausage.ExternalDataUseObserver;
import org.chromium.chrome.browser.document.DocumentActivity;
import org.chromium.chrome.browser.document.IncognitoDocumentActivity;
import org.chromium.chrome.browser.download.DownloadController;
import org.chromium.chrome.browser.download.DownloadManagerService;
import org.chromium.chrome.browser.externalauth.ExternalAuthUtils;
import org.chromium.chrome.browser.feedback.EmptyFeedbackReporter;
import org.chromium.chrome.browser.feedback.FeedbackReporter;
import org.chromium.chrome.browser.firstrun.ForcedSigninProcessor;
import org.chromium.chrome.browser.gsa.GSAHelper;
import org.chromium.chrome.browser.help.HelpAndFeedback;
import org.chromium.chrome.browser.identity.UniqueIdentificationGeneratorFactory;
import org.chromium.chrome.browser.identity.UuidBasedUniqueIdentificationGenerator;
import org.chromium.chrome.browser.init.InvalidStartupDialog;
import org.chromium.chrome.browser.instantapps.InstantAppsHandler;
import org.chromium.chrome.browser.invalidation.UniqueIdInvalidationClientNameGenerator;
import org.chromium.chrome.browser.locale.LocaleManager;
import org.chromium.chrome.browser.metrics.UmaUtils;
import org.chromium.chrome.browser.metrics.VariationsSession;
import org.chromium.chrome.browser.multiwindow.MultiWindowUtils;
import org.chromium.chrome.browser.net.qualityprovider.ExternalEstimateProviderAndroid;
import org.chromium.chrome.browser.net.spdyproxy.DataReductionProxySettings;
import org.chromium.chrome.browser.notifications.NotificationPlatformBridge;
import org.chromium.chrome.browser.omaha.RequestGenerator;
import org.chromium.chrome.browser.partnercustomizations.PartnerBrowserCustomizations;
import org.chromium.chrome.browser.physicalweb.PhysicalWebBleClient;
import org.chromium.chrome.browser.physicalweb.PhysicalWebEnvironment;
import org.chromium.chrome.browser.policy.PolicyAuditor;
import org.chromium.chrome.browser.preferences.LocationSettings;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.preferences.Preferences;
import org.chromium.chrome.browser.preferences.PreferencesLauncher;
import org.chromium.chrome.browser.preferences.autofill.AutofillPreferences;
import org.chromium.chrome.browser.preferences.password.SavePasswordsPreferences;
import org.chromium.chrome.browser.preferences.privacy.ClearBrowsingDataPreferences;
import org.chromium.chrome.browser.preferences.website.SingleWebsitePreferences;
import org.chromium.chrome.browser.printing.PrintingControllerFactory;
import org.chromium.chrome.browser.rlz.RevenueStats;
import org.chromium.chrome.browser.services.AccountsChangedReceiver;
import org.chromium.chrome.browser.services.AndroidEduOwnerCheckCallback;
import org.chromium.chrome.browser.services.GoogleServicesManager;
import org.chromium.chrome.browser.share.ShareHelper;
import org.chromium.chrome.browser.signin.GoogleActivityController;
import org.chromium.chrome.browser.sync.GmsCoreSyncListener;
import org.chromium.chrome.browser.sync.SyncController;
import org.chromium.chrome.browser.tab.AuthenticatorNavigationInterceptor;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.tabmodel.document.ActivityDelegateImpl;
import org.chromium.chrome.browser.tabmodel.document.DocumentTabModelSelector;
import org.chromium.chrome.browser.tabmodel.document.StorageDelegate;
import org.chromium.chrome.browser.tabmodel.document.TabDelegate;
import org.chromium.chrome.browser.util.FeatureUtilities;
import org.chromium.chrome.browser.util.Logger;
import org.chromium.components.sync.signin.AccountManagerDelegate;
import org.chromium.components.sync.signin.AccountManagerHelper;
import org.chromium.components.sync.signin.SystemAccountManagerDelegate;
import org.chromium.content.app.ContentApplication;
import org.chromium.content.browser.ChildProcessCreationParams;
import org.chromium.content.browser.ChildProcessLauncher;
import org.chromium.content.common.ContentSwitches;
import org.chromium.policy.AppRestrictionsProvider;
import org.chromium.policy.CombinedPolicyProvider;
import org.chromium.policy.CombinedPolicyProvider.PolicyChangeListener;
import org.chromium.printing.PrintingController;
import org.chromium.ui.UiUtils;
import org.chromium.ui.base.ActivityWindowAndroid;
import org.chromium.ui.base.ResourceBundle;
import org.codeaurora.swe.SWECommandLine;

import java.lang.ref.WeakReference;
import java.util.Locale;
import java.io.File;

/**
 * Basic application functionality that should be shared among all browser applications that use
 * chrome layer.
 */
public class ChromeApplication extends ContentApplication {
    public static final String COMMAND_LINE_FILE = "chrome-command-line";

    private static final String TAG = "ChromiumApplication";
    private static final String PREF_BOOT_TIMESTAMP =
            "com.google.android.apps.chrome.ChromeMobileApplication.BOOT_TIMESTAMP";
    private static final long BOOT_TIMESTAMP_MARGIN_MS = 1000;
    private static final String PREF_LOCALE = "locale";
    private static final String PRIVATE_DATA_DIRECTORY_SUFFIX = "chrome";
    private static final String PRIVATE_DATA_DIRECTORY_SUFFIX_SWE = "swe_webview";
    private static final String PRIVATE_DATA_DIRECTORY_PREFIX = "app_";    // TODO: Get programatically

    /**
     * Keep socket name format to be of form "webview_devtools_server_pid"
     * which is required to work with Chrome devtools and Chromedriver.
     * Pass prefix string "webview" to DevToolsServer which will append
     * "_devtools_server_%pid", where %pid is process id of running app.
     */
    private static final String DEV_TOOLS_SERVER_SOCKET_PREFIX = "webview";
    private static final String SESSIONS_UUID_PREF_KEY = "chromium.sync.sessions.id";

    private static boolean sIsFinishedCachingNativeFlags;
    private static DocumentTabModelSelector sDocumentTabModelSelector;

    private final PowerBroadcastReceiver mPowerBroadcastReceiver = new PowerBroadcastReceiver();

    // Used to trigger variation changes (such as seed fetches) upon application foregrounding.
    private VariationsSession mVariationsSession;

    private DevToolsServer mDevToolsServer;

    private boolean mIsStarted;
    private boolean mInitializedSharedClasses;
    private boolean mIsProcessInitialized;

    private ChromeLifetimeController mChromeLifetimeController;
    private PrintingController mPrintingController;

    /**
     * This is called during early initialization in order to set up ChildProcessLauncher
     * for certain Chrome packaging configurations
     */
    public ChildProcessCreationParams getChildProcessCreationParams() {
        return null;
    }

    /**
     * This is called once per ChromeApplication instance, which get created per process
     * (browser OR renderer).  Don't stick anything in here that shouldn't be called multiple times
     * during Chrome's lifetime.
     */
    @Override
    public void onCreate() {
        UmaUtils.recordMainEntryPointTime();
        initCommandLine();
        TraceEvent.maybeEnableEarlyTracing();
        TraceEvent.begin("ChromeApplication.onCreate");

        super.onCreate();
        ContextUtils.initApplicationContext(this);

        UiUtils.setKeyboardShowingDelegate(new UiUtils.KeyboardShowingDelegate() {
            @Override
            public boolean disableKeyboardCheck(Context context, View view) {
                Activity activity = null;
                if (context instanceof Activity) {
                    activity = (Activity) context;
                } else if (view != null && view.getContext() instanceof Activity) {
                    activity = (Activity) view.getContext();
                }

                // For multiwindow mode we do not track keyboard visibility.
                return activity != null
                        && MultiWindowUtils.getInstance().isLegacyMultiWindow(activity);
            }
        });

        // Initialize the AccountManagerHelper with the correct AccountManagerDelegate. Must be done
        // only once and before AccountMangerHelper.get(...) is called to avoid using the
        // default AccountManagerDelegate.
        AccountManagerHelper.initializeAccountManagerHelper(this, createAccountManagerDelegate());

        // Set the unique identification generator for invalidations.  The
        // invalidations system can start and attempt to fetch the client ID
        // very early.  We need this generator to be ready before that happens.
        UniqueIdInvalidationClientNameGenerator.doInitializeAndInstallGenerator(this);

        // Set minimum Tango log level. This sets an in-memory static field, and needs to be
        // set in the ApplicationContext instead of an activity, since Tango can be woken up
        // by the system directly though messages from GCM.
        AndroidLogger.setMinimumAndroidLogLevel(Log.WARN);

        // Set up the identification generator for sync. The ID is actually generated
        // in the SyncController constructor.
        UniqueIdentificationGeneratorFactory.registerGenerator(SyncController.GENERATOR_ID,
                new UuidBasedUniqueIdentificationGenerator(this, SESSIONS_UUID_PREF_KEY), false);
        TraceEvent.end("ChromeApplication.onCreate");
    }

    /**
     * Each top-level activity (ChromeTabbedActivity, FullscreenActivity) should call this during
     * its onStart phase. When called for the first time, this marks the beginning of a foreground
     * session and calls onForegroundSessionStart(). Subsequent calls are noops until
     * onForegroundSessionEnd() is called, to handle changing top-level Chrome activities in one
     * foreground session.
     */
    public void onStartWithNative() {
        if (mIsStarted) return;
        mIsStarted = true;

        assert mIsProcessInitialized;

        onForegroundSessionStart();
        cacheNativeFlags();
    }

    /**
     * Called when a top-level Chrome activity (ChromeTabbedActivity, FullscreenActivity) is
     * started in foreground. It will not be called again when other Chrome activities take over
     * (see onStart()), that is, when correct activity calls startActivity() for another Chrome
     * activity.
     */
    private void onForegroundSessionStart() {
        UmaUtils.recordForegroundStartTime();
        ChildProcessLauncher.onBroughtToForeground();
        updatePasswordEchoState();
        FontSizePrefs.getInstance(this).onSystemFontScaleChanged();
        updateAcceptLanguages();
        mVariationsSession.start(getApplicationContext());
        mPowerBroadcastReceiver.onForegroundSessionStart();

        // Track the ratio of Chrome startups that are caused by notification clicks.
        // TODO(johnme): Add other reasons (and switch to recordEnumeratedHistogram).
        RecordHistogram.recordBooleanHistogram(
                "Startup.BringToForegroundReason",
                NotificationPlatformBridge.wasNotificationRecentlyClicked());
    }

    /**
     * Called when last of Chrome activities is stopped, ending the foreground session. This will
     * not be called when a Chrome activity is stopped because another Chrome activity takes over.
     * This is ensured by ActivityStatus, which switches to track new activity when its started and
     * will not report the old one being stopped (see createStateListener() below).
     */
    private void onForegroundSessionEnd() {
        if (!mIsStarted) return;
        flushPersistentData();
        mIsStarted = false;
        mPowerBroadcastReceiver.onForegroundSessionEnd();

        ChildProcessLauncher.onSentToBackground();
        IntentHandler.clearPendingReferrer();
        IntentHandler.clearPendingIncognitoUrl();

        int totalTabCount = 0;
        for (WeakReference<Activity> reference : ApplicationStatus.getRunningActivities()) {
            Activity activity = reference.get();
            if (activity instanceof ChromeActivity) {
                TabModelSelector tabModelSelector =
                        ((ChromeActivity) activity).getTabModelSelector();
                if (tabModelSelector != null) {
                    totalTabCount += tabModelSelector.getTotalTabCount();
                }
            }
        }
        RecordHistogram.recordCountHistogram(
                "Tab.TotalTabCount.BeforeLeavingApp", totalTabCount);
    }

    /**
     * Called after onForegroundSessionEnd() indicating that the activity whose onStop() ended the
     * last foreground session was destroyed.
     */
    private void onForegroundActivityDestroyed() {
        if (ApplicationStatus.isEveryActivityDestroyed()) {
            // These will all be re-initialized when a new Activity starts / upon next use.
            PartnerBrowserCustomizations.destroy();
            ShareHelper.clearSharedImages(this);
        }
    }

    private ApplicationStateListener createApplicationStateListener() {
        return new ApplicationStateListener() {
            @Override
            public void onApplicationStateChange(int newState) {
                if (newState == ApplicationState.HAS_STOPPED_ACTIVITIES) {
                    onForegroundSessionEnd();
                } else if (newState == ApplicationState.HAS_DESTROYED_ACTIVITIES) {
                    onForegroundActivityDestroyed();
                }
            }
        };
    }

    /**
     * Returns a new instance of VariationsSession.
     */
    public VariationsSession createVariationsSession() {
        return new VariationsSession();
    }

    /**
     * Return a {@link AuthenticatorNavigationInterceptor} for the given {@link Tab}.
     * This can be null if there are no applicable interceptor to be built.
     */
    @SuppressWarnings("unused")
    public AuthenticatorNavigationInterceptor createAuthenticatorNavigationInterceptor(Tab tab) {
        return null;
    }

    /**
     * Starts the application activity tracker.
     */
    protected void startApplicationActivityTracker() {}

    /**
     * Stops the application activity tracker.
     */
    protected void stopApplicationActivityTracker() {}

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

    @CalledByNative
    protected void showAutofillSettings() {
        PreferencesLauncher.launchSettingsPage(this,
                AutofillPreferences.class.getName());
    }

    @CalledByNative
    protected void showPasswordSettings() {
        PreferencesLauncher.launchSettingsPage(this,
                SavePasswordsPreferences.class.getName());
    }

    /**
     * Opens the single origin settings page for the given URL.
     *
     * @param url The URL to show the single origin settings for. This is a complete url
     *            including scheme, domain, port, path, etc.
     */
    protected void showSingleOriginSettings(String url) {
        Bundle fragmentArgs = SingleWebsitePreferences.createFragmentArgsForSite(url);
        Intent intent = PreferencesLauncher.createIntentForSettingsPage(
                this, SingleWebsitePreferences.class.getName());
        intent.putExtra(Preferences.EXTRA_SHOW_FRAGMENT_ARGUMENTS, fragmentArgs);
        startActivity(intent);
    }

    @Override
    protected void initializeLibraryDependencies() {
        // The ResourceExtractor is only needed by the browser process, but this will have no
        // impact on the renderer process construction.
        ResourceBundle.initializeLocalePaks(this, R.array.locale_paks);
        ResourceExtractor.setResourcesToExtract(ResourceBundle.getActiveLocaleResources());

        // Rename data directory from "app_swe_webview" to "app_chrome" on first run after
        // upgrading from m42 to a later version.
        final ApplicationInfo appInfo = getApplicationInfo();
        String dataDir = appInfo.dataDir;

        File chromeDataDirFile = new File(dataDir,
            PRIVATE_DATA_DIRECTORY_PREFIX + PRIVATE_DATA_DIRECTORY_SUFFIX);
        if (!chromeDataDirFile.exists()) {
            File sweDataDirFile = new File(dataDir,
                PRIVATE_DATA_DIRECTORY_PREFIX + PRIVATE_DATA_DIRECTORY_SUFFIX_SWE);
            if (sweDataDirFile.exists()) {
                boolean ret = sweDataDirFile.renameTo(chromeDataDirFile);
                if (!ret) {
                    Log.e(TAG, "M42 dir rename failed");
                    recursivelyDeleteFileAsync(sweDataDirFile);
                }
            }
        }

        PathUtils.setPrivateDataDirectorySuffix(PRIVATE_DATA_DIRECTORY_SUFFIX, this);
    }

     private void recursivelyDeleteFileAsync(File file) {
        new AsyncTask<File, Void, Void>() {
            @Override
            protected Void doInBackground(File... params) {
                FileUtils.recursivelyDeleteFile(params[0]);
                return null;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, file);
    }

    /**
     * The host activity should call this after the native library has loaded to ensure classes
     * shared by Activities in the same process are properly initialized.
     */
    public void initializeSharedClasses() {
        if (mInitializedSharedClasses) return;
        mInitializedSharedClasses = true;

        DeferredStartupHandler.getInstance().addDeferredTask(new Runnable() {
            @Override
            public void run() {
                ForcedSigninProcessor.start(getApplicationContext());
                AccountsChangedReceiver.addObserver(
                        new AccountsChangedReceiver.AccountsChangedObserver() {
                            @Override
                            public void onAccountsChanged(Context context, Intent intent) {
                                ThreadUtils.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        ForcedSigninProcessor.start(getApplicationContext());
                                    }
                                });
                            }
                        });
            }
        });

        DeferredStartupHandler.getInstance().addDeferredTask(new Runnable() {
            @Override
            public void run() {
                GoogleServicesManager.get(getApplicationContext()).onMainActivityStart();
                RevenueStats.getInstance();
            }
        });

        DeferredStartupHandler.getInstance().addDeferredTask(new Runnable() {
            @Override
            public void run() {
                mDevToolsServer = new DevToolsServer(DEV_TOOLS_SERVER_SOCKET_PREFIX);
                mDevToolsServer.setRemoteDebuggingEnabled(
                        true, DevToolsServer.Security.ALLOW_DEBUG_PERMISSION);

                startApplicationActivityTracker();
            }
        });

        DeferredStartupHandler.getInstance().addDeferredTask(new Runnable() {
            @Override
            public void run() {
                // Add process check to diagnose http://crbug.com/606309. Remove this after the bug
                // is fixed.
                assert !CommandLine.getInstance().hasSwitch(ContentSwitches.SWITCH_PROCESS_TYPE);
                if (!CommandLine.getInstance().hasSwitch(ContentSwitches.SWITCH_PROCESS_TYPE)) {
                    DownloadController.setDownloadNotificationService(
                            DownloadManagerService.getDownloadManagerService(
                                    getApplicationContext()));
                }

                if (ApiCompatibilityUtils.isPrintingSupported()) {
                    mPrintingController = PrintingControllerFactory.create(getApplicationContext());
                }
            }
        });
    }

    /**
     * For extending classes to carry out tasks that initialize the browser process.
     * Should be called almost immediately after the native library has loaded to initialize things
     * that really, really have to be set up early.  Avoid putting any long tasks here.
     */
    public void initializeProcess() {
        if (mIsProcessInitialized) return;
        mIsProcessInitialized = true;
        assert !mIsStarted;

        DataReductionProxySettings.reconcileDataReductionProxyEnabledState(getApplicationContext());

        mVariationsSession = createVariationsSession();
        removeSessionCookies();
        ApplicationStatus.registerApplicationStateListener(createApplicationStateListener());
        AppBannerManager.setAppDetailsDelegate(createAppDetailsDelegate());
        mChromeLifetimeController = new ChromeLifetimeController();

        PrefServiceBridge.getInstance().migratePreferences(this);
    }

    @Override
    public void initCommandLine() {
        CommandLineInitUtil.initCommandLine(this, COMMAND_LINE_FILE);
        if (CommandLine.getInstance().hasSwitch(ChromeSwitches.ENABLE_VERBOSE_LOGGING)) {
            Logger.enableVerboseLogging();
        }

        // SWE specific command line switches
        SWECommandLine.getInstance(this).initSWECommandLine();
    }

    /**
     * Shows an error dialog following a startup error, and then exits the application.
     * @param e The exception reported by Chrome initialization.
     */
    public static void reportStartupErrorAndExit(final ProcessInitException e) {
        Activity activity = ApplicationStatus.getLastTrackedFocusedActivity();
        if (ApplicationStatus.getStateForActivity(activity) == ActivityState.DESTROYED) {
            return;
        }
        InvalidStartupDialog.show(activity, e.getErrorCode());
    }

    /**
     * Returns an instance of LocationSettings to be installed as a singleton.
     */
    public LocationSettings createLocationSettings() {
        // Using an anonymous subclass as the constructor is protected.
        // This is done to deter instantiation of LocationSettings elsewhere without using the
        // getInstance() helper method.
        return new LocationSettings(){};
    }

    /**
     * @return The Application's PowerBroadcastReceiver.
     */
    @VisibleForTesting
    public PowerBroadcastReceiver getPowerBroadcastReceiver() {
        return mPowerBroadcastReceiver;
    }

    /**
     * Opens the UI to clear browsing data.
     * @param tab The tab that triggered the request.
     */
    @CalledByNative
    protected void openClearBrowsingData(Tab tab) {
        Activity activity = tab.getWindowAndroid().getActivity().get();
        if (activity == null) {
            Log.e(TAG,
                    "Attempting to open clear browsing data for a tab without a valid activity");
            return;
        }
        Intent intent = PreferencesLauncher.createIntentForSettingsPage(activity,
                ClearBrowsingDataPreferences.class.getName());
        activity.startActivity(intent);
    }

    /**
     * @return Whether parental controls are enabled.  Returning true will disable
     *         incognito mode.
     */
    @CalledByNative
    protected boolean areParentalControlsEnabled() {
        return PartnerBrowserCustomizations.isIncognitoDisabled();
    }

    /**
     * @return A provider of external estimates.
     * @param nativePtr Pointer to the native ExternalEstimateProviderAndroid object.
     */
    public ExternalEstimateProviderAndroid createExternalEstimateProviderAndroid(long nativePtr) {
        return new ExternalEstimateProviderAndroid(nativePtr) {};
    }

    /**
     * @return An external observer of data use.
     * @param nativePtr Pointer to the native ExternalDataUseObserver object.
     */
    public ExternalDataUseObserver createExternalDataUseObserver(long nativePtr) {
        return new ExternalDataUseObserver(nativePtr);
    }

    /**
     * @return The user agent string of Chrome.
     */
    public static String getBrowserUserAgent() {
        return nativeGetBrowserUserAgent();
    }

    /**
     * The host activity should call this during its onPause() handler to ensure
     * all state is saved when the app is suspended.  Calling ChromiumApplication.onStop() does
     * this for you.
     */
    public static void flushPersistentData() {
        try {
            TraceEvent.begin("ChromiumApplication.flushPersistentData");
            nativeFlushPersistentData();
        } finally {
            TraceEvent.end("ChromiumApplication.flushPersistentData");
        }
    }

    /**
     * Removes all session cookies (cookies with no expiration date) after device reboots.
     * This function will incorrectly clear cookies when Daylight Savings Time changes the clock.
     * Without a way to get a monotonically increasing system clock, the boot timestamp will be off
     * by one hour.  However, this should only happen at most once when the clock changes since the
     * updated timestamp is immediately saved.
     */
    protected void removeSessionCookies() {
        long lastKnownBootTimestamp =
                ContextUtils.getAppSharedPreferences().getLong(PREF_BOOT_TIMESTAMP, 0);
        long bootTimestamp = System.currentTimeMillis() - SystemClock.uptimeMillis();
        long difference = bootTimestamp - lastKnownBootTimestamp;

        // Allow some leeway to account for fractions of milliseconds.
        if (Math.abs(difference) > BOOT_TIMESTAMP_MARGIN_MS) {
            nativeRemoveSessionCookies();

            SharedPreferences prefs = ContextUtils.getAppSharedPreferences();
            SharedPreferences.Editor editor = prefs.edit();
            editor.putLong(PREF_BOOT_TIMESTAMP, bootTimestamp);
            editor.apply();
        }
    }

    private static native void nativeRemoveSessionCookies();
    private static native String nativeGetBrowserUserAgent();
    private static native void nativeFlushPersistentData();

    /**
     * @return An instance of {@link FeedbackReporter} to report feedback.
     */
    public FeedbackReporter createFeedbackReporter() {
        return new EmptyFeedbackReporter();
    }

    /**
     * @return An instance of ExternalAuthUtils to be installed as a singleton.
     */
    public ExternalAuthUtils createExternalAuthUtils() {
        return new ExternalAuthUtils();
    }

    /**
     * Returns a new instance of HelpAndFeedback.
     */
    public HelpAndFeedback createHelpAndFeedback() {
        return new HelpAndFeedback();
    }

    /**
     * @return A new ActivityWindowAndroid instance.
     */
    public ActivityWindowAndroid createActivityWindowAndroid(Activity activity) {
        if (activity instanceof ChromeActivity) return new ChromeWindow((ChromeActivity) activity);
        return new ActivityWindowAndroid(activity);
    }

    /**
     * @return An instance of {@link CustomTabsConnection}. Should not be called
     * outside of {@link CustomTabsConnection#getInstance()}.
     */
    public CustomTabsConnection createCustomTabsConnection() {
        return new CustomTabsConnection(this);
    }

    /**
     * @return A new {@link PhysicalWebBleClient} instance.
     */
    public PhysicalWebBleClient createPhysicalWebBleClient() {
        return new PhysicalWebBleClient();
    }

    /**
     * @return A new {@link PhysicalWebEnvironment} instance.
     */
    public PhysicalWebEnvironment createPhysicalWebEnvironment() {
        return new PhysicalWebEnvironment();
    }

    /**
     * @return Instance of printing controller that is shared among all chromium activities. May
     *         return null if printing is not supported on the platform.
     */
    public PrintingController getPrintingController() {
        return mPrintingController;
    }

    public InstantAppsHandler createInstantAppsHandler() {
        return new InstantAppsHandler();
    }

    /**
     * @return An instance of {@link GSAHelper} that handles the start point of chrome's integration
     *         with GSA.
     */
    public GSAHelper createGsaHelper() {
        return new GSAHelper();
    }

    /**
     * @return An instance of {@link LocaleManager} that handles customized locale related logic.
     */
    public LocaleManager createLocaleManager() {
        return new LocaleManager();
    }

   /**
     * Registers various policy providers with the policy manager.
     * Providers are registered in increasing order of precedence so overrides should call this
     * method in the end for this method to maintain the highest precedence.
     * @param combinedProvider The {@link CombinedPolicyProvider} to register the providers with.
     */
    public void registerPolicyProviders(CombinedPolicyProvider combinedProvider) {
        combinedProvider.registerProvider(new AppRestrictionsProvider(getApplicationContext()));
    }

    /**
     * Add a listener to be notified upon policy changes.
     */
    public void addPolicyChangeListener(PolicyChangeListener listener) {
        CombinedPolicyProvider.get().addPolicyChangeListener(listener);
    }

    /**
     * Remove a listener to be notified upon policy changes.
     */
    public void removePolicyChangeListener(PolicyChangeListener listener) {
        CombinedPolicyProvider.get().removePolicyChangeListener(listener);
    }

    /**
     * @return An instance of PolicyAuditor that notifies the policy system of the user's activity.
     * Only applicable when the user has a policy active, that is tracking the activity.
     */
    public PolicyAuditor getPolicyAuditor() {
        // This class has a protected constructor to prevent accidental instantiation.
        return new PolicyAuditor() {};
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
     * @return An instance of AppDetailsDelegate that can be queried about app information for the
     *         App Banner feature.  Will be null if one is unavailable.
     */
    protected AppDetailsDelegate createAppDetailsDelegate() {
        return null;
    }

    /**
     * Returns the Singleton instance of the DocumentTabModelSelector.
     * TODO(dfalcantara): Find a better place for this once we differentiate between activity and
     *                    application-level TabModelSelectors.
     * @return The DocumentTabModelSelector for the application.
     */
    @SuppressFBWarnings("LI_LAZY_INIT_STATIC")
    public static DocumentTabModelSelector getDocumentTabModelSelector() {
        ThreadUtils.assertOnUiThread();
        if (sDocumentTabModelSelector == null) {
            ActivityDelegateImpl activityDelegate = new ActivityDelegateImpl(
                    DocumentActivity.class, IncognitoDocumentActivity.class);
            sDocumentTabModelSelector = new DocumentTabModelSelector(activityDelegate,
                    new StorageDelegate(), new TabDelegate(false), new TabDelegate(true));
        }
        return sDocumentTabModelSelector;
    }

    /**
     * @return An instance of RevenueStats to be installed as a singleton.
     */
    public RevenueStats createRevenueStatsInstance() {
        return new RevenueStats();
    }

    /**
     * Creates a new {@link AccountManagerDelegate}.
     * @return the created {@link AccountManagerDelegate}.
     */
    public AccountManagerDelegate createAccountManagerDelegate() {
        return new SystemAccountManagerDelegate(this);
    }

    /**
     * Update the accept languages after changing Android locale setting. Doing so kills the
     * Activities but it doesn't kill the ChromeApplication, so this should be called in
     * {@link #onStart} instead of {@link #initialize}.
     */
    private void updateAcceptLanguages() {
        PrefServiceBridge instance = PrefServiceBridge.getInstance();
        String localeString = Locale.getDefault().toString();  // ex) en_US, de_DE, zh_CN_#Hans
        if (hasLocaleChanged(localeString)) {
            instance.resetAcceptLanguages(localeString);
            // Clear cache so that accept-languages change can be applied immediately.
            // TODO(changwan): The underlying BrowsingDataRemover::Remove() is an asynchronous call.
            // So cache-clearing may not be effective if URL rendering can happen before
            // OnBrowsingDataRemoverDone() is called, in which case we may have to reload as well.
            // Check if it can happen.
            instance.clearBrowsingData(
                    null, new int[]{ BrowsingDataType.CACHE }, TimePeriod.ALL_TIME);
        }
    }

    private boolean hasLocaleChanged(String newLocale) {
        String previousLocale = ContextUtils.getAppSharedPreferences().getString(
                PREF_LOCALE, "");

        if (!previousLocale.equals(newLocale)) {
            SharedPreferences prefs = ContextUtils.getAppSharedPreferences();
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(PREF_LOCALE, newLocale);
            editor.apply();
            return true;
        }
        return false;
    }

    /**
     * Honor the Android system setting about showing the last character of a password for a short
     * period of time.
     */
    private void updatePasswordEchoState() {
        boolean systemEnabled = Settings.System.getInt(
                getApplicationContext().getContentResolver(),
                Settings.System.TEXT_SHOW_PASSWORD, 1) == 1;
        if (PrefServiceBridge.getInstance().getPasswordEchoEnabled() == systemEnabled) return;

        PrefServiceBridge.getInstance().setPasswordEchoEnabled(systemEnabled);
    }

    /**
     * Caches flags that are needed by Activities that launch before the native library is loaded
     * and stores them in SharedPreferences. Because this function is called during launch after the
     * library has loaded, they won't affect the next launch until Chrome is restarted.
     */
    private void cacheNativeFlags() {
        if (sIsFinishedCachingNativeFlags) return;
        FeatureUtilities.cacheNativeFlags(this);
        sIsFinishedCachingNativeFlags = true;
    }
}

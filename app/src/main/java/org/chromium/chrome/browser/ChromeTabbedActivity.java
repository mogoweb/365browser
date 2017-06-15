// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.AppTask;
import android.app.ActivityManager.RecentTaskInfo;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.KeyboardShortcutGroup;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.PopupWindow.OnDismissListener;

import org.chromium.base.ActivityState;
import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.ApplicationStatus;
import org.chromium.base.Callback;
import org.chromium.base.CommandLine;
import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.base.MemoryPressureListener;
import org.chromium.base.TraceEvent;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.library_loader.LibraryLoader;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.IntentHandler.IntentHandlerDelegate;
import org.chromium.chrome.browser.IntentHandler.TabOpenType;
import org.chromium.chrome.browser.appmenu.AppMenuPropertiesDelegate;
import org.chromium.chrome.browser.bookmarks.BookmarkUtils;
import org.chromium.chrome.browser.compositor.CompositorViewHolder;
import org.chromium.chrome.browser.compositor.bottombar.OverlayPanel.StateChangeReason;
import org.chromium.chrome.browser.compositor.layouts.Layout;
import org.chromium.chrome.browser.compositor.layouts.LayoutManager;
import org.chromium.chrome.browser.compositor.layouts.LayoutManagerChrome;
import org.chromium.chrome.browser.compositor.layouts.LayoutManagerChromePhone;
import org.chromium.chrome.browser.compositor.layouts.LayoutManagerChromeTablet;
import org.chromium.chrome.browser.compositor.layouts.OverviewModeBehavior.OverviewModeObserver;
import org.chromium.chrome.browser.compositor.layouts.phone.StackLayout;
import org.chromium.chrome.browser.cookies.CookiesFetcher;
import org.chromium.chrome.browser.device.DeviceClassManager;
import org.chromium.chrome.browser.document.ChromeLauncherActivity;
import org.chromium.chrome.browser.document.DocumentUtils;
import org.chromium.chrome.browser.download.DownloadUtils;
import org.chromium.chrome.browser.feature_engagement_tracker.FeatureEngagementTrackerFactory;
import org.chromium.chrome.browser.firstrun.FirstRunActivity;
import org.chromium.chrome.browser.firstrun.FirstRunFlowSequencer;
import org.chromium.chrome.browser.firstrun.FirstRunSignInProcessor;
import org.chromium.chrome.browser.firstrun.FirstRunStatus;
import org.chromium.chrome.browser.fullscreen.ChromeFullscreenManager;
import org.chromium.chrome.browser.fullscreen.ComposedBrowserControlsVisibilityDelegate;
import org.chromium.chrome.browser.incognito.IncognitoNotificationManager;
import org.chromium.chrome.browser.infobar.DataReductionPromoInfoBar;
import org.chromium.chrome.browser.locale.LocaleManager;
import org.chromium.chrome.browser.metrics.ActivityStopMetrics;
import org.chromium.chrome.browser.metrics.LaunchMetrics;
import org.chromium.chrome.browser.metrics.MainIntentBehaviorMetrics;
import org.chromium.chrome.browser.metrics.StartupMetrics;
import org.chromium.chrome.browser.metrics.UmaUtils;
import org.chromium.chrome.browser.multiwindow.MultiInstanceChromeTabbedActivity;
import org.chromium.chrome.browser.multiwindow.MultiWindowUtils;
import org.chromium.chrome.browser.net.spdyproxy.DataReductionProxySettings;
import org.chromium.chrome.browser.ntp.ChromeHomeNewTabPage;
import org.chromium.chrome.browser.ntp.NativePageAssassin;
import org.chromium.chrome.browser.ntp.NewTabPage;
import org.chromium.chrome.browser.ntp.NewTabPageUma;
import org.chromium.chrome.browser.omaha.OmahaBase;
import org.chromium.chrome.browser.omnibox.AutocompleteController;
import org.chromium.chrome.browser.partnercustomizations.HomepageManager;
import org.chromium.chrome.browser.partnercustomizations.PartnerBrowserCustomizations;
import org.chromium.chrome.browser.preferences.ChromePreferenceManager;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.preferences.datareduction.DataReductionPromoScreen;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.signin.SigninPromoUtil;
import org.chromium.chrome.browser.snackbar.undo.UndoBarController;
import org.chromium.chrome.browser.suggestions.SuggestionsEventReporterBridge;
import org.chromium.chrome.browser.tab.BrowserControlsVisibilityDelegate;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabDelegateFactory;
import org.chromium.chrome.browser.tab.TabStateBrowserControlsVisibilityDelegate;
import org.chromium.chrome.browser.tabmodel.ChromeTabCreator;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.TabModel.TabLaunchType;
import org.chromium.chrome.browser.tabmodel.TabModel.TabSelectionType;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.tabmodel.TabModelSelectorImpl;
import org.chromium.chrome.browser.tabmodel.TabModelSelectorTabModelObserver;
import org.chromium.chrome.browser.tabmodel.TabModelSelectorTabObserver;
import org.chromium.chrome.browser.tabmodel.TabModelUtils;
import org.chromium.chrome.browser.tabmodel.TabWindowManager;
import org.chromium.chrome.browser.toolbar.ToolbarControlContainer;
import org.chromium.chrome.browser.util.FeatureUtilities;
import org.chromium.chrome.browser.util.IntentUtils;
import org.chromium.chrome.browser.vr_shell.VrShellDelegate;
import org.chromium.chrome.browser.widget.bottomsheet.BottomSheet;
import org.chromium.chrome.browser.widget.bottomsheet.BottomSheetMetrics;
import org.chromium.chrome.browser.widget.emptybackground.EmptyBackgroundViewWrapper;
import org.chromium.chrome.browser.widget.findinpage.FindToolbarManager;
import org.chromium.chrome.browser.widget.textbubble.ViewAnchoredTextBubble;
import org.chromium.components.feature_engagement_tracker.FeatureConstants;
import org.chromium.components.feature_engagement_tracker.FeatureEngagementTracker;
import org.chromium.content.browser.ContentVideoView;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.content.browser.crypto.CipherFactory;
import org.chromium.content.common.ContentSwitches;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.content_public.common.Referrer;
import org.chromium.ui.base.DeviceFormFactor;
import org.chromium.ui.base.PageTransition;
import org.chromium.ui.base.WindowAndroid;
import org.chromium.ui.widget.Toast;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * This is the main activity for ChromeMobile when not running in document mode.  All the tabs
 * are accessible via a chrome specific tab switching UI.
 */
public class ChromeTabbedActivity extends ChromeActivity implements OverviewModeObserver {

    private static final int FIRST_RUN_EXPERIENCE_RESULT = 101;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
        BACK_PRESSED_NOTHING_HAPPENED,
        BACK_PRESSED_HELP_URL_CLOSED,
        BACK_PRESSED_MINIMIZED_NO_TAB_CLOSED,
        BACK_PRESSED_MINIMIZED_TAB_CLOSED,
        BACK_PRESSED_TAB_CLOSED,
        BACK_PRESSED_TAB_IS_NULL,
        BACK_PRESSED_EXITED_TAB_SWITCHER,
        BACK_PRESSED_EXITED_FULLSCREEN,
        BACK_PRESSED_NAVIGATED_BACK
    })
    private @interface BackPressedResult {}
    private static final int BACK_PRESSED_NOTHING_HAPPENED = 0;
    private static final int BACK_PRESSED_HELP_URL_CLOSED = 1;
    private static final int BACK_PRESSED_MINIMIZED_NO_TAB_CLOSED = 2;
    private static final int BACK_PRESSED_MINIMIZED_TAB_CLOSED = 3;
    private static final int BACK_PRESSED_TAB_CLOSED = 4;
    private static final int BACK_PRESSED_TAB_IS_NULL = 5;
    private static final int BACK_PRESSED_EXITED_TAB_SWITCHER = 6;
    private static final int BACK_PRESSED_EXITED_FULLSCREEN = 7;
    private static final int BACK_PRESSED_NAVIGATED_BACK = 8;
    private static final int BACK_PRESSED_COUNT = 9;

    private static final String TAG = "ChromeTabbedActivity";

    private static final String HELP_URL_PREFIX = "https://support.google.com/chrome/";

    private static final String FRE_RUNNING = "First run is running";

    private static final String WINDOW_INDEX = "window_index";

    // How long to delay closing the current tab when our app is minimized.  Have to delay this
    // so that we don't show the contents of the next tab while minimizing.
    private static final long CLOSE_TAB_ON_MINIMIZE_DELAY_MS = 500;

    // Maximum delay for initial tab creation. This is for homepage and NTP, not previous tabs
    // restore. This is needed because we do not know when reading PartnerBrowserCustomizations
    // provider will be finished.
    private static final int INITIAL_TAB_CREATION_TIMEOUT_MS = 500;

    /**
     * Sending an intent with this extra sets the app into single process mode.
     * This is only used for testing, when certain tests want to force this behaviour.
     */
    public static final String INTENT_EXTRA_TEST_RENDER_PROCESS_LIMIT = "render_process_limit";

    /**
     * Sending an intent with this action to Chrome will cause it to close all tabs
     * (iff the --enable-test-intents command line flag is set). If a URL is supplied in the
     * intent data, this will be loaded and unaffected by the close all action.
     */
    private static final String ACTION_CLOSE_TABS =
            "com.google.android.apps.chrome.ACTION_CLOSE_TABS";

    private static final String LAST_BACKGROUNDED_TIME_MS_PREF =
            "ChromeTabbedActivity.BackgroundTimeMs";
    private static final String NTP_LAUNCH_DELAY_IN_MINS_PARAM = "delay_in_mins";

    /** The task id of the activity that tabs were merged into. */
    private static int sMergedInstanceTaskId;

    private final ActivityStopMetrics mActivityStopMetrics;
    private final MainIntentBehaviorMetrics mMainIntentMetrics;

    private FindToolbarManager mFindToolbarManager;

    private UndoBarController mUndoBarPopupController;

    private LayoutManagerChrome mLayoutManager;

    private ViewGroup mContentContainer;

    private ToolbarControlContainer mControlContainer;

    private TabModelSelectorImpl mTabModelSelectorImpl;
    private TabModelSelectorTabObserver mTabModelSelectorTabObserver;
    private TabModelSelectorTabModelObserver mTabModelObserver;

    private boolean mUIInitialized;

    private boolean mIsOnFirstRun;
    private Boolean mMergeTabsOnResume;

    private Boolean mIsAccessibilityEnabled;

    private LocaleManager mLocaleManager;

    private AppIndexingUtil mAppIndexingUtil;

    /**
     * Whether an initial tab needs to be created during UI initialization.
     */
    private Runnable mDelayedInitialTabBehaviorDuringUiInit;

    /**
     * Keeps track of whether or not a specific tab was created based on the startup intent.
     */
    private boolean mCreatedTabOnStartup;

    // Whether or not chrome was launched with an intent to open a tab.
    private boolean mIntentWithEffect;

    // Time at which an intent was received and handled.
    private long mIntentHandlingTimeMs;

    private class TabbedAssistStatusHandler extends AssistStatusHandler {
        public TabbedAssistStatusHandler(Activity activity) {
            super(activity);
        }

        @Override
        public boolean isAssistSupported() {
            // If we are in the tab switcher and any incognito tabs are present, disable assist.
            if (isInOverviewMode() && mTabModelSelectorImpl != null
                    && mTabModelSelectorImpl.getModel(true).getCount() > 0) {
                return false;
            }
            return super.isAssistSupported();
        }
    }

    // TODO(mthiesse): Move VR control visibility handling into ChromeActivity. crbug.com/688611
    private static class TabbedModeBrowserControlsVisibilityDelegate
            extends TabStateBrowserControlsVisibilityDelegate {
        public TabbedModeBrowserControlsVisibilityDelegate(Tab tab) {
            super(tab);
        }

        @Override
        public boolean isShowingBrowserControlsEnabled() {
            if (VrShellDelegate.isInVr()) return false;
            return super.isShowingBrowserControlsEnabled();
        }

        @Override
        public boolean isHidingBrowserControlsEnabled() {
            if (VrShellDelegate.isInVr()) return true;
            return super.isHidingBrowserControlsEnabled();
        }
    }

    private class TabbedModeTabDelegateFactory extends TabDelegateFactory {
        @Override
        public BrowserControlsVisibilityDelegate createBrowserControlsVisibilityDelegate(Tab tab) {
            return new ComposedBrowserControlsVisibilityDelegate(
                    new TabbedModeBrowserControlsVisibilityDelegate(tab),
                    getFullscreenManager().getBrowserVisibilityDelegate());
        }
    }

    private class TabbedModeTabCreator extends ChromeTabCreator {
        private boolean mIsIncognito;

        public TabbedModeTabCreator(
                ChromeTabbedActivity activity, WindowAndroid nativeWindow, boolean incognito) {
            super(activity, nativeWindow, incognito);

            mIsIncognito = incognito;
        }

        @Override
        public TabDelegateFactory createDefaultTabDelegateFactory() {
            return new TabbedModeTabDelegateFactory();
        }

        @Override
        public Tab launchUrl(
                String url, TabModel.TabLaunchType type, Intent intent, long intentTimestamp) {
            if (openNtpBottomSheet(url)) return null;
            return super.launchUrl(url, type, intent, intentTimestamp);
        }

        @Override
        public Tab createNewTab(
                LoadUrlParams loadUrlParams, TabLaunchType type, Tab parent, Intent intent) {
            if (openNtpBottomSheet(loadUrlParams.getUrl())) return null;
            return super.createNewTab(loadUrlParams, type, parent, intent);
        }

        /**
         * Handles opening the NTP in the bottom sheet if supported.
         *
         * @param url The URL that is used to determine if this is an NTP being opened.
         * @return Whether the NTP experience is opened in the bottom sheet without a corresponding
         *         Tab associated with it.
         */
        private boolean openNtpBottomSheet(String url) {
            if (getBottomSheet() != null && NewTabPage.isNTPUrl(url)) {
                if (!mUIInitialized) {
                    assert mDelayedInitialTabBehaviorDuringUiInit == null;
                    mDelayedInitialTabBehaviorDuringUiInit = new Runnable() {
                        @Override
                        public void run() {
                            getBottomSheet().displayNewTabUi(mIsIncognito);
                        }
                    };
                } else {
                    getBottomSheet().displayNewTabUi(mIsIncognito);
                }
                return true;
            }
            return false;
        }
    }

    /**
     * Return whether the passed in class name matches any of the supported tabbed mode activities.
     */
    public static boolean isTabbedModeClassName(String className) {
        return TextUtils.equals(className, ChromeTabbedActivity.class.getName())
                || TextUtils.equals(className, MultiInstanceChromeTabbedActivity.class.getName())
                || TextUtils.equals(className, ChromeTabbedActivity2.class.getName());
    }

    /**
     * Constructs a ChromeTabbedActivity.
     */
    public ChromeTabbedActivity() {
        mActivityStopMetrics = new ActivityStopMetrics();
        mMainIntentMetrics = new MainIntentBehaviorMetrics(this);
        mAppIndexingUtil = new AppIndexingUtil();
    }

    @Override
    public void initializeCompositor() {
        try {
            TraceEvent.begin("ChromeTabbedActivity.initializeCompositor");
            super.initializeCompositor();

            mTabModelSelectorImpl.onNativeLibraryReady(getTabContentManager());

            mTabModelObserver = new TabModelSelectorTabModelObserver(mTabModelSelectorImpl) {
                @Override
                public void didCloseTab(int tabId, boolean incognito) {
                    closeIfNoTabsAndHomepageEnabled(false);
                }

                @Override
                public void tabPendingClosure(Tab tab) {
                    closeIfNoTabsAndHomepageEnabled(true);
                }

                @Override
                public void tabRemoved(Tab tab) {
                    closeIfNoTabsAndHomepageEnabled(false);
                }

                private void closeIfNoTabsAndHomepageEnabled(boolean isPendingClosure) {
                    if (getTabModelSelector().getTotalTabCount() == 0) {
                        // If the last tab is closed, and homepage is enabled, then exit Chrome.
                        if (HomepageManager.isHomepageEnabled(getApplicationContext())) {
                            finish();
                        } else if (isPendingClosure) {
                            NewTabPageUma.recordNTPImpression(
                                    NewTabPageUma.NTP_IMPESSION_POTENTIAL_NOTAB);
                        }
                    }
                }

                @Override
                public void didAddTab(Tab tab, TabLaunchType type) {
                    if (type == TabLaunchType.FROM_LONGPRESS_BACKGROUND
                            && !DeviceClassManager.enableAnimations()) {
                        Toast.makeText(ChromeTabbedActivity.this,
                                R.string.open_in_new_tab_toast,
                                Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void allTabsPendingClosure(List<Tab> tabs) {
                    NewTabPageUma.recordNTPImpression(
                            NewTabPageUma.NTP_IMPESSION_POTENTIAL_NOTAB);
                }
            };

            Bundle state = getSavedInstanceState();
            if (state != null && state.containsKey(FRE_RUNNING)) {
                mIsOnFirstRun = state.getBoolean(FRE_RUNNING);
            }
        } finally {
            TraceEvent.end("ChromeTabbedActivity.initializeCompositor");
        }
    }

    private void refreshSignIn() {
        if (mIsOnFirstRun) return;
        FirstRunSignInProcessor.start(this);
    }

    @Override
    public void onNewIntent(Intent intent) {
        mIntentHandlingTimeMs = SystemClock.uptimeMillis();
        super.onNewIntent(intent);
    }

    @Override
    public void finishNativeInitialization() {
        try {
            TraceEvent.begin("ChromeTabbedActivity.finishNativeInitialization");

            refreshSignIn();

            initializeUI();

            // The dataset has already been created, we need to initialize our state.
            mTabModelSelectorImpl.notifyChanged();

            ApiCompatibilityUtils.setWindowIndeterminateProgress(getWindow());

            // Check for incognito tabs to handle the case where Chrome was swiped away in the
            // background.
            if (TabWindowManager.getInstance().canDestroyIncognitoProfile()) {
                IncognitoNotificationManager.dismissIncognitoNotification();
            }

            // LocaleManager can only function after the native library is loaded.
            mLocaleManager = LocaleManager.getInstance();
            boolean searchEnginePromoShown =
                    mLocaleManager.showSearchEnginePromoIfNeeded(this, null);

            ChromePreferenceManager preferenceManager = ChromePreferenceManager.getInstance();
            // Promos can only be shown when we start with ACTION_MAIN intent and
            // after FRE is complete. Native initialization can finish before the FRE flow is
            // complete, and this will only show promos on the second opportunity. This is
            // because the FRE is shown on the first opportunity, and we don't want to show such
            // content back to back.
            if (!searchEnginePromoShown && !mIntentWithEffect
                    && FirstRunStatus.getFirstRunFlowComplete()
                    && preferenceManager.getPromosSkippedOnFirstStart()) {
                // Data reduction promo should be temporarily suppressed if the sign in promo is
                // shown to avoid nagging users too much.
                if (!SigninPromoUtil.launchSigninPromoIfNeeded(this)) {
                    DataReductionPromoScreen.launchDataReductionPromo(this);
                }
            } else {
                preferenceManager.setPromosSkippedOnFirstStart(true);
            }

            super.finishNativeInitialization();
        } finally {
            TraceEvent.end("ChromeTabbedActivity.finishNativeInitialization");
        }
    }

    /**
     * Determine whether the incognito profile needs to be destroyed as part of startup.  This is
     * only needed on L+ when it is possible to swipe away tasks from Android recents without
     * killing the process.  When this occurs, the normal incognito profile shutdown does not
     * happen, which can leave behind incognito cookies from an existing session.
     */
    @SuppressLint("NewApi")
    private boolean shouldDestroyIncognitoProfile() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return false;

        Context context = ContextUtils.getApplicationContext();
        ActivityManager manager =
                (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        PackageManager pm = context.getPackageManager();

        Set<Integer> tabbedModeTaskIds = new HashSet<>();
        for (AppTask task : manager.getAppTasks()) {
            RecentTaskInfo info = DocumentUtils.getTaskInfoFromTask(task);
            if (info == null) continue;
            String className = DocumentUtils.getTaskClassName(task, pm);

            if (isTabbedModeClassName(className)) {
                tabbedModeTaskIds.add(info.id);
            }
        }

        if (tabbedModeTaskIds.size() == 0) {
            return Profile.getLastUsedProfile().hasOffTheRecordProfile();
        }

        List<WeakReference<Activity>> activities = ApplicationStatus.getRunningActivities();
        for (int i = 0; i < activities.size(); i++) {
            Activity activity = activities.get(i).get();
            if (activity == null) continue;
            tabbedModeTaskIds.remove(activity.getTaskId());
        }

        // If all tabbed mode tasks listed in Android recents are alive, check to see if
        // any have incognito tabs exist.  If all are alive and no tabs exist, we should ensure that
        // we delete the incognito profile if one is around still.
        if (tabbedModeTaskIds.size() == 0) {
            return TabWindowManager.getInstance().canDestroyIncognitoProfile()
                    && Profile.getLastUsedProfile().hasOffTheRecordProfile();
        }

        // In this case, we have tabbed mode activities listed in recents that do not have an
        // active running activity associated with them.  We can not accurately get an incognito
        // tab count as we do not know if any incognito tabs are associated with the yet unrestored
        // tabbed mode.  Thus we do not proactivitely destroy the incognito profile.
        return false;
    }

    @Override
    public void onResumeWithNative() {
        super.onResumeWithNative();

        if (shouldDestroyIncognitoProfile()) {
            Profile.getLastUsedProfile().getOffTheRecordProfile().destroyWhenAppropriate();
        } else {
            CookiesFetcher.restoreCookies(this);
        }

        StartupMetrics.getInstance().recordHistogram(false);

        if (FeatureUtilities.isTabModelMergingEnabled()) {
            boolean inMultiWindowMode = MultiWindowUtils.getInstance().isInMultiWindowMode(this);
            // Merge tabs if the activity is not in multi-window mode and mMergeTabsOnResume is true
            // or unset because the activity is just starting or was destroyed.
            if (!inMultiWindowMode && (mMergeTabsOnResume == null || mMergeTabsOnResume)) {
                maybeMergeTabs();
            }
            mMergeTabsOnResume = false;
        }

        mLocaleManager.setSnackbarManager(getSnackbarManager());
        mLocaleManager.startObservingPhoneChanges();

        if (isWarmOnResume()) {
            SuggestionsEventReporterBridge.onActivityWarmResumed();
        } else {
            SuggestionsEventReporterBridge.onColdStart();
        }
    }

    @Override
    public void onPauseWithNative() {
        mTabModelSelectorImpl.commitAllTabClosures();
        CookiesFetcher.persistCookies(this);

        mLocaleManager.setSnackbarManager(null);
        mLocaleManager.stopObservingPhoneChanges();

        super.onPauseWithNative();
    }

    @Override
    public void onStopWithNative() {
        super.onStopWithNative();

        if (getActivityTab() != null) getActivityTab().setIsAllowedToReturnToExternalApp(false);

        mTabModelSelectorImpl.saveState();
        StartupMetrics.getInstance().recordHistogram(true);
        mActivityStopMetrics.onStopWithNative(this);

        ContextUtils.getAppSharedPreferences()
                .edit()
                .putLong(LAST_BACKGROUNDED_TIME_MS_PREF, System.currentTimeMillis())
                .apply();
    }

    @Override
    public void onStart() {
        super.onStart();
        StartupMetrics.getInstance().updateIntent(getIntent());
    }

    @Override
    public void onStartWithNative() {
        super.onStartWithNative();
        // If we don't have a current tab, show the overview mode.
        if (getActivityTab() == null && !mLayoutManager.overviewVisible()) {
            mLayoutManager.showOverview(false);
        }

        resetSavedInstanceState();
    }

    @Override
    public void onNewIntentWithNative(Intent intent) {
        try {
            TraceEvent.begin("ChromeTabbedActivity.onNewIntentWithNative");

            super.onNewIntentWithNative(intent);
            if (isMainIntentFromLauncher(intent)) {
                if (IntentHandler.getUrlFromIntent(intent) == null) {
                    maybeLaunchNtpFromMainIntent(intent);
                }
                logMainIntentBehavior(intent);
            }

            if (CommandLine.getInstance().hasSwitch(ContentSwitches.ENABLE_TEST_INTENTS)) {
                handleDebugIntent(intent);
            }
        } finally {
            TraceEvent.end("ChromeTabbedActivity.onNewIntentWithNative");
        }
    }

    @Override
    public ChromeTabCreator getTabCreator(boolean incognito) {
        TabCreator tabCreator = super.getTabCreator(incognito);
        assert tabCreator instanceof ChromeTabCreator;
        return (ChromeTabCreator) tabCreator;
    }

    @Override
    public ChromeTabCreator getCurrentTabCreator() {
        TabCreator tabCreator = super.getCurrentTabCreator();
        assert tabCreator instanceof ChromeTabCreator;
        return (ChromeTabCreator) tabCreator;
    }

    @Override
    protected AssistStatusHandler createAssistStatusHandler() {
        return new TabbedAssistStatusHandler(this);
    }

    private void handleDebugIntent(Intent intent) {
        if (ACTION_CLOSE_TABS.equals(intent.getAction())) {
            getTabModelSelector().closeAllTabs();
        } else if (MemoryPressureListener.handleDebugIntent(ChromeTabbedActivity.this,
                intent.getAction())) {
            // Handled.
        }
    }

    private void initializeUI() {
        try {
            TraceEvent.begin("ChromeTabbedActivity.initializeUI");

            CompositorViewHolder compositorViewHolder = getCompositorViewHolder();
            if (DeviceFormFactor.isTablet()) {
                mLayoutManager = new LayoutManagerChromeTablet(compositorViewHolder);
            } else {
                mLayoutManager = new LayoutManagerChromePhone(compositorViewHolder);
                if (getBottomSheet() != null) {
                    ((LayoutManagerChromePhone) mLayoutManager)
                            .setForegroundTabAnimationDisabled(true);
                }
            }
            mLayoutManager.setEnableAnimations(DeviceClassManager.enableAnimations());
            mLayoutManager.addOverviewModeObserver(this);

            if (getBottomSheet() != null) getBottomSheet().setLayoutManagerChrome(mLayoutManager);

            // TODO(yusufo): get rid of findViewById(R.id.url_bar).
            initializeCompositorContent(mLayoutManager, findViewById(R.id.url_bar),
                    mContentContainer, mControlContainer);

            mTabModelSelectorImpl.setOverviewModeBehavior(mLayoutManager);

            mUndoBarPopupController.initialize();

            // Adjust the content container if we're not entering fullscreen mode.
            if (getFullscreenManager() == null) {
                float controlHeight = getResources().getDimension(R.dimen.control_container_height);
                ((FrameLayout.LayoutParams) mContentContainer.getLayoutParams()).topMargin =
                        (int) controlHeight;
            }

            mFindToolbarManager = new FindToolbarManager(this,
                    getToolbarManager().getActionModeController().getActionModeCallback());
            if (getContextualSearchManager() != null) {
                getContextualSearchManager().setFindToolbarManager(mFindToolbarManager);
            }

            OnClickListener tabSwitcherClickHandler = new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (getFullscreenManager() != null
                            && getFullscreenManager().getPersistentFullscreenMode()) {
                        return;
                    }
                    toggleOverview();
                }
            };
            OnClickListener newTabClickHandler = new OnClickListener() {
                @Override
                public void onClick(View v) {
                    getTabModelSelector().getModel(false).commitAllTabClosures();
                    // This assumes that the keyboard can not be seen at the same time as the
                    // newtab button on the toolbar.
                    getCurrentTabCreator().launchNTP();
                    mLocaleManager.showSearchEnginePromoIfNeeded(ChromeTabbedActivity.this, null);
                }
            };
            OnClickListener bookmarkClickHandler = new OnClickListener() {
                @Override
                public void onClick(View v) {
                    addOrEditBookmark(getActivityTab());
                }
            };

            getToolbarManager().initializeWithNative(mTabModelSelectorImpl,
                    getFullscreenManager().getBrowserVisibilityDelegate(),
                    mFindToolbarManager, mLayoutManager, mLayoutManager,
                    tabSwitcherClickHandler, newTabClickHandler, bookmarkClickHandler, null);

            if (isTablet()) {
                EmptyBackgroundViewWrapper bgViewWrapper = new EmptyBackgroundViewWrapper(
                        getTabModelSelector(), getTabCreator(false), ChromeTabbedActivity.this,
                        getAppMenuHandler(), getSnackbarManager(), mLayoutManager);
                bgViewWrapper.initialize();
            }

            if (mDelayedInitialTabBehaviorDuringUiInit != null) {
                mDelayedInitialTabBehaviorDuringUiInit.run();
                mDelayedInitialTabBehaviorDuringUiInit = null;
            } else {
                mLayoutManager.hideOverview(false);
            }

            FeatureEngagementTracker tracker =
                    FeatureEngagementTrackerFactory.getFeatureEngagementTrackerForProfile(
                            Profile.getLastUsedProfile());
            tracker.addOnInitializedCallback(new Callback<Boolean>() {
                @Override
                public void onResult(Boolean result) {
                    showFeatureEngagementTextBubbleForDownloadHome();
                }
            });

            mUIInitialized = true;
        } finally {
            TraceEvent.end("ChromeTabbedActivity.initializeUI");
        }
    }

    private void showFeatureEngagementTextBubbleForDownloadHome() {
        final FeatureEngagementTracker tracker =
                FeatureEngagementTrackerFactory.getFeatureEngagementTrackerForProfile(
                        Profile.getLastUsedProfile());
        if (!tracker.shouldTriggerHelpUI(FeatureConstants.DOWNLOAD_HOME_FEATURE)) return;

        ViewAnchoredTextBubble textBubble = new ViewAnchoredTextBubble(this,
                getToolbarManager().getMenuButton(), R.string.iph_download_home_text,
                R.string.iph_download_home_accessibility_text);
        textBubble.setDismissOnTouchInteraction(true);
        textBubble.addOnDismissListener(new OnDismissListener() {
            @Override
            public void onDismiss() {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        tracker.dismissed(FeatureConstants.DOWNLOAD_HOME_FEATURE);
                        getAppMenuHandler().setMenuHighlight(null);
                    }
                });
            }
        });
        getAppMenuHandler().setMenuHighlight(R.id.downloads_menu_id);
        int yInsetPx =
                getResources().getDimensionPixelOffset(R.dimen.text_bubble_menu_anchor_y_inset);
        textBubble.setInsetPx(0, FeatureUtilities.isChromeHomeEnabled() ? yInsetPx : 0, 0,
                FeatureUtilities.isChromeHomeEnabled() ? 0 : yInsetPx);
        textBubble.show();
    }

    private boolean isMainIntentFromLauncher(Intent intent) {
        return intent != null && TextUtils.equals(intent.getAction(), Intent.ACTION_MAIN)
                && intent.hasCategory(Intent.CATEGORY_LAUNCHER);
    }

    private void logMainIntentBehavior(Intent intent) {
        assert isMainIntentFromLauncher(intent);
        long currentTime = System.currentTimeMillis();
        long lastBackgroundedTimeMs = ContextUtils.getAppSharedPreferences().getLong(
                LAST_BACKGROUNDED_TIME_MS_PREF, currentTime);
        mMainIntentMetrics.onMainIntentWithNative(currentTime - lastBackgroundedTimeMs);
    }

    /** Access the main intent metrics for test validation. */
    @VisibleForTesting
    public MainIntentBehaviorMetrics getMainIntentBehaviorMetricsForTesting() {
        return mMainIntentMetrics;
    }

    /**
     * Determines if the intent should trigger an NTP and launches it if applicable.
     *
     * @param intent The intent to check whether an NTP should be triggered.
     * @return Whether an NTP was triggered as a result of this intent.
     */
    private boolean maybeLaunchNtpFromMainIntent(Intent intent) {
        assert isMainIntentFromLauncher(intent);

        if (!mIntentHandler.isIntentUserVisible()) return false;
        if (FeatureUtilities.isChromeHomeEnabled()) return false;

        if (!ChromeFeatureList.isEnabled(ChromeFeatureList.NTP_LAUNCH_AFTER_INACTIVITY)) {
            return false;
        }

        int ntpLaunchDelayInMins = ChromeFeatureList.getFieldTrialParamByFeatureAsInt(
                ChromeFeatureList.NTP_LAUNCH_AFTER_INACTIVITY, NTP_LAUNCH_DELAY_IN_MINS_PARAM, -1);
        if (ntpLaunchDelayInMins == -1) {
            Log.e(TAG, "No NTP launch delay specified despite enabled field trial");
            return false;
        }

        long lastBackgroundedTimeMs =
                ContextUtils.getAppSharedPreferences().getLong(LAST_BACKGROUNDED_TIME_MS_PREF, -1);
        if (lastBackgroundedTimeMs == -1) return false;

        long backgroundDurationMinutes = TimeUnit.MINUTES.convert(
                System.currentTimeMillis() - lastBackgroundedTimeMs, TimeUnit.MILLISECONDS);

        if (backgroundDurationMinutes < ntpLaunchDelayInMins) {
            Log.i(TAG, "Not launching NTP due to inactivity, background time: %d, launch delay: %d",
                    backgroundDurationMinutes, ntpLaunchDelayInMins);
            return false;
        }

        if (mLayoutManager != null && mLayoutManager.overviewVisible() && !isTablet()) {
            mLayoutManager.hideOverview(false);
        }

        // In cases where the tab model is initialized, attempt to reuse an existing NTP if
        // available before attempting to create a new one.
        TabModel normalTabModel = getTabModelSelector().getModel(false);
        Tab ntpToRefocus = null;
        for (int i = 0; i < normalTabModel.getCount(); i++) {
            Tab tab = normalTabModel.getTabAt(i);

            if (NewTabPage.isNTPUrl(tab.getUrl()) && !tab.canGoBack() && !tab.canGoForward()) {
                // If the currently selected tab is an NTP, then take no action.
                if (getActivityTab().equals(tab)) return true;
                ntpToRefocus = tab;
                break;
            }
        }

        if (ntpToRefocus != null) {
            normalTabModel.moveTab(ntpToRefocus.getId(), normalTabModel.getCount());
            normalTabModel.setIndex(
                    TabModelUtils.getTabIndexById(normalTabModel, ntpToRefocus.getId()),
                    TabSelectionType.FROM_USER);
        } else {
            getTabCreator(false).launchUrl(UrlConstants.NTP_URL, TabLaunchType.FROM_EXTERNAL_APP);
        }
        RecordUserAction.record("MobileStartup.MainIntent.NTPCreatedDueToInactivity");
        return true;
    }

    @Override
    public void initializeState() {
        // This method goes through 3 steps:
        // 1. Load the saved tab state (but don't start restoring the tabs yet).
        // 2. Process the Intent that this activity received and if that should result in any
        //    new tabs, create them.  This is done after step 1 so that the new tab gets
        //    created after previous tab state was restored.
        // 3. If no tabs were created in any of the above steps, create an NTP, otherwise
        //    start asynchronous tab restore (loading the previously active tab synchronously
        //    if no new tabs created in step 2).

        // Only look at the original intent if this is not a "restoration" and we are allowed to
        // process intents. Any subsequent intents are carried through onNewIntent.
        try {
            TraceEvent.begin("ChromeTabbedActivity.initializeState");

            super.initializeState();

            Intent intent = getIntent();

            boolean hadCipherData =
                    CipherFactory.getInstance().restoreFromBundle(getSavedInstanceState());

            boolean noRestoreState =
                    CommandLine.getInstance().hasSwitch(ChromeSwitches.NO_RESTORE_STATE);
            if (noRestoreState) {
                // Clear the state files because they are inconsistent and useless from now on.
                mTabModelSelectorImpl.clearState();
            } else if (!mIsOnFirstRun) {
                // State should be clear when we start first run and hence we do not need to load
                // a previous state. This may change the current Model, watch out for initialization
                // based on the model.
                // Never attempt to restore incognito tabs when this activity was previously swiped
                // away in Recents. http://crbug.com/626629
                boolean ignoreIncognitoFiles = !hadCipherData;
                mTabModelSelectorImpl.loadState(ignoreIncognitoFiles);
            }

            mIntentWithEffect = false;
            if ((mIsOnFirstRun || getSavedInstanceState() == null) && intent != null) {
                if (!mIntentHandler.shouldIgnoreIntent(intent)) {
                    mIntentWithEffect = mIntentHandler.onNewIntent(intent);
                }

                if (isMainIntentFromLauncher(intent)) {
                    if (IntentHandler.getUrlFromIntent(intent) == null) {
                        assert !mIntentWithEffect
                                : "ACTION_MAIN should not have triggered any prior action";
                        mIntentWithEffect = maybeLaunchNtpFromMainIntent(intent);
                    }
                    logMainIntentBehavior(intent);
                }
            }

            mCreatedTabOnStartup = getCurrentTabModel().getCount() > 0
                    || mTabModelSelectorImpl.getRestoredTabCount() > 0
                    || mIntentWithEffect;

            // We always need to try to restore tabs. The set of tabs might be empty, but at least
            // it will trigger the notification that tab restore is complete which is needed by
            // other parts of Chrome such as sync.
            boolean activeTabBeingRestored = !mIntentWithEffect;
            mMainIntentMetrics.setIgnoreEvents(true);
            mTabModelSelectorImpl.restoreTabs(activeTabBeingRestored);
            mMainIntentMetrics.setIgnoreEvents(false);

            // Only create an initial tab if no tabs were restored and no intent was handled.
            // Also, check whether the active tab was supposed to be restored and that the total
            // tab count is now non zero.  If this is not the case, tab restore failed and we need
            // to create a new tab as well.
            if (!mCreatedTabOnStartup
                    || (activeTabBeingRestored && getTabModelSelector().getTotalTabCount() == 0)) {
                // If homepage URI is not determined, due to PartnerBrowserCustomizations provider
                // async reading, then create a tab at the async reading finished. If it takes
                // too long, just create NTP.
                PartnerBrowserCustomizations.setOnInitializeAsyncFinished(
                        new Runnable() {
                            @Override
                            public void run() {
                                mMainIntentMetrics.setIgnoreEvents(true);
                                createInitialTab();
                                mMainIntentMetrics.setIgnoreEvents(false);
                            }
                        }, INITIAL_TAB_CREATION_TIMEOUT_MS);
            }

            RecordHistogram.recordBooleanHistogram(
                    "MobileStartup.ColdStartupIntent", mIntentWithEffect);
        } finally {
            TraceEvent.end("ChromeTabbedActivity.initializeState");
        }
    }

    /**
     * Create an initial tab for cold start without restored tabs.
     */
    private void createInitialTab() {
        String url = HomepageManager.getHomepageUri(getApplicationContext());
        if (TextUtils.isEmpty(url) || NewTabPage.isNTPUrl(url)) {
            url = UrlConstants.NTP_URL;
        }

        getTabCreator(false).launchUrl(url, TabLaunchType.FROM_CHROME_UI);
    }

    @Override
    public boolean onActivityResultWithNative(int requestCode, int resultCode, Intent data) {
        if (super.onActivityResultWithNative(requestCode, resultCode, data)) return true;

        if (requestCode == FIRST_RUN_EXPERIENCE_RESULT) {
            mIsOnFirstRun = false;
            if (resultCode == RESULT_OK) {
                refreshSignIn();
                mLocaleManager.showSearchEnginePromoIfNeeded(this, null);
            } else {
                if (data != null && data.getBooleanExtra(
                        FirstRunActivity.RESULT_CLOSE_APP, false)) {
                    getTabModelSelector().closeAllTabs(true);
                    finish();
                } else {
                    launchFirstRunExperience();
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public void onAccessibilityModeChanged(boolean enabled) {
        super.onAccessibilityModeChanged(enabled);

        if (mLayoutManager != null) {
            mLayoutManager.setEnableAnimations(DeviceClassManager.enableAnimations());
        }
        if (isTablet()) {
            if (getCompositorViewHolder() != null) {
                getCompositorViewHolder().onAccessibilityStatusChanged(enabled);
            }
        }
        if (mLayoutManager != null && mLayoutManager.overviewVisible()
                && mIsAccessibilityEnabled != enabled) {
            mLayoutManager.hideOverview(false);
            if (getTabModelSelector().getCurrentModel().getCount() == 0) {
                getCurrentTabCreator().launchNTP();
            }
        }
        mIsAccessibilityEnabled = enabled;
    }

    /**
     * Internal class which performs the intent handling operations delegated by IntentHandler.
     */
    private class InternalIntentDelegate implements IntentHandler.IntentHandlerDelegate {
        /**
         * Processes a url view intent.
         *
         * @param url The url from the intent.
         */
        @Override
        public void processUrlViewIntent(String url, String referer, String headers,
                TabOpenType tabOpenType, String externalAppId, int tabIdToBringToFront,
                boolean hasUserGesture, Intent intent) {
            if (isFromChrome(intent, externalAppId)) {
                RecordUserAction.record("MobileTabbedModeViewIntentFromChrome");
            } else {
                RecordUserAction.record("MobileTabbedModeViewIntentFromApp");
            }

            if (getBottomSheet() != null) {
                // If the bottom sheet new tab UI is showing and the tab open type is not
                // already OPEN_NEW_TAB or OPEN_NEW_INCOGNITO_TAB, set the open type so that a real
                // new tab will be created and added to the appropriate model.
                if (getBottomSheet().isShowingNewTab() && tabOpenType != TabOpenType.OPEN_NEW_TAB
                        && tabOpenType != TabOpenType.OPEN_NEW_INCOGNITO_TAB) {
                    tabOpenType = mTabModelSelectorImpl.isIncognitoSelected()
                            ? TabOpenType.OPEN_NEW_INCOGNITO_TAB
                            : TabOpenType.OPEN_NEW_TAB;
                }

                // Either a new tab is opening, a tab is being clobbered, or a tab is being brought
                // to the front. In all scenarios, the bottom sheet should be closed.
                getBottomSheet().getBottomSheetMetrics().setSheetCloseReason(
                        BottomSheetMetrics.CLOSED_BY_NAVIGATION);
                getBottomSheet().setSheetState(BottomSheet.SHEET_STATE_PEEK, true);
            }

            // We send this intent so that we can enter WebVr presentation mode if needed. This
            // call doesn't consume the intent because it also has the url that we need to load.
            VrShellDelegate.onNewIntent(intent);

            TabModel tabModel = getCurrentTabModel();
            boolean fromLauncherShortcut = IntentUtils.safeGetBooleanExtra(
                    intent, IntentHandler.EXTRA_INVOKED_FROM_SHORTCUT, false);
            switch (tabOpenType) {
                case REUSE_URL_MATCHING_TAB_ELSE_NEW_TAB:
                    // Used by the bookmarks application.
                    if (tabModel.getCount() > 0 && mUIInitialized
                            && mLayoutManager.overviewVisible()) {
                        mLayoutManager.hideOverview(true);
                    }
                    mTabModelSelectorImpl.tryToRestoreTabStateForUrl(url);
                    int tabToBeClobberedIndex = TabModelUtils.getTabIndexByUrl(tabModel, url);
                    Tab tabToBeClobbered = tabModel.getTabAt(tabToBeClobberedIndex);
                    if (tabToBeClobbered != null) {
                        TabModelUtils.setIndex(tabModel, tabToBeClobberedIndex);
                        tabToBeClobbered.reload();
                        RecordUserAction.record("MobileTabClobbered");
                    } else {
                        launchIntent(url, referer, headers, externalAppId, true, intent);
                    }
                    logMobileReceivedExternalIntent(externalAppId, intent);
                    int shortcutSource = intent.getIntExtra(
                            ShortcutHelper.EXTRA_SOURCE, ShortcutSource.UNKNOWN);
                    LaunchMetrics.recordHomeScreenLaunchIntoTab(url, shortcutSource);
                    break;
                case BRING_TAB_TO_FRONT:
                    mTabModelSelectorImpl.tryToRestoreTabStateForId(tabIdToBringToFront);

                    int tabIndex = TabModelUtils.getTabIndexById(tabModel, tabIdToBringToFront);
                    if (tabIndex == TabModel.INVALID_TAB_INDEX) {
                        TabModel otherModel =
                                getTabModelSelector().getModel(!tabModel.isIncognito());
                        tabIndex = TabModelUtils.getTabIndexById(otherModel, tabIdToBringToFront);
                        if (tabIndex != TabModel.INVALID_TAB_INDEX) {
                            getTabModelSelector().selectModel(otherModel.isIncognito());
                            TabModelUtils.setIndex(otherModel, tabIndex);
                        } else {
                            Log.e(TAG, "Failed to bring tab to front because it doesn't exist.");
                            return;
                        }
                    } else {
                        TabModelUtils.setIndex(tabModel, tabIndex);
                    }
                    logMobileReceivedExternalIntent(externalAppId, intent);
                    break;
                case CLOBBER_CURRENT_TAB:
                    // The browser triggered the intent. This happens when clicking links which
                    // can be handled by other applications (e.g. www.youtube.com links).
                    Tab currentTab = getActivityTab();
                    if (currentTab != null) {
                        currentTab.getTabRedirectHandler().updateIntent(intent);
                        int transitionType = PageTransition.LINK | PageTransition.FROM_API;
                        LoadUrlParams loadUrlParams = new LoadUrlParams(url);
                        loadUrlParams.setIntentReceivedTimestamp(mIntentHandlingTimeMs);
                        loadUrlParams.setHasUserGesture(hasUserGesture);
                        loadUrlParams.setTransitionType(IntentHandler.getTransitionTypeFromIntent(
                                intent, transitionType));
                        if (referer != null) {
                            loadUrlParams.setReferrer(
                                    new Referrer(referer, Referrer.REFERRER_POLICY_DEFAULT));
                        }
                        currentTab.loadUrl(loadUrlParams);
                        RecordUserAction.record("MobileTabClobbered");
                    } else {
                        launchIntent(url, referer, headers, externalAppId, true, intent);
                    }
                    break;
                case REUSE_APP_ID_MATCHING_TAB_ELSE_NEW_TAB:
                    openNewTab(url, referer, headers, externalAppId, intent, false);
                    break;
                case OPEN_NEW_TAB:
                    if (fromLauncherShortcut) {
                        recordLauncherShortcutAction(false);
                        reportNewTabShortcutUsed(false);
                    }

                    openNewTab(url, referer, headers, externalAppId, intent, true);
                    break;
                case OPEN_NEW_INCOGNITO_TAB:
                    if (!TextUtils.equals(externalAppId, getPackageName())) {
                        assert false : "Only Chrome is allowed to open incognito tabs";
                        Log.e(TAG, "Only Chrome is allowed to open incognito tabs");
                        return;
                    }

                    if (!PrefServiceBridge.getInstance().isIncognitoModeEnabled()) {
                        // The incognito launcher shortcut is manipulated in #onDeferredStartup(),
                        // so it's possible for a user to invoke the shortcut before it's disabled.
                        // Opening an incognito tab while incognito mode is disabled from somewhere
                        // besides the launcher shortcut is an error.
                        if (!fromLauncherShortcut) {
                            assert false : "Tried to open incognito tab while incognito disabled";
                            Log.e(TAG, "Tried to open incognito tab while incognito disabled");
                        }

                        return;
                    }

                    if (url == null || url.equals(UrlConstants.NTP_URL)) {
                        if (fromLauncherShortcut) {
                            getTabCreator(true).launchUrl(
                                    UrlConstants.NTP_URL, TabLaunchType.FROM_LAUNCHER_SHORTCUT);
                            recordLauncherShortcutAction(true);
                            reportNewTabShortcutUsed(true);
                        } else {
                            // Used by the Account management screen to open a new incognito tab.
                            // Account management screen collects its metrics separately.
                            getTabCreator(true).launchUrl(
                                    UrlConstants.NTP_URL, TabLaunchType.FROM_CHROME_UI,
                                    intent, mIntentHandlingTimeMs);
                        }
                    } else {
                        TabLaunchType launchType = IntentHandler.getTabLaunchType(intent);
                        if (launchType != null) {
                            getTabCreator(true).launchUrl(
                                    url, launchType, intent, mIntentHandlingTimeMs);
                        } else {
                            getTabCreator(true).launchUrl(
                                    url, TabLaunchType.FROM_LINK, intent, mIntentHandlingTimeMs);
                        }
                    }
                    break;
                default:
                    assert false : "Unknown TabOpenType: " + tabOpenType;
                    break;
            }
            getToolbarManager().setUrlBarFocus(false);
        }

        @Override
        public void processWebSearchIntent(String query) {
            assert false;
        }

        /**
         * Opens a new Tab with the possibility of showing it in a Custom Tab, instead.
         *
         * See IntentHandler#processUrlViewIntent() for an explanation most of the parameters.
         * @param forceNewTab If not handled by a Custom Tab, forces the new tab to be created.
         */
        private void openNewTab(String url, String referer, String headers,
                String externalAppId, Intent intent, boolean forceNewTab) {
            boolean isAllowedToReturnToExternalApp = IntentUtils.safeGetBooleanExtra(intent,
                    ChromeLauncherActivity.EXTRA_IS_ALLOWED_TO_RETURN_TO_PARENT, true);

            // Create a new tab.
            Tab newTab =
                    launchIntent(url, referer, headers, externalAppId, forceNewTab, intent);
            if (newTab != null) {
                newTab.setIsAllowedToReturnToExternalApp(isAllowedToReturnToExternalApp);
            } else {
                // TODO(twellington): This should only happen for NTPs created in Chrome Home.  See
                //                    if we should be caching setIsAllowedToReturnToExternalApp
                //                    in those cases.
                assert NewTabPage.isNTPUrl(url);
                assert getBottomSheet() != null;
            }
            logMobileReceivedExternalIntent(externalAppId, intent);
        }

        // TODO(tedchoc): Remove once we have verified that MobileTabbedModeViewIntentFromChrome
        //                and MobileTabbedModeViewIntentFromApp are suitable/more correct
        //                replacments for these.
        private void logMobileReceivedExternalIntent(String externalAppId, Intent intent) {
            RecordUserAction.record("MobileReceivedExternalIntent");
            if (isFromChrome(intent, externalAppId)) {
                RecordUserAction.record("MobileReceivedExternalIntent.Chrome");
            } else {
                RecordUserAction.record("MobileReceivedExternalIntent.App");
            }
        }

        private boolean isFromChrome(Intent intent, String externalAppId) {
            // To determine if the processed intent is from Chrome, check for any of the following:
            // 1.) The authentication token that will be added to trusted intents.
            // 2.) The app ID matches Chrome.  This value can be spoofed by other applications, but
            //     in cases where we were not able to add the authentication token this is our only
            //     indication the intent was from Chrome.
            return IntentHandler.wasIntentSenderChrome(intent)
                    || TextUtils.equals(externalAppId, getPackageName());
        }
    }

    @Override
    public void preInflationStartup() {
        super.preInflationStartup();

        // Decide whether to record startup UMA histograms. This is done  early in the main
        // Activity.onCreate() to avoid recording navigation delays when they require user input to
        // proceed. For example, FRE (First Run Experience) happens before the activity is created,
        // and triggers initialization of the native library. At the moment it seems safe to assume
        // that uninitialized native library is an indication of an application start that is
        // followed by navigation immediately without user input.
        if (!LibraryLoader.isInitialized()) {
            UmaUtils.setRunningApplicationStart(true);
        }

        CommandLine commandLine = CommandLine.getInstance();
        if (commandLine.hasSwitch(ContentSwitches.ENABLE_TEST_INTENTS)
                && getIntent() != null
                && getIntent().hasExtra(
                        ChromeTabbedActivity.INTENT_EXTRA_TEST_RENDER_PROCESS_LIMIT)) {
            int value = getIntent().getIntExtra(
                    ChromeTabbedActivity.INTENT_EXTRA_TEST_RENDER_PROCESS_LIMIT, -1);
            if (value != -1) {
                String[] args = new String[1];
                args[0] = "--" + ContentSwitches.RENDER_PROCESS_LIMIT
                        + "=" + Integer.toString(value);
                commandLine.appendSwitchesAndArguments(args);
            }
        }

        supportRequestWindowFeature(Window.FEATURE_ACTION_MODE_OVERLAY);

        // We are starting from history with a URL after data has been cleared. On Samsung this
        // can happen after user clears data and clicks on a recents item on pre-L devices.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP
                && getIntent().getData() != null
                && (getIntent().getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0
                && OmahaBase.isProbablyFreshInstall(this)) {
            getIntent().setData(null);
        }

        launchFirstRunExperience();
    }

    @Override
    protected int getControlContainerLayoutId() {
        if (FeatureUtilities.isChromeHomeEnabled()) {
            return R.layout.bottom_control_container;
        }
        return R.layout.control_container;
    }

    @Override
    protected int getToolbarLayoutId() {
        if (DeviceFormFactor.isTablet()) return R.layout.toolbar_tablet;

        if (FeatureUtilities.isChromeHomeEnabled()) return R.layout.bottom_toolbar_phone;
        return R.layout.toolbar_phone;
    }

    @Override
    public void postInflationStartup() {
        super.postInflationStartup();

        // Critical path for startup. Create the minimum objects needed
        // to allow a blank screen draw (without depending on any native code)
        // and then yield ASAP.
        if (isFinishing()) return;

        // Don't show the keyboard until user clicks in.
        getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        mContentContainer = (ViewGroup) findViewById(android.R.id.content);
        mControlContainer = (ToolbarControlContainer) findViewById(R.id.control_container);

        mUndoBarPopupController = new UndoBarController(this, mTabModelSelectorImpl,
                getSnackbarManager());
    }

    @Override
    protected void initializeToolbar() {
        super.initializeToolbar();
        if (DeviceFormFactor.isTablet()) {
            getToolbarManager().setShouldUpdateToolbarPrimaryColor(false);
        }
    }

    @Override
    protected TabModelSelector createTabModelSelector() {
        assert mTabModelSelectorImpl == null;

        Bundle savedInstanceState = getSavedInstanceState();

        // We determine the model as soon as possible so every systems get initialized coherently.
        boolean startIncognito = savedInstanceState != null
                && savedInstanceState.getBoolean("is_incognito_selected", false);
        int index = savedInstanceState != null ? savedInstanceState.getInt(WINDOW_INDEX, 0) : 0;

        mTabModelSelectorImpl = (TabModelSelectorImpl)
                TabWindowManager.getInstance().requestSelector(this, this, index);
        if (mTabModelSelectorImpl == null) {
            Toast.makeText(this, getString(R.string.unsupported_number_of_windows),
                    Toast.LENGTH_LONG).show();
            finish();
            return null;
        }

        mTabModelSelectorTabObserver = new TabModelSelectorTabObserver(mTabModelSelectorImpl) {
            private boolean mIsFirstPageLoadStart = true;

            @Override
            public void onPageLoadStarted(Tab tab, String url) {
                // Discard startup navigation measurements when the user interfered and started the
                // 2nd navigation (in activity lifetime) in parallel.
                if (!mIsFirstPageLoadStart) {
                    UmaUtils.setRunningApplicationStart(false);
                } else {
                    mIsFirstPageLoadStart = false;
                }
            }

            @Override
            public void onPageLoadFinished(final Tab tab) {
                mAppIndexingUtil.extractCopylessPasteMetadata(tab);
            }

            @Override
            public void onDidFinishNavigation(Tab tab, String url, boolean isInMainFrame,
                    boolean isErrorPage, boolean hasCommitted, boolean isSameDocument,
                    boolean isFragmentNavigation, Integer pageTransition, int errorCode,
                    int httpStatusCode) {
                if (hasCommitted && isInMainFrame) {
                    DataReductionPromoInfoBar.maybeLaunchPromoInfoBar(ChromeTabbedActivity.this,
                            tab.getWebContents(), url, tab.isShowingErrorPage(),
                            isFragmentNavigation, httpStatusCode);
                }
            }
        };

        if (startIncognito) mTabModelSelectorImpl.selectModel(true);

        return mTabModelSelectorImpl;
    }

    @Override
    protected AppMenuPropertiesDelegate createAppMenuPropertiesDelegate() {
        return new AppMenuPropertiesDelegate(this) {
            private boolean showDataSaverFooter() {
                return getBottomSheet() == null
                        && DataReductionProxySettings.getInstance()
                                   .shouldUseDataReductionMainMenuItem();
            }

            @Override
            public int getFooterResourceId() {
                if (getBottomSheet() != null) {
                    boolean isPageMenu = !isTablet() && !isInOverviewMode();
                    return isPageMenu ? R.layout.icon_row_menu_footer : 0;
                }

                return showDataSaverFooter() ? R.layout.data_reduction_main_menu_footer : 0;
            }

            @Override
            public boolean shouldShowFooter(int maxMenuHeight) {
                if (showDataSaverFooter()) {
                    return maxMenuHeight >= getResources().getDimension(
                                                    R.dimen.data_saver_menu_footer_min_show_height);
                }
                return super.shouldShowFooter(maxMenuHeight);
            }
        };
    }

    @Override
    protected Pair<TabbedModeTabCreator, TabbedModeTabCreator> createTabCreators() {
        return Pair.create(
                new TabbedModeTabCreator(this, getWindowAndroid(), false),
                new TabbedModeTabCreator(this, getWindowAndroid(), true));
    }

    /**
     * Launch the First Run flow to set up Chrome.
     * There are two different pathways that can occur:
     * 1) The First Run Experience activity is run, which walks the user through the ToS, signing
     * in, and turning on UMA reporting.  This happens in most cases.
     * 2) We automatically try to sign-in the user and skip the FRE activity, then ask the user to
     * turn on UMA reporting some time later using an InfoBar.  This happens if Chrome is opened
     * with an Intent to view a URL, or if we're on a Nexus device where the user has already
     * been exposed to the ToS and Privacy Notice.
     */
    private void launchFirstRunExperience() {
        if (mIsOnFirstRun) {
            mTabModelSelectorImpl.clearState();
            return;
        }

        final Intent freIntent =
                FirstRunFlowSequencer.checkIfFirstRunIsNecessary(this, getIntent(), false);
        if (freIntent == null) return;

        mIsOnFirstRun = true;

        // TODO(dtrainor): Investigate this further and revert once Android pushes fix?
        // Posting this due to Android bug where we apparently are stopping a
        // non-resumed activity.  That statement looks incorrect, but need to not hit
        // the runtime exception here.
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                startActivityForResult(freIntent, FIRST_RUN_EXPERIENCE_RESULT);
            }
        });
    }

    @Override
    protected void onDeferredStartup() {
        super.onDeferredStartup();
        DeferredStartupHandler.getInstance().addDeferredTask(new Runnable() {
            @Override
            public void run() {
                ActivityManager am =
                        (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                RecordHistogram.recordSparseSlowlyHistogram(
                        "MemoryAndroid.DeviceMemoryClass", am.getMemoryClass());

                AutocompleteController.nativePrefetchZeroSuggestResults();

                LauncherShortcutActivity.updateIncognitoShortcut(ChromeTabbedActivity.this);
            }
        });
    }

    @Override
    protected void recordIntentToCreationTime(long timeMs) {
        super.recordIntentToCreationTime(timeMs);
        RecordHistogram.recordCustomTimesHistogram("MobileStartup.IntentToCreationTime.TabbedMode",
                timeMs, 1, TimeUnit.SECONDS.toMillis(30), TimeUnit.MILLISECONDS, 50);
    }

    @Override
    protected boolean isStartedUpCorrectly(Intent intent) {
        // If tabs from this instance were merged into a different ChromeTabbedActivity instance
        // and the other instance is still running, then this instance should not be created. This
        // may happen if the process is restarted e.g. on upgrade or from about://flags.
        // See crbug.com/657418
        boolean tabsMergedIntoAnotherInstance =
                sMergedInstanceTaskId != 0 && sMergedInstanceTaskId != getTaskId();

        // Since a static is used to track the merged instance task id, it is possible that
        // sMergedInstanceTaskId is still set even though the associated task is not running.
        boolean mergedInstanceTaskStillRunning = isMergedInstanceTaskRunning();

        if (tabsMergedIntoAnotherInstance && mergedInstanceTaskStillRunning) {
            // Currently only two instances of ChromeTabbedActivity may be running at any given
            // time. If tabs were merged into another instance and this instance is being killed due
            // to incorrect startup, then no other instances should exist. Reset the merged instance
            // task id.
            setMergedInstanceTaskId(0);
            return false;
        } else if (!mergedInstanceTaskStillRunning) {
            setMergedInstanceTaskId(0);
        }

        return super.isStartedUpCorrectly(intent);
    }

    @Override
    public void terminateIncognitoSession() {
        getTabModelSelector().getModel(true).closeAllTabs();
    }

    @Override
    public boolean onMenuOrKeyboardAction(final int id, boolean fromMenu) {
        final Tab currentTab = getActivityTab();
        boolean currentTabIsNtp = currentTab != null && NewTabPage.isNTPUrl(currentTab.getUrl());
        if (id == R.id.move_to_other_window_menu_id) {
            if (currentTab != null) moveTabToOtherWindow(currentTab);
        } else if (id == R.id.new_tab_menu_id) {
            getTabModelSelector().getModel(false).commitAllTabClosures();
            RecordUserAction.record("MobileMenuNewTab");
            RecordUserAction.record("MobileNewTabOpened");
            reportNewTabShortcutUsed(false);
            getTabCreator(false).launchNTP();

            mLocaleManager.showSearchEnginePromoIfNeeded(this, null);
        } else if (id == R.id.new_incognito_tab_menu_id) {
            if (PrefServiceBridge.getInstance().isIncognitoModeEnabled()) {
                getTabModelSelector().getModel(false).commitAllTabClosures();
                // This action must be recorded before opening the incognito tab since UMA actions
                // are dropped when an incognito tab is open.
                RecordUserAction.record("MobileMenuNewIncognitoTab");
                RecordUserAction.record("MobileNewTabOpened");
                reportNewTabShortcutUsed(true);
                getTabCreator(true).launchNTP();
            }
        } else if (id == R.id.all_bookmarks_menu_id) {
            if (currentTab != null) {
                getCompositorViewHolder().hideKeyboard(new Runnable() {
                    @Override
                    public void run() {
                        StartupMetrics.getInstance().recordOpenedBookmarks();
                        BookmarkUtils.showBookmarkManager(ChromeTabbedActivity.this);
                    }
                });
                if (currentTabIsNtp) {
                    NewTabPageUma.recordAction(NewTabPageUma.ACTION_OPENED_BOOKMARKS_MANAGER);
                }
                RecordUserAction.record("MobileMenuAllBookmarks");
            }
        } else if (id == R.id.recent_tabs_menu_id) {
            if (currentTab != null) {
                currentTab.loadUrl(new LoadUrlParams(
                        UrlConstants.RECENT_TABS_URL,
                        PageTransition.AUTO_BOOKMARK));
                if (currentTabIsNtp) {
                    NewTabPageUma.recordAction(NewTabPageUma.ACTION_OPENED_RECENT_TABS_MANAGER);
                }
                RecordUserAction.record("MobileMenuRecentTabs");
            }
        } else if (id == R.id.close_all_tabs_menu_id) {
            // Close both incognito and normal tabs
            getTabModelSelector().closeAllTabs();
            RecordUserAction.record("MobileMenuCloseAllTabs");
        } else if (id == R.id.close_all_incognito_tabs_menu_id) {
            // Close only incognito tabs
            getTabModelSelector().getModel(true).closeAllTabs();
            // TODO(nileshagrawal) Record unique action for this. See bug http://b/5542946.
            RecordUserAction.record("MobileMenuCloseAllTabs");
        } else if (id == R.id.find_in_page_id) {
            mFindToolbarManager.showToolbar();
            if (getContextualSearchManager() != null) {
                getContextualSearchManager().hideContextualSearch(StateChangeReason.UNKNOWN);
            }
            if (fromMenu) {
                RecordUserAction.record("MobileMenuFindInPage");
            } else {
                RecordUserAction.record("MobileShortcutFindInPage");
            }
        } else if (id == R.id.focus_url_bar) {
            boolean isUrlBarVisible = !mLayoutManager.overviewVisible()
                    && (!isTablet() || getCurrentTabModel().getCount() != 0);
            if (isUrlBarVisible) {
                getToolbarManager().setUrlBarFocus(true);
            }
        } else if (id == R.id.downloads_menu_id) {
            DownloadUtils.showDownloadManager(this, currentTab);
            if (currentTabIsNtp) {
                NewTabPageUma.recordAction(NewTabPageUma.ACTION_OPENED_DOWNLOADS_MANAGER);
            }
            RecordUserAction.record("MobileMenuDownloadManager");
        } else if (id == R.id.open_recently_closed_tab) {
            TabModel currentModel = mTabModelSelectorImpl.getCurrentModel();
            if (!currentModel.isIncognito()) currentModel.openMostRecentlyClosedTab();
            RecordUserAction.record("MobileTabClosedUndoShortCut");
        } else if (id == R.id.enter_vr_id) {
            VrShellDelegate.enterVrIfNecessary();
        } else {
            return super.onMenuOrKeyboardAction(id, fromMenu);
        }
        return true;
    }

    @Override
    protected void onOmniboxFocusChanged(boolean hasFocus) {
        super.onOmniboxFocusChanged(hasFocus);

        mMainIntentMetrics.onOmniboxFocused();
    }

    private void recordBackPressedUma(String logMessage, @BackPressedResult int action) {
        Log.i(TAG, "Back pressed: " + logMessage);
        RecordHistogram.recordEnumeratedHistogram(
                "Android.Activity.ChromeTabbedActivity.SystemBackAction",
                action, BACK_PRESSED_COUNT);
    }

    private void recordLauncherShortcutAction(boolean isIncognito) {
        if (isIncognito) {
            RecordUserAction.record("Android.LauncherShortcut.NewIncognitoTab");
        } else {
            RecordUserAction.record("Android.LauncherShortcut.NewTab");
        }
    }

    private void moveTabToOtherWindow(Tab tab) {
        Class<? extends Activity> targetActivity =
                MultiWindowUtils.getInstance().getOpenInOtherWindowActivity(this);
        if (targetActivity == null) return;

        Intent intent = new Intent(this, targetActivity);
        MultiWindowUtils.setOpenInOtherWindowIntentExtras(intent, this, targetActivity);
        MultiWindowUtils.onMultiInstanceModeStarted();

        tab.detachAndStartReparenting(intent, null, null);
    }

    @Override
    public boolean handleBackPressed() {
        if (!mUIInitialized) return false;
        final Tab currentTab = getActivityTab();

        // Close the bottom sheet before trying to navigate back. If the tab is on the NTP, fall
        // through to decide if the browser should be sent into the background.
        if (getBottomSheet() != null
                && getBottomSheet().getSheetState() != BottomSheet.SHEET_STATE_PEEK
                && (currentTab == null
                           || !(currentTab.getNativePage() instanceof ChromeHomeNewTabPage))) {
            getBottomSheet().setSheetState(BottomSheet.SHEET_STATE_PEEK, true);
            return true;
        }

        if (currentTab == null) {
            recordBackPressedUma("currentTab is null", BACK_PRESSED_TAB_IS_NULL);
            moveTaskToBack(true);
            return true;
        }

        // If we are in overview mode and not a tablet, then leave overview mode on back.
        if (mLayoutManager.overviewVisible() && !isTablet()) {
            recordBackPressedUma("Hid overview", BACK_PRESSED_EXITED_TAB_SWITCHER);
            mLayoutManager.hideOverview(true);
            return true;
        }

        if (exitFullscreenIfShowing()) {
            recordBackPressedUma("Exited fullscreen", BACK_PRESSED_EXITED_FULLSCREEN);
            return true;
        }

        if (getToolbarManager().back()) {
            recordBackPressedUma("Navigating backward", BACK_PRESSED_NAVIGATED_BACK);
            RecordUserAction.record("MobileTabClobbered");
            return true;
        }

        // If the current tab url is HELP_URL, then the back button should close the tab to
        // get back to the previous state. The reason for startsWith check is that the
        // actual redirected URL is a different system language based help url.
        final TabLaunchType type = currentTab.getLaunchType();
        final boolean helpUrl = currentTab.getUrl().startsWith(HELP_URL_PREFIX);
        if (type == TabLaunchType.FROM_CHROME_UI && helpUrl) {
            getCurrentTabModel().closeTab(currentTab);
            recordBackPressedUma("Closed tab for help URL", BACK_PRESSED_HELP_URL_CLOSED);
            return true;
        }

        // [true]: Reached the bottom of the back stack on a tab the user did not explicitly
        // create (i.e. it was created by an external app or opening a link in background, etc).
        // [false]: Reached the bottom of the back stack on a tab that the user explicitly
        // created (e.g. selecting "new tab" from menu).
        final int parentId = currentTab.getParentId();
        final boolean shouldCloseTab = type == TabLaunchType.FROM_LINK
                || type == TabLaunchType.FROM_EXTERNAL_APP
                || type == TabLaunchType.FROM_LONGPRESS_FOREGROUND
                || type == TabLaunchType.FROM_LONGPRESS_BACKGROUND
                || (type == TabLaunchType.FROM_RESTORE && parentId != Tab.INVALID_TAB_ID);

        // Minimize the app if either:
        // - we decided not to close the tab
        // - we decided to close the tab, but it was opened by an external app, so we will go
        //   exit Chrome on top of closing the tab
        final boolean minimizeApp = !shouldCloseTab || currentTab.isCreatedForExternalApp();
        if (minimizeApp) {
            // TODO(mthiesse): We never want to close Chrome through the in-vr back button (but we
            // want to reuse CTA logic for how the back button should otherwise behave). We should
            // refactor the behaviour in this function so that we can either re-use the parts of
            // this behaviour we want, or be able to know in advance whether or not clicking the
            // back button would close Chrome so that we can disable it.
            if (VrShellDelegate.isInVr()) return true;
            if (shouldCloseTab) {
                recordBackPressedUma("Minimized and closed tab", BACK_PRESSED_MINIMIZED_TAB_CLOSED);
                mActivityStopMetrics.setStopReason(ActivityStopMetrics.STOP_REASON_BACK_BUTTON);
                sendToBackground(currentTab);
                return true;
            } else {
                recordBackPressedUma("Minimized, kept tab", BACK_PRESSED_MINIMIZED_NO_TAB_CLOSED);
                mActivityStopMetrics.setStopReason(ActivityStopMetrics.STOP_REASON_BACK_BUTTON);
                sendToBackground(null);
                return true;
            }
        } else if (shouldCloseTab) {
            recordBackPressedUma("Tab closed", BACK_PRESSED_TAB_CLOSED);
            getCurrentTabModel().closeTab(currentTab, true, false, false);
            return true;
        }

        assert false : "The back button should have already been handled by this point";
        recordBackPressedUma("Unhandled", BACK_PRESSED_NOTHING_HAPPENED);
        return false;
    }

    /**
     * Sends this Activity to the background.
     *
     * @param tabToClose Tab that will be closed once the app is not visible.
     */
    private void sendToBackground(@Nullable final Tab tabToClose) {
        Log.i(TAG, "sendToBackground(): " + tabToClose);
        moveTaskToBack(true);
        if (tabToClose != null) {
            // In the case of closing a tab upon minimization, don't allow the close action to
            // happen until after our app is minimized to make sure we don't get a brief glimpse of
            // the newly active tab before we exit Chrome.
            //
            // If the runnable doesn't run before the Activity dies, Chrome won't crash but the tab
            // won't be closed (crbug.com/587565).
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    boolean hasNextTab =
                            getCurrentTabModel().getNextTabIfClosed(tabToClose.getId()) != null;
                    getCurrentTabModel().closeTab(tabToClose, false, true, false);

                    // If there is no next tab to open, enter overview mode.
                    if (!hasNextTab) mLayoutManager.showOverview(false);
                }
            }, CLOSE_TAB_ON_MINIMIZE_DELAY_MS);
        }
    }

    /**
     * Launch a URL from an intent.
     *
     * @param url           The url from the intent.
     * @param referer       Optional referer URL to be used.
     * @param headers       Optional headers to be sent when opening the URL.
     * @param externalAppId External app id.
     * @param forceNewTab   Whether to force the URL to be launched in a new tab or to fall
     *                      back to the default behavior for making that determination.
     * @param intent        The original intent.
     */
    private Tab launchIntent(String url, String referer, String headers,
            String externalAppId, boolean forceNewTab, Intent intent) {
        if (mUIInitialized) {
            mLayoutManager.hideOverview(false);
            getToolbarManager().finishAnimations();
        }
        if (TextUtils.equals(externalAppId, getPackageName())) {
            // If the intent was launched by chrome, open the new tab in the appropriate model.
            // Using FROM_LINK ensures the tab is parented to the current tab, which allows
            // the back button to close these tabs and restore selection to the previous tab.
            boolean isIncognito = IntentUtils.safeGetBooleanExtra(intent,
                    IntentHandler.EXTRA_OPEN_NEW_INCOGNITO_TAB, false);
            boolean fromLauncherShortcut = IntentUtils.safeGetBooleanExtra(
                    intent, IntentHandler.EXTRA_INVOKED_FROM_SHORTCUT, false);
            LoadUrlParams loadUrlParams = new LoadUrlParams(url);
            loadUrlParams.setIntentReceivedTimestamp(mIntentHandlingTimeMs);
            loadUrlParams.setVerbatimHeaders(headers);
            return getTabCreator(isIncognito).createNewTab(
                    loadUrlParams,
                    fromLauncherShortcut ? TabLaunchType.FROM_LAUNCHER_SHORTCUT
                            : TabLaunchType.FROM_LINK,
                    null,
                    intent);
        } else {
            return getTabCreator(false).launchUrlFromExternalApp(url, referer, headers,
                    externalAppId, forceNewTab, intent, mIntentHandlingTimeMs);
        }
    }

    private void toggleOverview() {
        Tab currentTab = getActivityTab();
        ContentViewCore contentViewCore =
                currentTab != null ? currentTab.getContentViewCore() : null;

        if (!mLayoutManager.overviewVisible()) {
            getCompositorViewHolder().hideKeyboard(new Runnable() {
                @Override
                public void run() {
                    mLayoutManager.showOverview(true);
                }
            });
            if (contentViewCore != null) {
                contentViewCore.setAccessibilityState(false);
            }
        } else {
            Layout activeLayout = mLayoutManager.getActiveLayout();
            if (activeLayout instanceof StackLayout) {
                ((StackLayout) activeLayout).commitOutstandingModelState(LayoutManager.time());
            }
            if (getCurrentTabModel().getCount() != 0) {
                // Don't hide overview if current tab stack is empty()
                mLayoutManager.hideOverview(true);

                // hideOverview could change the current tab.  Update the local variables.
                currentTab = getActivityTab();
                contentViewCore = currentTab != null ? currentTab.getContentViewCore() : null;

                if (contentViewCore != null) {
                    contentViewCore.setAccessibilityState(true);
                }
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        CipherFactory.getInstance().saveToBundle(outState);
        outState.putBoolean("is_incognito_selected", getCurrentTabModel().isIncognito());
        outState.putBoolean(FRE_RUNNING, mIsOnFirstRun);
        outState.putInt(WINDOW_INDEX,
                TabWindowManager.getInstance().getIndexForWindow(this));
    }

    @Override
    public void onDestroyInternal() {
        if (mLayoutManager != null) mLayoutManager.removeOverviewModeObserver(this);

        if (mTabModelSelectorTabObserver != null) {
            mTabModelSelectorTabObserver.destroy();
            mTabModelSelectorTabObserver = null;
        }

        if (mTabModelObserver != null) mTabModelObserver.destroy();

        if (mUndoBarPopupController != null) {
            mUndoBarPopupController.destroy();
            mUndoBarPopupController = null;
        }

        super.onDestroyInternal();
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        // The conditions are expressed using ranges to capture intermediate levels possibly added
        // to the API in the future.
        if ((level >= TRIM_MEMORY_RUNNING_LOW && level < TRIM_MEMORY_UI_HIDDEN)
                || level >= TRIM_MEMORY_MODERATE) {
            NativePageAssassin.getInstance().freezeAllHiddenPages();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        Boolean result = KeyboardShortcuts.dispatchKeyEvent(event, this, mUIInitialized);
        return result != null ? result : super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (!mUIInitialized) {
            return super.onKeyDown(keyCode, event);
        }
        boolean isCurrentTabVisible = !mLayoutManager.overviewVisible()
                && (!isTablet() || getCurrentTabModel().getCount() != 0);
        return KeyboardShortcuts.onKeyDown(event, this, isCurrentTabVisible, true)
                || super.onKeyDown(keyCode, event);
    }

    @Override
    public void onProvideKeyboardShortcuts(List<KeyboardShortcutGroup> data, Menu menu,
            int deviceId) {
        data.addAll(KeyboardShortcuts.createShortcutGroup(this));
    }

    @VisibleForTesting
    public View getTabsView() {
        return getCompositorViewHolder();
    }

    @VisibleForTesting
    public LayoutManagerChrome getLayoutManager() {
        return (LayoutManagerChrome) getCompositorViewHolder().getLayoutManager();
    }

    @VisibleForTesting
    public Layout getOverviewListLayout() {
        return getLayoutManager().getOverviewListLayout();
    }

    @Override
    public boolean isOverlayVisible() {
        return getCompositorViewHolder() != null && !getCompositorViewHolder().isTabInteractive();
    }

    // App Menu related code -----------------------------------------------------------------------

    @Override
    public boolean shouldShowAppMenu() {
        // The popup menu relies on the model created during the full UI initialization, so do not
        // attempt to show the menu until the UI creation has finished.
        if (!mUIInitialized) return false;

        // Do not show the menu if we are in find in page view.
        if (mFindToolbarManager != null && mFindToolbarManager.isShowing() && !isTablet()) {
            return false;
        }

        return super.shouldShowAppMenu();
    }

    @Override
    protected void showAppMenuForKeyboardEvent() {
        if (!mUIInitialized || isFullscreenVideoPlaying()) return;
        super.showAppMenuForKeyboardEvent();
    }

    private boolean isFullscreenVideoPlaying() {
        View view = ContentVideoView.getContentVideoView();
        return view != null && view.getContext() == this;
    }

    @Override
    public boolean isInOverviewMode() {
        return mLayoutManager != null && mLayoutManager.overviewVisible();
    }

    @Override
    protected IntentHandlerDelegate createIntentHandlerDelegate() {
        return new InternalIntentDelegate();
    }

    @Override
    public void onSceneChange(Layout layout) {
        super.onSceneChange(layout);
        if (!layout.shouldDisplayContentOverlay()) mTabModelSelectorImpl.onTabsViewShown();
    }

    @Override
    public void onOverviewModeStartedShowing(boolean showToolbar) {
        if (mFindToolbarManager != null) mFindToolbarManager.hideToolbar();
        if (getAssistStatusHandler() != null) getAssistStatusHandler().updateAssistState();
        if (getAppMenuHandler() != null) getAppMenuHandler().hideAppMenu();
        ApiCompatibilityUtils.setStatusBarColor(getWindow(), Color.BLACK);
        StartupMetrics.getInstance().recordOpenedTabSwitcher();
    }

    @Override
    public void onOverviewModeFinishedShowing() {}

    @Override
    public void onOverviewModeStartedHiding(boolean showToolbar, boolean delayAnimation) {
        if (getAppMenuHandler() != null) getAppMenuHandler().hideAppMenu();
    }

    @Override
    public void onOverviewModeFinishedHiding() {
        if (getAssistStatusHandler() != null) getAssistStatusHandler().updateAssistState();
        if (getActivityTab() != null) {
            setStatusBarColor(getActivityTab(), getActivityTab().getThemeColor());
        }
    }

    @Override
    protected void setStatusBarColor(Tab tab, int color) {
        if (DeviceFormFactor.isTablet()) return;
        super.setStatusBarColor(tab, isInOverviewMode() ? Color.BLACK : color);
    }

    @Override
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode) {
        super.onMultiWindowModeChanged(isInMultiWindowMode);
        if (!FeatureUtilities.isTabModelMergingEnabled()) return;
        if (!isInMultiWindowMode) {
            // If the activity is currently resumed when multi-window mode is exited, try to merge
            // tabs from the other activity instance.
            if (ApplicationStatus.getStateForActivity(this) == ActivityState.RESUMED) {
                maybeMergeTabs();
            } else {
                mMergeTabsOnResume = true;
            }
        }
    }

    /**
     * Writes the tab state to disk.
     */
    @VisibleForTesting
    public void saveState() {
        mTabModelSelectorImpl.saveState();
    }

    /**
     * Merges tabs from a second ChromeTabbedActivity instance if necesssary and calls
     * finishAndRemoveTask() on the other activity.
     */
    @TargetApi(Build.VERSION_CODES.M)
    @VisibleForTesting
    public void maybeMergeTabs() {
        if (!FeatureUtilities.isTabModelMergingEnabled()) return;

        Class<?> otherWindowActivityClass =
                MultiWindowUtils.getInstance().getOpenInOtherWindowActivity(this);

        // 1. Find the other activity's task if it's still running so that it can be removed from
        //    Android recents.
        ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        List<ActivityManager.AppTask> appTasks = activityManager.getAppTasks();
        ActivityManager.AppTask otherActivityTask = null;
        for (ActivityManager.AppTask task : appTasks) {
            if (task.getTaskInfo() == null || task.getTaskInfo().baseActivity == null) continue;
            String baseActivity = task.getTaskInfo().baseActivity.getClassName();
            if (baseActivity.equals(otherWindowActivityClass.getName())) {
                otherActivityTask = task;
            }
        }

        if (otherActivityTask != null) {
            for (WeakReference<Activity> activityRef : ApplicationStatus.getRunningActivities()) {
                Activity activity = activityRef.get();
                if (activity == null) continue;
                // 2. If the other activity is still running (not destroyed), save its tab list.
                //    Saving the tab list prevents missing tabs or duplicate tabs if tabs have been
                //    reparented.
                // TODO(twellington): saveState() gets called in onStopWithNative() after the merge
                // starts, causing some duplicate work to be done. Avoid the redundancy.
                if (activity.getClass().equals(otherWindowActivityClass)) {
                    ((ChromeTabbedActivity) activity).saveState();
                    break;
                }
            }
            // 3. Kill the other activity's task to remove it from Android recents.
            otherActivityTask.finishAndRemoveTask();
        }

        // 4. Ask TabPersistentStore to merge state.
        RecordUserAction.record("Android.MergeState.Live");
        mTabModelSelectorImpl.mergeState();

        setMergedInstanceTaskId(getTaskId());
    }

    @Override
    public void onEnterVr() {
        super.onEnterVr();
        mControlContainer.setVisibility(View.INVISIBLE);
    }

    @Override
    public void onExitVr() {
        super.onExitVr();
        mControlContainer.setVisibility(View.VISIBLE);
    }

    /**
     * Reports that a new tab launcher shortcut was selected or an action equivalent to a shortcut
     * was performed.
     * @param isIncognito Whether the shortcut or action created a new incognito tab.
     */
    @TargetApi(Build.VERSION_CODES.N_MR1)
    private void reportNewTabShortcutUsed(boolean isIncognito) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return;

        ShortcutManager shortcutManager = getSystemService(ShortcutManager.class);
        shortcutManager.reportShortcutUsed(
                isIncognito ? "new-incognito-tab-shortcut" : "new-tab-shortcut");
    }

    @Override
    protected ChromeFullscreenManager createFullscreenManager() {
        return new ChromeFullscreenManager(this, FeatureUtilities.isChromeHomeEnabled());
    }

    /**
     * Should be called when multi-instance mode is started.
     */
    public static void onMultiInstanceModeStarted() {
        // When a second instance is created, the merged instance task id should be cleared.
        setMergedInstanceTaskId(0);
    }

    private static void setMergedInstanceTaskId(int mergedInstanceTaskId) {
        sMergedInstanceTaskId = mergedInstanceTaskId;
    }

    @SuppressLint("NewApi")
    private boolean isMergedInstanceTaskRunning() {
        if (!FeatureUtilities.isTabModelMergingEnabled() || sMergedInstanceTaskId == 0) {
            return false;
        }

        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (AppTask task : manager.getAppTasks()) {
            RecentTaskInfo info = DocumentUtils.getTaskInfoFromTask(task);
            if (info == null) continue;
            if (info.id == sMergedInstanceTaskId) return true;
        }
        return false;
    }

    @Override
    public boolean supportsFullscreenActivity() {
        return true;
    }
}

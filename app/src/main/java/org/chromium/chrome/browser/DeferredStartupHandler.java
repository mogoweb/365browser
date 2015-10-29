// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.content.Context;
import android.os.AsyncTask;
import android.text.TextUtils;

import org.chromium.base.CommandLine;
import org.chromium.base.FieldTrialList;
import org.chromium.base.PowerMonitor;
import org.chromium.base.SysUtils;
import org.chromium.base.ThreadUtils;
import org.chromium.base.TraceEvent;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.bookmarkswidget.BookmarkThumbnailWidgetProviderBase;
import org.chromium.chrome.browser.crash.CrashFileManager;
import org.chromium.chrome.browser.crash.MinidumpUploadService;
import org.chromium.chrome.browser.download.DownloadManagerService;
import org.chromium.chrome.browser.media.MediaNotificationService;
import org.chromium.chrome.browser.partnerbookmarks.PartnerBookmarksShim;
import org.chromium.chrome.browser.precache.PrecacheLauncher;
import org.chromium.chrome.browser.preferences.ChromePreferenceManager;
import org.chromium.chrome.browser.preferences.privacy.PrivacyPreferencesManager;
import org.chromium.chrome.browser.share.ShareHelper;
import org.chromium.components.variations.VariationsAssociatedData;
import org.chromium.content.browser.ChildProcessLauncher;

/**
 * Handler for application level tasks to be completed on deferred startup.
 */
public class DeferredStartupHandler {
    // Constants for trial of moderate binding support.
    private static final String MODERATE_BINDING_TRIAL_NAME = "MobileModerateBinding";
    private static final String MODERATE_BINDING_LOW_REDUCE_RATIO_PARAM = "low_reduce_ratio";
    private static final String MODERATE_BINDING_HIGH_REDUCE_RATIO_PARAM = "high_reduce_ratio";
    private static final String MODERATE_BINDING_ENABLED_PARAM = "enabled";
    private static final String MODERATE_BINDING_SWITCH_PREFIX = "moderate-binding-";
    private static final float DEFAULT_MODERATE_BINDING_REDUCE_RATIO = 0.25f;

    private static DeferredStartupHandler sDeferredStartupHandler;
    private boolean mDeferredStartupComplete;

    /**
     * This class is an application specific object that handles the deferred startup.
     * @return The singleton instance of {@link DeferredStartupHandler}.
     */
    public static DeferredStartupHandler getInstance() {
        if (sDeferredStartupHandler == null) {
            sDeferredStartupHandler = new DeferredStartupHandler();
        }
        return sDeferredStartupHandler;
    }

    private DeferredStartupHandler() { }

    /**
     * Handle application level deferred startup tasks that can be lazily done after all
     * the necessary initialization has been completed. Any calls requiring network access should
     * probably go here.
     * @param application The application object to use for context.
     * @param crashDumpUploadingDisabled Whether crash dump uploading should be disabled.
     */
    public void onDeferredStartup(final ChromeApplication application,
            final boolean crashDumpUploadingDisabled) {
        if (mDeferredStartupComplete) return;
        ThreadUtils.assertOnUiThread();

        // Punt all tasks that may block the UI thread off onto a background thread.
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                try {
                    TraceEvent.begin("ChromeBrowserInitializer.onDeferredStartup.doInBackground");
                    if (crashDumpUploadingDisabled) {
                        PrivacyPreferencesManager.getInstance(application).disableCrashUploading();
                    } else {
                        MinidumpUploadService.tryUploadAllCrashDumps(application);
                    }
                    CrashFileManager crashFileManager =
                            new CrashFileManager(application.getCacheDir());
                    crashFileManager.cleanOutAllNonFreshMinidumpFiles();

                    MinidumpUploadService.storeBreakpadUploadAttemptsInUma(
                                ChromePreferenceManager.getInstance(application));

                    // Force a widget refresh in order to wake up any possible zombie widgets.
                    // This is needed to ensure the right behavior when the process is suddenly
                    // killed.
                    BookmarkThumbnailWidgetProviderBase.refreshAllWidgets(application);

                    // Initialize whether or not precaching is enabled.
                    PrecacheLauncher.updatePrecachingEnabled(application);

                    return null;
                } finally {
                    TraceEvent.end("ChromeBrowserInitializer.onDeferredStartup.doInBackground");
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

        AfterStartupTaskUtils.setStartupComplete();

        // TODO(aruslan): http://b/6397072 This will be moved elsewhere
        PartnerBookmarksShim.kickOffReading(application);

        PowerMonitor.create(application);

        // Starts syncing with GSA.
        application.createGsaHelper().startSync();

        DownloadManagerService.getDownloadManagerService(application)
                .clearPendingDownloadNotifications();

        application.initializeSharedClasses();

        ShareHelper.clearSharedScreenshots(application);

        // Clear any media notifications that existed when Chrome was last killed.
        MediaNotificationService.clearMediaNotifications(application);

        startModerateBindingManagementIfNeeded(application);

        String customTabsTrialGroupName = FieldTrialList.findFullName("CustomTabs");
        if (customTabsTrialGroupName.equals("Disabled")) {
            ChromePreferenceManager.getInstance(application).setCustomTabsEnabled(false);
        } else if (customTabsTrialGroupName.equals("Enabled")
                || customTabsTrialGroupName.equals("DisablePrerender")) {
            ChromePreferenceManager.getInstance(application).setCustomTabsEnabled(true);
        }

        mDeferredStartupComplete = true;
    }

    private static float parseFloat(String value, float defaultValue) {
        try {
            return TextUtils.isEmpty(value) ? defaultValue : Float.parseFloat(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static String getModerateBindingConfigValue(String param) {
        String switchName = MODERATE_BINDING_SWITCH_PREFIX + param;
        if (CommandLine.getInstance().hasSwitch(switchName)) {
            return CommandLine.getInstance().getSwitchValue(switchName);
        }
        return VariationsAssociatedData.getVariationParamValue(MODERATE_BINDING_TRIAL_NAME, param);
    }

    private static void startModerateBindingManagementIfNeeded(Context context) {
        // Ensures that a low memory device isn't included in the field trial experimental.
        if (SysUtils.isLowEndDevice()) return;

        String enabledValue = getModerateBindingConfigValue(MODERATE_BINDING_ENABLED_PARAM);
        if (!Boolean.parseBoolean(enabledValue)) return;

        String lowReduceRatioValue =
                getModerateBindingConfigValue(MODERATE_BINDING_LOW_REDUCE_RATIO_PARAM);
        String highReduceRatioValue =
                getModerateBindingConfigValue(MODERATE_BINDING_HIGH_REDUCE_RATIO_PARAM);
        float lowReduceRatio =
                parseFloat(lowReduceRatioValue, DEFAULT_MODERATE_BINDING_REDUCE_RATIO);
        float highReduceRatio =
                parseFloat(highReduceRatioValue, DEFAULT_MODERATE_BINDING_REDUCE_RATIO * 2);
        highReduceRatio = Math.max(0, Math.min(1, highReduceRatio));
        lowReduceRatio = Math.max(0, Math.min(highReduceRatio, lowReduceRatio));
        ChildProcessLauncher.startModerateBindingManagement(
                context, lowReduceRatio, highReduceRatio);
    }

    /**
    * @return Whether deferred startup has been completed.
    */
    @VisibleForTesting
    public boolean isDeferredStartupComplete() {
        return mDeferredStartupComplete;
    }
}

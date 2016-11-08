// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.instantapps;

import android.content.Context;
import android.content.Intent;
import android.os.StrictMode;
import android.os.SystemClock;
import android.provider.Browser;

import org.chromium.base.CommandLine;
import org.chromium.base.FieldTrialList;
import org.chromium.base.Log;
import org.chromium.chrome.browser.ChromeApplication;
import org.chromium.chrome.browser.ChromeSwitches;
import org.chromium.chrome.browser.IntentHandler;
import org.chromium.chrome.browser.externalnav.ExternalNavigationDelegateImpl;
import org.chromium.chrome.browser.metrics.LaunchMetrics.TimesHistogramSample;
import org.chromium.chrome.browser.net.spdyproxy.DataReductionProxySettings;
import org.chromium.chrome.browser.preferences.ChromePreferenceManager;
import org.chromium.chrome.browser.util.IntentUtils;

import java.util.concurrent.TimeUnit;

/** A launcher for Instant Apps. */
public class InstantAppsHandler {
    private static final String TAG = "InstantAppsHandler";

    private static final Object INSTANCE_LOCK = new Object();
    private static InstantAppsHandler sInstance;

    private static final String DO_NOT_LAUNCH_EXTRA =
            "com.google.android.gms.instantapps.DO_NOT_LAUNCH_INSTANT_APP";

    private static final String CUSTOM_APPS_INSTANT_APP_EXTRA =
            "android.support.customtabs.extra.EXTRA_ENABLE_INSTANT_APPS";

    private static final String INSTANT_APP_START_TIME_EXTRA =
            "org.chromium.chrome.INSTANT_APP_START_TIME";

    /** Finch experiment name. */
    private static final String INSTANT_APPS_EXPERIMENT_NAME = "InstantApps";

    /** Finch experiment group which is enabled for instant apps. */
    private static final String INSTANT_APPS_ENABLED_ARM = "InstantAppsEnabled";

    /** Finch experiment group which is disabled for instant apps. */
    private static final String INSTANT_APPS_DISABLED_ARM = "InstantAppsDisabled";

    /** A histogram to record how long each handleIntent() call took. */
    private static final TimesHistogramSample sHandleIntentDuration = new TimesHistogramSample(
            "Android.InstantApps.HandleIntentDuration", TimeUnit.MILLISECONDS);

    /** A histogram to record how long the fallback intent roundtrip was. */
    private static final TimesHistogramSample sFallbackIntentTimes = new TimesHistogramSample(
            "Android.InstantApps.FallbackDuration", TimeUnit.MILLISECONDS);

    /** A histogram to record how long the GMS Core API call took. */
    private static final TimesHistogramSample sInstantAppsApiCallTimes = new TimesHistogramSample(
            "Android.InstantApps.ApiCallDuration", TimeUnit.MILLISECONDS);


    /** @return The singleton instance of {@link InstantAppsHandler}. */
    public static InstantAppsHandler getInstance(ChromeApplication application) {
        synchronized (INSTANCE_LOCK) {
            if (sInstance == null) {
                sInstance = application.createInstantAppsHandler();
            }
        }
        return sInstance;
    }

    /**
     * Check the cached value to figure out if the feature is enabled. We have to use the cached
     * value because native library hasn't yet been loaded.
     * @param context The application context.
     * @return Whether the feature is enabled.
     */
    private boolean isEnabled(Context context) {
        // Will go away once the feature is enabled for everyone by default.
        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskReads();
        try {
            return ChromePreferenceManager.getInstance(context).getCachedInstantAppsEnabled();
        } finally {
            StrictMode.setThreadPolicy(oldPolicy);
        }
    }

    /**
     * Record how long the handleIntent() method took.
     * @param startTime The timestamp for handleIntent start time.
     */
    private void recordHandleIntentDuration(long startTime) {
        sHandleIntentDuration.record(SystemClock.elapsedRealtime() - startTime);
    }

    /**
     * Record the amount of time spent in the instant apps API call.
     * @param startTime The time at which we started doing computations.
     */
    protected void recordInstantAppsApiCallTime(long startTime) {
        sInstantAppsApiCallTimes.record(SystemClock.elapsedRealtime() - startTime);
    }

    /**
     * In the case Chrome is called through the fallback mechanism from Instant Apps, record the
     * amount of time the whole trip took.
     * @param intent The current intent.
     */
    private void maybeRecordFallbackDuration(Intent intent) {
        if (intent.hasExtra(INSTANT_APP_START_TIME_EXTRA)) {
            Long startTime = intent.getLongExtra(INSTANT_APP_START_TIME_EXTRA, 0);
            if (startTime != 0) {
                sFallbackIntentTimes.record(SystemClock.elapsedRealtime() - startTime);
            }
        }
    }

    /**
     * Cache whether the Instant Apps feature is enabled.
     * This should only be called with the native library loaded.
     */
    public void cacheInstantAppsEnabled(Context context) {
        boolean isEnabled = false;
        boolean wasEnabled = isEnabled(context);
        CommandLine instance = CommandLine.getInstance();
        if (instance.hasSwitch(ChromeSwitches.DISABLE_APP_LINK)) {
            isEnabled = false;
        } else if (instance.hasSwitch(ChromeSwitches.ENABLE_APP_LINK)) {
            isEnabled = true;
        } else {
            String experiment = FieldTrialList.findFullName(INSTANT_APPS_EXPERIMENT_NAME);
            if (INSTANT_APPS_DISABLED_ARM.equals(experiment)) {
                isEnabled = false;
            } else if (INSTANT_APPS_ENABLED_ARM.equals(experiment)) {
                isEnabled = true;
            }
        }

        if (isEnabled != wasEnabled) {
            ChromePreferenceManager.getInstance(context).setCachedInstantAppsEnabled(isEnabled);
        }
    }

    /** Handle incoming intent. */
    public boolean handleIncomingIntent(Context context, Intent intent,
            boolean isCustomTabsIntent) {
        long startTimeStamp = SystemClock.elapsedRealtime();
        boolean result = handleIncomingIntentInternal(context, intent, isCustomTabsIntent,
                startTimeStamp);
        recordHandleIntentDuration(startTimeStamp);
        return result;
    }

    private boolean handleIncomingIntentInternal(
            Context context, Intent intent, boolean isCustomTabsIntent, long startTime) {
        if (!isEnabled(context)
                || IntentUtils.safeGetBooleanExtra(intent, DO_NOT_LAUNCH_EXTRA, false)
                || IntentUtils.safeGetBooleanExtra(
                        intent, IntentHandler.EXTRA_OPEN_NEW_INCOGNITO_TAB, false)
                || (isCustomTabsIntent && !IntentUtils.safeGetBooleanExtra(
                        intent, CUSTOM_APPS_INSTANT_APP_EXTRA, false))
                || DataReductionProxySettings.isEnabledBeforeNativeLoad(context)
                || isIntentFromChrome(context, intent)
                || (IntentHandler.getUrlFromIntent(intent) == null)) {
            Log.i(TAG, "Not handling with Instant Apps");
            return false;
        }

        maybeRecordFallbackDuration(intent);

        // Used to search for the intent handlers. Needs null component to return correct results.
        Intent intentCopy = new Intent(intent);
        intentCopy.setComponent(null);

        if (!(isCustomTabsIntent || isChromeDefaultHandler(context))
                || ExternalNavigationDelegateImpl.isPackageSpecializedHandler(
                        context, null, intentCopy)) {
            // Chrome is not the default browser or a specialized handler exists.
            Log.i(TAG, "Not handling with Instant Apps because Chrome is not default or "
                    + "there's a specialized handler");
            return false;
        }

        Intent callbackIntent = new Intent(intent);
        callbackIntent.putExtra(DO_NOT_LAUNCH_EXTRA, true);
        callbackIntent.putExtra(INSTANT_APP_START_TIME_EXTRA, startTime);

        return tryLaunchingInstantApp(context, intent, isCustomTabsIntent, callbackIntent);
    }

    /**
     * Attempts to launch an Instant App, if possible.
     * @param context The activity context.
     * @param intent The incoming intent.
     * @param isCustomTabsIntent Whether the intent is for a CustomTab.
     * @param fallbackIntent The intent that will be launched by Instant Apps in case of failure to
     *        load.
     * @return Whether an Instant App was launched.
     */
    protected boolean tryLaunchingInstantApp(
            Context context, Intent intent, boolean isCustomTabsIntent, Intent fallbackIntent) {
        return false;
    }

    /**
     * @return Whether the intent was fired from Chrome. This happens when the user gets a
     *         disambiguation dialog and chooses to stay in Chrome.
     */
    private boolean isIntentFromChrome(Context context, Intent intent) {
        return context.getPackageName().equals(IntentUtils.safeGetStringExtra(
                intent, Browser.EXTRA_APPLICATION_ID))
                // We shouldn't leak internal intents with authentication tokens
                || IntentHandler.wasIntentSenderChrome(intent, context);
    }

    /** @return Whether Chrome is the default browser on the device. */
    private boolean isChromeDefaultHandler(Context context) {
        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskReads();
        try {
            return ChromePreferenceManager.getInstance(context).getCachedChromeDefaultBrowser();
        } finally {
            StrictMode.setThreadPolicy(oldPolicy);
        }
    }
}

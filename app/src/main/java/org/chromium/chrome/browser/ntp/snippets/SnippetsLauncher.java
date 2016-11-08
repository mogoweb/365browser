// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp.snippets;

import android.content.Context;

import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.OneoffTask;
import com.google.android.gms.gcm.PeriodicTask;
import com.google.android.gms.gcm.Task;

import org.chromium.base.Log;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.chrome.browser.ChromeBackgroundService;
import org.chromium.chrome.browser.externalauth.ExternalAuthUtils;
import org.chromium.chrome.browser.externalauth.UserRecoverableErrorHandler;

import java.util.Date;

/**
 * The {@link SnippetsLauncher} singleton is created and owned by the C++ browser.
 *
 * Thread model: This class is to be run on the UI thread only.
 */
public class SnippetsLauncher {
    private static final String TAG = "SnippetsLauncher";

    // Task tags for fetching snippets.
    public static final String TASK_TAG_WIFI_CHARGING = "FetchSnippetsWifiCharging";
    public static final String TASK_TAG_WIFI = "FetchSnippetsWifi";
    public static final String TASK_TAG_FALLBACK = "FetchSnippetsFallback";

    // Task tag for re-scheduling the snippet fetching. This is used to support different fetching
    // intervals during different times of day.
    public static final String TASK_TAG_RESCHEDULE = "RescheduleSnippets";

    // The instance of SnippetsLauncher currently owned by a C++ SnippetsLauncherAndroid, if any.
    // If it is non-null then the browser is running.
    private static SnippetsLauncher sInstance;

    private GcmNetworkManager mScheduler;

    private boolean mGCMEnabled = true;

    /**
     * Create a SnippetsLauncher object, which is owned by C++.
     * @param context The app context.
     */
    @VisibleForTesting
    @CalledByNative
    public static SnippetsLauncher create(Context context) {
        if (sInstance != null) {
            throw new IllegalStateException("Already instantiated");
        }

        sInstance = new SnippetsLauncher(context);
        return sInstance;
    }

    /**
     * Called when the C++ counterpart is deleted.
     */
    @VisibleForTesting
    @SuppressFBWarnings("ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD")
    @CalledByNative
    public void destroy() {
        assert sInstance == this;
        sInstance = null;
    }

    /**
     * Returns true if the native browser has started and created an instance of {@link
     * SnippetsLauncher}.
     */
    public static boolean hasInstance() {
        return sInstance != null;
    }

    protected SnippetsLauncher(Context context) {
        checkGCM(context);
        mScheduler = GcmNetworkManager.getInstance(context);
    }

    private boolean canUseGooglePlayServices(Context context) {
        return ExternalAuthUtils.getInstance().canUseGooglePlayServices(
                context, new UserRecoverableErrorHandler.Silent());
    }

    private void checkGCM(Context context) {
        // Check to see if Play Services is up to date, and disable GCM if not.
        if (!canUseGooglePlayServices(context)) {
            mGCMEnabled = false;
            Log.i(TAG, "Disabling SnippetsLauncher because Play Services is not up to date.");
        }
    }

    private static PeriodicTask buildFetchTask(
            String tag, long periodSeconds, int requiredNetwork, boolean requiresCharging) {
        return new PeriodicTask.Builder()
                .setService(ChromeBackgroundService.class)
                .setTag(tag)
                .setPeriod(periodSeconds)
                .setRequiredNetwork(requiredNetwork)
                .setRequiresCharging(requiresCharging)
                .setPersisted(true)
                .setUpdateCurrent(true)
                .build();
    }

    private static OneoffTask buildRescheduleTask(Date date) {
        Date now = new Date();
        // Convert from milliseconds to seconds, rounding up.
        long delaySeconds = (date.getTime() - now.getTime() + 999) / 1000;
        final long intervalSeconds = 15 * 60;
        return new OneoffTask.Builder()
                .setService(ChromeBackgroundService.class)
                .setTag(TASK_TAG_RESCHEDULE)
                .setExecutionWindow(delaySeconds, delaySeconds + intervalSeconds)
                .setRequiredNetwork(Task.NETWORK_STATE_ANY)
                .setRequiresCharging(false)
                .setPersisted(true)
                .setUpdateCurrent(true)
                .build();
    }

    private void scheduleOrCancelFetchTask(
            String taskTag, long period, int requiredNetwork, boolean requiresCharging) {
        if (period > 0) {
            mScheduler.schedule(buildFetchTask(taskTag, period, requiredNetwork, requiresCharging));
        } else {
            mScheduler.cancelTask(taskTag, ChromeBackgroundService.class);
        }
    }

    @CalledByNative
    private boolean schedule(long periodWifiChargingSeconds, long periodWifiSeconds,
            long periodFallbackSeconds, long rescheduleTime) {
        if (!mGCMEnabled) return false;
        Log.d(TAG, "Scheduling: " + periodWifiChargingSeconds + " " + periodWifiSeconds + " "
                        + periodFallbackSeconds);
        // Google Play Services may not be up to date, if the application was not installed through
        // the Play Store. In this case, scheduling the task will fail silently.
        try {
            scheduleOrCancelFetchTask(TASK_TAG_WIFI_CHARGING, periodWifiChargingSeconds,
                    Task.NETWORK_STATE_UNMETERED, true);
            scheduleOrCancelFetchTask(
                    TASK_TAG_WIFI, periodWifiSeconds, Task.NETWORK_STATE_UNMETERED, false);
            scheduleOrCancelFetchTask(
                    TASK_TAG_FALLBACK, periodFallbackSeconds, Task.NETWORK_STATE_CONNECTED, false);
            if (rescheduleTime > 0) {
                mScheduler.schedule(buildRescheduleTask(new Date(rescheduleTime)));
            } else {
                mScheduler.cancelTask(TASK_TAG_RESCHEDULE, ChromeBackgroundService.class);
            }
        } catch (IllegalArgumentException e) {
            // Disable GCM for the remainder of this session.
            mGCMEnabled = false;
            // Return false so that the failure will be logged.
            return false;
        }
        return true;
    }

    @CalledByNative
    private boolean unschedule() {
        if (!mGCMEnabled) return false;
        Log.i(TAG, "Unscheduling");
        return schedule(0, 0, 0, 0);
    }
}


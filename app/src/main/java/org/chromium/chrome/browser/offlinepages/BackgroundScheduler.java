// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.offlinepages;

import android.content.Context;
import android.os.Build;

import java.util.concurrent.TimeUnit;

/**
 * The background scheduler class is for setting GCM Network Manager tasks.
 */
public abstract class BackgroundScheduler {
    private static final long ONE_WEEK_IN_SECONDS = TimeUnit.DAYS.toSeconds(7);
    private static final long FIVE_MINUTES_IN_SECONDS = TimeUnit.MINUTES.toSeconds(5);
    private static final long NO_DELAY = 0;
    private static final boolean OVERWRITE = true;

    /**
     * Context used by the scheduler to access services. Extracted to a field, to clean up method
     * signatures.
     */
    private Context mContext;

    /**
     * Provides an instance of BackgroundScheduler for given context and current API level.
     * <p>
     * Warning: Don't cache the returned value, as it is bound to {@code context}. Consumers should
     * simply get an instance every time.
     * @return An instance of BackgroundScheduler.
     */
    public static BackgroundScheduler getInstance(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return new BackgroundJobScheduler(context);
        } else {
            return new BackgroundGcmScheduler(context);
        }
    }

    protected BackgroundScheduler(Context context) {
        mContext = context;
    }

    /** Schedules a GCM Network Manager task for provided triggering conditions. */
    public void schedule(TriggerConditions triggerConditions) {
        scheduleImpl(triggerConditions, NO_DELAY, ONE_WEEK_IN_SECONDS, OVERWRITE);
    }

    /**
     * If there is no currently scheduled task, then start a GCM Network Manager request
     * for the given Triggering conditions but delayed to run after {@code delayStartSeconds}.
     * Typically, the Request Coordinator will overwrite this task after task processing
     * and/or queue updates. This is a backup task in case processing is killed by the
     * system.
     */
    public void scheduleBackup(TriggerConditions triggerConditions, long delayStartSeconds) {
        scheduleImpl(triggerConditions, delayStartSeconds, ONE_WEEK_IN_SECONDS, !OVERWRITE);
    }

    /** Cancel any outstanding GCM Network Manager requests. */
    public abstract void cancel();

    /**
     * For the given Triggering conditions, start a new GCM Network Manager request allowed
     * to run after {@code delayStartSecs} seconds.
     */
    protected abstract void scheduleImpl(TriggerConditions triggerConditions,
            long delayStartSeconds, long executionDeadlineSeconds, boolean overwrite);

    /** @return Context used to access OS services. */
    protected Context getContext() {
        return mContext;
    }

    /**
     * If GooglePlayServices upgrades, any outstaning tasks will be lost.
     * Set a reminder to wake up and check the task queue if an upgrade happens.
     */
    public void rescheduleOfflinePagesTasksOnUpgrade() {
        // We use the least restrictive trigger conditions.  A wakeup will cause
        // the queue to be checked, and the trigger conditions will be replaced by
        // the current trigger conditions needed.
        TriggerConditions triggerConditions = new TriggerConditions(false, 0, false);
        scheduleBackup(triggerConditions, FIVE_MINUTES_IN_SECONDS);
    }
}

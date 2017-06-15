// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.offlinepages.prefetch;

import android.content.Context;

import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.base.library_loader.LibraryProcessType;
import org.chromium.base.library_loader.ProcessInitException;
import org.chromium.chrome.browser.init.ChromeBrowserInitializer;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.components.background_task_scheduler.BackgroundTask;
import org.chromium.components.background_task_scheduler.BackgroundTask.TaskFinishedCallback;
import org.chromium.components.background_task_scheduler.BackgroundTaskScheduler;
import org.chromium.components.background_task_scheduler.BackgroundTaskSchedulerFactory;
import org.chromium.components.background_task_scheduler.TaskIds;
import org.chromium.components.background_task_scheduler.TaskInfo;
import org.chromium.components.background_task_scheduler.TaskParameters;
import org.chromium.content.browser.BrowserStartupController;

import java.util.concurrent.TimeUnit;

/**
 * Handles servicing background offlining requests.
 *
 * Can schedule or cancel tasks, and handles the actual initialization that
 * happens when a task fires.
 */
@JNINamespace("offline_pages::prefetch")
public class PrefetchBackgroundTask implements BackgroundTask {
    private static final String TAG = "OPPrefetchBGTask";

    private long mNativeTask = 0;
    private TaskFinishedCallback mTaskFinishedCallback = null;

    private Profile mProfile;

    public PrefetchBackgroundTask() {
        mProfile = Profile.getLastUsedProfile();
    }

    public PrefetchBackgroundTask(Profile profile) {
        mProfile = profile;
    }

    /**
     * Schedules the default 'NWake' task for the prefetching service.
     *
     * This task will only be scheduled on a good network type.
     * TODO(dewittj): Handle skipping work if the battery percentage is too low.
     */
    @CalledByNative
    public static void scheduleTask() {
        BackgroundTaskScheduler scheduler = BackgroundTaskSchedulerFactory.getScheduler();
        TaskInfo taskInfo =
                TaskInfo.createOneOffTask(TaskIds.OFFLINE_PAGES_PREFETCH_JOB_ID,
                                PrefetchBackgroundTask.class,
                                // Minimum time to wait
                                TimeUnit.MINUTES.toMillis(15),
                                // Maximum time to wait.  After this interval the event will fire
                                // regardless of whether the conditions are right.
                                TimeUnit.DAYS.toMillis(7))
                        .setRequiredNetworkType(TaskInfo.NETWORK_TYPE_UNMETERED)
                        .setIsPersisted(true)
                        .setUpdateCurrent(true)
                        .build();
        scheduler.schedule(ContextUtils.getApplicationContext(), taskInfo);
    }

    /**
     * Cancels the default 'NWake' task for the prefetching service.
     */
    @CalledByNative
    public static void cancelTask() {
        BackgroundTaskScheduler scheduler = BackgroundTaskSchedulerFactory.getScheduler();
        scheduler.cancel(
                ContextUtils.getApplicationContext(), TaskIds.OFFLINE_PAGES_PREFETCH_JOB_ID);
    }

    /**
     * Initializer that runs when the task wakes up Chrome.
     *
     * Loads the native library and then calls into the PrefetchService, which then manages the
     * lifetime of this task.
     */
    @Override
    public boolean onStartTask(
            Context context, TaskParameters taskParameters, TaskFinishedCallback callback) {
        assert taskParameters.getTaskId() == TaskIds.OFFLINE_PAGES_PREFETCH_JOB_ID;
        if (mNativeTask != 0) return false;

        // TODO(dewittj): Ensure that the conditions are right to do work.  If the maximum time to
        // wait is reached, it is possible the task will fire even if network conditions are
        // incorrect.

        // Ensures that native potion of the browser is launched.
        launchBrowserIfNecessary(context);

        mTaskFinishedCallback = callback;
        return nativeStartPrefetchTask(mProfile);
    }

    @Override
    public boolean onStopTask(Context context, TaskParameters taskParameters) {
        assert taskParameters.getTaskId() == TaskIds.OFFLINE_PAGES_PREFETCH_JOB_ID;
        if (mNativeTask == 0) return false;

        return nativeOnStopTask(mNativeTask);
    }

    @Override
    public void reschedule(Context context) {}

    /**
     * Called during construction of the native task.
     *
     * PrefetchBackgroundTask#onStartTask constructs the native task.
     */
    @VisibleForTesting
    @CalledByNative
    void setNativeTask(long nativeTask) {
        mNativeTask = nativeTask;
    }

    /**
     * Invoked by the native task when it is destroyed.
     */
    @VisibleForTesting
    @CalledByNative
    void doneProcessing(boolean needsReschedule) {
        assert mTaskFinishedCallback != null;
        mTaskFinishedCallback.taskFinished(needsReschedule);
        setNativeTask(0);
    }

    @VisibleForTesting
    @SuppressFBWarnings("DM_EXIT")
    void launchBrowserIfNecessary(Context context) {
        if (BrowserStartupController.get(LibraryProcessType.PROCESS_BROWSER)
                        .isStartupSuccessfullyCompleted()) {
            return;
        }

        // TODO(https://crbug.com/717251): Remove when BackgroundTaskScheduler supports loading the
        // native library.
        try {
            ChromeBrowserInitializer.getInstance(context).handleSynchronousStartup();
        } catch (ProcessInitException e) {
            Log.e(TAG, "ProcessInitException while starting the browser process.");
            // Since the library failed to initialize nothing in the application can work, so kill
            // the whole application not just the activity.
            System.exit(-1);
        }
    }

    @VisibleForTesting
    native boolean nativeStartPrefetchTask(Profile profile);
    @VisibleForTesting
    native boolean nativeOnStopTask(long nativePrefetchBackgroundTask);
}

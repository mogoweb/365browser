// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.offlinepages;

import android.content.Context;

import org.chromium.base.Callback;
import org.chromium.base.Log;
import org.chromium.base.library_loader.LibraryProcessType;
import org.chromium.base.library_loader.ProcessInitException;
import org.chromium.chrome.browser.init.ChromeBrowserInitializer;
import org.chromium.chrome.browser.offlinepages.interfaces.BackgroundSchedulerProcessor;
import org.chromium.components.background_task_scheduler.BackgroundTask;
import org.chromium.components.background_task_scheduler.BackgroundTask.TaskFinishedCallback;
import org.chromium.components.background_task_scheduler.TaskIds;
import org.chromium.components.background_task_scheduler.TaskParameters;
import org.chromium.content.browser.BrowserStartupController;

/**
 * Handles servicing background offlining requests coming via background_task_scheduler component.
 */
public class OfflineBackgroundTask implements BackgroundTask {
    private static final String TAG = "OPBackgroundTask";

    BackgroundSchedulerProcessor mBackgroundProcessor;

    public OfflineBackgroundTask() {
        mBackgroundProcessor = new BackgroundSchedulerProcessorImpl();
    }

    @Override
    public boolean onStartTask(
            Context context, TaskParameters taskParameters, TaskFinishedCallback callback) {
        assert taskParameters.getTaskId() == TaskIds.OFFLINE_PAGES_BACKGROUND_JOB_ID;

        // Ensuring that native potion of the browser is launched.
        launchBrowserIfNecessary(context);

        return BackgroundOfflinerTask.startBackgroundRequestsImpl(
                mBackgroundProcessor, context, taskParameters.getExtras(), wrapCallback(callback));
    }

    @Override
    public boolean onStopTask(Context context, TaskParameters taskParameters) {
        return mBackgroundProcessor.stopScheduledProcessing();
    }

    @Override
    public void reschedule(Context context) {
        BackgroundScheduler.getInstance(context).rescheduleOfflinePagesTasksOnUpgrade();
    }

    /** Wraps the callback for code reuse */
    private Callback<Boolean> wrapCallback(final TaskFinishedCallback callback) {
        return new Callback<Boolean>() {
            @Override
            public void onResult(Boolean result) {
                callback.taskFinished(result);
            }
        };
    }

    private static void launchBrowserIfNecessary(Context context) {
        if (BrowserStartupController.get(LibraryProcessType.PROCESS_BROWSER)
                        .isStartupSuccessfullyCompleted()) {
            return;
        }

        // TODO(fgorski): This method is taken from ChromeBackgroundService as a local fix and will
        // be removed with BackgroundTaskScheduler supporting GcmNetworkManager scheduling.
        try {
            ChromeBrowserInitializer.getInstance(context).handleSynchronousStartup();
        } catch (ProcessInitException e) {
            Log.e(TAG, "ProcessInitException while starting the browser process.");
            // Since the library failed to initialize nothing in the application can work, so kill
            // the whole application not just the activity.
            System.exit(-1);
        }
    }
}

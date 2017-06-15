// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.offlinepages;

import android.content.Context;
import android.os.Bundle;

import org.chromium.components.background_task_scheduler.BackgroundTaskSchedulerFactory;
import org.chromium.components.background_task_scheduler.TaskIds;
import org.chromium.components.background_task_scheduler.TaskInfo;

import java.util.concurrent.TimeUnit;

/**
 * The background job scheduler class used for scheduling tasks using JobScheduler.
 */
public class BackgroundJobScheduler extends BackgroundScheduler {
    public BackgroundJobScheduler(Context context) {
        super(context);
    }

    @Override
    public void cancel() {
        BackgroundTaskSchedulerFactory.getScheduler().cancel(
                getContext(), TaskIds.OFFLINE_PAGES_BACKGROUND_JOB_ID);
    }

    @Override
    protected void scheduleImpl(TriggerConditions triggerConditions, long delayStartSeconds,
            long executionDeadlineSeconds, boolean overwrite) {
        Bundle taskExtras = new Bundle();
        TaskExtrasPacker.packTimeInBundle(taskExtras);
        TaskExtrasPacker.packTriggerConditionsInBundle(taskExtras, triggerConditions);

        TaskInfo taskInfo =
                TaskInfo.createOneOffTask(TaskIds.OFFLINE_PAGES_BACKGROUND_JOB_ID,
                                OfflineBackgroundTask.class,
                                TimeUnit.SECONDS.toMillis(delayStartSeconds),
                                TimeUnit.SECONDS.toMillis(executionDeadlineSeconds))
                        .setRequiredNetworkType(triggerConditions.requireUnmeteredNetwork()
                                        ? TaskInfo.NETWORK_TYPE_UNMETERED
                                        : TaskInfo.NETWORK_TYPE_ANY)
                        .setUpdateCurrent(overwrite)
                        .setIsPersisted(true)
                        .setExtras(taskExtras)
                        .setRequiresCharging(triggerConditions.requirePowerConnected())
                        .build();

        BackgroundTaskSchedulerFactory.getScheduler().schedule(getContext(), taskInfo);
    }
}

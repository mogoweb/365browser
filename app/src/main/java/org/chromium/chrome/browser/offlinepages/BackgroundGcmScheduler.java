// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.offlinepages;

import android.content.Context;
import android.os.Bundle;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.OneoffTask;
import com.google.android.gms.gcm.Task;

import org.chromium.chrome.browser.ChromeBackgroundService;

/**
 * The background scheduler class is for setting GCM Network Manager tasks.
 */
public class BackgroundGcmScheduler extends BackgroundScheduler {
    public BackgroundGcmScheduler(Context context) {
        super(context);
    }

    @Override
    public void cancel() {
        GcmNetworkManager gcmNetworkManager = getGcmNetworkManager();
        if (gcmNetworkManager == null) return;
        gcmNetworkManager.cancelTask(OfflinePageUtils.TASK_TAG, ChromeBackgroundService.class);
    }

    @Override
    protected void scheduleImpl(TriggerConditions triggerConditions, long delayStartSeconds,
            long executionDeadlineSeconds, boolean overwrite) {
        GcmNetworkManager gcmNetworkManager = getGcmNetworkManager();
        if (gcmNetworkManager == null) return;

        Bundle taskExtras = new Bundle();
        TaskExtrasPacker.packTimeInBundle(taskExtras);
        TaskExtrasPacker.packHoldWakelock(taskExtras);
        TaskExtrasPacker.packTriggerConditionsInBundle(taskExtras, triggerConditions);

        Task task = new OneoffTask.Builder()
                            .setService(ChromeBackgroundService.class)
                            .setExecutionWindow(delayStartSeconds, executionDeadlineSeconds)
                            .setTag(OfflinePageUtils.TASK_TAG)
                            .setUpdateCurrent(overwrite)
                            .setRequiredNetwork(triggerConditions.requireUnmeteredNetwork()
                                            ? Task.NETWORK_STATE_UNMETERED
                                            : Task.NETWORK_STATE_CONNECTED)
                            .setRequiresCharging(triggerConditions.requirePowerConnected())
                            .setPersisted(true)
                            .setExtras(taskExtras)
                            .build();

        // Schedule a task using GCM network manager.
        gcmNetworkManager.schedule(task);
    }

    private GcmNetworkManager getGcmNetworkManager() {
        if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(getContext())
                == ConnectionResult.SUCCESS) {
            return GcmNetworkManager.getInstance(getContext());
        }
        return null;
    }
}

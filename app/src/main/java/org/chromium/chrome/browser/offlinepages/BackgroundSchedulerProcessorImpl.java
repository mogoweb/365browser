// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.offlinepages;

import org.chromium.base.Callback;
import org.chromium.chrome.browser.offlinepages.interfaces.BackgroundSchedulerProcessor;

/**
 * Implementation of the scheduler bridge class that defers to the static background scheduler
 * bridge.
 */
public class BackgroundSchedulerProcessorImpl implements BackgroundSchedulerProcessor {
    @Override
    public boolean startScheduledProcessing(
            DeviceConditions deviceConditions, Callback<Boolean> callback) {
        return BackgroundSchedulerBridge.startScheduledProcessing(deviceConditions, callback);
    }

    @Override
    public boolean stopScheduledProcessing() {
        return BackgroundSchedulerBridge.stopScheduledProcessing();
    }
}

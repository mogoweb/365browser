// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.components.precache;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

import org.chromium.base.VisibleForTesting;

/**
 * Utility class that provides information about the current state of the device.
 */
public class DeviceState {
    private static DeviceState sDeviceState;

    // Saved battery level percentage.
    private int mSavedBatteryPercentage;

    /** Disallow Construction of DeviceState objects. Use {@link #getInstance()} instead to create
     * a singleton instance.
     */
    protected DeviceState() {}

    public static DeviceState getInstance() {
        if (sDeviceState == null) sDeviceState = new DeviceState();
        return sDeviceState;
    }

    protected NetworkInfoDelegateFactory mNetworkInfoDelegateFactory =
            new NetworkInfoDelegateFactory();

    @VisibleForTesting
    void setNetworkInfoDelegateFactory(NetworkInfoDelegateFactory factory) {
        mNetworkInfoDelegateFactory = factory;
    }

    /** @return integer representing the current status of the battery. */
    @VisibleForTesting
    int getStickyBatteryStatus(Context context) {
        IntentFilter iFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        // Call registerReceiver on context.getApplicationContext(), not on context itself, because
        // context could be a BroadcastReceiver context, which would throw an
        // android.content.ReceiverCallNotAllowedException.
        Intent batteryStatus = context.getApplicationContext().registerReceiver(null, iFilter);

        if (batteryStatus == null) {
            return BatteryManager.BATTERY_STATUS_UNKNOWN;
        }
        return batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS,
                BatteryManager.BATTERY_STATUS_UNKNOWN);
    }

    /** @return whether the device is connected to power. */
    public boolean isPowerConnected(Context context) {
        int status = getStickyBatteryStatus(context);
        return status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;
    }

    /**
     * @return the previously saved battery level percentage.
     * @param context the application context
     */
    public int getSavedBatteryPercentage() {
        return mSavedBatteryPercentage;
    }

    /**
     * Saves the current battery level percentage to be retrieved later.
     */
    public void saveCurrentBatteryPercentage(Context context) {
        mSavedBatteryPercentage = getCurrentBatteryPercentage(context);
    }

    /**
     * @return the current battery level as percentage.
     * @param context the application context
     */
    public int getCurrentBatteryPercentage(Context context) {
        IntentFilter iFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, iFilter);
        if (batteryStatus == null) return 0;
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        if (level == -1 || scale == -1) return 0;
        if (scale == 0) return 0;

        return Math.round(100 * level / (float) scale);
    }

    /** @return whether the currently active network is unmetered. */
    public boolean isUnmeteredNetworkAvailable(Context context) {
        NetworkInfoDelegate networkInfo =
                mNetworkInfoDelegateFactory.getNetworkInfoDelegate(context);
        return (networkInfo.isValid()
                && networkInfo.isAvailable()
                && networkInfo.isConnected()
                && !networkInfo.isRoaming()
                && !networkInfo.isActiveNetworkMetered());
    }
}


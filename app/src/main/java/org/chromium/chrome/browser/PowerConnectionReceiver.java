/* Copyright (c) 2015, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *      * Redistributions of source code must retain the above copyright
 *      notice, this list of conditions and the following disclaimer.
 *      * Redistributions in binary form must reproduce the above
 *      copyright notice, this list of conditions and the following
 *      disclaimer in the documentation and/or other materials provided
 *      with the distribution.
 *      * Neither the name of The Linux Foundation nor the names of its
 *      contributors may be used to endorse or promote products derived
 *      from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT,INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.chromium.chrome.browser;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;

import org.chromium.chrome.browser.preferences.Preferences;
import org.chromium.chrome.browser.preferences.PreferencesLauncher;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.preferences.PowersaveModePreference;

public class PowerConnectionReceiver extends BroadcastReceiver {
    static final String POWER_MODE_TOGGLE = PowerManager.ACTION_POWER_SAVE_MODE_CHANGED;
    static final String POWER_OKAY = Intent.ACTION_BATTERY_OKAY;
    static final String POWER_LOW = Intent.ACTION_BATTERY_LOW;

    @Override
    public void onReceive(Context context, Intent intent) {
        PrefServiceBridge prefs = PrefServiceBridge.getInstance();
        String action = intent.getAction();

        if (POWER_MODE_TOGGLE.equals(action)) {
            // This feature is only on android L
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                PowerManager pm = (PowerManager) context.getSystemService(context.POWER_SERVICE);
                prefs.setPowersaveModeEnabled(pm.isPowerSaveMode());
            }
        }

        if (POWER_OKAY.equals(action)) {
            prefs.setPowersaveModeEnabled(false);
        } else if (POWER_LOW.equals(action)) {
            if (prefs.getPowersaveModeEnabled()) {
                return;
            }
            Bundle args = new Bundle();
            args.putBoolean("LowPower", true);
            Intent preferencesIntent = PreferencesLauncher.createIntentForSettingsPage(
                    context, PowersaveModePreference.class.getName());
            preferencesIntent.putExtra(
                    Preferences.EXTRA_SHOW_FRAGMENT_ARGUMENTS, args);
            context.startActivity(preferencesIntent);
        }
    }
}

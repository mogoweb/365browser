/*
 * Copyright (c) 2015, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package org.chromium.chrome.browser.preferences;

import android.content.DialogInterface;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.SwitchPreference;
import android.support.v7.app.AlertDialog;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.ApplicationLifetime;

/**
 * A preference to control whether power saving mode is enabled or not.
 */
public class PowersaveModePreference extends PreferenceFragment {

    private static final String PREF_POWERSAVE_MODE_SWITCH = "powersave_mode_switch";
    private SwitchPreference mPowersaveModeSwitch;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.powersave_mode_preferences);
        getActivity().setTitle(R.string.powersave_mode_title);

        mPowersaveModeSwitch = (SwitchPreference) findPreference(PREF_POWERSAVE_MODE_SWITCH);

        mPowersaveModeSwitch.setChecked(
                PrefServiceBridge.getInstance().getPowersaveModeEnabled());

        mPowersaveModeSwitch.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                PrefServiceBridge.getInstance().setPowersaveModeEnabled((boolean) newValue);
                createOptOutAlertDialog((boolean) newValue).show();
                return true;
            }
        }
        );
        if (getArguments() != null && getArguments().getBoolean("LowPower", false)) {
            createLowPowerAlertDialog().show();
        }
    }

    private AlertDialog createOptOutAlertDialog(final boolean optOut) {
        final boolean isSwitchEnabled = !PrefServiceBridge.getInstance().getPowersaveModeEnabled();

        AlertDialog dialog = new AlertDialog.Builder(getActivity(), R.style.AlertDialogTheme)
                .setTitle(optOut ? R.string.powersave_mode_turn_on_title
                        : R.string.powersave_mode_turn_off_title)
                .setMessage(optOut ? R.string.powersave_mode_opt_in_confirmation
                        : R.string.powersave_mode_opt_out_confirmation)
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        mPowersaveModeSwitch.setChecked(isSwitchEnabled);
                        dialog.dismiss();
                    }
                })
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        ApplicationLifetime.terminate(true);
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        mPowersaveModeSwitch.setChecked(isSwitchEnabled);
                    }
                })
                .create();

        return dialog;
    }

    private AlertDialog createLowPowerAlertDialog() {

        AlertDialog dialog = new AlertDialog.Builder(getActivity(), R.style.AlertDialogTheme)
                .setTitle(R.string.powersave_mode_turn_on_title)
                .setMessage(R.string.powersave_mode_opt_in_confirmation)
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.dismiss();
                        getActivity().finish();
                    }
                })
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        PrefServiceBridge.getInstance().setPowersaveModeEnabled(true);
                        dialog.dismiss();
                        ApplicationLifetime.terminate(true);
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                    }
                })
                .create();

        return dialog;
    }
}

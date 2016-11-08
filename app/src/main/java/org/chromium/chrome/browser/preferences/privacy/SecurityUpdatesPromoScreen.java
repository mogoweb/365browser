/*
 * Copyright (c) 2016, The Linux Foundation. All rights reserved.
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

package org.chromium.chrome.browser.preferences.privacy;


import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.widget.SwitchCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;

import org.chromium.base.CommandLine;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeSwitches;
import org.chromium.chrome.browser.multiwindow.MultiWindowUtils;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;

/**
 * The promo screen informing users about security updates for the browser.
 */
public class SecurityUpdatesPromoScreen extends Dialog implements View.OnClickListener,
        DialogInterface.OnDismissListener {
    /**
     * Key used to save whether the promo screen is shown and the time in milliseconds since epoch,
     * it was shown.
     */
    private static final String SHARED_PREF_DISPLAYED_PROMO = "displayed_security_updates_promo";
    private static final String SHARED_PREF_DISPLAYED_PROMO_TIME_MS =
            "displayed_security_updates_promo_time_ms";

    private static View getContentView(Context context) {
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        return inflater.inflate(R.layout.security_updates_promo_screen, null);
    }

    @Override
    public void onClick(View v) {
        dismiss();
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        saveSecurityUpdatesPromoDisplayed(getContext());
    }

    public static boolean shouldShowPromo(Activity parentActivity) {
        if (CommandLine.getInstance().hasSwitch(ChromeSwitches.DISABLE_FIRST_RUN_EXPERIENCE)) {
            return false;
        }
        if (MultiWindowUtils.getInstance().isLegacyMultiWindow(parentActivity)) return false;

        return !getDisplayedSecurityUpdatesPromo(parentActivity);
    }

    /**
     * Launch the promo, if it needs to be displayed.
     */
    public static void launchSecurityUpdatesPromo(Activity parentActivity) {
        SecurityUpdatesPromoScreen promoScreen = new SecurityUpdatesPromoScreen(parentActivity);
        promoScreen.setOnDismissListener(promoScreen);
        promoScreen.show();
    }

    public SecurityUpdatesPromoScreen(Context context) {
        super(context, R.style.DataReductionPromoScreenDialog);
        setContentView(getContentView(context), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));

        // Remove the shadow from the enable button.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Button enableButton = (Button) findViewById(R.id.enable_button);
            enableButton.setStateListAnimator(null);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Keep the window full screen otherwise the flip animation will frame-skip.
        getWindow().setLayout(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT);

        PrefServiceBridge.getInstance().setSecurityUpdatesDefault();
        addListenerOnButton();
    }

    private void addListenerOnButton() {
        int [] interactiveViewIds = new int[] {
                R.id.enable_button,
                R.id.close_button
        };

        for (int interactiveViewId : interactiveViewIds) {
            findViewById(interactiveViewId).setOnClickListener(this);
        }

        SwitchCompat mobileUpdatesSwitch = (SwitchCompat) findViewById(R.id.enable_mobile_updates);
        mobileUpdatesSwitch.setChecked(
                PrefServiceBridge.getInstance().getSecurityUpdatesOnCellular());
        mobileUpdatesSwitch.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                PrefServiceBridge.getInstance().setSecurityUpdatesOnCellular(isChecked);
            }
        });
    }

    /**
     * Returns whether the promo has been displayed before.
     *
     * @param context An Android context.
     * @return Whether the promo has been displayed.
     */
    public static boolean getDisplayedSecurityUpdatesPromo(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
                SHARED_PREF_DISPLAYED_PROMO, false);
    }

    /**
     * Saves shared prefs indicating that the promo screen has been displayed
     * at the current time.
     *
     * @param context An Android context.
     */
    public static void saveSecurityUpdatesPromoDisplayed(Context context) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putBoolean(SHARED_PREF_DISPLAYED_PROMO, true)
                .putLong(SHARED_PREF_DISPLAYED_PROMO_TIME_MS, System.currentTimeMillis())
                .apply();
    }
}
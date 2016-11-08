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

package org.chromium.chrome.browser.preferences;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.preference.PreferenceCategory;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import org.chromium.chrome.R;

/**
 * A preference category that behaves like a Preference but allows customization and provides a
 * callback interface.
 */
public class BrowserSwitchPreferenceCategory extends PreferenceCategory implements
        CompoundButton.OnCheckedChangeListener {
    TextView mTitle;
    View mTitleBar;
    Switch mToggle;
    String mName;
    int mBackgroundColor = - 1;
    int mAccentColor = -1;
    boolean mPersistedSetting;
    boolean mDisplayPropertiesSet;
    ModulePreferenceToggled mToggleCallback;

    public BrowserSwitchPreferenceCategory(Context context, AttributeSet attrs,
                                           int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public BrowserSwitchPreferenceCategory(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public BrowserSwitchPreferenceCategory(Context context) {
        super(context);
    }

    /**
     * An interface to implement to get a callback when the preference is toggled.
     * @param newSetting The new setting of the toggle.
     */
    public interface ModulePreferenceToggled {
        public void onSettingToggled(boolean newSetting);
    }

    @Override
    public void onBindView(@NonNull View view) {
        super.onBindView(view);
        ImageView shadow = (ImageView)view.findViewById(R.id.category_border_top);
        if (shadow != null) shadow.setVisibility(View.GONE);
        mTitle = (TextView) view.findViewById(android.R.id.title);
        mTitleBar = view.findViewById(R.id.browser_pref_cat_first);

        mToggle = (Switch) view.findViewById(R.id.browser_pref_cat_switch_btn);

        if (mToggle != null) {
            mToggle.setVisibility(View.VISIBLE);
        }
        if (mDisplayPropertiesSet) applyDisplayProperties();
    }

    private void applyDisplayProperties() {
        if (mTitle != null) {
            if (mAccentColor != -1) mTitle.setTextColor(mAccentColor);
            if (!TextUtils.isEmpty(mName)) mTitle.setText(mName);
        }

        if (mTitleBar != null && mBackgroundColor != -1) {
            mTitleBar.setBackgroundColor(mBackgroundColor);
        }

        if (mToggle != null) {
                mToggle.setChecked(mPersistedSetting);

            if (mAccentColor != -1) {
                int[][] states = new int[][]{
                        new int[]{android.R.attr.state_checked},  // checked
                        new int[]{-android.R.attr.state_checked}, // unchecked
                };

                int[] colors = new int[]{
                        mAccentColor,
                        Color.GRAY
                };

                ColorStateList myList = new ColorStateList(states, colors);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    mToggle.setThumbTintList(myList);
                }
            }

            mToggle.setOnCheckedChangeListener(this);
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        mPersistedSetting = isChecked;
        if (mToggleCallback != null) mToggleCallback.onSettingToggled(isChecked);
        callChangeListener(isChecked); //Preserving regular preference behavior
    }

    /**
     * Use this function to setup the colors and initial setting of the Preference
     * Call after the preference is added from xml.
     * @param title The title of preference
     * @param accentColor The color to be used for the Text and the switch's "ON" position.
     *                    Use -1 to ignore.
     * @param backgroundColor The color to be used for the Background of the preference.
     *                        Use -1 to ignore.
     * @param startPermission The initial setting for the preference to be initialized to.
     * @param callback The callback to receive notifications when the setting is toggled. This is
     *                 sometimes useful when you need access to member variables of the class
     *                 holding onto this preference. Pass NULL if it's not needed.
     */
    public void setDisplayProperties(String title, int accentColor, int backgroundColor,
                                     boolean startPermission,
                                     ModulePreferenceToggled callback) {
        mDisplayPropertiesSet = true;
        mName = title;
        mAccentColor = accentColor;
        mBackgroundColor = backgroundColor;
        mPersistedSetting = startPermission;
        mToggleCallback = callback;
    }

    public boolean isChecked() {
        if (mToggle != null) return mToggle.isChecked();
        return false;
    }
}
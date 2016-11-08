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
import android.support.v7.widget.SwitchCompat;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.widget.CompoundButton;
import android.widget.LinearLayout;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.preferences.privacy.PrivacyPreferences;

/**
 * A switch that represents a single Secure Connect Rule.
 */
public class SecureConnectDetailItem extends LinearLayout {
    private SwitchCompat mSwitch;
    private CompoundButton.OnCheckedChangeListener mListener;

    private CharSequence mTitle;
    private boolean mChecked;

    public SecureConnectDetailItem(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate () {
        super.onFinishInflate();
        mSwitch = (SwitchCompat) findViewById(R.id.rule_switch);
        if (mSwitch != null) {
            mSwitch.setText(mTitle);
            mSwitch.setChecked(mChecked);
            mSwitch.setOnCheckedChangeListener(mListener);
        }
    }

    public void setTitle(CharSequence title) {
        if (!TextUtils.isEmpty(title)) {
            mTitle = title;
            if (mSwitch != null) mSwitch.setText(title);
        }
    }

    public void setChecked(boolean checked) {
        if (checked != mSwitch.isChecked()) {
            mChecked = checked;
            if (mSwitch != null) mSwitch.setChecked(checked);
        }
    }

    public void setListener(CompoundButton.OnCheckedChangeListener listener) {
        mListener = listener;
        if (mSwitch != null) mSwitch.setOnCheckedChangeListener(mListener);
    }
}
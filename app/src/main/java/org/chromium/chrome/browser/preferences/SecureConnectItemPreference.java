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
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.CompoundButton;

import org.chromium.chrome.R;

public class SecureConnectItemPreference extends Preference {
    private SecureConnectDetailItem mItem;
    private boolean mChecked;
    private CharSequence mTitle;

    public SecureConnectItemPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        mItem = (SecureConnectDetailItem) view.findViewById(R.id.secure_connect_item);
        if (mItem != null) {
            mItem.setListener(null); // Clear the old listener if there is one.
            mItem.setTitle(mTitle);
            mItem.setChecked(mChecked);
            mItem.setListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    mChecked = isChecked;
                    getOnPreferenceClickListener()
                            .onPreferenceClick(SecureConnectItemPreference.this);
                }
            });
        }
    }

    public SecureConnectDetailItem getItem() {
        return mItem;
    }

    public void setChecked(boolean checked) {
        mChecked = checked;
        if (mItem != null) mItem.setChecked(mChecked);
    }

    public boolean getChecked() {
        return mChecked;
    }

    @Override
    public void setTitle(CharSequence title) {
        mTitle = title;
        if (mItem != null) mItem.setTitle(mTitle);
    }

    @Override
    public CharSequence getTitle() {
        if (mTitle != null) return mTitle;
        return "";
    }
}

/*
 * Copyright (c) 2015-2016, The Linux Foundation. All rights reserved.
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

package org.chromium.chrome.browser.preferences.website;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.preference.PreferenceCategory;
import android.provider.Browser;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.widget.TintedImageView;

import java.util.Locale;

public class SmartProtectPreferenceCategory extends PreferenceCategory {
    TextView mTitle;
    View mTitleBar;
    int mBackgroundColor;
    int mTextColor;
    String mSupportURL;
    String mTitleText;

    public SmartProtectPreferenceCategory(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public SmartProtectPreferenceCategory(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SmartProtectPreferenceCategory(Context context) {
        super(context);
    }

    @Override
    public void onBindView(@NonNull View view) {
        super.onBindView(view);
        mTitle = (TextView) view.findViewById(android.R.id.title);
        mTitleBar = view.findViewById(R.id.browser_pref_cat_first);
        if (mTitleBar != null) {
            mTitleBar.setBackgroundColor(mBackgroundColor);
        }
        if (mTitle != null) {
            mTitle.setTextColor(mTextColor);

            if (!TextUtils.isEmpty(mTitleText))
                mTitle.setText(mTitleText);
        }

        TintedImageView btn = (TintedImageView) view.findViewById(R.id.button);
        if (btn != null && !TextUtils.isEmpty(mSupportURL)) {
            btn.setVisibility(View.VISIBLE);
            btn.setImageDrawable(ApiCompatibilityUtils.getDrawable(getContext().getResources(),
                    R.drawable.help_outline));

            ColorStateList colorList = ColorStateList.valueOf(mTextColor);
            btn.setTint(colorList);

            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(mSupportURL));
                    intent.putExtra(Browser.EXTRA_APPLICATION_ID, getContext().getPackageName());
                    intent.putExtra(Browser.EXTRA_CREATE_NEW_TAB, true);
                    intent.setPackage(getContext().getPackageName());
                    getContext().startActivity(intent);
                }
            });
        }
    }

    /*
    Call immediately after adding preferences from xml.
     */
    public void setTitleAttributes(int backgroundColor, int textColor) {
        mBackgroundColor = backgroundColor;
        mTextColor = textColor;
    }

    public void setSupportURL(String url) {
        String lang = Locale.getDefault().getLanguage();
        String localizedUrl = url.replace("$HOST_LANG", lang);
        mSupportURL = localizedUrl;
    }

    public void setTitle(String title) {
        mTitleText = title;
    }
}
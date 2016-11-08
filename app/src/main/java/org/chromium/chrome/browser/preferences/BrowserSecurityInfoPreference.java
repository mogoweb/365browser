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
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.webkit.URLUtil;
import android.widget.TextView;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.ContentSettingsType;
import org.chromium.chrome.browser.incognito.IncognitoOnlyModeUtil;
import org.chromium.chrome.browser.preferences.website.ContentSetting;
import org.chromium.chrome.browser.preferences.website.ContentSettingsResources;
import org.chromium.chrome.browser.preferences.website.Website;
import org.chromium.components.security_state.ConnectionSecurityLevel;

import java.util.EnumMap;
import java.util.Map;

/*
A preference that shows the status of the website. It can show up to 3 different
levels of security information. Info, Error, and Warning depending on the status
of the website and its security level.
 */
public class BrowserSecurityInfoPreference extends Preference {
    private SiteSecurityViewFactory mSecurityViews;
    private int mSecurityLevel = -1;

    public BrowserSecurityInfoPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.browser_website_security_info);
        setSelectable(false);
    }


    @Override
    public void onBindView(@NonNull View view) {
        if (mSecurityViews != null) {
            mSecurityViews.setResource(SiteSecurityViewFactory.ViewType.ERROR,
                    view, R.id.site_security_error);
            mSecurityViews.setResource(SiteSecurityViewFactory.ViewType.WARNING,
                    view, R.id.site_security_warning);
            mSecurityViews.setResource(SiteSecurityViewFactory.ViewType.INFO,
                    view, R.id.site_security_verbose);
        }
    }

    /**
     * Call after adding the perefernce from XML
     * @param securityLevel The @Link ConnectionSecurityLevel of the website
     */
    public void setupSecurityInformation(int securityLevel) {
        mSecurityViews = new SiteSecurityViewFactory();
        mSecurityLevel = securityLevel;
        updateSecurityInfo();
    }

    /*
    Check if there is any information to be displayed for this website. Setup must be called before.
     */
    public boolean hasInformation() {
        return !mSecurityViews.mIsEmpty;
    }

    /**
     * Refresh the views based on the new state of the website
     * @param site The Webstie object based on which the views will update.
     */
    public void refreshInformation(Website site) {
        //Now update any warnings we want to show to the user
        String securityWarnings = "";

        if (ContentSetting.ALLOW.equals(site.getGeolocationPermission())) {
            securityWarnings += (securityWarnings.isEmpty()) ? getContext().getResources()
                    .getString(ContentSettingsResources.getTitle(ContentSettingsType.
                            CONTENT_SETTINGS_TYPE_GEOLOCATION))
                    : "";
        }

        if (!securityWarnings.isEmpty()) {
            if (!URLUtil.isHttpsUrl(site.getAddress().getOrigin())
                    && !IncognitoOnlyModeUtil.getInstance().isIncognitoOnlyModeEnabled()) {
                securityWarnings += " ";
                securityWarnings += getContext().getResources()
                        .getString(R.string.website_settings_unsecure_blocked);
            }
            securityWarnings += " ";
            securityWarnings += getContext().getResources()
                    .getString(R.string.page_info_permission_allowed);
            mSecurityViews.setText(SiteSecurityViewFactory.ViewType.WARNING, securityWarnings);
        } else if (!URLUtil.isHttpsUrl(site.getAddress().getOrigin())
                    && !IncognitoOnlyModeUtil.getInstance().isIncognitoOnlyModeEnabled()) {
                securityWarnings += getContext().getResources()
                        .getString(R.string.website_settings_unsecure_blocked);
                mSecurityViews.setText(SiteSecurityViewFactory.ViewType.WARNING, securityWarnings);
        } else {
            mSecurityViews.clearText(SiteSecurityViewFactory.ViewType.WARNING);
        }
    }

    private void updateSecurityInfo() {
        switch (mSecurityLevel) {
            case ConnectionSecurityLevel.NONE:
                break;
            case ConnectionSecurityLevel.SECURITY_WARNING:
                mSecurityViews.appendText(SiteSecurityViewFactory.ViewType.WARNING,
                        getContext().getResources().getString(R.string.ssl_warning));
                break;
            case ConnectionSecurityLevel.SECURITY_ERROR:
                mSecurityViews.appendText(SiteSecurityViewFactory.ViewType.ERROR,
                        getContext().getResources().getString(R.string.ssl_error));
                break;
            case ConnectionSecurityLevel.SECURE:
            case ConnectionSecurityLevel.EV_SECURE:
                mSecurityViews.appendText(SiteSecurityViewFactory.ViewType.INFO,
                        getContext().getResources().getString(R.string.ssl_secure));
                break;
            default:
                break;
        }
    }

    private static class SiteSecurityViewFactory {
        private class SiteSecurityView {
            private TextView mTextView;
            private String mDisplayText;

            public SiteSecurityView(View parent, int resId, String text) {
                mTextView = (TextView) parent.findViewById(resId);
                mTextView.setText(text);
                mDisplayText = text;
                updateVisibility();
            }

            private void updateVisibility() {
                if (TextUtils.isEmpty(mDisplayText)) {
                    mTextView.setVisibility(View.GONE);
                } else {
                    mTextView.setVisibility(View.VISIBLE);
                }
            }

            public void setText(String text) {
                mDisplayText = text;
                mTextView.setText(mDisplayText);
                updateVisibility();
            }

            public void clearText() {
                mDisplayText = null;
                updateVisibility();
            }
        }

        public enum ViewType{
            ERROR,
            WARNING,
            INFO
        };

        private Map<ViewType, SiteSecurityView> mViews =
                new EnumMap<ViewType, SiteSecurityView>(ViewType.class);
        private Map<ViewType, String> mTexts = new EnumMap<ViewType, String>(ViewType.class);

        private boolean mIsEmpty = true;

        public void setText(ViewType type, String text) {
            mTexts.put(type, text);

            SiteSecurityView view = mViews.get(type);
            if (view != null) {
                view.setText(text);
            }

            mIsEmpty = false;
        }

        public void appendText(ViewType type, String text) {
            String newText = mTexts.get(type);
            if (newText != null)
                newText += " " + text;
            else
                newText = text;

            mTexts.put(type, newText);

            SiteSecurityView view = mViews.get(type);
            if (view != null) {
                view.setText(newText);
            }

            mIsEmpty = false;
        }

        public void clearText(ViewType type) {
            mTexts.remove(type);

            SiteSecurityView view = mViews.get(type);
            if (view != null) {
                view.clearText();
            }

            boolean empty = true;
            for (Map.Entry<ViewType, String> entry: mTexts.entrySet()) {
                if (!entry.getValue().isEmpty()) {
                    empty = false;
                }
            }
            mIsEmpty = empty;
        }

        public void setResource(ViewType type, View parent, int resId) {
            String text = mTexts.get(type);
            mViews.remove(type);
            mViews.put(type, new SiteSecurityView(parent, resId, text));
        }
    }

}
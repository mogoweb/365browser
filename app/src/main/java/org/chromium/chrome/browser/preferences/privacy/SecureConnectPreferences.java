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

import android.os.Bundle;
import android.preference.Preference;
import android.preference.SwitchPreference;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.preferences.ChromeSwitchPreference;
import org.chromium.chrome.browser.preferences.SecureConnectDetails;
import org.chromium.chrome.browser.preferences.SecureConnectPreferenceHandler;
import org.chromium.chrome.browser.preferences.website.SingleCategoryPreferences;
import org.chromium.content.browser.SecureConnect;

import java.util.ArrayList;
import java.util.Locale;

public class SecureConnectPreferences extends SingleCategoryPreferences {

    private SecureConnectDetails mSecureConnectRecycler;
    private ArrayList<SecureConnect.URLInfo> mDisabledRulesForDisplay;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Preference secureConnectModifiedRules =
                getPreferenceScreen().findPreference(SECURE_CONNECT_MODIFIED_RULES_KEY);
        getPreferenceScreen().removePreference(secureConnectModifiedRules);
        setupStats();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
        View view = super.onCreateView(inflater, container, bundle);
        if (view == null) return view;
        ListView list = (ListView) view.findViewById(android.R.id.list);

        if (list == null) return view;

        ViewGroup.LayoutParams params = list.getLayoutParams();
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        list.setLayoutParams(params);
        list.setPadding(0, list.getPaddingTop(), 0, list.getPaddingBottom());
        list.setDivider(null);
        list.setDividerHeight(0);

        list.setOnHierarchyChangeListener(
                new ViewGroup.OnHierarchyChangeListener() {
                    @Override
                    public void onChildViewAdded(View parent, View child) {
                        onChildViewAddedToHierarchy(parent, child);
                    }

                    @Override
                    public void onChildViewRemoved(View parent, View child) {

                    }
                }
        );

        return view;
    }

    private void onChildViewAddedToHierarchy(View parent, View child) {
        if (child.getId() == R.id.secure_connect_details_layout) {
            SecureConnectDetails view = (SecureConnectDetails)
                    child.findViewById(R.id.secure_connect_recycler);
            if (view != null) {
                mSecureConnectRecycler = view;
                mSecureConnectRecycler.updateRulsetArray(mDisabledRulesForDisplay);
            }
            TextView title = (TextView) child.findViewById(R.id.recycler_title);
            if (title != null) {
                title.setText(getFormattedTitle(
                        R.string.website_settings_secure_connect_disabled_rulesets));
            }
        }
    }

    @Override
    protected void resetList() {
        super.resetList();
        setupStats();
    }

    @Override
    protected void websitesReady(int count) {
        super.websitesReady(count);

        SwitchPreference secureContentOnly = (SwitchPreference)
                findPreference(SECURE_CONNECT_SECURE_CONTENT_KEY);
        ChromeSwitchPreference globalToggle = (ChromeSwitchPreference)
                getPreferenceScreen().findPreference(READ_WRITE_TOGGLE_KEY);
        if (secureContentOnly != null) {
            if (!globalToggle.isChecked() && count == 0) {
                secureContentOnly.setEnabled(false);
                secureContentOnly.setChecked(false);
            } else {
                secureContentOnly.setEnabled(true);
            }
        }
    }

    private void setupStats() {
        long mainFrameCount = SecureConnectPreferenceHandler.getPageUpgradeCount(true);
        long subFrameCount = SecureConnectPreferenceHandler.getPageUpgradeCount(false);
        Preference mainFrame = findPreference(SECURE_CONNECT_MAINFRAME_KEY);
        if (mainFrame != null) {
            mainFrame.setTitle(getFormattedTitle(
                    R.string.secure_connect_mainframes, mainFrameCount));
        }
        Preference subFrame = findPreference(SECURE_CONNECT_SUBFRAME_KEY);
        if (subFrame != null) {
            subFrame.setTitle(getFormattedTitle(R.string.secure_connect_subframes, subFrameCount));
        }
        SwitchPreference secureContentOnly = (SwitchPreference)
                findPreference(SECURE_CONNECT_SECURE_CONTENT_KEY);
        if (secureContentOnly != null) {
            secureContentOnly.setOnPreferenceChangeListener(null);
            secureContentOnly.setChecked(SecureConnectPreferenceHandler
                    .getSecureContentOnlyEnabled());
            secureContentOnly.setOnPreferenceChangeListener(this);
        }

        Preference secureConnectList = findPreference(SECURE_CONNECT_MODIFIED_RULES_KEY);
        String[] disabledRulesets = SecureConnectPreferenceHandler.getDisabledRulesets();
        mDisabledRulesForDisplay = new ArrayList<>();
        if (disabledRulesets != null && disabledRulesets.length > 0 && secureConnectList != null) {
            for (String rulesetName : disabledRulesets) {
                mDisabledRulesForDisplay.add(new SecureConnect
                        .URLInfo(null, false, rulesetName, false, null));
            }

        } else if (secureConnectList != null) {
            getPreferenceScreen().removePreference(secureConnectList);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (SECURE_CONNECT_SECURE_CONTENT_KEY.equals(preference.getKey())) {
            SecureConnectPreferenceHandler.setSecureContentOnlyMode((boolean) newValue);
            return true;
        }
        return super.onPreferenceChange(preference, newValue);
    }

    /*
    See @ExpandableGroupPreference.setGroupTitle
     */
    private CharSequence getFormattedTitle (int resourceId, long count) {
        SpannableStringBuilder spannable =
                new SpannableStringBuilder(getResources().getString(resourceId));
        String prefCount = String.format(Locale.getDefault(), " - %,d", count);
        spannable.append(prefCount);

        // Color the first part of the title blue.
        ForegroundColorSpan blueSpan = new ForegroundColorSpan(
                ApiCompatibilityUtils.getColor(getResources(),
                        R.color.pref_accent_color));
        spannable.setSpan(blueSpan, 0, spannable.length() - prefCount.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        // Gray out the total count of items.
        int gray = ApiCompatibilityUtils.getColor(getResources(),
                R.color.expandable_group_dark_gray);
        spannable.setSpan(new ForegroundColorSpan(gray),
                spannable.length() - prefCount.length(),
                spannable.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return spannable;
    }

    private CharSequence getFormattedTitle (int resourceId) {
        SpannableStringBuilder spannable =
                new SpannableStringBuilder(getResources().getString(resourceId));
        ForegroundColorSpan blueSpan = new ForegroundColorSpan(
                ApiCompatibilityUtils.getColor(getResources(),
                        R.color.pref_accent_color));
        spannable.setSpan(blueSpan, 0, spannable.length() - 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        return spannable;
    }
}
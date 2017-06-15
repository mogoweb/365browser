// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.privacy;

import android.os.Bundle;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.browsing_data.ClearBrowsingDataTab;
import org.chromium.chrome.browser.preferences.ClearBrowsingDataTabCheckBoxPreference;
import org.chromium.chrome.browser.sync.ProfileSyncService;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.document.TabDelegate;
import org.chromium.components.signin.ChromeSigninController;
import org.chromium.components.sync.AndroidSyncSettings;
import org.chromium.components.sync.ModelType;

/**
 * A simpler version of {@link ClearBrowsingDataPreferences} with fewer dialog options and more
 * explanatory text.
 */
public class ClearBrowsingDataPreferencesBasic extends ClearBrowsingDataPreferencesTab {
    /** The my activity URL. */
    private static final String MY_ACTIVITY_URL =
            "https://myactivity.google.com/myactivity/?utm_source=chrome_cbd";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ClearBrowsingDataTabCheckBoxPreference historyCheckbox =
                (ClearBrowsingDataTabCheckBoxPreference) findPreference(PREF_HISTORY);
        ClearBrowsingDataTabCheckBoxPreference cookiesCheckbox =
                (ClearBrowsingDataTabCheckBoxPreference) findPreference(PREF_COOKIES);

        historyCheckbox.setLinkClickDelegate(new Runnable() {
            @Override
            public void run() {
                new TabDelegate(false /* incognito */)
                        .launchUrl(MY_ACTIVITY_URL, TabModel.TabLaunchType.FROM_CHROME_UI);
            }
        });

        if (ChromeSigninController.get().isSignedIn()) {
            if (isHistorySyncEnabled()) {
                historyCheckbox.setSummary(R.string.clear_browsing_history_summary_synced);
            } else {
                historyCheckbox.setSummary(R.string.clear_browsing_history_summary_signed_in);
            }
            cookiesCheckbox.setSummary(
                    R.string.clear_cookies_and_site_data_signed_in_summary_basic);
        }
    }

    private boolean isHistorySyncEnabled() {
        boolean syncEnabled = AndroidSyncSettings.isSyncEnabled(getActivity());
        ProfileSyncService syncService = ProfileSyncService.get();
        return syncEnabled && syncService != null
                && syncService.getActiveDataTypes().contains(ModelType.HISTORY_DELETE_DIRECTIVES);
    }

    @Override
    protected DialogOption[] getDialogOptions() {
        return new DialogOption[] {DialogOption.CLEAR_HISTORY,
                DialogOption.CLEAR_COOKIES_AND_SITE_DATA, DialogOption.CLEAR_CACHE};
    }

    @Override
    protected int getPreferenceType() {
        return ClearBrowsingDataTab.BASIC;
    }
}

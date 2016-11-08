/*
 * Copyright (c) 2016 The Linux Foundation. All rights reserved.
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

import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.incognito.IncognitoOnlyModeUtil;

/**
 * A preference to choose search engine for regular and incognito tabs.
 */
public class SearchEnginePreferenceFragment extends PreferenceFragment {

    public static final String PREF_SEARCH_ENGINE_FOR_REGULAR = "search_engine_regular";
    public static final String EXTRA_SHOW_SEARCH_ENGINE_PICKER = "show_search_engine_picker";
    private boolean mShowSearchEnginePicker;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.search_engine_preferences);
        getActivity().setTitle(R.string.prefs_search_engine);

        PreferenceScreen preferenceScreen = getPreferenceScreen();
        boolean isIncognitoOnlyBrowser = IncognitoOnlyModeUtil.getInstance()
                .isIncognitoOnlyModeEnabled();
        SearchEnginePreference searchEngineRegularPref =
                (SearchEnginePreference) findPreference(PREF_SEARCH_ENGINE_FOR_REGULAR);
        if(isIncognitoOnlyBrowser) {
            preferenceScreen.removePreference(searchEngineRegularPref);
        }

        if (savedInstanceState == null && getArguments() != null
                && getArguments().getBoolean(EXTRA_SHOW_SEARCH_ENGINE_PICKER, false)) {
            mShowSearchEnginePicker = true;
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (mShowSearchEnginePicker) {
            mShowSearchEnginePicker = false;
            ((SearchEnginePreference) findPreference(PREF_SEARCH_ENGINE_FOR_REGULAR))
                    .showDialog();
        }
    }
}

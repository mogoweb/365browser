/*
 * Copyright (c) 2016, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided
 * with the distribution.
 * * Neither the name of The Linux Foundation nor the names of its
 * contributors may be used to endorse or promote products derived
 * from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package org.codeaurora.swe;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.search_engines.TemplateUrlService;
import org.chromium.chrome.browser.search_engines.TemplateUrlService.TemplateUrl;
import org.chromium.chrome.browser.search_engines.TemplateUrlService.LoadListener;
import org.chromium.chrome.browser.util.Logger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

public class MigrateSWEData {
    private static final String LOGTAG = "MigrateSWEData";
    private static MigrateSWEData sThis;

    public static MigrateSWEData getInstance() {
        if (null == sThis)
            sThis = new MigrateSWEData();

        return sThis;
    }

    public void migrateHomepageSharedPreference(Context ctx, String defaultHomepageUrl) {
        final String PREF_HOMEPAGE_ENABLED = "homepage";
        final String PREF_HOMEPAGE_CUSTOM_URI = "homepage_custom_uri";
        final String PREF_HOMEPAGE_USE_DEFAULT_URI = "homepage_partner_enabled";

        SharedPreferences prefs =
            PreferenceManager.getDefaultSharedPreferences(ctx);
        Map<String, ?> allPrefs = prefs.getAll();
        Object value = allPrefs.get(PREF_HOMEPAGE_ENABLED);
        if (null != value) {
            SharedPreferences.Editor prefEditor = prefs.edit();
            if (value instanceof String) {
                Logger.v(LOGTAG, "Migrating homepage URL...");

                String homepageUrl = (String)value;
                prefEditor.remove(PREF_HOMEPAGE_ENABLED);
                prefEditor.putString(PREF_HOMEPAGE_CUSTOM_URI, homepageUrl);
                prefEditor.putBoolean(PREF_HOMEPAGE_ENABLED, true);
                if (defaultHomepageUrl.equals(homepageUrl))
                    prefEditor.putBoolean(PREF_HOMEPAGE_USE_DEFAULT_URI, true);
                else
                    prefEditor.putBoolean(PREF_HOMEPAGE_USE_DEFAULT_URI, false);
            }
            else if (!(value instanceof Boolean)) {
                prefEditor.remove(PREF_HOMEPAGE_ENABLED);
            }
            prefEditor.apply();
        }
    }

    public void migrateSearchEnginePreference(Context ctx) {
        new MigrateSearchEnginePreference().migrate(ctx);
    }

    public void migratePowerSavePreference(Context ctx) {
        final String POWER_SAVE_ENABLED = "powersave_enabled";
        SharedPreferences sharedPrefs =
            PreferenceManager.getDefaultSharedPreferences(ctx);
        if (sharedPrefs.contains(POWER_SAVE_ENABLED)) {
            boolean enabled = sharedPrefs.getBoolean(POWER_SAVE_ENABLED, false);

            // setPowersaveModeEnabled sets the shared preference as well as call
            // native method to set power save mode.
            PrefServiceBridge.getInstance().setPowersaveModeEnabled(enabled);
        }
    }

    private class MigrateSearchEnginePreference implements LoadListener {
        private String mLocaleDSEKey;
        private String mDSEName;
        private SharedPreferences mSharedPrefs;
        private TemplateUrlService mService;

        private final static String PREF_USER_SEARCH_ENGINE = "user_search_engine-";
        private final static String PREF_SEARCH_ENGINE = "search_engine";

        void migrate(Context ctx) {
            mLocaleDSEKey = PREF_USER_SEARCH_ENGINE +
                ctx.getResources().getConfiguration().locale;

            mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(ctx);
            mDSEName = mSharedPrefs.getString(mLocaleDSEKey, null);
            if (null == mDSEName)
                return;

            mService = TemplateUrlService.getInstance();
            if (mService.isLoaded())
                onTemplateUrlServiceLoaded();
            else
                mService.registerLoadListener((LoadListener)this);
        }

        @Override
        public void onTemplateUrlServiceLoaded() {
            Logger.v(LOGTAG, "Migrating default search engine...");
            List<TemplateUrl> searchUrls = mService.getLocalizedSearchEngines();
            ListIterator<TemplateUrl> it = searchUrls.listIterator();
            while (it.hasNext()) {
                TemplateUrl url = it.next();
                if (mDSEName.equalsIgnoreCase(url.getShortName())) {
                    mService.setSearchEngine(url.getIndex());
                    break;
                }
            }

            deleteSearchEngineSharedPreference();
        }

        private void deleteSearchEngineSharedPreference() {
            Logger.v(LOGTAG, "Deleting keys from shared preference");
            SharedPreferences.Editor prefEditor = mSharedPrefs.edit();
            for (String key : getAllStartsWithKeys(PREF_USER_SEARCH_ENGINE)) {
                prefEditor.remove(key);
            }
            prefEditor.remove(PREF_SEARCH_ENGINE);
            prefEditor.apply();
        }

        private List<String> getAllStartsWithKeys(String key) {
            List<String> list = new ArrayList<String>();
            for (String prefsKey : mSharedPrefs.getAll().keySet()) {
                if (prefsKey.startsWith(key))
                    list.add(prefsKey);
            }

            return list;
        }
    }
}

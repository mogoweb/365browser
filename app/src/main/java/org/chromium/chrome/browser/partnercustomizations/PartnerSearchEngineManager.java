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

package org.chromium.chrome.browser.partnercustomizations;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;

import org.chromium.chrome.browser.search_engines.TemplateUrlService;
import org.chromium.chrome.browser.util.Logger;
import org.codeaurora.swe.partnerbrowsercustomizations.TemplateUrl;

import java.util.ArrayList;

public class PartnerSearchEngineManager {
    private static final String LOGTAG = "PartnerSearchEngineManager";
    private static final String PROVIDER_AUTHORITY = ".partnerbrowsercustomizations";
    private static final String SEARCH_ENGINES_PATH = "searchengines";

    // Private Search Engine structure.
    private static final String SEARCH_ENGINE_COLUMN_ID = "_id";
    private static final String SEARCH_ENGINE_COLUMN_NAME = "name";
    private static final String SEARCH_ENGINE_COLUMN_KEYWORD = "keyword";
    private static final String SEARCH_ENGINE_COLUMN_SEARCH_URL = "searchUrl";
    private static final String SEARCH_ENGINE_COLUMN_FAVICON_URL = "faviconUrl";
    private static final String SEARCH_ENGINE_COLUMN_DEFAULT= "default";
    private static final String SEARCH_ENGINE_COLUMN_DEFAULT_INCOGNITO= "default_incognito";


    private static final String[] SEARCH_ENGINE_PROJECTION = {
        SEARCH_ENGINE_COLUMN_ID,
        SEARCH_ENGINE_COLUMN_NAME,
        SEARCH_ENGINE_COLUMN_KEYWORD,
        SEARCH_ENGINE_COLUMN_SEARCH_URL,
        SEARCH_ENGINE_COLUMN_FAVICON_URL,
        SEARCH_ENGINE_COLUMN_DEFAULT,
        SEARCH_ENGINE_COLUMN_DEFAULT_INCOGNITO
    };

    private static PartnerSearchEngineManager sInstance;
    private Context mContext;
    private ReadSearchEngineTask mTask;
    private ArrayList<TemplateUrl> mSearchEngines;

    private PartnerSearchEngineManager(Context context) {
        mContext = context;
        String providerAuthority = context.getPackageName() + PROVIDER_AUTHORITY;
        Uri contentUri = new Uri.Builder().scheme("content")
            .authority(providerAuthority).build();
        contentUri = contentUri.buildUpon()
            .appendPath(SEARCH_ENGINES_PATH).build();
        mTask = new ReadSearchEngineTask(mContext, contentUri);
    }

    public void load() {
        if (mTask.getStatus() == AsyncTask.Status.PENDING)
            mTask.execute();
    }

    public boolean shouldUsePreloadSearchEngine() {
        return (mSearchEngines != null && mSearchEngines.size() > 0);
    }

    /**
     * Returns the singleton instance of PartnerSearchEngineManager, creating it if needed.
     * @param context Any old Context.
     */
    public static PartnerSearchEngineManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new PartnerSearchEngineManager(context);
        }
        return sInstance;
    }

    public static PartnerSearchEngineManager getInstance() {
        return sInstance;
    }

    public void injectSearchEngines() {
        if (shouldUsePreloadSearchEngine()) {
            String defaultKeyword = "";
            String defaultIncognitoKeyword = "";

            for (int i = 0; i < mSearchEngines.size(); i++) {
                TemplateUrl url = mSearchEngines.get(i);
                TemplateUrlService.getInstance().addSearchEngine(url.getShortName(),
                        url.getKeyword(), url.getSearchURL(), url.getFaviconUrl());
                if (url.isDefault())
                    defaultKeyword = url.getKeyword();
                if (url.isDefaultIncognito())
                    defaultIncognitoKeyword = url.getKeyword();
            }
            // use keyword and set the default search engine
            TemplateUrlService.getInstance().setDefaultSearchEngine(defaultKeyword);

            // Set default search engine for incognito tabs
            // Store the default search engine now and set it once it's TemplateUrlService is loaded
            TemplateUrlService.getInstance(true)
                    .setDefaultSearchEngineInjected(defaultIncognitoKeyword);
        }
    }

    private class ReadSearchEngineTask extends AsyncTask<Void, Void, Void> {
        private Uri mContentUri;
        private Cursor mCursor;
        private Context mContext;

        public ReadSearchEngineTask(Context ctx, Uri contentUri) {
            mContentUri = contentUri;
            mContext = ctx;
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {
                mCursor = mContext.getContentResolver().query( mContentUri,
                        SEARCH_ENGINE_PROJECTION, null, null, null);
                if (mCursor != null) {
                    mSearchEngines = new ArrayList<TemplateUrl>(mCursor.getCount());
                    for (int i = 0; i < mCursor.getCount(); i++) {
                        if (mCursor.moveToNext()) {
                            mSearchEngines.add(
                                    new TemplateUrl(
                                            mCursor.getInt(mCursor.getColumnIndexOrThrow(
                                                    SEARCH_ENGINE_COLUMN_ID)),
                                            mCursor.getString(mCursor.getColumnIndexOrThrow(
                                                    SEARCH_ENGINE_COLUMN_NAME)),
                                            mCursor.getString(mCursor.getColumnIndexOrThrow(
                                                    SEARCH_ENGINE_COLUMN_KEYWORD)),
                                            mCursor.getString(mCursor.getColumnIndexOrThrow(
                                                    SEARCH_ENGINE_COLUMN_SEARCH_URL)),
                                            mCursor.getString(mCursor.getColumnIndexOrThrow(
                                                    SEARCH_ENGINE_COLUMN_FAVICON_URL)),
                                            mCursor.getInt(mCursor.getColumnIndexOrThrow(
                                                    SEARCH_ENGINE_COLUMN_DEFAULT)),
                                            mCursor.getInt(mCursor.getColumnIndexOrThrow(
                                                    SEARCH_ENGINE_COLUMN_DEFAULT_INCOGNITO))
                                            ));
                        }
                    }
                }
            } catch (IllegalArgumentException e) {
                Logger.e(LOGTAG, "Dropping the search engine: " + e.getMessage());
                return null;
            }
            return null;
        }
    }
}

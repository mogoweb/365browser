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

package org.codeaurora.swe.partnerbrowsercustomizations;

import android.content.Context;
import android.database.MatrixCursor;

import org.chromium.chrome.browser.util.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.ArrayList;

public class SearchEngineParser {
    private static final String LOGTAG = "SearchEngineParser";
    private static final String JSON_PARTNER_SEARCH_ENGINE_VERSION_CODE = "version";
    private static final String JSON_PARTNER_SEARCH_ENGINE = "searchEngines";
    private static final String JSON_PARTNER_SEARCH_ENGINE_URL = "searchUrl";
    private static final String JSON_PARTNER_SEARCH_ENGINE_NAME = "name";
    private static final String JSON_PARTNER_SEARCH_ENGINE_KEYWORD = "keyword";
    private static final String JSON_PARTNER_SEARCH_ENGINE_FAVICON = "faviconUrl";
    private static final String JSON_PARTNER_SEARCH_ENGINE_DEFAULT = "default";
    private static final String JSON_PARTNER_SEARCH_ENGINE_DEFAULT_INCOGNITO = "default_incognito";


    private String mSearchJson;
    private ArrayList<TemplateUrl> mSearchEngines;
    private MatrixCursor mMatrixCursor;
    private String[] mSelectionArgs;
    private Context mContext;
    /* Example configuration for partner search engine json format*/
    /*
    {
        "version": "1.0",
        "searchEngines": [{
            "name": "Foo",
            "keyword": "foo.com",
            "searchUrl": "www.foo.com?q={searchTerms}",
            "faviconUrl": "www.foo.com/ico.png"
            }, {
            "name": "bar",
            "keyword": "bar.com",
            "searchUrl": "www.bar.com?q={searchTerms}",
            "faviconUrl": "www.bar.com/ico.png"
        }]
        "default" : 1 // index in array which should be the default. If not specified
                      // the first one is the default
    } */

    public SearchEngineParser(Context ctx, String json, String[] projection) {
        mSearchJson = json;
        mContext = ctx;
        mSelectionArgs = projection;
        mMatrixCursor = new MatrixCursor(projection);
    }

    public MatrixCursor parse() {
        try {
            JSONObject jsonResult = (JSONObject) new JSONTokener(mSearchJson).nextValue();
            String versionCode = jsonResult.getString(JSON_PARTNER_SEARCH_ENGINE_VERSION_CODE);
            parseSearchEngines(jsonResult);
        } catch (JSONException e) {
            Logger.e(LOGTAG, "parse Exception : " + e.toString());
            mMatrixCursor = null;
        }
        return mMatrixCursor;
    }

    void parseSearchEngines(JSONObject object) throws JSONException {
        JSONArray entries = object.getJSONArray(JSON_PARTNER_SEARCH_ENGINE);
        int def = object.has(JSON_PARTNER_SEARCH_ENGINE_DEFAULT) ?
                object.getInt(JSON_PARTNER_SEARCH_ENGINE_DEFAULT) : 0;
        int def_incognito = object.has(JSON_PARTNER_SEARCH_ENGINE_DEFAULT_INCOGNITO) ?
                object.getInt(JSON_PARTNER_SEARCH_ENGINE_DEFAULT_INCOGNITO) : 0;
        mSearchEngines = new ArrayList<TemplateUrl>(entries.length());
        for (int i = 0; i < entries.length(); i++) {
            mSearchEngines.add(parseEngine(i, entries.getJSONObject(i), def == i,
                    def_incognito == i));
        }
    }

    private TemplateUrl parseEngine(int i, JSONObject obj, boolean def, boolean def_incognito)
            throws JSONException {
        TemplateUrl url = new TemplateUrl(i, obj.getString(JSON_PARTNER_SEARCH_ENGINE_NAME),
                obj.getString(JSON_PARTNER_SEARCH_ENGINE_KEYWORD),
                obj.getString(JSON_PARTNER_SEARCH_ENGINE_URL),
                obj.getString(JSON_PARTNER_SEARCH_ENGINE_FAVICON), def ? 1 : 0,
                def_incognito ? 1 : 0);
        addToCursor(url);
        return url;
    }

    private void addToCursor(TemplateUrl url) {
        mMatrixCursor.addRow(new Object[]{
                url.getIndex(), url.getShortName(), url.getKeyword(),
                url.getSearchURL(), url.getFaviconUrl(), url.isDefault() ? 1 : 0,
                url.isDefaultIncognito() ? 1 : 0});
    }
}
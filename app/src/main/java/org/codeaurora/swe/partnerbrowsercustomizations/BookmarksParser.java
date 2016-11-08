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
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.util.Base64;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.util.Logger;
import org.chromium.components.url_formatter.UrlFormatter;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;

public class BookmarksParser {
    private static final String LOGTAG = "BookmarkParser";
    private static final String JSON_PARTNER_BOOKMARK_VERSION_CODE = "version";
    private static final String JSON_PARTNER_BOOKMARKS = "bookmarks";
    private static final String JSON_PARTNER_BOOKMARKS_URL = "url";
    private static long  id = Bookmark.ROOT_FOLDER_ID; // Start of Ids
    private static final String JSON_PARTNER_BOOKMARKS_TITLE = "title";
    private static final String JSON_PARTNER_BOOKMARKS_FAVICON = "favicon";
    private static final String JSON_PARTNER_BOOKMARKS_TOUCHICON = "touchicon";

    private String mBookmarkJson;
    private Bookmark mBookmark;
    private MatrixCursor mMatrixCursor;
    private String[] mSelectionArgs;
    private Context mContext;
    /* Example configuration for partner bookmarks json format*/
    /*
            {
                "version": "1.0",
                "title" : "Carrier-Bookmarks", // toplevel folder
                "bookmarks": [{ // toplevel bookmark
                    "title": "bookmark-top",
                    "url": "www.foo.com",
                    "favicon": "data:image\/png;base64,.....",
                    "touchicon": "data:image\/png;base64,....."
                 }, {
                    "title": "nested-folder", // top level folder
                    "bookmarks": [{ // bookmark in folder
                        "title": "bar",
                        "url": "www.bar.com",
                        "favicon": "data:image\/png;base64,.....",
                        "touchicon": "data:image\/png;base64,....."
                        }]
                }]
            }
    */

    public BookmarksParser(Context ctx, String json, String[] projection) {
        mBookmarkJson = json;
        mContext = ctx;
        mSelectionArgs = projection;
        mMatrixCursor = new MatrixCursor(projection);
    }

    private Bookmark createRootBookmarksFolderBookmark(String title) {
        Bookmark root = new Bookmark();
        root.mId = ++id;
        root.mTitle = title;
        root.mParentId = Bookmark.ROOT_FOLDER_ID;
        root.mIsFolder = true;
        return root;
    }

    public MatrixCursor parse() {
        try {
            JSONObject jsonResult = (JSONObject) new JSONTokener(mBookmarkJson).nextValue();
            String versionCode = jsonResult.getString(JSON_PARTNER_BOOKMARK_VERSION_CODE);
            parseBookmarkFolder(null, jsonResult);
        } catch (JSONException e) {
            Logger.e(LOGTAG, "parse Exception : " + e.toString());
            mBookmark = null;
            mMatrixCursor = null;
        }
        return mMatrixCursor;
    }

    private Bookmark parseBookmarkFolder(Bookmark parent, JSONObject obj) throws JSONException {
        Bookmark bookmark = null;
        // if root
        if (parent == null) {
            mBookmark = bookmark = createRootBookmarksFolderBookmark(
                obj.getString(JSON_PARTNER_BOOKMARKS_TITLE));
            addToCursor(mBookmark);
        } else {
            bookmark = new Bookmark();
            bookmark.setTitle(obj.getString(JSON_PARTNER_BOOKMARKS_TITLE));
            bookmark.setFolder(true);
            bookmark.setParent(parent);
            bookmark.setId(++id);
            bookmark.setParentId(parent.getId());
            addToCursor(bookmark);
        }
        JSONArray entries = obj.getJSONArray(JSON_PARTNER_BOOKMARKS);
        ArrayList<Bookmark> items = new ArrayList<Bookmark>(entries.length());
        for (int i = 0; i < entries.length(); i++) {
            JSONObject item = entries.getJSONObject(i);
            if (isFolder(item)) {
                items.add(i, parseBookmarkFolder(bookmark, item));
            } else {
                items.add(i, parseBookmark(bookmark, item));
            }
        }
        bookmark.setEntries(items);
        return bookmark;
    }

    private void addToCursor(Bookmark bk) {
        mMatrixCursor.addRow(new Object[]{
                bk.getId(), bk.getUrl(), bk.getTitle(), bk.isFolder() ? 2 : 0,
                bk.getParentId(), bk.getFavicon(), bk.getTouchicon()});
    }

    private Bookmark parseBookmark(Bookmark parent, JSONObject obj) throws JSONException {
        Bookmark bookmark = new Bookmark();
        bookmark.setUrl(UrlFormatter.fixupUrl(obj.getString(JSON_PARTNER_BOOKMARKS_URL)));
        bookmark.setTitle(obj.getString(JSON_PARTNER_BOOKMARKS_TITLE));
        bookmark.setFavicon(ConvertToByteArray(obj.getString(JSON_PARTNER_BOOKMARKS_FAVICON)));
        bookmark.setTouchicon(ConvertToByteArray(obj.getString(JSON_PARTNER_BOOKMARKS_TOUCHICON)));
        bookmark.setFolder(false);
        bookmark.setParent(parent);
        bookmark.setId(++id);
        bookmark.setParentId(parent.getId());
        addToCursor(bookmark);
        return bookmark;
    }

    private byte[] ConvertToByteArray(String data) {
        if (TextUtils.isEmpty(data) ||
                (data.indexOf("image/png") <= 0 && data.indexOf("base64") <= 0))
            return null;
        byte[] img_data = Base64.decode(data.substring(data.indexOf(",") + 1).getBytes(),
                Base64.DEFAULT);
        InputStream in = new ByteArrayInputStream(img_data);
        Bitmap bitmap = BitmapFactory.decodeStream(in);
        ByteArrayOutputStream baoStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baoStream);
        return baoStream.toByteArray();
    }

    private boolean isFolder(JSONObject obj) throws JSONException {
        return obj.has(JSON_PARTNER_BOOKMARKS_URL)  ? false : true;
    }

}

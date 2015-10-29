// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.bookmarkimport;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;

import org.chromium.chrome.browser.bookmark.BookmarkColumns;

import java.util.NoSuchElementException;

/**
 * Imports bookmarks from Android Browser using the public provider API.
 */
class AndroidBrowserProviderIterator implements BookmarkImporter.BookmarkIterator {
    private static final String SELECT_IS_BOOKMARK = BookmarkColumns.BOOKMARK + "=1";

    /**
     * A table containing both bookmarks and history items. The columns of the table are defined in
     * {@link BookmarkColumns}. Reading this table requires the
     * {@link android.Manifest.permission#READ_HISTORY_BOOKMARKS} permission and writing to it
     * requires the {@link android.Manifest.permission#WRITE_HISTORY_BOOKMARKS} permission.
     */
    public static final Uri BOOKMARKS_URI = Uri.parse("content://browser/bookmarks");

    private final Cursor mCursor;
    private long mNextId = BookmarkImporter.ROOT_FOLDER_ID + 1;

    static boolean isProviderAvailable(ContentResolver contentResolver) {
        ContentProviderClient providerClient = contentResolver.acquireContentProviderClient(
                BOOKMARKS_URI);
        if (providerClient != null) {
            providerClient.release();
            return true;
        }
        return false;
    }

    static AndroidBrowserProviderIterator createIfAvailable(
            ContentResolver contentResolver) {
        Cursor cursor = contentResolver.query(BOOKMARKS_URI, null, SELECT_IS_BOOKMARK,
                null, null);
        return cursor == null ? null : new AndroidBrowserProviderIterator(cursor);
    }

    private AndroidBrowserProviderIterator(Cursor cursor) {
        mCursor = cursor;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() {
        if (mCursor == null) throw new IllegalStateException();
        mCursor.close();
    }

    @Override
    public boolean hasNext() {
        if (mCursor == null) throw new IllegalStateException();
        return mCursor.getCount() > 0 && !mCursor.isLast() && !mCursor.isAfterLast();
    }

    @Override
    public BookmarkImporter.Bookmark next() {
        if (mCursor == null) throw new IllegalStateException();
        if (!mCursor.moveToNext()) throw new NoSuchElementException();

        BookmarkImporter.Bookmark bookmark = new BookmarkImporter.Bookmark();
        try {
            bookmark.url = mCursor.getString(mCursor.getColumnIndexOrThrow(BookmarkColumns.URL));
            bookmark.title =
                    mCursor.getString(mCursor.getColumnIndexOrThrow(BookmarkColumns.TITLE));
        } catch (IllegalArgumentException e) {
            return null;
        }

        if (bookmark.url == null || bookmark.title == null) {
            return null;
        }

        int index = mCursor.getColumnIndex(BookmarkColumns.CREATED);
        if (index != -1 && !mCursor.isNull(index)) bookmark.created = mCursor.getLong(index);

        index = mCursor.getColumnIndex(BookmarkColumns.DATE);
        if (index != -1 && !mCursor.isNull(index)) bookmark.lastVisit = mCursor.getLong(index);

        index = mCursor.getColumnIndex(BookmarkColumns.VISITS);
        if (index != -1 && !mCursor.isNull(index)) bookmark.visits = mCursor.getLong(index);

        index = mCursor.getColumnIndex(BookmarkColumns.FAVICON);
        if (index != -1 && !mCursor.isNull(index)) bookmark.favicon = mCursor.getBlob(index);

        // Add hierarchy information (flat).
        bookmark.id = mNextId++;
        bookmark.parentId = BookmarkImporter.ROOT_FOLDER_ID;
        bookmark.isFolder = false;

        return bookmark;
    }
}

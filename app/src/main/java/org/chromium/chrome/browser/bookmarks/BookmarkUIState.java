// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.bookmarks;

import android.net.Uri;
import android.text.TextUtils;

import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.components.bookmarks.BookmarkId;

/**
 * A class representing the UI state of the {@link BookmarkManager}. All
 * states can be uniquely identified by a URL.
 */
class BookmarkUIState {
    static final String URI_PERSIST_QUERY_NAME = "persist";

    static final int STATE_LOADING = 1;
    static final int STATE_ALL_BOOKMARKS = 2;
    static final int STATE_FOLDER = 3;

    /**
     * One of the STATE_* constants.
     */
    int mState;
    String mUrl;
    BookmarkId mFolder;

    static BookmarkUIState createLoadingState() {
        BookmarkUIState state = new BookmarkUIState();
        state.mState = STATE_LOADING;
        state.mUrl = "";
        return state;
    }

    /**
     * Depending on experiments we run, "all bookmarks" might be redirected to the default folder.
     */
    static BookmarkUIState createAllBookmarksState(BookmarkModel bookmarkModel) {
        if (!BookmarkUtils.isAllBookmarksViewEnabled()) {
            return createFolderState(bookmarkModel.getDefaultFolder(), null);
        }
        BookmarkUIState state = new BookmarkUIState();
        state.mState = STATE_ALL_BOOKMARKS;
        state.mUrl = UrlConstants.BOOKMARKS_URL;
        return state;
    }

    static BookmarkUIState createFolderState(BookmarkId folder, BookmarkModel bookmarkModel) {
        if (folder == null) return createAllBookmarksState(bookmarkModel);
        BookmarkUIState state = new BookmarkUIState();
        state.mState = STATE_FOLDER;
        state.mUrl = UrlConstants.BOOKMARKS_FOLDER_URL + folder.toString();
        state.mFolder = folder;
        return state;
    }

    /**
     * Parses the url and generates the corresponding state.
     */
    static BookmarkUIState createStateFromUrl(String url, BookmarkModel bookmarkModel) {
        if (url.equals(UrlConstants.BOOKMARKS_URL)) {
            return createAllBookmarksState(bookmarkModel);
        } else if (url.startsWith(UrlConstants.BOOKMARKS_FOLDER_URL)) {
            String path = url.substring(UrlConstants.BOOKMARKS_FOLDER_URL.length());
            if (!path.isEmpty()) {
                BookmarkId folder = BookmarkId.getBookmarkIdFromString(path);
                return createFolderState(folder, bookmarkModel);
            }
        }
        // If this line is reached, the url is not valid. Fall back to all bookmarks.
        return createAllBookmarksState(bookmarkModel);
    }

    @VisibleForTesting
    static Uri createFolderUrl(BookmarkId folderId) {
        Uri.Builder builder = Uri.parse(UrlConstants.BOOKMARKS_FOLDER_URL).buildUpon();
        builder.appendPath(folderId.toString());
        return builder.build();
    }

    private BookmarkUIState() {}

    @Override
    public int hashCode() {
        return 31 * mUrl.hashCode() + mState;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof BookmarkUIState)) return false;
        BookmarkUIState other = (BookmarkUIState) obj;
        return mState == other.mState && TextUtils.equals(mUrl, other.mUrl);
    }

    /**
     * @return Whether this state is valid
     */
    boolean isValid(BookmarkModel bookmarkModel) {
        if (mState == STATE_FOLDER) {
            return mFolder != null && bookmarkModel.doesBookmarkExist(mFolder)
                    && !mFolder.equals(bookmarkModel.getRootFolderId());
        }
        return mUrl != null;
    }
}

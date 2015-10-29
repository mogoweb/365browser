// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.enhancedbookmarks;

import org.chromium.base.ObserverList;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.BookmarksBridge;
import org.chromium.chrome.browser.offlinepages.OfflinePageBridge;
import org.chromium.chrome.browser.offlinepages.OfflinePageBridge.OfflinePageModelObserver;
import org.chromium.chrome.browser.offlinepages.OfflinePageBridge.SavePageCallback;
import org.chromium.chrome.browser.offlinepages.OfflinePageItem;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.components.bookmarks.BookmarkId;
import org.chromium.components.bookmarks.BookmarkType;
import org.chromium.components.dom_distiller.core.DomDistillerUrlUtils;
import org.chromium.components.offlinepages.SavePageResult;
import org.chromium.content_public.browser.WebContents;

import java.util.ArrayList;
import java.util.List;

/**
 * A class that encapsulates {@link BookmarksBridge} and provides extra features such as undo, large
 * icon fetching, reader mode url redirecting, etc. This class should serve as the single class for
 * the UI to acquire data from the backend.
 */
public class EnhancedBookmarksModel extends BookmarksBridge {
    private static final int FAVICON_MAX_CACHE_SIZE = 10 * 1024 * 1024; // 10MB

    /**
     * Callback for use with addBookmark.
     */
    public interface AddBookmarkCallback {
        /**
         * Called when the bookmark has been added.
         * @param bookmarkId ID of the bookmark that has been added.
         * @param whether an offline copy of the bookmarked page was successfully saved. This could
         *        be false due to, e.g. save not requested or storage full.
         */
        void onBookmarkAdded(BookmarkId bookmarkId, boolean pageSavedOffline);
    }

    /**
     * Observer that listens to delete event. This interface is used by undo controllers to know
     * which bookmarks were deleted. Note this observer only listens to events that go through
     * enhanced bookmark model.
     */
    public interface EnhancedBookmarkDeleteObserver {

        /**
         * Callback being triggered immediately before bookmarks are deleted.
         * @param titles All titles of the bookmarks to be deleted.
         * @param isUndoable Whether the deletion is undoable.
         */
        void onDeleteBookmarks(String[] titles, boolean isUndoable);
    }

    private ObserverList<EnhancedBookmarkDeleteObserver> mDeleteObservers = new ObserverList<>();
    private OfflinePageBridge mOfflinePageBridge;
    private boolean mIsOfflinePageModelLoaded;
    private OfflinePageModelObserver mOfflinePageModelObserver;

    /**
     * Initialize enhanced bookmark model for last used non-incognito profile.
     */
    public EnhancedBookmarksModel() {
        this(Profile.getLastUsedProfile().getOriginalProfile());
    }

    @VisibleForTesting
    public EnhancedBookmarksModel(Profile profile) {
        super(profile);

        // Note: we check if mOfflinePageBridge is null after this to determine if offline pages
        // feature is enabled. When it is enabled by default, we should check all the places
        // that checks for nullability of mOfflinePageBridge.
        if (OfflinePageBridge.isEnabled()) {
            mOfflinePageBridge = new OfflinePageBridge(profile);
            if (mOfflinePageBridge.isOfflinePageModelLoaded()) {
                mIsOfflinePageModelLoaded = true;
            } else {
                mOfflinePageModelObserver = new OfflinePageModelObserver() {
                    @Override
                    public void offlinePageModelLoaded() {
                        mIsOfflinePageModelLoaded = true;
                        if (isBookmarkModelLoaded()) {
                            notifyBookmarkModelLoaded();
                        }
                    }
                };
                mOfflinePageBridge.addObserver(mOfflinePageModelObserver);
            }
        }
    }

    /**
     * Clean up all the bridges. This must be called after done using this class.
     */
    @Override
    public void destroy() {
        if (mOfflinePageBridge != null) {
            mOfflinePageBridge.destroy();
            mOfflinePageBridge = null;
        }

        super.destroy();
    }

    @Override
    public boolean isBookmarkModelLoaded() {
        return super.isBookmarkModelLoaded()
                && (mOfflinePageBridge == null || mIsOfflinePageModelLoaded);
    }

    /**
     * Add an observer that listens to delete events that go through enhanced bookmark model.
     * @param observer The observer to add.
     */
    public void addDeleteObserver(EnhancedBookmarkDeleteObserver observer) {
        mDeleteObservers.addObserver(observer);
    }

    /**
     * Remove the observer from listening to bookmark deleting events.
     * @param observer The observer to remove.
     */
    public void removeDeleteObserver(EnhancedBookmarkDeleteObserver observer) {
        mDeleteObservers.removeObserver(observer);
    }

    /**
     * Delete one or multiple bookmarks from model. If more than one bookmarks are passed here, this
     * method will group these delete operations into one undo bundle so that later if the user
     * clicks undo, all bookmarks deleted here will be restored.
     * @param bookmarks Bookmarks to delete. Note this array should not contain a folder and its
     *                  children, because deleting folder will also remove all its children, and
     *                  deleting children once more will cause errors.
     */
    public void deleteBookmarks(BookmarkId... bookmarks) {
        assert bookmarks != null && bookmarks.length > 0;
        // Store all titles of bookmarks.
        String[] titles = new String[bookmarks.length];
        boolean isUndoable = true;
        for (int i = 0; i < bookmarks.length; i++) {
            titles[i] = getBookmarkTitle(bookmarks[i]);
            isUndoable &= (bookmarks[i].getType() == BookmarkType.NORMAL);
        }

        if (bookmarks.length == 1) {
            deleteBookmark(bookmarks[0]);
        } else {
            startGroupingUndos();
            for (BookmarkId bookmark : bookmarks) {
                deleteBookmark(bookmark);
            }
            endGroupingUndos();
        }

        for (EnhancedBookmarkDeleteObserver observer : mDeleteObservers) {
            observer.onDeleteBookmarks(titles, isUndoable);
        }
    }

    /**
     * Calls {@link BookmarksBridge#moveBookmark(BookmarkId, BookmarkId, int)} in a reversed
     * order of the list, in order to let the last item appear at the top.
     */
    public void moveBookmarks(List<BookmarkId> bookmarkIds, BookmarkId newParentId) {
        for (int i = bookmarkIds.size() - 1; i >= 0; i--) {
            moveBookmark(bookmarkIds.get(i), newParentId, 0);
        }
    }

    /**
     * Add a new bookmark asynchronously.
     *
     * @param parent Folder where to add.
     * @param index The position where the bookmark will be placed in parent folder
     * @param title Title of the new bookmark.
     * @param url Url of the new bookmark
     * @param webContents A {@link WebContents} object.
     * @param callback The callback to be invoked when the bookmark is added.
     */
    public void addBookmarkAsync(BookmarkId parent, int index, String title, String url,
                                 WebContents webContents, final AddBookmarkCallback callback) {
        url = DomDistillerUrlUtils.getOriginalUrlFromDistillerUrl(url);
        final BookmarkId enhancedId = addBookmark(parent, index, title, url);

        // If there is no need to save offline page, return now.
        if (mOfflinePageBridge == null) {
            callback.onBookmarkAdded(enhancedId, false);
            return;
        }

        mOfflinePageBridge.savePage(webContents, enhancedId,
                new SavePageCallback() {
                    @Override
                    public void onSavePageDone(int savePageResult, String url) {
                        callback.onBookmarkAdded(
                                enhancedId, savePageResult == SavePageResult.SUCCESS);
                    }
                });
    }

    /**
     * @see org.chromium.chrome.browser.BookmarksBridge.BookmarkItem#getTitle()
     */
    public String getBookmarkTitle(BookmarkId bookmarkId) {
        return getBookmarkById(bookmarkId).getTitle();
    }

    /**
     * Returns the url used to launch a bookmark.
     *
     * @param bookmarkId ID of the bookmark to launch.
     */
    public String getBookmarkLaunchUrl(BookmarkId bookmarkId) {
        String url = getBookmarkById(bookmarkId).getUrl();
        if (mOfflinePageBridge == null) {
            return url;
        }

        // Return the offline url for the offline page.
        OfflinePageItem page = mOfflinePageBridge.getPageByBookmarkId(bookmarkId);
        return page == null ? url : page.getOfflineUrl();
    }

    /**
     * @return The id of the default folder to add bookmarks/folders to.
     */
    public BookmarkId getDefaultFolder() {
        return getMobileFolderId();
    }

    /**
     * Gets a list of bookmark IDs of bookmarks that match a specified filter.
     *
     * @param filter Filter to be applied to the bookmarks.
     * @return A list of bookmark IDs of bookmarks matching the filter.
     */
    public List<BookmarkId> getBookmarkIDsByFilter(EnhancedBookmarkFilter filter) {
        assert filter == EnhancedBookmarkFilter.OFFLINE_PAGES;
        assert mOfflinePageBridge != null;

        List<BookmarkId> bookmarkIds = new ArrayList<BookmarkId>();
        for (OfflinePageItem offlinePage : mOfflinePageBridge.getAllPages()) {
            bookmarkIds.add(offlinePage.getBookmarkId());
        }
        return bookmarkIds;
    }

    /**
     * @return Offline page bridge.
     */
    public OfflinePageBridge getOfflinePageBridge() {
        return mOfflinePageBridge;
    }
}

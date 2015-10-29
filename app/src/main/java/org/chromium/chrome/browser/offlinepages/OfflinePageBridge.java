// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.offlinepages;

import android.os.Environment;

import org.chromium.base.ObserverList;
import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.chrome.browser.BookmarksBridge;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.components.bookmarks.BookmarkId;
import org.chromium.content_public.browser.WebContents;

import java.util.ArrayList;
import java.util.List;

/**
 * Access gate to C++ side offline pages functionalities.
 */
@JNINamespace("offline_pages::android")
public final class OfflinePageBridge {

    private static final long STORAGE_ALMOST_FULL_THRESHOLD_BYTES = 10L * (1 << 20);  // 10M

    private long mNativeOfflinePageBridge;
    private boolean mIsNativeOfflinePageModelLoaded;
    private final ObserverList<OfflinePageModelObserver> mObservers =
            new ObserverList<OfflinePageModelObserver>();

    /** Whether the offline pages feature is enabled. */
    private static Boolean sIsEnabled;

    /**
     * Callback used to saving an offline page.
     */
    public interface SavePageCallback {
        /**
         * Delivers result of saving a page.
         *
         * @param savePageResult Result of the saving. Uses
         *     {@see org.chromium.components.offlinepages.SavePageResult} enum.
         * @param url URL of the saved page.
         * @see OfflinePageBridge#savePage()
         */
        @CalledByNative("SavePageCallback")
        void onSavePageDone(int savePageResult, String url);
    }

    /**
     * Callback used to deleting an offline page.
     */
    public interface DeletePageCallback {
        /**
         * Delivers result of deleting a page.
         *
         * @param deletePageResult Result of deleting the page. Uses
         *     {@see org.chromium.components.offlinepages.DeletePageResult} enum.
         * @see OfflinePageBridge#deletePage()
         */
        @CalledByNative("DeletePageCallback")
        void onDeletePageDone(int deletePageResult);
    }

    /**
     * Interface that provides listeners to be notified of changes to the offline page model.
     */
    public interface OfflinePageModelObserver {
        /**
         * Called when the native side of offline pages is loaded and now in usable state.
         */
        void offlinePageModelLoaded();
    }

    /**
     * Creates offline pages bridge for a given profile.
     */
    @VisibleForTesting
    public OfflinePageBridge(Profile profile) {
        mNativeOfflinePageBridge = nativeInit(profile);
    }

    /**
     * Returns true if the offline pages feature is enabled.
     */
    public static boolean isEnabled() {
        ThreadUtils.assertOnUiThread();
        if (sIsEnabled == null) {
            // Enhanced bookmarks feature should also be enabled.
            sIsEnabled = nativeIsOfflinePagesEnabled()
                    && BookmarksBridge.isEnhancedBookmarksEnabled();
        }
        return sIsEnabled;
    }

    /**
     * Returns true if the stoarge is almost full which indicates that the user probably needs to
     * free up some space.
     */
    public static boolean isStorageAlmostFull() {
        return Environment.getExternalStorageDirectory().getUsableSpace()
                < STORAGE_ALMOST_FULL_THRESHOLD_BYTES;
    }

    /**
     * Destroys native offline pages bridge. It should be called during
     * destruction to ensure proper cleanup.
     */
    public void destroy() {
        assert mNativeOfflinePageBridge != 0;
        nativeDestroy(mNativeOfflinePageBridge);
        mIsNativeOfflinePageModelLoaded = false;
        mNativeOfflinePageBridge = 0;
    }

    /**
     * Adds an observer to offline page model changes.
     * @param observer The observer to be added.
     */
    @VisibleForTesting
    public void addObserver(OfflinePageModelObserver observer) {
        mObservers.addObserver(observer);
    }

    /**
     * Removes an observer to offline page model changes.
     * @param observer The observer to be removed.
     */
    @VisibleForTesting
    public void removeObserver(OfflinePageModelObserver observer) {
        mObservers.removeObserver(observer);
    }

    /**
     * @return Gets all available offline pages. Requires that the model is already loaded.
     */
    @VisibleForTesting
    public List<OfflinePageItem> getAllPages() {
        assert mIsNativeOfflinePageModelLoaded;
        List<OfflinePageItem> result = new ArrayList<OfflinePageItem>();
        nativeGetAllPages(mNativeOfflinePageBridge, result);
        return result;
    }

    /**
     * Gets an offline page associated with a provided bookmark ID.
     *
     * @param bookmarkId Id of the bookmark associated with an offline page.
     * @return An {@link OfflinePageItem} matching the bookmark Id or <code>null</code> if none
     * exist.
     */
    @VisibleForTesting
    public OfflinePageItem getPageByBookmarkId(BookmarkId bookmarkId) {
        return nativeGetPageByBookmarkId(mNativeOfflinePageBridge, bookmarkId.getId());
    }

    /**
     * Saves the web page loaded into web contents offline.
     *
     * @param webContents Contents of the page to save.
     * @param bookmarkId Id of the bookmark related to the offline page.
     * @param callback Interface that contains a callback.
     * @see SavePageCallback
     */
    @VisibleForTesting
    public void savePage(
            WebContents webContents, BookmarkId bookmarkId, SavePageCallback callback) {
        assert mIsNativeOfflinePageModelLoaded;
        nativeSavePage(mNativeOfflinePageBridge, callback, webContents, bookmarkId.getId());
    }

    /**
     * Deletes an offline page related to a specified bookmark.
     *
     * @param bookmarkId Bookmark ID for which the offline copy will be deleted.
     * @param callback Interface that contains a callback.
     * @see DeletePageCallback
     */
    @VisibleForTesting
    public void deletePage(BookmarkId bookmarkId, DeletePageCallback callback) {
        assert mIsNativeOfflinePageModelLoaded;
        nativeDeletePage(mNativeOfflinePageBridge, callback, bookmarkId.getId());
    }

    /**
     * Deletes offline pages based on the list of provided bookamrk IDs. Calls the callback
     * when operation is complete. Requires that the model is already loaded.
     *
     * @param bookmarkIds A list of bookmark IDs for which the offline pages will be deleted.
     * @param callback A callback that will be called once operation is completed.
     */
    public void deletePages(List<BookmarkId> bookmarkIds, DeletePageCallback callback) {
        assert mIsNativeOfflinePageModelLoaded;
        long[] ids = new long[bookmarkIds.size()];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = bookmarkIds.get(i).getId();
        }
        nativeDeletePages(mNativeOfflinePageBridge, callback, ids);
    }

    /**
     * Whether or not the underlying offline page model is loaded.
     */
    public boolean isOfflinePageModelLoaded() {
        return mIsNativeOfflinePageModelLoaded;
    }

    /**
     * @return Gets a list of pages that will be removed to clean up storage.  Requires that the
     *     model is already loaded.
     */
    public List<OfflinePageItem> getPagesToCleanUp() {
        assert mIsNativeOfflinePageModelLoaded;
        List<OfflinePageItem> result = new ArrayList<OfflinePageItem>();
        nativeGetPagesToCleanUp(mNativeOfflinePageBridge, result);
        return result;
    }

    @CalledByNative
    private void offlinePageModelLoaded() {
        mIsNativeOfflinePageModelLoaded = true;
        for (OfflinePageModelObserver observer : mObservers) {
            observer.offlinePageModelLoaded();
        }
    }

    @CalledByNative
    private static void createOfflinePageAndAddToList(List<OfflinePageItem> offlinePagesList,
            String url, long bookmarkId, String offlineUrl, long fileSize) {
        offlinePagesList.add(createOfflinePageItem(url, bookmarkId, offlineUrl, fileSize));
    }

    @CalledByNative
    private static OfflinePageItem createOfflinePageItem(
            String url, long bookmarkId, String offlineUrl, long fileSize) {
        return new OfflinePageItem(url, bookmarkId, offlineUrl, fileSize);
    }

    private static native boolean nativeIsOfflinePagesEnabled();

    private native long nativeInit(Profile profile);
    private native void nativeDestroy(long nativeOfflinePageBridge);
    private native void nativeGetAllPages(
            long nativeOfflinePageBridge, List<OfflinePageItem> offlinePages);
    private native OfflinePageItem nativeGetPageByBookmarkId(
            long nativeOfflinePageBridge, long bookmarkId);
    private native void nativeSavePage(long nativeOfflinePageBridge, SavePageCallback callback,
            WebContents webContents, long bookmarkId);
    private native void nativeDeletePage(long nativeOfflinePageBridge,
            DeletePageCallback callback, long bookmarkId);
    private native void nativeDeletePages(
            long nativeOfflinePageBridge, DeletePageCallback callback, long[] bookmarkIds);
    private native void nativeGetPagesToCleanUp(
            long nativeOfflinePageBridge, List<OfflinePageItem> offlinePages);
}

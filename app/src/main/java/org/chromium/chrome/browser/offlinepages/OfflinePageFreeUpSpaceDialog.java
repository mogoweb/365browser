// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.offlinepages;

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.text.format.Formatter;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.offlinepages.OfflinePageBridge.DeletePageCallback;
import org.chromium.components.bookmarks.BookmarkId;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows a "Free up space" dialog to clean up storage for offline pages.
 */
public class OfflinePageFreeUpSpaceDialog
        extends DialogFragment implements DialogInterface.OnClickListener {
    private OfflinePageBridge mOfflinePageBridge;
    private List<OfflinePageItem> mOfflinePagesToDelete;
    private OfflinePageFreeUpSpaceCallback mCallback;

     /**
     * Creates the dialog. If the passed bridge instance needs to be destroyed after the dialog
     * is finished, it should be taken care of in the callback implementation.
     *
     * @param offlinePageBridge An object to access offline page functionality.
     * @param callback An object that will be called when the dialog finishes. Can be null.
     * @see OfflinePageFreeUpSpaceCallback
     */
    public static OfflinePageFreeUpSpaceDialog newInstance(
            OfflinePageBridge offlinePageBridge, OfflinePageFreeUpSpaceCallback callback) {
        assert offlinePageBridge != null;
        OfflinePageFreeUpSpaceDialog dialog = new OfflinePageFreeUpSpaceDialog();
        dialog.mOfflinePageBridge = offlinePageBridge;
        dialog.mCallback = callback;
        return dialog;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        if (savedInstanceState != null) dismiss();

        mOfflinePagesToDelete = mOfflinePageBridge.getPagesToCleanUp();
        AlertDialog.Builder builder =
                new AlertDialog.Builder(getActivity(), R.style.AlertDialogTheme)
                        .setTitle(R.string.offline_pages_free_up_space_title)
                        .setPositiveButton(R.string.delete, this)
                        .setNegativeButton(R.string.cancel, this)
                        .setMessage(getString(R.string.offline_pages_free_up_space_message,
                                mOfflinePagesToDelete.size(),
                                Formatter.formatFileSize(getActivity(), getTotalSize())));
        return builder.create();
    }

    @Override
    public void onClick(DialogInterface dialog, int id) {
        if (id == AlertDialog.BUTTON_NEGATIVE) {
            dialog.cancel();
            if (mCallback != null) mCallback.onFreeUpSpaceCancelled();
            return;
        }

        mOfflinePageBridge.deletePages(getBookmarkIdsToDelete(), new DeletePageCallback() {
            @Override
            public void onDeletePageDone(int deletePageResult) {
                if (mCallback != null) mCallback.onFreeUpSpaceDone();
            }
        });
    }

    /** Returns a list of Bookmark IDs for which the offline pages will be deleted. */
    private List<BookmarkId> getBookmarkIdsToDelete() {
        List<BookmarkId> bookmarkIds = new ArrayList<BookmarkId>();
        for (OfflinePageItem offlinePage : mOfflinePagesToDelete) {
            bookmarkIds.add(offlinePage.getBookmarkId());
        }
        return bookmarkIds;
    }

    /** Returns a total size of offline pages that will be deleted. */
    private long getTotalSize() {
        long totalSize = 0;
        for (OfflinePageItem offlinePage : mOfflinePagesToDelete) {
            totalSize += offlinePage.getFileSize();
        }
        return totalSize;
    }
}

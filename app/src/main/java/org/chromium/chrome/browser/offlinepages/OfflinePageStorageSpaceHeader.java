// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.offlinepages;

import android.app.Activity;
import android.content.Context;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.chromium.chrome.R;

import java.util.List;

/**
 * Header shown in saved pages view to inform user of total used storage and offer freeing up
 * space.
 */
public class OfflinePageStorageSpaceHeader {
    /**
     * Minimal total size of all pages, before a header will be shown to offer freeing up space.
     */
    private static final long MINIMUM_TOTAL_SIZE_BYTES = 10 * (1 << 20); // 10MB
    /**
     * Minimal size of pages to clean up, before a header will be shown to offer freeing up space.
     */
    private static final long MINIMUM_CEALNUP_SIZE_BYTES = 5 * (1 << 20); // 5MB

    OfflinePageBridge mOfflinePageBridge;
    Context mContext;
    OfflinePageFreeUpSpaceCallback mCallback;

    /**
     * @param offlinePageBridge An object to access offline page functionality.
     */
    public OfflinePageStorageSpaceHeader(Context context, OfflinePageBridge offlinePageBridge,
            OfflinePageFreeUpSpaceCallback callback) {
        assert offlinePageBridge != null;
        mOfflinePageBridge = offlinePageBridge;
        mContext = context;
        mCallback = callback;
    }

    /** @return Whether the header should be shown. */
    public boolean shouldShow() {
        return getSizeOfAllPages() > MINIMUM_TOTAL_SIZE_BYTES
                && getSizeOfPagesToCleanUp() > MINIMUM_CEALNUP_SIZE_BYTES;
    }

    /** @return A view holder with the contents of the header. */
    public ViewHolder createHolder(ViewGroup parent) {
        // TODO(fgorski): Enable recalculation in case some pages were deleted.
        ViewGroup header = (ViewGroup) LayoutInflater.from(mContext).inflate(
                R.layout.eb_offline_pages_storage_space_header, parent, false);

        ((TextView) header.findViewById(R.id.storage_header_message))
                .setText(mContext.getString(R.string.offline_pages_storage_space_message,
                        Formatter.formatFileSize(mContext, getSizeOfAllPages())));

        header.findViewById(R.id.storage_header_button)
                .setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        OfflinePageFreeUpSpaceDialog dialog =
                                OfflinePageFreeUpSpaceDialog.newInstance(
                                        mOfflinePageBridge, mCallback);
                        dialog.show(((Activity) mContext).getFragmentManager(), null);
                    }
                });

        return new ViewHolder(header) {};
    }

    private long getSizeOfAllPages() {
        return getTotalSize(mOfflinePageBridge.getAllPages());
    }

    private long getSizeOfPagesToCleanUp() {
        return getTotalSize(mOfflinePageBridge.getPagesToCleanUp());
    }

    private long getTotalSize(List<OfflinePageItem> offlinePages) {
        long totalSize = 0;
        for (OfflinePageItem page : offlinePages) {
            totalSize += page.getFileSize();
        }
        return totalSize;
    }
}

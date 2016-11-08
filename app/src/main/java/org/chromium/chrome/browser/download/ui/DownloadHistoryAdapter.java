// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download.ui;

import android.content.ComponentName;
import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.chromium.base.metrics.RecordHistogram;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.download.DownloadItem;
import org.chromium.chrome.browser.download.ui.BackendProvider.DownloadDelegate;
import org.chromium.chrome.browser.download.ui.BackendProvider.OfflinePageDelegate;
import org.chromium.chrome.browser.download.ui.DownloadHistoryItemWrapper.DownloadItemWrapper;
import org.chromium.chrome.browser.download.ui.DownloadHistoryItemWrapper.OfflinePageItemWrapper;
import org.chromium.chrome.browser.download.ui.DownloadManagerUi.DownloadUiObserver;
import org.chromium.chrome.browser.offlinepages.downloads.OfflinePageDownloadBridge;
import org.chromium.chrome.browser.offlinepages.downloads.OfflinePageDownloadItem;
import org.chromium.chrome.browser.widget.DateDividedAdapter;
import org.chromium.chrome.browser.widget.selection.SelectionDelegate;
import org.chromium.components.url_formatter.UrlFormatter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Bridges the user's download history and the UI used to display it. */
public class DownloadHistoryAdapter extends DateDividedAdapter implements DownloadUiObserver {

    /** Holds onto a View that displays information about a downloaded file. */
    public static class ItemViewHolder extends RecyclerView.ViewHolder {
        public DownloadItemView mItemView;
        public ImageView mIconView;
        public TextView mFilenameView;
        public TextView mHostnameView;
        public TextView mFilesizeView;

        public ItemViewHolder(View itemView) {
            super(itemView);

            assert itemView instanceof DownloadItemView;
            mItemView = (DownloadItemView) itemView;

            mIconView = (ImageView) itemView.findViewById(R.id.icon_view);
            mFilenameView = (TextView) itemView.findViewById(R.id.filename_view);
            mHostnameView = (TextView) itemView.findViewById(R.id.hostname_view);
            mFilesizeView = (TextView) itemView.findViewById(R.id.filesize_view);
        }
    }

    /** See {@link #findItemIndex}. */
    private static final int INVALID_INDEX = -1;

    /**
     * Externally deleted items that have been removed from downloads history.
     * Shared across instances.
     */
    private static Map<String, Boolean> sExternallyDeletedItems = new HashMap<>();

    /**
     * Externally deleted off-the-record items that have been removed from downloads history.
     * Shared across instances.
     */
    private static Map<String, Boolean> sExternallyDeletedOffTheRecordItems = new HashMap<>();

    /**
     * The number of DownloadHistoryAdapater instances in existence that have been initialized.
     */
    private static final AtomicInteger sNumInstancesInitialized = new AtomicInteger();

    private final List<DownloadItemWrapper> mDownloadItems = new ArrayList<>();
    private final List<DownloadItemWrapper> mDownloadOffTheRecordItems = new ArrayList<>();
    private final List<OfflinePageItemWrapper> mOfflinePageItems = new ArrayList<>();
    private final List<DownloadHistoryItemWrapper> mFilteredItems = new ArrayList<>();
    private final ComponentName mParentComponent;
    private final boolean mShowOffTheRecord;
    private final LoadingStateDelegate mLoadingDelegate;

    private BackendProvider mBackendProvider;
    private OfflinePageDownloadBridge.Observer mOfflinePageObserver;
    private int mFilter = DownloadFilter.FILTER_ALL;

    DownloadHistoryAdapter(boolean showOffTheRecord, ComponentName parentComponent) {
        mShowOffTheRecord = showOffTheRecord;
        mParentComponent = parentComponent;
        setHasStableIds(true);
        mLoadingDelegate = new LoadingStateDelegate(mShowOffTheRecord);
    }

    public void initialize(BackendProvider provider) {
        mBackendProvider = provider;

        // Get all regular and (if necessary) off the record downloads.
        DownloadDelegate downloadManager = getDownloadDelegate();
        downloadManager.addDownloadHistoryAdapter(this);
        downloadManager.getAllDownloads(false);
        if (mShowOffTheRecord) downloadManager.getAllDownloads(true);

        initializeOfflinePageBridge();

        sNumInstancesInitialized.getAndIncrement();
    }

    /** Called when the user's download history has been gathered. */
    public void onAllDownloadsRetrieved(List<DownloadItem> result, boolean isOffTheRecord) {
        if (isOffTheRecord && !mShowOffTheRecord) return;

        mLoadingDelegate.updateLoadingState(
                isOffTheRecord ? LoadingStateDelegate.OFF_THE_RECORD_HISTORY_LOADED
                        : LoadingStateDelegate.DOWNLOAD_HISTORY_LOADED);

        List<DownloadItemWrapper> list = getDownloadItemList(isOffTheRecord);
        list.clear();
        int[] mItemCounts = new int[DownloadFilter.FILTER_BOUNDARY];

        for (DownloadItem item : result) {
            DownloadItemWrapper wrapper = createDownloadItemWrapper(item, isOffTheRecord);

            // TODO(twellington): The native downloads service should remove externally deleted
            //                    downloads rather than passing them to Java.
            if (getExternallyDeletedItemsMap(isOffTheRecord).containsKey(wrapper.getId())) {
                continue;
            } else if (wrapper.hasBeenExternallyRemoved()) {
                removeExternallyDeletedItem(wrapper, isOffTheRecord);
            } else {
                list.add(wrapper);
                mItemCounts[wrapper.getFilterType()]++;
            }
        }

        if (!isOffTheRecord) recordDownloadCountHistograms(mItemCounts, result.size());

        if (mLoadingDelegate.isLoaded()) filter(mLoadingDelegate.getPendingFilter());
    }

    /** Called when the user's offline page history has been gathered. */
    private void onAllOfflinePagesRetrieved(List<OfflinePageDownloadItem> result) {
        mLoadingDelegate.updateLoadingState(LoadingStateDelegate.OFFLINE_PAGE_LOADED);
        mOfflinePageItems.clear();
        for (OfflinePageDownloadItem item : result) {
            mOfflinePageItems.add(createOfflinePageItemWrapper(item));
        }

        if (mLoadingDelegate.isLoaded()) filter(mLoadingDelegate.getPendingFilter());

        RecordHistogram.recordCountHistogram("Android.DownloadManager.InitialCount.OfflinePage",
                result.size());
    }

    /** Returns the total size of all non-deleted downloaded items. */
    public long getTotalDownloadSize() {
        long totalSize = 0;
        for (DownloadHistoryItemWrapper wrapper : mDownloadItems) {
            assert wrapper instanceof DownloadItemWrapper;
            DownloadItemWrapper downloadWrapper = (DownloadItemWrapper) wrapper;
            totalSize += wrapper.getFileSize();
        }
        for (DownloadHistoryItemWrapper wrapper : mDownloadOffTheRecordItems) {
            assert wrapper instanceof DownloadItemWrapper;
            DownloadItemWrapper downloadWrapper = (DownloadItemWrapper) wrapper;
            totalSize += wrapper.getFileSize();
        }
        for (DownloadHistoryItemWrapper wrapper : mOfflinePageItems) {
            totalSize += wrapper.getFileSize();
        }
        return totalSize;
    }

    @Override
    protected int getTimedItemViewResId() {
        return R.layout.download_date_view;
    }

    @Override
    public ViewHolder createViewHolder(ViewGroup parent) {
        View v = LayoutInflater.from(parent.getContext()).inflate(
                R.layout.download_item_view, parent, false);
        ((DownloadItemView) v).setSelectionDelegate(getSelectionDelegate());
        return new ItemViewHolder(v);
    }

    @Override
    public void bindViewHolderForTimedItem(ViewHolder current, TimedItem timedItem) {
        final DownloadHistoryItemWrapper item = (DownloadHistoryItemWrapper) timedItem;

        ItemViewHolder holder = (ItemViewHolder) current;
        Context context = holder.mFilesizeView.getContext();
        holder.mFilenameView.setText(item.getDisplayFileName());
        holder.mHostnameView.setText(
                UrlFormatter.formatUrlForSecurityDisplay(item.getUrl(), false));
        holder.mFilesizeView.setText(
                Formatter.formatFileSize(context, item.getFileSize()));
        holder.mItemView.initialize(item);

        // Pick what icon to display for the item.
        int fileType = item.getFilterType();
        int iconResource = R.drawable.ic_drive_file_white_24dp;
        switch (fileType) {
            case DownloadFilter.FILTER_PAGE:
                iconResource = R.drawable.ic_drive_site_white_24dp;
                break;
            case DownloadFilter.FILTER_VIDEO:
                iconResource = R.drawable.ic_play_arrow_white_24dp;
                break;
            case DownloadFilter.FILTER_AUDIO:
                iconResource = R.drawable.ic_music_note_white_24dp;
                break;
            case DownloadFilter.FILTER_IMAGE:
                iconResource = R.drawable.ic_image_white_24dp;
                break;
            case DownloadFilter.FILTER_DOCUMENT:
                iconResource = R.drawable.ic_drive_text_white_24dp;
                break;
            default:
        }

        holder.mIconView.setImageResource(iconResource);
    }

    /**
     * Updates the list when new information about a download comes in.
     */
    public void onDownloadItemUpdated(DownloadItem item, boolean isOffTheRecord) {
        if (isOffTheRecord && !mShowOffTheRecord) return;

        List<DownloadItemWrapper> list = getDownloadItemList(isOffTheRecord);
        int index = findItemIndex(list, item.getId());

        DownloadItemWrapper wrapper = createDownloadItemWrapper(item, isOffTheRecord);

        // If an externally deleted item has already been removed from the history service, it
        // shouldn't be removed again.
        if (getExternallyDeletedItemsMap(isOffTheRecord).containsKey(wrapper.getId())) return;

        if (wrapper.hasBeenExternallyRemoved()) {
            removeExternallyDeletedItem(wrapper, isOffTheRecord);
            return;
        }

        if (index == INVALID_INDEX) {
            // Add a new entry.
            list.add(wrapper);
        } else {
            // Update the old one.
            list.set(index, wrapper);
        }

        filter(mFilter);
    }

    /**
     * Removes the DownloadItem with the given ID.
     * @param guid           ID of the DownloadItem that has been removed.
     * @param isOffTheRecord True if off the record, false otherwise.
     */
    public void onDownloadItemRemoved(String guid, boolean isOffTheRecord) {
        if (isOffTheRecord && !mShowOffTheRecord) return;
        if (removeItemFromList(getDownloadItemList(isOffTheRecord), guid)) filter(mFilter);
    }

    @Override
    public void onFilterChanged(int filter) {
        if (mLoadingDelegate.isLoaded()) {
            filter(filter);
        } else {
            // On tablets, this method might be called before anything is loaded. In this case,
            // cache the filter, and wait till the backends are loaded.
            mLoadingDelegate.setPendingFilter(filter);
        }
    }

    @Override
    public void onManagerDestroyed() {
        getDownloadDelegate().removeDownloadHistoryAdapter(this);
        getOfflinePageBridge().removeObserver(mOfflinePageObserver);

        // If there are no more instances, clear out externally deleted items maps so that they stop
        // taking up space.
        if (sNumInstancesInitialized.decrementAndGet() == 0) {
            sExternallyDeletedItems.clear();
            sExternallyDeletedOffTheRecordItems.clear();
        }
    }

    private DownloadDelegate getDownloadDelegate() {
        return mBackendProvider.getDownloadDelegate();
    }

    private OfflinePageDelegate getOfflinePageBridge() {
        return mBackendProvider.getOfflinePageBridge();
    }

    private SelectionDelegate<DownloadHistoryItemWrapper> getSelectionDelegate() {
        return mBackendProvider.getSelectionDelegate();
    }

    /** Filters the list of downloads to show only files of a specific type. */
    private void filter(int filterType) {
        mFilter = filterType;
        mFilteredItems.clear();
        if (filterType == DownloadFilter.FILTER_ALL) {
            mFilteredItems.addAll(mDownloadItems);
            mFilteredItems.addAll(mDownloadOffTheRecordItems);
            mFilteredItems.addAll(mOfflinePageItems);
        } else {
            for (DownloadHistoryItemWrapper item : mDownloadItems) {
                if (item.getFilterType() == filterType) mFilteredItems.add(item);
            }

            for (DownloadHistoryItemWrapper item : mDownloadOffTheRecordItems) {
                if (item.getFilterType() == filterType) mFilteredItems.add(item);
            }

            if (filterType == DownloadFilter.FILTER_PAGE) {
                for (DownloadHistoryItemWrapper item : mOfflinePageItems) mFilteredItems.add(item);
            }
        }

        loadItems(mFilteredItems);
    }

    private void initializeOfflinePageBridge() {
        mOfflinePageObserver = new OfflinePageDownloadBridge.Observer() {
            @Override
            public void onItemsLoaded() {
                onAllOfflinePagesRetrieved(getOfflinePageBridge().getAllItems());
            }

            @Override
            public void onItemAdded(OfflinePageDownloadItem item) {
                mOfflinePageItems.add(createOfflinePageItemWrapper(item));
                updateFilter();
            }

            @Override
            public void onItemDeleted(String guid) {
                if (removeItemFromList(mOfflinePageItems, guid)) updateFilter();
            }

            @Override
            public void onItemUpdated(OfflinePageDownloadItem item) {
                int index = findItemIndex(mOfflinePageItems, item.getGuid());
                if (index != INVALID_INDEX) {
                    mOfflinePageItems.set(index, createOfflinePageItemWrapper(item));
                    updateFilter();
                }
            }

            /** Re-filter the items if needed. */
            private void updateFilter() {
                if (mFilter == DownloadFilter.FILTER_ALL || mFilter == DownloadFilter.FILTER_PAGE) {
                    filter(mFilter);
                }
            }
        };
        getOfflinePageBridge().addObserver(mOfflinePageObserver);
    }

    private List<DownloadItemWrapper> getDownloadItemList(boolean isOffTheRecord) {
        return isOffTheRecord ? mDownloadOffTheRecordItems : mDownloadItems;
    }

    /**
     * Search for an existing entry for the {@link DownloadHistoryItemWrapper} with the given ID.
     * @param list List to search through.
     * @param guid GUID of the entry.
     * @return The index of the item, or INVALID_INDEX if it couldn't be found.
     */
    private <T extends DownloadHistoryItemWrapper> int findItemIndex(List<T> list, String guid) {
        for (int i = 0; i < list.size(); i++) {
            if (TextUtils.equals(list.get(i).getId(), guid)) return i;
        }
        return INVALID_INDEX;
    }

    /**
     * Removes the item matching the given |guid|.
     * @param list List of the users downloads of a specific type.
     * @param guid GUID of the download to remove.
     * @return True if something was removed, false otherwise.
     */
    private <T extends DownloadHistoryItemWrapper> boolean removeItemFromList(
            List<T> list, String guid) {
        int index = findItemIndex(list, guid);
        if (index != INVALID_INDEX) {
            T wrapper = list.remove(index);
            if (getSelectionDelegate().isItemSelected(wrapper)) {
                getSelectionDelegate().toggleSelectionForItem(wrapper);
            }
            return true;
        }
        return false;
    }

    private DownloadItemWrapper createDownloadItemWrapper(
            DownloadItem item, boolean isOffTheRecord) {
        return new DownloadItemWrapper(item, isOffTheRecord, mBackendProvider);
    }

    private OfflinePageItemWrapper createOfflinePageItemWrapper(OfflinePageDownloadItem item) {
        return new OfflinePageItemWrapper(item, mBackendProvider, mParentComponent);
    }

    private void recordDownloadCountHistograms(int[] itemCounts, int totalCount) {
        RecordHistogram.recordCountHistogram("Android.DownloadManager.InitialCount.Audio",
                itemCounts[DownloadFilter.FILTER_AUDIO]);
        RecordHistogram.recordCountHistogram("Android.DownloadManager.InitialCount.Document",
                itemCounts[DownloadFilter.FILTER_DOCUMENT]);
        RecordHistogram.recordCountHistogram("Android.DownloadManager.InitialCount.Image",
                itemCounts[DownloadFilter.FILTER_IMAGE]);
        RecordHistogram.recordCountHistogram("Android.DownloadManager.InitialCount.Other",
                itemCounts[DownloadFilter.FILTER_OTHER]);
        RecordHistogram.recordCountHistogram("Android.DownloadManager.InitialCount.Video",
                itemCounts[DownloadFilter.FILTER_VIDEO]);
        RecordHistogram.recordCountHistogram("Android.DownloadManager.InitialCount.Total",
                totalCount);
    }

    private void removeExternallyDeletedItem(DownloadItemWrapper wrapper, boolean isOffTheRecord) {
        getExternallyDeletedItemsMap(isOffTheRecord).put(wrapper.getId(), true);
        wrapper.remove();
        RecordUserAction.record("Android.DownloadManager.Item.ExternallyDeleted");
    }

    private Map<String, Boolean> getExternallyDeletedItemsMap(boolean isOffTheRecord) {
        return isOffTheRecord ? sExternallyDeletedOffTheRecordItems : sExternallyDeletedItems;
    }
    /**
     * Determines when the data from all of the backends has been loaded.
     * <p>
     * TODO(ianwen): add a timeout mechanism to either the DownloadLoadingDelegate or to the
     * backend so that if it takes forever to load one of the backend, users are still able to see
     * the other two.
     */
    private static class LoadingStateDelegate {
        public static final int DOWNLOAD_HISTORY_LOADED = 0b001;
        public static final int OFF_THE_RECORD_HISTORY_LOADED = 0b010;
        public static final int OFFLINE_PAGE_LOADED = 0b100;

        private static final int ALL_LOADED = 0b111;

        private int mState;
        private int mPendingFilter = DownloadFilter.FILTER_ALL;

        /**
         * @param offTheRecord Whether this delegate needs to consider incognito.
         */
        public LoadingStateDelegate(boolean offTheRecord) {
            // If we don't care about incognito, mark it as loaded.
            mState = offTheRecord ? 0 : OFF_THE_RECORD_HISTORY_LOADED;
        }

        /**
         * Tells this delegate one of the three backends has been loaded.
         */
        public void updateLoadingState(int flagToUpdate) {
            mState |= flagToUpdate;
        }

        /**
         * @return Whether all backends are loaded.
         */
        public boolean isLoaded() {
            return mState == ALL_LOADED;
        }

        /**
         * Caches a filter for when the backends have loaded.
         */
        public void setPendingFilter(int filter) {
            mPendingFilter = filter;
        }

        /**
         * @return The cached filter. If there are no such filter, fall back to
         *         {@link DownloadFilter#FILTER_ALL}.
         */
        public int getPendingFilter() {
            return mPendingFilter;
        }
    }
}

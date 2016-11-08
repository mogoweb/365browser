// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download.ui;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;

import org.chromium.base.ContextUtils;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.download.DownloadItem;
import org.chromium.chrome.browser.offlinepages.downloads.OfflinePageDownloadItem;
import org.chromium.chrome.browser.widget.DateDividedAdapter.TimedItem;
import org.chromium.ui.widget.Toast;

import java.io.File;
import java.util.Locale;

/** Wraps different classes that contain information about downloads. */
public abstract class DownloadHistoryItemWrapper implements TimedItem {
    private static final String TAG = "download_ui";

    protected final BackendProvider mBackendProvider;
    private Long mStableId;

    private DownloadHistoryItemWrapper(BackendProvider provider) {
        mBackendProvider = provider;
    }

    @Override
    public long getStableId() {
        if (mStableId == null) {
            // Generate a stable ID that combines the timestamp and the download ID.
            mStableId = (long) getId().hashCode();
            mStableId = (mStableId << 32) + (getTimestamp() & 0x0FFFFFFFF);
        }
        return mStableId;
    }

    /** @return Item that is being wrapped. */
    abstract Object getItem();

    /** @return ID representing the download. */
    abstract String getId();

    /** @return String showing where the download resides. */
    abstract String getFilePath();

    /** @return The file where the download resides. */
    abstract File getFile();

    /** @return String to display for the file. */
    abstract String getDisplayFileName();

    /** @return Size of the file. */
    abstract long getFileSize();

    /** @return URL the file was downloaded from. */
    abstract String getUrl();

    /** @return {@link DownloadFilter} that represents the file type. */
    abstract int getFilterType();

    /** @return The mime type or null if the item doesn't have one. */
    abstract String getMimeType();

    /** Called when the user wants to open the file. */
    abstract void open();

    /**
     * Called when the user wants to remove the download from the backend. May also delete the file
     * associated with the download item.
     * @return Whether the file associated with the download item was deleted.
     */
    abstract boolean remove();

    /**
     * @return Whether the file associated with this item has been removed through an external
     *         action.
     */
    abstract boolean hasBeenExternallyRemoved();

    protected void recordOpenSuccess() {
        RecordHistogram.recordEnumeratedHistogram("Android.DownloadManager.Item.OpenSucceeded",
                getFilterType(), DownloadFilter.FILTER_BOUNDARY);
    }

    protected void recordOpenFailure() {
        RecordHistogram.recordEnumeratedHistogram("Android.DownloadManager.Item.OpenFailed",
                getFilterType(), DownloadFilter.FILTER_BOUNDARY);
    }

    /** Wraps a {@link DownloadItem}. */
    static class DownloadItemWrapper extends DownloadHistoryItemWrapper {
        private static final String MIMETYPE_VIDEO = "video";
        private static final String MIMETYPE_AUDIO = "audio";
        private static final String MIMETYPE_IMAGE = "image";
        private static final String MIMETYPE_DOCUMENT = "text";

        private final DownloadItem mItem;
        private final boolean mIsOffTheRecord;
        private File mFile;

        DownloadItemWrapper(DownloadItem item, boolean isOffTheRecord, BackendProvider provider) {
            super(provider);
            mItem = item;
            mIsOffTheRecord = isOffTheRecord;
        }

        @Override
        public DownloadItem getItem() {
            return mItem;
        }

        @Override
        public String getId() {
            return mItem.getId();
        }

        @Override
        public long getTimestamp() {
            return mItem.getStartTime();
        }

        @Override
        public String getFilePath() {
            return mItem.getDownloadInfo().getFilePath();
        }

        @Override
        public File getFile() {
            if (mFile == null) mFile = new File(getFilePath());
            return mFile;
        }

        @Override
        public String getDisplayFileName() {
            return mItem.getDownloadInfo().getFileName();
        }

        @Override
        public long getFileSize() {
            return mItem.getDownloadInfo().getContentLength();
        }

        @Override
        public String getUrl() {
            return mItem.getDownloadInfo().getUrl();
        }

        @Override
        public int getFilterType() {
            return convertMimeTypeToFilterType(getMimeType());
        }

        @Override
        public String getMimeType() {
            return mItem.getDownloadInfo().getMimeType();
        }

        @Override
        public void open() {
            Context context = ContextUtils.getApplicationContext();

            if (mItem.hasBeenExternallyRemoved()) {
                Toast.makeText(context, context.getString(R.string.download_cant_open_file),
                        Toast.LENGTH_SHORT).show();
                return;
            }

            String mimeType = Intent.normalizeMimeType(mItem.getDownloadInfo().getMimeType());
            Uri fileUri = Uri.fromFile(getFile());

            // Check if any apps can open the file.
            Intent fileIntent = new Intent();
            fileIntent.setAction(Intent.ACTION_VIEW);
            if (TextUtils.isEmpty(mimeType)) {
                fileIntent.setData(fileUri);
            } else {
                fileIntent.setDataAndType(fileUri, mimeType);
            }
            fileIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            try {
                context.startActivity(fileIntent);
                recordOpenSuccess();
            } catch (ActivityNotFoundException e) {
                // Can't launch the Intent.
                Toast.makeText(context, context.getString(R.string.download_cant_open_file),
                        Toast.LENGTH_SHORT).show();
                recordOpenFailure();
            }
        }

        @Override
        public boolean remove() {
            // Tell the DownloadManager to remove the file from history.
            mBackendProvider.getDownloadDelegate().removeDownload(getId(), mIsOffTheRecord);
            return false;
        }

        @Override
        boolean hasBeenExternallyRemoved() {
            return mItem.hasBeenExternallyRemoved();
        }

        /** Identifies the type of file represented by the given MIME type string. */
        private static int convertMimeTypeToFilterType(String mimeType) {
            if (TextUtils.isEmpty(mimeType)) return DownloadFilter.FILTER_OTHER;

            String[] pieces = mimeType.toLowerCase(Locale.getDefault()).split("/");
            if (pieces.length != 2) return DownloadFilter.FILTER_OTHER;

            if (MIMETYPE_VIDEO.equals(pieces[0])) {
                return DownloadFilter.FILTER_VIDEO;
            } else if (MIMETYPE_AUDIO.equals(pieces[0])) {
                return DownloadFilter.FILTER_AUDIO;
            } else if (MIMETYPE_IMAGE.equals(pieces[0])) {
                return DownloadFilter.FILTER_IMAGE;
            } else if (MIMETYPE_DOCUMENT.equals(pieces[0])) {
                return DownloadFilter.FILTER_DOCUMENT;
            } else {
                return DownloadFilter.FILTER_OTHER;
            }
        }
    }

    /** Wraps a {@link OfflinePageDownloadItem}. */
    static class OfflinePageItemWrapper extends DownloadHistoryItemWrapper {
        private final OfflinePageDownloadItem mItem;
        private final ComponentName mComponent;
        private File mFile;

        OfflinePageItemWrapper(OfflinePageDownloadItem item, BackendProvider provider,
                ComponentName component) {
            super(provider);
            mItem = item;
            mComponent = component;
        }

        @Override
        public OfflinePageDownloadItem getItem() {
            return mItem;
        }

        @Override
        public String getId() {
            return mItem.getGuid();
        }

        @Override
        public long getTimestamp() {
            return mItem.getStartTimeMs();
        }

        @Override
        public String getFilePath() {
            return mItem.getTargetPath();
        }

        @Override
        public File getFile() {
            if (mFile == null) mFile = new File(getFilePath());
            return mFile;
        }

        @Override
        public String getDisplayFileName() {
            String title = mItem.getTitle();
            if (TextUtils.isEmpty(title)) {
                File path = new File(getFilePath());
                return path.getName();
            } else {
                return title;
            }
        }

        @Override
        public long getFileSize() {
            return mItem.getTotalBytes();
        }

        @Override
        public String getUrl() {
            return mItem.getUrl();
        }

        @Override
        public int getFilterType() {
            return DownloadFilter.FILTER_PAGE;
        }

        @Override
        public String getMimeType() {
            return "text/plain";
        }

        @Override
        public void open() {
            mBackendProvider.getOfflinePageBridge().openItem(getId(), mComponent);
            recordOpenSuccess();
        }

        @Override
        public boolean remove() {
            mBackendProvider.getOfflinePageBridge().deleteItem(getId());
            return true;
        }

        @Override
        boolean hasBeenExternallyRemoved() {
            // We don't currently detect when offline pages have been removed externally.
            return false;
        }
    }
}

// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download.ui;

import android.content.ComponentName;
import android.content.Context;
import android.text.TextUtils;

import org.chromium.base.ContextUtils;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.download.DownloadInfo;
import org.chromium.chrome.browser.download.DownloadItem;
import org.chromium.chrome.browser.download.DownloadNotificationService;
import org.chromium.chrome.browser.download.DownloadUtils;
import org.chromium.chrome.browser.offlinepages.downloads.OfflinePageDownloadItem;
import org.chromium.chrome.browser.widget.DateDividedAdapter.TimedItem;
import org.chromium.components.offline_items_collection.OfflineItem.Progress;
import org.chromium.components.offline_items_collection.OfflineItemProgressUnit;
import org.chromium.components.url_formatter.UrlFormatter;
import org.chromium.content_public.browser.DownloadState;
import org.chromium.ui.widget.Toast;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Wraps different classes that contain information about downloads. */
public abstract class DownloadHistoryItemWrapper extends TimedItem {
    public static final Integer FILE_EXTENSION_OTHER = 0;
    public static final Integer FILE_EXTENSION_APK = 1;
    public static final Integer FILE_EXTENSION_CSV = 2;
    public static final Integer FILE_EXTENSION_DOC = 3;
    public static final Integer FILE_EXTENSION_DOCX = 4;
    public static final Integer FILE_EXTENSION_EXE = 5;
    public static final Integer FILE_EXTENSION_PDF = 6;
    public static final Integer FILE_EXTENSION_PPT = 7;
    public static final Integer FILE_EXTENSION_PPTX = 8;
    public static final Integer FILE_EXTENSION_PSD = 9;
    public static final Integer FILE_EXTENSION_RTF = 10;
    public static final Integer FILE_EXTENSION_TXT = 11;
    public static final Integer FILE_EXTENSION_XLS = 12;
    public static final Integer FILE_EXTENSION_XLSX = 13;
    public static final Integer FILE_EXTENSION_ZIP = 14;
    public static final Integer FILE_EXTENSION_BOUNDARY = 15;

    private static final Map<String, Integer> EXTENSIONS_MAP;
    static {
        Map<String, Integer> extensions = new HashMap<>();
        extensions.put("apk", FILE_EXTENSION_APK);
        extensions.put("csv", FILE_EXTENSION_CSV);
        extensions.put("doc", FILE_EXTENSION_DOC);
        extensions.put("docx", FILE_EXTENSION_DOCX);
        extensions.put("exe", FILE_EXTENSION_EXE);
        extensions.put("pdf", FILE_EXTENSION_PDF);
        extensions.put("ppt", FILE_EXTENSION_PPT);
        extensions.put("pptx", FILE_EXTENSION_PPTX);
        extensions.put("psd", FILE_EXTENSION_PSD);
        extensions.put("rtf", FILE_EXTENSION_RTF);
        extensions.put("txt", FILE_EXTENSION_TXT);
        extensions.put("xls", FILE_EXTENSION_XLS);
        extensions.put("xlsx", FILE_EXTENSION_XLSX);
        extensions.put("zip", FILE_EXTENSION_ZIP);

        EXTENSIONS_MAP = Collections.unmodifiableMap(extensions);
    }

    protected final BackendProvider mBackendProvider;
    protected final ComponentName mComponentName;
    protected File mFile;
    private Long mStableId;
    private boolean mIsDeletionPending;

    private DownloadHistoryItemWrapper(BackendProvider provider, ComponentName component) {
        mBackendProvider = provider;
        mComponentName = component;
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

    /** @return Whether the file will soon be deleted. */
    final boolean isDeletionPending() {
        return mIsDeletionPending;
    }

    /** Track whether or not the file will soon be deleted. */
    final void setIsDeletionPending(boolean state) {
        mIsDeletionPending = state;
    }

    /** @return Whether this download should be shown to the user. */
    boolean isVisibleToUser(int filter) {
        if (isDeletionPending()) return false;
        return filter == getFilterType() || filter == DownloadFilter.FILTER_ALL;
    }

    /** @return Item that is being wrapped. */
    abstract Object getItem();

    /**
     * Replaces the item being wrapped with a new one.
     * @return Whether or not the user needs to be informed of changes to the data.
     */
    abstract boolean replaceItem(Object item);

    /** @return ID representing the download. */
    abstract String getId();

    /** @return String showing where the download resides. */
    abstract String getFilePath();

    /** @return The file where the download resides. */
    public final File getFile() {
        if (mFile == null) mFile = new File(getFilePath());
        return mFile;
    }

    /** @return String to display for the hostname. */
    public final String getDisplayHostname() {
        return UrlFormatter.formatUrlForSecurityDisplay(getUrl(), false);
    }

    /** @return String to display for the file. */
    abstract String getDisplayFileName();

    /** @return Size of the file. */
    abstract long getFileSize();

    /** @return URL the file was downloaded from. */
    public abstract String getUrl();

    /** @return {@link DownloadFilter} that represents the file type. */
    public abstract int getFilterType();

    /** @return The mime type or null if the item doesn't have one. */
    public abstract String getMimeType();

    /** @return The file extension type. See list at the top of the file. */
    public abstract int getFileExtensionType();

    /** @return How much of the download has completed, or null if there is no progress. */
    abstract Progress getDownloadProgress();

    /** @return String indicating the status of the download. */
    abstract String getStatusString();

    /** @return Whether the file for this item has been removed through an external action. */
    abstract boolean hasBeenExternallyRemoved();

    /** @return Whether this download is associated with the off the record profile. */
    abstract boolean isOffTheRecord();

    /** @return Whether the item has been completely downloaded. */
    abstract boolean isComplete();

    /** @return Whether the download is currently paused. */
    abstract boolean isPaused();

    /** Called when the user wants to open the file. */
    abstract void open();

    /** Called when the user tries to cancel downloading the file. */
    abstract void cancel();

    /** Called when the user tries to pause downloading the file. */
    abstract void pause();

    /** Called when the user tries to resume downloading the file. */
    abstract void resume();

    /**
     * Called when the user wants to remove the download from the backend.
     * May also delete the file associated with the download item.
     *
     * @return Whether the file associated with the download item was deleted.
     */
    abstract boolean remove();

    protected void recordOpenSuccess() {
        RecordUserAction.record("Android.DownloadManager.Item.OpenSucceeded");
        RecordHistogram.recordEnumeratedHistogram("Android.DownloadManager.Item.OpenSucceeded",
                getFilterType(), DownloadFilter.FILTER_BOUNDARY);

        if (getFilterType() == DownloadFilter.FILTER_OTHER) {
            RecordHistogram.recordEnumeratedHistogram(
                    "Android.DownloadManager.OtherExtensions.OpenSucceeded",
                    getFileExtensionType(), FILE_EXTENSION_BOUNDARY);
        }
    }

    protected void recordOpenFailure() {
        RecordHistogram.recordEnumeratedHistogram("Android.DownloadManager.Item.OpenFailed",
                getFilterType(), DownloadFilter.FILTER_BOUNDARY);

        if (getFilterType() == DownloadFilter.FILTER_OTHER) {
            RecordHistogram.recordEnumeratedHistogram(
                    "Android.DownloadManager.OtherExtensions.OpenFailed",
                    getFileExtensionType(), FILE_EXTENSION_BOUNDARY);
        }
    }

    /** Wraps a {@link DownloadItem}. */
    public static class DownloadItemWrapper extends DownloadHistoryItemWrapper {
        private DownloadItem mItem;
        private Integer mFileExtensionType;

        DownloadItemWrapper(DownloadItem item, BackendProvider provider, ComponentName component) {
            super(provider, component);
            mItem = item;
        }

        @Override
        public DownloadItem getItem() {
            return mItem;
        }

        @Override
        public boolean replaceItem(Object item) {
            assert item instanceof DownloadItem;
            DownloadItem downloadItem = (DownloadItem) item;
            assert TextUtils.equals(mItem.getId(), downloadItem.getId());

            boolean visuallyChanged = isNewItemVisiblyDifferent(downloadItem);
            mItem = downloadItem;
            mFile = null;
            return visuallyChanged;
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
        public String getDisplayFileName() {
            return mItem.getDownloadInfo().getFileName();
        }

        @Override
        public long getFileSize() {
            if (mItem.getDownloadInfo().state() == DownloadState.COMPLETE) {
                return mItem.getDownloadInfo().getBytesReceived();
            } else {
                return 0;
            }
        }

        @Override
        public String getUrl() {
            return mItem.getDownloadInfo().getUrl();
        }

        @Override
        public int getFilterType() {
            return DownloadFilter.fromMimeType(getMimeType());
        }

        @Override
        public String getMimeType() {
            return mItem.getDownloadInfo().getMimeType();
        }

        @Override
        public int getFileExtensionType() {
            if (mFileExtensionType == null) {
                int extensionIndex = getFilePath().lastIndexOf(".");
                if (extensionIndex == -1 || extensionIndex == getFilePath().length() - 1) {
                    mFileExtensionType = FILE_EXTENSION_OTHER;
                    return mFileExtensionType;
                }

                String extension = getFilePath().substring(extensionIndex + 1);
                if (!TextUtils.isEmpty(extension) && EXTENSIONS_MAP.containsKey(
                        extension.toLowerCase(Locale.getDefault()))) {
                    mFileExtensionType = EXTENSIONS_MAP.get(
                            extension.toLowerCase(Locale.getDefault()));
                } else {
                    mFileExtensionType = FILE_EXTENSION_OTHER;
                }
            }

            return mFileExtensionType;
        }

        @Override
        public Progress getDownloadProgress() {
            return mItem.getDownloadInfo().getProgress();
        }

        @Override
        public String getStatusString() {
            return DownloadUtils.getStatusString(mItem);
        }

        @Override
        public void open() {
            Context context = ContextUtils.getApplicationContext();

            if (mItem.hasBeenExternallyRemoved()) {
                Toast.makeText(context, context.getString(R.string.download_cant_open_file),
                        Toast.LENGTH_SHORT).show();
                return;
            }

            if (DownloadUtils.openFile(getFile(), getMimeType(),
                        mItem.getDownloadInfo().getDownloadGuid(), isOffTheRecord())) {
                recordOpenSuccess();
            } else {
                recordOpenFailure();
            }
        }

        @Override
        public void cancel() {
            mBackendProvider.getDownloadDelegate().broadcastDownloadAction(
                    mItem, DownloadNotificationService.ACTION_DOWNLOAD_CANCEL);
        }

        @Override
        public void pause() {
            mBackendProvider.getDownloadDelegate().broadcastDownloadAction(
                    mItem, DownloadNotificationService.ACTION_DOWNLOAD_PAUSE);
        }

        @Override
        public void resume() {
            mBackendProvider.getDownloadDelegate().broadcastDownloadAction(
                    mItem, DownloadNotificationService.ACTION_DOWNLOAD_RESUME);
        }

        @Override
        public boolean remove() {
            // Tell the DownloadManager to remove the file from history.
            mBackendProvider.getDownloadDelegate().removeDownload(getId(), isOffTheRecord());
            return false;
        }

        @Override
        boolean hasBeenExternallyRemoved() {
            return mItem.hasBeenExternallyRemoved();
        }

        @Override
        boolean isOffTheRecord() {
            return mItem.getDownloadInfo().isOffTheRecord();
        }

        @Override
        public boolean isComplete() {
            return mItem.getDownloadInfo().state() == DownloadState.COMPLETE;
        }

        @Override
        public boolean isPaused() {
            return DownloadUtils.isDownloadPaused(mItem);
        }

        @Override
        boolean isVisibleToUser(int filter) {
            if (!super.isVisibleToUser(filter)) return false;

            if (TextUtils.isEmpty(getFilePath()) || TextUtils.isEmpty(getDisplayFileName())) {
                return false;
            }

            int state = mItem.getDownloadInfo().state();
            if ((state == DownloadState.INTERRUPTED && !mItem.getDownloadInfo().isResumable())
                    || state == DownloadState.CANCELLED) {
                // Mocks don't include showing cancelled/unresumable downloads.  Might need to if
                // undeletable files become a big issue.
                return false;
            }

            return true;
        }

        /** @return whether the given DownloadItem is visibly different from the current one. */
        private boolean isNewItemVisiblyDifferent(DownloadItem newItem) {
            DownloadInfo oldInfo = mItem.getDownloadInfo();
            DownloadInfo newInfo = newItem.getDownloadInfo();

            if (oldInfo.getProgress().equals(newInfo.getProgress())) return true;
            if (oldInfo.getBytesReceived() != newInfo.getBytesReceived()) return true;
            if (oldInfo.state() != newInfo.state()) return true;
            if (oldInfo.isPaused() != newInfo.isPaused()) return true;
            if (!TextUtils.equals(oldInfo.getFilePath(), newInfo.getFilePath())) return true;

            return false;
        }
    }

    /** Wraps a {@link OfflinePageDownloadItem}. */
    public static class OfflinePageItemWrapper extends DownloadHistoryItemWrapper {
        private OfflinePageDownloadItem mItem;

        OfflinePageItemWrapper(OfflinePageDownloadItem item, BackendProvider provider,
                ComponentName component) {
            super(provider, component);
            mItem = item;
        }

        @Override
        public OfflinePageDownloadItem getItem() {
            return mItem;
        }

        @Override
        public boolean replaceItem(Object item) {
            assert item instanceof OfflinePageDownloadItem;
            OfflinePageDownloadItem newItem = (OfflinePageDownloadItem) item;
            assert TextUtils.equals(newItem.getGuid(), mItem.getGuid());

            mItem = newItem;
            mFile = null;
            return true;
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
        public String getDisplayFileName() {
            String title = mItem.getTitle();
            if (TextUtils.isEmpty(title)) {
                return getDisplayHostname();
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
            return "text/html";
        }

        @Override
        public int getFileExtensionType() {
            return FILE_EXTENSION_OTHER;
        }

        @Override
        public Progress getDownloadProgress() {
            // Only completed offline page downloads are shown.
            return isComplete() ? new Progress(100, 100L, OfflineItemProgressUnit.PERCENTAGE)
                    : Progress.createIndeterminateProgress();
        }

        @Override
        public String getStatusString() {
            Context context = ContextUtils.getApplicationContext();

            int state = mItem.getDownloadState();

            if (state == org.chromium.components.offlinepages.downloads.DownloadState.COMPLETE) {
                return context.getString(R.string.download_notification_completed);
            }

            if (state == org.chromium.components.offlinepages.downloads.DownloadState.PENDING) {
                return context.getString(R.string.download_notification_pending);
            }

            if (state == org.chromium.components.offlinepages.downloads.DownloadState.PAUSED) {
                return context.getString(R.string.download_notification_paused);
            }

            long bytesReceived = mItem.getDownloadProgressBytes();
            if (bytesReceived == 0) {
                return context.getString(R.string.download_started);
            } else {
                return DownloadUtils.getStringForDownloadedBytes(context, bytesReceived);
            }
        }

        @Override
        public void open() {
            mBackendProvider.getOfflinePageBridge().openItem(getId(), mComponentName);
            recordOpenSuccess();
        }

        @Override
        public void cancel() {
            mBackendProvider.getOfflinePageBridge().cancelDownload(getId());
        }

        @Override
        public void pause() {
            mBackendProvider.getOfflinePageBridge().pauseDownload(getId());
        }

        @Override
        public void resume() {
            mBackendProvider.getOfflinePageBridge().resumeDownload(getId());
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

        @Override
        boolean isOffTheRecord() {
            return false;
        }

        /** @return Whether this page is to be shown in the suggested reading section. */
        public boolean isSuggested() {
            return mItem.isSuggested();
        }

        @Override
        public boolean isComplete() {
            return mItem.getDownloadState()
                    == org.chromium.components.offlinepages.downloads.DownloadState.COMPLETE;
        }

        @Override
        public boolean isPaused() {
            return mItem.getDownloadState()
                    == org.chromium.components.offlinepages.downloads.DownloadState.PAUSED;
        }
    }
}

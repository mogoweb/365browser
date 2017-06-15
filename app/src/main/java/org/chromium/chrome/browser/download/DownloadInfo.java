// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download;

import android.graphics.Bitmap;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.components.offline_items_collection.ContentId;
import org.chromium.components.offline_items_collection.LegacyHelpers;
import org.chromium.components.offline_items_collection.OfflineItem;
import org.chromium.components.offline_items_collection.OfflineItem.Progress;
import org.chromium.components.offline_items_collection.OfflineItemProgressUnit;
import org.chromium.components.offline_items_collection.OfflineItemState;
import org.chromium.components.offline_items_collection.OfflineItemVisuals;
import org.chromium.content_public.browser.DownloadState;

/**
 * Class representing the state of a single download.
 */
public final class DownloadInfo {
    private final String mUrl;
    private final String mUserAgent;
    private final String mMimeType;
    private final String mCookie;
    private final String mFileName;
    private final String mDescription;
    private final String mFilePath;
    private final String mReferrer;
    private final String mOriginalUrl;
    private final long mBytesReceived;
    private final String mDownloadGuid;
    private final boolean mHasUserGesture;
    private final String mContentDisposition;
    private final boolean mIsGETRequest;
    private final Progress mProgress;
    private final long mTimeRemainingInMillis;
    private final boolean mIsResumable;
    private final boolean mIsPaused;
    private final boolean mIsOffTheRecord;
    private final boolean mIsOfflinePage;
    private final int mState;
    private final long mLastAccessTime;

    // New variables to assist with the migration to OfflineItems.
    private final ContentId mContentId;
    private final boolean mIsOpenable;
    private final boolean mIsTransient;
    private final Bitmap mIcon;

    private DownloadInfo(Builder builder) {
        mUrl = builder.mUrl;
        mUserAgent = builder.mUserAgent;
        mMimeType = builder.mMimeType;
        mCookie = builder.mCookie;
        mFileName = builder.mFileName;
        mDescription = builder.mDescription;
        mFilePath = builder.mFilePath;
        mReferrer = builder.mReferrer;
        mOriginalUrl = builder.mOriginalUrl;
        mBytesReceived = builder.mBytesReceived;
        mDownloadGuid = builder.mDownloadGuid;
        mHasUserGesture = builder.mHasUserGesture;
        mIsGETRequest = builder.mIsGETRequest;
        mContentDisposition = builder.mContentDisposition;
        mProgress = builder.mProgress;
        mTimeRemainingInMillis = builder.mTimeRemainingInMillis;
        mIsResumable = builder.mIsResumable;
        mIsPaused = builder.mIsPaused;
        mIsOffTheRecord = builder.mIsOffTheRecord;
        mIsOfflinePage = builder.mIsOfflinePage;
        mState = builder.mState;
        mLastAccessTime = builder.mLastAccessTime;

        if (builder.mContentId != null) {
            mContentId = builder.mContentId;
        } else {
            mContentId = LegacyHelpers.buildLegacyContentId(mIsOfflinePage, mDownloadGuid);
        }
        mIsOpenable = builder.mIsOpenable;
        mIsTransient = builder.mIsTransient;
        mIcon = builder.mIcon;
    }

    public String getUrl() {
        return mUrl;
    }

    public String getUserAgent() {
        return mUserAgent;
    }

    public String getMimeType() {
        return mMimeType;
    }

    public String getCookie() {
        return mCookie;
    }

    public String getFileName() {
        return mFileName;
    }

    public String getDescription() {
        return mDescription;
    }

    public String getFilePath() {
        return mFilePath;
    }

    public String getReferrer() {
        return mReferrer;
    }

    public String getOriginalUrl() {
        return mOriginalUrl;
    }

    public long getBytesReceived() {
        return mBytesReceived;
    }

    public boolean isGETRequest() {
        return mIsGETRequest;
    }

    public String getDownloadGuid() {
        return mDownloadGuid;
    }

    public boolean hasUserGesture() {
        return mHasUserGesture;
    }

    public String getContentDisposition() {
        return mContentDisposition;
    }

    public Progress getProgress() {
        return mProgress;
    }

    /**
     * @return Remaining download time in milliseconds or -1 if it is unknown.
     */
    public long getTimeRemainingInMillis() {
        return mTimeRemainingInMillis;
    }

    public boolean isResumable() {
        return mIsResumable;
    }

    public boolean isPaused() {
        return mIsPaused;
    }

    public boolean isOffTheRecord() {
        return mIsOffTheRecord;
    }

    public boolean isOfflinePage() {
        return mIsOfflinePage;
    }

    public int state() {
        return mState;
    }

    public long getLastAccessTime() {
        return mLastAccessTime;
    }

    public ContentId getContentId() {
        return mContentId;
    }

    public boolean getIsOpenable() {
        return mIsOpenable;
    }

    public boolean getIsTransient() {
        return mIsTransient;
    }

    public Bitmap getIcon() {
        return mIcon;
    }

    /**
     * Helper method to build a {@link DownloadInfo} from an {@link OfflineItem}.
     * @param item The {@link OfflineItem} to mimic.
     * @return     A {@link DownloadInfo} containing the relevant fields from {@code item}.
     */
    public static DownloadInfo fromOfflineItem(OfflineItem item, OfflineItemVisuals visuals) {
        int state;
        switch (item.state) {
            case OfflineItemState.COMPLETE:
                state = DownloadState.COMPLETE;
                break;
            case OfflineItemState.CANCELLED:
                state = DownloadState.CANCELLED;
                break;
            case OfflineItemState.INTERRUPTED:
                state = DownloadState.INTERRUPTED;
                break;
            case OfflineItemState.FAILED:
                state = DownloadState.INTERRUPTED; // TODO(dtrainor): Validate what this state is.
                break;
            case OfflineItemState.PENDING: // TODO(dtrainor): Validate what this state is.
            case OfflineItemState.IN_PROGRESS:
            case OfflineItemState.PAUSED: // TODO(dtrainor): Validate what this state is.
            default:
                state = DownloadState.IN_PROGRESS;
                break;
        }

        return new DownloadInfo.Builder()
                .setContentId(item.id)
                .setFileName(item.title)
                .setDescription(item.description)
                .setIsTransient(item.isTransient)
                .setLastAccessTime(item.lastAccessedTimeMs)
                .setIsOpenable(item.isOpenable)
                .setOriginalUrl(item.originalUrl)
                .setIsOffTheRecord(item.isOffTheRecord)
                .setState(state)
                .setIsPaused(item.state == OfflineItemState.PAUSED)
                .setIsResumable(item.isResumable)
                .setBytesReceived(item.receivedBytes)
                .setProgress(item.progress)
                .setTimeRemainingInMillis(item.timeRemainingMs)
                .setIcon(visuals == null ? null : visuals.icon)
                .build();
    }

    /**
     * Helper class for building the DownloadInfo object.
     */
    public static class Builder {
        private String mUrl;
        private String mUserAgent;
        private String mMimeType;
        private String mCookie;
        private String mFileName;
        private String mDescription;
        private String mFilePath;
        private String mReferrer;
        private String mOriginalUrl;
        private long mBytesReceived;
        private boolean mIsGETRequest;
        private String mDownloadGuid;
        private boolean mHasUserGesture;
        private String mContentDisposition;
        private Progress mProgress = Progress.createIndeterminateProgress();
        private long mTimeRemainingInMillis;
        private boolean mIsResumable = true;
        private boolean mIsPaused;
        private boolean mIsOffTheRecord;
        private boolean mIsOfflinePage;
        private int mState = DownloadState.IN_PROGRESS;
        private long mLastAccessTime;
        private ContentId mContentId;
        private boolean mIsOpenable = true;
        private boolean mIsTransient;
        private Bitmap mIcon;

        public Builder setUrl(String url) {
            mUrl = url;
            return this;
        }

        public Builder setUserAgent(String userAgent) {
            mUserAgent = userAgent;
            return this;
        }

        public Builder setMimeType(String mimeType) {
            mMimeType = mimeType;
            return this;
        }

        public Builder setCookie(String cookie) {
            mCookie = cookie;
            return this;
        }

        public Builder setFileName(String fileName) {
            mFileName = fileName;
            return this;
        }

        public Builder setDescription(String description) {
            mDescription = description;
            return this;
        }

        public Builder setFilePath(String filePath) {
            mFilePath = filePath;
            return this;
        }

        public Builder setReferrer(String referer) {
            mReferrer = referer;
            return this;
        }

        public Builder setOriginalUrl(String originalUrl) {
            mOriginalUrl = originalUrl;
            return this;
        }

        public Builder setBytesReceived(long bytesReceived) {
            mBytesReceived = bytesReceived;
            return this;
        }

        public Builder setIsGETRequest(boolean isGETRequest) {
            mIsGETRequest = isGETRequest;
            return this;
        }

        public Builder setDownloadGuid(String downloadGuid) {
            mDownloadGuid = downloadGuid;
            return this;
        }

        public Builder setHasUserGesture(boolean hasUserGesture) {
            mHasUserGesture = hasUserGesture;
            return this;
        }

        public Builder setContentDisposition(String contentDisposition) {
            mContentDisposition = contentDisposition;
            return this;
        }

        public Builder setProgress(OfflineItem.Progress progress) {
            mProgress = progress;
            return this;
        }

        public Builder setTimeRemainingInMillis(long timeRemainingInMillis) {
            mTimeRemainingInMillis = timeRemainingInMillis;
            return this;
        }

        public Builder setIsResumable(boolean isResumable) {
            mIsResumable = isResumable;
            return this;
        }

        public Builder setIsPaused(boolean isPaused) {
            mIsPaused = isPaused;
            return this;
        }

        public Builder setIsOffTheRecord(boolean isOffTheRecord) {
            mIsOffTheRecord = isOffTheRecord;
            return this;
        }

        public Builder setIsOfflinePage(boolean isOfflinePage) {
            mIsOfflinePage = isOfflinePage;
            return this;
        }

        public Builder setState(int downloadState) {
            mState = downloadState;
            return this;
        }

        public Builder setLastAccessTime(long lastAccessTime) {
            mLastAccessTime = lastAccessTime;
            return this;
        }

        public Builder setContentId(ContentId contentId) {
            mContentId = contentId;
            return this;
        }

        public Builder setIsOpenable(boolean isOpenable) {
            mIsOpenable = isOpenable;
            return this;
        }

        public Builder setIsTransient(boolean isTransient) {
            mIsTransient = isTransient;
            return this;
        }

        public Builder setIcon(Bitmap icon) {
            mIcon = icon;
            return this;
        }

        public DownloadInfo build() {
            return new DownloadInfo(this);
        }

        /**
         * Create a builder from the DownloadInfo object.
         * @param downloadInfo DownloadInfo object from which builder fields are populated.
         * @return A builder initialized with fields from downloadInfo object.
         */
        public static Builder fromDownloadInfo(final DownloadInfo downloadInfo) {
            Builder builder = new Builder();
            builder.setUrl(downloadInfo.getUrl())
                    .setUserAgent(downloadInfo.getUserAgent())
                    .setMimeType(downloadInfo.getMimeType())
                    .setCookie(downloadInfo.getCookie())
                    .setFileName(downloadInfo.getFileName())
                    .setDescription(downloadInfo.getDescription())
                    .setFilePath(downloadInfo.getFilePath())
                    .setReferrer(downloadInfo.getReferrer())
                    .setOriginalUrl(downloadInfo.getOriginalUrl())
                    .setBytesReceived(downloadInfo.getBytesReceived())
                    .setDownloadGuid(downloadInfo.getDownloadGuid())
                    .setHasUserGesture(downloadInfo.hasUserGesture())
                    .setContentDisposition(downloadInfo.getContentDisposition())
                    .setIsGETRequest(downloadInfo.isGETRequest())
                    .setProgress(downloadInfo.getProgress())
                    .setTimeRemainingInMillis(downloadInfo.getTimeRemainingInMillis())
                    .setIsResumable(downloadInfo.isResumable())
                    .setIsPaused(downloadInfo.isPaused())
                    .setIsOffTheRecord(downloadInfo.isOffTheRecord())
                    .setIsOfflinePage(downloadInfo.isOfflinePage())
                    .setState(downloadInfo.state())
                    .setLastAccessTime(downloadInfo.getLastAccessTime())
                    .setIcon(downloadInfo.getIcon());
            return builder;
        }
    }

    @CalledByNative
    private static DownloadInfo createDownloadInfo(String downloadGuid, String fileName,
            String filePath, String url, String mimeType, long bytesReceived, boolean isIncognito,
            int state, int percentCompleted, boolean isPaused, boolean hasUserGesture,
            boolean isResumable, String originalUrl, String referrerUrl, long timeRemainingInMs,
            long lastAccessTime) {
        String remappedMimeType = ChromeDownloadDelegate.remapGenericMimeType(
                mimeType, url, fileName);
        Progress progress = new Progress(percentCompleted, percentCompleted == -1 ? null : 100L,
                OfflineItemProgressUnit.PERCENTAGE);
        return new DownloadInfo.Builder()
                .setBytesReceived(bytesReceived)
                .setDescription(fileName)
                .setDownloadGuid(downloadGuid)
                .setFileName(fileName)
                .setFilePath(filePath)
                .setHasUserGesture(hasUserGesture)
                .setIsOffTheRecord(isIncognito)
                .setIsPaused(isPaused)
                .setIsResumable(isResumable)
                .setMimeType(remappedMimeType)
                .setOriginalUrl(originalUrl)
                .setProgress(progress)
                .setReferrer(referrerUrl)
                .setState(state)
                .setTimeRemainingInMillis(timeRemainingInMs)
                .setLastAccessTime(lastAccessTime)
                .setUrl(url)
                .build();
    }
}

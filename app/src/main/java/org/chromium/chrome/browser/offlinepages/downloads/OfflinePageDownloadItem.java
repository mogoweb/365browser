// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.offlinepages.downloads;

import org.chromium.components.offlinepages.downloads.DownloadState;

/** Class representing offline page or save page request to downloads UI. */
public class OfflinePageDownloadItem {
    private final String mUrl;
    private final int mDownloadState;
    private final long mDownloadProgressBytes;
    private final String mTitle;
    private final String mGuid;
    private final String mTargetPath;
    private final long mStartTimeMs;
    private final long mTotalBytes;

    public OfflinePageDownloadItem(String guid, String url, int downloadState,
            long downloadProgressBytes, String title, String targetPath,
            long startTimeMs, long totalBytes) {
        mGuid = guid;
        mUrl = url;
        mDownloadState = downloadState;
        mDownloadProgressBytes = downloadProgressBytes;
        mTitle = title;
        mTargetPath = targetPath;
        mStartTimeMs = startTimeMs;
        mTotalBytes = totalBytes;
    }

    /** @return GUID identifying the item. */
    public String getGuid() {
        return mGuid;
    }

    /** @return URL related to the item. */
    public String getUrl() {
        return mUrl;
    }

    /** @return DownloadState value. */
    public int getDownloadState() {
        return mDownloadState;
    }

    /**
     *  @return current download progress while the item is downloaded.
     *  Returns 0 if the item is not currently downloading.
     */
    public long getDownloadProgressBytes() {
        if (mDownloadState != DownloadState.IN_PROGRESS) return 0;
        return mDownloadProgressBytes;
    }

    /** @return Title of the page. */
    public String getTitle() {
        return mTitle;
    }

    /** @return Path to the offline item on the disk. */
    public String getTargetPath() {
        return mTargetPath;
    }

    /** @return Start time of the item, corresponding to when the offline page was saved. */
    public long getStartTimeMs() {
        return mStartTimeMs;
    }

    /** @return Size of the offline archive in bytes. */
    public long getTotalBytes() {
        return mTotalBytes;
    }

    /** @return Whether this page is to be shown in the suggested reading section. */
    public boolean isSuggested() {
        return false;
    }
}

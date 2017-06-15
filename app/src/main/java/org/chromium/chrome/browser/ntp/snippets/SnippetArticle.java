// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
package org.chromium.chrome.browser.ntp.snippets;

import android.graphics.Bitmap;
import android.support.annotation.Nullable;

import org.chromium.base.DiscardableReferencePool.DiscardableReference;
import org.chromium.chrome.browser.suggestions.OfflinableSuggestion;

import java.io.File;

/**
 * Represents the data for an article card on the NTP.
 */
public class SnippetArticle implements OfflinableSuggestion {
    /** The category of this article. */
    public final int mCategory;

    /** The identifier for this article within the category - not necessarily unique globally. */
    public final String mIdWithinCategory;

    /** The title of this article. */
    public final String mTitle;

    /** The canonical publisher name (e.g., New York Times). */
    public final String mPublisher;

    /** The snippet preview text. */
    public final String mPreviewText;

    /** The URL of this article. This may be an AMP url. */
    public final String mUrl;

    /** The time when this article was published. */
    public final long mPublishTimestampMilliseconds;

    /** The score expressing relative quality of the article for the user. */
    public final float mScore;

    /**
     * The time when the article was fetched from the server. This field is only used for remote
     * suggestions.
     */
    public final long mFetchTimestampMilliseconds;

    /** The rank of this article within its section. */
    private int mPerSectionRank = -1;

    /** The global rank of this article in the complete list. */
    private int mGlobalRank = -1;

    /** Bitmap of the thumbnail, fetched lazily, when the RecyclerView wants to show the snippet. */
    private DiscardableReference<Bitmap> mThumbnailBitmap;

    /** Stores whether impression of this article has been tracked already. */
    private boolean mImpressionTracked;

    /** Whether the linked article represents an asset download. */
    private boolean mIsAssetDownload;

    /** The GUID of the asset download (only for asset download articles). */
    private String mAssetDownloadGuid;

    /** The path to the asset download (only for asset download articles). */
    private File mAssetDownloadFile;

    /** The mime type of the asset download (only for asset download articles). */
    private String mAssetDownloadMimeType;

    /** The tab id of the corresponding tab (only for recent tab articles). */
    private int mRecentTabId;

    /** The offline id of the corresponding offline page, if any. */
    private Long mOfflinePageOfflineId;

    /**
     * Creates a SnippetArticleListItem object that will hold the data.
     */
    public SnippetArticle(int category, String idWithinCategory, String title, String publisher,
            String previewText, String url, long publishTimestamp, float score,
            long fetchTimestamp) {
        mCategory = category;
        mIdWithinCategory = idWithinCategory;
        mTitle = title;
        mPublisher = publisher;
        mPreviewText = previewText;
        mUrl = url;
        mPublishTimestampMilliseconds = publishTimestamp;
        mScore = score;
        mFetchTimestampMilliseconds = fetchTimestamp;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof SnippetArticle)) return false;
        SnippetArticle rhs = (SnippetArticle) other;
        return mCategory == rhs.mCategory && mIdWithinCategory.equals(rhs.mIdWithinCategory);
    }

    @Override
    public int hashCode() {
        return mCategory ^ mIdWithinCategory.hashCode();
    }

    /**
     * Returns this article's thumbnail as a {@link Bitmap}. Can return {@code null} as it is
     * initially unset.
     */
    public Bitmap getThumbnailBitmap() {
        return mThumbnailBitmap == null ? null : mThumbnailBitmap.get();
    }

    /** Sets the thumbnail bitmap for this article. */
    public void setThumbnailBitmap(DiscardableReference<Bitmap> bitmap) {
        mThumbnailBitmap = bitmap;
    }

    /** Returns whether to track an impression for this article. */
    public boolean trackImpression() {
        // Track UMA only upon the first impression per life-time of this object.
        if (mImpressionTracked) return false;
        mImpressionTracked = true;
        return true;
    }

    /** @return whether a snippet is a remote suggestion. */
    public boolean isArticle() {
        return mCategory == KnownCategories.ARTICLES;
    }

    /** @return whether a snippet is either offline page or asset download. */
    public boolean isDownload() {
        return mCategory == KnownCategories.DOWNLOADS;
    }

    /** @return whether a snippet is asset download. */
    public boolean isAssetDownload() {
        return mIsAssetDownload;
    }

    /**
     * @return the GUID of the asset download. May only be called if {@link #mIsAssetDownload} is
     * {@code true} (which implies that this snippet belongs to the DOWNLOADS category).
     */
    public String getAssetDownloadGuid() {
        assert isDownload();
        assert mIsAssetDownload;
        return mAssetDownloadGuid;
    }

    /**
     * @return the asset download path. May only be called if {@link #mIsAssetDownload} is
     * {@code true} (which implies that this snippet belongs to the DOWNLOADS category).
     */
    public File getAssetDownloadFile() {
        assert isDownload();
        assert mIsAssetDownload;
        return mAssetDownloadFile;
    }

    /**
     * @return the mime type of the asset download. May only be called if {@link #mIsAssetDownload}
     * is {@code true} (which implies that this snippet belongs to the DOWNLOADS category).
     */
    public String getAssetDownloadMimeType() {
        assert isDownload();
        assert mIsAssetDownload;
        return mAssetDownloadMimeType;
    }

    /**
     * Marks the article suggestion as an asset download with the given path and mime type. May only
     * be called if this snippet belongs to DOWNLOADS category.
     */
    public void setAssetDownloadData(String downloadGuid, String filePath, String mimeType) {
        assert isDownload();
        mIsAssetDownload = true;
        mAssetDownloadGuid = downloadGuid;
        mAssetDownloadFile = new File(filePath);
        mAssetDownloadMimeType = mimeType;
    }

    /**
     * Marks the article suggestion as an offline page download with the given id. May only
     * be called if this snippet belongs to DOWNLOADS category.
     */
    public void setOfflinePageDownloadData(long offlinePageId) {
        assert isDownload();
        mIsAssetDownload = false;
        setOfflinePageOfflineId(offlinePageId);
    }

    @Override
    public boolean requiresExactOfflinePage() {
        return isDownload() || isRecentTab();
    }

    public boolean isRecentTab() {
        return mCategory == KnownCategories.RECENT_TABS;
    }

    /**
     * @return the corresponding recent tab id. May only be called if this snippet is a recent tab
     * article.
     */
    public int getRecentTabId() {
        assert isRecentTab();
        return mRecentTabId;
    }

    /**
     * Sets tab id and offline page id for recent tab articles. May only be called if this snippet
     * is a recent tab article.
     */
    public void setRecentTabData(int tabId, long offlinePageId) {
        assert isRecentTab();
        mRecentTabId = tabId;
        setOfflinePageOfflineId(offlinePageId);
    }

    @Override
    public String getUrl() {
        return mUrl;
    }

    @Override
    public void setOfflinePageOfflineId(@Nullable Long offlineId) {
        mOfflinePageOfflineId = offlineId;
    }

    @Override
    @Nullable
    public Long getOfflinePageOfflineId() {
        return mOfflinePageOfflineId;
    }

    @Override
    public String toString() {
        // For debugging purposes. Displays the first 42 characters of the title.
        return String.format("{%s, %1.42s}", getClass().getSimpleName(), mTitle);
    }

    public void setRank(int perSectionRank, int globalRank) {
        mPerSectionRank = perSectionRank;
        mGlobalRank = globalRank;
    }

    public int getGlobalRank() {
        return mGlobalRank;
    }

    public int getPerSectionRank() {
        return mPerSectionRank;
    }
}

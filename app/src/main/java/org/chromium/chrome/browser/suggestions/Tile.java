// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.suggestions;

import android.graphics.drawable.Drawable;
import android.support.annotation.Nullable;

/**
 * Holds the details to populate a site suggestion tile.
 */
public class Tile implements OfflinableSuggestion {
    private final String mTitle;
    private final String mUrl;
    private final String mWhitelistIconPath;
    private final int mIndex;

    @TileSource
    private final int mSource;

    @TileVisualType
    private int mType = TileVisualType.NONE;

    @Nullable
    private Drawable mIcon;

    @Nullable
    private Long mOfflinePageOfflineId;

    /**
     * @param title The tile title.
     * @param url The site URL.
     * @param whitelistIconPath The path to the icon image file, if this is a whitelisted tile.
     * Empty otherwise.
     * @param index The index of this tile in the list of tiles.
     * @param source The {@code TileSource} that generated this tile.
     */
    public Tile(
            String title, String url, String whitelistIconPath, int index, @TileSource int source) {
        mTitle = title;
        mUrl = url;
        mWhitelistIconPath = whitelistIconPath;
        mIndex = index;
        mSource = source;
    }

    /**
     * Imports transient data from an old tile, and reports whether there is a significant
     * difference between the two that would require a redraw.
     * Assumes that the current tile and the old tile (if provided) both describe the same site,
     * so the URLs have to be the same.
     */
    public boolean importData(@Nullable Tile tile) {
        if (tile == null) return true;

        assert tile.getUrl().equals(mUrl);

        mType = tile.getType();
        mIcon = tile.getIcon();
        mOfflinePageOfflineId = tile.mOfflinePageOfflineId;

        if (!tile.getTitle().equals(mTitle)) return true;
        if (tile.getIndex() != mIndex) return true;

        // Ignore the whitelist changes when we already have an icon, since we won't need to reload
        // it. We also omit requesting a redraw when |mSource| changes, as it only affects UMA.
        if (!tile.getWhitelistIconPath().equals(mWhitelistIconPath) && mIcon == null) return true;

        return false;
    }

    @Override
    public String getUrl() {
        return mUrl;
    }

    @Override
    public void setOfflinePageOfflineId(@Nullable Long offlineId) {
        mOfflinePageOfflineId = offlineId;
    }

    @Nullable
    @Override
    public Long getOfflinePageOfflineId() {
        return mOfflinePageOfflineId;
    }

    @Override
    public boolean requiresExactOfflinePage() {
        return false;
    }

    /**
     * @return The title of this tile.
     */
    public String getTitle() {
        return mTitle;
    }

    /**
     * @return The path of the whitelist icon associated with the URL.
     */
    public String getWhitelistIconPath() {
        return mWhitelistIconPath;
    }

    /**
     * @return Whether this tile is available offline.
     */
    public boolean isOfflineAvailable() {
        return getOfflinePageOfflineId() != null;
    }

    /**
     * @return The index of this tile in the list of tiles.
     */
    public int getIndex() {
        return mIndex;
    }

    /**
     * @return The source of this tile. Used for metrics tracking. Valid values are listed in
     * {@code TileSource}.
     */
    @TileSource
    public int getSource() {
        return mSource;
    }

    /**
     * @return The visual type of this tile. Valid values are listed in {@link TileVisualType}.
     */
    @TileVisualType
    public int getType() {
        return mType;
    }

    /**
     * Sets the visual type of this tile. Valid values are listed in
     * {@link TileVisualType}.
     */
    public void setType(@TileVisualType int type) {
        mType = type;
    }

    /**
     * @return The icon, may be null.
     */
    @Nullable
    public Drawable getIcon() {
        return mIcon;
    }

    /**
     * Updates the icon drawable.
     */
    public void setIcon(@Nullable Drawable icon) {
        mIcon = icon;
    }
}

// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.suggestions;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import org.chromium.base.ContextUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.ntp.ContextMenuManager;
import org.chromium.chrome.browser.ntp.cards.ItemViewType;
import org.chromium.chrome.browser.ntp.cards.NewTabPageViewHolder;
import org.chromium.chrome.browser.ntp.cards.NodeVisitor;
import org.chromium.chrome.browser.ntp.cards.OptionalLeaf;
import org.chromium.chrome.browser.offlinepages.OfflinePageBridge;

/**
 * The model and controller for a group of site suggestion tiles that will be rendered in a grid.
 */
public class TileGrid extends OptionalLeaf implements TileGroup.Observer {
    /**
     * The maximum number of tiles to try and fit in a row. On smaller screens, there may not be
     * enough space to fit all of them.
     */
    private static final int MAX_TILE_COLUMNS = 4;

    /**
     * Experiment parameter for the maximum number of tile suggestion rows to show.
     */
    private static final String PARAM_CHROME_HOME_MAX_TILE_ROWS = "chrome_home_max_tile_rows";

    /**
     * Experiment parameter for the number of tile title lines to show.
     */
    private static final String PARAM_CHROME_HOME_TILE_TITLE_LINES = "chrome_home_tile_title_lines";

    private final TileGroup mTileGroup;

    public TileGrid(SuggestionsUiDelegate uiDelegate, ContextMenuManager contextMenuManager,
            TileGroup.Delegate tileGroupDelegate, OfflinePageBridge offlinePageBridge) {
        mTileGroup = new TileGroup(ContextUtils.getApplicationContext(), uiDelegate,
                contextMenuManager, tileGroupDelegate,
                /* observer = */ this, offlinePageBridge, getTileTitleLines());
        mTileGroup.startObserving(getMaxTileRows() * MAX_TILE_COLUMNS);
    }

    @Override
    @ItemViewType
    protected int getItemViewType() {
        return ItemViewType.TILE_GRID;
    }

    @Override
    protected void onBindViewHolder(NewTabPageViewHolder holder) {
        assert holder instanceof ViewHolder;
        ((ViewHolder) holder).updateTiles(mTileGroup);
    }

    @Override
    protected void visitOptionalItem(NodeVisitor visitor) {
        visitor.visitTileGrid();
    }

    @Override
    public void onTileDataChanged() {
        setVisible(mTileGroup.getTiles().length != 0);
        if (isVisible()) notifyItemChanged(0, new ViewHolder.UpdateTilesCallback(mTileGroup));
    }

    @Override
    public void onTileCountChanged() {
        onTileDataChanged();
    }

    @Override
    public void onTileIconChanged(Tile tile) {
        if (isVisible()) notifyItemChanged(0, new ViewHolder.UpdateIconViewCallback(tile));
    }

    @Override
    public void onTileOfflineBadgeVisibilityChanged(Tile tile) {
        if (isVisible()) notifyItemChanged(0, new ViewHolder.UpdateOfflineBadgeCallback(tile));
    }

    @Override
    public void onLoadTaskAdded() {}

    @Override
    public void onLoadTaskCompleted() {}

    public TileGroup getTileGroup() {
        return mTileGroup;
    }

    private static int getMaxTileRows() {
        int defaultValue = 1;
        return ChromeFeatureList.getFieldTrialParamByFeatureAsInt(
                ChromeFeatureList.CHROME_HOME, PARAM_CHROME_HOME_MAX_TILE_ROWS, defaultValue);
    }

    private static int getTileTitleLines() {
        int defaultValue = 1;
        return ChromeFeatureList.getFieldTrialParamByFeatureAsInt(
                ChromeFeatureList.CHROME_HOME, PARAM_CHROME_HOME_TILE_TITLE_LINES, defaultValue);
    }

    /**
     * The {@code ViewHolder} for the {@link TileGrid}.
     */
    public static class ViewHolder extends NewTabPageViewHolder {
        private final TileGridLayout mLayout;

        public ViewHolder(ViewGroup parentView) {
            super(LayoutInflater.from(parentView.getContext())
                            .inflate(R.layout.suggestions_site_tile_grid, parentView, false));
            mLayout = (TileGridLayout) itemView;
            mLayout.setMaxRows(getMaxTileRows());
            mLayout.setMaxColumns(MAX_TILE_COLUMNS);
        }

        public void updateTiles(TileGroup tileGroup) {
            tileGroup.renderTileViews(mLayout, /* trackLoadTasks = */ false,
                    /* condensed = */ false);
        }

        public void updateIconView(Tile tile) {
            mLayout.updateIconView(tile);
        }

        public void updateOfflineBadge(Tile tile) {
            mLayout.updateOfflineBadge(tile);
        }

        /**
         * Callback to update all the tiles in the view holder.
         */
        public static class UpdateTilesCallback extends PartialBindCallback {
            private final TileGroup mTileGroup;

            public UpdateTilesCallback(TileGroup tileGroup) {
                mTileGroup = tileGroup;
            }

            @Override
            public void onResult(NewTabPageViewHolder holder) {
                assert holder instanceof ViewHolder;
                ((ViewHolder) holder).updateTiles(mTileGroup);
            }
        }

        /**
         * Callback to update the icon view for the view holder.
         */
        public static class UpdateIconViewCallback extends PartialBindCallback {
            private final Tile mTile;

            public UpdateIconViewCallback(Tile tile) {
                mTile = tile;
            }

            @Override
            public void onResult(NewTabPageViewHolder holder) {
                assert holder instanceof ViewHolder;
                ((ViewHolder) holder).updateIconView(mTile);
            }
        }

        /**
         * Callback to update the offline badge for the view holder.
         */
        public static class UpdateOfflineBadgeCallback extends PartialBindCallback {
            private final Tile mTile;

            public UpdateOfflineBadgeCallback(Tile tile) {
                mTile = tile;
            }

            @Override
            public void onResult(NewTabPageViewHolder holder) {
                assert holder instanceof ViewHolder;
                ((ViewHolder) holder).updateOfflineBadge(mTile);
            }
        }
    }
}

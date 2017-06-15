// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.suggestions;

import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.ntp.TitleUtil;

/**
 * The view for a site suggestion tile. Displays the title of the site beneath a large icon. If a
 * large icon isn't available, displays a rounded rectangle with a single letter in its place.
 */
public class TileView extends FrameLayout {
    /** The url currently associated to this tile. */
    private String mUrl;

    private TextView mTitleView;
    private ImageView mIconView;
    private ImageView mBadgeView;

    /**
     * Constructor for inflating from XML.
     */
    public TileView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mTitleView = (TextView) findViewById(R.id.tile_view_title);
        mIconView = (ImageView) findViewById(R.id.tile_view_icon);
        mBadgeView = (ImageView) findViewById(R.id.offline_badge);
    }

    /**
     * Initializes the view using the data held by {@code tile}. This should be called immediately
     * after inflation.
     * @param tile The tile that holds the data to populate this view.
     * @param titleLines The number of text lines to use for the tile title.
     * @param condensed Whether to use a condensed layout.
     */
    public void initialize(Tile tile, int titleLines, boolean condensed) {
        mTitleView.setLines(titleLines);
        mUrl = tile.getUrl();

        // TODO(mvanouwerkerk): Move this code to xml - https://crbug.com/695817.
        if (condensed) {
            Resources res = getResources();

            setPadding(0, 0, 0, 0);
            LayoutParams tileParams = (LayoutParams) getLayoutParams();
            tileParams.width = res.getDimensionPixelOffset(R.dimen.tile_view_width_condensed);
            setLayoutParams(tileParams);

            LayoutParams iconParams = (LayoutParams) mIconView.getLayoutParams();
            iconParams.setMargins(0,
                    res.getDimensionPixelOffset(R.dimen.tile_view_icon_margin_top_condensed), 0, 0);
            mIconView.setLayoutParams(iconParams);

            View highlightView = findViewById(R.id.tile_view_highlight);
            LayoutParams highlightParams = (LayoutParams) highlightView.getLayoutParams();
            highlightParams.setMargins(0,
                    res.getDimensionPixelOffset(R.dimen.tile_view_icon_margin_top_condensed), 0, 0);
            highlightView.setLayoutParams(highlightParams);

            LayoutParams titleParams = (LayoutParams) mTitleView.getLayoutParams();
            titleParams.setMargins(0,
                    res.getDimensionPixelOffset(R.dimen.tile_view_title_margin_top_condensed), 0,
                    0);
            mTitleView.setLayoutParams(titleParams);
        }

        renderTile(tile);
    }

    /** @return The url associated with this view. */
    public String getUrl() {
        return mUrl;
    }

    /**
     * Renders the icon held by the {@link Tile} or clears it from the view if the icon is null.
     */
    public void renderIcon(Tile tile) {
        mIconView.setImageDrawable(tile.getIcon());
    }

    /** Shows or hides the offline badge to reflect the offline availability of the {@link Tile}. */
    public void renderOfflineBadge(Tile tile) {
        mBadgeView.setVisibility(tile.isOfflineAvailable() ? VISIBLE : GONE);
    }

    /** Updates the view if there have been changes since the last time. */
    public void updateIfDataChanged(Tile tile) {
        if (!isUpToDate(tile)) renderTile(tile);
    }

    private boolean isUpToDate(Tile tile) {
        assert mUrl.equals(tile.getUrl());

        if (tile.getIcon() != mIconView.getDrawable()) return false;
        if (tile.isOfflineAvailable() != (mBadgeView.getVisibility() == VISIBLE)) return false;
        // We don't check the title since it's not likely to change, but that could also be done.
        return true;
    }

    private void renderTile(Tile tile) {
        // A TileView should not be reused across tiles having different urls, as registered
        // callbacks and handlers use it to look up the data and notify the rest of the system.
        assert mUrl.equals(tile.getUrl());
        mTitleView.setText(TitleUtil.getTitleForDisplay(tile.getTitle(), tile.getUrl()));
        renderOfflineBadge(tile);
        renderIcon(tile);
    }
}

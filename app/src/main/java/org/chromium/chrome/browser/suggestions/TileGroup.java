// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.suggestions;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.support.annotation.Nullable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnCreateContextMenuListener;
import android.view.ViewGroup;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.Callback;
import org.chromium.base.Log;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.favicon.LargeIconBridge.LargeIconCallback;
import org.chromium.chrome.browser.ntp.ContextMenuManager;
import org.chromium.chrome.browser.ntp.ContextMenuManager.ContextMenuItemId;
import org.chromium.chrome.browser.offlinepages.OfflinePageBridge;
import org.chromium.chrome.browser.widget.RoundedIconGenerator;
import org.chromium.ui.mojom.WindowOpenDisposition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The model and controller for a group of site suggestion tiles.
 */
public class TileGroup implements MostVisitedSites.Observer {
    /**
     * Performs work in other parts of the system that the {@link TileGroup} should not know about.
     */
    public interface Delegate {
        /**
         * @param tile The tile corresponding to the most visited item to remove.
         * @param removalUndoneCallback The callback to invoke if the removal is reverted. The
         *                              callback's argument is the URL being restored.
         */
        void removeMostVisitedItem(Tile tile, Callback<String> removalUndoneCallback);

        void openMostVisitedItem(int windowDisposition, Tile tile);

        /**
         * Gets the list of most visited sites.
         * @param observer The observer to be notified with the list of sites.
         * @param maxResults The maximum number of sites to retrieve.
         */
        void setMostVisitedSitesObserver(MostVisitedSites.Observer observer, int maxResults);

        /**
         * Called when the NTP has completely finished loading (all views will be inflated
         * and any dependent resources will have been loaded).
         * @param tiles The tiles owned by the {@link TileGroup}. Used to record metrics.
         */
        void onLoadingComplete(Tile[] tiles);

        /**
         * To be called before this instance is abandoned to the garbage collector so it can do any
         * necessary cleanups. This instance must not be used after this method is called.
         */
        void destroy();
    }

    /**
     * An observer for events in the {@link TileGroup}.
     */
    public interface Observer {
        /**
         * Called when the tile group is initialised and when any of the tile data has changed,
         * such as an icon, url, or title.
         */
        void onTileDataChanged();

        /**
         * Called when the number of tiles has changed.
         */
        void onTileCountChanged();

        /**
         * Called when a tile icon has changed.
         * @param tile The tile for which the icon has changed.
         */
        void onTileIconChanged(Tile tile);

        /**
         * Called when the visibility of a tile's offline badge has changed.
         * @param tile The tile for which the visibility of the offline badge has changed.
         */
        void onTileOfflineBadgeVisibilityChanged(Tile tile);

        /**
         * Called when an asynchronous loading task has started.
         */
        void onLoadTaskAdded();

        /**
         * Called when an asynchronous loading task has completed.
         */
        void onLoadTaskCompleted();
    }

    private static final String TAG = "TileGroup";

    private static final int ICON_CORNER_RADIUS_DP = 4;
    private static final int ICON_TEXT_SIZE_DP = 20;
    private static final int ICON_MIN_SIZE_PX = 48;

    private final Context mContext;
    private final SuggestionsUiDelegate mUiDelegate;
    private final ContextMenuManager mContextMenuManager;
    private final Delegate mTileGroupDelegate;
    private final Observer mObserver;
    private final int mTitleLinesCount;
    private final int mMinIconSize;
    private final int mDesiredIconSize;
    private final RoundedIconGenerator mIconGenerator;

    /**
     * Access point to offline related features. Will be {@code null} when the badges are disabled.
     * @see ChromeFeatureList#NTP_OFFLINE_PAGES_FEATURE_NAME
     */
    @Nullable
    private final OfflineModelObserver mOfflineModelObserver;

    /**
     * Source of truth for the tile data. Since the objects can change when the data is updated,
     * other objects should not hold references to them but keep track of the URL instead, and use
     * it to retrieve a {@link Tile}.
     * @see #getTile(String)
     */
    private Tile[] mTiles = new Tile[0];

    /** Most recently received tile data that has not been displayed yet. */
    @Nullable
    private List<Tile> mPendingTiles;

    /**
     * URL of the most recently removed tile. Used to identify when a tile removal is confirmed by
     * the tile backend.
     */
    @Nullable
    private String mPendingRemovalUrl;

    /**
     * URL of the most recently added tile. Used to identify when a given tile's insertion is
     * confirmed by the tile backend. This is relevant when a previously existing tile is removed,
     * then the user undoes the action and wants that tile back.
     */
    @Nullable
    private String mPendingInsertionUrl;

    private boolean mHasReceivedData;

    /**
     * @param context Used for initialisation and resolving resources.
     * @param uiDelegate Delegate used to interact with the rest of the system.
     * @param contextMenuManager Used to handle context menu invocations on the tiles.
     * @param tileGroupDelegate Used for interactions with the Most Visited backend.
     * @param observer Will be notified of changes to the tile data.
     * @param titleLines The number of text lines to use for each tile title.
     */
    public TileGroup(Context context, SuggestionsUiDelegate uiDelegate,
            ContextMenuManager contextMenuManager, Delegate tileGroupDelegate, Observer observer,
            OfflinePageBridge offlinePageBridge, int titleLines) {
        mContext = context;
        mUiDelegate = uiDelegate;
        mContextMenuManager = contextMenuManager;
        mTileGroupDelegate = tileGroupDelegate;
        mObserver = observer;
        mTitleLinesCount = titleLines;

        Resources resources = mContext.getResources();
        mDesiredIconSize = resources.getDimensionPixelSize(R.dimen.tile_view_icon_size);
        // On ldpi devices, mDesiredIconSize could be even smaller than ICON_MIN_SIZE_PX.
        mMinIconSize = Math.min(mDesiredIconSize, ICON_MIN_SIZE_PX);
        int desiredIconSizeDp =
                Math.round(mDesiredIconSize / resources.getDisplayMetrics().density);
        int iconColor =
                ApiCompatibilityUtils.getColor(resources, R.color.default_favicon_background_color);
        mIconGenerator = new RoundedIconGenerator(mContext, desiredIconSizeDp, desiredIconSizeDp,
                ICON_CORNER_RADIUS_DP, iconColor, ICON_TEXT_SIZE_DP);

        if (ChromeFeatureList.isEnabled(ChromeFeatureList.NTP_OFFLINE_PAGES_FEATURE_NAME)) {
            mOfflineModelObserver = new OfflineModelObserver(offlinePageBridge);
            mUiDelegate.addDestructionObserver(mOfflineModelObserver);
        } else {
            mOfflineModelObserver = null;
        }
    }

    @Override
    public void onMostVisitedURLsAvailable(final String[] titles, final String[] urls,
            final String[] whitelistIconPaths, final int[] sources) {
        boolean removalCompleted = mPendingRemovalUrl != null;
        boolean insertionCompleted = mPendingInsertionUrl == null;

        Set<String> addedUrls = new HashSet<>();
        mPendingTiles = new ArrayList<>();
        for (int i = 0; i < titles.length; i++) {
            assert urls[i] != null; // We assume everywhere that the url is not null.

            // TODO(dgn): Checking this should not even be necessary as the backend is supposed to
            // send non dupes URLs. Remove once https://crbug.com/703628 is fixed.
            if (addedUrls.contains(urls[i])) continue;

            mPendingTiles.add(new Tile(titles[i], urls[i], whitelistIconPaths[i], i, sources[i]));
            addedUrls.add(urls[i]);

            if (urls[i].equals(mPendingRemovalUrl)) removalCompleted = false;
            if (urls[i].equals(mPendingInsertionUrl)) insertionCompleted = true;
        }

        boolean expectedChangeCompleted = false;
        if (mPendingRemovalUrl != null && removalCompleted) {
            mPendingRemovalUrl = null;
            expectedChangeCompleted = true;
        }
        if (mPendingInsertionUrl != null && insertionCompleted) {
            mPendingInsertionUrl = null;
            expectedChangeCompleted = true;
        }

        if (!mHasReceivedData || !mUiDelegate.isVisible() || expectedChangeCompleted) loadTiles();
    }

    @Override
    public void onIconMadeAvailable(String siteUrl) {
        Tile tile = getTile(siteUrl);
        if (tile == null) return; // The tile might have been removed.

        LargeIconCallback iconCallback =
                new LargeIconCallbackImpl(siteUrl, /* trackLoadTask = */ false);
        mUiDelegate.getLargeIconForUrl(siteUrl, mMinIconSize, iconCallback);
    }

    /**
     * Instructs this instance to start listening for data. The {@link TileGroup.Observer} may be
     * called immediately if new data is received synchronously.
     * @param maxResults The maximum number of sites to retrieve.
     */
    public void startObserving(int maxResults) {
        mObserver.onLoadTaskAdded();
        mTileGroupDelegate.setMostVisitedSitesObserver(this, maxResults);
    }

    /**
     * Renders tile views in the given {@link TileGridLayout}, reusing existing tile views where
     * possible because view inflation and icon loading are slow.
     * @param parent The layout to render the tile views into.
     * @param trackLoadTasks Whether to track load tasks.
     * @param condensed Whether to use a condensed layout.
     */
    public void renderTileViews(ViewGroup parent, boolean trackLoadTasks, boolean condensed) {
        // Map the old tile views by url so they can be reused later.
        Map<String, TileView> oldTileViews = new HashMap<>();
        int childCount = parent.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TileView tileView = (TileView) parent.getChildAt(i);
            oldTileViews.put(tileView.getUrl(), tileView);
        }

        // Remove all views from the layout because even if they are reused later they'll have to be
        // added back in the correct order.
        parent.removeAllViews();

        for (Tile tile : mTiles) {
            TileView tileView = oldTileViews.get(tile.getUrl());
            if (tileView == null) {
                tileView = buildTileView(tile, parent, trackLoadTasks, condensed);
            } else {
                tileView.updateIfDataChanged(tile);
            }

            parent.addView(tileView);
        }
    }

    public Tile[] getTiles() {
        return Arrays.copyOf(mTiles, mTiles.length);
    }

    public boolean hasReceivedData() {
        return mHasReceivedData;
    }

    /** To be called when the view displaying the tile group becomes visible. */
    public void onSwitchToForeground() {
        if (mPendingTiles != null) loadTiles();
    }

    /**
     * Inflates a new tile view, initializes it, and loads an icon for it.
     * @param tile The tile that holds the data to populate the new tile view.
     * @param parentView The parent of the new tile view.
     * @param trackLoadTask Whether to track a load task.
     * @param condensed Whether to use a condensed layout.
     * @return The new tile view.
     */
    @VisibleForTesting
    TileView buildTileView(
            Tile tile, ViewGroup parentView, boolean trackLoadTask, boolean condensed) {
        TileView tileView = (TileView) LayoutInflater.from(parentView.getContext())
                                    .inflate(R.layout.tile_view, parentView, false);
        tileView.initialize(tile, mTitleLinesCount, condensed);

        // Note: It is important that the callbacks below don't keep a reference to the tile or
        // modify them as there is no guarantee that the same tile would be used to update the view.
        LargeIconCallback iconCallback = new LargeIconCallbackImpl(tile.getUrl(), trackLoadTask);
        if (trackLoadTask) mObserver.onLoadTaskAdded();
        if (!loadWhitelistIcon(tile, iconCallback)) {
            mUiDelegate.getLargeIconForUrl(tile.getUrl(), mMinIconSize, iconCallback);
        }

        TileInteractionDelegate delegate = new TileInteractionDelegate(tile.getUrl());
        tileView.setOnClickListener(delegate);
        tileView.setOnCreateContextMenuListener(delegate);

        return tileView;
    }

    private boolean loadWhitelistIcon(Tile tile, LargeIconCallback iconCallback) {
        if (tile.getWhitelistIconPath().isEmpty()) return false;

        // TODO(mvanouwerkerk): Consider using an AsyncTask to reduce rendering jank.
        Bitmap bitmap = BitmapFactory.decodeFile(tile.getWhitelistIconPath());
        if (bitmap == null) {
            Log.d(TAG, "Image decoding failed: %s", tile.getWhitelistIconPath());
            return false;
        }
        iconCallback.onLargeIconAvailable(bitmap, Color.BLACK, false);
        return true;
    }

    /** Loads tile data from {@link #mPendingTiles} and clears it afterwards. */
    private void loadTiles() {
        assert mPendingTiles != null;

        boolean isInitialLoad = !mHasReceivedData;
        mHasReceivedData = true;

        boolean countChanged = isInitialLoad || mTiles.length != mPendingTiles.size();
        boolean dataChanged = countChanged;
        for (Tile newTile : mPendingTiles) {
            if (newTile.importData(getTile(newTile.getUrl()))) dataChanged = true;
        }

        mTiles = mPendingTiles.toArray(new Tile[mPendingTiles.size()]);
        mPendingTiles = null;

        if (!dataChanged) return;

        if (mOfflineModelObserver != null) {
            mOfflineModelObserver.updateOfflinableSuggestionsAvailability();
        }

        if (countChanged) mObserver.onTileCountChanged();
        if (isInitialLoad) mObserver.onLoadTaskCompleted();
        mObserver.onTileDataChanged();
    }

    /** @return A tile matching the provided URL, or {@code null} if none is found. */
    @Nullable
    private Tile getTile(String url) {
        for (Tile tile : mTiles) {
            if (tile.getUrl().equals(url)) return tile;
        }
        return null;
    }

    private class LargeIconCallbackImpl implements LargeIconCallback {
        private final String mUrl;
        private final boolean mTrackLoadTask;

        private LargeIconCallbackImpl(String url, boolean trackLoadTask) {
            mUrl = url;
            mTrackLoadTask = trackLoadTask;
        }

        @Override
        public void onLargeIconAvailable(
                @Nullable Bitmap icon, int fallbackColor, boolean isFallbackColorDefault) {
            if (mTrackLoadTask) mObserver.onLoadTaskCompleted();

            Tile tile = getTile(mUrl);
            if (tile == null) return; // The tile might have been removed.

            if (icon == null) {
                mIconGenerator.setBackgroundColor(fallbackColor);
                icon = mIconGenerator.generateIconForUrl(mUrl);
                tile.setIcon(new BitmapDrawable(mContext.getResources(), icon));
                tile.setType(isFallbackColorDefault ? TileVisualType.ICON_DEFAULT
                                                    : TileVisualType.ICON_COLOR);
            } else {
                RoundedBitmapDrawable roundedIcon =
                        RoundedBitmapDrawableFactory.create(mContext.getResources(), icon);
                int cornerRadius = Math.round(ICON_CORNER_RADIUS_DP
                        * mContext.getResources().getDisplayMetrics().density * icon.getWidth()
                        / mDesiredIconSize);
                roundedIcon.setCornerRadius(cornerRadius);
                roundedIcon.setAntiAlias(true);
                roundedIcon.setFilterBitmap(true);

                tile.setIcon(roundedIcon);
                tile.setType(TileVisualType.ICON_REAL);
            }

            mObserver.onTileIconChanged(tile);
        }
    }

    private class TileInteractionDelegate
            implements ContextMenuManager.Delegate, OnClickListener, OnCreateContextMenuListener {
        private final String mUrl;

        public TileInteractionDelegate(String url) {
            mUrl = url;
        }

        @Override
        public void onClick(View view) {
            Tile tile = getTile(mUrl);
            if (tile == null) return;

            SuggestionsMetrics.recordTileTapped();
            mTileGroupDelegate.openMostVisitedItem(WindowOpenDisposition.CURRENT_TAB, tile);
        }

        @Override
        public void openItem(int windowDisposition) {
            Tile tile = getTile(mUrl);
            if (tile == null) return;

            mTileGroupDelegate.openMostVisitedItem(windowDisposition, tile);
        }

        @Override
        public void removeItem() {
            Tile tile = getTile(mUrl);
            if (tile == null) return;

            // Note: This does not track all the removals, but will track the most recent one. If
            // that removal is committed, it's good enough for change detection.
            mPendingRemovalUrl = mUrl;
            mTileGroupDelegate.removeMostVisitedItem(tile, new RemovalUndoneCallback());
        }

        @Override
        public String getUrl() {
            return mUrl;
        }

        @Override
        public boolean isItemSupported(@ContextMenuItemId int menuItemId) {
            return true;
        }

        @Override
        public void onContextMenuCreated() {}

        @Override
        public void onCreateContextMenu(
                ContextMenu contextMenu, View view, ContextMenuInfo contextMenuInfo) {
            mContextMenuManager.createContextMenu(contextMenu, view, this);
        }
    }

    private class RemovalUndoneCallback extends Callback<String> {
        @Override
        public void onResult(String restoredUrl) {
            mPendingInsertionUrl = restoredUrl;
        }
    }

    private class OfflineModelObserver extends SuggestionsOfflineModelObserver<Tile> {
        public OfflineModelObserver(OfflinePageBridge bridge) {
            super(bridge);
        }

        @Override
        public void onSuggestionOfflineIdChanged(Tile suggestion, @Nullable Long id) {
            // Retrieve a tile from the internal data, to make sure we don't update a stale object.
            Tile tile = getTile(suggestion.getUrl());
            if (tile == null) return;

            boolean oldOfflineAvailable = tile.isOfflineAvailable();
            tile.setOfflinePageOfflineId(id);

            // Only notify to update the view if there will be a visible change.
            if (oldOfflineAvailable == tile.isOfflineAvailable()) return;
            mObserver.onTileOfflineBadgeVisibilityChanged(tile);
        }

        @Override
        public Iterable<Tile> getOfflinableSuggestions() {
            return Arrays.asList(mTiles);
        }
    }
}

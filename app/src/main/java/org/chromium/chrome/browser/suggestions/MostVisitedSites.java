// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.suggestions;

import org.chromium.base.annotations.CalledByNative;

/**
 * Methods to provide most recent urls, titles and thumbnails.
 */
interface MostVisitedSites {
    /**
     * An interface for handling events in {@link MostVisitedSites}.
     */
    interface Observer {
        /**
         * This is called when the list of most visited URLs is initially available or updated.
         * Parameters guaranteed to be non-null.
         *
         * @param titles Array of most visited url page titles.
         * @param urls Array of most visited URLs, including popular URLs if
         *             available and necessary (i.e. there aren't enough most
         *             visited URLs).
         * @param whitelistIconPaths The paths to the icon image files for whitelisted tiles, empty
         *                           strings otherwise.
         * @param sources For each tile, the {@code TileSource} that generated the tile.
         */
        @CalledByNative("Observer")
        void onMostVisitedURLsAvailable(
                String[] titles, String[] urls, String[] whitelistIconPaths, int[] sources);

        /**
         * This is called when a previously uncached icon has been fetched.
         * Parameters guaranteed to be non-null.
         *
         * @param siteUrl URL of site with newly-cached icon.
         */
        @CalledByNative("Observer")
        void onIconMadeAvailable(String siteUrl);
    }

    /**
     * This instance must not be used after calling destroy().
     */
    void destroy();

    /**
     * Sets the recipient for events from {@link MostVisitedSites}. The observer may be notified
     * synchronously or asynchronously.
     * @param observer The observer to be notified.
     * @param numSites The maximum number of sites to return.
     */
    void setObserver(Observer observer, int numSites);

    /**
     * Blacklists a URL from the most visited URLs list.
     */
    void addBlacklistedUrl(String url);

    /**
     * Removes a URL from the most visited URLs blacklist.
     */
    void removeBlacklistedUrl(String url);

    /**
     * Records metrics about an impression, including the sources (local, server, ...) and visual
     * types of the tiles that are shown.
     * @param tileTypes An array of values from {@link TileVisualType} indicating the type of each
     *                  tile that's currently showing.
     * @param sources An array of values from {@link TileSource} indicating the source of each tile
     *                that's currently showing.
     * @param tileUrls An array of strings indicating the URL of each tile.
     */
    void recordPageImpression(int[] tileTypes, int[] sources, String[] tileUrls);

    /**
     * Records the opening of a Most Visited Item.
     * @param index The index of the item that was opened.
     * @param type The visual type of the item as defined in {@link TileVisualType}.
     * @param source The {@link TileSource} that generated this item.
     */
    void recordOpenedMostVisitedItem(int index, @TileVisualType int type, @TileSource int source);
}

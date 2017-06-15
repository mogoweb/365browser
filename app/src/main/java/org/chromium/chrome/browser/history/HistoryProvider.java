// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.history;

import java.util.List;

/**
 * Provides methods needed for querying and managing browsing history.
 */
public interface HistoryProvider {
    /**
     * Observer to be notified of browsing history events.
     */
    public interface BrowsingHistoryObserver {
        /**
         * Called after {@link BrowsingHistoryBridge#queryHistory(String, long)} is complete.
         * @param items The items that matched the #queryHistory() parameters.
         * @param hasMorePotentialMatches Whether there are more items that match the query text.
         *                                This will be false once the entire local history database
         *                                has been searched.
         */
        void onQueryHistoryComplete(List<HistoryItem> items,
                boolean hasMorePotentialMatches);

        /**
         * Called when history has been deleted through something other than a call to
         * BrowsingHistoryBridge#removeItems(). For example, if two instances of the history page
         * are open and the user removes items in one instance, the other instance will be notified
         * via this method.
         */
        void onHistoryDeleted();

        /**
         * Called after querying history to indicate whether other forms of browsing history were
         * found.
         * @param hasOtherForms Whether other forms of browsing history were found.
         * @param hasSyncedResults Whether synced results were found.
         */
        void hasOtherFormsOfBrowsingData(boolean hasOtherForms, boolean hasSyncedResults);
    }

    /**
     * Sets the {@link BrowsingHistoryObserver} to be notified of browsing history events.
     */
    void setObserver(BrowsingHistoryObserver observer);

    /**
     * Query browsing history. Only one query may be in-flight at any time. See
     * BrowsingHistoryService::QueryHistory.
     * @param query The query search text. May be empty.
     * @param endQueryTime The end of the time range to search. A value of 0 indicates that there
     *                     is no limit on the end time. See the native QueryOptions.
     */
    void queryHistory(String query, long endQueryTime);

    /**
     * Adds the HistoryItem to the list of items being removed. The removal will not be committed
     * until {@link #removeItems()} is called.
     * @param item The item to mark for removal.
     */
    void markItemForRemoval(HistoryItem item);

    /**
     * Removes all items that have been marked for removal through #markItemForRemoval().
     */
    void removeItems();

    /**
     * Destroys the HistoryProvider.
     */
    void destroy();
}

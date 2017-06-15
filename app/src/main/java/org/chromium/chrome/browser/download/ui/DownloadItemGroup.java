// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download.ui;

import org.chromium.chrome.browser.download.ui.DownloadHistoryAdapter.SubsectionHeader;
import org.chromium.chrome.browser.download.ui.DownloadHistoryItemWrapper.OfflinePageItemWrapper;
import org.chromium.chrome.browser.widget.DateDividedAdapter;
import org.chromium.chrome.browser.widget.DateDividedAdapter.TimedItem;

/**
 * A bucket of downloaded items with the same date. It also holds the suggested offline items which
 * are shown at the end of the list for that date.
 */
public class DownloadItemGroup extends DateDividedAdapter.ItemGroup {
    public DownloadItemGroup(long timestamp) {
        super(timestamp);
    }

    private boolean isSuggestedOfflinePage(TimedItem timedItem) {
        if (timedItem instanceof OfflinePageItemWrapper) {
            return ((OfflinePageItemWrapper) timedItem).isSuggested();
        }

        return false;
    }

    @Override
    public int compareItem(TimedItem lhs, TimedItem rhs) {
        int lhsOrder = order(lhs);
        int rhsOrder = order(rhs);

        if (lhsOrder != rhsOrder) {
            return lhsOrder < rhsOrder ? -1 : 1;
        }

        return super.compareItem(lhs, rhs);
    }

    /**
     * A sorting helper function based on the item type. The items in the list view are placed from
     * the top in the following order: download items, suggested pages header, suggested pages.
     * @param timedItem The item to be displayed.
     * @return An integer based on the item type which is to be used for sorting.
     */
    private int order(TimedItem timedItem) {
        if (isSuggestedOfflinePage(timedItem)) {
            return 2;
        } else if (timedItem instanceof SubsectionHeader) {
            return 1;
        } else {
            return 0;
        }
    }
}

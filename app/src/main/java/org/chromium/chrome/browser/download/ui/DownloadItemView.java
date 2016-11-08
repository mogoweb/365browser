// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download.ui;

import android.content.Context;
import android.util.AttributeSet;

import org.chromium.chrome.browser.widget.selection.SelectableItemView;

/**
 * The view for a downloaded item displayed in the Downloads list.
 */
public class DownloadItemView extends SelectableItemView<DownloadHistoryItemWrapper> {
    DownloadHistoryItemWrapper mItem;

    /**
     * Constructor for inflating from XML.
     */
    public DownloadItemView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * Initialize the DownloadItemView. Must be called before the item can respond to click events.
     *
     * @param item The item represented by this DownloadItemView.
     */
    public void initialize(DownloadHistoryItemWrapper item) {
        mItem = item;
        setItem(item);
    }

    @Override
    public void onClick() {
        if (mItem != null) mItem.open();
    }
}

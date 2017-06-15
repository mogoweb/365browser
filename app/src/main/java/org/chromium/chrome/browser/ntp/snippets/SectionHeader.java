// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp.snippets;

import org.chromium.chrome.browser.ntp.cards.ItemViewType;
import org.chromium.chrome.browser.ntp.cards.NewTabPageViewHolder;
import org.chromium.chrome.browser.ntp.cards.NodeVisitor;
import org.chromium.chrome.browser.ntp.cards.OptionalLeaf;

/**
 * Represents the data for a header of a group of snippets.
 */
public class SectionHeader extends OptionalLeaf {
    /** The header text to be shown. */
    private final String mHeaderText;

    public SectionHeader(String headerText) {
        this.mHeaderText = headerText;
        setVisible(true);
    }

    @Override
    @ItemViewType
    public int getItemViewType() {
        return ItemViewType.HEADER;
    }

    public String getHeaderText() {
        return mHeaderText;
    }

    @Override
    protected void onBindViewHolder(NewTabPageViewHolder holder) {
        assert holder instanceof SectionHeaderViewHolder;
        ((SectionHeaderViewHolder) holder).onBindViewHolder(this);
    }

    @Override
    public void visitOptionalItem(NodeVisitor visitor) {
        visitor.visitHeader();
    }
}

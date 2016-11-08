// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp.cards;

import java.util.List;

/**
 * A group of items.
 */
public interface ItemGroup {
    /**
     * @return A list of items contained in this group. The list should not be modified.
     */
    List<NewTabPageItem> getItems();
}

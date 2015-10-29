// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.bottombar.contextualsearch;

import org.chromium.chrome.browser.customtabs.CustomTab;

/**
 * A utility class meant to determine whether certain features are available in the Search Panel.
 */
public class ContextualSearchPanelFeatures {
    private boolean mIsCustomTab;

    /**
     * @param isCustomTab Whether the current activity contains a {@link CustomTab}.
     */
    public ContextualSearchPanelFeatures(boolean isCustomTab) {
        mIsCustomTab = isCustomTab;
    }

    /**
     * @return {@code true} Whether the side search icon is available.
     */
    public boolean isSearchIconAvailable() {
        return !mIsCustomTab;
    }

    /**
     * @return {@code true} Whether search term refining is available.
     */
    public boolean isSearchTermRefiningAvailable() {
        return !mIsCustomTab;
    }

    /**
     * @return {@code true} Whether the close button is available.
     */
    public boolean isCloseButtonAvailable() {
        return mIsCustomTab;
    }

    /**
     * @return {@code true} Whether the close animation should run when the the panel is closed
     *                      due the panel being promoted to a tab.
     */
    public boolean shouldAnimatePanelCloseOnPromoteToTab() {
        return mIsCustomTab;
    }
}

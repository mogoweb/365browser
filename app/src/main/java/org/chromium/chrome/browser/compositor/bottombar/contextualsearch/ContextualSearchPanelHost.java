// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.bottombar.contextualsearch;

/**
 * Interface that allows {@link ContextualSearchPanel} to communicate with its host Layout.
 */
public interface ContextualSearchPanelHost {
    /**
     * Hides the Contextual Search Supported Layout.
     * @param immediately Whether it should be hidden immediately.
     */
    void hideLayout(boolean immediately);
}

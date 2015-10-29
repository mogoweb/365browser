// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.contextmenu;

import android.content.Context;
import android.view.ContextMenu;

/**
 * A simple wrapper around a {@link ContextMenuPopulator} to handle delegating calls to another
 * populator while allowing overriding of specific methods.
 */
public class ContextMenuPopulatorWrapper implements ContextMenuPopulator {
    private final ContextMenuPopulator mPopulator;

    /**
     * Constructs an instance of a {@link ContextMenuPopulator} and delegate calls to
     * {@code populator}.
     * @param populator The {@link ContextMenuPopulator} to delegate calls to.
     */
    public ContextMenuPopulatorWrapper(ContextMenuPopulator populator) {
        mPopulator = populator;
    }

    @Override
    public boolean shouldShowContextMenu(ContextMenuParams params) {
        return mPopulator.shouldShowContextMenu(params);
    }

    @Override
    public void buildContextMenu(ContextMenu menu, Context context, ContextMenuParams params) {
        mPopulator.buildContextMenu(menu, context, params);
    }

    @Override
    public boolean onItemSelected(ContextMenuHelper helper, ContextMenuParams params, int itemId) {
        return mPopulator.onItemSelected(helper, params, itemId);
    }
}
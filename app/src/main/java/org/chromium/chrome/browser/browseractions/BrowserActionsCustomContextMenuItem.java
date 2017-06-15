// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.browseractions;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.support.annotation.IdRes;
import android.support.customtabs.browseractions.BrowserActionItem;

import org.chromium.chrome.browser.contextmenu.ContextMenuItem;

/**
 * A class represents Browser Actions context menu with custom title and icon.
 */
public class BrowserActionsCustomContextMenuItem implements ContextMenuItem {
    @IdRes
    private final int mMenuId;
    private final String mTitle;
    private final Bitmap mIcon;

    /**
     * Constructor to build a custom context menu item from {@link BrowserActionItem}.
     * @param id The {@link IdRes} of the custom context menu item.
     * @param item The {@link BrowserActionItem} specifies the title and action of the menu item.
     */
    BrowserActionsCustomContextMenuItem(@IdRes int id, BrowserActionItem item) {
        mMenuId = id;
        mTitle = item.getTitle();
        mIcon = item.getIcon();
    }

    @Override
    public int getMenuId() {
        return mMenuId;
    }

    @Override
    public String getTitle(Context context) {
        return mTitle;
    }

    @Override
    public Drawable getDrawable(Context context) {
        return new BitmapDrawable(context.getResources(), mIcon);
    }
}
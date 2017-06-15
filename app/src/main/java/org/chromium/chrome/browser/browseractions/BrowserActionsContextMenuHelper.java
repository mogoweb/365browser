// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.browseractions;

import android.app.PendingIntent;
import android.support.customtabs.browseractions.BrowserActionItem;
import android.support.customtabs.browseractions.BrowserActionsIntent;
import android.util.Pair;
import android.util.SparseArray;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View;
import android.view.View.OnAttachStateChangeListener;
import android.view.View.OnCreateContextMenuListener;

import org.chromium.base.Callback;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.contextmenu.ChromeContextMenuItem;
import org.chromium.chrome.browser.contextmenu.ContextMenuItem;
import org.chromium.chrome.browser.contextmenu.ContextMenuParams;
import org.chromium.chrome.browser.contextmenu.ContextMenuUi;
import org.chromium.chrome.browser.contextmenu.PlatformContextMenuUi;
import org.chromium.chrome.browser.contextmenu.TabularContextMenuUi;
import org.chromium.ui.base.WindowAndroid.OnCloseContextMenuListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A helper class that handles generating context menus for Browser Actions.
 */
public class BrowserActionsContextMenuHelper implements OnCreateContextMenuListener,
                                                        OnCloseContextMenuListener,
                                                        OnAttachStateChangeListener {
    private static final boolean IS_NEW_UI_ENABLED = true;

    // Items list that could be included in the Browser Actions context menu for type {@code LINK}.
    private static final List<? extends ContextMenuItem> BROWSER_ACTIONS_LINK_GROUP =
            Arrays.asList(ChromeContextMenuItem.BROWSER_ACTIONS_OPEN_IN_BACKGROUND,
                    ChromeContextMenuItem.BROWSER_ACTIONS_OPEN_IN_INCOGNITO_TAB,
                    ChromeContextMenuItem.BROWSER_ACTION_SAVE_LINK_AS,
                    ChromeContextMenuItem.BROWSER_ACTIONS_COPY_ADDRESS,
                    ChromeContextMenuItem.BROWSER_ACTIONS_SHARE);

    private static final List<Integer> CUSTOM_BROWSER_ACTIONS_ID_GROUP =
            Arrays.asList(R.id.browser_actions_custom_item_one,
                    R.id.browser_actions_custom_item_two, R.id.browser_actions_custom_item_three,
                    R.id.browser_actions_custom_item_four, R.id.browser_actions_custom_item_five);

    // Map each custom item's id with its PendingIntent action.
    private final SparseArray<PendingIntent> mCustomItemActionMap = new SparseArray<>();

    private final ContextMenuParams mCurrentContextMenuParams;
    private final BrowserActionsContextMenuItemDelegate mDelegate;
    private final BrowserActionActivity mActivity;
    private final Callback<Integer> mItemSelectedCallback;
    private final Runnable mOnMenuShown;
    private final Runnable mOnMenuClosed;
    private final Runnable mOnShareClickedRunnable;

    private final List<Pair<Integer, List<ContextMenuItem>>> mItems;

    public BrowserActionsContextMenuHelper(BrowserActionActivity activity, ContextMenuParams params,
            List<BrowserActionItem> customItems) {
        mActivity = activity;
        mCurrentContextMenuParams = params;
        mOnMenuShown = new Runnable() {
            @Override
            public void run() {
                mActivity.onMenuShown();
            }
        };
        mOnMenuClosed = new Runnable() {
            @Override
            public void run() {
                mActivity.finish();
            }
        };
        mItemSelectedCallback = new Callback<Integer>() {
            @Override
            public void onResult(Integer result) {
                onItemSelected(result);
            }
        };
        mOnShareClickedRunnable = new Runnable() {
            @Override
            public void run() {}
        };
        mDelegate = new BrowserActionsContextMenuItemDelegate();
        mItems = buildContextMenuItems(customItems);
    }

    /**
     * Builds items for Browser Actions context menu.
     */
    private List<Pair<Integer, List<ContextMenuItem>>> buildContextMenuItems(
            List<BrowserActionItem> customItems) {
        List<Pair<Integer, List<ContextMenuItem>>> menuItems = new ArrayList<>();
        List<ContextMenuItem> items = new ArrayList<>();
        items.addAll(BROWSER_ACTIONS_LINK_GROUP);
        addBrowserActionItems(items, customItems);

        menuItems.add(new Pair<>(R.string.contextmenu_link_title, items));
        return menuItems;
    }

    /**
     * Adds custom items to the context menu list and populates custom item action map.
     * @param items List of {@link ContextMenuItem} to display the context menu.
     * @param customItems List of {@link BrowserActionItem} for custom items.
     */
    private void addBrowserActionItems(
            List<ContextMenuItem> items, List<BrowserActionItem> customItems) {
        for (int i = 0; i < customItems.size() && i < BrowserActionsIntent.MAX_CUSTOM_ITEMS; i++) {
            items.add(new BrowserActionsCustomContextMenuItem(
                    CUSTOM_BROWSER_ACTIONS_ID_GROUP.get(i), customItems.get(i)));
            mCustomItemActionMap.put(
                    CUSTOM_BROWSER_ACTIONS_ID_GROUP.get(i), customItems.get(i).getAction());
        }
    }

    private boolean onItemSelected(int itemId) {
        if (itemId == R.id.browser_actions_open_in_background) {
            mDelegate.onOpenInBackground(mCurrentContextMenuParams.getLinkUrl());
        } else if (itemId == R.id.browser_actions_open_in_incognito_tab) {
            mDelegate.onOpenInIncognitoTab(mCurrentContextMenuParams.getLinkUrl());
        } else if (itemId == R.id.browser_actions_save_link_as) {
            mDelegate.startDownload(mCurrentContextMenuParams.getLinkUrl());
        } else if (itemId == R.id.browser_actions_copy_address) {
            mDelegate.onSaveToClipboard(mCurrentContextMenuParams.getLinkUrl());
        } else if (itemId == R.id.browser_actions_share) {
            mDelegate.share(mCurrentContextMenuParams.getLinkUrl());
        } else if (mCustomItemActionMap.indexOfKey(itemId) >= 0) {
            mDelegate.onCustomItemSelected(mCustomItemActionMap.get(itemId));
        }
        return true;
    }

    /**
     * Displays the Browser Actions context menu.
     * @param view The view to show the context menu if old UI is used.
     */
    public void displayBrowserActionsMenu(final View view) {
        if (IS_NEW_UI_ENABLED) {
            ContextMenuUi menuUi = new TabularContextMenuUi(mOnShareClickedRunnable);
            menuUi.displayMenu(mActivity, mCurrentContextMenuParams, mItems, mItemSelectedCallback,
                    mOnMenuShown, mOnMenuClosed);
        } else {
            view.setOnCreateContextMenuListener(BrowserActionsContextMenuHelper.this);
            assert view.getWindowToken() == null;
            view.addOnAttachStateChangeListener(this);
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        ContextMenuUi menuUi = new PlatformContextMenuUi(menu);
        menuUi.displayMenu(mActivity, mCurrentContextMenuParams, mItems, mItemSelectedCallback,
                mOnMenuShown, mOnMenuClosed);
    }

    @Override
    public void onContextMenuClosed() {
        mOnMenuClosed.run();
    }

    @Override
    public void onViewAttachedToWindow(View view) {
        if (view.showContextMenu()) {
            mOnMenuShown.run();
        }
    }

    @Override
    public void onViewDetachedFromWindow(View v) {}
}

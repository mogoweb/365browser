// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.appmenu;

import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.bookmarks.BookmarkBridge;
import org.chromium.chrome.browser.download.DownloadUtils;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.util.AccessibilityUtil;
import org.chromium.chrome.browser.widget.TintedImageButton;

/**
 * A {@link LinearLayout} that displays a horizontal row of icons for page actions.
 */
public class AppMenuIconRowFooter
        extends LinearLayout implements View.OnClickListener, View.OnLongClickListener {
    private ChromeActivity mActivity;
    private AppMenu mAppMenu;

    private TintedImageButton mForwardButton;
    private TintedImageButton mBookmarkButton;
    private TintedImageButton mDownloadButton;
    private TintedImageButton mPageInfoButton;
    private TintedImageButton mReloadButton;

    public AppMenuIconRowFooter(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mForwardButton = (TintedImageButton) findViewById(R.id.forward_menu_id);
        mForwardButton.setOnClickListener(this);
        mForwardButton.setOnLongClickListener(this);

        mBookmarkButton = (TintedImageButton) findViewById(R.id.bookmark_this_page_id);
        mBookmarkButton.setOnClickListener(this);
        mBookmarkButton.setOnLongClickListener(this);

        mDownloadButton = (TintedImageButton) findViewById(R.id.offline_page_id);
        mDownloadButton.setOnClickListener(this);
        mDownloadButton.setOnLongClickListener(this);

        mPageInfoButton = (TintedImageButton) findViewById(R.id.info_menu_id);
        mPageInfoButton.setOnClickListener(this);
        mPageInfoButton.setOnLongClickListener(this);

        mReloadButton = (TintedImageButton) findViewById(R.id.reload_menu_id);
        mReloadButton.setOnClickListener(this);
        mReloadButton.setOnLongClickListener(this);
    }

    /**
     * Initializes the icons, setting enabled state, drawables, and content descriptions.
     * @param activity The {@link ChromeActivity} displaying the menu.
     * @param appMenu The {@link AppMenu} that contains the icon row.
     * @param bookmarkBridge The {@link BookmarkBridge} used to retrieve information about
     *                       bookmarks.
     */
    public void initialize(
            ChromeActivity activity, AppMenu appMenu, BookmarkBridge bookmarkBridge) {
        mActivity = activity;
        mAppMenu = appMenu;
        Tab currentTab = mActivity.getActivityTab();

        mForwardButton.setEnabled(currentTab.canGoForward());

        updateBookmarkMenuItem(bookmarkBridge, currentTab);

        mDownloadButton.setEnabled(DownloadUtils.isAllowedToDownloadPage(currentTab));

        mReloadButton.setImageResource(R.drawable.btn_reload_stop);
        loadingStateChanged(currentTab.isLoading());
    }

    @Override
    public void onClick(View v) {
        mActivity.onMenuOrKeyboardAction(v.getId(), true);
        mAppMenu.dismiss();
    }

    @Override
    public boolean onLongClick(View v) {
        String description = null;
        Context context = getContext();
        Resources resources = context.getResources();
        final int itemId = v.getId();

        if (itemId == R.id.forward_menu_id) {
            description = resources.getString(R.string.menu_forward);
        } else if (itemId == R.id.bookmark_this_page_id) {
            description = resources.getString(R.string.menu_bookmark);
        } else if (itemId == R.id.offline_page_id) {
            description = resources.getString(R.string.menu_download);
        } else if (itemId == R.id.info_menu_id) {
            description = resources.getString(R.string.menu_page_info);
        } else if (itemId == R.id.reload_menu_id) {
            description = resources.getString(R.string.menu_refresh);
        }
        return AccessibilityUtil.showAccessibilityToast(context, v, description);
    }

    /**
     * Called when the current tab's load state  has changed.
     * @param isLoading Whether the tab is currently loading.
     */
    public void loadingStateChanged(boolean isLoading) {
        mReloadButton.getDrawable().setLevel(isLoading
                        ? AppMenuPropertiesDelegate.RELOAD_BUTTON_LEVEL_STOP_LOADING
                        : AppMenuPropertiesDelegate.RELOAD_BUTTON_LEVEL_RELOAD);
        mReloadButton.setContentDescription(isLoading
                        ? mActivity.getString(R.string.accessibility_btn_stop_loading)
                        : mActivity.getString(R.string.accessibility_btn_refresh));
    }

    private void updateBookmarkMenuItem(BookmarkBridge bookmarkBridge, Tab currentTab) {
        mBookmarkButton.setEnabled(bookmarkBridge.isEditBookmarksEnabled());

        if (currentTab.getBookmarkId() != Tab.INVALID_BOOKMARK_ID) {
            mBookmarkButton.setImageResource(R.drawable.btn_star_filled);
            mBookmarkButton.setContentDescription(mActivity.getString(R.string.edit_bookmark));
            mBookmarkButton.setTint(ApiCompatibilityUtils.getColorStateList(
                    getResources(), R.color.blue_mode_tint));
        } else {
            mBookmarkButton.setImageResource(R.drawable.btn_star);
            mBookmarkButton.setContentDescription(
                    mActivity.getString(R.string.accessibility_menu_bookmark));
        }
    }

    TintedImageButton getForwardButtonForTests() {
        return mForwardButton;
    }

    TintedImageButton getBookmarkButtonForTests() {
        return mBookmarkButton;
    }

    TintedImageButton getDownloadButtonForTests() {
        return mDownloadButton;
    }

    TintedImageButton getPageInfoButtonForTests() {
        return mPageInfoButton;
    }

    TintedImageButton getReloadButtonForTests() {
        return mReloadButton;
    }
}

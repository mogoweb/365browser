// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.customtabs;

import android.view.Menu;
import android.view.MenuItem;

import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.DefaultBrowserInfo;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.chrome.browser.appmenu.AppMenuPropertiesDelegate;
import org.chromium.chrome.browser.download.DownloadUtils;
import org.chromium.chrome.browser.firstrun.FirstRunStatus;
import org.chromium.chrome.browser.share.ShareHelper;
import org.chromium.chrome.browser.tab.Tab;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * App menu properties delegate for {@link CustomTabActivity}.
 */
public class CustomTabAppMenuPropertiesDelegate extends AppMenuPropertiesDelegate {
    private static final String SAMPLE_URL = "https://www.google.com";

    private final boolean mShowShare;
    private final boolean mIsMediaViewer;
    private final boolean mShowStar;
    private final boolean mShowDownload;
    private final boolean mIsOpenedByChrome;

    private final List<String> mMenuEntries;
    private final Map<MenuItem, Integer> mItemToIndexMap = new HashMap<MenuItem, Integer>();

    private boolean mIsCustomEntryAdded;

    /**
     * Creates an {@link CustomTabAppMenuPropertiesDelegate} instance.
     */
    public CustomTabAppMenuPropertiesDelegate(final ChromeActivity activity,
            List<String> menuEntries, boolean showShare, final boolean isOpenedByChrome,
            final boolean isMediaViewer, boolean showStar, boolean showDownload) {
        super(activity);
        mMenuEntries = menuEntries;
        mShowShare = showShare;
        mIsMediaViewer = isMediaViewer;
        mShowStar = showStar;
        mShowDownload = showDownload;
        mIsOpenedByChrome = isOpenedByChrome;
    }

    @Override
    public void prepareMenu(Menu menu) {
        Tab currentTab = mActivity.getActivityTab();
        if (currentTab != null) {
            MenuItem forwardMenuItem = menu.findItem(R.id.forward_menu_id);
            forwardMenuItem.setEnabled(currentTab.canGoForward());

            mReloadMenuItem = menu.findItem(R.id.reload_menu_id);
            mReloadMenuItem.setIcon(R.drawable.btn_reload_stop);
            loadingStateChanged(currentTab.isLoading());

            MenuItem shareItem = menu.findItem(R.id.share_row_menu_id);
            shareItem.setVisible(mShowShare);
            shareItem.setEnabled(mShowShare);
            if (mShowShare) {
                ShareHelper.configureDirectShareMenuItem(
                        mActivity, menu.findItem(R.id.direct_share_menu_id));
            }

            MenuItem iconRow = menu.findItem(R.id.icon_row_menu_id);
            MenuItem openInChromeItem = menu.findItem(R.id.open_in_browser_id);
            MenuItem bookmarkItem = menu.findItem(R.id.bookmark_this_page_id);
            MenuItem downloadItem = menu.findItem(R.id.offline_page_id);

            boolean addToHomeScreenVisible = true;

            // Hide request desktop site on all chrome:// pages except for the NTP. Check request
            // desktop site if it's activated on this page.
            MenuItem requestItem = menu.findItem(R.id.request_desktop_site_id);
            updateRequestDesktopSiteMenuItem(requestItem, currentTab);

            if (mIsMediaViewer) {
                // Most of the menu items don't make sense when viewing media.
                iconRow.setVisible(false);
                openInChromeItem.setVisible(false);
                menu.findItem(R.id.find_in_page_id).setVisible(false);
                menu.findItem(R.id.request_desktop_site_id).setVisible(false);
                addToHomeScreenVisible = false;
            } else {
                openInChromeItem.setTitle(
                        DefaultBrowserInfo.getTitleOpenInDefaultBrowser(mIsOpenedByChrome));
                updateBookmarkMenuItem(bookmarkItem, currentTab);
            }
            bookmarkItem.setVisible(mShowStar);
            downloadItem.setVisible(mShowDownload);
            if (!FirstRunStatus.getFirstRunFlowComplete()) {
                openInChromeItem.setVisible(false);
                bookmarkItem.setVisible(false);
                downloadItem.setVisible(false);
                addToHomeScreenVisible = false;
            }

            downloadItem.setEnabled(DownloadUtils.isAllowedToDownloadPage(currentTab));

            String url = currentTab.getUrl();
            boolean isChromeScheme = url.startsWith(UrlConstants.CHROME_URL_PREFIX)
                    || url.startsWith(UrlConstants.CHROME_NATIVE_URL_PREFIX);
            if (isChromeScheme) {
                addToHomeScreenVisible = false;
            }

            // Add custom menu items. Make sure they are only added once.
            if (!mIsCustomEntryAdded) {
                mIsCustomEntryAdded = true;
                for (int i = 0; i < mMenuEntries.size(); i++) {
                    MenuItem item = menu.add(0, 0, 1, mMenuEntries.get(i));
                    mItemToIndexMap.put(item, i);
                }
            }

            prepareAddToHomescreenMenuItem(menu, currentTab, addToHomeScreenVisible);
        }
    }

    /**
     * @return The index that the given menu item should appear in the result of
     *         {@link CustomTabIntentDataProvider#getMenuTitles()}. Returns -1 if item not found.
     */
    public int getIndexOfMenuItem(MenuItem menuItem) {
        if (!mItemToIndexMap.containsKey(menuItem)) {
            return -1;
        }
        return mItemToIndexMap.get(menuItem).intValue();
    }

    @Override
    public int getFooterResourceId() {
        return mIsMediaViewer ? 0 : R.layout.powered_by_chrome_footer;
    }

    /**
     * Get the {@link MenuItem} object associated with the given title. If multiple menu items have
     * the same title, a random one will be returned. This method is for testing purpose _only_.
     */
    @VisibleForTesting
    MenuItem getMenuItemForTitle(String title) {
        for (MenuItem item : mItemToIndexMap.keySet()) {
            if (item.getTitle().equals(title)) return item;
        }
        return null;
    }
}

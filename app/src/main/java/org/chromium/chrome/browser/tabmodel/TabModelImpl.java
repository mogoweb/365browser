// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tabmodel;

import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.compositor.layouts.content.TabContentManager;
import org.chromium.chrome.browser.device.DeviceClassManager;
import org.chromium.chrome.browser.partnercustomizations.HomepageManager;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.content_public.browser.WebContents;

/**
 * This class contains additions to the base TabModel implementation (notifications
 * and overrides of existing methods).
 *
 * TODO: merge this into TabModelBase after upstreaming.
 */
public class TabModelImpl extends TabModelBase {
    /**
     * The application ID used for tabs opened from an application that does not specify an app ID
     * in its VIEW intent extras.
     */
    public static final String UNKNOWN_APP_ID = "com.google.android.apps.chrome.unknown_app";

    private final ChromeActivity mActivity;
    private final TabModelSelectorUma mUma;
    private final TabContentManager mTabContentManager;
    private final TabPersistentStore mTabSaver;

    public TabModelImpl(boolean incognito,
            ChromeActivity activity, TabModelSelectorUma uma,
            TabModelOrderController orderController, TabContentManager tabContentManager,
            TabPersistentStore tabSaver, TabModelDelegate modelDelegate) {
        super(incognito, orderController, modelDelegate);
        mActivity = activity;
        mUma = uma;
        mTabContentManager = tabContentManager;
        mTabSaver = tabSaver;
        addObserver(mObserver);
    }

    private final TabModelObserver mObserver = new EmptyTabModelObserver() {
        @Override
        public void didSelectTab(Tab tab, TabSelectionType type, int lastId) {
            boolean wasAlreadySelected = tab.getId() == lastId;

            // TODO(joth): Make this even faster, ChromeTab.show() can still block for over 50ms.
            // See http://b/5278198 and http://b/5035061
            if (!wasAlreadySelected) {
                if (type == TabSelectionType.FROM_USER) {
                    // We only want to record when the user actively switches to a different tab.
                    mUma.userSwitchedToTab();
                }
            }
        }

        @Override
        public void didCloseTab(Tab tab) {
            mTabContentManager.removeTabThumbnail(tab.getId());
            mTabSaver.removeTabFromQueues(tab);

            if (!isIncognito()) tab.createHistoricalTab();
        }
    };

    @Override
    public boolean supportsPendingClosures() {
        return super.supportsPendingClosures() && DeviceClassManager.enableUndo(mActivity);
    }

    @Override
    public void closeAllTabs(boolean allowDelegation, boolean uponExit) {
        mTabSaver.cancelLoadingTabs(isIncognito());

        if (uponExit) {
            super.closeAllTabs(allowDelegation, uponExit);
            return;
        }

        if (allowDelegation && mModelDelegate.closeAllTabsRequest(isIncognito())) return;

        if (HomepageManager.isHomepageEnabled(mActivity)) {
            super.closeAllTabs(false, uponExit);
            return;
        }

        if (getCount() == 1) {
            closeTab(getTabAt(0), true, false, true);
            return;
        }

        super.closeAllTabs(true, false, true);
    }

    @Override
    protected boolean createTabWithWebContents(boolean incognito, WebContents webContents,
            int parentId) {
        return mActivity.getTabCreator(incognito).createTabWithWebContents(webContents,
                parentId, TabLaunchType.FROM_LONGPRESS_BACKGROUND);
    }

    @Override
    protected Tab createNewTabForDevTools(String url) {
        return mActivity.getTabCreator(false).createNewTab(new LoadUrlParams(url),
                TabModel.TabLaunchType.FROM_MENU_OR_OVERVIEW, null);
    }
}

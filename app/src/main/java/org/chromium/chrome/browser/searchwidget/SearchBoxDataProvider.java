// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.searchwidget;

import org.chromium.base.library_loader.LibraryLoader;
import org.chromium.chrome.browser.ntp.NewTabPage;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.toolbar.ToolbarDataProvider;

class SearchBoxDataProvider implements ToolbarDataProvider {
    private Tab mTab;

    /**
     * Called when native library is loaded and a tab has been initialized.
     * @param tab The tab to use.
     */
    public void onNativeLibraryReady(Tab tab) {
        assert LibraryLoader.isInitialized();
        mTab = tab;
    }

    @Override
    public boolean isUsingBrandColor() {
        return false;
    }

    @Override
    public boolean isIncognito() {
        if (mTab == null) return false;
        return mTab.isIncognito();
    }

    @Override
    public Profile getProfile() {
        if (mTab == null) return null;
        return mTab.getProfile();
    }

    @Override
    public String getText() {
        return null;
    }

    @Override
    public Tab getTab() {
        return mTab;
    }

    @Override
    public int getPrimaryColor() {
        return 0;
    }

    @Override
    public NewTabPage getNewTabPageForCurrentTab() {
        return null;
    }

    @Override
    public String getCurrentUrl() {
        return SearchWidgetProvider.getDefaultSearchEngineUrl();
    }
}

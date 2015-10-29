// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.bookmark;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.ViewGroup;

import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ShortcutHelper;
import org.chromium.chrome.browser.ShortcutSource;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.chrome.browser.favicon.FaviconHelper;
import org.chromium.chrome.browser.init.AsyncInitializationActivity;
import org.chromium.chrome.browser.ntp.BookmarksPage;
import org.chromium.chrome.browser.ntp.BookmarksPage.BookmarkSelectedListener;
import org.chromium.chrome.browser.partnerbookmarks.PartnerBookmarksShim;
import org.chromium.chrome.browser.profiles.Profile;

/**
 * Activity that allows the user to select a bookmark to add to their homescreen as a shortcut.
 */
public class ShortcutActivity extends AsyncInitializationActivity implements
        BookmarkSelectedListener {
    private BookmarksPage mBookmarksPage;

    @Override
    protected void setContentView() {
        // We can't show anything until the native library is loaded.
    }

    @Override
    public void postInflationStartup() {
        setTitle(getResources().getString(R.string.bookmark_shortcut_choose_bookmark));
    }

    @Override
    public void initializeState() {
        super.initializeState();

        // Partner bookmarks need to be loaded explicitly.
        PartnerBookmarksShim.kickOffReading(this);

        Profile profile = Profile.getLastUsedProfile();
        mBookmarksPage = BookmarksPage.buildPageInSelectBookmarkMode(this, profile, this);
        mBookmarksPage.updateForUrl(UrlConstants.BOOKMARKS_URL);

        setContentView(mBookmarksPage.getView(), new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mBookmarksPage != null) {
            mBookmarksPage.destroy();
            mBookmarksPage = null;
        }
    }

    // BookmarkSelectedListener implementation

    @Override
    public void onNewTabOpened() {
    }

    @Override
    public void onBookmarkSelected(String url, String title, Bitmap favicon) {
        int dominantColor = FaviconHelper.getDominantColorForBitmap(favicon);
        Bitmap launcherIcon = ShortcutHelper.createLauncherIcon(this, favicon, url,
                Color.red(dominantColor), Color.green(dominantColor), Color.blue(dominantColor));
        Intent intent = ShortcutHelper.createAddToHomeIntent(url, title, launcherIcon);
        intent.putExtra(ShortcutHelper.EXTRA_SOURCE, ShortcutSource.BOOKMARK_SHORTCUT_WIDGET);
        setResult(RESULT_OK, intent);
        RecordUserAction.record("BookmarkShortcutWidgetAdded");
        finish();
    }

    @Override
    public void onResumeWithNative() { }

    @Override
    public void onPauseWithNative() { }

    @Override
    public void onStopWithNative() { }
}

// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.bookmarks;

import android.app.Activity;
import android.graphics.Canvas;
import android.support.v4.widget.DrawerLayout;
import android.view.View;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.BasicNativePage;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.chrome.browser.compositor.layouts.content.InvalidationAwareThumbnailProvider;
import org.chromium.chrome.browser.tab.Tab;


/**
 * A native page holding a {@link BookmarkManager} on _tablet_.
 */
public class BookmarkPage extends BasicNativePage 
        implements InvalidationAwareThumbnailProvider, DrawerLayout.DrawerListener,
        View.OnAttachStateChangeListener{
    private BookmarkManager mManager;
    private String mTitle;
    private boolean mNeedsCapture = true;

    /**
     * Create a new instance of the bookmarks page.
     * @param activity The activity to get context and manage fragments.
     * @param tab The tab to load urls.
     */
    public BookmarkPage(Activity activity, Tab tab) {
        super(activity, tab);
    }

    @Override
    protected void initialize(Activity activity, Tab tab) {
        mManager = new BookmarkManager(activity, false);
        mManager.setBasicNativePage(this);
        if (mManager.doesDrawerExist()) mManager.addDrawerListener(this);
        mManager.addSearchViewStateListener(this);
        mTitle = activity.getString(R.string.bookmarks);
    }

    @Override
    public View getView() {
        return mManager.getView();
    }

    @Override
    public String getTitle() {
        return mTitle;
    }

    @Override
    public String getHost() {
        return UrlConstants.BOOKMARKS_HOST;
    }

    @Override
    public void updateForUrl(String url) {
        super.updateForUrl(url);
        mManager.updateForUrl(url);
    }

    @Override
    public void destroy() {
        if (mManager.doesDrawerExist()) mManager.removeDrawerListener(this);
        mManager.removeSearchViewStateListener(this);
        mManager.destroy();
        mManager = null;
        super.destroy();
    }

    @Override
    public boolean shouldCaptureThumbnail() {
        return mNeedsCapture;
    }

    @Override
    public void captureThumbnail(Canvas canvas) {
        //BrowserNewTabPage will handle this
    }

    public void onExternalCapture() {
        mNeedsCapture = false;
    }

    @Override
    public void onDrawerStateChanged(int newState) {
        mNeedsCapture = true;
    }

    @Override
    public void onViewAttachedToWindow(View v) {
        mNeedsCapture = true;
    }

    @Override
    public void onViewDetachedFromWindow(View v) {
        mNeedsCapture = true;
    }

    @Override
    public void onDrawerSlide(View drawerView, float slideOffset) {

    }

    @Override
    public void onDrawerOpened(View drawerView) {

    }

    @Override
    public void onDrawerClosed(View drawerView) {

    }
}

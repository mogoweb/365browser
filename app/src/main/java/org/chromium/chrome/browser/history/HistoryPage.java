// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.history;

import android.app.Activity;
import android.view.View;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.BasicNativePage;
import org.chromium.chrome.browser.NativePageHost;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.chrome.browser.snackbar.SnackbarManager.SnackbarManageable;

/**
 * Native page for managing browsing history.
 */
public class HistoryPage extends BasicNativePage {
    private HistoryManager mHistoryManager;
    private String mTitle;

    /**
     * Create a new instance of the history page.
     * @param activity The {@link Activity} used to get context and instantiate the
     *                 {@link HistoryManager}.
     * @param host A NativePageHost to load URLs.
     */
    public HistoryPage(Activity activity, NativePageHost host) {
        super(activity, host);
    }

    @Override
    protected void initialize(Activity activity, final NativePageHost host) {
        mHistoryManager = new HistoryManager(
                activity, false, ((SnackbarManageable) activity).getSnackbarManager());
        mTitle = activity.getString(R.string.menu_history);
    }

    @Override
    public View getView() {
        return mHistoryManager.getView();
    }

    @Override
    public String getTitle() {
        return mTitle;
    }

    @Override
    public String getHost() {
        return UrlConstants.HISTORY_HOST;
    }

    @Override
    public void destroy() {
        mHistoryManager.onDestroyed();
        mHistoryManager = null;
        super.destroy();
    }
}

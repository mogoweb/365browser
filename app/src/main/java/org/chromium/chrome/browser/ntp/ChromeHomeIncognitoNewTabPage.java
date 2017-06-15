// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp;

import android.content.Context;
import android.content.res.Resources;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.compositor.layouts.LayoutManagerChrome;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;

/**
 * The incognito new tab page to display when Chrome Home is enabled.
 */
public class ChromeHomeIncognitoNewTabPage extends ChromeHomeNewTabPageBase {
    private final View mView;
    private final int mBackgroundColor;
    private final int mThemeColor;

    /**
     * Constructs a ChromeHomeIncognitoNewTabPage.
     * @param context The context used to inflate the view.
     * @param tab The {@link Tab} that is showing this new tab page.
     * @param tabModelSelector The {@link TabModelSelector} used to open tabs.
     * @param layoutManager The {@link LayoutManagerChrome} used to observe overview mode changes.
     *                      This may be null if the NTP is created on startup due to
     *                      PartnerBrowserCustomizations.
     */
    public ChromeHomeIncognitoNewTabPage(final Context context, final Tab tab,
            final TabModelSelector tabModelSelector,
            @Nullable final LayoutManagerChrome layoutManager) {
        super(context, tab, tabModelSelector, layoutManager);

        mView = LayoutInflater.from(context).inflate(
                R.layout.chrome_home_incognito_new_tab_page, null);
        initializeCloseButton(mView.findViewById(R.id.close_button));

        Resources res = context.getResources();
        mBackgroundColor = ApiCompatibilityUtils.getColor(res, R.color.ntp_bg_incognito);
        mThemeColor = ApiCompatibilityUtils.getColor(res, R.color.incognito_primary_color);
    }

    @Override
    public View getView() {
        return mView;
    }

    @Override
    public int getBackgroundColor() {
        return mBackgroundColor;
    }

    @Override
    public int getThemeColor() {
        return mThemeColor;
    }
}

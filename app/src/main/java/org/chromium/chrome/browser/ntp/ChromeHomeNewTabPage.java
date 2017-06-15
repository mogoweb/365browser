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
import org.chromium.chrome.browser.ntp.LogoBridge.Logo;
import org.chromium.chrome.browser.ntp.LogoBridge.LogoObserver;
import org.chromium.chrome.browser.search_engines.TemplateUrlService;
import org.chromium.chrome.browser.search_engines.TemplateUrlService.TemplateUrlServiceObserver;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;

/**
 * The new tab page to display when Chrome Home is enabled.
 */
public class ChromeHomeNewTabPage
        extends ChromeHomeNewTabPageBase implements TemplateUrlServiceObserver {
    private final LogoView.Delegate mLogoDelegate;

    private final View mView;
    private final LogoView mLogoView;

    private final int mBackgroundColor;
    private final int mThemeColor;

    /**
     * Constructs a ChromeHomeNewTabPage.
     * @param context The context used to inflate the view.
     * @param tab The {@link Tab} that is showing this new tab page.
     * @param tabModelSelector The {@link TabModelSelector} used to open tabs.
     * @param layoutManager The {@link LayoutManagerChrome} used to observe overview mode changes.
     *                      This may be null if the NTP is created on startup due to
     *                      PartnerBrowserCustomizations.
     */
    public ChromeHomeNewTabPage(final Context context, final Tab tab,
            final TabModelSelector tabModelSelector,
            @Nullable final LayoutManagerChrome layoutManager) {
        super(context, tab, tabModelSelector, layoutManager);

        mView = LayoutInflater.from(context).inflate(R.layout.chrome_home_new_tab_page, null);
        mLogoView = (LogoView) mView.findViewById(R.id.search_provider_logo);
        initializeCloseButton(mView.findViewById(R.id.close_button));

        Resources res = context.getResources();
        mBackgroundColor = ApiCompatibilityUtils.getColor(res, R.color.ntp_bg);
        mThemeColor = ApiCompatibilityUtils.getColor(res, R.color.default_primary_color);

        mLogoDelegate = initializeLogoView();

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

    @Override
    public boolean needsToolbarShadow() {
        return false;
    }

    @Override
    public void updateForUrl(String url) {}

    @Override
    public void destroy() {
        super.destroy();
        mLogoDelegate.destroy();
    }

    private void updateSearchProviderLogoVisibility() {
        boolean hasLogo = TemplateUrlService.getInstance().isDefaultSearchEngineGoogle();
        mLogoView.setVisibility(hasLogo ? View.VISIBLE : View.GONE);
    }

    private LogoView.Delegate initializeLogoView() {
        TemplateUrlService.getInstance().addObserver(this);

        final LogoView.Delegate logoDelegate = new LogoDelegateImpl(mTab, mLogoView);
        logoDelegate.getSearchProviderLogo(new LogoObserver() {
            @Override
            public void onLogoAvailable(Logo logo, boolean fromCache) {
                if (logo == null && fromCache) return;
                mLogoView.setDelegate(logoDelegate);
                mLogoView.updateLogo(logo);
                // TODO(twellington): The new logo may be taller than the default logo. Adjust
                //                    the view positioning.
            }
        });
        updateSearchProviderLogoVisibility();
        return logoDelegate;
    }

    // TemplateUrlServiceObserver overrides.

    @Override
    public void onTemplateURLServiceChanged() {
        updateSearchProviderLogoVisibility();
    }
}

// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp;

import android.content.Context;
import android.support.annotation.CallSuper;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnClickListener;

import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeTabbedActivity;
import org.chromium.chrome.browser.NativePage;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.chrome.browser.compositor.layouts.EmptyOverviewModeObserver;
import org.chromium.chrome.browser.compositor.layouts.LayoutManagerChrome;
import org.chromium.chrome.browser.compositor.layouts.OverviewModeBehavior.OverviewModeObserver;
import org.chromium.chrome.browser.tab.EmptyTabObserver;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabObserver;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.widget.bottomsheet.BottomSheet;
import org.chromium.chrome.browser.widget.bottomsheet.BottomSheetMetrics;
import org.chromium.content_public.browser.LoadUrlParams;

/**
 * The base class for the new tab pages displayed in Chrome Home.
 */
public abstract class ChromeHomeNewTabPageBase implements NativePage {
    final Tab mTab;
    final TabObserver mTabObserver;
    final TabModelSelector mTabModelSelector;
    final OverviewModeObserver mOverviewModeObserver;
    @Nullable
    final LayoutManagerChrome mLayoutManager;
    final BottomSheet mBottomSheet;
    final String mTitle;

    private boolean mShowOverviewOnClose;
    private View mCloseButton;

    /**
     * Constructs a ChromeHomeNewTabPageBase.
     * @param context The context used to inflate the view.
     * @param tab The {@link Tab} that is showing this new tab page.
     * @param tabModelSelector The {@link TabModelSelector} used to open tabs.
     * @param layoutManager The {@link LayoutManagerChrome} used to observe overview mode changes.
     *                      This may be null if the NTP is created on startup due to
     *                      PartnerBrowserCustomizations.
     */
    public ChromeHomeNewTabPageBase(final Context context, final Tab tab,
            final TabModelSelector tabModelSelector,
            @Nullable final LayoutManagerChrome layoutManager) {
        mTab = tab;
        mTabModelSelector = tabModelSelector;
        mLayoutManager = layoutManager;
        mBottomSheet = mTab.getActivity().getBottomSheet();
        mTitle = context.getResources().getString(R.string.button_new_tab);

        // A new tab may be created on startup due to PartnerBrowserCustomizations before the
        // LayoutManagerChrome has been created (see ChromeTabbedActivity#initializeState()).
        if (mLayoutManager != null) {
            mShowOverviewOnClose = mLayoutManager.overviewVisible();

            // TODO(twellington): Long term we will not allow NTPs to remain open after the user
            // navigates away from them. Remove this observer after that happens.
            mOverviewModeObserver = new EmptyOverviewModeObserver() {
                @Override
                public void onOverviewModeFinishedHiding() {
                    mShowOverviewOnClose = mTabModelSelector.getCurrentTab() == mTab;
                }
            };
            mLayoutManager.addOverviewModeObserver(mOverviewModeObserver);
        } else {
            mOverviewModeObserver = null;
        }

        mTabObserver = new EmptyTabObserver() {
            @Override
            public void onShown(Tab tab) {
                onNewTabPageShown();
            }

            @Override
            public void onHidden(Tab tab) {
                mTab.getActivity().getFadingBackgroundView().setEnabled(true);
                if (!mTab.isClosing()) mShowOverviewOnClose = false;
            }

            @Override
            public void onLoadUrl(Tab tab, LoadUrlParams params, int loadType) {
                // If the NTP is loading, the sheet state will be set to SHEET_STATE_HALF.
                if (TextUtils.equals(tab.getUrl(), getUrl())) return;

                mBottomSheet.getBottomSheetMetrics().setSheetCloseReason(
                        BottomSheetMetrics.CLOSED_BY_NAVIGATION);
                mBottomSheet.setSheetState(BottomSheet.SHEET_STATE_PEEK, true);
            }
        };
        mTab.addObserver(mTabObserver);

        // If the tab is already showing TabObserver#onShown() won't be called, so we need to call
        // #onNewTabPageShown() directly.
        boolean tabAlreadyShowing = mTabModelSelector.getCurrentTab() == mTab;
        if (tabAlreadyShowing) onNewTabPageShown();

        mBottomSheet.setSheetState(BottomSheet.SHEET_STATE_HALF, true);
        mBottomSheet.getBottomSheetMetrics().recordSheetOpenReason(
                BottomSheetMetrics.OPENED_BY_NEW_TAB_CREATION);

        // TODO(twellington): disallow moving the NTP to the other window in Android N+
        //                    multi-window mode.
    }

    @Override
    public String getTitle() {
        return mTitle;
    }

    @Override
    public String getUrl() {
        return UrlConstants.NTP_URL;
    }

    @Override
    public String getHost() {
        return UrlConstants.NTP_HOST;
    }

    @Override
    public boolean needsToolbarShadow() {
        return false;
    }

    @Override
    public void updateForUrl(String url) {}

    @Override
    @CallSuper
    public void destroy() {
        // The next tab will be selected before this one is destroyed. If the currently selected
        // tab is a Chrome Home new tab page, the FadingBackgroundView should not be enabled.
        mTab.getActivity().getFadingBackgroundView().setEnabled(
                !isTabChromeHomeNewTabPage(mTabModelSelector.getCurrentTab()));

        if (mLayoutManager != null) {
            mLayoutManager.removeOverviewModeObserver(mOverviewModeObserver);
        }
        mTab.removeObserver(mTabObserver);
    }

    private void onNewTabPageShown() {
        if (mTab.getActivity().getFadingBackgroundView() != null) {
            mTab.getActivity().getFadingBackgroundView().setEnabled(false);
        }
    }

    private boolean isTabChromeHomeNewTabPage(Tab tab) {
        return tab != null && tab.getUrl().equals(getUrl());
    }

    void initializeCloseButton(View closeButton) {
        mCloseButton = closeButton;
        mCloseButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mBottomSheet.getBottomSheetMetrics().setSheetCloseReason(
                        BottomSheetMetrics.CLOSED_BY_NTP_CLOSE_BUTTON);
                mBottomSheet.setSheetState(BottomSheet.SHEET_STATE_PEEK, true);
                if (mShowOverviewOnClose && getLayoutManager() != null) {
                    getLayoutManager().showOverview(false);
                }

                // Close the tab after showing the overview mode so the bottom sheet doesn't open
                // if another NTP is selected when this one is closed.
                // TODO(twellington): remove this comment after only one NTP may be open at a time.
                mTabModelSelector.closeTab(mTab);
            }
        });
    }

    private LayoutManagerChrome getLayoutManager() {
        if (mLayoutManager != null) return mLayoutManager;

        return ((ChromeTabbedActivity) mTab.getActivity()).getLayoutManager();
    }

    // Methods for testing.

    @VisibleForTesting
    public View getCloseButtonForTests() {
        return mCloseButton;
    }
}

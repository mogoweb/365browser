// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.suggestions;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;

import org.chromium.base.Callback;
import org.chromium.base.CommandLine;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.ChromeSwitches;
import org.chromium.chrome.browser.ntp.NewTabPageUma;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.snackbar.Snackbar;
import org.chromium.chrome.browser.snackbar.SnackbarManager;
import org.chromium.chrome.browser.snackbar.SnackbarManager.SnackbarController;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.tabmodel.TabModelUtils;
import org.chromium.chrome.browser.util.UrlUtilities;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.ui.base.PageTransition;
import org.chromium.ui.mojom.WindowOpenDisposition;

/**
 * Reusable implementation of {@link TileGroup.Delegate}. Performs work in parts of the system that
 * the {@link TileGroup} should not know about.
 */
public class TileGroupDelegateImpl implements TileGroup.Delegate {
    private static MostVisitedSites sMostVisitedSitesForTests;

    private final Context mContext;
    private final SnackbarManager mSnackbarManager;
    private final TabModelSelector mTabModelSelector;
    private final SuggestionsNavigationDelegate mNavigationDelegate;
    private final MostVisitedSites mMostVisitedSites;

    private boolean mIsDestroyed;
    private SnackbarController mTileRemovedSnackbarController;

    public TileGroupDelegateImpl(ChromeActivity activity, Profile profile,
            TabModelSelector tabModelSelector, SuggestionsNavigationDelegate navigationDelegate,
            SnackbarManager snackbarManager) {
        mContext = activity;
        mSnackbarManager = snackbarManager;
        mTabModelSelector = tabModelSelector;
        mNavigationDelegate = navigationDelegate;
        mMostVisitedSites = buildMostVisitedSites(profile);
    }

    @Override
    public void removeMostVisitedItem(Tile item, Callback<String> removalUndoneCallback) {
        assert !mIsDestroyed;

        mMostVisitedSites.addBlacklistedUrl(item.getUrl());
        showTileRemovedSnackbar(item.getUrl(), removalUndoneCallback);
    }

    @Override
    public void openMostVisitedItem(int windowDisposition, Tile item) {
        assert !mIsDestroyed;

        String url = item.getUrl();

        // TODO(treib): Should we call recordOpenedMostVisitedItem here?
        if (windowDisposition != WindowOpenDisposition.NEW_WINDOW) {
            recordOpenedTile(item);
        }

        if (windowDisposition == WindowOpenDisposition.CURRENT_TAB && switchToExistingTab(url)) {
            return;
        }

        mNavigationDelegate.openUrl(
                windowDisposition, new LoadUrlParams(url, PageTransition.AUTO_BOOKMARK));
    }

    @Override
    public void setMostVisitedSitesObserver(MostVisitedSites.Observer observer, int maxResults) {
        assert !mIsDestroyed;

        mMostVisitedSites.setObserver(observer, maxResults);
    }

    @Override
    public void onLoadingComplete(Tile[] tiles) {
        assert !mIsDestroyed;

        int types[] = new int[tiles.length];
        int sources[] = new int[tiles.length];
        String urls[] = new String[tiles.length];

        for (int i = 0; i < tiles.length; i++) {
            types[i] = tiles[i].getType();
            sources[i] = tiles[i].getSource();
            urls[i] = tiles[i].getUrl();
        }

        mMostVisitedSites.recordPageImpression(types, sources, urls);
    }

    @Override
    public void destroy() {
        assert !mIsDestroyed;
        mIsDestroyed = true;

        if (mTileRemovedSnackbarController != null) {
            mSnackbarManager.dismissSnackbars(mTileRemovedSnackbarController);
        }
        mMostVisitedSites.destroy();
    }

    private static MostVisitedSites buildMostVisitedSites(Profile profile) {
        if (sMostVisitedSitesForTests != null) {
            return sMostVisitedSitesForTests;
        } else {
            return new MostVisitedSitesBridge(profile);
        }
    }

    @VisibleForTesting
    public static void setMostVisitedSitesForTests(MostVisitedSites mostVisitedSitesForTests) {
        sMostVisitedSitesForTests = mostVisitedSitesForTests;
    }

    private void showTileRemovedSnackbar(String url, final Callback<String> removalUndoneCallback) {
        if (mTileRemovedSnackbarController == null) {
            mTileRemovedSnackbarController = new SnackbarController() {
                @Override
                public void onDismissNoAction(Object actionData) {}

                /** Undoes the tile removal. */
                @Override
                public void onAction(Object actionData) {
                    if (mIsDestroyed) return;
                    String url = (String) actionData;
                    removalUndoneCallback.onResult(url);
                    mMostVisitedSites.removeBlacklistedUrl(url);
                }
            };
        }
        Snackbar snackbar = Snackbar.make(mContext.getString(R.string.most_visited_item_removed),
                                            mTileRemovedSnackbarController, Snackbar.TYPE_ACTION,
                                            Snackbar.UMA_NTP_MOST_VISITED_DELETE_UNDO)
                                    .setAction(mContext.getString(R.string.undo), url);
        mSnackbarManager.showSnackbar(snackbar);
    }

    private void recordOpenedTile(Tile tile) {
        NewTabPageUma.recordAction(NewTabPageUma.ACTION_OPENED_MOST_VISITED_TILE);
        RecordUserAction.record("MobileNTPMostVisited");
        NewTabPageUma.recordExplicitUserNavigation(
                tile.getUrl(), NewTabPageUma.RAPPOR_ACTION_VISITED_SUGGESTED_TILE);
        mMostVisitedSites.recordOpenedMostVisitedItem(
                tile.getIndex(), tile.getType(), tile.getSource());
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private boolean switchToExistingTab(String url) {
        String matchPattern =
                CommandLine.getInstance().getSwitchValue(ChromeSwitches.NTP_SWITCH_TO_EXISTING_TAB);
        boolean matchByHost;
        if ("url".equals(matchPattern)) {
            matchByHost = false;
        } else if ("host".equals(matchPattern)) {
            matchByHost = true;
        } else {
            return false;
        }

        TabModel tabModel = mTabModelSelector.getModel(false);
        for (int i = tabModel.getCount() - 1; i >= 0; --i) {
            if (matchURLs(tabModel.getTabAt(i).getUrl(), url, matchByHost)) {
                TabModelUtils.setIndex(tabModel, i);
                return true;
            }
        }
        return false;
    }

    private static boolean matchURLs(String url1, String url2, boolean matchByHost) {
        if (url1 == null || url2 == null) return false;
        return matchByHost ? UrlUtilities.sameHost(url1, url2) : url1.equals(url2);
    }
}

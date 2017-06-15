// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.suggestions;

import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.NativePageHost;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.chrome.browser.bookmarks.BookmarkUtils;
import org.chromium.chrome.browser.download.DownloadUtils;
import org.chromium.chrome.browser.multiwindow.MultiWindowUtils;
import org.chromium.chrome.browser.ntp.NewTabPageUma;
import org.chromium.chrome.browser.ntp.snippets.KnownCategories;
import org.chromium.chrome.browser.ntp.snippets.SnippetArticle;
import org.chromium.chrome.browser.offlinepages.DownloadUiActionFlags;
import org.chromium.chrome.browser.offlinepages.OfflinePageBridge;
import org.chromium.chrome.browser.offlinepages.OfflinePageUtils;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.TabModel.TabLaunchType;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.tabmodel.TabModelUtils;
import org.chromium.chrome.browser.tabmodel.document.TabDelegate;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.content_public.common.Referrer;
import org.chromium.ui.base.PageTransition;
import org.chromium.ui.mojom.WindowOpenDisposition;

/**
 * {@link SuggestionsUiDelegate} implementation.
 */
public class SuggestionsNavigationDelegateImpl implements SuggestionsNavigationDelegate {
    private static final String CHROME_CONTENT_SUGGESTIONS_REFERRER =
            "https://www.googleapis.com/auth/chrome-content-suggestions";
    private static final String NEW_TAB_URL_HELP =
            "https://support.google.com/chrome/?p=new_tab";

    private final ChromeActivity mActivity;
    private final Profile mProfile;

    private final NativePageHost mHost;
    private final TabModelSelector mTabModelSelector;

    public SuggestionsNavigationDelegateImpl(ChromeActivity activity, Profile profile,
            NativePageHost host, TabModelSelector tabModelSelector) {
        mActivity = activity;
        mProfile = profile;
        mHost = host;
        mTabModelSelector = tabModelSelector;
    }

    @Override
    public boolean isOpenInNewWindowEnabled() {
        return MultiWindowUtils.getInstance().isOpenInOtherWindowSupported(mActivity);
    }

    @Override
    public boolean isOpenInIncognitoEnabled() {
        return PrefServiceBridge.getInstance().isIncognitoModeEnabled();
    }

    @Override
    public void navigateToBookmarks() {
        RecordUserAction.record("MobileNTPSwitchToBookmarks");
        BookmarkUtils.showBookmarkManager(mActivity);
    }

    @Override
    public void navigateToRecentTabs() {
        RecordUserAction.record("MobileNTPSwitchToOpenTabs");
        mHost.loadUrl(new LoadUrlParams(UrlConstants.RECENT_TABS_URL), /* incognito = */ false);
    }

    @Override
    public void navigateToDownloadManager() {
        RecordUserAction.record("MobileNTPSwitchToDownloadManager");
        DownloadUtils.showDownloadManager(mActivity, mHost.getActiveTab());
    }

    @Override
    public void navigateToHelpPage() {
        NewTabPageUma.recordAction(NewTabPageUma.ACTION_CLICKED_LEARN_MORE);
        // TODO(dgn): Use the standard Help UI rather than a random link to online help?
        openUrl(WindowOpenDisposition.CURRENT_TAB,
                new LoadUrlParams(NEW_TAB_URL_HELP, PageTransition.AUTO_BOOKMARK));
    }

    @Override
    public void openSnippet(int windowOpenDisposition, SnippetArticle article) {
        NewTabPageUma.recordAction(NewTabPageUma.ACTION_OPENED_SNIPPET);

        if (article.isAssetDownload()) {
            assert windowOpenDisposition == WindowOpenDisposition.CURRENT_TAB
                    || windowOpenDisposition == WindowOpenDisposition.NEW_WINDOW
                    || windowOpenDisposition == WindowOpenDisposition.NEW_BACKGROUND_TAB;
            DownloadUtils.openFile(article.getAssetDownloadFile(),
                    article.getAssetDownloadMimeType(), article.getAssetDownloadGuid(), false);
            return;
        }

        if (article.isRecentTab()) {
            assert windowOpenDisposition == WindowOpenDisposition.CURRENT_TAB;
            boolean success = openRecentTabSnippet(article);
            assert success;
            return;
        }

        LoadUrlParams loadUrlParams;
        // We explicitly open an offline page only for offline page downloads. For all other
        // sections the URL is opened and it is up to Offline Pages whether to open its offline
        // page (e.g. when offline).
        if (article.isDownload() && !article.isAssetDownload()) {
            assert article.getOfflinePageOfflineId() != null;
            assert windowOpenDisposition == WindowOpenDisposition.CURRENT_TAB
                    || windowOpenDisposition == WindowOpenDisposition.NEW_WINDOW
                    || windowOpenDisposition == WindowOpenDisposition.NEW_BACKGROUND_TAB;
            loadUrlParams = OfflinePageUtils.getLoadUrlParamsForOpeningOfflineVersion(
                    article.mUrl, article.getOfflinePageOfflineId());
            // Extra headers are not read in loadUrl, but verbatim headers are.
            loadUrlParams.setVerbatimHeaders(loadUrlParams.getExtraHeadersString());
        } else {
            loadUrlParams = new LoadUrlParams(article.mUrl, PageTransition.AUTO_BOOKMARK);
        }

        // For article suggestions, we set the referrer. This is exploited
        // to filter out these history entries for NTP tiles.
        // TODO(mastiz): Extend this with support for other categories.
        if (article.mCategory == KnownCategories.ARTICLES) {
            loadUrlParams.setReferrer(new Referrer(
                    CHROME_CONTENT_SUGGESTIONS_REFERRER, Referrer.REFERRER_POLICY_ALWAYS));
        }

        openUrl(windowOpenDisposition, loadUrlParams);

        // TODO(treib): Also track other dispositions. crbug.com/665915
        if (windowOpenDisposition == WindowOpenDisposition.CURRENT_TAB) {
            NewTabPageUma.monitorContentSuggestionVisit(mHost.getActiveTab(), article.mCategory);
        }
    }

    @Override
    public void openUrl(int windowOpenDisposition, LoadUrlParams loadUrlParams) {
        switch (windowOpenDisposition) {
            case WindowOpenDisposition.CURRENT_TAB:
                mHost.loadUrl(loadUrlParams, mTabModelSelector.isIncognitoSelected());
                break;
            case WindowOpenDisposition.NEW_BACKGROUND_TAB:
                openUrlInNewTab(loadUrlParams);
                break;
            case WindowOpenDisposition.OFF_THE_RECORD:
                mHost.loadUrl(loadUrlParams, true);
                break;
            case WindowOpenDisposition.NEW_WINDOW:
                openUrlInNewWindow(loadUrlParams);
                break;
            case WindowOpenDisposition.SAVE_TO_DISK:
                saveUrlForOffline(loadUrlParams.getUrl());
                break;
            default:
                assert false;
        }
    }

    private boolean openRecentTabSnippet(SnippetArticle article) {
        TabModel tabModel = mTabModelSelector.getModel(false);
        int tabId = article.getRecentTabId();
        int tabIndex = TabModelUtils.getTabIndexById(tabModel, tabId);
        if (tabIndex == TabModel.INVALID_TAB_INDEX) return false;
        TabModelUtils.setIndex(tabModel, tabIndex);
        return true;
    }

    private void openUrlInNewWindow(LoadUrlParams loadUrlParams) {
        TabDelegate tabDelegate = new TabDelegate(false);
        tabDelegate.createTabInOtherWindow(loadUrlParams, mActivity, mHost.getParentId());
    }

    private void openUrlInNewTab(LoadUrlParams loadUrlParams) {
        mTabModelSelector.openNewTab(loadUrlParams, TabLaunchType.FROM_LONGPRESS_BACKGROUND,
                mHost.getActiveTab(), /* incognito = */ false);
    }

    private void saveUrlForOffline(String url) {
        if (mHost.getActiveTab() != null) {
            OfflinePageBridge.getForProfile(mProfile).scheduleDownload(
                    mHost.getActiveTab().getWebContents(), "ntp_suggestions", url,
                    DownloadUiActionFlags.ALL);
        } else {
            OfflinePageBridge.getForProfile(mProfile).savePageLater(
                    url, "ntp_suggestions", true /* userRequested */);
        }
    }
}

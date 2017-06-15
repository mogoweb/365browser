// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.CommandLine;
import org.chromium.base.DiscardableReferencePool;
import org.chromium.base.Log;
import org.chromium.base.TraceEvent;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.ChromeSwitches;
import org.chromium.chrome.browser.NativePage;
import org.chromium.chrome.browser.NativePageHost;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.chrome.browser.compositor.layouts.content.InvalidationAwareThumbnailProvider;
import org.chromium.chrome.browser.download.DownloadManagerService;
import org.chromium.chrome.browser.metrics.StartupMetrics;
import org.chromium.chrome.browser.ntp.NewTabPageView.NewTabPageManager;
import org.chromium.chrome.browser.ntp.snippets.SnippetsBridge;
import org.chromium.chrome.browser.ntp.snippets.SuggestionsSource;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.search_engines.TemplateUrlService;
import org.chromium.chrome.browser.search_engines.TemplateUrlService.TemplateUrlServiceObserver;
import org.chromium.chrome.browser.suggestions.SuggestionsEventReporter;
import org.chromium.chrome.browser.suggestions.SuggestionsEventReporterBridge;
import org.chromium.chrome.browser.suggestions.SuggestionsMetrics;
import org.chromium.chrome.browser.suggestions.SuggestionsNavigationDelegate;
import org.chromium.chrome.browser.suggestions.SuggestionsNavigationDelegateImpl;
import org.chromium.chrome.browser.suggestions.SuggestionsUiDelegateImpl;
import org.chromium.chrome.browser.suggestions.Tile;
import org.chromium.chrome.browser.suggestions.TileGroup;
import org.chromium.chrome.browser.suggestions.TileGroupDelegateImpl;
import org.chromium.chrome.browser.sync.SyncSessionsMetrics;
import org.chromium.chrome.browser.tab.EmptyTabObserver;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabObserver;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.tabmodel.TabModelUtils;
import org.chromium.chrome.browser.util.FeatureUtilities;
import org.chromium.chrome.browser.util.UrlUtilities;
import org.chromium.chrome.browser.vr_shell.VrShellDelegate;
import org.chromium.content_public.browser.NavigationController;
import org.chromium.content_public.browser.NavigationEntry;
import org.chromium.net.NetworkChangeNotifier;
import org.chromium.ui.base.DeviceFormFactor;
import org.chromium.ui.mojom.WindowOpenDisposition;

import java.util.concurrent.TimeUnit;

/**
 * Provides functionality when the user interacts with the NTP.
 */
public class NewTabPage
        implements NativePage, InvalidationAwareThumbnailProvider, TemplateUrlServiceObserver {
    private static final String TAG = "NewTabPage";

    // Key for the scroll position data that may be stored in a navigation entry.
    private static final String NAVIGATION_ENTRY_SCROLL_POSITION_KEY = "NewTabPageScrollPosition";

    private static SuggestionsSource sSuggestionsSourceForTests;

    private final Tab mTab;
    private final TabModelSelector mTabModelSelector;

    private final String mTitle;
    private final int mBackgroundColor;
    private final int mThemeColor;
    private final NewTabPageView mNewTabPageView;
    private final NewTabPageManagerImpl mNewTabPageManager;
    private final TileGroup.Delegate mTileGroupDelegate;

    private TabObserver mTabObserver;
    private boolean mSearchProviderHasLogo;
    private FakeboxDelegate mFakeboxDelegate;
    private SnippetsBridge mSnippetsBridge;

    // The timestamp at which the constructor was called.
    private final long mConstructedTimeNs;

    // The timestamp at which this NTP was last shown to the user.
    private long mLastShownTimeNs;

    private boolean mIsLoaded;

    // Whether destroy() has been called.
    private boolean mIsDestroyed;

    /**
     * Allows clients to listen for updates to the scroll changes of the search box on the
     * NTP.
     */
    public interface OnSearchBoxScrollListener {
        /**
         * Callback to be notified when the scroll position of the search box on the NTP has
         * changed.  A scroll percentage of 0, means the search box has no scroll applied and
         * is in it's natural resting position.  A value of 1 means the search box is scrolled
         * entirely to the top of the screen viewport.
         *
         * @param scrollPercentage The percentage the search box has been scrolled off the page.
         */
        void onNtpScrollChanged(float scrollPercentage);
    }

    /**
     * Handles user interaction with the fakebox (the URL bar in the NTP).
     */
    public interface FakeboxDelegate {
        /**
         * Shows the voice recognition dialog. Called when the user taps the microphone icon.
         */
        void startVoiceRecognition();

        /**
         * @return Whether voice search is currently enabled.
         */
        boolean isVoiceSearchEnabled();

        /**
         * @return Whether the URL bar is currently focused.
         */
        boolean isUrlBarFocused();

        /**
         * Focuses the URL bar when the user taps the fakebox, types in the fakebox, or pastes text
         * into the fakebox.
         *
         * @param pastedText The text that was pasted or typed into the fakebox, or null if the user
         *                   just tapped the fakebox.
         */
        void requestUrlFocusFromFakebox(String pastedText);

        /**
         * @return whether the provided native page is the one currently displayed to the user.
         */
        boolean isCurrentPage(NativePage nativePage);
    }

    /**
     * @param url The URL to check whether it is for the NTP.
     * @return Whether the passed in URL is used to render the NTP.
     */
    public static boolean isNTPUrl(String url) {
        // Also handle the legacy chrome://newtab URL since that will redirect to
        // chrome-native://newtab natively.
        return url != null
                && (url.startsWith(UrlConstants.NTP_URL) || url.startsWith("chrome://newtab"));
    }

    private boolean isNtpOfflinePagesEnabled() {
        return ChromeFeatureList.isEnabled(ChromeFeatureList.NTP_OFFLINE_PAGES_FEATURE_NAME);
    }

    @VisibleForTesting
    public static void setSuggestionsSourceForTests(SuggestionsSource suggestionsSource) {
        sSuggestionsSourceForTests = suggestionsSource;
    }

    private class NewTabPageManagerImpl
            extends SuggestionsUiDelegateImpl implements NewTabPageManager {
        public NewTabPageManagerImpl(SuggestionsSource suggestionsSource,
                SuggestionsEventReporter eventReporter,
                SuggestionsNavigationDelegate navigationDelegate, Profile profile,
                NativePageHost nativePageHost, DiscardableReferencePool referencePool) {
            super(suggestionsSource, eventReporter, navigationDelegate, profile, nativePageHost,
                    referencePool);
        }

        @Override
        public boolean isLocationBarShownInNTP() {
            if (mIsDestroyed) return false;
            Context context = mNewTabPageView.getContext();
            return isInSingleUrlBarMode(context)
                    && !mNewTabPageView.urlFocusAnimationsDisabled();
        }

        @Override
        public boolean isVoiceSearchEnabled() {
            return mFakeboxDelegate != null && mFakeboxDelegate.isVoiceSearchEnabled();
        }

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        private boolean switchToExistingTab(String url) {
            String matchPattern = CommandLine.getInstance().getSwitchValue(
                    ChromeSwitches.NTP_SWITCH_TO_EXISTING_TAB);
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

        private boolean matchURLs(String url1, String url2, boolean matchByHost) {
            if (url1 == null || url2 == null) return false;
            return matchByHost ? UrlUtilities.sameHost(url1, url2) : url1.equals(url2);
        }

        @Override
        public void focusSearchBox(boolean beginVoiceSearch, String pastedText) {
            if (mIsDestroyed) return;
            if (VrShellDelegate.isInVr()) return;
            if (mFakeboxDelegate != null) {
                if (beginVoiceSearch) {
                    mFakeboxDelegate.startVoiceRecognition();
                } else {
                    mFakeboxDelegate.requestUrlFocusFromFakebox(pastedText);
                }
            }
        }

        @Override
        public SuggestionsSource getSuggestionsSource() {
            if (sSuggestionsSourceForTests != null) return sSuggestionsSourceForTests;
            return mSnippetsBridge;
        }

        @Override
        public boolean isCurrentPage() {
            if (mIsDestroyed) return false;
            if (mFakeboxDelegate == null) return false;
            return mFakeboxDelegate.isCurrentPage(NewTabPage.this);
        }
    }

    /**
     * Extends {@link TileGroupDelegateImpl} to add metrics logging that is specific to
     * {@link NewTabPage}.
     */
    private class NewTabPageTileGroupDelegate extends TileGroupDelegateImpl {
        private NewTabPageTileGroupDelegate(ChromeActivity activity, Profile profile,
                TabModelSelector tabModelSelector,
                SuggestionsNavigationDelegate navigationDelegate) {
            super(activity, profile, tabModelSelector, navigationDelegate,
                    activity.getSnackbarManager());
        }

        @Override
        public void onLoadingComplete(Tile[] items) {
            if (mIsDestroyed) return;

            super.onLoadingComplete(items);

            long loadTimeMs = (System.nanoTime() - mConstructedTimeNs) / 1000000;
            RecordHistogram.recordTimesHistogram(
                    "Tab.NewTabOnload", loadTimeMs, TimeUnit.MILLISECONDS);
            mIsLoaded = true;
            StartupMetrics.getInstance().recordOpenedNTP();
            NewTabPageUma.recordNTPImpression(NewTabPageUma.NTP_IMPRESSION_REGULAR);
            // If not visible when loading completes, wait until onShown is received.
            if (!mTab.isHidden()) recordNTPShown();

            if (isNtpOfflinePagesEnabled()) {
                final int maxNumTiles = 12;
                for (int i = 0; i < items.length; i++) {
                    if (items[i].isOfflineAvailable()) {
                        RecordHistogram.recordEnumeratedHistogram(
                                "NewTabPage.TileOfflineAvailable", i, maxNumTiles);
                    }
                }
            }
            SyncSessionsMetrics.recordYoungestForeignTabAgeOnNTP();
        }

        @Override
        public void openMostVisitedItem(int windowDisposition, Tile tile) {
            if (mIsDestroyed) return;

            super.openMostVisitedItem(windowDisposition, tile);

            if (windowDisposition != WindowOpenDisposition.NEW_WINDOW) {
                RecordHistogram.recordMediumTimesHistogram("NewTabPage.MostVisitedTime",
                        System.nanoTime() - mLastShownTimeNs, TimeUnit.NANOSECONDS);
            }
        }
    }

    /**
     * Constructs a NewTabPage.
     * @param activity The activity used for context to create the new tab page's View.
     * @param nativePageHost The host that is showing this new tab page.
     * @param tabModelSelector The TabModelSelector used to open tabs.
     */
    public NewTabPage(ChromeActivity activity, NativePageHost nativePageHost,
            TabModelSelector tabModelSelector) {
        mConstructedTimeNs = System.nanoTime();
        TraceEvent.begin(TAG);

        mTab = nativePageHost.getActiveTab();
        mTabModelSelector = tabModelSelector;
        Profile profile = mTab.getProfile();

        mSnippetsBridge = new SnippetsBridge(profile);
        SuggestionsEventReporter eventReporter = new SuggestionsEventReporterBridge();

        SuggestionsNavigationDelegateImpl navigationDelegate =
                new SuggestionsNavigationDelegateImpl(
                        activity, profile, nativePageHost, tabModelSelector);
        mNewTabPageManager = new NewTabPageManagerImpl(mSnippetsBridge, eventReporter,
                navigationDelegate, profile, nativePageHost, activity.getReferencePool());
        mTileGroupDelegate = new NewTabPageTileGroupDelegate(
                activity, profile, tabModelSelector, navigationDelegate);

        mTitle = activity.getResources().getString(R.string.button_new_tab);
        mBackgroundColor = ApiCompatibilityUtils.getColor(activity.getResources(), R.color.ntp_bg);
        mThemeColor = ApiCompatibilityUtils.getColor(
                activity.getResources(), R.color.default_primary_color);
        TemplateUrlService.getInstance().addObserver(this);

        mTabObserver = new EmptyTabObserver() {
            @Override
            public void onShown(Tab tab) {
                // Showing the NTP is only meaningful when the page has been loaded already.
                if (mIsLoaded) recordNTPShown();

                mNewTabPageView.getTileGroup().onSwitchToForeground();
            }

            @Override
            public void onHidden(Tab tab) {
                if (mIsLoaded) recordNTPHidden();
            }

            @Override
            public void onPageLoadStarted(Tab tab, String url) {
                int scrollPosition = mNewTabPageView.getScrollPosition();
                if (scrollPosition == RecyclerView.NO_POSITION) return;

                if (mTab.getWebContents() == null) return;

                NavigationController controller = mTab.getWebContents().getNavigationController();
                int index = controller.getLastCommittedEntryIndex();
                NavigationEntry entry = controller.getEntryAtIndex(index);
                if (entry == null) return;

                // At least under test conditions this method may be called initially for the load
                // of the NTP itself, at which point the last committed entry is not for the NTP
                // yet. This method will then be called a second time when the user navigates away,
                // at which point the last committed entry is for the NTP. The extra data must only
                // be set in the latter case.
                if (!isNTPUrl(entry.getUrl())) return;

                controller.setEntryExtraData(index, NAVIGATION_ENTRY_SCROLL_POSITION_KEY,
                        Integer.toString(scrollPosition));
            }
        };
        mTab.addObserver(mTabObserver);
        updateSearchProviderHasLogo();

        LayoutInflater inflater = LayoutInflater.from(activity);
        mNewTabPageView = (NewTabPageView) inflater.inflate(R.layout.new_tab_page_view, null);
        mNewTabPageView.initialize(mNewTabPageManager, mTab, mTileGroupDelegate,
                mSearchProviderHasLogo, getScrollPositionFromNavigationEntry());

        eventReporter.onSurfaceOpened();

        DownloadManagerService.getDownloadManagerService().checkForExternallyRemovedDownloads(
                /*isOffRecord=*/false);

        RecordHistogram.recordBooleanHistogram(
                "NewTabPage.MobileIsUserOnline", NetworkChangeNotifier.isOnline());
        NewTabPageUma.recordLoadType(activity);
        TraceEvent.end(TAG);
    }

    /** @return The view container for the new tab page. */
    @VisibleForTesting
    public NewTabPageView getNewTabPageView() {
        return mNewTabPageView;
    }

    /**
     * Updates whether the NewTabPage should animate on URL focus changes.
     * @param disable Whether to disable the animations.
     */
    public void setUrlFocusAnimationsDisabled(boolean disable) {
        mNewTabPageView.setUrlFocusAnimationsDisabled(disable);
    }

    private boolean isInSingleUrlBarMode(Context context) {
        if (DeviceFormFactor.isTablet()) return false;
        if (FeatureUtilities.isChromeHomeEnabled()) return false;
        return mSearchProviderHasLogo;
    }

    private void updateSearchProviderHasLogo() {
        mSearchProviderHasLogo = TemplateUrlService.getInstance().isDefaultSearchEngineGoogle();
    }

    private void onSearchEngineUpdated() {
        // TODO(newt): update this if other search providers provide logos.
        updateSearchProviderHasLogo();
        mNewTabPageView.setSearchProviderHasLogo(mSearchProviderHasLogo);
    }

    /**
     * Specifies the percentage the URL is focused during an animation.  1.0 specifies that the URL
     * bar has focus and has completed the focus animation.  0 is when the URL bar is does not have
     * any focus.
     *
     * @param percent The percentage of the URL bar focus animation.
     */
    public void setUrlFocusChangeAnimationPercent(float percent) {
        mNewTabPageView.setUrlFocusChangeAnimationPercent(percent);
    }

    /**
     * Get the bounds of the search box in relation to the top level NewTabPage view.
     *
     * @param bounds The current drawing location of the search box.
     * @param translation The translation applied to the search box by the parent view hierarchy up
     *                    to the NewTabPage view.
     */
    public void getSearchBoxBounds(Rect bounds, Point translation) {
        mNewTabPageView.getSearchBoxBounds(bounds, translation);
    }

    /**
     * Updates the opacity of the search box when scrolling.
     *
     * @param alpha opacity (alpha) value to use.
     */
    public void setSearchBoxAlpha(float alpha) {
        mNewTabPageView.setSearchBoxAlpha(alpha);
    }

    /**
     * Updates the opacity of the search provider logo when scrolling.
     *
     * @param alpha opacity (alpha) value to use.
     */
    public void setSearchProviderLogoAlpha(float alpha) {
        mNewTabPageView.setSearchProviderLogoAlpha(alpha);
    }

    /**
     * @return Whether the location bar is shown in the NTP.
     */
    public boolean isLocationBarShownInNTP() {
        return mNewTabPageManager.isLocationBarShownInNTP();
    }

    /**
     * Sets the listener for search box scroll changes.
     * @param listener The listener to be notified on changes.
     */
    public void setSearchBoxScrollListener(OnSearchBoxScrollListener listener) {
        mNewTabPageView.setSearchBoxScrollListener(listener);
    }

    /**
     * Sets the FakeboxDelegate that this pages interacts with.
     */
    public void setFakeboxDelegate(FakeboxDelegate fakeboxDelegate) {
        mFakeboxDelegate = fakeboxDelegate;
        if (mFakeboxDelegate != null) {
            mNewTabPageView.updateVoiceSearchButtonVisibility();

            // The toolbar can't get the reference to the native page until its initialization is
            // finished, so we can't cache it here and transfer it to the view later. We pull that
            // state from the location bar when we get a reference to it as a workaround.
            mNewTabPageView.setUrlFocusChangeAnimationPercent(
                    fakeboxDelegate.isUrlBarFocused() ? 1f : 0f);
        }
    }

    /**
     * Records UMA for the NTP being shown. This includes a fresh page load or being brought to the
     * foreground.
     */
    private void recordNTPShown() {
        mLastShownTimeNs = System.nanoTime();
        RecordUserAction.record("MobileNTPShown");
        SuggestionsMetrics.recordSurfaceVisible();
    }

    /** Records UMA for the NTP being hidden and the time spent on it. */
    private void recordNTPHidden() {
        RecordHistogram.recordMediumTimesHistogram(
                "NewTabPage.TimeSpent", System.nanoTime() - mLastShownTimeNs, TimeUnit.NANOSECONDS);
        SuggestionsMetrics.recordSurfaceHidden();
    }

    /**
     * Returns the value of the adapter scroll position that was stored in the last committed
     * navigation entry. Returns {@code RecyclerView.NO_POSITION} if there is no last committed
     * navigation entry, or if no data is found.
     * @return The adapter scroll position.
     */
    private int getScrollPositionFromNavigationEntry() {
        if (mTab.getWebContents() == null) return RecyclerView.NO_POSITION;

        NavigationController controller = mTab.getWebContents().getNavigationController();
        int index = controller.getLastCommittedEntryIndex();
        String scrollPositionData =
                controller.getEntryExtraData(index, NAVIGATION_ENTRY_SCROLL_POSITION_KEY);
        if (TextUtils.isEmpty(scrollPositionData)) return RecyclerView.NO_POSITION;

        try {
            return Integer.parseInt(scrollPositionData);
        } catch (NumberFormatException e) {
            Log.w(TAG, "Bad data found for scroll position: %s", scrollPositionData, e);
            return RecyclerView.NO_POSITION;
        }
    }

    /**
     * @return Whether the NTP has finished loaded.
     */
    @VisibleForTesting
    public boolean isLoadedForTests() {
        return mIsLoaded;
    }

    // TemplateUrlServiceObserver overrides

    @Override
    public void onTemplateURLServiceChanged() {
        onSearchEngineUpdated();
    }

    // NativePage overrides

    @Override
    public void destroy() {
        assert !mIsDestroyed;
        assert !ViewCompat
                .isAttachedToWindow(getView()) : "Destroy called before removed from window";
        if (mIsLoaded && !mTab.isHidden()) recordNTPHidden();

        if (mSnippetsBridge != null) {
            mSnippetsBridge.onDestroy();
            mSnippetsBridge = null;
        }
        mNewTabPageManager.onDestroy();
        mTileGroupDelegate.destroy();
        TemplateUrlService.getInstance().removeObserver(this);
        mTab.removeObserver(mTabObserver);
        mTabObserver = null;
        mIsDestroyed = true;
    }

    @Override
    public String getUrl() {
        return UrlConstants.NTP_URL;
    }

    @Override
    public String getTitle() {
        return mTitle;
    }

    @Override
    public int getBackgroundColor() {
        return mBackgroundColor;
    }

    @Override
    public int getThemeColor() {
        return isLocationBarShownInNTP() ? mBackgroundColor : mThemeColor;
    }

    @Override
    public boolean needsToolbarShadow() {
        return !mSearchProviderHasLogo;
    }

    @Override
    public View getView() {
        return mNewTabPageView;
    }

    @Override
    public String getHost() {
        return UrlConstants.NTP_HOST;
    }

    @Override
    public void updateForUrl(String url) {
    }

    // InvalidationAwareThumbnailProvider

    @Override
    public boolean shouldCaptureThumbnail() {
        return mNewTabPageView.shouldCaptureThumbnail();
    }

    @Override
    public void captureThumbnail(Canvas canvas) {
        mNewTabPageView.captureThumbnail(canvas);
    }

    @VisibleForTesting
    public NewTabPageManager getManagerForTesting() {
        return mNewTabPageManager;
    }
}

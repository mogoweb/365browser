// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.customtabs;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Rect;
import android.os.IBinder;
import android.os.SystemClock;
import android.os.TransactionTooLargeException;
import android.support.customtabs.CustomTabsCallback;
import android.text.TextUtils;
import android.view.ContextMenu;
import android.view.Menu;

import org.chromium.base.Log;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.UrlUtilities;
import org.chromium.chrome.browser.WebContentsFactory;
import org.chromium.chrome.browser.banners.AppBannerManager;
import org.chromium.chrome.browser.contextmenu.ChromeContextMenuPopulator;
import org.chromium.chrome.browser.contextmenu.ContextMenuParams;
import org.chromium.chrome.browser.contextmenu.ContextMenuPopulator;
import org.chromium.chrome.browser.externalnav.ExternalNavigationDelegateImpl;
import org.chromium.chrome.browser.externalnav.ExternalNavigationHandler;
import org.chromium.chrome.browser.ssl.ConnectionSecurityLevel;
import org.chromium.chrome.browser.tab.ChromeTab;
import org.chromium.chrome.browser.tab.EmptyTabObserver;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabIdManager;
import org.chromium.chrome.browser.tabmodel.TabModel.TabLaunchType;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.content_public.browser.WebContents;
import org.chromium.ui.base.WindowAndroid;

import java.util.concurrent.TimeUnit;

/**
 * A chrome tab that is only used as a custom tab.
 */
public class CustomTab extends ChromeTab {
    private static class CustomTabObserver extends EmptyTabObserver {
        private CustomTabsConnection mCustomTabsConnection;
        private IBinder mSession;
        private long mIntentReceivedTimestamp;
        private long mPageLoadStartedTimestamp;

        private static final int STATE_RESET = 0;
        private static final int STATE_WAITING_LOAD_START = 1;
        private static final int STATE_WAITING_LOAD_FINISH = 2;
        private int mCurrentState;

        public CustomTabObserver(CustomTabsConnection customTabsConnection, IBinder session) {
            mCustomTabsConnection = customTabsConnection;
            mSession = session;
            resetPageLoadTracking();
        }

        /**
         * Tracks the next page load, with timestamp as the origin of time.
         */
        public void trackNextPageLoadFromTimestamp(long timestamp) {
            mIntentReceivedTimestamp = timestamp;
            mCurrentState = STATE_WAITING_LOAD_START;
        }

        @Override
        public void onLoadUrl(Tab tab, LoadUrlParams params, int loadType) {
            mCustomTabsConnection.registerLaunch(mSession, params.getUrl());
        }

        @Override
        public void onPageLoadStarted(Tab tab, String url) {
            if (mCurrentState == STATE_WAITING_LOAD_START) {
                mPageLoadStartedTimestamp = SystemClock.elapsedRealtime();
                mCurrentState = STATE_WAITING_LOAD_FINISH;
            } else if (mCurrentState == STATE_WAITING_LOAD_FINISH) {
                mCustomTabsConnection.notifyNavigationEvent(
                        mSession, CustomTabsCallback.NAVIGATION_ABORTED);
                mPageLoadStartedTimestamp = SystemClock.elapsedRealtime();
            }
            mCustomTabsConnection.notifyNavigationEvent(
                    mSession, CustomTabsCallback.NAVIGATION_STARTED);
        }

        @Override
        public void onShown(Tab tab) {
            mCustomTabsConnection.notifyNavigationEvent(
                    mSession, CustomTabsCallback.TAB_SHOWN);
        }

        @Override
        public void onPageLoadFinished(Tab tab) {
            long pageLoadFinishedTimestamp = SystemClock.elapsedRealtime();
            mCustomTabsConnection.notifyNavigationEvent(
                    mSession, CustomTabsCallback.NAVIGATION_FINISHED);
            // Both histograms (commit and PLT) are reported here, to make sure
            // that they are always recorded together, and that we only record
            // commits for successful navigations.
            if (mCurrentState == STATE_WAITING_LOAD_FINISH && mIntentReceivedTimestamp > 0) {
                long timeToPageLoadStartedMs = mPageLoadStartedTimestamp - mIntentReceivedTimestamp;
                long timeToPageLoadFinishedMs =
                        pageLoadFinishedTimestamp - mIntentReceivedTimestamp;
                // Same bounds and bucket count as "Startup.FirstCommitNavigationTime"
                RecordHistogram.recordCustomTimesHistogram(
                        "CustomTabs.IntentToFirstCommitNavigationTime", timeToPageLoadStartedMs,
                        1, TimeUnit.MINUTES.toMillis(1), TimeUnit.MILLISECONDS, 225);
                // Same bounds and bucket count as PLT histograms.
                RecordHistogram.recordCustomTimesHistogram("CustomTabs.IntentToPageLoadedTime",
                        timeToPageLoadFinishedMs, 10, TimeUnit.MINUTES.toMillis(10),
                        TimeUnit.MILLISECONDS, 100);
            }
            resetPageLoadTracking();
        }

        @Override
        public void onDidAttachInterstitialPage(Tab tab) {
            if (tab.getSecurityLevel() != ConnectionSecurityLevel.SECURITY_ERROR) return;
            resetPageLoadTracking();
            mCustomTabsConnection.notifyNavigationEvent(
                    mSession, CustomTabsCallback.NAVIGATION_FAILED);
        }

        @Override
        public void onPageLoadFailed(Tab tab, int errorCode) {
            resetPageLoadTracking();
            mCustomTabsConnection.notifyNavigationEvent(
                    mSession, CustomTabsCallback.NAVIGATION_FAILED);
        }

        private void resetPageLoadTracking() {
            mCurrentState = STATE_RESET;
            mIntentReceivedTimestamp = -1;
        }
    }

    private ExternalNavigationHandler mNavigationHandler;
    private CustomTabNavigationDelegate mNavigationDelegate;
    private TabChromeContextMenuItemDelegate
            mContextMenuDelegate = new TabChromeContextMenuItemDelegate() {
                @Override
                public boolean startDownload(String url, boolean isLink) {
                    // Behave similarly to ChromeTabChromeContextMenuItemDelegate in ChromeTab.
                    return !isLink || !shouldInterceptContextMenuDownload(url);
                }
            };

    private CustomTabObserver mTabObserver;
    private final boolean mEnableUrlBarHiding;
    private boolean mShouldReplaceCurrentEntry;

    /**
     * Construct an CustomTab. Note that url and referrer given here is only used to retrieve a
     * prerendered web contents if it exists. It might load a prerendered {@link WebContents} for
     * the URL, if {@link CustomTabsConnectionService} has successfully warmed up for the url.
     */
    public CustomTab(ChromeActivity activity, WindowAndroid windowAndroid, IBinder session,
            String url, String referrer, int parentTabId, boolean enableUrlBarHiding) {
        super(TabIdManager.getInstance().generateValidId(Tab.INVALID_TAB_ID), activity, false,
                windowAndroid, TabLaunchType.FROM_EXTERNAL_APP, parentTabId, null, null);
        mEnableUrlBarHiding = enableUrlBarHiding;
        CustomTabsConnection customTabsConnection =
                CustomTabsConnection.getInstance(activity.getApplication());
        WebContents webContents = customTabsConnection.takePrerenderedUrl(session, url, referrer);
        if (webContents == null) {
            webContents = customTabsConnection.takeSpareWebContents();
            // TODO(lizeb): Remove this once crbug.com/521729 is fixed.
            if (webContents != null) mShouldReplaceCurrentEntry = true;
        }
        if (webContents == null) {
            webContents = WebContentsFactory.createWebContents(isIncognito(), false);
        }
        initialize(webContents, activity.getTabContentManager(), false);
        getView().requestFocus();
        mTabObserver = new CustomTabObserver(customTabsConnection, session);
        addObserver(mTabObserver);
    }

    /**
     * Loads a URL and tracks its load time, from the timestamp of the intent arrival.
     *
     * @param params As in {@link Tab#loadUrl(LoadUrlParams)}.
     * @param timestamp Timestamp of the intent arrival, as returned by
     *                  {@link SystemClock#elapsedRealtime()}.
     */
    void loadUrlAndTrackFromTimestamp(LoadUrlParams params, long timestamp) {
        mTabObserver.trackNextPageLoadFromTimestamp(timestamp);
        if (mShouldReplaceCurrentEntry) params.setShouldReplaceCurrentEntry(true);
        mShouldReplaceCurrentEntry = false;
        loadUrl(params);
    }

    @Override
    protected InterceptNavigationDelegateImpl createInterceptNavigationDelegate() {
        mNavigationDelegate = new CustomTabNavigationDelegate(mActivity);
        mNavigationHandler = new ExternalNavigationHandler(mNavigationDelegate);
        return new InterceptNavigationDelegateImpl(mNavigationHandler);
    }

    /**
     * @return The {@link ExternalNavigationHandler} in this tab. For test purpose only.
     */
    @VisibleForTesting
    ExternalNavigationHandler getExternalNavigationHandler() {
        return mNavigationHandler;
    }

    @Override
    protected boolean isHidingTopControlsEnabled() {
        return mEnableUrlBarHiding && super.isHidingTopControlsEnabled();
    }

    /**
     * @return The {@link CustomTabNavigationDelegate} in this tab. For test purpose only.
     */
    @VisibleForTesting
    CustomTabNavigationDelegate getExternalNavigationDelegate() {
        return mNavigationDelegate;
    }

    @Override
    protected AppBannerManager createAppBannerManager() {
        return null;
    }

    @Override
    protected ContextMenuPopulator createContextMenuPopulator() {
        return new ChromeContextMenuPopulator(mContextMenuDelegate) {
            @Override
            public void buildContextMenu(ContextMenu menu, Context context,
                    ContextMenuParams params) {
                String linkUrl = params.getLinkUrl();
                if (linkUrl != null) linkUrl = linkUrl.trim();
                if (!TextUtils.isEmpty(linkUrl)) {
                    menu.add(Menu.NONE, org.chromium.chrome.R.id.contextmenu_copy_link_address,
                            Menu.NONE, org.chromium.chrome.R.string.contextmenu_copy_link_address);
                }

                String linkText = params.getLinkText();
                if (linkText != null) linkText = linkText.trim();
                if (!TextUtils.isEmpty(linkText)) {
                    menu.add(Menu.NONE, org.chromium.chrome.R.id.contextmenu_copy_link_text,
                            Menu.NONE, org.chromium.chrome.R.string.contextmenu_copy_link_text);
                }
                if (params.isImage()) {
                    menu.add(Menu.NONE, R.id.contextmenu_save_image, Menu.NONE,
                            R.string.contextmenu_save_image);
                    menu.add(Menu.NONE, R.id.contextmenu_open_image, Menu.NONE,
                            R.string.contextmenu_open_image);
                    menu.add(Menu.NONE, R.id.contextmenu_copy_image, Menu.NONE,
                            R.string.contextmenu_copy_image);
                    menu.add(Menu.NONE, R.id.contextmenu_copy_image_url, Menu.NONE,
                            R.string.contextmenu_copy_image_url);
                } else if (UrlUtilities.isDownloadableScheme(params.getLinkUrl())) {
                    // "Save link" is not shown for image.
                    menu.add(Menu.NONE, R.id.contextmenu_save_link_as, Menu.NONE,
                            R.string.contextmenu_save_link);
                }
            }
        };
    }

    /**
     * A custom external navigation delegate that forbids the intent picker from showing up.
     */
    static class CustomTabNavigationDelegate extends ExternalNavigationDelegateImpl {
        private static final String TAG = "cr.customtabs";
        private boolean mHasActivityStarted;

        /**
         * Constructs a new instance of {@link CustomTabNavigationDelegate}.
         */
        public CustomTabNavigationDelegate(ChromeActivity activity) {
            super(activity);
        }

        @Override
        public void startActivity(Intent intent) {
            super.startActivity(intent);
            mHasActivityStarted = true;
        }

        @Override
        public boolean startActivityIfNeeded(Intent intent) {
            boolean isExternalProtocol = !UrlUtilities.isAcceptedScheme(intent.getDataString());
            boolean hasDefaultHandler = hasDefaultHandler(intent);
            try {
                // For a url chrome can handle and there is no default set, handle it ourselves.
                if (!hasDefaultHandler && !isExternalProtocol) return false;
                // If android fails to find a handler, handle it ourselves.
                if (!getActivity().startActivityIfNeeded(intent, -1)) return false;

                mHasActivityStarted = true;
                return true;
            } catch (RuntimeException e) {
                logTransactionTooLargeOrRethrow(e, intent);
                return false;
            }
        }

        /**
         * Resolve the default external handler of an intent.
         * @return Whether the default external handler is found: if chrome turns out to be the
         *         default handler, this method will return false.
         */
        private boolean hasDefaultHandler(Intent intent) {
            try {
                ResolveInfo info = getActivity().getPackageManager().resolveActivity(intent, 0);
                if (info != null) {
                    final String chromePackage = getActivity().getPackageName();
                    // If a default handler is found and it is not chrome itself, fire the intent.
                    if (info.match != 0 && !chromePackage.equals(info.activityInfo.packageName)) {
                        return true;
                    }
                }
            } catch (RuntimeException e) {
                logTransactionTooLargeOrRethrow(e, intent);
            }
            return false;
        }

        /**
         * @return Whether an external activity has started to handle a url. For testing only.
         */
        @VisibleForTesting
        public boolean hasExternalActivityStarted() {
            return mHasActivityStarted;
        }

        private static void logTransactionTooLargeOrRethrow(RuntimeException e, Intent intent) {
            // See http://crbug.com/369574.
            if (e.getCause() instanceof TransactionTooLargeException) {
                Log.e(TAG, "Could not resolve Activity for intent " + intent.toString(), e);
            } else {
                throw e;
            }
        }
    }

    @Override
    protected TabChromeWebContentsDelegateAndroid createWebContentsDelegate() {
        return new TabChromeWebContentsDelegateAndroidImpl() {
            private String mTargetUrl;

            @Override
            public boolean shouldResumeRequestsForCreatedWindow() {
                return true;
            }

            @Override
            public void webContentsCreated(WebContents sourceWebContents, long openerRenderFrameId,
                    String frameName, String targetUrl, WebContents newWebContents) {
                super.webContentsCreated(
                        sourceWebContents, openerRenderFrameId, frameName, targetUrl,
                        newWebContents);
                mTargetUrl = targetUrl;
            }

            @Override
            public boolean addNewContents(WebContents sourceWebContents, WebContents webContents,
                    int disposition, Rect initialPosition, boolean userGesture) {
                assert mTargetUrl != null;
                loadUrlAndTrackFromTimestamp(
                        new LoadUrlParams(mTargetUrl), SystemClock.elapsedRealtime());
                mTargetUrl = null;
                return false;
            }

            @Override
            protected void bringActivityToForeground() {
                // No-op here. If client's task is in background Chrome is unable to foreground it.
            }
        };
    }
}

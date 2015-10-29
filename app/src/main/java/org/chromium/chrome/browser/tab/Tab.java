// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tab;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.provider.Browser;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.FrameLayout;

import org.chromium.base.ActivityState;
import org.chromium.base.ApplicationState;
import org.chromium.base.ApplicationStatus;
import org.chromium.base.ObserverList;
import org.chromium.base.ObserverList.RewindableIterator;
import org.chromium.base.TraceEvent;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.AccessibilityUtil;
import org.chromium.chrome.browser.ChromeWebContentsDelegateAndroid;
import org.chromium.chrome.browser.FrozenNativePage;
import org.chromium.chrome.browser.IntentHandler.TabOpenType;
import org.chromium.chrome.browser.NativePage;
import org.chromium.chrome.browser.RepostFormWarningDialog;
import org.chromium.chrome.browser.SwipeRefreshHandler;
import org.chromium.chrome.browser.TabState;
import org.chromium.chrome.browser.TabState.WebContentsState;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.chrome.browser.WebContentsFactory;
import org.chromium.chrome.browser.banners.AppBannerManager;
import org.chromium.chrome.browser.compositor.layouts.content.TabContentManager;
import org.chromium.chrome.browser.contextmenu.ChromeContextMenuItemDelegate;
import org.chromium.chrome.browser.contextmenu.ChromeContextMenuPopulator;
import org.chromium.chrome.browser.contextmenu.ContextMenuParams;
import org.chromium.chrome.browser.contextmenu.ContextMenuPopulator;
import org.chromium.chrome.browser.contextmenu.ContextMenuPopulatorWrapper;
import org.chromium.chrome.browser.contextmenu.EmptyChromeContextMenuItemDelegate;
import org.chromium.chrome.browser.fullscreen.FullscreenManager;
import org.chromium.chrome.browser.help.HelpAndFeedback;
import org.chromium.chrome.browser.infobar.InfoBarContainer;
import org.chromium.chrome.browser.metrics.UmaSessionStats;
import org.chromium.chrome.browser.metrics.UmaUtils;
import org.chromium.chrome.browser.printing.TabPrinter;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.snackbar.SnackbarManager;
import org.chromium.chrome.browser.ssl.ConnectionSecurity;
import org.chromium.chrome.browser.ssl.ConnectionSecurityLevel;
import org.chromium.chrome.browser.tab.TabUma.TabCreationState;
import org.chromium.chrome.browser.tabmodel.TabModel.TabLaunchType;
import org.chromium.chrome.browser.tabmodel.TabModel.TabSelectionType;
import org.chromium.chrome.browser.tabmodel.TabModelBase;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.util.FeatureUtilities;
import org.chromium.components.navigation_interception.InterceptNavigationDelegate;
import org.chromium.content.browser.ContentView;
import org.chromium.content.browser.ContentViewClient;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.content_public.browser.InvalidateTypes;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.content_public.browser.WebContents;
import org.chromium.content_public.browser.WebContentsObserver;
import org.chromium.content_public.common.Referrer;
import org.chromium.content_public.common.TopControlsState;
import org.chromium.printing.PrintManagerDelegateImpl;
import org.chromium.printing.PrintingController;
import org.chromium.printing.PrintingControllerImpl;
import org.chromium.ui.WindowOpenDisposition;
import org.chromium.ui.base.Clipboard;
import org.chromium.ui.base.LocalizationUtils;
import org.chromium.ui.base.PageTransition;
import org.chromium.ui.base.WindowAndroid;
import org.chromium.ui.gfx.DeviceDisplayInfo;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * The basic Java representation of a tab.  Contains and manages a {@link ContentView}.
 *
 * Tab provides common functionality for ChromeShell Tab as well as Chrome on Android's
 * tab. It is intended to be extended either on Java or both Java and C++, with ownership managed
 * by this base class.
 *
 * Extending just Java:
 *  - Just extend the class normally.  Do not override initializeNative().
 * Extending Java and C++:
 *  - Because of the inner-workings of JNI, the subclass is responsible for constructing the native
 *    subclass, which in turn constructs TabAndroid (the native counterpart to Tab), which in
 *    turn sets the native pointer for Tab.  For destruction, subclasses in Java must clear
 *    their own native pointer reference, but Tab#destroy() will handle deleting the native
 *    object.
 */
public class Tab implements ViewGroup.OnHierarchyChangeListener,
        View.OnSystemUiVisibilityChangeListener {
    public static final int INVALID_TAB_ID = -1;
    private static final long INVALID_TIMESTAMP = -1;

    // TabRendererCrashStatus defined in tools/metrics/histograms/histograms.xml.
    private static final int TAB_RENDERER_CRASH_STATUS_SHOWN_IN_FOREGROUND_APP = 0;
    private static final int TAB_RENDERER_CRASH_STATUS_HIDDEN_IN_FOREGROUND_APP = 1;
    private static final int TAB_RENDERER_CRASH_STATUS_HIDDEN_IN_BACKGROUND_APP = 2;
    private static final int TAB_RENDERER_CRASH_STATUS_MAX = 3;

    /**
     * The required page load percentage for the page to be considered ready assuming the
     * TextureView is also ready.
     */
    private static final int CONSIDERED_READY_LOAD_PERCENTAGE = 100;

    /** Used for logging. */
    private static final String TAG = "Tab";

    private long mNativeTabAndroid;

    /** Unique id of this tab (within its container). */
    private final int mId;

    /** Whether or not this tab is an incognito tab. */
    private final boolean mIncognito;

    /**
     * An Application {@link Context}.  Unlike {@link #mContext}, this is the only one that is
     * publicly exposed to help prevent leaking the {@link Activity}.
     */
    private final Context mApplicationContext;

    /**
     * The {@link Context} used to create {@link View}s and other Android components.  Unlike
     * {@link #mApplicationContext}, this is not publicly exposed to help prevent leaking the
     * {@link Activity}.
     */
    private final Context mContext;

    /** Gives {@link Tab} a way to interact with the Android window. */
    private final WindowAndroid mWindowAndroid;

    /** Whether or not this {@link Tab} is initialized and should be interacted with. */
    private boolean mIsInitialized;

    /** The current native page (e.g. chrome-native://newtab), or {@code null} if there is none. */
    private NativePage mNativePage;

    /** InfoBar container to show InfoBars for this tab. */
    private InfoBarContainer mInfoBarContainer;

    /** Manages app banners shown for this tab. */
    private AppBannerManager mAppBannerManager;

    /** Controls overscroll pull-to-refresh behavior for this tab. */
    private SwipeRefreshHandler mSwipeRefreshHandler;

    /** The sync id of the Tab if session sync is enabled. */
    private int mSyncId;

    /** {@link ContentViewCore} showing the current page, or {@code null} if the tab is frozen. */
    private ContentViewCore mContentViewCore;

    /** The parent view of the ContentView and the InfoBarContainer. */
    private FrameLayout mContentViewParent;

    /** A list of Tab observers.  These are used to broadcast Tab events to listeners. */
    private final ObserverList<TabObserver> mObservers = new ObserverList<TabObserver>();

    /**
     * A list of {@link ContentViewCore} overlay objects that are managed by external components but
     * need to be sized and rendered along side this {@link Tab}s content.
     */
    private final List<ContentViewCore> mOverlayContentViewCores = new ArrayList<ContentViewCore>();

    // Content layer Observers and Delegates
    private ContentViewClient mContentViewClient;
    private WebContentsObserver mWebContentsObserver;
    private TabChromeWebContentsDelegateAndroid mWebContentsDelegate;

    /**
     * If this tab was opened from another tab, store the id of the tab that
     * caused it to be opened so that we can activate it when this tab gets
     * closed.
     */
    private int mParentId = INVALID_TAB_ID;

    /**
     * If this tab was opened from another tab in another Activity, this is the Intent that can be
     * fired to bring the parent Activity back.
     * TODO(dfalcantara): Remove this mechanism when we have a global TabManager.
     */
    private Intent mParentIntent;

    /**
     * Whether the tab should be grouped with its parent tab.
     */
    private boolean mGroupedWithParent = true;

    private boolean mIsClosing;
    private boolean mIsShowingErrorPage;

    private Bitmap mFavicon;

    private String mFaviconUrl;

    /**
     * The number of pixel of 16DP.
     */
    private int mNumPixel16DP;

    /** Whether or not the TabState has changed. */
    private boolean mIsTabStateDirty = true;

    /**
     * Saves how this tab was launched (from a link, external app, etc) so that
     * we can determine the different circumstances in which it should be
     * closed. For example, a tab opened from an external app should be closed
     * when the back stack is empty and the user uses the back hardware key. A
     * standard tab however should be kept open and the entire activity should
     * be moved to the background.
     */
    private final TabLaunchType mLaunchType;

    /**
     * Navigation state of the WebContents as returned by nativeGetContentsStateAsByteBuffer(),
     * stored to be inflated on demand using unfreezeContents(). If this is not null, there is no
     * WebContents around. Upon tab switch WebContents will be unfrozen and the variable will be set
     * to null.
     */
    private WebContentsState mFrozenContentsState;

    /**
     * URL load to be performed lazily when the Tab is next shown.
     */
    private LoadUrlParams mPendingLoadParams;

    /**
     * URL of the page currently loading. Used as a fall-back in case tab restore fails.
     */
    private String mUrl;

    /**
     * The external application that this Tab is associated with (null if not associated with any
     * app). Allows reusing of tabs opened from the same application.
     */
    private String mAppAssociatedWith;

    /**
     * Keeps track of whether the Tab should be kept in the TabModel after the user hits "back".
     * Used by Document mode to keep track of whether we want to remove the tab when user hits back.
     */
    private boolean mShouldPreserve;

    /**
     * Indicates if the tab needs to be reloaded upon next display. This is set to true when the tab
     * crashes while in background, or if a speculative restore in background gets cancelled before
     * it completes.
     */
    private boolean mNeedsReload;

    /**
     * True while a page load is in progress.
     */
    private boolean mIsLoading;

    /**
     * True while a restore page load is in progress.
     */
    private boolean mIsBeingRestored;

    /**
     * Whether or not the Tab is currently visible to the user.
     */
    private boolean mIsHidden = true;

    /**
     * The last time this tab was shown or the time of its initialization if it wasn't yet shown.
     */
    private long mTimestampMillis = INVALID_TIMESTAMP;

    /**
     * Title of the ContentViews webpage.Always update mTitle through updateTitle() so that it also
     * updates mIsTitleDirectionRtl correctly.
     */
    private String mTitle;

    /**
     * Indicates if mTitle should be displayed from right to left.
     */
    private boolean mIsTitleDirectionRtl;

    /**
     * The mInterceptNavigationDelegate will be consulted for top-level frame navigations. This
     * allows presenting the intent picker to the user so that a native Android application can be
     * used if available.
     */
    private InterceptNavigationDelegate mInterceptNavigationDelegate;

    private FullscreenManager mFullscreenManager;
    private float mPreviousFullscreenTopControlsOffsetY = Float.NaN;
    private float mPreviousFullscreenContentOffsetY = Float.NaN;
    private float mPreviousFullscreenOverdrawBottomHeight = Float.NaN;
    private int mFullscreenHungRendererToken = FullscreenManager.INVALID_TOKEN;

    /**
     * The UMA object used to report stats for this tab. Note that this may be null under certain
     * conditions, such as incognito mode.
     */
    private TabUma mTabUma;

    /**
     * Reference to the current sadTabView if one is defined.
     */
    private View mSadTabView;

    private final int mDefaultThemeColor;
    private int mThemeColor;

    /**
     * A default {@link ChromeContextMenuItemDelegate} that supports some of the context menu
     * functionality.
     */
    protected class TabChromeContextMenuItemDelegate
            extends EmptyChromeContextMenuItemDelegate {
        private final Clipboard mClipboard;

        /**
         * Builds a {@link TabChromeContextMenuItemDelegate} instance.
         */
        public TabChromeContextMenuItemDelegate() {
            mClipboard = new Clipboard(getApplicationContext());
        }

        @Override
        public boolean isIncognito() {
            return mIncognito;
        }

        @Override
        public void onSaveToClipboard(String text, int clipboardType) {
            mClipboard.setText(text, text);
        }

        @Override
        public void onSaveImageToClipboard(String url) {
            mClipboard.setHTMLText("<img src=\"" + url + "\">", url, url);
        }

        @Override
        public void onReloadIgnoringCache() {
            reloadIgnoringCache();
        }

        @Override
        public void onLoadOriginalImage() {
            if (mNativeTabAndroid != 0) nativeLoadOriginalImage(mNativeTabAndroid);
        }

        @Override
        public String getPageUrl() {
            return getUrl();
        }

        @Override
        public void onOpenImageUrl(String url, Referrer referrer) {
            LoadUrlParams loadUrlParams = new LoadUrlParams(url);
            loadUrlParams.setTransitionType(PageTransition.LINK);
            loadUrlParams.setReferrer(referrer);
            loadUrl(loadUrlParams);
        }

        @Override
        public void onSearchByImageInNewTab() {
            triggerSearchByImage();
        }
    }

    /**
     * A basic {@link ChromeWebContentsDelegateAndroid} that forwards some calls to the registered
     * {@link TabObserver}s.  Meant to be overridden by subclasses.
     */
    public class TabChromeWebContentsDelegateAndroid
            extends ChromeWebContentsDelegateAndroid {
        @Override
        public void onLoadProgressChanged(int progress) {
            notifyLoadProgress(progress);
        }

        @Override
        public void onLoadStarted() {
            for (TabObserver observer : mObservers) observer.onLoadStarted(Tab.this);
        }

        @Override
        public void onLoadStopped() {
            for (TabObserver observer : mObservers) observer.onLoadStopped(Tab.this);
        }

        @Override
        public void onUpdateUrl(String url) {
            for (TabObserver observer : mObservers) observer.onUpdateUrl(Tab.this, url);
        }

        @Override
        public void showRepostFormWarningDialog() {
            // When the dialog is visible, keeping the refresh animation active
            // in the background is distracting and unnecessary (and likely to
            // jank when the dialog is shown).
            if (mSwipeRefreshHandler != null) {
                mSwipeRefreshHandler.reset();
            }
            RepostFormWarningDialog warningDialog = new RepostFormWarningDialog(
                    new Runnable() {
                        @Override
                        public void run() {
                            getWebContents().getNavigationController().cancelPendingReload();
                        }
                    }, new Runnable() {
                        @Override
                        public void run() {
                            getWebContents().getNavigationController().continuePendingReload();
                        }
                    });
            Activity activity = (Activity) mContext;
            warningDialog.show(activity.getFragmentManager(), null);
        }

        @Override
        public void toggleFullscreenModeForTab(boolean enableFullscreen) {
            if (mFullscreenManager != null) {
                mFullscreenManager.setPersistentFullscreenMode(enableFullscreen);
            }

            for (TabObserver observer : mObservers) {
                observer.onToggleFullscreenMode(Tab.this, enableFullscreen);
            }
        }

        @Override
        public void navigationStateChanged(int flags) {
            if ((flags & InvalidateTypes.TITLE) != 0) {
                // Update cached title then notify observers.
                updateTitle();
            }
            if ((flags & InvalidateTypes.URL) != 0) {
                for (TabObserver observer : mObservers) observer.onUrlUpdated(Tab.this);
            }
        }

        @Override
        public void visibleSSLStateChanged() {
            for (TabObserver observer : mObservers) observer.onSSLStateUpdated(Tab.this);
        }

        @Override
        public void webContentsCreated(WebContents sourceWebContents, long openerRenderFrameId,
                String frameName, String targetUrl, WebContents newWebContents) {
            for (TabObserver observer : mObservers) {
                observer.webContentsCreated(Tab.this, sourceWebContents, openerRenderFrameId,
                        frameName, targetUrl, newWebContents);
            }
        }

        @Override
        public void rendererUnresponsive() {
            super.rendererUnresponsive();
            if (mFullscreenManager == null) return;
            mFullscreenHungRendererToken =
                    mFullscreenManager.showControlsPersistentAndClearOldToken(
                            mFullscreenHungRendererToken);
        }

        @Override
        public void rendererResponsive() {
            super.rendererResponsive();
            if (mFullscreenManager == null) return;
            mFullscreenManager.hideControlsPersistent(mFullscreenHungRendererToken);
            mFullscreenHungRendererToken = FullscreenManager.INVALID_TOKEN;
        }

        @Override
        public boolean isFullscreenForTabOrPending() {
            return mFullscreenManager == null
                    ? false : mFullscreenManager.getPersistentFullscreenMode();
        }

        @Override
        public void openNewTab(String url, String extraHeaders, byte[] postData, int disposition,
                boolean isRendererInitiated) {
            Tab.this.openNewTab(
                    url, extraHeaders, postData, disposition, true, isRendererInitiated);
        }
    }

    /**
     * ContentViewClient that provides basic tab functionality and is meant to be extended
     * by child classes.
     */
    protected class TabContentViewClient extends ContentViewClient {
        @Override
        public void onUpdateTitle(String title) {
            updateTitle(title);
        }

        @Override
        public void onContextualActionBarShown() {
            for (TabObserver observer : mObservers) {
                observer.onContextualActionBarVisibilityChanged(Tab.this, true);
            }
        }

        @Override
        public void onContextualActionBarHidden() {
            for (TabObserver observer : mObservers) {
                observer.onContextualActionBarVisibilityChanged(Tab.this, false);
            }
        }

        @Override
        public void onImeEvent() {
            // Some text was set in the page. Don't reuse it if a tab is
            // open from the same external application, we might lose some
            // user data.
            mAppAssociatedWith = null;
        }

        @Override
        public void onFocusedNodeEditabilityChanged(boolean editable) {
            if (getFullscreenManager() == null) return;
            updateFullscreenEnabledState();
        }
    }

    private class TabContextMenuPopulator extends ContextMenuPopulatorWrapper {
        public TabContextMenuPopulator(ContextMenuPopulator populator) {
            super(populator);
        }

        @Override
        public void buildContextMenu(ContextMenu menu, Context context, ContextMenuParams params) {
            super.buildContextMenu(menu, context, params);
            for (TabObserver observer : mObservers) observer.onContextMenuShown(Tab.this, menu);
        }
    }

    private class TabWebContentsObserver extends WebContentsObserver {
        public TabWebContentsObserver(WebContents webContents) {
            super(webContents);
        }

        @Override
        public void renderProcessGone(boolean processWasOomProtected) {
            Log.i(TAG, "renderProcessGone() for tab id: " + getId()
                    + ", oom protected: " + Boolean.toString(processWasOomProtected)
                    + ", already needs reload: " + Boolean.toString(needsReload()));
            // Do nothing for subsequent calls that happen while the tab remains crashed. This
            // can occur when the tab is in the background and it shares the renderer with other
            // tabs. After the renderer crashes, the WebContents of its tabs are still around
            // and they still share the RenderProcessHost. When one of the tabs reloads spawning
            // a new renderer for the shared RenderProcessHost and the new renderer crashes
            // again, all tabs sharing this renderer will be notified about the crash (including
            // potential background tabs that did not reload yet).
            if (needsReload() || isShowingSadTab()) return;

            int activityState = ApplicationStatus.getStateForActivity(
                    mWindowAndroid.getActivity().get());
            int rendererCrashStatus = TAB_RENDERER_CRASH_STATUS_MAX;
            if (!processWasOomProtected
                    || activityState == ActivityState.PAUSED
                    || activityState == ActivityState.STOPPED
                    || activityState == ActivityState.DESTROYED) {
                // The tab crashed in background or was killed by the OS out-of-memory killer.
                //setNeedsReload(true);
                mNeedsReload = true;
                if (ApplicationStatus.getStateForApplication()
                        == ApplicationState.HAS_RUNNING_ACTIVITIES) {
                    rendererCrashStatus = TAB_RENDERER_CRASH_STATUS_HIDDEN_IN_FOREGROUND_APP;
                } else {
                    rendererCrashStatus = TAB_RENDERER_CRASH_STATUS_HIDDEN_IN_BACKGROUND_APP;
                }
            } else {
                rendererCrashStatus = TAB_RENDERER_CRASH_STATUS_SHOWN_IN_FOREGROUND_APP;
                showSadTab();
                // TODO(jaekyun): This isn't needed anymore because a histogram
                // "Tab.RendererCrashStatus" is recorded.
                UmaSessionStats.logRendererCrash(mWindowAndroid.getActivity().get());
            }
            RecordHistogram.recordEnumeratedHistogram(
                    "Tab.RendererCrashStatus", rendererCrashStatus, TAB_RENDERER_CRASH_STATUS_MAX);

            mIsLoading = false;
            mIsBeingRestored = false;
            handleTabCrash();

            boolean sadTabShown = isShowingSadTab();
            RewindableIterator<TabObserver> observers = getTabObservers();
            while (observers.hasNext()) {
                observers.next().onCrash(Tab.this, sadTabShown);
            }
        }

        @Override
        public void navigationEntryCommitted() {
            if (getNativePage() != null) {
                pushNativePageStateToNavigationEntry();
            }
        }

        @Override
        public void didFinishLoad(long frameId, String validatedUrl, boolean isMainFrame) {
            if (isMainFrame) didFinishPageLoad();
        }

        @Override
        public void didFailLoad(boolean isProvisionalLoad, boolean isMainFrame, int errorCode,
                String description, String failingUrl, boolean wasIgnoredByHandler) {
            for (TabObserver observer : mObservers) {
                observer.onDidFailLoad(Tab.this, isProvisionalLoad, isMainFrame, errorCode,
                        description, failingUrl);
            }

            if (isMainFrame) didFailPageLoad(errorCode);
        }

        @Override
        public void didStartProvisionalLoadForFrame(long frameId, long parentFrameId,
                boolean isMainFrame, String validatedUrl, boolean isErrorPage,
                boolean isIframeSrcdoc) {
            if (isMainFrame) didStartPageLoad(validatedUrl, isErrorPage);

            for (TabObserver observer : mObservers) {
                observer.onDidStartProvisionalLoadForFrame(Tab.this, frameId, parentFrameId,
                        isMainFrame, validatedUrl, isErrorPage, isIframeSrcdoc);
            }
        }

        @Override
        public void didCommitProvisionalLoadForFrame(long frameId, boolean isMainFrame, String url,
                int transitionType) {
            if (isMainFrame && UmaUtils.isRunningApplicationStart()) {
                nativeRecordStartupToCommitUma();
                UmaUtils.setRunningApplicationStart(false);
            }

            if (isMainFrame) {
                mIsTabStateDirty = true;
                updateTitle();
            }

            for (TabObserver observer : mObservers) {
                observer.onDidCommitProvisionalLoadForFrame(
                        Tab.this, frameId, isMainFrame, url, transitionType);
            }

            notifyPageUrlChanged();
        }

        @Override
        public void didNavigateMainFrame(String url, String baseUrl,
                boolean isNavigationToDifferentPage, boolean isFragmentNavigation, int statusCode) {
            FullscreenManager fullscreenManager = getFullscreenManager();
            if (isNavigationToDifferentPage && fullscreenManager != null) {
                fullscreenManager.setPersistentFullscreenMode(false);
            }

            for (TabObserver observer : mObservers) {
                observer.onDidNavigateMainFrame(
                        Tab.this, url, baseUrl, isNavigationToDifferentPage,
                        isFragmentNavigation, statusCode);
            }

            if (mSwipeRefreshHandler != null) {
                mSwipeRefreshHandler.didStopRefreshing();
            }
        }

        @Override
        public void didFirstVisuallyNonEmptyPaint() {
            for (TabObserver observer : mObservers) {
                observer.didFirstVisuallyNonEmptyPaint(Tab.this);
            }
        }

        @Override
        public void didChangeThemeColor(int color) {
            int securityLevel = getSecurityLevel();
            if (securityLevel == ConnectionSecurityLevel.SECURITY_ERROR
                    || securityLevel == ConnectionSecurityLevel.SECURITY_WARNING
                    || securityLevel == ConnectionSecurityLevel.SECURITY_POLICY_WARNING) {
                color = mDefaultThemeColor;
            }
            if (isShowingInterstitialPage()) color = mDefaultThemeColor;
            if (!FeatureUtilities.isDocumentMode(mApplicationContext)) color = mDefaultThemeColor;
            if (color == Color.TRANSPARENT) color = mDefaultThemeColor;
            color |= 0xFF000000;
            if (mThemeColor == color) return;
            mThemeColor = color;
            for (TabObserver observer : mObservers) {
                observer.onDidChangeThemeColor(Tab.this, mThemeColor);
            }
        }

        @Override
        public void didAttachInterstitialPage() {
            getInfoBarContainer().setVisibility(View.INVISIBLE);
            showRenderedPage();
            didChangeThemeColor(mDefaultThemeColor);

            for (TabObserver observer : mObservers) {
                observer.onDidAttachInterstitialPage(Tab.this);
            }
            notifyLoadProgress(getProgress());

            updateFullscreenEnabledState();
        }

        @Override
        public void didDetachInterstitialPage() {
            getInfoBarContainer().setVisibility(View.VISIBLE);
            didChangeThemeColor(getWebContents().getThemeColor(mDefaultThemeColor));

            for (TabObserver observer : mObservers) {
                observer.onDidDetachInterstitialPage(Tab.this);
            }
            notifyLoadProgress(getProgress());

            updateFullscreenEnabledState();
        }

        @Override
        public void didStartNavigationToPendingEntry(String url) {
            for (TabObserver observer : mObservers) {
                observer.onDidStartNavigationToPendingEntry(Tab.this, url);
            }
        }
    }

    /**
     * Creates an instance of a {@link Tab} with no id.
     * @param incognito Whether or not this tab is incognito.
     * @param context   An instance of a {@link Context}.
     * @param window    An instance of a {@link WindowAndroid}.
     */
    @VisibleForTesting
    public Tab(boolean incognito, Context context, WindowAndroid window) {
        this(INVALID_TAB_ID, incognito, context, window);
    }

    /**
     * Creates an instance of a {@link Tab}.
     * @param id        The id this tab should be identified with.
     * @param incognito Whether or not this tab is incognito.
     * @param context   An instance of a {@link Context}.
     * @param window    An instance of a {@link WindowAndroid}.
     */
    public Tab(int id, boolean incognito, Context context, WindowAndroid window) {
        this(id, INVALID_TAB_ID, incognito, context, window, null);
    }

    /**
     * Creates an instance of a {@link Tab}.
     * @param id        The id this tab should be identified with.
     * @param parentId  The id id of the tab that caused this tab to be opened.
     * @param incognito Whether or not this tab is incognito.
     * @param context   An instance of a {@link Context}.
     * @param window    An instance of a {@link WindowAndroid}.
     */
    public Tab(int id, int parentId, boolean incognito, Context context, WindowAndroid window,
            TabLaunchType type) {
        this(id, parentId, incognito, context, window, type, null);
    }

    /**
     * Creates an instance of a {@link Tab}.
     * @param id          The id this tab should be identified with.
     * @param parentId    The id id of the tab that caused this tab to be opened.
     * @param incognito   Whether or not this tab is incognito.
     * @param context     An instance of a {@link Context}.
     * @param window      An instance of a {@link WindowAndroid}.
     * @param frozenState State containing information about this Tab, if it was persisted.
     */
    public Tab(int id, int parentId, boolean incognito, Context context, WindowAndroid window,
            TabLaunchType type, TabState frozenState) {
        // We need a valid Activity Context to build the ContentView with.
        assert context == null || context instanceof Activity;

        mId = TabIdManager.getInstance().generateValidId(id);
        mParentId = parentId;
        mIncognito = incognito;
        // TODO(dtrainor): Only store application context here.
        mContext = context;
        mApplicationContext = context != null ? context.getApplicationContext() : null;
        mWindowAndroid = window;
        mLaunchType = type;
        if (mContext != null) {
            mNumPixel16DP = (int) (DeviceDisplayInfo.create(mContext).getDIPScale() * 16);
            Resources resources = mContext.getResources();
            mDefaultThemeColor = mIncognito ? resources.getColor(R.color.incognito_primary_color)
                    : resources.getColor(R.color.default_primary_color);
            mThemeColor = mDefaultThemeColor;
        } else {
            mDefaultThemeColor = 0;
            mNumPixel16DP = 16;
        }

        // Restore data from the TabState, if it existed.
        if (frozenState == null) {
            assert type != TabLaunchType.FROM_RESTORE;
        } else {
            assert type == TabLaunchType.FROM_RESTORE;
            restoreFieldsFromState(frozenState);
        }
    }

    /**
     * Sets the mTabUma object for stats reporting.
     * @param tabUma TabUma object to use to report UMA stats.
     */
    protected void setTabUma(TabUma tabUma) {
        mTabUma = tabUma;
    }

    /**
     * Restores member fields from the given TabState.
     * @param state TabState containing information about this Tab.
     */
    protected void restoreFieldsFromState(TabState state) {
        assert state != null;
        mAppAssociatedWith = state.openerAppId;
        mFrozenContentsState = state.contentsState;
        mSyncId = (int) state.syncId;
        mShouldPreserve = state.shouldPreserve;
        mTimestampMillis = state.timestampMillis;
        mUrl = state.getVirtualUrlFromState();

        mTitle = state.getDisplayTitleFromState();
        mIsTitleDirectionRtl = LocalizationUtils.getFirstStrongCharacterDirection(mTitle)
                == LocalizationUtils.RIGHT_TO_LEFT;
    }

    /**
     * Adds a {@link TabObserver} to be notified on {@link Tab} changes.
     * @param observer The {@link TabObserver} to add.
     */
    public void addObserver(TabObserver observer) {
        mObservers.addObserver(observer);
    }

    /**
     * Removes a {@link TabObserver}.
     * @param observer The {@link TabObserver} to remove.
     */
    public void removeObserver(TabObserver observer) {
        mObservers.removeObserver(observer);
    }

    /**
     * @return Whether or not this tab has a previous navigation entry.
     */
    public boolean canGoBack() {
        return getWebContents() != null && getWebContents().getNavigationController().canGoBack();
    }

    /**
     * @return Whether or not this tab has a navigation entry after the current one.
     */
    public boolean canGoForward() {
        return getWebContents() != null && getWebContents().getNavigationController()
                .canGoForward();
    }

    /**
     * Goes to the navigation entry before the current one.
     */
    public void goBack() {
        if (getWebContents() != null) getWebContents().getNavigationController().goBack();
    }

    /**
     * Goes to the navigation entry after the current one.
     */
    public void goForward() {
        if (getWebContents() != null) getWebContents().getNavigationController().goForward();
    }

    /**
     * Loads the current navigation if there is a pending lazy load (after tab restore).
     */
    public void loadIfNecessary() {
        if (getWebContents() != null) getWebContents().getNavigationController().loadIfNecessary();
    }

    /**
     * Requests the current navigation to be loaded upon the next call to loadIfNecessary().
     */
    protected void requestRestoreLoad() {
        if (getWebContents() != null) {
            getWebContents().getNavigationController().requestRestoreLoad();
        }
    }

    /**
     * Causes this tab to navigate to the specified URL.
     * @param params parameters describing the url load. Note that it is important to set correct
     *               page transition as it is used for ranking URLs in the history so the omnibox
     *               can report suggestions correctly.
     * @return FULL_PRERENDERED_PAGE_LOAD or PARTIAL_PRERENDERED_PAGE_LOAD if the page has been
     *         prerendered. DEFAULT_PAGE_LOAD if it had not.
     */
    public int loadUrl(LoadUrlParams params) {
        try {
            TraceEvent.begin("Tab.loadUrl");
            removeSadTabIfPresent();

            // Clear the app association if the user navigated to a different page from the omnibox.
            if ((params.getTransitionType() & PageTransition.FROM_ADDRESS_BAR)
                    == PageTransition.FROM_ADDRESS_BAR) {
                mAppAssociatedWith = null;
            }

            // We load the URL from the tab rather than directly from the ContentView so the tab has
            // a chance of using a prerenderer page is any.
            int loadType = nativeLoadUrl(mNativeTabAndroid, params.getUrl(),
                    params.getVerbatimHeaders(), params.getPostData(), params.getTransitionType(),
                    params.getReferrer() != null ? params.getReferrer().getUrl() : null,
                    // Policy will be ignored for null referrer url, 0 is just a placeholder.
                    // TODO(ppi): Should we pass Referrer jobject and add JNI methods to read it
                    //            from the native?
                    params.getReferrer() != null ? params.getReferrer().getPolicy() : 0,
                    params.getIsRendererInitiated(), params.getShouldReplaceCurrentEntry(),
                    params.getIntentReceivedTimestamp(), params.getHasUserGesture());

            for (TabObserver observer : mObservers) {
                observer.onLoadUrl(this, params, loadType);
            }
            return loadType;
        } finally {
            TraceEvent.end("Tab.loadUrl");
        }
    }

    /**
     * @return Whether or not the {@link Tab} is currently showing an interstitial page, such as
     *         a bad HTTPS page.
     */
    public boolean isShowingInterstitialPage() {
        return getWebContents() != null && getWebContents().isShowingInterstitialPage();
    }

    /**
     * @return Whether the {@link Tab} is currently showing an error page.
     */
    public boolean isShowingErrorPage() {
        return mIsShowingErrorPage;
    }

    /**
     * @return Whether or not the tab has something valid to render.
     */
    public boolean isReady() {
        return mNativePage != null || (getWebContents() != null && getWebContents().isReady());
    }

    /**
     * @return The {@link View} displaying the current page in the tab. This might be a
     *         native view or a placeholder view for content rendered by the compositor.
     *         This can be {@code null}, if the tab is frozen or being initialized or destroyed.
     */
    public View getView() {
        return mNativePage != null ? mNativePage.getView() : mContentViewParent;
    }

    /**
     * @return The width of the content of this tab.  Can be 0 if there is no content.
     */
    public int getWidth() {
        View view = getView();
        return view != null ? view.getWidth() : 0;
    }

    /**
     * @return The height of the content of this tab.  Can be 0 if there is no content.
     */
    public int getHeight() {
        View view = getView();
        return view != null ? view.getHeight() : 0;
    }

    /**
     * @return The application {@link Context} associated with this tab.
     */
    protected Context getApplicationContext() {
        return mApplicationContext;
    }

    /**
     * @return The infobar container.
     */
    public final InfoBarContainer getInfoBarContainer() {
        return mInfoBarContainer;
    }

    /** @return An opaque "state" object that can be persisted to storage. */
    public TabState getState() {
        if (!isInitialized()) return null;
        TabState tabState = new TabState();
        tabState.contentsState = getWebContentsState();
        tabState.openerAppId = mAppAssociatedWith;
        tabState.parentId = mParentId;
        tabState.shouldPreserve = mShouldPreserve;
        tabState.syncId = mSyncId;
        tabState.timestampMillis = mTimestampMillis;
        return tabState;
    }

    /** @return WebContentsState representing the state of the WebContents (navigations, etc.) */
    public WebContentsState getFrozenContentsState() {
        return mFrozenContentsState;
    }

    /** Returns an object representing the state of the Tab's WebContents. */
    protected TabState.WebContentsState getWebContentsState() {
        if (mFrozenContentsState != null) return mFrozenContentsState;

        // Native call returns null when buffer allocation needed to serialize the state failed.
        ByteBuffer buffer = getWebContentsStateAsByteBuffer();
        if (buffer == null) return null;

        TabState.WebContentsState state = new TabState.WebContentsStateNative(buffer);
        state.setVersion(TabState.CONTENTS_STATE_CURRENT_VERSION);
        return state;
    }

    /** Returns an ByteBuffer representing the state of the Tab's WebContents. */
    protected ByteBuffer getWebContentsStateAsByteBuffer() {
        if (mPendingLoadParams == null) {
            return TabState.getContentsStateAsByteBuffer(this);
        } else {
            Referrer referrer = mPendingLoadParams.getReferrer();
            return TabState.createSingleNavigationStateAsByteBuffer(
                    mPendingLoadParams.getUrl(),
                    referrer != null ? referrer.getUrl() : null,
                    // Policy will be ignored for null referrer url, 0 is just a placeholder.
                    referrer != null ? referrer.getPolicy() : 0,
                    isIncognito());
        }
    }

    /**
     * Prints the current page.
     *
     * @return Whether the printing process is started successfully.
     **/
    public boolean print() {
        assert mNativeTabAndroid != 0;
        return nativePrint(mNativeTabAndroid);
    }

    @CalledByNative
    public void setPendingPrint() {
        PrintingController printingController = PrintingControllerImpl.getInstance();
        if (printingController == null) return;

        printingController.setPendingPrint(new TabPrinter(this),
                new PrintManagerDelegateImpl(mContext));
    }

    /**
     * Reloads the current page content.
     */
    public void reload() {
        // TODO(dtrainor): Should we try to rebuild the ContentView if it's frozen?
        if (getWebContents() != null) getWebContents().getNavigationController().reload(true);
    }

    /**
     * Reloads the current page content.
     * This version ignores the cache and reloads from the network.
     */
    public void reloadIgnoringCache() {
        if (getWebContents() != null) {
            getWebContents().getNavigationController().reloadIgnoringCache(true);
        }
    }

    /**
     * @return Whether or not the loading and rendering of the page is done.
     */
    @VisibleForTesting
    public boolean isLoadingAndRenderingDone() {
        return isReady() && getProgress() >= CONSIDERED_READY_LOAD_PERCENTAGE;
    }

    /** Stop the current navigation. */
    public void stopLoading() {
        if (isLoading()) {
            RewindableIterator<TabObserver> observers = getTabObservers();
            while (observers.hasNext()) observers.next().onPageLoadFinished(this);
        }
        if (getWebContents() != null) getWebContents().stop();
    }

    /**
     * @return a value between 0 and 100 reflecting what percentage of the page load is complete.
     */
    public int getProgress() {
        ChromeWebContentsDelegateAndroid delegate = getChromeWebContentsDelegateAndroid();
        if (delegate == null) return 0;
        return isLoading() ? delegate.getMostRecentProgress() : 100;
    }

    /**
     * @return The background color of the tab.
     */
    public int getBackgroundColor() {
        if (mNativePage != null) return mNativePage.getBackgroundColor();
        if (getWebContents() != null) return getWebContents().getBackgroundColor();
        return Color.WHITE;
    }

    /**
     * @return The current theme color based on the value passed from the web contents and the
     *         security state.
     */
    public int getThemeColor() {
        return mThemeColor;
    }

    /**
     * @return The web contents associated with this tab.
     */
    public WebContents getWebContents() {
        return mContentViewCore != null ? mContentViewCore.getWebContents() : null;
    }

    /**
     * @return The profile associated with this tab.
     */
    public Profile getProfile() {
        if (mNativeTabAndroid == 0) return null;
        return nativeGetProfileAndroid(mNativeTabAndroid);
    }

    /**
     * For more information about the uniqueness of {@link #getId()} see comments on {@link Tab}.
     * @see Tab
     * @return The id representing this tab.
     */
    @CalledByNative
    public int getId() {
        return mId;
    }

    /**
     * @return Whether or not this tab is incognito.
     */
    public boolean isIncognito() {
        return mIncognito;
    }

    /**
     * @return The {@link ContentViewCore} associated with the current page, or {@code null} if
     *         there is no current page or the current page is displayed using a native view.
     */
    public ContentViewCore getContentViewCore() {
        return mNativePage == null ? mContentViewCore : null;
    }

    /**
     * @return The {@link NativePage} associated with the current page, or {@code null} if there is
     *         no current page or the current page is displayed using something besides
     *         {@link NativePage}.
     */
    public NativePage getNativePage() {
        return mNativePage;
    }

    /**
     * @return Whether or not the {@link Tab} represents a {@link NativePage}.
     */
    public boolean isNativePage() {
        return mNativePage != null;
    }

    /**
     * Set whether or not the {@link ContentViewCore} should be using a desktop user agent for the
     * currently loaded page.
     * @param useDesktop     If {@code true}, use a desktop user agent.  Otherwise use a mobile one.
     * @param reloadOnChange Reload the page if the user agent has changed.
     */
    public void setUseDesktopUserAgent(boolean useDesktop, boolean reloadOnChange) {
        if (getWebContents() != null) {
            getWebContents().getNavigationController()
                    .setUseDesktopUserAgent(useDesktop, reloadOnChange);
        }
    }

    /**
     * @return Whether or not the {@link ContentViewCore} is using a desktop user agent.
     */
    public boolean getUseDesktopUserAgent() {
        return getWebContents() != null && getWebContents().getNavigationController()
                .getUseDesktopUserAgent();
    }

    /**
     * @return The current {@link ConnectionSecurityLevel} for the tab.
     */
    // TODO(tedchoc): Remove this and transition all clients to use ToolbarModel directly.
    public int getSecurityLevel() {
        return ConnectionSecurity.getSecurityLevelForWebContents(getWebContents());
    }

    /**
     * @return The sync id of the tab if session sync is enabled, {@code 0} otherwise.
     */
    @CalledByNative
    protected int getSyncId() {
        return mSyncId;
    }

    /**
     * @param syncId The sync id of the tab if session sync is enabled.
     */
    @CalledByNative
    protected void setSyncId(int syncId) {
        mSyncId = syncId;
    }

    /**
     * @return An {@link ObserverList.RewindableIterator} instance that points to all of
     *         the current {@link TabObserver}s on this class.  Note that calling
     *         {@link java.util.Iterator#remove()} will throw an
     *         {@link UnsupportedOperationException}.
     */
    protected ObserverList.RewindableIterator<TabObserver> getTabObservers() {
        return mObservers.rewindableIterator();
    }

    /**
     * @return The {@link ContentViewClient} currently bound to any {@link ContentViewCore}
     *         associated with the current page.  There can still be a {@link ContentViewClient}
     *         even when there is no {@link ContentViewCore}.
     */
    protected ContentViewClient getContentViewClient() {
        return mContentViewClient;
    }

    /**
     * @param client The {@link ContentViewClient} to be bound to any current or new
     *               {@link ContentViewCore}s associated with this {@link Tab}.
     */
    protected void setContentViewClient(ContentViewClient client) {
        if (mContentViewClient == client) return;

        ContentViewClient oldClient = mContentViewClient;
        mContentViewClient = client;

        if (mContentViewCore == null) return;

        if (mContentViewClient != null) {
            mContentViewCore.setContentViewClient(mContentViewClient);
        } else if (oldClient != null) {
            // We can't set a null client, but we should clear references to the last one.
            mContentViewCore.setContentViewClient(new ContentViewClient());
        }
    }

    /**
     * Called on the foreground tab when the Activity showing the Tab gets started. This is called
     * on both cold and warm starts.
     */
    public void onActivityStart() {
        onActivityStartInternal(true);
    }

    protected void onActivityStartInternal(boolean showNow) {
        if (isHidden()) {
            if (showNow) show(TabSelectionType.FROM_USER);
        } else {
            // The visible Tab's renderer process may have died after the activity was paused.
            // Ensure that it's restored appropriately.
            loadIfNeeded();
        }

        // When resuming the activity, force an update to the fullscreen state to ensure a
        // subactivity did not change the fullscreen configuration of this ChromeTab's renderer in
        // the case where it was shared (i.e. via an EmbedContentViewActivity).
        updateFullscreenEnabledState();
    }

    /**
     * Called on the foreground tab when the Activity is stopped.
     */
    public void onActivityStop() {
        // TODO(jdduke): Remove this method when all downstream callers have been removed.
    }

    /**
     * Prepares the tab to be shown. This method is supposed to be called before the tab is
     * displayed. It restores the ContentView if it is not available after the cold start and
     * reloads the tab if its renderer has crashed.
     * @param type Specifies how the tab was selected.
     */
    public final void show(TabSelectionType type) {
        try {
            TraceEvent.begin("Tab.show");
            if (!isHidden()) return;
            // Keep unsetting mIsHidden above loadIfNeeded(), so that we pass correct visibility
            // when spawning WebContents in loadIfNeeded().
            mIsHidden = false;

            loadIfNeeded();
            assert !isFrozen();

            if (mContentViewCore != null) mContentViewCore.onShow();

            if (mTabUma != null) mTabUma.onShow(type, getTimestampMillis());

            showInternal(type);

            // If the page is still loading, update the progress bar (otherwise it would not show
            // until the renderer notifies of new progress being made).
            if (getProgress() < 100 && !isShowingInterstitialPage()) {
                notifyLoadProgress(getProgress());
            }

            // Updating the timestamp has to happen after the showInternal() call since subclasses
            // may use it for logging.
            mTimestampMillis = System.currentTimeMillis();

            for (TabObserver observer : mObservers) observer.onShown(this);
        } finally {
            TraceEvent.end("Tab.show");
        }
    }

    /**
     * Called when the Tab is behing shown to perform any subclass-specific tasks.
     * @param type Specifies how the tab was selected.
     */
    protected void showInternal(TabSelectionType type) {
    }

    /**
     * Triggers the hiding logic for the view backing the tab.
     */
    public final void hide() {
        if (isHidden()) return;
        mIsHidden = true;

        if (mContentViewCore != null) mContentViewCore.onHide();

        // Clean up any fullscreen state that might impact other tabs.
        if (mFullscreenManager != null) {
            mFullscreenManager.setPersistentFullscreenMode(false);
            mFullscreenManager.hideControlsPersistent(mFullscreenHungRendererToken);
            mFullscreenHungRendererToken = FullscreenManager.INVALID_TOKEN;
            mPreviousFullscreenOverdrawBottomHeight = Float.NaN;
        }

        if (mTabUma != null) mTabUma.onHide();

        hideInternal();

        for (TabObserver observer : mObservers) observer.onHidden(this);
    }

    /**
     * Called when the Tab is being hidden to perform any subclass-specific tasks.
     */
    protected void hideInternal() {
    }

    /**
     * Shows the given {@code nativePage} if it's not already showing.
     * @param nativePage The {@link NativePage} to show.
     */
    protected void showNativePage(NativePage nativePage) {
        if (mNativePage == nativePage) return;
        NativePage previousNativePage = mNativePage;
        mNativePage = nativePage;
        pushNativePageStateToNavigationEntry();
        // Notifying of theme color change before content change because some of
        // the observers depend on the theme information being correct in
        // onContentChanged().
        for (TabObserver observer : mObservers) {
            observer.onDidChangeThemeColor(this, mDefaultThemeColor);
        }
        for (TabObserver observer : mObservers) {
            observer.onContentChanged(this);
        }
        destroyNativePageInternal(previousNativePage);
    }

    /**
     * Replaces the current NativePage with a empty stand-in for a NativePage. This can be used
     * to reduce memory pressure.
     */
    public void freezeNativePage() {
        if (mNativePage == null || mNativePage instanceof FrozenNativePage) return;
        assert mNativePage.getView().getParent() == null : "Cannot freeze visible native page";
        mNativePage = FrozenNativePage.freeze(mNativePage);
    }

    /**
     * Hides the current {@link NativePage}, if any, and shows the {@link ContentViewCore}'s view.
     */
    protected void showRenderedPage() {
        updateTitle();

        if (mNativePage == null) return;
        NativePage previousNativePage = mNativePage;
        mNativePage = null;
        for (TabObserver observer : mObservers) observer.onContentChanged(this);
        destroyNativePageInternal(previousNativePage);
    }

    /**
     * Initializes {@link Tab} with {@code webContents}.  If {@code webContents} is {@code null} a
     * new {@link WebContents} will be created for this {@link Tab}.
     * @param webContents       A {@link WebContents} object or {@code null} if one should be
     *                          created.
     * @param tabContentManager A {@link TabContentManager} instance or {@code null} if the web
     *                          content will be managed/displayed manually.
     * @param initiallyHidden   Only used if {@code webContents} is {@code null}.  Determines
     *                          whether or not the newly created {@link WebContents} will be hidden
     *                          or not.
     */
    public final void initialize(WebContents webContents, TabContentManager tabContentManager,
            boolean initiallyHidden) {
        try {
            TraceEvent.begin("Tab.initialize");

            internalInit();

            // Attach the TabContentManager if we have one.  This will bind this Tab's content layer
            // to this manager.
            // TODO(dtrainor): Remove this and move to a pull model instead of pushing the layer.
            nativeAttachToTabContentManager(mNativeTabAndroid, tabContentManager);

            // If there is a frozen WebContents state or a pending lazy load, don't create a new
            // WebContents.
            if (getFrozenContentsState() != null || getPendingLoadParams() != null) return;

            boolean creatingWebContents = webContents == null;
            if (creatingWebContents) {
                webContents = WebContentsFactory.createWebContents(isIncognito(), initiallyHidden);
            }

            ContentViewCore contentViewCore = ContentViewCore.fromWebContents(webContents);

            if (contentViewCore == null) {
                initContentViewCore(webContents);
            } else {
                setContentViewCore(contentViewCore);
            }

            if (!creatingWebContents && webContents.isLoadingToDifferentDocument()) {
                didStartPageLoad(webContents.getUrl(), false);
            }
        } finally {
            if (mTimestampMillis == INVALID_TIMESTAMP) {
                mTimestampMillis = System.currentTimeMillis();
            }

            TraceEvent.end("Tab.initialize");
        }
    }

    /**
     * Perform any class-specific initialization tasks.
     */
    protected void internalInit() {
        initializeNative();

        if (AppBannerManager.isEnabled()) {
            mAppBannerManager = createAppBannerManager();
            if (mAppBannerManager != null) addObserver(mAppBannerManager);
        }
    }

    /**
     * @return {@link AppBannerManager} to be used for this tab. May be null.
     */
    protected AppBannerManager createAppBannerManager() {
        return new AppBannerManager(this, mContext);
    }

    /**
     * Used to get a list of Android {@link View}s that represent both the normal content as well as
     * overlays.  This does not return {@link View}s for {@link NativePage}s.
     * @param content A {@link List} that will be populated with {@link View}s that represent all of
     *                the content in this {@link Tab}.
     */
    public void getAllContentViews(List<View> content) {
        if (!isNativePage()) {
            content.add(getView());
        } else if (mContentViewCore != null) {
            content.add(mContentViewCore.getContainerView());
        }
        for (int i = 0; i < mOverlayContentViewCores.size(); i++) {
            content.add(mOverlayContentViewCores.get(i).getContainerView());
        }
    }

    /**
     * Used to get a list of {@link ContentViewCore}s that represent both the normal content as well
     * as overlays.  These are all {@link ContentViewCore}s currently showing or rendering content
     * for this {@link Tab}.
     * @param content A {@link List} that will be populated with {@link ContentViewCore}s currently
     *                rendering content related to this {@link Tab}.
     */
    public void getAllContentViewCores(List<ContentViewCore> content) {
        content.add(mContentViewCore);
        for (int i = 0; i < mOverlayContentViewCores.size(); i++) {
            content.add(mOverlayContentViewCores.get(i));
        }
    }

    /**
     * Adds a {@link ContentViewCore} to this {@link Tab} as an overlay object.
     * If attachLayer is set, the {@link ContentViewCore} will be attached to CC layer hierarchy.
     * This {@link ContentViewCore} will have all layout events propagated to it.
     * This {@link ContentViewCore} can be removed via
     * {@link #detachOverlayContentViewCore(ContentViewCore)}.
     * @param content The {@link ContentViewCore} to attach.
     * @param visible Whether or not to make the content visible.
     * @param attachLayer Whether or not to attach the content view to the CC layer hierarchy.
     */
    public void attachOverlayContentViewCore(
            ContentViewCore content, boolean visible, boolean attachLayer) {
        if (content == null) return;

        assert !mOverlayContentViewCores.contains(content);
        mOverlayContentViewCores.add(content);
        if (attachLayer) nativeAttachOverlayContentViewCore(mNativeTabAndroid, content, visible);
        for (TabObserver observer : mObservers) {
            observer.onOverlayContentViewCoreAdded(this, content);
        }
    }

    /**
     * TODO(aruslan): remove this.
     * Temporary overload to avoid 2-way commit.
     */
    public void attachOverlayContentViewCore(ContentViewCore content, boolean visible) {
        attachOverlayContentViewCore(content, visible, true);
    }

    /**
     * Removes a {@link ContentViewCore} overlay object from this {@link Tab}.  This
     * {@link ContentViewCore} must have previously been added via
     * {@link #attachOverlayContentViewCore(ContentViewCore, boolean)}.
     * @param content The {@link ContentViewCore} to detach.
     */
    public void detachOverlayContentViewCore(ContentViewCore content) {
        if (content == null) return;

        for (TabObserver observer : mObservers) {
            observer.onOverlayContentViewCoreRemoved(this, content);
        }

        assert mOverlayContentViewCores.contains(content);
        mOverlayContentViewCores.remove(content);

        nativeDetachOverlayContentViewCore(mNativeTabAndroid, content);
    }

    /**
     * Called when a page has started loading.
     * @param validatedUrl URL being loaded.
     * @param showingErrorPage Whether an error page is being shown.
     */
    protected void didStartPageLoad(String validatedUrl, boolean showingErrorPage) {
        mIsShowingErrorPage = showingErrorPage;
        mIsLoading = true;

        updateTitle();
        removeSadTabIfPresent();

        clearHungRendererState();

        for (TabObserver observer : mObservers) observer.onPageLoadStarted(this, validatedUrl);
    }

    /**
     * Called when a page has finished loading.
     */
    protected void didFinishPageLoad() {
        mIsLoading = false;
        mIsBeingRestored = false;
        mIsTabStateDirty = true;
        updateTitle();
        updateFullscreenEnabledState();
        if (!isNativePage()) {
            RecordHistogram.recordBooleanHistogram(
                    "Navigation.IsMobileOptimized", mContentViewCore.getIsMobileOptimizedHint());
        }

        if (mTabUma != null) mTabUma.onLoadFinished();

        for (TabObserver observer : mObservers) observer.onPageLoadFinished(this);
    }

    /**
     * Called when a page has failed loading.
     * @param errorCode The error code causing the page to fail loading.
     */
    protected void didFailPageLoad(int errorCode) {
        mIsLoading = false;
        mIsBeingRestored = false;
        if (mTabUma != null) mTabUma.onLoadFailed(errorCode);
        for (TabObserver observer : mObservers) observer.onPageLoadFailed(this, errorCode);
    }

    /**
     * Builds the native counterpart to this class.  Meant to be overridden by subclasses to build
     * subclass native counterparts instead.  Subclasses should not call this via super and instead
     * rely on the native class to create the JNI association.
     *
     * TODO(dfalcantara): Make this function harder to access.
     */
    public void initializeNative() {
        if (mNativeTabAndroid == 0) nativeInit();
        assert mNativeTabAndroid != 0;
        mIsInitialized = true;
    }

    /**
     * Creates and initializes the {@link ContentViewCore}.
     *
     * @param webContents The WebContents object that will be used to build the
     *                    {@link ContentViewCore}.
     */
    protected void initContentViewCore(WebContents webContents) {
        ContentViewCore cvc = new ContentViewCore(mContext);
        ContentView cv = new ContentView(mContext, cvc);
        cv.setContentDescription(mContext.getResources().getString(
                R.string.accessibility_content_view));
        cvc.initialize(cv, cv, webContents, getWindowAndroid());
        setContentViewCore(cvc);
    }

    /**
     * Completes the {@link ContentViewCore} specific initialization around a native WebContents
     * pointer. {@link #getNativePage()} will still return the {@link NativePage} if there is one.
     * All initialization that needs to reoccur after a web contents swap should be added here.
     * <p />
     * NOTE: If you attempt to pass a native WebContents that does not have the same incognito
     * state as this tab this call will fail.
     *
     * @param cvc The content view core that needs to be set as active view for the tab.
     */
    protected void setContentViewCore(ContentViewCore cvc) {
        NativePage previousNativePage = mNativePage;
        mNativePage = null;
        destroyNativePageInternal(previousNativePage);

        mContentViewCore = cvc;
        cvc.getContainerView().setOnHierarchyChangeListener(this);
        cvc.getContainerView().setOnSystemUiVisibilityChangeListener(this);

        // Wrap the ContentView in a FrameLayout, which will contain both the ContentView and the
        // InfoBarContainer. The alternative -- placing the InfoBarContainer inside the ContentView
        // -- causes problems since then the ContentView would contain both real views (the
        // infobars) and virtual views (the web page elements), which breaks Android accessibility.
        // http://crbug.com/416663
        if (mContentViewParent != null) {
            assert false;
            mContentViewParent.removeAllViews();
        }
        mContentViewParent = new FrameLayout(mContext);
        mContentViewParent.addView(cvc.getContainerView(),
                new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        mWebContentsDelegate = createWebContentsDelegate();
        mWebContentsObserver = new TabWebContentsObserver(mContentViewCore.getWebContents());

        if (mContentViewClient != null) mContentViewCore.setContentViewClient(mContentViewClient);

        assert mNativeTabAndroid != 0;
        nativeInitWebContents(
                mNativeTabAndroid, mIncognito, mContentViewCore, mWebContentsDelegate,
                new TabContextMenuPopulator(createContextMenuPopulator()));

        // In the case where restoring a Tab or showing a prerendered one we already have a
        // valid infobar container, no need to recreate one.
        if (mInfoBarContainer == null) {
            // The InfoBarContainer needs to be created after the ContentView has been natively
            // initialized.
            mInfoBarContainer = new InfoBarContainer(mContext, getId(), mContentViewParent, this);
        } else {
            mInfoBarContainer.onParentViewChanged(getId(), mContentViewParent);
        }
        mInfoBarContainer.setContentViewCore(mContentViewCore);

        mSwipeRefreshHandler = new SwipeRefreshHandler(mContext);
        mSwipeRefreshHandler.setContentViewCore(mContentViewCore);

        for (TabObserver observer : mObservers) observer.onContentChanged(this);

        // For browser tabs, we want to set accessibility focus to the page
        // when it loads. This is not the default behavior for embedded
        // web views.
        mContentViewCore.setShouldSetAccessibilityFocusOnPageLoad(true);
    }

    /**
     * Constructs and shows a sad tab (Aw, Snap!).
     */
    protected void showSadTab() {
        if (getContentViewCore() != null) {
            OnClickListener suggestionAction = new OnClickListener() {
                @Override
                public void onClick(View view) {
                    Activity activity = mWindowAndroid.getActivity().get();
                    assert activity != null;
                    HelpAndFeedback.getInstance(activity).show(activity,
                            activity.getString(R.string.help_context_sad_tab),
                            Profile.getLastUsedProfile(), null);
                }
            };
            OnClickListener reloadButtonAction = new OnClickListener() {
                @Override
                public void onClick(View view) {
                    reload();
                }
            };

            // Make sure we are not adding the "Aw, snap" view over an existing one.
            assert mSadTabView == null;
            mSadTabView = SadTabViewFactory.createSadTabView(
                    mContext, suggestionAction, reloadButtonAction);

            // Show the sad tab inside ContentView.
            getContentViewCore().getContainerView().addView(
                    mSadTabView, new FrameLayout.LayoutParams(
                            LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            for (TabObserver observer : mObservers) observer.onContentChanged(this);
        }
        FullscreenManager fullscreenManager = getFullscreenManager();
        if (fullscreenManager != null) {
            fullscreenManager.setPositionsForTabToNonFullscreen();
        }
    }

    /**
     * Removes the sad tab view if present.
     */
    protected void removeSadTabIfPresent() {
        if (isShowingSadTab()) {
            getContentViewCore().getContainerView().removeView(mSadTabView);
            for (TabObserver observer : mObservers) observer.onContentChanged(this);
        }
        mSadTabView = null;
    }

    /**
     * @return Whether or not the sad tab is showing.
     */
    public boolean isShowingSadTab() {
        return mSadTabView != null && getContentViewCore() != null
                && mSadTabView.getParent() == getContentViewCore().getContainerView();
    }

    /**
     * Cleans up all internal state, destroying any {@link NativePage} or {@link ContentViewCore}
     * currently associated with this {@link Tab}.  This also destroys the native counterpart
     * to this class, which means that all subclasses should erase their native pointers after
     * this method is called.  Once this call is made this {@link Tab} should no longer be used.
     */
    public void destroy() {
        mIsInitialized = false;
        // Update the title before destroying the tab. http://b/5783092
        updateTitle();

        if (mTabUma != null) mTabUma.onDestroy();

        for (TabObserver observer : mObservers) observer.onDestroyed(this);
        mObservers.clear();

        NativePage currentNativePage = mNativePage;
        mNativePage = null;
        destroyNativePageInternal(currentNativePage);
        destroyContentViewCore(true);

        // Destroys the native tab after destroying the ContentView but before destroying the
        // InfoBarContainer. The native tab should be destroyed before the infobar container as
        // destroying the native tab cleanups up any remaining infobars. The infobar container
        // expects all infobars to be cleaned up before its own destruction.
        assert mNativeTabAndroid != 0;
        nativeDestroy(mNativeTabAndroid);
        assert mNativeTabAndroid == 0;

        if (mInfoBarContainer != null) {
            mInfoBarContainer.destroy();
            mInfoBarContainer = null;
        }

        // Destroy the AppBannerManager after the InfoBarContainer because it monitors for infobar
        // removals.
        if (mAppBannerManager != null) {
            mAppBannerManager.destroy();
            mAppBannerManager = null;
        }

        mPreviousFullscreenTopControlsOffsetY = Float.NaN;
        mPreviousFullscreenContentOffsetY = Float.NaN;

        mNeedsReload = false;
    }

    /**
     * @return Whether or not this Tab has a live native component.
     */
    public boolean isInitialized() {
        return mIsInitialized;
    }

    /**
     * @return The URL associated with the tab.
     */
    @CalledByNative
    public String getUrl() {
        String url = getWebContents() != null ? getWebContents().getUrl() : "";

        // If we have a ContentView, or a NativePage, or the url is not empty, we have a WebContents
        // so cache the WebContent's url. If not use the cached version.
        if (getContentViewCore() != null || getNativePage() != null || !TextUtils.isEmpty(url)) {
            mUrl = url;
        }

        return mUrl != null ? mUrl : "";
    }

    /**
     * @return The tab title.
     */
    @CalledByNative
    public String getTitle() {
        if (mTitle == null) updateTitle();
        return mTitle;
    }

    private void updateTitle() {
        if (isFrozen()) return;

        // When restoring the tabs, the title will no longer be populated, so request it from the
        // ContentViewCore or NativePage (if present).
        String title = "";
        if (mNativePage != null) {
            title = mNativePage.getTitle();
        } else if (getWebContents() != null) {
            title = getWebContents().getTitle();
        }
        updateTitle(title);
    }

    /**
     * Cache the title for the current page.
     *
     * {@link ContentViewClient#onUpdateTitle} is unreliable, particularly for navigating backwards
     * and forwards in the history stack, so pull the correct title whenever the page changes.
     * onUpdateTitle is only called when the title of a navigation entry changes. When the user goes
     * back a page the navigation entry exists with the correct title, thus the title is not
     * actually changed, and no notification is sent.
     * @param title Title of the page.
     */
    private void updateTitle(String title) {
        if (TextUtils.equals(mTitle, title)) return;

        mIsTabStateDirty = true;
        mTitle = title;
        mIsTitleDirectionRtl = LocalizationUtils.getFirstStrongCharacterDirection(title)
                == LocalizationUtils.RIGHT_TO_LEFT;
        notifyPageTitleChanged();
    }

    protected void notifyPageTitleChanged() {
        RewindableIterator<TabObserver> observers = getTabObservers();
        while (observers.hasNext()) {
            observers.next().onTitleUpdated(this);
        }
    }

    /**
     * Notify the observers that the load progress has changed.
     * @param progress The current percentage of progress.
     */
    protected void notifyLoadProgress(int progress) {
        for (TabObserver observer : mObservers) observer.onLoadProgressChanged(Tab.this, progress);
    }

    protected void notifyFaviconChanged() {
        RewindableIterator<TabObserver> observers = getTabObservers();
        while (observers.hasNext()) {
            observers.next().onFaviconUpdated(this);
        }
    }

    private void notifyPageUrlChanged() {
        RewindableIterator<TabObserver> observers = getTabObservers();
        while (observers.hasNext()) {
            observers.next().onUrlUpdated(this);
        }
    }

    /**
     * @return True if the tab title should be displayed from right to left.
     */
    public boolean isTitleDirectionRtl() {
        return mIsTitleDirectionRtl;
    }

    /**
     * @return The bitmap of the favicon scaled to 16x16dp. null if no favicon
     *         is specified or it requires the default favicon.
     */
    public Bitmap getFavicon() {
        // If we have no content or a native page, return null.
        if (getContentViewCore() == null) return null;

        // Use the cached favicon only if the page wasn't changed.
        if (mFavicon != null && mFaviconUrl != null && mFaviconUrl.equals(getUrl())) {
            return mFavicon;
        }

        return nativeGetFavicon(mNativeTabAndroid);
    }

    /**
     * Loads the tab if it's not loaded (e.g. because it was killed in background).
     * This will trigger a regular load for tabs with pending lazy first load (tabs opened in
     * background on low-memory devices).
     * @return true iff the Tab handled the request.
     */
    @CalledByNative
    public boolean loadIfNeeded() {
        if (mContext == null) {
            Log.e(TAG, "Tab couldn't be loaded because Context was null.");
            return false;
        }

        if (mPendingLoadParams != null) {
            assert isFrozen();
            initContentViewCore(WebContentsFactory.createWebContents(isIncognito(), isHidden()));
            loadUrl(mPendingLoadParams);
            mPendingLoadParams = null;
            return true;
        }

        restoreIfNeeded();
        return true;
    }

    /**
     * Loads a tab that was already loaded but since then was lost. This happens either when we
     * unfreeze the tab from serialized state or when we reload a tab that crashed. In both cases
     * the load codepath is the same (run in loadIfNecessary()) and the same caching policies of
     * history load are used.
     */
    private final void restoreIfNeeded() {
        try {
            TraceEvent.begin("Tab.restoreIfNeeded");
            if (isFrozen() && mFrozenContentsState != null) {
                // Restore is needed for a tab that is loaded for the first time. WebContents will
                // be restored from a saved state.
                unfreezeContents();
            } else if (mNeedsReload) {
                // Restore is needed for a tab that was previously loaded, but its renderer was
                // killed by the oom killer.
                mNeedsReload = false;
                requestRestoreLoad();
            } else {
                // No restore needed.
                return;
            }

            loadIfNecessary();
            restoreIfNeededInternal();
        } finally {
            TraceEvent.end("Tab.restoreIfNeeded");
        }
    }

    /**
     * Performs any subclass-specific tasks when the Tab is restored.
     */
    protected void restoreIfNeededInternal() {
        mIsBeingRestored = true;
        if (mTabUma != null) mTabUma.onRestoreStarted();
    }

    /**
     * Issues a fake notification about the renderer being killed.
     *
     * @param wasOomProtected True if the renderer was protected from the OS out-of-memory killer
     *                        (e.g. renderer for the currently selected tab)
     */
    @VisibleForTesting
    public void simulateRendererKilledForTesting(boolean wasOomProtected) {
        if (mWebContentsObserver != null) {
            mWebContentsObserver.renderProcessGone(wasOomProtected);
        }
    }

    /**
     * Restores the WebContents from its saved state.
     * @return Whether or not the restoration was successful.
     */
    public boolean unfreezeContents() {
        try {
            TraceEvent.begin("Tab.unfreezeContents");
            assert mFrozenContentsState != null;
            assert getContentViewCore() == null;

            boolean forceNavigate = false;
            WebContents webContents =
                    mFrozenContentsState.restoreContentsFromByteBuffer(isHidden());
            if (webContents == null) {
                // State restore failed, just create a new empty web contents as that is the best
                // that can be done at this point. TODO(jcivelli) http://b/5910521 - we should show
                // an error page instead of a blank page in that case (and the last loaded URL).
                webContents = WebContentsFactory.createWebContents(isIncognito(), isHidden());
                forceNavigate = true;
            }

            mFrozenContentsState = null;
            initContentViewCore(webContents);

            if (forceNavigate) {
                String url = TextUtils.isEmpty(mUrl) ? UrlConstants.NTP_URL : mUrl;
                loadUrl(new LoadUrlParams(url, PageTransition.GENERATED));
            }
            return !forceNavigate;
        } finally {
            TraceEvent.end("Tab.unfreezeContents");
        }
    }

    /**
     * @return Whether or not the tab is hidden.
     */
    public boolean isHidden() {
        return mIsHidden;
    }

    /**
     * @return Whether or not the tab is in the closing process.
     */
    public boolean isClosing() {
        return mIsClosing;
    }

    /**
     * @param closing Whether or not the tab is in the closing process.
     */
    public void setClosing(boolean closing) {
        mIsClosing = closing;

        for (TabObserver observer : mObservers) observer.onClosingStateChanged(this, closing);
    }

    /**
     * @return Whether the Tab needs to be reloaded. {@see #mNeedsReload}
     */
    public boolean needsReload() {
        return mNeedsReload;
    }

    /**
     * Set whether the Tab needs to be reloaded. {@see #mNeedsReload}
     */
    protected void setNeedsReload(boolean needsReload) {
        mNeedsReload = needsReload;
    }

    /**
     * @return true iff the tab is loading and an interstitial page is not showing.
     */
    public boolean isLoading() {
        return mIsLoading && !isShowingInterstitialPage();
    }

    /**
     * @return true iff the tab is performing a restore page load.
     */
    public boolean isBeingRestored() {
        return mIsBeingRestored;
    }

    /**
     * @return The id of the tab that caused this tab to be opened.
     */
    public int getParentId() {
        return mParentId;
    }

    /**
     * @return Whether the tab should be grouped with its parent tab (true by default).
     */
    public boolean isGroupedWithParent() {
        return mGroupedWithParent;
    }

    /**
     * Sets whether the tab should be grouped with its parent tab.
     *
     * @param groupedWithParent The new value.
     * @see #isGroupedWithParent
     */
    public void setGroupedWithParent(boolean groupedWithParent) {
        mGroupedWithParent = groupedWithParent;
    }

    private void destroyNativePageInternal(NativePage nativePage) {
        if (nativePage == null) return;
        assert nativePage != mNativePage : "Attempting to destroy active page.";

        nativePage.destroy();
    }

    /**
     * Called when the background color for the content changes.
     * @param color The current for the background.
     */
    protected void onBackgroundColorChanged(int color) {
        for (TabObserver observer : mObservers) observer.onBackgroundColorChanged(this, color);
    }

    /**
     * Destroys the current {@link ContentViewCore}.
     * @param deleteNativeWebContents Whether or not to delete the native WebContents pointer.
     */
    protected final void destroyContentViewCore(boolean deleteNativeWebContents) {
        if (mContentViewCore == null) return;

        destroyContentViewCoreInternal(mContentViewCore);

        if (mInfoBarContainer != null && mInfoBarContainer.getParent() != null) {
            mInfoBarContainer.removeFromParentView();
            mInfoBarContainer.setContentViewCore(null);
        }
        if (mSwipeRefreshHandler != null) {
            mSwipeRefreshHandler.setContentViewCore(null);
            mSwipeRefreshHandler = null;
        }
        mContentViewParent = null;
        mContentViewCore.destroy();
        mContentViewCore = null;

        mWebContentsDelegate = null;

        if (mWebContentsObserver != null) {
            mWebContentsObserver.destroy();
            mWebContentsObserver = null;
        }

        assert mNativeTabAndroid != 0;
        nativeDestroyWebContents(mNativeTabAndroid, deleteNativeWebContents);
    }

    /**
     * Gives subclasses the chance to clean up some state associated with this
     * {@link ContentViewCore}. This is because {@link #getContentViewCore()}
     * can return {@code null} if a {@link NativePage} is showing.
     *
     * @param cvc The {@link ContentViewCore} that should have associated state
     *            cleaned up.
     */
    protected void destroyContentViewCoreInternal(ContentViewCore cvc) {
        cvc.getContainerView().setOnHierarchyChangeListener(null);
        cvc.getContainerView().setOnSystemUiVisibilityChangeListener(null);
    }

    /**
     * A helper method to allow subclasses to build their own delegate.
     * @return An instance of a {@link TabChromeWebContentsDelegateAndroid}.
     */
    protected TabChromeWebContentsDelegateAndroid createWebContentsDelegate() {
        return new TabChromeWebContentsDelegateAndroid();
    }

    /**
     * A helper method to allow subclasses to handle the Instant support
     * disabled event.
     */
    @CalledByNative
    private void onWebContentsInstantSupportDisabled() {
        for (TabObserver observer : mObservers) observer.onWebContentsInstantSupportDisabled();
    }

    /**
     * A helper method to allow subclasses to build their own menu populator.
     * @return An instance of a {@link ContextMenuPopulator}.
     */
    protected ContextMenuPopulator createContextMenuPopulator() {
        return new ChromeContextMenuPopulator(new TabChromeContextMenuItemDelegate());
    }

    /**
     * @return The {@link WindowAndroid} associated with this {@link Tab}.
     */
    public WindowAndroid getWindowAndroid() {
        return mWindowAndroid;
    }

    /**
     * @return The current {@link ChromeWebContentsDelegateAndroid} instance.
     */
    public ChromeWebContentsDelegateAndroid getChromeWebContentsDelegateAndroid() {
        return mWebContentsDelegate;
    }

    @CalledByNative
    protected void onFaviconAvailable(Bitmap icon) {
        boolean needUpdate = false;
        String url = getUrl();
        boolean pageUrlChanged = !url.equals(mFaviconUrl);
        // This method will be called multiple times if the page has more than one favicon.
        // we are trying to use the 16x16 DP icon here, Bitmap.createScaledBitmap will return
        // the origin bitmap if it is already 16x16 DP.
        if (pageUrlChanged || (icon.getWidth() == mNumPixel16DP
                && icon.getHeight() == mNumPixel16DP)) {
            mFavicon = Bitmap.createScaledBitmap(icon, mNumPixel16DP, mNumPixel16DP, true);
            needUpdate = true;
        }

        if (pageUrlChanged) {
            mFaviconUrl = url;
            needUpdate = true;
        }

        if (!needUpdate) return;

        for (TabObserver observer : mObservers) observer.onFaviconUpdated(this);
    }
    /**
     * Called when the navigation entry containing the history item changed,
     * for example because of a scroll offset or form field change.
     */
    @CalledByNative
    protected void onNavEntryChanged() {
        mIsTabStateDirty = true;
    }

    /**
     * Returns the SnackbarManager for the activity that owns this Tab, if any. May
     * return null.
     */
    public SnackbarManager getSnackbarManager() {
        return null;
    }

    /**
     * @return The native pointer representing the native side of this {@link Tab} object.
     */
    @CalledByNative
    private long getNativePtr() {
        return mNativeTabAndroid;
    }

    /** This is currently called when committing a pre-rendered page. */
    @VisibleForTesting
    @CalledByNative
    public void swapWebContents(
            WebContents webContents, boolean didStartLoad, boolean didFinishLoad) {
        ContentViewCore cvc = new ContentViewCore(mContext);
        ContentView cv = new ContentView(mContext, cvc);
        cv.setContentDescription(mContext.getResources().getString(
                R.string.accessibility_content_view));
        cvc.initialize(cv, cv, webContents, getWindowAndroid());
        swapContentViewCore(cvc, false, didStartLoad, didFinishLoad);
    }

    /**
     * Called to swap out the current view with the one passed in.
     *
     * @param newContentViewCore The content view that should be swapped into the tab.
     * @param deleteOldNativeWebContents Whether to delete the native web
     *         contents of old view.
     * @param didStartLoad Whether
     *         WebContentsObserver::DidStartProvisionalLoadForFrame() has
     *         already been called.
     * @param didFinishLoad Whether WebContentsObserver::DidFinishLoad() has
     *         already been called.
     */
    public void swapContentViewCore(ContentViewCore newContentViewCore,
            boolean deleteOldNativeWebContents, boolean didStartLoad, boolean didFinishLoad) {
        int originalWidth = 0;
        int originalHeight = 0;
        if (mContentViewCore != null) {
            originalWidth = mContentViewCore.getViewportWidthPix();
            originalHeight = mContentViewCore.getViewportHeightPix();
            mContentViewCore.onHide();
        }
        destroyContentViewCore(deleteOldNativeWebContents);
        NativePage previousNativePage = mNativePage;
        mNativePage = null;
        setContentViewCore(newContentViewCore);
        // Size of the new ContentViewCore is zero at this point. If we don't call onSizeChanged(),
        // next onShow() call would send a resize message with the current ContentViewCore size
        // (zero) to the renderer process, although the new size will be set soon.
        // However, this size fluttering may confuse Blink and rendered result can be broken
        // (see http://crbug.com/340987).
        mContentViewCore.onSizeChanged(originalWidth, originalHeight, 0, 0);
        mContentViewCore.onShow();
        mContentViewCore.attachImeAdapter();
        destroyNativePageInternal(previousNativePage);
        mWebContentsObserver.didChangeThemeColor(
                getWebContents().getThemeColor(mDefaultThemeColor));
        for (TabObserver observer : mObservers) {
            observer.onWebContentsSwapped(this, didStartLoad, didFinishLoad);
        }
    }

    @CalledByNative
    private void clearNativePtr() {
        assert mNativeTabAndroid != 0;
        mNativeTabAndroid = 0;
    }

    @CalledByNative
    private void setNativePtr(long nativePtr) {
        assert mNativeTabAndroid == 0;
        mNativeTabAndroid = nativePtr;
    }

    /**
     * @return Whether the TabState representing this Tab has been updated.
     */
    public boolean isTabStateDirty() {
        return mIsTabStateDirty;
    }

    /**
     * Set whether the TabState representing this Tab has been updated.
     * @param isDirty Whether the Tab's state has changed.
     */
    public void setIsTabStateDirty(boolean isDirty) {
        mIsTabStateDirty = isDirty;
    }

    /**
     * @return Whether the Tab should be preserved in Android's Recents list when users hit "back".
     */
    public boolean shouldPreserve() {
        return mShouldPreserve;
    }

    /**
     * Sets whether the Tab should be preserved in Android's Recents list when users hit "back".
     * @param preserve Whether the tab should be preserved.
     */
    public void setShouldPreserve(boolean preserve) {
        mShouldPreserve = preserve;
    }

    /**
     * @return Parameters that should be used for a lazily loaded Tab.  May be null.
     */
    protected LoadUrlParams getPendingLoadParams() {
        return mPendingLoadParams;
    }

    /**
     * @param params Parameters that should be used for a lazily loaded Tab.
     */
    protected void setPendingLoadParams(LoadUrlParams params) {
        mPendingLoadParams = params;
        mUrl = params.getUrl();
    }

    /**
     * @see #setAppAssociatedWith(String) for more information.
     * TODO(aurimas): investigate reducing the visibility of this method after TabModel refactoring.
     *
     * @return The id of the application associated with that tab (null if not
     *         associated with an app).
     */
    public String getAppAssociatedWith() {
        return mAppAssociatedWith;
    }

    /**
     * Associates this tab with the external app with the specified id. Once a Tab is associated
     * with an app, it is reused when a new page is opened from that app (unless the user typed in
     * the location bar or in the page, in which case the tab is dissociated from any app)
     * TODO(aurimas): investigate reducing the visibility of this method after TabModel refactoring.
     *
     * @param appId The ID of application associated with the tab.
     */
    public void setAppAssociatedWith(String appId) {
        mAppAssociatedWith = appId;
    }

    /**
     * @return See {@link #mTimestampMillis}.
     */
    protected long getTimestampMillis() {
        return mTimestampMillis;
    }

    /**
     * Restores a tab either frozen or from state.
     * TODO(aurimas): investigate reducing the visibility of this method after TabModel refactoring.
     */
    public void createHistoricalTab() {
        if (!isFrozen()) {
            nativeCreateHistoricalTab(mNativeTabAndroid);
        } else if (mFrozenContentsState != null) {
            mFrozenContentsState.createHistoricalTab();
        }
    }

    /**
     * @return The reason the Tab was launched.
     */
    public TabLaunchType getLaunchType() {
        return mLaunchType;
    }

    /**
     * @return true iff the tab doesn't hold a live page. This happens before initialize() and when
     * the tab holds frozen WebContents state that is yet to be inflated.
     */
    @VisibleForTesting
    public boolean isFrozen() {
        return getNativePage() == null && getContentViewCore() == null;
    }

    /**
     * @return An instance of a {@link FullscreenManager}.
     */
    protected FullscreenManager getFullscreenManager() {
        return mFullscreenManager;
    }

    /**
     * Clears hung renderer state.
     */
    protected void clearHungRendererState() {
        if (mFullscreenManager == null) return;

        mFullscreenManager.hideControlsPersistent(mFullscreenHungRendererToken);
        mFullscreenHungRendererToken = FullscreenManager.INVALID_TOKEN;
        updateFullscreenEnabledState();
    }

    /**
     * Called when offset values related with fullscreen functionality has been changed by the
     * compositor.
     * @param topControlsOffsetY The Y offset of the top controls in physical pixels.
     * @param contentOffsetY The Y offset of the content in physical pixels.
     * @param overdrawBottomHeight The overdraw height.
     * @param isNonFullscreenPage Whether a current page is non-fullscreen page or not.
     */
    protected void onOffsetsChanged(float topControlsOffsetY, float contentOffsetY,
            float overdrawBottomHeight, boolean isNonFullscreenPage) {
        mPreviousFullscreenTopControlsOffsetY = topControlsOffsetY;
        mPreviousFullscreenContentOffsetY = contentOffsetY;
        mPreviousFullscreenOverdrawBottomHeight = overdrawBottomHeight;

        if (mFullscreenManager == null) return;
        if (isNonFullscreenPage || isNativePage()) {
            mFullscreenManager.setPositionsForTabToNonFullscreen();
        } else {
            mFullscreenManager.setPositionsForTab(topControlsOffsetY, contentOffsetY);
        }
        TabModelBase.setActualTabSwitchLatencyMetricRequired();
    }

    /**
     * Push state about whether or not the top controls can show or hide to the renderer.
     */
    public void updateFullscreenEnabledState() {
        if (isFrozen()) return;

        updateTopControlsState(getTopControlsStateConstraints(), TopControlsState.BOTH, true);

        if (getContentViewCore() != null && mFullscreenManager != null) {
            getContentViewCore().updateMultiTouchZoomSupport(
                    !mFullscreenManager.getPersistentFullscreenMode());
        }
    }

    /**
     * Updates the top controls state for this tab.  As these values are set at the renderer
     * level, there is potential for this impacting other tabs that might share the same
     * process.
     *
     * @param constraints The constraints that determine whether the controls can be shown
     *                    or hidden at all.
     * @param current The desired current state for the controls.  Pass
     *                {@link TopControlsState#BOTH} to preserve the current position.
     * @param animate Whether the controls should animate to the specified ending condition or
     *                should jump immediately.
     */
    protected void updateTopControlsState(int constraints, int current, boolean animate) {
        if (mNativeTabAndroid == 0) return;
        nativeUpdateTopControlsState(mNativeTabAndroid, constraints, current, animate);
    }

    /**
     * Updates the top controls state for this tab.  As these values are set at the renderer
     * level, there is potential for this impacting other tabs that might share the same
     * process.
     *
     * @param current The desired current state for the controls.  Pass
     *                {@link TopControlsState#BOTH} to preserve the current position.
     * @param animate Whether the controls should animate to the specified ending condition or
     *                should jump immediately.
     */
    public void updateTopControlsState(int current, boolean animate) {
        int constraints = getTopControlsStateConstraints();
        // Do nothing if current and constraints conflict to avoid error in
        // renderer.
        if ((constraints == TopControlsState.HIDDEN && current == TopControlsState.SHOWN)
                || (constraints == TopControlsState.SHOWN && current == TopControlsState.HIDDEN)) {
            return;
        }
        updateTopControlsState(getTopControlsStateConstraints(), current, animate);
    }

    /**
     * @return Whether hiding top controls is enabled or not.
     */
    protected boolean isHidingTopControlsEnabled() {
        WebContents webContents = getWebContents();
        if (webContents == null || webContents.isDestroyed()) return false;

        String url = getUrl();
        boolean enableHidingTopControls = url != null && !url.startsWith(UrlConstants.CHROME_SCHEME)
                && !url.startsWith(UrlConstants.CHROME_NATIVE_SCHEME);

        int securityState = getSecurityLevel();
        enableHidingTopControls &= (securityState != ConnectionSecurityLevel.SECURITY_ERROR
                && securityState != ConnectionSecurityLevel.SECURITY_WARNING);

        enableHidingTopControls &=
                !AccessibilityUtil.isAccessibilityEnabled(getApplicationContext());
        enableHidingTopControls &= !mContentViewCore.isFocusedNodeEditable();
        enableHidingTopControls &= !mIsShowingErrorPage;
        enableHidingTopControls &= !webContents.isShowingInterstitialPage();
        enableHidingTopControls &= (mFullscreenManager != null);

        return enableHidingTopControls;
    }

    /**
     * Performs any subclass-specific tasks when the Tab crashes.
     */
    protected void handleTabCrash() {
        if (mTabUma != null) mTabUma.onRendererCrashed();
    }

    /**
     * @return Whether showing top controls is enabled or not.
     */
    public boolean isShowingTopControlsEnabled() {
        if (mFullscreenManager == null) return true;
        return !mFullscreenManager.getPersistentFullscreenMode();
    }

    /**
     * @return The current visibility constraints for the display of top controls.
     *         {@link TopControlsState} defines the valid return options.
     */
    protected int getTopControlsStateConstraints() {
        boolean enableHidingTopControls = isHidingTopControlsEnabled();
        boolean enableShowingTopControls = isShowingTopControlsEnabled();

        int constraints = TopControlsState.BOTH;
        if (!enableShowingTopControls) {
            constraints = TopControlsState.HIDDEN;
        } else if (!enableHidingTopControls) {
            constraints = TopControlsState.SHOWN;
        }
        return constraints;
    }

    /**
     * @param manager The fullscreen manager that should be notified of changes to this tab (if
     *                set to null, no more updates will come from this tab).
     */
    public void setFullscreenManager(FullscreenManager manager) {
        mFullscreenManager = manager;
        if (mFullscreenManager != null) {
            if (Float.isNaN(mPreviousFullscreenTopControlsOffsetY)
                    || Float.isNaN(mPreviousFullscreenContentOffsetY)) {
                mFullscreenManager.setPositionsForTabToNonFullscreen();
            } else {
                mFullscreenManager.setPositionsForTab(
                        mPreviousFullscreenTopControlsOffsetY, mPreviousFullscreenContentOffsetY);
            }
            mFullscreenManager.showControlsTransient();
            updateFullscreenEnabledState();
        }
    }

    /**
     * @return The most recent frame's overdraw bottom height in pixels.
     */
    public float getFullscreenOverdrawBottomHeightPix() {
        return mPreviousFullscreenOverdrawBottomHeight;
    }

    private void pushNativePageStateToNavigationEntry() {
        assert mNativeTabAndroid != 0 && getNativePage() != null;
        nativeSetActiveNavigationEntryTitleForUrl(mNativeTabAndroid, getNativePage().getUrl(),
                getNativePage().getTitle());
    }

    @Override
    public void onChildViewRemoved(View parent, View child) {
        FullscreenManager fullscreenManager = getFullscreenManager();
        if (fullscreenManager != null) {
            fullscreenManager.updateContentViewChildrenState();
        }
    }

    @Override
    public void onChildViewAdded(View parent, View child) {
        FullscreenManager fullscreenManager = getFullscreenManager();
        if (fullscreenManager != null) {
            fullscreenManager.updateContentViewChildrenState();
        }
    }

    @Override
    public void onSystemUiVisibilityChange(int visibility) {
        FullscreenManager fullscreenManager = getFullscreenManager();
        if (fullscreenManager != null) {
            fullscreenManager.onContentViewSystemUiVisibilityChange(visibility);
        }
    }

    /**
     * Triggers native search by image method.
     */
    public void triggerSearchByImage() {
        if (mNativeTabAndroid != 0) nativeSearchByImageInNewTabAsync(mNativeTabAndroid);
    }

    /**
     * @return The ID of the bookmark associated with the current URL (or -1 if no such bookmark
     *         exists).
     */
    public long getBookmarkId() {
        return isFrozen() ? -1 : nativeGetBookmarkId(mNativeTabAndroid, false);
    }

    /**
     * Same as getBookmarkId() but never returns ids for managed bookmarks, or any other bookmarks
     * that can't be edited by the user.
     */
    public long getUserBookmarkId() {
        return isFrozen() ? -1 : nativeGetBookmarkId(mNativeTabAndroid, true);
    }

    /**
     * @return True if the offline page is opened.
     */
    public boolean isOfflinePage() {
        return isFrozen() ? false : nativeIsOfflinePage(mNativeTabAndroid);
    }

    /**
     * Request that this tab receive focus. Currently, this function requests focus for the main
     * View (usually a ContentView).
     */
    public void requestFocus() {
        View view = getView();
        if (view != null) view.requestFocus();
    }

    @CalledByNative
    protected void openNewTab(String url, String extraHeaders, byte[] postData, int disposition,
            boolean hasParent, boolean isRendererInitiated) {
        if (isClosing()) return;

        boolean incognito = isIncognito();
        TabLaunchType tabLaunchType = TabLaunchType.FROM_LONGPRESS_FOREGROUND;
        Tab parentTab = hasParent ? this : null;

        switch (disposition) {
            case WindowOpenDisposition.NEW_WINDOW: // fall through
            case WindowOpenDisposition.NEW_FOREGROUND_TAB:
                break;
            case WindowOpenDisposition.NEW_POPUP: // fall through
            case WindowOpenDisposition.NEW_BACKGROUND_TAB:
                tabLaunchType = TabLaunchType.FROM_LONGPRESS_BACKGROUND;
                break;
            case WindowOpenDisposition.OFF_THE_RECORD:
                assert incognito;
                incognito = true;
                break;
            default:
                assert false;
        }

        // If shouldIgnoreNewTab returns true, the intent is handled by another
        // activity. As a result, don't launch a new tab to open the URL.
        if (shouldIgnoreNewTab(url, incognito)) return;

        LoadUrlParams loadUrlParams = new LoadUrlParams(url);
        loadUrlParams.setVerbatimHeaders(extraHeaders);
        loadUrlParams.setPostData(postData);
        loadUrlParams.setIsRendererInitiated(isRendererInitiated);
        openNewTab(loadUrlParams, tabLaunchType, parentTab, incognito);
    }

    /**
     * Asks the Tab to open another Tab with the given parameters.  Must be implemented by
     * subclasses because the Tab itself has no access to a TabModel.
     * @param params Parameters for loading a URL in the new Tab.
     * @param launchType How the Tab is being launched.
     * @param parentTab Tab that is spawning a new Tab.
     * @param incognito Whether the new Tab is supposed to be incognito.
     */
    protected void openNewTab(
            LoadUrlParams params, TabLaunchType launchType, Tab parentTab, boolean incognito) {
        assert false : "Subclasses must implement this method.";
    }

    /**
     * @return True if the Tab should block the creation of new tabs via {@link #openNewTab}.
     */
    protected boolean shouldIgnoreNewTab(String url, boolean incognito) {
        return false;
    }

    /**
     * See {@link #mInterceptNavigationDelegate}.
     */
    protected InterceptNavigationDelegate getInterceptNavigationDelegate() {
        return mInterceptNavigationDelegate;
    }

    /**
     * See {@link #mInterceptNavigationDelegate}.
     */
    protected void setInterceptNavigationDelegate(InterceptNavigationDelegate delegate) {
        mInterceptNavigationDelegate = delegate;
        nativeSetInterceptNavigationDelegate(mNativeTabAndroid, delegate);
    }

    /**
     * @return the AppBannerManager.
     */
    @VisibleForTesting
    public AppBannerManager getAppBannerManagerForTesting() {
        return mAppBannerManager;
    }

    @VisibleForTesting
    public boolean hasPrerenderedUrl(String url) {
        return nativeHasPrerenderedUrl(mNativeTabAndroid, url);
    }

    /**
     * @return the UMA object for the tab. Note that this may be null in some
     * cases.
     */
    public TabUma getTabUma() {
        return mTabUma;
    }

    /**
     * Sets the Intent that can be fired to restart the Activity of this Tab's parent.
     * Should only be called if the Tab was launched via a different Activity.
     * @return Intent that can be fired to restart the parent Activity.
     */
    public void setParentIntent(Intent parentIntent) {
        mParentIntent = parentIntent;
    }

    /**
     * @return Intent that can be fired to restart the parent Activity.
     */
    protected Intent getParentIntent() {
        return mParentIntent;
    }

    /**
     * Creates a new tab to be loaded lazily. This can be used for tabs opened in the background
     * that should be loaded when switched to. initialize() needs to be called afterwards to
     * complete the second level initialization.
     */
    @VisibleForTesting
    static Tab createTabForLazyLoad(Context context, boolean incognito, WindowAndroid nativeWindow,
            TabLaunchType type, int parentId, LoadUrlParams loadUrlParams,
            TabModelSelector tabModelSelector) {
        Tab tab = new Tab(INVALID_TAB_ID, parentId, incognito, context, nativeWindow, type, null);
        if (context != null) {
            tab.setTabUma(new TabUma(tab, TabCreationState.FROZEN_FOR_LAZY_LOAD,
                    tabModelSelector.getModel(incognito)));
        }
        tab.setPendingLoadParams(loadUrlParams);
        return tab;
    }

    /**
     * Creates a fresh tab. initialize() needs to be called afterwards to complete the second level
     * initialization.
     * @param initiallyHidden true iff the tab being created is initially in background
     */
    @VisibleForTesting
    static Tab createLiveTab(int id, Context context, boolean incognito, WindowAndroid nativeWindow,
            TabLaunchType type, int parentId, boolean initiallyHidden,
            TabModelSelector tabModelSelector) {
        Tab tab = new Tab(id, parentId, incognito, context, nativeWindow, type, null);
        if (context != null) {
            tab.setTabUma(new TabUma(tab, initiallyHidden ? TabCreationState.LIVE_IN_BACKGROUND
                                                          : TabCreationState.LIVE_IN_FOREGROUND,
                    tabModelSelector.getModel(incognito)));
        }
        return tab;
    }

    /**
     * Returns an Intent that tells Chrome to open a Tab with a particular ID.
     */
    public static Intent createBringTabToFrontIntent(int tabId) {
        String packageName = ApplicationStatus.getApplicationContext().getPackageName();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.putExtra(Browser.EXTRA_APPLICATION_ID, packageName);
        intent.putExtra(TabOpenType.BRING_TAB_TO_FRONT.name(), tabId);
        intent.setPackage(packageName);
        return intent;
    }

    private native void nativeInit();
    private native void nativeDestroy(long nativeTabAndroid);
    private native void nativeInitWebContents(long nativeTabAndroid, boolean incognito,
            ContentViewCore contentViewCore, ChromeWebContentsDelegateAndroid delegate,
            ContextMenuPopulator contextMenuPopulator);
    private native void nativeDestroyWebContents(long nativeTabAndroid, boolean deleteNative);
    private native Profile nativeGetProfileAndroid(long nativeTabAndroid);
    private native int nativeLoadUrl(long nativeTabAndroid, String url, String extraHeaders,
            byte[] postData, int transition, String referrerUrl, int referrerPolicy,
            boolean isRendererInitiated, boolean shoulReplaceCurrentEntry,
            long intentReceivedTimestamp, boolean hasUserGesture);
    private native void nativeSetActiveNavigationEntryTitleForUrl(long nativeTabAndroid, String url,
            String title);
    private native boolean nativePrint(long nativeTabAndroid);
    private native Bitmap nativeGetFavicon(long nativeTabAndroid);
    private native void nativeCreateHistoricalTab(long nativeTabAndroid);
    private native void nativeUpdateTopControlsState(
            long nativeTabAndroid, int constraints, int current, boolean animate);
    private native void nativeLoadOriginalImage(long nativeTabAndroid);
    private native void nativeSearchByImageInNewTabAsync(long nativeTabAndroid);
    private native long nativeGetBookmarkId(long nativeTabAndroid, boolean onlyEditable);
    private native boolean nativeIsOfflinePage(long nativeTabAndroid);
    private native void nativeSetInterceptNavigationDelegate(long nativeTabAndroid,
            InterceptNavigationDelegate delegate);
    private native void nativeAttachToTabContentManager(long nativeTabAndroid,
            TabContentManager tabContentManager);
    private native void nativeAttachOverlayContentViewCore(long nativeTabAndroid,
            ContentViewCore content, boolean visible);
    private native void nativeDetachOverlayContentViewCore(long nativeTabAndroid,
            ContentViewCore content);
    private native boolean nativeHasPrerenderedUrl(long nativeTabAndroid, String url);

    private static native void nativeRecordStartupToCommitUma();
}

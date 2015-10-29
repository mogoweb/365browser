// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.customtabs;

import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import android.support.customtabs.CustomTabsCallback;
import android.support.customtabs.CustomTabsIntent;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.Log;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.IntentHandler;
import org.chromium.chrome.browser.IntentHandler.ExternalAppId;
import org.chromium.chrome.browser.appmenu.AppMenuPropertiesDelegate;
import org.chromium.chrome.browser.appmenu.ChromeAppMenuPropertiesDelegate;
import org.chromium.chrome.browser.compositor.bottombar.contextualsearch.ContextualSearchPanel.StateChangeReason;
import org.chromium.chrome.browser.compositor.layouts.LayoutManagerDocument;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.SingleTabModelSelector;
import org.chromium.chrome.browser.tabmodel.TabModel.TabLaunchType;
import org.chromium.chrome.browser.toolbar.ToolbarControlContainer;
import org.chromium.chrome.browser.util.ColorUtils;
import org.chromium.chrome.browser.util.IntentUtils;
import org.chromium.chrome.browser.widget.findinpage.FindToolbarManager;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.content_public.common.Referrer;

/**
 * The activity for custom tabs. It will be launched on top of a client's task.
 */
public class CustomTabActivity extends ChromeActivity {
    private static final String TAG = "cr.CustomTabActivity";
    private static CustomTabContentHandler sActiveContentHandler;

    private CustomTab mTab;
    private FindToolbarManager mFindToolbarManager;
    private CustomTabIntentDataProvider mIntentDataProvider;
    private IBinder mSession;
    private CustomTabContentHandler mCustomTabContentHandler;

    // This is to give the right package name while using the client's resources during an
    // overridePendingTransition call.
    // TODO(ianwen, yusufo): Figure out a solution to extract external resources without having to
    // change the package name.
    private boolean mShouldOverridePackage;

    private boolean mRecordedStartupUma;

    /**
     * Sets the currently active {@link CustomTabContentHandler} in focus.
     * @param contentHandler {@link CustomTabContentHandler} to set.
     */
    public static void setActiveContentHandler(CustomTabContentHandler contentHandler) {
        sActiveContentHandler = contentHandler;
    }

    /**
     * Used to check whether an incoming intent can be handled by the
     * current {@link CustomTabContentHandler}.
     * @return Whether the active {@link CustomTabContentHandler} has handled the intent.
     */
    public static boolean handleInActiveContentIfNeeded(Intent intent) {
        if (sActiveContentHandler == null) return false;

        if (sActiveContentHandler.shouldIgnoreIntent(intent)) {
            Log.w(TAG, "Incoming intent to Custom Tab was ignored.");
            return false;
        }

        IBinder session = IntentUtils.safeGetBinderExtra(intent, CustomTabsIntent.EXTRA_SESSION);
        if (session == null || !session.equals(sActiveContentHandler.getSession())) return false;

        String url = IntentHandler.getUrlFromIntent(intent);
        if (TextUtils.isEmpty(url)) return false;
        sActiveContentHandler.loadUrlAndTrackFromTimestamp(new LoadUrlParams(url),
                IntentHandler.getTimestampFromIntent(intent));
        return true;
    }

    @Override
    public boolean isCustomTab() {
        return true;
    }

    @Override
    public void onStart() {
        super.onStart();
        CustomTabsConnection.getInstance(getApplication())
                .keepAliveForSession(mIntentDataProvider.getSession(),
                        mIntentDataProvider.getKeepAliveServiceIntent());
    }

    @Override
    public void onStop() {
        super.onStop();
        CustomTabsConnection.getInstance(getApplication())
                .dontKeepAliveForSession(mIntentDataProvider.getSession());
    }

    @Override
    public void preInflationStartup() {
        super.preInflationStartup();
        setTabModelSelector(new SingleTabModelSelector(this, false, true) {
            @Override
            public Tab openNewTab(LoadUrlParams loadUrlParams, TabLaunchType type, Tab parent,
                    boolean incognito) {
                // A custom tab either loads a url or starts an external activity to handle the url.
                // It never opens a new tab/chrome activity.
                mTab.loadUrl(loadUrlParams);
                return mTab;
            }
        });
        mIntentDataProvider = new CustomTabIntentDataProvider(getIntent(), this);
        supportRequestWindowFeature(Window.FEATURE_ACTION_MODE_OVERLAY);
    }

    @Override
    public void postInflationStartup() {
        super.postInflationStartup();
        getToolbarManager().setCloseButtonDrawable(mIntentDataProvider.getCloseButtonDrawable());
        getToolbarManager().setShowTitle(mIntentDataProvider.getTitleVisibilityState()
                == CustomTabIntentDataProvider.SHOW_PAGE_TITLE);
        int toolbarColor = mIntentDataProvider.getToolbarColor();
        getToolbarManager().updatePrimaryColor(toolbarColor);
        if (toolbarColor != getResources().getColor(R.color.default_primary_color)) {
            ApiCompatibilityUtils.setStatusBarColor(getWindow(),
                    ColorUtils.getDarkenedColorForStatusBar(toolbarColor));
        }

        // Setting task title and icon to be null will preserve the client app's title and icon.
        ApiCompatibilityUtils.setTaskDescription(this, null, null, toolbarColor);
        if (mIntentDataProvider.shouldShowActionButton()) {
            getToolbarManager().addCustomActionButton(mIntentDataProvider.getActionButtonIcon(),
                    mIntentDataProvider.getActionButtonDescription(), new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            mIntentDataProvider.sendButtonPendingIntentWithUrl(
                                    getApplicationContext(), mTab.getUrl());
                            RecordUserAction.record("CustomTabsCustomActionButtonClick");
                        }
                    });
        }
    }

    @Override
    public void finishNativeInitialization() {
        mSession = mIntentDataProvider.getSession();
        String url = IntentHandler.getUrlFromIntent(getIntent());
        // Get any referrer that has been explicitly set by the app.
        String referrerUrl = IntentHandler.getReferrerUrlIncludingExtraHeaders(getIntent(), this);
        if (referrerUrl == null) {
            Referrer referrer = CustomTabsConnection.getInstance(getApplication())
                    .getReferrerForSession(mSession);
            if (referrer != null) referrerUrl = referrer.getUrl();
        }
        // If extra headers have been passed, cancel any current prerender, as
        // prerendering doesn't support extra headers.
        if (IntentHandler.getExtraHeadersFromIntent(getIntent()) != null) {
            CustomTabsConnection.getInstance(getApplication())
                    .takePrerenderedUrl(mSession, "", null);
        }

        mTab = new CustomTab(this, getWindowAndroid(), mSession, url, referrerUrl,
                Tab.INVALID_TAB_ID, mIntentDataProvider.shouldEnableUrlBarHiding());
        getTabModelSelector().setTab(mTab);

        ToolbarControlContainer controlContainer = (ToolbarControlContainer) findViewById(
                R.id.control_container);
        LayoutManagerDocument layoutDriver = new LayoutManagerDocument(getCompositorViewHolder());
        initializeCompositorContent(layoutDriver, findViewById(R.id.url_bar),
                (ViewGroup) findViewById(android.R.id.content), controlContainer);
        mFindToolbarManager = new FindToolbarManager(this, getTabModelSelector(),
                getToolbarManager().getActionModeController().getActionModeCallback());
        getToolbarManager().initializeWithNative(getTabModelSelector(), getFullscreenManager(),
                mFindToolbarManager, null, layoutDriver, null, null, null,
                new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        CustomTabActivity.this.finish();
                    }
                });

        mTab.setFullscreenManager(getFullscreenManager());
        mCustomTabContentHandler = new CustomTabContentHandler() {
            @Override
            public void loadUrlAndTrackFromTimestamp(LoadUrlParams params, long timestamp) {
                loadUrlInCurrentTab(params, timestamp);
            }

            @Override
            public IBinder getSession() {
                return mSession;
            }

            @Override
            public boolean shouldIgnoreIntent(Intent intent) {
                return mIntentHandler.shouldIgnoreIntent(CustomTabActivity.this, intent);
            }
        };
        loadUrlInCurrentTab(new LoadUrlParams(url),
                IntentHandler.getTimestampFromIntent(getIntent()));
        super.finishNativeInitialization();
    }

    @Override
    public void onStartWithNative() {
        super.onStartWithNative();
        setActiveContentHandler(mCustomTabContentHandler);

        if (!mRecordedStartupUma) {
            mRecordedStartupUma = true;
            ExternalAppId externalId =
                    IntentHandler.determineExternalIntentSource(getPackageName(), getIntent());
            RecordHistogram.recordEnumeratedHistogram("CustomTabs.ClientAppId",
                    externalId.ordinal(), ExternalAppId.INDEX_BOUNDARY.ordinal());
        }
    }


    @Override
    public void onPauseWithNative() {
        super.onPauseWithNative();
        CustomTabsConnection.getInstance(getApplication()).notifyNavigationEvent(
                mSession, CustomTabsCallback.TAB_HIDDEN);
    }

    @Override
    public void onStopWithNative() {
        super.onStopWithNative();
        setActiveContentHandler(null);
    }

    /**
     * Loads the current tab with the given load params. Unlike
     * {@link CustomTab#loadUrlAndTrackFromTimestamp(LoadUrlParams, long)}, this method takes client
     * referrer and extra headers into account.
     */
    private void loadUrlInCurrentTab(LoadUrlParams params, long timeStamp) {
        Intent intent = getIntent();
        IntentHandler.addReferrerAndHeaders(params, intent, this);
        if (params.getReferrer() == null) {
            params.setReferrer(CustomTabsConnection.getInstance(getApplication())
                    .getReferrerForSession(mSession));
        }
        mTab.loadUrlAndTrackFromTimestamp(params, timeStamp);
    }

    @Override
    public void createContextualSearchTab(String searchUrl) {
        if (mTab == null) return;
        mTab.loadUrl(new LoadUrlParams(searchUrl));
    }

    @Override
    public SingleTabModelSelector getTabModelSelector() {
        return (SingleTabModelSelector) super.getTabModelSelector();
    }

    @Override
    protected ChromeAppMenuPropertiesDelegate createAppMenuPropertiesDelegate() {
        return new CustomTabAppMenuPropertiesDelegate(this, mIntentDataProvider.getMenuTitles());
    }

    @Override
    protected int getAppMenuLayoutId() {
        return R.menu.custom_tabs_menu;
    }

    @Override
    protected int getControlContainerLayoutId() {
        return R.layout.custom_tabs_control_container;
    }

    @Override
    public int getControlContainerHeightResource() {
        return R.dimen.custom_tabs_control_container_height;
    }

    @Override
    public String getPackageName() {
        if (mShouldOverridePackage) return mIntentDataProvider.getClientPackageName();
        return super.getPackageName();
    }

    @Override
    public void finish() {
        super.finish();
        if (mIntentDataProvider.shouldAnimateOnFinish()) {
            mShouldOverridePackage = true;
            overridePendingTransition(mIntentDataProvider.getAnimationEnterRes(),
                    mIntentDataProvider.getAnimationExitRes());
            mShouldOverridePackage = false;
        }
    }

    @Override
    protected boolean handleBackPressed() {
        if (mTab == null) return false;
        if (mTab.canGoBack()) {
            mTab.goBack();
        } else {
            finish();
        }
        return true;
    }

    @Override
    public boolean shouldShowAppMenu() {
        return mTab != null && getToolbarManager().isInitialized();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int menuIndex = getAppMenuPropertiesDelegate().getIndexOfMenuItem(item);
        if (menuIndex >= 0) {
            mIntentDataProvider.clickMenuItemWithUrl(getApplicationContext(), menuIndex,
                    getTabModelSelector().getCurrentTab().getUrl());
            RecordUserAction.record("CustomTabsMenuCustomMenuItem");
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onMenuOrKeyboardAction(int id, boolean fromMenu) {
        if (id == R.id.show_menu) {
            if (shouldShowAppMenu()) {
                getAppMenuHandler().showAppMenu(null, false);
                return true;
            }
        } else if (id == R.id.open_in_chrome_id) {
            String url = getTabModelSelector().getCurrentTab().getUrl();
            Intent chromeIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            chromeIntent.setPackage(getApplicationContext().getPackageName());
            chromeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(chromeIntent);
            RecordUserAction.record("CustomTabsMenuOpenInChrome");
            return true;
        } else if (id == R.id.find_in_page_id) {
            mFindToolbarManager.showToolbar();
            if (getContextualSearchManager() != null) {
                getContextualSearchManager().hideContextualSearch(StateChangeReason.UNKNOWN);
            }
            if (fromMenu) {
                RecordUserAction.record("MobileMenuFindInPage");
            } else {
                RecordUserAction.record("MobileShortcutFindInPage");
            }
            return true;
        }
        return super.onMenuOrKeyboardAction(id, fromMenu);
    }

    /**
     * @return The {@link AppMenuPropertiesDelegate} associated with this activity. For test
     *         purposes only.
     */
    @VisibleForTesting
    @Override
    public CustomTabAppMenuPropertiesDelegate getAppMenuPropertiesDelegate() {
        return (CustomTabAppMenuPropertiesDelegate) super.getAppMenuPropertiesDelegate();
    }

    /**
     * @return The {@link CustomTabIntentDataProvider} for this {@link CustomTabActivity}. For test
     *         purposes only.
     */
    @VisibleForTesting
    CustomTabIntentDataProvider getIntentDataProvider() {
        return mIntentDataProvider;
    }
}

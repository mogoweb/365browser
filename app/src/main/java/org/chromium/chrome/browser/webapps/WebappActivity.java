// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.webapps;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.chromium.base.ActivityState;
import org.chromium.base.ApplicationStatus;
import org.chromium.base.VisibleForTesting;
import org.chromium.blink_public.platform.WebDisplayMode;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ShortcutHelper;
import org.chromium.chrome.browser.UrlUtilities;
import org.chromium.chrome.browser.document.DocumentUtils;
import org.chromium.chrome.browser.fullscreen.ChromeFullscreenManager;
import org.chromium.chrome.browser.ssl.ConnectionSecurityLevel;
import org.chromium.chrome.browser.tab.EmptyTabObserver;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabObserver;
import org.chromium.chrome.browser.util.FeatureUtilities;
import org.chromium.content.browser.ScreenOrientationProvider;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.content_public.browser.WebContentsObserver;
import org.chromium.net.NetworkChangeNotifier;
import org.chromium.ui.base.PageTransition;

import java.io.File;

/**
 * Displays a webapp in a nearly UI-less Chrome (InfoBars still appear).
 */
public class WebappActivity extends FullScreenActivity {
    public static final String WEBAPP_SCHEME = "webapp";

    private static final String TAG = "WebappActivity";
    private static final long MS_BEFORE_NAVIGATING_BACK_FROM_INTERSTITIAL = 1000;

    private final WebappInfo mWebappInfo;
    private AsyncTask<Void, Void, Void> mCleanupTask;

    private WebContentsObserver mWebContentsObserver;

    private ViewGroup mSplashScreen;
    private WebappUrlBar mUrlBar;

    private boolean mIsInitialized;
    private Integer mBrandColor;

    /**
     * Construct all the variables that shouldn't change.  We do it here both to clarify when the
     * objects are created and to ensure that they exist throughout the parallelized initialization
     * of the WebappActivity.
     */
    public WebappActivity() {
        mWebappInfo = WebappInfo.createEmpty();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if (intent == null) return;
        super.onNewIntent(intent);

        WebappInfo newWebappInfo = WebappInfo.create(intent);
        if (newWebappInfo == null) {
            Log.e(TAG, "Failed to parse new Intent: " + intent);
            finish();
        } else if (!TextUtils.equals(mWebappInfo.id(), newWebappInfo.id())) {
            mWebappInfo.copy(newWebappInfo);
            resetSavedInstanceState();
            if (mIsInitialized) initializeUI(null);
        }
    }

    private void initializeUI(Bundle savedInstanceState) {
        // We do not load URL when restoring from saved instance states.
        if (savedInstanceState == null && mWebappInfo.isInitialized()) {
            if (TextUtils.isEmpty(getActivityTab().getUrl())) {
                getActivityTab().loadUrl(new LoadUrlParams(
                        mWebappInfo.uri().toString(), PageTransition.AUTO_TOPLEVEL));
            }
        } else {
            if (NetworkChangeNotifier.isOnline()) getActivityTab().reloadIgnoringCache();
        }

        mWebContentsObserver = createWebContentsObserver();
        getActivityTab().addObserver(createTabObserver());
        getActivityTab().getChromeWebContentsDelegateAndroid().setDisplayMode(
                (int) WebDisplayMode.Standalone);
        updateTaskDescription();
    }

    @Override
    public void preInflationStartup() {
        WebappInfo info = WebappInfo.create(getIntent());
        if (info != null) mWebappInfo.copy(info);

        updateTaskDescription();

        mCleanupTask = new WebappDirectoryManager(getActivityDirectory(),
                WEBAPP_SCHEME, FeatureUtilities.isDocumentModeEligible(this));

        ScreenOrientationProvider.lockOrientation((byte) mWebappInfo.orientation(), this);
        super.preInflationStartup();
    }

    @Override
    public void finishNativeInitialization() {
        if (!mWebappInfo.isInitialized()) finish();
        super.finishNativeInitialization();
        initializeUI(getSavedInstanceState());
        mIsInitialized = true;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (getActivityTab() != null) getActivityTab().saveInstanceState(outState);
    }

    @Override
    public void onStartWithNative() {
        super.onStartWithNative();
        if (mCleanupTask.getStatus() == AsyncTask.Status.PENDING) mCleanupTask.execute();
    }

    @Override
    public void onStopWithNative() {
        super.onStopWithNative();
        mCleanupTask.cancel(true);
        if (getActivityTab() != null) getActivityTab().saveState(getActivityDirectory());
        if (getFullscreenManager() != null) {
            getFullscreenManager().setPersistentFullscreenMode(false);
        }
    }

    @Override
    public void onResume() {
        if (!isFinishing() && getIntent() != null) {
            // Avoid situations where Android starts two Activities with the same data.
            DocumentUtils.finishOtherTasksWithData(getIntent().getData(), getTaskId());
        }
        super.onResume();
    }
    @Override
    protected int getControlContainerLayoutId() {
        return R.layout.webapp_control_container;
    }

    @Override
    public void postInflationStartup() {
        ViewGroup contentView = (ViewGroup) findViewById(android.R.id.content);
        mSplashScreen = (ViewGroup) LayoutInflater.from(this)
                .inflate(R.layout.webapp_splashscreen, contentView, false);

        if (mWebappInfo.backgroundColor() == ShortcutHelper.MANIFEST_COLOR_INVALID_OR_MISSING) {
            mSplashScreen.setBackgroundResource(R.color.webapp_default_bg);
        } else {
            mSplashScreen.setBackgroundColor((int) mWebappInfo.backgroundColor());
        }
        contentView.addView(mSplashScreen);

        super.postInflationStartup();
        WebappControlContainer controlContainer =
                (WebappControlContainer) findViewById(R.id.control_container);
        mUrlBar = (WebappUrlBar) controlContainer.findViewById(R.id.webapp_url_bar);
    }

    /**
     * @return Structure containing data about the webapp currently displayed.
     */
    WebappInfo getWebappInfo() {
        return mWebappInfo;
    }

    private void updateUrlBar() {
        Tab tab = getActivityTab();
        if (tab == null || mUrlBar == null) return;
        mUrlBar.update(tab.getUrl(), tab.getSecurityLevel());
    }

    private WebContentsObserver createWebContentsObserver() {
        // TODO: Move to TabObserver eventually.
        return new WebContentsObserver(getActivityTab().getWebContents()) {
            @Override
            public void didNavigateMainFrame(String url, String baseUrl,
                    boolean isNavigationToDifferentPage, boolean isNavigationInPage,
                    int statusCode) {
                updateUrlBar();
            }

            @Override
            public void didAttachInterstitialPage() {
                updateUrlBar();

                int state = ApplicationStatus.getStateForActivity(WebappActivity.this);
                if (state == ActivityState.PAUSED || state == ActivityState.STOPPED
                        || state == ActivityState.DESTROYED) {
                    return;
                }

                // Kick the interstitial navigation to Chrome.
                Intent intent = new Intent(
                        Intent.ACTION_VIEW, Uri.parse(getActivityTab().getUrl()));
                intent.setPackage(getPackageName());
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);

                // Pretend like the navigation never happened.  We delay so that this happens while
                // the Activity is in the background.
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        getActivityTab().goBack();
                    }
                }, MS_BEFORE_NAVIGATING_BACK_FROM_INTERSTITIAL);
            }

            @Override
            public void didDetachInterstitialPage() {
                updateUrlBar();
            }

            @Override
            public void didFirstVisuallyNonEmptyPaint() {
                if (mSplashScreen == null) return;

                mSplashScreen.animate()
                        .alpha(0f)
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                ViewGroup contentView =
                                        (ViewGroup) findViewById(android.R.id.content);
                                contentView.removeView(mSplashScreen);
                                mSplashScreen = null;
                            }
                        });
            }
        };
    }

    private boolean isWebappDomain() {
        return UrlUtilities.sameDomainOrHost(
                getActivityTab().getUrl(), getWebappInfo().uri().toString(), true);
    }

    protected TabObserver createTabObserver() {
        return new EmptyTabObserver() {
            @Override
            public void onSSLStateUpdated(Tab tab) {
                updateUrlBar();
            }

            @Override
            public void onDidStartProvisionalLoadForFrame(
                    Tab tab, long frameId, long parentFrameId, boolean isMainFrame,
                    String validatedUrl, boolean isErrorPage, boolean isIframeSrcdoc) {
                if (isMainFrame) updateUrlBar();
            }

            @Override
            public void onDidChangeThemeColor(Tab tab, int color) {
                if (!isWebappDomain()) return;
                mBrandColor = color;
                updateTaskDescription();
            }

            @Override
            public void onTitleUpdated(Tab tab) {
                if (!isWebappDomain()) return;
                updateTaskDescription();
            }

            @Override
            public void onFaviconUpdated(Tab tab) {
                if (!isWebappDomain()) return;
                updateTaskDescription();
            }
        };
    }

    private void updateTaskDescription() {
        String title = null;
        if (!TextUtils.isEmpty(mWebappInfo.shortName())) {
            title = mWebappInfo.shortName();
        } else if (getActivityTab() != null) {
            title = getActivityTab().getTitle();
        }

        Bitmap icon = null;
        if (mWebappInfo.icon() != null) {
            icon = mWebappInfo.icon();
        } else if (getActivityTab() != null) {
            icon = getActivityTab().getFavicon();
        }

        if (mBrandColor == null
                && mWebappInfo.themeColor() != ShortcutHelper.MANIFEST_COLOR_INVALID_OR_MISSING
                && (mWebappInfo.themeColor() & 0xFF000000L) != 0) {
            mBrandColor = (int) mWebappInfo.themeColor();
        }
        int color = mBrandColor == null
                ? getResources().getColor(R.color.default_primary_color) : mBrandColor;

        DocumentUtils.updateTaskDescription(this, title, icon, color, mBrandColor == null);
    }

    /**
     * Get the active directory by this web app.
     *
     * @return The directory used for the current web app.
     */
    @Override
    protected File getActivityDirectory() {
        return WebappDirectoryManager.getWebappDirectory(mWebappInfo.id());
    }

    @VisibleForTesting
    WebappUrlBar getUrlBarForTests() {
        return mUrlBar;
    }

    @VisibleForTesting
    boolean isUrlBarVisible() {
        return findViewById(R.id.control_container).getVisibility() == View.VISIBLE;
    }

    @Override
    protected final ChromeFullscreenManager createFullscreenManager(View controlContainer) {
        return new ChromeFullscreenManager(this, controlContainer, getTabModelSelector(),
                getControlContainerHeightResource(), false /* supportsBrowserOverride */);
    }

    @Override
    public int getControlContainerHeightResource() {
        return R.dimen.webapp_control_container_height;
    }

    @Override
    protected Drawable getBackgroundDrawable() {
        return null;
    }

    // Implements {@link FullScreenActivityTab.TopControlsVisibilityDelegate}.
    @Override
    public boolean shouldShowTopControls(String url, int securityLevel) {
        boolean visible = false;  // do not show top controls when URL is not ready yet.
        if (!TextUtils.isEmpty(url)) {
            boolean isSameWebsite =
                    UrlUtilities.sameDomainOrHost(mWebappInfo.uri().toString(), url, true);
            visible = !isSameWebsite || securityLevel == ConnectionSecurityLevel.SECURITY_ERROR
                    || securityLevel == ConnectionSecurityLevel.SECURITY_WARNING;
        }

        return visible;
    }

    // We're temporarily disable CS on webapp since there are some issues. (http://crbug.com/471950)
    // TODO(changwan): re-enable it once the issues are resolved.
    @Override
    protected boolean isContextualSearchAllowed() {
        return false;
    }
}

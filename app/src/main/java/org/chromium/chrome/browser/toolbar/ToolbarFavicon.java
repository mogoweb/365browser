/*
 * Copyright (c) 2015-2016, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package org.chromium.chrome.browser.toolbar;

import android.animation.Animator;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.ValueCallback;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.ApplicationStatus;
import org.chromium.base.ContextUtils;
import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.NativePage;
import org.chromium.chrome.browser.SiteTileView;
import org.chromium.chrome.browser.ntp.IncognitoNewTabPage;
import org.chromium.chrome.browser.preferences.PreferencesLauncher;
import org.chromium.chrome.browser.preferences.SecureConnectPreferenceHandler;
import org.chromium.chrome.browser.preferences.website.BrowserSingleWebsitePreferences;
import org.chromium.chrome.browser.preferences.website.WebDefenderPreferenceHandler;
import org.chromium.chrome.browser.preferences.website.WebRefinerPreferenceHandler;
import org.chromium.chrome.browser.tab.EmptyTabObserver;
import org.chromium.chrome.browser.util.ColorUtils;
import org.chromium.chrome.browser.util.UrlUtilities;
import org.chromium.chrome.browser.favicon.FaviconHelper;
import org.chromium.chrome.browser.favicon.LargeIconBridge;
import org.chromium.chrome.browser.ntp.NewTabPage;
import org.chromium.chrome.browser.preferences.Preferences;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.search_engines.TemplateUrlService;
import org.chromium.components.security_state.ConnectionSecurityLevel;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabObserver;
import org.chromium.chrome.browser.widget.BrowserTooltip;
import org.chromium.chrome.browser.widget.RoundedIconGenerator;
import org.chromium.content.browser.SecureConnect;
import org.chromium.content.browser.WebDefender;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.ui.interpolators.BakedBezierInterpolator;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ToolbarFavicon implements View.OnClickListener {

    private SiteTileView mFaviconView;
    private Context mContext;
    private ToolbarLayout mParent;
    private TabObserver mTabObserver;
    private Bitmap mFavicon;
    private Tab mTab;
    private LargeIconBridge mLargeIconBridge;
    private boolean mbSiteSettingsVisible;
    private boolean mBlockedCountSet = false;
    //Variable to track when the layout has decided to hide the favicon.
    private boolean mBrowsingModeViewsHidden = false;
    private static int mDefaultThemeColor;
    private static int mDefaultThemeColorIncognito;
    private boolean mUsingBrandColor;

    private static Map<String, HashMap<Integer, Integer>> mFaviconDominantColorsForUrls =
            new HashMap<>();

    //Favicon statics
    private static final int FAVICON_MIN_SIZE_PX = 48;
    public static final int SEARCHENGINE_FAVICON_MIN_SIZE_PX = 16;
    private static final int FAVICON_CORNER_RADIUS = 4;
    private static final int FAVICON_TEXT_SIZE = 20;
    public static final int OVERRIDE_SEARCHENGINE_COLOR = 0xff4285f4;

    private static final int URL_FOCUS_TOOLBAR_BUTTONS_DURATION_MS = 100;
    private static final int URL_CLEAR_FOCUS_TABSTACK_DELAY_MS = 200;

    public static final String PREF_FAVICON_CLICK_FOR_SEARCH_ENGINE =
            "org.chromium.chrome.browser.toolbar.ToolbarFavicon.SEARCH_ENGINE_CLICK";
    public static final String PREF_FAVICON_CLICK_FOR_SITE_SETTINGS =
            "org.chromium.chrome.browser.toolbar.ToolbarFavicon.SITE_SETTINGS_CLICK";
    public static final String PREF_FAVICON_CLICK_FOR_SECURITY_INFO =
            "org.chromium.chrome.browser.toolbar.ToolbarFavicon.SECURITY_INFO_CLICK";
    public static final String PREF_FAVICON_CLICK_FOR_SITE_INTEGRITY =
            "org.chromium.chrome.browser.toolbar.ToolbarFavicon.SITE_INTEGRITY_CLICK";
    public static final String PREF_FAVICON_CLICK_FOR_MIXED_CONTENT =
            "org.chromium.chrome.browser.toolbar.ToolbarFavicon.MIXED_CONTENT_CLICK";
    public static final String PREF_FAVICON_CLICK_FOR_MAINFRAME_UPGRADE =
            "org.chromium.chrome.browser.toolbar.ToolbarFavicon.MAINFRAME_UPGRADE_CLICK";
    public static final String PREF_FAVICON_CLICK_FOR_SUBFRAME_UPGRADE =
            "org.chromium.chrome.browser.toolbar.ToolbarFavicon.SUBFRAME_UPGRADE_CLICK";
    public static final String PREF_FAVICON_TAB_LOAD_COUNT =
            "org.chromium.chrome.browser.toolbar.ToolbarFavicon.TAB_LOAD_COUNT";
    public static final String PREF_FAVICON_NEWTAB_LOAD_COUNT =
            "org.chromium.chrome.browser.toolbar.ToolbarFavicon.NEWTAB_LOAD_COUNT";

    private enum TooltipPrecedence {
        ZEROTH, //DO NOT USE
        SEARCH_ENGINE,
        SITE_SETTINGS,
        SITE_INTEGRITY,
        SECURITY_INFO,
        SUBFRAME_UPGRADED,
        MAINFRAME_UPGRADED,
        MIXED_CONTENT
    }

    private ValueAnimator mAnimator;
    private Integer mStatusBarColor;

    private TemplateUrlService.TemplateUrlServiceObserver mTemplateUrlObserver;
    private TemplateUrlService.LoadListener mTemplateUrlLoadListener;
    private String[] mSearchEngineNames;
    private int[] mSearchEngineIndices;
    private int mDefaultSearchEngineIndex;

    private BrowserTooltip mTooltip;
    private String mTooltipPref;
    private int mTooltipMaxShowCount = 0;
    private int mTooltipTimeoutMS = 5000;
    private boolean mDiscardFocusChange;
    private boolean mFakeIconGenerated;

    public ToolbarFavicon(final ToolbarLayout parent) {
        mFaviconView = (SiteTileView) parent.findViewById(R.id.swe_favicon_badge);
        if (mFaviconView != null) {
            mFaviconView.setOnClickListener(this);
            mParent = parent;
            mContext = ContextUtils.getApplicationContext();
            mDefaultThemeColor = ApiCompatibilityUtils.getColor(mContext.getResources(),
                    R.color.default_primary_color);
            mDefaultThemeColorIncognito = ApiCompatibilityUtils.getColor(mContext.getResources(),
                    R.color.incognito_primary_color);
            mTooltipMaxShowCount = mContext.getResources().
                    getInteger(R.integer.tooltips_max_show_count);
            mTooltipTimeoutMS = mContext.getResources().getInteger(R.integer.tooltips_timeout_ms);

            mTabObserver = new EmptyTabObserver() {
                @Override
                public void onSSLStateUpdated(Tab tab) {
                    refreshTabSecurityState();
                    refreshPageUpgrade();
                }

                //onContentChanged notifies us when the nativePages are modified/swapped
                @Override
                public void onContentChanged(Tab tab) {
                    refreshFavicon();
                    refreshPageUpgrade();
                }

                @Override
                public void onPageLoadStarted(Tab tab, String url) {
                    mBlockedCountSet = false;
                    refreshBlockedCount();

                    mUsingBrandColor = false;

                    if (mTooltip != null)
                        mTooltip.dismiss();

                    SharedPreferences prefs = PreferenceManager.
                            getDefaultSharedPreferences(mContext);
                    SharedPreferences.Editor editor = prefs.edit();

                    if (NewTabPage.isNTPUrl(url)) {
                        int ntpCount = prefs.getInt(PREF_FAVICON_NEWTAB_LOAD_COUNT, 0);
                        editor.putInt(PREF_FAVICON_NEWTAB_LOAD_COUNT, ntpCount + 1);
                        mDiscardFocusChange = true;
                    } else {
                        try {
                            URI parsedUrl = new URI(url);
                            if (!UrlUtilities.isInternalScheme(parsedUrl)) {
                                int count = prefs.getInt(PREF_FAVICON_TAB_LOAD_COUNT, 0);
                                editor.putInt(PREF_FAVICON_TAB_LOAD_COUNT, count + 1);
                                mDiscardFocusChange = false;
                            }
                        } catch (URISyntaxException e) {
                        }
                    }
                    editor.apply();
                }

                @Override
                public void onLoadUrl(Tab tab, LoadUrlParams params, int loadType) {
                    refreshFavicon();
                    refreshTabSecurityState();
                }

                @Override
                public void onDidNavigateMainFrame(Tab tab, String url, String baseUrl,
                                                   boolean isNavigationToDifferentPage,
                                                   boolean isFragmentNavigation, int statusCode) {
                    if (isNavigationToDifferentPage) {
                        refreshFavicon();
                        refreshTabSecurityState();
                    }
                }

                @Override
                public void onDidCommitProvisionalLoadForFrame(Tab tab, long frameId,
                                           boolean isMainFrame, String url, int transitionType) {
                    refreshPageUpgrade();
                }

                @Override
                public void onPageLoadFinished(Tab tab) {
                    refreshTabSecurityState();
                }

                @Override
                public void onLoadProgressChanged(Tab tab, int progress) {
                    refreshBlockedCount();
                }

                @Override
                public void onFaviconUpdated(Tab tab, Bitmap icon) {
                    refreshFavicon();
                }

                @Override
                public void onDidChangeThemeColor(Tab tab, int color) {
                    mUsingBrandColor = isBrandColor(color, tab.isIncognito());
                    if (mUsingBrandColor)
                        setStatusBarColor(color);
                    else if (!mFakeIconGenerated  && mFavicon != null)
                        setStatusBarColor(FaviconHelper.getDominantColorForBitmap(mFavicon));
                }

                @Override
                public void onWebContentsSwapped(Tab tab,
                                                 boolean didStartLoad, boolean didFinishLoad) {
                    if (didFinishLoad || didStartLoad) {
                        refreshBlockedCount();
                        refreshPageUpgrade();
                    }
                }

            };

            refreshTab(mParent.getToolbarDataProvider().getTab());

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                final Activity activity = ApplicationStatus.getLastTrackedFocusedActivity();
                mStatusBarColor = activity.getWindow().getStatusBarColor();
            }

            mFaviconView.setOnVisibilityChangeListener(new ValueCallback<View>() {
                @Override
                public void onReceiveValue(View view) {
                    if (view.getVisibility() != View.VISIBLE) {
                        synchronized (ToolbarFavicon.this) {
                            if (mTooltip != null) {
                                mTooltip.dismiss();
                                mTooltip = null;
                            }
                        }
                    }
                }
            });
        }

        mbSiteSettingsVisible = false;
        mDiscardFocusChange = false;
    }

    private void refreshPageUpgrade() {
        if (mTab == null || mTab.getContentViewCore() == null || !tabHasPermissions()) {
            mFaviconView.setPageUpgradeBadge(SiteTileView.UPGRADE_STATE_NONE);
            return;
        }
        SecureConnect.Info info = SecureConnectPreferenceHandler.getInfo(mTab.getContentViewCore());
        if (info == null) {
            mFaviconView.setPageUpgradeBadge(SiteTileView.UPGRADE_STATE_NONE);
            return;
        }

        if (info.wasMainFrameUpgraded()) {
            if (info.hasMixedContent()) {
                showToolTip(R.string.tooltip_favicon_page_mixed_content,
                        TooltipPrecedence.MIXED_CONTENT.ordinal(),
                        PREF_FAVICON_CLICK_FOR_MIXED_CONTENT);
                mFaviconView.setPageUpgradeBadge(SiteTileView.UPGRADE_STATE_MIXED_CONTENT);
            } else {
                showToolTip(R.string.tooltip_favicon_page_mainframe_upgraded,
                        TooltipPrecedence.MAINFRAME_UPGRADED.ordinal(),
                        PREF_FAVICON_CLICK_FOR_MAINFRAME_UPGRADE);
                mFaviconView.setPageUpgradeBadge(SiteTileView.UPGRADE_STATE_MAINFRAME);
            }
        } else if (info.mSubFrameURLs != null && info.mSubFrameURLs.length > 0) {
            //showDifferentToolTip
            showToolTip(R.string.tooltip_favicon_page_subframes_upgraded,
                    TooltipPrecedence.SUBFRAME_UPGRADED.ordinal(),
                    PREF_FAVICON_CLICK_FOR_SUBFRAME_UPGRADE);
            mFaviconView.setPageUpgradeBadge(SiteTileView.UPGRADE_STATE_NONE);
        } else {
            mFaviconView.setPageUpgradeBadge(SiteTileView.UPGRADE_STATE_NONE);
        }

    }

    public void onUrlFocusChange(boolean hasFocus) {
        if (hasFocus) {
            if (!mDiscardFocusChange) {
                synchronized (ToolbarFavicon.this) {
                    if (mTooltip != null) {
                        mTooltip.dismiss();
                        mTooltip = null;
                    }
                }
            }
        }
        mDiscardFocusChange = false;
    }

    private boolean isBrandColor(int color, boolean isIncognitoTab) {
        if (isIncognitoTab)
            return color != mDefaultThemeColorIncognito;
        else
            return color != mDefaultThemeColor;
    }

    /**
     * @return True if tab doesn't exist/Or a Navtive page is visible since we don't want to update
     * the favicon when there's no tab or when showing a native page.
     */
    private boolean isNativePage() {
        return (mTab == null) ? true : mTab.isNativePage();
    }

    public static void showOfflinePageDialog(Context ctx, final Tab tab) {
        new AlertDialog.Builder(ctx)
                .setTitle(R.string.offline_pages_viewing_offline_page)
                .setMessage(tab.getOriginalUrl())
                .setPositiveButton(R.string.menu_load_live_page_for_offline_page,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                String liveUrl = tab.getOriginalUrl();
                                if (!TextUtils.isEmpty(liveUrl)) {
                                    tab.loadUrl(new LoadUrlParams(liveUrl));
                                }
                            }
                        })
                .setNegativeButton(R.string.cancel,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        })
                .show();
    }

    private void showSearchEngineSelectionDialog(Context ctx) {
        new AlertDialog.Builder(ctx)
                .setSingleChoiceItems(
                        mSearchEngineNames,
                        mDefaultSearchEngineIndex,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                final int index = which;
                                ThreadUtils.runOnUiThread(
                                        new Runnable() {
                                            @Override
                                            public void run() {
                                                TemplateUrlService.getInstance()
                                                     .setSearchEngine(mSearchEngineIndices[index]);
                                            }
                                        }
                                );
                                dialog.dismiss();
                            }
                        }
                )
                .setTitle(R.string.prefs_search_engine)
                .show();
    }

    @Override
    public void onClick(View v) {
        if (mFaviconView == v && mTab != null && !mBrowsingModeViewsHidden) {
            NativePage page = mTab.getNativePage();
            if (page instanceof NewTabPage) {
                showSearchEngineSelectionDialog(v.getContext());
                if (mTooltipPref != null) {
                    tooltipAcknowledged(mTooltipPref);
                }
            } else if (mTab.isOfflinePage()) {
                showOfflinePageDialog(v.getContext(), mTab);
            } else if (tabHasPermissions()) {
                showCurrentSiteSettings();
                if (mTooltipPref != null) {
                    tooltipAcknowledged(mTooltipPref);
                }
                mbSiteSettingsVisible = true;
            }
        }
    }

    /**
     * Add the favicon unfocus animator to a list of animators
     * @param animators The list of animators to which this animation should be added.
     */
    public void addUrlFocusLostAnimator(List<Animator> animators) {
        if (mFaviconView != null) {
            Animator animator = ObjectAnimator.ofFloat(mFaviconView, View.TRANSLATION_X, 0);
            animator.setDuration(URL_FOCUS_TOOLBAR_BUTTONS_DURATION_MS);
            animator.setStartDelay(URL_CLEAR_FOCUS_TABSTACK_DELAY_MS);
            animator.setInterpolator(BakedBezierInterpolator.TRANSFORM_CURVE);
            animators.add(animator);

            animator = ObjectAnimator.ofFloat(mFaviconView, View.ALPHA, 1);
            animator.setDuration(URL_FOCUS_TOOLBAR_BUTTONS_DURATION_MS);
            animator.setStartDelay(URL_CLEAR_FOCUS_TABSTACK_DELAY_MS);
            animator.setInterpolator(BakedBezierInterpolator.TRANSFORM_CURVE);
            animators.add(animator);
        }
    }

    /**
     * Add the favicon focus animator to a list of animators
     * @param animators The list of animators to which this animation should be added.
     * @param toolbarButtonTranslationX The amount by which the favicon should move while animating.
     */
    public void addUrlFocusAnimator(List<Animator> animators, float toolbarButtonTranslationX) {
        if (mFaviconView != null) {
            Animator animator = ObjectAnimator.ofFloat(
                    mFaviconView, View.TRANSLATION_X, toolbarButtonTranslationX);
            animator.setDuration(URL_FOCUS_TOOLBAR_BUTTONS_DURATION_MS);
            animator.setInterpolator(BakedBezierInterpolator.FADE_OUT_CURVE);
            animators.add(animator);

            animator = ObjectAnimator.ofFloat(mFaviconView, View.ALPHA, 0);
            animator.setDuration(URL_FOCUS_TOOLBAR_BUTTONS_DURATION_MS);
            animator.setInterpolator(BakedBezierInterpolator.FADE_OUT_CURVE);
            animators.add(animator);
        }
    }

    private boolean tabHasPermissions() {
        if (mTab == null || TextUtils.isEmpty(mTab.getUrl())) return false;
        URI parsedUrl = null;
        boolean isInternalPage = false;
        try {
            parsedUrl = new URI(mTab.getUrl());
            isInternalPage = UrlUtilities.isInternalScheme(parsedUrl);
        } catch (URISyntaxException e) {
        }
        return !isNativePage() && !mTab.isShowingInterstitialPage()
                && !mTab.isShowingSadTab() && !isInternalPage;
    }

    private void updateSearchEngineList(Tab tab) {
        TemplateUrlService templateUrlService = TemplateUrlService.getInstance();
        List<TemplateUrlService.TemplateUrl> searchEngines =
                templateUrlService.getLocalizedSearchEngines();
        mSearchEngineNames = new String[searchEngines.size()];
        mSearchEngineIndices = new int[searchEngines.size()];

        FaviconHelper faviconHelper = new FaviconHelper();
        for (int i = 0; i < searchEngines.size(); ++i) {
            int index = searchEngines.get(i).getIndex();
            String url = templateUrlService.getSearchEngineFavicon(index);
            mSearchEngineNames[i] = searchEngines.get(i).getShortName();
            mSearchEngineIndices[i] = index;

            faviconHelper.ensureIconIsAvailable(Profile.getLastUsedProfile(),
                    tab.getWebContents(), url, url, false, false,
                    new FaviconHelper.IconAvailabilityCallback() {

                        @Override
                        public void onIconAvailabilityChecked(boolean newlyAvailable) {
                            if (newlyAvailable) {
                                if (mFavicon == null || mFakeIconGenerated) refreshFavicon();
                            }
                        }
                    }
            );
        }
    }

    private void ensureSearchEngineFaviconAvailability(Tab tab) {
        if (tab == null || TemplateUrlService.getInstance() == null) return;

        if (mTemplateUrlObserver == null) {
            mTemplateUrlObserver = new TemplateUrlService.TemplateUrlServiceObserver() {
                @Override
                public void onTemplateURLServiceChanged() {
                    updateSearchEngine();
                }
            };

            TemplateUrlService.getInstance().addObserver(mTemplateUrlObserver);
        }

        if (mTemplateUrlLoadListener == null) {
            final Tab localTab = tab;
            mTemplateUrlLoadListener = new TemplateUrlService.LoadListener() {
                @Override
                public void onTemplateUrlServiceLoaded() {
                    updateSearchEngineList(localTab);
                }
            };

            TemplateUrlService.getInstance().registerLoadListener(mTemplateUrlLoadListener);
            updateSearchEngineList(localTab);
        }
    }

    private void tooltipAcknowledged(String pref) {
        synchronized (this) {
            if (mTooltip != null) {
                mTooltip.dismiss();
                mTooltip = null;
            }
        }

        if (TextUtils.isEmpty(pref)) {
            return;
        }

        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(mContext);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(pref, mTooltipMaxShowCount);
        editor.apply();
    }

    private void showToolTip(int textResId, int precedence, final String pref) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        if (!TextUtils.isEmpty(pref)) {
            if (prefs.getInt(pref, 0) >= mTooltipMaxShowCount) {
                return;
            }
        }

        synchronized (this) {
            if (mTooltip != null) {
                if (mTooltip.getCookie() >= precedence ||
                        (!TextUtils.isEmpty(mTooltipPref) && mTooltipPref.equals(pref))) {
                    return;
                }

                mTooltip.dismiss();
            }

            mTooltipPref = pref;

            mTooltip = new BrowserTooltip(mContext, mContext.getString(textResId), R.color.accent,
                    R.color.default_primary_color, precedence);

            mTooltip.setTouchListener(
                    new View.OnTouchListener() {
                        @Override
                        public boolean onTouch(View v, MotionEvent event) {
                            v.performClick();
                            tooltipAcknowledged(pref);
                            return true;
                        }
                    });

            mTooltip.show(mTooltipTimeoutMS, mFaviconView, new BrowserTooltip.TooltipTimeout() {
                @Override
                public void onTimeout() {
                    synchronized (ToolbarFavicon.this) {
                        mTooltip = null;
                        if (!TextUtils.isEmpty(pref)) {
                            SharedPreferences.Editor editor = prefs.edit();
                            editor.putInt(pref, prefs.getInt(pref, 0) + 1);
                            editor.apply();
                        }
                    }
                }
            });
        }
    }

    public void refreshTab(Tab tab) {
        if (mFaviconView == null || tab == mTab) return;

        if (mTab != null) {
            mTab.removeObserver(mTabObserver);
        }
        mTab = tab;

        if (mTab != null) {
            mTab.addObserver(mTabObserver);
        }

        mBlockedCountSet = false;
        mFaviconView.setBadgeBlockedObjectsCount(0); //Clear the count
        refreshPageUpgrade(); //Check the upgrade state

        refreshFavicon();
        refreshTabSecurityState();
        refreshBlockedCount();
        ensureSearchEngineFaviconAvailability(tab);
    }

    private void refreshBlockedCount() {
        if (mTab == null ||
                mTab.getContentViewCore() == null) return ;
        int count = WebRefinerPreferenceHandler.getBlockedURLCount(
                mTab.getContentViewCore());
        WebDefenderPreferenceHandler.StatusParcel statusParcel =
                WebDefenderPreferenceHandler.getStatus(mTab.getContentViewCore());
        if (statusParcel != null ) {
            WebDefender.ProtectionStatus protectionStatus = statusParcel.getStatus();
            if (protectionStatus != null && protectionStatus.mTrackingProtectionEnabled
                    && protectionStatus.mTrackerDomains != null
                    && protectionStatus.mTrackerDomains.length > 0) {
                for (WebDefender.TrackerDomain trackerDomain : protectionStatus.mTrackerDomains) {
                    if (trackerDomain.mProtectiveAction ==
                            WebDefender.TrackerDomain.PROTECTIVE_ACTION_BLOCK_URL ||
                            trackerDomain.mProtectiveAction ==
                                    WebDefender.TrackerDomain.PROTECTIVE_ACTION_BLOCK_COOKIES) {
                        count++;
                    }
                }
            }
        }

        if (mFaviconView != null) {
            mFaviconView.setBadgeBlockedObjectsCount(count);
            if (count > 0) {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
                int tabLoadCount = prefs.getInt(PREF_FAVICON_TAB_LOAD_COUNT, 0);
                if (count > 50) {
                    if (tabLoadCount > mContext.getResources().getInteger(
                            R.integer.tooltip_site_integrity_check_wait)) {
                        showToolTip(R.string.tooltip_favicon_site_integrity_check,
                                TooltipPrecedence.SITE_INTEGRITY.ordinal(),
                                PREF_FAVICON_CLICK_FOR_SITE_INTEGRITY);
                    }
                } if (count > 10) {
                    if (tabLoadCount > mContext.getResources().getInteger(
                            R.integer.tooltip_site_security_info_wait)) {
                        showToolTip(R.string.tooltip_favicon_content_blocking_info,
                                TooltipPrecedence.SECURITY_INFO.ordinal(),
                                PREF_FAVICON_CLICK_FOR_SECURITY_INFO);
                    }
                }
            }
        }
        if (count > 0)
            mBlockedCountSet = true;
    }

    public final int getMeasuredWidth() {
        mbSiteSettingsVisible = false;
        return (mFaviconView != null) ? mFaviconView.getMeasuredWidth() : 0;
    }

    public final View getView() {
        return mFaviconView;
    }

    public void updateSearchEngine() {
        if (mTab != null && isNativePage()) {
            TemplateUrlService templateUrlService = TemplateUrlService.getInstance();
            TemplateUrlService.TemplateUrl mSearchEngine =
                    templateUrlService.getDefaultSearchEngineTemplateUrl();

            if (mSearchEngine == null) return;

            int index = mSearchEngine.getIndex();
            String favicon_url = templateUrlService.getSearchEngineFavicon(index);
            mDefaultSearchEngineIndex = index;

            NativePage page = mTab.getNativePage();
            if (page instanceof NewTabPage || page instanceof IncognitoNewTabPage) {
                LargeIconForTab callback = new LargeIconForTab(mTab);

                if (mLargeIconBridge == null)
                    mLargeIconBridge = new LargeIconBridge(Profile.getLastUsedProfile());

                mLargeIconBridge.getLargeIconForUrl(
                        favicon_url, SEARCHENGINE_FAVICON_MIN_SIZE_PX, callback);
            }
        }
    }

    public void refreshFavicon() {

        if (mTab == null) {
            if (mFaviconView != null)
                mFaviconView.setVisibility(View.GONE);
            mFavicon = null;
            return;
        }

        if (isNativePage()) {
            NativePage page = mTab.getNativePage();
            if (page instanceof NewTabPage) {
                if (mFaviconView != null) {
                    mFaviconView.setBadgeBlockedObjectsCount(0);
                    mFaviconView.setBadgeHasCertIssues(false);
                    mFaviconView.setTrustLevel(SiteTileView.TRUST_UNKNOWN);
                    updateSearchEngine();
                    refreshPageUpgrade();
                }
            } else {
                if (mFaviconView != null)
                    mFaviconView.setVisibility(View.GONE);
                mFavicon = null;
                setStatusBarColor(Color.BLACK);
            }
        } else {
            if (mLargeIconBridge == null)
                mLargeIconBridge = new LargeIconBridge(Profile.getLastUsedProfile());

            LargeIconForTab callback = new LargeIconForTab(mTab);
            String url = mTab.getUrl();
            mLargeIconBridge.getLargeIconForUrl(url, FAVICON_MIN_SIZE_PX, callback);
        }
    }

    @VisibleForTesting
    public boolean isShowingSiteSettings() {
        return mbSiteSettingsVisible;
    }

    private void refreshTabSecurityState() {
        if (mFaviconView != null && mTab != null) {
            int level = mTab.getSecurityLevel();
            switch (level) {
                case ConnectionSecurityLevel.NONE:
                    mFaviconView.setTrustLevel(SiteTileView.TRUST_UNKNOWN);
                    mFaviconView.setBadgeHasCertIssues(false);
                    break;
                case ConnectionSecurityLevel.SECURITY_WARNING:
                    mFaviconView.setTrustLevel(SiteTileView.TRUST_UNTRUSTED);
                    mFaviconView.setBadgeHasCertIssues(true);
                    break;
                case ConnectionSecurityLevel.SECURITY_ERROR:
                    mFaviconView.setTrustLevel(SiteTileView.TRUST_AVOID);
                    mFaviconView.setBadgeHasCertIssues(true);
                    break;
                case ConnectionSecurityLevel.SECURE:
                case ConnectionSecurityLevel.EV_SECURE:
                    mFaviconView.setTrustLevel(SiteTileView.TRUST_TRUSTED);
                    mFaviconView.setBadgeHasCertIssues(false);
                    break;
                default:
                    break;
            }

            if (mTab.isOfflinePage()) {
                mFaviconView.setBadgeOverride(true, R.drawable.ic_deco_tile_saved_page_accent);
            } else {
                mFaviconView.setBadgeOverride(false, 0);
            }
        }
    }

    private void setStatusBarColor(int color) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            final Activity activity = ApplicationStatus.getLastTrackedFocusedActivity();
            if (!(activity instanceof ChromeActivity)) {
                return;
            }

            if (((ChromeActivity) activity).isInOverviewMode()) return;

            mStatusBarColor = activity.getWindow().getStatusBarColor();
            synchronized (this) {
                if (mAnimator != null && mAnimator.isRunning()) {
                    mAnimator.cancel();
                    mAnimator = null;
                }

                Integer to = ColorUtils.getDarkenedColorForStatusBar(color);
                if (mStatusBarColor.intValue() == to.intValue()) {
                    ApiCompatibilityUtils.setStatusBarColor(activity.getWindow(), to.intValue());
                    return;
                }

                mAnimator = ValueAnimator.ofObject(new ArgbEvaluator(), mStatusBarColor, to);
                mAnimator.addUpdateListener(
                        new ValueAnimator.AnimatorUpdateListener() {
                            @SuppressLint("NewApi")
                            @Override
                            public void onAnimationUpdate(ValueAnimator animation) {
                                synchronized (ToolbarFavicon.this) {
                                    if (((ChromeActivity) activity).isInOverviewMode()) {
                                        mAnimator.cancel();
                                        return;
                                    }
                                    Integer value = (Integer) animation.getAnimatedValue();
                                    ApiCompatibilityUtils.setStatusBarColor(activity.getWindow(),
                                            value.intValue());
                                    mStatusBarColor = value;
                                }
                            }
                        }
                );
                mAnimator.start();
            }
        }
    }

    private void showCurrentSiteSettings() {
        String url = mTab.getUrl();
        Context context = ContextUtils.getApplicationContext();

        Bitmap favicon = mFavicon != null ? mFavicon : mTab.getFavicon();
        Bundle fragmentArguments = BrowserSingleWebsitePreferences.createFragmentArgsForSite(url,
                favicon,
                mTab);
        Intent preferencesIntent = PreferencesLauncher.createIntentForSettingsPage(
                context, BrowserSingleWebsitePreferences.class.getName());
        preferencesIntent.putExtra(
                Preferences.EXTRA_SHOW_FRAGMENT_ARGUMENTS, fragmentArguments);
        context.startActivity(preferencesIntent);
    }

    public void setVisibility(int browsingModeVisibility) {
        mBrowsingModeViewsHidden = browsingModeVisibility == View.INVISIBLE;

        if (mFavicon != null) {
            mFaviconView.setVisibility(browsingModeVisibility);
        }
    }

    class LargeIconForTab implements LargeIconBridge.LargeIconCallback {
        // Tab that made the request
        private Tab mClientTab;

        public LargeIconForTab(Tab tab) {
            mClientTab = tab;
        }

        @Override
        public void onLargeIconAvailable(Bitmap icon, int fallbackColor) {
            if (mClientTab == null || mTab == null || mTab != mClientTab) return;
            mFakeIconGenerated = false;

            String url = mTab.isOfflinePage() ? mTab.getOriginalUrl() : mTab.getUrl();

            NativePage page = mTab.getNativePage();
            if (page instanceof NewTabPage &&
                    TemplateUrlService.getInstance().isDefaultSearchEngineGoogle()) {
                fallbackColor = OVERRIDE_SEARCHENGINE_COLOR;
            } else if (icon != null) {
                fallbackColor = getFallbackColor(url, icon, icon.getHeight());
            } else {
                icon = mClientTab.getToolbarFavicon();
                if (icon != null) {
                    int size = mClientTab.getToolbarFaviconOriginalSize();
                    fallbackColor = getFallbackColor(url, icon, size);
                } else {
                    if (mUsingBrandColor) {
                        fallbackColor = ColorUtils.computeActionBarColor(mTab.getThemeColor());
                    } else {
                        Bitmap favicon = mClientTab.getFaviconNoDefault();
                        if (favicon != null) {
                            fallbackColor = getFallbackColor(url, favicon, 0);
                        }
                    }

                    RoundedIconGenerator roundedIconGenerator = new RoundedIconGenerator(
                            mContext, FAVICON_MIN_SIZE_PX, FAVICON_MIN_SIZE_PX,
                            FAVICON_CORNER_RADIUS, fallbackColor,
                            FAVICON_TEXT_SIZE);
                    icon = roundedIconGenerator.generateIconForUrl(url);
                    mFakeIconGenerated = true;
                }
            }

            if (!mUsingBrandColor) setStatusBarColor(fallbackColor);

            if (mFaviconView != null) {
                setFavicon(icon);
                if (mBrowsingModeViewsHidden)
                    return;

                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);

                if (mTab.isNativePage() && mTab.getNativePage() instanceof NewTabPage
                        && !mFakeIconGenerated) {
                    if (prefs.getInt(PREF_FAVICON_NEWTAB_LOAD_COUNT, 0) >
                            mContext.getResources().getInteger(
                                    R.integer.tooltip_ntp_search_engine_wait)) {
                        showToolTip(R.string.tooltip_favicon_change_search_engine,
                                TooltipPrecedence.SEARCH_ENGINE.ordinal(),
                                PREF_FAVICON_CLICK_FOR_SEARCH_ENGINE);
                    }
                } else if (!mTab.isOfflinePage() && !mTab.isNativePage()) {
                    if (prefs.getInt(PREF_FAVICON_TAB_LOAD_COUNT, 0) >
                            mContext.getResources().getInteger(
                                    R.integer.tooltip_site_settings_wait)) {
                        showToolTip(R.string.tooltip_favicon_change_site_settings,
                                TooltipPrecedence.SITE_SETTINGS.ordinal(),
                                PREF_FAVICON_CLICK_FOR_SITE_SETTINGS);
                    }
                }
            }
        }
    }

    private void setFavicon(Bitmap icon) {
        mFavicon = icon;
        mFaviconView.replaceFavicon(icon);
        mFaviconView.setVisibility(mBrowsingModeViewsHidden ?
                View.GONE : View.VISIBLE);
    }

    private synchronized int getFallbackColor(String url, Bitmap icon, int size) {
        HashMap<Integer, Integer> faviconDominantColors;
        int fallbackColor;

        if (mFaviconDominantColorsForUrls.containsKey((url))) {
            faviconDominantColors = mFaviconDominantColorsForUrls.get(url);
            if (faviconDominantColors.containsKey(size)) {
                fallbackColor = faviconDominantColors.get(size);
            } else {
                fallbackColor = FaviconHelper.getDominantColorForBitmap(icon);
                faviconDominantColors.put(size, fallbackColor);
            }
        } else {
            faviconDominantColors = new HashMap<>();
            fallbackColor = FaviconHelper.getDominantColorForBitmap(icon);
            faviconDominantColors.put(size, fallbackColor);
            mFaviconDominantColorsForUrls.put(url, faviconDominantColors);
        }

        return fallbackColor;
    }
}

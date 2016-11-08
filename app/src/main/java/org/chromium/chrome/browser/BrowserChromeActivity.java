/*
 * Copyright (c) 2016, The Linux Foundation. All rights reserved.
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

package org.chromium.chrome.browser;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.text.TextUtils;
import android.view.Menu;
import android.view.WindowManager;
import android.support.v4.content.LocalBroadcastManager;

import org.chromium.base.CommandLine;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.incognito.IncognitoOnlyModeUtil;
import org.chromium.chrome.browser.infobar.DownloadInfoBar;
import org.chromium.chrome.browser.init.AsyncInitializationActivity;
import org.chromium.chrome.browser.preferences.AboutChromePreferences;
import org.chromium.chrome.browser.preferences.Preferences;
import org.chromium.chrome.browser.preferences.BrowserHomepagePreferences;
import org.chromium.chrome.browser.preferences.PreferencesLauncher;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.preferences.SecureConnectPreferenceHandler;
import org.chromium.chrome.browser.preferences.privacy.PrivacyPreferencesManager;
import org.chromium.chrome.browser.preferences.privacy.SafeBrowsingPromoScreen;
import org.chromium.chrome.browser.preferences.privacy.SecureConnectPromoScreen;
import org.chromium.chrome.browser.preferences.privacy.SecurityUpdatesPromoScreen;
import org.chromium.chrome.browser.preferences.website.SmartProtectDetailsPreferences;
import org.chromium.chrome.browser.preferences.website.WebDefenderPreferenceHandler;
import org.chromium.chrome.browser.preferences.website.WebRefinerPreferenceHandler;
import org.chromium.chrome.browser.preferences.website.WebsiteAddress;
import org.chromium.chrome.browser.snackbar.Snackbar;
import org.chromium.chrome.browser.snackbar.SnackbarManager;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.EmptyTabModel;
import org.chromium.chrome.browser.tabmodel.EmptyTabModelObserver;
import org.chromium.chrome.browser.tabmodel.EmptyTabModelSelectorObserver;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.tabmodel.TabModelUtils;
import org.chromium.chrome.browser.toolbar.ToolbarFavicon;
import org.chromium.chrome.browser.toolbar.ToolbarManager;
import org.chromium.chrome.browser.util.FeatureUtilities;
import org.chromium.components.url_formatter.UrlFormatter;

import org.chromium.content.browser.SecureConnect;
import org.chromium.content_public.browser.LoadUrlParams;

import org.chromium.net.NetError;

import org.codeaurora.swe.SWEBrowserSwitches;
import org.codeaurora.swe.SWECommandLine;
import org.codeaurora.swe.DownloadInfoBarContainer;

import java.net.URI;
import java.util.List;
import java.util.Set;

public abstract class BrowserChromeActivity extends AsyncInitializationActivity {
    private TabModelSelector mTabModelSelector;
    private EmptyTabModelObserver mBrowserTabModelObserver;
    private PowerConnectionReceiver mPowerChangeReceiver;
    private PowerConnectionReceiver mLowPowerReceiver;
    private BroadcastReceiver mDirSelectIntentReceiver;
    private SnackbarManager mBrowserSnackbarManager;
    private Uri mCurrentDownloadInfoBarDataUri;
    private static final int PREFERENCE_REQUEST = 1;
    private static final int DOWNLOADPATH_SELECTION = 0;

    private boolean mSecureConnectObserverRegistered = false;

    /**
     * Sets the {@link TabModelSelector} owned by this {@link ChromeActivity}.
     * @param tabModelSelector A {@link TabModelSelector} instance.
     */
    protected void setTabModelSelector(TabModelSelector tabModelSelector) {
        mTabModelSelector = tabModelSelector;
        if (!FeatureUtilities.isDocumentMode(this)) {
            mBrowserTabModelObserver = new EmptyTabModelObserver() {
                @Override
                public void didCloseTab(int tabId, boolean incognito) {
                    if (!incognito) return;
                    boolean incognitoSessionEnded = true;
                    for (TabModel tabModel : mTabModelSelector.getModels()) {

                        if (tabModel.isIncognito() && tabModel.getCount() != 0) {
                            incognitoSessionEnded = false;
                        }
                    }
                    if (incognitoSessionEnded) {
                        WebRefinerPreferenceHandler.onIncognitoSessionFinish();
                        WebDefenderPreferenceHandler.onIncognitoSessionFinish();
                        SecureConnectPreferenceHandler.onIncognitoSessionFinish();
                    }
                }
            };

            for (TabModel tabModel : mTabModelSelector.getModels()) {
                if (tabModel.isIncognito()) {
                    tabModel.removeObserver(mBrowserTabModelObserver);
                    tabModel.addObserver(mBrowserTabModelObserver);
                }
            }
            tabModelSelector.addObserver(new EmptyTabModelSelectorObserver() {
                @Override
                public void onTabModelSelected(TabModel newModel, TabModel oldModel) {
                    if (newModel.isIncognito()) {
                        newModel.removeObserver(mBrowserTabModelObserver);
                        newModel.addObserver(mBrowserTabModelObserver);
                    }
                }
            });
        }
    }

    protected void setupPromoPages() { //Only show one of our promos at a time.
        if (SafeBrowsingPromoScreen.shouldShowPromo(this)) {
            SafeBrowsingPromoScreen.launchSafeBrowsingPromo(this);
        } else if (SecureConnectPromoScreen.shouldShowPromo(this)) {
            SecureConnectPromoScreen.launchSecureConnectPromo(this);
        } else if (SecurityUpdatesPromoScreen.shouldShowPromo(this)) {
            SecurityUpdatesPromoScreen.launchSecurityUpdatesPromo(this);
        }
    }

    protected ToolbarManager getToolbarManager() {
        return null;
    }

    @Override
    public void onStart() {
        super.onStart();
        mBrowserSnackbarManager.onStart();
        UpdateNotificationService.updateCheck(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        mBrowserSnackbarManager.onStop();
    }

    @Override
    public void onResumeWithNative() {
        super.onResumeWithNative();

        ToolbarManager toolbarManager = getToolbarManager();
        if (toolbarManager != null) {
            toolbarManager.onActivityResume();
        }

        if (!mSecureConnectObserverRegistered) {
            SecureConnectPreferenceHandler.addObserver(new SecureConnect.Listener() {
                @Override
                public void onPageUpgrade(String url, Boolean mainFrame) {
                }

                @Override
                public void onUpgradeFailed(String origin, String url, String redirect, int error) {
                    if (error == NetError.ERR_BLOCKED_BY_CLIENT) {
                        if (!mBrowserSnackbarManager.isShowing()) maybeShowSecureContentSnackbar();
                        return;
                    }

                    SecureConnectPreferenceHandler.
                            setSecureConnectSettingForOrigin(origin, false, false);
                    loadTabsIfNecessary(redirect, url);
                }
            });

            mSecureConnectObserverRegistered = true;
        }

        purgeTemporaryPrefs();
        reloadTabsIfNecessary();

        this.registerReceiver(mLowPowerReceiver, new IntentFilter(Intent.ACTION_BATTERY_LOW));
        registerDownloadPathSelectionReceiver();
    }

    /**
     * Deletes the shared preferences used by the Smart Protect details screen.
     */
    private void purgeTemporaryPrefs() {
        SharedPreferences sharedPreferences = getApplicationContext()
                .getSharedPreferences(SmartProtectDetailsPreferences.SMART_PROTECT_DETAILS_PREF,
                        Context.MODE_PRIVATE);
        sharedPreferences.edit().clear().apply();
    }

    protected void maybeShowSecureContentSnackbar() {
        if (SecureConnectPreferenceHandler.getSecureContentOnlyEnabled()
                && mBrowserSnackbarManager != null) {
            Snackbar toShow = Snackbar.make(
                    getResources().getString(R.string.secure_connect_secure_content_snackbar),
                    new SnackbarManager.SnackbarController() {
                        @Override
                        public void onAction(Object actionData) {
                            SecureConnectPreferenceHandler.setSecureContentOnlyMode(false);
                            mTabModelSelector.getCurrentTab().reload();
                        }

                        @Override
                        public void onDismissNoAction(Object actionData) {

                        }
                    }
                    , Snackbar.TYPE_NOTIFICATION, Snackbar.UMA_UNKNOWN);
            toShow.setAction(getResources().getString(
                    R.string.secure_connect_secure_content_snackbar_action), null);
            toShow.setSingleLine(false);
            mBrowserSnackbarManager.showSnackbar(toShow);
        }
    }


    @Override
    public void onPauseWithNative() {
        unregisterDownloadPathSelectionReceiver();
        this.unregisterReceiver(mLowPowerReceiver);
        super.onPauseWithNative();
    }

    @SuppressLint("NewApi")
    @Override
    protected void onDestroy() {
        unregisterDownloadPathSelectionReceiver();
        this.unregisterReceiver(mPowerChangeReceiver);
        super.onDestroy();
    }

    @Override
    public void finish() {
        unregisterDownloadPathSelectionReceiver();
        super.finish();
    }

    @SuppressLint("NewApi")
    @Override
    public void postInflationStartup() {
        super.postInflationStartup();

        mPowerChangeReceiver = new PowerConnectionReceiver();
        mLowPowerReceiver = new PowerConnectionReceiver();
        mBrowserSnackbarManager = new SnackbarManager(this);

        IntentFilter filter = new IntentFilter();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Power save mode only exists in Lollipop and above
            filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
        }
        filter.addAction(Intent.ACTION_BATTERY_OKAY);
        this.registerReceiver(mPowerChangeReceiver, filter);

        if (PrivacyPreferencesManager.getInstance().isBlockScreenObserversEnabled()) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PREFERENCE_REQUEST && resultCode == RESULT_OK) {
            if (data.getExtras().containsKey("Secure")){
                if (data.getBooleanExtra("Secure", false)){
                    getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                            WindowManager.LayoutParams.FLAG_SECURE);
                }
                else {
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
                }
            }
        } else if (requestCode == DOWNLOADPATH_SELECTION &&
                resultCode == RESULT_OK) {
            handleDownloadPathSelectionRequestCode(data);
        }
    }

    public void prepareMenu(Menu menu) {
        if (IncognitoOnlyModeUtil.getInstance().isIncognitoOnlyModeEnabled()) {
            menu.findItem(R.id.new_incognito_tab_menu_id).setVisible(false);
        }
    }

    private void loadTabsIfNecessary(String currentURL, String newURL) {
        List<TabModel> tabModels;
        if (FeatureUtilities.isDocumentMode(this)) {
            tabModels = ChromeApplication.getDocumentTabModelSelector().getModels();
        } else {
            tabModels = mTabModelSelector.getModels();
        }

        for (TabModel model : tabModels) {
            if (model == null) continue;
            int tabCount = model.getCount();
            for (int tabCounter = 0; tabCounter < tabCount; tabCounter++) {
                Tab tab = model.getTabAt(tabCounter);
                if (tab == null || TextUtils.isEmpty(tab.getUrl())) continue;
                if (tab.getUrl().equals(currentURL) || tab.getUrl().equals(newURL)) {
                    tab.loadUrl(new LoadUrlParams(newURL));
                }
            }
        }
    }

    private void reloadTabsIfNecessary() {
        Set<String> origins = PrefServiceBridge.getInstance().getOriginsPendingReload();
        boolean reload = PrefServiceBridge.getInstance().getPendingReload();
        List<TabModel> tabModels;
        if (!reload && origins.isEmpty()) {
            return;
        }
        if (FeatureUtilities.isDocumentMode(this)) {
            tabModels = ChromeApplication.getDocumentTabModelSelector().getModels();
        } else {
            tabModels = mTabModelSelector.getModels();
        }

        for (TabModel model : tabModels) {
            if (model == null) continue;
            int tabCount = model.getCount();
            for (int tabCounter = 0; tabCounter < tabCount; tabCounter++) {
                Tab tab = model.getTabAt(tabCounter);
                if (tab == null || TextUtils.isEmpty(tab.getUrl())) continue;
                if (reload) {
                    if (tab == getActivityTab()) {
                        SecureConnectPreferenceHandler.reloadTabIfNeeded(tab);
                    } else {
                        tab.setNeedsReload(true);
                    }
                } else {
                    for (String url : origins) {
                        WebsiteAddress address = WebsiteAddress.create(tab.getUrl());
                        if (TextUtils.equals(url, address != null ? address.getOrigin() : null)
                                || TextUtils.equals(url, UrlFormatter
                                .formatUrlForSecurityDisplay(tab.getUrl(), true))) {
                            if (tab == mTabModelSelector.getCurrentTab()) {
                                SecureConnectPreferenceHandler.reloadTabIfNeeded(tab);
                            } else {
                                tab.setNeedsReload(true);
                            }
                        }
                    }
                }
            }
        }
        PrefServiceBridge.getInstance().reloadComplete();
    }

    /**
     * Returns the tab being displayed by this ChromeActivity instance. This allows differentiation
     * between ChromeActivity subclasses that swap between multiple tabs (e.g. ChromeTabbedActivity)
     * and subclasses that only display one Tab (e.g. FullScreenActivity and DocumentActivity).
     *
     * The default implementation grabs the tab currently selected by the TabModel, which may be
     * null if the Tab does not exist or the system is not initialized.
     */
    private Tab getActivityTab() {
        return TabModelUtils.getCurrentTab(getCurrentTabModel());
    }

    /**
     * Gets the current (inner) TabModel.  This is a convenience function for
     * getModelSelector().getCurrentModel().  It is *not* equivalent to the former getModel()
     * @return Never null, if modelSelector or its field is uninstantiated returns a
     *         {@link EmptyTabModel} singleton
     */
    private TabModel getCurrentTabModel() {
        if (mTabModelSelector == null) return EmptyTabModel.getInstance();
        return mTabModelSelector.getCurrentModel();
    }

    public boolean onMenuOrKeyboardAction(int id, boolean fromMenu) {
        if (id == R.id.preferences_id) {
            Intent intent = PreferencesLauncher.createIntentForSettingsPage(this, null);
            this.startActivityForResult(intent, PREFERENCE_REQUEST);
            RecordUserAction.record("MobileMenuSettings");
            return true;
        }

        final Tab currentTab = getActivityTab();
        if (currentTab == null) {
            return false;
        } else if (id == R.id.info_menu_id) {
            if (currentTab.isOfflinePage()) {
                ToolbarFavicon.showOfflinePageDialog(this, currentTab);
                return true;
            }
        } else if (id == R.id.about_id) {
            Intent preferencesIntent = PreferencesLauncher.createIntentForSettingsPage(
                    this, AboutChromePreferences.class.getName());
            Bundle bundle = new Bundle();
            bundle.putCharSequence(AboutChromePreferences.TABTITLE, getActivityTab().getTitle());
            bundle.putCharSequence(AboutChromePreferences.TABURL, getActivityTab().getUrl());
            preferencesIntent.putExtra(AboutChromePreferences.TABBUNDLE, bundle);
            this.startActivity(preferencesIntent, bundle);
            RecordUserAction.record("MobileMenuAbout");
            return true;
        } else if (id == R.id.preferences_id) {
            Intent intent = PreferencesLauncher.createIntentForSettingsPage(this, null);
            if (currentTab != null && !currentTab.isIncognito() && !currentTab.isNativePage()
                    && !currentTab.isShowingInterstitialPage() && !currentTab.isShowingSadTab()) {
                Bundle args = new Bundle();
                args.putString(BrowserHomepagePreferences.CURRENT_URL, getActivityTab().getUrl());
                intent.putExtra(Preferences.EXTRA_SHOW_FRAGMENT_ARGUMENTS, args);
            }
            this.startActivityForResult(intent, PREFERENCE_REQUEST);
            RecordUserAction.record("MobileMenuSettings");
            return true;
        }
        return false;
    }

    private void registerDownloadPathSelectionReceiver() {
        if (!CommandLine.getInstance().hasSwitch(
                    SWEBrowserSwitches.DOWNLOAD_PATH_SELECTION))
            return;

        if (null != mDirSelectIntentReceiver)
            return;

        mDirSelectIntentReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Uri uri = intent.getData();
                if (null == uri)
                    return;

                Intent selectDirIntent =
                    DownloadInfoBarContainer.getInstance().getSelectDirIntent(
                            uri);
                if (null == selectDirIntent)
                    return;

                mCurrentDownloadInfoBarDataUri = uri;
                BrowserChromeActivity.this.startActivityForResult(
                        selectDirIntent,
                        DOWNLOADPATH_SELECTION);
            }
        };

        try {
            LocalBroadcastManager.getInstance(this).registerReceiver(
                    mDirSelectIntentReceiver,
                    new IntentFilter(DownloadInfoBarContainer.RECEIVER_NAME, "*/*"));
        } catch (IntentFilter.MalformedMimeTypeException e) {
            e.printStackTrace();
        }
    }

    private void unregisterDownloadPathSelectionReceiver() {
        if (null != mDirSelectIntentReceiver) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(
                    mDirSelectIntentReceiver);
            mDirSelectIntentReceiver = null;
        }
    }

    private void handleDownloadPathSelectionRequestCode(Intent data) {
        if (null == data || null == mCurrentDownloadInfoBarDataUri)
            return;

        String selectDirKey = SWECommandLine.getResourceString(
                getApplicationContext(),
                SWECommandLine.kSWEDownloadPathActivityResultSelection);
        if (TextUtils.isEmpty(selectDirKey))
            return;

        String selectedDir = data.getStringExtra(selectDirKey);
        if (TextUtils.isEmpty(selectedDir))
            return;

        DownloadInfoBarContainer container =
            DownloadInfoBarContainer.getInstance();
        if (null == container)
            return;

        DownloadInfoBar infoBar =
            container.getInstance().getDownloadInfoBar(
                mCurrentDownloadInfoBarDataUri);
        if (null == infoBar)
            return;

        infoBar.setDirFullPath(selectedDir, true);
        mCurrentDownloadInfoBarDataUri = null;
    }
}

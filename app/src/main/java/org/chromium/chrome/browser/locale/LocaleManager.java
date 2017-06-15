// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.locale;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;

import org.chromium.base.Callback;
import org.chromium.base.ContextUtils;
import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.AppHooks;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.preferences.PreferencesLauncher;
import org.chromium.chrome.browser.preferences.SearchEnginePreference;
import org.chromium.chrome.browser.search_engines.TemplateUrlService;
import org.chromium.chrome.browser.search_engines.TemplateUrlService.TemplateUrl;
import org.chromium.chrome.browser.snackbar.Snackbar;
import org.chromium.chrome.browser.snackbar.SnackbarManager;
import org.chromium.chrome.browser.snackbar.SnackbarManager.SnackbarController;
import org.chromium.ui.base.PageTransition;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;
import java.util.List;

/**
 * Manager for some locale specific logics.
 */
public class LocaleManager {
    public static final String PREF_AUTO_SWITCH = "LocaleManager_PREF_AUTO_SWITCH";
    public static final String PREF_PROMO_SHOWN = "LocaleManager_PREF_PROMO_SHOWN";
    public static final String PREF_WAS_IN_SPECIAL_LOCALE = "LocaleManager_WAS_IN_SPECIAL_LOCALE";
    public static final String SPECIAL_LOCALE_ID = "US";

    /** The current state regarding search engine promo dialogs. */
    @IntDef({SEARCH_ENGINE_PROMO_SHOULD_CHECK, SEARCH_ENGINE_PROMO_CHECKED_NOT_SHOWN,
            SEARCH_ENGINE_PROMO_CHECKED_AND_SHOWN})
    @Retention(RetentionPolicy.SOURCE)
    public @interface SearchEnginePromoState {}
    public static final int SEARCH_ENGINE_PROMO_SHOULD_CHECK = -1;
    public static final int SEARCH_ENGINE_PROMO_CHECKED_NOT_SHOWN = 0;
    public static final int SEARCH_ENGINE_PROMO_CHECKED_AND_SHOWN = 1;

    /** The different types of search engine promo dialogs. */
    @IntDef({SEARCH_ENGINE_PROMO_DONT_SHOW, SEARCH_ENGINE_PROMO_SHOW_SOGOU,
            SEARCH_ENGINE_PROMO_SHOW_EXISTING, SEARCH_ENGINE_PROMO_SHOW_NEW})
    @Retention(RetentionPolicy.SOURCE)
    public @interface SearchEnginePromoType {}

    public static final int SEARCH_ENGINE_PROMO_DONT_SHOW = -1;
    public static final int SEARCH_ENGINE_PROMO_SHOW_SOGOU = 0;
    public static final int SEARCH_ENGINE_PROMO_SHOW_EXISTING = 1;
    public static final int SEARCH_ENGINE_PROMO_SHOW_NEW = 2;

    protected static final String KEY_SEARCH_ENGINE_PROMO_SHOW_STATE =
            "com.android.chrome.SEARCH_ENGINE_PROMO_SHOWN";

    private static final int SNACKBAR_DURATION_MS = 6000;

    private static LocaleManager sInstance;

    private boolean mSearchEnginePromoShown;

    // LocaleManager is a singleton and it should not have strong reference to UI objects.
    // SnackbarManager is owned by ChromeActivity and is not null as long as the activity is alive.
    private WeakReference<SnackbarManager> mSnackbarManager;
    private SpecialLocaleHandler mLocaleHandler;

    private SnackbarController mSnackbarController = new SnackbarController() {
        @Override
        public void onDismissNoAction(Object actionData) { }

        @Override
        public void onAction(Object actionData) {
            Context context = ContextUtils.getApplicationContext();
            Intent intent = PreferencesLauncher.createIntentForSettingsPage(context,
                    SearchEnginePreference.class.getName());
            context.startActivity(intent);
        }
    };

    /**
     * @return An instance of the {@link LocaleManager}. This should only be called on UI thread.
     */
    @CalledByNative
    public static LocaleManager getInstance() {
        assert ThreadUtils.runningOnUiThread();
        if (sInstance == null) {
            sInstance = AppHooks.get().createLocaleManager();
        }
        return sInstance;
    }

    /**
     * Default constructor.
     */
    public LocaleManager() {
        int state = ContextUtils.getAppSharedPreferences().getInt(
                KEY_SEARCH_ENGINE_PROMO_SHOW_STATE, SEARCH_ENGINE_PROMO_SHOULD_CHECK);
        mSearchEnginePromoShown = state == SEARCH_ENGINE_PROMO_CHECKED_AND_SHOWN;
    }

    /**
     * Starts listening to state changes of the phone.
     */
    public void startObservingPhoneChanges() {
        maybeAutoSwitchSearchEngine();
    }

    /**
     * Stops listening to state changes of the phone.
     */
    public void stopObservingPhoneChanges() {}

    /**
     * Starts recording metrics in deferred startup.
     */
    public void recordStartupMetrics() {}

    /**
     * @return Whether the Chrome instance is running in a special locale.
     */
    public boolean isSpecialLocaleEnabled() {
        // If there is a kill switch sent from the server, disable the feature.
        if (!ChromeFeatureList.isEnabled("SpecialLocaleWrapper")) {
            return false;
        }
        boolean inSpecialLocale = ChromeFeatureList.isEnabled("SpecialLocale");
        inSpecialLocale = isReallyInSpecialLocale(inSpecialLocale);
        return inSpecialLocale;
    }

    /**
     * @return The country id of the special locale.
     */
    public String getSpecialLocaleId() {
        return SPECIAL_LOCALE_ID;
    }

    /**
     * Adds local search engines for special locale.
     */
    public void addSpecialSearchEngines() {
        if (!isSpecialLocaleEnabled()) return;
        getSpecialLocaleHandler().loadTemplateUrls();
    }

    /**
     * Removes local search engines for special locale.
     */
    public void removeSpecialSearchEngines() {
        if (isSpecialLocaleEnabled()) return;
        getSpecialLocaleHandler().removeTemplateUrls();
    }

    /**
     * Overrides the default search engine to a different search engine we designate. This is a
     * no-op if the user has manually changed DSP settings.
     */
    public void overrideDefaultSearchEngine() {
        if (!isSearchEngineAutoSwitchEnabled() || !isSpecialLocaleEnabled()) return;
        getSpecialLocaleHandler().overrideDefaultSearchProvider();
        showSnackbar(ContextUtils.getApplicationContext().getString(R.string.using_sogou));
    }

    /**
     * Reverts the temporary change made in {@link #overrideDefaultSearchEngine()}. This is a no-op
     * if the user has manually changed DSP settings.
     */
    public void revertDefaultSearchEngineOverride() {
        if (!isSearchEngineAutoSwitchEnabled() || isSpecialLocaleEnabled()) return;
        getSpecialLocaleHandler().setGoogleAsDefaultSearch();
        showSnackbar(ContextUtils.getApplicationContext().getString(R.string.using_google));
    }

    /**
     * Switches the default search engine based on the current locale, if the user has delegated
     * Chrome to do so. This method also adds some special engines to user's search engine list, as
     * long as the user is in this locale.
     */
    protected void maybeAutoSwitchSearchEngine() {
        SharedPreferences preferences = ContextUtils.getAppSharedPreferences();
        boolean wasInSpecialLocale = preferences.getBoolean(PREF_WAS_IN_SPECIAL_LOCALE, false);
        boolean isInSpecialLocale = isSpecialLocaleEnabled();
        if (wasInSpecialLocale && !isInSpecialLocale) {
            revertDefaultSearchEngineOverride();
            removeSpecialSearchEngines();
        } else if (isInSpecialLocale && !wasInSpecialLocale) {
            addSpecialSearchEngines();
            overrideDefaultSearchEngine();
        } else if (isInSpecialLocale) {
            // As long as the user is in the special locale, special engines should be in the list.
            addSpecialSearchEngines();
        }
        preferences.edit().putBoolean(PREF_WAS_IN_SPECIAL_LOCALE, isInSpecialLocale).apply();
    }

    /**
     * Shows a promotion dialog about search engines depending on Locale and other conditions.
     * See {@link LocaleManager#getSearchEnginePromoShowType()} for possible types and logic.
     *
     * @param context     Context showing the dialog.
     * @param onDismissed Notified when the dialog is dismissed and whether the user acted on it.
     * @return Whether such dialog is needed.
     */
    public boolean showSearchEnginePromoIfNeeded(
            Context context, @Nullable Callback<Boolean> onDismissed) {
        int shouldShow = getSearchEnginePromoShowType();
        switch (shouldShow) {
            case SEARCH_ENGINE_PROMO_DONT_SHOW:
                return false;
            case SEARCH_ENGINE_PROMO_SHOW_SOGOU:
                new SogouPromoDialog(context, this, onDismissed).show();
                return true;
            case SEARCH_ENGINE_PROMO_SHOW_EXISTING:
            case SEARCH_ENGINE_PROMO_SHOW_NEW:
                DefaultSearchEnginePromoDialog.show(context, shouldShow, onDismissed);
                return true;
            default:
                assert false;
                return false;
        }
    }

    /**
     * @return Whether auto switch for search engine is enabled.
     */
    public boolean isSearchEngineAutoSwitchEnabled() {
        return ContextUtils.getAppSharedPreferences().getBoolean(PREF_AUTO_SWITCH, false);
    }

    /**
     * Sets whether auto switch for search engine is enabled.
     */
    public void setSearchEngineAutoSwitch(boolean isEnabled) {
        ContextUtils.getAppSharedPreferences().edit().putBoolean(PREF_AUTO_SWITCH, isEnabled)
                .apply();
    }

    /**
     * Sets the {@link SnackbarManager} used by this instance.
     */
    public void setSnackbarManager(SnackbarManager manager) {
        mSnackbarManager = new WeakReference<SnackbarManager>(manager);
    }

    private void showSnackbar(CharSequence title) {
        SnackbarManager manager = mSnackbarManager.get();
        if (manager == null) return;

        Context context = ContextUtils.getApplicationContext();
        Snackbar snackbar = Snackbar.make(title, mSnackbarController, Snackbar.TYPE_NOTIFICATION,
                Snackbar.UMA_SPECIAL_LOCALE);
        snackbar.setDuration(SNACKBAR_DURATION_MS);
        snackbar.setAction(context.getString(R.string.preferences), null);
        manager.showSnackbar(snackbar);
    }

    /**
     * Does some extra checking about whether the user is in special locale.
     * @param inSpecialLocale Whether the variation service thinks the client is in special locale.
     * @return The result after extra confirmation.
     */
    protected boolean isReallyInSpecialLocale(boolean inSpecialLocale) {
        return inSpecialLocale;
    }

    /**
     * @return Whether and which search engine promo should be shown.
     */
    @SearchEnginePromoType
    public int getSearchEnginePromoShowType() {
        if (!isSpecialLocaleEnabled()) return SEARCH_ENGINE_PROMO_DONT_SHOW;
        SharedPreferences preferences = ContextUtils.getAppSharedPreferences();
        if (preferences.getBoolean(PREF_PROMO_SHOWN, false)) {
            return SEARCH_ENGINE_PROMO_DONT_SHOW;
        }
        return SEARCH_ENGINE_PROMO_SHOW_SOGOU;
    }

    /**
     * @return The referral ID to be passed when searching with Yandex as the DSE.
     */
    @CalledByNative
    protected String getYandexReferralId() {
        return "";
    }

    /**
     * @return The search engine type for the given url if applicable.
     *         See template_url_prepopulate_data.cc for all values.
     */
    protected static int getSearchEngineType(String url) {
        return nativeGetEngineType(url);
    }

    /**
     * To be called after the user has made a selection from a search engine promo dialog.
     * @param type The type of search engine promo dialog that was shown.
     * @param keywords The keywords for all search engines listed in the order shown to the user.
     * @param keyword The keyword for the search engine chosen.
     */
    protected void onUserSearchEngineChoiceFromPromoDialog(
            @SearchEnginePromoType int type, List<String> keywords, String keyword) {
        TemplateUrlService.getInstance().setSearchEngine(keyword);
        ContextUtils.getAppSharedPreferences()
                .edit()
                .putInt(KEY_SEARCH_ENGINE_PROMO_SHOW_STATE, SEARCH_ENGINE_PROMO_CHECKED_AND_SHOWN)
                .apply();
        mSearchEnginePromoShown = true;
    }

    /**
     * To be called after the user has made a selection from a search engine promo dialog.
     * @param type The type of search engine promo dialog that was shown.
     * @param keyword The keyword for the search engine chosen.
     */
    protected void onUserSearchEngineChoiceFromPromoDialog(
            @SearchEnginePromoType int type, String keyword) {
        // TODO(yusufo) : Not used. Remove this.
    }

    private SpecialLocaleHandler getSpecialLocaleHandler() {
        if (mLocaleHandler == null) mLocaleHandler = new SpecialLocaleHandler(getSpecialLocaleId());
        return mLocaleHandler;
    }

    /**
     * Get the list of search engines that a user may choose between.
     * @param promoType Which search engine list to show.
     * @return List of engines to show.
     */
    public List<TemplateUrl> getSearchEnginesForPromoDialog(@SearchEnginePromoType int promoType) {
        TemplateUrlService instance = TemplateUrlService.getInstance();
        assert instance.isLoaded();
        return instance.getSearchEngines();
    }

    /** Set a LocaleManager to be used for testing. */
    @VisibleForTesting
    public static void setInstanceForTest(LocaleManager instance) {
        sInstance = instance;
    }

    /**
     * Record any locale based metrics related with the search widget. Recorded on initialization
     * only.
     * @param widgetPresent Whether there is at least one search widget on home screen.
     */
    public void recordLocaleBasedSearchWidgetMetrics(boolean widgetPresent) {}

    /**
     * @return Whether the search engine promo has been shown.
     */
    public boolean hasShownSearchEnginePromo() {
        return mSearchEnginePromoShown;
    }

    /**
     * Record any locale based metrics related with search. Recorded per search.
     * @param isFromSearchWidget Whether the search was performed from the search widget.
     * @param url Url for the search made.
     * @param transition The transition type for the navigation.
     */
    public void recordLocaleBasedSearchMetrics(
            boolean isFromSearchWidget, String url, @PageTransition int transition) {}

    private static native int nativeGetEngineType(String url);
}

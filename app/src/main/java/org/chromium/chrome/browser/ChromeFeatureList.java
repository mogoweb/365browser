// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.base.annotations.MainDex;
import org.chromium.base.library_loader.LibraryLoader;

import java.util.Map;

/**
 * Java accessor for base/feature_list.h state.
 */
@JNINamespace("chrome::android")
@MainDex
public abstract class ChromeFeatureList {
    /** Map that stores substitution feature flags for tests. */
    private static Map<String, Boolean> sTestFeatures;

    // Prevent instantiation.
    private ChromeFeatureList() {}

    /**
     * Sets the feature flags to use in JUnit tests, since native calls are not available there.
     * Do not use directly, prefer using the {@link Features} annotation.
     *
     * @see Features
     * @see Features.Processor
     */
    @VisibleForTesting
    public static void setTestFeatures(Map<String, Boolean> features) {
        sTestFeatures = features;
    }

    /**
     * @return Whether the native FeatureList has been initialized. If this method returns false,
     * none of the methods in this class that require native access should be called (except in
     * tests if test features have been set).
     */
    public static boolean isInitialized() {
        if (!LibraryLoader.isInitialized()) return false;

        // Even if the native library is loaded, the C++ FeatureList might not be initialized yet.
        // In that case, accessing it will not immediately fail, but instead cause a crash later
        // when it is initialized. Return whether the native FeatureList has been initialized,
        // so the return value can be tested, or asserted for a more actionable stack trace
        // on failure.
        return nativeIsInitialized();
    }

    /**
     * Returns whether the specified feature is enabled or not.
     *
     * Note: Features queried through this API must be added to the array
     * |kFeaturesExposedToJava| in chrome/browser/android/chrome_feature_list.cc
     *
     * @param featureName The name of the feature to query.
     * @return Whether the feature is enabled or not.
     */
    public static boolean isEnabled(String featureName) {
        if (sTestFeatures != null) {
            Boolean enabled = sTestFeatures.get(featureName);
            if (enabled == null) throw new IllegalArgumentException(featureName);
            return enabled.booleanValue();
        }

        assert isInitialized();
        return nativeIsEnabled(featureName);
    }

    /**
     * Returns a field trial param for the specified feature.
     *
     * Note: Features queried through this API must be added to the array
     * |kFeaturesExposedToJava| in chrome/browser/android/chrome_feature_list.cc
     *
     * @param featureName The name of the feature to retrieve a param for.
     * @param paramName The name of the param for which to get as an integer.
     * @return The parameter value as a String. The string is empty if the feature does not exist or
     *   the specified parameter does not exist.
     */
    public static String getFieldTrialParamByFeature(String featureName, String paramName) {
        assert isInitialized();
        return nativeGetFieldTrialParamByFeature(featureName, paramName);
    }

    /**
     * Returns a field trial param as an int for the specified feature.
     *
     * Note: Features queried through this API must be added to the array
     * |kFeaturesExposedToJava| in chrome/browser/android/chrome_feature_list.cc
     *
     * @param featureName The name of the feature to retrieve a param for.
     * @param paramName The name of the param for which to get as an integer.
     * @param defaultValue The integer value to use if the param is not available.
     * @return The parameter value as an int. Default value if the feature does not exist or the
     *         specified parameter does not exist or its string value does not represent an int.
     */
    public static int getFieldTrialParamByFeatureAsInt(
            String featureName, String paramName, int defaultValue) {
        assert isInitialized();
        return nativeGetFieldTrialParamByFeatureAsInt(featureName, paramName, defaultValue);
    }

    /**
     * Returns a field trial param as a double for the specified feature.
     *
     * Note: Features queried through this API must be added to the array
     * |kFeaturesExposedToJava| in chrome/browser/android/chrome_feature_list.cc
     *
     * @param featureName The name of the feature to retrieve a param for.
     * @param paramName The name of the param for which to get as an integer.
     * @param defaultValue The double value to use if the param is not available.
     * @return The parameter value as a double. Default value if the feature does not exist or the
     *         specified parameter does not exist or its string value does not represent a double.
     */
    public static double getFieldTrialParamByFeatureAsDouble(
            String featureName, String paramName, double defaultValue) {
        assert isInitialized();
        return nativeGetFieldTrialParamByFeatureAsDouble(featureName, paramName, defaultValue);
    }

    /**
     * Returns a field trial param as a boolean for the specified feature.
     *
     * Note: Features queried through this API must be added to the array
     * |kFeaturesExposedToJava| in chrome/browser/android/chrome_feature_list.cc
     *
     * @param featureName The name of the feature to retrieve a param for.
     * @param paramName The name of the param for which to get as an integer.
     * @param defaultValue The boolean value to use if the param is not available.
     * @return The parameter value as a boolean. Default value if the feature does not exist or the
     *         specified parameter does not exist or its string value is neither "true" nor "false".
     */
    public static boolean getFieldTrialParamByFeatureAsBoolean(
            String featureName, String paramName, boolean defaultValue) {
        assert isInitialized();
        return nativeGetFieldTrialParamByFeatureAsBoolean(featureName, paramName, defaultValue);
    }

    // Alphabetical:
    public static final String ANDROID_PAY_INTEGRATION_V1 = "AndroidPayIntegrationV1";
    public static final String ANDROID_PAY_INTEGRATION_V2 = "AndroidPayIntegrationV2";
    public static final String ANDROID_PAYMENT_APPS = "AndroidPaymentApps";
    public static final String AUTOFILL_SCAN_CARDHOLDER_NAME = "AutofillScanCardholderName";
    public static final String CCT_BACKGROUND_TAB = "CCTBackgroundTab";
    public static final String CCT_EXTERNAL_LINK_HANDLING = "CCTExternalLinkHandling";
    public static final String CCT_POST_MESSAGE_API = "CCTPostMessageAPI";
    public static final String CHROME_HOME = "ChromeHome";
    public static final String CHROME_HOME_EXPAND_BUTTON = "ChromeHomeExpandButton";
    public static final String CONSISTENT_OMNIBOX_GEOLOCATION = "ConsistentOmniboxGeolocation";
    public static final String CONTENT_SUGGESTIONS_FAVICONS_FROM_NEW_SERVER =
            "ContentSuggestionsFaviconsFromNewServer";
    public static final String CONTENT_SUGGESTIONS_NOTIFICATIONS =
            "ContentSuggestionsNotifications";
    public static final String CONTENT_SUGGESTIONS_CATEGORIES = "ContentSuggestionsCategories";
    public static final String CONTENT_SUGGESTIONS_SETTINGS = "ContentSuggestionsSettings";
    public static final String CONTENT_SUGGESTIONS_SHOW_SUMMARY = "ContentSuggestionsShowSummary";
    public static final String CONTEXTUAL_SEARCH_SINGLE_ACTIONS = "ContextualSearchSingleActions";
    public static final String CONTEXTUAL_SEARCH_URL_ACTIONS = "ContextualSearchUrlActions";
    public static final String CONTEXTUAL_SUGGESTIONS_CAROUSEL = "ContextualSuggestionsCarousel";
    public static final String COPYLESS_PASTE = "CopylessPaste";
    public static final String CUSTOM_CONTEXT_MENU = "CustomContextMenu";
    public static final String CUSTOM_FEEDBACK_UI = "CustomFeedbackUi";
    // Enables the Data Reduction Proxy menu item in the main menu rather than under Settings on
    // Android.
    public static final String DATA_REDUCTION_MAIN_MENU = "DataReductionProxyMainMenu";
    public static final String DATA_REDUCTION_SITE_BREAKDOWN = "DataReductionProxySiteBreakdown";
    public static final String DOWNLOAD_HOME_SHOW_STORAGE_INFO = "DownloadHomeShowStorageInfo";
    // When enabled, fullscreen WebContents will be moved to a new Activity. Coming soon...
    public static final String FULLSCREEN_ACTIVITY = "FullscreenActivity";
    // Whether we show an important sites dialog in the "Clear Browsing Data" flow.
    public static final String IMPORTANT_SITES_IN_CBD = "ImportantSitesInCBD";
    public static final String TABS_IN_CBD = "TabsInCBD";
    public static final String IMPROVED_A2HS = "ImprovedA2HS";
    public static final String SEARCH_ENGINE_PROMO_EXISTING_DEVICE =
            "SearchEnginePromo.ExistingDevice";
    public static final String SEARCH_ENGINE_PROMO_NEW_DEVICE = "SearchEnginePromo.NewDevice";
    public static final String MATERIAL_DESIGN_INCOGNITO_NTP = "MaterialDesignIncognitoNTP";
    public static final String NEW_PHOTO_PICKER = "NewPhotoPicker";
    public static final String NO_CREDIT_CARD_ABORT = "NoCreditCardAbort";
    public static final String NTP_CONDENSED_LAYOUT = "NTPCondensedLayout";
    public static final String NTP_CONDENSED_TILE_LAYOUT = "NTPCondensedTileLayout";
    public static final String NTP_FOREIGN_SESSIONS_SUGGESTIONS = "NTPForeignSessionsSuggestions";
    public static final String NTP_LAUNCH_AFTER_INACTIVITY = "NTPLaunchAfterInactivity";
    public static final String NTP_OFFLINE_PAGES_FEATURE_NAME = "NTPOfflinePages";
    public static final String NTP_SHOW_GOOGLE_G_IN_OMNIBOX = "NTPShowGoogleGInOmnibox";
    public static final String NTP_SNIPPETS_INCREASED_VISIBILITY = "NTPSnippetsIncreasedVisibility";
    public static final String SERVICE_WORKER_PAYMENT_APPS = "ServiceWorkerPaymentApps";
    public static final String TAB_REPARENTING = "TabReparenting";
    public static final String VIDEO_PERSISTENCE = "VideoPersistence";
    public static final String VR_BROWSING_FEEDBACK = "VrBrowsingFeedback";
    public static final String VR_CUSTOM_TAB_BROWSING = "VrCustomTabBrowsing";
    public static final String VR_SHELL = "VrShell";
    public static final String WEB_PAYMENTS = "WebPayments";
    public static final String WEB_PAYMENTS_MODIFIERS = "WebPaymentsModifiers";
    public static final String WEB_PAYMENTS_SINGLE_APP_UI_SKIP = "WebPaymentsSingleAppUiSkip";
    public static final String WEBVR_AUTOPRESENT = "WebVrAutopresent";
    public static final String WEBVR_CARDBOARD_SUPPORT = "WebVRCardboardSupport";
    public static final String XGEO_VISIBLE_NETWORKS = "XGEOVisibleNetworks";

    private static native boolean nativeIsInitialized();
    private static native boolean nativeIsEnabled(String featureName);
    private static native String nativeGetFieldTrialParamByFeature(
            String featureName, String paramName);
    private static native int nativeGetFieldTrialParamByFeatureAsInt(
            String featureName, String paramName, int defaultValue);
    private static native double nativeGetFieldTrialParamByFeatureAsDouble(
            String featureName, String paramName, double defaultValue);
    private static native boolean nativeGetFieldTrialParamByFeatureAsBoolean(
            String featureName, String paramName, boolean defaultValue);
}

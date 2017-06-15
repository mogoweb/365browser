// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.contextualsearch;

import android.text.TextUtils;

import org.chromium.base.CommandLine;
import org.chromium.base.SysUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.ChromeSwitches;
import org.chromium.components.variations.VariationsAssociatedData;

/**
 * Provides Field Trial support for the Contextual Search application within Chrome for Android.
 */
public class ContextualSearchFieldTrial {
    private static final String FIELD_TRIAL_NAME = "ContextualSearch";
    private static final String DISABLED_PARAM = "disabled";
    private static final String ENABLED_VALUE = "true";

    static final String MANDATORY_PROMO_ENABLED = "mandatory_promo_enabled";
    static final String MANDATORY_PROMO_LIMIT = "mandatory_promo_limit";
    static final int MANDATORY_PROMO_DEFAULT_LIMIT = 10;

    private static final String PEEK_PROMO_FORCED = "peek_promo_forced";
    @VisibleForTesting
    static final String PEEK_PROMO_ENABLED = "peek_promo_enabled";
    private static final String PEEK_PROMO_MAX_SHOW_COUNT = "peek_promo_max_show_count";
    private static final int PEEK_PROMO_DEFAULT_MAX_SHOW_COUNT = 10;

    private static final String DISABLE_SEARCH_TERM_RESOLUTION = "disable_search_term_resolution";
    private static final String ENABLE_BLACKLIST = "enable_blacklist";

    // Translation.  All these members are private, except for usage by testing.
    // Master switch, needed to disable all translate code for Contextual Search in case of an
    // emergency.
    @VisibleForTesting
    static final String DISABLE_TRANSLATION = "disable_translation";
    // Enables usage of English as the target language even when it's the primary UI language.
    @VisibleForTesting
    static final String ENABLE_ENGLISH_TARGET_TRANSLATION =
            "enable_english_target_translation";

    // TODO(donnd): remove all supporting code once short-lived data collection is done.
    private static final String SCREEN_TOP_SUPPRESSION_DPS = "screen_top_suppression_dps";
    private static final String ENABLE_BAR_OVERLAP_COLLECTION = "enable_bar_overlap_collection";
    private static final String BAR_OVERLAP_SUPPRESSION_ENABLED = "enable_bar_overlap_suppression";

    private static final String MINIMUM_SELECTION_LENGTH = "minimum_selection_length";

    // Safety switch for disabling online-detection.  Also used to disable detection when running
    // tests.
    @VisibleForTesting
    static final String ONLINE_DETECTION_DISABLED = "disable_online_detection";

    private static final String DISABLE_AMP_AS_SEPARATE_TAB = "disable_amp_as_separate_tab";

    // Machine Learning
    private static final String ENABLE_RANKER_LOGGING = "enable_ranker_logging";

    // Privacy-related flags
    private static final String DISABLE_SEND_HOME_COUNTRY = "disable_send_home_country";
    private static final String DISABLE_PAGE_CONTENT_NOTIFICATION =
            "disable_page_content_notification";

    // Cached values to avoid repeated and redundant JNI operations.
    private static Boolean sEnabled;
    private static Boolean sDisableSearchTermResolution;
    private static Boolean sIsMandatoryPromoEnabled;
    private static Integer sMandatoryPromoLimit;
    private static Boolean sIsPeekPromoEnabled;
    private static Integer sPeekPromoMaxCount;
    private static Boolean sIsTranslationDisabled;
    private static Boolean sIsEnglishTargetTranslationEnabled;
    private static Integer sScreenTopSuppressionDps;
    private static Boolean sIsBarOverlapCollectionEnabled;
    private static Boolean sIsBarOverlapSuppressionEnabled;
    private static Integer sMinimumSelectionLength;
    private static Boolean sIsOnlineDetectionDisabled;
    private static Boolean sIsAmpAsSeparateTabDisabled;
    private static Boolean sContextualSearchSingleActionsEnabled;
    private static Boolean sIsSendHomeCountryDisabled;
    private static Boolean sIsPageContentNotificationDisabled;
    private static Boolean sContextualSearchUrlActionsEnabled;
    private static Boolean sIsRankerLoggingEnabled;

    /**
     * Don't instantiate.
     */
    private ContextualSearchFieldTrial() {}

    /**
     * Checks the current Variations parameters associated with the active group as well as the
     * Chrome preference to determine if the service is enabled.
     * @return Whether Contextual Search is enabled or not.
     */
    public static boolean isEnabled() {
        if (sEnabled == null) {
            sEnabled = detectEnabled();
        }
        return sEnabled.booleanValue();
    }

    private static boolean detectEnabled() {
        if (SysUtils.isLowEndDevice()) {
            return false;
        }

        // Allow this user-flippable flag to disable the feature.
        if (CommandLine.getInstance().hasSwitch(ChromeSwitches.DISABLE_CONTEXTUAL_SEARCH)) {
            return false;
        }

        // Allow this user-flippable flag to enable the feature.
        if (CommandLine.getInstance().hasSwitch(ChromeSwitches.ENABLE_CONTEXTUAL_SEARCH)) {
            return true;
        }

        // Allow disabling the feature remotely.
        if (getBooleanParam(DISABLED_PARAM)) {
            return false;
        }

        return true;
    }

    /**
     * @return Whether the search term resolution is enabled.
     */
    static boolean isSearchTermResolutionEnabled() {
        if (sDisableSearchTermResolution == null) {
            sDisableSearchTermResolution = getBooleanParam(DISABLE_SEARCH_TERM_RESOLUTION);
        }

        if (sDisableSearchTermResolution.booleanValue()) {
            return false;
        }

        return true;
    }

    /**
     * @return Whether the Mandatory Promo is enabled.
     */
    static boolean isMandatoryPromoEnabled() {
        if (sIsMandatoryPromoEnabled == null) {
            sIsMandatoryPromoEnabled = getBooleanParam(MANDATORY_PROMO_ENABLED);
        }
        return sIsMandatoryPromoEnabled.booleanValue();
    }

    /**
     * @return The number of times the Promo should be seen before it becomes mandatory.
     */
    static int getMandatoryPromoLimit() {
        if (sMandatoryPromoLimit == null) {
            sMandatoryPromoLimit = getIntParamValueOrDefault(
                    MANDATORY_PROMO_LIMIT,
                    MANDATORY_PROMO_DEFAULT_LIMIT);
        }
        return sMandatoryPromoLimit.intValue();
    }

    /**
     * @return Whether the Peek Promo is forcibly enabled (used for testing).
     */
    static boolean isPeekPromoForced() {
        return CommandLine.getInstance().hasSwitch(PEEK_PROMO_FORCED);
    }

    /**
     * @return Whether the Peek Promo is enabled.
     */
    static boolean isPeekPromoEnabled() {
        if (sIsPeekPromoEnabled == null) {
            sIsPeekPromoEnabled = getBooleanParam(PEEK_PROMO_ENABLED);
        }
        return sIsPeekPromoEnabled.booleanValue();
    }

    /**
     * @return Whether the blacklist is enabled.
     */
    static boolean isBlacklistEnabled() {
        return getBooleanParam(ENABLE_BLACKLIST);
    }

    /**
     * @return The maximum number of times the Peek Promo should be displayed.
     */
    static int getPeekPromoMaxShowCount() {
        if (sPeekPromoMaxCount == null) {
            sPeekPromoMaxCount = getIntParamValueOrDefault(
                    PEEK_PROMO_MAX_SHOW_COUNT,
                    PEEK_PROMO_DEFAULT_MAX_SHOW_COUNT);
        }
        return sPeekPromoMaxCount.intValue();
    }

    /**
     * @return Whether all translate code is disabled.
     */
    static boolean isTranslationDisabled() {
        if (sIsTranslationDisabled == null) {
            sIsTranslationDisabled = getBooleanParam(DISABLE_TRANSLATION);
        }
        return sIsTranslationDisabled.booleanValue();
    }

    /**
     * @return Whether English-target translation should be enabled (default is disabled for 'en').
     */
    static boolean isEnglishTargetTranslationEnabled() {
        if (sIsEnglishTargetTranslationEnabled == null) {
            sIsEnglishTargetTranslationEnabled = getBooleanParam(ENABLE_ENGLISH_TARGET_TRANSLATION);
        }
        return sIsEnglishTargetTranslationEnabled.booleanValue();
    }

    /**
     * Gets a Y value limit that will suppress a Tap near the top of the screen.
     * Any Y value less than the limit will suppress the Tap trigger.
     * @return The Y value triggering limit in DPs, a value of zero will not limit.
     */
    static int getScreenTopSuppressionDps() {
        if (sScreenTopSuppressionDps == null) {
            sScreenTopSuppressionDps = getIntParamValueOrDefault(SCREEN_TOP_SUPPRESSION_DPS, 0);
        }
        return sScreenTopSuppressionDps.intValue();
    }

    /**
     * @return Whether collecting data on Bar overlap is enabled.
     */
    static boolean isBarOverlapCollectionEnabled() {
        if (sIsBarOverlapCollectionEnabled == null) {
            sIsBarOverlapCollectionEnabled = getBooleanParam(ENABLE_BAR_OVERLAP_COLLECTION);
        }
        return sIsBarOverlapCollectionEnabled.booleanValue();
    }

    /**
     * @return Whether triggering is suppressed by a selection nearly overlapping the normal
     *         Bar peeking location.
     */
    static boolean isBarOverlapSuppressionEnabled() {
        if (sIsBarOverlapSuppressionEnabled == null) {
            sIsBarOverlapSuppressionEnabled = getBooleanParam(BAR_OVERLAP_SUPPRESSION_ENABLED);
        }
        return sIsBarOverlapSuppressionEnabled.booleanValue();
    }

    /**
     * @return The minimum valid selection length.
     */
    static int getMinimumSelectionLength() {
        if (sMinimumSelectionLength == null) {
            sMinimumSelectionLength = getIntParamValueOrDefault(MINIMUM_SELECTION_LENGTH, 0);
        }
        return sMinimumSelectionLength.intValue();
    }

    /**
     * @return Whether to disable auto-promotion of clicks in the AMP carousel into a separate Tab.
     */
    static boolean isAmpAsSeparateTabDisabled() {
        if (sIsAmpAsSeparateTabDisabled == null) {
            sIsAmpAsSeparateTabDisabled = getBooleanParam(DISABLE_AMP_AS_SEPARATE_TAB);
        }
        return sIsAmpAsSeparateTabDisabled;
    }

    /**
     * @return Whether detection of device-online should be disabled (default false).
     */
    static boolean isOnlineDetectionDisabled() {
        // TODO(donnd): Convert to test-only after launch and we have confidence it's robust.
        if (sIsOnlineDetectionDisabled == null) {
            sIsOnlineDetectionDisabled = getBooleanParam(ONLINE_DETECTION_DISABLED);
        }
        return sIsOnlineDetectionDisabled;
    }

    /**
     * @return Whether sending the "home country" to Google is disabled.
     */
    static boolean isSendHomeCountryDisabled() {
        if (sIsSendHomeCountryDisabled == null) {
            sIsSendHomeCountryDisabled = getBooleanParam(DISABLE_SEND_HOME_COUNTRY);
        }
        return sIsSendHomeCountryDisabled.booleanValue();
    }

    /**
     * @return Whether sending the page content notifications to observers (e.g. icing for
     *         conversational search) is disabled.
     */
    static boolean isPageContentNotificationDisabled() {
        if (sIsPageContentNotificationDisabled == null) {
            sIsPageContentNotificationDisabled = getBooleanParam(DISABLE_PAGE_CONTENT_NOTIFICATION);
        }
        return sIsPageContentNotificationDisabled.booleanValue();
    }

    /**
     * @return Whether or not logging to Ranker is enabled.
     */
    static boolean isRankerLoggingEnabled() {
        if (sIsRankerLoggingEnabled == null) {
            sIsRankerLoggingEnabled = getBooleanParam(ENABLE_RANKER_LOGGING);
        }

        return sIsRankerLoggingEnabled;
    }

    // ---------------
    // Features.
    // ---------------

    /**
     * @return Whether or not single actions based on Contextual Cards is enabled.
     */
    static boolean isContextualSearchSingleActionsEnabled() {
        if (sContextualSearchSingleActionsEnabled == null) {
            sContextualSearchSingleActionsEnabled =
                    ChromeFeatureList.isEnabled(ChromeFeatureList.CONTEXTUAL_SEARCH_SINGLE_ACTIONS);
        }

        return sContextualSearchSingleActionsEnabled;
    }

    /**
     * @return Whether or not URL actions based on Contextual Cards is enabled.
     */
    static boolean isContextualSearchUrlActionsEnabled() {
        if (sContextualSearchUrlActionsEnabled == null) {
            sContextualSearchUrlActionsEnabled =
                    ChromeFeatureList.isEnabled(ChromeFeatureList.CONTEXTUAL_SEARCH_URL_ACTIONS);
        }

        return sContextualSearchUrlActionsEnabled;
    }

    // --------------------------------------------------------------------------------------------
    // Helpers.
    // --------------------------------------------------------------------------------------------

    /**
     * Gets a boolean Finch parameter, assuming the <paramName>="true" format.  Also checks for a
     * command-line switch with the same name, for easy local testing.
     * @param paramName The name of the Finch parameter (or command-line switch) to get a value for.
     * @return Whether the Finch param is defined with a value "true", if there's a command-line
     *         flag present with any value.
     */
    private static boolean getBooleanParam(String paramName) {
        if (CommandLine.getInstance().hasSwitch(paramName)) {
            return true;
        }
        return TextUtils.equals(ENABLED_VALUE,
                VariationsAssociatedData.getVariationParamValue(FIELD_TRIAL_NAME, paramName));
    }

    /**
     * Returns an integer value for a Finch parameter, or the default value if no parameter exists
     * in the current configuration.  Also checks for a command-line switch with the same name.
     * @param paramName The name of the Finch parameter (or command-line switch) to get a value for.
     * @param defaultValue The default value to return when there's no param or switch.
     * @return An integer value -- either the param or the default.
     */
    private static int getIntParamValueOrDefault(String paramName, int defaultValue) {
        String value = CommandLine.getInstance().getSwitchValue(paramName);
        if (TextUtils.isEmpty(value)) {
            value = VariationsAssociatedData.getVariationParamValue(FIELD_TRIAL_NAME, paramName);
        }
        if (!TextUtils.isEmpty(value)) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }

        return defaultValue;
    }
}

// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.contextualsearch;

import android.text.TextUtils;

import org.chromium.base.CommandLine;
import org.chromium.base.SysUtils;
import org.chromium.chrome.browser.ChromeSwitches;
import org.chromium.chrome.browser.ChromeVersionInfo;
import org.chromium.components.variations.VariationsAssociatedData;

import java.util.Arrays;
import java.util.Locale;

/**
 * Provides Field Trial support for the Contextual Search application within Chrome for Android.
 */
public class ContextualSearchFieldTrial {
    private static final String FIELD_TRIAL_NAME = "ContextualSearch";
    private static final String ENABLED_PARAM = "enabled";
    private static final String ENABLED_VALUE = "true";

    private static final String DISABLE_FOR_CJK = "disable_for_cjk";
    private static final String DISABLE_FOR_CHINESE = "disable_for_chinese";
    private static final String DISABLE_FOR_JAPANESE = "disable_for_japanese";
    private static final String DISABLE_FOR_KOREAN = "disable_for_korean";

    // TODO(pedrosimonetti): Confirm if we can delete promo_on_longpress_only now.
    private static final String PROMO_ON_LONGPRESS_ONLY = "promo_on_longpress_only";
    static final String PROMO_ON_LIMITED_TAPS = "promo_on_limited_taps";
    static final String TAP_TRIGGERED_PROMO_LIMIT = "tap_triggered_promo_limit";
    static final String TAP_RESOLVE_LIMIT_FOR_DECIDED = "tap_resolve_limit_for_decided";
    static final String TAP_PREFETCH_LIMIT_FOR_DECIDED = "tap_prefetch_limit_for_decided";
    static final String TAP_RESOLVE_LIMIT_FOR_UNDECIDED = "tap_resolve_limit_for_undecided";
    static final String TAP_PREFETCH_LIMIT_FOR_UNDECIDED = "tap_prefetch_limit_for_undecided";

    static final String SELECTION_EXPANSION_DISABLED =
            "contextual_search_selection_expansion_disabled";

    static final String NARROW_PANEL_SUPPORTED = "contextual_search_narrow_panel_supported";

    private static final String CHINESE_LANGUAGE_CODE = "zh";
    private static final String JAPANESE_LANGUAGE_CODE = "ja";
    private static final String KOREAN_LANGUAGE_CODE = "ko";
    private static final String[] CJK_LANGUAGE_CODES = {CHINESE_LANGUAGE_CODE,
        JAPANESE_LANGUAGE_CODE, KOREAN_LANGUAGE_CODE};

    // The default navigation-detection-delay in milliseconds.
    private static final int DEFAULT_TAP_NAVIGATION_DETECTION_DELAY = 16;
    private static final String NAVIGATION_DETECTION_DELAY = "tap_navigation_detection_delay";

    private static final int UNLIMITED_TAPS = -1;
    private static final int DEFAULT_TAP_RESOLVE_LIMIT_FOR_DECIDED = UNLIMITED_TAPS;
    private static final int DEFAULT_TAP_PREFETCH_LIMIT_FOR_DECIDED = UNLIMITED_TAPS;
    private static final int DEFAULT_TAP_RESOLVE_LIMIT_FOR_UNDECIDED = 100;
    private static final int DEFAULT_TAP_PREFETCH_LIMIT_FOR_UNDECIDED = 10;

    // Cached value to avoid repeated and redundant JNI operations.
    private static Boolean sEnabled;
    private static Boolean sSelectionExpansionDisabled;
    private static Boolean sNarrowPanelSupported;

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

        // This is used for instrumentation tests (i.e. it is not a user-flippable flag). We cannot
        // use Variations params because in the test harness, the initialization comes before any
        // native methods are available. And the ContextualSearchManager is initialized very early
        // in the Chrome initialization.
        if (CommandLine.getInstance().hasSwitch(
                    ChromeSwitches.ENABLE_CONTEXTUAL_SEARCH_FOR_TESTING)) {
            return true;
        }

        // Allow this user-flippable flag to disable the feature.
        if (CommandLine.getInstance().hasSwitch(ChromeSwitches.DISABLE_CONTEXTUAL_SEARCH)) {
            return false;
        }

        // Allow this user-flippable flag to override disabling due to language.
        if (CommandLine.getInstance().hasSwitch(ChromeSwitches.ENABLE_CONTEXTUAL_SEARCH)) {
            return true;
        }

        String languageCode = Locale.getDefault().getLanguage();
        if (!isLanguageSupported(languageCode)) return false;

        if (ChromeVersionInfo.isLocalBuild()) return true;

        return getBooleanParam(ENABLED_PARAM);
    }

    /**
     * @param languageCode The language code of the system.
     * @return Whether the language is supported, given the language code.
     */
    static boolean isLanguageSupported(String languageCode) {
        if (Arrays.asList(CJK_LANGUAGE_CODES).contains(languageCode)
                && getBooleanParam(DISABLE_FOR_CJK)) {
            return false;
        }

        if (languageCode.equals(CHINESE_LANGUAGE_CODE) && getBooleanParam(DISABLE_FOR_CHINESE)) {
            return false;
        }

        if (languageCode.equals(JAPANESE_LANGUAGE_CODE) && getBooleanParam(DISABLE_FOR_JAPANESE)) {
            return false;
        }

        if (languageCode.equals(KOREAN_LANGUAGE_CODE) && getBooleanParam(DISABLE_FOR_KOREAN)) {
            return false;
        }

        return true;
    }

    /**
     * Gets whether the promo should be triggered on longpress only.
     * @return {@code true} iff Finch says we should trigger the promo only on touch-and-hold.
     */
    static boolean isPromoLongpressTriggeredOnly() {
        return getBooleanParam(PROMO_ON_LONGPRESS_ONLY);
    }

    /**
     * @return Whether the promo should be triggered by a limited number of taps.
     */
    public static boolean isPromoLimitedByTapCounts() {
        return getBooleanParam(PROMO_ON_LIMITED_TAPS);
    }

    /**
     * @return The maximum number of times the promo can be triggered by a tap, or
     * {@code ContextualSearchUma#PROMO_TAPS_REMAINING_INVALID} if no value is present in the finch
     * configuration.
     */
    static int getPromoTapTriggeredLimit() {
        return getIntParamValueOrDefault(TAP_TRIGGERED_PROMO_LIMIT, UNLIMITED_TAPS);
    }

    /**
     * @return The delay to use for navigation-detection when triggering on a Tap.
     */
    static int getNavigationDetectionDelay() {
        return getIntParamValueOrDefault(NAVIGATION_DETECTION_DELAY,
                DEFAULT_TAP_NAVIGATION_DETECTION_DELAY);
    }

    /**
     * @return Whether Search Term Resolution in response to a Tap gesture is limited for decided
     *         users.
     */
    static boolean isTapResolveLimitedForDecided() {
        return getTapResolveLimitForDecided() != ContextualSearchFieldTrial.UNLIMITED_TAPS;
    }

    /**
     * @return Whether prefetch in response to a Tap gesture is limited for decided users.
     */
    static boolean isTapPrefetchLimitedForDecided() {
        return getTapPrefetchLimitForDecided() != ContextualSearchFieldTrial.UNLIMITED_TAPS;
    }

    /**
     * @return Whether Search Term Resolution in response to a Tap gesture is limited for undecided
     *         users.
     */
    static boolean isTapResolveLimitedForUndecided() {
        return getTapResolveLimitForUndecided() != ContextualSearchFieldTrial.UNLIMITED_TAPS;
    }

    /**
     * @return Whether prefetch in response to a Tap gesture is limited for undecided users.
     */
    static boolean isTapPrefetchLimitedForUndecided() {
        return getTapPrefetchLimitForUndecided() != ContextualSearchFieldTrial.UNLIMITED_TAPS;
    }
    /**
     * @return The limit on the number of taps to resolve for decided users, or the default if no
     *         value is present in the Finch configuration.
     */
    static int getTapResolveLimitForDecided() {
        return getIntParamValueOrDefault(TAP_RESOLVE_LIMIT_FOR_DECIDED,
                DEFAULT_TAP_RESOLVE_LIMIT_FOR_DECIDED);
    }

    /**
     * @return The limit on the number of prefetches to issue for decided users, or the default
     *         if no value is present.
     */
    static int getTapPrefetchLimitForDecided() {
        return getIntParamValueOrDefault(TAP_PREFETCH_LIMIT_FOR_DECIDED,
                DEFAULT_TAP_PREFETCH_LIMIT_FOR_DECIDED);
    }

    /**
     * @return The limit on the number of taps to resolve for undecided users, or the default if no
     *         value is present in the Finch configuration.
     */
    static int getTapResolveLimitForUndecided() {
        return getIntParamValueOrDefault(TAP_RESOLVE_LIMIT_FOR_UNDECIDED,
                DEFAULT_TAP_RESOLVE_LIMIT_FOR_UNDECIDED);
    }

    /**
     * @return The limit on the number of prefetches to issue for undecided users, or the default
     *         if no value is present.
     */
    static int getTapPrefetchLimitForUndecided() {
        return getIntParamValueOrDefault(TAP_PREFETCH_LIMIT_FOR_UNDECIDED,
                DEFAULT_TAP_PREFETCH_LIMIT_FOR_UNDECIDED);
    }

    // --------------------------------------------------------------------------------------------
    // Experimental UI Features.
    // --------------------------------------------------------------------------------------------

    /**
     * @return Whether the base page selection expansion after server response is disabled.
     */
    public static boolean isSelectionExpansionDisabled() {
        if (sSelectionExpansionDisabled == null) {
            sSelectionExpansionDisabled = getBooleanParam(SELECTION_EXPANSION_DISABLED);
        }
        return sSelectionExpansionDisabled.booleanValue();
    }

    /**
     * @return Whether the narrow version of the Search Panel is supported.
     */
    public static boolean isNarrowPanelSupported() {
        if (sNarrowPanelSupported == null) {
            sNarrowPanelSupported = getBooleanParam(NARROW_PANEL_SUPPORTED);
        }
        return sNarrowPanelSupported.booleanValue();
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

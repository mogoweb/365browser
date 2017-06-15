// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.contextualsearch;

import java.net.URL;

/**
 * An interface for logging to UMA via Ranker.
 */
public interface ContextualSearchRankerLogger {
    enum Feature {
        UNKNOWN,
        // Outcome labels:
        OUTCOME_WAS_PANEL_OPENED,
        OUTCOME_WAS_QUICK_ACTION_CLICKED,
        OUTCOME_WAS_QUICK_ANSWER_SEEN,
        // Features:
        DURATION_AFTER_SCROLL_MS,
        DURATION_BEFORE_SCROLL_MS,
        SCREEN_TOP_DPS,
        WAS_SCREEN_BOTTOM,
        // User usage features:
        PREVIOUS_WEEK_IMPRESSIONS_COUNT,
        PREVIOUS_WEEK_CTR_PERCENT,
        PREVIOUS_28DAY_IMPRESSIONS_COUNT,
        PREVIOUS_28DAY_CTR_PERCENT
    }

    /**
     * Sets up logging for the page with the given URL.
     * This method must be called before calling {@link #log} or {@link #logOutcome}.
     * @param basePageUrl The URL of the base page to log with Ranker.
     */
    void setupLoggingForPage(URL basePageUrl);

    /**
     * Logs a particular feature at inference time as a key/value pair.
     * @param feature The feature to log.
     * @param value The value to log, which is associated with the given key.
     */
    void log(Feature feature, Object value);

    /**
     * Logs an outcome value at training time that indicates an ML label as a key/value pair.
     * @param feature The feature to log.
     * @param value The outcome label value.
     */
    void logOutcome(Feature feature, Object value);

    /**
     * Writes all the accumulated log entries and resets the logger so that future log calls
     * accumulate into a new record.
     * After calling this method another call to {@link #setupLoggingForPage} is required before
     * additional {@link #log} or {@link #logOutcome} calls.
     */
    void writeLogAndReset();
}

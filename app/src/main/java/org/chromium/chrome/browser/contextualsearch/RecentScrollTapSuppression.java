// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.contextualsearch;

/**
 * Heuristic for Tap suppression after a recent scroll action.
 * Handles logging of results seen and activation.
 */
public class RecentScrollTapSuppression extends ContextualSearchHeuristic {
    private final int mExperiementThresholdMs;
    private final int mDurationSinceRecentScrollMs;
    private final boolean mIsConditionSatisfied;

    /**
     * Constructs a Tap suppression heuristic that handles a Tap after a recent scroll.
     * This logs activation data that includes whether it activated for a threshold specified
     * by an experiment. This also logs Results-seen data to profile when results are seen relative
     * to a recent scroll.
     * @param selectionController The {@link ContextualSearchSelectionController}.
     */
    RecentScrollTapSuppression(ContextualSearchSelectionController selectionController) {
        long recentScrollTimeNs = selectionController.getLastScrollTime();
        if (recentScrollTimeNs > 0) {
            mDurationSinceRecentScrollMs =
                    (int) ((System.nanoTime() - recentScrollTimeNs) / NANOSECONDS_IN_A_MILLISECOND);
        } else {
            mDurationSinceRecentScrollMs = 0;
        }
        mExperiementThresholdMs = ContextualSearchFieldTrial.getRecentScrollSuppressionDurationMs();
        // If the configured threshold is 0, then suppression is not enabled.
        if (mExperiementThresholdMs > 0) {
            mIsConditionSatisfied = mDurationSinceRecentScrollMs > 0
                    && mDurationSinceRecentScrollMs < mExperiementThresholdMs;
        } else {
            mIsConditionSatisfied = false;
        }
    }

    @Override
    protected boolean isConditionSatisfied() {
        return mIsConditionSatisfied;
    }

    @Override
    protected void logConditionState() {
        if (mExperiementThresholdMs > 0) {
            ContextualSearchUma.logRecentScrollSuppression(mIsConditionSatisfied);
        }
    }

    @Override
    protected void logResultsSeen(boolean wasSearchContentViewSeen, boolean wasActivatedByTap) {
        if (wasActivatedByTap && mDurationSinceRecentScrollMs > 0
                && ContextualSearchFieldTrial.isRecentScrollCollectionEnabled()) {
            ContextualSearchUma.logRecentScrollDuration(
                    mDurationSinceRecentScrollMs, wasSearchContentViewSeen);
        }
    }
}

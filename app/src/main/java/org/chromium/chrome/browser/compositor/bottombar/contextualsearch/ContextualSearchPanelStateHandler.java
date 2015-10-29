// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.bottombar.contextualsearch;

import org.chromium.chrome.browser.compositor.bottombar.contextualsearch.ContextualSearchPanel.PanelState;
import org.chromium.chrome.browser.compositor.bottombar.contextualsearch.ContextualSearchPanel.StateChangeReason;
import org.chromium.chrome.browser.contextualsearch.ContextualSearchUma;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Holds the state of the Contextual Search Panel.
 */
abstract class ContextualSearchPanelStateHandler {

    // Valid previous states for the Panel.
    private static final Map<PanelState, PanelState> PREVIOUS_STATES;
    static {
        Map<PanelState, PanelState> states = new HashMap<PanelState, PanelState>();
        // Pairs are of the form <Current, Previous>.
        states.put(PanelState.PEEKED, PanelState.CLOSED);
        states.put(PanelState.EXPANDED, PanelState.PEEKED);
        states.put(PanelState.MAXIMIZED, PanelState.EXPANDED);
        PREVIOUS_STATES = Collections.unmodifiableMap(states);
    }

    // The current state of the Contextual Search Panel.
    private PanelState mPanelState = PanelState.UNDEFINED;
    private boolean mDidSearchInvolvePromo;
    private boolean mWasSearchContentViewSeen;
    private boolean mIsPromoActive;
    private boolean mHasExpanded;
    private boolean mHasMaximized;
    private boolean mHasExitedPeeking;
    private boolean mHasExitedExpanded;
    private boolean mHasExitedMaximized;
    private boolean mIsSerpNavigation;
    private boolean mWasActivatedByTap;
    private boolean mIsSearchPanelFullyPreloaded;
    private long mSearchStartTimeNs;
    private long mSearchViewStartTimeNs;

    // --------------------------------------------------------------------------------------------
    // Contextual Search Panel states
    // --------------------------------------------------------------------------------------------

    /**
     * @return The panel's state.
     */
    PanelState getPanelState() {
        return mPanelState;
    }

    /**
     * @return The {@code PanelState} that is before the |state| in the order of states.
     */
    PanelState getPreviousPanelState(PanelState state) {
        PanelState prevState = PREVIOUS_STATES.get(state);
        return prevState != null ? prevState : PanelState.UNDEFINED;
    }

    /**
     * Sets the panel's state.
     * @param toState The panel state to transition to.
     * @param reason The reason for a change in the panel's state.
     */
    protected void setPanelState(PanelState toState, StateChangeReason reason) {
        // Note: the logging within this function includes the promo, unless specifically
        // excluded.
        PanelState fromState = mPanelState;
        boolean isStartingSearch = isStartingNewContextualSearch(toState, reason);
        boolean isEndingSearch = isEndingContextualSearch(toState, isStartingSearch);
        boolean isChained = isStartingSearch && isOngoingContextualSearch();
        boolean isSameState = fromState == toState;
        boolean isFirstExitFromPeeking = fromState == PanelState.PEEKED && !mHasExitedPeeking
                && (!isSameState || isStartingSearch);
        boolean isFirstExitFromExpanded = fromState == PanelState.EXPANDED && !mHasExitedExpanded
                && !isSameState;
        boolean isFirstExitFromMaximized = fromState == PanelState.MAXIMIZED && !mHasExitedMaximized
                && !isSameState;
        boolean isFirstSearchView = isFirstExitFromPeeking && toState != PanelState.CLOSED;
        // This variable is needed for logging and gets reset in an isStartingSearch block below,
        // so a local copy is created before the reset.
        boolean isSearchPanelFullyPreloaded = mIsSearchPanelFullyPreloaded;

        if (isEndingSearch) {
            if (!mDidSearchInvolvePromo) {
                // Measure duration only when the promo is not involved.
                long durationMs = (System.nanoTime() - mSearchStartTimeNs) / 1000000;
                ContextualSearchUma.logDuration(mWasSearchContentViewSeen, isChained, durationMs);
            }
            if (mIsPromoActive) {
                // The user is exiting still in the promo, without choosing an option.
                ContextualSearchUma.logPromoSeen(mWasSearchContentViewSeen, mWasActivatedByTap);
            } else {
                ContextualSearchUma.logResultsSeen(mWasSearchContentViewSeen, mWasActivatedByTap);
            }
        }
        if (isStartingSearch) {
            mSearchStartTimeNs = System.nanoTime();
            mSearchViewStartTimeNs = 0;
            mIsSearchPanelFullyPreloaded = false;
            mWasActivatedByTap = reason == StateChangeReason.TEXT_SELECT_TAP;
        }
        if (isFirstSearchView) {
            onSearchPanelFirstView();
        }

        // Log state changes. We only log the first transition to a state within a contextual
        // search. Note that when a user clicks on a link on the search content view, this will
        // trigger a transition to MAXIMIZED (SERP_NAVIGATION) followed by a transition to
        // CLOSED (TAB_PROMOTION). For the purpose of logging, the reason for the second transition
        // is reinterpreted to SERP_NAVIGATION, in order to distinguish it from a tab promotion
        // caused when tapping on the Search Bar when the Panel is maximized.
        StateChangeReason reasonForLogging =
                mIsSerpNavigation ? StateChangeReason.SERP_NAVIGATION : reason;
        if (isStartingSearch || isEndingSearch
                || (!mHasExpanded && toState == PanelState.EXPANDED)
                || (!mHasMaximized && toState == PanelState.MAXIMIZED)) {
            ContextualSearchUma.logFirstStateEntry(fromState, toState, reasonForLogging);
        }
        // Note: CLOSED / UNDEFINED state exits are detected when a search that is not chained is
        // starting.
        if ((isStartingSearch && !isChained) || isFirstExitFromPeeking || isFirstExitFromExpanded
                || isFirstExitFromMaximized) {
            ContextualSearchUma.logFirstStateExit(fromState, toState, reasonForLogging);
        }

        // We can now modify the state.
        if (isFirstExitFromPeeking) {
            mHasExitedPeeking = true;
        } else if (isFirstExitFromExpanded) {
            mHasExitedExpanded = true;
        } else if (isFirstExitFromMaximized) {
            mHasExitedMaximized = true;
        }

        mPanelState = toState;

        if (toState == PanelState.EXPANDED) {
            mHasExpanded = true;
        } else if (toState == PanelState.MAXIMIZED) {
            mHasMaximized = true;
        }
        if (reason == StateChangeReason.SERP_NAVIGATION) {
            mIsSerpNavigation = true;
        }

        if (isEndingSearch) {
            if (mHasMaximized || mHasExpanded) {
                ContextualSearchUma.logSerpLoadedOnClose(isSearchPanelFullyPreloaded);
            }
            mDidSearchInvolvePromo = false;
            mWasSearchContentViewSeen = false;
            mHasExpanded = false;
            mHasMaximized = false;
            mHasExitedPeeking = false;
            mHasExitedExpanded = false;
            mHasExitedMaximized = false;
            mIsSerpNavigation = false;
        }

        // TODO(manzagop): When the user opts in, we should replay his actions for the current
        // contextual search for the standard (non promo) UMA histograms.
    }

    /**
     * Determine if a specific {@code PanelState} is a valid state in the current environment.
     * @param state The state being evaluated.
     * @return whether the state is valid.
     */
    boolean isValidState(PanelState state) {
        ArrayList<PanelState> validStates =
                new ArrayList<PanelState>(PREVIOUS_STATES.values());
        // MAXIMIZED is not the previous state of anything, but it's a valid state.
        validStates.add(PanelState.MAXIMIZED);

        return validStates.contains(state);
    }

    /**
     * Sets that the contextual search involved the promo.
     */
    void setDidSearchInvolvePromo() {
        mDidSearchInvolvePromo = true;
    }

    /**
     * Sets that the Search Content View was seen.
     */
    void setWasSearchContentViewSeen() {
        mWasSearchContentViewSeen = true;
    }

    /**
     * Sets whether the promo is active.
     */
    void setIsPromoActive(boolean shown) {
        mIsPromoActive = shown;
    }

    /**
     * Gets whether the promo is active.
     */
    boolean getIsPromoActive() {
        return mIsPromoActive;
    }

    /**
     * Records timing information when the search results have fully loaded.
     * @param wasPrefetch Whether the request was prefetch-enabled.
     */
    void onSearchResultsLoaded(boolean wasPrefetch) {
        if (mHasExpanded || mHasMaximized) {
            // Already opened, log how long it took.
            assert mSearchViewStartTimeNs != 0;
            long durationMs = (System.nanoTime() - mSearchViewStartTimeNs) / 1000000;
            logSearchPanelLoadDuration(wasPrefetch, durationMs);
        }

        // Not yet opened, wait till an open to log.
        mIsSearchPanelFullyPreloaded = true;
    }

    /**
     * Records timing information when the search panel has been viewed for the first time.
     */
    private void onSearchPanelFirstView() {
        if (mIsSearchPanelFullyPreloaded) {
            // Already fully pre-loaded, record a wait of 0 milliseconds.
            logSearchPanelLoadDuration(true, 0);
        } else {
            // Start a loading timer.
            mSearchViewStartTimeNs = System.nanoTime();
        }
    }

    // --------------------------------------------------------------------------------------------
    // Helpers
    // --------------------------------------------------------------------------------------------

    /**
     * Determine whether a new contextual search is starting.
     * @param toState The contextual search state that will be transitioned to.
     * @param reason The reason for the search state transition.
     * @return Whether a new contextual search is starting.
     */
    private boolean isStartingNewContextualSearch(PanelState toState, StateChangeReason reason) {
        return toState == PanelState.PEEKED
                && (reason == StateChangeReason.TEXT_SELECT_TAP
                        || reason == StateChangeReason.TEXT_SELECT_LONG_PRESS);
    }

    /**
     * Determine whether a contextual search is ending.
     * @param toState The contextual search state that will be transitioned to.
     * @param isStartingSearch Whether a new contextual search is starting.
     * @return Whether a contextual search is ending.
     */
    private boolean isEndingContextualSearch(PanelState toState, boolean isStartingSearch) {
        return isOngoingContextualSearch() && (toState == PanelState.CLOSED || isStartingSearch);
    }

    /**
     * @return Whether there is an ongoing contextual search.
     */
    private boolean isOngoingContextualSearch() {
        return mPanelState != PanelState.UNDEFINED && mPanelState != PanelState.CLOSED;
    }

    /**
     * Logs the duration the user waited for the search panel to fully load, once it was opened.
     * @param wasPrefetch Whether the load included prefetch.
     * @param durationMs The duration to log.
     */
    private void logSearchPanelLoadDuration(boolean wasPrefetch, long durationMs) {
        ContextualSearchUma.logSearchPanelLoadDuration(wasPrefetch, durationMs);
    }
}

// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.contextualsearch;

import org.chromium.chrome.browser.compositor.bottombar.OverlayPanel.StateChangeReason;

/**
 * An interface for driving operations in the Contextual Search Manager's internal state by the
 * {@link ContextualSearchInternalStateController} class.
 */
public interface ContextualSearchInternalStateHandler {
    /**
     * Hides the Contextual Search user interface.
     * {@See ContextualSearchInternalStateController#InternalState#IDLE}.
     */
    void hideContextualSearchUi(StateChangeReason reason);

    /**
     * Shows the Contextual Search user interface for a Tap.
     * {@See ContextualSearchInternalStateController#InternalState#SHOW_FULL_TAP_UI}.
     */
    void showContextualSearchTapUi();

    /**
     * Shows the Contextual Search user interface for a Long-press.
     * {@See ContextualSearchInternalStateController#InternalState#SHOWING_LONGPRESS_SEARCH}.
     */
    void showContextualSearchLongpressUi();

    /**
     * Gathers text surrounding the current selection, which may have been created by either a Tap
     * or a Long-press gesture.
     * {@See ContextualSearchInternalStateController#InternalState#GATHERING_SURROUNDINGS}.
     */
    void gatherSurroundingText();

    /**
     * Starts the process of deciding if we'll suppress the current gesture or not.
     * {@See ContextualSearchInternalStateController#InternalState#DECIDING_SUPPRESSION}.
     */
    void decideSuppression();

    /**
     * Starts the process of selecting a word around the current caret.
     * {@See ContextualSearchInternalStateController#InternalState#START_SHOWING_TAP_UI}.
     */
    void startShowingTapUi();

    /**
     * Waits to see if a Tap gesture will be made when a previous Tap was recognized.
     * {@See
     * ContextualSearchInternalStateController#InternalState#WAITING_FOR_POSSIBLE_TAP_NEAR_PREVIOUS}
     */
    void waitForPossibleTapNearPrevious();

    /**
     * Starts a Resolve request to our server for the best Search Term.
     * {@See ContextualSearchInternalStateController#InternalState#RESOLVING}.
     */
    void resolveSearchTerm();
}

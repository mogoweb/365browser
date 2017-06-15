// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.widget.bottomsheet;

import org.chromium.chrome.browser.widget.bottomsheet.BottomSheet.BottomSheetContent;
import org.chromium.chrome.browser.widget.bottomsheet.BottomSheet.SheetState;

/**
 * An interface for notifications about the state of the bottom sheet.
 */
public interface BottomSheetObserver {
    /**
     * A notification that the sheet has been opened, meaning the sheet is any height greater
     * than its peeking state.
     */
    void onSheetOpened();

    /**
     * A notification that the sheet has closed, meaning the sheet has reached its peeking state.
     */
    void onSheetClosed();

    /**
     * A notification that the bottom sheet is no longer being dragged by the user and is instead
     * animating to its final state.
     */
    void onSheetReleased();

    /**
     * A notification that the sheet has begun loading a URL.
     *
     * @param url The URL being loaded.
     */
    void onLoadUrl(String url);

    /**
     * An event for when the sheet's offset from the bottom of the screen changes.
     *
     * @param heightFraction The fraction of the way to the fully expanded state that the sheet
     *                       is. This will be 0.0f when the sheet is peeking and 1.0f when the
     *                       sheet is completely expanded.
     */
    void onSheetOffsetChanged(float heightFraction);

    /**
     * A notification that the dimensions of the sheet has changed. The container height will be
     * different from the window height when the keyboard is showing.
     * @param windowHeight The height of the window in px.
     * @param containerHeight The height of the bottom sheet's container in px.
     */
    void onSheetLayout(int windowHeight, int containerHeight);

    /**
     * An event for when the sheet is transitioning from the peeking state to the half expanded
     * state. Once the sheet is outside the peek-half range, this event will no longer be
     * called. This event is guaranteed to be called at least once with 0.0f in the peeking state
     * and 1.0f at or past the half state. This means if the sheet is set to the full state from
     * the peeking state, this event will be called a single time with 1.0f.
     *
     * @param transitionFraction The fraction of the way to the half expanded state that the
     *                           sheet is. This will be 0.0f when the sheet is peeking and 1.0f
     *                           when the sheet is half expanded.
     */
    void onTransitionPeekToHalf(float transitionFraction);

    /**
     * An event for when the sheet changes state.
     * @param newState The new sheet state. See {@link SheetState}.
     */
    void onSheetStateChanged(@SheetState int newState);

    /**
     * An event for when the sheet content changes.
     * @param newContent The new {@link BottomSheetContent}.
     */
    void onSheetContentChanged(BottomSheetContent newContent);
}
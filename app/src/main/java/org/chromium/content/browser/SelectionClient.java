// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

/**
 * Interface to a content layer client that can process and modify selection text.
 */
public interface SelectionClient {
    /**
     * Notification that the web content selection has changed, regardless of the causal action.
     * @param selection The newly established selection.
     */
    void onSelectionChanged(String selection);

    /**
     * Notification that a user-triggered selection or insertion-related event has occurred.
     * @param eventType The selection event type, see {@link SelectionEventType}.
     * @param posXPix The x coordinate of the selection start handle.
     * @param posYPix The y coordinate of the selection start handle.
     */
    void onSelectionEvent(int eventType, float posXPix, float posYPix);

    /**
     * Requests to show the UI for an unhandled tap, if needed.
     * @param x The x coordinate of the tap.
     * @param y The y coordinate of the tap.
     */
    void showUnhandledTapUIIfNeeded(int x, int y);

    /**
     * Acknowledges that a selectWordAroundCaret action has completed with the given result.
     * @param didSelect Whether a word was actually selected or not.
     * @param startAdjust The adjustment to the selection start offset needed to select the word.
     *        This is typically a negative number (expressed in terms of number of characters).
     * @param endAdjust The adjustment to the selection end offset needed to select the word.
     *        This is typically a positive number (expressed in terms of number of characters).
     */
    void selectWordAroundCaretAck(boolean didSelect, int startAdjust, int endAdjust);

    /**
     * Notifies the SelectionClient that the selection menu has been requested.
     * @param shouldSuggest Whether SelectionClient should suggest and classify or just classify.
     * @return True if embedder should wait for a response before showing selection menu.
     */
    boolean requestSelectionPopupUpdates(boolean shouldSuggest);

    /**
     * Cancel any outstanding requests the embedder had previously requested using
     * SelectionClient.requestSelectionPopupUpdates().
     */
    public void cancelAllRequests();

    /**
     * Sets TextClassifier for the Smart Text selection. Pass null argument to use the system
     * classifier
     */
    public void setTextClassifier(Object textClassifier);

    /**
     * Gets TextClassifier that is used for the Smart Text selection. If the custom classifier
     * has been set with setTextClassifier, returns that object, otherwise returns the system
     * classifier.
     */
    public Object getTextClassifier();

    /**
     * Returns the TextClassifier which has been set with setTextClassifier(), or null.
     */
    public Object getCustomTextClassifier();
}

// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.bottombar.contextualsearch;

import org.chromium.chrome.browser.compositor.bottombar.contextualsearch.ContextualSearchPanel.PanelState;
import org.chromium.chrome.browser.compositor.bottombar.contextualsearch.ContextualSearchPanel.StateChangeReason;
import org.chromium.components.navigation_interception.InterceptNavigationDelegate;
import org.chromium.components.web_contents_delegate_android.WebContentsDelegateAndroid;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.content_public.browser.WebContents;

/**
 * The delegate that that interfaces with the {@link ContextualSearchPanel}.
 */
public interface ContextualSearchPanelDelegate {
    /**
     * @return Whether the Panel is in fullscreen size.
     */
    boolean isFullscreenSizePanel();

    /**
     * @return Whether the Panel is showing.
     */
    boolean isShowing();

    /**
     * @return Whether the Search Bar is peeking.
     */
    boolean isPeeking();

    /**
     * @return The width of the Contextual Search Panel in pixels.
     */
    int getMaximumWidthPx();

    /**
     * @return The height of the Contextual Search Panel in pixels.
     */
    int getMaximumHeightPx();

    /**
     * @return The width of the Search Content View in pixels.
     */
    int getSearchContentViewWidthPx();

    /**
     * @return The height of the Search Content View in pixels.
     */
    int getSearchContentViewHeightPx();

    /**
     * Maximizes the Contextual Search Panel, then promotes it to a regular Tab.
     * @param reason The {@code StateChangeReason} behind the maximization and promotion to tab.
     */
    void maximizePanelThenPromoteToTab(StateChangeReason reason);

    /**
     * Maximizes the Contextual Search Panel, then promotes it to a regular Tab.
     * @param reason The {@code StateChangeReason} behind the maximization and promotion to tab.
     * @param duration The animation duration in milliseconds.
     */
    void maximizePanelThenPromoteToTab(StateChangeReason reason, long duration);

    /**
     * Animates the Contextual Search Panel to its peeked state.
     * @param reason The {@code StateChangeReason} to which closing the panel is attributed.
     */
    void peekPanel(StateChangeReason reason);

    /**
     * Animates the Contextual Search Panel to its closed state.
     * @param reason The {@code StateChangeReason} to which closing the panel is attributed.
     * @param animate Whether to animate the close action.
     */
    void closePanel(StateChangeReason reason, boolean animate);

    /**
     * Updates the y coordinate of the Base Page's selection start position.
     * @param y The y coordinate.
     */
    void updateBasePageSelectionYPx(float y);

    /**
     * Handles the onLoadStarted event in the WebContents.
     */
    void onLoadStarted();

    /**
     * Handles the onLoadStopped event in the WebContents.
     */
    void onLoadStopped();

    /**
     * Handles the onLoadProgressChanged event in the WebContents.
     * @param progress The loading progress in percentage (from 0 to 100).
     */
    void onLoadProgressChanged(int progress);

    /**
     * @return The panel's state.
     */
    PanelState getPanelState();

    /**
     * Sets that the contextual search involved the promo.
     */
    void setDidSearchInvolvePromo();

    /**
     * Sets that the Search Content View was seen.
     */
    void setWasSearchContentViewSeen();

    /**
     * Sets whether the promo is active.
     * @param shown Whether the promo is active.
     */
    void setIsPromoActive(boolean shown);

    /**
     * Gets whether a touch on the search content view has been done yet or not.
     */
    boolean didTouchSearchContentView();

    /**
     * Called when the SERP finishes loading, this records the duration of loading the SERP from
     * the time the panel was opened until the present.
     * @param wasPrefetch Whether the request was prefetch-enabled.
     */
    void onSearchResultsLoaded(boolean wasPrefetch);

    /**
     * @return ContextualSearchControl The Android View that renders the BottomBar text.
     */
    ContextualSearchControl getContextualSearchControl();

    /**
     * @return {@code true} Whether the close animation should run when the the panel is closed
     *                      due the panel being promoted to a tab.
     */
    boolean shouldAnimatePanelCloseOnPromoteToTab();

    /**
     * @return The ContentViewCore associated with the panel.
     */
    ContentViewCore getContentViewCore();

    /**
     * Set this panel's ContentViewCore to null.
     */
    void resetContentViewCore();

    /**
     * Destroy the native components of this class.
     */
    void destroy();

    /**
     * Remove the last entry from history provided it is in a given time frame.
     * @param historyUrl The URL to remove.
     * @param urlTimeMs The time that the URL was visited.
     */
    void removeLastHistoryEntry(String historyUrl, long urlTimeMs);

    /**
     * Set the WebContents
     */
    void setWebContents(ContentViewCore contentView, WebContentsDelegateAndroid delegate);

    /**
     * Destroy the web contents associated with this object.
     */
    void destroyWebContents();

    /**
     * Reset the native handle to the WebContents.
     */
    void releaseWebContents();

    /**
     * @param delegate InterceptNavigationDelegate to use.
     * @param webContents The WebContents to associate with the delegate.
     */
    void setInterceptNavigationDelegate(
            InterceptNavigationDelegate delegate, WebContents webContents);
}

// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.contextualsearch;

import org.chromium.chrome.browser.compositor.bottombar.contextualsearch.ContextualSearchPanel.StateChangeReason;
import org.chromium.chrome.browser.compositor.bottombar.contextualsearch.ContextualSearchPanelDelegate;
import org.chromium.chrome.browser.customtabs.CustomTab;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.content_public.common.TopControlsState;

/**
 * The delegate that provides global management functionality for Contextual Search.
 */
public interface ContextualSearchManagementDelegate {
    /**
     * @return Whether the Search Panel is showing.
     */
    boolean isShowingSearchPanel();

    /**
     * Sets the preference state to enabled or disabled.
     *
     * @param enabled Whether the preference should be set to enabled.
     */
    void setPreferenceState(boolean enabled);

    /**
     * @return Whether the Opt-out promo is available to be be shown in the panel.
     */
    boolean isPromoAvailable();

    /**
     * Called when the promo Panel gets closed, to log the outcome.
     */
    void logPromoOutcome();

    /**
     * Updates the top controls state for the base tab.  As these values are set at the renderer
     * level, there is potential for this impacting other tabs that might share the same
     * process. See {@link Tab#updateTopControlsState(int current, boolean animate)}
     * @param current The desired current state for the controls.  Pass
     *                {@link TopControlsState#BOTH} to preserve the current position.
     * @param animate Whether the controls should animate to the specified ending condition or
     *                should jump immediately.
     */
    void updateTopControlsState(int current, boolean animate);

    /**
     * Promotes the current Content View Core in the Contextual Search Panel to its own Tab.
     */
    void promoteToTab();

    /**
     * Resets the Search Content View scroll position.
     */
    void resetSearchContentViewScroll();

    /**
     * Gets the Search Content View's vertical scroll position. If the Search Content View
     * is not available it returns -1.
     * @return The Search Content View scroll position.
     */
    float getSearchContentViewVerticalScroll();

    /**
     * Sets the visibility of the Search Content View.
     * TODO(pedrosimonetti): Revisit this API. Consumers should not be allowed to make
     * it invisible, only visible.
     * @param isVisible True to make it visible.
     */
    void setSearchContentViewVisibility(boolean isVisible);

    /**
     * Sets the delegate responsible for manipulating the ContextualSearchLayout.
     * @param delegate The ContextualSearchLayoutDelegate.
     */
    void setContextualSearchPanelDelegate(ContextualSearchPanelDelegate delegate);

    /**
     * Gets whether the device is running in compatibility mode for Contextual Search.
     * If so, a new tab showing search results should be opened instead of showing the panel.
     * @return whether the device is running in compatibility mode.
     */
    boolean isRunningInCompatibilityMode();

    /**
     * Opens the resolved search URL in a new tab.
     */
    void openResolvedSearchUrlInNewTab();

    /**
     * Preserves the Base Page's selection next time it loses focus.
     */
    void preserveBasePageSelectionOnNextLossOfFocus();

    /**
     * Dismisses the Contextual Search bar completely.  This will hide any panel that's currently
     * showing as well as any bar that's peeking.
     */
    void dismissContextualSearchBar();

    /**
     * Notifies that the Contextual Search Panel did get closed.
     * @param reason The reason the panel is closing.
     */
    void onCloseContextualSearch(StateChangeReason reason);

    /**
     * Gets the {@code ContentViewCore} associated with Contextual Search Panel.
     * @return Contextual Search Panel's {@code ContentViewCore}.
     */
    ContentViewCore getSearchContentViewCore();

    /**
     * @return The resource id that contains how large the top controls are.
     */
    int getControlContainerHeightResource();

    /**
     * @return Whether the current activity contains a {@link CustomTab}.
     */
    boolean isCustomTab();
}

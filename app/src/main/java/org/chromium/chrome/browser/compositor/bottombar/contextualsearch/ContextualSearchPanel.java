// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.bottombar.contextualsearch;

import android.content.Context;
import android.os.Handler;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.browser.compositor.layouts.LayoutUpdateHost;
import org.chromium.components.navigation_interception.InterceptNavigationDelegate;
import org.chromium.components.web_contents_delegate_android.WebContentsDelegateAndroid;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.content_public.browser.WebContents;

/**
 * Controls the Contextual Search Panel.
 */
public class ContextualSearchPanel extends ContextualSearchPanelAnimation
        implements ContextualSearchPanelDelegate {

    /**
     * State of the Contextual Search Panel.
     */
    public static enum PanelState {
        UNDEFINED,
        CLOSED,
        PEEKED,
        EXPANDED,
        MAXIMIZED;
    }

    /**
     * The reason for a change in the Contextual Search Panel's state.
     */
    public static enum StateChangeReason {
        UNKNOWN,
        RESET,
        BACK_PRESS,
        TEXT_SELECT_TAP,
        TEXT_SELECT_LONG_PRESS,
        INVALID_SELECTION,
        CLEARED_SELECTION,
        BASE_PAGE_TAP,
        BASE_PAGE_SCROLL,
        SEARCH_BAR_TAP,
        SERP_NAVIGATION,
        TAB_PROMOTION,
        CLICK,
        SWIPE,
        FLING,
        OPTIN,
        OPTOUT,
        CLOSE_BUTTON;
    }

    /**
     * The ContentViewCore that this panel will display.
     */
    private ContentViewCore mContentViewCore;

    /**
     * The pointer to the native version of this class.
     */
    private long mNativeContextualSearchPanelPtr;

    /**
     * The delay after which the hide progress will be hidden.
     */
    private static final long HIDE_PROGRESS_BAR_DELAY = 1000 / 60 * 4;

    /**
     * The initial height of the Contextual Search Panel.
     */
    private float mInitialPanelHeight;

    /**
     * Whether the Panel should be promoted to a new tab after being maximized.
     */
    private boolean mShouldPromoteToTabAfterMaximizing;

    /**
     * Whether a touch gesture has been detected.
     */
    private boolean mHasDetectedTouchGesture;

    /**
     * Whether the search content view has been touched.
     */
    private boolean mHasSearchContentViewBeenTouched;

    /**
     * The {@link ContextualSearchPanelHost} used to communicate with the supported layout.
     */
    private ContextualSearchPanelHost mSearchPanelHost;

    // ============================================================================================
    // Constructor
    // ============================================================================================

    /**
     * @param context The current Android {@link Context}.
     * @param updateHost The {@link LayoutUpdateHost} used to request updates in the Layout.
     */
    public ContextualSearchPanel(Context context, LayoutUpdateHost updateHost) {
        super(context, updateHost);
        nativeInit();
    }

    // ============================================================================================
    // Layout Integration
    // ============================================================================================

    /**
     * Sets the {@ContextualSearchPanelHost} used to communicate with the supported layout.
     * @param host The {@ContextualSearchPanelHost}.
     */
    public void setHost(ContextualSearchPanelHost host) {
        mSearchPanelHost = host;
    }

    // ============================================================================================
    // Contextual Search Manager Integration
    // ============================================================================================

    /**
     * Sets the visibility of the Search Content View.
     * @param isVisible True to make it visible.
     */
    public void setSearchContentViewVisibility(boolean isVisible) {
        if (getManagementDelegate() != null) {
            getManagementDelegate().setSearchContentViewVisibility(isVisible);
        }
    }

    @Override
    public void setPreferenceState(boolean enabled) {
        if (getManagementDelegate() != null) {
            getManagementDelegate().setPreferenceState(enabled);
        }
    }

    @Override
    protected boolean isPromoAvailable() {
        return getManagementDelegate() != null && getManagementDelegate().isPromoAvailable();
    }

    @Override
    public void onPromoButtonClick(boolean accepted) {
        super.onPromoButtonClick(accepted);
        getManagementDelegate().logPromoOutcome();
        setIsPromoActive(false);
    }

    @Override
    protected void onClose(StateChangeReason reason) {
        getManagementDelegate().onCloseContextualSearch(reason);
    }

    // ============================================================================================
    // Generic Event Handling
    // ============================================================================================

    /**
     * Handles the beginning of the swipe gesture.
     */
    public void handleSwipeStart() {
        if (animationIsRunning()) {
            cancelHeightAnimation();
        }

        mHasDetectedTouchGesture = false;
        mInitialPanelHeight = getHeight();
    }

    /**
     * Handles the movement of the swipe gesture.
     *
     * @param ty The movement's total displacement in dps.
     */
    public void handleSwipeMove(float ty) {
        if (ty > 0 && getPanelState() == PanelState.MAXIMIZED) {
            // Resets the Search Content View scroll position when swiping the Panel down
            // after being maximized.
            getManagementDelegate().resetSearchContentViewScroll();
        }

        // Negative ty value means an upward movement so subtracting ty means expanding the panel.
        setClampedPanelHeight(mInitialPanelHeight - ty);
        requestUpdate();
    }

    /**
     * Handles the end of the swipe gesture.
     */
    public void handleSwipeEnd() {
        // This method will be called after handleFling() and handleClick()
        // methods because we also need to track down the onUpOrCancel()
        // action from the Layout. Therefore the animation to the nearest
        // PanelState should only happen when no other gesture has been
        // detected.
        if (!mHasDetectedTouchGesture) {
            mHasDetectedTouchGesture = true;
            animateToNearestState();
        }
    }

    /**
     * Handles the fling gesture.
     *
     * @param velocity The velocity of the gesture in dps per second.
     */
    public void handleFling(float velocity) {
        mHasDetectedTouchGesture = true;
        animateToProjectedState(velocity);
    }

    /**
     * Handles the click gesture.
     *
     * @param time The timestamp of the gesture.
     * @param x The x coordinate of the gesture.
     * @param y The y coordinate of the gesture.
     */
    public void handleClick(long time, float x, float y) {
        mHasDetectedTouchGesture = true;
        if (isCoordinateInsideBasePage(x, y)) {
            closePanel(StateChangeReason.BASE_PAGE_TAP, true);
        } else if (isCoordinateInsideSearchBar(x, y)) {
            if (isPeeking()) {
                if (getManagementDelegate().isRunningInCompatibilityMode()) {
                    getManagementDelegate().openResolvedSearchUrlInNewTab();
                } else {
                    if (isFullscreenSizePanel()) {
                        expandPanel(StateChangeReason.SEARCH_BAR_TAP);
                    } else {
                        maximizePanel(StateChangeReason.SEARCH_BAR_TAP);
                    }
                }
            } else if (isExpanded()) {
                peekPanel(StateChangeReason.SEARCH_BAR_TAP);
            } else if (isMaximized()) {
                if (mSearchPanelFeatures.isSearchTermRefiningAvailable()) {
                    getManagementDelegate().promoteToTab();
                }
                if (mSearchPanelFeatures.isCloseButtonAvailable()
                        && isCoordinateInsideCloseButton(x, y)) {
                    closePanel(StateChangeReason.CLOSE_BUTTON, true);
                }
            }
        }
    }

    // ============================================================================================
    // Gesture Event helpers
    // ============================================================================================

    /**
     * @param x The x coordinate in dp.
     * @param y The y coordinate in dp.
     * @return Whether the given coordinate is inside the Search Panel area.
     */
    private boolean isCoordinateInsideSearchPanel(float x, float y) {
        return y >= getOffsetY() && y <= (getOffsetY() + getHeight())
                &&  x >= getOffsetX() && x <= (getOffsetX() + getWidth());
    }

    /**
     * @param x The x coordinate in dp.
     * @param y The y coordinate in dp.
     * @return Whether the given coordinate is inside the Base Page area.
     */
    private boolean isCoordinateInsideBasePage(float x, float y) {
        return !isCoordinateInsideSearchPanel(x, y);
    }

    /**
     * @param x The x coordinate in dp.
     * @param y The y coordinate in dp.
     * @return Whether the given coordinate is inside the Search Bar area.
     */
    public boolean isCoordinateInsideSearchBar(float x, float y) {
        return isCoordinateInsideSearchPanel(x, y)
                && y >= getOffsetY() && y <= (getOffsetY() + getSearchBarHeight());
    }

    /**
     * @param x The x coordinate in dp.
     * @param y The y coordinate in dp.
     * @return Whether the given coordinate is inside the Search Content View area.
     */
    public boolean isCoordinateInsideSearchContentView(float x, float y) {
        return isCoordinateInsideSearchPanel(x, y)
                && y > getSearchContentViewOffsetY();
    }

    /**
     * @return The horizontal offset of the Search Content View in dp.
     */
    public float getSearchContentViewOffsetX() {
        return getOffsetX();
    }

    /**
     * @return The vertical offset of the Search Content View in dp.
     */
    public float getSearchContentViewOffsetY() {
        return getOffsetY() + getSearchBarHeight() + getPromoHeight();
    }

    /**
     * @param x The x coordinate in dp.
     * @param y The y coordinate in dp.
     * @return Whether the given |x| |y| coordinate is inside the close button.
     */
    private boolean isCoordinateInsideCloseButton(float x, float y) {
        boolean isInY = y >= getCloseIconY() && y <= (getCloseIconY() + getCloseIconDimension());
        boolean isInX = x >= getCloseIconX() && x <= (getCloseIconX() + getCloseIconDimension());
        return isInY && isInX;
    }

    /**
     * @return Whether the Panel is in its expanded state.
     */
    protected boolean isExpanded() {
        return doesPanelHeightMatchState(PanelState.EXPANDED);
    }

    /**
     * Acknowledges that there was a touch in the search content view, though no immediate action
     * needs to be taken.
     */
    public void onTouchSearchContentViewAck() {
        mHasSearchContentViewBeenTouched = true;
    }

    // ============================================================================================
    // Animation Handling
    // ============================================================================================

    @Override
    protected void onAnimationFinished() {
        super.onAnimationFinished();

        if (shouldHideContextualSearchLayout()) {
            if (mSearchPanelHost != null) {
                mSearchPanelHost.hideLayout(false);
            }
        }

        if (mShouldPromoteToTabAfterMaximizing && getPanelState() == PanelState.MAXIMIZED) {
            mShouldPromoteToTabAfterMaximizing = false;
            getManagementDelegate().promoteToTab();
        }
    }

    /**
    * Whether the Contextual Search Layout should be hidden.
    *
    * @return Whether the Contextual Search Layout should be hidden.
    */
    private boolean shouldHideContextualSearchLayout() {
        final PanelState state = getPanelState();

        return (state == PanelState.PEEKED || state == PanelState.CLOSED)
                && getHeight() == getPanelHeightFromState(state);
    }

    // ============================================================================================
    // Panel Delegate
    // ============================================================================================

    @Override
    public boolean isFullscreenSizePanel() {
        // NOTE(pedrosimonetti): exposing superclass method to the interface.
        return super.isFullscreenSizePanel();
    }

    @Override
    public boolean isShowing() {
        // NOTE(pedrosimonetti): exposing superclass method to the interface.
        return super.isShowing();
    }

    @Override
    public boolean isPeeking() {
        return doesPanelHeightMatchState(PanelState.PEEKED);
    }

    @Override
    public int getMaximumWidthPx() {
        // NOTE(pedrosimonetti): exposing superclass method to the interface.
        return super.getMaximumWidthPx();
    }

    @Override
    public int getMaximumHeightPx() {
        // NOTE(pedrosimonetti): exposing superclass method to the interface.
        return super.getMaximumHeightPx();
    }

    @Override
    public int getSearchContentViewWidthPx() {
        // NOTE(pedrosimonetti): exposing superclass method to the interface.
        return super.getSearchContentViewWidthPx();
    }

    @Override
    public int getSearchContentViewHeightPx() {
        // NOTE(pedrosimonetti): exposing superclass method to the interface.
        return super.getSearchContentViewHeightPx();
    }

    @Override
    public void maximizePanelThenPromoteToTab(StateChangeReason reason) {
        mShouldPromoteToTabAfterMaximizing = true;
        maximizePanel(reason);
    }

    @Override
    public void maximizePanelThenPromoteToTab(StateChangeReason reason, long duration) {
        mShouldPromoteToTabAfterMaximizing = true;
        animatePanelToState(PanelState.MAXIMIZED, reason, duration);
    }

    @Override
    public void peekPanel(StateChangeReason reason) {
        // NOTE(pedrosimonetti): exposing superclass method to the interface.
        super.peekPanel(reason);

        if (getPanelState() == PanelState.CLOSED || getPanelState() == PanelState.PEEKED) {
            mHasSearchContentViewBeenTouched = false;
        }
    }

    @Override
    public void closePanel(StateChangeReason reason, boolean animate) {
        // If the close action is animated, the Layout will be hidden when
        // the animation is finished, so we should only hide the Layout
        // here when not animating.
        if (!animate && mSearchPanelHost != null) {
            mSearchPanelHost.hideLayout(true);
        }
        mHasSearchContentViewBeenTouched = false;

        super.closePanel(reason, animate);
    }

    @Override
    public void updateBasePageSelectionYPx(float y) {
        // NOTE(pedrosimonetti): exposing superclass method to the interface.
        super.updateBasePageSelectionYPx(y);
    }

    @Override
    public void onLoadStarted() {
        setProgressBarCompletion(0);
        setProgressBarVisible(true);
        requestUpdate();
    }

    @Override
    public void onLoadStopped() {
        // Hides the Progress Bar after a delay to make sure it is rendered for at least
        // a few frames, otherwise its completion won't be visually noticeable.
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                setProgressBarVisible(false);
                requestUpdate();
            }
        }, HIDE_PROGRESS_BAR_DELAY);
    }

    @Override
    public void onLoadProgressChanged(int progress) {
        setProgressBarCompletion(progress);
        requestUpdate();
    }

    @Override
    public PanelState getPanelState() {
        // NOTE(pedrosimonetti): exposing superclass method to the interface.
        return super.getPanelState();
    }

    @Override
    public void setDidSearchInvolvePromo() {
        // NOTE(pedrosimonetti): exposing superclass method to the interface.
        super.setDidSearchInvolvePromo();
    }

    @Override
    public void setWasSearchContentViewSeen() {
        // NOTE(pedrosimonetti): exposing superclass method to the interface.
        super.setWasSearchContentViewSeen();
    }

    @Override
    public void setIsPromoActive(boolean shown) {
        // NOTE(pedrosimonetti): exposing superclass method to the interface.
        super.setIsPromoActive(shown);
    }

    @Override
    public boolean didTouchSearchContentView() {
        return mHasSearchContentViewBeenTouched;
    }

    @Override
    public void onSearchResultsLoaded(boolean wasPrefetch) {
        // NOTE(pedrosimonetti): exposing superclass method to the interface.
        super.onSearchResultsLoaded(wasPrefetch);
    }

    @Override
    public ContextualSearchControl getContextualSearchControl() {
        // NOTE(pedrosimonetti): exposing superclass method to the interface.
        return super.getContextualSearchControl();
    }

    @Override
    public boolean shouldAnimatePanelCloseOnPromoteToTab() {
        return mSearchPanelFeatures.shouldAnimatePanelCloseOnPromoteToTab();
    }

    // ============================================================================================
    // Methods for managing this panel's ContentViewCore.
    // ============================================================================================

    @CalledByNative
    public void clearNativePanelContentPtr() {
        assert mNativeContextualSearchPanelPtr != 0;
        mNativeContextualSearchPanelPtr = 0;
    }

    @CalledByNative
    public void setNativePanelContentPtr(long nativePtr) {
        assert mNativeContextualSearchPanelPtr == 0;
        mNativeContextualSearchPanelPtr = nativePtr;
    }

    @Override
    public ContentViewCore getContentViewCore() {
        return mContentViewCore;
    }

    public void resetContentViewCore() {
        // TODO(mdjones): Merge all code related to deleting/resetting the ContentViewCore.
        mContentViewCore = null;
    }

    @Override
    public void destroy() {
        nativeDestroy(mNativeContextualSearchPanelPtr);
    }

    @Override
    public void removeLastHistoryEntry(String historyUrl, long urlTimeMs) {
        nativeRemoveLastHistoryEntry(mNativeContextualSearchPanelPtr, historyUrl, urlTimeMs);
    }

    @Override
    public void setWebContents(ContentViewCore contentView, WebContentsDelegateAndroid delegate) {
        mContentViewCore = contentView;
        nativeSetWebContents(mNativeContextualSearchPanelPtr, contentView, delegate);
    }

    @Override
    public void destroyWebContents() {
        nativeDestroyWebContents(mNativeContextualSearchPanelPtr);
    }

    @Override
    public void releaseWebContents() {
        nativeReleaseWebContents(mNativeContextualSearchPanelPtr);
    }

    @Override
    public void setInterceptNavigationDelegate(
            InterceptNavigationDelegate delegate, WebContents webContents) {
        nativeSetInterceptNavigationDelegate(mNativeContextualSearchPanelPtr,
                delegate, webContents);
    }

    // Native calls.
    protected native long nativeInit();
    private native void nativeDestroy(long nativeContextualSearchPanel);
    private native void nativeRemoveLastHistoryEntry(
            long nativeContextualSearchPanel, String historyUrl, long urlTimeMs);
    private native void nativeSetWebContents(long nativeContextualSearchPanel,
            ContentViewCore contentViewCore, WebContentsDelegateAndroid delegate);
    private native void nativeDestroyWebContents(long nativeContextualSearchPanel);
    private native void nativeReleaseWebContents(long nativeContextualSearchPanel);
    private native void nativeSetInterceptNavigationDelegate(long nativeContextualSearchPanel,
            InterceptNavigationDelegate delegate, WebContents webContents);
}

// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.bottombar.contextualsearch;

import android.content.Context;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.compositor.bottombar.contextualsearch.ContextualSearchOptOutPromo.ContextualSearchPromoHost;
import org.chromium.chrome.browser.compositor.bottombar.contextualsearch.ContextualSearchPanel.PanelState;
import org.chromium.chrome.browser.compositor.bottombar.contextualsearch.ContextualSearchPanel.StateChangeReason;
import org.chromium.chrome.browser.contextualsearch.ContextualSearchFieldTrial;
import org.chromium.chrome.browser.contextualsearch.ContextualSearchManagementDelegate;
import org.chromium.chrome.browser.preferences.PreferencesLauncher;
import org.chromium.chrome.browser.preferences.privacy.ContextualSearchPreferenceFragment;
import org.chromium.chrome.browser.util.MathUtils;
import org.chromium.ui.base.LocalizationUtils;
import org.chromium.ui.resources.dynamics.DynamicResourceLoader;

/**
 * Base abstract class for the Contextual Search Panel.
 */
abstract class ContextualSearchPanelBase extends ContextualSearchPanelStateHandler
        implements ContextualSearchPromoHost {
    /**
     * The side padding of Search Bar icons in dps.
     */
    private static final float SEARCH_BAR_ICON_SIDE_PADDING_DP = 16.f;

    /**
     * The height of the Search Bar's border in dps.
     */
    private static final float SEARCH_BAR_BORDER_HEIGHT_DP = 1.f;

    /**
     * The height of the expanded Search Panel relative to the height of the screen.
     */
    private static final float EXPANDED_PANEL_HEIGHT_PERCENTAGE = .7f;

    /**
     * The height of the expanded Search Panel relative to the height of the screen when
     * the panel is in the narrow width mode.
     */
    private static final float NARROW_EXPANDED_PANEL_HEIGHT_PERCENTAGE = .3f;

    /**
     * The height of the maximized Search Panel relative to the height of the screen when
     * the panel is in the narrow width mode.
     */
    private static final float NARROW_MAXIMIZED_PANEL_HEIGHT_PERCENTAGE = .9f;

    /**
     * The width of the small version of the Search Panel in dps.
     */
    private static final float SMALL_PANEL_WIDTH_DP = 600.f;

    /**
     * The minimum width a screen should have in order to trigger the small version of the Panel.
     */
    private static final float SMALL_PANEL_WIDTH_THRESHOLD_DP = 620.f;

    /**
     * The height of the Contextual Search Panel's Shadow in dps.
     */
    private static final float PANEL_SHADOW_HEIGHT_DP = 16.f;

    /**
     * The brightness of the base page when the Panel is peeking.
     */
    private static final float BASE_PAGE_BRIGHTNESS_STATE_PEEKED = 1.f;

    /**
     * The brightness of the base page when the Panel is expanded.
     */
    private static final float BASE_PAGE_BRIGHTNESS_STATE_EXPANDED = .7f;

    /**
     * The brightness of the base page when the Panel is maximized. This value matches the alert
     * dialog brightness filter.
     */
    private static final float BASE_PAGE_BRIGHTNESS_STATE_MAXIMIZED = .4f;

    /**
     * The opacity of the search icon when the Panel is peeking.
     */
    private static final float SEARCH_ICON_OPACITY_STATE_PEEKED = 0.f;

    /**
     * The opacity of the search icon when the Panel is expanded.
     */
    private static final float SEARCH_ICON_OPACITY_STATE_EXPANDED = 0.f;

    /**
     * The opacity of the search icon when the Panel is maximized.
     */
    private static final float SEARCH_ICON_OPACITY_STATE_MAXIMIZED = 1.f;

    /**
     * The opacity of the arrow icon when the Panel is peeking.
     */
    private static final float ARROW_ICON_OPACITY_STATE_PEEKED = 1.f;

    /**
     * The opacity of the arrow icon when the Panel is expanded.
     */
    private static final float ARROW_ICON_OPACITY_STATE_EXPANDED = 1.f;

    /**
     * The opacity of the arrow icon when the Panel is maximized.
     */
    private static final float ARROW_ICON_OPACITY_STATE_MAXIMIZED = 0.f;

    /**
     * The rotation of the arrow icon when the Panel is peeking.
     */
    private static final float ARROW_ICON_ROTATION_STATE_PEEKED = -90.f;

    /**
     * The rotation of the arrow icon when the Panel is expanded.
     */
    private static final float ARROW_ICON_ROTATION_STATE_EXPANDED = -270.f;

    /**
     * The opacity of the close icon when the Panel is peeking.
     */
    private static final float CLOSE_ICON_OPACITY_STATE_PEEKED = 0.f;

    /**
     * The opacity of the close icon when the Panel is expanded.
     */
    private static final float CLOSE_ICON_OPACITY_STATE_EXPANDED = 0.f;

    /**
     * The opacity of the close icon when the Panel is maximized.
     */
    private static final float CLOSE_ICON_OPACITY_STATE_MAXIMIZED = 1.f;

    /**
     * The id of the close icon drawable.
     */
    public static final int CLOSE_ICON_DRAWABLE_ID = R.drawable.btn_close;

    /**
     * The height of the Progress Bar in dps.
     */
    private static final float PROGRESS_BAR_HEIGHT_DP = 2.f;

    /**
     * The distance from the Progress Bar must be away from the bottom of the
     * screen in order to be completely visible. The closer the Progress Bar
     * gets to the bottom of the screen, the lower its opacity will be. When the
     * Progress Bar is at the very bottom of the screen (when the Search Panel
     * is peeking) it will be completely invisible.
     */
    private static final float PROGRESS_BAR_VISIBILITY_THRESHOLD_DP = 10.f;

    /**
     * The height of the Toolbar in dps.
     */
    private float mToolbarHeight;

    /**
     * The padding top of the Search Bar.
     */
    private float mSearchBarPaddingTop;

    /**
     * The height of the Search Bar when the Panel is peeking, in dps.
     */
    private float mSearchBarHeightPeeking;

    /**
     * The height of the Search Bar when the Panel is expanded, in dps.
     */
    private float mSearchBarHeightExpanded;

    /**
     * The height of the Search Bar when the Panel is maximized, in dps.
     */
    private float mSearchBarHeightMaximized;

    /**
     * Ratio of dps per pixel.
     */
    private float mPxToDp;

    /**
     * The approximate Y coordinate of the selection in pixels.
     */
    private float mBasePageSelectionYPx = -1.f;

    /**
     * The Y coordinate to apply to the Base Page in order to keep the selection
     * in view when the Search Panel is in its EXPANDED state.
     */
    private float mBasePageTargetY = 0.f;

    /**
     * Whether the Panel is showing.
     */
    private boolean mIsShowing;

    /**
     * The current context.
     */
    private final Context mContext;

    /**
     * The object for handling global Contextual Search management duties
     */
    private ContextualSearchManagementDelegate mManagementDelegate;

    /**
     * The {@link ContextualSearchPanelFeatures} for this panel.
     */
    protected ContextualSearchPanelFeatures mSearchPanelFeatures;


    // ============================================================================================
    // Constructor
    // ============================================================================================

    /**
     * @param context The current Android {@link Context}.
     */
    public ContextualSearchPanelBase(Context context) {
        mContext = context;
    }

    // ============================================================================================
    // General API
    // ============================================================================================

    /**
     * Animates the Contextual Search Panel to its closed state.
     *
     * @param reason The reason for the change of panel state.
     */
    protected abstract void closePanel(StateChangeReason reason, boolean animate);

    /**
     * Sets Contextual Search's preference state.
     *
     * @param enabled Whether the preference should be enabled.
     */
    public abstract void setPreferenceState(boolean enabled);

    /**
     * @return Whether the Panel Promo is available.
     */
    protected abstract boolean isPromoAvailable();

    /**
     * Animates the acceptance of the Promo.
     */
    protected abstract void animatePromoAcceptance();

    /**
     * Event notification that the Panel did get closed.
     * @param reason The reason the panel is closing.
     */
    protected abstract void onClose(StateChangeReason reason);

    // ============================================================================================
    // Contextual Search Manager Integration
    // ============================================================================================

    /**
     * Sets the {@code ContextualSearchManagementDelegate} associated with this panel.
     * @param delegate The {@code ContextualSearchManagementDelegate}.
     */
    public void setManagementDelegate(ContextualSearchManagementDelegate delegate) {
        if (mManagementDelegate != delegate) {
            mManagementDelegate = delegate;
            if (delegate != null) {
                initializeUiState();
            }
        }
    }

    /**
     * @return The {@code ContextualSearchManagementDelegate} associated with this Layout.
     */
    public ContextualSearchManagementDelegate getManagementDelegate() {
        return mManagementDelegate;
    }

    // ============================================================================================
    // Layout Integration
    // ============================================================================================

    private float mLayoutWidth;
    private float mLayoutHeight;
    private boolean mIsToolbarShowing;

    private float mMaximumWidth;
    private float mMaximumHeight;

    private boolean mIsFullscreenSizePanelForTesting;
    private boolean mOverrideIsFullscreenSizePanelForTesting;

    /**
     * Called when the size of the view has changed.
     *
     * @param width  The new width in dp.
     * @param height The new width in dp.
     * @param isToolbarShowing Whether the Toolbar is showing.
     */
    public final void onSizeChanged(float width, float height, boolean isToolbarShowing) {
        mLayoutWidth = width;
        mLayoutHeight = height;
        mIsToolbarShowing = isToolbarShowing;

        mMaximumWidth = calculateSearchPanelWidth();
        mMaximumHeight = getPanelHeightFromState(PanelState.MAXIMIZED);
    }

    /**
     * Overrides the FullscreenSizePanel state for testing.
     *
     * @param isFullscreenSizePanel
     */
    @VisibleForTesting
    public void setIsFullscreenSizePanelForTesting(boolean isFullscreenSizePanel) {
        mOverrideIsFullscreenSizePanelForTesting = true;
        mIsFullscreenSizePanelForTesting = isFullscreenSizePanel;
    }

    /**
     * @return Whether the Panel is in fullscreen size.
     */
    protected boolean isFullscreenSizePanel() {
        if (mOverrideIsFullscreenSizePanelForTesting) {
            return mIsFullscreenSizePanelForTesting;
        }

        if (!ContextualSearchFieldTrial.isNarrowPanelSupported()) {
            return true;
        }

        return getFullscreenWidth() <= SMALL_PANEL_WIDTH_THRESHOLD_DP;
    }

    /**
     * @return The current X-position of the Contextual Search Panel.
     */
    protected float calculateSearchPanelX() {
        return isFullscreenSizePanel() ? 0.f :
            Math.round((getFullscreenWidth() - calculateSearchPanelWidth()) / 2.f);
    }

    /**
     * @return The current Y-position of the Contextual Search Panel.
     */
    protected float calculateSearchPanelY() {
        return getFullscreenHeight() - mHeight;
    }

    /**
     * @return The current width of the Contextual Search Panel.
     */
    protected float calculateSearchPanelWidth() {
        return isFullscreenSizePanel() ? getFullscreenWidth() : SMALL_PANEL_WIDTH_DP;
    }

    /**
     * @return The height of the Chrome toolbar in dp.
     */
    public float getToolbarHeight() {
        return mToolbarHeight;
    }

    /**
     * @param y The y coordinate.
     * @return The Y coordinate relative the fullscreen height.
     */
    public float getFullscreenY(float y) {
        if (mIsToolbarShowing) {
            y += mToolbarHeight / mPxToDp;
        }
        return y;
    }

    /**
     * @return Whether the Panel is showing.
     */
    public boolean isShowing() {
        return mIsShowing;
    }

    /**
     * Starts showing the Panel.
     */
    protected void startShowing() {
        mIsShowing = true;
    }

    /**
     * @return The fullscreen width.
     */
    private float getFullscreenWidth() {
        return mLayoutWidth;
    }

    /**
     * @return The fullscreen height.
     */
    private float getFullscreenHeight() {
        float height = mLayoutHeight;
        // NOTE(pedrosimonetti): getHeight() only returns the content height
        // when the Toolbar is not showing. If we don't add the Toolbar height
        // here, there will be a "jump" when swiping the Search Panel around.
        // TODO(pedrosimonetti): Find better way to get the fullscreen height.
        if (mIsToolbarShowing) {
            height += mToolbarHeight;
        }
        return height;
    }

    /**
     * @return The maximum width of the Contextual Search Panel in pixels.
     */
    public int getMaximumWidthPx() {
        return Math.round(mMaximumWidth / mPxToDp);
    }

    /**
     * @return The maximum height of the Contextual Search Panel in pixels.
     */
    public int getMaximumHeightPx() {
        return Math.round(mMaximumHeight / mPxToDp);
    }

    /**
     * @return The width of the Search Content View in pixels.
     */
    public int getSearchContentViewWidthPx() {
        return getMaximumWidthPx();
    }

    /**
     * @return The height of the Search Content View in pixels.
     */
    public int getSearchContentViewHeightPx() {
        float searchBarExpandedHeight = isFullscreenSizePanel()
                ? getToolbarHeight() : mSearchBarHeightPeeking;
        return Math.round((mMaximumHeight - searchBarExpandedHeight) / mPxToDp);
    }

    // ============================================================================================
    // UI States
    // ============================================================================================

    // --------------------------------------------------------------------------------------------
    // Test Infrastructure
    // --------------------------------------------------------------------------------------------

    /**
     * @param height The height of the Contextual Search Panel to be set.
     */
    @VisibleForTesting
    public void setHeightForTesting(float height) {
        mHeight = height;
    }

    /**
     * @param offsetY The vertical offset of the Contextual Search Panel to be
     *            set.
     */
    @VisibleForTesting
    public void setOffsetYForTesting(float offsetY) {
        mOffsetY = offsetY;
    }

    /**
     * @param isMaximized The setting for whether the Search Panel is fully
     *            maximized.
     */
    @VisibleForTesting
    public void setMaximizedForTesting(boolean isMaximized) {
        mIsMaximized = isMaximized;
    }

    /**
     * @param searchBarHeight The height of the Contextual Search Bar to be set.
     */
    @VisibleForTesting
    public void setSearchBarHeightForTesting(float searchBarHeight) {
        mSearchBarHeight = searchBarHeight;
    }

    /**
     * @param searchBarBorderHeight The height of the Search Bar border to be
     *            set.
     */
    @VisibleForTesting
    public void setSearchBarBorderHeight(float searchBarBorderHeight) {
        mSearchBarBorderHeight = searchBarBorderHeight;
    }

    // --------------------------------------------------------------------------------------------
    // Contextual Search Panel states
    // --------------------------------------------------------------------------------------------

    private float mOffsetX;
    private float mOffsetY;
    private float mHeight;
    private boolean mIsMaximized;

    /**
     * @return The vertical offset of the Contextual Search Panel.
     */
    public float getOffsetX() {
        return mOffsetX;
    }

    /**
     * @return The vertical offset of the Contextual Search Panel.
     */
    public float getOffsetY() {
        return mOffsetY;
    }

    /**
     * @return The width of the Contextual Search Panel in dps.
     */
    public float getWidth() {
        return mMaximumWidth;
    }

    /**
     * @return The height of the Contextual Search Panel in dps.
     */
    public float getHeight() {
        return mHeight;
    }

    /**
     * @return Whether the Search Panel is fully maximized.
     */
    public boolean isMaximized() {
        return mIsMaximized;
    }

    // --------------------------------------------------------------------------------------------
    // Contextual Search Bar states
    // --------------------------------------------------------------------------------------------
    private float mSearchBarMarginSide;
    private float mSearchBarHeight;
    private float mSearchBarTextOpacity;
    private boolean mIsSearchBarBorderVisible;
    private float mSearchBarBorderY;
    private float mSearchBarBorderHeight;

    private boolean mSearchBarShadowVisible = false;
    private float mSearchBarShadowOpacity = 0.f;

    private float mSearchIconOpacity;

    private float mArrowIconOpacity;
    private float mArrowIconRotation;

    private float mCloseIconOpacity;
    private float mCloseIconWidth;

    /**
     * @return The side margin of the Contextual Search Bar.
     */
    public float getSearchBarMarginSide() {
        return mSearchBarMarginSide;
    }

    /**
     * @return The height of the Contextual Search Bar.
     */
    public float getSearchBarHeight() {
        return mSearchBarHeight;
    }

    /**
     * @return The opacity of the Contextual Search Bar text.
     */
    public float getSearchBarTextOpacity() {
        return mSearchBarTextOpacity;
    }

    /**
     * @return Whether the Search Bar border is visible.
     */
    public boolean isSearchBarBorderVisible() {
        return mIsSearchBarBorderVisible;
    }

    /**
     * @return The Y coordinate of the Search Bar border.
     */
    public float getSearchBarBorderY() {
        return mSearchBarBorderY;
    }

    /**
     * @return The height of the Search Bar border.
     */
    public float getSearchBarBorderHeight() {
        return mSearchBarBorderHeight;
    }

    /**
     * @return Whether the Search Bar shadow is visible.
     */
    public boolean getSearchBarShadowVisible() {
        return mSearchBarShadowVisible;
    }

    /**
     * @return The opacity of the Search Bar shadow.
     */
    public float getSearchBarShadowOpacity() {
        return mSearchBarShadowOpacity;
    }

    /**
     * @return Whether the search icon is visible.
     */
    public boolean isSearchIconVisible() {
        return mSearchPanelFeatures.isSearchIconAvailable();
    }

    /**
     * @return The opacity of the search icon.
     */
    public float getSearchIconOpacity() {
        return mSearchIconOpacity;
    }

    /**
     * @return The opacity of the arrow icon.
     */
    public float getArrowIconOpacity() {
        return mArrowIconOpacity;
    }

    /**
     * @return The rotation of the arrow icon, in degrees.
     */
    public float getArrowIconRotation() {
        return mArrowIconRotation;
    }

    /**
     * @return Whether the close icon is visible.
     */
    public boolean isCloseIconVisible() {
        return mSearchPanelFeatures.isCloseButtonAvailable();
    }

    /**
     * @return The opacity of the close icon.
     */
    public float getCloseIconOpacity() {
        return mCloseIconOpacity;
    }

    /**
     * @return The width/height of the close icon.
     */
    public float getCloseIconDimension() {
        if (mCloseIconWidth == 0) {
            mCloseIconWidth = ApiCompatibilityUtils.getDrawable(mContext.getResources(),
                    CLOSE_ICON_DRAWABLE_ID).getIntrinsicWidth() * mPxToDp;
        }
        return mCloseIconWidth;
    }

    /**
     * @return The Y coordinate of the close icon.
     */
    public float getCloseIconY() {
        return getOffsetY() + ((getSearchBarHeight() - getCloseIconDimension()) / 2);
    }

    /**
     * @return The X coordinate of the close icon.
     */
    public float getCloseIconX() {
        if (LocalizationUtils.isLayoutRtl()) {
            return getOffsetX() + getSearchBarMarginSide();
        } else {
            return getOffsetX() + getWidth() - getSearchBarMarginSide() - getCloseIconDimension();
        }
    }

    // --------------------------------------------------------------------------------------------
    // Base Page states
    // --------------------------------------------------------------------------------------------

    private float mBasePageY;
    private float mBasePageBrightness;

    /**
     * @return The vertical offset of the base page.
     */
    public float getBasePageY() {
        return mBasePageY;
    }

    /**
     * @return The brightness of the base page.
     */
    public float getBasePageBrightness() {
        return mBasePageBrightness;
    }

    // --------------------------------------------------------------------------------------------
    // Progress Bar states
    // --------------------------------------------------------------------------------------------

    private float mProgressBarOpacity;
    private boolean mIsProgressBarVisible;
    private float mProgressBarY;
    private float mProgressBarHeight;
    private int mProgressBarCompletion;

    /**
     * @return Whether the Progress Bar is visible.
     */
    public boolean isProgressBarVisible() {
        return mIsProgressBarVisible;
    }

    /**
     * @param isVisible Whether the Progress Bar should be visible.
     */
    protected void setProgressBarVisible(boolean isVisible) {
        mIsProgressBarVisible = isVisible;
    }

    /**
     * @return The Y coordinate of the Progress Bar.
     */
    public float getProgressBarY() {
        return mProgressBarY;
    }

    /**
     * @return The Progress Bar height.
     */
    public float getProgressBarHeight() {
        return mProgressBarHeight;
    }

    /**
     * @return The Progress Bar opacity.
     */
    public float getProgressBarOpacity() {
        return mProgressBarOpacity;
    }

    /**
     * @return The completion percentage of the Progress Bar.
     */
    public int getProgressBarCompletion() {
        return mProgressBarCompletion;
    }

    /**
     * @param completion The completion percentage to be set.
     */
    protected void setProgressBarCompletion(int completion) {
        mProgressBarCompletion = completion;
    }

    // --------------------------------------------------------------------------------------------
    // Promo states
    // --------------------------------------------------------------------------------------------

    private boolean mPromoVisible = false;
    private float mPromoContentHeightPx = 0.f;
    private float mPromoHeightPx;
    private float mPromoOpacity;

    /**
     * @return Whether the promo is visible.
     */
    public boolean getPromoVisible() {
        return mPromoVisible;
    }

    /**
     * Sets the height of the promo content.
     */
    public void setPromoContentHeightPx(float heightPx) {
        mPromoContentHeightPx = heightPx;
    }

    /**
     * @return Height of the promo in dps.
     */
    public float getPromoHeight() {
        return mPromoHeightPx * mPxToDp;
    }

    /**
     * @return Height of the promo in pixels.
     */
    public float getPromoHeightPx() {
        return mPromoHeightPx;
    }

    /**
     * @return The opacity of the promo.
     */
    public float getPromoOpacity() {
        return mPromoOpacity;
    }

    /**
     * @return Y coordinate of the promo in pixels.
     */
    protected float getPromoYPx() {
        return Math.round((getOffsetY() + getSearchBarHeight()) / mPxToDp);
    }

    // ============================================================================================
    // Helpers
    // ============================================================================================

    /**
     * Initializes the UI state.
     */
    protected void initializeUiState() {
        mSearchPanelFeatures = new ContextualSearchPanelFeatures(mManagementDelegate.isCustomTab());
        mIsShowing = false;

        // Static values.
        mPxToDp = 1.f / mContext.getResources().getDisplayMetrics().density;

        mToolbarHeight = mContext.getResources().getDimension(
                mManagementDelegate.getControlContainerHeightResource()) * mPxToDp;

        mSearchBarPaddingTop = PANEL_SHADOW_HEIGHT_DP;

        mSearchBarHeightPeeking = mContext.getResources().getDimension(
                R.dimen.contextual_search_bar_height) * mPxToDp;
        mSearchBarHeightMaximized = mContext.getResources().getDimension(
                R.dimen.toolbar_height_no_shadow) * mPxToDp;
        mSearchBarHeightExpanded =
                Math.round((mSearchBarHeightPeeking + mSearchBarHeightMaximized) / 2.f);
        mSearchBarMarginSide = SEARCH_BAR_ICON_SIDE_PADDING_DP;
        mProgressBarHeight = PROGRESS_BAR_HEIGHT_DP;
        mSearchBarBorderHeight = SEARCH_BAR_BORDER_HEIGHT_DP;

        // Dynamic values.
        mSearchBarHeight = mSearchBarHeightPeeking;
    }

    /**
     * Gets the height of the Contextual Search Panel in dps for a given
     * |state|.
     *
     * @param state The state whose height will be calculated.
     * @return The height of the Contextual Search Panel in dps for a given
     *         |state|.
     */
    protected float getPanelHeightFromState(PanelState state) {
        float fullscreenHeight = getFullscreenHeight();
        float panelHeight = 0;

        if (state == PanelState.UNDEFINED) {
            panelHeight = 0;
        } else if (state == PanelState.CLOSED) {
            panelHeight = 0;
        } else if (state == PanelState.PEEKED) {
            panelHeight = mSearchBarHeightPeeking;
        } else if (state == PanelState.EXPANDED) {
            if (isFullscreenSizePanel()) {
                panelHeight = fullscreenHeight * EXPANDED_PANEL_HEIGHT_PERCENTAGE;
            } else {
                panelHeight = mLayoutHeight * NARROW_EXPANDED_PANEL_HEIGHT_PERCENTAGE;
            }
        } else if (state == PanelState.MAXIMIZED) {
            if (isFullscreenSizePanel()) {
                panelHeight = fullscreenHeight;
            } else {
                panelHeight = mLayoutHeight * NARROW_MAXIMIZED_PANEL_HEIGHT_PERCENTAGE;
            }
        }

        return panelHeight;
    }

    /**
     * Finds the state which has the nearest height compared to a given
     * |desiredPanelHeight|.
     *
     * @param desiredPanelHeight The height to compare to.
     * @return The nearest panel state.
     */
    protected PanelState findNearestPanelStateFromHeight(float desiredPanelHeight) {
        PanelState closestPanelState = PanelState.CLOSED;
        float smallestHeightDiff = Float.POSITIVE_INFINITY;

        // Iterate over all states and find the one which has the nearest
        // height.
        for (PanelState state : PanelState.values()) {
            if (!isValidState(state)) {
                continue;
            }
            if (!isFullscreenSizePanel() && state == PanelState.EXPANDED) {
                continue;
            }

            float height = getPanelHeightFromState(state);
            float heightDiff = Math.abs(desiredPanelHeight - height);
            if (heightDiff < smallestHeightDiff) {
                closestPanelState = state;
                smallestHeightDiff = heightDiff;
            }
        }

        return closestPanelState;
    }

    /**
     * Sets the last panel height within the limits allowable by our UI.
     *
     * @param height The height of the panel in dps.
     */
    protected void setClampedPanelHeight(float height) {
        final float clampedHeight = MathUtils.clamp(height,
                getPanelHeightFromState(PanelState.MAXIMIZED),
                getPanelHeightFromState(PanelState.PEEKED));
        setPanelHeight(clampedHeight);
    }

    @Override
    protected void setPanelState(PanelState state, StateChangeReason reason) {
        super.setPanelState(state, reason);

        if (state == PanelState.CLOSED) {
            mIsShowing = false;
            destroyPromoView();
            destroyContextualSearchControl();
            onClose(reason);
        } else if (state == PanelState.EXPANDED && isFullscreenSizePanel()
                || (state == PanelState.MAXIMIZED && !isFullscreenSizePanel())) {
            showPromoViewAtYPosition(getPromoYPx());
        }
    }

    /**
     * Sets the panel height.
     *
     * @param height The height of the panel in dps.
     */
    protected void setPanelHeight(float height) {
        // As soon as we resize the Panel to a different height than the expanded one,
        // then we should hide the Promo View once the snapshot will be shown in its place.
        if (height != getPanelHeightFromState(PanelState.EXPANDED)) {
            hidePromoView();
        }

        updatePanelForHeight(height);
    }

    /**
     * @param state The Panel state.
     * @return Whether the Panel height matches the one from the given state.
     */
    protected boolean doesPanelHeightMatchState(PanelState state) {
        return state == getPanelState() && getHeight() == getPanelHeightFromState(state);
    }

    // ============================================================================================
    // UI Update Handling
    // ============================================================================================

    /**
     * Updates the UI state for a given |height|.
     *
     * @param height The Contextual Search Panel height.
     */
    private void updatePanelForHeight(float height) {
        PanelState endState = findLargestPanelStateFromHeight(height);
        PanelState startState = getPreviousPanelState(endState);
        float percentage = getStateCompletion(height, startState, endState);

        updatePanelSize(height, endState, percentage);

        if (endState == PanelState.CLOSED || endState == PanelState.PEEKED) {
            updatePanelForCloseOrPeek(percentage);
        } else if (endState == PanelState.EXPANDED) {
            updatePanelForExpansion(percentage);
        } else if (endState == PanelState.MAXIMIZED) {
            updatePanelForMaximization(percentage);
        }
    }

    /**
     * Updates the Panel size information.
     *
     * @param height The Contextual Search Panel height.
     * @param endState The final state of transition being executed.
     * @param percentage The completion percentage of the transition.
     */
    private void updatePanelSize(float height, PanelState endState, float percentage) {
        mOffsetX = calculateSearchPanelX();
        mOffsetY = calculateSearchPanelY();
        mHeight = height;
        mIsMaximized = height == getPanelHeightFromState(PanelState.MAXIMIZED);
    }

    /**
     * Finds the largest Panel state which is being transitioned to/from.
     * Whenever the Panel is in between states, let's say, when resizing the
     * Panel from its peeked to expanded state, we need to know those two states
     * in order to calculate how closely we are from one of them. This method
     * will always return the nearest state with the largest height, and
     * together with the state preceding it, it's possible to calculate how far
     * the Panel is from them.
     *
     * @param panelHeight The height to compare to.
     * @return The panel state which is being transitioned to/from.
     */
    private PanelState findLargestPanelStateFromHeight(float panelHeight) {
        PanelState stateFound = PanelState.CLOSED;

        // Iterate over all states and find the largest one which is being
        // transitioned to/from.
        for (PanelState state : PanelState.values()) {
            if (!isValidState(state)) {
                continue;
            }
            if (panelHeight <= getPanelHeightFromState(state)) {
                stateFound = state;
                break;
            }
        }

        return stateFound;
    }

    /**
     * Gets the state completion percentage, taking into consideration the
     * |height| of the Contextual Search Panel, and the initial and final
     * states. A completion of 0 means the Panel is in the initial state and a
     * completion of 1 means the Panel is in the final state.
     *
     * @param height The height of the Contextual Search Panel.
     * @param startState The initial state of the Panel.
     * @param endState The final state of the Panel.
     * @return The completion percentage.
     */
    private float getStateCompletion(float height, PanelState startState, PanelState endState) {
        float startSize = getPanelHeightFromState(startState);
        float endSize = getPanelHeightFromState(endState);
        float percentage = (height - startSize) / (endSize - startSize);
        return percentage;
    }

    /**
     * Updates the UI state for the closed to peeked transition (and vice
     * versa), according to a completion |percentage|.
     *
     * @param percentage The completion percentage.
     */
    private void updatePanelForCloseOrPeek(float percentage) {
        // Update the opt out promo.
        updatePromoVisibility(1.f);

        // Base page offset.
        mBasePageY = 0.f;

        // Base page brightness.
        mBasePageBrightness = BASE_PAGE_BRIGHTNESS_STATE_PEEKED;

        // Search Bar height.
        mSearchBarHeight = mSearchBarHeightPeeking;

        // Search Bar border.
        mIsSearchBarBorderVisible = false;

        // Search Bar text opacity.
        mSearchBarTextOpacity = 1.f;

        // Search icon opacity.
        mSearchIconOpacity = SEARCH_ICON_OPACITY_STATE_PEEKED;

        // Arrow Icon.
        mArrowIconOpacity = ARROW_ICON_OPACITY_STATE_PEEKED;
        mArrowIconRotation = ARROW_ICON_ROTATION_STATE_PEEKED;

        // Close icon opacity.
        mCloseIconOpacity = CLOSE_ICON_OPACITY_STATE_PEEKED;

        // Progress Bar.
        mProgressBarOpacity = 0.f;

        // Update the Search Bar Shadow.
        updateSearchBarShadow();
    }

    /**
     * Updates the UI state for the peeked to expanded transition (and vice
     * versa), according to a completion |percentage|.
     *
     * @param percentage The completion percentage.
     */
    private void updatePanelForExpansion(float percentage) {
        // Update the opt out promo.
        updatePromoVisibility(1.f);

        // Base page offset.
        float baseBaseY = MathUtils.interpolate(
                0.f,
                getBasePageTargetY(),
                percentage);
        mBasePageY = baseBaseY;

        // Base page brightness.
        float brightness = MathUtils.interpolate(
                BASE_PAGE_BRIGHTNESS_STATE_PEEKED,
                BASE_PAGE_BRIGHTNESS_STATE_EXPANDED,
                percentage);
        mBasePageBrightness = brightness;

        // Search Bar height.
        float searchBarHeight = Math.round(MathUtils.interpolate(
                mSearchBarHeightPeeking,
                getSearchBarHeightExpanded(),
                percentage));
        mSearchBarHeight = searchBarHeight;

        // Search Bar text opacity.
        mSearchBarTextOpacity = 1.f;

        // Search Bar border.
        mIsSearchBarBorderVisible = true;
        mSearchBarBorderY = searchBarHeight - SEARCH_BAR_BORDER_HEIGHT_DP + 1;

        // Search icon opacity.
        mSearchIconOpacity = SEARCH_ICON_OPACITY_STATE_EXPANDED;

        // Arrow Icon.
        mArrowIconOpacity = ARROW_ICON_OPACITY_STATE_EXPANDED;
        mArrowIconRotation = Math.round(MathUtils.interpolate(
                ARROW_ICON_ROTATION_STATE_PEEKED,
                ARROW_ICON_ROTATION_STATE_EXPANDED,
                percentage));

        // Close icon opacity.
        mCloseIconOpacity = CLOSE_ICON_OPACITY_STATE_EXPANDED;

        // Progress Bar.
        float peekedHeight = getPanelHeightFromState(PanelState.PEEKED);
        float threshold = PROGRESS_BAR_VISIBILITY_THRESHOLD_DP / mPxToDp;
        float diff = Math.min(mHeight - peekedHeight, threshold);
        // Fades the Progress Bar the closer it gets to the bottom of the
        // screen.
        float progressBarOpacity = MathUtils.interpolate(0.f, 1.f, diff / threshold);
        mProgressBarOpacity = progressBarOpacity;
        mProgressBarY = searchBarHeight - PROGRESS_BAR_HEIGHT_DP + 1;

        // Update the Search Bar Shadow.
        updateSearchBarShadow();
    }

    /**
     * Updates the UI state for the expanded to maximized transition (and vice
     * versa), according to a completion |percentage|.
     *
     * @param percentage The completion percentage.
     */
    private void updatePanelForMaximization(float percentage) {
        // Update the opt out promo.
        float promoVisibilityPercentage = isFullscreenSizePanel() ? 1.f - percentage : 1.f;
        updatePromoVisibility(promoVisibilityPercentage);

        // Base page offset.
        mBasePageY = getBasePageTargetY();

        // Base page brightness.
        float brightness = MathUtils.interpolate(
                BASE_PAGE_BRIGHTNESS_STATE_EXPANDED,
                BASE_PAGE_BRIGHTNESS_STATE_MAXIMIZED,
                percentage);
        mBasePageBrightness = brightness;

        // Search Bar height.
        float searchBarHeight = Math.round(MathUtils.interpolate(
                getSearchBarHeightExpanded(),
                getSearchBarHeightMaximized(),
                percentage));
        mSearchBarHeight = searchBarHeight;

        // Search Bar border.
        mIsSearchBarBorderVisible = true;
        mSearchBarBorderY = searchBarHeight - SEARCH_BAR_BORDER_HEIGHT_DP + 1;

        // Search Bar text opacity.
        mSearchBarTextOpacity = 1.f;

        // Determine fading element opacities. If both the arrow icon and close
        // icon are visible, the arrow icon needs to finish fading out before
        // the close icon starts fading in. Any other elements fading in or
        // fading out should use the same percentage.
        float fadingOutPercentage = percentage;
        float fadingInPercentage = percentage;
        if (mSearchPanelFeatures.isCloseButtonAvailable()) {
            fadingOutPercentage = Math.min(percentage, .5f) / .5f;
            fadingInPercentage = Math.max(percentage - .5f, 0.f) / .5f;
        }

        // Search icon opacity.
        float searchIconOpacity = MathUtils.interpolate(
                SEARCH_ICON_OPACITY_STATE_EXPANDED,
                SEARCH_ICON_OPACITY_STATE_MAXIMIZED,
                fadingInPercentage);
        mSearchIconOpacity = searchIconOpacity;

        // Arrow Icon.
        mArrowIconOpacity = MathUtils.interpolate(
                ARROW_ICON_OPACITY_STATE_EXPANDED,
                ARROW_ICON_OPACITY_STATE_MAXIMIZED,
                fadingOutPercentage);
        mArrowIconRotation = ARROW_ICON_ROTATION_STATE_EXPANDED;

        // Close icon opacity.
        mCloseIconOpacity = MathUtils.interpolate(
                CLOSE_ICON_OPACITY_STATE_EXPANDED,
                CLOSE_ICON_OPACITY_STATE_MAXIMIZED,
                fadingInPercentage);

        // Progress Bar.
        mProgressBarOpacity = 1.f;
        mProgressBarY = searchBarHeight - PROGRESS_BAR_HEIGHT_DP + 1;

        // Update the Search Bar Shadow.
        updateSearchBarShadow();
    }

    private float getSearchBarHeightExpanded() {
        if (isFullscreenSizePanel()) {
            return mSearchBarHeightExpanded;
        } else {
            return mSearchBarHeightPeeking;
        }
    }

    private float getSearchBarHeightMaximized() {
        if (isFullscreenSizePanel()) {
            return mSearchBarHeightMaximized;
        } else {
            return mSearchBarHeightPeeking;
        }
    }

    /**
     * Updates the UI state for Opt Out Promo.
     *
     * @param percentage The visibility percentage of the Promo. A visibility of 0 means the
     * Promo is not visible. A visibility of 1 means the Promo is fully visible. And
     * visibility between 0 and 1 means the Promo is partially visible.
     */
    private void updatePromoVisibility(float percentage) {
        if (isPromoAvailable()) {
            mPromoVisible = true;

            mPromoHeightPx = Math.round(MathUtils.clamp(percentage * mPromoContentHeightPx,
                    0.f, mPromoContentHeightPx));
            mPromoOpacity = percentage;
        } else {
            mPromoVisible = false;
            mPromoHeightPx = 0.f;
            mPromoOpacity = 0.f;
        }
    }

    /**
     * Updates the UI state for Search Bar Shadow.
     */
    public void updateSearchBarShadow() {
        float searchBarShadowHeightPx = 9.f / mPxToDp;
        if (mPromoVisible && mPromoHeightPx > 0.f) {
            mSearchBarShadowVisible = true;
            float threshold = 2 * searchBarShadowHeightPx;
            mSearchBarShadowOpacity = mPromoHeightPx > searchBarShadowHeightPx ? 1.f :
                MathUtils.interpolate(0.f, 1.f, mPromoHeightPx / threshold);
        } else {
            mSearchBarShadowVisible = false;
            mSearchBarShadowOpacity = 0.f;
        }
    }

    // ============================================================================================
    // Base Page Offset
    // ============================================================================================

    /**
     * Updates the coordinate of the existing selection.
     * @param y The y coordinate of the selection in pixels.
     */
    protected void updateBasePageSelectionYPx(float y) {
        mBasePageSelectionYPx = y;
        updateBasePageTargetY();
    }

    /**
     * Updates the target offset of the Base Page in order to keep the selection in view
     * after expanding the Panel.
     */
    private void updateBasePageTargetY() {
        mBasePageTargetY = calculateBasePageTargetY(PanelState.EXPANDED);
    }

    /**
     * Calculates the target offset of the Base Page in order to keep the selection in view
     * after expanding the Panel to the given |expandedState|.
     *
     * @param expandedState
     * @return The target offset Y.
     */
    private float calculateBasePageTargetY(PanelState expandedState) {
        // Only a fullscreen wide Panel should offset the base page. A small panel should
        // always return zero to ensure the Base Page remains in the same position.
        if (!isFullscreenSizePanel()) return 0.f;

        // Convert from px to dp.
        final float selectionY = mBasePageSelectionYPx * mPxToDp;

        // Calculate the exact height of the expanded Panel without taking into
        // consideration the height of the shadow (what is returned by the
        // getPanelFromHeight method). We need the measurement of the portion
        // of the Panel that occludes the page.
        final float expandedHeight = getPanelHeightFromState(expandedState)
                - mSearchBarPaddingTop;

        // Calculate the offset to center the selection on the available area.
        final float fullscreenHeight = getFullscreenHeight();
        final float availableHeight = fullscreenHeight - expandedHeight;
        float offset = -selectionY + availableHeight / 2;

        // Make sure offset is negative to prevent Base Page from moving down,
        // because there's nothing to render above the Page.
        offset = Math.min(offset, 0.f);
        // If visible, the Toolbar will be hidden. Therefore, we need to adjust
        // the offset to account for this difference.
        if (mIsToolbarShowing) offset -= mToolbarHeight;
        // Make sure the offset is not greater than the expanded height, because
        // there's nothing to render below the Page.
        offset = Math.max(offset, -expandedHeight);

        return offset;
    }

    /**
     * @return The Y coordinate to apply to the Base Page in order to keep the selection
     *         in view when the Search Panel is in EXPANDED state.
     */
    public float getBasePageTargetY() {
        return mBasePageTargetY;
    }

    // ============================================================================================
    // Resource Loader
    // ============================================================================================

    private ViewGroup mContainerView;
    private DynamicResourceLoader mResourceLoader;

    /**
     * @param resourceLoader The {@link DynamicResourceLoader} to register and unregister the view.
     */
    public void setDynamicResourceLoader(DynamicResourceLoader resourceLoader) {
        mResourceLoader = resourceLoader;

        if (mControl != null) {
            mResourceLoader.registerResource(R.id.contextual_search_view,
                    mControl.getResourceAdapter());
        }

        if (mPromoView != null) {
            mResourceLoader.registerResource(R.id.contextual_search_opt_out_promo,
                    mPromoView.getResourceAdapter());
        }
    }

    /**
     * Sets the container ViewGroup to which the auxiliary Views will be attached to.
     *
     * @param container The {@link ViewGroup} container.
     */
    public void setContainerView(ViewGroup container) {
        mContainerView = container;
    }

    // ============================================================================================
    // ContextualSearchControl
    // ============================================================================================

    // TODO(pedrosimonetti): rename this to something more generic (e.g. BottomBarTextView).

    private ContextualSearchControl mControl;

    /**
     * Inflates the Contextual Search control, if needed. The View will be set to INVISIBLE
     * after being inflated, because it won't actually be displayed on the screen (its
     * snapshot will be displayed instead).
     */
    protected ContextualSearchControl getContextualSearchControl() {
        assert mContainerView != null;

        if (mControl == null) {
            LayoutInflater.from(mContext).inflate(R.layout.contextual_search_view, mContainerView);
            mControl = (ContextualSearchControl)
                    mContainerView.findViewById(R.id.contextual_search_view);

            // Adjust size for small Panel.
            if (!isFullscreenSizePanel()) {
                mControl.getLayoutParams().width = getMaximumWidthPx();
                mControl.requestLayout();
            }

            if (mResourceLoader != null) {
                mResourceLoader.registerResource(R.id.contextual_search_view,
                        mControl.getResourceAdapter());
            }
        }

        assert mControl != null;
        // TODO(pedrosimonetti): For now, we're still relying on a Android View
        // to render the text that appears in the Search Bar. The View will be
        // invisible and will not capture events. Consider rendering the text
        // in the Compositor and get rid of the View entirely.
        mControl.setVisibility(View.INVISIBLE);
        return mControl;
    }

    protected void destroyContextualSearchControl() {
        if (mControl != null) {
            mContainerView.removeView(mControl);
            mControl = null;
            if (mResourceLoader != null) {
                mResourceLoader.unregisterResource(R.id.contextual_search_view);
            }
        }
    }

    // ============================================================================================
    // Promo Host
    // ============================================================================================

    @Override
    public void onPromoPreferenceClick() {
        new Handler().post(new Runnable() {
            @Override
            public void run() {
                setIsPromoActive(false);
                PreferencesLauncher.launchSettingsPage(mContext,
                        ContextualSearchPreferenceFragment.class.getName());
            }
        });
    }

    @Override
    public void onPromoButtonClick(boolean accepted) {
        if (accepted) {
            animatePromoAcceptance();
        } else {
            hidePromoView();
            setPreferenceState(false);
            closePanel(StateChangeReason.OPTOUT, true);
        }
    }

    // ============================================================================================
    // Opt Out Promo View
    // ============================================================================================

    // TODO(pedrosimonetti): Consider maybe adding a 9.patch to avoid the hacky nested layouts in
    // order to have the transparent gap at the top of the Promo View.

    /**
     * The {@link ContextualSearchOptOutPromo} instance.
     */
    private ContextualSearchOptOutPromo mPromoView;

    /**
     * Whether the Search Promo View is visible.
     */
    private boolean mIsSearchPromoViewVisible = false;

    /**
     * Creates the Search Promo View.
     */
    public void createPromoView() {
        if (!isPromoAvailable()) return;

        assert mContainerView != null;

        if (mPromoView == null) {
            LayoutInflater.from(mContext).inflate(
                    R.layout.contextual_search_opt_out_promo, mContainerView);
            mPromoView = (ContextualSearchOptOutPromo)
                    mContainerView.findViewById(R.id.contextual_search_opt_out_promo);

            final int maximumWidth = getMaximumWidthPx();

            // Adjust size for small Panel.
            if (!isFullscreenSizePanel()) {
                mPromoView.getLayoutParams().width = maximumWidth;
                mPromoView.requestLayout();
            }

            if (mResourceLoader != null) {
                mResourceLoader.registerResource(R.id.contextual_search_opt_out_promo,
                        mPromoView.getResourceAdapter());
            }

            mPromoView.setPromoHost(this);
            setPromoContentHeightPx(mPromoView.getHeightForGivenWidth(maximumWidth));
        }

        assert mPromoView != null;
    }

    /**
     * Destroys the Search Promo View.
     */
    protected void destroyPromoView() {
        if (!isPromoAvailable()) return;

        if (mPromoView != null) {
            mContainerView.removeView(mPromoView);
            mPromoView = null;
            if (mResourceLoader != null) {
                mResourceLoader.unregisterResource(R.id.contextual_search_opt_out_promo);
            }
        }
    }

    /**
     * Displays the Search Promo View at the given Y position.
     *
     * @param y The Y position.
     */
    public void showPromoViewAtYPosition(float y) {
        if (mPromoView == null || !isPromoAvailable()) return;

        mPromoView.setTranslationX(getOffsetX() / mPxToDp);
        mPromoView.setTranslationY(y);
        mPromoView.setVisibility(View.VISIBLE);

        // NOTE(pedrosimonetti): We need to call requestLayout, otherwise
        // the Promo View will not become visible.
        mPromoView.requestLayout();

        mIsSearchPromoViewVisible = true;
    }

    /**
     * Hides the Search Promo View.
     */
    public void hidePromoView() {
        if (mPromoView == null
                || !mIsSearchPromoViewVisible
                || !isPromoAvailable()) {
            return;
        }

        mPromoView.setVisibility(View.INVISIBLE);

        mIsSearchPromoViewVisible = false;
    }

    /**
     * Updates the UI state for Opt In Promo animation.
     *
     * @param percentage The visibility percentage of the Promo.
     */
    protected void setPromoVisibilityForOptInAnimation(float percentage) {
        updatePromoVisibility(percentage);
        updateSearchBarShadow();
    }
}

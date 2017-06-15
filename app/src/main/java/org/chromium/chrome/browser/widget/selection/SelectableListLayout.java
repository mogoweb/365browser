// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.widget.selection;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.support.annotation.VisibleForTesting;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.Adapter;
import android.support.v7.widget.RecyclerView.AdapterDataObserver;
import android.support.v7.widget.RecyclerView.ItemAnimator;
import android.support.v7.widget.RecyclerView.OnScrollListener;
import android.support.v7.widget.Toolbar.OnMenuItemClickListener;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewStub;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.util.FeatureUtilities;
import org.chromium.chrome.browser.widget.FadingShadow;
import org.chromium.chrome.browser.widget.FadingShadowView;
import org.chromium.chrome.browser.widget.LoadingView;
import org.chromium.chrome.browser.widget.displaystyle.DisplayStyleObserver;
import org.chromium.chrome.browser.widget.displaystyle.HorizontalDisplayStyle;
import org.chromium.chrome.browser.widget.displaystyle.UiConfig;
import org.chromium.chrome.browser.widget.displaystyle.UiConfig.DisplayStyle;
import org.chromium.chrome.browser.widget.selection.SelectionDelegate.SelectionObserver;

import java.util.List;

import javax.annotation.Nullable;

/**
 * Contains UI elements common to selectable list views: a loading view, empty view, selection
 * toolbar, shadow, and RecyclerView.
 *
 * After the SelectableListLayout is inflated, it should be initialized through calls to
 * #initializeRecyclerView(), #initializeToolbar(), and #initializeEmptyView().
 *
 * @param <E> The type of the selectable items this layout holds.
 */
public class SelectableListLayout<E>
        extends FrameLayout implements DisplayStyleObserver, SelectionObserver<E> {
    /**
     * @param res Resources used to retrieve drawables and dimensions.
     * @return The default list item lateral margin size in pixels. This value should be used in
     *         {@link HorizontalDisplayStyle#REGULAR} to hide the lateral shadow and rounded edges
     *         on items that use the list_item* 9-patches as a background.
     */
    public static int getDefaultListItemLateralMarginPx(Resources res) {
        if (sDefaultListItemLateralMarginPx == -1) {
            int cardCornerRadius = res.getDimensionPixelSize(R.dimen.list_item_corner_radius);

            // A negative margin is used in HorizontalDisplayStyle#REGULAR to hide the lateral
            // shadow.
            sDefaultListItemLateralMarginPx =
                    -(getDefaultListItemLateralShadowSizePx(res) + cardCornerRadius);
        }

        return sDefaultListItemLateralMarginPx;
    }

    /**
     * Returns the list_item* 9-patch shadow size for use in {@link HorizontalDisplayStyle#WIDE} to
     * align items that don't use the list_item* 9-patches as a background with items that do.
     *
     * @param res Resources used to retrieve drawables and dimensions.
     * @return The default list item shadow size in pixels.
     */
    public static int getDefaultListItemLateralShadowSizePx(Resources res) {
        if (sDefaultListItemLateralShadowSizePx == -1) {
            // Retrieve the size of the nine-patch shadow from the drawable's padding.
            Rect listItemShadow = new Rect();
            ApiCompatibilityUtils.getDrawable(res, R.drawable.card_middle)
                    .getPadding(listItemShadow);

            assert listItemShadow.left == listItemShadow.right;

            sDefaultListItemLateralShadowSizePx = listItemShadow.left;
        }

        return sDefaultListItemLateralShadowSizePx;
    }

    private static final int WIDE_DISPLAY_MIN_PADDING_DP = 16;

    private static int sDefaultListItemLateralMarginPx = -1;
    private static int sDefaultListItemLateralShadowSizePx = -1;

    private Adapter<RecyclerView.ViewHolder> mAdapter;
    private ViewStub mToolbarStub;
    private TextView mEmptyView;
    private LoadingView mLoadingView;
    private RecyclerView mRecyclerView;
    private ItemAnimator mItemAnimator;
    SelectableListToolbar<E> mToolbar;
    private FadingShadowView mToolbarShadow;

    private int mEmptyStringResId;
    private int mSearchEmptyStringResId;
    private int mChromeHomeEmptyAndLoadingViewTopPadding;

    private UiConfig mUiConfig;

    private final AdapterDataObserver mAdapterObserver = new AdapterDataObserver() {
        @Override
        public void onChanged() {
            super.onChanged();
            if (mAdapter.getItemCount() == 0) {
                mEmptyView.setVisibility(View.VISIBLE);
                mRecyclerView.setVisibility(View.GONE);
            } else {
                mEmptyView.setVisibility(View.GONE);
                mRecyclerView.setVisibility(View.VISIBLE);
            }
            // At inflation, the RecyclerView is set to gone, and the loading view is visible. As
            // long as the adapter data changes, we show the recycler view, and hide loading view.
            mLoadingView.hideLoadingUI();

            mToolbar.onDataChanged(mAdapter.getItemCount());
        }

        @Override
        public void onItemRangeInserted(int positionStart, int itemCount) {
            super.onItemRangeInserted(positionStart, itemCount);
            updateEmptyViewVisibility();
        }

        @Override
        public void onItemRangeRemoved(int positionStart, int itemCount) {
            super.onItemRangeRemoved(positionStart, itemCount);
            updateEmptyViewVisibility();
        }
    };

    public SelectableListLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        LayoutInflater.from(getContext()).inflate(R.layout.selectable_list_layout, this);

        // TODO(twellington): Remove this fork in the code after UX decides on final design
        // for empty and loading views.
        mChromeHomeEmptyAndLoadingViewTopPadding =
                getResources().getDimensionPixelSize(R.dimen.chrome_home_empty_view_top_padding);

        mEmptyView = (TextView) findViewById(R.id.empty_view);
        setEmptyOrLoadingViewStyle(mEmptyView);
        mLoadingView = (LoadingView) findViewById(R.id.loading_view);
        setEmptyOrLoadingViewStyle(mLoadingView);
        mLoadingView.showLoadingUI();

        mToolbarStub = (ViewStub) findViewById(R.id.action_bar_stub);

        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (mUiConfig != null) mUiConfig.updateDisplayStyle();
    }

    /**
     * Initializes the RecyclerView.
     *
     * @param adapter The adapter that provides a binding from an app-specific data set to views
     *                that are displayed within the RecyclerView.
     * @return The RecyclerView itself.
     */
    public RecyclerView initializeRecyclerView(Adapter<RecyclerView.ViewHolder> adapter) {
        mAdapter = adapter;
        mAdapter.registerAdapterDataObserver(mAdapterObserver);

        mRecyclerView = (RecyclerView) findViewById(R.id.recycler_view);
        mRecyclerView.setAdapter(mAdapter);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.addOnScrollListener(new OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                setToolbarShadowVisibility();
            }
        });

        mItemAnimator = mRecyclerView.getItemAnimator();

        return mRecyclerView;
    }

    /**
     * Initializes the SelectionToolbar.
     *
     * @param toolbarLayoutId The resource id of the toolbar layout. This will be inflated into
     *                        a ViewStub.
     * @param delegate The SelectionDelegate that will inform the toolbar of selection changes.
     * @param titleResId The resource id of the title string. May be 0 if this class shouldn't set
     *                   set a title when the selection is cleared.
     * @param drawerLayout The DrawerLayout whose navigation icon is displayed in this toolbar.
     * @param normalGroupResId The resource id of the menu group to show when a selection isn't
     *                         established.
     * @param selectedGroupResId The resource id of the menu item to show when a selection is
     *                           established.
     * @param normalBackgroundColorResId The resource id of the color to use as the background color
     *                                   when selection is not enabled. If null the default appbar
     *                                   background color will be used.
     * @param listener The OnMenuItemClickListener to set on the toolbar.
     * @return The initialized SelectionToolbar.
     */
    public SelectableListToolbar<E> initializeToolbar(int toolbarLayoutId,
            SelectionDelegate<E> delegate, int titleResId, @Nullable DrawerLayout drawerLayout,
            int normalGroupResId, int selectedGroupResId,
            @Nullable Integer normalBackgroundColorResId,
            @Nullable OnMenuItemClickListener listener) {
        mToolbarStub.setLayoutResource(toolbarLayoutId);
        @SuppressWarnings("unchecked")
        SelectableListToolbar<E> toolbar = (SelectableListToolbar<E>) mToolbarStub.inflate();
        mToolbar = toolbar;
        mToolbar.initialize(delegate, titleResId, drawerLayout, normalGroupResId,
                selectedGroupResId, normalBackgroundColorResId);

        if (listener != null) {
            mToolbar.setOnMenuItemClickListener(listener);
        }

        mToolbarShadow = (FadingShadowView) findViewById(R.id.shadow);
        mToolbarShadow.init(
                ApiCompatibilityUtils.getColor(getResources(), R.color.toolbar_shadow_color),
                FadingShadow.POSITION_TOP);
        delegate.addObserver(this);
        setToolbarShadowVisibility();

        return mToolbar;
    }

    /**
     * Initializes the view shown when the selectable list is empty.
     *
     * @param emptyDrawable The Drawable to show when the selectable list is empty.
     * @param emptyStringResId The string to show when the selectable list is empty.
     * @param searchEmptyStringResId The string to show when the selectable list is empty during
     *                               a search.
     * @return The {@link TextView} displayed when the list is empty.
     */
    public TextView initializeEmptyView(
            Drawable emptyDrawable, int emptyStringResId, int searchEmptyStringResId) {
        mEmptyStringResId = emptyStringResId;
        mSearchEmptyStringResId = searchEmptyStringResId;

        mEmptyView.setCompoundDrawablesWithIntrinsicBounds(null, emptyDrawable, null, null);
        mEmptyView.setText(mEmptyStringResId);

        return mEmptyView;
    }

    /**
     * Called when the view that owns the SelectableListLayout is destroyed.
     */
    public void onDestroyed() {
        mAdapter.unregisterAdapterDataObserver(mAdapterObserver);
        mToolbar.getSelectionDelegate().removeObserver(this);
        mToolbar.destroy();
    }

    /**
     * Calls {@link #configureWideDisplayStyle(int)} using the default list item lateral shadow
     * defined by {@link #getDefaultListItemLateralShadowSizePx(Resources)}.
     */
    public void configureWideDisplayStyle() {
        configureWideDisplayStyle(getDefaultListItemLateralShadowSizePx(getResources()));
    }

    /**
     * When this layout has a wide display style, it will be width constrained to
     * {@link UiConfig#WIDE_DISPLAY_STYLE_MIN_WIDTH_DP}. If the current screen width is greater than
     * UiConfig#WIDE_DISPLAY_STYLE_MIN_WIDTH_DP, the SelectableListLayout will be visually centered
     * by adding padding to both sides.
     *
     * This method should be called after the toolbar and RecyclerView are initialized.
     *
     * @param wideDisplayToolbarLateralOffsetPx The offset to use for the toolbar's lateral padding
     *                                          when in {@link HorizontalDisplayStyle#WIDE}.
     */
    public void configureWideDisplayStyle(int wideDisplayToolbarLateralOffsetPx) {
        mUiConfig = new UiConfig(this);
        mToolbar.configureWideDisplayStyle(wideDisplayToolbarLateralOffsetPx, mUiConfig);
        mUiConfig.addObserver(this);
    }

    /**
     * @return The {@link UiConfig} associated with this View if one has been created, or null.
     */
    @Nullable
    public UiConfig getUiConfig() {
        return mUiConfig;
    }

    @Override
    public void onDisplayStyleChanged(DisplayStyle newDisplayStyle) {
        int padding = getPaddingForDisplayStyle(newDisplayStyle, getResources());

        ApiCompatibilityUtils.setPaddingRelative(mRecyclerView,
                padding, mRecyclerView.getPaddingTop(),
                padding, mRecyclerView.getPaddingBottom());
    }

    @Override
    public void onSelectionStateChange(List<E> selectedItems) {
        setToolbarShadowVisibility();
    }

    /**
     * Removes the toolbar view from this view and returns it so that it may be re-attached
     * elsewhere.
     * @return The toolbar view.
     */
    public SelectableListToolbar<E> detachToolbarView() {
        removeView(mToolbar);

        // The top margin for the content and shadow needs to be removed now that the toolbar
        // has been removed.
        View content = findViewById(R.id.list_content);
        ((MarginLayoutParams) content.getLayoutParams()).topMargin = 0;
        ((MarginLayoutParams) mToolbarShadow.getLayoutParams()).topMargin = 0;

        return mToolbar;
    }

    /**
     * Called when a search is starting.
     */
    public void onStartSearch() {
        mRecyclerView.setItemAnimator(null);
        mToolbarShadow.setVisibility(View.VISIBLE);
        mEmptyView.setText(mSearchEmptyStringResId);
    }

    /**
     * Called when a search has ended.
     */
    public void onEndSearch() {
        mRecyclerView.setItemAnimator(mItemAnimator);
        setToolbarShadowVisibility();
        mEmptyView.setText(mEmptyStringResId);
    }

    /**
     * @param displayStyle The current display style..
     * @param resources The {@link Resources} used to retrieve configuration and display metrics.
     * @return The lateral padding to use for the current display style.
     */
    public static int getPaddingForDisplayStyle(DisplayStyle displayStyle, Resources resources) {
        int padding = 0;
        if (displayStyle.horizontal == HorizontalDisplayStyle.WIDE) {
            int screenWidthDp = resources.getConfiguration().screenWidthDp;
            float dpToPx = resources.getDisplayMetrics().density;
            padding = (int) (((screenWidthDp - UiConfig.WIDE_DISPLAY_STYLE_MIN_WIDTH_DP) / 2.f)
                    * dpToPx);
            padding = (int) Math.max(WIDE_DISPLAY_MIN_PADDING_DP * dpToPx, padding);
        }
        return padding;
    }

    private void setToolbarShadowVisibility() {
        if (mToolbar == null || mRecyclerView == null) return;

        boolean showShadow = mRecyclerView.canScrollVertically(-1) || mToolbar.isSearching()
                || mToolbar.getSelectionDelegate().isSelectionEnabled();
        mToolbarShadow.setVisibility(showShadow ? View.VISIBLE : View.GONE);
    }

    /**
     * Unlike ListView or GridView, RecyclerView does not provide default empty
     * view implementation. We need to check it ourselves.
     */
    private void updateEmptyViewVisibility() {
        mEmptyView.setVisibility(mAdapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
    }

    @VisibleForTesting
    public View getToolbarShadowForTests() {
        return mToolbarShadow;
    }

    private void setEmptyOrLoadingViewStyle(View view) {
        if (!FeatureUtilities.isChromeHomeEnabled()) return;

        ((FrameLayout.LayoutParams) view.getLayoutParams()).gravity = Gravity.CENTER_HORIZONTAL;
        ApiCompatibilityUtils.setPaddingRelative(view, ApiCompatibilityUtils.getPaddingStart(view),
                view.getPaddingTop() + mChromeHomeEmptyAndLoadingViewTopPadding,
                ApiCompatibilityUtils.getPaddingEnd(view), view.getPaddingBottom());
    }
}
// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp;

import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.ntp.NewTabPageUma.NTPLayoutResult;
import org.chromium.chrome.browser.ntp.cards.CardsVariationParameters;
import org.chromium.chrome.browser.ntp.cards.NewTabPageRecyclerView;
import org.chromium.chrome.browser.ntp.snippets.SnippetsConfig;
import org.chromium.chrome.browser.suggestions.TileGridLayout;

/**
 * Layout for the new tab page. This positions the page elements in the correct vertical positions.
 * There are no separate phone and tablet UIs; this layout adapts based on the available space.
 */
public class NewTabPageLayout extends LinearLayout {

    // Space permitting, the spacers will grow from 0dp to the heights given below. If there is
    // additional space, it will be distributed evenly between the top and bottom spacers.
    private static final float TOP_SPACER_HEIGHT_DP = 44f;
    private static final float MIDDLE_SPACER_HEIGHT_DP = 24f;
    private static final float BOTTOM_SPACER_HEIGHT_DP = 44f;
    private static final float TOTAL_SPACER_HEIGHT_DP = TOP_SPACER_HEIGHT_DP
            + MIDDLE_SPACER_HEIGHT_DP + BOTTOM_SPACER_HEIGHT_DP;

    private final int mTopSpacerIdealHeight;
    private final int mMiddleSpacerIdealHeight;
    private final int mBottomSpacerIdealHeight;
    private final int mTotalSpacerIdealHeight;
    private final int mTileGridLayoutBleed;
    private final int mPeekingCardHeight;
    private final int mTabStripHeight;
    private final int mFieldTrialLayoutAdjustment;
    private final int mSearchboxShadowWidth;

    private int mParentViewportHeight;

    private View mTopSpacer; // Spacer above search logo.
    private View mMiddleSpacer; // Spacer between toolbar and Most Likely.
    private View mBottomSpacer; // Spacer below Most Likely.

    private View mLogoSpacer; // Spacer above the logo.
    private View mSearchBoxSpacer; // Spacer above the search box.

    private LogoView mSearchProviderLogoView;
    private View mSearchBoxView;
    private TileGridLayout mTileGridLayout;

    private boolean mLayoutResultRecorded;

    /**
     * Constructor for inflating from XML.
     */
    public NewTabPageLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        Resources res = getResources();
        float density = res.getDisplayMetrics().density;
        mTopSpacerIdealHeight = Math.round(density * TOP_SPACER_HEIGHT_DP);
        mMiddleSpacerIdealHeight = Math.round(density * MIDDLE_SPACER_HEIGHT_DP);
        mBottomSpacerIdealHeight = Math.round(density * BOTTOM_SPACER_HEIGHT_DP);
        mTotalSpacerIdealHeight = Math.round(density * TOTAL_SPACER_HEIGHT_DP);
        mTileGridLayoutBleed = res.getDimensionPixelSize(R.dimen.tile_grid_layout_bleed);
        mPeekingCardHeight = SnippetsConfig.isIncreasedCardVisibilityEnabled()
                ? res.getDimensionPixelSize(R.dimen.snippets_peeking_card_peek_amount)
                : res.getDimensionPixelSize(R.dimen.snippets_padding);
        mTabStripHeight = res.getDimensionPixelSize(R.dimen.tab_strip_height);
        mFieldTrialLayoutAdjustment = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                CardsVariationParameters.getFirstCardOffsetDp(), res.getDisplayMetrics());
        mSearchboxShadowWidth = res.getDimensionPixelOffset(R.dimen.ntp_search_box_shadow_width);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mTopSpacer = findViewById(R.id.ntp_top_spacer);
        mMiddleSpacer = findViewById(R.id.ntp_middle_spacer);
        mBottomSpacer = findViewById(R.id.ntp_bottom_spacer);
        mLogoSpacer = findViewById(R.id.search_provider_logo_spacer);
        mSearchBoxSpacer = findViewById(R.id.search_box_spacer);
        mSearchProviderLogoView = (LogoView) findViewById(R.id.search_provider_logo);
        mSearchBoxView = findViewById(R.id.search_box);
        mTileGridLayout = (TileGridLayout) findViewById(R.id.tile_grid_layout);
    }

    /**
     * Specifies the height of the parent's viewport for the container view of this View.
     *
     * As this is required in onMeasure, we can not rely on the parent having the proper
     * size set yet and thus must be told explicitly of this size.
     *
     * This View takes into account the presence of the tab strip height for tablets.
     */
    public void setParentViewportHeight(int height) {
        mParentViewportHeight = height;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        calculateVerticalSpacing(widthMeasureSpec, heightMeasureSpec);
        unifyElementWidths();
    }

    /**
     * Uses the total vertical space to determine and configure the layout. This can be one of:
     * - If our contents cannot fit on the screen, increase the spacing to push the Most Likely
     *   partially off the screen, suggesting to users they can scroll.
     * - If our contents can fit on the screen, increase the spacing to fill the space (minus space
     *   for the CardsUI Peeking card).
     */
    private void calculateVerticalSpacing(int widthMeasureSpec, int heightMeasureSpec) {
        mLogoSpacer.setVisibility(View.GONE);
        mSearchBoxSpacer.setVisibility(View.GONE);

        // Remove the extra spacing before measuring because it might not be needed anymore.
        mTileGridLayout.setExtraVerticalSpacing(0);

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        boolean hasSpaceForPeekingCard = false;
        int spaceToFill = mParentViewportHeight - mPeekingCardHeight - mTabStripHeight;
        @NTPLayoutResult int layoutResult;

        if (ChromeFeatureList.isEnabled(ChromeFeatureList.NTP_CONDENSED_LAYOUT)) {
            layoutResult = NewTabPageUma.NTP_LAYOUT_CONDENSED;
        } else if (getMeasuredHeight() > spaceToFill) {
            // We need to make sure we have just enough space to show the peeking card.
            layoutResult = NewTabPageUma.NTP_LAYOUT_DOES_NOT_FIT;

            // We don't have enough, we will push the peeking card completely below the fold
            // and let the tile grid get cut to make it clear that the page is scrollable.
            if (mTileGridLayout.getChildCount() > 0) {
                // Add some extra space if needed (the 'bleed' is the amount of the layout that
                // will be cut off by the bottom of the screen).
                int currentBleed = getMeasuredHeight() - mParentViewportHeight - mTabStripHeight;
                int minimumBleed = (int) (mTileGridLayout.getChildAt(0).getMeasuredHeight() * 0.44);
                if (currentBleed < minimumBleed) {
                    int extraBleed = minimumBleed - currentBleed;
                    mLogoSpacer.getLayoutParams().height = (int) (extraBleed * 0.25);
                    mLogoSpacer.setVisibility(View.INVISIBLE);
                    mSearchBoxSpacer.getLayoutParams().height = (int) (extraBleed * 0.25);
                    mSearchBoxSpacer.setVisibility(View.INVISIBLE);
                    mTileGridLayout.setExtraVerticalSpacing((int) (extraBleed * 0.5));
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);

                    layoutResult = NewTabPageUma.NTP_LAYOUT_DOES_NOT_FIT_PUSH_MOST_LIKELY;
                }
            }
        } else {
            hasSpaceForPeekingCard = true;
            // We leave more than or just enough space needed for the peeking card. Redistribute
            // any weighted space.

            // There is a field trial experiment to determine the effect of raising the peeking
            // card, allowing the user to see some of it's contents when scrolled to the top. This
            // is achieved by making the NewTabPageLayout smaller.
            // If there is enough space, reduce the space we are going to fill.
            if (mFieldTrialLayoutAdjustment != 0f) {
                if (getMeasuredHeight() < spaceToFill - mFieldTrialLayoutAdjustment) {
                    spaceToFill -= mFieldTrialLayoutAdjustment;
                    layoutResult = NewTabPageUma.NTP_LAYOUT_FITS_WITH_FIELD_TRIAL;
                } else {
                    layoutResult = NewTabPageUma.NTP_LAYOUT_FITS_WITHOUT_FIELD_TRIAL;
                }
            } else {
                layoutResult = NewTabPageUma.NTP_LAYOUT_FITS_NO_FIELD_TRIAL;
            }

            // Call super.onMeasure with mode EXACTLY and the target height to allow the top
            // spacer (which has a weight of 1) to grow and take up the remaining space.
            heightMeasureSpec =
                    MeasureSpec.makeMeasureSpec(spaceToFill, MeasureSpec.EXACTLY);
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);

            distributeExtraSpace(mTopSpacer.getMeasuredHeight());
        }

        NewTabPageRecyclerView recyclerView = (NewTabPageRecyclerView) getParent();
        recyclerView.setHasSpaceForPeekingCard(hasSpaceForPeekingCard);

        // The first few runs of this method occur before the tile grid layout has loaded its
        // contents. We want to record what the user sees when the layout has stabilized.
        if (mTileGridLayout.getChildCount() > 0 && !mLayoutResultRecorded) {
            mLayoutResultRecorded = true;
            NewTabPageUma.recordNTPLayoutResult(layoutResult);
        }
    }

    /**
     * Makes the Search Box and Logo as wide as Most Visited.
     */
    private void unifyElementWidths() {
        if (mTileGridLayout.getVisibility() != GONE) {
            final int width = mTileGridLayout.getMeasuredWidth() - mTileGridLayoutBleed;
            measureExactly(mSearchBoxView,
                    width + mSearchboxShadowWidth, mSearchBoxView.getMeasuredHeight());
            measureExactly(mSearchProviderLogoView,
                    width, mSearchProviderLogoView.getMeasuredHeight());
        }
    }

    /**
     * Distribute extra vertical space between the three spacer views. Doing this here allows for
     * more sophisticated constraints than in xml.
     * @param extraHeight The amount of extra space, in pixels.
     */
    private void distributeExtraSpace(int extraHeight) {
        int topSpacerHeight;
        int middleSpacerHeight;
        int bottomSpacerHeight;

        if (extraHeight < mTotalSpacerIdealHeight) {
            // The spacers will be less than their ideal height, shrink them proportionally.
            topSpacerHeight =
                    Math.round(extraHeight * (TOP_SPACER_HEIGHT_DP / TOTAL_SPACER_HEIGHT_DP));
            middleSpacerHeight =
                    Math.round(extraHeight * (MIDDLE_SPACER_HEIGHT_DP / TOTAL_SPACER_HEIGHT_DP));
            bottomSpacerHeight = extraHeight - topSpacerHeight - middleSpacerHeight;
        } else {
            // Distribute remaining space evenly between the top and bottom spacers.
            extraHeight -= mTotalSpacerIdealHeight;
            topSpacerHeight = mTopSpacerIdealHeight + extraHeight / 2;
            middleSpacerHeight = mMiddleSpacerIdealHeight;
            bottomSpacerHeight = mBottomSpacerIdealHeight + extraHeight / 2;
        }

        measureExactly(mTopSpacer, 0, topSpacerHeight);
        measureExactly(mMiddleSpacer, 0, middleSpacerHeight);
        measureExactly(mBottomSpacer, 0, bottomSpacerHeight);
    }

    /**
     * Convenience method to call measure() on the given View with MeasureSpecs converted from the
     * given dimensions (in pixels) with MeasureSpec.EXACTLY.
     */
    private static void measureExactly(View view, int widthPx, int heightPx) {
        view.measure(MeasureSpec.makeMeasureSpec(widthPx, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(heightPx, MeasureSpec.EXACTLY));
    }
}

// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp.cards;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Region;
import android.support.v4.view.animation.LinearOutSlowInInterpolator;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Interpolator;

import org.chromium.base.Log;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ntp.NewTabPageLayout;
import org.chromium.chrome.browser.ntp.snippets.SectionHeaderViewHolder;
import org.chromium.chrome.browser.ntp.snippets.SnippetsConfig;
import org.chromium.chrome.browser.preferences.ChromePreferenceManager;
import org.chromium.chrome.browser.suggestions.SuggestionsRecyclerView;
import org.chromium.chrome.browser.util.ViewUtils;

/**
 * Simple wrapper on top of a RecyclerView that will acquire focus when tapped.  Ensures the
 * New Tab page receives focus when clicked.
 */
public class NewTabPageRecyclerView extends SuggestionsRecyclerView {
    private static final String TAG = "NtpCards";
    private static final Interpolator PEEKING_CARD_INTERPOLATOR = new LinearOutSlowInInterpolator();
    private static final int PEEKING_CARD_ANIMATION_TIME_MS = 1000;
    private static final int PEEKING_CARD_ANIMATION_START_DELAY_MS = 300;

    private final int mToolbarHeight;
    private final int mSearchBoxTransitionLength;
    private final int mPeekingHeight;

    /** How much of the first card is visible above the fold with the increased visibility UI. */
    private final int mPeekingCardBounceDistance;

    /** The peeking card animates in the first time it is made visible. */
    private boolean mFirstCardAnimationRun;

    /** We have tracked that the user has caused an impression after viewing the animation. */
    private boolean mCardImpressionAfterAnimationTracked;

    /** View used to calculate the position of the cards' snap point. */
    private final NewTabPageLayout mAboveTheFoldView;

    /** Whether the above-the-fold left space for a peeking card to be displayed. */
    private boolean mHasSpaceForPeekingCard;

    /** Whether the location bar is shown as part of the UI. */
    private boolean mContainsLocationBar;

    public NewTabPageRecyclerView(Context context) {
        super(context);

        Resources res = getContext().getResources();
        mToolbarHeight = res.getDimensionPixelSize(R.dimen.toolbar_height_no_shadow)
                + res.getDimensionPixelSize(R.dimen.toolbar_progress_bar_height);
        mPeekingCardBounceDistance =
                res.getDimensionPixelSize(R.dimen.snippets_peeking_card_bounce_distance);
        mSearchBoxTransitionLength =
                res.getDimensionPixelSize(R.dimen.ntp_search_box_transition_length);
        mPeekingHeight = res.getDimensionPixelSize(R.dimen.snippets_padding);

        mAboveTheFoldView = (NewTabPageLayout) LayoutInflater.from(getContext())
                                    .inflate(R.layout.new_tab_page_layout, this, false);
    }

    public NewTabPageLayout getAboveTheFoldView() {
        return mAboveTheFoldView;
    }

    public void setHasSpaceForPeekingCard(boolean hasSpaceForPeekingCard) {
        mHasSpaceForPeekingCard = hasSpaceForPeekingCard;
    }

    public void setContainsLocationBar(boolean containsLocationBar) {
        mContainsLocationBar = containsLocationBar;
    }

    private void scrollToFirstCard() {
        // Offset the target scroll by the height of the omnibox (the top padding).
        final int targetScroll = mAboveTheFoldView.getHeight() - mAboveTheFoldView.getPaddingTop();
        // If (somehow) the peeking card is tapped while midway through the transition,
        // we need to account for how much we have already scrolled.
        smoothScrollBy(0, targetScroll - computeVerticalScrollOffset());
    }

    /**
     * Updates the space added at the end of the list to make sure the above/below the fold
     * distinction can be preserved.
     */
    private void refreshBottomSpacing() {
        ViewHolder bottomSpacingViewHolder = findBottomSpacer();

        // It might not be in the layout yet if it's not visible or ready to be displayed.
        if (bottomSpacingViewHolder == null) return;

        assert bottomSpacingViewHolder.getItemViewType() == ItemViewType.SPACING;
        bottomSpacingViewHolder.itemView.requestLayout();
    }

    /**
     * Calculates the height of the bottom spacing item, such that there is always enough content
     * below the fold to push the header up to to the top of the screen.
     */
    int calculateBottomSpacing() {
        int aboveTheFoldPosition = getNewTabPageAdapter().getAboveTheFoldPosition();
        int firstVisiblePos = getLinearLayoutManager().findFirstVisibleItemPosition();
        if (aboveTheFoldPosition == RecyclerView.NO_POSITION
                || firstVisiblePos == RecyclerView.NO_POSITION) {
            return 0;
        }

        if (firstVisiblePos > aboveTheFoldPosition) {
            // We have enough items to fill the viewport, since we have scrolled past the
            // above-the-fold item. We must check whether the above-the-fold view has been rendered
            // at least once, because it's possible to skip right over it if the initial scroll
            // position is not 0, in which case we may need the spacer to be taller than 0.
            return 0;
        }

        ViewHolder lastContentItem = findLastContentItem();
        ViewHolder aboveTheFold = findViewHolderForAdapterPosition(aboveTheFoldPosition);

        int bottomSpacing = getHeight() - mToolbarHeight;
        if (lastContentItem == null || aboveTheFold == null) {
            // This can happen in several cases, where some elements are not visible and the
            // RecyclerView didn't already attach them. We handle it by just adding space to make
            // sure that we never run out and force the UI to jump around and get stuck in a
            // position that breaks the animations. The height will be properly adjusted at the
            // next pass. Known cases that make it necessary:
            //  - The card list is refreshed while the NTP is not shown, for example when changing
            //    the sync settings.
            //  - Dismissing a suggestion and having the status card coming to take its place.
            //  - Refresh while being below the fold, for example by tapping the status card.

            if (aboveTheFold != null) bottomSpacing -= aboveTheFold.itemView.getBottom();

            Log.w(TAG, "The RecyclerView items are not attached, can't determine the content "
                            + "height: snap=%s, spacer=%s. Using full height: %d ",
                    aboveTheFold, lastContentItem, bottomSpacing);
        } else {
            int contentHeight =
                    lastContentItem.itemView.getBottom() - aboveTheFold.itemView.getBottom();
            bottomSpacing -= contentHeight - getCompensationHeight();
        }

        return Math.max(0, bottomSpacing);
    }

    public void updatePeekingCardAndHeader() {
        NewTabPageLayout aboveTheFoldView = findAboveTheFoldView();
        if (aboveTheFoldView == null) return;

        SectionHeaderViewHolder header = findFirstHeader();
        if (header == null) return;

        header.updateDisplay(computeVerticalScrollOffset(), mHasSpaceForPeekingCard);

        CardViewHolder firstCard = findFirstCard();
        if (firstCard != null) updatePeekingCard(firstCard);

        // Update the space at the bottom, which needs to know about the height of the header.
        refreshBottomSpacing();
    }

    /**
     * Updates the peeking state of the provided card. Relies on the dimensions of the header to
     * be correct, prefer {@link #updatePeekingCardAndHeader} that updates both together.
     */
    public void updatePeekingCard(CardViewHolder peekingCard) {
        assert peekingCard.getAdapterPosition() == getNewTabPageAdapter().getFirstCardPosition();

        if (!shouldPeekFirstCard()) {
            peekingCard.setNotPeeking();
            return;
        }

        SectionHeaderViewHolder header = findFirstHeader();
        if (header == null) {
            // No header, we must have scrolled quite far. Fallback to a non animated (full bleed)
            // card.
            peekingCard.setNotPeeking();
            return;
        }

        // The space below the header is what we have available.
        // TODO(bauerb): The header position isn't always accurate at this point, if the height has
        // been changed in the layout params but the layout pass hasn't run yet.
        peekingCard.updatePeek(getHeight() - header.itemView.getBottom());
    }

    /**
     * Returns the approximate adapter position that the user has scrolled to. The purpose of this
     * value is that it can be stored and later retrieved to restore a scroll position that is
     * familiar to the user, showing (part of) the same content the user was previously looking at.
     * This position is valid for that purpose regardless of device orientation changes. Note that
     * if the underlying data has changed in the meantime, different content would be shown for this
     * position.
     */
    public int getScrollPosition() {
        return getLinearLayoutManager().findFirstVisibleItemPosition();
    }

    /**
     * Finds the view holder for the first header.
     * @return The {@code ViewHolder} of the header, or null if it is not present.
     */
    private SectionHeaderViewHolder findFirstHeader() {
        int position = getNewTabPageAdapter().getFirstHeaderPosition();
        if (position == RecyclerView.NO_POSITION) return null;

        ViewHolder viewHolder = findViewHolderForAdapterPosition(position);
        if (!(viewHolder instanceof SectionHeaderViewHolder)) return null;

        return (SectionHeaderViewHolder) viewHolder;
    }

    /**
     * Finds the view holder for the first card.
     * @return The {@code ViewHolder} for the first card, or null if it is not present.
     */
    private CardViewHolder findFirstCard() {
        int position = getNewTabPageAdapter().getFirstCardPosition();
        if (position == RecyclerView.NO_POSITION) return null;

        ViewHolder viewHolder = findViewHolderForAdapterPosition(position);
        if (!(viewHolder instanceof CardViewHolder)) return null;

        return (CardViewHolder) viewHolder;
    }

    /**
     * Finds the view holder for the bottom spacer.
     * @return The {@code ViewHolder} of the bottom spacer, or null if it is not present.
     */
    private ViewHolder findBottomSpacer() {
        int position = getNewTabPageAdapter().getBottomSpacerPosition();
        if (position == RecyclerView.NO_POSITION) return null;

        return findViewHolderForAdapterPosition(position);
    }

    private ViewHolder findLastContentItem() {
        int position = getNewTabPageAdapter().getLastContentItemPosition();
        if (position == RecyclerView.NO_POSITION) return null;

        return findViewHolderForAdapterPosition(position);
    }

    /**
     * Finds the above the fold view.
     * @return The view for above the fold or null, if it is not present.
     */
    private NewTabPageLayout findAboveTheFoldView() {
        int position = getNewTabPageAdapter().getAboveTheFoldPosition();
        if (position == RecyclerView.NO_POSITION) return null;

        ViewHolder viewHolder = findViewHolderForAdapterPosition(position);
        if (viewHolder == null) return null;

        View view = viewHolder.itemView;
        if (!(view instanceof NewTabPageLayout)) return null;

        return (NewTabPageLayout) view;
    }

    @Override
    public void onItemDismissStarted(ViewHolder viewHolder) {
        super.onItemDismissStarted(viewHolder);
        refreshBottomSpacing();
    }

    @Override
    public void onItemDismissFinished(ViewHolder viewHolder) {
        super.onItemDismissFinished(viewHolder);
        refreshBottomSpacing();
    }

    /**
     * Calculates the position to scroll to in order to move out of a region where the RecyclerView
     * should not stay at rest.
     * @param currentScroll the current scroll position.
     * @param regionStart the beginning of the region to scroll out of.
     * @param regionEnd the end of the region to scroll out of.
     * @param flipPoint the threshold used to decide which bound of the region to scroll to.
     * @return the position to scroll to.
     */
    private static int calculateSnapPositionForRegion(
            int currentScroll, int regionStart, int regionEnd, int flipPoint) {
        assert regionStart <= flipPoint;
        assert flipPoint <= regionEnd;

        if (currentScroll < regionStart || currentScroll > regionEnd) return currentScroll;

        if (currentScroll < flipPoint) {
            return regionStart;
        } else {
            return regionEnd;
        }
    }

    /**
     * If the RecyclerView is currently scrolled to between regionStart and regionEnd, smooth scroll
     * out of the region to the nearest edge.
     */
    private static int calculateSnapPositionForRegion(
            int currentScroll, int regionStart, int regionEnd) {
        return calculateSnapPositionForRegion(
                currentScroll, regionStart, regionEnd, (regionStart + regionEnd) / 2);
    }

    /**
     * Snaps the scroll point of the RecyclerView to prevent the user from scrolling to midway
     * through a transition and to allow peeking card behaviour.
     */
    public void snapScroll(View fakeBox, int parentHeight) {
        int initialScroll = computeVerticalScrollOffset();

        int scrollTo = calculateSnapPosition(initialScroll, fakeBox, parentHeight);

        // Calculating the snap position should be idempotent.
        assert scrollTo == calculateSnapPosition(scrollTo, fakeBox, parentHeight);

        smoothScrollBy(0, scrollTo - initialScroll);
    }

    @VisibleForTesting
    int calculateSnapPosition(int scrollPosition, View fakeBox, int parentHeight) {
        if (mContainsLocationBar) {
            // Snap scroll to prevent only part of the toolbar from showing.
            scrollPosition = calculateSnapPositionForRegion(scrollPosition, 0, mToolbarHeight);

            // Snap scroll to prevent resting in the middle of the omnibox transition.
            int fakeBoxUpperBound = fakeBox.getTop() + fakeBox.getPaddingTop();
            scrollPosition = calculateSnapPositionForRegion(scrollPosition,
                    fakeBoxUpperBound - mSearchBoxTransitionLength, fakeBoxUpperBound);
        }

        // Snap scroll to prevent resting in the middle of the peeking card transition
        // and to allow the peeking card to peek a bit before snapping back.
        CardViewHolder peekingCardViewHolder = findFirstCard();
        if (peekingCardViewHolder == null) return scrollPosition;

        if (!isFirstItemVisible() || !shouldPeekFirstCard()) return scrollPosition;

        ViewHolder firstHeaderViewHolder = findFirstHeader();

        // It is possible to have a card but no header, for example the sign in promo.
        // That one does not peek.
        if (firstHeaderViewHolder == null) return scrollPosition;

        View peekingCardView = peekingCardViewHolder.itemView;
        View headerView = firstHeaderViewHolder.itemView;

        // |A + B - C| gives the offset of the peeking card relative to the RecyclerView,
        // so scrolling to this point would put the peeking card at the top of the screen.
        // Remove the |headerView| height which gets dynamically increased with scrolling.
        // |A + B - C - D| will scroll us so that the peeking card is just off the bottom
        // of the screen.
        // Finally, we get |A + B - C - D + E| because the transition starts from the
        // peeking card's resting point, which is |E| from the bottom of the screen.
        int start = peekingCardView.getTop() // A.
                + scrollPosition // B.
                - headerView.getHeight() // C.
                - parentHeight // D.
                + mPeekingHeight; // E.

        // The height of the region in which the the peeking card will snap.
        int snapScrollHeight = mPeekingHeight + headerView.getHeight();

        return calculateSnapPositionForRegion(
                scrollPosition, start, start + snapScrollHeight, start + snapScrollHeight);
    }

    @Override
    public boolean gatherTransparentRegion(Region region) {
        ViewUtils.gatherTransparentRegionsForOpaqueView(this, region);
        return true;
    }

    private boolean shouldAnimateFirstCard() {
        // The "bouncing" animation for the first card is only enabled if
        // 1) there is space for it, ...
        if (!mHasSpaceForPeekingCard) return false;

        // ... 2) the corresponding feature is enabled, ...
        if (!SnippetsConfig.isIncreasedCardVisibilityEnabled()) return false;

        // ... 3) and the animation hasn't run yet.
        return !mFirstCardAnimationRun;
    }

    private boolean shouldPeekFirstCard() {
        // Peeking above the fold is only enabled if there is space.
        if (!mHasSpaceForPeekingCard) return false;

        // It's also disabled in the card offset field trial...
        if (CardsVariationParameters.getFirstCardOffsetDp() > 0) return false;

        // ...and in the increased visibility (bouncing animation) feature.
        return !SnippetsConfig.isIncreasedCardVisibilityEnabled();
    }

    @Override
    public void onCardBound(CardViewHolder cardViewHolder) {
        if (cardViewHolder.getAdapterPosition() == getNewTabPageAdapter().getFirstCardPosition()) {
            updatePeekingCard(cardViewHolder);
        } else {
            cardViewHolder.setNotPeeking();
        }

        // Animate the peeking card.
        // We only run if the feature is enabled and once per NTP.
        if (!shouldAnimateFirstCard()) return;
        mFirstCardAnimationRun = true;

        // We only want an animation to run if we are not scrolled.
        if (computeVerticalScrollOffset() != 0) return;

        // We only show the animation a certain number of times to a user.
        ChromePreferenceManager manager = ChromePreferenceManager.getInstance();
        int animCount = manager.getNewTabPageFirstCardAnimationRunCount();
        if (animCount > CardsVariationParameters.getFirstCardAnimationMaxRuns()) return;
        manager.setNewTabPageFirstCardAnimationRunCount(animCount + 1);

        // We do not show the animation if the user has previously seen it then scrolled.
        if (manager.getCardsImpressionAfterAnimation()) return;

        // The peeking card bounces up twice from its position.
        ObjectAnimator animator =
                ObjectAnimator.ofFloat(cardViewHolder.itemView, View.TRANSLATION_Y, 0f,
                        -mPeekingCardBounceDistance, 0f, -mPeekingCardBounceDistance, 0f);
        animator.setStartDelay(PEEKING_CARD_ANIMATION_START_DELAY_MS);
        animator.setDuration(PEEKING_CARD_ANIMATION_TIME_MS);
        animator.setInterpolator(PEEKING_CARD_INTERPOLATOR);
        animator.start();
    }

    @Override
    public void onSnippetImpression() {
        // If the user has seen the first card animation and causes a snippet impression, remember
        // for future runs.
        if (!mFirstCardAnimationRun && !mCardImpressionAfterAnimationTracked) return;

        ChromePreferenceManager.getInstance().setCardsImpressionAfterAnimation(true);
        mCardImpressionAfterAnimationTracked = true;
    }

    @Override
    public boolean interceptCardTapped(CardViewHolder cardViewHolder) {
        if (!cardViewHolder.isPeeking()) return false;

        scrollToFirstCard();
        return true;
    }

}

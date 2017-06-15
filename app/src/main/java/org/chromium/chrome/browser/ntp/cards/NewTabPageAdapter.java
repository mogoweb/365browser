// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp.cards;

import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.Adapter;
import android.view.View;
import android.view.ViewGroup;

import org.chromium.base.Callback;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.ntp.ContextMenuManager;
import org.chromium.chrome.browser.ntp.cards.NewTabPageViewHolder.PartialBindCallback;
import org.chromium.chrome.browser.ntp.snippets.SectionHeaderViewHolder;
import org.chromium.chrome.browser.ntp.snippets.SnippetArticleViewHolder;
import org.chromium.chrome.browser.offlinepages.OfflinePageBridge;
import org.chromium.chrome.browser.suggestions.SuggestionsRecyclerView;
import org.chromium.chrome.browser.suggestions.SuggestionsUiDelegate;
import org.chromium.chrome.browser.suggestions.TileGrid;
import org.chromium.chrome.browser.suggestions.TileGroup;
import org.chromium.chrome.browser.widget.displaystyle.UiConfig;

import java.util.List;
import java.util.Set;

/**
 * A class that handles merging above the fold elements and below the fold cards into an adapter
 * that will be used to back the NTP RecyclerView. The first element in the adapter should always be
 * the above-the-fold view (containing the logo, search box, and most visited tiles) and subsequent
 * elements will be the cards shown to the user
 */
public class NewTabPageAdapter extends Adapter<NewTabPageViewHolder> implements NodeParent {
    private final SuggestionsUiDelegate mUiDelegate;
    private final ContextMenuManager mContextMenuManager;

    @Nullable
    private final View mAboveTheFoldView;
    private final UiConfig mUiConfig;
    private SuggestionsRecyclerView mRecyclerView;

    private final InnerNode mRoot;

    @Nullable
    private final AboveTheFoldItem mAboveTheFold;
    @Nullable
    private final TileGrid mTileGrid;
    private final SectionList mSections;
    private final SignInPromo mSigninPromo;
    private final AllDismissedItem mAllDismissed;
    private final Footer mFooter;
    private final SpacingItem mBottomSpacer;

    /**
     * Creates the adapter that will manage all the cards to display on the NTP.
     * @param uiDelegate used to interact with the rest of the system.
     * @param aboveTheFoldView the layout encapsulating all the above-the-fold elements
     *         (logo, search box, most visited tiles), or null if only suggestions should
     *         be displayed.
     * @param uiConfig the NTP UI configuration, to be passed to created views.
     * @param offlinePageBridge used to determine if articles are available.
     * @param contextMenuManager used to build context menus.
     * @param tileGroupDelegate if not null this is used to build a {@link TileGrid}.
     */
    public NewTabPageAdapter(SuggestionsUiDelegate uiDelegate, @Nullable View aboveTheFoldView,
            UiConfig uiConfig, OfflinePageBridge offlinePageBridge,
            ContextMenuManager contextMenuManager, @Nullable TileGroup.Delegate tileGroupDelegate) {
        mUiDelegate = uiDelegate;
        mContextMenuManager = contextMenuManager;

        mAboveTheFoldView = aboveTheFoldView;
        mUiConfig = uiConfig;
        mRoot = new InnerNode();

        mSections = new SectionList(mUiDelegate, offlinePageBridge);
        mSigninPromo = new SignInPromo(mUiDelegate);
        mAllDismissed = new AllDismissedItem();
        mFooter = new Footer();

        if (mAboveTheFoldView == null) {
            mAboveTheFold = null;
        } else {
            mAboveTheFold = new AboveTheFoldItem();
            mRoot.addChild(mAboveTheFold);
        }
        if (tileGroupDelegate == null) {
            mTileGrid = null;
        } else {
            mTileGrid = new TileGrid(
                    uiDelegate, mContextMenuManager, tileGroupDelegate, offlinePageBridge);
            mRoot.addChild(mTileGrid);
        }
        mRoot.addChildren(mSections, mSigninPromo, mAllDismissed, mFooter);
        if (mAboveTheFoldView == null
                || ChromeFeatureList.isEnabled(ChromeFeatureList.NTP_CONDENSED_LAYOUT)) {
            mBottomSpacer = null;
        } else {
            mBottomSpacer = new SpacingItem();
            mRoot.addChild(mBottomSpacer);
        }

        updateAllDismissedVisibility();
        mRoot.setParent(this);
    }

    @Override
    @ItemViewType
    public int getItemViewType(int position) {
        return mRoot.getItemViewType(position);
    }

    @Override
    public NewTabPageViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        assert parent == mRecyclerView;

        switch (viewType) {
            case ItemViewType.ABOVE_THE_FOLD:
                return new NewTabPageViewHolder(mAboveTheFoldView);

            case ItemViewType.TILE_GRID:
                return new TileGrid.ViewHolder(mRecyclerView);

            case ItemViewType.HEADER:
                return new SectionHeaderViewHolder(mRecyclerView, mUiConfig);

            case ItemViewType.SNIPPET:
                return new SnippetArticleViewHolder(
                        mRecyclerView, mContextMenuManager, mUiDelegate, mUiConfig);

            case ItemViewType.SPACING:
                return new NewTabPageViewHolder(SpacingItem.createView(parent));

            case ItemViewType.STATUS:
                return new StatusCardViewHolder(mRecyclerView, mContextMenuManager, mUiConfig);

            case ItemViewType.PROGRESS:
                return new ProgressViewHolder(mRecyclerView);

            case ItemViewType.ACTION:
                return new ActionItem.ViewHolder(
                        mRecyclerView, mContextMenuManager, mUiDelegate, mUiConfig);

            case ItemViewType.PROMO:
                return new SignInPromo.ViewHolder(mRecyclerView, mContextMenuManager, mUiConfig);

            case ItemViewType.FOOTER:
                return new Footer.ViewHolder(mRecyclerView, mUiDelegate.getNavigationDelegate());

            case ItemViewType.ALL_DISMISSED:
                return new AllDismissedItem.ViewHolder(mRecyclerView, mSections);
        }

        assert false : viewType;
        return null;
    }

    @Override
    public void onBindViewHolder(NewTabPageViewHolder holder, int position, List<Object> payloads) {
        if (payloads.isEmpty()) {
            mRoot.onBindViewHolder(holder, position);
            return;
        }

        for (Object payload : payloads) {
            assert payload instanceof PartialBindCallback;
            ((PartialBindCallback) payload).onResult(holder);
        }
    }

    @Override
    public void onBindViewHolder(NewTabPageViewHolder holder, final int position) {
        mRoot.onBindViewHolder(holder, position);
    }

    @Override
    public int getItemCount() {
        return mRoot.getItemCount();
    }

    /** Resets suggestions, pulling the current state as known by the backend. */
    public void refreshSuggestions() {
        if (ChromeFeatureList.isEnabled(ChromeFeatureList.CHROME_HOME)) {
            mSections.synchroniseWithSource();
        } else {
            mSections.refreshSuggestions();
        }

        if (mTileGrid != null) mTileGrid.getTileGroup().onSwitchToForeground();
    }

    public int getAboveTheFoldPosition() {
        if (mAboveTheFoldView == null) return RecyclerView.NO_POSITION;

        return getChildPositionOffset(mAboveTheFold);
    }

    public int getFirstHeaderPosition() {
        return getFirstPositionForType(ItemViewType.HEADER);
    }

    public int getFirstCardPosition() {
        for (int i = 0; i < getItemCount(); ++i) {
            if (CardViewHolder.isCard(getItemViewType(i))) return i;
        }
        return RecyclerView.NO_POSITION;
    }

    int getLastContentItemPosition() {
        return getChildPositionOffset(hasAllBeenDismissed() ? mAllDismissed : mFooter);
    }

    int getBottomSpacerPosition() {
        if (mBottomSpacer == null) return RecyclerView.NO_POSITION;

        return getChildPositionOffset(mBottomSpacer);
    }

    private void updateAllDismissedVisibility() {
        boolean showAllDismissed = hasAllBeenDismissed();
        mAllDismissed.setVisible(showAllDismissed);
        mFooter.setVisible(!showAllDismissed);
    }

    @Override
    public void onItemRangeChanged(TreeNode child, int itemPosition, int itemCount,
            @Nullable PartialBindCallback callback) {
        assert child == mRoot;
        notifyItemRangeChanged(itemPosition, itemCount, callback);
    }

    @Override
    public void onItemRangeInserted(TreeNode child, int itemPosition, int itemCount) {
        assert child == mRoot;
        notifyItemRangeInserted(itemPosition, itemCount);
        if (mBottomSpacer != null) mBottomSpacer.refresh();

        updateAllDismissedVisibility();
    }

    @Override
    public void onItemRangeRemoved(TreeNode child, int itemPosition, int itemCount) {
        assert child == mRoot;
        notifyItemRangeRemoved(itemPosition, itemCount);
        if (mBottomSpacer != null) mBottomSpacer.refresh();

        updateAllDismissedVisibility();
    }

    @Override
    public void onAttachedToRecyclerView(RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);

        // We are assuming for now that the adapter is used with a single RecyclerView.
        // Getting the reference as we are doing here is going to be broken if that changes.
        assert mRecyclerView == null;

        // FindBugs chokes on the cast below when not checked, raising BC_UNCONFIRMED_CAST
        assert recyclerView instanceof SuggestionsRecyclerView;

        mRecyclerView = (SuggestionsRecyclerView) recyclerView;
    }

    /**
     * @return the set of item positions that should be dismissed simultaneously when dismissing the
     *         item at the given {@code position} (including the position itself), or an empty set
     *         if the item can't be dismissed.
     */
    public Set<Integer> getItemDismissalGroup(int position) {
        return mRoot.getItemDismissalGroup(position);
    }

    /**
     * Dismisses the item at the provided adapter position. Can also cause the dismissal of other
     * items or even entire sections.
     * @param position the position of an item to be dismissed.
     * @param itemRemovedCallback
     */
    public void dismissItem(int position, Callback<String> itemRemovedCallback) {
        mRoot.dismissItem(position, itemRemovedCallback);
    }

    private boolean hasAllBeenDismissed() {
        return mSections.isEmpty() && !mSigninPromo.isVisible();
    }

    private int getChildPositionOffset(TreeNode child) {
        return mRoot.getStartingOffsetForChild(child);
    }

    @VisibleForTesting
    int getFirstPositionForType(@ItemViewType int viewType) {
        int count = getItemCount();
        for (int i = 0; i < count; i++) {
            if (getItemViewType(i) == viewType) return i;
        }
        return RecyclerView.NO_POSITION;
    }

    SectionList getSectionListForTesting() {
        return mSections;
    }

    InnerNode getRootForTesting() {
        return mRoot;
    }
}

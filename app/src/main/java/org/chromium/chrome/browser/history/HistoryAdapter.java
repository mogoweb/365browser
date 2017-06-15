// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.history;

import android.content.res.Resources;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.history.HistoryProvider.BrowsingHistoryObserver;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.widget.DateDividedAdapter;
import org.chromium.chrome.browser.widget.displaystyle.MarginResizer;
import org.chromium.chrome.browser.widget.selection.SelectableItemViewHolder;
import org.chromium.chrome.browser.widget.selection.SelectableListLayout;
import org.chromium.chrome.browser.widget.selection.SelectionDelegate;
import org.chromium.chrome.browser.widget.selection.SelectionDelegate.SelectionObserver;
import org.chromium.components.signin.ChromeSigninController;
import org.chromium.ui.text.NoUnderlineClickableSpan;
import org.chromium.ui.text.SpanApplier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Bridges the user's browsing history and the UI used to display it.
 */
public class HistoryAdapter extends DateDividedAdapter implements BrowsingHistoryObserver {
    private static final String EMPTY_QUERY = "";
    private static final String LEARN_MORE_LINK =
            "https://support.google.com/chrome/?p=sync_history&amp;hl="
                    + Locale.getDefault().toString();
    private static final String GOOGLE_HISTORY_LINK = "history.google.com";
    private static final String MY_ACTIVITY_LINK = "myactivity.google.com";

    private final SelectionDelegate<HistoryItem> mSelectionDelegate;
    private final HistoryProvider mHistoryProvider;
    private final HistoryManager mHistoryManager;
    private final ArrayList<HistoryItemView> mItemViews;
    private RecyclerView mRecyclerView;

    private ViewGroup mPrivacyDisclaimers;
    private TextView mSignedInNotSyncedTextView;
    private TextView mSignedInSyncedTextView;
    private TextView mOtherFormsOfBrowsingHistoryTextView;
    private Button mClearBrowsingDataButton;
    private FrameLayout mClearBrowsingDataButtonContainer;

    private boolean mHasOtherFormsOfBrowsingData;
    private boolean mHasSyncedData;
    private boolean mIsHeaderInflated;
    private boolean mIsDestroyed;
    private boolean mIsInitialized;
    private boolean mIsLoadingItems;
    private boolean mIsSearching;
    private boolean mHasMorePotentialItems;
    private boolean mClearOnNextQueryComplete;
    private long mNextQueryEndTime;
    private String mQueryText = EMPTY_QUERY;
    private int mDefaultTextMargin;

    public HistoryAdapter(SelectionDelegate<HistoryItem> delegate, HistoryManager manager,
            HistoryProvider provider) {
        setHasStableIds(true);
        mSelectionDelegate = delegate;
        mHistoryProvider = provider;
        mHistoryProvider.setObserver(this);
        mHistoryManager = manager;
        mItemViews = new ArrayList<>();
    }

    /**
     * Called when the activity/native page is destroyed.
     */
    public void onDestroyed() {
        mHistoryProvider.destroy();
        mIsDestroyed = true;
        mRecyclerView = null;
    }

    /**
     * Initializes the HistoryAdapter and loads the first set of browsing history items.
     */
    public void initialize() {
        mIsInitialized = false;
        mIsLoadingItems = true;
        mNextQueryEndTime = 0;
        mClearOnNextQueryComplete = true;
        mHistoryProvider.queryHistory(mQueryText, mNextQueryEndTime);
    }

    @Override
    public void onAttachedToRecyclerView(RecyclerView recyclerView) {
        // This adapter should only ever be attached to one RecyclerView.
        assert mRecyclerView == null;

        mRecyclerView = recyclerView;
    }

    @Override
    public void onDetachedFromRecyclerView(RecyclerView recyclerView) {
        mRecyclerView = null;
    }

    /**
     * Load more browsing history items. Returns early if more items are already being loaded or
     * there are no more items to load.
     */
    public void loadMoreItems() {
        if (!canLoadMoreItems()) return;

        mIsLoadingItems = true;
        addFooter();
        notifyDataSetChanged();
        mHistoryProvider.queryHistory(mQueryText, mNextQueryEndTime);
    }

    /**
     * @return Whether more items can be loaded right now.
     */
    public boolean canLoadMoreItems() {
        return !mIsLoadingItems && mHasMorePotentialItems;
    }

    /**
     * Called to perform a search.
     * @param query The text to search for.
     */
    public void search(String query) {
        mQueryText = query;
        mNextQueryEndTime = 0;
        mIsSearching = true;
        mClearOnNextQueryComplete = true;
        mHistoryProvider.queryHistory(mQueryText, mNextQueryEndTime);
    }

    /**
     * Called when a search is ended.
     */
    public void onEndSearch() {
        mQueryText = EMPTY_QUERY;
        mIsSearching = false;

        // Re-initialize the data in the adapter.
        initialize();
    }

    /**
     * Adds the HistoryItem to the list of items being removed and removes it from the adapter. The
     * removal will not be committed until #removeItems() is called.
     * @param item The item to mark for removal.
     */
    public void markItemForRemoval(HistoryItem item) {
        removeItem(item);
        mHistoryProvider.markItemForRemoval(item);

        // If there is only one item left, remove the header so the empty view will be displayed.
        if (getItemCount() == 1) {
            removeHeader();
        }
    }

    /**
     * Removes all items that have been marked for removal through #markItemForRemoval().
     */
    public void removeItems() {
        mHistoryProvider.removeItems();

        // TODO(twellington): this could be optimized by only setting the background for item views
        //                    in a group that has changed.
        for (HistoryItemView itemView : mItemViews) {
            itemView.setBackgroundResourceForGroupPosition();
        }
    }

    /**
     * Should be called when the user's sign in state changes.
     */
    public void onSignInStateChange() {
        for (HistoryItemView itemView : mItemViews) {
            itemView.onSignInStateChange();
        }
        updateClearBrowsingDataButtonVisibility();
    }

    /**
     * See {@link SelectionObserver}.
     */
    public void onSelectionStateChange(boolean selectionEnabled) {
        if (mClearBrowsingDataButton != null) {
            mClearBrowsingDataButton.setEnabled(!selectionEnabled);
        }
        for (HistoryItemView item : mItemViews) {
            item.setRemoveButtonVisible(!selectionEnabled);
        }
    }

    @Override
    protected ViewHolder createViewHolder(ViewGroup parent) {
        View v = LayoutInflater.from(parent.getContext()).inflate(
                R.layout.history_item_view, parent, false);
        SelectableItemViewHolder<HistoryItem> viewHolder =
                new SelectableItemViewHolder<>(v, mSelectionDelegate);
        HistoryItemView itemView = (HistoryItemView) viewHolder.itemView;
        itemView.configureWideDisplayStyle(mHistoryManager.getSelectableListLayout().getUiConfig());
        itemView.setRemoveButtonVisible(!mSelectionDelegate.isSelectionEnabled());
        mItemViews.add(itemView);
        return viewHolder;
    }

    @Override
    protected void bindViewHolderForTimedItem(ViewHolder current, TimedItem timedItem) {
        final HistoryItem item = (HistoryItem) timedItem;
        @SuppressWarnings("unchecked")
        SelectableItemViewHolder<HistoryItem> holder =
                (SelectableItemViewHolder<HistoryItem>) current;
        holder.displayItem(item);
        ((HistoryItemView) holder.itemView).setHistoryManager(mHistoryManager);
    }

    @Override
    protected int getTimedItemViewResId() {
        return R.layout.date_view;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onQueryHistoryComplete(List<HistoryItem> items, boolean hasMorePotentialMatches) {
        // Return early if the results are returned after the activity/native page is
        // destroyed to avoid unnecessary work.
        if (mIsDestroyed) return;

        if (mClearOnNextQueryComplete) {
            clear(true);
            mClearOnNextQueryComplete = false;
        }

        boolean wasInitialized = mIsInitialized;
        if (!mIsInitialized) {
            if (items.size() > 0 && !mIsSearching) addHeader();
            mIsInitialized = true;
        }

        removeFooter();

        HistoryItemView lastHistoryItemView = null;
        if (mRecyclerView != null && wasInitialized) {
            ViewHolder holder = mRecyclerView.findViewHolderForAdapterPosition(getItemCount() - 1);
            if (holder instanceof SelectableItemViewHolder) {
                lastHistoryItemView =
                        (HistoryItemView) ((SelectableItemViewHolder<HistoryItem>) holder).itemView;
            }
        }

        loadItems(items);

        if (lastHistoryItemView != null) {
            // When loading more items, the last item's background needs to be reset since it may
            // now be in the middle of an ItemGroup.
            lastHistoryItemView.setBackgroundResourceForGroupPosition();
        }

        mIsLoadingItems = false;
        mHasMorePotentialItems = hasMorePotentialMatches;
        if (items.size() > 0) {
            mNextQueryEndTime = items.get(items.size() - 1).getTimestamp();
        }
    }

    @Override
    public void onHistoryDeleted() {
        mSelectionDelegate.clearSelection();
        // TODO(twellington): Account for items that have been paged in due to infinite scroll.
        //                    This currently removes all items and re-issues a query.
        initialize();
    }

    @Override
    public void hasOtherFormsOfBrowsingData(boolean hasOtherForms, boolean hasSyncedResults) {
        mHasOtherFormsOfBrowsingData = hasOtherForms;
        mHasSyncedData = hasSyncedResults;
        setPrivacyDisclaimerVisibility();
    }

    @Override
    protected BasicViewHolder createHeader(ViewGroup parent) {
        ViewGroup v = (ViewGroup) LayoutInflater.from(parent.getContext()).inflate(
                R.layout.history_header, parent, false);
        Resources resources = v.getResources();
        mIsHeaderInflated = true;

        mClearBrowsingDataButton = (Button) v.findViewById(R.id.clear_browsing_data_button);
        mClearBrowsingDataButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mHistoryManager.openClearBrowsingDataPreference();
            }
        });
        mClearBrowsingDataButtonContainer = (FrameLayout) mClearBrowsingDataButton.getParent();
        MarginResizer.createWithViewAdapter(mClearBrowsingDataButtonContainer,
                mHistoryManager.getSelectableListLayout().getUiConfig(),
                SelectableListLayout.getDefaultListItemLateralMarginPx(resources), 0);
        updateClearBrowsingDataButtonVisibility();

        mPrivacyDisclaimers = (ViewGroup) v.findViewById(R.id.privacy_disclaimers);

        mSignedInNotSyncedTextView = (TextView) v.findViewById(R.id.signed_in_not_synced);
        setPrivacyDisclaimerText(mSignedInNotSyncedTextView,
                R.string.android_history_no_synced_results, LEARN_MORE_LINK);
        MarginResizer.createWithViewAdapter(mSignedInNotSyncedTextView,
                mHistoryManager.getSelectableListLayout().getUiConfig(),
                getDefaultTextMargin(resources),
                SelectableListLayout.getDefaultListItemLateralShadowSizePx(resources));

        mSignedInSyncedTextView = (TextView) v.findViewById(R.id.signed_in_synced);
        setPrivacyDisclaimerText(mSignedInSyncedTextView,
                R.string.android_history_has_synced_results, LEARN_MORE_LINK);
        MarginResizer.createWithViewAdapter(mSignedInSyncedTextView,
                mHistoryManager.getSelectableListLayout().getUiConfig(),
                getDefaultTextMargin(resources),
                SelectableListLayout.getDefaultListItemLateralShadowSizePx(resources));

        mOtherFormsOfBrowsingHistoryTextView = (TextView) v.findViewById(
                R.id.other_forms_of_browsing_history);
        boolean flagEnabled = ChromeFeatureList.isEnabled(ChromeFeatureList.TABS_IN_CBD);
        int disclaimerTextId = flagEnabled ? R.string.android_history_other_forms_of_history_new
                                           : R.string.android_history_other_forms_of_history;
        String disclaimerUrl = flagEnabled ? MY_ACTIVITY_LINK : GOOGLE_HISTORY_LINK;
        setPrivacyDisclaimerText(
                mOtherFormsOfBrowsingHistoryTextView, disclaimerTextId, disclaimerUrl);
        MarginResizer.createWithViewAdapter(mOtherFormsOfBrowsingHistoryTextView,
                mHistoryManager.getSelectableListLayout().getUiConfig(),
                getDefaultTextMargin(resources),
                SelectableListLayout.getDefaultListItemLateralShadowSizePx(resources));

        setPrivacyDisclaimerVisibility();

        return new BasicViewHolder(v);
    }

    @Override
    protected BasicViewHolder createFooter(ViewGroup parent) {
        return new BasicViewHolder(LayoutInflater.from(parent.getContext()).inflate(
                R.layout.indeterminate_progress_view, parent, false));
    }

    @Override
    protected DateViewHolder createDateViewHolder(ViewGroup parent) {
        DateViewHolder viewHolder = super.createDateViewHolder(parent);
        MarginResizer.createWithViewAdapter(viewHolder.itemView,
                mHistoryManager.getSelectableListLayout().getUiConfig(),
                getDefaultTextMargin(parent.getResources()),
                SelectableListLayout.getDefaultListItemLateralShadowSizePx(parent.getResources()));
        return viewHolder;
    }

    private void setPrivacyDisclaimerText(TextView view, int stringId, final  String url) {
        NoUnderlineClickableSpan link = new NoUnderlineClickableSpan() {
            @Override
            public void onClick(View view) {
                mHistoryManager.openUrl(url, null, true);
            }
        };
        SpannableString spannable = SpanApplier.applySpans(
                view.getResources().getString(stringId),
                new SpanApplier.SpanInfo("<link>", "</link>", link));
        view.setText(spannable);
        view.setMovementMethod(LinkMovementMethod.getInstance());
    }

    private void setPrivacyDisclaimerVisibility() {
        if (!mIsHeaderInflated) return;

        boolean isSignedIn = ChromeSigninController.get().isSignedIn();
        mSignedInNotSyncedTextView.setVisibility(
                !mHasSyncedData && isSignedIn ? View.VISIBLE : View.GONE);
        mSignedInSyncedTextView.setVisibility(mHasSyncedData ? View.VISIBLE : View.GONE);
        mOtherFormsOfBrowsingHistoryTextView.setVisibility(
                mHasOtherFormsOfBrowsingData ? View.VISIBLE : View.GONE);

        boolean arePrivacyDisclaimersVisible =
                isSignedIn || mHasSyncedData || mHasOtherFormsOfBrowsingData;
        mPrivacyDisclaimers.setVisibility(arePrivacyDisclaimersVisible ? View.VISIBLE : View.GONE);
    }

    private void updateClearBrowsingDataButtonVisibility() {
        // If the history header is not showing (e.g. when there is no browsing history),
        // mClearBrowsingDataButton will be null.
        if (mClearBrowsingDataButton == null) return;

        mClearBrowsingDataButtonContainer.setVisibility(
                !PrefServiceBridge.getInstance().canDeleteBrowsingHistory() ? View.GONE :
                    View.VISIBLE);
    }

    private int getDefaultTextMargin(Resources resources) {
        if (mDefaultTextMargin == 0) {
            mDefaultTextMargin = resources.getDimensionPixelSize(R.dimen.list_item_default_margin);
        }
        return mDefaultTextMargin;
    }

    @VisibleForTesting
    TextView getSignedInNotSyncedViewForTests() {
        return mSignedInNotSyncedTextView;
    }

    @VisibleForTesting
    TextView getSignedInSyncedViewForTests() {
        return mSignedInSyncedTextView;
    }

    @VisibleForTesting
    TextView getOtherFormsOfBrowsingHistoryViewForTests() {
        return mOtherFormsOfBrowsingHistoryTextView;
    }
}

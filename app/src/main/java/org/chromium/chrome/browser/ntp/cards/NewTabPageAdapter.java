// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp.cards;

import android.graphics.Canvas;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.Adapter;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.view.View;
import android.view.ViewGroup;

import org.chromium.base.Callback;
import org.chromium.base.Log;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ntp.NewTabPageUma;
import org.chromium.chrome.browser.ntp.NewTabPageView.NewTabPageManager;
import org.chromium.chrome.browser.ntp.UiConfig;
import org.chromium.chrome.browser.ntp.snippets.CategoryInt;
import org.chromium.chrome.browser.ntp.snippets.CategoryStatus;
import org.chromium.chrome.browser.ntp.snippets.CategoryStatus.CategoryStatusEnum;
import org.chromium.chrome.browser.ntp.snippets.SectionHeader;
import org.chromium.chrome.browser.ntp.snippets.SectionHeaderViewHolder;
import org.chromium.chrome.browser.ntp.snippets.SnippetArticle;
import org.chromium.chrome.browser.ntp.snippets.SnippetArticleViewHolder;
import org.chromium.chrome.browser.ntp.snippets.SnippetsBridge;
import org.chromium.chrome.browser.ntp.snippets.SuggestionsSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A class that handles merging above the fold elements and below the fold cards into an adapter
 * that will be used to back the NTP RecyclerView. The first element in the adapter should always be
 * the above-the-fold view (containing the logo, search box, and most visited tiles) and subsequent
 * elements will be the cards shown to the user
 */
public class NewTabPageAdapter extends Adapter<NewTabPageViewHolder>
        implements SuggestionsSource.Observer {
    private static final String TAG = "Ntp";

    private final NewTabPageManager mNewTabPageManager;
    private final View mAboveTheFoldView;
    private SuggestionsSource mSuggestionsSource;
    private final UiConfig mUiConfig;
    private final ItemTouchCallbacks mItemTouchCallbacks = new ItemTouchCallbacks();
    private NewTabPageRecyclerView mRecyclerView;

    /**
     * List of all item groups (which can themselves contain multiple items. When flattened, this
     * will be a list of all items the adapter exposes.
     */
    private final List<ItemGroup> mGroups = new ArrayList<>();
    private final AboveTheFoldItem mAboveTheFold = new AboveTheFoldItem();
    private final Footer mFooter = new Footer();
    private final SpacingItem mBottomSpacer = new SpacingItem();

    /** Maps suggestion categories to sections, with stable iteration ordering. */
    private final Map<Integer, SuggestionsSection> mSections = new LinkedHashMap<>();

    private class ItemTouchCallbacks extends ItemTouchHelper.Callback {
        @Override
        public void onSwiped(ViewHolder viewHolder, int direction) {
            mRecyclerView.onItemDismissStarted(viewHolder.itemView);
            NewTabPageAdapter.this.dismissItem(viewHolder.getAdapterPosition());
        }

        @Override
        public void clearView(RecyclerView recyclerView, ViewHolder viewHolder) {
            // clearView() is called when an interaction with the item is finished, which does
            // not mean that the user went all the way and dismissed the item before releasing it.
            // We need to check that the item has been removed.
            if (viewHolder.getAdapterPosition() == RecyclerView.NO_POSITION) {
                mRecyclerView.onItemDismissFinished(viewHolder.itemView);
            }

            super.clearView(recyclerView, viewHolder);
        }

        @Override
        public boolean onMove(RecyclerView recyclerView, ViewHolder viewHolder, ViewHolder target) {
            assert false; // Drag and drop not supported, the method will never be called.
            return false;
        }

        @Override
        public int getMovementFlags(RecyclerView recyclerView, ViewHolder viewHolder) {
            assert viewHolder instanceof NewTabPageViewHolder;

            int swipeFlags = 0;
            if (((NewTabPageViewHolder) viewHolder).isDismissable()) {
                swipeFlags = ItemTouchHelper.START | ItemTouchHelper.END;
            }

            return makeMovementFlags(0 /* dragFlags */, swipeFlags);
        }

        @Override
        public void onChildDraw(Canvas c, RecyclerView recyclerView, ViewHolder viewHolder,
                float dX, float dY, int actionState, boolean isCurrentlyActive) {
            assert viewHolder instanceof NewTabPageViewHolder;

            mRecyclerView.updateViewStateForDismiss(dX, viewHolder);

            // The super implementation performs animation and elevation, but only the animation is
            // needed.
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            ViewCompat.setElevation(viewHolder.itemView, 0f);
        }
    }

    /**
     * Constructor to create the manager for all the cards to display on the NTP
     *
     * @param manager the NewTabPageManager to use to interact with the rest of the system.
     * @param aboveTheFoldView the layout encapsulating all the above-the-fold elements
     *                         (logo, search box, most visited tiles)
     * @param suggestionsSource the bridge to interact with the content suggestions service.
     * @param uiConfig the NTP UI configuration, to be passed to created views.
     */
    public NewTabPageAdapter(NewTabPageManager manager, View aboveTheFoldView,
            SuggestionsSource suggestionsSource, UiConfig uiConfig) {
        mNewTabPageManager = manager;
        mAboveTheFoldView = aboveTheFoldView;
        mSuggestionsSource = suggestionsSource;
        mUiConfig = uiConfig;

        int[] categories = mSuggestionsSource.getCategories();
        int[] suggestionsPerCategory = new int[categories.length];
        int i = 0;
        for (int category : categories) {
            int categoryStatus = suggestionsSource.getCategoryStatus(category);
            assert categoryStatus != CategoryStatus.NOT_PROVIDED;
            if (categoryStatus == CategoryStatus.LOADING_ERROR
                    || categoryStatus == CategoryStatus.CATEGORY_EXPLICITLY_DISABLED)
                continue;

            List<SnippetArticle> suggestions =
                    suggestionsSource.getSuggestionsForCategory(category);
            suggestionsPerCategory[i++] = suggestions.size();

            // Create the new section.
            SuggestionsCategoryInfo info = mSuggestionsSource.getCategoryInfo(category);
            if (suggestions.isEmpty() && !info.showIfEmpty()) continue;
            mSections.put(category, new SuggestionsSection(category, info, this));

            // Add the new suggestions.
            setSuggestions(category, suggestions, categoryStatus);
        }
        // |mNewTabPageManager| is null in some tests.
        if (mNewTabPageManager != null) {
            mNewTabPageManager.trackSnippetsPageImpression(categories, suggestionsPerCategory);
        }
        suggestionsSource.setObserver(this);
        updateGroups();
    }

    /** Returns callbacks to configure the interactions with the RecyclerView's items. */
    public ItemTouchHelper.Callback getItemTouchCallbacks() {
        return mItemTouchCallbacks;
    }

    @Override
    public void onNewSuggestions(@CategoryInt int category) {
        // We never want to add suggestions from unknown categories.
        if (!mSections.containsKey(category)) return;

        // We never want to refresh the suggestions if we already have some content.
        if (mSections.get(category).hasSuggestions()) return;

        // The status may have changed while the suggestions were loading, perhaps they should not
        // be displayed any more.
        @CategoryStatusEnum
        int status = mSuggestionsSource.getCategoryStatus(category);
        if (!SnippetsBridge.isCategoryEnabled(status)) {
            Log.w(TAG, "Received suggestions for a disabled category (id=%d, status=%d)", category,
                    status);
            return;
        }

        List<SnippetArticle> suggestions =
                mSuggestionsSource.getSuggestionsForCategory(category);

        Log.d(TAG, "Received %d new suggestions for category %d.", suggestions.size(), category);

        // At first, there might be no suggestions available, we wait until they have been fetched.
        if (suggestions.isEmpty()) return;

        setSuggestions(category, suggestions, status);
        updateGroups();

        NewTabPageUma.recordSnippetAction(NewTabPageUma.SNIPPETS_ACTION_SHOWN);
    }

    @Override
    public void onCategoryStatusChanged(@CategoryInt int category, @CategoryStatusEnum int status) {
        // Observers should not be registered for this state.
        assert status != CategoryStatus.ALL_SUGGESTIONS_EXPLICITLY_DISABLED;

        // If there is no section for this category there is nothing to do.
        if (!mSections.containsKey(category)) return;

        // The section provider has gone away. Keep open UIs as they are.
        if (status == CategoryStatus.NOT_PROVIDED) return;

        if (status == CategoryStatus.CATEGORY_EXPLICITLY_DISABLED
                || status == CategoryStatus.LOADING_ERROR) {
            // Need to remove the entire section from the UI immediately.
            mSections.remove(category);
        } else {
            mSections.get(category).setStatus(status);
        }
        updateGroups();
    }

    @Override
    public void onSuggestionInvalidated(@CategoryInt int category, String suggestionId) {
        if (!mSections.containsKey(category)) return;
        mSections.get(category).removeSuggestionById(suggestionId);
        updateGroups();
    }

    @Override
    @NewTabPageItem.ViewType
    public int getItemViewType(int position) {
        return getItems().get(position).getType();
    }

    @Override
    public NewTabPageViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        assert parent == mRecyclerView;

        if (viewType == NewTabPageItem.VIEW_TYPE_ABOVE_THE_FOLD) {
            return new NewTabPageViewHolder(mAboveTheFoldView);
        }

        if (viewType == NewTabPageItem.VIEW_TYPE_HEADER) {
            return new SectionHeaderViewHolder(mRecyclerView, mUiConfig);
        }

        if (viewType == NewTabPageItem.VIEW_TYPE_SNIPPET) {
            return new SnippetArticleViewHolder(mRecyclerView, mNewTabPageManager,
                    mSuggestionsSource, mUiConfig);
        }

        if (viewType == NewTabPageItem.VIEW_TYPE_SPACING) {
            return new NewTabPageViewHolder(SpacingItem.createView(parent));
        }

        if (viewType == NewTabPageItem.VIEW_TYPE_STATUS) {
            return new StatusItem.ViewHolder(mRecyclerView, mUiConfig);
        }

        if (viewType == NewTabPageItem.VIEW_TYPE_PROGRESS) {
            return new ProgressViewHolder(mRecyclerView);
        }

        if (viewType == NewTabPageItem.VIEW_TYPE_ACTION) {
            return new ActionItem.ViewHolder(mRecyclerView, mNewTabPageManager, mUiConfig);
        }

        if (viewType == NewTabPageItem.VIEW_TYPE_FOOTER) {
            return new Footer.ViewHolder(mRecyclerView, mNewTabPageManager);
        }

        return null;
    }

    @Override
    public void onBindViewHolder(NewTabPageViewHolder holder, final int position) {
        holder.onBindViewHolder(getItems().get(position));
    }

    @Override
    public int getItemCount() {
        return getItems().size();
    }

    public int getAboveTheFoldPosition() {
        return getGroupPositionOffset(mAboveTheFold);
    }

    public int getFirstHeaderPosition() {
        List<NewTabPageItem> items = getItems();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i) instanceof SectionHeader) return i;
        }
        return RecyclerView.NO_POSITION;
    }

    public int getFirstCardPosition() {
        // TODO(mvanouwerkerk): Don't rely on getFirstHeaderPosition() here.
        int firstHeaderPosition = getFirstHeaderPosition();
        if (firstHeaderPosition == RecyclerView.NO_POSITION) return RecyclerView.NO_POSITION;
        return firstHeaderPosition + 1;
    }

    public int getLastContentItemPosition() {
        return getGroupPositionOffset(mFooter);
    }

    public int getBottomSpacerPosition() {
        return getGroupPositionOffset(mBottomSpacer);
    }

    public int getSuggestionPosition(String suggestionId) {
        List<NewTabPageItem> items = getItems();
        for (int i = 0; i < items.size(); i++) {
            NewTabPageItem item = items.get(i);
            if (item instanceof SnippetArticle
                    && ((SnippetArticle) item).mId.equals(suggestionId)) {
                return i;
            }
        }
        return RecyclerView.NO_POSITION;
    }

    /** Start a request for new snippets. */
    public void reloadSnippets() {
        SnippetsBridge.fetchSnippets(/*forceRequest=*/true);
    }

    private void setSuggestions(@CategoryInt int category, List<SnippetArticle> suggestions,
            @CategoryStatusEnum int status) {
        // Count the number of suggestions before this category.
        int globalPositionOffset = 0;
        for (Map.Entry<Integer, SuggestionsSection> entry : mSections.entrySet()) {
            if (entry.getKey() == category) break;
            globalPositionOffset += entry.getValue().getSuggestionsCount();
        }
        // Assign global indices to the new suggestions.
        for (SnippetArticle suggestion : suggestions) {
            suggestion.mGlobalPosition = globalPositionOffset + suggestion.mPosition;
        }

        mSections.get(category).setSuggestions(suggestions, status);
    }

    private void updateGroups() {
        mGroups.clear();
        mGroups.add(mAboveTheFold);
        // TODO(treib,bauerb): Preserve the order of categories we got from getCategories.
        mGroups.addAll(mSections.values());
        if (!mSections.isEmpty()) {
            mGroups.add(mFooter);
            mGroups.add(mBottomSpacer);
        }

        // TODO(bauerb): Notify about a smaller range.
        notifyDataSetChanged();
    }

    @Override
    public void onAttachedToRecyclerView(RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);

        // We are assuming for now that the adapter is used with a single RecyclerView.
        // Getting the reference as we are doing here is going to be broken if that changes.
        assert mRecyclerView == null;

        // FindBugs chokes on the cast below when not checked, raising BC_UNCONFIRMED_CAST
        assert recyclerView instanceof NewTabPageRecyclerView;

        mRecyclerView = (NewTabPageRecyclerView) recyclerView;
    }

    public void dismissItem(int position) {
        SnippetArticle suggestion = (SnippetArticle) getItems().get(position);
        mSuggestionsSource.getSuggestionVisited(suggestion, new Callback<Boolean>() {
            @Override
            public void onResult(Boolean result) {
                NewTabPageUma.recordSnippetAction(result
                                ? NewTabPageUma.SNIPPETS_ACTION_DISMISSED_VISITED
                                : NewTabPageUma.SNIPPETS_ACTION_DISMISSED_UNVISITED);
            }
        });

        mRecyclerView.announceForAccessibility(mRecyclerView.getResources().getString(
                R.string.ntp_accessibility_item_removed, suggestion.mTitle));

        mSuggestionsSource.dismissSuggestion(suggestion);
        SuggestionsSection section = (SuggestionsSection) getGroup(position);
        section.removeSuggestion(suggestion);

        if (section.hasSuggestions()) {
            // If one of many suggestions was dismissed, it's a simple item removal, which can be
            // animated smoothly by the RecyclerView.
            notifyItemRemoved(position);
        } else {
            // If the last suggestion was dismissed, multiple items will have changed, so mark
            // everything as changed.
            notifyDataSetChanged();
        }
    }

    /**
     * Returns an unmodifiable list containing all items in the adapter.
     */
    public List<NewTabPageItem> getItems() {
        List<NewTabPageItem> items = new ArrayList<>();
        for (ItemGroup group : mGroups) {
            items.addAll(group.getItems());
        }
        return Collections.unmodifiableList(items);
    }

    @VisibleForTesting
    ItemGroup getGroup(int itemPosition) {
        int itemsSkipped = 0;
        for (ItemGroup group : mGroups) {
            List<NewTabPageItem> items = group.getItems();
            itemsSkipped += items.size();
            if (itemPosition < itemsSkipped) return group;
        }
        return null;
    }

    @VisibleForTesting
    List<ItemGroup> getGroups() {
        return Collections.unmodifiableList(mGroups);
    }

    private int getGroupPositionOffset(ItemGroup group) {
        int positionOffset = 0;
        for (ItemGroup candidateGroup : mGroups) {
            if (candidateGroup == group) return positionOffset;
            positionOffset += candidateGroup.getItems().size();
        }
        return RecyclerView.NO_POSITION;
    }
}

// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp.cards;

import android.view.View;

import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ntp.ContextMenuManager;
import org.chromium.chrome.browser.ntp.snippets.CategoryInt;
import org.chromium.chrome.browser.suggestions.ContentSuggestionsAdditionalAction;
import org.chromium.chrome.browser.suggestions.SuggestionsMetrics;
import org.chromium.chrome.browser.suggestions.SuggestionsRanker;
import org.chromium.chrome.browser.suggestions.SuggestionsRecyclerView;
import org.chromium.chrome.browser.suggestions.SuggestionsUiDelegate;
import org.chromium.chrome.browser.widget.displaystyle.UiConfig;

/**
 * Item that allows the user to perform an action on the NTP.
 */
public class ActionItem extends OptionalLeaf {
    private final SuggestionsCategoryInfo mCategoryInfo;
    private final SuggestionsSection mParentSection;
    private final SuggestionsRanker mSuggestionsRanker;

    private boolean mImpressionTracked;
    private int mPerSectionRank = -1;

    public ActionItem(SuggestionsSection section, SuggestionsRanker ranker) {
        mCategoryInfo = section.getCategoryInfo();
        mParentSection = section;
        mSuggestionsRanker = ranker;
        setVisible(mCategoryInfo.getAdditionalAction() != ContentSuggestionsAdditionalAction.NONE);
    }

    @Override
    public int getItemViewType() {
        return ItemViewType.ACTION;
    }

    @Override
    protected void onBindViewHolder(NewTabPageViewHolder holder) {
        assert holder instanceof ViewHolder;
        mSuggestionsRanker.rankActionItem(this, mParentSection);
        ((ViewHolder) holder).onBindViewHolder(this);
    }

    @Override
    public void visitOptionalItem(NodeVisitor visitor) {
        visitor.visitActionItem(mCategoryInfo.getAdditionalAction());
    }

    @CategoryInt
    public int getCategory() {
        return mCategoryInfo.getCategory();
    }

    public void setPerSectionRank(int perSectionRank) {
        mPerSectionRank = perSectionRank;
    }

    public int getPerSectionRank() {
        return mPerSectionRank;
    }

    @VisibleForTesting
    void performAction(SuggestionsUiDelegate uiDelegate) {
        uiDelegate.getEventReporter().onMoreButtonClicked(this);

        switch (mCategoryInfo.getAdditionalAction()) {
            case ContentSuggestionsAdditionalAction.VIEW_ALL:
                SuggestionsMetrics.recordActionViewAll();
                mCategoryInfo.performViewAllAction(uiDelegate.getNavigationDelegate());
                return;
            case ContentSuggestionsAdditionalAction.FETCH:
                uiDelegate.getSuggestionsSource().fetchSuggestions(
                        mCategoryInfo.getCategory(), mParentSection.getDisplayedSuggestionIds());
                mParentSection.onFetchStarted();
                return;
            case ContentSuggestionsAdditionalAction.NONE:
            default:
                // Should never be reached.
                assert false;
        }
    }

    /** ViewHolder associated to {@link ItemViewType#ACTION}. */
    public static class ViewHolder extends CardViewHolder implements ContextMenuManager.Delegate {
        private ActionItem mActionListItem;

        public ViewHolder(final SuggestionsRecyclerView recyclerView,
                ContextMenuManager contextMenuManager, final SuggestionsUiDelegate uiDelegate,
                UiConfig uiConfig) {
            super(R.layout.new_tab_page_action_card, recyclerView, uiConfig, contextMenuManager);

            itemView.findViewById(R.id.action_button)
                    .setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            mActionListItem.performAction(uiDelegate);
                        }
                    });

            new ImpressionTracker(itemView, new ImpressionTracker.Listener() {
                @Override
                public void onImpression() {
                    if (mActionListItem != null && !mActionListItem.mImpressionTracked) {
                        mActionListItem.mImpressionTracked = true;
                        uiDelegate.getEventReporter().onMoreButtonShown(mActionListItem);
                    }
                }
            });
        }

        public void onBindViewHolder(ActionItem item) {
            super.onBindViewHolder();
            mActionListItem = item;
        }
    }
}

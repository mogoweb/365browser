// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp.cards;

import android.view.View;

import org.chromium.base.Log;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ntp.NewTabPageView.NewTabPageManager;
import org.chromium.chrome.browser.ntp.UiConfig;
import org.chromium.chrome.browser.ntp.snippets.KnownCategories;

/**
 * Item that allows the user to perform an action on the NTP.
 */
class ActionItem implements NewTabPageItem {
    private static final String TAG = "NtpCards";

    private final int mCategory;

    // The position (index) of this item within its section, for logging purposes.
    private int mPosition;
    private boolean mImpressionTracked = false;

    public ActionItem(int category) {
        mCategory = category;
    }

    @Override
    public int getType() {
        return NewTabPageItem.VIEW_TYPE_ACTION;
    }

    public int getPosition() {
        return mPosition;
    }

    public void setPosition(int position) {
        mPosition = position;
    }

    public static class ViewHolder extends CardViewHolder {
        private ActionItem mActionListItem;

        public ViewHolder(NewTabPageRecyclerView recyclerView, final NewTabPageManager manager,
                UiConfig uiConfig) {
            super(R.layout.new_tab_page_action_card, recyclerView, uiConfig);

            itemView.findViewById(R.id.action_button)
                    .setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            int category = mActionListItem.mCategory;
                            manager.trackSnippetCategoryActionClick(
                                    category, mActionListItem.mPosition);
                            if (category == KnownCategories.BOOKMARKS) {
                                manager.navigateToBookmarks();
                            } else if (category == KnownCategories.DOWNLOADS) {
                                manager.navigateToDownloadManager();
                            } else {
                                // TODO(pke): This should redirect to the C++ backend. Once it does,
                                // change the condition in the SuggestionsSection constructor.
                                Log.wtf(TAG,
                                        "More action called on a dynamically added section: %d",
                                        category);
                            }
                        }
                    });

            new ImpressionTracker(itemView, new ImpressionTracker.Listener() {
                @Override
                public void onImpression() {
                    if (mActionListItem != null && !mActionListItem.mImpressionTracked) {
                        mActionListItem.mImpressionTracked = true;
                        manager.trackSnippetCategoryActionImpression(
                                mActionListItem.mCategory, mActionListItem.mPosition);
                    }
                }
            });
        }

        @Override
        public void onBindViewHolder(NewTabPageItem item) {
            super.onBindViewHolder(item);
            mActionListItem = (ActionItem) item;
        }
    }
}

// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp.cards;

import org.chromium.chrome.browser.ntp.snippets.ContentSuggestionsCardLayout.ContentSuggestionsCardLayoutEnum;

/**
 * Contains static meta information about a Category. Equivalent of the CategoryInfo class in
 * components/ntp_snippets/category_info.h.
 */
public class SuggestionsCategoryInfo {
    /**
     * Localized title of the category.
     */
    private final String mTitle;

    /**
     * Layout of the cards to be used to display suggestions in this category.
     */
    @ContentSuggestionsCardLayoutEnum
    private final int mCardLayout;

    /**
     * Whether the category supports a "More" button. The button either triggers
     * a fixed action (like opening a native page) or, if there is no such fixed
     * action, it queries the provider for more suggestions.
     */
    private final boolean mHasMoreButton;

    /** Whether this category should be shown if it offers no suggestions. */
    private final boolean mShowIfEmpty;

    public SuggestionsCategoryInfo(String title, @ContentSuggestionsCardLayoutEnum int cardLayout,
            boolean hasMoreButton, boolean showIfEmpty) {
        mTitle = title;
        mCardLayout = cardLayout;
        mHasMoreButton = hasMoreButton;
        mShowIfEmpty = showIfEmpty;
    }

    public String getTitle() {
        return mTitle;
    }

    @ContentSuggestionsCardLayoutEnum
    public int getCardLayout() {
        return mCardLayout;
    }

    public boolean hasMoreButton() {
        return mHasMoreButton;
    }

    public boolean showIfEmpty() {
        return mShowIfEmpty;
    }
}

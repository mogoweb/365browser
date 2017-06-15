// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp.cards;

import org.chromium.base.Log;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.ntp.snippets.CategoryInt;
import org.chromium.chrome.browser.ntp.snippets.CategoryStatus;
import org.chromium.chrome.browser.ntp.snippets.KnownCategories;
import org.chromium.chrome.browser.ntp.snippets.SnippetArticle;
import org.chromium.chrome.browser.ntp.snippets.SnippetsBridge;
import org.chromium.chrome.browser.ntp.snippets.SuggestionsSource;
import org.chromium.chrome.browser.offlinepages.OfflinePageBridge;
import org.chromium.chrome.browser.suggestions.DestructionObserver;
import org.chromium.chrome.browser.suggestions.SuggestionsRanker;
import org.chromium.chrome.browser.suggestions.SuggestionsUiDelegate;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A node in the tree containing a list of all suggestions sections. It listens to changes in the
 * suggestions source and updates the corresponding sections.
 */
public class SectionList
        extends InnerNode implements SuggestionsSource.Observer, SuggestionsSection.Delegate {
    private static final String TAG = "Ntp";

    /** Maps suggestion categories to sections, with stable iteration ordering. */
    private final Map<Integer, SuggestionsSection> mSections = new LinkedHashMap<>();
    /** List of categories that are hidden because they have no content to show. */
    private final Set<Integer> mBlacklistedCategories = new HashSet<>();
    private final SuggestionsUiDelegate mUiDelegate;
    private final OfflinePageBridge mOfflinePageBridge;

    public SectionList(SuggestionsUiDelegate uiDelegate, OfflinePageBridge offlinePageBridge) {
        mUiDelegate = uiDelegate;
        mUiDelegate.getSuggestionsSource().setObserver(this);
        mOfflinePageBridge = offlinePageBridge;

        mUiDelegate.addDestructionObserver(new DestructionObserver() {
            @Override
            public void onDestroy() {
                removeAllSections();
            }
        });
    }

    /**
     * Resets the sections, reloading the whole new tab page content.
     * @param alwaysAllowEmptySections Whether sections are always allowed to be displayed when
     *     they are empty, even when they are normally not.
     */
    private void resetSections(boolean alwaysAllowEmptySections) {
        removeAllSections();

        SuggestionsSource suggestionsSource = mUiDelegate.getSuggestionsSource();
        int[] categories = suggestionsSource.getCategories();
        int[] suggestionsPerCategory = new int[categories.length];
        int visibleCategoriesCount = 0;
        int categoryIndex = 0;
        for (int category : categories) {
            int categoryStatus = suggestionsSource.getCategoryStatus(category);
            int suggestionsCount = 0;
            if (SnippetsBridge.isCategoryEnabled(categoryStatus)) {
                suggestionsCount = resetSection(category, categoryStatus, alwaysAllowEmptySections);
                if (mSections.get(category) != null) ++visibleCategoriesCount;
            }
            suggestionsPerCategory[categoryIndex] = suggestionsCount;
            ++categoryIndex;
        }

        maybeHideArticlesHeader();
        mUiDelegate.getEventReporter().onPageShown(
                categories, suggestionsPerCategory, visibleCategoriesCount);
    }

    /**
     * Resets the section for {@code category}. Removes the section if there are no suggestions for
     * it and it is not allowed to be empty. Otherwise, creates the section if it is not present
     * yet. Sets the available suggestions on the section.
     * @param category The category for which the section must be reset.
     * @param categoryStatus The category status.
     * @param alwaysAllowEmptySections Whether sections are always allowed to be displayed when
     *     they are empty, even when they are normally not.
     * @return The number of suggestions for the section.
     */
    private int resetSection(@CategoryInt int category, @CategoryStatus int categoryStatus,
            boolean alwaysAllowEmptySections) {
        SuggestionsSource suggestionsSource = mUiDelegate.getSuggestionsSource();
        List<SnippetArticle> suggestions = suggestionsSource.getSuggestionsForCategory(category);
        SuggestionsCategoryInfo info = suggestionsSource.getCategoryInfo(category);

        SuggestionsSection section = mSections.get(category);

        // Do not show an empty section if not allowed.
        if (suggestions.isEmpty() && !info.showIfEmpty() && !alwaysAllowEmptySections) {
            mBlacklistedCategories.add(category);
            if (section != null) removeSection(section);
            return 0;
        } else {
            mBlacklistedCategories.remove(category);
        }

        // Create the section if needed.
        if (section == null) {
            SuggestionsRanker suggestionsRanker = mUiDelegate.getSuggestionsRanker();
            section = new SuggestionsSection(
                    this, mUiDelegate, suggestionsRanker, mOfflinePageBridge, info);
            mSections.put(category, section);
            suggestionsRanker.registerCategory(category);
            addChild(section);
        } else {
            section.clearData();
        }

        // Set the new suggestions.
        section.setStatus(categoryStatus);
        section.appendSuggestions(suggestions, /* userRequested = */ false);
        return suggestions.size();
    }

    @Override
    public void onNewSuggestions(@CategoryInt int category) {
        @CategoryStatus
        int status = mUiDelegate.getSuggestionsSource().getCategoryStatus(category);
        if (!canProcessSuggestions(category, status)) return;

        SuggestionsSection section = mSections.get(category);
        section.setStatus(status);
        section.updateSuggestions(mUiDelegate.getSuggestionsSource());
    }

    @Override
    public void onMoreSuggestions(@CategoryInt int category, List<SnippetArticle> suggestions) {
        @CategoryStatus
        int status = mUiDelegate.getSuggestionsSource().getCategoryStatus(category);
        if (!canProcessSuggestions(category, status)) return;

        SuggestionsSection section = mSections.get(category);
        section.setStatus(status);
        section.appendSuggestions(suggestions, /* userRequested = */ true);
    }

    @Override
    public void onCategoryStatusChanged(@CategoryInt int category, @CategoryStatus int status) {
        // Observers should not be registered for this state.
        assert status != CategoryStatus.ALL_SUGGESTIONS_EXPLICITLY_DISABLED;

        // If the category was blacklisted, we note that there might be new content to show.
        mBlacklistedCategories.remove(category);

        // If there is no section for this category there is nothing to do.
        if (!mSections.containsKey(category)) return;

        switch (status) {
            case CategoryStatus.NOT_PROVIDED:
                // The section provider has gone away. Keep open UIs as they are.
                return;

            case CategoryStatus.CATEGORY_EXPLICITLY_DISABLED:
            case CategoryStatus.LOADING_ERROR:
                // Need to remove the entire section from the UI immediately.
                removeSection(mSections.get(category));
                return;

            default:
                mSections.get(category).setStatus(status);
                return;
        }
    }

    @Override
    public void onSuggestionInvalidated(@CategoryInt int category, String idWithinCategory) {
        if (!mSections.containsKey(category)) return;
        mSections.get(category).removeSuggestionById(idWithinCategory);
    }

    @Override
    public void onFullRefreshRequired() {
        refreshSuggestions();
    }

    @Override
    public void dismissSection(SuggestionsSection section) {
        mUiDelegate.getSuggestionsSource().dismissCategory(section.getCategory());
        removeSection(section);
    }

    @Override
    public boolean isResetAllowed() {
        if (!ChromeFeatureList.isEnabled(ChromeFeatureList.CHROME_HOME)) return false;

        // TODO(dgn): Also check if the bottom sheet is closed and how long since it has been closed
        // or opened, so that we don't refresh content while the user still cares about it.
        // Note: don't only use visibility, as pending FetchMore requests can still come, we don't
        // want to clear all the current suggestions in that case. See https://crbug.com/711414

        return !mUiDelegate.isVisible();
    }

    /**
     * Resets all the sections, getting the current list of categories and the associated
     * suggestions from the backend.
     */
    public void refreshSuggestions() {
        resetSections(/* alwaysAllowEmptySections = */false);
    }

    /**
     * Restores any sections that have been dismissed and triggers a new fetch.
     */
    public void restoreDismissedSections() {
        mUiDelegate.getSuggestionsSource().restoreDismissedCategories();
        resetSections(/* allowEmptySections = */ true);
        mUiDelegate.getSuggestionsSource().fetchRemoteSuggestions();
    }

    /**
     * @return Whether the list of sections is empty.
     */
    public boolean isEmpty() {
        return mSections.isEmpty();
    }

    /**
     * Synchronises the data of the sections with that of the suggestions source, resetting the ones
     * that are stale. (see {@link SuggestionsSection#isDataStale()})
     */
    public void synchroniseWithSource() {
        int[] categories = mUiDelegate.getSuggestionsSource().getCategories();

        if (categoriesChanged(categories)) {
            Log.d(TAG, "The categories have changed: old=%s, new=%s - Resetting all the sections.",
                    mSections.keySet(), Arrays.toString(categories));
            // The number or the order of the sections changed. We reset everything.
            resetSections(/* alwaysAllowEmptySections = */ false);
            return;
        }

        for (Map.Entry<Integer, SuggestionsSection> sectionsEntry : mSections.entrySet()) {
            if (!sectionsEntry.getValue().isDataStale()) continue;

            @CategoryInt
            int category = sectionsEntry.getKey();
            Log.d(TAG, "The section for category %d is stale - Resetting.", category);
            resetSection(category, mUiDelegate.getSuggestionsSource().getCategoryStatus(category),
                    /* alwaysAllowEmptySections = */ false);
        }
    }

    private void removeSection(SuggestionsSection section) {
        mSections.remove(section.getCategory());
        removeChild(section);
    }

    private void removeAllSections() {
        mSections.clear();
        removeChildren();
    }

    /** Hides the header for the {@link KnownCategories#ARTICLES} section when necessary. */
    private void maybeHideArticlesHeader() {
        // If there is more than a section we want to show the headers for disambiguation purposes.
        if (mSections.size() != 1) return;

        SuggestionsSection articlesSection = mSections.get(KnownCategories.ARTICLES);
        if (articlesSection == null) return;

        articlesSection.setHeaderVisibility(false);
    }

    /**
     * Checks that the list of categories currently displayed by this list is the same as
     * {@code newCategories}: same categories in the same order.
     */
    @VisibleForTesting
    boolean categoriesChanged(@CategoryInt int[] newCategories) {
        Iterator<Integer> shownCategories = mSections.keySet().iterator();
        for (int category : newCategories) {
            if (mBlacklistedCategories.contains(category)) {
                Log.d(TAG, "categoriesChanged: ignoring blacklisted category %d", category);
                continue;
            }
            if (!shownCategories.hasNext()) return true;
            if (shownCategories.next() != category) return true;
        }

        return shownCategories.hasNext();
    }

    /**
     * Returns whether the category is able to process the suggestions. The category might decide
     * not to show incoming suggestions later, but this check ensures it's in a basic state
     * compatible with displaying content.
     */
    private boolean canProcessSuggestions(@CategoryInt int category, @CategoryStatus int status) {
        // If the category was blacklisted, we note that there might be new content to show.
        mBlacklistedCategories.remove(category);

        // We never want to add suggestions from unknown categories.
        if (!mSections.containsKey(category)) return false;

        // The status may have changed while the suggestions were loading, perhaps they should not
        // be displayed any more.
        if (!SnippetsBridge.isCategoryEnabled(status)) {
            Log.w(TAG, "Received suggestions for a disabled category (id=%d, status=%d)", category,
                    status);
            return false;
        }

        return true;
    }

    SuggestionsSection getSectionForTesting(@CategoryInt int categoryId) {
        return mSections.get(categoryId);
    }
}

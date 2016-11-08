// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp.snippets;

import android.graphics.Bitmap;

import org.chromium.base.Callback;
import org.chromium.chrome.browser.ntp.cards.SuggestionsCategoryInfo;
import org.chromium.chrome.browser.ntp.snippets.CategoryStatus.CategoryStatusEnum;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A fake Suggestions source for use in unit and instrumentation tests.
 */
public class FakeSuggestionsSource implements SuggestionsSource {
    private SuggestionsSource.Observer mObserver;
    private final Map<Integer, List<SnippetArticle>> mSuggestions = new HashMap<>();
    private final Map<Integer, Integer> mCategoryStatus = new LinkedHashMap<>();
    private final Map<Integer, SuggestionsCategoryInfo> mCategoryInfo = new HashMap<>();
    private final Map<String, Bitmap> mThumbnails = new HashMap<>();

    /**
     * Sets the status to be returned for a given category.
     */
    public void setStatusForCategory(@CategoryInt int category,
            @CategoryStatusEnum int status) {
        mCategoryStatus.put(category, status);
        if (mObserver != null) mObserver.onCategoryStatusChanged(category, status);
    }

    /**
     * Sets the suggestions to be returned for a given category.
     */
    public void setSuggestionsForCategory(
            @CategoryInt int category, List<SnippetArticle> suggestions) {
        // Copy the suggestions list so that it can't be modified anymore.
        mSuggestions.put(category, new ArrayList<>(suggestions));
        if (mObserver != null) mObserver.onNewSuggestions(category);
    }

    /**
     * Sets the metadata to be returned for a given category.
     */
    public void setInfoForCategory(@CategoryInt int category, SuggestionsCategoryInfo info) {
        mCategoryInfo.put(category, info);
    }

    /**
     * Set's the bitmap to be returned when the Thumbnail is requested for a snippet with that id.
     */
    public void setThumbnailForId(String id, Bitmap bitmap) {
        mThumbnails.put(id, bitmap);
    }

    /**
     * Removes the given suggestion from the source and notifies any observer that it has been
     * invalidated.
     */
    public void fireSuggestionInvalidated(@CategoryInt int category, String suggestionId) {
        for (SnippetArticle suggestion : mSuggestions.get(category)) {
            if (suggestion.mId.equals(suggestionId)) {
                mSuggestions.get(category).remove(suggestion);
                break;
            }
        }
        mObserver.onSuggestionInvalidated(category, suggestionId);
    }

    /**
     * Removes a category from the fake source without notifying anyone.
     */
    public void silentlyRemoveCategory(int category) {
        mSuggestions.remove(category);
        mCategoryStatus.remove(category);
        mCategoryInfo.remove(category);
    }

    @Override
    public void dismissSuggestion(SnippetArticle suggestion) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void fetchSuggestionImage(
            SnippetArticle suggestion, Callback<Bitmap> callback) {
        if (mThumbnails.containsKey(suggestion.mId)) {
            callback.onResult(mThumbnails.get(suggestion.mId));
        }
    }

    @Override
    public void getSuggestionVisited(
            SnippetArticle suggestion, Callback<Boolean> callback) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setObserver(Observer observer) {
        mObserver = observer;
    }

    @Override
    public int[] getCategories() {
        Set<Integer> ids = mCategoryStatus.keySet();
        int[] result = new int[ids.size()];
        int index = 0;
        for (int id : ids) result[index++] = id;
        return result;
    }

    @CategoryStatusEnum
    @Override
    public int getCategoryStatus(@CategoryInt int category) {
        return mCategoryStatus.get(category);
    }

    @Override
    public SuggestionsCategoryInfo getCategoryInfo(int category) {
        return mCategoryInfo.get(category);
    }

    @Override
    public List<SnippetArticle> getSuggestionsForCategory(int category) {
        if (!SnippetsBridge.isCategoryStatusAvailable(mCategoryStatus.get(category))) {
            return Collections.emptyList();
        }
        List<SnippetArticle> result = mSuggestions.get(category);
        return result == null ? Collections.<SnippetArticle>emptyList() : result;
    }
}
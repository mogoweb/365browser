// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp.snippets;

import android.graphics.Bitmap;

import org.chromium.base.Callback;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.browser.ntp.cards.SuggestionsCategoryInfo;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.suggestions.ContentSuggestionsAdditionalAction;
import org.chromium.chrome.browser.suggestions.DestructionObserver;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides access to the snippets to display on the NTP using the C++ ContentSuggestionsService.
 */
public class SnippetsBridge implements SuggestionsSource, DestructionObserver {
    private static final String TAG = "SnippetsBridge";

    private long mNativeSnippetsBridge;
    private SuggestionsSource.Observer mObserver;

    public static boolean isCategoryStatusAvailable(@CategoryStatus int status) {
        // Note: This code is duplicated in content_suggestions_category_status.cc.
        return status == CategoryStatus.AVAILABLE_LOADING || status == CategoryStatus.AVAILABLE;
    }

    /** Returns whether the category is considered "enabled", and can show content suggestions. */
    public static boolean isCategoryEnabled(@CategoryStatus int status) {
        switch (status) {
            case CategoryStatus.INITIALIZING:
            case CategoryStatus.AVAILABLE:
            case CategoryStatus.AVAILABLE_LOADING:
                return true;
        }
        return false;
    }

    public static boolean isCategoryLoading(@CategoryStatus int status) {
        return status == CategoryStatus.AVAILABLE_LOADING || status == CategoryStatus.INITIALIZING;
    }

    /**
     * Creates a SnippetsBridge for getting snippet data for the current user.
     *
     * @param profile Profile of the user that we will retrieve snippets for.
     */
    public SnippetsBridge(Profile profile) {
        mNativeSnippetsBridge = nativeInit(profile);
    }

    /**
     * Destroys the native bridge. This object can no longer be used to send native commands, and
     * any observer is nulled out and will stop receiving updates. This object should be discarded.
     */
    @Override
    public void onDestroy() {
        assert mNativeSnippetsBridge != 0;
        nativeDestroy(mNativeSnippetsBridge);
        mNativeSnippetsBridge = 0;
        mObserver = null;
    }

    /**
     * Reschedules the fetching of snippets.
     */
    public static void rescheduleFetching() {
        nativeRemoteSuggestionsSchedulerRescheduleFetching();
    }

    /**
     * Fetches remote suggestions in background.
     */
    public static void fetchRemoteSuggestionsFromBackground() {
        nativeRemoteSuggestionsSchedulerOnFetchDue();
    }

    public static void setRemoteSuggestionsEnabled(boolean enabled) {
        nativeSetRemoteSuggestionsEnabled(enabled);
    }

    public static boolean areRemoteSuggestionsEnabled() {
        return nativeAreRemoteSuggestionsEnabled();
    }

    public static boolean areRemoteSuggestionsManaged() {
        return nativeAreRemoteSuggestionsManaged();
    }

    public static boolean areRemoteSuggestionsManagedByCustodian() {
        return nativeAreRemoteSuggestionsManagedByCustodian();
    }

    public static void setContentSuggestionsNotificationsEnabled(boolean enabled) {
        nativeSetContentSuggestionsNotificationsEnabled(enabled);
    }

    public static boolean areContentSuggestionsNotificationsEnabled() {
        return nativeAreContentSuggestionsNotificationsEnabled();
    }

    @Override
    public void fetchRemoteSuggestions() {
        nativeReloadSuggestions(mNativeSnippetsBridge);
    }

    @Override
    public int[] getCategories() {
        assert mNativeSnippetsBridge != 0;
        return nativeGetCategories(mNativeSnippetsBridge);
    }

    @Override
    @CategoryStatus
    public int getCategoryStatus(int category) {
        assert mNativeSnippetsBridge != 0;
        return nativeGetCategoryStatus(mNativeSnippetsBridge, category);
    }

    @Override
    public SuggestionsCategoryInfo getCategoryInfo(int category) {
        assert mNativeSnippetsBridge != 0;
        return nativeGetCategoryInfo(mNativeSnippetsBridge, category);
    }

    @Override
    public List<SnippetArticle> getSuggestionsForCategory(int category) {
        assert mNativeSnippetsBridge != 0;
        return nativeGetSuggestionsForCategory(mNativeSnippetsBridge, category);
    }

    @Override
    public void fetchSuggestionImage(SnippetArticle suggestion, Callback<Bitmap> callback) {
        assert mNativeSnippetsBridge != 0;
        nativeFetchSuggestionImage(mNativeSnippetsBridge, suggestion.mCategory,
                suggestion.mIdWithinCategory, callback);
    }

    @Override
    public void fetchSuggestionFavicon(SnippetArticle suggestion, int minimumSizePx,
            int desiredSizePx, Callback<Bitmap> callback) {
        assert mNativeSnippetsBridge != 0;
        nativeFetchSuggestionFavicon(mNativeSnippetsBridge, suggestion.mCategory,
                suggestion.mIdWithinCategory, minimumSizePx, desiredSizePx, callback);
    }

    @Override
    public void fetchContextualSuggestions(String url, Callback<List<SnippetArticle>> callback) {
        assert mNativeSnippetsBridge != 0;
        nativeFetchContextualSuggestions(mNativeSnippetsBridge, url, callback);
    }

    @Override
    public void dismissSuggestion(SnippetArticle suggestion) {
        assert mNativeSnippetsBridge != 0;
        nativeDismissSuggestion(mNativeSnippetsBridge, suggestion.mUrl, suggestion.getGlobalRank(),
                suggestion.mCategory, suggestion.getPerSectionRank(), suggestion.mIdWithinCategory);
    }

    @Override
    public void dismissCategory(@CategoryInt int category) {
        assert mNativeSnippetsBridge != 0;
        nativeDismissCategory(mNativeSnippetsBridge, category);
    }

    @Override
    public void restoreDismissedCategories() {
        assert mNativeSnippetsBridge != 0;
        nativeRestoreDismissedCategories(mNativeSnippetsBridge);
    }

    @Override
    public void setObserver(Observer observer) {
        assert observer != null;
        mObserver = observer;
    }

    @Override
    public void fetchSuggestions(@CategoryInt int category, String[] displayedSuggestionIds) {
        nativeFetch(mNativeSnippetsBridge, category, displayedSuggestionIds);
    }

    @CalledByNative
    private static List<SnippetArticle> createSuggestionList() {
        return new ArrayList<>();
    }

    @CalledByNative
    private static SnippetArticle addSuggestion(List<SnippetArticle> suggestions, int category,
            String id, String title, String publisher, String previewText, String url,
            long timestamp, float score, long fetchTime) {
        int position = suggestions.size();
        suggestions.add(new SnippetArticle(
                category, id, title, publisher, previewText, url, timestamp, score, fetchTime));
        return suggestions.get(position);
    }

    @CalledByNative
    private static void setAssetDownloadDataForSuggestion(
            SnippetArticle suggestion, String downloadGuid, String filePath, String mimeType) {
        suggestion.setAssetDownloadData(downloadGuid, filePath, mimeType);
    }

    @CalledByNative
    private static void setOfflinePageDownloadDataForSuggestion(
            SnippetArticle suggestion, long offlinePageId) {
        suggestion.setOfflinePageDownloadData(offlinePageId);
    }

    @CalledByNative
    private static void setRecentTabDataForSuggestion(
            SnippetArticle suggestion, int tabId, long offlinePageId) {
        suggestion.setRecentTabData(tabId, offlinePageId);
    }

    @CalledByNative
    private static SuggestionsCategoryInfo createSuggestionsCategoryInfo(int category, String title,
            @ContentSuggestionsCardLayout int cardLayout,
            @ContentSuggestionsAdditionalAction int additionalAction, boolean showIfEmpty,
            String noSuggestionsMessage) {
        return new SuggestionsCategoryInfo(
                category, title, cardLayout, additionalAction, showIfEmpty, noSuggestionsMessage);
    }

    @CalledByNative
    private void onNewSuggestions(@CategoryInt int category) {
        if (mObserver != null) mObserver.onNewSuggestions(category);
    }

    @CalledByNative
    private void onMoreSuggestions(@CategoryInt int category, List<SnippetArticle> suggestions) {
        if (mObserver != null) mObserver.onMoreSuggestions(category, suggestions);
    }

    @CalledByNative
    private void onCategoryStatusChanged(@CategoryInt int category, @CategoryStatus int newStatus) {
        if (mObserver != null) mObserver.onCategoryStatusChanged(category, newStatus);
    }

    @CalledByNative
    private void onSuggestionInvalidated(@CategoryInt int category, String idWithinCategory) {
        if (mObserver != null) mObserver.onSuggestionInvalidated(category, idWithinCategory);
    }

    @CalledByNative
    private void onFullRefreshRequired() {
        if (mObserver != null) mObserver.onFullRefreshRequired();
    }

    private native long nativeInit(Profile profile);
    private native void nativeDestroy(long nativeNTPSnippetsBridge);
    private native void nativeReloadSuggestions(long nativeNTPSnippetsBridge);
    private static native void nativeRemoteSuggestionsSchedulerOnFetchDue();
    private static native void nativeRemoteSuggestionsSchedulerRescheduleFetching();
    private static native void nativeSetRemoteSuggestionsEnabled(boolean enabled);
    private static native boolean nativeAreRemoteSuggestionsEnabled();
    private static native boolean nativeAreRemoteSuggestionsManaged();
    private static native boolean nativeAreRemoteSuggestionsManagedByCustodian();
    private static native void nativeSetContentSuggestionsNotificationsEnabled(boolean enabled);
    private static native boolean nativeAreContentSuggestionsNotificationsEnabled();
    private native int[] nativeGetCategories(long nativeNTPSnippetsBridge);
    private native int nativeGetCategoryStatus(long nativeNTPSnippetsBridge, int category);
    private native SuggestionsCategoryInfo nativeGetCategoryInfo(
            long nativeNTPSnippetsBridge, int category);
    private native List<SnippetArticle> nativeGetSuggestionsForCategory(
            long nativeNTPSnippetsBridge, int category);
    private native void nativeFetchSuggestionImage(long nativeNTPSnippetsBridge, int category,
            String idWithinCategory, Callback<Bitmap> callback);
    private native void nativeFetchSuggestionFavicon(long nativeNTPSnippetsBridge, int category,
            String idWithinCategory, int minimumSizePx, int desiredSizePx,
            Callback<Bitmap> callback);
    private native void nativeFetch(
            long nativeNTPSnippetsBridge, int category, String[] knownSuggestions);
    private native void nativeFetchContextualSuggestions(
            long nativeNTPSnippetsBridge, String url, Callback<List<SnippetArticle>> callback);
    private native void nativeDismissSuggestion(long nativeNTPSnippetsBridge, String url,
            int globalPosition, int category, int positionInCategory, String idWithinCategory);
    private native void nativeDismissCategory(long nativeNTPSnippetsBridge, int category);
    private native void nativeRestoreDismissedCategories(long nativeNTPSnippetsBridge);
}

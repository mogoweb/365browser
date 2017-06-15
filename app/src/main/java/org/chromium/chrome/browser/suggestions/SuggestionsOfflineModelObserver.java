// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.suggestions;

import android.support.annotation.Nullable;

import org.chromium.base.Callback;
import org.chromium.chrome.browser.offlinepages.ClientId;
import org.chromium.chrome.browser.offlinepages.OfflinePageBridge;
import org.chromium.chrome.browser.offlinepages.OfflinePageItem;

/**
 * Handles checking the offline state of suggestions and notifications about related changes.
 * @param <T> type of suggestion to handle. Mostly a convenience parameter to avoid casts.
 */
public abstract class SuggestionsOfflineModelObserver<T extends OfflinableSuggestion>
        extends OfflinePageBridge.OfflinePageModelObserver implements DestructionObserver {
    private final OfflinePageBridge mOfflinePageBridge;

    /**
     * Constructor for an offline model observer. It registers itself with the bridge, but the
     * unregistration will have to be done by the caller, either directly or by registering the
     * created observer as {@link DestructionObserver}.
     * @param bridge source of the offline state data.
     */
    public SuggestionsOfflineModelObserver(OfflinePageBridge bridge) {
        mOfflinePageBridge = bridge;
        mOfflinePageBridge.addObserver(this);
    }

    @Override
    public void onDestroy() {
        mOfflinePageBridge.removeObserver(this);
    }

    @Override
    public void offlinePageModelLoaded() {
        updateOfflinableSuggestionsAvailability();
    }

    @Override
    public void offlinePageAdded(OfflinePageItem addedPage) {
        updateOfflinableSuggestionsAvailability();
    }

    @Override
    public void offlinePageDeleted(long offlineId, ClientId clientId) {
        for (T suggestion : getOfflinableSuggestions()) {
            if (suggestion.requiresExactOfflinePage()) continue;

            Long suggestionOfflineId = suggestion.getOfflinePageOfflineId();
            if (suggestionOfflineId == null) continue;
            if (suggestionOfflineId != offlineId) continue;

            // The old value cannot be simply removed without a request to the
            // model, because there may be an older offline page for the same
            // URL.
            updateOfflinableSuggestionAvailability(suggestion);
        }
    }

    public void updateOfflinableSuggestionsAvailability() {
        for (T suggestion : getOfflinableSuggestions()) {
            if (suggestion.requiresExactOfflinePage()) continue;
            updateOfflinableSuggestionAvailability(suggestion);
        }
    }

    public void updateOfflinableSuggestionAvailability(final T suggestion) {
        // This method is not applicable to articles for which the exact offline id must specified.
        assert !suggestion.requiresExactOfflinePage();
        if (!mOfflinePageBridge.isOfflinePageModelLoaded()) return;

        // TabId is relevant only for recent tab offline pages, which we do not handle here, so we
        // do not care about tab id.
        mOfflinePageBridge.selectPageForOnlineUrl(
                suggestion.getUrl(), /*tabId=*/0, new Callback<OfflinePageItem>() {
                    @Override
                    public void onResult(OfflinePageItem item) {
                        onSuggestionOfflineIdChanged(
                                suggestion, item == null ? null : item.getOfflineId());
                    }
                });
    }

    /**
     * Called when the offline state of a suggestion is retrieved.
     * @param suggestion the suggestion for which the offline state was checked.
     * @param id the new offline id of the suggestion.
     */
    public abstract void onSuggestionOfflineIdChanged(T suggestion, @Nullable Long id);

    /** Handle to the suggestions for which to observe changes. */
    public abstract Iterable<T> getOfflinableSuggestions();
}

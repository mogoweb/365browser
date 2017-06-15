// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.suggestions;

import org.chromium.base.DiscardableReferencePool;
import org.chromium.chrome.browser.favicon.FaviconHelper.FaviconImageCallback;
import org.chromium.chrome.browser.favicon.FaviconHelper.IconAvailabilityCallback;
import org.chromium.chrome.browser.favicon.LargeIconBridge.LargeIconCallback;
import org.chromium.chrome.browser.ntp.snippets.SuggestionsSource;

/**
 * Interface between the suggestion surface and the rest of the browser.
 */
public interface SuggestionsUiDelegate {
    // Dependency injection
    // TODO(dgn): remove these methods once the users have a different way to get a reference
    // to these objects (https://crbug.com/677672)

    /** Convenience method to access the {@link SuggestionsSource}. */
    SuggestionsSource getSuggestionsSource();

    /** Convenience method to access the {@link SuggestionsRanker}. */
    SuggestionsRanker getSuggestionsRanker();

    /** Convenience method to access the {@link SuggestionsEventReporter}. */
    SuggestionsEventReporter getEventReporter();

    /** Convenience method to access the {@link SuggestionsNavigationDelegate}. */
    SuggestionsNavigationDelegate getNavigationDelegate();

    /**
     * @return The reference pool to use for large objects that should be dropped under
     * memory pressure.
     */
    DiscardableReferencePool getReferencePool();

    // Favicons

    /**
     * Checks if an icon with the given URL is available. If not,
     * downloads it and stores it as a favicon/large icon for the given {@code pageUrl}.
     * @param pageUrl The URL of the site whose icon is being requested.
     * @param iconUrl The URL of the favicon/large icon.
     * @param isLargeIcon Whether the {@code iconUrl} represents a large icon or favicon.
     * @param callback The callback to be notified when the favicon has been checked.
     */
    void ensureIconIsAvailable(String pageUrl, String iconUrl, boolean isLargeIcon,
            boolean isTemporary, IconAvailabilityCallback callback);

    /**
     * Gets the large icon (e.g. favicon or touch icon) for a given URL.
     * @param url The URL of the site whose icon is being requested.
     * @param size The desired size of the icon in pixels.
     * @param callback The callback to be notified when the icon is available.
     */
    void getLargeIconForUrl(String url, int size, LargeIconCallback callback);

    /**
     * Gets the favicon image for a given URL.
     * @param url The URL of the site whose favicon is being requested.
     * @param size The desired size of the favicon in pixels.
     * @param faviconCallback The callback to be notified when the favicon is available.
     */
    void getLocalFaviconImageForURL(String url, int size, FaviconImageCallback faviconCallback);

    // Feature/State checks

    /**
     * Registers a {@link DestructionObserver}, notified when the New Tab Page goes away.
     */
    void addDestructionObserver(DestructionObserver destructionObserver);

    /** @return Whether the suggestions UI is currently visible. */
    boolean isVisible();
}
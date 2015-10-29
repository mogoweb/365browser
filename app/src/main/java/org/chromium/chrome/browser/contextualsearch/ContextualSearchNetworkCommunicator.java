// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.contextualsearch;

import java.net.URL;

import javax.annotation.Nullable;


/**
 * An interface for network communication between the Contextual Search client and server.
 */
public interface ContextualSearchNetworkCommunicator {

    /**
     * Starts a Search Term Resolution request.
     * When the response comes back {@link #handleSearchTermResolutionResponse} will be called.
     * @param selection the current selected text.
     */
    void startSearchTermResolutionRequest(String selection);

    /**
     * Handles a Search Term Resolution response.
     * @param isNetworkUnavailable whether the network is available.
     * @param responseCode the server's HTTP response code.
     * @param searchTerm the term to search for.
     * @param displayText the text to display that describes the search term.
     * @param alternateTerm the alternate search term.
     * @param doPreventPreload whether to prevent preloading the search result.
     * @param selectionStartAdjust The start offset adjustment of the selection to use to highlight
     *        the search term.
     * @param selectionEndAdjust The end offset adjustment of the selection to use to highlight
     *        the search term.
     */
    void handleSearchTermResolutionResponse(boolean isNetworkUnavailable, int responseCode,
            String searchTerm, String displayText, String alternateTerm, boolean doPreventPreload,
            int selectionStartAdjust, int selectionEndAdjust);

    /**
     * Loads a URL in the search content view.
     * @param url the URL of the page to load.
     */
    void loadUrl(String url);

    // --------------------------------------------------------------------------------------------
    // These are non-network actions that need to be stubbed out for testing.
    // --------------------------------------------------------------------------------------------

    /**
     * Gets the URL of the base page.
     * TODO(donnd): move to another interface, or rename this interface:
     * This is needed to stub out for testing, but has nothing to do with networking.
     * @return The URL of the base page (needed for testing purposes).
     */
    @Nullable URL getBasePageUrl();

    /**
     * Handles the WebContentsObserver#didNavigateMainFrame callback.
     * @param url The URL of the navigation.
     * @param httpResultCode The HTTP result code of the navigation.
     */
    void handleDidNavigateMainFrame(String url, int httpResultCode);

    /**
     * Creates and sets up a new Search Panel's {@code ContentViewCore}. If there's an existing
     * {@code ContentViewCore} being used, it will be destroyed first. This should be called as
     * late as possible to avoid unnecessarily consuming memory.
     */
    void createNewSearchContentView();

    /**
     * Destroys the Search Panel's {@code ContentViewCore}.
     */
    void destroySearchContentView();
}

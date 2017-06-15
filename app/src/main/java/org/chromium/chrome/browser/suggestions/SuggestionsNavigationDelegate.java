// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.suggestions;

import org.chromium.chrome.browser.ntp.snippets.SnippetArticle;
import org.chromium.content_public.browser.LoadUrlParams;

/**
 * Interface exposing to the suggestion surface methods to navigate to other parts of the browser.
 */
public interface SuggestionsNavigationDelegate {
    /** @return Whether context menus should allow the option to open a link in incognito. */
    boolean isOpenInIncognitoEnabled();

    /** @return Whether context menus should allow the option to open a link in a new window. */
    boolean isOpenInNewWindowEnabled();

    /** Opens the bookmarks page in the current tab. */
    void navigateToBookmarks();

    /** Opens the Download Manager UI in the current tab. */
    void navigateToDownloadManager();

    /** Opens the recent tabs page in the current tab. */
    void navigateToRecentTabs();

    /** Opens the help page for the content suggestions in the current tab. */
    void navigateToHelpPage();

    /**
     * Opens a content suggestion and records related metrics.
     * @param windowOpenDisposition How to open (current tab, new tab, new window etc).
     * @param article The content suggestion to open.
     */
    void openSnippet(int windowOpenDisposition, SnippetArticle article);

    /** Opens an url with the desired disposition. */
    void openUrl(int windowOpenDisposition, LoadUrlParams loadUrlParams);
}
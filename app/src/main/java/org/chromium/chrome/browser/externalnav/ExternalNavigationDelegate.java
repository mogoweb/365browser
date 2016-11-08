// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.externalnav;

import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.app.Activity;

import org.chromium.chrome.browser.externalnav.ExternalNavigationHandler.OverrideUrlLoadingResult;
import org.chromium.chrome.browser.tab.Tab;

import java.util.List;

/**
 * A delegate for the class responsible for navigating to external applications from Chrome. Used
 * by {@link ExternalNavigationHandler}.
 */
interface ExternalNavigationDelegate {
    /**
     * Get the list of component name of activities which can resolve |intent|.
     */
    List<ResolveInfo> queryIntentActivities(Intent intent);

    /**
     * Return The activity that this delegate is associated with.
     */
    Activity getActivity();

    /**
     * Determine if Chrome is the default or only handler for a given intent. If true, Chrome
     * will handle the intent when started.
     */
    boolean willChromeHandleIntent(Intent intent);

    /**
     * Search for intent handlers that are specific to this URL aka, specialized apps like
     * google maps or youtube
     */
    boolean isSpecializedHandlerAvailable(List<ResolveInfo> infos);

    /**
     * Returns the number of specialized intent handlers in {@params infos}. Specialized intent
     * handlers are intent handlers which handle only a few URLs (e.g. google maps or youtube).
     */
    int countSpecializedHandlers(List<ResolveInfo> infos);

    /**
     * Returns the package name of the first valid WebAPK in {@link infos}.
     * @param infos ResolveInfos to search.
     * @return The package name of the first valid WebAPK. Null if no valid WebAPK was found.
     */
    String findWebApkPackageName(List<ResolveInfo> infos);

    /**
     * Get the name of the package of the currently running activity so that incoming intents
     * can be identified as originating from this activity.
     */
    String getPackageName();

    /**
     * Start an activity for the intent. Used for intents that must be handled externally.
     */
    void startActivity(Intent intent);

    /**
     * Start an activity for the intent. Used for intents that may be handled internally or
     * externally. If the user chooses to handle the intent internally, this routine must return
     * false.
     */
    boolean startActivityIfNeeded(Intent intent);

    /**
     * Display a dialog warning the user that they may be leaving Chrome by starting this
     * intent. Give the user the opportunity to cancel the action. And if it is canceled, a
     * navigation will happen in Chrome.
     */
    void startIncognitoIntent(Intent intent, String referrerUrl, String fallbackUrl, Tab tab,
            boolean needsToCloseTab);

    /**
     * @param url The requested url.
     * @param tab The current tab.
     * @return Whether we should block the navigation and request file access before proceeding.
     */
    boolean shouldRequestFileAccess(String url, Tab tab);

    /**
     * Trigger a UI affordance that will ask the user to grant file access.  After the access
     * has been granted or denied, continue loading the specified file URL.
     *
     * @param intent The intent to continue loading the file URL.
     * @param referrerUrl The HTTP referrer URL.
     * @param tab The current tab.
     * @param needsToCloseTab Whether this action should close the current tab.
     */
    void startFileIntent(Intent intent, String referrerUrl, Tab tab, boolean needsToCloseTab);

    /**
     * Clobber the current tab and try not to pass an intent when it should be handled by Chrome
     * so that we can deliver HTTP referrer information safely.
     *
     * @param url The new URL after clobbering the current tab.
     * @param referrerUrl The HTTP referrer URL.
     * @param tab The current tab.
     * @return OverrideUrlLoadingResult (if the tab has been clobbered, or we're launching an
     *         intent.)
     */
    OverrideUrlLoadingResult clobberCurrentTab(String url, String referrerUrl, Tab tab);

    /** Adds a window id to the intent, if necessary. */
    void maybeSetWindowId(Intent intent);

    /** Adds the package name of a specialized intent handler. */
    void maybeRecordAppHandlersInIntent(Intent intent, List<ResolveInfo> info);

    /**
     * Determine if the Chrome app is in the foreground.
     */
    boolean isChromeAppInForeground();

    /**
     * @return Default SMS application's package name. Null if there isn't any.
     */
    String getDefaultSmsPackageName();

    /**
     * @return Whether the URL is a file download.
     */
    boolean isPdfDownload(String url);
}

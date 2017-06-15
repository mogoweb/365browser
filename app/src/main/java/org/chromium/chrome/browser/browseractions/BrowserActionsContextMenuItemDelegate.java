// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.browseractions;

import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Browser;

import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.chrome.browser.IntentHandler;
import org.chromium.chrome.browser.document.ChromeLauncherActivity;
import org.chromium.chrome.browser.tabmodel.TabModel.TabLaunchType;
import org.chromium.chrome.browser.util.IntentUtils;

/**
 * A delegate responsible for taking actions based on browser action context menu selections.
 */
public class BrowserActionsContextMenuItemDelegate {
    private static final String TAG = "BrowserActionsItem";

    private final Context mContext;

    /**
     * Builds a {@link BrowserActionsContextMenuItemDelegate} instance.
     */
    public BrowserActionsContextMenuItemDelegate() {
        mContext = ContextUtils.getApplicationContext();
    }

    /**
     * Called when the {@code text} should be saved to the clipboard.
     * @param text The text to save to the clipboard.
     */
    public void onSaveToClipboard(String text) {
        ClipboardManager clipboardManager =
                (ClipboardManager) mContext.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData data = ClipData.newPlainText("url", text);
        clipboardManager.setPrimaryClip(data);
    }

    /**
     * Called when the {@code linkUrl} should be opened in Chrome incognito tab.
     * @param linkUrl The url to open.
     */
    public void onOpenInIncognitoTab(String linkUrl) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(linkUrl));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setPackage(mContext.getPackageName());
        intent.putExtra(ChromeLauncherActivity.EXTRA_IS_ALLOWED_TO_RETURN_TO_PARENT, false);
        intent.putExtra(IntentHandler.EXTRA_OPEN_NEW_INCOGNITO_TAB, true);
        intent.putExtra(Browser.EXTRA_APPLICATION_ID, mContext.getPackageName());
        IntentHandler.addTrustedIntentExtras(intent);
        IntentHandler.setTabLaunchType(intent, TabLaunchType.FROM_EXTERNAL_APP);
        IntentUtils.safeStartActivity(mContext, intent);
    }

    /**
     * Called when the {@code linkUrl} should be opened in Chrome in the background.
     * @param linkUrl The url to open.
     */
    public void onOpenInBackground(String linkUrl) {}

    /**
     * Called when a custom item of Browser action menu is selected.
     * @param action The PendingIntent action to be launched.
     */
    public void onCustomItemSelected(PendingIntent action) {
        try {
            action.send();
        } catch (CanceledException e) {
            Log.e(TAG, "Browser Action in Chrome failed to send pending intent.");
        }
    }

    /**
     * Called when the page of the {@code linkUrl} should be downloaded.
     * @param linkUrl The url of the page to download.
     */
    public void startDownload(String linkUrl) {}

    /**
     * Called when the {@code linkUrl} should be shared.
     * @param linkUrl The url to share.
     */
    public void share(String linkUrl) {}
}

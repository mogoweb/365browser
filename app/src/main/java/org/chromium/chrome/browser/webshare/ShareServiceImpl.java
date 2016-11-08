// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.webshare;

import android.app.Activity;
import android.content.ComponentName;
import android.support.annotation.Nullable;

import org.chromium.chrome.browser.share.ShareHelper;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.content_public.browser.WebContents;
import org.chromium.mojo.system.MojoException;
import org.chromium.mojom.webshare.ShareService;
import org.chromium.ui.base.WindowAndroid;

/**
 * Android implementation of the ShareService service defined in
 * third_party/WebKit/public/platform/modules/webshare/webshare.mojom.
 */
public class ShareServiceImpl implements ShareService {
    private final Activity mActivity;

    public ShareServiceImpl(@Nullable WebContents webContents) {
        mActivity = activityFromWebContents(webContents);
    }

    @Override
    public void close() {}

    @Override
    public void onConnectionError(MojoException e) {}

    @Override
    public void share(String title, String text, final ShareResponse callback) {
        if (mActivity == null) {
            callback.call("Share failed");
            return;
        }

        ShareHelper.TargetChosenCallback innerCallback = new ShareHelper.TargetChosenCallback() {
            public void onTargetChosen(ComponentName chosenComponent) {
                callback.call(null);
            }
        };

        ShareHelper.share(false, false, mActivity, title, text, null, null, null, innerCallback);
    }

    @Nullable
    private static Activity activityFromWebContents(@Nullable WebContents webContents) {
        if (webContents == null) return null;

        ContentViewCore contentViewCore = ContentViewCore.fromWebContents(webContents);
        if (contentViewCore == null) return null;

        WindowAndroid window = contentViewCore.getWindowAndroid();
        if (window == null) return null;

        return window.getActivity().get();
    }
}

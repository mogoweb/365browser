// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.infobar;

import android.graphics.Bitmap;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.browser.instantapps.InstantAppsBannerData;
import org.chromium.content_public.browser.WebContents;

import java.net.URI;

/**
 * Delegate for {@link InstantAppsInfoBar}. Use launch() method to display the infobar.
 */
public class InstantAppsInfoBarDelegate {

    private InstantAppsBannerData mData;

    public static void launch(WebContents webContents, String appName, Bitmap appIcon) {
        String hostname = "";
        try {
            URI uri = URI.create(webContents.getUrl());
            hostname = uri.getRawAuthority();
        } catch (IllegalArgumentException e) {
            // not able to parse the URL.
        }
        InstantAppsBannerData data = new InstantAppsBannerData(appName, appIcon, hostname);
        nativeLaunch(webContents, data);
    }

    @CalledByNative
    private static InstantAppsInfoBarDelegate create() {
        return new InstantAppsInfoBarDelegate();
    }

    private InstantAppsInfoBarDelegate() {}

    @CalledByNative
    private void openInstantApp() {
        // TODO(mariakhomenko): launch instant app intent
    }

    private static native void nativeLaunch(WebContents webContents, InstantAppsBannerData data);
}

// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.webapk.lib.client;

import android.content.Intent;

/**
 * WebApkNavigationClient provides an API to get an intent to launch a WebAPK.
 */
public class WebApkNavigationClient {
    /**
     * Creates intent to launch a WebAPK.
     * @param webApkPackageName Package name of the WebAPK to launch.
     * @param url URL to navigate WebAPK to.
     * @return The intent.
     */
    public static Intent createLaunchWebApkIntent(String webApkPackageName, String url) {
        Intent intent;
        try {
            intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
        } catch (Exception e) {
            return null;
        }

        intent.setPackage(webApkPackageName);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }
}

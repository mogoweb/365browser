// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.webapps;

import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.util.UrlUtilities;

class WebApkBrowserControlsDelegate extends WebappBrowserControlsDelegate {
    public WebApkBrowserControlsDelegate(WebappActivity activity, Tab tab) {
        super(activity, tab);
    }

    @Override
    protected boolean shouldShowBrowserControlsForUrl(WebappInfo info, String url) {
        return !UrlUtilities.isUrlWithinScope(url, info.scopeUri().toString());
    }
}

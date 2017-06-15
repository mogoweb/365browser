// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.webapps;

import android.text.TextUtils;

import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabStateBrowserControlsVisibilityDelegate;
import org.chromium.chrome.browser.util.UrlUtilities;
import org.chromium.components.security_state.ConnectionSecurityLevel;

class WebappBrowserControlsDelegate extends TabStateBrowserControlsVisibilityDelegate {
    private final WebappActivity mActivity;

    public WebappBrowserControlsDelegate(WebappActivity activity, Tab tab) {
        super(tab);
        mActivity = activity;
    }

    @Override
    public boolean isShowingBrowserControlsEnabled() {
        if (!super.isShowingBrowserControlsEnabled()) return false;

        return shouldShowBrowserControls(
                mActivity.getWebappInfo(), mTab.getUrl(), mTab.getSecurityLevel());
    }

    @Override
    public boolean isHidingBrowserControlsEnabled() {
        return !isShowingBrowserControlsEnabled();
    }

    /**
     * Returns whether the browser controls should be shown when a webapp is navigated to
     * {@link url} given the page's security level.
     * @param info
     * @param url The webapp's current URL
     * @param securityLevel The security level for the webapp's current URL.
     * @return Whether the browser controls should be shown for {@link url}.
     */
    public boolean shouldShowBrowserControls(
            WebappInfo info, String url, int securityLevel) {
        // Do not show browser controls when URL is not ready yet.
        if (TextUtils.isEmpty(url)) return false;

        return shouldShowBrowserControlsForUrl(info, url)
                || securityLevel == ConnectionSecurityLevel.DANGEROUS
                || securityLevel == ConnectionSecurityLevel.SECURITY_WARNING;
    }

    /**
     * Returns whether the browser controls should be shown when a webapp is navigated to
     * {@link url}.
     */
    protected boolean shouldShowBrowserControlsForUrl(WebappInfo info, String url) {
        return !UrlUtilities.sameDomainOrHost(info.uri().toString(), url, true);
    }
}

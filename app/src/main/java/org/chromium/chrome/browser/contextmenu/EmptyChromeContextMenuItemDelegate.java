// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.contextmenu;

import org.chromium.content_public.common.Referrer;

/**
 * An empty implementation of {@link ChromeContextMenuItemDelegate} to make overriding subsets of
 * the delegate methods easier.
 */
public class EmptyChromeContextMenuItemDelegate implements ChromeContextMenuItemDelegate {
    @Override
    public boolean isIncognito() {
        return false;
    }

    @Override
    public boolean isIncognitoSupported() {
        return false;
    }

    @Override
    public boolean canLoadOriginalImage() {
        return false;
    }

    @Override
    public boolean isDataReductionProxyEnabledForURL(String url) {
        return false;
    }

    @Override
    public boolean startDownload(String url, boolean isLink) {
        return false;
    }

    @Override
    public void onOpenInNewTab(String url, Referrer referrer) {
    }

    @Override
    public void onReloadIgnoringCache() {
    }

    @Override
    public void onLoadOriginalImage() {
    }

    @Override
    public void onOpenInNewIncognitoTab(String url) {
    }

    @Override
    public void onOpenImageUrl(String url, Referrer referrer) {
    }

    @Override
    public void onOpenImageInNewTab(String url, Referrer referrer) {
    }

    @Override
    public void onSaveToClipboard(String text, int clipboardType) {
    }

    @Override
    public void onSaveImageToClipboard(String url) {
    }

    @Override
    public void onSearchByImageInNewTab() {
    }

    @Override
    public String getPageUrl() {
        return "";
    }
}

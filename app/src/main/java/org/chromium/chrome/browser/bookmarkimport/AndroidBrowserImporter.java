// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.bookmarkimport;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;

import org.chromium.base.VisibleForTesting;

/**
 * Imports bookmarks from Android Browser into Chrome.
 */
public class AndroidBrowserImporter extends BookmarkImporter {
    private static final String BROWSER_PROVIDER_PROXY_PACKAGE = "com.android.browser.provider";
    private static final String ANDROID_BROWSER_AUTHORITIES = "com.android.browser;browser";

    private ContentResolver mInputResolver;
    private boolean mIgnoreAvailableProvidersForTestPurposes;

    public AndroidBrowserImporter(Context context) {
        super(context);
        mInputResolver = context.getContentResolver();
    }

    /**
     * @return Flag indicating if Android Browser bookmarks data is accesible.
     *         Note that this doesn't ensure the existence of any new or valid bookmarks.
     */
    public boolean areBookmarksAccessible() {
        if (!areProvidersValid()) return false;
        return AndroidBrowserProviderIterator.isProviderAvailable(mInputResolver);
    }

    // Used by tests through reflection.
    @VisibleForTesting
    void setInputResolver(ContentResolver inputResolver) {
        mInputResolver = inputResolver;
    }

    @VisibleForTesting
    void setIgnoreAvailableProvidersForTestPurposes(boolean ignoreProviders) {
        mIgnoreAvailableProvidersForTestPurposes = ignoreProviders;
    }

    private boolean areProvidersValid() {
        // Unless a test tests if a provider is valid, this function shall always return true.
        if (mIgnoreAvailableProvidersForTestPurposes) return true;

        // If the proxy is present then we are in a post-OTA scenario where we have completely
        // replaced Android Browser. In that case the providers point to ourselves.
        try {
            PackageInfo packageInfo = mContext.getPackageManager().getPackageInfo(
                    BROWSER_PROVIDER_PROXY_PACKAGE, PackageManager.GET_PROVIDERS);
            // There is no provider proxy package, we will not query ourselves, let's continue
            if (packageInfo == null) return true;

            ProviderInfo[] providers = packageInfo.providers;
            // This should be present, but if it's not assume the package was placed by ourselves.
            if (providers == null) return false;

            // Verify the authority of the package in case OEMs create one with the same name
            // that doesn't replace Android Browser's authority.
            for (ProviderInfo provider : providers) {
                if (provider != null && provider.authority != null
                        && provider.authority.equals(ANDROID_BROWSER_AUTHORITIES)) {
                    return false;
                }
            }
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return true;
        }
    }

    @Override
    protected BookmarkImporter.BookmarkIterator[] availableBookmarks() {
        // Make sure we don't query ourselves.
        if (!areProvidersValid()) return null;

        return new BookmarkImporter.BookmarkIterator[] {
            AndroidBrowserProviderIterator.createIfAvailable(mInputResolver)
        };
    }
}

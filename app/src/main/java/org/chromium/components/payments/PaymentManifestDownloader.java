// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.components.payments;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.content_public.browser.WebContents;

import java.net.URI;

/**
 * See comment in:
 * components/payments/content/android/payment_manifest_downloader.h
 */
@JNINamespace("payments")
public class PaymentManifestDownloader {
    /** Interface for the callback to invoke when finished downloading. */
    public interface ManifestDownloadCallback {
        /**
         * Called on successful download of a payment method manifest.
         *
         * @param content The successfully downloaded payment method manifest.
         */
        @CalledByNative("ManifestDownloadCallback")
        void onPaymentMethodManifestDownloadSuccess(String content);

        /**
         * Called on successful download of a web app manifest.
         *
         * @param content The successfully downloaded web app manifest.
         */
        @CalledByNative("ManifestDownloadCallback")
        void onWebAppManifestDownloadSuccess(String content);

        /** Called on failed download. */
        @CalledByNative("ManifestDownloadCallback")
        void onManifestDownloadFailure();
    }

    private final WebContents mWebContents;

    /**
     * Builds the downloader.
     *
     * @param webContents The web contents to use as the context for the download. If this goes
     *                    away, the download is cancelled.
     */
    public PaymentManifestDownloader(WebContents webContents) {
        mWebContents = webContents;
    }

    /**
     * Downloads the payment method manifest file asynchronously.
     *
     * @param methodName The payment method name that is a URI with HTTPS scheme.
     * @param callback   The callback to invoke when finished downloading.
     */
    public void downloadPaymentMethodManifest(URI methodName, ManifestDownloadCallback callback) {
        nativeDownloadPaymentMethodManifest(mWebContents, methodName, callback);
    }

    /**
     * Downloads the web app manifest file asynchronously.
     *
     * @param webAppmanifestUri The web app manifest URI with HTTPS scheme.
     * @param callback          The callback to invoke when finished downloading.
     */
    public void downloadWebAppManifest(URI webAppManifestUri, ManifestDownloadCallback callback) {
        nativeDownloadWebAppManifest(mWebContents, webAppManifestUri, callback);
    }

    @CalledByNative
    private static String getUriString(URI methodName) {
        return methodName.toString();
    }

    private static native void nativeDownloadPaymentMethodManifest(
            WebContents webContents, URI methodName, ManifestDownloadCallback callback);
    private static native void nativeDownloadWebAppManifest(
            WebContents webContents, URI webAppManifestUri, ManifestDownloadCallback callback);
}

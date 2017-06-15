// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.components.payments;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.payments.mojom.WebAppManifestSection;

import java.net.URI;
import java.net.URISyntaxException;

/** Parses payment manifests in a utility process. */
@JNINamespace("payments")
public class PaymentManifestParser {
    /** Interface for the callback to invoke when finished parsing. */
    public interface ManifestParseCallback {
        /**
         * Called on successful parse of a payment method manifest.
         *
         * @param webAppManifestUris The successfully parsed payment method manifest.
         */
        @CalledByNative("ManifestParseCallback")
        void onPaymentMethodManifestParseSuccess(URI[] webAppManifestUris);

        /**
         * Called on successful parse of a web app manifest.
         *
         * @param manifest The successfully parsed web app manifest.
         */
        @CalledByNative("ManifestParseCallback")
        void onWebAppManifestParseSuccess(WebAppManifestSection[] manifest);

        /** Called on failed parse of a payment method manifest. */
        @CalledByNative("ManifestParseCallback")
        void onManifestParseFailure();
    }

    /** Owned native host of the utility process that parses manifest contents. */
    private long mNativePaymentManifestParserAndroid;

    /** Starts the utility process. */
    public void startUtilityProcess() {
        assert mNativePaymentManifestParserAndroid == 0;
        mNativePaymentManifestParserAndroid = nativeCreatePaymentManifestParserAndroid();
        nativeStartUtilityProcess(mNativePaymentManifestParserAndroid);
    }

    /** Stops the utility process. */
    public void stopUtilityProcess() {
        assert mNativePaymentManifestParserAndroid != 0;
        nativeStopUtilityProcess(mNativePaymentManifestParserAndroid);
        mNativePaymentManifestParserAndroid = 0;
    }

    /** @return Whether the utility process is running. */
    public boolean isUtilityProcessRunning() {
        return mNativePaymentManifestParserAndroid != 0;
    }

    /**
     * Parses the payment method manifest file asynchronously.
     *
     * @param content  The content to parse.
     * @param callback The callback to invoke when finished parsing.
     */
    public void parsePaymentMethodManifest(String content, ManifestParseCallback callback) {
        nativeParsePaymentMethodManifest(mNativePaymentManifestParserAndroid, content, callback);
    }

    /**
     * Parses the web app manifest file asynchronously.
     *
     * @param content  The content to parse.
     * @param callback The callback to invoke when finished parsing.
     */
    public void parseWebAppManifest(String content, ManifestParseCallback callback) {
        nativeParseWebAppManifest(mNativePaymentManifestParserAndroid, content, callback);
    }

    @CalledByNative
    private static URI[] createWebAppManifestUris(int numberOfWebAppManifests) {
        return new URI[numberOfWebAppManifests];
    }

    @CalledByNative
    private static boolean addUri(URI[] uris, int uriIndex, String uriToAdd) {
        try {
            uris[uriIndex] = new URI(uriToAdd);
        } catch (URISyntaxException e) {
            return false;
        }
        return true;
    }

    @CalledByNative
    private static WebAppManifestSection[] createManifest(int numberOfsections) {
        return new WebAppManifestSection[numberOfsections];
    }

    @CalledByNative
    private static void addSectionToManifest(WebAppManifestSection[] manifest, int sectionIndex,
            String id, long minVersion, int numberOfFingerprints) {
        manifest[sectionIndex] = new WebAppManifestSection();
        manifest[sectionIndex].id = id;
        manifest[sectionIndex].minVersion = minVersion;
        manifest[sectionIndex].fingerprints = new byte[numberOfFingerprints][];
    }

    @CalledByNative
    private static void addFingerprintToSection(WebAppManifestSection[] manifest, int sectionIndex,
            int fingerprintIndex, byte[] fingerprint) {
        manifest[sectionIndex].fingerprints[fingerprintIndex] = fingerprint;
    }

    private static native long nativeCreatePaymentManifestParserAndroid();
    private static native void nativeStartUtilityProcess(long nativePaymentManifestParserAndroid);
    private static native void nativeParsePaymentMethodManifest(
            long nativePaymentManifestParserAndroid, String content,
            ManifestParseCallback callback);
    private static native void nativeParseWebAppManifest(long nativePaymentManifestParserAndroid,
            String content, ManifestParseCallback callback);
    private static native void nativeStopUtilityProcess(long nativePaymentManifestParserAndroid);
}

// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.customtabs;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.support.annotation.NonNull;

import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.base.library_loader.LibraryProcessType;
import org.chromium.chrome.browser.IntentHandler;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.content.browser.BrowserStartupController;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Used to verify postMessage origin for a designated package name.
 *
 * Uses Digital Asset Links to confirm that the given origin is associated with the package name as
 * a postMessage origin. It caches any origin that has been verified during the current application
 * lifecycle and reuses that without making any new network requests.
 *
 * The lifecycle of this object is governed by the owner. The owner has to call
 * {@link OriginVerifier#cleanUp()} for proper cleanup of dependencies.
 */
@JNINamespace("customtabs")
class OriginVerifier {
    private static final String TAG = "OriginVerifier";
    private static final char[] HEX_CHAR_LOOKUP = "0123456789ABCDEF".toCharArray();
    private static Map<String, Uri> sCachedOriginMap;
    private final OriginVerificationListener mListener;
    private final String mPackageName;
    private final String mSignatureFingerprint;
    private long mNativeOriginVerifier = 0;
    private Uri mOrigin;

    /**
     * To be used for prepopulating verified origin for testing functionality.
     * @param packageName The package name to prepopulate for.
     * @param origin The origin to add as verified.
     */
    @VisibleForTesting
    static void prePopulateVerifiedOriginForTesting(String packageName, Uri origin) {
        cacheVerifiedOriginIfNeeded(packageName, origin);
    }

    private static Uri getPostMessageOriginFromVerifiedOrigin(
            String packageName, Uri verifiedOrigin) {
        return Uri.parse(IntentHandler.ANDROID_APP_REFERRER_SCHEME + "://"
                + verifiedOrigin.getHost() + "/" + packageName);
    }

    private static void cacheVerifiedOriginIfNeeded(String packageName, Uri origin) {
        if (sCachedOriginMap == null) sCachedOriginMap = new HashMap<>();
        if (!sCachedOriginMap.containsKey(packageName)) {
            sCachedOriginMap.put(packageName, origin);
        }
    }

    /**
     * Callback interface for getting verification results.
     */
    public interface OriginVerificationListener {
        /**
         * To be posted on the handler thread after the verification finishes.
         * @param packageName The package name for the origin verification query for this result.
         * @param origin The origin that was declared on the query for this result.
         * @param verified Whether the given origin was verified to correspond to the given package.
         */
        void onOriginVerified(String packageName, Uri origin, boolean verified);
    }

    /**
     * Main constructor.
     * Use {@link OriginVerifier#start(Uri)}
     * @param listener The listener who will get the verification result.
     * @param packageName The package for the Android application for verification.
     */
    public OriginVerifier(OriginVerificationListener listener, String packageName) {
        mListener = listener;
        mPackageName = packageName;
        mSignatureFingerprint = getCertificateSHA256FingerprintForPackage(mPackageName);
    }

    /**
     * Verify the claimed origin for the cached package name asynchronously. This will end up
     * making a network request for non-cached origins with a URLFetcher using the last used
     * profile as context.
     * @param origin The postMessage origin the application is claiming to have. Can't be null.
     */
    public void start(@NonNull Uri origin) {
        ThreadUtils.assertOnUiThread();
        mOrigin = origin;
        if (!UrlConstants.HTTPS_SCHEME.equals(mOrigin.getScheme().toLowerCase(Locale.US))) {
            ThreadUtils.postOnUiThread(new Runnable() {
                @Override
                public void run() {
                    originVerified(false);
                }
            });
            return;
        }

        // If this origin is cached as verified already, use that.
        Uri cachedOrigin = getCachedOriginIfExists();
        if (cachedOrigin != null && cachedOrigin.equals(origin)) {
            ThreadUtils.postOnUiThread(new Runnable() {
                @Override
                public void run() {
                    originVerified(true);
                }
            });
            return;
        }
        if (mNativeOriginVerifier != 0) cleanUp();
        if (!BrowserStartupController.get(LibraryProcessType.PROCESS_BROWSER)
                        .isStartupSuccessfullyCompleted()) {
            // Early return for testing without native.
            return;
        }
        mNativeOriginVerifier = nativeInit(Profile.getLastUsedProfile().getOriginalProfile());
        assert mNativeOriginVerifier != 0;
        boolean success = nativeVerifyOrigin(
                mNativeOriginVerifier, mPackageName, mSignatureFingerprint, mOrigin.toString());
        if (!success) {
            ThreadUtils.postOnUiThread(new Runnable() {
                @Override
                public void run() {
                    originVerified(false);
                }
            });
        }
    }

    /**
     * Cleanup native dependencies on this object.
     */
    void cleanUp() {
        if (mNativeOriginVerifier == 0) return;
        nativeDestroy(mNativeOriginVerifier);
        mNativeOriginVerifier = 0;
    }

    private static PackageInfo getPackageInfo(String packageName) {
        PackageManager pm = ContextUtils.getApplicationContext().getPackageManager();

        PackageInfo packageInfo = null;
        try {
            packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
        } catch (PackageManager.NameNotFoundException e) {
            // Will return null if there is no package found.
        }
        return packageInfo;
    }

    /**
     * Computes the SHA256 certificate for the given package name. The app with the given package
     * name has to be installed on device. The output will be a 30 long HEX string with : between
     * each value.
     * @param packageName The package name to query the signature for.
     * @return The SHA256 certificate for the package name.
     */
    static String getCertificateSHA256FingerprintForPackage(String packageName) {
        PackageInfo packageInfo = getPackageInfo(packageName);
        if (packageInfo == null) return null;

        InputStream input = new ByteArrayInputStream(packageInfo.signatures[0].toByteArray());
        X509Certificate certificate = null;
        String hexString = null;
        try {
            certificate =
                    (X509Certificate) CertificateFactory.getInstance("X509").generateCertificate(
                            input);
            hexString = byteArrayToHexString(
                    MessageDigest.getInstance("SHA256").digest(certificate.getEncoded()));
        } catch (CertificateEncodingException e) {
            Log.w(TAG, "Certificate type X509 encoding failed");
        } catch (CertificateException | NoSuchAlgorithmException e) {
            // This shouldn't happen.
        }
        return hexString;
    }

    /**
     * Converts a byte array to hex string with : inserted between each element.
     * @param byteArray The array to be converted.
     * @return A string with two letters representing each byte and : in between.
     */
    static String byteArrayToHexString(byte[] byteArray) {
        StringBuilder hexString = new StringBuilder(byteArray.length * 3 - 1);
        for (int i = 0; i < byteArray.length; ++i) {
            hexString.append(HEX_CHAR_LOOKUP[(byteArray[i] & 0xf0) >>> 4]);
            hexString.append(HEX_CHAR_LOOKUP[byteArray[i] & 0xf]);
            if (i < (byteArray.length - 1)) hexString.append(':');
        }
        return hexString.toString();
    }

    @CalledByNative
    private void originVerified(boolean originVerified) {
        if (originVerified) {
            cacheVerifiedOriginIfNeeded(mPackageName, mOrigin);
            mOrigin = getPostMessageOriginFromVerifiedOrigin(mPackageName, mOrigin);
        }
        mListener.onOriginVerified(mPackageName, mOrigin, originVerified);
        cleanUp();
    }

    private Uri getCachedOriginIfExists() {
        if (sCachedOriginMap == null) return null;
        return sCachedOriginMap.get(mPackageName);
    }

    private native long nativeInit(Profile profile);
    private native boolean nativeVerifyOrigin(long nativeOriginVerifier, String packageName,
            String signatureFingerprint, String origin);
    private native void nativeDestroy(long nativeOriginVerifier);
}

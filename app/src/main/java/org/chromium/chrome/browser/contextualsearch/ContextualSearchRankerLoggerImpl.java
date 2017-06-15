// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.contextualsearch;

import org.chromium.base.Log;

import java.net.URL;

/**
 * Implements the UMA logging for Ranker that's used for Contextual Search Tap Suppression.
 */
public class ContextualSearchRankerLoggerImpl implements ContextualSearchRankerLogger {
    private static final String TAG = "ContextualSearch";

    // Pointer to the native instance of this class.
    private long mNativePointer;

    // Whether logging for the current URL is currently setup.
    private boolean mIsLoggingSetup;

    // Whether the service is ready to actually record log data.
    private boolean mCanServiceActuallyRecord;

    // Whether any data has been written to the log since calling setupLoggingForPage().
    private boolean mDidLog;

    /**
     * Constructs a Ranker Logger and associated native implementation to write Contextual Search
     * ML data to Ranker.
     */
    public ContextualSearchRankerLoggerImpl() {
        if (isEnabled()) mNativePointer = nativeInit();
    }

    /**
     * This method should be called to clean up storage when an instance of this class is
     * no longer in use.  The nativeDestroy will call the destructor on the native instance.
     */
    void destroy() {
        if (isEnabled()) {
            assert mNativePointer != 0;
            writeLogAndReset();
            nativeDestroy(mNativePointer);
            mNativePointer = 0;
            mCanServiceActuallyRecord = false;
            mDidLog = false;
        }
        mIsLoggingSetup = false;
    }

    @Override
    public void setupLoggingForPage(URL basePageUrl) {
        mIsLoggingSetup = true;
        if (isEnabled()) {
            // The URL may be null for custom Chrome URIs like chrome://flags.
            if (basePageUrl != null) {
                nativeSetupLoggingAndRanker(mNativePointer, basePageUrl.toString());
                mCanServiceActuallyRecord = true;
            }
        }
    }

    @Override
    public void log(Feature feature, Object value) {
        assert mIsLoggingSetup;
        if (!isEnabled()) return;

        // TODO(donnd): Add some enforcement that log() calls are done before inference time.
        logInternal(feature, value);
    }

    @Override
    public void logOutcome(Feature feature, Object value) {
        assert mIsLoggingSetup;
        if (!isEnabled()) return;

        logInternal(feature, value);
    }

    @Override
    public void writeLogAndReset() {
        if (isEnabled()) {
            if (mDidLog) nativeWriteLogAndReset(mNativePointer);
            mCanServiceActuallyRecord = false;
            mDidLog = false;
        }
        mIsLoggingSetup = false;
    }

    /** Whether actually writing data is enabled.  If not, we may do nothing or print. */
    private boolean isEnabled() {
        return ContextualSearchFieldTrial.isRankerLoggingEnabled();
    }

    /**
     * Logs the given feature/value after checking that logging has been set up.
     * @param feature The feature to log.
     * @param value The value to log.
     */
    private void logInternal(Feature feature, Object value) {
        if (value instanceof Boolean) {
            logToNative(feature.toString(), ((boolean) value ? 1 : 0));
        } else if (value instanceof Integer) {
            logToNative(feature.toString(), Long.valueOf((int) value));
        } else if (value instanceof Long) {
            logToNative(feature.toString(), (long) value);
        } else if (value instanceof Character) {
            logToNative(feature.toString(), Character.getNumericValue((char) value));
        } else {
            Log.w(TAG,
                    "Could not log feature to Ranker: " + feature.toString() + " of class "
                            + value.getClass());
        }
    }

    /**
     * Logs to the native instance.  All native logging must go through this bottleneck.
     * @param feature The feature to log.
     * @param value The value to log.
     */
    private void logToNative(String feature, long value) {
        if (mCanServiceActuallyRecord) {
            nativeLogLong(mNativePointer, feature, value);
            mDidLog = true;
        }
    }

    // ============================================================================================
    // Native methods.
    // ============================================================================================
    private native long nativeInit();
    private native void nativeDestroy(long nativeContextualSearchRankerLoggerImpl);
    private native void nativeLogLong(
            long nativeContextualSearchRankerLoggerImpl, String featureString, long value);
    private native void nativeSetupLoggingAndRanker(
            long nativeContextualSearchRankerLoggerImpl, String basePageUrl);
    private native void nativeWriteLogAndReset(long nativeContextualSearchRankerLoggerImpl);
}

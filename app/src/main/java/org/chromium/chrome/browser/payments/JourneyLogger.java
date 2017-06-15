// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.payments;

import org.chromium.base.annotations.JNINamespace;

/**
 * A class used to record journey metrics for the Payment Request feature.
 */
@JNINamespace("payments")
public class JourneyLogger {
    /**
     * Pointer to the native implementation.
     */
    private long mJourneyLoggerAndroid;

    private boolean mWasShowCalled;
    private boolean mHasRecorded;

    public JourneyLogger(boolean isIncognito, String url) {
        // Note that this pointer could leak the native object. The called must call destroy() to
        // ensure that the native object is destroyed.
        mJourneyLoggerAndroid = nativeInitJourneyLoggerAndroid(isIncognito, url);
    }

    /** Will destroy the native object. This class shouldn't be used afterwards. */
    public void destroy() {
        if (mJourneyLoggerAndroid != 0) {
            nativeDestroy(mJourneyLoggerAndroid);
            mJourneyLoggerAndroid = 0;
        }
    }

    /**
     * Sets the number of suggestions shown for the specified section.
     *
     * @param section The section for which to log.
     * @param number The number of suggestions.
     */
    public void setNumberOfSuggestionsShown(int section, int number) {
        assert section < Section.MAX;
        nativeSetNumberOfSuggestionsShown(mJourneyLoggerAndroid, section, number);
    }

    /**
     * Increments the number of selection changes for the specified section.
     *
     * @param section The section for which to log.
     */
    public void incrementSelectionChanges(int section) {
        assert section < Section.MAX;
        nativeIncrementSelectionChanges(mJourneyLoggerAndroid, section);
    }

    /**
     * Increments the number of selection edits for the specified section.
     *
     * @param section The section for which to log.
     */
    public void incrementSelectionEdits(int section) {
        assert section < Section.MAX;
        nativeIncrementSelectionEdits(mJourneyLoggerAndroid, section);
    }

    /**
     * Increments the number of selection adds for the specified section.
     *
     * @param section The section for which to log.
     */
    public void incrementSelectionAdds(int section) {
        assert section < Section.MAX;
        nativeIncrementSelectionAdds(mJourneyLoggerAndroid, section);
    }

    /**
     * Records the fact that the merchant called CanMakePayment and records it's return value.
     *
     * @param value The return value of the CanMakePayment call.
     */
    public void setCanMakePaymentValue(boolean value) {
        nativeSetCanMakePaymentValue(mJourneyLoggerAndroid, value);
    }

    /**
     * Records the fact that the Payment Request was shown to the user.
     */
    public void setShowCalled() {
        mWasShowCalled = true;
        nativeSetShowCalled(mJourneyLoggerAndroid);
    }

    /**
     * Records that an event occurred.
     *
     * @param event The event that occured.
     */
    public void setEventOccurred(int event) {
        assert event >= 0;
        assert event < Event.ENUM_MAX;
        nativeSetEventOccurred(mJourneyLoggerAndroid, event);
    }

    /**
     * Records the payment method that was selected by the user.
     *
     * @param paymentMethod The payment method that was selected.
     */
    public void setSelectedPaymentMethod(int paymentMethod) {
        assert paymentMethod >= 0;
        assert paymentMethod < SelectedPaymentMethod.MAX;
        nativeSetSelectedPaymentMethod(mJourneyLoggerAndroid, paymentMethod);
    }

    /**
     * Records that the Payment Request was completed sucessfully. Also starts the logging of
     * all the journey logger metrics.
     */
    public void setCompleted() {
        assert !mHasRecorded;
        assert mWasShowCalled;

        if (!mHasRecorded && mWasShowCalled) {
            mHasRecorded = true;
            nativeSetCompleted(mJourneyLoggerAndroid);
        }
    }

    /**
     * Records that the Payment Request was aborted and for what reason. Also starts the logging of
     * all the journey logger metrics.
     *
     * @param reason An int indicating why the payment request was aborted.
     */
    public void setAborted(int reason) {
        assert reason < AbortReason.MAX;
        assert mWasShowCalled;

        // The abort reasons on Android cascade into each other, so only the first one should be
        // recorded.
        if (!mHasRecorded && mWasShowCalled) {
            mHasRecorded = true;
            nativeSetAborted(mJourneyLoggerAndroid, reason);
        }
    }

    /**
     * Records that the Payment Request was not shown to the user and for what reason.
     *
     * @param reason An int indicating why the payment request was not shown.
     */
    public void setNotShown(int reason) {
        assert reason < NotShownReason.MAX;
        assert !mWasShowCalled;
        assert !mHasRecorded;

        if (!mHasRecorded) {
            mHasRecorded = true;
            nativeSetNotShown(mJourneyLoggerAndroid, reason);
        }
    }

    private native long nativeInitJourneyLoggerAndroid(boolean isIncognito, String url);
    private native void nativeDestroy(long nativeJourneyLoggerAndroid);
    private native void nativeSetNumberOfSuggestionsShown(
            long nativeJourneyLoggerAndroid, int section, int number);
    private native void nativeIncrementSelectionChanges(
            long nativeJourneyLoggerAndroid, int section);
    private native void nativeIncrementSelectionEdits(long nativeJourneyLoggerAndroid, int section);
    private native void nativeIncrementSelectionAdds(long nativeJourneyLoggerAndroid, int section);
    private native void nativeSetCanMakePaymentValue(
            long nativeJourneyLoggerAndroid, boolean value);
    private native void nativeSetShowCalled(long nativeJourneyLoggerAndroid);
    private native void nativeSetEventOccurred(long nativeJourneyLoggerAndroid, int event);
    private native void nativeSetSelectedPaymentMethod(
            long nativeJourneyLoggerAndroid, int paymentMethod);
    private native void nativeSetCompleted(long nativeJourneyLoggerAndroid);
    private native void nativeSetAborted(long nativeJourneyLoggerAndroid, int reason);
    private native void nativeSetNotShown(long nativeJourneyLoggerAndroid, int reason);
}
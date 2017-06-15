// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill;

import android.text.Editable;
import android.text.TextWatcher;

import org.chromium.base.annotations.JNINamespace;

/**
 * Android wrapper of i18n::phonenumbers::PhoneNumberUtil which provides convenient methods to
 * format and validate phone number.
 */
@JNINamespace("autofill")
public class PhoneNumberUtil {
    // Avoid instantiation by accident.
    private PhoneNumberUtil() {}

    /** TextWatcher to watch phone number changes so as to format it dynamically */
    public static class FormatTextWatcher implements TextWatcher {
        /** Indicates the change was caused by ourselves. */
        private boolean mSelfChange;

        @Override
        public void afterTextChanged(Editable s) {
            if (mSelfChange) return;

            String formattedNumber = formatForDisplay(s.toString());
            mSelfChange = true;
            s.replace(0, s.length(), formattedNumber, 0, formattedNumber.length());
            mSelfChange = false;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }

    /**
     * Formats the given phone number in INTERNATIONAL format
     * [i18n::phonenumbers::PhoneNumberUtil::PhoneNumberFormat::INTERNATIONAL], returning the
     * original number if no formatting can be made. For example, the number of the Google Zürich
     * office will be formatted as "+41 44 668 1800" in INTERNATIONAL format.
     *
     * @param phoneNumber The given phone number.
     * @return Formatted phone number.
     */
    public static String formatForDisplay(String phoneNumber) {
        return nativeFormatForDisplay(phoneNumber);
    }

    /**
     * Formats the given phone number in E.164 format as specified in the Payment Request spec
     * (https://w3c.github.io/browser-payment-api/#paymentrequest-updated-algorithm)
     * [i18n::phonenumbers::PhoneNumberUtil::PhoneNumberFormat::E164], returning the original number
     * if no formatting can be made. For example, the number of the Google Zürich office will be
     * formatted as "+41446681800" in E.164 format.
     *
     * @param phoneNumber The given phone number.
     * @return Formatted phone number.
     */
    public static String formatForResponse(String phoneNumber) {
        return nativeFormatForResponse(phoneNumber);
    }

    /**
     * Checks whether the given phone number matches a valid pattern according to region code. The
     * region code is from the given phone number if it starts with '+', otherwise application
     * locale is used to figure out the region code.
     *
     * @param phoneNumber The given phone number.
     * @return True if the given number is valid, otherwise return false.
     */
    public static boolean isValidNumber(String phoneNumber) {
        return nativeIsValidNumber(phoneNumber);
    }

    private static native String nativeFormatForDisplay(String phoneNumber);
    private static native String nativeFormatForResponse(String phoneNumber);
    private static native boolean nativeIsValidNumber(String phoneNumber);
}
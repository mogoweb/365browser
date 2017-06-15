// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.payments;

import org.chromium.payments.mojom.PaymentItem;
import org.chromium.payments.mojom.PaymentMethodData;

import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * The interface that a payment app implements. A payment app can get its data from Chrome autofill,
 * Android Pay, or third party apps.
 */
public interface PaymentApp {
    /**
     * The interface for the requester of instruments.
     */
    public interface InstrumentsCallback {
        /**
         * Called by this app to provide a list of instruments asynchronously.
         *
         * @param app         The calling app.
         * @param instruments The instruments from this app.
         */
        void onInstrumentsReady(PaymentApp app, List<PaymentInstrument> instruments);
    }

    /**
     * Provides a list of all payment instruments in this app. For example, this can be all credit
     * cards for the current profile. Can return null or empty list, e.g., if user has no locally
     * stored credit cards.
     *
     * @param methodDataMap    The map from methods to method specific data. The data contains such
     *                         information as whether the app should be invoked in test or
     *                         production mode, merchant identifier, or a public key.
     * @param origin           The origin of this merchant.
     * @param iframeOrigin     The origin of the iframe that invoked PaymentRequest. Same as origin
     *                         if PaymentRequest was not invoked from inside an iframe.
     * @param certificateChain The site certificate chain of the merchant. Null for localhost and
     *                         file on disk, which are secure origins without SSL.
     * @param total            The total amount that can be used to filter out instruments with
     *                         insufficient funds.
     * @param callback         The object that will receive the list of instruments.
     */
    void getInstruments(Map<String, PaymentMethodData> methodDataMap, String origin,
            String iframeOrigin, @Nullable byte[][] certificateChain, PaymentItem total,
            InstrumentsCallback callback);

    /**
     * Returns a list of all payment method names that this app supports. For example, ["visa",
     * "mastercard", "basic-card"] in basic card payments. Should return a list of at least one
     * method name. https://w3c.github.io/webpayments-methods-card/#method-id
     *
     * @return The list of all payment method names that this app supports.
     */
    Set<String> getAppMethodNames();

    /**
     * Checks whether the app can support the payment methods when the method-specific data is taken
     * into account.
     *
     * @param methodDataMap A mapping from the payment methods supported by this app to the
     *                      corresponding method-specific data. Should not be null.
     * @return True if the given methods are supported when the method-specific data is taken into
     *         account.
     */
    boolean supportsMethodsAndData(Map<String, PaymentMethodData> methodDataMap);

    /**
     * Returns the identifier for this payment app to be saved in user preferences. For example,
     * this can be "autofill", "https://android.com/pay", or "com.example.app.ExamplePaymentApp".
     *
     * @return The identifier for this payment app.
     */
    String getAppIdentifier();
}

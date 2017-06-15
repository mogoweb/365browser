// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.payments;

import android.graphics.drawable.Drawable;

import org.chromium.chrome.browser.payments.ui.PaymentOption;
import org.chromium.payments.mojom.PaymentDetailsModifier;
import org.chromium.payments.mojom.PaymentItem;
import org.chromium.payments.mojom.PaymentMethodData;

import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * The base class for a single payment instrument, e.g., a credit card.
 */
public abstract class PaymentInstrument extends PaymentOption {
    /**
     * The interface for the requester of instrument details.
     */
    public interface InstrumentDetailsCallback {
        /**
         * Called by the payment instrument to let Chrome know that the payment app's UI is
         * now hidden, but the payment instrument has not been returned yet. This is a good
         * time to show a "loading" progress indicator UI.
         */
        void onInstrumentDetailsLoadingWithoutUI();

        /**
         * Called after retrieving instrument details.
         *
         * @param methodName         Method name. For example, "visa".
         * @param stringifiedDetails JSON-serialized object. For example, {"card": "123"}.
         */
        void onInstrumentDetailsReady(String methodName, String stringifiedDetails);

        /**
         * Called if unable to retrieve instrument details.
         */
        void onInstrumentDetailsError();
    }

    protected PaymentInstrument(String id, String label, String sublabel, Drawable icon) {
        super(id, label, sublabel, icon);
    }

    /**
     * Sets the modified total for this payment instrument.
     *
     * @param modifiedTotal The new modified total to use.
     */
    public void setModifiedTotal(@Nullable String modifiedTotal) {
        updateTertiarylabel(modifiedTotal);
    }

    /**
     * Returns a set of payment method names for this instrument, e.g., "visa" or
     * "mastercard" in basic card payments:
     * https://w3c.github.io/webpayments-methods-card/#method-id
     *
     * @return The method names for this instrument.
     */
    public abstract Set<String> getInstrumentMethodNames();

    /**
     * Invoke the payment app to retrieve the instrument details.
     *
     * The callback will be invoked with the resulting payment details or error.
     *
     * @param id               The unique identifier of the PaymentRequest.
     * @param merchantName     The name of the merchant.
     * @param origin           The origin of this merchant.
     * @param iframeOrigin     The origin of the iframe that invoked PaymentRequest.
     * @param certificateChain The site certificate chain of the merchant. Can be null for localhost
     *                         or local file, which are secure contexts without SSL.
     * @param methodDataMap    The payment-method specific data for all applicable payment methods,
     *                         e.g., whether the app should be invoked in test or production, a
     *                         merchant identifier, or a public key.
     * @param total            The total amount.
     * @param displayItems     The shopping cart items.
     * @param modifiers        The relevant payment details modifiers.
     * @param callback         The object that will receive the instrument details.
     */
    public abstract void invokePaymentApp(String id, String merchantName, String origin,
            String iframeOrigin, @Nullable byte[][] certificateChain,
            Map<String, PaymentMethodData> methodDataMap, PaymentItem total,
            List<PaymentItem> displayItems, Map<String, PaymentDetailsModifier> modifiers,
            InstrumentDetailsCallback callback);

    /**
     * Cleans up any resources held by the payment instrument. For example, closes server
     * connections.
     */
    public abstract void dismissInstrument();
}

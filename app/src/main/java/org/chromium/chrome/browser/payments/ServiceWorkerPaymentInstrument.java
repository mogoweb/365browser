// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.payments;

import org.chromium.content_public.browser.WebContents;
import org.chromium.payments.mojom.PaymentDetailsModifier;
import org.chromium.payments.mojom.PaymentItem;
import org.chromium.payments.mojom.PaymentMethodData;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This instrument class represents a single payment instrument for a service
 * worker based payment app.
 *
 * @see org.chromium.chrome.browser.payments.ServiceWorkerPaymentApp
 *
 * @see https://w3c.github.io/webpayments-payment-handler/
 */
public class ServiceWorkerPaymentInstrument extends PaymentInstrument {
    private final WebContents mWebContents;
    private final long mSWRegistrationId;
    private final String mInstrumentId;
    private final Set<String> mMethodNames;

    /**
     * Build a service worker based payment instrument.
     *
     * @see https://w3c.github.io/webpayments-payment-apps-api/#payment-app-options
     *
     * @param webContents       The web contents where PaymentRequest was invoked.
     * @param swRegistrationId  The registration id of the corresponding service worker payment app.
     * @param instrumentId      The unique id of the payment instrument.
     * @param label             The label of the payment instrument.
     * @param methodNames       A set of payment method names supported by the payment instrument.
     */
    public ServiceWorkerPaymentInstrument(WebContents webContents, long swRegistrationId,
            String instrumentId, String label, Set<String> methodNames) {
        super(Long.toString(swRegistrationId) + "#" + instrumentId, label, null /* sublabel */,
                null /* icon */);
        mWebContents = webContents;
        mSWRegistrationId = swRegistrationId;
        mInstrumentId = instrumentId;
        mMethodNames = methodNames;
    }

    @Override
    public Set<String> getInstrumentMethodNames() {
        return Collections.unmodifiableSet(mMethodNames);
    }

    @Override
    public void invokePaymentApp(String id, String merchantName, String origin, String iframeOrigin,
            byte[][] unusedCertificateChain, Map<String, PaymentMethodData> methodData,
            PaymentItem total, List<PaymentItem> displayItems,
            Map<String, PaymentDetailsModifier> modifiers, InstrumentDetailsCallback callback) {
        ServiceWorkerPaymentAppBridge.invokePaymentApp(mWebContents, mSWRegistrationId, origin,
                iframeOrigin, id, new HashSet<>(methodData.values()), total,
                new HashSet<>(modifiers.values()), mInstrumentId, callback);
    }

    @Override
    public void dismissInstrument() {}
}

// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.payments;

import android.os.Handler;

import org.chromium.content_public.browser.WebContents;
import org.chromium.payments.mojom.PaymentItem;
import org.chromium.payments.mojom.PaymentMethodData;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This app class represents a service worker based payment app.
 *
 * Such apps are implemented as service workers according to the Payment
 * Handler API specification.
 *
 * @see https://w3c.github.io/webpayments-payment-handler/
 */
public class ServiceWorkerPaymentApp implements PaymentApp {
    private final WebContents mWebContents;
    private final List<PaymentInstrument> mInstruments;
    private final Set<String> mMethodNames;

    /**
     * Build a service worker payment app instance per origin.
     *
     * @see https://w3c.github.io/webpayments-payment-handler/#structure-of-a-web-payment-app
     *
     * @param webContents The web contents where PaymentRequest was invoked.
     * @param instruments A list of payment instruments supported by the payment app.
     */
    public ServiceWorkerPaymentApp(WebContents webContents, List<PaymentInstrument> instruments) {
        mWebContents = webContents;
        mInstruments = instruments;

        mMethodNames = new HashSet<>();
        for (PaymentInstrument instrument : instruments) {
            mMethodNames.addAll(instrument.getInstrumentMethodNames());
        }
    }

    @Override
    public void getInstruments(Map<String, PaymentMethodData> unusedMethodDataMap,
            String unusedOrigin, String unusedIFrameOrigin, byte[][] unusedCertificateChain,
            PaymentItem unusedItem, final InstrumentsCallback callback) {
        new Handler().post(new Runnable() {
            @Override
            public void run() {
                callback.onInstrumentsReady(
                        ServiceWorkerPaymentApp.this, Collections.unmodifiableList(mInstruments));
            }
        });
    }

    @Override
    public Set<String> getAppMethodNames() {
        return Collections.unmodifiableSet(mMethodNames);
    }

    @Override
    public boolean supportsMethodsAndData(Map<String, PaymentMethodData> methodsAndData) {
        // TODO(tommyt): crbug.com/669876. Implement this for Service Worker Payment Apps.
        return true;
    }

    @Override
    public String getAppIdentifier() {
        return "Chrome_Service_Worker_Payment_App";
    }
}

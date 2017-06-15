// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.payments;

import android.os.Handler;
import android.text.TextUtils;

import org.chromium.chrome.browser.autofill.PersonalDataManager;
import org.chromium.chrome.browser.autofill.PersonalDataManager.AutofillProfile;
import org.chromium.chrome.browser.autofill.PersonalDataManager.CreditCard;
import org.chromium.content_public.browser.WebContents;
import org.chromium.payments.mojom.BasicCardNetwork;
import org.chromium.payments.mojom.PaymentItem;
import org.chromium.payments.mojom.PaymentMethodData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Provides access to locally stored user credit cards.
 */
public class AutofillPaymentApp implements PaymentApp {
    /** The method name for any type of credit card. */
    public static final String BASIC_CARD_METHOD_NAME = "basic-card";

    private final WebContents mWebContents;

    /**
     * Builds a payment app backed by autofill cards.
     *
     * @param webContents The web contents where PaymentRequest was invoked.
     */
    public AutofillPaymentApp(WebContents webContents) {
        mWebContents = webContents;
    }

    @Override
    public void getInstruments(Map<String, PaymentMethodData> methodDataMap, String unusedOrigin,
            String unusedIFRameOrigin, byte[][] unusedCertificateChain, PaymentItem unusedTotal,
            final InstrumentsCallback callback) {
        PersonalDataManager pdm = PersonalDataManager.getInstance();
        List<CreditCard> cards = pdm.getCreditCardsToSuggest();
        final List<PaymentInstrument> instruments = new ArrayList<>(cards.size());

        Set<String> basicCardSupportedNetworks =
                convertBasicCardToNetworks(methodDataMap.get(BASIC_CARD_METHOD_NAME));

        for (int i = 0; i < cards.size(); i++) {
            CreditCard card = cards.get(i);
            AutofillProfile billingAddress = TextUtils.isEmpty(card.getBillingAddressId())
                    ? null : pdm.getProfile(card.getBillingAddressId());

            if (billingAddress != null
                    && AutofillAddress.checkAddressCompletionStatus(
                               billingAddress, AutofillAddress.IGNORE_PHONE_COMPLETENESS_CHECK)
                            != AutofillAddress.COMPLETE) {
                billingAddress = null;
            }

            if (billingAddress == null) card.setBillingAddressId(null);

            String methodName = null;
            if (basicCardSupportedNetworks != null
                    && basicCardSupportedNetworks.contains(card.getBasicCardIssuerNetwork())) {
                methodName = BASIC_CARD_METHOD_NAME;
            } else if (methodDataMap.containsKey(card.getBasicCardIssuerNetwork())) {
                methodName = card.getBasicCardIssuerNetwork();
            }

            if (methodName != null) {
                instruments.add(new AutofillPaymentInstrument(
                        mWebContents, card, billingAddress, methodName));
            }
        }

        new Handler().post(new Runnable() {
            @Override
            public void run() {
                callback.onInstrumentsReady(AutofillPaymentApp.this, instruments);
            }
        });
    }

    /** @return A set of card networks (e.g., "visa", "amex") accepted by "basic-card" method. */
    public static Set<String> convertBasicCardToNetworks(PaymentMethodData data) {
        // Merchant website does not support any issuer networks.
        if (data == null) return null;

        // Merchant website supports all issuer networks.
        Map<Integer, String> networks = getNetworks();
        if (data.supportedNetworks == null || data.supportedNetworks.length == 0) {
            return new HashSet<>(networks.values());
        }

        // Merchant website supports some issuer networks.
        Set<String> result = new HashSet<>();
        for (int i = 0; i < data.supportedNetworks.length; i++) {
            String network = networks.get(data.supportedNetworks[i]);
            if (network != null) result.add(network);
        }
        return result;
    }

    private static Map<Integer, String> getNetworks() {
        Map<Integer, String> networks = new HashMap<>();
        networks.put(BasicCardNetwork.AMEX, "amex");
        networks.put(BasicCardNetwork.DINERS, "diners");
        networks.put(BasicCardNetwork.DISCOVER, "discover");
        networks.put(BasicCardNetwork.JCB, "jcb");
        networks.put(BasicCardNetwork.MASTERCARD, "mastercard");
        networks.put(BasicCardNetwork.MIR, "mir");
        networks.put(BasicCardNetwork.UNIONPAY, "unionpay");
        networks.put(BasicCardNetwork.VISA, "visa");
        return networks;
    }

    @Override
    public Set<String> getAppMethodNames() {
        Set<String> methods = new HashSet<>(getNetworks().values());
        methods.add(BASIC_CARD_METHOD_NAME);
        return methods;
    }

    @Override
    public boolean supportsMethodsAndData(Map<String, PaymentMethodData> methodDataMap) {
        assert methodDataMap != null;

        PaymentMethodData basicCardData = methodDataMap.get(BASIC_CARD_METHOD_NAME);
        if (basicCardData != null) {
            Set<String> basicCardNetworks = convertBasicCardToNetworks(basicCardData);
            if (basicCardNetworks != null && !basicCardNetworks.isEmpty()) return true;
        }

        Set<String> methodNames = new HashSet<>(methodDataMap.keySet());
        methodNames.retainAll(getNetworks().values());
        return !methodNames.isEmpty();
    }

    @Override
    public String getAppIdentifier() {
        return "Chrome_Autofill_Payment_App";
    }
}

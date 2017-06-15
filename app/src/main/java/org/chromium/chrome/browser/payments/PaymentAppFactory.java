// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.payments;

import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.content_public.browser.WebContents;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds instances of payment apps.
 */
public class PaymentAppFactory {
    private static PaymentAppFactory sInstance;

    /**
     * Can be used to build additional types of payment apps without Chrome knowing about their
     * types.
     */
    private final List<PaymentAppFactoryAddition> mAdditionalFactories;

    /**
     * Interface for receiving newly created apps.
     */
    public interface PaymentAppCreatedCallback {
        /**
         * Called when the factory has create a payment app. This method may be called
         * zero, one, or many times before the app creation is finished.
         */
        void onPaymentAppCreated(PaymentApp paymentApp);

        /**
         * Called when the factory is finished creating payment apps.
         */
        void onAllPaymentAppsCreated();
    }

    /**
     * The interface for additional payment app factories.
     */
    public interface PaymentAppFactoryAddition {
        /**
         * Builds instances of payment apps.
         *
         * @param webContents The web contents that invoked PaymentRequest.
         * @param methods     The methods that the merchant supports.
         * @param callback    The callback to invoke when apps are created.
         */
        void create(
                WebContents webContents, Set<String> methods, PaymentAppCreatedCallback callback);
    }

    private PaymentAppFactory() {
        mAdditionalFactories = new ArrayList<>();

        if (ChromeFeatureList.isEnabled(ChromeFeatureList.ANDROID_PAYMENT_APPS)) {
            mAdditionalFactories.add(new AndroidPaymentAppFactory());
        }

        if (ChromeFeatureList.isEnabled(ChromeFeatureList.SERVICE_WORKER_PAYMENT_APPS)) {
            mAdditionalFactories.add(new ServiceWorkerPaymentAppBridge());
        }
    }

    /**
     * @return The singleton PaymentAppFactory instance.
     */
    public static PaymentAppFactory getInstance() {
        if (sInstance == null) sInstance = new PaymentAppFactory();
        return sInstance;
    }

    /**
     * Add an additional factory that can build instances of payment apps.
     *
     * @param additionalFactory Can build instances of payment apps.
     */
    @VisibleForTesting
    public void addAdditionalFactory(PaymentAppFactoryAddition additionalFactory) {
        mAdditionalFactories.add(additionalFactory);
    }

    /**
     * Builds instances of payment apps.
     *
     * @param webContents The web contents where PaymentRequest was invoked.
     * @param methods     The methods that the merchant supports.
     * @param callback    The callback to invoke when apps are created.
     */
    public void create(WebContents webContents, Set<String> methods,
            final PaymentAppCreatedCallback callback) {
        callback.onPaymentAppCreated(new AutofillPaymentApp(webContents));

        if (mAdditionalFactories.isEmpty()) {
            callback.onAllPaymentAppsCreated();
            return;
        }

        final Set<PaymentAppFactoryAddition> mPendingTasks =
                new HashSet<PaymentAppFactoryAddition>(mAdditionalFactories);

        for (int i = 0; i < mAdditionalFactories.size(); i++) {
            final PaymentAppFactoryAddition additionalFactory = mAdditionalFactories.get(i);
            PaymentAppCreatedCallback cb = new PaymentAppCreatedCallback() {
                @Override
                public void onPaymentAppCreated(PaymentApp paymentApp) {
                    callback.onPaymentAppCreated(paymentApp);
                }

                @Override
                public void onAllPaymentAppsCreated() {
                    mPendingTasks.remove(additionalFactory);
                    if (mPendingTasks.isEmpty()) callback.onAllPaymentAppsCreated();
                }
            };
            additionalFactory.create(webContents, methods, cb);
        }
    }
}

// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.payments;

import org.chromium.base.metrics.RecordHistogram;

/**
 * A class used to record metrics for the Payment Request feature.
 */
public final class PaymentRequestMetrics {
    // There should be no instance of PaymentRequestMetrics created.
    private PaymentRequestMetrics() {}

    /*
     * Records the metric that keeps track of what user information are requested by merchants to
     * complete a payment request.
     *
     * @param requestEmail    Whether the merchant requested an email address.
     * @param requestPhone    Whether the merchant requested a phone number.
     * @param requestShipping Whether the merchant requested a shipping address.
     * @param requestName     Whether the merchant requestes a name.
     */
    public static void recordRequestedInformationHistogram(boolean requestEmail,
            boolean requestPhone, boolean requestShipping, boolean requestName) {
        int requestInformation = (requestEmail ? RequestedInformation.EMAIL : 0)
                | (requestPhone ? RequestedInformation.PHONE : 0)
                | (requestShipping ? RequestedInformation.SHIPPING : 0)
                | (requestName ? RequestedInformation.NAME : 0);
        RecordHistogram.recordEnumeratedHistogram("PaymentRequest.RequestedInformation",
                requestInformation, RequestedInformation.MAX);
    }
}

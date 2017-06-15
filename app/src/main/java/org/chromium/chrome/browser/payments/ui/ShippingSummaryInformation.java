// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.payments.ui;

import javax.annotation.Nullable;

/** The data to show in a shipping summary section. It contains shipping address and option. */
public class ShippingSummaryInformation {
    private SectionInformation mShippingAddress;
    private SectionInformation mShippingOption;

    /** Builds ShppingSummaryInformation with shipping address and option section information. */
    public ShippingSummaryInformation(
            SectionInformation shippingAddress, SectionInformation shippingOption) {
        mShippingAddress = shippingAddress;
        mShippingOption = shippingOption;
    }

    /**
     * Returns the label for the selected shipping address.
     *
     * @return The label for the selected shipping address or null.
     */
    @Nullable
    public String getSelectedShippingAddressLabel() {
        PaymentOption address = mShippingAddress.getSelectedItem();
        return address != null ? address.getLabel() : null;
    }

    /**
     * Returns the sublabel for the selected shipping address.
     *
     * @return The sublabel for the selected shipping address or null.
     */
    @Nullable
    public String getSelectedShippingAddressSublabel() {
        PaymentOption address = mShippingAddress.getSelectedItem();
        return address != null ? address.getSublabel() : null;
    }

    /**
     * Returns the tertiary label for the selected shipping address.
     *
     * @return The tertiary label for the selected shipping address or null.
     */
    @Nullable
    public String getSelectedShippingAddressTertiaryLabel() {
        PaymentOption address = mShippingAddress.getSelectedItem();
        return address != null ? address.getTertiaryLabel() : null;
    }

    /**
     * Returns the label for the selected shipping option.
     *
     * @return The label for the selected shipping option or null.
     */
    @Nullable
    public String getSelectedShippingOptionLabel() {
        PaymentOption option = mShippingOption.getSelectedItem();
        return option != null ? option.getLabel() : null;
    }

    /**
     * Returns the shipping address section information.
     *
     * @return The shipping address section information.
     */
    public SectionInformation getShippingAddressSectionInfo() {
        return mShippingAddress;
    }
}

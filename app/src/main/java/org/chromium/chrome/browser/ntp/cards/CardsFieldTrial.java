// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp.cards;

import android.text.TextUtils;

import org.chromium.base.CommandLine;
import org.chromium.base.Log;
import org.chromium.chrome.browser.ntp.NewTabPage;
import org.chromium.components.variations.VariationsAssociatedData;

/**
 * Provides easy access to data for field trials to do with the Cards UI.
 */
public final class CardsFieldTrial {
    private static final String TAG = "CardFinchExperiments";

    // TODO(peconn): Move NewTabPage.FIELD_TRIAL_NAME and all uses into this class.
    private static final String FIRST_CARD_OFFSET = "first_card_offset";

    private CardsFieldTrial() {
    }

    /**
     * Provides the value of the field trial to offset the peeking card (can be overridden
     * with a command line flag). It will return 0 if there is no such field trial.
     */
    public static int getFirstCardOffsetDp() {
        String value = CommandLine.getInstance().getSwitchValue(FIRST_CARD_OFFSET);

        if (TextUtils.isEmpty(value)) {
            value = VariationsAssociatedData.getVariationParamValue(NewTabPage.FIELD_TRIAL_NAME,
                    FIRST_CARD_OFFSET);
        }

        if (!TextUtils.isEmpty(value)) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ex) {
                Log.w(TAG, "Cannot parse card offset experiment value, %s.", value);
            }
        }

        return 0;
    }
}

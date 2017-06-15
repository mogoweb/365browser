// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.physicalweb;

import android.app.IntentService;
import android.content.Intent;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.messages.MessageListener;

/**
 * Service that handles intents from Nearby.
 */
public class NearbyMessageIntentService extends IntentService {
    private static final MessageListener MESSAGE_LISTENER =
            PhysicalWebBleClient.getInstance().createBackgroundMessageListener();


    public NearbyMessageIntentService() {
        super(NearbyMessageIntentService.class.getSimpleName());
        setIntentRedelivery(true);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Nearby.Messages.handleIntent(intent, MESSAGE_LISTENER);
    }
}

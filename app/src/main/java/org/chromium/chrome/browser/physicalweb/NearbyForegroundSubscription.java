// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.physicalweb;

import android.app.Activity;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.messages.Distance;
import com.google.android.gms.nearby.messages.Message;
import com.google.android.gms.nearby.messages.MessageListener;

import org.chromium.base.ThreadUtils;

/**
 * This class represents a connection to Google Play Services that does foreground
 * subscription/unsubscription to Nearby Eddystone-URLs.
 * To use this class, one should:
 * 1. connect,
 * 2. subscribe,
 * 3. unsubscribe,
 * 4. repeat steps 2-3 as desired, and
 * 5. disconnect.
 */
class NearbyForegroundSubscription extends NearbySubscription {
    private static final String TAG = "PhysicalWeb";
    private static final MessageListener MESSAGE_LISTENER = new MessageListener() {
        @Override
        public void onFound(Message message) {}

        @Override
        public void onDistanceChanged(Message message, final Distance distance) {
            final String url = PhysicalWebBleClient.getInstance().getUrlFromMessage(message);
            if (url == null) return;

            ThreadUtils.postOnUiThread(new Runnable() {
                @Override
                public void run() {
                    UrlManager.getInstance().addUrl(
                            new UrlInfo(url).setDistance(distance.getMeters()));
                }
            });
        }
    };
    private boolean mShouldSubscribe;

    NearbyForegroundSubscription(Activity activity) {
        super(activity);
        mShouldSubscribe = false;
    }

    @Override
    protected void onConnected() {
        if (mShouldSubscribe) {
            subscribe();
        }
    }

    void subscribe() {
        if (!getGoogleApiClient().isConnected()) {
            mShouldSubscribe = true;
            return;
        }
        Nearby.Messages.subscribe(getGoogleApiClient(), MESSAGE_LISTENER, createSubscribeOptions())
                .setResultCallback(new SimpleResultCallback("foreground subscribe"));
    }

    void unsubscribe() {
        if (!getGoogleApiClient().isConnected()) {
            mShouldSubscribe = false;
            return;
        }
        Nearby.Messages.unsubscribe(getGoogleApiClient(), MESSAGE_LISTENER)
                .setResultCallback(new SimpleResultCallback("foreground unsubscribe"));
    }
}

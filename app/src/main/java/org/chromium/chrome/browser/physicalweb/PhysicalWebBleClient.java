// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.physicalweb;

import android.os.Handler;
import android.os.Looper;

import com.google.android.gms.nearby.messages.Message;
import com.google.android.gms.nearby.messages.MessageFilter;
import com.google.android.gms.nearby.messages.MessageListener;

import org.chromium.chrome.browser.AppHooks;

/**
 * The Client that harvests URLs from BLE signals.
 * This class is designed to scan URLs from Bluetooth Low Energy beacons.
 * This class is currently an empty implementation and must be extended by a
 * subclass.
 */
public class PhysicalWebBleClient {
    private static PhysicalWebBleClient sInstance;
    private static final String TAG = "PhysicalWeb";

    protected static class BackgroundMessageListener extends MessageListener {
        @Override
        public void onFound(Message message) {
            final String url = PhysicalWebBleClient.getInstance().getUrlFromMessage(message);
            if (url != null) {
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        UrlManager.getInstance().addUrl(new UrlInfo(url));
                    }
                });
            }
        }

        @Override
        public void onLost(Message message) {
            final String url = PhysicalWebBleClient.getInstance().getUrlFromMessage(message);
            if (url != null) {
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        UrlManager.getInstance().removeUrl(new UrlInfo(url));
                    }
                });
            }
        }
    };

    /**
     * Get a singleton instance of this class.
     * @return an instance of this class (or subclass).
     */
    public static PhysicalWebBleClient getInstance() {
        if (sInstance == null) {
            sInstance = AppHooks.get().createPhysicalWebBleClient();
        }
        return sInstance;
    }

    /**
     * Create a MessageListener that listens during a background scan.
     * @return the MessageListener.
     */
    MessageListener createBackgroundMessageListener() {
        return new BackgroundMessageListener();
    }

    /**
     * Get the URLs from a device within a message.
     * @param message The Nearby message.
     * @return The URL contained in the message.
     */
    String getUrlFromMessage(Message message) {
        return null;
    }

    /**
     * Modify a MessageFilter.Builder as necessary for doing Physical Web scanning.
     * @param builder The builder to be modified.
     * @return The Builder.
     */
    MessageFilter.Builder modifyMessageFilterBuilder(MessageFilter.Builder builder) {
        return builder.includeAllMyTypes();
    }
}

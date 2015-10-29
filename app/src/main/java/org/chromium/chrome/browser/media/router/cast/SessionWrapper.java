// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.router.cast;

import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

import org.chromium.base.Log;
import org.chromium.chrome.browser.media.router.ChromeMediaRouter;

import java.io.IOException;

/**
 * A wrapper around the established Cast application session.
 */
public class SessionWrapper {
    private static final String TAG = "cr.MediaRouter";

    private static class PresentationApiChannel implements Cast.MessageReceivedCallback {
        private SessionWrapper mSession;

        public PresentationApiChannel(SessionWrapper session) {
            mSession = session;
        }

        public String getNamespace() {
            return "urn:x-cast:presentation-api";
        }

        @Override
        public void onMessageReceived(CastDevice castDevice, String namespace, String message) {
            if (!getNamespace().equals(namespace)) return;
            mSession.onMessage(message);
        }
    }

    private GoogleApiClient mApiClient;
    private final String mMediaRouteId;
    private String mSessionId;
    private final PresentationApiChannel mChannel;
    private final ChromeMediaRouter mMediaRouter;

    /**
     * Initializes a new {@link SessionWrapper} instance.
     * @param apiClient The Google Play Services client used to create the session.
     * @param sessionId The session identifier to use with the Cast SDK.
     * @param mediaRouteId The media route identifier associated with this session.
     * @param mediaRouter The {@link ChromeMediaRouter} instance managing this session.
     */
    public SessionWrapper(
            GoogleApiClient apiClient,
            String sessionId,
            String mediaRouteId,
            ChromeMediaRouter mediaRouter) {
        mApiClient = apiClient;
        mSessionId = sessionId;
        mMediaRouteId = mediaRouteId;
        mMediaRouter = mediaRouter;

        mChannel = new PresentationApiChannel(this);
        try {
            Cast.CastApi.setMessageReceivedCallbacks(mApiClient, mChannel.getNamespace(), mChannel);
        } catch (IOException e) {
            Log.e(TAG, "Exception while creating channel", e);
        }
    }

    /**
     * Stops the Cast application associated with this session.
     */
    public void stop() {
        assert mApiClient != null;

        if (mApiClient.isConnected() || mApiClient.isConnecting()) {
            Cast.CastApi.stopApplication(mApiClient, mSessionId);
        }

        mSessionId = null;
        mApiClient = null;
    }

    /**
     * Send a string message to the session and invokes the {@link ChromeMediaRouter} with the
     * passed callback id on success or failure.
     * @param message The message to send.
     * @param callbackId The id of the callback handling the result.
     */
    public void sendStringMessage(String message, final int callbackId) {
        if (!mApiClient.isConnected() && !mApiClient.isConnecting()) {
            mMediaRouter.onMessageSentResult(false, callbackId);
            return;
        }

        try {
            Cast.CastApi.sendMessage(mApiClient, mChannel.getNamespace(), message)
                    .setResultCallback(
                            new ResultCallback<Status>() {
                                @Override
                                public void onResult(Status result) {
                                    mMediaRouter.onMessageSentResult(
                                            result.isSuccess(), callbackId);
                                }
                            });
        } catch (Exception e) {
            Log.e(TAG, "Exception while sending message", e);
            mMediaRouter.onMessageSentResult(false, callbackId);
        }
    }

    public void onMessage(String message) {
        mMediaRouter.onMessage(mMediaRouteId, message);
    }
}

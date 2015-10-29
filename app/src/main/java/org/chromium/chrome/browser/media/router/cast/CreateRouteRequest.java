// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.router.cast;

import android.content.Context;
import android.os.Bundle;
import android.support.v7.media.MediaRouter;

import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

import org.chromium.chrome.browser.media.router.ChromeMediaRouter;

/**
 * Establishes a {@link MediaRoute} by starting a Cast application represented by the given
 * presentation URL. Reports success or failure to {@link ChromeMediaRouter}.
 * Since there're numerous asynchronous calls involved in getting the application to launch
 * the class is implemented as a state machine.
 */
public class CreateRouteRequest implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        ResultCallback<Cast.ApplicationConnectionResult> {
    private static final int STATE_IDLE = 0;
    private static final int STATE_CONNECTING_TO_API = 1;
    private static final int STATE_API_CONNECTION_SUSPENDED = 2;
    private static final int STATE_LAUNCHING_APPLICATION = 3;
    private static final int STATE_LAUNCH_SUCCEEDED = 4;
    private static final int STATE_TERMINATED = 5;

    private static final String ERROR_NEW_ROUTE_INVALID_SOURCE_URN = "Invalid source URN: %s";
    private static final String ERROR_NEW_ROUTE_INVALID_SINK_URN = "Invalid sink URN: %s";
    private static final String ERROR_NEW_ROUTE_LAUNCH_APPLICATION_FAILED =
            "Launch application failed: %s, %s";
    private static final String ERROR_NEW_ROUTE_LAUNCH_APPLICATION_FAILED_STATUS =
            "Launch application failed with status: %s, %d, %s";
    private static final String ERROR_NEW_ROUTE_CLIENT_CONNECTION_FAILED =
            "GoogleApiClient connection failed: %d, %b";

    private final String mSourceUrn;
    private final MediaSource mMediaSource;
    private final String mSinkId;
    private final String mPresentationId;
    private final int mRequestId;
    private final ChromeMediaRouter mMediaRouter;

    private GoogleApiClient mApiClient;
    private MediaRouter.RouteInfo mRoute;
    private int mState = STATE_IDLE;

    /**
     * Initializes the request.
     * @param sourceUrn The URN defining the application to launch on the Cast device
     * @param sinkUrn The URN identifying the selected Cast device
     * @param presentationId The presentation id assigned to the route by {@link ChromeMediaRouter}
     * @param requestId The id of the route creation request for tracking by
     * {@link ChromeMediaRouter}
     * @param router The instance of {@link ChromeMediaRouter} handling the request
     */
    public CreateRouteRequest(
            String sourceUrn,
            String sinkUrn,
            String presentationId,
            int requestId,
            ChromeMediaRouter router) {
        mSourceUrn = sourceUrn;
        mMediaSource = MediaSource.from(sourceUrn);
        mSinkId = sinkUrn;
        mPresentationId = presentationId;
        mRequestId = requestId;
        mMediaRouter = router;
    }

    /**
     * Starts the process of launching the application on the Cast device.
     * @param androidMediaRouter Android's {@link MediaRouter} instance.
     * @param applicationContext application context
     * @param castApplicationListener {@link com.google.android.gms.cast.Cast.Listener}
     * implementation provided by the caller.
     */
    public void start(
            MediaRouter androidMediaRouter,
            Context applicationContext,
            Cast.Listener castApplicationListener) {
        assert androidMediaRouter != null;
        assert applicationContext != null;
        assert castApplicationListener != null;

        if (mState != STATE_IDLE) throwInvalidState();

        if (mMediaSource == null) {
            reportError(String.format(ERROR_NEW_ROUTE_INVALID_SOURCE_URN, mSourceUrn));
            return;
        }

        mRoute = null;
        for (MediaRouter.RouteInfo route : androidMediaRouter.getRoutes()) {
            MediaSink routeSink = MediaSink.fromRoute(route);
            if (routeSink.getId().equals(mSinkId)) {
                mRoute = route;
                break;
            }
        }

        if (mRoute == null) {
            reportError(String.format(ERROR_NEW_ROUTE_INVALID_SINK_URN, mSinkId));
            return;
        }

        mApiClient = createApiClient(mRoute, castApplicationListener, applicationContext);
        mApiClient.connect();
        mState = STATE_CONNECTING_TO_API;
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        if (mState != STATE_CONNECTING_TO_API && mState != STATE_API_CONNECTION_SUSPENDED) {
            throwInvalidState();
        }

        // TODO(avayvod): switch to using ConnectedTask class for GoogleApiClient operations.
        // See https://crbug.com/522478
        if (mState == STATE_API_CONNECTION_SUSPENDED) return;

        try {
            launchApplication(mApiClient, mMediaSource.getApplicationId(), false)
                    .setResultCallback(this);
            mState = STATE_LAUNCHING_APPLICATION;
        } catch (Exception e) {
            reportError(String.format(ERROR_NEW_ROUTE_LAUNCH_APPLICATION_FAILED,
                    mMediaSource.getApplicationId(), e));
        }
    }

    // TODO(avayvod): switch to using ConnectedTask class for GoogleApiClient operations.
    // See https://crbug.com/522478
    @Override
    public void onConnectionSuspended(int cause) {
        mState = STATE_API_CONNECTION_SUSPENDED;
    }

    @Override
    public void onResult(Cast.ApplicationConnectionResult result) {
        if (mState != STATE_LAUNCHING_APPLICATION) throwInvalidState();

        Status status = result.getStatus();
        if (!status.isSuccess()) {
            reportError(String.format(
                    ERROR_NEW_ROUTE_LAUNCH_APPLICATION_FAILED_STATUS,
                    mMediaSource.getApplicationId(),
                    status.getStatusCode(),
                    status.getStatusMessage()));
        }

        mState = STATE_LAUNCH_SUCCEEDED;
        reportSuccess(result.getSessionId(), result.getWasLaunched());
    }

    // TODO(avayvod): switch to using ConnectedTask class for GoogleApiClient operations.
    // See https://crbug.com/522478
    @Override
    public void onConnectionFailed(ConnectionResult result) {
        if (mState != STATE_CONNECTING_TO_API) throwInvalidState();

        reportError(String.format(
                ERROR_NEW_ROUTE_CLIENT_CONNECTION_FAILED,
                result.getErrorCode(),
                result.hasResolution()));
    }

    private GoogleApiClient createApiClient(
            MediaRouter.RouteInfo route, Cast.Listener listener, Context context) {
        CastDevice selectedDevice = CastDevice.getFromBundle(route.getExtras());

        Cast.CastOptions.Builder apiOptionsBuilder = Cast.CastOptions
                .builder(selectedDevice, listener)
                // TODO(avayvod): hide this behind the flag or remove
                .setVerboseLoggingEnabled(true);

        return new GoogleApiClient.Builder(context)
                .addApi(Cast.API, apiOptionsBuilder.build())
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
    }

    private PendingResult<Cast.ApplicationConnectionResult> launchApplication(
            GoogleApiClient apiClient,
            String appId,
            boolean relaunchIfRunning) {
        return Cast.CastApi.launchApplication(apiClient, appId, relaunchIfRunning);
    }

    private void throwInvalidState() {
        throw new RuntimeException(String.format("Invalid state: %d", mState));
    }

    private void reportSuccess(String sessionId, boolean wasLaunched) {
        if (mState != STATE_LAUNCH_SUCCEEDED) throwInvalidState();

        String mediaRouteId = ChromeMediaRouter.createMediaRouteId(
                mPresentationId, mSinkId, mSourceUrn);
        mMediaRouter.onRouteCreated(
                mediaRouteId,
                mRequestId,
                new SessionWrapper(mApiClient, sessionId, mediaRouteId, mMediaRouter),
                wasLaunched);

        terminate();
    }

    private void reportError(String message) {
        if (mState == STATE_TERMINATED) throwInvalidState();

        assert mMediaRouter != null;
        mMediaRouter.onRouteCreationError(message, mRequestId);

        terminate();
    }

    private void terminate() {
        mApiClient.unregisterConnectionCallbacks(this);
        mApiClient.unregisterConnectionFailedListener(this);
        mState = STATE_TERMINATED;
    }
}
// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.router;

import android.content.Context;
import android.support.v7.media.MediaRouter;

import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.CastStatusCodes;

import org.chromium.base.Log;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.chrome.browser.media.router.cast.CreateRouteRequest;
import org.chromium.chrome.browser.media.router.cast.DiscoveryCallback;
import org.chromium.chrome.browser.media.router.cast.MediaSink;
import org.chromium.chrome.browser.media.router.cast.MediaSource;
import org.chromium.chrome.browser.media.router.cast.SessionWrapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * Implements the JNI interface called from the C++ Media Router implementation on Android.
 */
@JNINamespace("media_router")
public class ChromeMediaRouter {
    private static final String TAG = "cr.MediaRouter";

    private final long mNativeMediaRouterAndroid;
    private final MediaRouter mAndroidMediaRouter;
    private final Context mApplicationContext;
    private final Map<String, List<MediaSink>> mSinks = new HashMap<String, List<MediaSink>>();
    private final Map<String, DiscoveryCallback> mDiscoveryCallbacks =
            new HashMap<String, DiscoveryCallback>();
    private final Map<String, SessionWrapper> mSessions =
            new HashMap<String, SessionWrapper>();

    /**
     * @param presentationId the presentation id associated with the route
     * @param mSinkId the id of the {@link MediaSink} associated with the route
     * @param mSourceUrn the presentation URL associated with the route
     * @return the media route id corresponding to the given parameters.
     */
    public static String createMediaRouteId(
            String presentationId, String sinkId, String sourceUrn) {
        return String.format("route:%s/%s/%s", presentationId, sinkId, sourceUrn);
    }

    /**
     * Called when the sinks found by the media route provider for
     * the particular |sourceUrn| have changed.
     * @param sourceUrn The URN of the source (presentation URL) that the sinks are received for.
     * @param sinks The list of {@link MediaSink}s
     */
    public void onSinksReceived(String sourceUrn, List<MediaSink> sinks) {
        mSinks.put(sourceUrn, sinks);
        nativeOnSinksReceived(mNativeMediaRouterAndroid, sourceUrn, sinks.size());
    }

    /**
     * Called when the route was created successfully.
     * @param mediaRouteId the id of the created route.
     * @param requestId the id of the route creation request.
     */
    public void onRouteCreated(
            String mediaRouteId, int requestId, SessionWrapper session, boolean wasLaunched) {
        mSessions.put(mediaRouteId, session);
        nativeOnRouteCreated(mNativeMediaRouterAndroid, mediaRouteId, requestId, wasLaunched);
    }

    /**
     * Called when the route was failed to create.
     * @param errorText the error message to return to the page.
     * @param requestId the id of the route creation request.
     */
    public void onRouteCreationError(String errorText, int requestId) {
        nativeOnRouteCreationError(mNativeMediaRouterAndroid, errorText, requestId);
    }

    /**
     * Updates the native part about the result of sending the message to the route.
     * @param success Indicates if the message was sent successfully.
     * @param callbackId The identifier of the callback to pass the result to.
     */
    public void onMessageSentResult(boolean success, int callbackId) {
        nativeOnMessageSentResult(mNativeMediaRouterAndroid, success, callbackId);
    }

    /**
     * Called when a specified media route receives a message.
     * @param mediaRouteId The identifier of the media route.
     * @param message The message contents.
     */
    public void onMessage(String mediaRouteId, String message) {
        nativeOnMessage(mNativeMediaRouterAndroid, mediaRouteId, message);
    }

    /**
     * Initializes the media router and its providers.
     * @param nativeMediaRouterAndroid the handler for the native counterpart of this instance
     * @param applicationContext the application context to use to obtain system APIs
     * @return an initialized {@link ChromeMediaRouter} instance
     */
    @CalledByNative
    public static ChromeMediaRouter create(long nativeMediaRouterAndroid,
            Context applicationContext) {
        return new ChromeMediaRouter(nativeMediaRouterAndroid, applicationContext);
    }

    /**
     * Starts background monitoring for available media sinks compatible with the given
     * |sourceUrn|
     * @param sourceUrn a URL to use for filtering of the available media sinks
     */
    @CalledByNative
    public void startObservingMediaSinks(String sourceUrn) {
        if (mAndroidMediaRouter == null) return;

        MediaSource source = MediaSource.from(sourceUrn);
        if (source == null) return;

        String applicationId = source.getApplicationId();
        if (mDiscoveryCallbacks.containsKey(applicationId)) {
            mDiscoveryCallbacks.get(applicationId).addSourceUrn(sourceUrn);
            return;
        }

        DiscoveryCallback callback = new DiscoveryCallback(sourceUrn, this);
        mAndroidMediaRouter.addCallback(
                source.buildRouteSelector(),
                callback,
                MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY);
        mDiscoveryCallbacks.put(applicationId, callback);
    }

    /**
     * Stops background monitoring for available media sinks compatible with the given
     * |sourceUrn|
     * @param sourceUrn a URL passed to {@link #startObservingMediaSinks(String)} before.
     */
    @CalledByNative
    public void stopObservingMediaSinks(String sourceUrn) {
        if (mAndroidMediaRouter == null) return;

        MediaSource source = MediaSource.from(sourceUrn);
        if (source == null) return;

        String applicationId = source.getApplicationId();
        if (!mDiscoveryCallbacks.containsKey(applicationId)) return;

        DiscoveryCallback callback = mDiscoveryCallbacks.get(applicationId);
        callback.removeSourceUrn(sourceUrn);

        if (callback.isEmpty()) {
            mAndroidMediaRouter.removeCallback(callback);
            mDiscoveryCallbacks.remove(applicationId);
        }

        mSinks.remove(sourceUrn);
    }

    /**
     * Returns the URN of the media sink corresponding to the given source URN
     * and an index. Essentially a way to access the corresponding {@link MediaSink}'s
     * list via JNI.
     * @param sourceUrn The URN to get the sink for.
     * @param index The index of the sink in the current sink array.
     * @return the corresponding sink URN if found or null.
     */
    @CalledByNative
    public String getSinkUrn(String sourceUrn, int index) {
        return getSink(sourceUrn, index).getUrn();
    }

    /**
     * Returns the name of the media sink corresponding to the given source URN
     * and an index. Essentially a way to access the corresponding {@link MediaSink}'s
     * list via JNI.
     * @param sourceUrn The URN to get the sink for.
     * @param index The index of the sink in the current sink array.
     * @return the corresponding sink name if found or null.
     */
    @CalledByNative
    public String getSinkName(String sourceUrn, int index) {
        return getSink(sourceUrn, index).getName();
    }

    /**
     * Initiates route creation with the given parameters. Notifies the native client of success
     * and failure.
     * @param sourceId the id of the {@link MediaSource} to route to the sink.
     * @param sinkId the id of the {@link MediaSink} to route the source to.
     * @param presentationId the id of the presentation to be used by the page.
     * @param requestId the id of the route creation request tracked by the native side.
     */
    @CalledByNative
    public void createRoute(
            final String sourceId,
            final String sinkId,
            final String presentationId,
            int requestId) {
        if (mAndroidMediaRouter == null) {
            nativeOnRouteCreationError(mNativeMediaRouterAndroid, "Not supported", requestId);
            return;
        }

        new CreateRouteRequest(sourceId, sinkId, presentationId, requestId, this).start(
                mAndroidMediaRouter,
                mApplicationContext,
                // TODO(avayvod): handle application disconnect and report back to the native side.
                // Part of https://crbug.com/517100.
                new Cast.Listener() {
                    @Override
                    public void onApplicationDisconnected(int errorCode) {
                        if (errorCode != CastStatusCodes.SUCCESS) {
                            Log.e(TAG, String.format(
                                    "Application disconnected with: %d", errorCode));
                        }
                        closeRoute(createMediaRouteId(presentationId, sinkId, sourceId));
                    }
                });
    }

    /**
     * Closes the route specified by the id.
     * @param routeId the id of the route to close.
     */
    @CalledByNative
    public void closeRoute(String routeId) {
        SessionWrapper session = mSessions.remove(routeId);
        if (session == null) return;

        session.stop();
        if (mAndroidMediaRouter != null) {
            mAndroidMediaRouter.selectRoute(mAndroidMediaRouter.getDefaultRoute());
        }
        nativeOnRouteClosed(mNativeMediaRouterAndroid, routeId);
    }

    @CalledByNative
    void sendStringMessage(String routeId, String message, int callbackId) {
        SessionWrapper session = mSessions.get(routeId);
        if (session == null) {
            nativeOnMessageSentResult(mNativeMediaRouterAndroid, false, callbackId);
            return;
        }

        session.sendStringMessage(message, callbackId);
    }

    @VisibleForTesting
    ChromeMediaRouter(long nativeMediaRouter, Context applicationContext) {
        assert applicationContext != null;

        mNativeMediaRouterAndroid = nativeMediaRouter;
        mAndroidMediaRouter = getAndroidMediaRouter(applicationContext);
        mApplicationContext = applicationContext;
    }

    @Nullable
    private MediaRouter getAndroidMediaRouter(Context applicationContext) {
        try {
            // Pre-MR1 versions of JB do not have the complete MediaRouter APIs,
            // so getting the MediaRouter instance will throw an exception.
            return MediaRouter.getInstance(applicationContext);
        } catch (NoSuchMethodError e) {
            return null;
        }
    }

    private MediaSink getSink(String sourceUrn, int index) {
        assert mSinks.containsKey(sourceUrn);
        return mSinks.get(sourceUrn).get(index);
    }

    native void nativeOnSinksReceived(
            long nativeMediaRouterAndroid, String sourceUrn, int count);
    native void nativeOnRouteCreated(
            long nativeMediaRouterAndroid,
            String mediaRouteId,
            int createRouteRequestId,
            boolean wasLaunched);
    native void nativeOnRouteCreationError(
            long nativeMediaRouterAndroid, String errorText, int createRouteRequestId);
    native void nativeOnRouteClosed(long nativeMediaRouterAndroid, String mediaRouteId);
    native void nativeOnMessageSentResult(
            long nativeMediaRouterAndroid, boolean success, int callbackId);
    native void nativeOnMessage(long nativeMediaRouterAndroid, String mediaRouteId, String message);
}
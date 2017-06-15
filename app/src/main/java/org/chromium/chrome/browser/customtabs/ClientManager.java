// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.customtabs;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.customtabs.CustomTabsCallback;
import android.support.customtabs.CustomTabsService;
import android.support.customtabs.CustomTabsSessionToken;
import android.text.TextUtils;
import android.util.SparseBooleanArray;

import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.browser.IntentHandler;
import org.chromium.chrome.browser.util.UrlUtilities;
import org.chromium.content_public.browser.WebContents;
import org.chromium.content_public.common.Referrer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Manages the clients' state for Custom Tabs. This class is threadsafe. */
@SuppressFBWarnings("CHROMIUM_SYNCHRONIZED_METHOD")
class ClientManager {
    // Values for the "CustomTabs.MayLaunchUrlType" UMA histogram. Append-only.
    @VisibleForTesting static final int NO_MAY_LAUNCH_URL = 0;
    @VisibleForTesting static final int LOW_CONFIDENCE = 1;
    @VisibleForTesting static final int HIGH_CONFIDENCE = 2;
    @VisibleForTesting static final int BOTH = 3;  // LOW + HIGH.
    private static final int MAY_LAUNCH_URL_TYPE_COUNT = 4;

    // Values for the "CustomTabs.PredictionStatus" UMA histogram. Append-only.
    @VisibleForTesting static final int NO_PREDICTION = 0;
    @VisibleForTesting static final int GOOD_PREDICTION = 1;
    @VisibleForTesting static final int BAD_PREDICTION = 2;
    private static final int PREDICTION_STATUS_COUNT = 3;
    // Values for the "CustomTabs.CalledWarmup" UMA histogram. Append-only.
    @VisibleForTesting static final int NO_SESSION_NO_WARMUP = 0;
    @VisibleForTesting static final int NO_SESSION_WARMUP = 1;
    @VisibleForTesting static final int SESSION_NO_WARMUP_ALREADY_CALLED = 2;
    @VisibleForTesting static final int SESSION_NO_WARMUP_NOT_CALLED = 3;
    @VisibleForTesting static final int SESSION_WARMUP = 4;
    @VisibleForTesting static final int SESSION_WARMUP_COUNT = 5;

    /** To be called when a client gets disconnected. */
    public interface DisconnectCallback { public void run(CustomTabsSessionToken session); }

    private static class KeepAliveServiceConnection implements ServiceConnection {
        private final Context mContext;
        private final Intent mServiceIntent;
        private boolean mHasDied;
        private boolean mIsBound;

        public KeepAliveServiceConnection(Context context, Intent serviceIntent) {
            mContext = context;
            mServiceIntent = serviceIntent;
        }

        /**
         * Connects to the service identified by |serviceIntent|. Does not reconnect if the service
         * got disconnected at some point from the other end (remote process death).
         */
        public boolean connect() {
            if (mIsBound) return true;
            // If the remote process died at some point, it doesn't make sense to resurrect it.
            if (mHasDied) return false;

            boolean ok;
            try {
                ok = mContext.bindService(mServiceIntent, this, Context.BIND_AUTO_CREATE);
            } catch (SecurityException e) {
                return false;
            }
            mIsBound = ok;
            return ok;
        }

        /**
         * Disconnects from the remote process. Safe to call even if {@link connect()} returned
         * false, or if the remote service died.
         */
        public void disconnect() {
            if (mIsBound) {
                mContext.unbindService(this);
                mIsBound = false;
            }
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {}

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (mIsBound) {
                // The remote process has died. This typically happens if the system is low enough
                // on memory to kill one of the last process on the "kill list". In this case, we
                // shouldn't resurrect the process (which happens with BIND_AUTO_CREATE) because
                // that could create a "restart/kill" loop.
                mHasDied = true;
                disconnect();
            }
        }
    }

    /** Per-session values. */
    private static class SessionParams {
        public final int uid;
        public final DisconnectCallback disconnectCallback;
        public final String packageName;
        public final PostMessageHandler postMessageHandler;
        public boolean mIgnoreFragments;
        public boolean lowConfidencePrediction;
        public boolean highConfidencePrediction;
        private boolean mShouldHideDomain;
        private boolean mShouldPrerenderOnCellular;
        private boolean mShouldSendNavigationInfo;
        private KeepAliveServiceConnection mKeepAliveConnection;
        private String mPredictedUrl;
        private long mLastMayLaunchUrlTimestamp;
        private int mSpeculationMode;

        public SessionParams(Context context, int uid, DisconnectCallback callback,
                PostMessageHandler postMessageHandler) {
            this.uid = uid;
            packageName = getPackageName(context, uid);
            disconnectCallback = callback;
            this.postMessageHandler = postMessageHandler;
            if (postMessageHandler != null) this.postMessageHandler.setPackageName(packageName);
            this.mSpeculationMode = CustomTabsConnection.SpeculationParams.PRERENDER;
        }

        private static String getPackageName(Context context, int uid) {
            PackageManager packageManager = context.getPackageManager();
            String[] packageList = packageManager.getPackagesForUid(uid);
            if (packageList.length != 1 || TextUtils.isEmpty(packageList[0])) return null;
            return packageList[0];
        }

        public KeepAliveServiceConnection getKeepAliveConnection() {
            return mKeepAliveConnection;
        }

        public void setKeepAliveConnection(KeepAliveServiceConnection serviceConnection) {
            mKeepAliveConnection = serviceConnection;
        }

        public void setPredictionMetrics(
                String predictedUrl, long lastMayLaunchUrlTimestamp, boolean lowConfidence) {
            mPredictedUrl = predictedUrl;
            mLastMayLaunchUrlTimestamp = lastMayLaunchUrlTimestamp;
            highConfidencePrediction |= !TextUtils.isEmpty(predictedUrl);
            lowConfidencePrediction |= lowConfidence;
        }

        /**
         * Resets the prediction metrics. This clears the predicted URL, last prediction time,
         * and whether a low and/or high confidence prediction has been done.
         */
        public void resetPredictionMetrics() {
            mPredictedUrl = null;
            mLastMayLaunchUrlTimestamp = 0;
            highConfidencePrediction = false;
            lowConfidencePrediction = false;
        }

        public String getPredictedUrl() {
            return mPredictedUrl;
        }

        public long getLastMayLaunchUrlTimestamp() {
            return mLastMayLaunchUrlTimestamp;
        }

        /**
         * @return Whether the default parameters are used for this session.
         */
        public boolean isDefault() {
            return !mIgnoreFragments && !mShouldPrerenderOnCellular;
        }
    }

    private final Context mContext;
    private final Map<CustomTabsSessionToken, SessionParams> mSessionParams = new HashMap<>();
    private final SparseBooleanArray mUidHasCalledWarmup = new SparseBooleanArray();
    private boolean mWarmupHasBeenCalled;

    public ClientManager(Context context) {
        mContext = context.getApplicationContext();
        RequestThrottler.loadInBackground(mContext);
    }

    /** Creates a new session.
     *
     * @param session Session provided by the client.
     * @param uid Client UID, as returned by Binder.getCallingUid(),
     * @param onDisconnect To be called on the UI thread when a client gets disconnected.
     * @param postMessageHandler The handler to be used for postMessage related operations.
     * @return true for success.
     */
    public boolean newSession(CustomTabsSessionToken session, int uid,
            DisconnectCallback onDisconnect, @NonNull PostMessageHandler postMessageHandler) {
        if (session == null) return false;
        SessionParams params = new SessionParams(mContext, uid, onDisconnect, postMessageHandler);
        synchronized (this) {
            if (mSessionParams.containsKey(session)) return false;
            mSessionParams.put(session, params);
        }
        return true;
    }

    public synchronized int postMessage(CustomTabsSessionToken session, String message) {
        SessionParams params = mSessionParams.get(session);
        if (params == null) return CustomTabsService.RESULT_FAILURE_MESSAGING_ERROR;
        return params.postMessageHandler.postMessageFromClientApp(message);
    }

    /**
     * Records that {@link CustomTabsConnection#warmup(long)} has been called from the given uid.
     */
    public synchronized void recordUidHasCalledWarmup(int uid) {
        mWarmupHasBeenCalled = true;
        mUidHasCalledWarmup.put(uid, true);
    }

    /** Updates the client behavior stats and returns whether speculation is allowed.
     *
     * The first call to the "low priority" mode is not throttled. Subsequent ones are.
     *
     * @param session Client session.
     * @param uid As returned by Binder.getCallingUid().
     * @param url Predicted URL.
     * @param lowConfidence whether the request contains some "low confidence" URLs.
     * @return true if speculation is allowed.
     */
    public synchronized boolean updateStatsAndReturnWhetherAllowed(
            CustomTabsSessionToken session, int uid, String url, boolean lowConfidence) {
        SessionParams params = mSessionParams.get(session);
        if (params == null || params.uid != uid) return false;
        boolean firstLowConfidencePrediction =
                TextUtils.isEmpty(url) && lowConfidence && !params.lowConfidencePrediction;
        params.setPredictionMetrics(url, SystemClock.elapsedRealtime(), lowConfidence);
        if (firstLowConfidencePrediction) return true;
        RequestThrottler throttler = RequestThrottler.getForUid(mContext, uid);
        return throttler.updateStatsAndReturnWhetherAllowed();
    }

    @VisibleForTesting
    synchronized int getWarmupState(CustomTabsSessionToken session) {
        SessionParams params = mSessionParams.get(session);
        boolean hasValidSession = params != null;
        boolean hasUidCalledWarmup = hasValidSession && mUidHasCalledWarmup.get(params.uid);
        int result = mWarmupHasBeenCalled ? NO_SESSION_WARMUP : NO_SESSION_NO_WARMUP;
        if (hasValidSession) {
            if (hasUidCalledWarmup) {
                result = SESSION_WARMUP;
            } else {
                result = mWarmupHasBeenCalled ? SESSION_NO_WARMUP_ALREADY_CALLED
                                              : SESSION_NO_WARMUP_NOT_CALLED;
            }
        }
        return result;
    }

    /**
     * @return the prediction outcome. NO_PREDICTION if mSessionParams.get(session) returns null.
     */
    @VisibleForTesting
    synchronized int getPredictionOutcome(CustomTabsSessionToken session, String url) {
        SessionParams params = mSessionParams.get(session);
        if (params == null) return NO_PREDICTION;

        String predictedUrl = params.getPredictedUrl();
        if (predictedUrl == null) return NO_PREDICTION;

        boolean urlsMatch = TextUtils.equals(predictedUrl, url)
                || (params.mIgnoreFragments
                        && UrlUtilities.urlsMatchIgnoringFragments(predictedUrl, url));
        return urlsMatch ? GOOD_PREDICTION : BAD_PREDICTION;
    }

    /**
     * Registers that a client has launched a URL inside a Custom Tab.
     */
    public synchronized void registerLaunch(CustomTabsSessionToken session, String url) {
        int outcome = getPredictionOutcome(session, url);
        RecordHistogram.recordEnumeratedHistogram(
                "CustomTabs.PredictionStatus", outcome, PREDICTION_STATUS_COUNT);

        SessionParams params = mSessionParams.get(session);
        if (outcome == GOOD_PREDICTION) {
            long elapsedTimeMs = SystemClock.elapsedRealtime()
                    - params.getLastMayLaunchUrlTimestamp();
            RequestThrottler.getForUid(mContext, params.uid).registerSuccess(
                    params.mPredictedUrl);
            RecordHistogram.recordCustomTimesHistogram("CustomTabs.PredictionToLaunch",
                    elapsedTimeMs, 1, TimeUnit.MINUTES.toMillis(3), TimeUnit.MILLISECONDS, 100);
        }
        RecordHistogram.recordEnumeratedHistogram(
                "CustomTabs.WarmupStateOnLaunch", getWarmupState(session), SESSION_WARMUP_COUNT);

        if (params == null) return;

        int value = (params.lowConfidencePrediction ? LOW_CONFIDENCE : 0)
                + (params.highConfidencePrediction ? HIGH_CONFIDENCE : 0);
        RecordHistogram.recordEnumeratedHistogram(
                "CustomTabs.MayLaunchUrlType", value, MAY_LAUNCH_URL_TYPE_COUNT);
        params.resetPredictionMetrics();
    }

    /**
     * See {@link PostMessageHandler#bindSessionToPostMessageService(Context, String)}.
     */
    public synchronized boolean bindToPostMessageServiceForSession(CustomTabsSessionToken session) {
        SessionParams params = mSessionParams.get(session);
        if (params == null) return false;
        return params.postMessageHandler.bindSessionToPostMessageService(
                mContext, params.packageName);
    }

    /**
     * See {@link PostMessageHandler#initializeWithOrigin(Uri)}.
     */
    public synchronized void initializeWithPostMessageOriginForSession(
            CustomTabsSessionToken session, Uri origin) {
        SessionParams params = mSessionParams.get(session);
        if (params == null) return;
        params.postMessageHandler.initializeWithOrigin(origin);
    }

    /**
     * See {@link PostMessageHandler#verifyAndInitializeWithOrigin(Uri)}.
     */
    public synchronized void verifyAndInitializeWithPostMessageOriginForSession(
            CustomTabsSessionToken session, Uri origin) {
        SessionParams params = mSessionParams.get(session);
        if (params == null) return;
        params.postMessageHandler.verifyAndInitializeWithOrigin(origin);
    }

    /**
     * @return The postMessage origin for the given session.
     */
    @VisibleForTesting
    synchronized Uri getPostMessageOriginForSessionForTesting(CustomTabsSessionToken session) {
        SessionParams params = mSessionParams.get(session);
        if (params == null) return null;
        return params.postMessageHandler.getOriginForTesting();
    }

    /**
     * See {@link PostMessageHandler#reset(WebContents)}.
     */
    public synchronized void resetPostMessageHandlerForSession(
            CustomTabsSessionToken session, WebContents webContents) {
        SessionParams params = mSessionParams.get(session);
        if (params == null) return;
        params.postMessageHandler.reset(webContents);
    }

    /**
     * @return The referrer that is associated with the client owning given session.
     */
    public synchronized Referrer getReferrerForSession(CustomTabsSessionToken session) {
        SessionParams params = mSessionParams.get(session);
        if (params == null) return null;
        final String packageName = params.packageName;
        return IntentHandler.constructValidReferrerForAuthority(packageName);
    }

    /**
     * @return The package name associated with the client owning the given session.
     */
    public synchronized String getClientPackageNameForSession(CustomTabsSessionToken session) {
        SessionParams params = mSessionParams.get(session);
        return params == null ? null : params.packageName;
    }

    /**
     * @return The callback {@link CustomTabsSessionToken} for the given session.
     */
    public synchronized CustomTabsCallback getCallbackForSession(CustomTabsSessionToken session) {
        return session != null ? session.getCallback() : null;
    }

    /**
     * @return Whether the urlbar should be hidden for the session on first page load. Urls are
     *         foced to show up after the user navigates away.
     */
    public synchronized boolean shouldHideDomainForSession(CustomTabsSessionToken session) {
        SessionParams params = mSessionParams.get(session);
        return params != null ? params.mShouldHideDomain : false;
    }

    /**
     * Sets whether the urlbar should be hidden for a given session.
     */
    public synchronized void setHideDomainForSession(CustomTabsSessionToken session, boolean hide) {
        SessionParams params = mSessionParams.get(session);
        if (params != null) params.mShouldHideDomain = hide;
    }

    /**
     * @return Whether navigation info should be recorded and shared for the session.
     */
    public synchronized boolean shouldSendNavigationInfoForSession(CustomTabsSessionToken session) {
        SessionParams params = mSessionParams.get(session);
        return params != null ? params.mShouldSendNavigationInfo : false;
    }

    /**
     * Sets whether navigation info should be recorded and shared for the current navigation in this
     * session.
     */
    public synchronized void setSendNavigationInfoForSession(
            CustomTabsSessionToken session, boolean send) {
        SessionParams params = mSessionParams.get(session);
        if (params != null) params.mShouldSendNavigationInfo = send;
    }

    /**
     * @return Whether the fragment should be ignored for prerender matching.
     */
    public synchronized boolean getIgnoreFragmentsForSession(CustomTabsSessionToken session) {
        SessionParams params = mSessionParams.get(session);
        return params == null ? false : params.mIgnoreFragments;
    }

    /** Sets whether the fragment should be ignored for prerender matching. */
    public synchronized void setIgnoreFragmentsForSession(
            CustomTabsSessionToken session, boolean value) {
        SessionParams params = mSessionParams.get(session);
        if (params != null) params.mIgnoreFragments = value;
    }

    /**
     * @return Whether prerender should be turned on for cellular networks for given session.
     */
    public synchronized boolean shouldPrerenderOnCellularForSession(
            CustomTabsSessionToken session) {
        SessionParams params = mSessionParams.get(session);
        return params != null ? params.mShouldPrerenderOnCellular : false;
    }

    /**
     * @return Whether the session is using the default parameters (that is,
     *         don't ignore fragments and don't prerender on cellular connections).
     */
    public synchronized boolean usesDefaultSessionParameters(CustomTabsSessionToken session) {
        SessionParams params = mSessionParams.get(session);
        return params != null ? params.isDefault() : true;
    }

    /**
     * Sets whether prerender should be turned on for mobile networks for given session.
     */
    public synchronized void setPrerenderCellularForSession(
            CustomTabsSessionToken session, boolean prerender) {
        SessionParams params = mSessionParams.get(session);
        if (params != null) params.mShouldPrerenderOnCellular = prerender;
    }

    /**
     * Sets the speculation mode to be used by default for given session.
     */
    public synchronized void setSpeculationModeForSession(
            CustomTabsSessionToken session, int speculationMode) {
        SessionParams params = mSessionParams.get(session);
        if (params != null) params.mSpeculationMode = speculationMode;
    }

    /**
     * Get the speculation mode to be used by default for the given session.
     * If no value has been set will default to PRERENDER mode.
     */
    public synchronized int getSpeculationModeForSession(CustomTabsSessionToken session) {
        SessionParams params = mSessionParams.get(session);
        return params == null ? CustomTabsConnection.SpeculationParams.PRERENDER
                              : params.mSpeculationMode;
    }

    /** Tries to bind to a client to keep it alive, and returns true for success. */
    public synchronized boolean keepAliveForSession(CustomTabsSessionToken session, Intent intent) {
        // When an application is bound to a service, its priority is raised to
        // be at least equal to the application's one. This binds to a dummy
        // service (no calls to this service are made).
        if (intent == null || intent.getComponent() == null) return false;
        SessionParams params = mSessionParams.get(session);
        if (params == null) return false;

        KeepAliveServiceConnection connection = params.getKeepAliveConnection();

        if (connection == null) {
            String packageName = intent.getComponent().getPackageName();
            PackageManager pm = mContext.getApplicationContext().getPackageManager();
            // Only binds to the application associated to this session.
            if (!Arrays.asList(pm.getPackagesForUid(params.uid)).contains(packageName)) {
                return false;
            }
            Intent serviceIntent = new Intent().setComponent(intent.getComponent());
            connection = new KeepAliveServiceConnection(mContext, serviceIntent);
        }

        boolean ok = connection.connect();
        if (ok) params.setKeepAliveConnection(connection);
        return ok;
    }

    /** Unbind from the KeepAlive service for a client. */
    public synchronized void dontKeepAliveForSession(CustomTabsSessionToken session) {
        SessionParams params = mSessionParams.get(session);
        if (params == null || params.getKeepAliveConnection() == null) return;
        KeepAliveServiceConnection connection = params.getKeepAliveConnection();
        connection.disconnect();
    }

    /** See {@link RequestThrottler#isPrerenderingAllowed()} */
    public synchronized boolean isPrerenderingAllowed(int uid) {
        return RequestThrottler.getForUid(mContext, uid).isPrerenderingAllowed();
    }

    /** See {@link RequestThrottler#registerPrerenderRequest(String)} */
    public synchronized void registerPrerenderRequest(int uid, String url) {
        RequestThrottler.getForUid(mContext, uid).registerPrerenderRequest(url);
    }

    /** See {@link RequestThrottler#reset()} */
    public synchronized void resetThrottling(int uid) {
        RequestThrottler.getForUid(mContext, uid).reset();
    }

    /** See {@link RequestThrottler#ban()} */
    public synchronized void ban(int uid) {
        RequestThrottler.getForUid(mContext, uid).ban();
    }

    /**
     * Cleans up all data associated with all sessions.
     */
    public synchronized void cleanupAll() {
        List<CustomTabsSessionToken> sessions = new ArrayList<>(mSessionParams.keySet());
        for (CustomTabsSessionToken session : sessions) cleanupSession(session);
    }

    /**
     * Handle any clean up left after a session is destroyed.
     * @param session The session that has been destroyed.
     */
    public synchronized void cleanupSession(CustomTabsSessionToken session) {
        SessionParams params = mSessionParams.get(session);
        if (params == null) return;
        mSessionParams.remove(session);
        if (params.postMessageHandler != null) {
            params.postMessageHandler.cleanup(mContext);
        }
        if (params.disconnectCallback != null) params.disconnectCallback.run(session);
        mUidHasCalledWarmup.delete(params.uid);
    }
}

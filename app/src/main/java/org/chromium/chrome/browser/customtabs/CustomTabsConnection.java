// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.customtabs;

import android.app.ActivityManager;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Point;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.support.customtabs.ICustomTabsCallback;
import android.support.customtabs.ICustomTabsService;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.WindowManager;

import org.chromium.base.FieldTrialList;
import org.chromium.base.Log;
import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.base.library_loader.ProcessInitException;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeApplication;
import org.chromium.chrome.browser.IntentHandler;
import org.chromium.chrome.browser.WarmupManager;
import org.chromium.chrome.browser.WebContentsFactory;
import org.chromium.chrome.browser.device.DeviceClassManager;
import org.chromium.chrome.browser.init.ChromeBrowserInitializer;
import org.chromium.chrome.browser.prerender.ExternalPrerenderHandler;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.content.browser.ChildProcessLauncher;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.content_public.browser.WebContents;
import org.chromium.content_public.common.Referrer;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Implementation of the ICustomTabsConnectionService interface.
 *
 * Note: This class is meant to be package private, and is public to be
 * accessible from {@link ChromeApplication}.
 */
public class CustomTabsConnection extends ICustomTabsService.Stub {
    private static final String TAG = "cr.ChromeConnection";
    @VisibleForTesting
    static final String NO_PRERENDERING_KEY =
            "android.support.customtabs.maylaunchurl.NO_PRERENDERING";

    // Values for the "CustomTabs.PredictionStatus" UMA histogram. Append-only.
    private static final int NO_PREDICTION = 0;
    private static final int GOOD_PREDICTION = 1;
    private static final int BAD_PREDICTION = 2;
    private static final int PREDICTION_STATUS_COUNT = 3;

    private static AtomicReference<CustomTabsConnection> sInstance =
            new AtomicReference<CustomTabsConnection>();

    private static final class PrerenderedUrlParams {
        public final IBinder mSession;
        public final WebContents mWebContents;
        public final String mUrl;
        public final String mReferrer;
        public final Bundle mExtras;

        PrerenderedUrlParams(IBinder session, WebContents webContents, String url, String referrer,
                Bundle extras) {
            mSession = session;
            mWebContents = webContents;
            mUrl = url;
            mReferrer = referrer;
            mExtras = extras;
        }
    }

    private static final class PredictionStats {
        private static final long MIN_DELAY = 100;
        private static final long MAX_DELAY = 10000;
        private long mLastRequestTimestamp = -1;
        private long mDelayMs = MIN_DELAY;

        /**
         * Updates the prediction stats and return whether prediction is allowed.
         *
         * The policy is:
         * 1. If the client does not wait more than mDelayMs, decline the request.
         * 2. If the client waits for more than mDelayMs but less than 2*mDelayMs,
         *    accept the request and double mDelayMs.
         * 3. If the client waits for more than 2*mDelayMs, accept the request
         *    and reset mDelayMs.
         *
         * And: 100ms <= mDelayMs <= 10s.
         *
         * This way, if an application sends a burst of requests, it is quickly
         * seriously throttled. If it stops being this way, back to normal.
         */
        public boolean updateStatsAndReturnIfAllowed() {
            long now = SystemClock.elapsedRealtime();
            long deltaMs = now - mLastRequestTimestamp;
            if (deltaMs < mDelayMs) return false;
            mLastRequestTimestamp = now;
            if (deltaMs < 2 * mDelayMs) {
                mDelayMs = Math.min(MAX_DELAY, mDelayMs * 2);
            } else {
                mDelayMs = MIN_DELAY;
            }
            return true;
        }
    }

    protected final Application mApplication;
    private final AtomicBoolean mWarmupHasBeenCalled = new AtomicBoolean();
    private ExternalPrerenderHandler mExternalPrerenderHandler;
    private PrerenderedUrlParams mPrerender;
    private WebContents mSpareWebContents;

    /** Per-session values. */
    private static class SessionParams {
        public final int mUid;
        public final Referrer mReferrer;
        public final ICustomTabsCallback mCallback;
        public final IBinder.DeathRecipient mDeathRecipient;
        private ServiceConnection mServiceConnection;
        private String mPredictedUrl;
        private long mLastMayLaunchUrlTimestamp;

        public SessionParams(Context context, int uid, ICustomTabsCallback callback,
                IBinder.DeathRecipient deathRecipient) {
            mUid = uid;
            mCallback = callback;
            mDeathRecipient = deathRecipient;
            mServiceConnection = null;
            mPredictedUrl = null;
            mLastMayLaunchUrlTimestamp = 0;
            mReferrer = constructReferrer(context);
        }

        private Referrer constructReferrer(Context context) {
            PackageManager packageManager = context.getPackageManager();
            String[] packageList = packageManager.getPackagesForUid(mUid);
            if (packageList.length != 1 || TextUtils.isEmpty(packageList[0])) return null;
            return IntentHandler.constructValidReferrerForAuthority(packageList[0]);
        }

        public ServiceConnection getServiceConnection() {
            return mServiceConnection;
        }

        public void setServiceConnection(ServiceConnection serviceConnection) {
            mServiceConnection = serviceConnection;
        }

        public void setPredictionMetrics(String predictedUrl, long lastMayLaunchUrlTimestamp) {
            mPredictedUrl = predictedUrl;
            mLastMayLaunchUrlTimestamp = lastMayLaunchUrlTimestamp;
        }

        public String getPredictedUrl() {
            return mPredictedUrl;
        }

        public long getLastMayLaunchUrlTimestamp() {
            return mLastMayLaunchUrlTimestamp;
        }
    }

    private final Object mLock = new Object();
    private final Map<IBinder, SessionParams> mSessionParams = new HashMap<>();
    // Prediction tracking is done by UID and not by session, since a
    // mis-behaving application can create a large number of sessions.
    private SparseArray<PredictionStats> mUidToPredictionsStats = new SparseArray<>();

    /**
     * <strong>DO NOT CALL</strong>
     * Public to be instanciable from {@link ChromeApplication}. This is however
     * intended to be private.
     */
    public CustomTabsConnection(Application application) {
        super();
        mApplication = application;
    }

    /**
     * @return The unique instance of ChromeCustomTabsConnection.
     */
    @SuppressFBWarnings("BC_UNCONFIRMED_CAST")
    public static CustomTabsConnection getInstance(Application application) {
        if (sInstance.get() == null) {
            ChromeApplication chromeApplication = (ChromeApplication) application;
            sInstance.compareAndSet(null, chromeApplication.createCustomTabsConnection());
        }
        return sInstance.get();
    }

    @Override
    public boolean newSession(ICustomTabsCallback callback) {
        if (callback == null) return false;
        final int uid = Binder.getCallingUid();
        final IBinder session = callback.asBinder();
        IBinder.DeathRecipient deathRecipient =
                new IBinder.DeathRecipient() {
                    @Override
                    public void binderDied() {
                        ThreadUtils.postOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                synchronized (mLock) {
                                    cleanupAlreadyLocked(session);
                                }
                            }
                        });
                    }
                };
        SessionParams sessionParams =
                new SessionParams(mApplication, uid, callback, deathRecipient);
        synchronized (mLock) {
            if (mSessionParams.containsKey(session)) return false;
            try {
                callback.asBinder().linkToDeath(deathRecipient, 0);
            } catch (RemoteException e) {
                // The return code doesn't matter, because this executes when
                // the caller has died.
                return false;
            }
            mSessionParams.put(session, sessionParams);
            if (mUidToPredictionsStats.get(uid) == null) {
                mUidToPredictionsStats.put(uid, new PredictionStats());
            }
        }
        return true;
    }

    @Override
    public boolean warmup(long flags) {
        // Here and in mayLaunchUrl(), don't do expensive work for background applications.
        if (!isCallerForegroundOrSelf()) return false;
        if (!mWarmupHasBeenCalled.compareAndSet(false, true)) return true;
        // The call is non-blocking and this must execute on the UI thread, post a task.
        ThreadUtils.postOnUiThread(new Runnable() {
            @Override
            @SuppressFBWarnings("DM_EXIT")
            public void run() {
                ChromeApplication app = (ChromeApplication) mApplication;
                try {
                    app.startBrowserProcessesAndLoadLibrariesSync(true);
                } catch (ProcessInitException e) {
                    Log.e(TAG, "ProcessInitException while starting the browser process.");
                    // Cannot do anything without the native library, and cannot show a
                    // dialog to the user.
                    System.exit(-1);
                }
                final Context context = app.getApplicationContext();
                new AsyncTask<Void, Void, Void>() {
                    @Override
                    protected Void doInBackground(Void... params) {
                        ChildProcessLauncher.warmUp(context);
                        return null;
                    }
                }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                ChromeBrowserInitializer.initNetworkChangeNotifier(context);
                WarmupManager.getInstance().initializeViewHierarchy(app.getApplicationContext(),
                        R.style.MainTheme, R.layout.main, R.id.control_container_stub,
                        R.layout.custom_tabs_control_container);
            }
        });
        return true;
    }

    /**
     * Creates a spare {@link WebContents}, if none exists.
     *
     * Navigating to "about:blank" forces a lot of initialization to take place
     * here. This improves PLT. This navigation is never registered in the history, as
     * "about:blank" is filtered by CanAddURLToHistory.
     *
     * TODO(lizeb): Replace this with a cleaner method. See crbug.com/521729.
     */
    private void createSpareWebContents() {
        ThreadUtils.assertOnUiThread();
        if (mSpareWebContents != null) return;
        mSpareWebContents = WebContentsFactory.createWebContents(false, false);
        if (mSpareWebContents != null) {
            mSpareWebContents.getNavigationController().loadUrl(new LoadUrlParams("about:blank"));
        }
    }

    @Override
    public boolean mayLaunchUrl(ICustomTabsCallback callback, Uri url, final Bundle extras,
            List<Bundle> otherLikelyBundles) {
        // Don't do anything for unknown schemes. Not having a scheme is
        // allowed, as we allow "www.example.com".
        String scheme = url.normalizeScheme().getScheme();
        if (scheme != null && !scheme.equals("http") && !scheme.equals("https")) return false;
        if (!isCallerForegroundOrSelf()) return false;

        // Things below need the browser process to be initialized.
        if (!warmup(0)) return false;

        final IBinder session = callback.asBinder();
        final String urlString = url.toString();
        final boolean noPrerendering =
                extras != null ? extras.getBoolean(NO_PRERENDERING_KEY, false) : false;
        int uid = Binder.getCallingUid();
        synchronized (mLock) {
            SessionParams sessionParams = mSessionParams.get(session);
            if (sessionParams == null || sessionParams.mUid != uid) return false;
            sessionParams.setPredictionMetrics(urlString, SystemClock.elapsedRealtime());
            if (!mUidToPredictionsStats.get(uid).updateStatsAndReturnIfAllowed()) return false;
        }
        ThreadUtils.postOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!TextUtils.isEmpty(urlString)) {
                    WarmupManager warmupManager = WarmupManager.getInstance();
                    warmupManager.maybePrefetchDnsForUrlInBackground(
                            mApplication.getApplicationContext(), urlString);
                    warmupManager.maybePreconnectUrlAndSubResources(
                            Profile.getLastUsedProfile(), urlString);
                }
                if (!noPrerendering && mayPrerender()) {
                    // Calling with a null or empty url cancels a current prerender.
                    prerenderUrl(session, urlString, extras);
                } else {
                    createSpareWebContents();
                }
            }
        });
        return true;
    }

    @Override
    public Bundle extraCommand(String commandName, Bundle args) {
        return null;
    }

    /**
     * @return a spare WebContents, or null.
     *
     * This WebContents has already navigated to "about:blank". You have to call
     * {@link LoadUrlParams.setShouldReplaceCurrentEntry(true)} for the next
     * navigation to ensure that a back navigation doesn't lead to about:blank.
     *
     * TODO(lizeb): Update this when crbug.com/521729 is fixed.
     */
    WebContents takeSpareWebContents() {
        ThreadUtils.assertOnUiThread();
        WebContents result = mSpareWebContents;
        mSpareWebContents = null;
        return result;
    }

    /**
     * Registers a launch of a |url| for a given |session|.
     *
     * This is used for accounting.
     */
    void registerLaunch(IBinder session, String url) {
        int outcome;
        long elapsedTimeMs = -1;
        synchronized (mLock) {
            SessionParams sessionParams = mSessionParams.get(session);
            if (sessionParams == null) {
                outcome = NO_PREDICTION;
            } else {
                String predictedUrl = sessionParams.getPredictedUrl();
                outcome = predictedUrl == null ? NO_PREDICTION
                        : predictedUrl.equals(url) ? GOOD_PREDICTION : BAD_PREDICTION;
                elapsedTimeMs = SystemClock.elapsedRealtime()
                        - sessionParams.getLastMayLaunchUrlTimestamp();
                sessionParams.setPredictionMetrics(null, 0);
                if (outcome == GOOD_PREDICTION) {
                    // If the prediction was correct, back to the smallest
                    // throttling level.
                    mUidToPredictionsStats.put(sessionParams.mUid, new PredictionStats());
                }
            }
        }
        RecordHistogram.recordEnumeratedHistogram(
                "CustomTabs.PredictionStatus", outcome, PREDICTION_STATUS_COUNT);
        if (outcome == GOOD_PREDICTION) {
            RecordHistogram.recordCustomTimesHistogram("CustomTabs.PredictionToLaunch",
                    elapsedTimeMs, 1, TimeUnit.MINUTES.toMillis(3), TimeUnit.MILLISECONDS, 100);
        }
    }

    /**
     * Transfers a prerendered WebContents if one exists.
     *
     * This resets the internal WebContents; a subsequent call to this method
     * returns null. Must be called from the UI thread.
     * If a prerender exists for a different URL with the same sessionId or with
     * a different referrer, then this is treated as a mispredict from the
     * client application, and cancels the previous prerender. This is done to
     * avoid keeping resources laying around for too long, but is subject to a
     * race condition, as the following scenario is possible:
     * The application calls:
     * 1. mayLaunchUrl(url1) <- IPC
     * 2. loadUrl(url2) <- Intent
     * 3. mayLaunchUrl(url3) <- IPC
     * If the IPC for url3 arrives before the intent for url2, then this methods
     * cancels the prerender for url3, which is unexpected. On the other
     * hand, not cancelling the previous prerender leads to wasted resources, as
     * a WebContents is lingering. This can be solved by requiring applications
     * to call mayLaunchUrl(null) to cancel a current prerender before 2, that
     * is for a mispredict.
     *
     * @param session The Binder object identifying a session.
     * @param url The URL the WebContents is for.
     * @param referrer The referrer to use for |url|.
     * @return The prerendered WebContents, or null.
     */
    WebContents takePrerenderedUrl(IBinder session, String url, String referrer) {
        ThreadUtils.assertOnUiThread();
        if (mPrerender == null || session == null || !session.equals(mPrerender.mSession)) {
            return null;
        }
        WebContents webContents = mPrerender.mWebContents;
        String prerenderedUrl = mPrerender.mUrl;
        String prerenderReferrer = mPrerender.mReferrer;
        if (referrer == null) referrer = "";
        mPrerender = null;
        if (TextUtils.equals(prerenderedUrl, url)
                && TextUtils.equals(prerenderReferrer, referrer)) {
            return webContents;
        }
        mExternalPrerenderHandler.cancelCurrentPrerender();
        webContents.destroy();
        return null;
    }

    public Referrer getReferrerForSession(IBinder session) {
        if (!mSessionParams.containsKey(session)) return null;
        return mSessionParams.get(session).mReferrer;
    }

    private ICustomTabsCallback getCallbackForSession(IBinder session) {
        synchronized (mLock) {
            SessionParams sessionParams = mSessionParams.get(session);
            if (sessionParams == null) return null;
            return sessionParams.mCallback;
        }
    }

    /**
     * Notifies the application of a navigation event.
     *
     * Delivers the {@link ICustomTabsConnectionCallback#onNavigationEvent}
     * callback to the aplication.
     *
     * @param session The Binder object identifying the session.
     * @param navigationEvent The navigation event code, defined in {@link CustomTabsCallback}
     * @return true for success.
     */
    boolean notifyNavigationEvent(IBinder session, int navigationEvent) {
        ICustomTabsCallback callback = getCallbackForSession(session);
        if (callback == null) return false;
        try {
            callback.onNavigationEvent(navigationEvent, null);
        } catch (Exception e) {
            // Catching all exceptions is really bad, but we need it here,
            // because Android exposes us to client bugs by throwing a variety
            // of exceptions. See crbug.com/517023.
            return false;
        }
        return true;
    }

    /**
     * Keeps the application linked with a given session alive.
     *
     * The application is kept alive (that is, raised to at least the current
     * process priority level) until {@link dontKeepAliveForSessionId()} is
     * called.
     *
     * @param session The Binder object identifying the session.
     * @param intent Intent describing the service to bind to.
     * @return true for success.
     */
    boolean keepAliveForSession(IBinder session, Intent intent) {
        // When an application is bound to a service, its priority is raised to
        // be at least equal to the application's one. This binds to a dummy
        // service (no calls to this service are made).
        if (intent == null || intent.getComponent() == null) return false;
        SessionParams sessionParams;
        synchronized (mLock) {
            sessionParams = mSessionParams.get(session);
            if (sessionParams == null) return false;
        }
        String packageName = intent.getComponent().getPackageName();
        PackageManager pm = mApplication.getApplicationContext().getPackageManager();
        // Only binds to the application associated to this session.
        int uid = sessionParams.mUid;
        if (!Arrays.asList(pm.getPackagesForUid(uid)).contains(packageName)) return false;
        Intent serviceIntent = new Intent().setComponent(intent.getComponent());
        // This ServiceConnection doesn't handle disconnects. This is on
        // purpose, as it occurs when the remote process has died. Since the
        // only use of this connection is to keep the application alive,
        // re-connecting would just re-create the process, but the application
        // state has been lost at that point, the callbacks invalidated, etc.
        ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {}
            @Override
            public void onServiceDisconnected(ComponentName name) {}
        };
        boolean ok;
        try {
            ok = mApplication.getApplicationContext().bindService(
                    serviceIntent, connection, Context.BIND_AUTO_CREATE);
        } catch (SecurityException e) {
            return false;
        }
        if (ok) sessionParams.setServiceConnection(connection);
        return ok;
    }

    /**
     * Lets the lifetime of the process linked to a given sessionId be managed normally.
     *
     * Without a matching call to {@link keepAliveForSessionId}, this is a no-op.
     *
     * @param session The Binder object identifying the session.
     */
    void dontKeepAliveForSession(IBinder session) {
        SessionParams sessionParams;
        synchronized (mLock) {
            sessionParams = mSessionParams.get(session);
            if (sessionParams == null || sessionParams.getServiceConnection() == null) return;
        }
        ServiceConnection serviceConnection = sessionParams.getServiceConnection();
        sessionParams.setServiceConnection(null);
        mApplication.getApplicationContext().unbindService(serviceConnection);
    }

    /**
     * @return the CPU cgroup of a given process, identified by its PID, or null.
     */
    @VisibleForTesting
    static String getSchedulerGroup(int pid) {
        // Android uses two cgroups for the processes: the root cgroup, and the
        // "/bg_non_interactive" one for background processes. The list of
        // cgroups a process is part of can be queried by reading
        // /proc/<pid>/cgroup, which is world-readable.
        String cgroupFilename = "/proc/" + pid + "/cgroup";
        try {
            FileReader fileReader = new FileReader(cgroupFilename);
            BufferedReader reader = new BufferedReader(fileReader);
            try {
                String line = null;
                while ((line = reader.readLine()) != null) {
                    // line format: 2:cpu:/bg_non_interactive
                    String fields[] = line.trim().split(":");
                    if (fields.length == 3 && fields[1].equals("cpu")) return fields[2];
                }
            } finally {
                reader.close();
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }

    private static boolean isBackgroundProcess(int pid) {
        String schedulerGroup = getSchedulerGroup(pid);
        // "/bg_non_interactive" is from L MR1, "/apps/bg_non_interactive" before.
        return "/bg_non_interactive".equals(schedulerGroup)
                || "/apps/bg_non_interactive".equals(schedulerGroup);
    }

    /**
     * @return true when inside a Binder transaction and the caller is in the
     * foreground or self. Don't use outside a Binder transaction.
     */
    private boolean isCallerForegroundOrSelf() {
        int uid = Binder.getCallingUid();
        if (uid == Process.myUid()) return true;
        // Starting with L MR1, AM.getRunningAppProcesses doesn't return all the
        // processes. We use a workaround in this case.
        boolean useWorkaround = true;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            ActivityManager am =
                    (ActivityManager) mApplication.getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningAppProcessInfo> running = am.getRunningAppProcesses();
            for (ActivityManager.RunningAppProcessInfo rpi : running) {
                boolean matchingUid = rpi.uid == uid;
                boolean isForeground = rpi.importance
                        == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
                useWorkaround &= !matchingUid;
                if (matchingUid && isForeground) return true;
            }
        }
        return useWorkaround ? !isBackgroundProcess(Binder.getCallingPid()) : false;
    }

    @VisibleForTesting
    void cleanupAll() {
        ThreadUtils.assertOnUiThread();
        synchronized (mLock) {
            List<IBinder> sessions = new ArrayList<>(mSessionParams.keySet());
            for (IBinder session : sessions) cleanupAlreadyLocked(session);
        }
    }

    /**
     * Called when a remote client has died.
     */
    private void cleanupAlreadyLocked(IBinder session) {
        ThreadUtils.assertOnUiThread();
        SessionParams params = mSessionParams.get(session);
        if (params == null) return;
        mSessionParams.remove(session);
        IBinder binder = params.mCallback.asBinder();
        binder.unlinkToDeath(params.mDeathRecipient, 0);
        if (mPrerender != null && session.equals(mPrerender.mSession)) {
            prerenderUrl(session, null, null); // Cancels the pre-render.
        }
    }

    private boolean mayPrerender() {
        if (FieldTrialList.findFullName("CustomTabs").equals("DisablePrerender")) return false;
        if (!DeviceClassManager.enablePrerendering()) return false;
        ConnectivityManager cm =
                (ConnectivityManager) mApplication.getApplicationContext().getSystemService(
                        Context.CONNECTIVITY_SERVICE);
        return !cm.isActiveNetworkMetered();
    }

    private void prerenderUrl(IBinder session, String url, Bundle extras) {
        ThreadUtils.assertOnUiThread();
        // TODO(lizeb): Prerendering through ChromePrerenderService is
        // incompatible with prerendering through this service. Remove this
        // limitation, or remove ChromePrerenderService.
        WarmupManager.getInstance().disallowPrerendering();
        // Ignores mayPrerender() for an empty URL, since it cancels an existing prerender.
        if (!mayPrerender() && !TextUtils.isEmpty(url)) return;
        if (!mWarmupHasBeenCalled.get()) return;
        // Last one wins and cancels the previous prerender.
        if (mPrerender != null) {
            mExternalPrerenderHandler.cancelCurrentPrerender();
            mPrerender.mWebContents.destroy();
            mPrerender = null;
        }
        if (TextUtils.isEmpty(url)) return;
        Intent extrasIntent = new Intent();
        if (extras != null) extrasIntent.putExtras(extras);
        if (IntentHandler.getExtraHeadersFromIntent(extrasIntent) != null) return;
        if (mExternalPrerenderHandler == null) {
            mExternalPrerenderHandler = new ExternalPrerenderHandler();
        }
        Point contentSize = estimateContentSize();
        Context context = mApplication.getApplicationContext();
        String referrer = IntentHandler.getReferrerUrlIncludingExtraHeaders(extrasIntent, context);
        if (referrer == null && getReferrerForSession(session) != null) {
            referrer = getReferrerForSession(session).getUrl();
        }
        if (referrer == null) referrer = "";
        WebContents webContents = mExternalPrerenderHandler.addPrerender(
                Profile.getLastUsedProfile(), url, referrer, contentSize.x, contentSize.y);
        if (webContents != null) {
            mPrerender = new PrerenderedUrlParams(session, webContents, url, referrer, extras);
        }
    }

    /**
     * Provides an estimate of the contents size.
     *
     * The estimate is likely to be incorrect. This is not a problem, as the aim
     * is to avoid getting a different layout and resources than needed at
     * render time.
     */
    private Point estimateContentSize() {
        // The size is estimated as:
        // X = screenSizeX
        // Y = screenSizeY - top bar - bottom bar - custom tabs bar
        Point screenSize = new Point();
        WindowManager wm = (WindowManager) mApplication.getSystemService(Context.WINDOW_SERVICE);
        wm.getDefaultDisplay().getSize(screenSize);
        Resources resources = mApplication.getResources();
        int statusBarId = resources.getIdentifier("status_bar_height", "dimen", "android");
        int navigationBarId = resources.getIdentifier("navigation_bar_height", "dimen", "android");
        try {
            screenSize.y -=
                    resources.getDimensionPixelSize(R.dimen.custom_tabs_control_container_height);
            screenSize.y -= resources.getDimensionPixelSize(statusBarId);
            screenSize.y -= resources.getDimensionPixelSize(navigationBarId);
        } catch (Resources.NotFoundException e) {
            // Nothing, this is just a best effort estimate.
        }
        return screenSize;
    }

    @VisibleForTesting
    void resetThrottling(int uid) {
        synchronized (mLock) {
            mUidToPredictionsStats.put(uid, new PredictionStats());
        }
    }
}

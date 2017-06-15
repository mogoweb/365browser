// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;

import org.chromium.base.Log;
import org.chromium.base.TraceEvent;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.process_launcher.ChildProcessCreationParams;
import org.chromium.base.process_launcher.ICallbackInt;
import org.chromium.base.process_launcher.IChildProcessService;

import javax.annotation.Nullable;

/**
 * Manages a connection between the browser activity and a child service.
 */
public class ChildProcessConnection {
    private static final String TAG = "ChildProcessConn";

    /**
     * Used to notify the consumer about disconnection of the service. This callback is provided
     * earlier than ConnectionCallbacks below, as a child process might die before the connection is
     * fully set up.
     */
    interface DeathCallback {
        // Called on Launcher thread.
        void onChildProcessDied(ChildProcessConnection connection);
    }

    /**
     * Used to notify the consumer about the process start. These callbacks will be invoked before
     * the ConnectionCallbacks.
     */
    interface StartCallback {
        /**
         * Called when the child process has successfully started and is ready for connection
         * setup.
         */
        void onChildStarted();

        /**
         * Called when the child process failed to start. This can happen if the process is already
         * in use by another client.
         */
        void onChildStartFailed();
    }

    /**
     * Used to notify the consumer about the connection being established.
     */
    interface ConnectionCallback {
        /**
         * Called when the connection to the service is established.
         * @param connecion the connection object to the child process
         */
        void onConnected(ChildProcessConnection connection);
    }

    /** Interface representing a connection to the Android service. Can be mocked in unit-tests. */
    @VisibleForTesting
    protected interface ChildServiceConnection {
        boolean bind();
        void unbind();
        boolean isBound();
    }

    /** Implementation of ChildServiceConnection that does connect to a service. */
    private class ChildServiceConnectionImpl implements ChildServiceConnection, ServiceConnection {
        private final int mBindFlags;
        private boolean mBound;

        private Intent createServiceBindIntent() {
            Intent intent = new Intent();
            if (mCreationParams != null) {
                mCreationParams.addIntentExtras(intent);
            }
            intent.setComponent(mServiceName);
            return intent;
        }

        private ChildServiceConnectionImpl(int bindFlags) {
            mBindFlags = bindFlags;
        }

        @Override
        public boolean bind() {
            if (!mBound) {
                try {
                    TraceEvent.begin("ChildProcessConnection.ChildServiceConnectionImpl.bind");
                    Intent intent = createServiceBindIntent();
                    if (mChildProcessCommonParameters != null) {
                        intent.putExtras(mChildProcessCommonParameters);
                    }
                    mBound = mContext.bindService(intent, this, mBindFlags);
                } finally {
                    TraceEvent.end("ChildProcessConnection.ChildServiceConnectionImpl.bind");
                }
            }
            return mBound;
        }

        @Override
        public void unbind() {
            if (mBound) {
                mContext.unbindService(this);
                mBound = false;
            }
        }

        @Override
        public boolean isBound() {
            return mBound;
        }

        @Override
        public void onServiceConnected(ComponentName className, final IBinder service) {
            LauncherThread.post(new Runnable() {
                @Override
                public void run() {
                    ChildProcessConnection.this.onServiceConnectedOnLauncherThread(service);
                }
            });
        }

        // Called on the main thread to notify that the child service did not disconnect gracefully.
        @Override
        public void onServiceDisconnected(ComponentName className) {
            LauncherThread.post(new Runnable() {
                @Override
                public void run() {
                    ChildProcessConnection.this.onServiceDisconnectedOnLauncherThread();
                }
            });
        }
    }

    private final Context mContext;
    private final ChildProcessConnection.DeathCallback mDeathCallback;
    private final ComponentName mServiceName;

    // Parameters passed to the child process through the service binding intent.
    // If the service gets recreated by the framework the intent will be reused, so these parameters
    // should be common to all processes of that type.
    private final Bundle mChildProcessCommonParameters;

    private final ChildProcessCreationParams mCreationParams;

    private static class ConnectionParams {
        final Bundle mConnectionBundle;
        final IBinder mCallback;

        ConnectionParams(Bundle connectionBundle, IBinder callback) {
            mConnectionBundle = connectionBundle;
            mCallback = callback;
        }
    }

    // This is set in start() and is used in onServiceConnected().
    private StartCallback mStartCallback;

    // This is set in setupConnection() and is later used in doConnectionSetup(), after which the
    // variable is cleared. Therefore this is only valid while the connection is being set up.
    private ConnectionParams mConnectionParams;

    // Callback provided in setupConnection() that will communicate the result to the caller. This
    // has to be called exactly once after setupConnection(), even if setup fails, so that the
    // caller can free up resources associated with the setup attempt. This is set to null after the
    // call.
    private ConnectionCallback mConnectionCallback;

    private IChildProcessService mService;

    // Set to true when the service connection callback runs. This differs from
    // mServiceConnectComplete, which tracks that the connection completed successfully.
    private boolean mDidOnServiceConnected;

    // Set to true when the service connected successfully.
    private boolean mServiceConnectComplete;

    // Set to true when the service disconnects, as opposed to being properly closed. This happens
    // when the process crashes or gets killed by the system out-of-memory killer.
    private boolean mServiceDisconnected;

    // Process ID of the corresponding child process.
    private int mPid;

    // Inital moderate binding.
    private final ChildServiceConnection mInitialBinding;

    // Strong binding will make the service priority equal to the priority of the activity.
    private final ChildServiceConnection mStrongBinding;

    // Moderate binding will make the service priority equal to the priority of a visible process
    // while the app is in the foreground.
    private final ChildServiceConnection mModerateBinding;

    // Low priority binding maintained in the entire lifetime of the connection, i.e. between calls
    // to start() and stop().
    private final ChildServiceConnection mWaivedBinding;

    // Incremented on addStrongBinding(), decremented on removeStrongBinding().
    private int mStrongBindingCount;

    // Indicates whether the connection only has the waived binding (if the connection is unbound,
    // it contains the state at time of unbinding).
    private boolean mWaivedBoundOnly;

    // Set to true once unbind() was called.
    private boolean mUnbound;

    ChildProcessConnection(Context context, DeathCallback deathCallback, ComponentName serviceName,
            boolean bindAsExternalService, Bundle childProcessCommonParameters,
            ChildProcessCreationParams creationParams) {
        this(context, deathCallback, serviceName, bindAsExternalService,
                childProcessCommonParameters, creationParams, true /* doBind */);
    }

    private ChildProcessConnection(Context context, DeathCallback deathCallback,
            ComponentName serviceName, boolean bindAsExternalService,
            Bundle childProcessCommonParameters, ChildProcessCreationParams creationParams,
            boolean doBind) {
        assert LauncherThread.runningOnLauncherThread();
        mContext = context;
        mDeathCallback = deathCallback;
        mCreationParams = creationParams;
        mServiceName = serviceName;

        mChildProcessCommonParameters = childProcessCommonParameters;

        if (doBind) {
            int defaultFlags = Context.BIND_AUTO_CREATE
                    | (bindAsExternalService ? Context.BIND_EXTERNAL_SERVICE : 0);
            mInitialBinding = createServiceConnection(defaultFlags);
            mModerateBinding = createServiceConnection(defaultFlags);
            mStrongBinding = createServiceConnection(defaultFlags | Context.BIND_IMPORTANT);
            mWaivedBinding = createServiceConnection(defaultFlags | Context.BIND_WAIVE_PRIORITY);
        } else {
            mInitialBinding = null;
            mModerateBinding = null;
            mStrongBinding = null;
            mWaivedBinding = null;
        }
    }

    public final Context getContext() {
        assert LauncherThread.runningOnLauncherThread();
        return mContext;
    }

    public final String getPackageName() {
        assert LauncherThread.runningOnLauncherThread();
        return mServiceName.getPackageName();
    }

    public final IChildProcessService getService() {
        assert LauncherThread.runningOnLauncherThread();
        return mService;
    }

    public final ComponentName getServiceName() {
        assert LauncherThread.runningOnLauncherThread();
        return mServiceName;
    }

    public boolean isConnected() {
        return mService != null;
    }

    /**
     * @return the connection pid, or 0 if not yet connected
     */
    public int getPid() {
        assert LauncherThread.runningOnLauncherThread();
        return mPid;
    }

    /**
     * Starts a connection to an IChildProcessService. This must be followed by a call to
     * setupConnection() to setup the connection parameters. start() and setupConnection() are
     * separate to allow to pass whatever parameters are available in start(), and complete the
     * remainder addStrongBinding while reducing the connection setup latency.
     * @param useStrongBinding whether a strong binding should be bound by default. If false, an
     * initial moderate binding is used.
     * @param startCallback (optional) callback when the child process starts or fails to start.
     */
    public void start(boolean useStrongBinding, StartCallback startCallback) {
        assert LauncherThread.runningOnLauncherThread();
        try {
            TraceEvent.begin("ChildProcessConnection.start");
            assert LauncherThread.runningOnLauncherThread();
            assert mConnectionParams
                    == null : "setupConnection() called before start() in ChildProcessConnection.";

            mStartCallback = startCallback;

            if (!bind(useStrongBinding)) {
                Log.e(TAG, "Failed to establish the service connection.");
                // We have to notify the caller so that they can free-up associated resources.
                // TODO(ppi): Can we hard-fail here?
                mDeathCallback.onChildProcessDied(ChildProcessConnection.this);
            }
        } finally {
            TraceEvent.end("ChildProcessConnection.start");
        }
    }

    /**
     * Sets-up the connection after it was started with start().
     * @param connectionBundle a bundle passed to the service that can be used to pass various
     *         parameters to the service
     * @param callback optional client specified callbacks that the child can use to communicate
     *                 with the parent process
     * @param connectionCallback will be called exactly once after the connection is set up or the
     *                           setup fails
     */
    public void setupConnection(Bundle connectionBundle, @Nullable IBinder callback,
            ConnectionCallback connectionCallback) {
        assert LauncherThread.runningOnLauncherThread();
        assert mConnectionParams == null;
        if (mServiceDisconnected) {
            Log.w(TAG, "Tried to setup a connection that already disconnected.");
            connectionCallback.onConnected(null);
            return;
        }
        try {
            TraceEvent.begin("ChildProcessConnection.setupConnection");
            mConnectionCallback = connectionCallback;
            mConnectionParams = new ConnectionParams(connectionBundle, callback);
            // Run the setup if the service is already connected. If not, doConnectionSetup() will
            // be called from onServiceConnected().
            if (mServiceConnectComplete) {
                doConnectionSetup();
            }
        } finally {
            TraceEvent.end("ChildProcessConnection.setupConnection");
        }
    }

    /**
     * Terminates the connection to IChildProcessService, closing all bindings. It is safe to call
     * this multiple times.
     */
    public void stop() {
        assert LauncherThread.runningOnLauncherThread();
        unbind();
        mService = null;
        mConnectionParams = null;
    }

    private void onServiceConnectedOnLauncherThread(IBinder service) {
        assert LauncherThread.runningOnLauncherThread();
        // A flag from the parent class ensures we run the post-connection logic only once
        // (instead of once per each ChildServiceConnection).
        if (mDidOnServiceConnected) {
            return;
        }
        try {
            TraceEvent.begin("ChildProcessConnection.ChildServiceConnection.onServiceConnected");
            mDidOnServiceConnected = true;
            mService = IChildProcessService.Stub.asInterface(service);

            StartCallback startCallback = mStartCallback;
            mStartCallback = null;

            boolean boundToUs = false;
            try {
                boolean bindCheck =
                        mCreationParams != null && mCreationParams.getBindToCallerCheck();
                boundToUs = bindCheck ? mService.bindToCaller() : true;
            } catch (RemoteException ex) {
                // Do not trigger the StartCallback here, since the service is already
                // dead and the DeathCallback will run from onServiceDisconnected().
                Log.e(TAG, "Failed to bind service to connection.", ex);
                return;
            }

            if (startCallback != null) {
                if (boundToUs) {
                    startCallback.onChildStarted();
                } else {
                    startCallback.onChildStartFailed();
                }
            }

            if (!boundToUs) {
                return;
            }

            mServiceConnectComplete = true;

            // Run the setup if the connection parameters have already been provided. If
            // not, doConnectionSetup() will be called from setupConnection().
            if (mConnectionParams != null) {
                doConnectionSetup();
            }
        } finally {
            TraceEvent.end("ChildProcessConnection.ChildServiceConnection.onServiceConnected");
        }
    }

    private void onServiceDisconnectedOnLauncherThread() {
        assert LauncherThread.runningOnLauncherThread();
        // Ensure that the disconnection logic runs only once (instead of once per each
        // ChildServiceConnection).
        if (mServiceDisconnected) {
            return;
        }
        mServiceDisconnected = true;
        Log.w(TAG, "onServiceDisconnected (crash or killed by oom): pid=%d", mPid);
        stop(); // We don't want to auto-restart on crash. Let the browser do that.
        mDeathCallback.onChildProcessDied(ChildProcessConnection.this);
        // If we have a pending connection callback, we need to communicate the failure to
        // the caller.
        if (mConnectionCallback != null) {
            mConnectionCallback.onConnected(null);
        }
        mConnectionCallback = null;
    }

    private void onSetupConnectionResult(int pid) {
        mPid = pid;
        assert mPid != 0 : "Child service claims to be run by a process of pid=0.";

        if (mConnectionCallback != null) {
            mConnectionCallback.onConnected(this);
        }
        mConnectionCallback = null;
    }

    /**
     * Called after the connection parameters have been set (in setupConnection()) *and* a
     * connection has been established (as signaled by onServiceConnected()). These two events can
     * happen in any order.
     */
    private void doConnectionSetup() {
        try {
            TraceEvent.begin("ChildProcessConnection.doConnectionSetup");
            assert mServiceConnectComplete && mService != null;
            assert mConnectionParams != null;

            ICallbackInt pidCallback = new ICallbackInt.Stub() {
                @Override
                public void call(final int pid) {
                    LauncherThread.post(new Runnable() {
                        @Override
                        public void run() {
                            onSetupConnectionResult(pid);
                        }
                    });
                }
            };
            try {
                mService.setupConnection(mConnectionParams.mConnectionBundle, pidCallback,
                        mConnectionParams.mCallback);
            } catch (RemoteException re) {
                Log.e(TAG, "Failed to setup connection.", re);
            }
            mConnectionParams = null;
        } finally {
            TraceEvent.end("ChildProcessConnection.doConnectionSetup");
        }
    }

    private boolean bind(boolean useStrongBinding) {
        assert LauncherThread.runningOnLauncherThread();
        assert !mUnbound;

        boolean success = useStrongBinding ? mStrongBinding.bind() : mInitialBinding.bind();
        if (!success) return false;

        updateWaivedBoundOnlyState();
        mWaivedBinding.bind();
        return true;
    }

    @VisibleForTesting
    protected void unbind() {
        assert LauncherThread.runningOnLauncherThread();
        mUnbound = true;
        mStrongBinding.unbind();
        mWaivedBinding.unbind();
        mModerateBinding.unbind();
        mInitialBinding.unbind();
        // Note that we don't update the waived bound only state here as to preserve the state when
        // disconnected.
    }

    public boolean isInitialBindingBound() {
        assert LauncherThread.runningOnLauncherThread();
        return mInitialBinding.isBound();
    }

    public void addInitialBinding() {
        assert LauncherThread.runningOnLauncherThread();
        mInitialBinding.bind();
        updateWaivedBoundOnlyState();
    }

    public boolean isStrongBindingBound() {
        assert LauncherThread.runningOnLauncherThread();
        return mStrongBinding.isBound();
    }

    public void removeInitialBinding() {
        assert LauncherThread.runningOnLauncherThread();
        mInitialBinding.unbind();
        updateWaivedBoundOnlyState();
    }

    public void dropOomBindings() {
        assert LauncherThread.runningOnLauncherThread();
        mInitialBinding.unbind();

        mStrongBindingCount = 0;
        mStrongBinding.unbind();
        updateWaivedBoundOnlyState();

        mModerateBinding.unbind();
    }

    public void addStrongBinding() {
        assert LauncherThread.runningOnLauncherThread();
        if (!isConnected()) {
            Log.w(TAG, "The connection is not bound for %d", getPid());
            return;
        }
        if (mStrongBindingCount == 0) {
            mStrongBinding.bind();
            updateWaivedBoundOnlyState();
        }
        mStrongBindingCount++;
    }

    public void removeStrongBinding() {
        assert LauncherThread.runningOnLauncherThread();
        if (!isConnected()) {
            Log.w(TAG, "The connection is not bound for %d", getPid());
            return;
        }
        assert mStrongBindingCount > 0;
        mStrongBindingCount--;
        if (mStrongBindingCount == 0) {
            mStrongBinding.unbind();
            updateWaivedBoundOnlyState();
        }
    }

    public boolean isModerateBindingBound() {
        assert LauncherThread.runningOnLauncherThread();
        return mModerateBinding.isBound();
    }

    public void addModerateBinding() {
        assert LauncherThread.runningOnLauncherThread();
        if (!isConnected()) {
            Log.w(TAG, "The connection is not bound for %d", getPid());
            return;
        }
        mModerateBinding.bind();
        updateWaivedBoundOnlyState();
    }

    public void removeModerateBinding() {
        assert LauncherThread.runningOnLauncherThread();
        if (!isConnected()) {
            Log.w(TAG, "The connection is not bound for %d", getPid());
            return;
        }
        mModerateBinding.unbind();
        updateWaivedBoundOnlyState();
    }

    /**
     * @return true if the connection is bound and only bound with the waived binding or if the
     * connection is unbound and was only bound with the waived binding when it disconnected.
     */
    public boolean isWaivedBoundOnlyOrWasWhenDied() {
        // WARNING: this method can be called from a thread other than the launcher thread.
        // Note that it returns the current waived bound only state and is racy. This not really
        // preventable without changing the caller's API, short of blocking.
        return mWaivedBoundOnly;
    }

    // Should be called every time the mInitialBinding or mStrongBinding are bound/unbound.
    private void updateWaivedBoundOnlyState() {
        if (!mUnbound) {
            mWaivedBoundOnly = !mInitialBinding.isBound() && !mStrongBinding.isBound()
                    && !mModerateBinding.isBound();
        }
    }

    @VisibleForTesting
    protected ChildServiceConnection createServiceConnection(int bindFlags) {
        assert LauncherThread.runningOnLauncherThread();
        return new ChildServiceConnectionImpl(bindFlags);
    }

    @VisibleForTesting
    public void crashServiceForTesting() throws RemoteException {
        mService.crashIntentionallyForTesting();
    }

    /** Creates a connection with no service bindings. */
    @VisibleForTesting
    static ChildProcessConnection createUnboundConnectionForTesting(Context context,
            DeathCallback deathCallback, ComponentName serviceName, boolean bindAsExternalService,
            Bundle childProcessCommonParameters, ChildProcessCreationParams creationParams) {
        return new ChildProcessConnection(context, deathCallback, serviceName,
                bindAsExternalService, childProcessCommonParameters, creationParams,
                false /* doBind */);
    }
}

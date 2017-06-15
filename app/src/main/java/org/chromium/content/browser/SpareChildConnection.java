// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.content.Context;
import android.os.Bundle;

import org.chromium.base.Log;
import org.chromium.base.process_launcher.ChildProcessCreationParams;

/**
 * This class is used to create a single spare ChildProcessConnection (usually early on during
 * start-up) that can then later be retrieved when a connection to a service is needed.
 */
public class SpareChildConnection {
    private static final String TAG = "SpareChildConn";

    // The actual spare connection.
    private ChildProcessConnection mConnection;

    // True when there is a spare connection and it is bound.
    private boolean mConnectionReady;

    // The callback that should be called when the connection becomes bound. Only non null when the
    // connection was retrieved but was not bound yet.
    private ChildProcessConnection.StartCallback mConnectionStartCallback;

    // Properties of the spare connection.
    private boolean mSandboxed;
    private boolean mAlwaysInForegound;
    private ChildProcessCreationParams mCreationParams;

    // An interface used to abstract connection creation so tests can use custom connections.
    interface ConnectionFactory {
        ChildProcessConnection allocateBoundConnection(ChildSpawnData spawnData,
                ChildProcessConnection.StartCallback startCallback, boolean queueIfNoneAvailable);
    }

    /** Creates and binds a ChildProcessConnection using the specified parameters. */
    public SpareChildConnection(Context context, ConnectionFactory connectionFactory,
            Bundle serviceBundle, boolean sandboxed, boolean alwaysInForeground,
            ChildProcessCreationParams creationParams) {
        assert LauncherThread.runningOnLauncherThread();
        ChildProcessConnection.StartCallback startCallback =
                new ChildProcessConnection.StartCallback() {
                    @Override
                    public void onChildStarted() {
                        assert LauncherThread.runningOnLauncherThread();
                        mConnectionReady = true;
                        if (mConnectionStartCallback != null) {
                            mConnectionStartCallback.onChildStarted();
                            clearConnection();
                        }
                        // If there is no chained callback, that means the spare connection has not
                        // been used yet. It will be cleared when used.
                    }

                    @Override
                    public void onChildStartFailed() {
                        assert LauncherThread.runningOnLauncherThread();
                        Log.e(TAG, "Failed to warm up the spare sandbox service");
                        if (mConnectionStartCallback != null) {
                            mConnectionStartCallback.onChildStartFailed();
                        }
                        clearConnection();
                    }
                };

        mSandboxed = sandboxed;
        mAlwaysInForegound = alwaysInForeground;
        mCreationParams = creationParams;
        ChildSpawnData spawnData = new ChildSpawnData(context, serviceBundle, null /* connection */,
                null /* launchCallback */, null /* child process callback */, sandboxed,
                alwaysInForeground, creationParams);
        mConnection = connectionFactory.allocateBoundConnection(
                spawnData, startCallback, false /* queueIfNoneAvailable */);
    }

    /**
     * @return a connection that has been bound or is being bound matching the given paramters, null
     * otherwise.
     */
    public ChildProcessConnection getConnection(Context context, boolean sandboxed,
            boolean alwaysInForeground, ChildProcessCreationParams creationParams,
            final ChildProcessConnection.StartCallback startCallback) {
        assert LauncherThread.runningOnLauncherThread();
        if (mConnection == null || mSandboxed != sandboxed
                || mAlwaysInForegound != alwaysInForeground || mCreationParams != creationParams
                || mConnectionStartCallback != null
                || !mConnection.getPackageName().equals(
                           ChildProcessLauncher.getPackageNameFromCreationParams(
                                   context, mCreationParams, sandboxed))) {
            return null;
        }

        ChildProcessConnection connection = mConnection;
        if (mConnectionReady) {
            if (startCallback != null) {
                // Post a task so the callback happens after the caller has retrieved the
                // connection.
                LauncherThread.post(new Runnable() {
                    @Override
                    public void run() {
                        startCallback.onChildStarted();
                    }
                });
            }
            clearConnection();
        } else {
            mConnectionStartCallback = startCallback;
        }
        return connection;
    }

    /**
     * Called when a connection is freed to give an opportunity to this class to clean up its
     * connection if it's the one that's been freed.
     * TODO(jcivelli): use the ChildProcessConnection.DeathCallback of the actuall connection so we
     * don't need this anymore once the CPL has been simplified.
     */
    public boolean onConnectionFreed(ChildProcessConnection connection) {
        if (mConnection != connection) {
            return false;
        }
        clearConnection();
        return true;
    }

    private void clearConnection() {
        assert LauncherThread.runningOnLauncherThread();
        mConnection = null;
        mConnectionReady = false;
        mConnectionStartCallback = null;
    }
}

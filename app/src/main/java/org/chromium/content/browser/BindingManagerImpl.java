// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.res.Configuration;
import android.util.SparseArray;

import org.chromium.base.Log;
import org.chromium.base.SysUtils;
import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.metrics.RecordHistogram;

import java.util.LinkedList;

/**
 * Manages oom bindings used to bound child services.
 * This object must only be accessed from the launcher thread.
 */
class BindingManagerImpl implements BindingManager {
    private static final String TAG = "cr_BindingManager";

    // Low reduce ratio of moderate binding.
    private static final float MODERATE_BINDING_LOW_REDUCE_RATIO = 0.25f;
    // High reduce ratio of moderate binding.
    private static final float MODERATE_BINDING_HIGH_REDUCE_RATIO = 0.5f;

    // Delay of 1 second used when removing temporary strong binding of a process (only on
    // non-low-memory devices).
    private static final long DETACH_AS_ACTIVE_HIGH_END_DELAY_MILLIS = 1 * 1000;

    // Delays used when clearing moderate binding pool when onSentToBackground happens.
    private static final long MODERATE_BINDING_POOL_CLEARER_DELAY_MILLIS = 10 * 1000;

    // These fields allow to override the parameters for testing - see
    // createBindingManagerForTesting().
    private final boolean mIsLowMemoryDevice;

    private static class ModerateBindingPool implements ComponentCallbacks2 {
        // Stores the connections in MRU order.
        private final LinkedList<ManagedConnection> mConnections = new LinkedList<>();
        private final int mMaxSize;

        private Runnable mDelayedClearer;

        public ModerateBindingPool(int maxSize) {
            mMaxSize = maxSize;
        }

        @Override
        public void onTrimMemory(final int level) {
            ThreadUtils.assertOnUiThread();
            LauncherThread.post(new Runnable() {
                @Override
                public void run() {
                    Log.i(TAG, "onTrimMemory: level=%d, size=%d", level, mConnections.size());
                    if (mConnections.isEmpty()) {
                        return;
                    }
                    if (level <= TRIM_MEMORY_RUNNING_MODERATE) {
                        reduce(MODERATE_BINDING_LOW_REDUCE_RATIO);
                    } else if (level <= TRIM_MEMORY_RUNNING_LOW) {
                        reduce(MODERATE_BINDING_HIGH_REDUCE_RATIO);
                    } else if (level == TRIM_MEMORY_UI_HIDDEN) {
                        // This will be handled by |mDelayedClearer|.
                        return;
                    } else {
                        removeAllConnections();
                    }
                }
            });
        }

        @Override
        public void onLowMemory() {
            ThreadUtils.assertOnUiThread();
            LauncherThread.post(new Runnable() {
                @Override
                public void run() {
                    Log.i(TAG, "onLowMemory: evict %d bindings", mConnections.size());
                    removeAllConnections();
                }
            });
        }

        @Override
        public void onConfigurationChanged(Configuration configuration) {}

        private void reduce(float reduceRatio) {
            int oldSize = mConnections.size();
            int newSize = (int) (oldSize * (1f - reduceRatio));
            Log.i(TAG, "Reduce connections from %d to %d", oldSize, newSize);
            removeOldConnections(oldSize - newSize);
            assert mConnections.size() == newSize;
        }

        void addConnection(ManagedConnection managedConnection) {
            managedConnection.addModerateBinding();
            if (managedConnection.mConnection.isModerateBindingBound()) {
                addConnectionImpl(managedConnection);
            } else {
                removeConnectionImpl(managedConnection);
            }
        }

        void removeConnection(ManagedConnection managedConnection) {
            removeConnectionImpl(managedConnection);
        }

        void removeAllConnections() {
            removeOldConnections(mConnections.size());
        }

        int size() {
            return mConnections.size();
        }

        private void addConnectionImpl(ManagedConnection managedConnection) {
            // Note that the size of connections is currently fairly small (20).
            // If it became bigger we should consider using an alternate data structure so we don't
            // have to traverse the list every time.

            // Remove the connection if it's already in the list, we'll add it at the head.
            mConnections.removeFirstOccurrence(managedConnection);
            if (mConnections.size() == mMaxSize) {
                // Make room for the connection we are about to add.
                removeOldConnections(1);
            }
            mConnections.add(0, managedConnection);
            assert mConnections.size() <= mMaxSize;
        }

        private void removeConnectionImpl(ManagedConnection managedConnection) {
            int index = mConnections.indexOf(managedConnection);
            if (index != -1) {
                ManagedConnection connection = mConnections.remove(index);
                connection.mConnection.removeModerateBinding();
            }
        }

        private void removeOldConnections(int numberOfConnections) {
            assert numberOfConnections <= mConnections.size();
            for (int i = 0; i < numberOfConnections; i++) {
                ManagedConnection connection = mConnections.removeLast();
                connection.mConnection.removeModerateBinding();
            }
        }

        void onSentToBackground(final boolean onTesting) {
            if (mConnections.isEmpty()) return;
            mDelayedClearer = new Runnable() {
                @Override
                public void run() {
                    Log.i(TAG, "Release moderate connections: %d", mConnections.size());
                    if (!onTesting) {
                        RecordHistogram.recordCountHistogram(
                                "Android.ModerateBindingCount", mConnections.size());
                    }
                    removeAllConnections();
                }
            };
            LauncherThread.postDelayed(mDelayedClearer, MODERATE_BINDING_POOL_CLEARER_DELAY_MILLIS);
        }

        void onBroughtToForeground() {
            if (mDelayedClearer != null) {
                LauncherThread.removeCallbacks(mDelayedClearer);
                mDelayedClearer = null;
            }
        }
    }

    private ModerateBindingPool mModerateBindingPool;

    /**
     * Wraps ChildProcessConnection keeping track of additional information needed to manage the
     * bindings of the connection. It goes away when the connection goes away.
     */
    private class ManagedConnection {
        // The connection to the service.
        private final ChildProcessConnection mConnection;

        // True iff there is a strong binding kept on the service because it is working in
        // foreground.
        private boolean mInForeground;

        // Indicates there's a pending view in this connection that's about to become foreground.
        // This currently maps exactly to the initial binding.
        private boolean mBoostPriorityForPendingViews = true;

        // True iff there is a strong binding kept on the service because it was bound for the
        // application background period.
        private boolean mBoundForBackgroundPeriod;

        /** Adds a strong service binding. */
        private void addStrongBinding() {
            mConnection.addStrongBinding();
            if (mModerateBindingPool != null) mModerateBindingPool.removeConnection(this);
        }

        /** Removes a strong service binding. */
        private void removeStrongBinding(final boolean keepAsModerate) {
            // We have to fail gracefully if the strong binding is not present, as on low-end the
            // binding could have been removed by dropOomBindings() when a new service was started.
            if (!mConnection.isStrongBindingBound()) return;

            // This runnable performs the actual unbinding. It will be executed synchronously when
            // on low-end devices and posted with a delay otherwise.
            Runnable doUnbind = new Runnable() {
                @Override
                public void run() {
                    if (mConnection.isStrongBindingBound()) {
                        mConnection.removeStrongBinding();
                        if (keepAsModerate) {
                            addConnectionToModerateBindingPool(mConnection);
                        }
                    }
                }
            };

            if (mIsLowMemoryDevice) {
                doUnbind.run();
            } else {
                LauncherThread.postDelayed(doUnbind, DETACH_AS_ACTIVE_HIGH_END_DELAY_MILLIS);
            }
        }

        /**
         * Adds connection to the moderate binding pool. No-op if the connection has a strong
         * binding.
         * @param connection The ChildProcessConnection to add to the moderate binding pool.
         */
        private void addConnectionToModerateBindingPool(ChildProcessConnection connection) {
            if (mModerateBindingPool != null && !connection.isStrongBindingBound()) {
                mModerateBindingPool.addConnection(ManagedConnection.this);
            }
        }

        /** Removes the moderate service binding. */
        private void removeModerateBinding() {
            if (!mConnection.isModerateBindingBound()) return;
            mConnection.removeModerateBinding();
        }

        /** Adds the moderate service binding. */
        private void addModerateBinding() {
            mConnection.addModerateBinding();
        }

        /**
         * Drops the service bindings. This is used on low-end to drop bindings of the current
         * service when a new one is used in foreground.
         */
        private void dropBindings() {
            assert mIsLowMemoryDevice;
            mConnection.dropOomBindings();
        }

        ManagedConnection(ChildProcessConnection connection) {
            mConnection = connection;
        }

        /**
         * Sets the visibility of the service, adding or removing the strong binding as needed.
         */
        void setPriority(boolean foreground, boolean boostForPendingViews) {
            // Always add bindings before removing them.
            if (!mInForeground && foreground) {
                addStrongBinding();
            }
            if (!mBoostPriorityForPendingViews && boostForPendingViews) {
                mConnection.addInitialBinding();
            }

            if (mInForeground && !foreground) {
                removeStrongBinding(true);
            }
            if (mBoostPriorityForPendingViews && !boostForPendingViews) {
                addConnectionToModerateBindingPool(mConnection);
                mConnection.removeInitialBinding();
            }

            mInForeground = foreground;
            mBoostPriorityForPendingViews = boostForPendingViews;
        }

        /**
         * Sets or removes additional binding when the service is main service during the embedder
         * background period.
         */
        void setBoundForBackgroundPeriod(boolean boundForBackgroundPeriod) {
            if (boundForBackgroundPeriod == mBoundForBackgroundPeriod) return;

            if (boundForBackgroundPeriod) {
                addStrongBinding();
            } else {
                removeStrongBinding(false);
            }
            mBoundForBackgroundPeriod = boundForBackgroundPeriod;
        }
    }

    private final SparseArray<ManagedConnection> mManagedConnections =
            new SparseArray<ManagedConnection>();

    // The connection that was most recently set as foreground (using setInForeground()). This is
    // used to add additional binding on it when the embedder goes to background. On low-end, this
    // is also used to drop process bindings when a new one is created, making sure that only one
    // renderer process at a time is protected from oom killing.
    private ManagedConnection mLastConnectionInForeground;

    // The connection bound with additional binding in onSentToBackground().
    private ManagedConnection mConnectionBoundForBackgroundPeriod;

    // Whether this instance is used on testing.
    private final boolean mOnTesting;

    /**
     * The constructor is private to hide parameters exposed for testing from the regular consumer.
     * Use factory methods to create an instance.
     */
    private BindingManagerImpl(boolean isLowMemoryDevice, boolean onTesting) {
        assert LauncherThread.runningOnLauncherThread();
        mIsLowMemoryDevice = isLowMemoryDevice;
        mOnTesting = onTesting;
    }

    public static BindingManagerImpl createBindingManager() {
        assert LauncherThread.runningOnLauncherThread();
        return new BindingManagerImpl(SysUtils.isLowEndDevice(), false);
    }

    /**
     * Creates a testing instance of BindingManager. Testing instance will have the unbinding delays
     * set to 0, so that the tests don't need to deal with actual waiting.
     * @param isLowEndDevice true iff the created instance should apply low-end binding policies
     */
    public static BindingManagerImpl createBindingManagerForTesting(boolean isLowEndDevice) {
        assert LauncherThread.runningOnLauncherThread();
        return new BindingManagerImpl(isLowEndDevice, true);
    }

    @Override
    public void addNewConnection(int pid, ChildProcessConnection connection) {
        assert LauncherThread.runningOnLauncherThread();
        // This will reset the previous entry for the pid in the unlikely event of the OS
        // reusing renderer pids.
        mManagedConnections.put(pid, new ManagedConnection(connection));
    }

    @Override
    public void setPriority(int pid, boolean foreground, boolean boostForPendingViews) {
        assert LauncherThread.runningOnLauncherThread();
        ManagedConnection managedConnection = mManagedConnections.get(pid);
        if (managedConnection == null) {
            Log.d(TAG, "Cannot setPriority() - never saw a connection for the pid: %d", pid);
            return;
        }

        if (foreground && mIsLowMemoryDevice && mLastConnectionInForeground != null
                && mLastConnectionInForeground != managedConnection) {
            mLastConnectionInForeground.dropBindings();
        }

        managedConnection.setPriority(foreground, boostForPendingViews);
        if (foreground) mLastConnectionInForeground = managedConnection;
    }

    @Override
    public void onSentToBackground() {
        assert LauncherThread.runningOnLauncherThread();
        assert mConnectionBoundForBackgroundPeriod == null;
        // mLastConnectionInForeground can be null at this point as the embedding application could
        // be used in foreground without spawning any renderers.
        if (mLastConnectionInForeground != null) {
            mLastConnectionInForeground.setBoundForBackgroundPeriod(true);
            mConnectionBoundForBackgroundPeriod = mLastConnectionInForeground;
        }
        if (mModerateBindingPool != null) mModerateBindingPool.onSentToBackground(mOnTesting);
    }

    @Override
    public void onBroughtToForeground() {
        assert LauncherThread.runningOnLauncherThread();
        if (mConnectionBoundForBackgroundPeriod != null) {
            mConnectionBoundForBackgroundPeriod.setBoundForBackgroundPeriod(false);
            mConnectionBoundForBackgroundPeriod = null;
        }
        if (mModerateBindingPool != null) mModerateBindingPool.onBroughtToForeground();
    }

    @Override
    public void removeConnection(int pid) {
        assert LauncherThread.runningOnLauncherThread();
        ManagedConnection managedConnection = mManagedConnections.get(pid);
        if (managedConnection == null) return;

        mManagedConnections.remove(pid);
        if (mLastConnectionInForeground == managedConnection) {
            mLastConnectionInForeground = null;
        }
        if (mConnectionBoundForBackgroundPeriod == managedConnection) {
            mConnectionBoundForBackgroundPeriod = null;
        }
        if (mModerateBindingPool != null) mModerateBindingPool.removeConnection(managedConnection);
    }

    /** @return true iff the connection reference is no longer held */
    @VisibleForTesting
    public boolean isConnectionCleared(int pid) {
        assert LauncherThread.runningOnLauncherThread();
        return mManagedConnections.get(pid) == null;
    }

    @Override
    public void startModerateBindingManagement(Context context, int maxSize) {
        assert LauncherThread.runningOnLauncherThread();
        if (mIsLowMemoryDevice) return;

        if (mModerateBindingPool == null) {
            Log.i(TAG, "Moderate binding enabled: maxSize=%d", maxSize);
            mModerateBindingPool = new ModerateBindingPool(maxSize);
            if (context != null) {
                // Note that it is safe to call Context.registerComponentCallbacks from a background
                // thread.
                context.registerComponentCallbacks(mModerateBindingPool);
            }
        }
    }

    @Override
    public void releaseAllModerateBindings() {
        assert LauncherThread.runningOnLauncherThread();
        if (mModerateBindingPool != null) {
            Log.i(TAG, "Release all moderate bindings: %d", mModerateBindingPool.size());
            mModerateBindingPool.removeAllConnections();
        }
    }
}

// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.content.Context;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.TextUtils;

import org.chromium.base.Log;
import org.chromium.base.ThreadUtils;
import org.chromium.base.TraceEvent;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.base.process_launcher.ChildProcessCreationParams;
import org.chromium.content.app.SandboxedProcessService;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class provides the method to start/stop ChildProcess called by native.
 *
 * Note about threading. The threading here is complicated and not well documented.
 * Code can run on these threads: UI, Launcher, async thread pool, binder, and one-off
 * background threads.
 */
public class ChildProcessLauncher {
    private static final String TAG = "ChildProcLauncher";

    private static final String NUM_SANDBOXED_SERVICES_KEY =
            "org.chromium.content.browser.NUM_SANDBOXED_SERVICES";
    private static final String SANDBOXED_SERVICES_NAME_KEY =
            "org.chromium.content.browser.SANDBOXED_SERVICES_NAME";
    private static final String NUM_PRIVILEGED_SERVICES_KEY =
            "org.chromium.content.browser.NUM_PRIVILEGED_SERVICES";
    private static final String PRIVILEGED_SERVICES_NAME_KEY =
            "org.chromium.content.browser.PRIVILEGED_SERVICES_NAME";

    /**
     * Implemented by ChildProcessLauncherHelper.
     */
    public interface LaunchCallback {
        void onChildProcessStarted(ChildProcessConnection connection);
    }

    // Map from package name to ChildConnectionAllocator.
    private static final Map<String, ChildConnectionAllocator>
            sSandboxedChildConnectionAllocatorMap = new HashMap<>();

    // Map from a connection to its ChildConnectionAllocator.
    private static final Map<ChildProcessConnection, ChildConnectionAllocator>
            sConnectionsToAllocatorMap = new HashMap<>();

    // Allocator used for non-sandboxed services.
    private static ChildConnectionAllocator sPrivilegedChildConnectionAllocator;

    // Used by tests to override the default sandboxed service allocator settings.
    private static ChildConnectionAllocator.ConnectionFactory sSandboxedServiceFactoryForTesting;
    private static int sSandboxedServicesCountForTesting = -1;
    private static String sSandboxedServicesNameForTesting;

    // A warmed-up connection to a sandboxed service.
    private static SpareChildConnection sSpareSandboxedConnection;

    public static String getPackageNameFromCreationParams(
            Context context, ChildProcessCreationParams params, boolean sandboxed) {
        return (sandboxed && params != null) ? params.getPackageNameForSandboxedService()
                                             : context.getPackageName();
    }

    public static boolean isServiceExternalFromCreationParams(
            ChildProcessCreationParams params, boolean sandboxed) {
        return sandboxed && params != null && params.getIsSandboxedServiceExternal();
    }

    // Factory used by the SpareConnection to create the actual ChildProcessConnection.
    private static final SpareChildConnection.ConnectionFactory SANDBOXED_SPARE_CONNECTION_FATORY =
            new SpareChildConnection.ConnectionFactory() {
                @Override
                public ChildProcessConnection allocateBoundConnection(ChildSpawnData spawnData,
                        ChildProcessConnection.StartCallback startCallback,
                        boolean queueIfNoneAvailable) {
                    return ChildProcessLauncher.allocateBoundConnection(
                            spawnData, startCallback, queueIfNoneAvailable);
                }
            };

    @SuppressFBWarnings("LI_LAZY_INIT_STATIC") // Method is single thread.
    public static ChildConnectionAllocator getConnectionAllocator(
            Context context, String packageName, boolean sandboxed, boolean bindAsExternalService) {
        assert LauncherThread.runningOnLauncherThread();
        if (!sandboxed) {
            if (sPrivilegedChildConnectionAllocator == null) {
                sPrivilegedChildConnectionAllocator = ChildConnectionAllocator.create(context,
                        packageName, PRIVILEGED_SERVICES_NAME_KEY, NUM_PRIVILEGED_SERVICES_KEY,
                        bindAsExternalService);
            }
            return sPrivilegedChildConnectionAllocator;
        }

        if (!sSandboxedChildConnectionAllocatorMap.containsKey(packageName)) {
            Log.w(TAG,
                    "Create a new ChildConnectionAllocator with package name = %s,"
                            + " inSandbox = true",
                    packageName);
            ChildConnectionAllocator connectionAllocator = null;
            if (sSandboxedServicesCountForTesting != -1) {
                // Testing case where allocator settings are overriden.
                String serviceName = !TextUtils.isEmpty(sSandboxedServicesNameForTesting)
                        ? sSandboxedServicesNameForTesting
                        : SandboxedProcessService.class.getName();
                connectionAllocator = ChildConnectionAllocator.createForTest(packageName,
                        serviceName, sSandboxedServicesCountForTesting, bindAsExternalService);
            } else {
                connectionAllocator = ChildConnectionAllocator.create(context, packageName,
                        SANDBOXED_SERVICES_NAME_KEY, NUM_SANDBOXED_SERVICES_KEY,
                        bindAsExternalService);
            }
            if (sSandboxedServiceFactoryForTesting != null) {
                connectionAllocator.setConnectionFactoryForTesting(
                        sSandboxedServiceFactoryForTesting);
            }
            sSandboxedChildConnectionAllocatorMap.put(packageName, connectionAllocator);
        }
        return sSandboxedChildConnectionAllocatorMap.get(packageName);
    }

    @VisibleForTesting
    static ChildProcessConnection allocateConnection(
            ChildSpawnData spawnData, boolean queueIfNoneAvailable) {
        assert LauncherThread.runningOnLauncherThread();
        ChildProcessConnection.DeathCallback deathCallback =
                new ChildProcessConnection.DeathCallback() {
                    @Override
                    public void onChildProcessDied(ChildProcessConnection connection) {
                        assert LauncherThread.runningOnLauncherThread();
                        if (connection.getPid() != 0) {
                            stop(connection.getPid());
                        } else {
                            freeConnection(connection);
                        }
                    }
                };
        final ChildProcessCreationParams creationParams = spawnData.getCreationParams();
        final Context context = spawnData.getContext();
        final boolean inSandbox = spawnData.isInSandbox();

        String packageName = getPackageNameFromCreationParams(context, creationParams, inSandbox);
        boolean bindAsExternalService =
                isServiceExternalFromCreationParams(creationParams, inSandbox);
        ChildConnectionAllocator allocator =
                getConnectionAllocator(context, packageName, inSandbox, bindAsExternalService);
        ChildProcessConnection connection =
                allocator.allocate(spawnData, deathCallback, queueIfNoneAvailable);
        sConnectionsToAllocatorMap.put(connection, allocator);
        return connection;
    }

    @VisibleForTesting
    static ChildProcessConnection allocateBoundConnection(ChildSpawnData spawnData,
            ChildProcessConnection.StartCallback startCallback, boolean queueIfNoneAvailable) {
        assert LauncherThread.runningOnLauncherThread();
        final Context context = spawnData.getContext();
        final boolean inSandbox = spawnData.isInSandbox();
        final ChildProcessCreationParams creationParams = spawnData.getCreationParams();

        ChildProcessConnection connection = allocateConnection(spawnData, queueIfNoneAvailable);
        if (connection != null) {
            boolean useStrongBinding = spawnData.isAlwaysInForeground();
            connection.start(useStrongBinding, startCallback);

            String packageName =
                    getPackageNameFromCreationParams(context, creationParams, inSandbox);
            boolean bindAsExternalService =
                    isServiceExternalFromCreationParams(creationParams, inSandbox);
            if (inSandbox
                    && !getConnectionAllocator(
                               context, packageName, true /* sandboxed */, bindAsExternalService)
                                .isFreeConnectionAvailable()) {
                // Proactively releases all the moderate bindings once all the sandboxed services
                // are allocated, which will be very likely to have some of them killed by OOM
                // killer.
                getBindingManager().releaseAllModerateBindings();
            }
        }
        return connection;
    }

    private static final long FREE_CONNECTION_DELAY_MILLIS = 1;

    private static void freeConnection(ChildProcessConnection connection) {
        assert LauncherThread.runningOnLauncherThread();

        if (sSpareSandboxedConnection != null
                && sSpareSandboxedConnection.onConnectionFreed(connection)) {
            // The spare connection died.
            sSpareSandboxedConnection = null;
        }

        // Freeing a service should be delayed. This is so that we avoid immediately reusing the
        // freed service (see http://crbug.com/164069): the framework might keep a service process
        // alive when it's been unbound for a short time. If a new connection to the same service
        // is bound at that point, the process is reused and bad things happen (mostly static
        // variables are set when we don't expect them to).
        final ChildProcessConnection conn = connection;
        LauncherThread.postDelayed(new Runnable() {
            @Override
            public void run() {
                ChildConnectionAllocator allocator = sConnectionsToAllocatorMap.remove(conn);
                assert allocator != null;
                final ChildSpawnData pendingSpawn = allocator.free(conn);
                if (pendingSpawn == null) {
                    String packageName = allocator.getPackageName();
                    if (!allocator.anyConnectionAllocated()
                            && sSandboxedChildConnectionAllocatorMap.get(packageName)
                                    == allocator) {
                        sSandboxedChildConnectionAllocatorMap.remove(packageName);
                    }
                } else {
                    LauncherThread.post(new Runnable() {
                        @Override
                        public void run() {
                            start(pendingSpawn.getContext(), pendingSpawn.getServiceBundle(),
                                    pendingSpawn.getConnectionBundle(),
                                    pendingSpawn.getLaunchCallback(),
                                    pendingSpawn.getChildProcessCallback(),
                                    pendingSpawn.isInSandbox(), pendingSpawn.isAlwaysInForeground(),
                                    pendingSpawn.getCreationParams());
                        }
                    });
                }
            }
        }, FREE_CONNECTION_DELAY_MILLIS);
    }

    // Map from pid to ChildService connection.
    private static Map<Integer, ChildProcessConnection> sServiceMap = new ConcurrentHashMap<>();

    // Manages oom bindings used to bind chind services. Lazily initialized by getBindingManager()
    private static BindingManager sBindingManager;

    // Whether the main application is currently brought to the foreground.
    private static boolean sApplicationInForeground = true;

    // Lazy initialize sBindingManager
    // TODO(boliu): This should be internal to content.
    @SuppressFBWarnings("LI_LAZY_INIT_STATIC") // Method is single thread.
    public static BindingManager getBindingManager() {
        assert LauncherThread.runningOnLauncherThread();
        if (sBindingManager == null) {
            sBindingManager = BindingManagerImpl.createBindingManager();
        }
        return sBindingManager;
    }

    @VisibleForTesting
    public static void setBindingManagerForTesting(BindingManager manager) {
        sBindingManager = manager;
    }

    /**
     * Called when the embedding application is sent to background.
     */
    public static void onSentToBackground() {
        assert ThreadUtils.runningOnUiThread();
        sApplicationInForeground = false;
        LauncherThread.post(new Runnable() {
            @Override
            public void run() {
                getBindingManager().onSentToBackground();
            }
        });
    }

    /**
     * Called when the embedding application is brought to foreground.
     */
    public static void onBroughtToForeground() {
        assert ThreadUtils.runningOnUiThread();
        sApplicationInForeground = true;
        LauncherThread.post(new Runnable() {
            @Override
            public void run() {
                getBindingManager().onBroughtToForeground();
            }
        });
    }

    /**
     * Returns whether the application is currently in the foreground.
     */
    static boolean isApplicationInForeground() {
        return sApplicationInForeground;
    }

    /**
     * Starts moderate binding management.
     * Note: WebAPKs and non WebAPKs share the same moderate binding pool, so the size of the
     * shared moderate binding pool is always set based on the number of sandboxes processes
     * used by Chrome.
     * @param context Android's context.
     */
    public static void startModerateBindingManagement(final Context context) {
        assert ThreadUtils.runningOnUiThread();
        LauncherThread.post(new Runnable() {
            @Override
            public void run() {
                boolean bindAsExternalService = isServiceExternalFromCreationParams(
                        ChildProcessCreationParams.getDefault(), true /* sandboxed */);
                ChildConnectionAllocator allocator = getConnectionAllocator(context,
                        context.getPackageName(), true /* sandboxed */, bindAsExternalService);
                getBindingManager().startModerateBindingManagement(
                        context, allocator.getNumberOfServices());
            }
        });
    }

    /**
     * Should be called early in startup so the work needed to spawn the child process can be done
     * in parallel to other startup work. Spare connection is created in sandboxed child process.
     * @param context the application context used for the connection.
     */
    public static void warmUp(final Context context) {
        assert ThreadUtils.runningOnUiThread();
        LauncherThread.post(new Runnable() {
            @Override
            public void run() {
                if (sSpareSandboxedConnection != null) return;
                ChildProcessCreationParams creationParams = ChildProcessCreationParams.getDefault();
                final boolean sandboxed = true;
                boolean bindToCallerCheck =
                        creationParams == null ? false : creationParams.getBindToCallerCheck();
                sSpareSandboxedConnection =
                        new SpareChildConnection(context, SANDBOXED_SPARE_CONNECTION_FATORY,
                                ChildProcessLauncherHelper.createServiceBundle(bindToCallerCheck),
                                sandboxed, false /* alwaysInForeground */, creationParams);
            }
        });
    }

    /**
     * Spawns and connects to a child process. It will not block, but will instead callback to
     * {@link #LaunchCallback} on the launcher thread when the connection is established on.
     *
     * @param context Context used to obtain the application context.
     * @param paramId Key used to retrieve ChildProcessCreationParams.
     * @param serviceBundle The Bundle passed in the intent used to bind to the service.
     * @param connectionBundle The Bundle passed in setupConnection call.
     * @param launchCallback Callback invoked when the connection is established.
     * @param childProcessCallback IBinder callback passed to the service.
     */
    @VisibleForTesting
    public static boolean start(final Context context, final Bundle serviceBundle,
            final Bundle connectionBundle, final LaunchCallback launchCallback,
            final IBinder childProcessCallback, final boolean inSandbox,
            final boolean alwaysInForeground, final ChildProcessCreationParams creationParams) {
        assert LauncherThread.runningOnLauncherThread();
        try {
            TraceEvent.begin("ChildProcessLauncher.start");

            ChildProcessConnection.StartCallback startCallback =
                    new ChildProcessConnection.StartCallback() {
                        @Override
                        public void onChildStarted() {}

                        @Override
                        public void onChildStartFailed() {
                            assert LauncherThread.runningOnLauncherThread();
                            Log.e(TAG, "ChildProcessConnection.start failed, trying again");
                            LauncherThread.post(new Runnable() {
                                @Override
                                public void run() {
                                    // The child process may already be bound to another client
                                    // (this can happen if multi-process WebView is used in more
                                    // than one process), so try starting the process again.
                                    // This connection that failed to start has not been freed,
                                    // so a new bound connection will be allocated.
                                    start(context, serviceBundle, connectionBundle, launchCallback,
                                            childProcessCallback, inSandbox, alwaysInForeground,
                                            creationParams);
                                }
                            });
                        }
                    };

            ChildProcessConnection allocatedConnection = null;
            // Try to use the spare connection if there's one.
            if (sSpareSandboxedConnection != null) {
                allocatedConnection = sSpareSandboxedConnection.getConnection(
                        context, inSandbox, alwaysInForeground, creationParams, startCallback);
                if (allocatedConnection != null) {
                    // We used the spare connection.
                    sSpareSandboxedConnection = null;
                }
            }

            if (allocatedConnection == null) {
                // No spare connection was available, create one.
                ChildSpawnData spawnData = new ChildSpawnData(context, serviceBundle,
                        connectionBundle, launchCallback, childProcessCallback, inSandbox,
                        alwaysInForeground, creationParams);
                allocatedConnection = allocateBoundConnection(
                        spawnData, startCallback, true /* queueIfNoneAvailable */);
                if (allocatedConnection == null) {
                    return false;
                }
            }
            boolean addToBindingmanager = inSandbox;
            triggerConnectionSetup(allocatedConnection, connectionBundle, childProcessCallback,
                    launchCallback, addToBindingmanager);
            return true;
        } finally {
            TraceEvent.end("ChildProcessLauncher.start");
        }
    }

    @VisibleForTesting
    static void triggerConnectionSetup(final ChildProcessConnection connection,
            Bundle connectionBundle, final IBinder childProcessCallback,
            final LaunchCallback launchCallback, final boolean addToBindingmanager) {
        assert LauncherThread.runningOnLauncherThread();
        Log.d(TAG, "Setting up connection to process, connection name=%s",
                connection.getServiceName());
        ChildProcessConnection.ConnectionCallback connectionCallback =
                new ChildProcessConnection.ConnectionCallback() {
                    @Override
                    public void onConnected(ChildProcessConnection connection) {
                        assert LauncherThread.runningOnLauncherThread();
                        if (connection != null) {
                            int pid = connection.getPid();
                            Log.d(TAG, "on connect callback, pid=%d", pid);
                            if (addToBindingmanager) {
                                getBindingManager().addNewConnection(pid, connection);
                            }
                            sServiceMap.put(pid, connection);
                        }
                        // If the connection fails and pid == 0, the Java-side cleanup was already
                        // handled by DeathCallback. We still have to call back to native for
                        // cleanup there.
                        if (launchCallback != null) { // Will be null in Java instrumentation tests.
                            launchCallback.onChildProcessStarted(connection);
                        }
                    }
                };

        connection.setupConnection(connectionBundle, childProcessCallback, connectionCallback);
    }

    /**
     * Terminates a child process. This may be called from any thread.
     *
     * @param pid The pid (process handle) of the service connection obtained from {@link #start}.
     */
    static void stop(int pid) {
        assert LauncherThread.runningOnLauncherThread();
        Log.d(TAG, "stopping child connection: pid=%d", pid);
        ChildProcessConnection connection = sServiceMap.remove(pid);
        if (connection == null) {
            // Can happen for single process.
            return;
        }
        getBindingManager().removeConnection(pid);
        connection.stop();
        freeConnection(connection);
    }

    public static int getNumberOfSandboxedServices(Context context, String packageName) {
        assert ThreadUtils.runningOnUiThread();
        if (sSandboxedServicesCountForTesting != -1) {
            return sSandboxedServicesCountForTesting;
        }
        return ChildConnectionAllocator.getNumberOfServices(
                context, packageName, NUM_SANDBOXED_SERVICES_KEY);
    }

    /** @return the count of services set up and working */
    @VisibleForTesting
    static int connectedServicesCountForTesting() {
        return sServiceMap.size();
    }

    @VisibleForTesting
    public static void setSandboxServicesSettingsForTesting(
            ChildConnectionAllocator.ConnectionFactory factory, int serviceCount,
            String serviceName) {
        sSandboxedServiceFactoryForTesting = factory;
        sSandboxedServicesCountForTesting = serviceCount;
        sSandboxedServicesNameForTesting = serviceName;
    }

    /**
     * Kills the child process for testing.
     * @return true iff the process was killed as expected
     */
    @VisibleForTesting
    public static boolean crashProcessForTesting(int pid) {
        if (sServiceMap.get(pid) == null) return false;

        try {
            sServiceMap.get(pid).crashServiceForTesting();
        } catch (RemoteException ex) {
            return false;
        }

        return true;
    }

    @VisibleForTesting
    public static Map<String, ChildConnectionAllocator> getSandboxedAllocatorMapForTesting() {
        return sSandboxedChildConnectionAllocatorMap;
    }
}

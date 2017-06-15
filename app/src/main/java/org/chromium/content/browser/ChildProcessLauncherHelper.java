// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.content.Context;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;

import org.chromium.base.ContextUtils;
import org.chromium.base.CpuFeatures;
import org.chromium.base.Log;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.base.library_loader.Linker;
import org.chromium.base.process_launcher.ChildProcessCreationParams;
import org.chromium.base.process_launcher.FileDescriptorInfo;
import org.chromium.content.app.ChromiumLinkerParams;
import org.chromium.content.common.ContentSwitches;

import java.io.IOException;

/**
 * This is the java counterpart to ChildProcessLauncherHelper. It is owned by native side and
 * has an explicit destroy method.
 * Each public or jni methods should have explicit documentation on what threads they are called.
 */
@JNINamespace("content::internal")
public class ChildProcessLauncherHelper {
    private static final String TAG = "ChildProcLH";

    // Represents an invalid process handle; same as base/process/process.h kNullProcessHandle.
    private static final int NULL_PROCESS_HANDLE = 0;

    // The IBinder provided to the created service.
    private final IBinder mIBinderCallback;

    // Note native pointer is only guaranteed live until nativeOnChildProcessStarted.
    private long mNativeChildProcessLauncherHelper;

    // The actual service connection. Set once we have connected to the service.
    private ChildProcessConnection mChildProcessConnection;

    @CalledByNative
    private static FileDescriptorInfo makeFdInfo(
            int id, int fd, boolean autoClose, long offset, long size) {
        assert LauncherThread.runningOnLauncherThread();
        ParcelFileDescriptor pFd;
        if (autoClose) {
            // Adopt the FD, it will be closed when we close the ParcelFileDescriptor.
            pFd = ParcelFileDescriptor.adoptFd(fd);
        } else {
            try {
                pFd = ParcelFileDescriptor.fromFd(fd);
            } catch (IOException e) {
                Log.e(TAG, "Invalid FD provided for process connection, aborting connection.", e);
                return null;
            }
        }
        return new FileDescriptorInfo(id, pFd, offset, size);
    }

    @VisibleForTesting
    @CalledByNative
    public static ChildProcessLauncherHelper createAndStart(long nativePointer, int paramId,
            final String[] commandLine, FileDescriptorInfo[] filesToBeMapped) {
        assert LauncherThread.runningOnLauncherThread();
        String processType =
                ContentSwitches.getSwitchValue(commandLine, ContentSwitches.SWITCH_PROCESS_TYPE);

        ChildProcessCreationParams params = ChildProcessCreationParams.get(paramId);
        if (paramId != ChildProcessCreationParams.DEFAULT_ID && params == null) {
            throw new RuntimeException("CreationParams id " + paramId + " not found");
        }

        Context context = ContextUtils.getApplicationContext();
        boolean sandboxed = true;
        boolean alwaysInForeground = false;
        if (!ContentSwitches.SWITCH_RENDERER_PROCESS.equals(processType)) {
            if (ContentSwitches.SWITCH_GPU_PROCESS.equals(processType)) {
                sandboxed = false;
                alwaysInForeground = true;
            } else {
                // We only support sandboxed utility processes now.
                assert ContentSwitches.SWITCH_UTILITY_PROCESS.equals(processType);
            }
        }

        ChildProcessLauncherHelper process_launcher =
                new ChildProcessLauncherHelper(nativePointer, processType);
        process_launcher.start(
                context, commandLine, filesToBeMapped, params, sandboxed, alwaysInForeground);
        return process_launcher;
    }

    private ChildProcessLauncherHelper(long nativePointer, String processType) {
        assert LauncherThread.runningOnLauncherThread();
        mNativeChildProcessLauncherHelper = nativePointer;
        mIBinderCallback = ContentSwitches.SWITCH_GPU_PROCESS.equals(processType)
                ? new GpuProcessCallback()
                : null;
        initLinker();
    }

    private void start(Context context, String[] commandLine,
            final FileDescriptorInfo[] filesToBeMapped, ChildProcessCreationParams params,
            boolean sandboxed, boolean alwaysInForeground) {
        boolean bindToCallerCheck = params == null ? false : params.getBindToCallerCheck();
        Bundle serviceBundle = createServiceBundle(bindToCallerCheck);
        onBeforeConnectionAllocated(serviceBundle);

        Bundle connectionBundle = createConnectionBundle(commandLine, filesToBeMapped);
        ChildProcessLauncher.start(context, serviceBundle,
                connectionBundle, new ChildProcessLauncher.LaunchCallback() {
                    @Override
                    public void onChildProcessStarted(ChildProcessConnection connection) {
                        mChildProcessConnection = connection;

                        // Proactively close the FDs rather than waiting for the GC to do it.
                        try {
                            for (FileDescriptorInfo fileInfo : filesToBeMapped) {
                                fileInfo.fd.close();
                            }
                        } catch (IOException ioe) {
                            Log.w(TAG, "Failed to close FD.", ioe);
                        }

                        if (mNativeChildProcessLauncherHelper != 0) {
                            nativeOnChildProcessStarted(
                                    mNativeChildProcessLauncherHelper, getPid());
                        }
                        mNativeChildProcessLauncherHelper = 0;
                    }
                }, getIBinderCallback(), sandboxed, alwaysInForeground, params);
    }

    private int getPid() {
        return mChildProcessConnection == null ? NULL_PROCESS_HANDLE
                                               : mChildProcessConnection.getPid();
    }

    // Called on client (UI or IO) thread.
    @CalledByNative
    private boolean isOomProtected() {
        // mChildProcessConnection is set on a different thread but does not change once it's been
        // set. So it is safe to test whether it's null from a different thread.
        if (mChildProcessConnection == null) {
            return false;
        }

        // We consider the process to be child protected if it has a strong or moderate binding and
        // the app is in the foreground.
        return ChildProcessLauncher.isApplicationInForeground()
                && !mChildProcessConnection.isWaivedBoundOnlyOrWasWhenDied();
    }

    @CalledByNative
    private void setInForeground(int pid, boolean foreground, boolean boostForPendingViews) {
        assert LauncherThread.runningOnLauncherThread();
        assert mChildProcessConnection != null;
        assert getPid() == pid;
        ChildProcessLauncher.getBindingManager().setPriority(pid, foreground, boostForPendingViews);
    }

    @CalledByNative
    private static void stop(int pid) {
        assert LauncherThread.runningOnLauncherThread();
        ChildProcessLauncher.stop(pid);
    }

    // Called on UI thread.
    @CalledByNative
    private static int getNumberOfRendererSlots() {
        final ChildProcessCreationParams params = ChildProcessCreationParams.getDefault();
        final Context context = ContextUtils.getApplicationContext();
        final String packageName = ChildProcessLauncher.getPackageNameFromCreationParams(
                context, params, true /* inSandbox */);
        try {
            return ChildProcessLauncher.getNumberOfSandboxedServices(context, packageName);
        } catch (RuntimeException e) {
            // Unittest packages do not declare services. Some tests require a realistic number
            // to test child process policies, so pick a high-ish number here.
            return 65535;
        }
    }

    // Can be called on a number of threads, including launcher, and binder.
    private static native void nativeOnChildProcessStarted(
            long nativeChildProcessLauncherHelper, int pid);

    private static boolean sLinkerInitialized;
    private static long sLinkerLoadAddress;
    @VisibleForTesting
    static void initLinker() {
        assert LauncherThread.runningOnLauncherThread();
        if (sLinkerInitialized) return;
        if (Linker.isUsed()) {
            sLinkerLoadAddress = Linker.getInstance().getBaseLoadAddress();
            if (sLinkerLoadAddress == 0) {
                Log.i(TAG, "Shared RELRO support disabled!");
            }
        }
        sLinkerInitialized = true;
    }

    private static ChromiumLinkerParams getLinkerParamsForNewConnection() {
        assert LauncherThread.runningOnLauncherThread();
        assert sLinkerInitialized;

        if (sLinkerLoadAddress == 0) return null;

        // Always wait for the shared RELROs in service processes.
        final boolean waitForSharedRelros = true;
        if (Linker.areTestsEnabled()) {
            Linker linker = Linker.getInstance();
            return new ChromiumLinkerParams(sLinkerLoadAddress, waitForSharedRelros,
                    linker.getTestRunnerClassNameForTesting(),
                    linker.getImplementationForTesting());
        } else {
            return new ChromiumLinkerParams(sLinkerLoadAddress, waitForSharedRelros);
        }
    }

    /**
     * Creates the common bundle to be passed to child processes through the service binding intent.
     * If the service gets recreated by the framework the intent will be reused, so these parameters
     * should be common to all processes of that type.
     *
     * @param commandLine Command line params to be passed to the service.
     * @param linkerParams Linker params to start the service.
     */
    // TODO(jcivelli): make private once warmup connection code is move from ChildProcessLauncher to
    // this class and remove initLinker call.
    static Bundle createServiceBundle(boolean bindToCallerCheck) {
        initLinker();
        Bundle bundle = new Bundle();
        bundle.putBoolean(ChildProcessConstants.EXTRA_BIND_TO_CALLER, bindToCallerCheck);
        bundle.putParcelable(
                ChildProcessConstants.EXTRA_LINKER_PARAMS, getLinkerParamsForNewConnection());
        return bundle;
    }

    @VisibleForTesting
    public static Bundle createConnectionBundle(
            String[] commandLine, FileDescriptorInfo[] filesToBeMapped) {
        assert sLinkerInitialized;

        Bundle bundle = new Bundle();
        bundle.putStringArray(ChildProcessConstants.EXTRA_COMMAND_LINE, commandLine);
        bundle.putParcelableArray(ChildProcessConstants.EXTRA_FILES, filesToBeMapped);
        // content specific parameters.
        bundle.putInt(ChildProcessConstants.EXTRA_CPU_COUNT, CpuFeatures.getCount());
        bundle.putLong(ChildProcessConstants.EXTRA_CPU_FEATURES, CpuFeatures.getMask());
        bundle.putBundle(Linker.EXTRA_LINKER_SHARED_RELROS, Linker.getInstance().getSharedRelros());
        return bundle;
    }

    // Below are methods that will eventually be moved to a content delegate class.

    private void onBeforeConnectionAllocated(Bundle commonParameters) {
        // TODO(jcivelli): move createServiceBundle in there.
    }

    private IBinder getIBinderCallback() {
        return mIBinderCallback;
    }

    // Testing only related methods.
    @VisibleForTesting
    public static ChildProcessLauncherHelper createAndStartForTesting(long nativePointer,
            String[] commandLine, FileDescriptorInfo[] filesToBeMapped,
            ChildProcessCreationParams creationParams, boolean sandboxed,
            boolean alwaysInForeground) {
        String processType =
                ContentSwitches.getSwitchValue(commandLine, ContentSwitches.SWITCH_PROCESS_TYPE);
        ChildProcessLauncherHelper launcherHelper =
                new ChildProcessLauncherHelper(nativePointer, processType);
        launcherHelper.start(ContextUtils.getApplicationContext(), commandLine, filesToBeMapped,
                creationParams, sandboxed, alwaysInForeground);
        return launcherHelper;
    }

    @VisibleForTesting
    public ChildProcessConnection getChildProcessConnection() {
        return mChildProcessConnection;
    }
}

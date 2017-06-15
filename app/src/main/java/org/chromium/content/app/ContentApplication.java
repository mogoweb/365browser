// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.app;

import android.os.Looper;
import android.os.MessageQueue;

import org.chromium.base.BaseChromiumApplication;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.MainDex;
import org.chromium.base.library_loader.LibraryLoader;
import org.chromium.content.browser.TracingControllerAndroid;

/**
 * Basic application functionality that should be shared among all browser applications
 * based on the content layer.
 */
@MainDex
public abstract class ContentApplication extends BaseChromiumApplication {
    private TracingControllerAndroid mTracingController;
    private boolean mLibraryDependenciesInitialized;

    public ContentApplication() {
        this(true /* mShouldInitializeApplicationStatusTracking */);
    }

    protected ContentApplication(boolean shouldInitializeApplicationStatusTracking) {
        super(shouldInitializeApplicationStatusTracking);
    }

    TracingControllerAndroid getTracingController() {
        if (mTracingController == null) {
            mTracingController = new TracingControllerAndroid(this);
        }
        return mTracingController;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // Delay TracingControllerAndroid.registerReceiver() until the main loop is idle.
        Looper.myQueue().addIdleHandler(new MessageQueue.IdleHandler() {
            @Override
            public boolean queueIdle() {
                // Will retry if the native library has not been initialized.
                if (!LibraryLoader.isInitialized()) return true;

                try {
                    getTracingController().registerReceiver(ContentApplication.this);
                } catch (SecurityException e) {
                    // Happens if the process is isolated. Ignore.
                }
                // Remove the idle handler.
                return false;
            }
        });

        mLibraryDependenciesInitialized = true;
    }

    /**
     * @return Whether the library dependencies have been initialized and it is safe to issue
     *         requests to load the native library.
     */
    @VisibleForTesting
    public boolean areLibraryDependenciesInitialized() {
        return mLibraryDependenciesInitialized;
    }

    /**
     * For emulated process environment only. On a production device, the application process is
     * simply killed without calling this method. We don't need to unregister the broadcast
     * receiver in the latter case.
     */
    @Override
    public void onTerminate() {
        try {
            getTracingController().unregisterReceiver(this);
            getTracingController().destroy();
        } catch (SecurityException e) {
            // Happens if the process is isolated. Ignore.
        }

        super.onTerminate();
    }
}

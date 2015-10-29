// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.precache;

import android.content.Context;

import org.chromium.base.Log;
import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.browser.preferences.privacy.PrivacyPreferencesManager;
import org.chromium.chrome.browser.sync.ProfileSyncService;

/** Class that interacts with the PrecacheManager to control precache cycles. */
public abstract class PrecacheLauncher {
    private static final String TAG = "cr.Precache";

    /** Pointer to the native PrecacheLauncher object. Set to 0 when uninitialized. */
    private long mNativePrecacheLauncher;

    /** Destroy the native PrecacheLauncher, releasing the memory that it was using. */
    public void destroy() {
        if (mNativePrecacheLauncher != 0) {
            nativeDestroy(mNativePrecacheLauncher);
            mNativePrecacheLauncher = 0;
        }
    }

    /** Starts a precache cycle. */
    public void start() {
        // Lazily initialize the native PrecacheLauncher.
        if (mNativePrecacheLauncher == 0) {
            mNativePrecacheLauncher = nativeInit();
        }
        nativeStart(mNativePrecacheLauncher);
    }

    /** Cancel the precache cycle if one is ongoing. */
    public void cancel() {
        // Lazily initialize the native PrecacheLauncher.
        if (mNativePrecacheLauncher == 0) {
            mNativePrecacheLauncher = nativeInit();
        }
        nativeCancel(mNativePrecacheLauncher);
    }

    /**
     * Called when a precache cycle completes.
     *
     * @param tryAgainSoon True iff the precache failed to start due to a transient error and should
     * be attempted again soon.
     */
    protected abstract void onPrecacheCompleted(boolean tryAgainSoon);

    /**
     * Called by native code when the precache cycle completes. This method exists because an
     * abstract method cannot be directly called from native.
     *
     * @param tryAgainSoon True iff the precache failed to start due to a transient error and should
     * be attempted again soon.
     */
    @CalledByNative
    private void onPrecacheCompletedCallback(boolean tryAgainSoon) {
        onPrecacheCompleted(tryAgainSoon);
    }

    /**
     * Updates the PrecacheServiceLauncher with whether conditions are right for precaching. All of
     * the following must be true:
     *
     * <ul>
     *   <li>The predictive network actions preference is enabled.</li>
     *   <li>The current network type is suitable for predictive network actions.</li>
     *   <li>Sync is enabled for sessions and it is not encrypted with a secondary passphrase.</li>
     *   <li>Either the Precache field trial or the precache commandline flag is enabled.</li>
     * </ul>
     *
     * This should be called only after the sync backend has been initialized. Must be called on the
     * UI thread.
     *
     * @param context The application context.
     */
    private void updateEnabledSync(Context context) {
        PrivacyPreferencesManager privacyPreferencesManager =
                PrivacyPreferencesManager.getInstance(context);

        // privacyPreferencesManager.shouldPrerender() and nativeShouldRun() can only be executed on
        // the UI thread.
        PrecacheServiceLauncher.setIsPrecachingEnabled(context.getApplicationContext(),
                privacyPreferencesManager.shouldPrerender() && nativeShouldRun());
        Log.v(TAG, "updateEnabledSync complete");
    }

    /**
     * If precaching is enabled, then allow the PrecacheService to be launched and signal Chrome
     * when conditions are right to start precaching. If precaching is disabled, prevent the
     * PrecacheService from ever starting.
     *
     * @param context Any context within the application.
     */
    @VisibleForTesting
    void updateEnabled(final Context context) {
        Log.v(TAG, "updateEnabled starting");
        ThreadUtils.postOnUiThread(new Runnable() {
            @Override
            public void run() {
                final ProfileSyncService sync = ProfileSyncService.get();

                if (mListener == null) {
                    mListener = new ProfileSyncService.SyncStateChangedListener() {
                        public void syncStateChanged() {
                            if (sync.isSyncInitialized()) {
                                updateEnabledSync(context);
                            }
                        }
                    };
                    sync.addSyncStateChangedListener(mListener);
                }

                // Call the listener once, in case the sync backend is already initialized.
                mListener.syncStateChanged();
                Log.v(TAG, "updateEnabled complete");
            }
        });
    }

    /**
     * If precaching is enabled, then allow the PrecacheService to be launched and signal Chrome
     * when conditions are right to start precaching. If precaching is disabled, prevent the
     * PrecacheService from ever starting.
     *
     * @param context Any context within the application.
     */
    public static void updatePrecachingEnabled(final Context context) {
        sInstance.updateEnabled(context);
    }

    private static final PrecacheLauncher sInstance = new PrecacheLauncher() {
        @Override
        protected void onPrecacheCompleted(boolean tryAgainSoon) {}
    };

    // Initialized by updateEnabled to call updateEnabledSync when the sync
    // backend is initialized. Only accessed on the UI thread.
    private ProfileSyncService.SyncStateChangedListener mListener = null;

    private native long nativeInit();
    private native void nativeDestroy(long nativePrecacheLauncher);
    private native void nativeStart(long nativePrecacheLauncher);
    private native void nativeCancel(long nativePrecacheLauncher);

    @VisibleForTesting native boolean nativeShouldRun();
}

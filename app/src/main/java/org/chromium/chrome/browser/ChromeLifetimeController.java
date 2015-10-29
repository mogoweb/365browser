// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;

import org.chromium.base.ActivityState;
import org.chromium.base.ApplicationStatus;
import org.chromium.base.Log;

import java.lang.ref.WeakReference;

/**
 * Handles killing and potentially restarting Chrome's main Browser process.  Note that this class
 * relies on main Chrome activities to properly call {@link Activity#finish()} on themselves so that
 * it will be notified that all {@link Activity}s under this {@link Application} have been
 * destroyed.
 */
class ChromeLifetimeController implements ApplicationLifetime.Observer,
        ApplicationStatus.ActivityStateListener {
    private static final String TAG = "cr.LifetimeController";

    // The amount of time to wait for Chrome to destroy all the activities.
    private static final long WATCHDOG_DELAY = 1000;

    private final Context mContext;
    private boolean mRestartChromeOnDestroy;
    private int mRemainingActivitiesCount = 0;
    private final Handler mHandler;

    /**
     * Creates a {@link ChromeLifetimeController} instance.
     * @param context A {@link Context} instance.  The application context will be saved from this
     *                one.
     */
    public ChromeLifetimeController(Context context) {
        mContext = context.getApplicationContext();
        ApplicationLifetime.addObserver(this);
        mHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void onTerminate(boolean restart) {
        mRestartChromeOnDestroy = restart;

        // We've called terminate twice, just wait for the first call to take effect.
        if (mRemainingActivitiesCount > 0) {
            Log.w(TAG, "onTerminate called twice");
            return;
        }

        for (WeakReference<Activity> weakActivity : ApplicationStatus.getRunningActivities()) {
            Activity activity = weakActivity.get();
            if (activity != null) {
                ApplicationStatus.registerStateListenerForActivity(this, activity);
                mRemainingActivitiesCount++;
                activity.finish();
            }
        }

        // Post a watchdog -- if Android is taking a long time to call onDestroy, kill the process.
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mRemainingActivitiesCount > 0) {
                    destroyProcess();
                }
            }
        }, WATCHDOG_DELAY);
    }


    @Override
    public void onActivityStateChange(Activity activity, int newState) {
        assert mRemainingActivitiesCount > 0;
        if (newState == ActivityState.DESTROYED) {
            mRemainingActivitiesCount--;
            if (mRemainingActivitiesCount == 0) {
                destroyProcess();
            }
        }

    }

    private void destroyProcess() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mRestartChromeOnDestroy) {
                    Log.w(TAG, "Launching BrowserRestartActivity to restart Chrome.");
                    Context context = ApplicationStatus.getApplicationContext();
                    Intent intent = new Intent();
                    intent.setClassName(
                            context.getPackageName(), BrowserRestartActivity.class.getName());
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.putExtra(BrowserRestartActivity.EXTRA_PID, Process.myPid());
                    context.startActivity(intent);
                } else {
                    Log.w(TAG, "Killing process directly.");
                    Process.killProcess(Process.myPid());
                }
                mRestartChromeOnDestroy = false;
            }
        });
    }
}

// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.content.Context;
import android.os.Bundle;
import android.os.IBinder;

import org.chromium.base.process_launcher.ChildProcessCreationParams;
import org.chromium.content.browser.ChildProcessLauncher.LaunchCallback;

/** Contains the information necessary to start a child process. */
class ChildSpawnData {
    private final Context mContext;
    private final Bundle mServiceBundle;
    private final Bundle mConnectionBundle;
    private final LaunchCallback mLaunchCallback;
    private final IBinder mChildProcessCallback;
    private final boolean mInSandbox;
    private final boolean mAlwaysInForeground;
    private final ChildProcessCreationParams mCreationParams;

    ChildSpawnData(Context context, Bundle serviceBundle, Bundle connectionBundle,
            LaunchCallback launchCallback, IBinder childProcessCallback, boolean inSandbox,
            boolean alwaysInForeground, ChildProcessCreationParams creationParams) {
        mContext = context;
        mServiceBundle = serviceBundle;
        mConnectionBundle = connectionBundle;
        mLaunchCallback = launchCallback;
        mChildProcessCallback = childProcessCallback;
        mInSandbox = inSandbox;
        mAlwaysInForeground = alwaysInForeground;
        mCreationParams = creationParams;
    }

    Context getContext() {
        return mContext;
    }

    Bundle getServiceBundle() {
        return mServiceBundle;
    }

    Bundle getConnectionBundle() {
        return mConnectionBundle;
    }

    LaunchCallback getLaunchCallback() {
        return mLaunchCallback;
    }

    IBinder getChildProcessCallback() {
        return mChildProcessCallback;
    }

    boolean isInSandbox() {
        return mInSandbox;
    }

    boolean isAlwaysInForeground() {
        return mAlwaysInForeground;
    }

    ChildProcessCreationParams getCreationParams() {
        return mCreationParams;
    }
}

// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.app;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import org.chromium.base.annotations.JNINamespace;
import org.chromium.content.browser.ChildProcessLauncher;

/**
 * This is the base class for child services; the [Non]SandboxedProcessService0, 1.. etc
 * subclasses provide the concrete service entry points, to enable the browser to connect
 * to more than one distinct process (i.e. one process per service number, up to limit of N).
 * The embedding application must declare these service instances in the application section
 * of its AndroidManifest.xml, for example with N entries of the form:-
 *     <service android:name="org.chromium.content.app.[Non]SandboxedProcessServiceX"
 *              android:process=":[non]sandboxed_processX" />
 * for X in 0...N-1 (where N is {@link ChildProcessLauncher#MAX_REGISTERED_SERVICES})
 */
@JNINamespace("content")
public class ChildProcessService extends Service {
    private final ChildProcessServiceImpl mChildProcessServiceImpl = new ChildProcessServiceImpl();

    @Override
    public void onCreate() {
        super.onCreate();
        mChildProcessServiceImpl.create(getApplicationContext(),
                getApplicationContext());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mChildProcessServiceImpl.destroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // We call stopSelf() to request that this service be stopped as soon as the client
        // unbinds. Otherwise the system may keep it around and available for a reconnect. The
        // child processes do not currently support reconnect; they must be initialized from
        // scratch every time.
        stopSelf();
        return mChildProcessServiceImpl.bind(intent, -1);
    }
}

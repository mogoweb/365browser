// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Process;

import org.chromium.base.ApplicationStatus;
import org.chromium.chrome.browser.util.IntentUtils;

/**
 * Activity that kills and restarts Chrome, then immediately kills itself.
 * Works around an Android framework issue for alarms set via the AlarmManager: crbug.com/515919.
 */
public class BrowserRestartActivity extends Activity {
    static final String EXTRA_PID = "org.chromium.chrome.browser.BrowserRestartActivity.pid";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int pid = IntentUtils.safeGetIntExtra(getIntent(), BrowserRestartActivity.EXTRA_PID, -1);
        if (pid != -1) {
            Process.killProcess(pid);

            // Restart Chrome.
            Context context = ApplicationStatus.getApplicationContext();
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setPackage(context.getPackageName());
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }

        finish();
        Process.killProcess(Process.myPid());
    }
}

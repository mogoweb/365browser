// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

import org.chromium.base.ActivityState;
import org.chromium.base.ApplicationStatus;
import org.chromium.base.CommandLineInitUtil;
import org.chromium.base.ContextUtils;
import org.chromium.base.ThreadUtils;
import org.chromium.base.TraceEvent;
import org.chromium.base.annotations.MainDex;
import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.base.library_loader.ProcessInitException;
import org.chromium.chrome.browser.document.DocumentActivity;
import org.chromium.chrome.browser.document.IncognitoDocumentActivity;
import org.chromium.chrome.browser.init.InvalidStartupDialog;
import org.chromium.chrome.browser.metrics.UmaUtils;
import org.chromium.chrome.browser.tabmodel.document.ActivityDelegateImpl;
import org.chromium.chrome.browser.tabmodel.document.DocumentTabModelSelector;
import org.chromium.chrome.browser.tabmodel.document.StorageDelegate;
import org.chromium.chrome.browser.tabmodel.document.TabDelegate;
import org.chromium.content.app.ContentApplication;

/**
 * Basic application functionality that should be shared among all browser applications that use
 * chrome layer.
 */
@MainDex
public class ChromeApplication extends ContentApplication {
    public static final String COMMAND_LINE_FILE = "chrome-command-line";
    private static final String TAG = "ChromiumApplication";
    private static final String PREF_BOOT_TIMESTAMP =
            "com.google.android.apps.chrome.ChromeMobileApplication.BOOT_TIMESTAMP";
    private static final long BOOT_TIMESTAMP_MARGIN_MS = 1000;

    private static DocumentTabModelSelector sDocumentTabModelSelector;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        ContextUtils.initApplicationContext(this);
    }

    /**
     * This is called once per ChromeApplication instance, which get created per process
     * (browser OR renderer).  Don't stick anything in here that shouldn't be called multiple times
     * during Chrome's lifetime.
     */
    @Override
    public void onCreate() {
        UmaUtils.recordMainEntryPointTime();
        initCommandLine();
        TraceEvent.maybeEnableEarlyTracing();
        TraceEvent.begin("ChromeApplication.onCreate");

        super.onCreate();

        TraceEvent.end("ChromeApplication.onCreate");
    }

    /**
     * Shows an error dialog following a startup error, and then exits the application.
     * @param e The exception reported by Chrome initialization.
     */
    public static void reportStartupErrorAndExit(final ProcessInitException e) {
        Activity activity = ApplicationStatus.getLastTrackedFocusedActivity();
        if (ApplicationStatus.getStateForActivity(activity) == ActivityState.DESTROYED) {
            return;
        }
        InvalidStartupDialog.show(activity, e.getErrorCode());
    }

    @Override
    public void initCommandLine() {
        CommandLineInitUtil.initCommandLine(this, COMMAND_LINE_FILE);
    }

    /**
     * @return The user agent string of Chrome.
     */
    public static String getBrowserUserAgent() {
        return nativeGetBrowserUserAgent();
    }

    /**
     * The host activity should call this during its onPause() handler to ensure
     * all state is saved when the app is suspended.  Calling ChromiumApplication.onStop() does
     * this for you.
     */
    public static void flushPersistentData() {
        try {
            TraceEvent.begin("ChromiumApplication.flushPersistentData");
            nativeFlushPersistentData();
        } finally {
            TraceEvent.end("ChromiumApplication.flushPersistentData");
        }
    }

    /**
     * Removes all session cookies (cookies with no expiration date) after device reboots.
     * This function will incorrectly clear cookies when Daylight Savings Time changes the clock.
     * Without a way to get a monotonically increasing system clock, the boot timestamp will be off
     * by one hour.  However, this should only happen at most once when the clock changes since the
     * updated timestamp is immediately saved.
     */
    public static void removeSessionCookies() {
        long lastKnownBootTimestamp =
                ContextUtils.getAppSharedPreferences().getLong(PREF_BOOT_TIMESTAMP, 0);
        long bootTimestamp = System.currentTimeMillis() - SystemClock.uptimeMillis();
        long difference = bootTimestamp - lastKnownBootTimestamp;

        // Allow some leeway to account for fractions of milliseconds.
        if (Math.abs(difference) > BOOT_TIMESTAMP_MARGIN_MS) {
            nativeRemoveSessionCookies();

            SharedPreferences prefs = ContextUtils.getAppSharedPreferences();
            SharedPreferences.Editor editor = prefs.edit();
            editor.putLong(PREF_BOOT_TIMESTAMP, bootTimestamp);
            editor.apply();
        }
    }

    private static native void nativeRemoveSessionCookies();
    private static native String nativeGetBrowserUserAgent();
    private static native void nativeFlushPersistentData();

    /**
     * Returns the singleton instance of the DocumentTabModelSelector.
     * TODO(dfalcantara): Find a better place for this once we differentiate between activity and
     *                    application-level TabModelSelectors.
     * @return The DocumentTabModelSelector for the application.
     */
    @SuppressFBWarnings("LI_LAZY_INIT_STATIC")
    public static DocumentTabModelSelector getDocumentTabModelSelector() {
        ThreadUtils.assertOnUiThread();
        if (sDocumentTabModelSelector == null) {
            ActivityDelegateImpl activityDelegate = new ActivityDelegateImpl(
                    DocumentActivity.class, IncognitoDocumentActivity.class);
            sDocumentTabModelSelector = new DocumentTabModelSelector(activityDelegate,
                    new StorageDelegate(), new TabDelegate(false), new TabDelegate(true));
        }
        return sDocumentTabModelSelector;
    }
}

// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.metrics;

import org.chromium.base.metrics.RecordHistogram;
import org.chromium.webapk.lib.common.WebApkConstants;

import java.util.concurrent.TimeUnit;

/**
 * Centralizes UMA data collection for WebAPKs. NOTE: Histogram names and values are defined in
 * tools/metrics/histograms/histograms.xml. Please update that file if any change is made.
 */
public class WebApkUma {
    // This enum is used to back UMA histograms, and should therefore be treated as append-only.
    public static final int UPDATE_REQUEST_SENT_FIRST_TRY = 0;
    public static final int UPDATE_REQUEST_SENT_ONSTOP = 1;
    public static final int UPDATE_REQUEST_SENT_WHILE_WEBAPK_IN_FOREGROUND = 2;
    public static final int UPDATE_REQUEST_SENT_MAX = 3;

    // This enum is used to back UMA histograms, and should therefore be treated as append-only.
    // The queued request times shouldn't exceed three.
    public static final int UPDATE_REQUEST_QUEUED_ONCE = 0;
    public static final int UPDATE_REQUEST_QUEUED_TWICE = 1;
    public static final int UPDATE_REQUEST_QUEUED_THREE_TIMES = 2;
    public static final int UPDATE_REQUEST_QUEUED_MAX = 3;

    // This enum is used to back UMA histograms, and should therefore be treated as append-only.
    public static final int GOOGLE_PLAY_INSTALL_SUCCESS = 0;
    public static final int GOOGLE_PLAY_INSTALL_FAILED_NO_DELEGATE = 1;
    public static final int GOOGLE_PLAY_INSTALL_FAILED_TO_CONNECT_TO_SERVICE = 2;
    public static final int GOOGLE_PLAY_INSTALL_FAILED_CALLER_VERIFICATION_FAILURE = 3;
    public static final int GOOGLE_PLAY_INSTALL_FAILED_POLICY_VIOLATION = 4;
    public static final int GOOGLE_PLAY_INSTALL_FAILED_API_DISABLED = 5;
    public static final int GOOGLE_PLAY_INSTALL_FAILED_REQUEST_FAILED = 6;
    public static final int GOOGLE_PLAY_INSTALL_FAILED_DOWNLOAD_CANCELLED = 7;
    public static final int GOOGLE_PLAY_INSTALL_FAILED_DOWNLOAD_ERROR = 8;
    public static final int GOOGLE_PLAY_INSTALL_FAILED_INSTALL_ERROR = 9;
    public static final int GOOGLE_PLAY_INSTALL_FAILED_INSTALL_TIMEOUT = 10;
    public static final int GOOGLE_PLAY_INSTALL_RESULT_MAX = 11;

    public static final String HISTOGRAM_UPDATE_REQUEST_SENT =
            "WebApk.Update.RequestSent";

    public static final String HISTOGRAM_UPDATE_REQUEST_QUEUED = "WebApk.Update.RequestQueued";

    private static final int WEBAPK_OPEN_MAX = 3;
    public static final int WEBAPK_OPEN_LAUNCH_SUCCESS = 0;
    public static final int WEBAPK_OPEN_NO_LAUNCH_INTENT = 1;
    public static final int WEBAPK_OPEN_ACTIVITY_NOT_FOUND = 2;

    /**
     * Records the time point when a request to update a WebAPK is sent to the WebAPK Server.
     * @param type representing when the update request is sent to the WebAPK server.
     */
    public static void recordUpdateRequestSent(int type) {
        assert type >= 0 && type < UPDATE_REQUEST_SENT_MAX;
        RecordHistogram.recordEnumeratedHistogram(HISTOGRAM_UPDATE_REQUEST_SENT,
                type, UPDATE_REQUEST_SENT_MAX);
    }

    /**
     * Records the times that an update request has been queued once, twice and three times before
     * sending to WebAPK server.
     * @param times representing the times that an update has been queued.
     */
    public static void recordUpdateRequestQueued(int times) {
        RecordHistogram.recordEnumeratedHistogram(HISTOGRAM_UPDATE_REQUEST_QUEUED, times,
                UPDATE_REQUEST_QUEUED_MAX);
    }

    /**
     * When a user presses on the "Open WebAPK" menu item, this records whether the WebAPK was
     * opened successfully.
     * @param type Result of trying to open WebAPK.
     */
    public static void recordWebApkOpenAttempt(int type) {
        assert type >= 0 && type < WEBAPK_OPEN_MAX;
        RecordHistogram.recordEnumeratedHistogram("WebApk.OpenFromMenu", type, WEBAPK_OPEN_MAX);
    }

    /**
     * Records whether installing a WebAPK from Google Play succeeded. If not, records the reason
     * that the install failed.
     */
    public static void recordGooglePlayInstallResult(int result) {
        assert result >= 0 && result < GOOGLE_PLAY_INSTALL_RESULT_MAX;
        RecordHistogram.recordEnumeratedHistogram(
                "WebApk.Install.GooglePlayInstallResult", result, GOOGLE_PLAY_INSTALL_RESULT_MAX);
    }

    /**
     * Records whether updating a WebAPK from Google Play succeeded. If not, records the reason
     * that the update failed.
     */
    public static void recordGooglePlayUpdateResult(int result) {
        assert result >= 0 && result < GOOGLE_PLAY_INSTALL_RESULT_MAX;
        RecordHistogram.recordEnumeratedHistogram(
                "WebApk.Update.GooglePlayUpdateResult", result, GOOGLE_PLAY_INSTALL_RESULT_MAX);
    }

    /** Records the duration of a WebAPK session (from launch/foreground to background). */
    public static void recordWebApkSessionDuration(long duration) {
        RecordHistogram.recordLongTimesHistogram(
                "WebApk.Session.TotalDuration", duration, TimeUnit.MILLISECONDS);
    }

    /** Records the amount of time that it takes to bind to the play install service. */
    public static void recordGooglePlayBindDuration(long durationMs) {
        RecordHistogram.recordTimesHistogram(
                "WebApk.Install.GooglePlayBindDuration", durationMs, TimeUnit.MILLISECONDS);
    }

    /** Records the current Shell APK version. */
    public static void recordShellApkVersion(int shellApkVersion, String packageName) {
        String name = packageName.startsWith(WebApkConstants.WEBAPK_PACKAGE_PREFIX)
                ? "WebApk.ShellApkVersion.BrowserApk"
                : "WebApk.ShellApkVersion.UnboundApk";
        RecordHistogram.recordSparseSlowlyHistogram(name, shellApkVersion);
    }
}

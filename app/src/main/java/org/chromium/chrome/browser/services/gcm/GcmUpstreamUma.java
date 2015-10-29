// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.services.gcm;

import android.content.Context;

import org.chromium.base.ThreadUtils;
import org.chromium.base.library_loader.LibraryProcessType;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.content.browser.BrowserStartupController;
import org.chromium.content.browser.BrowserStartupController.StartupCallback;

/**
 * Helper Class for Invalidations GCM Upstream UMA Collection.
 */
public class GcmUpstreamUma {
    // Values for the "Invalidations.GCMUpstreamRequest" UMA histogram. The list is append-only.
    public static final int UMA_SUCCESS = 0;
    public static final int UMA_SIZE_LIMIT_EXCEEDED = 1;
    public static final int UMA_TOKEN_REQUEST_FAILED = 2;
    public static final int UMA_SEND_FAILED = 3;
    public static final int UMA_MAX = 4;

    public static void recordHistogram(final Context context, final int value) {
        ThreadUtils.postOnUiThread(new Runnable() {
            @Override
            public void run() {
                BrowserStartupController.get(context, LibraryProcessType.PROCESS_BROWSER)
                        .addStartupCompletedObserver(
                                new StartupCallback() {
                                    @Override
                                    public void onSuccess(boolean alreadyStarted) {
                                        RecordHistogram.recordEnumeratedHistogram(
                                                "Invalidations.GCMUpstreamRequest", value, UMA_MAX);
                                    }

                                    @Override
                                    public void onFailure() {
                                        // Startup failed.
                                    }
                                });
            }
        });
    }
}


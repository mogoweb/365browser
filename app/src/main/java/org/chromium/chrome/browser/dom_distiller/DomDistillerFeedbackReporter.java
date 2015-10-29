// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.dom_distiller;

import android.app.Activity;

import org.chromium.base.ThreadUtils;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.chrome.browser.ChromeApplication;
import org.chromium.chrome.browser.feedback.FeedbackCollector;
import org.chromium.chrome.browser.feedback.FeedbackReporter;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.ui.base.WindowAndroid;

/**
 * Java implementation of dom_distiller::android::ExternalFeedbackReporterAndroid.
 */
@JNINamespace("dom_distiller::android")
public final class DomDistillerFeedbackReporter {
    private static final String DISTILLATION_QUALITY_KEY = "Distillation quality";
    private static final String DISTILLATION_QUALITY_GOOD = "good";
    private static final String DISTILLATION_QUALITY_BAD = "bad";

    private static FeedbackReporter sFeedbackReporter;

    /**
     * A static method for native code to call to call the external feedback form.
     * @param window WindowAndroid object to get an activity from.
     * @param url The URL to report feedback for.
     * @param good True if the feedback is good and false if not.
     */
    @CalledByNative
    public static void reportFeedbackWithWindow(
            WindowAndroid window, String url, final boolean good) {
        ThreadUtils.assertOnUiThread();
        Activity activity = window.getActivity().get();
        if (sFeedbackReporter == null) {
            ChromeApplication application = (ChromeApplication) activity.getApplication();
            sFeedbackReporter = application.createFeedbackReporter();
        }
        FeedbackCollector.create(activity, Profile.getLastUsedProfile(), url,
                new FeedbackCollector.FeedbackResult() {
                    @Override
                    public void onResult(FeedbackCollector collector) {
                        String quality =
                                good ? DISTILLATION_QUALITY_GOOD : DISTILLATION_QUALITY_BAD;
                        collector.add(DISTILLATION_QUALITY_KEY, quality);
                        sFeedbackReporter.reportFeedback(collector);
                    }
                });
    }

    private DomDistillerFeedbackReporter() {}
}

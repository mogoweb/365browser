// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.dom_distiller;

import android.app.Activity;

/**
 * Provides a way of reporting feedback to an external feedback service.
 */
public interface ExternalFeedbackReporter {
    /**
     * Records feedback for the distilled content.
     *
     * @param activity the activity to take a screenshot of.
     * @param url the URL to report feedback for.
     * @param good whether the perceived quality of the distillation of a web page was good.
     */
    void reportFeedback(Activity activity, String url, boolean good);
}

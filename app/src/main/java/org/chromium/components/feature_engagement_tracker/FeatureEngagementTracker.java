// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.components.feature_engagement_tracker;

import android.support.annotation.CheckResult;

import org.chromium.base.Callback;

/**
 * FeatureEngagementTracker is the Java representation of a native FeatureEngagementTracker object.
 * It is owned by the native BrowserContext.
 *
 * FeatureEngagementTracker is the core class for the feature engagement tracker.
 */
public interface FeatureEngagementTracker {
    /**
     * Must be called whenever an event happens.
     */
    void notifyEvent(String event);

    /**
     * This function must be called whenever the triggering condition for a specific feature
     * happens. Returns true iff the display of the in-product help must happen.
     * If {@code true} is returned, the caller *must* call {@link #dismissed()} when display
     * of feature enlightenment ends.
     * @return whether feature enlightenment should be displayed.
     */
    @CheckResult
    boolean shouldTriggerHelpUI(String feature);

    /**
     * Must be called after display of feature enlightenment finishes for a particular feature.
     */
    void dismissed(String feature);

    /**
     * Returns whether the tracker has been successfully initialized. During startup, this will be
     * false until the internal model has been loaded at which point it is set to true if the
     * initialization was successful. The state will never change from initialized to uninitialized.
     * Callers can invoke AddOnInitializedCallback(...) to be notified when the result of the
     * initialization is ready.
     */
    boolean isInitialized();

    /**
     * For features that trigger on startup, they can register a callback to ensure that they are
     * informed when the tracker has finished the initialization. If the tracker has already been
     * initialized, the callback will still be invoked with the result. The callback is guaranteed
     * to be invoked exactly one time.
     *
     * The |result| parameter indicates whether the initialization was a success and the tracker is
     * ready to receive calls.
     */
    void addOnInitializedCallback(Callback<Boolean> callback);
}

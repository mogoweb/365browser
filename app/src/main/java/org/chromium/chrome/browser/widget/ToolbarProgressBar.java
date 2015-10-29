// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.widget;

import android.animation.TimeAnimator;
import android.animation.TimeAnimator.TimeListener;
import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;

import org.chromium.base.CommandLine;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.ChromeSwitches;
import org.chromium.ui.interpolators.BakedBezierInterpolator;

/**
 * Progress bar for use in the Toolbar view.
 */
public class ToolbarProgressBar extends ClipDrawableProgressBar {

    /**
     * Interface for progress bar animation interpolation logics.
     */
    interface AnimationLogic {
        /**
         * Resets internal data. It must be called on every loading start.
         */
        void reset();

        /**
         * Returns interpolated progress for animation.
         *
         * @param targetProgress Actual page loading progress.
         * @param frameTimeSec   Duration since the last call.
         * @param resolution     Resolution of the displayed progress bar. Mainly for rounding.
         */
        float updateProgress(float targetProgress, float frameTimeSec, int resolution);
    }

    private static final long PROGRESS_FRAME_TIME_CAP_MS = 50;
    private long mAlphaAnimationDurationMs = 140;
    private long mHidingDelayMs = 100;

    private boolean mIsStarted;
    private float mTargetProgress;
    AnimationLogic mAnimationLogic;
    private boolean mAnimationInitialized;

    private final Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            animateAlphaTo(0.0f);
        }
    };

    private final TimeAnimator mProgressAnimator = new TimeAnimator();
    {
        mProgressAnimator.setTimeListener(new TimeListener() {
            @Override
            public void onTimeUpdate(TimeAnimator animation, long totalTimeMs, long deltaTimeMs) {
                // Cap progress bar animation frame time so that it doesn't jump too much even when
                // the animation is janky.
                ToolbarProgressBar.super.setProgress(mAnimationLogic.updateProgress(
                        mTargetProgress,
                        Math.max(deltaTimeMs, PROGRESS_FRAME_TIME_CAP_MS) * 0.001f,
                        getWidth()));

                if (getProgress() == mTargetProgress) {
                    if (mTargetProgress == 1.0f && !mIsStarted) {
                        postOnAnimationDelayed(mHideRunnable, mHidingDelayMs);
                    }
                    mProgressAnimator.end();
                    return;
                }
            }
        });
    }

    /**
     * Creates a toolbar progress bar.
     *
     * @param context the application environment.
     * @param attrs the xml attributes that should be used to initialize this view.
     */
    public ToolbarProgressBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        setAlpha(0.0f);
    }

    /**
     * Initializes animation based on command line configuration. This must be called when native
     * library is ready.
     */
    public void initializeAnimation() {
        if (mAnimationInitialized) return;
        mAnimationInitialized = true;

        assert mAnimationLogic == null;

        String animation = CommandLine.getInstance().getSwitchValue(
                ChromeSwitches.PROGRESS_BAR_ANIMATION);
        if (TextUtils.equals(animation, "smooth")) {
            mAnimationLogic = new ProgressAnimationSmooth();
        } else if (TextUtils.equals(animation, "fast-start")) {
            mAnimationLogic = new ProgressAnimationFastStart();
        } else if (TextUtils.equals(animation, "linear")) {
            mAnimationLogic = new ProgressAnimationLinear();
        } else {
            assert TextUtils.isEmpty(animation) || TextUtils.equals(animation, "disabled");
        }
    }

    /**
     * Start showing progress bar animation.
     */
    public void start() {
        mIsStarted = true;
        super.setProgress(0.0f);
        if (mAnimationLogic != null) mAnimationLogic.reset();
        removeCallbacks(mHideRunnable);
        animateAlphaTo(1.0f);
    }

    /**
     * Start hiding progress bar animation.
     * @param delayed Whether a delayed fading out animation should be posted.
     */
    public void finish(boolean delayed) {
        mIsStarted = false;

        if (delayed) {
            updateVisibleProgress();
        } else {
            removeCallbacks(mHideRunnable);
            animate().cancel();
            setAlpha(0.0f);
        }
    }

    /**
     * Set alpha show&hide animation duration. This is for faster testing.
     * @param alphaAnimationDurationMs Alpha animation duration in milliseconds.
     */
    @VisibleForTesting
    public void setAlphaAnimationDuration(long alphaAnimationDurationMs) {
        mAlphaAnimationDurationMs = alphaAnimationDurationMs;
    }

    /**
     * Set hiding delay duration. This is for faster testing.
     * @param hidngDelayMs Hiding delay duration in milliseconds.
     */
    @VisibleForTesting
    public void setHidingDelay(long hidngDelayMs) {
        mHidingDelayMs = hidngDelayMs;
    }

    private void animateAlphaTo(float targetAlpha) {
        float alphaDiff = targetAlpha - getAlpha();
        if (alphaDiff != 0.0f) {
            animate().alpha(targetAlpha)
                    .setDuration((long) Math.abs(alphaDiff * mAlphaAnimationDurationMs))
                    .setInterpolator(alphaDiff > 0
                            ? BakedBezierInterpolator.FADE_IN_CURVE
                            : BakedBezierInterpolator.FADE_OUT_CURVE);
        }
    }

    private void updateVisibleProgress() {
        if (mAnimationLogic == null) {
            super.setProgress(mTargetProgress);
            if (!mIsStarted) postOnAnimationDelayed(mHideRunnable, mHidingDelayMs);
        } else {
            if (!mProgressAnimator.isStarted()) mProgressAnimator.start();
        }
    }

    // ClipDrawableProgressBar implementation.

    @Override
    public void setProgress(float progress) {
        assert mIsStarted;
        assert getProgress() <= progress;

        mTargetProgress = progress;
        updateVisibleProgress();
    }
}

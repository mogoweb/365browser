// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.enhancedbookmarks;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

import org.chromium.chrome.R;
import org.chromium.ui.interpolators.BakedBezierInterpolator;

/**
 * View for showing loading content animation. The animation logic follows the Android loading
 * content UI guideline.
 */
public class EnhancedBookmarkLoadingView extends FrameLayout {
    private static final int LOADING_ANIMATION_DELAY_MS = 500;
    private static final int MINIMUM_ANIMATION_SHOW_TIME_MS = 500;

    private long mStartTime = -1;

    private ProgressBar mLoadingProgressBar;

    private final Runnable mDelayedShow = new Runnable() {
        @Override
        public void run() {
            mStartTime = System.currentTimeMillis();
            mLoadingProgressBar.setAlpha(1.0f);
            mLoadingProgressBar.setVisibility(View.VISIBLE);
        }
    };

    // Material loading design spec requires us to show progress spinner at least 500ms, so we need
    // this delayed runnable to implement that.
    private final Runnable mDelayedHide = new Runnable() {
        @Override
        public void run() {
            animate().alpha(0.0f).setInterpolator(BakedBezierInterpolator.TRANSFORM_CURVE)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            setVisibility(GONE);
                        }
                    });
        }
    };

    /**
     * Constructor for inflating from XML.
     */
    public EnhancedBookmarkLoadingView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mLoadingProgressBar = (ProgressBar) findViewById(R.id.eb_loading_circle);
    }

    /**
     * Show loading UI. It shows the loading animation 500ms after.
     */
    public void showLoadingUI() {
        removeCallbacks(mDelayedShow);
        removeCallbacks(mDelayedHide);

        setVisibility(VISIBLE);
        setAlpha(1.0f);
        mLoadingProgressBar.setVisibility(GONE);

        postDelayed(mDelayedShow, LOADING_ANIMATION_DELAY_MS);
    }

    /**
     * Hide loading UI. If progress bar is not shown, it disappears immediately. If so, it smoothly
     * fades out.
     */
    public void hideLoadingUI() {
        removeCallbacks(mDelayedShow);
        removeCallbacks(mDelayedHide);

        if (mLoadingProgressBar.getVisibility() == VISIBLE) {
            postDelayed(mDelayedHide, Math.max(0,
                    mStartTime + MINIMUM_ANIMATION_SHOW_TIME_MS - System.currentTimeMillis()));
        } else {
            setVisibility(GONE);
        }
    }
}

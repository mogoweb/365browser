// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.toolbar;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.view.View;

import org.chromium.chrome.R;
import org.chromium.ui.interpolators.BakedBezierInterpolator;

/**
 * A delegate class to handle animations in {@link CustomTabToolbar}.
 */
class CustomTabToolbarAnimationDelegate {
    private static final int CUSTOM_TAB_TOOLBAR_SLIDE_DURATION_MS = 200;
    private static final int CUSTOM_TAB_TOOLBAR_FADE_DURATION_MS = 150;

    private final View mSecurityButton;
    private final AnimatorSet mSecurityButtonShowAnimator;
    private final AnimatorSet mSecurityButtonHideAnimator;

    /**
     * Constructs an instance of {@link CustomTabToolbarAnimationDelegate}.
     */
    CustomTabToolbarAnimationDelegate(View securityButton, final View titleUrlContainer) {
        mSecurityButton = securityButton;

        mSecurityButtonShowAnimator = new AnimatorSet();
        int securityButtonWidth = securityButton.getResources()
                .getDimensionPixelSize(R.dimen.location_bar_icon_width);
        Animator translateRight = ObjectAnimator.ofFloat(titleUrlContainer,
                View.TRANSLATION_X, securityButtonWidth);
        translateRight.setInterpolator(BakedBezierInterpolator.TRANSFORM_CURVE);
        translateRight.setDuration(CUSTOM_TAB_TOOLBAR_SLIDE_DURATION_MS);

        Animator fadeIn = ObjectAnimator.ofFloat(mSecurityButton, View.ALPHA, 1);
        fadeIn.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                mSecurityButton.setVisibility(View.VISIBLE);
                mSecurityButton.setAlpha(0f);
                titleUrlContainer.setTranslationX(0);
            }
        });
        fadeIn.setInterpolator(BakedBezierInterpolator.FADE_IN_CURVE);
        fadeIn.setDuration(CUSTOM_TAB_TOOLBAR_FADE_DURATION_MS);
        mSecurityButtonShowAnimator.playSequentially(translateRight, fadeIn);

        mSecurityButtonHideAnimator = new AnimatorSet();
        Animator fadeOut = ObjectAnimator.ofFloat(mSecurityButton, View.ALPHA, 0);
        fadeOut.setInterpolator(BakedBezierInterpolator.FADE_OUT_CURVE);
        fadeOut.setDuration(CUSTOM_TAB_TOOLBAR_FADE_DURATION_MS);

        Animator translateLeft = ObjectAnimator.ofFloat(titleUrlContainer,
                View.TRANSLATION_X, -securityButtonWidth);
        translateLeft.setInterpolator(BakedBezierInterpolator.TRANSFORM_CURVE);
        translateLeft.setDuration(CUSTOM_TAB_TOOLBAR_SLIDE_DURATION_MS);
        translateLeft.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mSecurityButton.setVisibility(View.GONE);
                titleUrlContainer.setTranslationX(0);
            }
        });
        mSecurityButtonHideAnimator.playSequentially(fadeOut, translateLeft);
    }

    /**
     * Starts the animation to show the security button. Will do nothing if the button is already
     * visible.
     */
    void showSecurityButton() {
        if (mSecurityButton.getVisibility() == View.VISIBLE) return;
        if (mSecurityButtonShowAnimator.isRunning()) mSecurityButtonShowAnimator.cancel();
        mSecurityButtonShowAnimator.start();
    }

    /**
     * Starts the animation to hide the security button. Will do nothing if the button is not
     * visible.
     */
    void hideSecurityButton() {
        if (mSecurityButton.getVisibility() == View.GONE) return;
        if (mSecurityButtonHideAnimator.isRunning()) return;
        mSecurityButtonHideAnimator.start();
    }
}

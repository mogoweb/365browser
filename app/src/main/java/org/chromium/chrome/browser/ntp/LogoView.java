// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Property;
import android.view.View;
import android.view.View.OnClickListener;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.ntp.LogoBridge.Logo;
import org.chromium.chrome.browser.ntp.NewTabPageView.NewTabPageManager;

import java.lang.ref.WeakReference;

/**
 * This view shows the default search provider's logo and fades in a new logo if one becomes
 * available.
 */
public class LogoView extends View implements OnClickListener {

    // Number of milliseconds for a new logo to fade in.
    private static final int LOGO_TRANSITION_TIME_MS = 400;

    // The default logo is shared across all NTPs.
    private static WeakReference<Bitmap> sDefaultLogo;

    private Paint mPaint;
    private Bitmap mLogo;
    private Bitmap mNewLogo;
    private Matrix mLogoMatrix;
    private Matrix mNewLogoMatrix;
    private boolean mLogoIsDefault;
    private boolean mNewLogoIsDefault;
    private ObjectAnimator mAnimation;

    /**
     * A measure from 0 to 1 of how much the new logo has faded in. 0 shows the old logo, 1 shows
     * the new logo, and intermediate values show the new logo cross-fading in over the old logo.
     * Set to 0 when not transitioning.
     */
    private float mTransitionAmount;

    private NewTabPageManager mManager;

    private final Property<LogoView, Float> mTransitionProperty =
            new Property<LogoView, Float>(Float.class, "") {
        @Override
        public Float get(LogoView logoView) {
            return logoView.mTransitionAmount;
        }

        @Override
        public void set(LogoView logoView, Float amount) {
            assert amount >= 0f;
            assert amount <= 1f;
            if (logoView.mTransitionAmount != amount) {
                logoView.mTransitionAmount = amount;
                invalidate();
            }
        }
    };

    /**
     * Constructor used to inflate a LogoView from XML.
     */
    public LogoView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mLogo = getDefaultLogo();
        mLogoMatrix = new Matrix();
        mLogoIsDefault = true;

        mPaint = new Paint();
        mPaint.setFilterBitmap(true);

        // Mark this view as non-clickable so that accessibility will ignore it. When a non-default
        // logo is shown, this view will be marked clickable again.
        setOnClickListener(this);
        setClickable(false);
    }

    /**
     * Sets the NewTabPageManager to notify when the logo is pressed.
     */
    public void setMananger(NewTabPageManager manager) {
        mManager = manager;
    }

    /**
     * Jumps to the end of the current logo animation, if any.
     */
    public void endAnimation() {
        if (mAnimation != null) {
            mAnimation.end();
            mAnimation = null;
        }
    }

    /**
     * Fades in a new logo over the current logo.
     *
     * @param logo The new logo to fade in. May be null to reset to the default logo.
     */
    public void updateLogo(Logo logo) {
        if (logo == null) {
            updateLogo(getDefaultLogo(), null, true);
        } else {
            String contentDescription = TextUtils.isEmpty(logo.altText) ? null
                    : getResources().getString(R.string.accessibility_google_doodle, logo.altText);
            updateLogo(logo.image, contentDescription, false);
        }
    }

    private void updateLogo(Bitmap logo, final String contentDescription, boolean isDefaultLogo) {
        if (mAnimation != null) mAnimation.end();

        mNewLogo = logo;
        mNewLogoMatrix = new Matrix();
        mNewLogoIsDefault = isDefaultLogo;
        setMatrix(mNewLogo, mNewLogoMatrix, mNewLogoIsDefault);

        mAnimation = ObjectAnimator.ofFloat(this, mTransitionProperty, 0f, 1f);
        mAnimation.setDuration(LOGO_TRANSITION_TIME_MS);
        mAnimation.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
            }

            @Override
            public void onAnimationRepeat(Animator animation) {
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mLogo = mNewLogo;
                mLogoMatrix = mNewLogoMatrix;
                mLogoIsDefault = mNewLogoIsDefault;
                mNewLogo = null;
                mNewLogoMatrix = null;
                mTransitionAmount = 0f;
                mAnimation = null;
                setContentDescription(contentDescription);
                setClickable(!mNewLogoIsDefault);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                onAnimationEnd(animation);
                invalidate();
            }
        });
        mAnimation.start();
    }

    /**
     * @return Whether a new logo is currently fading in over the old logo.
     */
    private boolean isTransitioning() {
        return mTransitionAmount != 0f;
    }

    /**
     * Sets the matrix to scale and translate the image so that it will be centered in the LogoView
     * and scaled to fit within the LogoView.
     *
     * @param preventUpscaling Whether the image should not be scaled up. If true, the image might
     *                         not fill the entire view but will still be centered.
     */
    private void setMatrix(Bitmap image, Matrix matrix, boolean preventUpscaling) {
        int width = getWidth();
        int height = getHeight();
        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();

        float scale = Math.min((float) width / imageWidth, (float) height / imageHeight);
        if (preventUpscaling) scale = Math.min(1.0f, scale);

        int imageOffsetX = Math.round((width - imageWidth * scale) * 0.5f);
        int imageOffsetY = Math.round((height - imageHeight * scale) * 0.5f);

        matrix.setScale(scale, scale);
        matrix.postTranslate(imageOffsetX, imageOffsetY);
    }

    /**
     * @return The default logo.
     */
    private Bitmap getDefaultLogo() {
        Bitmap defaultLogo = sDefaultLogo == null ? null : sDefaultLogo.get();
        if (defaultLogo == null) {
            defaultLogo = BitmapFactory.decodeResource(getResources(), R.drawable.google_logo);
            sDefaultLogo = new WeakReference<Bitmap>(defaultLogo);
        }
        return defaultLogo;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mLogo != null && mTransitionAmount < 0.5f) {
            mPaint.setAlpha((int) (255 * 2 * (0.5f - mTransitionAmount)));
            canvas.save();
            canvas.concat(mLogoMatrix);
            canvas.drawBitmap(mLogo, 0, 0, mPaint);
            canvas.restore();
        }

        if (mNewLogo != null && mTransitionAmount > 0.5f) {
            mPaint.setAlpha((int) (255 * 2 * (mTransitionAmount - 0.5f)));
            canvas.save();
            canvas.concat(mNewLogoMatrix);
            canvas.drawBitmap(mNewLogo, 0, 0, mPaint);
            canvas.restore();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (w != oldw || h != oldh) {
            if (mLogo != null) setMatrix(mLogo, mLogoMatrix, mLogoIsDefault);
            if (mNewLogo != null) setMatrix(mNewLogo, mNewLogoMatrix, mNewLogoIsDefault);
        }
    }

    @Override
    public void onClick(View view) {
        if (view == this && mManager != null && !isTransitioning()) {
            mManager.openLogoLink();
        }
    }
}

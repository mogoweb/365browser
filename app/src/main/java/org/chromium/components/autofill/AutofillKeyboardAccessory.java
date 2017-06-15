// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.components.autofill;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.v7.content.res.AppCompatResources;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.ui.UiUtils;
import org.chromium.ui.base.WindowAndroid;

import java.util.ArrayList;

/**
 * The Autofill suggestion view that lists relevant suggestions. It sits above the keyboard and
 * below the content area.
 */
public class AutofillKeyboardAccessory extends LinearLayout
        implements WindowAndroid.KeyboardVisibilityListener, View.OnClickListener,
        View.OnLongClickListener {
    // Time to pause before reversing animation when the first suggestion is a hint.
    private static final long PAUSE_ANIMATION_BEFORE_REVERSE_MILLIS = 1000;
    // Time to fade in views that we temporarily hide when we change container layout.
    private static final long ALPHA_ANIMATION_DURATION_MILLIS = 250;

    private final WindowAndroid mWindowAndroid;
    private final AutofillDelegate mAutofillDelegate;
    // If |mMaximumLabelWidthPx| is 0, we do not call |setMaxWidth| on the |TextView| for a fillable
    // suggestion label.
    private final int mMaximumLabelWidthPx;
    private final int mMaximumSublabelWidthPx;
    private final int mAnimationDurationMillis;
    // We start animating by scrolling the suggestions from beyond the viewport.
    private final int mStartAnimationTranslationPx;

    private int mSeparatorPosition;
    private Animator mAnimator;
    private Runnable mReverseAnimationRunnable;

    /**
     * Creates an AutofillKeyboardAccessory with specified parameters.
     * @param windowAndroid The owning WindowAndroid.
     * @param autofillDelegate A object that handles the calls to the native
     *                         AutofillKeyboardAccessoryView.
     * @param animationDurationMillis If 0, do not animate.
     * @param shouldLimitLabelWidth If true, limit suggestion label width to 1/2 device's width.
     */
    public AutofillKeyboardAccessory(WindowAndroid windowAndroid, AutofillDelegate autofillDelegate,
            int animationDurationMillis, boolean shouldLimitLabelWidth) {
        super(windowAndroid.getActivity().get());
        assert autofillDelegate != null;
        assert windowAndroid.getActivity().get() != null;
        mWindowAndroid = windowAndroid;
        mAutofillDelegate = autofillDelegate;

        int deviceWidthPx = windowAndroid.getDisplay().getDisplayWidth();
        mMaximumLabelWidthPx = shouldLimitLabelWidth ? deviceWidthPx / 2 : 0;
        mMaximumSublabelWidthPx = deviceWidthPx / 4;

        mWindowAndroid.addKeyboardVisibilityListener(this);
        int horizontalPaddingPx = getResources().getDimensionPixelSize(
                R.dimen.keyboard_accessory_half_padding);
        setPadding(horizontalPaddingPx, 0, horizontalPaddingPx, 0);

        mAnimationDurationMillis = animationDurationMillis;
        mStartAnimationTranslationPx = getResources().getDimensionPixelSize(
                R.dimen.keyboard_accessory_start_animation_translation);
    }

    /**
     * Shows the given suggestions.
     * @param suggestions Autofill suggestion data.
     * @param isRtl Gives the layout direction for the <input> field.
     */
    @SuppressLint("InlinedApi")
    public void showWithSuggestions(AutofillSuggestion[] suggestions, final boolean isRtl) {
        assert suggestions.length > 0;
        // The first suggestion may be a hint to call attention to the keyboard accessory. See
        // |IsHintEnabledInKeyboardAccessory|. A 'hint' suggestion does not have a label and is not
        // fillable, but has an icon.
        final boolean isFirstSuggestionAHint = TextUtils.isEmpty(suggestions[0].getLabel());
        if (isFirstSuggestionAHint) {
            assert suggestions[0].getIconId() != 0 && !suggestions[0].isFillable();
        }

        removeAllViews();
        mSeparatorPosition = -1;
        for (int i = 0; i < suggestions.length; i++) {
            AutofillSuggestion suggestion = suggestions[i];
            boolean isKeyboardAccessoryHint = i == 0 && isFirstSuggestionAHint;
            if (!isKeyboardAccessoryHint) {
                assert !TextUtils.isEmpty(suggestion.getLabel());
            }

            View touchTarget;
            if (!suggestion.isFillable() && suggestion.getIconId() != 0) {
                touchTarget = LayoutInflater.from(getContext()).inflate(
                        R.layout.autofill_keyboard_accessory_icon, this, false);

                if (mSeparatorPosition == -1 && !isKeyboardAccessoryHint) mSeparatorPosition = i;

                ImageView icon = (ImageView) touchTarget;
                Drawable drawable =
                        AppCompatResources.getDrawable(getContext(), suggestion.getIconId());
                if (isKeyboardAccessoryHint) {
                    drawable.setColorFilter(ApiCompatibilityUtils.getColor(getResources(),
                                                    R.color.keyboard_accessory_hint_icon),
                            PorterDuff.Mode.SRC_IN);
                } else {
                    icon.setContentDescription(suggestion.getLabel());
                }
                icon.setImageDrawable(drawable);
            } else {
                touchTarget = LayoutInflater.from(getContext()).inflate(
                        R.layout.autofill_keyboard_accessory_item, this, false);

                TextView label = (TextView) touchTarget.findViewById(
                        R.id.autofill_keyboard_accessory_item_label);

                if (mMaximumLabelWidthPx > 0 && suggestion.isFillable()) {
                    label.setMaxWidth(mMaximumLabelWidthPx);
                }

                label.setText(suggestion.getLabel());
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                    label.setTypeface(Typeface.DEFAULT_BOLD);
                }

                if (suggestion.getIconId() != 0) {
                    ApiCompatibilityUtils.setCompoundDrawablesRelativeWithIntrinsicBounds(
                            label,
                            AppCompatResources.getDrawable(getContext(), suggestion.getIconId()),
                            null /* top */, null /* end */, null /* bottom */);
                }

                if (!TextUtils.isEmpty(suggestion.getSublabel())) {
                    assert suggestion.isFillable();
                    TextView sublabel = (TextView) touchTarget.findViewById(
                            R.id.autofill_keyboard_accessory_item_sublabel);
                    sublabel.setText(suggestion.getSublabel());
                    sublabel.setVisibility(View.VISIBLE);
                    sublabel.setMaxWidth(mMaximumSublabelWidthPx);
                }
            }

            if (!isKeyboardAccessoryHint) {
                touchTarget.setTag(i);
                touchTarget.setOnClickListener(this);
                if (suggestion.isDeletable()) {
                    touchTarget.setOnLongClickListener(this);
                }
            }
            addView(touchTarget);
        }

        if (mSeparatorPosition != -1) {
            addView(createSeparatorView(), mSeparatorPosition);
        }

        // TODO(crbug/722897): Following does not reverse layout order for RTL.
        ApiCompatibilityUtils.setLayoutDirection(
                this, isRtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);

        final HorizontalScrollView container =
                (HorizontalScrollView) mWindowAndroid.getKeyboardAccessoryView();

        if (getParent() == null) {
            container.addView(this);
            // If we are animating the view, we |setVisibility| in |onAnimationStart|.
            if (mAnimationDurationMillis == 0) {
                container.setVisibility(View.VISIBLE);
            }
            container.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        }

        if (mAnimationDurationMillis > 0) {
            cancelAnimations(container);
            mAnimator = ObjectAnimator.ofFloat(
                    this, View.TRANSLATION_X, -mStartAnimationTranslationPx, 0);
            mAnimator.setDuration(mAnimationDurationMillis);
            mAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animator) {
                    if (isFirstSuggestionAHint) {
                        // We will make suggestions beyond the separator visible after we have
                        // finished the reverse animation and removed the first suggestion. This
                        // prevents a sudden movement of these suggestions when the container view
                        // is redrawn. See |scheduleReverseAnimation|.
                        for (int i = mSeparatorPosition + 1; i < getChildCount(); ++i) {
                            getChildAt(i).setAlpha(0);
                        }
                    }
                    container.setVisibility(View.VISIBLE);
                }
                @Override
                public void onAnimationEnd(Animator animator) {
                    mAnimator.removeListener(this);
                    if (isFirstSuggestionAHint && container.getVisibility() == View.VISIBLE) {
                        scheduleReverseAnimation(container);
                    }
                }
            });
        }
        container.post(new Runnable() {
            @Override
            public void run() {
                if (mAnimationDurationMillis > 0) {
                    mAnimator.start();
                } else {
                    container.scrollTo(isRtl ? getRight() : 0, 0);
                }
            }
        });
    }

    /**
     * Called to hide the suggestion view.
     */
    public void dismiss() {
        ViewGroup container = mWindowAndroid.getKeyboardAccessoryView();
        container.removeView(this);
        container.setVisibility(View.GONE);
        mWindowAndroid.removeKeyboardVisibilityListener(this);
        ((View) container.getParent()).requestLayout();
    }

    @Override
    public void keyboardVisibilityChanged(boolean isShowing) {
        if (!isShowing) {
            dismiss();
            mAutofillDelegate.dismissed();
        }
    }

    @Override
    public void onClick(View v) {
        UiUtils.hideKeyboard(this);
        mAutofillDelegate.suggestionSelected((int) v.getTag());
    }

    @Override
    public boolean onLongClick(View v) {
        mAutofillDelegate.deleteSuggestion((int) v.getTag());
        return true;
    }

    // Helper to create separator view so that the settings icon is aligned to the right of the
    // screen.
    private View createSeparatorView() {
        View separator = new View(getContext());
        // Specify a layout weight so that the settings icon, which is displayed after the
        // separator, is aligned with the edge of the viewport.
        separator.setLayoutParams(new LinearLayout.LayoutParams(0, 0, 1));
        return separator;
    }

    // Cancels any ongoing and pending reverse animations.
    private void cancelAnimations(View view) {
        if (mAnimator != null && mAnimator.isStarted()) mAnimator.cancel();
        view.removeCallbacks(mReverseAnimationRunnable);
        mAnimator = null;
    }

    // Schedules the reverse of the animation. Scrolls the first suggestion (which is a non-fillable
    // hint) out of the viewport at the end of the reversed animation.
    private void scheduleReverseAnimation(final View view) {
        assert getChildCount() > 1;
        // We may have removed the keyboardAccessoryHint from a previous animation that we
        // cancelled.
        final View firstSuggestion = getChildAt(0);
        if (!(firstSuggestion instanceof ImageView
                    && firstSuggestion.getContentDescription() == null)) {
            return;
        }

        // Play 2 animations sequentially.
        // 1. Move the icon hint out of the viewport by reversing the forward animation.
        //    At the end of this animation, we remove the icon hint, mark views beyond
        //    |mSeparatorPosition| as |View.VISIBLE| but 100% transparent.
        // 2. Fade-in the views beyond |mSeparatorPosition|.
        // We use 2 animations to smoothen the relayout of the |HorizonatlScrollView| when we
        // remove the icon hint.
        assert getChildAt(0) instanceof ImageView;
        for (int i = mSeparatorPosition + 1; i < getChildCount(); ++i) {
            assert getChildAt(i) instanceof ImageView;
        }

        // |reverseAnimator| moves icon hint out of viewport, removes the icon hint and hides views
        // beyond |mSeparatorPosition|.
        int hintWidth = firstSuggestion.getWidth();
        final ObjectAnimator reverseAnimator =
                ObjectAnimator.ofFloat(this, View.TRANSLATION_X, -hintWidth);
        reverseAnimator.setDuration(mAnimationDurationMillis);
        reverseAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animator) {
                reverseAnimator.removeListener(this);
                if (view.getVisibility() != View.VISIBLE) return;
                removeView(firstSuggestion);
                mSeparatorPosition--;
                // Reset 'X' and redraw the layout.
                setTranslationX(0);
            }
        });

        // |alphaAnimation| fades in views beyond |mSeparatorPosition|.
        final AnimatorSet alphaAnimation = new AnimatorSet();
        ArrayList<Animator> alphaAnimators = new ArrayList<Animator>();
        for (int i = mSeparatorPosition + 1; i < getChildCount(); ++i) {
            alphaAnimators.add(ObjectAnimator.ofFloat(getChildAt(i), View.ALPHA, 1f));
        }
        alphaAnimation.setDuration(ALPHA_ANIMATION_DURATION_MILLIS);
        alphaAnimation.playTogether(alphaAnimators);

        mAnimator = new AnimatorSet();
        ((AnimatorSet) mAnimator).playSequentially(reverseAnimator, alphaAnimation);
        mReverseAnimationRunnable = new Runnable() {
            @Override
            public void run() {
                if (view.getVisibility() == View.VISIBLE) mAnimator.start();
            }
        };
        view.postDelayed(mReverseAnimationRunnable, PAUSE_ANIMATION_BEFORE_REVERSE_MILLIS);
    }
}

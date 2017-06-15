// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.widget.textbubble;

import android.content.Context;
import android.graphics.Rect;
import android.support.annotation.StringRes;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnPreDrawListener;
import android.widget.PopupWindow.OnDismissListener;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.content.browser.PositionObserver;
import org.chromium.content.browser.ViewPositionObserver;

/**
 * A helper class that anchors a {@link TextBubble} to a particular {@link View}.  The bubble will
 * listen to layout events on the {@link View} and update accordingly.
 */
public class ViewAnchoredTextBubble extends TextBubble
        implements PositionObserver.Listener, ViewTreeObserver.OnGlobalLayoutListener,
                   View.OnAttachStateChangeListener, OnPreDrawListener, OnDismissListener {
    private final int[] mCachedScreenCoordinates = new int[2];
    private final Rect mAnchorRect = new Rect();
    private final Rect mInsetRect = new Rect();
    private final View mAnchorView;

    private final ViewPositionObserver mViewPositionObserver;

    /** If not {@code null}, the {@link ViewTreeObserver} that we are registered to. */
    private ViewTreeObserver mViewTreeObserver;

    /**
     * Creates an instance of a {@link ViewAnchoredTextBubble}.
     * @param context    Context to draw resources from.
     * @param anchorView The {@link View} to anchor to.
     * @param stringId The id of the string resource for the text that should be shown.
     * @param accessibilityStringId The id of the string resource of the accessibility text.
     */
    public ViewAnchoredTextBubble(Context context, View anchorView, @StringRes int stringId,
            @StringRes int accessibilityStringId) {
        super(context, anchorView.getRootView(), stringId, accessibilityStringId);
        mAnchorView = anchorView;

        mViewPositionObserver = new ViewPositionObserver(mAnchorView);
    }

    /**
     * Specifies the inset values in pixels that determine how to shrink the {@link View} bounds
     * when creating the anchor {@link Rect}.
     */
    public void setInsetPx(int left, int top, int right, int bottom) {
        mInsetRect.set(left, top, right, bottom);
        refreshAnchorBounds();
    }

    // TextBubble implementation.
    @Override
    public void show() {
        mViewPositionObserver.addListener(this);
        mAnchorView.addOnAttachStateChangeListener(this);
        mViewTreeObserver = mAnchorView.getViewTreeObserver();
        mViewTreeObserver.addOnGlobalLayoutListener(this);
        mViewTreeObserver.addOnPreDrawListener(this);

        refreshAnchorBounds();
        super.show();
    }

    @Override
    public void onDismiss() {
        mViewPositionObserver.removeListener(this);
        mAnchorView.removeOnAttachStateChangeListener(this);

        if (mViewTreeObserver != null && mViewTreeObserver.isAlive()) {
            mViewTreeObserver.removeOnGlobalLayoutListener(this);
            mViewTreeObserver.removeOnPreDrawListener(this);
        }
        mViewTreeObserver = null;
    }

    // ViewTreeObserver.OnGlobalLayoutListener implementation.
    @Override
    public void onGlobalLayout() {
        if (!mAnchorView.isShown()) dismiss();
    }

    // ViewTreeObserver.OnPreDrawListener implementation.
    @Override
    public boolean onPreDraw() {
        if (!mAnchorView.isShown()) dismiss();
        return true;
    }

    // View.OnAttachStateChangedObserver implementation.
    @Override
    public void onViewAttachedToWindow(View v) {}

    @Override
    public void onViewDetachedFromWindow(View v) {
        dismiss();
    }

    // PositionObserver.Listener implementation.
    @Override
    public void onPositionChanged(int positionX, int positionY) {
        refreshAnchorBounds();
    }

    private void refreshAnchorBounds() {
        mAnchorView.getLocationOnScreen(mCachedScreenCoordinates);
        mAnchorRect.left = mCachedScreenCoordinates[0];
        mAnchorRect.top = mCachedScreenCoordinates[1];
        mAnchorRect.right = mAnchorRect.left + mAnchorView.getWidth();
        mAnchorRect.bottom = mAnchorRect.top + mAnchorView.getHeight();

        mAnchorRect.left += mInsetRect.left;
        mAnchorRect.top += mInsetRect.top;
        mAnchorRect.right -= mInsetRect.right;
        mAnchorRect.bottom -= mInsetRect.bottom;

        // Account for the padding.
        boolean isRtl = ApiCompatibilityUtils.isLayoutRtl(mAnchorView);
        mAnchorRect.left += isRtl ? ApiCompatibilityUtils.getPaddingEnd(mAnchorView)
                                  : ApiCompatibilityUtils.getPaddingStart(mAnchorView);
        mAnchorRect.right -= isRtl ? ApiCompatibilityUtils.getPaddingStart(mAnchorView)
                                   : ApiCompatibilityUtils.getPaddingEnd(mAnchorView);
        mAnchorRect.top += mAnchorView.getPaddingTop();
        mAnchorRect.bottom -= mAnchorView.getPaddingBottom();

        // Make sure we still have a valid Rect after applying the inset.
        mAnchorRect.right = Math.max(mAnchorRect.left, mAnchorRect.right);
        mAnchorRect.bottom = Math.max(mAnchorRect.top, mAnchorRect.bottom);

        setAnchorRect(mAnchorRect);
    }
}
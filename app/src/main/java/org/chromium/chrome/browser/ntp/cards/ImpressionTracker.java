// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp.cards;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewTreeObserver;

/**
 * A class that helps with tracking impressions.
 */
public class ImpressionTracker implements ViewTreeObserver.OnPreDrawListener {
    /**
     * The Listener will be called back on each impression. Whenever at least 1/3 of the view's
     * height is visible, that counts as an impression. Note that this will get called often while
     * the view is visible; it's the implementer's responsibility to count only one impression.
     */
    public interface Listener {
        void onImpression();
    }

    private final View mView;
    private final Listener mListener;

    public ImpressionTracker(View view, Listener listener) {
        mView = view;
        mListener = listener;

        // Listen to onPreDraw only if this view is potentially visible (attached to the window).
        mView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                mView.getViewTreeObserver().addOnPreDrawListener(ImpressionTracker.this);
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                mView.getViewTreeObserver().removeOnPreDrawListener(ImpressionTracker.this);
            }
        });
    }

    @Override
    public boolean onPreDraw() {
        Rect rect = new Rect(0, 0, mView.getWidth(), mView.getHeight());
        mView.getParent().getChildVisibleRect(mView, rect, null);
        // Track impression if at least one third of the view is visible.
        if (rect.height() >= mView.getHeight() / 3) {
            mListener.onImpression();
        }
        // Proceed with the current drawing pass.
        return true;
    }
}

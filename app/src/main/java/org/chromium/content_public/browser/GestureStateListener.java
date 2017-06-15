// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content_public.browser;

/**
 * A class that is notified of events and state changes related to gesture processing from
 * the ContentViewCore.
 */
public class GestureStateListener {
    /**
     * Called when the pinch gesture starts.
     */
    public void onPinchStarted() {}

    /**
     * Called when the pinch gesture ends.
     */
    public void onPinchEnded() {}

    /**
     * Called when a fling starts.
     */
    public void onFlingStartGesture(int scrollOffsetY, int scrollExtentY) {}

    /**
     * Called when a fling has ended.
     */
    public void onFlingEndGesture(int scrollOffsetY, int scrollExtentY) {}

    /**
     * Called to indicate that a scroll update gesture had been consumed by the page.
     * This callback is called whenever any layer is scrolled (like a frame or div). It is
     * not called when a JS touch handler consumes the event (preventDefault), it is not called
     * for JS-initiated scrolling.
     */
    public void onScrollUpdateGestureConsumed() {}

    /*
     * Called when a scroll gesture has started.
     */
    public void onScrollStarted(int scrollOffsetY, int scrollExtentY) {}

    /*
     * Called when a scroll gesture has stopped.
     */
    public void onScrollEnded(int scrollOffsetY, int scrollExtentY) {}

    /*
     * Called when the min or max scale factor may have been changed.
     */
    public void onScaleLimitsChanged(float minPageScaleFactor, float maxPageScaleFactor) {}

    /*
     * Called when the scroll offsets or extents may have changed.
     */
    public void onScrollOffsetOrExtentChanged(int scrollOffsetY, int scrollExtentY) {}

    /*
     * Called after a single-tap gesture event was dispatched to the renderer,
     * indicating whether or not the gesture was consumed.
     */
    public void onSingleTap(boolean consumed) {}

    /*
     * Called after a single-tap gesture event was processed by the renderer,
     * but was not handled.
     */
    public void onShowUnhandledTapUIIfNeeded(int x, int y) {}

    /**
     * Called when the gesture source (ContentViewCore) loses window focus.
     */
    public void onWindowFocusChanged(boolean hasWindowFocus) {}

    /**
     * Called when the gesture source (ContentViewCore) is being destroyed.
     */
    public void onDestroyed() {}
}

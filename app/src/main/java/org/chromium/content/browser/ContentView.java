// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.DragEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewStructure;
import android.view.accessibility.AccessibilityNodeProvider;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.FrameLayout;

import org.chromium.base.TraceEvent;
import org.chromium.ui.base.EventForwarder;

/**
 * The containing view for {@link ContentViewCore} that exists in the Android UI hierarchy and
 * exposes the various {@link View} functionality to it.
 */
public class ContentView extends FrameLayout
        implements ContentViewCore.InternalAccessDelegate, SmartClipProvider {

    private static final String TAG = "cr.ContentView";

    // Default value to signal that the ContentView's size need not be overridden.
    public static final int DEFAULT_MEASURE_SPEC =
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);

    protected final ContentViewCore mContentViewCore;
    private EventForwarder mEventForwarder;

    /**
     * The desired size of this view in {@link MeasureSpec}. Set by the host
     * when it should be different from that of the parent.
     */
    private int mDesiredWidthMeasureSpec = DEFAULT_MEASURE_SPEC;
    private int mDesiredHeightMeasureSpec = DEFAULT_MEASURE_SPEC;

    /**
     * Constructs a new ContentView for the appropriate Android version.
     * @param context The Context the view is running in, through which it can
     *                access the current theme, resources, etc.
     * @param cvc A pointer to the content view core managing this content view.
     * @return an instance of a ContentView.
     */
    public static ContentView createContentView(Context context, ContentViewCore cvc) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return new ContentViewApi23(context, cvc);
        }
        return new ContentView(context, cvc);
    }

    /**
     * Creates an instance of a ContentView.
     * @param context The Context the view is running in, through which it can
     *                access the current theme, resources, etc.
     * @param cvc A pointer to the content view core managing this content view.
     */
    ContentView(Context context, ContentViewCore cvc) {
        super(context, null, android.R.attr.webViewStyle);

        if (getScrollBarStyle() == View.SCROLLBARS_INSIDE_OVERLAY) {
            setHorizontalScrollBarEnabled(false);
            setVerticalScrollBarEnabled(false);
        }

        setFocusable(true);
        setFocusableInTouchMode(true);

        mContentViewCore = cvc;
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (mContentViewCore.supportsAccessibilityAction(action)) {
            return mContentViewCore.performAccessibilityAction(action, arguments);
        }

        return super.performAccessibilityAction(action, arguments);
    }

    /**
     * Set the desired size of the view. The values are in {@link MeasureSpec}.
     * @param width The width of the content view.
     * @param height The height of the content view.
     */
    public void setDesiredMeasureSpec(int width, int height) {
        mDesiredWidthMeasureSpec = width;
        mDesiredHeightMeasureSpec = height;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mDesiredWidthMeasureSpec != DEFAULT_MEASURE_SPEC) {
            widthMeasureSpec = mDesiredWidthMeasureSpec;
        }
        if (mDesiredHeightMeasureSpec != DEFAULT_MEASURE_SPEC) {
            heightMeasureSpec = mDesiredHeightMeasureSpec;
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public AccessibilityNodeProvider getAccessibilityNodeProvider() {
        AccessibilityNodeProvider provider = mContentViewCore.getAccessibilityNodeProvider();
        if (provider != null) {
            return provider;
        } else {
            return super.getAccessibilityNodeProvider();
        }
    }

    // Needed by ContentViewCore.InternalAccessDelegate
    @Override
    public void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        try {
            TraceEvent.begin("ContentView.onSizeChanged");
            super.onSizeChanged(w, h, ow, oh);
            mContentViewCore.onSizeChanged(w, h, ow, oh);
        } finally {
            TraceEvent.end("ContentView.onSizeChanged");
        }
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        return mContentViewCore.onCreateInputConnection(outAttrs);
    }

    @Override
    public boolean onCheckIsTextEditor() {
        return mContentViewCore.onCheckIsTextEditor();
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        try {
            TraceEvent.begin("ContentView.onFocusChanged");
            super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
            mContentViewCore.onFocusChanged(gainFocus, true /* hideKeyboardOnBlur */);
        } finally {
            TraceEvent.end("ContentView.onFocusChanged");
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        mContentViewCore.onWindowFocusChanged(hasWindowFocus);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return mContentViewCore.onKeyUp(keyCode, event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (isFocused()) {
            return mContentViewCore.dispatchKeyEvent(event);
        } else {
            return super.dispatchKeyEvent(event);
        }
    }

    @Override
    public boolean onDragEvent(DragEvent event) {
        return mContentViewCore.onDragEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return getEventForwarder().onTouchEvent(event);
    }

    /**
     * Mouse move events are sent on hover enter, hover move and hover exit.
     * They are sent on hover exit because sometimes it acts as both a hover
     * move and hover exit.
     */
    @Override
    public boolean onHoverEvent(MotionEvent event) {
        boolean consumed = mContentViewCore.onHoverEvent(event);
        if (!mContentViewCore.isTouchExplorationEnabled()) super.onHoverEvent(event);
        return consumed;
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        return mContentViewCore.onGenericMotionEvent(event);
    }

    private EventForwarder getEventForwarder() {
        if (mEventForwarder == null) {
            mEventForwarder = mContentViewCore.getWebContents().getEventForwarder();
        }
        return mEventForwarder;
    }

    @Override
    public boolean performLongClick() {
        return false;
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        mContentViewCore.onConfigurationChanged(newConfig);
    }

    /**
     * Currently the ContentView scrolling happens in the native side. In
     * the Java view system, it is always pinned at (0, 0). scrollBy() and scrollTo()
     * are overridden, so that View's mScrollX and mScrollY will be unchanged at
     * (0, 0). This is critical for drawing ContentView correctly.
     */
    @Override
    public void scrollBy(int x, int y) {
        mContentViewCore.scrollBy(x, y);
    }

    @Override
    public void scrollTo(int x, int y) {
        mContentViewCore.scrollTo(x, y);
    }

    @Override
    protected int computeHorizontalScrollExtent() {
        // TODO(dtrainor): Need to expose scroll events properly to public. Either make getScroll*
        // work or expose computeHorizontalScrollOffset()/computeVerticalScrollOffset as public.
        return mContentViewCore.computeHorizontalScrollExtent();
    }

    @Override
    protected int computeHorizontalScrollOffset() {
        return mContentViewCore.computeHorizontalScrollOffset();
    }

    @Override
    protected int computeHorizontalScrollRange() {
        return mContentViewCore.computeHorizontalScrollRange();
    }

    @Override
    protected int computeVerticalScrollExtent() {
        return mContentViewCore.computeVerticalScrollExtent();
    }

    @Override
    protected int computeVerticalScrollOffset() {
        return mContentViewCore.computeVerticalScrollOffset();
    }

    @Override
    protected int computeVerticalScrollRange() {
        return mContentViewCore.computeVerticalScrollRange();
    }

    // End FrameLayout overrides.

    @Override
    public boolean awakenScrollBars(int startDelay, boolean invalidate) {
        return mContentViewCore.awakenScrollBars(startDelay, invalidate);
    }

    @Override
    public boolean awakenScrollBars() {
        return super.awakenScrollBars();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mContentViewCore.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mContentViewCore.onDetachedFromWindow();
    }

    // Implements SmartClipProvider
    @Override
    public void extractSmartClipData(int x, int y, int width, int height) {
        mContentViewCore.getWebContents().requestSmartClipExtract(
                x, y, width, height, mContentViewCore.getRenderCoordinates());
    }

    // Implements SmartClipProvider
    @Override
    public void setSmartClipResultHandler(final Handler resultHandler) {
        mContentViewCore.getWebContents().setSmartClipResultHandler(resultHandler);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    //              Start Implementation of ContentViewCore.InternalAccessDelegate               //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public boolean super_onKeyUp(int keyCode, KeyEvent event) {
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean super_dispatchKeyEvent(KeyEvent event) {
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean super_onGenericMotionEvent(MotionEvent event) {
        return super.onGenericMotionEvent(event);
    }

    @Override
    public void super_onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public boolean super_awakenScrollBars(int startDelay, boolean invalidate) {
        return super.awakenScrollBars(startDelay, invalidate);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    //                End Implementation of ContentViewCore.InternalAccessDelegate               //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    private static class ContentViewApi23 extends ContentView {
        public ContentViewApi23(Context context, ContentViewCore cvc) {
            super(context, cvc);
        }

        @Override
        public void onProvideVirtualStructure(final ViewStructure structure) {
            mContentViewCore.onProvideVirtualStructure(structure, false);
        }
    }
}

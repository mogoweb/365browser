// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.MotionEvent.PointerCoords;
import android.view.MotionEvent.PointerProperties;
import android.view.View;

import org.chromium.base.ContextUtils;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.ui.display.DisplayAndroid;

/**
 * Provides a Java-side implementation for injecting synthetic touch events.
 */
@JNINamespace("content")
public class MotionEventSynthesizer {
    private static final int MAX_NUM_POINTERS = 16;

    private static final int ACTION_START = 0;
    private static final int ACTION_MOVE = 1;
    private static final int ACTION_CANCEL = 2;
    private static final int ACTION_END = 3;
    private static final int ACTION_SCROLL = 4;
    private static final int ACTION_HOVER_ENTER = 5;
    private static final int ACTION_HOVER_EXIT = 6;
    private static final int ACTION_HOVER_MOVE = 7;

    private final View mTarget;
    private final WindowAndroidProvider mWindowProvider;
    private final PointerProperties[] mPointerProperties;
    private final PointerCoords[] mPointerCoords;
    private long mDownTimeInMs;

    public MotionEventSynthesizer(View target, WindowAndroidProvider windowProvider) {
        mTarget = target;
        mWindowProvider = windowProvider;
        mPointerProperties = new PointerProperties[MAX_NUM_POINTERS];
        mPointerCoords = new PointerCoords[MAX_NUM_POINTERS];
    }

    // Guaranteed to return a non-null DisplayAndroid.
    private DisplayAndroid getDisplay() {
        if (mWindowProvider != null && mWindowProvider.getWindowAndroid() != null) {
            return mWindowProvider.getWindowAndroid().getDisplay();
        }
        return DisplayAndroid.getNonMultiDisplay(ContextUtils.getApplicationContext());
    }

    @CalledByNative
    void setPointer(int index, int x, int y, int id) {
        assert (0 <= index && index < MAX_NUM_POINTERS);

        // Convert coordinates from density independent pixels to density dependent pixels.
        float scaleFactor = getDisplay().getDipScale();

        PointerCoords coords = new PointerCoords();
        coords.x = scaleFactor * x;
        coords.y = scaleFactor * y;
        coords.pressure = 1.0f;
        mPointerCoords[index] = coords;

        PointerProperties properties = new PointerProperties();
        properties.id = id;
        mPointerProperties[index] = properties;
    }

    @CalledByNative
    void setScrollDeltas(int x, int y, int dx, int dy) {
        setPointer(0, x, y, 0);
        // Convert coordinates from density independent pixels to density dependent pixels.
        float scaleFactor = getDisplay().getDipScale();
        mPointerCoords[0].setAxisValue(MotionEvent.AXIS_HSCROLL, scaleFactor * dx);
        mPointerCoords[0].setAxisValue(MotionEvent.AXIS_VSCROLL, scaleFactor * dy);
    }

    @CalledByNative
    void inject(int action, int pointerCount, long timeInMs) {
        switch (action) {
            case ACTION_START: {
                mDownTimeInMs = timeInMs;
                MotionEvent event = MotionEvent.obtain(
                        mDownTimeInMs, timeInMs, MotionEvent.ACTION_DOWN, 1,
                        mPointerProperties, mPointerCoords,
                        0, 0, 1, 1, 0, 0, 0, 0);
                mTarget.dispatchTouchEvent(event);
                event.recycle();

                if (pointerCount > 1) {
                    event = MotionEvent.obtain(
                            mDownTimeInMs, timeInMs,
                            MotionEvent.ACTION_POINTER_DOWN, pointerCount,
                            mPointerProperties, mPointerCoords,
                            0, 0, 1, 1, 0, 0, 0, 0);
                    mTarget.dispatchTouchEvent(event);
                    event.recycle();
                }
                break;
            }
            case ACTION_MOVE: {
                MotionEvent event = MotionEvent.obtain(mDownTimeInMs, timeInMs,
                        MotionEvent.ACTION_MOVE,
                        pointerCount, mPointerProperties, mPointerCoords,
                        0, 0, 1, 1, 0, 0, 0, 0);
                mTarget.dispatchTouchEvent(event);
                event.recycle();
                break;
            }
            case ACTION_CANCEL: {
                MotionEvent event = MotionEvent.obtain(
                        mDownTimeInMs, timeInMs, MotionEvent.ACTION_CANCEL, 1,
                        mPointerProperties, mPointerCoords,
                        0, 0, 1, 1, 0, 0, 0, 0);
                mTarget.dispatchTouchEvent(event);
                event.recycle();
                break;
            }
            case ACTION_END: {
                if (pointerCount > 1) {
                    MotionEvent event = MotionEvent.obtain(
                            mDownTimeInMs, timeInMs, MotionEvent.ACTION_POINTER_UP,
                            pointerCount, mPointerProperties, mPointerCoords,
                            0, 0, 1, 1, 0, 0, 0, 0);
                    mTarget.dispatchTouchEvent(event);
                    event.recycle();
                }

                MotionEvent event = MotionEvent.obtain(
                        mDownTimeInMs, timeInMs, MotionEvent.ACTION_UP, 1,
                        mPointerProperties, mPointerCoords,
                        0, 0, 1, 1, 0, 0, 0, 0);
                mTarget.dispatchTouchEvent(event);
                event.recycle();
                break;
            }
            case ACTION_SCROLL: {
                assert pointerCount == 1;
                MotionEvent event = MotionEvent.obtain(mDownTimeInMs, timeInMs,
                        MotionEvent.ACTION_SCROLL, pointerCount, mPointerProperties, mPointerCoords,
                        0, 0, 1, 1, 0, 0, InputDevice.SOURCE_CLASS_POINTER, 0);
                mTarget.dispatchGenericMotionEvent(event);
                event.recycle();
                break;
            }
            case ACTION_HOVER_ENTER:
            case ACTION_HOVER_EXIT:
            case ACTION_HOVER_MOVE: {
                injectHover(action, pointerCount, timeInMs);
                break;
            }
            default: {
                assert false : "Unreached";
                break;
            }
        }
    }

    private void injectHover(int action, int pointerCount, long timeInMs) {
        assert pointerCount == 1;
        int androidAction = MotionEvent.ACTION_HOVER_ENTER;
        if (ACTION_HOVER_EXIT == action) androidAction = MotionEvent.ACTION_HOVER_EXIT;
        if (ACTION_HOVER_MOVE == action) androidAction = MotionEvent.ACTION_HOVER_MOVE;
        MotionEvent event = MotionEvent.obtain(mDownTimeInMs, timeInMs, androidAction, pointerCount,
                mPointerProperties, mPointerCoords, 0, 0, 1, 1, 0, 0,
                InputDevice.SOURCE_CLASS_POINTER, 0);
        mTarget.dispatchGenericMotionEvent(event);
        event.recycle();
    }
}

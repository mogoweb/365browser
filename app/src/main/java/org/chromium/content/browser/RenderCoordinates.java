// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.content.Context;
import android.util.TypedValue;

import org.chromium.base.VisibleForTesting;

import java.lang.ref.WeakReference;

/**
 * Cached copy of all positions and scales (CSS-to-DIP-to-physical pixels)
 * reported from the renderer.
 * Provides wrappers and a utility class to help with coordinate transforms on the client side.
 * Provides the internally-visible set of update methods (called from ContentViewCore).
 *
 * Unless stated otherwise, all coordinates are in CSS (document) coordinate space.
 */
public class RenderCoordinates {

    // Scroll offset from the native in CSS.
    private float mScrollXCss;
    private float mScrollYCss;

    // Content size from native in CSS.
    private float mContentWidthCss;
    private float mContentHeightCss;

    // Last-frame render-reported viewport size in CSS.
    private float mLastFrameViewportWidthCss;
    private float mLastFrameViewportHeightCss;

    // Cached page scale factor from native.
    private float mPageScaleFactor = 1.0f;
    private float mMinPageScaleFactor = 1.0f;
    private float mMaxPageScaleFactor = 1.0f;

    // Cached device density.
    private float mDeviceScaleFactor = 1.0f;

    // Multiplier that determines how many (device) pixels to scroll per mouse
    // wheel tick. Defaults to the preferred list item height.
    private float mWheelScrollFactor;

    private float mTopContentOffsetYPix;

    private boolean mHasFrameInfo;

    // Internally-visible set of update methods (used by ContentViewCore).
    void reset() {
        mScrollXCss = mScrollYCss = 0;
        mPageScaleFactor = 1.0f;
        mHasFrameInfo = false;
    }

    void updateContentSizeCss(float contentWidthCss, float contentHeightCss) {
        mContentWidthCss = contentWidthCss;
        mContentHeightCss = contentHeightCss;
    }

    void setDeviceScaleFactor(float dipScale, WeakReference<Context> displayContext) {
        mDeviceScaleFactor = dipScale;

        // The wheel scroll factor depends on the theme in the context.
        // This code assumes that the theme won't change between this call and
        // getWheelScrollFactor().

        Context context = displayContext.get();
        TypedValue outValue = new TypedValue();
        // This is the same attribute used by Android Views to scale wheel
        // event motion into scroll deltas.
        if (context != null && context.getTheme().resolveAttribute(
                android.R.attr.listPreferredItemHeight, outValue, true)) {
            mWheelScrollFactor = outValue.getDimension(context.getResources().getDisplayMetrics());
        } else {
            // If attribute retrieval fails, just use a sensible default.
            mWheelScrollFactor = 64 * mDeviceScaleFactor;
        }
    }

    void updateFrameInfo(float scrollXCss, float scrollYCss, float contentWidthCss,
            float contentHeightCss, float viewportWidthCss, float viewportHeightCss,
            float pageScaleFactor, float minPageScaleFactor, float maxPageScaleFactor,
            float contentOffsetYPix) {
        mScrollXCss = scrollXCss;
        mScrollYCss = scrollYCss;
        mPageScaleFactor = pageScaleFactor;
        mMinPageScaleFactor = minPageScaleFactor;
        mMaxPageScaleFactor = maxPageScaleFactor;
        mTopContentOffsetYPix = contentOffsetYPix;

        updateContentSizeCss(contentWidthCss, contentHeightCss);
        mLastFrameViewportWidthCss = viewportWidthCss;
        mLastFrameViewportHeightCss = viewportHeightCss;

        mHasFrameInfo = true;
    }

    /**
     * Sets several fields for unit test. (used by {@link CursorAnchorInfoControllerTest}).
     * @param deviceScaleFactor Device scale factor (maps DIP pixels to physical pixels).
     * @param contentOffsetYPix Physical on-screen Y offset amount below the browser controls.
     */
    @VisibleForTesting
    public void setFrameInfoForTest(float deviceScaleFactor, float contentOffsetYPix) {
        reset();
        mDeviceScaleFactor = deviceScaleFactor;
        mTopContentOffsetYPix = contentOffsetYPix;
    }

    /**
     * Handles conversion of a point from window-relative-local-dip or screen-pix
     * to document-absolute-CSS space and vice versa.
     */
    public class NormalizedPoint {
        private float mXAbsoluteCss, mYAbsoluteCss;

        private NormalizedPoint() {
        }

        /**
         * @return Absolute CSS (document) X coordinate of the point.
         */
        public float getXAbsoluteCss() {
            return mXAbsoluteCss;
        }

        /**
         * @return Absolute CSS (document) Y coordinate of the point.
         */
        public float getYAbsoluteCss() {
            return mYAbsoluteCss;
        }

        /**
         * @return Local device-scale-unadjusted X coordinate of the point.
         */
        public float getXLocalDip() {
            return (mXAbsoluteCss - mScrollXCss) * mPageScaleFactor;
        }

        /**
         * @return Local device-scale-unadjusted Y coordinate of the point.
         */
        public float getYLocalDip() {
            return (mYAbsoluteCss - mScrollYCss) * mPageScaleFactor;
        }

        /**
         * @return Physical (screen) X coordinate of the point.
         */
        public float getXPix() {
            return getXLocalDip() * mDeviceScaleFactor;
        }

        /**
         * @return Physical (screen) Y coordinate of the point.
         */
        public float getYPix() {
            return getYLocalDip() * mDeviceScaleFactor + mTopContentOffsetYPix;
        }

        /**
         * Sets the point to the given absolute CSS (document) coordinates.
         */
        public void setAbsoluteCss(float xCss, float yCss) {
            mXAbsoluteCss = xCss;
            mYAbsoluteCss = yCss;
        }

        /**
         * Sets the point to the given local device-scale-unadjusted coordinates.
         */
        public void setLocalDip(float xDip, float yDip) {
            setAbsoluteCss(
                    xDip / mPageScaleFactor + mScrollXCss,
                    yDip / mPageScaleFactor + mScrollYCss);
        }

        /**
         * Sets the point to the given physical (screen) coordinates.
         */
        public void setScreen(float xPix, float yPix) {
            setLocalDip(xPix / mDeviceScaleFactor, yPix / mDeviceScaleFactor);
        }
    }

    /**
     * @return A helper to convert a point between between absolute CSS and local DIP spaces.
     */
    public NormalizedPoint createNormalizedPoint() {
        return new NormalizedPoint();
    }

    /**
     * @return Horizontal scroll offset in CSS pixels.
     */
    public float getScrollX() {
        return mScrollXCss;
    }

    /**
     * @return Vertical scroll offset in CSS pixels.
     */
    public float getScrollY() {
        return mScrollYCss;
    }

    /**
     * @return Horizontal scroll offset in physical pixels.
     */
    public float getScrollXPix() {
        return fromLocalCssToPix(mScrollXCss);
    }

    /**
     * @return Vertical scroll offset in physical pixels.
     */
    public float getScrollYPix() {
        return fromLocalCssToPix(mScrollYCss);
    }

    /**
     * @return Horizontal scroll offset in physical pixels (approx, integer).
     */
    public int getScrollXPixInt() {
        return (int) Math.floor(getScrollXPix());
    }

    /**
     * @return Vertical scroll offset in physical pixels (approx, integer).
     */
    public int getScrollYPixInt() {
        return (int) Math.floor(getScrollYPix());
    }

    /**
     * @return Width of the content in CSS pixels.
     */
    public float getContentWidthCss() {
        return mContentWidthCss;
    }

    /**
     * @return Height of the content in CSS pixels.
     */
    public float getContentHeightCss() {
        return mContentHeightCss;
    }

    /**
     * @return Approximate width of the content in physical pixels.
     */
    public float getContentWidthPix() {
        return fromLocalCssToPix(mContentWidthCss);
    }

    /**
     * @return Approximate height of the content in physical pixels.
     */
    public float getContentHeightPix() {
        return fromLocalCssToPix(mContentHeightCss);
    }

    /**
     * @return Approximate width of the content in physical pixels (integer).
     */
    public int getContentWidthPixInt() {
        return (int) Math.ceil(getContentWidthPix());
    }

    /**
     * @return Approximate height of the content in physical pixels (integer).
     */
    public int getContentHeightPixInt() {
        return (int) Math.ceil(getContentHeightPix());
    }

    /**
     * @return Render-reported width of the viewport in CSS pixels.
     */
    public float getLastFrameViewportWidthCss() {
        return mLastFrameViewportWidthCss;
    }

    /**
     * @return Render-reported height of the viewport in CSS pixels.
     */
    public float getLastFrameViewportHeightCss() {
        return mLastFrameViewportHeightCss;
    }

    /**
     * @return Render-reported width of the viewport in physical pixels (approximate).
     */
    public float getLastFrameViewportWidthPix() {
        return fromLocalCssToPix(mLastFrameViewportWidthCss);
    }

    /**
     * @return Render-reported height of the viewport in physical pixels (approximate).
     */
    public float getLastFrameViewportHeightPix() {
        return fromLocalCssToPix(mLastFrameViewportHeightCss);
    }

    /**
     * @return Render-reported width of the viewport in physical pixels (approx, integer).
     */
    public int getLastFrameViewportWidthPixInt() {
        return (int) Math.ceil(getLastFrameViewportWidthPix());
    }

    /**
     * @return Render-reported height of the viewport in physical pixels (approx, integer).
     */
    public int getLastFrameViewportHeightPixInt() {
        return (int) Math.ceil(getLastFrameViewportHeightPix());
    }

    /**
     * @return The Physical on-screen Y offset amount below the browser controls.
     */
    public float getContentOffsetYPix() {
        return mTopContentOffsetYPix;
    }

    /**
     * @return Current page scale factor (maps CSS pixels to DIP pixels).
     */
    public float getPageScaleFactor() {
        return mPageScaleFactor;
    }

    /**
     * @return Minimum page scale factor to be used with the content.
     */
    public float getMinPageScaleFactor() {
        return mMinPageScaleFactor;
    }

    /**
     * @return Maximum page scale factor to be used with the content.
     */
    public float getMaxPageScaleFactor() {
        return mMaxPageScaleFactor;
    }

    /**
     * @return Current device scale factor (maps DIP pixels to physical pixels).
     */
    public float getDeviceScaleFactor() {
        return mDeviceScaleFactor;
    }

    /**
     * @return Current wheel scroll factor (physical pixels per mouse scroll click).
     */
    public float getWheelScrollFactor() {
        return mWheelScrollFactor;
    }

    /**
     * @return Maximum possible horizontal scroll in physical pixels.
     */
    public float getMaxHorizontalScrollPix() {
        return getContentWidthPix() - getLastFrameViewportWidthPix();
    }

    /**
     * @return Maximum possible vertical scroll in physical pixels.
     */
    public float getMaxVerticalScrollPix() {
        return getContentHeightPix() - getLastFrameViewportHeightPix();
    }

    /**
     * @return Maximum possible horizontal scroll in physical pixels (approx, integer).
     */
    public int getMaxHorizontalScrollPixInt() {
        return (int) Math.floor(getMaxHorizontalScrollPix());
    }

    /**
     * @return Maximum possible vertical scroll in physical pixels (approx, integer).
     */
    public int getMaxVerticalScrollPixInt() {
        return (int) Math.floor(getMaxVerticalScrollPix());
    }

    /**
     * @return Whether a frame info update has been received.
     */
    public boolean hasFrameInfo() {
        return mHasFrameInfo;
    }

    /**
     * @return Physical on-screen coordinate converted to local DIP.
     */
    public float fromPixToDip(float pix) {
        return pix / mDeviceScaleFactor;
    }

    /**
     * @return Local DIP converted to physical coordinates.
     */
    public float fromDipToPix(float dip) {
        return dip * mDeviceScaleFactor;
    }

    /**
     * @return Physical coordinate converted to local CSS.
     */
    public float fromPixToLocalCss(float pix) {
        return pix / (mDeviceScaleFactor * mPageScaleFactor);
    }

    /**
     * @return Local CSS converted to physical coordinates.
     */
    public float fromLocalCssToPix(float css) {
        return css * mPageScaleFactor * mDeviceScaleFactor;
    }
}

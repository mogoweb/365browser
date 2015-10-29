// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.graphics.Rect;
import android.graphics.RectF;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.blink_public.platform.WebDisplayMode;
import org.chromium.chrome.browser.findinpage.FindMatchRectsDetails;
import org.chromium.chrome.browser.findinpage.FindNotificationDetails;
import org.chromium.components.web_contents_delegate_android.WebContentsDelegateAndroid;
import org.chromium.content_public.browser.WebContents;

/**
 * Chromium Android specific WebContentsDelegate.
 * This file is the Java version of the native class of the same name.
 * It should contain empty WebContentsDelegate methods to be implemented by the embedder.
 * These methods belong to the Chromium Android port but not to WebView.
 */
public class ChromeWebContentsDelegateAndroid extends WebContentsDelegateAndroid {

    private FindResultListener mFindResultListener;

    private FindMatchRectsListener mFindMatchRectsListener = null;

    private int mDisplayMode = WebDisplayMode.Browser;

    /**
     * Listener to be notified when a find result is received.
     */
    public interface FindResultListener {
        public void onFindResult(FindNotificationDetails result);
    }

    /**
     * Listener to be notified when the rects corresponding to find matches are received.
     */
    public interface FindMatchRectsListener {
        public void onFindMatchRects(FindMatchRectsDetails result);
    }

    /**
     * Sets the current display mode which can be queried using media queries.
     * @param displayMode A value from {@link org.chromium.blink_public.platform.WebDisplayMode}.
     */
    public void setDisplayMode(int displayMode) {
        mDisplayMode = displayMode;
    }

    @CalledByNative
    private int getDisplayMode() {
        return mDisplayMode;
    }

    @CalledByNative
    private void onFindResultAvailable(FindNotificationDetails result) {
        if (mFindResultListener != null) {
            mFindResultListener.onFindResult(result);
        }
    }

    @CalledByNative
    private void onFindMatchRectsAvailable(FindMatchRectsDetails result) {
        if (mFindMatchRectsListener != null) {
            mFindMatchRectsListener.onFindMatchRects(result);
        }
    }

    /** Register to receive the results of startFinding calls. */
    public void setFindResultListener(FindResultListener listener) {
        mFindResultListener = listener;
    }

    /** Register to receive the results of requestFindMatchRects calls. */
    public void setFindMatchRectsListener(FindMatchRectsListener listener) {
        mFindMatchRectsListener = listener;
    }

    @CalledByNative
    public boolean shouldResumeRequestsForCreatedWindow() {
        return true;
    }

    @CalledByNative
    public boolean addNewContents(WebContents sourceWebContents, WebContents webContents,
            int disposition, Rect initialPosition, boolean userGesture) {
        return false;
    }

    @Override
    public void webContentsCreated(WebContents sourceWebContents, long openerRenderFrameId,
            String frameName, String targetUrl, WebContents newWebContents) {
    }

    // Helper functions used to create types that are part of the public interface
    @CalledByNative
    private static Rect createRect(int x, int y, int right, int bottom) {
        return new Rect(x, y, right, bottom);
    }

    @CalledByNative
    private static RectF createRectF(float x, float y, float right, float bottom) {
        return new RectF(x, y, right, bottom);
    }

    @CalledByNative
    private static FindNotificationDetails createFindNotificationDetails(
            int numberOfMatches, Rect rendererSelectionRect,
            int activeMatchOrdinal, boolean finalUpdate) {
        return new FindNotificationDetails(numberOfMatches, rendererSelectionRect,
                activeMatchOrdinal, finalUpdate);
    }

    @CalledByNative
    private static FindMatchRectsDetails createFindMatchRectsDetails(
            int version, int numRects, RectF activeRect) {
        return new FindMatchRectsDetails(version, numRects, activeRect);
    }

    @CalledByNative
    private static void setMatchRectByIndex(
            FindMatchRectsDetails findMatchRectsDetails, int index, RectF rect) {
        findMatchRectsDetails.rects[index] = rect;
    }

    protected static native boolean nativeIsCapturingAudio(WebContents webContents);
    protected static native boolean nativeIsCapturingVideo(WebContents webContents);
    protected static native boolean nativeHasAudibleAudio(WebContents webContents);
}

// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tab;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.util.MathUtils;
import org.chromium.content.browser.ContentReadbackHandler;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.content_public.browser.GestureStateListener;
import org.chromium.content_public.browser.WebContents;
import org.chromium.ui.base.WindowAndroid;

/**
 * Handles capturing most visited thumbnails for a tab.
 */
public class ThumbnailTabHelper {

    private static final String TAG = "ThumbnailTabHelper";

    /** The general motivation for this value is giving the scrollbar fadeout
     *  animation sufficient time to finish before the capture executes. */
    private static final int THUMBNAIL_CAPTURE_DELAY_MS = 350;

    private final Tab mTab;
    private final Handler mHandler;

    private final int mThumbnailWidth;
    private final int mThumbnailHeight;

    private ContentViewCore mContentViewCore;
    private boolean mThumbnailCapturedForLoad;
    private boolean mIsRenderViewHostReady;
    private boolean mWasRenderViewHostReady;

    private final Runnable mThumbnailRunnable = new Runnable() {
        @Override
        public void run() {
            // http://crbug.com/461506 : Do not get thumbnail unless render view host is ready.
            if (!mIsRenderViewHostReady) return;

            if (mThumbnailCapturedForLoad) return;
            // Prevent redundant thumbnail capture attempts.
            mThumbnailCapturedForLoad = true;
            if (!canUpdateHistoryThumbnail()) {
                // Allow a hidden tab to re-attempt capture in the future via |show()|.
                mThumbnailCapturedForLoad = !mTab.isHidden();
                return;
            }
            if (!shouldUpdateThumbnail()) return;

            int snapshotWidth = Math.min(mTab.getWidth(), mThumbnailWidth);
            int snapshotHeight = Math.min(mTab.getHeight(), mThumbnailHeight);

            ContentReadbackHandler readbackHandler = getActivity().getContentReadbackHandler();
            if (readbackHandler == null || mTab.getContentViewCore() == null) return;
            final String requestedUrl = mTab.getUrl();
            ContentReadbackHandler.GetBitmapCallback bitmapCallback =
                    new ContentReadbackHandler.GetBitmapCallback() {
                        @Override
                        public void onFinishGetBitmap(Bitmap bitmap, int response) {
                            // Ensure that the URLs match for the requested page, and ensure
                            // that the page is still valid for thumbnail capturing (i.e.
                            // not showing an error page).
                            if (bitmap == null
                                    || !TextUtils.equals(requestedUrl, mTab.getUrl())
                                    || !mThumbnailCapturedForLoad
                                    || !canUpdateHistoryThumbnail()) {
                                return;
                            }
                            updateHistoryThumbnail(bitmap);
                            bitmap.recycle();
                        }
                    };
            readbackHandler.getContentBitmapAsync(1, new Rect(0, 0, snapshotWidth, snapshotHeight),
                    mTab.getContentViewCore(), Bitmap.Config.ARGB_8888, bitmapCallback);
        }
    };

    private final TabObserver mTabObserver = new EmptyTabObserver() {
        @Override
        public void onContentChanged(Tab tab) {
            ThumbnailTabHelper.this.onContentChanged();
        }

        @Override
        public void onCrash(Tab tab, boolean sadTabShown) {
            cancelThumbnailCapture();
        }

        @Override
        public void onPageLoadStarted(Tab tab, String url) {
            cancelThumbnailCapture();
            mThumbnailCapturedForLoad = false;
        }

        @Override
        public void onPageLoadFinished(Tab tab) {
            rescheduleThumbnailCapture();
        }

        @Override
        public void onPageLoadFailed(Tab tab, int errorCode) {
            cancelThumbnailCapture();
        }

        @Override
        public void onShown(Tab tab) {
            // For tabs opened in the background, they may finish loading prior to becoming visible
            // and the thumbnail capture triggered as part of load finish will be skipped as the
            // tab has nothing rendered.  To handle this case, we also attempt thumbnail capture
            // when showing the tab to give it a better chance to have valid content.
            rescheduleThumbnailCapture();
        }

        @Override
        public void onClosingStateChanged(Tab tab, boolean closing) {
            if (closing) cancelThumbnailCapture();
        }

        @Override
        public void onDestroyed(Tab tab) {
            mTab.removeObserver(mTabObserver);
            if (mContentViewCore != null) {
                mContentViewCore.removeGestureStateListener(mGestureListener);
                mContentViewCore = null;
            }
        }

        @Override
        public void onDidStartProvisionalLoadForFrame(
                Tab tab, long frameId, long parentFrameId, boolean isMainFrame, String validatedUrl,
                boolean isErrorPage, boolean isIframeSrcdoc) {
            if (isMainFrame) {
                mWasRenderViewHostReady = mIsRenderViewHostReady;
                mIsRenderViewHostReady = false;
            }
        }

        @Override
        public void onDidFailLoad(
                Tab tab, boolean isProvisionalLoad, boolean isMainFrame, int errorCode,
                String description, String failingUrl) {
            // For a case that URL overriding happens, we should recover |mIsRenderViewHostReady| to
            // its old value to enable capturing thumbnail of the current page.
            // If this failure shows an error page, capturing thumbnail will be denied anyway in
            // canUpdateHistoryThumbnail().
            if (isProvisionalLoad && isMainFrame) mIsRenderViewHostReady = mWasRenderViewHostReady;
        }

        @Override
        public void onDidCommitProvisionalLoadForFrame(
                Tab tab, long frameId, boolean isMainFrame, String url, int transitionType) {
            if (isMainFrame) mIsRenderViewHostReady = true;
        }
    };

    private GestureStateListener mGestureListener = new GestureStateListener() {
        @Override
        public void onFlingStartGesture(int vx, int vy, int scrollOffsetY, int scrollExtentY) {
            cancelThumbnailCapture();
        }

        @Override
        public void onFlingEndGesture(int scrollOffsetY, int scrollExtentY) {
            rescheduleThumbnailCapture();
        }

        @Override
        public void onScrollStarted(int scrollOffsetY, int scrollExtentY) {
            cancelThumbnailCapture();
        }

        @Override
        public void onScrollEnded(int scrollOffsetY, int scrollExtentY) {
            rescheduleThumbnailCapture();
        }
    };

    /**
     * Creates a thumbnail tab helper for the given tab.
     * @param tab The Tab whose thumbnails will be generated by this helper.
     */
    public static void createForTab(Tab tab) {
        new ThumbnailTabHelper(tab);
    }

    /**
     * Constructs the thumbnail tab helper for a given Tab.
     * @param tab The Tab whose thumbnails will be generated by this helper.
     */
    private ThumbnailTabHelper(Tab tab) {
        mTab = tab;
        mTab.addObserver(mTabObserver);

        mHandler = new Handler();

        Resources res = tab.getWindowAndroid().getApplicationContext().getResources();
        mThumbnailWidth = res.getDimensionPixelSize(R.dimen.most_visited_thumbnail_width);
        mThumbnailHeight = res.getDimensionPixelSize(R.dimen.most_visited_thumbnail_height);

        onContentChanged();
    }

    private void onContentChanged() {
        if (mContentViewCore != null) {
            mContentViewCore.removeGestureStateListener(mGestureListener);
        }

        mContentViewCore = mTab.getContentViewCore();
        if (mContentViewCore != null) {
            mContentViewCore.addGestureStateListener(mGestureListener);
            nativeInitThumbnailHelper(mContentViewCore.getWebContents());
        }
    }

    private ChromeActivity getActivity() {
        WindowAndroid window = mTab.getWindowAndroid();
        return (ChromeActivity) window.getActivity().get();
    }

    private void cancelThumbnailCapture() {
        mHandler.removeCallbacks(mThumbnailRunnable);
    }

    private void rescheduleThumbnailCapture() {
        if (mThumbnailCapturedForLoad) return;
        cancelThumbnailCapture();
        // Capture will be rescheduled when the GestureStateListener receives a
        // scroll or fling end notification.
        if (mTab.getContentViewCore() != null
                && mTab.getContentViewCore().isScrollInProgress()) {
            return;
        }
        mHandler.postDelayed(mThumbnailRunnable, THUMBNAIL_CAPTURE_DELAY_MS);
    }

    private boolean shouldUpdateThumbnail() {
        return nativeShouldUpdateThumbnail(mTab.getProfile(), mTab.getUrl());
    }

    private void updateThumbnail(Bitmap bitmap) {
        if (mTab.getContentViewCore() != null) {
            final boolean atTop = mTab.getContentViewCore().computeVerticalScrollOffset() == 0;
            nativeUpdateThumbnail(mTab.getWebContents(), bitmap, atTop);
        }
    }

    private boolean canUpdateHistoryThumbnail() {
        String url = mTab.getUrl();
        if (url.startsWith(UrlConstants.CHROME_SCHEME)
                || url.startsWith(UrlConstants.CHROME_NATIVE_SCHEME)) {
            return false;
        }
        return mTab.isReady()
                && !mTab.isShowingErrorPage()
                && !mTab.isHidden()
                && !mTab.isShowingSadTab()
                && !mTab.isShowingInterstitialPage()
                && mTab.getProgress() == 100
                && mTab.getWidth() > 0
                && mTab.getHeight() > 0;
    }

    private void updateHistoryThumbnail(Bitmap bitmap) {
        if (mTab.isIncognito()) return;

        // TODO(yusufo): It will probably be faster and more efficient on resources to do this on
        // the native side, but the thumbnail_generator code has to be refactored a bit to allow
        // creating a downsized version of a bitmap progressively.
        if (bitmap.getWidth() != mThumbnailWidth
                || bitmap.getHeight() != mThumbnailHeight
                || bitmap.getConfig() != Config.ARGB_8888) {
            try {
                int[] dim = new int[] {
                        bitmap.getWidth(), bitmap.getHeight()
                };
                // If the thumbnail size is small compared to the bitmap size downsize in
                // two stages. This makes the final quality better.
                float scale = Math.max(
                        (float) mThumbnailWidth / dim[0],
                        (float) mThumbnailHeight / dim[1]);
                int adjustedWidth = (scale < 1)
                        ? mThumbnailWidth * (int) (1 / Math.sqrt(scale)) : mThumbnailWidth;
                int adjustedHeight = (scale < 1)
                        ? mThumbnailHeight * (int) (1 / Math.sqrt(scale)) : mThumbnailHeight;
                scale = MathUtils.scaleToFitTargetSize(dim, adjustedWidth, adjustedHeight);
                // Horizontally center the source bitmap in the final result.
                float leftOffset = (adjustedWidth - dim[0]) / 2.0f / scale;
                Bitmap tmpBitmap = Bitmap.createBitmap(adjustedWidth,
                        adjustedHeight, Config.ARGB_8888);
                Canvas c = new Canvas(tmpBitmap);
                c.scale(scale, scale);
                c.drawBitmap(bitmap, leftOffset, 0, new Paint(Paint.FILTER_BITMAP_FLAG));
                if (scale < 1) {
                    tmpBitmap = Bitmap.createScaledBitmap(tmpBitmap,
                            mThumbnailWidth, mThumbnailHeight, true);
                }
                updateThumbnail(tmpBitmap);
                tmpBitmap.recycle();
            } catch (OutOfMemoryError ex) {
                Log.w(TAG, "OutOfMemoryError while updating the history thumbnail.");
            }
        } else {
            updateThumbnail(bitmap);
        }
    }

    private static native void nativeInitThumbnailHelper(WebContents webContents);
    private static native void nativeUpdateThumbnail(
            WebContents webContents, Bitmap bitmap, boolean atTop);
    private static native boolean nativeShouldUpdateThumbnail(Profile profile, String url);
}

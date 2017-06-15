// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.widget.textbubble;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Rect;
import android.os.Handler;
import android.support.annotation.StringRes;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.FrameLayout;
import android.widget.PopupWindow;
import android.widget.PopupWindow.OnDismissListener;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.ObserverList;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.util.AccessibilityUtil;
import org.chromium.chrome.browser.util.MathUtils;

/**
 * UI component that handles showing a text callout bubble.  Positioning this bubble happens through
 * calls to {@link #setAnchorRect(Rect)}.  This should be called at least once before the
 * {@link #show()} call.  To attach to a {@link View} see {@link ViewAnchoredTextBubble}.
 */
public class TextBubble implements OnTouchListener {
    /**
     * Specifies no limit to the popup duration.
     * @see #setAutoDismissTimeout(long)
     */
    public static final long NO_TIMEOUT = 0;

    // Cache Rect objects for querying View and Screen coordinate APIs.
    private final Rect mCachedPaddingRect = new Rect();
    private final Rect mCachedWindowRect = new Rect();

    private final Context mContext;
    private final Handler mHandler;
    private final View mRootView;

    /** The margin to add to the text bubble so it doesn't bump against the edges of the screen. */
    private final int mMarginPx;

    /** The actual {@link PopupWindow}.  Internalized to prevent API leakage. */
    private final PopupWindow mPopupWindow;

    /** The {@link Drawable} that is responsible for drawing the bubble and the arrow. */
    private final ArrowBubbleDrawable mDrawable;

    /** The {@link Rect} to anchor the bubble to in screen space. */
    private final Rect mAnchorRect = new Rect();

    private final Runnable mDismissRunnable = new Runnable() {
        @Override
        public void run() {
            if (mPopupWindow.isShowing()) dismiss();
        }
    };

    private final OnDismissListener mDismissListener = new OnDismissListener() {
        @Override
        public void onDismiss() {
            if (mIgnoreDismissal) return;

            mHandler.removeCallbacks(mDismissRunnable);
            for (OnDismissListener listener : mDismissListeners) listener.onDismiss();
        }
    };

    /**
     * How long to wait before automatically dismissing the bubble.  {@link #NO_TIMEOUT} is the
     * default and means the bubble will stay visible indefinitely.
     */
    private long mAutoDismissTimeoutMs;
    private boolean mDismissOnTouchInteraction;

    // Pass through for the internal PopupWindow.  This class needs to intercept these for API
    // purposes, but they are still useful to callers.
    private ObserverList<OnDismissListener> mDismissListeners = new ObserverList<>();
    private OnTouchListener mTouchListener;

    // Positioning/sizing coordinates for the popup bubble.
    private int mX;
    private int mY;
    private int mWidth;
    private int mHeight;

    /**
     * Tracks whether or not we are in the process of updating the bubble, which might include a
     * dismiss and show.  In that case we don't want to let the world know we're dismissing because
     * it's only temporary.
     */
    private boolean mIgnoreDismissal;

    // Content specific variables.
    /** The resource id for the string to show in the bubble. */
    @StringRes
    private final int mStringId;

    /** The resource id for the accessibility string associated with the bubble. */
    @StringRes
    private final int mAccessibilityStringId;

    /**
     * Constructs a {@link TextBubble} instance.
     * @param context  Context to draw resources from.
     * @param rootView The {@link View} to use for size calculations and for display.
     * @param stringId The id of the string resource for the text that should be shown.
     * @param accessibilityStringId The id of the string resource of the accessibility text.
     */
    public TextBubble(Context context, View rootView, @StringRes int stringId,
            @StringRes int accessibilityStringId) {
        mContext = context;
        mRootView = rootView.getRootView();
        mStringId = stringId;
        mAccessibilityStringId = accessibilityStringId;
        mPopupWindow = new PopupWindow(mContext);
        mDrawable = new ArrowBubbleDrawable(context);
        mHandler = new Handler();

        mPopupWindow.setWidth(ViewGroup.LayoutParams.WRAP_CONTENT);
        mPopupWindow.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mPopupWindow.setBackgroundDrawable(mDrawable);
        mPopupWindow.setAnimationStyle(R.style.TextBubbleAnimation);

        mPopupWindow.setTouchInterceptor(this);
        mPopupWindow.setOnDismissListener(mDismissListener);

        mMarginPx = context.getResources().getDimensionPixelSize(R.dimen.text_bubble_margin);

        // Set predefined styles for the TextBubble.
        mDrawable.setBubbleColor(
                ApiCompatibilityUtils.getColor(mContext.getResources(), R.color.google_blue_500));
    }

    /** Shows the bubble. Will have no effect if the bubble is already showing. */
    public void show() {
        if (mPopupWindow.isShowing()) return;

        if (!mPopupWindow.isShowing() && mAutoDismissTimeoutMs != NO_TIMEOUT) {
            mHandler.postDelayed(mDismissRunnable, mAutoDismissTimeoutMs);
        }

        createContentView();
        updateBubbleLayout();
        mPopupWindow.showAtLocation(mRootView, Gravity.TOP | Gravity.START, mX, mY);
    }

    /**
     * Disposes of the bubble.  Will have no effect if the bubble isn't showing.
     * @see PopupWindow#dismiss()
     */
    public void dismiss() {
        mPopupWindow.dismiss();
    }

    /**
     * @param onTouchListener A callback for all touch events being dispatched to the bubble.
     * @see PopupWindow#setTouchInterceptor(OnTouchListener)
     */
    public void setTouchInterceptor(OnTouchListener onTouchListener) {
        mTouchListener = onTouchListener;
    }

    /**
     * @param onDismissListener A listener to be called when the bubble is dismissed.
     * @see PopupWindow#setOnDismissListener(OnDismissListener)
     */
    public void addOnDismissListener(OnDismissListener onDismissListener) {
        mDismissListeners.addObserver(onDismissListener);
    }

    /**
     * @param onDismissListener The listener to remove and not call when the bubble is dismissed.
     * @see PopupWindow#setOnDismissListener(OnDismissListener)
     */
    public void removeOnDismissListener(OnDismissListener onDismissListener) {
        mDismissListeners.removeObserver(onDismissListener);
    }

    /**
     * Updates the timeout that is used to determine when to automatically dismiss the bubble.  If
     * the bubble is already showing, the timeout will start from the time of this call.  Any
     * previous timeouts will be canceled.  {@link #NO_TIMEOUT} is the default value.
     * @param timeoutMs The time (in milliseconds) the bubble should be dismissed after.  Use
     *                  {@link #NO_TIMEOUT} for no timeout.
     */
    public void setAutoDismissTimeout(long timeoutMs) {
        mAutoDismissTimeoutMs = timeoutMs;
        mHandler.removeCallbacks(mDismissRunnable);
        if (mPopupWindow.isShowing() && mAutoDismissTimeoutMs != NO_TIMEOUT) {
            mHandler.postDelayed(mDismissRunnable, mAutoDismissTimeoutMs);
        }
    }

    /**
     * @param dismiss Whether or not to dismiss this bubble when the screen is tapped.  This will
     *                happen for both taps inside and outside the popup.  The default is
     *                {@code false}.
     */
    public void setDismissOnTouchInteraction(boolean dismiss) {
        mDismissOnTouchInteraction = dismiss;
        mPopupWindow.setOutsideTouchable(mDismissOnTouchInteraction);
    }

    /**
     * Sets the {@link Rect} this bubble attaches and orients to.  This will move the bubble if it
     * is already visible, but should be called at least once calling {@link #show()}.
     *
     * @param rect A {@link Rect} object that represents the anchor object's position in screen
     *             space (e.g. if this bubble is backing a {@link View}, see
     *             {@link View#getLocationOnScreen(int[])}.
     */
    public void setAnchorRect(Rect rect) {
        mAnchorRect.set(rect);
        if (mPopupWindow.isShowing()) updateBubbleLayout();
    }

    /**
     * Causes this bubble to position/size itself.  The calculations will happen even if the bubble
     * isn't visible.
     */
    private void updateBubbleLayout() {
        // Determine the size of the text bubble.
        ArrowBubbleDrawable background = (ArrowBubbleDrawable) mPopupWindow.getBackground();
        boolean currentPositionBelow = background.isArrowOnTop();
        boolean preferCurrentOrientation = mPopupWindow.isShowing();

        background.getPadding(mCachedPaddingRect);
        int paddingX = mCachedPaddingRect.left + mCachedPaddingRect.right;
        int paddingY = mCachedPaddingRect.top + mCachedPaddingRect.bottom;

        int maxContentWidth = mRootView.getWidth() - paddingX - mMarginPx * 2;

        // Determine whether or not the bubble should be above or below the anchor.
        // Aggressively try to put it below the anchor.  Put it above only if it would fit better.
        View contentView = mPopupWindow.getContentView();
        int widthSpec = MeasureSpec.makeMeasureSpec(maxContentWidth, MeasureSpec.AT_MOST);
        contentView.measure(widthSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        int idealHeight = contentView.getMeasuredHeight();

        mRootView.getWindowVisibleDisplayFrame(mCachedWindowRect);

        // TODO(dtrainor): This follows the previous logic.  But we should look into if we want to
        // use the root view dimensions instead of the window dimensions here so the bubble can't
        // bleed onto the decorations.
        int spaceAboveAnchor = mAnchorRect.top - mCachedWindowRect.top - paddingY - mMarginPx;
        int spaceBelowAnchor = mCachedWindowRect.bottom - mAnchorRect.bottom - paddingY - mMarginPx;

        // Bias based on the center of the bubble and where it is on the screen.
        boolean idealFitsBelow = idealHeight <= spaceBelowAnchor;
        boolean idealFitsAbove = idealHeight <= spaceAboveAnchor;

        // Position the bubble in the largest available space where it can fit.  This will bias the
        // bubbles to show below the anchor if it will not fit in either place.
        boolean positionBelow =
                (idealFitsBelow && spaceBelowAnchor >= spaceAboveAnchor) || !idealFitsAbove;

        // Override the ideal bubble orientation if we are trying to maintain the current one.
        if (preferCurrentOrientation && currentPositionBelow != positionBelow) {
            if (currentPositionBelow && idealFitsBelow) positionBelow = true;
            if (!currentPositionBelow && idealFitsAbove) positionBelow = false;
        }

        int maxContentHeight = positionBelow ? spaceBelowAnchor : spaceAboveAnchor;
        contentView.measure(
                widthSpec, MeasureSpec.makeMeasureSpec(maxContentHeight, MeasureSpec.AT_MOST));

        mWidth = contentView.getMeasuredWidth() + paddingX;
        mHeight = contentView.getMeasuredHeight() + paddingY;

        // Determine the position of the text bubble and arrow.
        if (positionBelow) {
            mY = mAnchorRect.bottom;
        } else {
            mY = mAnchorRect.top - mHeight;
        }

        mX = mAnchorRect.left + (mAnchorRect.width() - mWidth) / 2 + mMarginPx;
        mX = MathUtils.clamp(mX, mMarginPx, mRootView.getWidth() - mWidth - mMarginPx);
        int arrowXOffset = mAnchorRect.centerX() - mX;

        // Force the anchor to be in a reasonable spot w.r.t. the bubble (not over the corners).
        int minArrowOffset = mDrawable.getArrowLeftSpacing();
        int maxArrowOffset = mWidth - mDrawable.getArrowRightSpacing();
        arrowXOffset = MathUtils.clamp(arrowXOffset, minArrowOffset, maxArrowOffset);

        // TODO(dtrainor): Figure out how to move the arrow and bubble to make things look better.

        mDrawable.setPositionProperties(arrowXOffset, positionBelow);

        if (positionBelow != currentPositionBelow) {
            // This is a hack to deal with the case where the arrow flips between top and bottom.
            // In this case the padding of the background drawable in the PopupWindow changes.
            try {
                mIgnoreDismissal = true;
                mPopupWindow.dismiss();
                mPopupWindow.showAtLocation(mRootView, Gravity.TOP | Gravity.START, mX, mY);
            } finally {
                mIgnoreDismissal = false;
            }
        } else {
            mPopupWindow.update(mX, mY, mWidth, mHeight);
        }
    }

    private void createContentView() {
        if (mPopupWindow.getContentView() != null) return;

        View view = LayoutInflater.from(mContext).inflate(R.layout.textbubble_text, null);
        ((TextView) view)
                .setText(AccessibilityUtil.isAccessibilityEnabled() ? mAccessibilityStringId
                                                                    : mStringId);
        mPopupWindow.setContentView(view);

        // On some versions of Android, the LayoutParams aren't set until after the popup window
        // is shown. Explicitly set the LayoutParams to avoid crashing. See crbug.com/713759.
        view.setLayoutParams(
                new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
    }

    // OnTouchListener implementation.
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        boolean returnValue = mTouchListener != null && mTouchListener.onTouch(v, event);
        if (mDismissOnTouchInteraction) dismiss();
        return returnValue;
    }
}

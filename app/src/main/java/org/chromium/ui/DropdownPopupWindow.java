// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.ui;

import android.content.Context;
import android.graphics.Rect;
import android.util.Log;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.View.OnLayoutChangeListener;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListPopupWindow;
import android.widget.PopupWindow;

import org.chromium.base.ApiCompatibilityUtils;

import java.lang.reflect.Method;

/**
 * The dropdown list popup window.
 */
public class DropdownPopupWindow extends ListPopupWindow {

    private final Context mContext;
    private final View mAnchorView;
    private boolean mRtl;
    private int mInitialSelection = -1;
    private OnLayoutChangeListener mLayoutChangeListener;
    private PopupWindow.OnDismissListener mOnDismissListener;
    private CharSequence mDescription;
    ListAdapter mAdapter;

    /**
     * Creates an DropdownPopupWindow with specified parameters.
     * @param context Application context.
     * @param anchorView Popup view to be anchored.
     */
    public DropdownPopupWindow(Context context, View anchorView) {
        super(context, null, 0, R.style.DropdownPopupWindow);
        mContext = context;
        mAnchorView = anchorView;

        mAnchorView.setId(R.id.dropdown_popup_window);
        mAnchorView.setTag(this);

        mLayoutChangeListener = new OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                    int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if (v == mAnchorView) DropdownPopupWindow.this.show();
            }
        };
        mAnchorView.addOnLayoutChangeListener(mLayoutChangeListener);

        super.setOnDismissListener(new PopupWindow.OnDismissListener() {
            @Override
            public void onDismiss() {
                if (mOnDismissListener != null) {
                    mOnDismissListener.onDismiss();
                }
                mAnchorView.removeOnLayoutChangeListener(mLayoutChangeListener);
                mAnchorView.setTag(null);
            }
        });

        setAnchorView(mAnchorView);
        Rect originalPadding = new Rect();
        getBackground().getPadding(originalPadding);
        setVerticalOffset(-originalPadding.top);
    }

    @Override
    public void setAdapter(ListAdapter adapter) {
        mAdapter = adapter;
        super.setAdapter(adapter);
    }


    public void setInitialSelection(int initialSelection) {
        mInitialSelection = initialSelection;
    }

    /**
     * Shows the popup. The adapter should be set before calling this method.
     */
    @Override
    public void show() {
        // An ugly hack to keep the popup from expanding on top of the keyboard.
        setInputMethodMode(INPUT_METHOD_NEEDED);

        int contentWidth = measureContentWidth();
        float anchorWidth = mAnchorView.getLayoutParams().width;
        assert anchorWidth > 0;
        Rect padding = new Rect();
        getBackground().getPadding(padding);
        if (contentWidth + padding.left + padding.right > anchorWidth) {
            setContentWidth(contentWidth);
            final Rect displayFrame = new Rect();
            mAnchorView.getWindowVisibleDisplayFrame(displayFrame);
            if (getWidth() > displayFrame.width()) {
                setWidth(displayFrame.width());
            }
        } else {
            setWidth(ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        boolean wasShowing = isShowing();
        super.show();
        getListView().setDividerHeight(0);
        ApiCompatibilityUtils.setLayoutDirection(getListView(),
                mRtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        if (!wasShowing) {
            getListView().setContentDescription(mDescription);
            getListView().sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        }
        if (mInitialSelection >= 0) {
            getListView().setSelection(mInitialSelection);
            mInitialSelection = -1;
        }
    }

    @Override
    public void setOnDismissListener(PopupWindow.OnDismissListener listener) {
        mOnDismissListener = listener;
    }

    /**
     * Sets the text direction in the dropdown. Should be called before show().
     * @param isRtl If true, then dropdown text direction is right to left.
     */
    public void setRtl(boolean isRtl) {
        mRtl = isRtl;
    }

    /**
     * Disable hiding on outside tap so that tapping on a text input field associated with the popup
     * will not hide the popup.
     */
    public void disableHideOnOutsideTap() {
        // HACK: The ListPopupWindow's mPopup automatically dismisses on an outside tap. There's
        // no way to override it or prevent it, except reaching into ListPopupWindow's hidden
        // API. This allows the C++ controller to completely control showing/hiding the popup.
        // See http://crbug.com/400601
        try {
            Method setForceIgnoreOutsideTouch = ListPopupWindow.class.getMethod(
                    "setForceIgnoreOutsideTouch", new Class[] { boolean.class });
            setForceIgnoreOutsideTouch.invoke(this, new Object[] { true });
        } catch (Exception e) {
            Log.e("AutofillPopup",
                    "ListPopupWindow.setForceIgnoreOutsideTouch not found",
                    e);
        }
    }

    /**
     * Sets the content description to be announced by accessibility services when the dropdown is
     * shown.
     * @param description The description of the content to be announced.
     */
    public void setContentDescriptionForAccessibility(CharSequence description) {
        mDescription = description;
    }

    /**
     * Measures the width of the list content. The adapter should not be null.
     * @return The popup window width in pixels.
     */
    private int measureContentWidth() {
        assert mAdapter != null : "Set the adapter before showing the popup.";
        int maxWidth = 0;
        View[] itemViews = new View[mAdapter.getViewTypeCount()];
        final int widthMeasureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        final int heightMeasureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        for (int i = 0; i < mAdapter.getCount(); i++) {
            int type = mAdapter.getItemViewType(i);
            itemViews[type] = mAdapter.getView(i, itemViews[type], null);
            View itemView = itemViews[type];
            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
            itemView.setLayoutParams(params);
            itemView.measure(widthMeasureSpec, heightMeasureSpec);
            maxWidth = Math.max(maxWidth, itemView.getMeasuredWidth());
        }
        return maxWidth;
    }
}

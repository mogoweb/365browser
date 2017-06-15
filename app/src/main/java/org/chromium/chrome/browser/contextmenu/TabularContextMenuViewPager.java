// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.contextmenu;

import android.content.Context;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.view.View;

import org.chromium.chrome.R;

/**
 * When there is more than one view for the context menu to display, it wraps the display in a view
 * pager.
 */
public class TabularContextMenuViewPager extends ViewPager {
    public TabularContextMenuViewPager(Context context) {
        super(context);
    }

    public TabularContextMenuViewPager(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * Used to show the full ViewPager dialog. Without this the dialog would have no height or
     * width.
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int menuHeight = 0;
        int tabHeight = 0;
        // getCurrentItem() does not take into account the tab layout unlike getChildCount().
        int currentItemsIndex = getCurrentItem() + 1;

        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            child.measure(
                    widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            int measuredHeight = child.getMeasuredHeight();

            // The ViewPager also considers the tab layout one of its children, and needs to be
            // treated separately from getting the largest height.
            if (child.getId() == R.id.tab_layout && child.getVisibility() != GONE) {
                tabHeight = measuredHeight;
            } else if (i == currentItemsIndex) {
                menuHeight = child.getMeasuredHeight();
                break;
            }
        }
        int fullHeight = menuHeight + tabHeight;

        heightMeasureSpec = MeasureSpec.makeMeasureSpec(fullHeight, MeasureSpec.EXACTLY);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}

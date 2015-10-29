// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.appmenu.ChromeAppMenuPropertiesDelegate;
import org.chromium.chrome.browser.widget.TintedDrawable;
import org.chromium.ui.base.DeviceFormFactor;
import org.chromium.ui.widget.Toast;

/**
 * The toolbar at the bottom of the new tab page. Contains buttons to open the bookmarks and
 * recent tabs pages.
 */
public class NewTabPageToolbar extends LinearLayout implements OnLongClickListener {

    private View mBookmarksButton, mRecentTabsButton;
    private Toast mToast;

    /**
     * Constructor for inflating from xml.
     */
    public NewTabPageToolbar(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public View getBookmarksButton() {
        return mBookmarksButton;
    }

    public View getRecentTabsButton() {
        return mRecentTabsButton;
    }

    @Override
    protected void onFinishInflate() {
        mBookmarksButton = initButton(R.id.bookmarks_button, R.drawable.btn_star);
        mRecentTabsButton = initButton(R.id.recent_tabs_button, R.drawable.btn_recents);
    }

    private View initButton(int buttonId, int drawableId) {
        ViewGroup button = (ViewGroup) findViewById(buttonId);
        TextView textView = (TextView) button.getChildAt(0);

        TintedDrawable icon = TintedDrawable.constructTintedDrawable(getResources(), drawableId);
        ApiCompatibilityUtils.setCompoundDrawablesRelativeWithIntrinsicBounds(
                textView, icon, null, null, null);
        if (!DeviceFormFactor.isTablet(getContext())
                && !ChromeAppMenuPropertiesDelegate.isMenuTrimmingExperimentEnabled()) {
            // On phones, no text is shown, but long pressing shows a tooltip.
            textView.setText("");
            textView.setCompoundDrawablePadding(0);
            button.setOnLongClickListener(this);
        }

        return button;
    }

    @Override
    public boolean onLongClick(View v) {
        // Display tooltip on long click
        if (v == mBookmarksButton) {
            showTooltip(R.string.ntp_bookmarks);
        } else if (v == mRecentTabsButton) {
            showTooltip(R.string.recent_tabs);
        }
        return true;
    }

    /**
     * Shows a tooltip for a button. If a tooltip is already showing, it will be hidden.
     * @param stringId The string resource ID of the tooltip to be shown.
     */
    private void showTooltip(int stringId) {
        if (mToast != null) mToast.cancel();
        Context ctx = getContext();
        mToast = Toast.makeText(ctx, ctx.getResources().getString(stringId), Toast.LENGTH_SHORT);
        mToast.setGravity(Gravity.BOTTOM | Gravity.CENTER, 0, getHeight());
        mToast.show();
    }
}

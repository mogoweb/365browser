// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.infobar;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;

import org.chromium.chrome.R;

/**
 * Lays out controls along a line, sandwiched between an (optional) icon and close button.
 * This should only be used by the {@link InfoBar} class, and is created when the InfoBar subclass
 * declares itself to be using a compact layout via {@link InfoBar#usesCompactLayout}.
 */
public class InfoBarCompactLayout extends LinearLayout implements View.OnClickListener {
    private final InfoBarView mInfoBarView;
    private final int mCompactInfoBarSize;
    private final View mCloseButton;

    InfoBarCompactLayout(
            Context context, InfoBarView infoBarView, int iconResourceId, Bitmap iconBitmap) {
        super(context);
        mInfoBarView = infoBarView;
        mCompactInfoBarSize =
                context.getResources().getDimensionPixelOffset(R.dimen.infobar_compact_size);

        setOrientation(LinearLayout.HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);

        prepareIcon(iconResourceId, iconBitmap);
        mCloseButton = prepareCloseButton();
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.infobar_close_button) {
            mInfoBarView.onCloseButtonClicked();
        } else {
            assert false;
        }
    }

    /**
     * Inserts a view before the close button.
     * @param view   View to insert.
     * @param weight Weight to assign to it.
     */
    protected void addContent(View view, float weight) {
        LinearLayout.LayoutParams params;
        if (weight <= 0.0f) {
            params = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, mCompactInfoBarSize);
        } else {
            params = new LinearLayout.LayoutParams(0, mCompactInfoBarSize);
            params.weight = weight;
        }
        params.gravity = Gravity.BOTTOM;
        addView(view, indexOfChild(mCloseButton), params);
    }

    /**
     * Adds an icon to the start of the infobar, if the infobar requires one.
     * @param iconResourceId Resource ID of the icon to use.
     * @param iconBitmap     Raw {@link Bitmap} to use instead of a resource.
     */
    private void prepareIcon(int iconResourceId, Bitmap iconBitmap) {
        ImageView iconView = InfoBarLayout.createIconView(getContext(), iconResourceId, iconBitmap);
        if (iconView != null) {
            LinearLayout.LayoutParams iconParams =
                    new LinearLayout.LayoutParams(mCompactInfoBarSize, mCompactInfoBarSize);
            addView(iconView, iconParams);
        }
    }

    /** Adds a close button to the end of the infobar. */
    private View prepareCloseButton() {
        ImageButton closeButton = InfoBarLayout.createCloseButton(getContext());
        closeButton.setOnClickListener(this);
        LinearLayout.LayoutParams closeParams =
                new LinearLayout.LayoutParams(mCompactInfoBarSize, mCompactInfoBarSize);
        addView(closeButton, closeParams);
        return closeButton;
    }
}

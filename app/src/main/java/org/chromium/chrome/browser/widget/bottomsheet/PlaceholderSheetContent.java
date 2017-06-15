// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.widget.bottomsheet;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;

/**
 * This class is used as a placeholder when there should otherwise be no content in the bottom
 * sheet.
 */
class PlaceholderSheetContent implements BottomSheet.BottomSheetContent {
    /** The view that represents this placeholder. */
    private View mView;

    /** Whether or not the content is using incognito colors. */
    private boolean mIsIncongitoThemed;

    public PlaceholderSheetContent(Context context) {
        mView = new View(context);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        mView.setLayoutParams(params);
        setIsIncognito(false);
    }

    /**
     * Set whether or not the placeholder content is being used in an incognito sheet. This affects
     * the color used in the background.
     * @param isIncognito Whether or not the sheet is incognito.
     */
    public void setIsIncognito(boolean isIncognito) {
        mIsIncongitoThemed = isIncognito;

        int colorId = isIncognito ? R.color.incognito_primary_color : R.color.default_primary_color;

        mView.setBackgroundColor(
                ApiCompatibilityUtils.getColor(mView.getContext().getResources(), colorId));
    }

    @Override
    public View getContentView() {
        return mView;
    }

    @Override
    public View getToolbarView() {
        return null;
    }

    @Override
    public boolean isUsingLightToolbarTheme() {
        // This value doesn't matter since this content does not provide a toolbar.
        return false;
    }

    @Override
    public boolean isIncognitoThemedContent() {
        return mIsIncongitoThemed;
    }

    @Override
    public int getVerticalScrollOffset() {
        return 0;
    }

    @Override
    public void destroy() {}

    @Override
    @BottomSheetContentController.ContentType
    public int getType() {
        return BottomSheetContentController.TYPE_PLACEHOLDER;
    }
}

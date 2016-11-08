/*
 * Copyright (c) 2016, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package org.chromium.chrome.browser.preferences.privacy;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.widget.PaddedFrameLayout;

/**
 * View that handles orientation changes for the Browser promo based Views. When the width is
 * greater than the height, switches the promo content view from vertical to horizontal and moves
 * the illustration from the top of the text to the side of the text.
 */
public class BrowserPromoView extends PaddedFrameLayout {

    private static final int ILLUSTRATION_HORIZONTAL_PADDING_DP = 24;
    private static final int FRAME_HEIGHT_MARGIN_DP = 30;
    private static final int NO_MAX_SIZE = -1;

    private View mIllustration;
    private LinearLayout mPromoContent;
    private int mMaxChildWidth;
    private int mMaxChildWidthHorizontal;
    private int mIllustrationPaddingBottom;
    private int mIllustrationPaddingSide;
    private int mFrameHeightMargin;

    public BrowserPromoView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mMaxChildWidth = getResources()
                    .getDimensionPixelSize(R.dimen.browser_promo_screen_width);
        mMaxChildWidthHorizontal = getResources()
                    .getDimensionPixelSize(R.dimen.browser_promo_screen_width_horizontal);
        mIllustrationPaddingBottom = getResources()
                    .getDimensionPixelSize(R.dimen.browser_promo_illustration_margin_bottom);
        float density = getResources().getDisplayMetrics().density;
        mIllustrationPaddingSide = (int) (ILLUSTRATION_HORIZONTAL_PADDING_DP * density + 0.5f);
        mFrameHeightMargin = (int) (FRAME_HEIGHT_MARGIN_DP * density + 0.5f);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mIllustration = findViewById(R.id.browser_promo_illustration);
        mPromoContent = (LinearLayout) findViewById(R.id.browser_promo_content);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);

        if (width >= 2 * mIllustration.getWidth() && width > height) {
            mPromoContent.setOrientation(LinearLayout.HORIZONTAL);
            setMaxChildWidth(mMaxChildWidthHorizontal);
            ApiCompatibilityUtils.setPaddingRelative(
                    mIllustration, 0, 0, mIllustrationPaddingSide, 0);
        } else {
            mPromoContent.setOrientation(LinearLayout.VERTICAL);
            setMaxChildWidth(mMaxChildWidth);
            mIllustration.setPadding(0, 0, 0, mIllustrationPaddingBottom);
        }

        setMaxChildHeight(height - mFrameHeightMargin);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}

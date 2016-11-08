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

package org.chromium.chrome.browser.widget;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.CountDownTimer;
import android.preference.PreferenceManager;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.CommandLine;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeSwitches;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.ui.base.LocalizationUtils;

public class BrowserTooltip {
    private CountDownTimer mTimer;
    private static final int MAX_NOTIFICATION_DIMENSION_DP = 600;
    private static final int DEFAULT_PADDING_DP = 10;
    private final int mTooltipMaxDimensions;
    private final int mTooltipPadding;

    public interface TooltipTimeout {
        void onTimeout();
    }

    private class TooltipTextBubble extends TextBubble {
        public TooltipTextBubble(Context context) {
            super(context, 0f);
            setBackgroundDrawable(new TooltipBackground(context));
            setAnimationStyle(R.style.FullscreenNotificationBubble);
        }

        @Override
        protected View createContent(Context context) {
            TextView tooltipText = new TextView(context);
            ApiCompatibilityUtils.setTextAppearance(tooltipText,
                    android.R.style.TextAppearance_DeviceDefault_Medium);
            return tooltipText;
        }

        class TooltipBackground extends TextBubble.BubbleBackgroundDrawable {

            TooltipBackground(Context context) {
                super(context);
            }

            @Override
            public void setColorFilter(ColorFilter cf) {
                super.setColorFilter(cf);
                mBubbleArrowDrawable.setColorFilter(cf);
                mBubbleContentsDrawable.setColorFilter(cf);
            }
        }
    }

    private TooltipTextBubble mTooltipBubble;
    private int mCookie;

    public BrowserTooltip(Context context, String text, int bgColorResId,
                          int txtColorResId, int cookie) {
        float density = context.getResources().getDisplayMetrics().density;
        mTooltipMaxDimensions = (int) (density * MAX_NOTIFICATION_DIMENSION_DP);
        mTooltipPadding = (int) (density * DEFAULT_PADDING_DP);
        mCookie = cookie;

        mTooltipBubble = new TooltipTextBubble(context);
        TextView tooltipText = ((TextView)mTooltipBubble.getContentView());
        tooltipText.setGravity(Gravity.CENTER_HORIZONTAL);
        tooltipText.setTextColor(ApiCompatibilityUtils.getColor(
                context.getResources(), txtColorResId));
        tooltipText.setText(text);
        tooltipText.measure(
                View.MeasureSpec.makeMeasureSpec(mTooltipMaxDimensions, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(mTooltipMaxDimensions, View.MeasureSpec.AT_MOST));
        tooltipText.setPadding(mTooltipPadding, mTooltipPadding, mTooltipPadding, mTooltipPadding);
        mTooltipBubble.getBackground().setAlpha(225);

        Drawable drawable = mTooltipBubble.getBackground();
        drawable.setColorFilter(ApiCompatibilityUtils.getColor(context.getResources(),bgColorResId),
                PorterDuff.Mode.MULTIPLY);
    }

    public void setTouchListener(View.OnTouchListener l) {
        mTooltipBubble.setTouchable(l != null);
        mTooltipBubble.setTouchInterceptor(l);
    }

    public int getCookie() {
        return mCookie;
    }

    public void show(int timeout, View anchor, final TooltipTimeout timeoutCallback) {
        if (CommandLine.getInstance().hasSwitch(ChromeSwitches.DISABLE_TOOLTIPS)
                || PrefServiceBridge.getInstance().getPowersaveModeEnabled()) {
            return;
        }

        mTooltipBubble.show(anchor);

        if (mTimer != null) mTimer.cancel();

        mTimer = new CountDownTimer(timeout, 1000) {

            public void onTick(long millisUntilFinished) {

            }

            public void onFinish() {
                mTooltipBubble.dismiss();
                timeoutCallback.onTimeout();
            }
        };

        mTimer.start();
    }

    public void dismiss() {
        if (mTimer != null)
            mTimer.cancel();

        mTooltipBubble.dismiss();
    }
}

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

package org.chromium.chrome.browser.preferences;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.PaintDrawable;
import android.preference.Preference;
import android.support.annotation.NonNull;
import android.text.Html;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.preferences.website.WebDefenderPreferenceHandler;
import org.chromium.content.browser.WebDefender;

/*
Preference showing the Privacy meter on screen when SmartProtect is active
 */
public class BrowserPrivacyMeterPreference extends Preference {
    public static final int GREEN = Color.parseColor("#008F02");
    public static final int YELLOW = Color.parseColor("#CBB325");
    public static final int RED = Color.parseColor("#AA232A");
    public static final int GRAY = Color.GRAY;
    private static final int PRIVACY_METER_BAR_WIDTH_DP = 15;
    private static final int STAR_HEIGHT_DP = 3;
    private static int mPrivacyMeterBarWidth;
    private static int mStarHeight;
    private static float mDpToPx;

    View mMeterView;
    boolean mWebDefenderPermission;
    boolean mWebRefinerPermission;
    WebDefender.ProtectionStatus mWebDefenderStatus;
    int mWebRefinerCount;

    /*
    Constructor that gets invoked when inflating from xml.
     */
    public BrowserPrivacyMeterPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWidgetLayoutResource(R.layout.webdefender_privacy_meter);
        setSelectable(false);
        setupStatics(context.getResources());
    }

    private static void setupStatics(Resources res) {
        if (res !=null && mDpToPx == 0) {
            mDpToPx = res.getDisplayMetrics().density;
            mPrivacyMeterBarWidth = (int) (PRIVACY_METER_BAR_WIDTH_DP * mDpToPx);
            mStarHeight = (int) (STAR_HEIGHT_DP * mDpToPx);
        }
    }

    @Override
    public void onBindView(@NonNull View view) {
        super.onBindView(view);
        mMeterView = view.findViewById(R.id.webdefender_privacy_meter);
        if (mMeterView != null) displayMeter();

    }

    /**
     * Call this just after adding preferences from xml
     * @param webDefenderPermission The initial WebDefender Permission.
     * @param webRefinerPermission The initial WebRefiner Permission.
     * @param webDefenderStatus The WebDefender Status parcel needed to setup the meter
     * @param webRefinerBlockedCount The count for objects blocked by WebRefiner
     */
    public void setupPrivacyMeter(boolean webDefenderPermission, boolean webRefinerPermission,
                                  WebDefender.ProtectionStatus webDefenderStatus,
                                  int webRefinerBlockedCount) {
        mWebDefenderPermission = webDefenderPermission;
        mWebRefinerPermission = webRefinerPermission;
        mWebDefenderStatus = webDefenderStatus;
        mWebRefinerCount = webRefinerBlockedCount;

    }

    /**
     * Call this to update the meter when WebRefiner or WebDefender change
     * @param newWebDefenderPermission The updated WebDefender Permission
     * @param newWebRefinerPermission The updated WebRefiner Permission
     */
    public void refreshMeter(boolean newWebDefenderPermission, boolean newWebRefinerPermission) {
        mWebDefenderPermission = newWebDefenderPermission;
        mWebRefinerPermission = newWebRefinerPermission;
        displayMeter();
    }

    private void displayMeter() {
        setupPrivacyMeterDisplay(getContext().getResources(),
                mMeterView, mWebDefenderPermission, mWebRefinerPermission,
                mWebDefenderStatus, mWebRefinerCount);
    }


    /*
    Static functions to draw the meter
     */

    private static void createRatingStar(Resources res, ImageView imageView, int height, int color) {
        Drawable drawable = generateBarDrawable(res, mPrivacyMeterBarWidth, height, color);
        imageView.setImageDrawable(drawable);
    }

    private static void setupPrivacyMeterDisplay(
            Resources res, View view, boolean webDefenderEnabled,
            boolean webRefinerEnabled, WebDefender.ProtectionStatus status,
            int webRefinerBlockedCount) {
        if (view == null) {
            return;
        }
        setupStatics(res);
        int blockedUrlCount = 0;
        int strippedCount = 0;
        int possibleConnections = 0;
        boolean addedProtection = webDefenderEnabled
                || (webRefinerEnabled && webRefinerBlockedCount > 0);
        if (WebDefenderPreferenceHandler.isInitialized() && status != null) {
            for (WebDefender.TrackerDomain trackerDomain : status.mTrackerDomains) {
                if (trackerDomain.mProtectiveAction ==
                        WebDefender.TrackerDomain.PROTECTIVE_ACTION_BLOCK_URL) {
                    blockedUrlCount++;
                } else if (trackerDomain.mProtectiveAction ==
                        WebDefender.TrackerDomain.PROTECTIVE_ACTION_BLOCK_COOKIES) {
                    strippedCount++;
                } else if (trackerDomain.mPotentialTracker) {
                    possibleConnections++;
                }
            }
        }

        int rawCount = ((webDefenderEnabled) ? blockedUrlCount + strippedCount : 0) +
                ((webRefinerEnabled) ? webRefinerBlockedCount : 0);
        double weightedWebDefenderCount = blockedUrlCount*1.37 + strippedCount;
        double weightedWebRefinerCount = webRefinerBlockedCount*1.37;
        double weightedCount = weightedWebDefenderCount + weightedWebRefinerCount;
        double effectiveWeightedCount =
                ((webDefenderEnabled) ? blockedUrlCount*1.37 + strippedCount : 0) +
                        ((webRefinerEnabled) ? webRefinerBlockedCount*1.37 : 0);
        int maxProtectImpact = getPrivacyImpact(weightedCount);
        int actualImpact = getPrivacyImpact(effectiveWeightedCount);
        int possibleImpact = getPrivacyImpact(possibleConnections);
        int unprotectedRating = 5 - maxProtectImpact - possibleImpact;
        int starRating = (addedProtection)
                ? 5 - possibleImpact - (maxProtectImpact - actualImpact)
                : unprotectedRating;
        int passiveColor = (unprotectedRating <= 1) ? RED :
                (unprotectedRating <= 4) ? YELLOW : GREEN;

        if (addedProtection) {
            int scaleIncrease = mStarHeight * 2;
            int scaleCount = 1;
            ImageView imageView = (ImageView) view.findViewById(R.id.star1);
            if ((starRating >= 1)) {

                createRatingStar(res, imageView, (unprotectedRating >= 1)
                                ? mStarHeight : (mStarHeight + (scaleCount++ * scaleIncrease)),
                        (unprotectedRating >= 1) ? passiveColor : GREEN);
            } else {
                createRatingStar(res, imageView, mStarHeight, GRAY);
            }

            imageView = (ImageView) view.findViewById(R.id.star2);
            if ((starRating >= 2)) {
                createRatingStar(res, imageView, (unprotectedRating >= 2)
                                ? mStarHeight : (mStarHeight + (scaleCount++ * scaleIncrease)),
                        (unprotectedRating >= 2) ? passiveColor : GREEN);
            } else {
                createRatingStar(res, imageView, mStarHeight, GRAY);
            }

            imageView = (ImageView) view.findViewById(R.id.star3);
            if ((starRating >= 3)) {
                createRatingStar(res, imageView, (unprotectedRating >= 3)
                                ? mStarHeight : (mStarHeight + (scaleCount++ * scaleIncrease)),
                        (unprotectedRating >= 3) ? passiveColor : GREEN);
            } else {
                createRatingStar(res, imageView, mStarHeight, GRAY);
            }

            imageView = (ImageView) view.findViewById(R.id.star4);
            if ((starRating >= 4)) {
                createRatingStar(res, imageView, (unprotectedRating >= 4)
                                ? mStarHeight : (mStarHeight + (scaleCount++ * scaleIncrease)),
                        (unprotectedRating >= 4) ? passiveColor : GREEN);
            } else {
                createRatingStar(res, imageView, mStarHeight, GRAY);
            }

            imageView = (ImageView) view.findViewById(R.id.star5);
            if ((starRating >= 5)) {
                createRatingStar(res, imageView, (unprotectedRating >= 5)
                                ? mStarHeight : (mStarHeight + (scaleCount * scaleIncrease)),
                        (unprotectedRating >= 5) ? passiveColor : GREEN);
            } else {
                createRatingStar(res, imageView, mStarHeight, Color.GRAY);
            }

        } else {
            ImageView imageView = (ImageView) view.findViewById(R.id.star1);
            createRatingStar(res, imageView, mStarHeight, (starRating >= 1) ? passiveColor : GRAY);

            imageView = (ImageView) view.findViewById(R.id.star2);
            createRatingStar(res, imageView, mStarHeight, (starRating >= 2) ? passiveColor : GRAY);

            imageView = (ImageView) view.findViewById(R.id.star3);
            createRatingStar(res, imageView, mStarHeight, (starRating >= 3) ? passiveColor : GRAY);

            imageView = (ImageView) view.findViewById(R.id.star4);
            createRatingStar(res, imageView, mStarHeight, (starRating >= 4) ? passiveColor : GRAY);

            imageView = (ImageView) view.findViewById(R.id.star5);
            createRatingStar(res, imageView, mStarHeight, (starRating >= 5) ? passiveColor : GRAY);
        }

        TextView textView = (TextView) view.findViewById(R.id.count);
        if (addedProtection && rawCount > 0) {
            textView.setVisibility(View.VISIBLE);
            String improvement = "+<b>" + rawCount + "</b>!";
            textView.setText(Html.fromHtml(improvement));
        } else {
            textView.setVisibility(View.INVISIBLE);
        }
    }

    private static int getPrivacyImpact(double count) {
        return Math.max((int) Math.round(Math.log1p(count)) - 1, 0);
    }

    private static Drawable generateBarDrawable(Resources res, int width, int height, int color) {
        PaintDrawable drawable = new PaintDrawable(color);
        drawable.setIntrinsicWidth(width);
        drawable.setIntrinsicHeight(height);
        drawable.setBounds(0, 0, width, height);

        Bitmap thumb = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(thumb);
        drawable.draw(c);

        return new BitmapDrawable(res, thumb);
    }
}
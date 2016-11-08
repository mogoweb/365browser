/*
 * Copyright (c) 2015-2016, The Linux Foundation. All rights reserved.
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
package org.chromium.chrome.browser;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;
import android.webkit.ValueCallback;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;

import java.util.HashMap;
import java.util.Map;

/**
 * This represents a WebSite Tile that is created from a Drawable and will scale across any
 * area this is externally layouted to. There are 3 possible looks:
 *   - just the favicon (TYPE_SMALL)
 *   - drop-shadow plus a thin overlay border (1dp) (TYPE_MEDIUM)
 *   - centered favicon, extended color, rounded base (TYPE_LARGE)
 *
 * By centralizing everything in this class we make customization of looks much easier.
 *
 * NOTES:
 *   - do not set a background from the outside; this overrides it automatically
 */
public class SiteTileView extends View {

    // public trust level constants
    public static final int TRUST_UNKNOWN   =    0; // default
    public static final int TRUST_AVOID     = 0x01;
    public static final int TRUST_UNTRUSTED = 0x02;
    public static final int TRUST_TRUSTED   = 0x04;
    public static final int TRUST_OVERRIDE  = 0x08;
    public static final int TRUST_UPGRADED  = 0x10;
    private static final int TRUST_MASK     = 0xff;


    // static configuration
    private static final int THRESHOLD_MEDIUM_DP = 32;
    private static final int THRESHOLD_LARGE_DP = 64;
    private static final int LARGE_FAVICON_SIZE_DP = 48;
    private static final int BACKGROUND_DRAWABLE_RES = R.drawable.img_tile_background;
    private static final int DEFAULT_SITE_FAVICON = 0;
    private static final float FILLER_RADIUS_DP = 2f; // sync with the bg image radius
    private static final int FILLER_FALLBACK_COLOR = Color.WHITE; // in case there is no favicon
    private static final boolean BADGE_SHOW_BLOCKED_COUNT = false;

    // internal enums
    private static final int TYPE_SMALL = 1;
    private static final int TYPE_MEDIUM = 2;
    private static final int TYPE_LARGE = 3;
    private static final int TYPE_AUTO = 0;
    private static final int COLOR_AUTO = 0;

    // PageUpgradeStatus
    public static final int UPGRADE_STATE_NONE = 0;
    public static final int UPGRADE_STATE_MAINFRAME = 1;
    public static final int UPGRADE_STATE_MIXED_CONTENT = 2;


    // configuration
    private Bitmap mFaviconBitmap = null;
    private Paint mFundamentalPaint = null;
    private int mFaviconWidth = 0;
    private int mFaviconHeight = 0;
    private int mForcedFundamentalColor = COLOR_AUTO;
    private boolean mBackgroundDisabled = false;
    private int mTrustLevel = TRUST_UNKNOWN;
    private int mBadgeBlockedObjectsCount = 0;
    private boolean mBadgeHasCertIssues = false;
    private int mPageUpgradeStatus = UPGRADE_STATE_NONE;


    // runtime params set on Layout
    private int mCurrentWidth = 0;
    private int mCurrentHeight = 0;
    private int mCurrentType = TYPE_MEDIUM;
    private int mPaddingLeft = 0;
    private int mPaddingTop = 0;
    private int mPaddingRight = 0;
    private int mPaddingBottom = 0;
    private boolean mCurrentShadowDrawn = false;

    // static objects, to be recycled amongst instances (this is an optimization)
    // NOTE: package-visible statics are for optimized usage inside FolderTileView as well
    private static int sMediumPxThreshold = -1;
    private static int sLargePxThreshold = -1;
    private static int sLargeFaviconPx = -1;
    /* package */ static float sRoundedRadius = -1;
    private static Paint sBitmapPaint = null;
    private static Paint sBadgeTextPaint = null;
    private static Rect sSrcRect = new Rect();
    private static Rect sDstRect = new Rect();
    /* package */ static RectF sRectF = new RectF();
    private static Drawable sBackgroundDrawable = null;
    private static class BadgeAssets {
        Drawable back;
        Drawable accent;
        int textColor;
    }
    private static Map<Integer, BadgeAssets> sBadges;
    private static Bitmap sDefaultSiteBitmap = null;
    /* package */ static Rect sBackgroundDrawablePadding = new Rect();

    private ValueCallback<View> mVisibilityChangeCallback;
    private boolean mOverrideBadge;

    /* XML constructors */

    public SiteTileView(Context context) {
        super(context);
        xmlInit(null, 0);
    }

    public SiteTileView(Context context, AttributeSet attrs) {
        super(context, attrs);
        xmlInit(attrs, 0);
    }

    public SiteTileView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        xmlInit(attrs, defStyle);
    }


    /* Programmatic Constructors */

    public SiteTileView(Context context, Bitmap favicon) {
        super(context);
        init(favicon, COLOR_AUTO);
    }

    public SiteTileView(Context context, Bitmap favicon, int fundamentalColor) {
        super(context);
        init(favicon, fundamentalColor);
    }


    /**
     * Changes the current favicon (and associated fundamental color) on the fly
     */
    public void replaceFavicon(Bitmap favicon) {
        replaceFavicon(favicon, COLOR_AUTO);
    }

    /**
     * Changes the current favicon (and associated fundamental color) on the fly
     * @param favicon the new favicon
     * @param fundamentalColor the new fudamental color, or COLOR_AUTO
     */
    public void replaceFavicon(Bitmap favicon, int fundamentalColor) {
        init(favicon, fundamentalColor);
        requestLayout();
    }

    /**
     * Disables the automatic background and filling. Useful for things that are not really
     * "Website Tiles", like folders.
     * @param disabled true to disable the background (defaults to false)
     */
    public void setBackgroundDisabled(boolean disabled) {
        if (mBackgroundDisabled != disabled) {
            mBackgroundDisabled = disabled;
            invalidate();
        }
    }

    /**
     * This results in the Badge being updated
     * @param trustLevel one of the TRUST_ constants
     */
    public void setTrustLevel(int trustLevel) {
        if (mTrustLevel != trustLevel) {
            mTrustLevel = trustLevel;
            invalidate();
        }
    }

    /**
     * Tells that there will be some message about issues inside
     * @param certIssues true if there are issues.
     */
    public void setBadgeHasCertIssues(boolean certIssues) {
        if (certIssues != mBadgeHasCertIssues) {
            mBadgeHasCertIssues = certIssues;
            invalidate();
        }
    }

    /**
     * Sets a badge on the favicon to indicate that the page has been upgraded.
     * @param upgradeState indicates the state secure connect upgraded the page to.
     */
    public void setPageUpgradeBadge(int upgradeState) {
        if (mPageUpgradeStatus != upgradeState) {
            mPageUpgradeStatus = upgradeState;
            if (mPageUpgradeStatus == UPGRADE_STATE_NONE) {
                sBadges.remove(TRUST_UPGRADED);
            } else {
                if (mPageUpgradeStatus == UPGRADE_STATE_MAINFRAME) {
                    loadBadgeResources(getResources(), TRUST_UPGRADED,
                            R.drawable.img_deco_smartprotect_secure_connect_elevated, 0,
                            R.color.TileBadgeTextUnknown);
                } else {
                    loadBadgeResources(getResources(), TRUST_UPGRADED,
                            R.drawable.img_deco_smartprotect_secure_connect_tile_mixed, 0,
                            R.color.TileBadgeTextAvoid);
                }
                invalidate();
            }
        }
    }

    /**
     * Sets the number of objects blocked (a positive contribution to the page). Presentation
     * may or may not have the number indication.
     * @param sessionCounter Counter of blocked objects. Use 0 to not display anything.
     */
    public void setBadgeBlockedObjectsCount(int sessionCounter) {
        if (sessionCounter != mBadgeBlockedObjectsCount) {
            // repaint if going from or to 0, or if showing the ads count
            //noinspection PointlessBooleanExpression,ConstantConditions
            if (mBadgeBlockedObjectsCount == 0 || sessionCounter == 0 || BADGE_SHOW_BLOCKED_COUNT)
                invalidate();
            mBadgeBlockedObjectsCount = sessionCounter;
        }
    }

    public void setBadgeOverride(boolean override, int resId) {
        if (override) {
            loadBadgeResources(getResources(), TRUST_OVERRIDE, R.drawable.img_deco_tile_unknown,
                    resId, R.color.TileBadgeTextUnknown);
            mOverrideBadge = true;
        } else {
            sBadges.remove(TRUST_OVERRIDE);
            mOverrideBadge = false;
        }
    }


    /**
     * @return The fundamental color representing the site.
     */
    public int getFundamentalColor() {
        if (mForcedFundamentalColor != COLOR_AUTO)
            return mForcedFundamentalColor;
        if (mFundamentalPaint == null)
            mFundamentalPaint = createFundamentalPaint(mFaviconBitmap, COLOR_AUTO);
        return mFundamentalPaint.getColor();
    }


    /*** private stuff ahead ***/

    private boolean requiresBadge() {
        return (!mBackgroundDisabled && (mTrustLevel != TRUST_UNKNOWN || mBadgeHasCertIssues
                || mBadgeBlockedObjectsCount > 0) || mPageUpgradeStatus != UPGRADE_STATE_NONE)
                || mOverrideBadge;
    }

    private int computeBadgeMessages() {
        // special case, for TRUST_AVOID, always show the common accent
        if (mTrustLevel == TRUST_AVOID || mOverrideBadge
                || mPageUpgradeStatus != UPGRADE_STATE_NONE)
            return 0;

        // recompute number of 'messages' inside the badge
        int count = 0;
        if (mBadgeHasCertIssues)
            count++;
        if (mBadgeBlockedObjectsCount > 0)
            count++;

        // add the number of blocked objects (-1, for having already counted the message) if needed
        if (BADGE_SHOW_BLOCKED_COUNT)
            count += mBadgeBlockedObjectsCount - 1;

        return count;
    }

    private void xmlInit(AttributeSet attrs, int defStyle) {
        // load attributes
        final TypedArray a = getContext().obtainStyledAttributes(attrs,
                R.styleable.SiteTileView, defStyle, 0);

        // fetch the drawable, if defined - then just extract and use the bitmap
        final Drawable drawable = a.getDrawable(R.styleable.SiteTileView_android_src);
        final Bitmap favicon = drawable instanceof BitmapDrawable ?
                ((BitmapDrawable) drawable).getBitmap() : null;

        // check if we want it background-less (disable shadow and filler)
        setBackgroundDisabled(a.getBoolean(R.styleable.SiteTileView_disableBackground, false));

        // read the trust level (unknown, aka 'default', if not present)
        setTrustLevel(a.getInteger(R.styleable.SiteTileView_trustLevel, TRUST_UNKNOWN)
                & TRUST_MASK);

        // read the amount of blocked objects (or 0 if not present)
        setBadgeBlockedObjectsCount(a.getInteger(R.styleable.SiteTileView_blockedObjects, 0));

        // delete attribute resolution
        a.recycle();

        // proceed with real initialization
        init(favicon, COLOR_AUTO);
    }

    private void init(Bitmap favicon, int fundamentalColor) {
        mFaviconBitmap = favicon;

        // show a default favicon if nothing is set (consider removing this, it's ugly)
        if (mFaviconBitmap == null && DEFAULT_SITE_FAVICON != 0) {
            if (sDefaultSiteBitmap == null)
                sDefaultSiteBitmap = BitmapFactory.decodeResource(getResources(),
                        DEFAULT_SITE_FAVICON);
            mFaviconBitmap = sDefaultSiteBitmap;
            fundamentalColor = 0xFF262626;
        }

        if (mFaviconBitmap != null) {
            mFaviconWidth = mFaviconBitmap.getWidth();
            mFaviconHeight = mFaviconBitmap.getHeight();
        }

        // don't compute the paint right now, just save any hint for later
        mFundamentalPaint = null;
        mForcedFundamentalColor = fundamentalColor;

        // shared (static) resources initialization; except for background, inited on-demand
        ensureCommonLoaded(getResources());

        // change when clicked
        setClickable(true);
    }

    static void ensureCommonLoaded(Resources r) {
        // check if already initialized
        if (sMediumPxThreshold != -1)
            return;

        // heuristics thresholds
        final DisplayMetrics displayMetrics = r.getDisplayMetrics();
        sMediumPxThreshold = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                THRESHOLD_MEDIUM_DP, displayMetrics);
        sLargePxThreshold = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                THRESHOLD_LARGE_DP, displayMetrics);
        sLargeFaviconPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                LARGE_FAVICON_SIZE_DP, displayMetrics);

        // rounded radius
        sRoundedRadius = FILLER_RADIUS_DP > 0 ? TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, FILLER_RADIUS_DP, displayMetrics) : 0;

        // bitmap paint (copy, smooth scale)
        sBitmapPaint = new Paint();
        sBitmapPaint.setColor(Color.BLACK);
        sBitmapPaint.setFilterBitmap(true);

        // badge text paint (anti-aliased)
        sBadgeTextPaint = new Paint();
        sBadgeTextPaint.setAntiAlias(true);
        Typeface badgeTypeface = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        if (badgeTypeface != null)
            sBadgeTextPaint.setTypeface(badgeTypeface);

        // load the background (could be loaded on demand, but in the end it's always needed)
        sBackgroundDrawable = ApiCompatibilityUtils.getDrawable(r, BACKGROUND_DRAWABLE_RES);
        if (sBackgroundDrawable != null)
            sBackgroundDrawable.getPadding(sBackgroundDrawablePadding);

        // load all the badge drawables
        sBadges = new HashMap<>();
        loadBadgeResources(r, TRUST_AVOID, R.drawable.img_deco_tile_avoid,
                R.drawable.img_deco_tile_avoid_accent, R.color.TileBadgeTextAvoid);
        loadBadgeResources(r, TRUST_UNTRUSTED, R.drawable.img_deco_tile_untrusted,
                R.drawable.img_deco_tile_untrusted_accent, R.color.TileBadgeTextUntrusted);
        loadBadgeResources(r, TRUST_UNKNOWN, R.drawable.img_deco_tile_unknown,
                R.drawable.img_deco_tile_unknown_accent, R.color.TileBadgeTextUnknown);
        loadBadgeResources(r, TRUST_TRUSTED, R.drawable.img_deco_tile_verified,
                R.drawable.img_deco_tile_verified_accent, R.color.TileBadgeTextVerified);
    }

    private static void loadBadgeResources(Resources r, int t, int back, int accent, int color) {
        BadgeAssets ba = new BadgeAssets();
        ba.back = back == 0 ? null : ApiCompatibilityUtils.getDrawable(r, back);
        ba.accent = accent == 0 ? null : ApiCompatibilityUtils.getDrawable(r, accent);
        ba.textColor = color == 0 ? Color.TRANSPARENT : ApiCompatibilityUtils.getColor(r, color);
        sBadges.put(t, ba);
    }

    static Rect getBackgroundDrawablePadding() {
        return sBackgroundDrawablePadding != null ? sBackgroundDrawablePadding : new Rect();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        mCurrentWidth = right - left;
        mCurrentHeight = bottom - top;

        // auto-determine the "TYPE_" from the physical size of the layout
        if (mCurrentWidth < sMediumPxThreshold && mCurrentHeight < sMediumPxThreshold)
            mCurrentType = TYPE_SMALL;
        else if (mCurrentWidth < sLargePxThreshold && mCurrentHeight < sLargePxThreshold)
            mCurrentType = TYPE_MEDIUM;
        else
            mCurrentType = TYPE_LARGE;

        // set or remove the background (if the need changed!)
        boolean requiresBackgroundDrawable = mCurrentType >= TYPE_MEDIUM;
        if (requiresBackgroundDrawable && !mCurrentShadowDrawn) {
            // draw the background
            mCurrentShadowDrawn = mCurrentType >= TYPE_LARGE;

            // background -> padding
            mPaddingLeft = sBackgroundDrawablePadding.left;
            mPaddingTop = sBackgroundDrawablePadding.top;
            mPaddingRight = sBackgroundDrawablePadding.right;
            mPaddingBottom = sBackgroundDrawablePadding.bottom;
        } else if (!requiresBackgroundDrawable && mCurrentShadowDrawn) {
            // turn off background drawing
            mCurrentShadowDrawn = false;

            // no background -> no padding
            mPaddingLeft = 0;
            mPaddingTop = 0;
            mPaddingRight = 0;
            mPaddingBottom = 0;
        }

        // just proceed, do nothing here
        super.onLayout(changed, left, top, right, bottom);
    }

    @Override
    public void setPressed(boolean pressed) {
        super.setPressed(pressed);
        // schedule a repaint to show pressed/released
        invalidate();
    }

    @Override
    public void setSelected(boolean selected) {
        super.setSelected(selected);
        // schedule a repaint to show selected
        invalidate();
    }

    @Override
    protected void onVisibilityChanged(View view, int visibility) {
        super.onVisibilityChanged(view, visibility);
        if (mVisibilityChangeCallback != null) {
            mVisibilityChangeCallback.onReceiveValue(view);
        }
    }

    public void setOnVisibilityChangeListener(ValueCallback<View> callback) {
        mVisibilityChangeCallback = callback;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Selection State: make everything smaller
        if (isSelected()) {
            float scale = 0.8f;
            canvas.translate(mCurrentWidth * (1 - scale) / 2, mCurrentHeight * (1 - scale) / 2);
            canvas.scale(scale, scale);
        }

        // Pressed state: make the button reach the finger
        if (isPressed()) {
            float scale = 1.1f;
            canvas.translate(mCurrentWidth * (1 - scale) / 2, mCurrentHeight * (1 - scale) / 2);
            canvas.scale(scale, scale);
        }

        final int left = mPaddingLeft;
        final int top = mPaddingTop;
        final int right = mCurrentWidth - mPaddingRight;
        final int bottom = mCurrentHeight - mPaddingBottom;
        final int contentWidth = right - left;
        final int contentHeight = bottom - top;

        // A. the background drawable (if set)
        boolean requiresBackground = mCurrentShadowDrawn && sBackgroundDrawable != null
                && !isPressed() && !mBackgroundDisabled;
        if (requiresBackground) {
            sBackgroundDrawable.setBounds(0, 0, mCurrentWidth, mCurrentHeight);
            sBackgroundDrawable.draw(canvas);
        }

        // B. (when needed) draw the background rectangle; sharp our rounded
        boolean requiresFundamentalFiller = mCurrentType >= TYPE_LARGE && !mBackgroundDisabled;
        if (requiresFundamentalFiller) {
            // create the filler paint on demand (not all icons need it)
            if (mFundamentalPaint == null)
                mFundamentalPaint = createFundamentalPaint(mFaviconBitmap, mForcedFundamentalColor);

            // paint if not white, since requiresBackground already painted it white
            int fundamentalColor = mFundamentalPaint.getColor();
            if (fundamentalColor != COLOR_AUTO &&
                    (fundamentalColor != Color.WHITE || !requiresBackground)) {
                if (sRoundedRadius >= 1.) {
                    sRectF.set(left, top, right, bottom);
                    canvas.drawRoundRect(sRectF, sRoundedRadius, sRoundedRadius, mFundamentalPaint);
                } else
                    canvas.drawRect(left, top, right, bottom, mFundamentalPaint);
            }
        }

        // C. (if present) draw the favicon
        boolean requiresFavicon = mFaviconBitmap != null
                && mFaviconWidth > 1 && mFaviconHeight > 1;
        if (requiresFavicon) {
            // destination can either fill, or auto-center
            boolean fillSpace = mCurrentType <= TYPE_MEDIUM;
            if (fillSpace || contentWidth < sLargeFaviconPx || contentHeight < sLargeFaviconPx) {
                sDstRect.set(left, top, right, bottom);
            } else {
                int dstLeft = left + (contentWidth - sLargeFaviconPx) / 2;
                int dstTop = top + (contentHeight - sLargeFaviconPx) / 2;
                sDstRect.set(dstLeft, dstTop, dstLeft + sLargeFaviconPx, dstTop + sLargeFaviconPx);
            }

            // source has to 'crop proportionally' to keep the dest aspect ratio
            sSrcRect.set(0, 0, mFaviconWidth, mFaviconHeight);
            int sW = sSrcRect.width();
            int sH = sSrcRect.height();
            int dW = sDstRect.width();
            int dH = sDstRect.height();
            if (sW > 4 && sH > 4 && dW > 4 && dH > 4) {
                float hScale = (float) dW / (float) sW;
                float vScale = (float) dH / (float) sH;
                if (hScale == vScale) {
                    // no transformation needed, just zoom
                } else if (hScale < vScale) {
                    // horizontal crop
                    float hCrop = 1 - hScale / vScale;
                    int hCropPx = (int) (sW * hCrop / 2 + 0.5);
                    sSrcRect.left += hCropPx;
                    sSrcRect.right -= hCropPx;
                    canvas.drawBitmap(mFaviconBitmap, sSrcRect, sDstRect, sBitmapPaint);
                } else {
                    // vertical crop
                    float vCrop = 1 - vScale / hScale;
                    int vCropPx = (int) (sH * vCrop / 2 + 0.5);
                    sSrcRect.top += vCropPx;
                    sSrcRect.bottom -= vCropPx;
                }
            }

            // blit favicon, croppped, scaled
            canvas.drawBitmap(mFaviconBitmap, sSrcRect, sDstRect, sBitmapPaint);
        }

        // D. show badge, if requested
        if (requiresBadge()) {
            // retrieve the badge resources
            int trustLevel = mTrustLevel;
            if (mPageUpgradeStatus != UPGRADE_STATE_NONE) trustLevel = TRUST_UPGRADED;
            else if (mOverrideBadge) trustLevel = TRUST_OVERRIDE;
            final BadgeAssets ba = sBadges.get(trustLevel);
            if (ba != null) {

                // paint back
                final Drawable back = ba.back;
                int badgeL = 0, badgeT = 0, badgeW = 0, badgeH = 0;
                if (back != null) {
                    badgeW = back.getIntrinsicWidth();
                    badgeH = back.getIntrinsicHeight();
                    badgeL = mCurrentWidth - mPaddingRight / 3 - badgeW;
                    badgeT = mCurrentHeight - mPaddingBottom / 3 - badgeH;
                    back.setBounds(badgeL, badgeT, badgeL + badgeW, badgeT + badgeH);
                    back.draw(canvas);
                }
                int messagesCount = computeBadgeMessages();

                // paint accent, if 0 messages
                if (messagesCount < 1) {
                    final Drawable accent = ba.accent;
                    if (accent != null && badgeW > 0 && badgeH > 0) {
                        int accentW = accent.getIntrinsicWidth();
                        int accentH = accent.getIntrinsicHeight();
                        int accentL = badgeL + (badgeW - accentW) / 2;
                        int accentT = badgeT + (badgeH - accentH) / 2;
                        accent.setBounds(accentL, accentT, accentL + accentW, accentT + accentH);
                        accent.draw(canvas);
                    }
                }
                // at least 1 message, draw text
                else if (Color.alpha(ba.textColor) > 0) {
                    float textSize = Math.min(2 * contentWidth / 5, sMediumPxThreshold / 4) * 1.1f;
                    sBadgeTextPaint.setTextSize(textSize);
                    sBadgeTextPaint.setColor(ba.textColor);
                    final String text = String.valueOf(messagesCount);
                    int textWidth = Math.round(sBadgeTextPaint.measureText(text) / 2);
                    int textCx = badgeL + badgeW / 2;
                    int textCy = badgeT + badgeH / 2;
                    canvas.drawText(text, textCx - textWidth, textCy + textSize / 3 + 1,
                            sBadgeTextPaint);
                }
            }
        }

        /*if (true) { // DEBUG TYPE
            Paint paint = new Paint();
            paint.setColor(Color.BLACK);
            paint.setTextSize(20);
            canvas.drawText(String.valueOf(mCurrentType), 30, 30, paint);
        }*/
    }


    /**
     * Creates a fill Paint from the favicon, or using the forced color (if not COLOR_AUTO)
     */
    private static Paint createFundamentalPaint(Bitmap favicon, int forceFillColor) {
        final Paint fillPaint = new Paint();
        if (forceFillColor != COLOR_AUTO)
            fillPaint.setColor(forceFillColor);
        else
            fillPaint.setColor(guessFundamentalColor(favicon));
        return fillPaint;
    }

    /**
     * This uses very stupid mechanism - a 9x9 grid sample on the borders and center - and selects
     * the color with the most frequency, or the center.
     *
     * @param bitmap the bitmap to guesss the color about
     * @return a Color
     */
    private static int guessFundamentalColor(Bitmap bitmap) {
        if (bitmap == null)
            return FILLER_FALLBACK_COLOR;
        int height = bitmap.getHeight();
        int width = bitmap.getWidth();
        if (height < 2 || width < 2)
            return FILLER_FALLBACK_COLOR;

        // pick up to 9 colors
        // NOTE: the order of sampling sets the precendece, in case of ties
        int[] pxColors = new int[9];
        int idx = 0;
        if ((pxColors[idx] = sampleColor(bitmap, width / 2, height / 2)) != 0) idx++;
        if ((pxColors[idx] = sampleColor(bitmap, width / 2, height - 1)) != 0) idx++;
        if ((pxColors[idx] = sampleColor(bitmap, width - 1, height - 1)) != 0) idx++;
        if ((pxColors[idx] = sampleColor(bitmap, width - 1, height / 2)) != 0) idx++;
        if ((pxColors[idx] = sampleColor(bitmap,         0, 0         )) != 0) idx++;
        if ((pxColors[idx] = sampleColor(bitmap, width / 2, 0         )) != 0) idx++;
        if ((pxColors[idx] = sampleColor(bitmap, width - 1, 0         )) != 0) idx++;
        if ((pxColors[idx] = sampleColor(bitmap, 0        , height / 2)) != 0) idx++;
        if ((pxColors[idx] = sampleColor(bitmap, 0        , height - 1)) != 0) idx++;

        // find the most popular
        int popColor = -1;
        int popCount = -1;
        for (int i = 0; i < idx; i++) {
            int thisColor = pxColors[i];
            int thisCount = 0;
            for (int j = 0; j < idx; j++) {
                if (pxColors[j] == thisColor)
                    thisCount++;
            }
            if (thisCount > popCount) {
                popColor = thisColor;
                popCount = thisCount;
            }
        }
        return popCount > -1 ? popColor : FILLER_FALLBACK_COLOR;
    }

    /**
     * @return Color, but if it's 0, you should discard it (not representative)
     */
    private static int sampleColor(Bitmap bitmap, int x, int y) {
        final int color = bitmap.getPixel(x, y);

        // discard semi-transparent pixels, because they're probably from a spurious border
        //if ((color >>> 24) <= 128)
        //    return 0;

        // compose transparent pixels with white, since the BG will be white anyway
        final int alpha = Color.alpha(color);
        if (alpha == 0)
            return Color.WHITE;
        if (alpha < 255) {
            // perform simplified Porter-Duff source-over
            int dstContribution = 255 - alpha;
            return Color.argb(255,
                    ((alpha * Color.red(color)) >> 8) + dstContribution,
                    ((alpha * Color.green(color)) >> 8) + dstContribution,
                    ((alpha * Color.blue(color)) >> 8) + dstContribution
            );
        }

        // discard black pixels, because black is not a color (well, not a good looking one)
        if ((color & 0xFFFFFF) == 0)
            return 0;

        return color;
    }

}

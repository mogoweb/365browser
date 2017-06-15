// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
package org.chromium.chrome.browser.compositor.bottombar.contextualsearch;

import android.content.Context;
import android.support.v4.view.animation.PathInterpolatorCompat;
import android.text.TextUtils;
import android.view.animation.Interpolator;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.compositor.bottombar.OverlayPanelAnimation;
import org.chromium.chrome.browser.compositor.layouts.ChromeAnimation;

/**
 * Controls the image shown in the Bar. Owns animating between the search provider icon and
 * custom image (either a thumbnail or quick action icon) for the current query.
 */
public class ContextualSearchImageControl
        implements ChromeAnimation.Animatable<ContextualSearchImageControl.AnimationType> {
    /**
      * Animation properties.
      */
    protected enum AnimationType { CUSTOM_IMAGE_VISIBILITY }

    /** The current context. */
    private final Context mContext;

    /** The OverlayPanelAnimation used to add animations. */
    private final OverlayPanelAnimation mOverlayPanelAnimation;

    /** The percentage the panel is expanded. 1.f is fully expanded and 0.f is peeked. */
    private float mExpandedPercentage;

    public ContextualSearchImageControl(OverlayPanelAnimation overlayPanelAnimation,
            Context context) {
        mContext = context;
        mOverlayPanelAnimation = overlayPanelAnimation;
    }

    /**
     * Updates the Bar image when in transition between peeked to expanded states.
     * @param percentage The percentage to the more opened state.
     */
    public void onUpdateFromPeekToExpand(float percentage) {
        mExpandedPercentage = percentage;

        if (mQuickActionIconVisible || mThumbnailVisible) {
            mCustomImageVisibilityPercentage = 1.f - percentage;
        }
    }

    // ============================================================================================
    // Quick Action Icon
    // ============================================================================================

    /**
     * The resource id of the quick action icon to display.
     */
    private int mQuickActionIconResourceId;

    /**
     * Whether the quick action icon is visible.
     */
    private boolean mQuickActionIconVisible;

    /**
     * @param resId The resource id of the quick action icon to display.
     */
    public void setQuickActionIconResourceId(int resId) {
        mQuickActionIconResourceId = resId;
        mQuickActionIconVisible = true;
        animateCustomImageVisibility(true);
    }

    /**
     * @return The resource id of the quick action icon to display.
     */
    public int getQuickActionIconResourceId() {
        return mQuickActionIconResourceId;
    }

    /**
     * @return Whether the quick action icon is visible.
     */
    public boolean getQuickActionIconVisible() {
        return mQuickActionIconVisible;
    }

    // ============================================================================================
    // Thumbnail
    // ============================================================================================

    /**
     * The URL of the thumbnail to display.
     */
    private String mThumbnailUrl;

    /**
     * Whether the thumbnail is visible.
     */
    private boolean mThumbnailVisible;

    /**
     * @param thumbnailUrl The URL of the thumbnail to display
     */
    public void setThumbnailUrl(String thumbnailUrl) {
        // If a quick action icon is showing, the thumbnail should not be shown.
        if (mQuickActionIconVisible) return;

        mThumbnailUrl = thumbnailUrl;
    }

    /**
     * @return The URL used to fetch a thumbnail to display in the Bar. Will return an empty string
     *         if no thumbnail is available.
     */
    public String getThumbnailUrl() {
        return mThumbnailUrl != null ? mThumbnailUrl : "";
    }

    /**
     * @return Whether the thumbnail is visible.
     */
    public boolean getThumbnailVisible() {
        return mThumbnailVisible;
    }

    /**
     * Called when the thumbnail has finished being fetched.
     * @param success Whether fetching the thumbnail was successful.
     */
    public void onThumbnailFetched(boolean success) {
        // Check if the thumbnail URL was cleared before the thumbnail fetch completed. This may
        // occur if the user taps to refine the search.
        mThumbnailVisible = success && !TextUtils.isEmpty(mThumbnailUrl);
        if (!mThumbnailVisible) return;

        animateCustomImageVisibility(true);
    }

    // ============================================================================================
    // Custom image -- either a thumbnail or quick action icon
    // ============================================================================================

    /** The height and width of the image displayed at the start of the bar in px. */
    private int mBarImageSize;

    /**
     * The custom image visibility percentage, which dictates how and where to draw the custom
     * image. The custom image is not visible at all at 0.f and completely visible at 1.f.
     */
    private float mCustomImageVisibilityPercentage;

    /**
     * Hides the custom image if it is visible. Also resets the thumbnail URL and quick action icon
     * resource id.
     * @param animate Whether hiding the thumbnail should be animated.
     */
    public void hideCustomImage(boolean animate) {
        if ((mThumbnailVisible || mQuickActionIconVisible) && animate) {
            animateCustomImageVisibility(false);
        } else {
            mOverlayPanelAnimation.cancelAnimation(this, AnimationType.CUSTOM_IMAGE_VISIBILITY);
            onCustomImageHidden();
        }
    }

    /**
     * @return The height and width of the image displayed at the start of the bar in px.
     */
    public int getBarImageSize() {
        if (mBarImageSize == 0) {
            mBarImageSize = mContext.getResources().getDimensionPixelSize(
                    R.dimen.contextual_search_bar_image_size);
        }
        return mBarImageSize;
    }

    /**
     * @return The custom image visibility percentage, which dictates how and where to draw the
     *         custom image. The custom image is not visible at all at 0.f and completely visible at
     *         1.f. The custom image may be either a thumbnail or quick action icon.
     */
    public float getCustomImageVisibilityPercentage() {
        return mCustomImageVisibilityPercentage;
    }

    /**
     * Called when the custom image finishes hiding to reset thumbnail and quick action icon values.
     */
    private void onCustomImageHidden() {
        mQuickActionIconResourceId = 0;
        mQuickActionIconVisible = false;

        mThumbnailUrl = "";
        mThumbnailVisible = false;
        mCustomImageVisibilityPercentage = 0.f;
    }

    // ============================================================================================
    // Thumbnail Animation
    // ============================================================================================

    private Interpolator mCustomImageVisibilityInterpolator;

    private void animateCustomImageVisibility(boolean visible) {
        // If the panel is expanded then #onUpdateFromPeekToExpand() is responsible for setting
        // mCustomImageVisibility and the custom image appearance should not be animated.
        if (visible && mExpandedPercentage > 0.f) return;

        if (mCustomImageVisibilityInterpolator == null) {
            mCustomImageVisibilityInterpolator =
                    PathInterpolatorCompat.create(0.4f, 0.f, 0.6f, 1.f);
        }

        mOverlayPanelAnimation.cancelAnimation(this, AnimationType.CUSTOM_IMAGE_VISIBILITY);

        float endValue = visible ? 1.f : 0.f;
        mOverlayPanelAnimation.addToAnimation(this, AnimationType.CUSTOM_IMAGE_VISIBILITY,
                mCustomImageVisibilityPercentage, endValue,
                OverlayPanelAnimation.BASE_ANIMATION_DURATION_MS, 0, false,
                mCustomImageVisibilityInterpolator);
    }

    @Override
    public void setProperty(AnimationType prop, float val) {
        if (prop == AnimationType.CUSTOM_IMAGE_VISIBILITY) {
            // If the panel is expanded, #onUpdateFromPeekedToExpanded() is responsible for setting
            // mCustomImageVisiblityPercentage.
            if (mExpandedPercentage == 0.f) mCustomImageVisibilityPercentage = val;
        }
    }

    @Override
    public void onPropertyAnimationFinished(AnimationType prop) {
        if (prop == AnimationType.CUSTOM_IMAGE_VISIBILITY) {
            if (mCustomImageVisibilityPercentage == 0.f) {
                onCustomImageHidden();
            }
        }
    }
}

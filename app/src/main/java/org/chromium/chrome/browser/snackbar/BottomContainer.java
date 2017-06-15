// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.snackbar;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import org.chromium.chrome.browser.fullscreen.ChromeFullscreenManager;
import org.chromium.chrome.browser.fullscreen.ChromeFullscreenManager.FullscreenListener;

/**
 * The container that holds both infobars and snackbars. It will be translated up and down when the
 * bottom controls' offset changes.
 */
public class BottomContainer extends FrameLayout implements FullscreenListener {

    private ChromeFullscreenManager mFullscreenManager;

    /**
     * Constructor for XML inflation.
     */
    public BottomContainer(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * Initializes this container.
     */
    public void initialize(ChromeFullscreenManager fullscreenManager) {
        mFullscreenManager = fullscreenManager;
        mFullscreenManager.addListener(this);
        setTranslationY(-fullscreenManager.getBottomControlsHeight());
    }

    // FullscreenListner methods
    @Override
    public void onControlsOffsetChanged(float topOffset, float bottomOffset, boolean needsAnimate) {
        setTranslationY(bottomOffset - mFullscreenManager.getBottomControlsHeight());
    }

    @Override
    public void onBottomControlsHeightChanged(int bottomControlsHeight) {
        setTranslationY(-bottomControlsHeight);
    }

    @Override
    public void onContentOffsetChanged(float offset) { }

    @Override
    public void onToggleOverlayVideoMode(boolean enabled) { }
}

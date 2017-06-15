// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tab;

import android.view.ViewGroup;

import org.chromium.ui.base.ViewAndroidDelegate;

/**
 * Implementation of the abstract class {@link ViewAndroidDelegate} for Chrome.
 */
class TabViewAndroidDelegate extends ViewAndroidDelegate {
    /** Used for logging. */
    private static final String TAG = "TabVAD";

    private final Tab mTab;
    private final ViewGroup mContainerView;

    TabViewAndroidDelegate(Tab tab, ViewGroup containerView) {
        mTab = tab;
        mContainerView = containerView;
    }

    @Override
    public void onBackgroundColorChanged(int color) {
        mTab.onBackgroundColorChanged(color);
    }

    @Override
    public void onTopControlsChanged(float topControlsOffsetY, float topContentOffsetY) {
        mTab.onOffsetsChanged(topControlsOffsetY, Float.NaN, topContentOffsetY);
    }

    @Override
    public void onBottomControlsChanged(float bottomControlsOffsetY, float bottomContentOffsetY) {
        mTab.onOffsetsChanged(Float.NaN, bottomControlsOffsetY, Float.NaN);
    }

    @Override
    public int getSystemWindowInsetBottom() {
        return mTab.getSystemWindowInsetBottom();
    }

    @Override
    public ViewGroup getContainerView() {
        return mContainerView;
    }
}

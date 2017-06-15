// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.widget.bottomsheet;

import org.chromium.chrome.browser.widget.bottomsheet.BottomSheet.BottomSheetContent;

/**
 * An empty base implementation of the {@link BottomSheetObserver} interface.
 */
public class EmptyBottomSheetObserver implements BottomSheetObserver {
    @Override
    public void onSheetOpened() {}

    @Override
    public void onSheetClosed() {}

    @Override
    public void onSheetReleased() {}

    @Override
    public void onLoadUrl(String url) {}

    @Override
    public void onSheetOffsetChanged(float heightFraction) {}

    @Override
    public void onSheetLayout(int windowHeight, int containerHeight) {}

    @Override
    public void onTransitionPeekToHalf(float transitionFraction) {}

    @Override
    public void onSheetStateChanged(int newState) {}

    @Override
    public void onSheetContentChanged(BottomSheetContent newContent) {}
}

// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.view.View;

/**
 * A utility class that has helper methods for Android view.
 */
public final class ViewUtils {
    // Prevent instantiation.
    private ViewUtils() {}

    /**
     * @return {@code true} if the given view has a focus.
     */
    public static boolean hasFocus(View view) {
        // If the container view is not focusable, we consider it always focused from
        // Chromium's point of view.
        return !view.isFocusable() ? true : view.hasFocus();
    }
}

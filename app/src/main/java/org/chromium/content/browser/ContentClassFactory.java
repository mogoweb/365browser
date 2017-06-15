// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import org.chromium.base.ThreadUtils;
import org.chromium.ui.base.WindowAndroid;

/**
 * A class factory for content browser layer.
 */
public class ContentClassFactory {
    private static ContentClassFactory sSingleton;

    /**
     * Sets the factory object.
     */
    public static void set(ContentClassFactory factory) {
        ThreadUtils.assertOnUiThread();

        sSingleton = factory;
    }

    /**
     * Returns the factory object.
     */
    public static ContentClassFactory get() {
        ThreadUtils.assertOnUiThread();

        if (sSingleton == null) sSingleton = new ContentClassFactory();
        return sSingleton;
    }

    /**
     * Constructor.
     */
    protected ContentClassFactory() {}

    /**
     * Creates SmartSelectorProvider object.
     */
    public SmartSelectionProvider createSmartSelectionProvider(
            SmartSelectionProvider.ResultCallback callback, WindowAndroid windowAndroid) {
        // Implemented by a subclass.
        return null;
    }
}

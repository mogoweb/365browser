// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import org.chromium.ui.base.WindowAndroid;

/**
 * WindowAndroidProvider is an interface that provides functionality to get WindowAndroid and
 * observe changes whenver WindowAndroid is updated by the class that implements this interface.
 */
public interface WindowAndroidProvider {
    /**
     * Gets WindowAndroid.
     */
    WindowAndroid getWindowAndroid();

    /**
     * Adds WindowAndroidChangedObserver observer.
     */
    void addWindowAndroidChangedObserver(WindowAndroidChangedObserver observer);

    /**
     * Removes WindowAndroidChangedObserver observer.
     */
    void removeWindowAndroidChangedObserver(WindowAndroidChangedObserver observer);
}
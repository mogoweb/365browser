// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import org.chromium.ui.base.WindowAndroid;

/**
 * Observer interface that is used by WindowAndroidProvider to notify observers when
 * WindowAndroidProvider changes its window.
 */
public interface WindowAndroidChangedObserver {
    /**
     * Notifies observer when WindowAndroid is changed.
     */
    void onWindowAndroidChanged(WindowAndroid newWindowAndroid);
}

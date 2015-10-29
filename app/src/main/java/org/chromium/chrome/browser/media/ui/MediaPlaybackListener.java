// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.ui;

/**
 * Interface for classes that need to be notified about media events.
 */
public interface MediaPlaybackListener {
    /**
     * Called when the user wants to resume the playback.
     */
    void onPlay();

    /**
     * Called when the user wants to pause the playback.
     */
    void onPause();

    /**
     * Called when the user wants to stop the playback.
     */
    void onStop();
}

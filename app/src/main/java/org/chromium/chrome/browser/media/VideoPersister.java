// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media;

import org.chromium.base.ThreadUtils;
import org.chromium.chrome.browser.AppHooks;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.tab.Tab;

/**
 * Utilities for persisting fullscreen video on Chrome exit.
 *
 * You should not instantiate this class yourself, only use it through {@link #getInstance()}.
 */
public class VideoPersister {
    private static VideoPersister sInstance;

    /**
     * Returns the singleton instance of VideoPersister, creating it if needed.
     */
    public static VideoPersister getInstance() {
        ThreadUtils.assertOnUiThread();
        if (sInstance == null) {
            sInstance = AppHooks.get().createVideoPersister();
        }
        return sInstance;
    }

    /**
     * If this method returns true, a tab should not toggle fullscreen. Calling this method queues
     * a fullscreen toggle at an appropriate later time.
     */
    public boolean shouldDelayFullscreenModeChange(Tab tab, boolean fullscreen) {
        return false;
    }

    /**
     * This method will persist a video if possible.
     */
    public void attemptPersist(ChromeActivity activity) {}

    /**
     * If the video has been persisted, perform cleanup.
     */
    public void cleanup(ChromeActivity activity) {}
}

// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.ui;

import android.text.TextUtils;

/**
 * Exposes information about the current media notification to the external clients.
 */
public class MediaNotificationInfo {
    /**
     * The title of the media.
     */
    public final String title;

    /**
     * The current state of the media, paused or not.
     */
    public boolean isPaused;

    /**
     * The origin of the tab containing the media.
     */
    public final String origin;

    /**
     * The id of the tab containing the media.
     */
    public final int tabId;

    /**
     * Whether the media notification should be considered as private.
     */
    public final boolean isPrivate;

    /**
     * The listener for the control events.
     */
    public final MediaPlaybackListener listener;

    /**
     * Create a new MediaNotificationInfo.
     * @param title
     * @param state
     * @param origin
     * @param tabId
     * @param listener
     */
    public MediaNotificationInfo(
            String title,
            boolean isPaused,
            String origin,
            int tabId,
            boolean isPrivate,
            MediaPlaybackListener listener) {
        this.title = title;
        this.isPaused = isPaused;
        this.origin = origin;
        this.tabId = tabId;
        this.isPrivate = isPrivate;
        this.listener = listener;
    }

    /**
     * Copy a media info.
     * @param other the source to copy from.
     */
    public MediaNotificationInfo(MediaNotificationInfo other) {
        this(other.title,
             other.isPaused,
             other.origin,
             other.tabId,
             other.isPrivate,
             other.listener);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (!(obj instanceof MediaNotificationInfo)) return false;

        MediaNotificationInfo other = (MediaNotificationInfo) obj;
        return isPaused == other.isPaused
                && isPrivate == other.isPrivate
                && tabId == other.tabId
                && TextUtils.equals(title, other.title)
                && TextUtils.equals(origin, other.origin)
                && listener.equals(other.listener);
    }

    @Override
    public int hashCode() {
        int result = isPaused ? 1 : 0;
        result = 31 * result + (isPrivate ? 1 : 0);
        result = 31 * result + (title == null ? 0 : title.hashCode());
        result = 31 * result + (origin == null ? 0 : origin.hashCode());
        result = 31 * result + tabId;
        result = 31 * result + listener.hashCode();
        return result;
    }
}

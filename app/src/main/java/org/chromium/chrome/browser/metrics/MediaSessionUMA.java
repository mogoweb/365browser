// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.metrics;

import org.chromium.base.metrics.RecordHistogram;

/**
 * Centralizes UMA data collection for Android-specific MediaSession features.
 */
public class MediaSessionUMA {
    // MediaSessionAction defined in tools/metrics/histograms/histograms.xml.
    public static final int MEDIA_SESSION_ACTION_SOURCE_MEDIA_NOTIFICATION = 0;
    // TODO(mlamouri): UMA do not handle well enumerations with only one value.
    // Other values will be addede later (like RemoteContro/MediaSession/etc.)
    // interactions but we have to fax the max value for now in order to prevent
    // crashes.
    public static final int MEDIA_SESSION_ACTION_SOURCE_MAX = 2;

    public static void recordPlay(int action) {
        assert action >= 0 && action < MEDIA_SESSION_ACTION_SOURCE_MAX;
        RecordHistogram.recordEnumeratedHistogram("Media.Session.Play", action,
                MEDIA_SESSION_ACTION_SOURCE_MAX);
    }

    public static void recordPause(int action) {
        assert action >= 0 && action < MEDIA_SESSION_ACTION_SOURCE_MAX;
        RecordHistogram.recordEnumeratedHistogram("Media.Session.Pause", action,
                MEDIA_SESSION_ACTION_SOURCE_MAX);
    }
}

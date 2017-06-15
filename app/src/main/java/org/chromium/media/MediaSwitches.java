// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.media;

/**
 * Contains command line switches that are specific to the media layer.
 */
public abstract class MediaSwitches {
    // Ignores all autoplay restrictions. It will ignore the current autoplay policy and all
    // restrictions such as playback in a background tab. It should only be enabled for testing.
    public static final String IGNORE_AUTOPLAY_RESTRICTIONS_FOR_TESTS =
            "ignore-autoplay-restrictions";

    // Prevents instantiation.
    private MediaSwitches() {}
}

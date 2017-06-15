// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.components.ntptiles;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.ContextUtils;
import org.chromium.base.annotations.CalledByNative;

/**
 * Utility class containing Android-specific functionality for the native MostVisitedSites.
 */
public final class MostVisitedSites {

    private MostVisitedSites() {}

    @CalledByNative
    private static boolean isPopularSitesForceEnabled() {
        return ApiCompatibilityUtils.isDemoUser(ContextUtils.getApplicationContext());
    }

}

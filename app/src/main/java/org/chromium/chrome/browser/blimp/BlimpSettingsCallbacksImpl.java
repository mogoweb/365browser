// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.blimp;

import org.chromium.blimp_public.BlimpSettingsCallbacks;
import org.chromium.chrome.browser.ApplicationLifetime;

/**
 * Implementation of {@link BlimpSettingsCallbacks} for Chrome to listen to blimp Setting events.
 */
public class BlimpSettingsCallbacksImpl implements BlimpSettingsCallbacks {

    @Override
    public void onRestartBrowserRequested() {
        ApplicationLifetime.terminate(true);
    }
}

// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.physicalweb;

import android.content.Intent;
import android.os.Build;

import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.share.ShareActivity;

/**
 * A simple activity that allows Chrome to start the physical web sharing service.
 */
public class PhysicalWebShareActivity extends ShareActivity {
    @Override
    protected void handleShareAction(ChromeActivity triggeringActivity) {
        String url = triggeringActivity.getActivityTab().getUrl();

        if (!PhysicalWeb.sharingIsOptedIn()) {
            // This shows an interstitial for the user to opt-in for sending URL to Google.
            Intent intent = new Intent(this, PhysicalWebShareEntryActivity.class);
            intent.putExtra(PhysicalWebShareEntryActivity.SHARING_ENTRY_URL, url);
            triggeringActivity.startActivity(intent);
            return;
        }

        PhysicalWebBroadcastService.startBroadcastService(url);
    }

    /**
     * Returns whether we should show this sharing option in the share sheet.
     * Pre-conditions for Physical Web Sharing to be enabled:
     *      Device has hardware BLE advertising capabilities.
     *      Device had Bluetooth on.
     *      Device is Marshmallow or above.
     *      Device has sharing feature enabled.
     * @return {@code true} if the feature should be enabled.
     */
    public static boolean featureIsAvailable() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false;
        return PhysicalWeb.hasBleAdvertiseCapability() && PhysicalWeb.bluetoothIsEnabled()
                && PhysicalWeb.sharingIsEnabled();
    }
}

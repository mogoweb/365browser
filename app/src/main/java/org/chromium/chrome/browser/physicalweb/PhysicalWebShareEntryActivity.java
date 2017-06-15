// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.physicalweb;

import android.app.Activity;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;

import org.chromium.chrome.R;

/**
 * Activity used to interact with user before starting Physical Web Sharing.
 * TODO(iankc): add Bluetooth checks handling.
 */
public class PhysicalWebShareEntryActivity extends Activity {
    public static final String SHARING_ENTRY_URL = "physical_web.entry.url";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle extras = getIntent().getExtras();
        if (extras == null) {
            finish();
            return;
        }

        final String url = extras.getString(SHARING_ENTRY_URL);
        if (url == null) {
            finish();
            return;
        }

        new AlertDialog.Builder(this, R.style.AlertDialogTheme)
                .setTitle(R.string.physical_web_share_entry_title)
                .setMessage(R.string.physical_web_share_entry_message)
                .setPositiveButton(R.string.continue_button,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                PhysicalWeb.setSharingOptedIn();
                                PhysicalWebBroadcastService.startBroadcastService(url);
                                finish();
                            }
                        })
                .setNegativeButton(R.string.cancel,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                finish();
                            }
                        })
                .setIcon(android.R.drawable.ic_dialog_info)
                .setCancelable(false)
                .show();
    }
}
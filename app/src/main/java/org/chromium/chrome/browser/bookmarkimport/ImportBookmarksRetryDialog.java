// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.bookmarkimport;

import android.app.Dialog;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;

import org.chromium.chrome.R;

/**
 * The dialog asked to retry when the import bookmarks failed.
 */
public class ImportBookmarksRetryDialog extends ImportBookmarksAlertDialog {
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder alertDialogBuilder =
                new AlertDialog.Builder(getActivity(), R.style.AlertDialogTheme)
                .setTitle(R.string.import_bookmarks_failed_header)
                .setPositiveButton(R.string.import_bookmarks_retry, this)
                .setNegativeButton(R.string.cancel, this)
                .setMessage(R.string.import_bookmarks_failed_message);
        return alertDialogBuilder.create();
    }
}

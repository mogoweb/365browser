// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.bookmarkimport;

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;

import org.chromium.chrome.R;

/**
 * This dialog to confirm user want to import bookmarks.
 */
public class ImportBookmarksAlertDialog extends DialogFragment
        implements DialogInterface.OnClickListener {

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder alertDialogBuilder =
                new AlertDialog.Builder(getActivity(), R.style.AlertDialogTheme)
                .setTitle(R.string.import_bookmarks)
                .setPositiveButton(R.string.import_bookmarks_ok, this)
                .setNegativeButton(R.string.cancel, this)
                .setMessage(R.string.import_bookmarks_prompt);
        return alertDialogBuilder.create();
    }

    private void importBookmarks() {
        ImportBookmarksProgressDialog progressDialog = new ImportBookmarksProgressDialog();
        progressDialog.show(getActivity().getFragmentManager(), null);
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == AlertDialog.BUTTON_POSITIVE) importBookmarks();
    }
}

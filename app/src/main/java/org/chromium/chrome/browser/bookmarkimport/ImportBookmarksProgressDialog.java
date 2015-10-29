// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.bookmarkimport;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.chrome.browser.document.ChromeLauncherActivity;
import org.chromium.chrome.browser.enhancedbookmarks.EnhancedBookmarkUtils;

/**
 * Show the progress dialog and import bookmarks from other browsers.
 */
public class ImportBookmarksProgressDialog extends DialogFragment
            implements BookmarkImporter.OnBookmarksImportedListener {
    private ProgressDialog mProgressDialog;
    private Activity mActivity;
    private BookmarkImporter mImporter;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        mActivity = getActivity();
        mProgressDialog = ProgressDialog.show(mActivity,
                mActivity.getString(R.string.import_bookmarks_progress_header),
                mActivity.getString(R.string.import_bookmarks_progress_message),
                true, true);
        mImporter = new AndroidBrowserImporter(mActivity);
        mImporter.importBookmarks(this);
        return mProgressDialog;
    }

    @Override
    public void onBookmarksImported(BookmarkImporter.ImportResults results) {
        if (mProgressDialog != null && mProgressDialog.isShowing()) mProgressDialog.dismiss();
        if (results != null && results.numImported == results.newBookmarks) {
            if (!EnhancedBookmarkUtils.showEnhancedBookmarkIfEnabled(mActivity)) {
                // Since the most probable use after importing bookmarks is to navigate them it
                // makes more sense to use the normal tab view instead of the embedded one for
                // preferences.
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.addFlags(Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                intent.setData(Uri.parse(UrlConstants.BOOKMARKS_FOLDER_URL + results.rootFolderId));
                intent.setPackage(mActivity.getPackageName());
                intent.setClassName(mActivity.getApplicationContext().getPackageName(),
                        ChromeLauncherActivity.class.getName());
                mActivity.startActivity(intent);
            }

        } else {
            ImportBookmarksRetryDialog retryDialog = new ImportBookmarksRetryDialog();
            retryDialog.show(mActivity.getFragmentManager(), null);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        // Cancel the import task.
        if (mImporter != null) mImporter.cancel();
    }
}

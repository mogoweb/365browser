// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download.ui;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.StatFs;
import android.support.v7.widget.RecyclerView;
import android.text.format.Formatter;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.Log;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.R;

import java.io.File;

/** A View that manages the display of space used by the downloads. */
class SpaceDisplay extends RecyclerView.AdapterDataObserver {
    private static final String TAG = "download_ui";
    private static final long BYTES_PER_GIGABYTE = 1024 * 1024 * 1024;

    private final AsyncTask<Void, Void, Long> mFileSystemBytesTask;

    private DownloadHistoryAdapter mHistoryAdapter;
    private TextView mSpaceUsedTextView;
    private TextView mSpaceTotalTextView;
    private ProgressBar mSpaceBar;
    private long mFileSystemBytes = Long.MAX_VALUE;

    SpaceDisplay(final ViewGroup parent, DownloadHistoryAdapter historyAdapter) {
        mHistoryAdapter = historyAdapter;
        mSpaceUsedTextView = (TextView) parent.findViewById(R.id.space_used_display);
        mSpaceTotalTextView = (TextView) parent.findViewById(R.id.space_total_display);
        mSpaceBar = (ProgressBar) parent.findViewById(R.id.space_bar);
        mFileSystemBytesTask =
                createStorageSizeTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private AsyncTask<Void, Void, Long> createStorageSizeTask() {
        return new AsyncTask<Void, Void, Long>() {
            @Override
            protected Long doInBackground(Void... params) {
                File downloadDirectory = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS);

                // Create the downloads directory, if necessary.
                if (!downloadDirectory.exists()) {
                    try {
                        // mkdirs() can fail, so we still need to check if the directory exists
                        // later.
                        downloadDirectory.mkdirs();
                    } catch (SecurityException e) {
                        Log.e(TAG, "SecurityException when creating download directory.", e);
                    }
                }

                // Determine how much space is available on the storage device where downloads
                // reside.  If the downloads directory doesn't exist, it is likely that the user
                // doesn't have an SD card installed.
                long fileSystemBytes = 0;
                if (downloadDirectory.exists()) {
                    StatFs statFs = new StatFs(downloadDirectory.getPath());
                    long totalBlocks = ApiCompatibilityUtils.getBlockCount(statFs);
                    long blockSize = ApiCompatibilityUtils.getBlockSize(statFs);
                    fileSystemBytes = totalBlocks * blockSize;
                } else {
                    Log.e(TAG, "Download directory doesn't exist.");
                }
                return fileSystemBytes;
            }
        };
    }

    @Override
    public void onChanged() {
        Context context = mSpaceUsedTextView.getContext();

        if (mFileSystemBytes == Long.MAX_VALUE) {
            try {
                mFileSystemBytes = mFileSystemBytesTask.get();
            } catch (Exception e) {
                Log.e(TAG, "Failed to get file system size.");
            }

            // Display how big the storage is.
            String fileSystemReadable = Formatter.formatFileSize(context, mFileSystemBytes);

            float fileSystemGigabytes = convertBytesToGigabytes(mFileSystemBytes);
            mSpaceTotalTextView.setText(context.getResources().getString(
                    R.string.download_manager_ui_space_available, fileSystemGigabytes));
        }

        // Indicate how much space has been used by downloads.
        long usedBytes = mHistoryAdapter.getTotalDownloadSize();
        int percentage =
                mFileSystemBytes == 0 ? 0 : (int) (100.0 * usedBytes / mFileSystemBytes);
        mSpaceBar.setProgress(percentage);

        float usedGigabytes = convertBytesToGigabytes(usedBytes);
        mSpaceUsedTextView.setText(context.getResources().getString(
                R.string.download_manager_ui_space_used, usedGigabytes));

        RecordHistogram.recordPercentageHistogram("Android.DownloadManager.SpaceUsed", percentage);
    }

    private float convertBytesToGigabytes(long bytes) {
        return bytes / (float) BYTES_PER_GIGABYTE;
    }
}

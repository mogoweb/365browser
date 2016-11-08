// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download;

import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;

import org.chromium.base.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * A wrapper for Android DownloadManager to provide utility functions.
 */
public class DownloadManagerDelegate {
    private static final String TAG = "DownloadDelegate";
    protected final Context mContext;

    public DownloadManagerDelegate(Context context) {
        mContext = context;
    }

    /**
     * @see android.app.DownloadManager#addCompletedDownload(String, String, boolean, String,
     * String, long, boolean)
     */
    protected long addCompletedDownload(String fileName, String description, String mimeType,
            String path, long length, String originalUrl, String referer) {
        DownloadManager manager =
                (DownloadManager) mContext.getSystemService(Context.DOWNLOAD_SERVICE);
        String newMimeType =
                ChromeDownloadDelegate.remapGenericMimeType(mimeType, originalUrl, fileName);
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
            Class<?> c = manager.getClass();
            try {
                Class[] args = {String.class, String.class, boolean.class, String.class,
                        String.class, long.class, boolean.class, Uri.class, Uri.class};
                Method method = c.getMethod("addCompletedDownload", args);
                Uri originalUri = Uri.parse(originalUrl);
                Uri refererUri = referer == null ? Uri.EMPTY : Uri.parse(referer);
                return (Long) method.invoke(manager, fileName, description, true, newMimeType, path,
                        length, false, originalUri, refererUri);
            } catch (SecurityException e) {
                Log.e(TAG, "Cannot access the needed method.");
            } catch (NoSuchMethodException e) {
                Log.e(TAG, "Cannot find the needed method.");
            } catch (InvocationTargetException e) {
                Log.e(TAG, "Error calling the needed method.");
            } catch (IllegalAccessException e) {
                Log.e(TAG, "Error accessing the needed method.");
            }
        }
        return manager.addCompletedDownload(fileName, description, true, newMimeType, path, length,
                false);
    }

    /**
     * Interface for returning the query result when it completes.
     */
    public interface DownloadQueryCallback {
        /**
         * Callback function to return query result.
         * @param result Query result from android DownloadManager.
         * @param showNotifications Whether to show status notifications.
         */
        public void onQueryCompleted(DownloadQueryResult result, boolean showNotifications);
    }

    /**
     * Result for querying the Android DownloadManager.
     */
    static class DownloadQueryResult {
        public final DownloadItem item;
        public final int downloadStatus;
        public final long downloadTimeInMilliseconds;
        public final long bytesDownloaded;
        public final boolean canResolve;
        public final int failureReason;

        DownloadQueryResult(DownloadItem item, int downloadStatus, long downloadTimeInMilliseconds,
                long bytesDownloaded, boolean canResolve, int failureReason) {
            this.item = item;
            this.downloadStatus = downloadStatus;
            this.downloadTimeInMilliseconds = downloadTimeInMilliseconds;
            this.canResolve = canResolve;
            this.bytesDownloaded = bytesDownloaded;
            this.failureReason = failureReason;
        }
    }

    /**
     * Query the Android DownloadManager for download status.
     * @param downloadItem Download item to query.
     * @param showNotifications Whether to show status notifications.
     * @param callback Callback to be notified when query completes.
     */
    void queryDownloadResult(
            DownloadItem downloadItem, boolean showNotifications, DownloadQueryCallback callback) {
        DownloadQueryTask task = new DownloadQueryTask(downloadItem, showNotifications, callback);
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * Async task to query download status from Android DownloadManager
     */
    private class DownloadQueryTask extends AsyncTask<Void, Void, DownloadQueryResult> {
        private final DownloadItem mDownloadItem;
        private final boolean mShowNotifications;
        private final DownloadQueryCallback mCallback;

        public DownloadQueryTask(DownloadItem downloadItem, boolean showNotifications,
                DownloadQueryCallback callback) {
            mDownloadItem = downloadItem;
            mShowNotifications = showNotifications;
            mCallback = callback;
        }

        @Override
        public DownloadQueryResult doInBackground(Void... voids) {
            DownloadManager manager =
                    (DownloadManager) mContext.getSystemService(Context.DOWNLOAD_SERVICE);
            Cursor c = manager.query(
                    new DownloadManager.Query().setFilterById(mDownloadItem.getSystemDownloadId()));
            if (c == null) {
                return new DownloadQueryResult(mDownloadItem,
                        DownloadManagerService.DOWNLOAD_STATUS_CANCELLED, 0, 0, false, 0);
            }
            long bytesDownloaded = 0;
            boolean canResolve = false;
            int downloadStatus = DownloadManagerService.DOWNLOAD_STATUS_IN_PROGRESS;
            int failureReason = 0;
            long lastModifiedTime = 0;
            if (c.moveToNext()) {
                int statusIndex = c.getColumnIndex(DownloadManager.COLUMN_STATUS);
                int status = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS));
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    downloadStatus = DownloadManagerService.DOWNLOAD_STATUS_COMPLETE;
                    if (mShowNotifications) {
                        canResolve = DownloadManagerService.isOMADownloadDescription(
                                mDownloadItem.getDownloadInfo())
                                || DownloadManagerService.canResolveDownloadItem(
                                        mContext, mDownloadItem);
                    }
                } else if (status == DownloadManager.STATUS_FAILED) {
                    downloadStatus = DownloadManagerService.DOWNLOAD_STATUS_FAILED;
                    failureReason = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_REASON));
                }
                lastModifiedTime =
                        c.getLong(c.getColumnIndex(DownloadManager.COLUMN_LAST_MODIFIED_TIMESTAMP));
                bytesDownloaded =
                        c.getLong(c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
            } else {
                downloadStatus = DownloadManagerService.DOWNLOAD_STATUS_CANCELLED;
            }
            c.close();
            long totalTime = Math.max(0, lastModifiedTime - mDownloadItem.getStartTime());
            return new DownloadQueryResult(mDownloadItem, downloadStatus, totalTime,
                    bytesDownloaded, canResolve, failureReason);
        }

        @Override
        protected void onPostExecute(DownloadQueryResult result) {
            mCallback.onQueryCompleted(result, mShowNotifications);
        }
    }
}

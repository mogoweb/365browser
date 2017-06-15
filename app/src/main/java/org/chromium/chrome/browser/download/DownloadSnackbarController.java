// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;

import org.chromium.base.ApplicationStatus;
import org.chromium.base.BuildInfo;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.customtabs.CustomTabActivity;
import org.chromium.chrome.browser.download.items.OfflineContentAggregatorNotificationBridgeUiFactory;
import org.chromium.chrome.browser.offlinepages.downloads.OfflinePageDownloadBridge;
import org.chromium.chrome.browser.snackbar.Snackbar;
import org.chromium.chrome.browser.snackbar.SnackbarManager;
import org.chromium.components.offline_items_collection.LegacyHelpers;

/**
 * Class for displaying a snackbar when a download completes.
 */
public class DownloadSnackbarController implements SnackbarManager.SnackbarController {
    public static final int INVALID_NOTIFICATION_ID = -1;
    private static final int SNACKBAR_DURATION_IN_MILLISECONDS = 5000;
    private final Context mContext;

    private static class ActionDataInfo {
        public final DownloadInfo downloadInfo;
        public final int notificationId;
        public final long systemDownloadId;
        public final boolean usesAndroidDownloadManager;

        ActionDataInfo(DownloadInfo downloadInfo, int notificationId, long systemDownloadId,
                boolean usesAndroidDownloadManager) {
            this.downloadInfo = downloadInfo;
            this.notificationId = notificationId;
            this.systemDownloadId = systemDownloadId;
            this.usesAndroidDownloadManager = usesAndroidDownloadManager;
        }
    }

    public DownloadSnackbarController(Context context) {
        mContext = context;
    }

    @Override
    public void onAction(Object actionData) {
        if (!(actionData instanceof ActionDataInfo)) {
            DownloadManagerService.openDownloadsPage(mContext);
            return;
        }

        DownloadManagerService manager = DownloadManagerService.getDownloadManagerService();
        final ActionDataInfo download = (ActionDataInfo) actionData;
        if (LegacyHelpers.isLegacyOfflinePage(download.downloadInfo.getContentId())) {
            OfflinePageDownloadBridge.openDownloadedPage(download.downloadInfo.getContentId());
        } else if (LegacyHelpers.isLegacyDownload(download.downloadInfo.getContentId())) {
            if (download.usesAndroidDownloadManager) {
                mContext.startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)
                                               .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } else {
                manager.openDownloadedContent(download.downloadInfo, download.systemDownloadId);
            }
        } else {
            OfflineContentAggregatorNotificationBridgeUiFactory.instance().openItem(
                    download.downloadInfo.getContentId());
        }

        if (download.notificationId != INVALID_NOTIFICATION_ID) {
            manager.getDownloadNotifier().removeDownloadNotification(
                    download.notificationId, download.downloadInfo);
        }
    }

    @Override
    public void onDismissNoAction(Object actionData) {
    }

    /**
     * Called to display the download succeeded snackbar.
     *
     * @param downloadInfo Info of the download.
     * @param notificationId Notification Id of the successful download.
     * @param downloadId Id of the download from Android DownloadManager.
     * @param canBeResolved Whether the download can be resolved to any activity.
     * @param usesAndroidDownloadManager Whether the download uses Android DownloadManager.
     */
    public void onDownloadSucceeded(
            DownloadInfo downloadInfo, int notificationId, long downloadId, boolean canBeResolved,
            boolean usesAndroidDownloadManager) {
        if (getSnackbarManager() == null) return;
        Snackbar snackbar;
        if (getActivity() instanceof CustomTabActivity) {
            String packageLabel = BuildInfo.getPackageLabel();
            snackbar = Snackbar.make(mContext.getString(R.string.download_succeeded_message,
                    downloadInfo.getFileName(), packageLabel),
                    this, Snackbar.TYPE_NOTIFICATION, Snackbar.UMA_DOWNLOAD_SUCCEEDED);
        } else {
            snackbar = Snackbar.make(mContext.getString(R.string.download_succeeded_message_default,
                    downloadInfo.getFileName()),
                    this, Snackbar.TYPE_NOTIFICATION, Snackbar.UMA_DOWNLOAD_SUCCEEDED);
        }
        // TODO(qinmin): Coalesce snackbars if multiple downloads finish at the same time.
        snackbar.setDuration(SNACKBAR_DURATION_IN_MILLISECONDS).setSingleLine(false);
        ActionDataInfo info = null;
        if (canBeResolved || !LegacyHelpers.isLegacyDownload(downloadInfo.getContentId())
                || usesAndroidDownloadManager) {
            info = new ActionDataInfo(downloadInfo, notificationId, downloadId,
                    usesAndroidDownloadManager);
        }
        // Show downloads app if the download cannot be resolved to any activity.
        snackbar.setAction(
                mContext.getString(R.string.open_downloaded_label), info);
        getSnackbarManager().showSnackbar(snackbar);
    }

    /**
     * Called to display the download failed snackbar.
     *
     * @param errorMessage     The message to show on the snackbar.
     * @param showAllDownloads Whether to show all downloads in case the failure is caused by
     *                         duplicated files.
     */
    public void onDownloadFailed(String errorMessage, boolean showAllDownloads) {
        if (getSnackbarManager() == null) return;
        // TODO(qinmin): Coalesce snackbars if multiple downloads finish at the same time.
        Snackbar snackbar = Snackbar
                .make(errorMessage, this, Snackbar.TYPE_NOTIFICATION, Snackbar.UMA_DOWNLOAD_FAILED)
                .setSingleLine(false)
                .setDuration(SNACKBAR_DURATION_IN_MILLISECONDS);
        if (showAllDownloads) {
            snackbar.setAction(
                    mContext.getString(R.string.open_downloaded_label),
                    null);
        }
        getSnackbarManager().showSnackbar(snackbar);
    }

    private Activity getActivity() {
        if (ApplicationStatus.hasVisibleActivities()) {
            return ApplicationStatus.getLastTrackedFocusedActivity();
        } else {
            return null;
        }
    }

    public SnackbarManager getSnackbarManager() {
        Activity activity = getActivity();
        if (activity != null && activity instanceof SnackbarManager.SnackbarManageable) {
            return ((SnackbarManager.SnackbarManageable) activity).getSnackbarManager();
        }
        return null;
    }
}

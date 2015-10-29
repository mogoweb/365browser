// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download;

import android.Manifest.permission;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;

import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.infobar.ConfirmInfoBar;
import org.chromium.chrome.browser.infobar.InfoBar;
import org.chromium.chrome.browser.infobar.InfoBarListeners;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.content.browser.ContentViewDownloadDelegate;
import org.chromium.content.browser.DownloadInfo;
import org.chromium.content_public.browser.WebContents;
import org.chromium.ui.base.WindowAndroid;
import org.chromium.ui.base.WindowAndroid.PermissionCallback;
import org.chromium.ui.widget.Toast;

import java.io.File;

/**
 * Chrome implementation of the ContentViewDownloadDelegate interface.
 *
 * Listens to POST and GET download events. GET download requests are passed along to the
 * Android Download Manager. POST downloads are expected to be handled natively and listener
 * is responsible for adding the completed download to the download manager.
 *
 * Prompts the user when a dangerous file is downloaded. Auto-opens PDFs after downloading.
 */
public class ChromeDownloadDelegate
        implements ContentViewDownloadDelegate, InfoBarListeners.Confirm {
    // The application context.
    private final Context mContext;
    private final Tab mTab;
    private final TabModelSelector mTabModelSelector;
    private static final String LOGTAG = "ChromeDownloadDelegate";

    // Pending download request for a dangerous file.
    private DownloadInfo mPendingRequest;

    @Override
    public void onConfirmInfoBarButtonClicked(ConfirmInfoBar infoBar, boolean confirm) {
        if (mPendingRequest.hasDownloadId()) {
            nativeDangerousDownloadValidated(mTab, mPendingRequest.getDownloadId(), confirm);
            if (confirm) {
                showDownloadStartNotification();
            }
            closeBlankTab();
        } else if (confirm) {
            // User confirmed the download.
            if (mPendingRequest.isGETRequest()) {
                enqueueDownloadManagerRequest(mPendingRequest);
            } else {
                DownloadInfo newDownloadInfo = DownloadInfo.Builder.fromDownloadInfo(
                        mPendingRequest).setIsSuccessful(true).build();
                DownloadManagerService.getDownloadManagerService(mContext).onDownloadCompleted(
                        newDownloadInfo);
            }
        } else {
            // User did not accept the download, discard the file if it is a POST download.
            if (!mPendingRequest.isGETRequest()) {
                discardFile(mPendingRequest.getFilePath());
            }
        }
        mPendingRequest = null;
        infoBar.dismissJavaOnlyInfoBar();
    }

    @Override
    public void onInfoBarDismissed(InfoBar infoBar) {
        if (mPendingRequest != null) {
            if (mPendingRequest.hasDownloadId()) {
                nativeDangerousDownloadValidated(mTab, mPendingRequest.getDownloadId(), false);
            } else if (!mPendingRequest.isGETRequest()) {
                // Infobar was dismissed, discard the file if a POST download is pending.
                discardFile(mPendingRequest.getFilePath());
            }
        }
        // Forget the pending request.
        mPendingRequest = null;
    }

    /**
     * Creates ChromeDownloadDelegate.
     * @param context The application context.
     * @param tabModelSelector The TabModelSelector responsible for {@code mTab}.
     * @param tab The corresponding tab instance.
     */
    public ChromeDownloadDelegate(
            Context context, TabModelSelector tabModelSelector, Tab tab) {
        mContext = context;
        mTab = tab;
        mTabModelSelector = tabModelSelector;
        mPendingRequest = null;
    }

    /**
     * Return the download path of a file.
     * @param fileName Name of the file.
     * @return path of the saved file.
     */
    protected String downloadPath(String fileName) {
        return mContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) + "/" + fileName;
    }

    /**
     * Request a download from the given url, or if a streaming viewer is available stream the
     * content into the viewer.
     * @param downloadInfo Information about the download.
     */
    @Override
    public void requestHttpGetDownload(DownloadInfo downloadInfo) {
        // If we're dealing with A/V content that's not explicitly marked for download, check if it
        // is streamable.
        if (!DownloadManagerService.isAttachment(downloadInfo.getContentDisposition())) {
            // Query the package manager to see if there's a registered handler that matches.
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.parse(downloadInfo.getUrl()), downloadInfo.getMimeType());
            // If the intent is resolved to ourselves, we don't want to attempt to load the url
            // only to try and download it again.
            if (DownloadManagerService.openIntent(mContext, intent, false)) {
                return;
            }
        }
        onDownloadStartNoStream(downloadInfo);
    }

    /**
     * Decide the file name of the final download. The file extension is derived
     * from the MIME type.
     * @param url The full URL to the content that should be downloaded.
     * @param mimeType The MIME type of the content reported by the server.
     * @param contentDisposition Content-Disposition HTTP header, if present.
     * @return The best guess of the file name for the downloaded object.
     */
    @VisibleForTesting
    public static String fileName(String url, String mimeType, String contentDisposition) {
        // URLUtil#guessFileName will prefer the MIME type extension over
        // the file extension only if the latter is of a known MIME type.
        // Therefore for things like "file.php" with Content-Type PDF, it will
        // still generate file names like "file.php" instead of "file.pdf".
        // If that's the case, rebuild the file extension from the MIME type.
        String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);
        int dotIndex = fileName.lastIndexOf('.');
        if (mimeType != null
                && !mimeType.isEmpty()
                && dotIndex > 1  // at least one char before the '.'
                && dotIndex < fileName.length()) { // '.' should not be the last char
            MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();

            String fileRoot = fileName.substring(0, dotIndex);
            String fileExtension = fileName.substring(dotIndex + 1);
            String fileExtensionMimeType =
                    mimeTypeMap.getMimeTypeFromExtension(fileExtension);

            // If the file extension's official MIME type and {@code mimeType}
            // are the same, simply use the file extension.
            // If not, extension derived from {@code mimeType} is preferred.
            if (mimeType.equals(fileExtensionMimeType)) {
                fileName = fileRoot + "." + fileExtension;
            } else {
                String mimeExtension =
                        mimeTypeMap.getExtensionFromMimeType(mimeType);

                if (mimeExtension != null && !mimeExtension.equals(fileExtension)) {
                    fileName = fileRoot + "." + mimeExtension;
                }
            }
        }
        return fileName;
    }

    /**
     * Notify the host application a download should be done, even if there is a
     * streaming viewer available for this type.
     *
     * @param downloadInfo Information about the download.
     */
    protected void onDownloadStartNoStream(DownloadInfo downloadInfo) {
        final String newMimeType = remapGenericMimeType(
                downloadInfo.getMimeType(),
                downloadInfo.getUrl(),
                downloadInfo.getFileName());
        final String path = TextUtils.isEmpty(downloadInfo.getFileName())
                ? fileName(downloadInfo.getUrl(), newMimeType, downloadInfo.getContentDisposition())
                : downloadInfo.getFileName();
        final File file = new File(path);
        final String fileName = file.getName();

        if (!checkExternalStorageAndNotify(downloadPath(fileName))) {
            return;
        }
        String url = sanitizeDownloadUrl(downloadInfo);
        if (url == null) return;
        DownloadInfo newInfo = DownloadInfo.Builder.fromDownloadInfo(downloadInfo)
                .setUrl(url)
                .setMimeType(newMimeType).setDescription(url)
                .setFileName(fileName).setIsGETRequest(true).build();

        // TODO(acleung): This is a temp fix to disable auto downloading if flash files.
        // We want to avoid downloading flash files when it is linked as an iframe.
        // The proper fix would be to let chrome knows which frame originated the request.
        if ("application/x-shockwave-flash".equals(newInfo.getMimeType())) return;

        if (isDangerousFile(fileName, newMimeType)) {
            confirmDangerousDownload(newInfo);
        } else {
            // Not a dangerous file, proceed.
            enqueueDownloadManagerRequest(newInfo);
        }
    }

    /**
     * Sanitize the URL for the download item.
     *
     * @param downloadInfo Information about the download.
     * @param sanitized URL to be downloaded, or null if the url cannot be sanitized.
     */
    protected String sanitizeDownloadUrl(DownloadInfo downloadInfo) {
        return downloadInfo.getUrl();
    }

    /**
     * Request user confirmation on a dangerous download.
     *
     * @param downloadInfo Information about the download.
     */
    private void confirmDangerousDownload(DownloadInfo downloadInfo) {
        // A Dangerous file is already pending user confirmation, ignore the new download.
        if (mPendingRequest != null) return;

        mPendingRequest = downloadInfo;

        // TODO(dfalcantara): Ask ainslie@ for an icon to use for this InfoBar.
        int drawableId = 0;
        final String titleText = nativeGetDownloadWarningText(mPendingRequest.getFileName());
        final String okButtonText = mContext.getResources().getString(R.string.ok);
        final String cancelButtonText = mContext.getResources().getString(R.string.cancel);

        mTab.getInfoBarContainer().addInfoBar(new ConfirmInfoBar(
                this, drawableId, null, titleText, null, okButtonText, cancelButtonText));
    }

    /**
     * Called when a danagers download is about to start.
     *
     * @param filename File name of the download item.
     * @param downloadId ID of the download.
     */
    @Override
    public void onDangerousDownload(String filename, int downloadId) {
        DownloadInfo downloadInfo = new DownloadInfo.Builder()
                .setFileName(filename)
                .setDescription(filename)
                .setHasDownloadId(true)
                .setDownloadId(downloadId).build();
        confirmDangerousDownload(downloadInfo);
    }

    /**
     * Launch an info bar if the file name already exists for the download.
     * @param info The information of the file we are about to download.
     * @return Whether an info bar has been launched or not.
     */
    private boolean launchInfoBarIfFileExists(final DownloadInfo info) {
        // Checks if file exists.
        final String fileName = info.getFileName();
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!dir.mkdir() && !dir.isDirectory()) return false;
        String dirName = dir.getName();
        final File file = new File(dir, info.getFileName());
        String fullDirPath = file.getParent();
        if (!file.exists()) return false;
        if (TextUtils.isEmpty(fileName) || TextUtils.isEmpty(dirName)
                || TextUtils.isEmpty(fullDirPath)) {
            return false;
        }

        nativeLaunchDownloadOverwriteInfoBar(
                this, mTab, info, info.getFileName(), dirName, fullDirPath);
        return true;
    }

    /**
     * Sends the download request to Android download manager.
     *
     * @param info Download information about the download.
     */
    protected void enqueueDownloadManagerRequest(final DownloadInfo info) {
        if (!launchInfoBarIfFileExists(info)) {
            enqueueDownloadManagerRequestInternal(info);
        }
    }

    /**
     * Enqueue download manager request, only from native side.
     *
     * @param overwrite Whether or not we will overwrite the file.
     * @param downloadInfo The download info.
     */
    @CalledByNative
    private void enqueueDownloadManagerRequestFromNative(
            boolean overwrite, DownloadInfo downloadInfo) {
        // Android DownloadManager does not have an overwriting option.
        // We remove the file here instead.
        if (overwrite) deleteFileForOverwrite(downloadInfo);
        enqueueDownloadManagerRequestInternal(downloadInfo);
    }

    private void deleteFileForOverwrite(DownloadInfo info) {
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!dir.isDirectory()) return;
        final File file = new File(dir, info.getFileName());
        if (!file.delete()) {
            Log.e(LOGTAG, "Failed to delete a file." + info.getFileName());
        }
    }

    private void enqueueDownloadManagerRequestInternal(final DownloadInfo info) {
        DownloadManagerService.getDownloadManagerService(
                mContext.getApplicationContext()).enqueueDownloadManagerRequest(info, true);
        closeBlankTab();
    }

    /**
     * Check the external storage and notify user on error.
     *
     * @param fileName Name of the download file.
     */
    protected boolean checkExternalStorageAndNotify(String fileName) {
        if (fileName != null && fileName.startsWith("null")) {
            alertDownloadFailure(R.string.download_no_sdcard_dlg_title);
            return false;
        }

        // Check to see if we have an SDCard
        String status = Environment.getExternalStorageState();
        if (!status.equals(Environment.MEDIA_MOUNTED)) {
            int title;

            // Check to see if the SDCard is busy, same as the music app
            if (status.equals(Environment.MEDIA_SHARED)) {
                title = R.string.download_sdcard_busy_dlg_title;
            } else {
                title = R.string.download_no_sdcard_dlg_title;
            }

            alertDownloadFailure(title);
            return false;
        }

        return true;
    }

    /**
     * Alerts user of download failure.
     *
     * @param code Error resource ID.
     */
    private void alertDownloadFailure(int resId) {
        Toast.makeText(mContext, resId, Toast.LENGTH_SHORT).show();
    }

    /**
     * Called when download starts.
     *
     * @param filename Name of the file.
     * @param mimeType MIME type of the content.
     */
    @Override
    public void onDownloadStarted(String filename, String mimeType) {
        if (!isDangerousFile(filename, mimeType)) {
            showDownloadStartNotification();
            closeBlankTab();
        }
    }

    /**
     * Shows the download started notification.
     */
    private void showDownloadStartNotification() {
        Toast.makeText(mContext, R.string.download_pending, Toast.LENGTH_SHORT).show();
    }

    /**
     * If the given MIME type is null, or one of the "generic" types (text/plain
     * or application/octet-stream) map it to a type that Android can deal with.
     * If the given type is not generic, return it unchanged.
     *
     * We have to implement this ourselves as
     * MimeTypeMap.remapGenericMimeType() is not public.
     * See http://crbug.com/407829.
     *
     * @param mimeType MIME type provided by the server.
     * @param url URL of the data being loaded.
     * @param filename file name obtained from content disposition header
     * @return The MIME type that should be used for this data.
     */
    private static String remapGenericMimeType(String mimeType, String url, String filename) {
        // If we have one of "generic" MIME types, try to deduce
        // the right MIME type from the file extension (if any):
        if (mimeType == null || mimeType.isEmpty() || "text/plain".equals(mimeType)
                || "application/octet-stream".equals(mimeType)
                || "octet/stream".equals(mimeType)
                || "application/force-download".equals(mimeType)) {

            if (!TextUtils.isEmpty(filename)) {
                url = filename;
            }
            String extension = MimeTypeMap.getFileExtensionFromUrl(url);
            String newMimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            if (newMimeType != null) {
                mimeType = newMimeType;
            } else if (extension.equals("dm")) {
                mimeType = OMADownloadHandler.OMA_DRM_MESSAGE_MIME;
            } else if (extension.equals("dd")) {
                mimeType = OMADownloadHandler.OMA_DOWNLOAD_DESCRIPTOR_MIME;
            }
        }
        return mimeType;
    }

    /**
     * Check whether a file is dangerous.
     *
     * @param filename Name of the file.
     * @param mimeType MIME type of the content.
     * @return true if the file is dangerous, or false otherwise.
     */
    protected boolean isDangerousFile(String filename, String mimeType) {
        return nativeIsDownloadDangerous(filename) || isDangerousExtension(
                MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType));
    }

    /**
     * Check whether a file extension is dangerous.
     *
     * @param ext Extension of the file.
     * @return true if the file is dangerous, or false otherwise.
     */
    private static boolean isDangerousExtension(String ext) {
        return "apk".equals(ext);
    }

    /**
     * Discards a downloaded file.
     *
     * @param filepath File to be discarded.
     */
    private static void discardFile(final String filepath) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            public Void doInBackground(Void... params) {
                Log.d(LOGTAG, "Discarding download:" + filepath);
                File file = new File(filepath);
                if (file.exists() && !file.delete()) {
                    Log.e(LOGTAG, "Error discarding file: " + filepath);
                }
                return null;
            }
        }.execute();
    }

    /**
     * Close a blank tab just opened for the download purpose.
     */
    private void closeBlankTab() {
        WebContents contents = mTab.getWebContents();
        boolean isInitialNavigation = contents == null
                || contents.getNavigationController().isInitialNavigation();
        if (isInitialNavigation) {
            // Tab is created just for download, close it.
            mTabModelSelector.closeTab(mTab);
        }
    }

    /**
     * For certain download types(OMA for example), android DownloadManager should
     * handle them. Call this function to intercept those downloads.
     *
     * @param url URL to be downloaded.
     * @return whether the DownloadManager should intercept the download.
     */
    public boolean shouldInterceptContextMenuDownload(String url) {
        Uri uri = Uri.parse(url);
        String scheme = uri.normalizeScheme().getScheme();
        if (!"http".equals(scheme) && !"https".equals(scheme)) return false;
        String path = uri.getPath();
        // OMA downloads have extension "dm" or "dd". For the latter, it
        // can be handled when native download completes.
        if (path != null && (path.endsWith(".dm"))) {
            final DownloadInfo downloadInfo = new DownloadInfo.Builder().setUrl(url).build();
            if (mTab == null) return true;
            WindowAndroid window = mTab.getWindowAndroid();
            if (window.hasPermission(permission.WRITE_EXTERNAL_STORAGE)) {
                onDownloadStartNoStream(downloadInfo);
            } else if (window.canRequestPermission(permission.WRITE_EXTERNAL_STORAGE)) {
                PermissionCallback permissionCallback = new PermissionCallback() {
                    @Override
                    public void onRequestPermissionsResult(
                            String[] permissions, int[] grantResults) {
                        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                            onDownloadStartNoStream(downloadInfo);
                        }
                    }
                };
                window.requestPermissions(
                        new String[] {permission.WRITE_EXTERNAL_STORAGE}, permissionCallback);
            }
            return true;
        }
        return false;
    }

    protected Context getContext() {
        return mContext;
    }

    private static native String nativeGetDownloadWarningText(String filename);
    private static native boolean nativeIsDownloadDangerous(String filename);
    private static native void nativeDangerousDownloadValidated(
            Object tab, int downloadId, boolean accept);
    private static native void nativeLaunchDownloadOverwriteInfoBar(ChromeDownloadDelegate delegate,
            Tab tab, DownloadInfo downloadInfo, String fileName, String dirName,
            String dirFullPath);
}

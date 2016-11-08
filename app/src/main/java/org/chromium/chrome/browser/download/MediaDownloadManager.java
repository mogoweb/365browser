/*
 * Copyright (c) 2016, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package org.chromium.chrome.browser.download;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;

import org.chromium.base.ApplicationStatus;
import org.chromium.chrome.R;
import org.chromium.base.CommandLine;
import org.codeaurora.swe.SWEBrowserSwitches;
import org.chromium.chrome.browser.download.DownloadInfo;
import org.codeaurora.swe.ReflectHelper;

public class MediaDownloadManager {
    private Context mContext;
    private ChromeDownloadDelegate mDelegate;

    public MediaDownloadManager(ChromeDownloadDelegate delegate) {
        // mContext = ctx;
        mDelegate = delegate;
    }

    public boolean shouldInterceptDownload(final DownloadInfo downloadInfo) {
        if (!CommandLine.getInstance().hasSwitch(SWEBrowserSwitches.OVERRIDE_MEDIA_DOWNLOAD))
            return false;
        mContext = ApplicationStatus.getLastTrackedFocusedActivity();
        Uri uri = Uri.parse(downloadInfo.getUrl());
        String scheme = uri.getScheme();
        String mimeType = downloadInfo.getMimeType();
        int fileType = getFileTypeForMimeType(mimeType);
        int app_res = mContext.getApplicationInfo().labelRes;
        if ("http".equalsIgnoreCase(scheme) &&
                (mimeType.startsWith("audio/") || mimeType.startsWith("video/") ||
                        isAudioFileType(fileType) ||
                        isVideoFileType(fileType))) {
            Resources res = mContext.getResources();


            new AlertDialog.Builder(mContext)
                    .setTitle(mContext.getString(app_res))
                    .setIcon(android.R.drawable.ic_media_play)
                    .setMessage(R.string.swe_media_msg)
                    .setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            mDelegate.onDownloadStartNoStream(downloadInfo);
                        }
                    })
                    .setNegativeButton(R.string.accessibility_play,
                            new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            // Query the package manager to see if there's a registered
                            // handler that matches.
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            intent.setDataAndType(Uri.parse(downloadInfo.getUrl()),
                                    downloadInfo.getMimeType());
                            // If the intent is resolved to ourselves, we don't want to attempt
                            // to load the url only to try and download it again.
                            if (DownloadManagerService.openIntent(mContext, intent, false)) {
                                return;
                            }
                        }
                    }).show();
            return true;
        }
        return false;
    }

    private static boolean isAudioFileType(int fileType){
        Object[] params  = {Integer.valueOf(fileType)};
        Class[] type = new Class[] {int.class};
        Boolean result = (Boolean) ReflectHelper.invokeMethod("android.media.MediaFile",
                "isAudioFileType", type, params);
        return result;
    }

    private static boolean isVideoFileType(int fileType){
        Object[] params  = {Integer.valueOf(fileType)};
        Class[] type = new Class[] {int.class};
        Boolean result = (Boolean) ReflectHelper.invokeMethod("android.media.MediaFile",
                "isVideoFileType", type, params);
        return result;
    }

    private static int getFileTypeForMimeType(String mimetype) {
        Object[] params = {mimetype};
        Class[] type = new Class[] {String.class};
        Integer result = (Integer) ReflectHelper.invokeMethod("android.media.MediaFile",
                "getFileTypeForMimeType", type, params);
        return result.intValue();
    }

}

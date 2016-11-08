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

package org.chromium.chrome.browser.infobar;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.StatFs;
import android.text.Spannable;
import android.os.Environment;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.support.v4.content.LocalBroadcastManager;

import org.chromium.base.ApplicationStatus;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.CommandLine;
import org.chromium.chrome.browser.download.DownloadSnackbarController;
import org.chromium.chrome.browser.infobar.DownloadOverwriteInfoBar;
import org.chromium.chrome.R;

import org.codeaurora.swe.SWECommandLine;
import org.codeaurora.swe.SWEBrowserSwitches;
import org.codeaurora.swe.DownloadInfoBarContainer;

import java.io.File;
import java.util.List;

public class DownloadInfoBar extends DownloadOverwriteInfoBar {
    private static final long RESERVED_BYTES = 32 * 1024 * 1024;
    private static final String TAG = "DownloadInfoBar";

    private final long mTotalBytes;
    private Uri mUri;
    private InfoBarLayout mInfoBarLayout;

    /**
     * Constructs DownloadInfoBar.
     * @param fileName The file name. ex) example.jpg
     * @param totalBytes The total bytes reported by Content-Length
     * @param mimeType Mime Type of the download file
     * @param dirName The dir name. ex) Downloads
     * @param dirFullPath The full dir path. ex) sdcards/Downloads
     */
    public DownloadInfoBar(String fileName, long totalBytes,
            String mimeType, String dirName, String dirFullPath) {
        super(fileName, dirName, dirFullPath);

        mTotalBytes = totalBytes;

        DownloadInfoBarContainer.createInstance();
        mUri = DownloadInfoBarContainer.getInstance().register(this);
        setDirFullPath(getPreferredFolderForMime(mimeType), false);
    }

    @Override
    public void onButtonClicked(boolean isPrimaryButton) {
        DownloadInfoBarContainer.getInstance().unregister(mUri);
        mUri = null;

        int action = isPrimaryButton ? ActionType.CREATE_NEW_FILE
                                     : ActionType.CANCEL;

        Context ctx = mInfoBarLayout.getContext();
        if (ActionType.CREATE_NEW_FILE == action &&
            MemoryCheckStatus.INSUFFICIENT_SPACE ==
                isExternalMemoryEnough(mTotalBytes, mDirFullPath)) {
            String reasonString = ctx.getString(
                R.string.swe_downloadpath_not_enough_space, mFileName);
            new DownloadSnackbarController(ctx).onDownloadFailed(
                reasonString, false);
            action = ActionType.CANCEL;
        }

        if (ActionType.CANCEL != action) {
            nativeSetDirFullPath(GetNativeInfoBarPtr(), mDirFullPath);
        }

        onButtonClicked(action);
    }

    @Override
    public void onCloseButtonClicked() {
        DownloadInfoBarContainer.getInstance().unregister(mUri);
        mUri = null;
    }

    private CharSequence getMessageText(Context context) {
        String template = context.getString(R.string.swe_download_infobar_text);
        Intent intent = getIntentForDirectoryLaunch(context, mDirFullPath);
        return formatInfoBarMessage(context, template, mFileName, mDirName, intent, mUri);
    }

    /**
     * @param dirFullPath The full path of the directory to be launched.
     * @return An Android intent that can launch the directory.
     */
    private static Intent getIntentForDirectoryLaunch(Context ctx,
            String dirFullPath) {
        String selectDirIntent = SWECommandLine.getResourceString(ctx,
                SWECommandLine.kSWEDownloadPathActivityIntent);
        if (TextUtils.isEmpty(selectDirIntent))
            return null;

        Intent intent = new Intent(selectDirIntent);
        if (null == intent)
            return null;

        return intent;
    }

    @Override
    public void createContent(InfoBarLayout layout) {
        Context context = layout.getContext();
        layout.setMessage(getMessageText(context));
        layout.setButtons(
                context.getString(R.string.ok),
                context.getString(R.string.cancel));

        mInfoBarLayout = layout;
    }

    /**
     * Create infobar message in the form of CharSequence.
     *
     * @param context The context.
     * @param template The template CharSequence.
     * @param fileName The file name.
     * @param dirName The directory name.
     * @param dirNameIntent The intent to be launched when user touches the directory name link.
     * @return CharSequence formatted message for InfoBar.
     */
    private static CharSequence formatInfoBarMessage(final Context context, String template,
            String fileName, String dirName, final Intent dirNameIntent, final Uri id) {
        SpannableString formattedFileName = new SpannableString(fileName);
        formattedFileName.setSpan(new StyleSpan(Typeface.BOLD), 0, fileName.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        SpannableString formattedDirName = new SpannableString(dirName);
        if (canResolveIntent(context, dirNameIntent)) {
            formattedDirName.setSpan(new ClickableSpan() {
                @Override
                public void onClick(View view) {
                    startDirSelectActivity(context, dirNameIntent, id);
                }
            }, 0, dirName.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        return TextUtils.expandTemplate(template, formattedFileName, formattedDirName);
    }

    private static boolean canResolveIntent(Context context, Intent intent) {
        if (context == null || intent == null) {
            return false;
        }
        final PackageManager packageManager = context.getPackageManager();
        List<ResolveInfo> resolveInfoList =
                packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        return resolveInfoList.size() > 0;
    }

    public boolean setDirFullPath(String dirFullPath, boolean updateUI) {
        File dirFullPathFile = new File(dirFullPath);
        if (!dirFullPathFile.isDirectory())
            return false;

        mDirFullPath = dirFullPathFile.toString();
        mDirName = new File(mDirFullPath).getName();

        if (updateUI)
            mInfoBarLayout.setMessage(getMessageText(mInfoBarLayout.getContext()));

        return true;
    }

    public static boolean areSWEDownloadEnhancementsEnabled() {
        return CommandLine.getInstance().hasSwitch(
                SWEBrowserSwitches.DOWNLOAD_PATH_SELECTION);
    }

    public static boolean canSetDirOnMime() {
        return CommandLine.getInstance().hasSwitch(
                SWEBrowserSwitches.DOWNLOAD_PATH_DIR_ON_MIME);
    }

    private static String getPreferredFolderForMime(String mimeType) {
        String dirType = Environment.DIRECTORY_DOWNLOADS;

        if (canSetDirOnMime() && !TextUtils.isEmpty(mimeType)) {
            if (mimeType.startsWith("audio")) {
                dirType = Environment.DIRECTORY_MUSIC;
            } else if (mimeType.startsWith("video")) {
                dirType = Environment.DIRECTORY_MOVIES;
            } else if (mimeType.startsWith("image")) {
                dirType = Environment.DIRECTORY_PICTURES;
            }
        }

        return Environment.getExternalStoragePublicDirectory(
                dirType).toString();
    }

    private enum MemoryCheckStatus {
        ERROR,
        CANNOT_DETERMINE,
        SDK_VERSION,
        SUFFICIENT_SPACE,
        INSUFFICIENT_SPACE
    }

    public static MemoryCheckStatus isExternalMemoryEnough(
            long totalBytes, String dirFullPath) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2)
            return MemoryCheckStatus.SDK_VERSION;

        if (totalBytes < 0)
            return MemoryCheckStatus.CANNOT_DETERMINE;

        try {
            String downloadFileDir = new File(dirFullPath).getParent();

            StatFs statFs = new StatFs(downloadFileDir);
            long blockSize = statFs.getBlockSizeLong();
            long availableSize = (statFs.getAvailableBlocksLong() * blockSize) - RESERVED_BYTES;

            return (totalBytes <= availableSize ?
                   MemoryCheckStatus.SUFFICIENT_SPACE :
                   MemoryCheckStatus.INSUFFICIENT_SPACE);
        }
        catch (Exception e) {
            return MemoryCheckStatus.ERROR;
        }
    }

    private static void startDirSelectActivity(Context ctx,
            Intent dirSelectIntent, Uri id) {
        DownloadInfoBarContainer.getInstance().setSelectDirIntent(
                id, dirSelectIntent);

        Intent sweIntent = new Intent(
                DownloadInfoBarContainer.RECEIVER_NAME);
        sweIntent.setDataAndType(id, "*/*");

        LocalBroadcastManager.getInstance(ctx).sendBroadcast(sweIntent);
    }
}

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

package org.codeaurora.swe;

import org.chromium.chrome.browser.infobar.DownloadInfoBar;

import android.content.Intent;
import android.net.Uri;
import android.net.Uri.Builder;

import java.util.Map;
import java.util.HashMap;

public class DownloadInfoBarContainer {
    public static final String RECEIVER_NAME =
            "start-dir-select-activity-receiver";
    private static DownloadInfoBarContainer sThis;

    private Map<Uri, DownloadInfoBarData> mDataMap;

    public class DownloadInfoBarData {
        public DownloadInfoBar downloadInfoBar;
        public Intent selectDirIntent;
    }

    public static void createInstance() {
        if (null == sThis)
            sThis = new DownloadInfoBarContainer();
    }

    public static DownloadInfoBarContainer getInstance() {
        return sThis;
    }

    public static boolean isInitialized() {
        return !(null == sThis);
    }

    private DownloadInfoBarContainer() {
        mDataMap = new HashMap<Uri, DownloadInfoBarData>();
    }

    public Uri register(DownloadInfoBar infoBar) {
        DownloadInfoBarData data = new DownloadInfoBarData();
        data.downloadInfoBar = infoBar;
        data.selectDirIntent = null;

        Uri id = (new Uri.Builder().path(
                    Integer.toString(infoBar.hashCode()))).build();
        mDataMap.put(id, data);

        return id;
    }

    public void unregister(Uri id) {
        mDataMap.remove(id);
    }

    public DownloadInfoBar getDownloadInfoBar(Uri id) {
        DownloadInfoBarData data = mDataMap.get(id);
        if (null == data)
            return null;

        return data.downloadInfoBar;
    }

    public Intent getSelectDirIntent(Uri id) {
        DownloadInfoBarData data = mDataMap.get(id);
        if (null == data)
            return null;

        return data.selectDirIntent;
    }

    public void setSelectDirIntent(Uri id, Intent selectDirIntent) {
        DownloadInfoBarData data = mDataMap.get(id);
        if (null != data)
            data.selectDirIntent = selectDirIntent;
    }
}


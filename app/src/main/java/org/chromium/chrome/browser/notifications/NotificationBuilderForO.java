// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.notifications;

import android.annotation.TargetApi;
import android.app.Notification;
import android.content.Context;

import org.chromium.base.BuildInfo;
import org.chromium.base.Log;
import org.chromium.chrome.browser.notifications.channels.ChannelDefinitions;
import org.chromium.chrome.browser.notifications.channels.ChannelsInitializer;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Builder to be used on Android O until we target O and these APIs are in the support library.
 */
@TargetApi(26 /* Build.VERSION_CODES.O */)
public class NotificationBuilderForO extends NotificationBuilder {
    private static final String TAG = "NotifBuilderForO";

    public NotificationBuilderForO(Context context, @ChannelDefinitions.ChannelId String channelId,
            ChannelsInitializer channelsInitializer) {
        super(context);
        assert BuildInfo.isAtLeastO();
        if (channelId == null) {
            // The channelId may be null if the notification will be posted by another app that
            // does not target O or sets its own channels. E.g. Web apk notifications.
            return;
        }
        channelsInitializer.ensureInitialized(channelId);
        // TODO(crbug.com/707804) Stop using reflection once compileSdkVersion is high enough.
        try {
            Method setChannel = Notification.Builder.class.getMethod("setChannel", String.class);
            setChannel.invoke(mBuilder, channelId);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            Log.e(TAG, "Error setting channel on notification builder:", e);
        }
    }
}

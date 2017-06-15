// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.notifications;

import android.app.Notification;

import org.chromium.chrome.browser.notifications.channels.Channel;
import org.chromium.chrome.browser.notifications.channels.ChannelDefinitions;

import java.util.List;

/**
 * A proxy for the Android Notification Manager. This allows tests to be written without having to
 * use the real Notification Manager.
 *
 * @see <a href="https://developer.android.com/reference/android/app/NotificationManager.html">
 *     https://developer.android.com/reference/android/app/NotificationManager.html</a>
 */
public interface NotificationManagerProxy {
    void cancel(int id);
    void cancel(String tag, int id);
    void cancelAll();
    void createNotificationChannel(Channel channel);
    void createNotificationChannelGroup(ChannelDefinitions.ChannelGroup channelGroup);
    List<Channel> getNotificationChannels();
    void deleteNotificationChannel(String id);

    void notify(int id, Notification notification);
    void notify(String tag, int id, Notification notification);
}

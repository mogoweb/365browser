// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.notifications.channels;

import android.content.res.Resources;

import org.chromium.chrome.browser.notifications.NotificationManagerProxy;

/**
 * Initializes our notification channels.
 */
public class ChannelsInitializer {
    private final NotificationManagerProxy mNotificationManager;
    private final Resources mResources;

    public ChannelsInitializer(
            NotificationManagerProxy notificationManagerProxy, Resources resources) {
        mNotificationManager = notificationManagerProxy;
        mResources = resources;
    }

    /**
     * Creates all the channels on the notification manager that we want to appear in our
     * channel settings from first launch onwards.
     */
    void initializeStartupChannels() {
        for (String channelId : ChannelDefinitions.getStartupChannelIds()) {
            ensureInitialized(channelId);
        }
    }

    /**
     * Cleans up any old channels that are no longer required from previous versions of the app.
     * It's safe to call this multiple times since deleting an already-deleted channel is a no-op.
     */
    void deleteLegacyChannels() {
        for (String channelId : ChannelDefinitions.getLegacyChannelIds()) {
            mNotificationManager.deleteNotificationChannel(channelId);
        }
    }

    /**
     * Ensures the given channel has been created on the notification manager so a notification
     * can be safely posted to it. This should only be used for channels that are predefined in
     * {@link ChannelDefinitions.PredefinedChannels}.
     *
     * Calling this is a (potentially lengthy) no-op if the channel has already been created.
     *
     * @param channelId The ID of the channel to be initialized.
     */
    public void ensureInitialized(@ChannelDefinitions.ChannelId String channelId) {
        ChannelDefinitions.PredefinedChannel predefinedChannel =
                ChannelDefinitions.getChannelFromId(channelId);
        if (predefinedChannel == null) {
            throw new IllegalStateException("Could not initialize channel: " + channelId);
        }
        // Channel group must be created before the channel.
        mNotificationManager.createNotificationChannelGroup(
                ChannelDefinitions.getChannelGroupFromId(predefinedChannel));
        mNotificationManager.createNotificationChannel(predefinedChannel.toChannel(mResources));
    }
}

// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.notifications.channels;

import android.annotation.TargetApi;
import android.app.NotificationManager;
import android.content.res.Resources;
import android.os.Build;
import android.support.annotation.StringDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Defines the properties of all notification channels we post notifications to in Android O+.
 *
 * PLEASE NOTE, notification channels appear in system UI and are persisted forever by Android,
 * so should not be added or removed lightly, and the proper deprecation and versioning steps must
 * be taken when doing so. Please read the comments and speak to one of this file's OWNERs when
 * adding/removing a channel.
 */
public class ChannelDefinitions {
    public static final String CHANNEL_ID_BROWSER = "browser";
    public static final String CHANNEL_ID_DOWNLOADS = "downloads";
    public static final String CHANNEL_ID_INCOGNITO = "incognito";
    public static final String CHANNEL_ID_MEDIA = "media";
    public static final String CHANNEL_ID_SITES = "sites";
    static final String CHANNEL_GROUP_ID_GENERAL = "general";
    /**
     * Version number identifying the current set of channels. This must be incremented whenever
     * the set of channels returned by {@link #getStartupChannelIds()} or
     * {@link #getLegacyChannelIds()} changes.
     */
    static final int CHANNELS_VERSION = 0;

    /**
     * To define a new channel, add the channel ID to this StringDef and add a new entry to
     * PredefinedChannels.MAP below with the appropriate channel parameters.
     * To remove an existing channel, remove the ID from this StringDef, remove its entry from
     * Predefined Channels.MAP, and add the ID to the LEGACY_CHANNELS_ID array below.
     */
    @StringDef({CHANNEL_ID_BROWSER, CHANNEL_ID_DOWNLOADS, CHANNEL_ID_INCOGNITO, CHANNEL_ID_MEDIA,
            CHANNEL_ID_SITES})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ChannelId {}

    @StringDef({CHANNEL_GROUP_ID_GENERAL})
    @Retention(RetentionPolicy.SOURCE)
    @interface ChannelGroupId {}

    // Map defined in static inner class so it's only initialized lazily.
    @TargetApi(Build.VERSION_CODES.N) // for NotificationManager.IMPORTANCE_* constants
    private static class PredefinedChannels {
        /**
         * The set of predefined channels to be initialized on startup. CHANNELS_VERSION must be
         * incremented every time an entry is modified, removed or added to this map.
         * If an entry is removed from here then it must be added to the LEGACY_CHANNEL_IDs array.
         */
        static final Map<String, PredefinedChannel> MAP;
        static {
            Map<String, PredefinedChannel> map = new HashMap<>();
            map.put(CHANNEL_ID_BROWSER,
                    new PredefinedChannel(CHANNEL_ID_BROWSER,
                            org.chromium.chrome.R.string.notification_category_browser,
                            NotificationManager.IMPORTANCE_LOW, CHANNEL_GROUP_ID_GENERAL));
            map.put(CHANNEL_ID_DOWNLOADS,
                    new PredefinedChannel(CHANNEL_ID_DOWNLOADS,
                            org.chromium.chrome.R.string.notification_category_downloads,
                            NotificationManager.IMPORTANCE_LOW, CHANNEL_GROUP_ID_GENERAL));
            map.put(CHANNEL_ID_INCOGNITO,
                    new PredefinedChannel(CHANNEL_ID_INCOGNITO,
                            org.chromium.chrome.R.string.notification_category_incognito,
                            NotificationManager.IMPORTANCE_LOW, CHANNEL_GROUP_ID_GENERAL));
            map.put(CHANNEL_ID_MEDIA,
                    new PredefinedChannel(CHANNEL_ID_MEDIA,
                            org.chromium.chrome.R.string.notification_category_media,
                            NotificationManager.IMPORTANCE_LOW, CHANNEL_GROUP_ID_GENERAL));
            map.put(CHANNEL_ID_SITES,
                    new PredefinedChannel(CHANNEL_ID_SITES,
                            org.chromium.chrome.R.string.notification_category_sites,
                            NotificationManager.IMPORTANCE_DEFAULT, CHANNEL_GROUP_ID_GENERAL));
            MAP = Collections.unmodifiableMap(map);
        }
    }

    /**
     * When channels become deprecated they should be removed from PredefinedChannels and their ids
     * added to this array so they can be deleted on upgrade.
     * We also want to keep track of old channel ids so they aren't accidentally reused.
     */
    private static final String[] LEGACY_CHANNEL_IDS = {};

    // Map defined in static inner class so it's only initialized lazily.
    private static class PredefinedChannelGroups {
        static final Map<String, ChannelGroup> MAP;
        static {
            Map<String, ChannelGroup> map = new HashMap<>();
            map.put(CHANNEL_GROUP_ID_GENERAL,
                    new ChannelGroup(CHANNEL_GROUP_ID_GENERAL,
                            org.chromium.chrome.R.string.notification_category_group_general));
            MAP = Collections.unmodifiableMap(map);
        }
    }

    /**
     * @return A set of channel ids of channels that should be initialized on startup.
     */
    static Set<String> getStartupChannelIds() {
        // CHANNELS_VERSION must be incremented if the set of channels returned here changes.
        return PredefinedChannels.MAP.keySet();
    }

    /**
     * @return An array of old ChannelIds that may have been returned by
     * {@link #getStartupChannelIds} in the past, but are no longer in use.
     */
    static String[] getLegacyChannelIds() {
        return LEGACY_CHANNEL_IDS;
    }

    static ChannelGroup getChannelGroupFromId(PredefinedChannel channel) {
        return PredefinedChannelGroups.MAP.get(channel.mGroupId);
    }

    static PredefinedChannel getChannelFromId(@ChannelId String channelId) {
        return PredefinedChannels.MAP.get(channelId);
    }

    /**
     * Helper class for storing predefined channel properties while allowing the channel name to be
     * lazily evaluated only when it is converted to an actual (Notification)Channel.
     */
    static class PredefinedChannel {
        @ChannelId
        private final String mId;
        private final int mNameResId;
        private final int mImportance;
        @ChannelGroupId
        private final String mGroupId;

        PredefinedChannel(@ChannelId String id, int nameResId, int importance,
                @ChannelGroupId String groupId) {
            this.mId = id;
            this.mNameResId = nameResId;
            this.mImportance = importance;
            this.mGroupId = groupId;
        }

        Channel toChannel(Resources resources) {
            String name = resources.getString(mNameResId);
            return new Channel(mId, name, mImportance, mGroupId);
        }
    }

    /**
     * Helper class containing notification channel group properties.
     */
    public static class ChannelGroup {
        @ChannelGroupId
        public final String mId;
        public final int mNameResId;

        ChannelGroup(@ChannelGroupId String id, int nameResId) {
            this.mId = id;
            this.mNameResId = nameResId;
        }
    }
}

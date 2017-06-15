// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.notifications;

import android.content.Intent;

/**
 * Constants used in more than a single Notification class, e.g. intents and extra names.
 */
public class NotificationConstants {
    // These actions have to be synchronized with the receiver defined in AndroidManifest.xml.
    static final String ACTION_CLICK_NOTIFICATION =
            "org.chromium.chrome.browser.notifications.CLICK_NOTIFICATION";
    static final String ACTION_CLOSE_NOTIFICATION =
            "org.chromium.chrome.browser.notifications.CLOSE_NOTIFICATION";

    /**
     * Name of the Intent extra set by the framework when a notification preferences intent has
     * been triggered from there, which could be one of the setting gears in system UI.
     */
    static final String EXTRA_NOTIFICATION_TAG = "notification_tag";

    /**
     * Names of the Intent extras used for Intents related to notifications. These intents are set
     * and owned by Chromium.
     *
     * When adding a new extra, as well as setting it on the intent in NotificationPlatformBridge,
     * it *must* also be set in {@link NotificationJobService#getJobExtrasFromIntent(Intent)}
     */
    public static final String EXTRA_NOTIFICATION_ID = "notification_id";
    static final String EXTRA_NOTIFICATION_INFO_ORIGIN = "notification_info_origin";
    static final String EXTRA_NOTIFICATION_INFO_PROFILE_ID = "notification_info_profile_id";
    static final String EXTRA_NOTIFICATION_INFO_PROFILE_INCOGNITO =
            "notification_info_profile_incognito";
    static final String EXTRA_NOTIFICATION_INFO_TAG = "notification_info_tag";
    static final String EXTRA_NOTIFICATION_INFO_ACTION_INDEX = "notification_info_action_index";
    static final String EXTRA_NOTIFICATION_INFO_WEBAPK_PACKAGE = "notification_info_webapk_package";
    static final String EXTRA_NOTIFICATION_REPLY = "notification_reply";
    static final String EXTRA_NOTIFICATION_ACTION = "notification_action";

    /**
     * Unique identifier for a single sync notification. Since the notification ID is reused,
     * old notifications will be overwritten.
     */
    public static final int NOTIFICATION_ID_SYNC = 1;
    /**
     * Unique identifier for the "Signed in to Chrome" notification.
     */
    @SuppressWarnings("unused")
    public static final int NOTIFICATION_ID_SIGNED_IN = 2;
    /**
     * Unique identifier for the Physical Web notification.
     */
    public static final int NOTIFICATION_ID_PHYSICAL_WEB = 3;

    /**
     * Unique identifier for the summary notification for downloads.  Using the ID this summary was
     * going to have before it was migrated here.
     * TODO(dtrainor): Clean up this ID and make sure it's in line with existing id counters without
     * tags.
     */
    public static final int NOTIFICATION_ID_DOWNLOAD_SUMMARY = 999999;

    /**
     * Separator used to separate the notification origin from additional data such as the
     * developer specified tag.
     */
    static final String NOTIFICATION_TAG_SEPARATOR = ";";

    /**
     * Key for retrieving the results of user input from notification text action intents.
     */
    static final String KEY_TEXT_REPLY = "key_text_reply";

    // Notification groups for features that show notifications to the user.
    public static final String GROUP_DOWNLOADS = "Downloads";
    public static final String GROUP_INCOGNITO = "Incognito";
    public static final String GROUP_MEDIA_PLAYBACK = "MediaPlayback";
    public static final String GROUP_MEDIA_PRESENTATION = "MediaPresentation";
    public static final String GROUP_MEDIA_REMOTE = "MediaRemote";
    public static final String GROUP_SYNC = "Sync";

    // Web notification group names are set dynamically as this prefix + notification origin.
    // For example, 'Web:chromium.org' for a notification from chromium.org.
    static final String GROUP_WEB_PREFIX = "Web:";

}

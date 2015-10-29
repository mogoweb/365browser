// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.util.SparseIntArray;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.tab.Tab;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Service that creates/destroys media related notifications.
 * There are two kinds of notifications:
 * 1. The WebRTC notification when media capture starts/stops.
 * 2. The audio playback notification when a tab is playing audio.
 * These notifications are made mutually exclusive: there can be
 * only one media notification for a tab.
 */
public class MediaNotificationService extends Service {

    private static final String NOTIFICATION_NAMESPACE = "MediaNotificationService";

    private static final String NOTIFICATION_ID_EXTRA = "NotificationId";
    private static final String NOTIFICATION_MEDIA_TYPE_EXTRA = "NotificationMediaType";
    private static final String NOTIFICATION_MEDIA_URL_EXTRA = "NotificationMediaUrl";

    private static final String MEDIA_NOTIFICATION_IDS = "WebRTCNotificationIds";
    private static final String LOG_TAG = "MediaNotificationService";

    private static final int MEDIATYPE_NO_MEDIA = 0;
    private static final int MEDIATYPE_AUDIO_AND_VIDEO_CAPTURE = 1;
    private static final int MEDIATYPE_VIDEO_CAPTURE_ONLY = 2;
    private static final int MEDIATYPE_AUDIO_CAPTURE_ONLY = 3;
    private static final int MEDIATYPE_AUDIO_PLAYBACK = 4;

    private NotificationManager mNotificationManager;
    private Context mContext;
    private SharedPreferences mSharedPreferences;
    private final SparseIntArray mNotifications = new SparseIntArray();

    @Override
    public void onCreate() {
        mContext = getApplicationContext();
        mNotificationManager = (NotificationManager) mContext.getSystemService(
                Context.NOTIFICATION_SERVICE);
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        super.onCreate();
    }

    /**
     * @param notificationId Unique id of the notification.
     * @param mediaType Media type of the notification.
     * @return Whether the notification has already been created for provided notification id and
     *         mediaType.
     */
    private boolean doesNotificationNeedUpdate(int notificationId, int mediaType) {
        return mNotifications.get(notificationId) != mediaType;
    }

    /**
     * @param notificationId Unique id of the notification.
     * @return Whether the notification has already been created for the provided notification id.
     */
    private boolean doesNotificationExist(int notificationId) {
        return mNotifications.indexOfKey(notificationId) >= 0;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getExtras() == null) {
            cancelPreviousWebRtcNotifications();
            stopSelf();
        } else {
            updateNotification(
                    intent.getIntExtra(NOTIFICATION_ID_EXTRA, Tab.INVALID_TAB_ID),
                    intent.getIntExtra(NOTIFICATION_MEDIA_TYPE_EXTRA, MEDIATYPE_NO_MEDIA),
                    intent.getStringExtra(NOTIFICATION_MEDIA_URL_EXTRA));
        }
        return super.onStartCommand(intent, flags, startId);
    }

    /**
     * Cancel all previously existing notifications. Essential while doing a clean start (may be
     * after a browser crash which caused old notifications to exist).
     */
    private void cancelPreviousWebRtcNotifications() {
        Set<String> notificationIds =
                mSharedPreferences.getStringSet(MEDIA_NOTIFICATION_IDS, null);
        if (notificationIds == null) return;
        Iterator<String> iterator = notificationIds.iterator();
        while (iterator.hasNext()) {
            mNotificationManager.cancel(NOTIFICATION_NAMESPACE, Integer.parseInt(iterator.next()));
        }
        SharedPreferences.Editor sharedPreferenceEditor = mSharedPreferences.edit();
        sharedPreferenceEditor.remove(MediaNotificationService.MEDIA_NOTIFICATION_IDS);
        sharedPreferenceEditor.apply();
    }

    /**
     * Updates the extisting notification or creates one if none exist for the provided
     * notificationId and mediaType.
     * @param notificationId Unique id of the notification.
     * @param mediaType Media type of the notification.
     * @param url Url of the current webrtc call.
     */
    private void updateNotification(int notificationId, int mediaType, String url) {
        if (doesNotificationExist(notificationId)
                && !doesNotificationNeedUpdate(notificationId, mediaType))  {
            return;
        }
        destroyNotification(notificationId);
        if (mediaType != MEDIATYPE_NO_MEDIA) {
            createNotification(notificationId, mediaType, url);
        }
        if (mNotifications.size() == 0) stopSelf();
    }

    /**
     * Destroys the notification for the id notificationId.
     * @param notificationId Unique id of the notification.
     */
    private void destroyNotification(int notificationId) {
        if (doesNotificationExist(notificationId)) {
            mNotificationManager.cancel(NOTIFICATION_NAMESPACE, notificationId);
            mNotifications.delete(notificationId);
            updateSharedPreferencesEntry(notificationId, true);
        }
    }

    /**
     * Creates a notification for the provided notificationId and mediaType.
     * @param notificationId Unique id of the notification.
     * @param mediaType Media type of the notification.
     * @param url Url of the current webrtc call.
     */
    private void createNotification(int notificationId, int mediaType, String url) {
        int notificationContentTextId = 0;
        int notificationIconId = 0;
        if (mediaType == MEDIATYPE_AUDIO_AND_VIDEO_CAPTURE) {
            notificationContentTextId = R.string.video_audio_call_notification_text_2;
            notificationIconId = R.drawable.webrtc_video;
        } else if (mediaType == MEDIATYPE_VIDEO_CAPTURE_ONLY) {
            notificationContentTextId = R.string.video_call_notification_text_2;
            notificationIconId = R.drawable.webrtc_video;
        } else if (mediaType == MEDIATYPE_AUDIO_CAPTURE_ONLY) {
            notificationContentTextId = R.string.audio_call_notification_text_2;
            notificationIconId = R.drawable.webrtc_audio;
        } else if (mediaType == MEDIATYPE_AUDIO_PLAYBACK) {
            notificationContentTextId = R.string.audio_playback_notification_text;
            notificationIconId = R.drawable.audio_playing;
        }

        Intent tabIntent = Tab.createBringTabToFrontIntent(notificationId);
        PendingIntent contentIntent = PendingIntent.getActivity(
                mContext, notificationId, tabIntent, 0);
        String contentText = mContext.getResources().getString(notificationContentTextId) + ". "
                + mContext.getResources().getString(
                        R.string.media_notification_link_text, url);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(mContext)
                .setAutoCancel(false)
                .setOngoing(true)
                .setContentIntent(contentIntent)
                .setContentTitle(mContext.getString(R.string.app_name))
                .setContentText(contentText)
                .setSmallIcon(notificationIconId)
                .setLocalOnly(true);

        Notification notification = new NotificationCompat.BigTextStyle(builder)
                .bigText(contentText).build();
        mNotificationManager.notify(NOTIFICATION_NAMESPACE, notificationId, notification);
        mNotifications.put(notificationId, mediaType);
        updateSharedPreferencesEntry(notificationId, false);
    }

    /**
     * Update shared preferences entry with ids of the visible notifications.
     * @param notificationId Id of the notification.
     * @param remove Boolean describing if the notification was added or removed.
     */
    private void updateSharedPreferencesEntry(int notificationId, boolean remove) {
        Set<String> notificationIds =
                new HashSet<String>(mSharedPreferences.getStringSet(MEDIA_NOTIFICATION_IDS,
                        new HashSet<String>()));
        if (remove && !notificationIds.isEmpty()
                && notificationIds.contains(String.valueOf(notificationId))) {
            notificationIds.remove(String.valueOf(notificationId));
        } else if (!remove) {
            notificationIds.add(String.valueOf(notificationId));
        }
        SharedPreferences.Editor sharedPreferenceEditor =  mSharedPreferences.edit();
        sharedPreferenceEditor.putStringSet(MEDIA_NOTIFICATION_IDS, notificationIds);
        sharedPreferenceEditor.apply();
    }

    @Override
    public void onDestroy() {
        cancelPreviousWebRtcNotifications();
        super.onDestroy();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        cancelPreviousWebRtcNotifications();
        return super.onUnbind(intent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * @param audio If audio is being captured.
     * @param video If video is being captured.
     * @return A constant identify what media is being captured.
     */
    public static int getMediaType(boolean audioCapture,
            boolean videoCapture, boolean audioPlayback) {
        if (audioCapture && videoCapture) {
            return MEDIATYPE_AUDIO_AND_VIDEO_CAPTURE;
        } else if (audioCapture) {
            return MEDIATYPE_AUDIO_CAPTURE_ONLY;
        } else if (videoCapture) {
            return MEDIATYPE_VIDEO_CAPTURE_ONLY;
        } else if (audioPlayback) {
            return MEDIATYPE_AUDIO_PLAYBACK;
        } else {
            return MEDIATYPE_NO_MEDIA;
        }
    }

    private static boolean shouldStartService(Context context, int mediaType, int tabId) {
        if (mediaType != MEDIATYPE_NO_MEDIA) return true;
        SharedPreferences sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(context);
        Set<String> notificationIds =
                sharedPreferences.getStringSet(MEDIA_NOTIFICATION_IDS, null);
        if (notificationIds != null
                && !notificationIds.isEmpty()
                && notificationIds.contains(String.valueOf(tabId))) {
            return true;
        }
        return false;
    }

    /**
     * Send an intent to MediaNotificationService to either create, update or destroy the
     * notification identified by tabId.
     * @param tabId Unique notification id.
     * @param audio If audio is being captured.
     * @param video If video is being captured.
     * @param fullUrl Url of the current webrtc call.
     */
    public static void updateMediaNotificationForTab(Context context, int tabId,
            boolean audioCapture, boolean videoCapture, boolean audioPlayback,
            String fullUrl) {
        int mediaType = getMediaType(audioCapture, videoCapture, audioPlayback);
        if (!shouldStartService(context, mediaType, tabId)) return;
        Intent intent = new Intent(context, MediaNotificationService.class);
        intent.putExtra(NOTIFICATION_ID_EXTRA, tabId);
        String baseUrl = fullUrl;
        try {
            URL url = new URL(fullUrl);
            baseUrl = url.getProtocol() + "://" + url.getHost();
        } catch (MalformedURLException e) {
            Log.w(LOG_TAG, "Error parsing the webrtc url " + fullUrl);
        }
        intent.putExtra(NOTIFICATION_MEDIA_URL_EXTRA, baseUrl);
        intent.putExtra(NOTIFICATION_MEDIA_TYPE_EXTRA, mediaType);
        context.startService(intent);
    }

    /**
     * Clear any previous media notifications.
     */
    public static void clearMediaNotifications(Context context) {
        SharedPreferences sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(context);
        Set<String> notificationIds =
                sharedPreferences.getStringSet(MEDIA_NOTIFICATION_IDS, null);
        if (notificationIds == null || notificationIds.isEmpty()) return;

        context.startService(new Intent(context, MediaNotificationService.class));
    }
}

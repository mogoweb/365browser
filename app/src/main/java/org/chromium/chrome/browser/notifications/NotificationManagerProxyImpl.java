// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.notifications;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;

import org.chromium.base.BuildInfo;
import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.chrome.browser.notifications.channels.Channel;
import org.chromium.chrome.browser.notifications.channels.ChannelDefinitions;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Default implementation of the NotificationManagerProxy, which passes through all calls to the
 * normal Android Notification Manager.
 */
public class NotificationManagerProxyImpl implements NotificationManagerProxy {
    private static final String TAG = "NotifManagerProxy";
    private final NotificationManager mNotificationManager;

    public NotificationManagerProxyImpl(NotificationManager notificationManager) {
        mNotificationManager = notificationManager;
    }

    @Override
    public void cancel(int id) {
        mNotificationManager.cancel(id);
    }

    @Override
    public void cancel(String tag, int id) {
        mNotificationManager.cancel(tag, id);
    }

    @Override
    public void cancelAll() {
        mNotificationManager.cancelAll();
    }

    @SuppressLint("NewApi")
    @Override
    public void createNotificationChannel(Channel channel) {
        assert BuildInfo.isAtLeastO();
        /*
        The code in the try-block uses reflection in order to compile as it calls APIs newer than
        our compileSdkVersion of Android. The equivalent code without reflection looks like this:

            NotificationChannel nc = new NotificationChannel(channel.getId(), channel.getName(),
                    channel.getImportance());
            nc.setGroup(channel.getGroupId());
            nc.setShowBadge(false);
            mNotificationManager.createNotificationChannel(nc);
         */
        // TODO(crbug.com/707804) Stop using reflection once compileSdkVersion is high enough.
        try {
            // Create channel
            Class<?> channelClass = Class.forName("android.app.NotificationChannel");
            Constructor<?> channelConstructor = channelClass.getDeclaredConstructor(
                    String.class, CharSequence.class, int.class);
            Object channelObject = channelConstructor.newInstance(
                    channel.getId(), channel.getName(), channel.getImportance());

            // Set group on channel
            Method setGroupMethod = channelClass.getMethod("setGroup", String.class);
            setGroupMethod.invoke(channelObject, channel.getGroupId());

            // Set channel to not badge on app icon
            Method setShowBadgeMethod = channelClass.getMethod("setShowBadge", boolean.class);
            setShowBadgeMethod.invoke(channelObject, false);

            // Register channel
            Method createNotificationChannelMethod = mNotificationManager.getClass().getMethod(
                    "createNotificationChannel", channelClass);
            createNotificationChannelMethod.invoke(mNotificationManager, channelObject);

        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException
                | InstantiationException | InvocationTargetException e) {
            Log.e(TAG, "Error initializing notification channel:", e);
        }
    }

    @SuppressLint("NewApi")
    @Override
    public void createNotificationChannelGroup(ChannelDefinitions.ChannelGroup channelGroup) {
        assert BuildInfo.isAtLeastO();
        /*
        The code in the try-block uses reflection in order to compile as it calls APIs newer than
        our compileSdkVersion of Android. The equivalent code without reflection looks like this:

            mNotificationManager.createNotificationChannelGroup(channelGroup);
         */
        // TODO(crbug.com/707804) Stop using reflection once compileSdkVersion is high enough.
        try {
            // Create channel group
            Class<?> channelGroupClass = Class.forName("android.app.NotificationChannelGroup");
            Constructor<?> channelGroupConstructor =
                    channelGroupClass.getDeclaredConstructor(String.class, CharSequence.class);
            Object channelGroupObject = channelGroupConstructor.newInstance(channelGroup.mId,
                    ContextUtils.getApplicationContext().getString(channelGroup.mNameResId));

            // Register channel group
            Method createNotificationChannelGroupMethod = mNotificationManager.getClass().getMethod(
                    "createNotificationChannelGroup", channelGroupClass);
            createNotificationChannelGroupMethod.invoke(mNotificationManager, channelGroupObject);

        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException
                | InstantiationException | InvocationTargetException e) {
            Log.e(TAG, "Error initializing notification channel group:", e);
        }
    }

    @SuppressLint("NewApi")
    @Override
    public List<Channel> getNotificationChannels() {
        assert BuildInfo.isAtLeastO();
        List<Channel> channels = new ArrayList<>();
        /*
        The code in the try-block uses reflection in order to compile as it calls APIs newer than
        our compileSdkVersion of Android. The equivalent code without reflection looks like this:

            List<NotificationChannel> list = mNotificationManager.getNotificationChannels();
            for (NotificationChannel nc : list) {
                list.add(new Channel(
                        nc.getId(), nc.getName(), nc.getImportance(), nc.getGroupId()));
            }
         */
        // TODO(crbug.com/707804) Stop using reflection once compileSdkVersion is high enough.
        try {
            Method method = mNotificationManager.getClass().getMethod("getNotificationChannels");
            List channelsList = (List) method.invoke(mNotificationManager);
            for (Object o : channelsList) {
                Method getId = o.getClass().getMethod("getId");
                Method getName = o.getClass().getMethod("getName");
                Method getImportance = o.getClass().getMethod("getImportance");
                Method getGroup = o.getClass().getMethod("getGroup");
                String channelId = (String) getId.invoke(o);
                String name = (String) getName.invoke(o);
                int importance = (int) getImportance.invoke(o);
                String groupId = (String) getGroup.invoke(o);
                channels.add(new Channel(channelId, name, importance, groupId));
            }
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            Log.e(TAG, "Error getting notification channels:", e);
        }
        return channels;
    }

    @SuppressLint("NewApi")
    @Override
    public void deleteNotificationChannel(String id) {
        assert BuildInfo.isAtLeastO();
        /*
        The code in the try-block uses reflection in order to compile as it calls APIs newer than
        our compileSdkVersion of Android. The equivalent code without reflection looks like this:

            mNotificationManager.deleteNotificationChannel(id);
         */
        // TODO(crbug.com/707804) Stop using reflection once compileSdkVersion is high enough.
        try {
            Method method = mNotificationManager.getClass().getMethod(
                    "deleteNotificationChannel", String.class);
            method.invoke(mNotificationManager, id);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            Log.e(TAG, "Error deleting notification channel:", e);
        }
    }

    @Override
    public void notify(int id, Notification notification) {
        mNotificationManager.notify(id, notification);
    }

    @Override
    public void notify(String tag, int id, Notification notification) {
        mNotificationManager.notify(tag, id, notification);
    }
}

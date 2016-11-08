// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.notifications;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Icon;
import android.os.Build;

import org.chromium.base.VisibleForTesting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.annotation.Nullable;

/**
 * Abstract base class for building a notification. Stores all given arguments for later use.
 */
public abstract class NotificationBuilderBase {
    protected static class Action {
        public int iconId;
        public Bitmap iconBitmap;
        public CharSequence title;
        public PendingIntent intent;

        Action(int iconId, CharSequence title, PendingIntent intent) {
            this.iconId = iconId;
            this.title = title;
            this.intent = intent;
        }

        Action(Bitmap iconBitmap, CharSequence title, PendingIntent intent) {
            this.iconBitmap = iconBitmap;
            this.title = title;
            this.intent = intent;
        }
    }

    /**
     * Maximum length of CharSequence inputs to prevent excessive memory consumption. At current
     * screen sizes we display about 500 characters at most, so this is a pretty generous limit, and
     * it matches what the Notification class does.
     */
    @VisibleForTesting
    static final int MAX_CHARSEQUENCE_LENGTH = 5 * 1024;

    /**
     * The maximum number of author provided action buttons. The settings button is not part of this
     * count.
     */
    private static final int MAX_AUTHOR_PROVIDED_ACTION_BUTTONS = 2;

    protected CharSequence mTitle;
    protected CharSequence mBody;
    protected CharSequence mOrigin;
    protected CharSequence mTickerText;
    protected Bitmap mImage;
    protected Bitmap mLargeIcon;
    protected int mSmallIconId;
    protected Bitmap mSmallIconBitmap;
    protected PendingIntent mContentIntent;
    protected PendingIntent mDeleteIntent;
    protected List<Action> mActions = new ArrayList<>(MAX_AUTHOR_PROVIDED_ACTION_BUTTONS);
    protected Action mSettingsAction;
    protected int mDefaults = Notification.DEFAULT_ALL;
    protected long[] mVibratePattern;
    protected long mTimestamp;
    protected boolean mRenotify;

    /**
     * Combines all of the options that have been set and returns a new Notification object.
     */
    public abstract Notification build();

    /**
     * Sets the title text of the notification.
     */
    public NotificationBuilderBase setTitle(@Nullable CharSequence title) {
        mTitle = limitLength(title);
        return this;
    }

    /**
     * Sets the body text of the notification.
     */
    public NotificationBuilderBase setBody(@Nullable CharSequence body) {
        mBody = limitLength(body);
        return this;
    }

    /**
     * Sets the origin text of the notification.
     */
    public NotificationBuilderBase setOrigin(@Nullable CharSequence origin) {
        mOrigin = limitLength(origin);
        return this;
    }

    /**
     * Sets the text that is displayed in the status bar when the notification first arrives.
     */
    public NotificationBuilderBase setTicker(@Nullable CharSequence tickerText) {
        mTickerText = limitLength(tickerText);
        return this;
    }

    /**
     * Sets the content image to be prominently displayed when the notification is expanded.
     */
    public NotificationBuilderBase setImage(@Nullable Bitmap image) {
        mImage = image;
        return this;
    }

    /**
     * Sets the large icon that is shown in the notification.
     */
    public NotificationBuilderBase setLargeIcon(@Nullable Bitmap icon) {
        mLargeIcon = icon;
        return this;
    }

    /**
     * Sets the small icon that is shown in the notification and in the status bar. Wherever the
     * platform supports using a small icon bitmap, and a non-null {@code Bitmap} is provided, it
     * will take precedence over one specified as a resource id.
     */
    public NotificationBuilderBase setSmallIcon(int iconId) {
        mSmallIconId = iconId;
        return this;
    }

    /**
     * Sets the small icon that is shown in the notification and in the status bar. Wherever the
     * platform supports using a small icon bitmap, and a non-null {@code Bitmap} is provided, it
     * will take precedence over one specified as a resource id.
     */
    public NotificationBuilderBase setSmallIcon(@Nullable Bitmap iconBitmap) {
        if (iconBitmap != null) {
            applyWhiteOverlayToBitmap(iconBitmap);
        }
        mSmallIconBitmap = iconBitmap;
        return this;
    }

    /**
     * Sets the PendingIntent to send when the notification is clicked.
     */
    public NotificationBuilderBase setContentIntent(@Nullable PendingIntent intent) {
        mContentIntent = intent;
        return this;
    }

    /**
     * Sets the PendingIntent to send when the notification is cleared by the user directly from the
     * notification panel.
     */
    public NotificationBuilderBase setDeleteIntent(@Nullable PendingIntent intent) {
        mDeleteIntent = intent;
        return this;
    }

    /**
     * Adds an action to the notification. Actions are typically displayed as a button adjacent to
     * the notification content.
     */
    public NotificationBuilderBase addAction(@Nullable Bitmap iconBitmap,
            @Nullable CharSequence title, @Nullable PendingIntent intent) {
        if (mActions.size() == MAX_AUTHOR_PROVIDED_ACTION_BUTTONS) {
            throw new IllegalStateException(
                    "Cannot add more than " + MAX_AUTHOR_PROVIDED_ACTION_BUTTONS + " actions.");
        }
        if (iconBitmap != null) {
            applyWhiteOverlayToBitmap(iconBitmap);
        }
        mActions.add(new Action(iconBitmap, limitLength(title), intent));
        return this;
    }

    /**
     * Adds an action to the notification for opening the settings screen.
     */
    public NotificationBuilderBase addSettingsAction(
            int iconId, @Nullable CharSequence title, @Nullable PendingIntent intent) {
        mSettingsAction = new Action(iconId, limitLength(title), intent);
        return this;
    }

    /**
     * Sets the default notification options that will be used.
     * <p>
     * The value should be one or more of the following fields combined with
     * bitwise-or:
     * {@link Notification#DEFAULT_SOUND}, {@link Notification#DEFAULT_VIBRATE},
     * {@link Notification#DEFAULT_LIGHTS}.
     * <p>
     * For all default values, use {@link Notification#DEFAULT_ALL}.
     */
    public NotificationBuilderBase setDefaults(int defaults) {
        mDefaults = defaults;
        return this;
    }

    /**
     * Sets the vibration pattern to use.
     */
    public NotificationBuilderBase setVibrate(long[] pattern) {
        mVibratePattern = Arrays.copyOf(pattern, pattern.length);
        return this;
    }

    /**
     * Sets the timestamp at which the event of the notification took place.
     */
    public NotificationBuilderBase setTimestamp(long timestamp) {
        mTimestamp = timestamp;
        return this;
    }

    /**
     * Sets the behavior for when the notification is replaced.
     */
    public NotificationBuilderBase setRenotify(boolean renotify) {
        mRenotify = renotify;
        return this;
    }

    @Nullable
    private static CharSequence limitLength(@Nullable CharSequence input) {
        if (input == null) {
            return input;
        }
        if (input.length() > MAX_CHARSEQUENCE_LENGTH) {
            return input.subSequence(0, MAX_CHARSEQUENCE_LENGTH);
        }
        return input;
    }

    /**
     * Sets the small icon on {@code builder} using a {@code Bitmap} if a non-null bitmap is
     * provided and the API level is high enough, otherwise the resource id is used.
     */
    @TargetApi(Build.VERSION_CODES.M) // For the Icon class.
    protected static void setSmallIconOnBuilder(
            Notification.Builder builder, int iconId, @Nullable Bitmap iconBitmap) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && iconBitmap != null) {
            builder.setSmallIcon(Icon.createWithBitmap(iconBitmap));
        } else {
            builder.setSmallIcon(iconId);
        }
    }

    /**
     * Adds an action to {@code builder} using a {@code Bitmap} if a bitmap is provided and the API
     * level is high enough, otherwise a resource id is used.
     */
    @SuppressWarnings("deprecation") // For addAction(int, CharSequence, PendingIntent)
    @TargetApi(Build.VERSION_CODES.M) // For the Icon class.
    protected static void addActionToBuilder(Notification.Builder builder, Action action) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && action.iconBitmap != null) {
            Icon icon = Icon.createWithBitmap(action.iconBitmap);
            builder.addAction(
                    new Notification.Action.Builder(icon, action.title, action.intent).build());
        } else {
            builder.addAction(action.iconId, action.title, action.intent);
        }
    }

    /**
     * Paints {@code bitmap} white. This processing should be performed if the Android system
     * expects a bitmap to be white, and the bitmap is not already known to be white. The bitmap
     * must be mutable.
     */
    static void applyWhiteOverlayToBitmap(Bitmap bitmap) {
        Paint paint = new Paint();
        paint.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP));
        Canvas canvas = new Canvas(bitmap);
        canvas.drawBitmap(bitmap, 0, 0, paint);
    }
}

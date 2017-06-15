# Notification Channels

## Background

Notification channels define the togglable categories shown in our notification
settings within Android settings UI in Android O and above. Channels also
provide properties for our notifications, such as whether they vibrate or
make a sound, and expose these settings to the user.

Starting with Android O, all notifications must be assigned to a registered
notification channel. We enforce this in the codebase by requiring all
notifications to be constructed using
`NotificationBuilderFactory.createChromeNotificationBuilder`, which requires a
valid `ChannelId`.

In M58 we started with only two channels - Sites and Browser. Web notifications
are posted to the Sites channel and all other notifications from the browser
went to the Browser channel.

In M59 we split various more specific channels out of the Browser channel,
including Media, Incognito and Downloads. The Browser channel still exists as
a general catch-all category for notifications sent from the browser.

For an up-to-date enumeration of what channels exist, see the
map of `ChannelId`s to `Channel`s in `ChannelDefinitions.PredefinedChannels`.

Further reading:
- [Android Notification Channels documentation](https://developer.android.com/preview/features/notification-channels.html)
- [Breaking out more channels post-M58 - design doc](https://docs.google.com/document/d/1K9pjvlHF1oANNI8TqZgy151tap9zs1KUr2qfBXo1s_4/edit?usp=sharing)

## When should a new channel be added?

New channels for new types of notifications should be added with caution -
whilst they do provide finer-grain control for users, this should be traded
off against the risk of settings creep. A multitude of settings can be
confusing, and users may have to toggle multiple settings to achieve their
desired state. Additionally, it’s hard to go back once channels have been
split out, without the risk of disregarding user preferences set on those
channels.

Therefore, any proposed new channels should go through the Chrome UI review
process.

If in doubt, we recommend posting the notification to the generic Browser
channel (assuming the Browser channel properties are appropriate). A new channel
can always be split out in future if deemed necessary.

> **Note**: Any time a new type of notification is added, a new
`SystemNotificationType` should be added to `histograms.xml` and
`NotificationUmaTracker.onNotificationShown` must be called with this new
 type whenever any notifications are shown, to collect UMA on how often the
 notifications are blocked. *It is not necessary to add a new channel
 for every new SystemNotificationType.*

## How to add a new notification channel

To register a new notification channel that notifications can be posted to,
once UI approval has been granted, follow these steps:

1. Add a new id to the `@ChannelId` intdef in `ChannelDefinitions.java`
2. Add a corresponding entry to `PredefinedChannels.MAP` in
`ChannelDefinitions.java` with the new channel properties (you'll need a new
string for the channel name)
3. Increment `CHANNELS_VERSION` in `ChannelDefinitions.java`
4. Update tests in `ChannelsInitializerTest.java` concerning which channels
should appear on startup*, and add a test for your new channel's properties.
5. Create notifications via
`NotificationBuilderFactory.createChromeNotificationBuilder`, passing the new
channel id (the custom builder will set the channel on the notification for
you, and ensure the channel is initialized before building it)
6. After posting a notification, remember to call
`NotificationUmaTracker.onNotificationShown`, passing the new channel id
(along with your new `SystemNotificationType`, see above)

> ***Warning**: Currently all predefined channels are initialized on first
launch, so they will appear in system settings even before any notifications
are shown from that channel. This should usually be the preferred behaviour,
and you will get it for free by following the above steps.

## How to deprecate a channel

Note, renaming an existing channel is free, just update the string and bump the
`CHANNELS_VERSION` in `ChannelDefinitions.java` so that updaters pick up the
change.

To stop an existing channel showing up any more, follow the following steps:

1. Ensure any notifications previously associated with this channel no longer
exist, or are now sent to alternative channels.
2. Remove the channel's entry from the `PredefinedChannels.MAP` in
`ChannelDefinitions.java`
3. Move the channel id from the `@ChannelId` intdef to the `LEGACY_CHANNEL_IDS`
array in `ChannelDefinitions.java`
4. Increment `CHANNELS_VERSION` in `ChannelDefinitions.java`
5. Update tests in `ChannelsInitializerTest.java` that refer to the old channel

This should only happen infrequently. Note a 'deleted channels' count in
the browser's system notification settings will appear & increment every time a
channel is deleted.

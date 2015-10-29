// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.remote;

import android.graphics.Bitmap;
import android.net.Uri;
import android.support.v7.media.MediaRouteSelector;

import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.media.remote.RemoteVideoInfo.PlayerState;

/**
 * Each MediaRouteController controls the routes to devices which support remote playback of
 * particular categories of Media elements (e.g. all YouTube media elements, all media elements
 * with simple http source URLs). The MediaRouteController is responsible for configuring
 * and controlling remote playback of the media elements it supports.
 */
public interface MediaRouteController extends TransportControl.Listener {
    /**
     * Listener for events that are relevant to the state of the media and the media controls
     */
    public interface MediaStateListener {
        /**
         * Called when the first route becomes available, or the last route
         * is removed.
         * @param available whether routes are available.
         */
        public void onRouteAvailabilityChanged(boolean available);

        /**
         * Called when an error is detected by the media route controller
         */
        public void onError();

        /**
         * Called when a seek completes on the current route
         */
        public void onSeekCompleted();

        /**
         * Called when the current route is ready to be used
         */
        public void onPrepared();

        /**
         * Called when a new route has been selected for Cast
         * @param name the name of the route
         */
        public void onRouteSelected(String name);

        /**
         * Called when the current route is unselected
         */
        public void onRouteUnselected();

        /**
         * Called when the playback state changes (e.g. from Playing to Paused)
         * @param newState the new playback state
         */
        public void onPlaybackStateChanged(PlayerState newState);

        /**
         * @return the title of the video
         */
        public String getTitle();

        /**
         * @return the poster bitmap
         */
        public Bitmap getPosterBitmap();
    }

    /**
     * Listener for events that are relevant to the Browser UI.
     */
    public interface UiListener {

        /**
         * Called when a new route is selected
         * @param name the name of the new route
         * @param mediaRouteController the controller that selected the route
         */
        void onRouteSelected(String name, MediaRouteController mediaRouteController);

        /**
         * Called when the current route is unselected
         * @param mediaRouteController the controller that had the route.
         */
        void onRouteUnselected(MediaRouteController mediaRouteController);

        /**
         * Called when the current route is ready to be used
         * @param mediaRouteController the controller that has the route.
         */
        void onPrepared(MediaRouteController mediaRouteController);

        /**
         * Called when an error is detected by the controller
         * @param errorType One of the error types from CastMediaControlIntent
         * @param message The message for the error
         */
        void onError(int errorType, String message);

        /**
         * Called when the Playback state has changed (e.g. from playing to paused)
         * @param oldState the old state
         * @param newState the new state
         */
        void onPlaybackStateChanged(PlayerState oldState, PlayerState newState);

        /**
         * Called when the duration of the currently playing video changes.
         * @param durationMillis the new duration in ms.
         */
        void onDurationUpdated(int durationMillis);

        /**
         * Called when the media route controller receives new information about the
         * current position in the video.
         * @param positionMillis the current position in the video in ms.
         */
        void onPositionChanged(int positionMillis);

        /**
         * Called if the title of the video changes
         * @param title the new title
         */
        void onTitleChanged(String title);
    }

    /**
     * Scan routes, and set up the MediaRouter object. This is called at every time we need to reset
     * the state. Because of that, this function is idempotent. If that changes in the future, where
     * this function gets called needs to be re-evaluated.
     *
     * @return false if device doesn't support cast, true otherwise.
     */
    public boolean initialize();

    /**
     * Can this mediaRouteController handle a media element?
     * @param sourceUrl the source
     * @param frameUrl
     * @return true if it can, false if it can't.
     */
    public boolean canPlayMedia(String sourceUrl, String frameUrl);

    /**
     * @return A new MediaRouteSelector filtering the remote playback devices from all the routes.
     */
    public MediaRouteSelector buildMediaRouteSelector();

    /**
     * @return Whether there're remote playback devices available.
     */
    public boolean isRemotePlaybackAvailable();

    /**
     * @return Whether the currently selected device supports remote playback
     */
    public boolean currentRouteSupportsRemotePlayback();

    /**
     * Checks if we want to reconnect, and if so starts trying to do so. Otherwise clears out the
     * persistent request to reconnect.
     */
    public boolean reconnectAnyExistingRoute();

    /**
     * Sets the video URL when it becomes known.
     *
     * This is the original video URL but if there's URL redirection, it will change as resolved by
     * {@link MediaUrlResolver}.
     *
     * @param uri The video URL.
     * @param userAgent The browser user agent.
     */
    public void setDataSource(Uri uri, String cookies, String userAgent);

    /**
     * Setup this object to discover new routes and register the necessary players.
     */
    public void prepareMediaRoute();

    /**
     * Add a Listener that will listen to events from this object
     *
     * @param listener the Listener that will receive the events
     */
    public void addUiListener(UiListener listener);

    /**
     * Removes a Listener from this object
     *
     * @param listener the Listener to remove
     */
    public void removeUiListener(UiListener listener);

    /**
     * @return The currently selected route's friendly name, or null if there is none selected
     */
    public String getRouteName();

    /**
     * @return true if this is currently using the default route, false if not.
     */
    public boolean routeIsDefaultRoute();

    /**
     * Called to prepare the remote playback asyncronously. onPrepared() of the current remote media
     * player object is called when the player is ready.
     *
     * @param startPositionMillis indicates where in the stream to start playing
     */
    public void prepareAsync(String frameUrl, long startPositionMillis);

    /**
     * Sets the remote volume of the current route.
     *
     * @param delta The delta value in arbitrary "Android Volume Units".
     */
    public void setRemoteVolume(int delta);

    /**
     * Resume paused playback of the current video.
     */
    public void resume();

    /**
     * Pauses the currently playing video if any.
     */
    public void pause();

    /**
     * Returns the current remote playback position. Estimates the current position by using the
     * last known position and the current time.
     *
     *  TODO(avayvod): Send periodic status update requests to update the position once in several
     * seconds or so.
     *
     * @return The current position of the remote playback in milliseconds.
     */
    public int getPosition();

    /**
     * @return The stream duration in milliseconds.
     */
    public int getDuration();

    /**
     * @return Whether the video is currently being played.
     */
    public boolean isPlaying();

    /**
     * @return Whether the video is being cast (any of playing/paused/loading/stopped).
     */
    public boolean isBeingCast();

    /**
     * Initiates a seek request for the remote playback device to the specified position.
     *
     * @param msec The position to seek to, in milliseconds.
     */
    public void seekTo(int msec);

    /**
     * Stop the current remote playback completely and release all resources.
     */
    public void release();

    /**
     * @param player - the current player using this media route controller.
     */
    public void setMediaStateListener(MediaStateListener listener);

    /**
     * @return the current VideoStateListener
     */
    public MediaStateListener getMediaStateListener();

    /**
     * @return true if the video is new
     */
    public boolean shouldResetState(MediaStateListener newListener);

    @VisibleForTesting
    public PlayerState getPlayerState();

    /**
     * Remove an existing media state listener
     * @param listener
     */
    public void removeMediaStateListener(MediaStateListener listener);

    /**
     * Add a media state listener
     * @param listener
     */
    public void addMediaStateListener(MediaStateListener listener);

    /**
     * Get the poster for the video, if any
     * @return the poster bitmap, or Null.
     */
    public Bitmap getPoster();
}

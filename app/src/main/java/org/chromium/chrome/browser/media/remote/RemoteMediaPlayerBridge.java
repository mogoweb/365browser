// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.remote;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import org.chromium.base.CommandLine;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.chrome.browser.ChromeSwitches;
import org.chromium.chrome.browser.media.remote.RemoteVideoInfo.PlayerState;
import org.chromium.media.MediaPlayerBridge;

/**
 * Acts as a proxy between the remotely playing video and the HTMLMediaElement.
 *
 * Note that the only reason this derives from MediaPlayerBridge is that the
 * MediaPlayerListener takes a MediaPlayerBridge in its constructor.
 * TODO(aberent) fix this by creating a MediaPlayerBridgeInterface (or similar).
 */
@JNINamespace("remote_media")
public class RemoteMediaPlayerBridge extends MediaPlayerBridge {
    private final long mStartPositionMillis;
    private long mNativeRemoteMediaPlayerBridge;

    private MediaPlayer.OnCompletionListener mOnCompletionListener;
    private MediaPlayer.OnSeekCompleteListener mOnSeekCompleteListener;
    private MediaPlayer.OnErrorListener mOnErrorListener;
    private MediaPlayer.OnPreparedListener mOnPreparedListener;

    private final MediaRouteController mRouteController;
    private final String mSourceUrl;
    private final String mFrameUrl;
    private final boolean mDebug;
    private Bitmap mPosterBitmap;

    // mActive is true when the Chrome is playing, or preparing to play, this player's video
    // remotely.
    private boolean mActive = false;

    private static final String TAG = "RemoteMediaPlayerBridge";

    private MediaRouteController.MediaStateListener mMediaStateListener =
            new MediaRouteController.MediaStateListener() {
        @Override
        public void onRouteAvailabilityChanged(boolean available) {
            if (mNativeRemoteMediaPlayerBridge == 0) return;
            nativeOnRouteAvailabilityChanged(mNativeRemoteMediaPlayerBridge, available);
        }

        @Override
        public void onError() {
            if (mActive && mOnErrorListener != null) {
                @SuppressLint("InlinedApi")
                int errorExtra = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1
                        ? MediaPlayer.MEDIA_ERROR_TIMED_OUT
                        : 0;
                mOnErrorListener.onError(null, MediaPlayer.MEDIA_ERROR_UNKNOWN, errorExtra);
            }
        }

        @Override
        public void onSeekCompleted() {
            if (mActive && mOnSeekCompleteListener != null) {
                mOnSeekCompleteListener.onSeekComplete(null);
            }
        }

        @Override
        public void onPrepared() {
            if (mActive && mOnPreparedListener != null) {
                mOnPreparedListener.onPrepared(null);
            }
        }

        @Override
        public void onRouteSelected(String name) {
            if (mNativeRemoteMediaPlayerBridge == 0) return;
            nativeOnRouteSelected(mNativeRemoteMediaPlayerBridge,
                    RemoteMediaPlayerController.instance().getCastingMessage(name));
        }

        @Override
        public void onRouteUnselected() {
            if (mNativeRemoteMediaPlayerBridge == 0) return;
            nativeOnRouteUnselected(mNativeRemoteMediaPlayerBridge);
        }

        @Override
        public void onPlaybackStateChanged(PlayerState newState) {
            if (mNativeRemoteMediaPlayerBridge == 0) return;
            if (newState == PlayerState.FINISHED || newState == PlayerState.INVALIDATED) {
                onCompleted();
                nativeOnPlaybackFinished(mNativeRemoteMediaPlayerBridge);
            } else if (newState == PlayerState.PLAYING) {
                nativeOnPlaying(mNativeRemoteMediaPlayerBridge);
            } else if (newState == PlayerState.PAUSED) {
                nativeOnPaused(mNativeRemoteMediaPlayerBridge);
            }
        }

        @Override
        public String getTitle() {
            if (mNativeRemoteMediaPlayerBridge == 0) return null;
            return nativeGetTitle(mNativeRemoteMediaPlayerBridge);
        }

        @Override
        public Bitmap getPosterBitmap() {
            return mPosterBitmap;
        }
    };

    private RemoteMediaPlayerBridge(long nativeRemoteMediaPlayerBridge, long startPositionMillis,
            String sourceUrl, String frameUrl) {

        mDebug = CommandLine.getInstance().hasSwitch(ChromeSwitches.ENABLE_CAST_DEBUG_LOGS);

        if (mDebug) Log.i(TAG, "Creating RemoteMediaPlayerBridge");
        mNativeRemoteMediaPlayerBridge = nativeRemoteMediaPlayerBridge;
        mStartPositionMillis = startPositionMillis;
        mSourceUrl = sourceUrl;
        mFrameUrl = frameUrl;
        // This will get null if there isn't a mediaRouteController that can play this media.
        mRouteController = RemoteMediaPlayerController.instance()
                .getMediaRouteController(sourceUrl, frameUrl);
    }

    @CalledByNative
    private static RemoteMediaPlayerBridge create(long nativeRemoteMediaPlayerBridge,
            long startPositionMillis, String sourceUrl, String frameUrl) {
        return new RemoteMediaPlayerBridge(nativeRemoteMediaPlayerBridge, startPositionMillis,
                sourceUrl, frameUrl);
    }

    /**
     * Called when a lower layer requests that a video be cast. This will typically be a request
     * from Blink when the cast button is pressed on the default video controls.
     */
    @CalledByNative
    private void requestRemotePlayback() {
        if (mDebug) Log.i(TAG, "requestRemotePlayback");
        RemoteMediaPlayerController.instance().requestRemotePlayback(
                mMediaStateListener, mRouteController);
    }

    /**
     * Called when a lower layer requests control of a video that is being cast.
     */
    @CalledByNative
    private void requestRemotePlaybackControl() {
        if (mDebug) Log.i(TAG, "requestRemotePlaybackControl");
        RemoteMediaPlayerController.instance().requestRemotePlaybackControl(mMediaStateListener);
    }

    @CalledByNative
    private void setNativePlayer() {
        if (mDebug) Log.i(TAG, "setNativePlayer");
        mRouteController.setMediaStateListener(mMediaStateListener);
        mActive = true;
    }

    @CalledByNative
    private void onPlayerCreated() {
        if (mDebug) Log.i(TAG, "onPlayerCreated");
        if (mRouteController != null) {
            mRouteController.addMediaStateListener(mMediaStateListener);
        }
    }

    @CalledByNative
    private void onPlayerDestroyed() {
        if (mDebug) Log.i(TAG, "onPlayerDestroyed");
        if (mRouteController != null) {
            mRouteController.removeMediaStateListener(mMediaStateListener);
        }
    }

    /**
     * @return Whether there're remote playback devices available.
     */
    @CalledByNative
    private boolean isRemotePlaybackAvailable() {
        return mRouteController.isRemotePlaybackAvailable();
    }

    /**
     * @param bitmap The bitmap of the poster for the video, null if no poster image exists.
     *
     *         TODO(cimamoglu): Notify the clients (probably through MediaRouteController.Listener)
     *        of the poster image change. This is necessary for when a web page changes the poster
     *        while the client (i.e. only ExpandedControllerActivity for now) is active.
     */
    @CalledByNative
    private void setPosterBitmap(Bitmap bitmap) {
        mPosterBitmap = bitmap;
    }

    /**
     * @return Whether the video should be played remotely if possible
     */
    @CalledByNative
    private boolean isRemotePlaybackPreferredForFrame() {
        return !mRouteController.routeIsDefaultRoute()
                && mRouteController.currentRouteSupportsRemotePlayback();
    }

    @CalledByNative
    private boolean isMediaPlayableRemotely() {
        return mRouteController != null;
    }

    @Override
    @CalledByNative
    protected boolean prepareAsync() {
        mRouteController.prepareAsync(mFrameUrl, mStartPositionMillis);
        return true;
    }

    @Override
    @CalledByNative
    protected boolean isPlaying() {
        return mRouteController.isPlaying();
    }

    @Override
    @CalledByNative
    protected int getCurrentPosition() {
        return mRouteController.getPosition();
    }

    @Override
    @CalledByNative
    protected int getDuration() {
        return mRouteController.getDuration();
    }

    @Override
    @CalledByNative
    protected void release() {
        // Remove the state change listeners. Release does mean that Chrome is no longer interested
        // in events from the media player.
        mRouteController.setMediaStateListener(null);
        mActive = false;
    }

    @Override
    @CalledByNative
    protected void setVolume(double volume) {
    }

    @Override
    @CalledByNative
    protected void start() throws IllegalStateException {
        mRouteController.resume();
    }

    @Override
    @CalledByNative
    protected void pause() throws IllegalStateException {
        mRouteController.pause();
    }

    @Override
    @CalledByNative
    protected void seekTo(int msec) throws IllegalStateException {
        mRouteController.seekTo(msec);
    }

    @Override
    @CalledByNative
    protected boolean setDataSource(
            Context context, String url, String cookies, String userAgent, boolean hideUrlLog) {
        mRouteController.setDataSource(Uri.parse(url), cookies, userAgent);
        return true;
    }

    @Override
    protected void setOnBufferingUpdateListener(MediaPlayer.OnBufferingUpdateListener listener) {
    }

    @Override
    protected void setOnCompletionListener(MediaPlayer.OnCompletionListener listener) {
        mOnCompletionListener = listener;
    }

    @Override
    protected void setOnSeekCompleteListener(MediaPlayer.OnSeekCompleteListener listener) {
        mOnSeekCompleteListener = listener;
    }

    @Override
    protected void setOnErrorListener(MediaPlayer.OnErrorListener listener) {
        mOnErrorListener = listener;
    }

    @Override
    protected void setOnPreparedListener(MediaPlayer.OnPreparedListener listener) {
        mOnPreparedListener = listener;
    }

    @Override
    protected void setOnVideoSizeChangedListener(MediaPlayer.OnVideoSizeChangedListener listener) {
    }

    /**
     * Called when the video finishes
     */
    public void onCompleted() {
        if (mActive && mOnCompletionListener != null) {
            mOnCompletionListener.onCompletion(null);
        }
    }

    @Override
    @CalledByNative
    protected void destroy() {
        if (mDebug) Log.i(TAG, "destroy");
        if (mRouteController != null) {
            mRouteController.removeMediaStateListener(mMediaStateListener);
        }
        mNativeRemoteMediaPlayerBridge = 0;
    }

    private native String nativeGetFrameUrl(long nativeRemoteMediaPlayerBridge);
    private native void nativeOnPlaying(long nativeRemoteMediaPlayerBridge);
    private native void nativeOnPaused(long nativeRemoteMediaPlayerBridge);
    private native void nativeOnRouteSelected(long nativeRemoteMediaPlayerBridge,
            String playerName);
    private native void nativeOnRouteUnselected(long nativeRemoteMediaPlayerBridge);
    private native void nativeOnPlaybackFinished(long nativeRemoteMediaPlayerBridge);
    private native void nativeOnRouteAvailabilityChanged(long nativeRemoteMediaPlayerBridge,
            boolean available);
    private native String nativeGetTitle(long nativeRemoteMediaPlayerBridge);

}

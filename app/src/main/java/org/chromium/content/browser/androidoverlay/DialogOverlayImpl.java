// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.androidoverlay;

import android.content.Context;
import android.os.Handler;
import android.os.IBinder;
import android.view.Surface;

import org.chromium.base.ContextUtils;
import org.chromium.base.ThreadUtils;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.gfx.mojom.Rect;
import org.chromium.media.mojom.AndroidOverlay;
import org.chromium.media.mojom.AndroidOverlayClient;
import org.chromium.media.mojom.AndroidOverlayConfig;
import org.chromium.mojo.system.MojoException;

/**
 * Default AndroidOverlay impl.  Uses a separate (shared) overlay thread to own a Dialog instance,
 * probably via a separate object that operates only on that thread.  We will post messages to /
 * from that thread from the UI thread.
 */
@JNINamespace("content")
public class DialogOverlayImpl implements AndroidOverlay, DialogOverlayCore.Host {
    private static final String TAG = "DialogOverlayImpl";

    private AndroidOverlayClient mClient;
    private Handler mOverlayHandler;
    // Runnable that we'll run when the overlay notifies us that it's been released.
    private Runnable mReleasedRunnable;

    private final ThreadHoppingHost mHoppingHost;

    private DialogOverlayCore mDialogCore;

    private long mNativeHandle;

    // If nonzero, then we have registered a surface with this ID.
    private int mSurfaceId;

    // Has close() been run yet?
    private boolean mClosed;

    /**
     * @param client Mojo client interface.
     * @param config initial overlay configuration.
     * @param handler handler that posts to the overlay thread.  This is the android UI thread that
     * the dialog uses, not the browser UI thread.
     * @param provider the overlay provider that owns us.
     */
    public DialogOverlayImpl(AndroidOverlayClient client, final AndroidOverlayConfig config,
            Handler overlayHandler, Runnable releasedRunnable) {
        ThreadUtils.assertOnUiThread();

        mClient = client;
        mReleasedRunnable = releasedRunnable;
        mOverlayHandler = overlayHandler;

        mDialogCore = new DialogOverlayCore();
        mHoppingHost = new ThreadHoppingHost(this);

        // Post init to the overlay thread.
        final DialogOverlayCore dialogCore = mDialogCore;
        final Context context = ContextUtils.getApplicationContext();
        mOverlayHandler.post(new Runnable() {
            @Override
            public void run() {
                dialogCore.initialize(context, config, mHoppingHost);
            }
        });

        // Register to get token updates.
        mNativeHandle = nativeInit(config.routingToken.high, config.routingToken.low);
        assert mNativeHandle != 0;
    }

    // AndroidOverlay impl.
    // Client is done with this overlay.
    @Override
    public void close() {
        ThreadUtils.assertOnUiThread();

        if (mClosed) return;

        mClosed = true;

        // TODO(liberato): verify that this actually works, else add an explicit shutdown and hope
        // that the client calls it.

        // Allow surfaceDestroyed to proceed, if it's waiting.
        mHoppingHost.onCleanup();

        // Notify |mDialogCore| that it has been released.  This might not be called if it notifies
        // us that it's been destroyed.  We still might send it in that case if the client closes
        // the connection before we find out that it's been destroyed on the overlay thread.
        if (mDialogCore != null) {
            final DialogOverlayCore dialogCore = mDialogCore;
            mOverlayHandler.post(new Runnable() {
                @Override
                public void run() {
                    dialogCore.release();
                }
            });

            // Note that we might get messagaes from |mDialogCore| after this, since they might be
            // dispatched before |r| arrives.  Clearing |mDialogCore| causes us to ignore them.
            cleanup();
        }

        // Notify the provider that we've been released by the client.  Note that the surface might
        // not have been destroyed yet, but that's okay.  We could wait for a callback from the
        // dialog core before proceeding, but this makes it easier for the client to destroy and
        // re-create an overlay without worrying about an intermittent failure due to having too
        // many overlays open at once.
        mReleasedRunnable.run();
    }

    // AndroidOverlay impl.
    @Override
    public void onConnectionError(MojoException e) {
        ThreadUtils.assertOnUiThread();

        close();
    }

    // AndroidOverlay impl.
    @Override
    public void scheduleLayout(final Rect rect) {
        ThreadUtils.assertOnUiThread();

        if (mDialogCore == null) return;

        final DialogOverlayCore dialogCore = mDialogCore;
        mOverlayHandler.post(new Runnable() {
            @Override
            public void run() {
                dialogCore.layoutSurface(rect);
            }
        });
    }

    // DialogOverlayCore.Host impl.
    // |surface| is now ready.  Register it with the surface tracker, and notify the client.
    @Override
    public void onSurfaceReady(Surface surface) {
        ThreadUtils.assertOnUiThread();

        if (mDialogCore == null || mClient == null) return;

        mSurfaceId = nativeRegisterSurface(surface);
        mClient.onSurfaceReady(mSurfaceId);
    }

    // DialogOverlayCore.Host impl.
    @Override
    public void onOverlayDestroyed() {
        ThreadUtils.assertOnUiThread();

        if (mDialogCore == null) return;

        // Notify the client that the overlay is gone.
        if (mClient != null) mClient.onDestroyed();

        // Also clear out |mDialogCore| to prevent us from sending useless messages to it.  Note
        // that we might have already sent useless messages to it, and it should be robust against
        // that sort of thing.
        cleanup();

        // Note that we don't notify |mReleasedRunnable| yet, though we could.  We wait for the
        // client to close their connection first.
    }

    // DialogOverlayCore.Host impl.
    // Due to threading issues, |mHoppingHost| doesn't forward this.
    @Override
    public void waitForCleanup() {
        assert false : "Not reached";
    }

    /**
     * Send |token| to the |mDialogCore| on the overlay thread.
     */
    private void sendWindowTokenToCore(final IBinder token) {
        ThreadUtils.assertOnUiThread();

        final DialogOverlayCore dialogCore = mDialogCore;
        mOverlayHandler.post(new Runnable() {
            @Override
            public void run() {
                dialogCore.onWindowToken(token);
            }
        });
    }

    /**
     * Callback from native that the window token has changed.
     */
    @CalledByNative
    public void onWindowToken(final IBinder token) {
        ThreadUtils.assertOnUiThread();

        if (mDialogCore == null) return;

        // Forward this change.
        // Note that if we don't have a window token, then we could wait until we do, simply by
        // skipping sending null if we haven't sent any non-null token yet.  If we're transitioning
        // between windows, that might make the client's job easier. It wouldn't have to guess when
        // a new token is available.
        sendWindowTokenToCore(token);
    }

    /**
     * Callback from native that we will be getting no additional tokens.
     */
    @CalledByNative
    public void onDismissed() {
        ThreadUtils.assertOnUiThread();

        // Notify the client that the overlay is going away.
        if (mClient != null) mClient.onDestroyed();

        // Notify |mDialogCore| that it lost the token, if it had one.
        sendWindowTokenToCore(null);

        cleanup();
    }

    /**
     * Unregister for callbacks, unregister any surface that we have, and forget about
     * |mDialogCore|.  Multiple calls are okay.
     */
    private void cleanup() {
        ThreadUtils.assertOnUiThread();

        if (mSurfaceId != 0) {
            nativeUnregisterSurface(mSurfaceId);
            mSurfaceId = 0;
        }

        // Note that we might not be registered for a token.
        if (mNativeHandle != 0) {
            nativeDestroy(mNativeHandle);
            mNativeHandle = 0;
        }

        // Also clear out |mDialogCore| to prevent us from sending useless messages to it.  Note
        // that we might have already sent useless messages to it, and it should be robust against
        // that sort of thing.
        mDialogCore = null;

        // If we wanted to send any message to |mClient|, we should have done so already.
        mClient = null;
    }

    /**
     * Initializes native side.  Will register for onWindowToken callbacks on |this|.  Returns a
     * handle that should be provided to nativeDestroy.
     */
    private native long nativeInit(long high, long low);

    /**
     * Stops native side and deallocates |handle|.
     */
    private native void nativeDestroy(long nativeDialogOverlayImpl);

    /**
     * Register a surface and return the surface id for it.
     * @param surface Surface that we should register.
     * @return surface id that we associated with |surface|.
     */
    private static native int nativeRegisterSurface(Surface surface);

    /**
     * Unregister a surface.
     * @param surfaceId Id that was returned by registerSurface.
     */
    private static native void nativeUnregisterSurface(int surfaceId);
}

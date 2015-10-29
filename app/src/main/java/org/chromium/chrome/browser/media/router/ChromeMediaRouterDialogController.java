// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.router;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.MediaRouteChooserDialogFragment;
import android.support.v7.app.MediaRouteDialogFactory;
import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;
import android.support.v7.media.MediaRouter.Callback;

import org.chromium.base.ApplicationStatus;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.chrome.browser.media.remote.ChromeMediaRouteDialogFactory;
import org.chromium.chrome.browser.media.router.cast.MediaSink;
import org.chromium.chrome.browser.media.router.cast.MediaSource;

/**
 * Implements the JNI interface called from the C++ Media Router dialog controller implementation
 * on Android.
 */
@JNINamespace("media_router")
public class ChromeMediaRouterDialogController extends MediaRouter.Callback
        implements OnDismissListener {
    private static final String MEDIA_ROUTE_CHOOSER_DIALOG_FRAGMENT =
            "android.support.v7.mediarouter:MediaRouteChooserDialogFragment";

    private final long mNativeDialogController;
    private final MediaRouter mAndroidMediaRouter;
    private MediaRouteChooserDialogFragment mChooserDialogFragment;

    /**
     * Returns a new initialized {@link ChromeMediaRouterDialogController}.
     * @param nativeDialogController the handle of the native object.
     * @param context the application context.
     * @return a new dialog controller to use from the native side.
     */
    @CalledByNative
    public static ChromeMediaRouterDialogController create(
            long nativeDialogController, Context context) {
        return new ChromeMediaRouterDialogController(nativeDialogController, context);
    }

    /**
     * Shows the {@link MediaRouteChooserDialogFragment} dialog if it's not shown yet.
     * @param sourceUrn the URN identifying the media source to filter the devices with.
     */
    @CalledByNative
    public void createDialog(String sourceUrn) {
        if (isShowingDialog()) return;

        MediaSource mediaSource = MediaSource.from(sourceUrn);
        if (mediaSource == null) return;

        FragmentActivity currentActivity =
                (FragmentActivity) ApplicationStatus.getLastTrackedFocusedActivity();
        if (currentActivity == null) return;

        FragmentManager fm = currentActivity.getSupportFragmentManager();
        if (fm == null) return;

        if (fm.findFragmentByTag(MEDIA_ROUTE_CHOOSER_DIALOG_FRAGMENT) != null) return;

        MediaRouteSelector selector = mediaSource.buildRouteSelector();
        mAndroidMediaRouter.addCallback(selector, this);

        MediaRouteDialogFactory factory = new ChromeMediaRouteDialogFactory();
        mChooserDialogFragment = factory.onCreateChooserDialogFragment();
        mChooserDialogFragment.setRouteSelector(selector);
        mChooserDialogFragment.show(fm, MEDIA_ROUTE_CHOOSER_DIALOG_FRAGMENT);
        fm.executePendingTransactions();

        Dialog dialog = mChooserDialogFragment.getDialog();
        if (dialog == null) {
            closeDialog();
            return;
        }

        dialog.setOnDismissListener(this);
    }

    /**
     * Closes the currently open dialog if it's open.
     */
    @CalledByNative
    public void closeDialog() {
        if (!isShowingDialog()) return;

        // Will remove MediaRouter.Callback in onDismiss().
        mChooserDialogFragment.dismiss();
        mChooserDialogFragment = null;
    }

    /**
     * @return if the media route chooser dialog is currently open.
     */
    @CalledByNative
    public boolean isShowingDialog() {
        return mChooserDialogFragment != null && mChooserDialogFragment.isVisible();
    }

    /**
     * {@link Callback} implementation.
     */
    @Override
    public void onRouteSelected(MediaRouter router, MediaRouter.RouteInfo route) {
        closeDialog();
        nativeOnSinkSelected(mNativeDialogController, MediaSink.fromRoute(route).getId());
    }

    /**
     * {@link OnDismissListener} implementation.
     */
    @Override
    public void onDismiss(DialogInterface dialog) {
        mAndroidMediaRouter.removeCallback(this);
        mChooserDialogFragment.dismiss();
        mChooserDialogFragment = null;
        nativeOnDialogDismissed(mNativeDialogController);
    }

    private ChromeMediaRouterDialogController(long nativeDialogController, Context context) {
        mNativeDialogController = nativeDialogController;
        MediaRouter androidMediaRouter = null;
        try {
            // Pre-MR1 versions of JB do not have the complete MediaRouter APIs,
            // so getting the MediaRouter instance will throw an exception.
            androidMediaRouter = MediaRouter.getInstance(context);
        } catch (NoSuchMethodError e) {
            androidMediaRouter = null;
        }
        mAndroidMediaRouter = androidMediaRouter;
    }

    native void nativeOnDialogDismissed(long nativeMediaRouterDialogControllerAndroid);
    native void nativeOnSinkSelected(
            long nativeMediaRouterDialogControllerAndroid, String sinkId);
}

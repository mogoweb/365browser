// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.vr_shell;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.StrictMode;
import android.os.SystemClock;
import android.support.annotation.IntDef;
import android.view.Choreographer;
import android.view.Choreographer.FrameCallback;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.widget.FrameLayout;

import org.chromium.base.ActivityState;
import org.chromium.base.ApplicationStatus;
import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.base.library_loader.LibraryLoader;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.ChromeTabbedActivity;
import org.chromium.chrome.browser.IntentHandler;
import org.chromium.chrome.browser.customtabs.CustomTabActivity;
import org.chromium.chrome.browser.help.HelpAndFeedback;
import org.chromium.chrome.browser.infobar.InfoBarIdentifier;
import org.chromium.chrome.browser.infobar.SimpleConfirmInfoBarBuilder;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.util.IntentUtils;
import org.chromium.chrome.browser.webapps.WebappActivity;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * Manages interactions with the VR Shell.
 */
@JNINamespace("vr_shell")
public class VrShellDelegate implements ApplicationStatus.ActivityStateListener,
                                        View.OnSystemUiVisibilityChangeListener {
    private static final String TAG = "VrShellDelegate";
    // Pseudo-random number to avoid request id collisions.
    public static final int EXIT_VR_RESULT = 721251;

    private static final int ENTER_VR_NOT_NECESSARY = 0;
    private static final int ENTER_VR_CANCELLED = 1;
    private static final int ENTER_VR_REQUESTED = 2;
    private static final int ENTER_VR_SUCCEEDED = 3;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({ENTER_VR_NOT_NECESSARY, ENTER_VR_CANCELLED, ENTER_VR_REQUESTED, ENTER_VR_SUCCEEDED})
    private @interface EnterVRResult {}

    private static final int VR_NOT_AVAILABLE = 0;
    private static final int VR_CARDBOARD = 1;
    private static final int VR_DAYDREAM = 2; // Supports both Cardboard and Daydream viewer.

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({VR_NOT_AVAILABLE, VR_CARDBOARD, VR_DAYDREAM})
    private @interface VrSupportLevel {}

    private static final String DAYDREAM_VR_EXTRA = "android.intent.extra.VR_LAUNCH";
    private static final String DAYDREAM_HOME_PACKAGE = "com.google.android.vr.home";

    // Linter and formatter disagree on how the line below should be formatted.
    /* package */
    static final String VR_ENTRY_RESULT_ACTION =
            "org.chromium.chrome.browser.vr_shell.VrEntryResult";

    private static final long REENTER_VR_TIMEOUT_MS = 1000;

    private static final String FEEDBACK_REPORT_TYPE = "USER_INITIATED_FEEDBACK_REPORT_VR";

    private static final int VR_SYSTEM_UI_FLAGS = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

    private static final String VR_CORE_MARKET_URI =
            "market://details?id=" + VrCoreVersionChecker.VR_CORE_PACKAGE_ID;

    private static VrShellDelegate sInstance;
    private static VrBroadcastReceiver sVrBroadcastReceiver;
    private static boolean sRegisteredDaydreamHook = false;

    private ChromeActivity mActivity;

    @VrSupportLevel
    private int mVrSupportLevel;

    // How often to prompt the user to enter VR feedback.
    private int mFeedbackFrequency;

    private final VrClassesWrapper mVrClassesWrapper;
    private VrShell mVrShell;
    private NonPresentingGvrContext mNonPresentingGvrContext;
    private VrDaydreamApi mVrDaydreamApi;
    private Boolean mIsDaydreamCurrentViewer;
    private VrCoreVersionChecker mVrCoreVersionChecker;
    private TabModelSelector mTabModelSelector;

    private boolean mInVr;
    private final Handler mEnterVrHandler;

    // Whether or not the VR Device ON flow succeeded. If this is true it means the user has a VR
    // headset on, but we haven't switched into VR mode yet.
    // See further documentation here: https://developers.google.com/vr/daydream/guides/vr-entry
    private boolean mDonSucceeded;
    // Best effort whether or not the system was in VR when Chrome launched.
    private Boolean mInVrAtChromeLaunch;
    private boolean mShowingDaydreamDoff;
    private boolean mExitingCct;
    private boolean mPaused;
    private int mRestoreSystemUiVisibilityFlag = -1;
    private Integer mRestoreOrientation = null;
    private long mNativeVrShellDelegate;
    private boolean mRequestedWebVr;
    private long mLastVrExit;
    private boolean mListeningForWebVrActivate;
    private boolean mListeningForWebVrActivateBeforePause;
    // Whether or not we should autopresent WebVr. If this is set, it means that a first
    // party app has asked us to autopresent WebVr content and we're waiting for the WebVr
    // content to call requestPresent.
    private boolean mAutopresentWebVr;

    // Set to true if performed VR browsing at least once. That is, this was not simply a WebVr
    // presentation experience.
    private boolean mVrBrowserUsed;

    private View mOverlayView;

    private static final class VrBroadcastReceiver extends BroadcastReceiver {
        private final WeakReference<ChromeActivity> mTargetActivity;

        public VrBroadcastReceiver(ChromeActivity activity) {
            mTargetActivity = new WeakReference<ChromeActivity>(activity);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            ChromeActivity activity = mTargetActivity.get();
            if (activity == null) return;
            getInstance(activity);
            assert sInstance != null;
            if (sInstance == null) return;
            sInstance.mDonSucceeded = true;
            if (sInstance.mPaused) {
                if (sInstance.mInVrAtChromeLaunch == null) sInstance.mInVrAtChromeLaunch = false;
                // We add a black overlay view so that we can show black while the VR UI is loading.
                // Note that this alone isn't sufficient to prevent 2D UI from showing while
                // resuming the Activity, see the comment about the custom animation below.
                sInstance.addOverlayView();

                // We start the Activity with a custom animation that keeps it hidden for a few
                // hundred milliseconds - enough time for us to draw the first black view.
                // TODO(mthiesse): This is really hacky. If we can find a way to cancel the
                // transition animation (I couldn't), then we can just make it indefinite until the
                // VR UI is ready, and then cancel it, rather than trying to guess how long it will
                // take to draw the first view, and possibly adding latency to VR startup.
                Bundle options =
                        ActivityOptions.makeCustomAnimation(activity, R.anim.stay_hidden, 0)
                                .toBundle();
                ((ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE))
                        .moveTaskToFront(activity.getTaskId(), 0, options);
            } else {
                if (sInstance.mInVrAtChromeLaunch == null) sInstance.mInVrAtChromeLaunch = true;
                // If a WebVR app calls requestPresent in response to the displayactivate event
                // after the DON flow completes, the DON flow is skipped, meaning our app won't be
                // paused when daydream fires our BroadcastReceiver, so onResume won't be called.
                sInstance.handleDonFlowSuccess();
            }
        }

        /**
         * Unregisters this {@link BroadcastReceiver} from the activity it's registered to.
         */
        public void unregister() {
            ChromeActivity activity = mTargetActivity.get();
            if (activity == null) return;
            try {
                activity.unregisterReceiver(VrBroadcastReceiver.this);
            } catch (IllegalArgumentException e) {
                // Ignore this. This means our receiver was already unregistered somehow.
            }
        }
    }

    /**
     * Called when the native library is first available.
     */
    public static void onNativeLibraryAvailable() {
        // Check if VR classes are available before trying to use them. Note that the native
        // vr_shell_delegate.cc is compiled out of unsupported platforms (like x86).
        VrClassesWrapper wrapper = getVrClassesWrapper();
        if (wrapper == null) return;
        nativeOnLibraryAvailable();
    }

    @VisibleForTesting
    public static VrShellDelegate getInstanceForTesting() {
        return getInstance();
    }

    /**
     * Whether or not we are currently in VR.
     */
    public static boolean isInVr() {
        if (sInstance == null) return false;
        return sInstance.mInVr;
    }

    /**
     * See {@link ChromeActivity#handleBackPressed}
     * Only handles the back press while in VR.
     */
    public static boolean onBackPressed() {
        if (sInstance == null) return false;
        return sInstance.onBackPressedInternal();
    }

    /**
     * Enters VR on the current tab if possible.
     */
    public static void enterVrIfNecessary() {
        boolean created_delegate = sInstance == null;
        VrShellDelegate instance = getInstance();
        if (instance == null) return;
        if (instance.enterVrInternal() == ENTER_VR_CANCELLED && created_delegate) {
            instance.destroy();
        }
    }

    /**
     * Handles the result of the exit VR flow (DOFF).
     */
    public static void onExitVrResult(int resultCode) {
        if (sInstance == null) return;
        sInstance.onExitVrResult(resultCode == Activity.RESULT_OK);
    }

    /**
     * Returns the current {@VrSupportLevel}.
     */
    public static int getVrSupportLevel(VrDaydreamApi daydreamApi,
            VrCoreVersionChecker versionChecker, Tab tabToShowInfobarIn) {
        if (versionChecker == null || daydreamApi == null
                || !isVrCoreCompatible(versionChecker, tabToShowInfobarIn)) {
            return VR_NOT_AVAILABLE;
        }

        if (daydreamApi.isDaydreamReadyDevice()) return VR_DAYDREAM;

        return VR_CARDBOARD;
    }

    /**
     * If VR Shell is enabled, and the activity is supported, register with the Daydream
     * platform that this app would like to be launched in VR when the device enters VR.
     */
    public static void maybeRegisterVrEntryHook(final ChromeActivity activity) {
        // Daydream is not supported on pre-N devices.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;
        if (sInstance != null) return; // Will be handled in onResume.
        if (!activitySupportsVrBrowsing(activity)) return;

        // Reading VR support level and version can be slow, so do it asynchronously.
        new AsyncTask<Void, Void, VrDaydreamApi>() {
            @Override
            protected VrDaydreamApi doInBackground(Void... params) {
                VrClassesWrapper wrapper = getVrClassesWrapper();
                if (wrapper == null) return null;
                VrDaydreamApi api = wrapper.createVrDaydreamApi(activity);
                if (api == null) return null;
                int vrSupportLevel =
                        getVrSupportLevel(api, wrapper.createVrCoreVersionChecker(), null);
                if (!isVrShellEnabled(vrSupportLevel)) return null;
                return api;
            }

            @Override
            protected void onPostExecute(VrDaydreamApi api) {
                // Registering the daydream intent has to be done on the UI thread. Note that this
                // call is slow (~10ms at time of writing).
                if (api != null
                        && ApplicationStatus.getStateForActivity(activity)
                                == ActivityState.RESUMED) {
                    registerDaydreamIntent(api, activity);
                }
            }
        }
                .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * When the app is pausing we need to unregister with the Daydream platform to prevent this app
     * from being launched from the background when the device enters VR.
     */
    public static void maybeUnregisterVrEntryHook(ChromeActivity activity) {
        // Daydream is not supported on pre-N devices.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;
        if (sInstance != null) return; // Will be handled in onPause.
        if (!sRegisteredDaydreamHook) return;
        VrClassesWrapper wrapper = getVrClassesWrapper();
        if (wrapper == null) return;
        VrDaydreamApi api = wrapper.createVrDaydreamApi(activity);
        if (api == null) return;
        unregisterDaydreamIntent(api);
    }

    @CalledByNative
    private static VrShellDelegate getInstance() {
        Activity activity = ApplicationStatus.getLastTrackedFocusedActivity();
        if (!(activity instanceof ChromeActivity)) return null;
        return getInstance((ChromeActivity) activity);
    }

    private static VrShellDelegate getInstance(ChromeActivity activity) {
        if (!LibraryLoader.isInitialized()) return null;
        if (activity == null || !activitySupportsPresentation(activity)) return null;
        if (sInstance != null) return sInstance;
        VrClassesWrapper wrapper = getVrClassesWrapper();
        if (wrapper == null) return null;
        ThreadUtils.assertOnUiThread();
        sInstance = new VrShellDelegate(activity, wrapper);

        return sInstance;
    }

    private static boolean activitySupportsPresentation(Activity activity) {
        return activity instanceof ChromeTabbedActivity || activity instanceof CustomTabActivity
                || activity instanceof WebappActivity;
    }

    private static boolean activitySupportsAutopresentation(Activity activity) {
        return activity instanceof ChromeTabbedActivity;
    }

    private static boolean activitySupportsVrBrowsing(Activity activity) {
        if (activity instanceof ChromeTabbedActivity) return true;
        if (activity instanceof CustomTabActivity) {
            return ChromeFeatureList.isEnabled(ChromeFeatureList.VR_CUSTOM_TAB_BROWSING);
        }
        return false;
    }

    private static boolean activitySupportsExitFeedback(Activity activity) {
        return activity instanceof ChromeTabbedActivity
                && ChromeFeatureList.isEnabled(ChromeFeatureList.VR_BROWSING_FEEDBACK);
    }

    /**
     * @return A helper class for creating VR-specific classes that may not be available at compile
     * time.
     */
    private static VrClassesWrapper getVrClassesWrapper() {
        if (sInstance != null) return sInstance.mVrClassesWrapper;
        return createVrClassesWrapper();
    }

    @SuppressWarnings("unchecked")
    private static VrClassesWrapper createVrClassesWrapper() {
        try {
            Class<? extends VrClassesWrapper> vrClassesBuilderClass =
                    (Class<? extends VrClassesWrapper>) Class.forName(
                            "org.chromium.chrome.browser.vr_shell.VrClassesWrapperImpl");
            Constructor<?> vrClassesBuilderConstructor = vrClassesBuilderClass.getConstructor();
            return (VrClassesWrapper) vrClassesBuilderConstructor.newInstance();
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException
                | IllegalArgumentException | InvocationTargetException | NoSuchMethodException e) {
            if (!(e instanceof ClassNotFoundException)) {
                Log.e(TAG, "Unable to instantiate VrClassesWrapper", e);
            }
            return null;
        }
    }

    private static PendingIntent getEnterVrPendingIntent(ChromeActivity activity) {
        if (sVrBroadcastReceiver != null) sVrBroadcastReceiver.unregister();
        IntentFilter filter = new IntentFilter(VR_ENTRY_RESULT_ACTION);
        sVrBroadcastReceiver = new VrBroadcastReceiver(activity);
        activity.registerReceiver(sVrBroadcastReceiver, filter);

        Intent vrIntent = new Intent(VR_ENTRY_RESULT_ACTION);
        vrIntent.setPackage(activity.getPackageName());
        return PendingIntent.getBroadcast(activity, 0, vrIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_ONE_SHOT);
    }

    /**
     * Registers the Intent to fire after phone inserted into a headset.
     */
    private static void registerDaydreamIntent(
            final VrDaydreamApi daydreamApi, final ChromeActivity activity) {
        if (sRegisteredDaydreamHook) return;
        if (!daydreamApi.registerDaydreamIntent(getEnterVrPendingIntent(activity))) return;
        sRegisteredDaydreamHook = true;
    }

    /**
     * Unregisters the Intent which registered by this context if any.
     */
    private static void unregisterDaydreamIntent(VrDaydreamApi daydreamApi) {
        if (!sRegisteredDaydreamHook) return;
        daydreamApi.unregisterDaydreamIntent();
        sRegisteredDaydreamHook = false;
    }

    /**
     * @return Whether or not VR Shell is currently enabled.
     */
    private static boolean isVrShellEnabled(int vrSupportLevel) {
        // Only enable ChromeVR (VrShell) on Daydream devices as it currently needs a Daydream
        // controller.
        if (vrSupportLevel != VR_DAYDREAM) return false;
        return ChromeFeatureList.isEnabled(ChromeFeatureList.VR_SHELL);
    }

    private VrShellDelegate(ChromeActivity activity, VrClassesWrapper wrapper) {
        mActivity = activity;
        mVrClassesWrapper = wrapper;
        // If an activity isn't resumed at the point, it must have been paused.
        mPaused = ApplicationStatus.getStateForActivity(activity) != ActivityState.RESUMED;
        updateVrSupportLevel();
        mNativeVrShellDelegate = nativeInit();
        mFeedbackFrequency = VrFeedbackStatus.getFeedbackFrequency();
        mEnterVrHandler = new Handler();
        Choreographer.getInstance().postFrameCallback(new FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                if (mNativeVrShellDelegate == 0) return;
                Display display =
                        ((WindowManager) mActivity.getSystemService(Context.WINDOW_SERVICE))
                                .getDefaultDisplay();
                nativeUpdateVSyncInterval(
                        mNativeVrShellDelegate, frameTimeNanos, 1.0d / display.getRefreshRate());
            }
        });
        ApplicationStatus.registerStateListenerForAllActivities(this);
    }

    @Override
    public void onActivityStateChange(Activity activity, int newState) {
        switch (newState) {
            case ActivityState.DESTROYED:
                if (activity == mActivity) destroy();
                break;
            case ActivityState.PAUSED:
                if (activity == mActivity) pauseVr();
                // Other activities should only pause while we're paused due to Android lifecycle.
                assert mPaused;
                break;
            case ActivityState.STOPPED:
                if (activity == mActivity) cancelPendingVrEntry();
                break;
            case ActivityState.RESUMED:
                assert !mInVr || mShowingDaydreamDoff;
                if (mInVr && activity != mActivity) {
                    if (mShowingDaydreamDoff) {
                        onExitVrResult(true);
                    } else {
                        // We should never reach this state currently, but just in case...
                        assert false;
                        shutdownVr(true /* disableVrMode */, false /* canReenter */,
                                false /* stayingInChrome */);
                    }
                }
                if (!activitySupportsPresentation(activity)) return;
                swapHostActivity((ChromeActivity) activity);
                resumeVr();
                break;
            default:
                break;
        }
    }

    // Called when an activity that supports VR is resumed, and attaches VrShellDelegate to that
    // activity.
    private void swapHostActivity(ChromeActivity activity) {
        assert mActivity != null;
        mActivity = activity;
        mVrDaydreamApi = mVrClassesWrapper.createVrDaydreamApi(mActivity);
        if (mNativeVrShellDelegate == 0 || mNonPresentingGvrContext == null) return;
        shutdownNonPresentingNativeContext();
        nativeUpdateNonPresentingContext(
                mNativeVrShellDelegate, createNonPresentingNativeContext());
    }

    /**
     * Updates mVrSupportLevel to the correct value. isVrCoreCompatible might return different value
     * at runtime.
     */
    // TODO(bshe): Find a place to call this function again, i.e. page refresh or onResume.
    private void updateVrSupportLevel() {
        if (mVrClassesWrapper == null) {
            mVrSupportLevel = VR_NOT_AVAILABLE;
            return;
        }
        if (mVrCoreVersionChecker == null) {
            mVrCoreVersionChecker = mVrClassesWrapper.createVrCoreVersionChecker();
        }
        if (mVrDaydreamApi == null) {
            mVrDaydreamApi = mVrClassesWrapper.createVrDaydreamApi(mActivity);
        }
        mVrSupportLevel = getVrSupportLevel(
                mVrDaydreamApi, mVrCoreVersionChecker, mActivity.getActivityTab());
    }

    /**
     * Returns whether the device has support for Daydream.
     */
    /* package */ boolean hasDaydreamSupport() {
        return mVrSupportLevel == VR_DAYDREAM;
    }

    private void maybeSetPresentResult(boolean result) {
        if (mNativeVrShellDelegate != 0 && mRequestedWebVr) {
            nativeSetPresentResult(mNativeVrShellDelegate, result);
        }
        mRequestedWebVr = false;
    }

    /**
     * Handle a successful VR DON flow, entering VR in the process unless we're unable to.
     * @return False if VR entry failed.
     */
    private boolean enterVrAfterDon() {
        if (mNativeVrShellDelegate == 0) return false;

        // Normally, if the active page doesn't have a vrdisplayactivate listener, and WebVR was not
        // presenting and VrShell was not enabled, the Daydream Homescreen should show after the DON
        // flow. However, due to a failure in unregisterDaydreamIntent, we still try to enterVR, so
        // detect this case and fail to enter VR.
        if (!mListeningForWebVrActivateBeforePause && !mRequestedWebVr
                && !canEnterVr(mActivity.getActivityTab())) {
            return false;
        }

        if (mListeningForWebVrActivateBeforePause && !mRequestedWebVr) {
            nativeDisplayActivate(mNativeVrShellDelegate);
        }

        // If the page is listening for vrdisplayactivate we assume it wants to request
        // presentation. Go into WebVR mode tentatively. If the page doesn't request presentation
        // in the vrdisplayactivate handler we will exit presentation later.
        enterVr(mListeningForWebVrActivateBeforePause && !mRequestedWebVr);

        // The user has successfully completed a DON flow.
        RecordUserAction.record("VR.DON");

        return true;
    }

    private void enterVr(final boolean tentativeWebVrMode) {
        // We can't enter VR before the application resumes, or we encounter bizarre crashes
        // related to gpu surfaces.
        // TODO(mthiesse): Is the above comment still accurate? It may have been tied to our HTML
        // UI which is gone.
        assert !mPaused;
        if (mInVr) return;
        if (mNativeVrShellDelegate == 0) {
            cancelPendingVrEntry();
            return;
        }
        if (!isWindowModeCorrectForVr()) {
            setWindowModeForVr(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            mEnterVrHandler.post(new Runnable() {
                @Override
                public void run() {
                    enterVr(tentativeWebVrMode);
                }
            });
            return;
        }
        // We need to add VR UI asynchronously, or we get flashes of 2D content. Presumably this is
        // because adding the VR UI is slow and Android times out and decides to just show
        // something.
        mEnterVrHandler.post(new Runnable() {
            @Override
            public void run() {
                enterVrWithCorrectWindowMode(tentativeWebVrMode);
            }
        });
    }

    private void enterVrWithCorrectWindowMode(final boolean tentativeWebVrMode) {
        if (mInVr) return;
        if (mNativeVrShellDelegate == 0) {
            cancelPendingVrEntry();
            return;
        }
        if (!createVrShell()) {
            maybeSetPresentResult(false);
            mVrDaydreamApi.launchVrHomescreen();
            cancelPendingVrEntry();
            return;
        }
        mVrClassesWrapper.setVrModeEnabled(mActivity, true);
        mInVr = true;
        // Lock orientation to landscape after enter VR.
        mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        addVrViews();
        boolean webVrMode = mRequestedWebVr || tentativeWebVrMode;
        mVrShell.initializeNative(
                mActivity.getActivityTab(), webVrMode, mActivity instanceof CustomTabActivity);
        mVrShell.setWebVrModeEnabled(webVrMode);

        // We're entering VR, but not in WebVr mode.
        mVrBrowserUsed = !webVrMode;

        // onResume needs to be called on GvrLayout after initialization to make sure DON flow works
        // properly.
        if (!mPaused) mVrShell.resume();

        maybeSetPresentResult(true);
        mVrShell.getContainer().setOnSystemUiVisibilityChangeListener(this);
        removeOverlayView();
    }

    private boolean launchInVr() {
        assert mActivity != null && mVrSupportLevel != VR_NOT_AVAILABLE;
        return mVrDaydreamApi.launchInVr(getEnterVrPendingIntent(mActivity));
    }

    private void onAutopresentIntent() {
        // Autopresent intents are only expected from trusted first party apps while
        // we're not in vr.
        assert !mInVr;
        mAutopresentWebVr = true;
    }

    /**
     * This is called every time ChromeActivity gets a new intent.
     */
    public static void onNewIntent(Intent intent) {
        if (IntentUtils.safeGetBooleanExtra(intent, DAYDREAM_VR_EXTRA, false)
                && ChromeFeatureList.isEnabled(ChromeFeatureList.WEBVR_AUTOPRESENT)
                && activitySupportsAutopresentation(
                           ApplicationStatus.getLastTrackedFocusedActivity())
                && IntentHandler.isIntentFromTrustedApp(intent, DAYDREAM_HOME_PACKAGE)) {
            VrShellDelegate instance = getInstance();
            if (instance == null) return;
            instance.onAutopresentIntent();
        }
    }

    @Override
    public void onSystemUiVisibilityChange(int visibility) {
        if (mInVr && !isWindowModeCorrectForVr()) {
            setWindowModeForVr(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
    }

    private boolean isWindowModeCorrectForVr() {
        int flags = mActivity.getWindow().getDecorView().getSystemUiVisibility();
        int orientation = mActivity.getResources().getConfiguration().orientation;
        // Mask the flags to only those that we care about.
        return (flags & VR_SYSTEM_UI_FLAGS) == VR_SYSTEM_UI_FLAGS
                && orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private void setWindowModeForVr(int requestedOrientation) {
        if (mRestoreOrientation == null) {
            mRestoreOrientation = mActivity.getRequestedOrientation();
        }
        mActivity.setRequestedOrientation(requestedOrientation);
        setupVrModeWindowFlags();
    }

    private void restoreWindowMode() {
        if (mRestoreOrientation != null) mActivity.setRequestedOrientation(mRestoreOrientation);
        mRestoreOrientation = null;
        clearVrModeWindowFlags();
    }

    /* package */ boolean canEnterVr(Tab tab) {
        if (!LibraryLoader.isInitialized()) {
            return false;
        }
        if (mVrSupportLevel == VR_NOT_AVAILABLE || mNativeVrShellDelegate == 0) return false;
        // If vr shell is not enabled and this is not a web vr request, then return false.
        if (!isVrShellEnabled(mVrSupportLevel)
                && !(mRequestedWebVr || mListeningForWebVrActivate)) {
            return false;
        }
        // TODO(mthiesse): When we have VR UI for opening new tabs, etc., allow VR Shell to be
        // entered without any current tabs.
        if (tab == null) {
            return false;
        }
        // For now we don't handle sad tab page. crbug.com/661609
        if (tab.isShowingSadTab()) {
            return false;
        }
        return true;
    }

    @CalledByNative
    private void presentRequested() {
        mRequestedWebVr = true;
        mAutopresentWebVr = false;
        switch (enterVrInternal()) {
            case ENTER_VR_NOT_NECESSARY:
                mVrShell.setWebVrModeEnabled(true);
                maybeSetPresentResult(true);
                break;
            case ENTER_VR_CANCELLED:
                maybeSetPresentResult(false);
                break;
            case ENTER_VR_REQUESTED:
                break;
            case ENTER_VR_SUCCEEDED:
                maybeSetPresentResult(true);
                break;
            default:
                Log.e(TAG, "Unexpected enum.");
        }
    }

    /**
     * Enters VR Shell if necessary, displaying browser UI and tab contents in VR.
     */
    @EnterVRResult
    private int enterVrInternal() {
        // Update VR support level as it can change at runtime
        updateVrSupportLevel();
        if (mVrSupportLevel == VR_NOT_AVAILABLE) return ENTER_VR_CANCELLED;
        if (mInVr) return ENTER_VR_NOT_NECESSARY;
        if (!canEnterVr(mActivity.getActivityTab())) return ENTER_VR_CANCELLED;

        if (mVrSupportLevel == VR_CARDBOARD || !isDaydreamCurrentViewer()) {
            // Avoid using launchInVr which would trigger DON flow regardless current viewer type
            // due to the lack of support for unexported activities.
            enterVr(false);
        } else {
            // LANDSCAPE orientation is needed before we can safely enter VR. DON can make sure that
            // the device is at LANDSCAPE orientation once it is finished. So here we use SENSOR to
            // avoid forcing LANDSCAPE orientation in order to have a smoother transition.
            setWindowModeForVr(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
            if (!launchInVr()) {
                restoreWindowMode();
                return ENTER_VR_CANCELLED;
            }
        }
        return ENTER_VR_REQUESTED;
    }

    @CalledByNative
    private boolean exitWebVRPresent() {
        if (!mInVr) return false;
        if (!isVrShellEnabled(mVrSupportLevel) || !isDaydreamCurrentViewer()
                || !activitySupportsVrBrowsing(mActivity)) {
            if (isDaydreamCurrentViewer()
                    && mVrDaydreamApi.exitFromVr(EXIT_VR_RESULT, new Intent())) {
                mShowingDaydreamDoff = true;
                return false;
            }
            shutdownVr(
                    true /* disableVrMode */, false /* canReenter */, true /* stayingInChrome */);
        } else {
            mVrBrowserUsed = true;
            mVrShell.setWebVrModeEnabled(false);
        }
        return true;
    }

    private void resumeVr() {
        mPaused = false;

        assert !mInVr || mShowingDaydreamDoff;

        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskWrites();
        try {
            if (mNativeVrShellDelegate != 0) nativeOnResume(mNativeVrShellDelegate);
        } finally {
            StrictMode.setThreadPolicy(oldPolicy);
        }

        if (mVrSupportLevel != VR_DAYDREAM) return;
        if (isVrShellEnabled(mVrSupportLevel) && activitySupportsVrBrowsing(mActivity)) {
            // registerDaydreamIntent is slow, so run it after resuming.
            new Handler().post(new Runnable() {
                @Override
                public void run() {
                    if (!mPaused) registerDaydreamIntent(mVrDaydreamApi, mActivity);
                }
            });
        }

        if (mInVr) {
            mVrShell.resume();
            return;
        }

        // This handles the case where we're already in VR, and an NFC scan is received that pauses
        // and resumes Chrome without going through the DON flow or firing the DON success intent.
        if (isDaydreamCurrentViewer()
                && mLastVrExit + REENTER_VR_TIMEOUT_MS > SystemClock.uptimeMillis()) {
            mDonSucceeded = true;
        }

        if (mDonSucceeded) {
            handleDonFlowSuccess();
        } else if (mRestoreOrientation != null) {
            // This means the user backed out of the DON flow, and we won't be entering VR.
            maybeSetPresentResult(false);
            restoreWindowMode();
        }
    }

    private void handleDonFlowSuccess() {
        mDonSucceeded = false;
        // If we fail to enter VR when we should have entered VR, return to the home screen.
        if (!enterVrAfterDon()) {
            cancelPendingVrEntry();
            maybeSetPresentResult(false);
            mVrDaydreamApi.launchVrHomescreen();
        }
    }

    private void pauseVr() {
        mPaused = true;
        unregisterDaydreamIntent(mVrDaydreamApi);
        if (mVrSupportLevel == VR_NOT_AVAILABLE) return;

        cancelPendingVrEntry();

        // When the active web page has a vrdisplayactivate event handler,
        // mListeningForWebVrActivate should be set to true, which means a vrdisplayactive event
        // should be fired once DON flow finished. However, DON flow will pause our activity,
        // which makes the active page becomes invisible. And the event fires before the active
        // page becomes visible again after DON finished. So here we remember the value of
        // mListeningForWebVrActivity before pause and use this value to decide if
        // vrdisplayactivate event should be dispatched in enterVRFromIntent.
        mListeningForWebVrActivateBeforePause = mListeningForWebVrActivate;

        if (mNativeVrShellDelegate != 0) nativeOnPause(mNativeVrShellDelegate);

        if (mShowingDaydreamDoff) {
            mVrShell.pause();
            return;
        }

        // TODO(mthiesse): When VR Shell lives in its own activity, and integrates with Daydream
        // home, pause instead of exiting VR here. For now, because VR Apps shouldn't show up in the
        // non-VR recents, and we don't want ChromeTabbedActivity disappearing, exit VR.
        shutdownVr(true /* disableVrMode */, true /* canReenter */, false /* stayingInChrome */);
        mIsDaydreamCurrentViewer = null;
    }

    private boolean onBackPressedInternal() {
        if (mVrSupportLevel == VR_NOT_AVAILABLE) return false;
        cancelPendingVrEntry();
        if (!mInVr) return false;
        shutdownVr(true /* disableVrMode */, false /* canReenter */, true /* stayingInChrome */);
        return true;
    }

    private void onExitVrResult(boolean success) {
        assert mVrSupportLevel != VR_NOT_AVAILABLE;

        // We may have manually handled the exit early by swapping to another Chrome activity that
        // supports VR while in the DOFF activity. If that happens we want to exit early when the
        // real DOFF flow calls us back.
        if (!mShowingDaydreamDoff) return;

        // For now, we don't handle re-entering VR when exit fails, so keep trying to exit.
        if (!success && mVrDaydreamApi.exitFromVr(EXIT_VR_RESULT, new Intent())) return;

        mShowingDaydreamDoff = false;

        shutdownVr(true /* disableVrMode */, false /* canReenter */,
                !mExitingCct /* stayingInChrome */);
        if (mExitingCct) ((CustomTabActivity) mActivity).finishAndClose(false);
        mExitingCct = false;
    }

    private boolean isDaydreamCurrentViewer() {
        if (mIsDaydreamCurrentViewer == null) {
            mIsDaydreamCurrentViewer = mVrDaydreamApi.isDaydreamCurrentViewer();
        }
        return mIsDaydreamCurrentViewer;
    }

    @CalledByNative
    private long createNonPresentingNativeContext() {
        if (mVrClassesWrapper == null) return 0;
        // Update VR support level as it can change at runtime
        updateVrSupportLevel();
        if (mVrSupportLevel == VR_NOT_AVAILABLE) return 0;
        mNonPresentingGvrContext = mVrClassesWrapper.createNonPresentingGvrContext(mActivity);
        if (mNonPresentingGvrContext == null) return 0;
        return mNonPresentingGvrContext.getNativeGvrContext();
    }

    @CalledByNative
    private void shutdownNonPresentingNativeContext() {
        if (mNonPresentingGvrContext == null) return;
        mNonPresentingGvrContext.shutdown();
        mNonPresentingGvrContext = null;
    }

    @CalledByNative
    private void setListeningForWebVrActivate(boolean listening) {
        // Non-Daydream devices may not have the concept of display activate. So disable
        // mListeningForWebVrActivate for them.
        if (mVrSupportLevel != VR_DAYDREAM) return;
        mListeningForWebVrActivate = listening;
        if (mPaused) return;
        if (listening) {
            registerDaydreamIntent(mVrDaydreamApi, mActivity);
            if (mAutopresentWebVr) {
                // Dispatch vrdisplayactivate so that the WebVr page can call requestPresent
                // to start presentation.
                // TODO(ymalik): There will be a delay between when we're asked to autopresent and
                // when the WebVr site calls requestPresent. In this time, the user sees 2D Chrome
                // UI which is suboptimal.
                nativeDisplayActivate(mNativeVrShellDelegate);
            }
        } else if (!canEnterVr(mActivity.getActivityTab())) {
            unregisterDaydreamIntent(mVrDaydreamApi);
        }
    }

    private void cancelPendingVrEntry() {
        // Ensure we can't asynchronously enter VR after trying to exit it.
        mEnterVrHandler.removeCallbacksAndMessages(null);
        mDonSucceeded = false;
        removeOverlayView();
    }

    /**
     * Exits VR Shell, performing all necessary cleanup.
     */
    /* package */ void shutdownVr(
            boolean disableVrMode, boolean canReenter, boolean stayingInChrome) {
        cancelPendingVrEntry();
        if (!mInVr) return;
        if (mShowingDaydreamDoff) {
            onExitVrResult(true);
            return;
        }
        mInVr = false;
        mRequestedWebVr = false;
        mAutopresentWebVr = false;
        mLastVrExit = canReenter ? SystemClock.uptimeMillis() : 0;

        // The user has exited VR.
        RecordUserAction.record("VR.DOFF");

        restoreWindowMode();
        mVrShell.pause();
        removeVrViews();
        destroyVrShell();
        if (disableVrMode) mVrClassesWrapper.setVrModeEnabled(mActivity, false);

        promptForFeedbackIfNeeded(stayingInChrome);
    }

    /* package */ void showDoffAndExitVr() {
        if (mShowingDaydreamDoff) return;
        if (mVrDaydreamApi.exitFromVr(EXIT_VR_RESULT, new Intent())) {
            mShowingDaydreamDoff = true;
            return;
        }
        shutdownVr(true /* disableVrMode */, false /* canReenter */, true /* stayingInChrome */);
    }

    /* package */ void exitCct() {
        if (mShowingDaydreamDoff) return;
        assert mActivity instanceof CustomTabActivity;
        if (mInVrAtChromeLaunch != null && !mInVrAtChromeLaunch) {
            if (mVrDaydreamApi.exitFromVr(EXIT_VR_RESULT, new Intent())) {
                mExitingCct = true;
                mShowingDaydreamDoff = true;
                return;
            }
        }
    }

    private static void startFeedback(Tab tab) {
        // TODO(ymalik): This call will connect to the Google Services api which can be slow. Can we
        // connect to it beforehand when we know that we'll be prompting for feedback?
        HelpAndFeedback.getInstance(tab.getActivity())
                .showFeedback(tab.getActivity(), tab.getProfile(), tab.getUrl(),
                        ContextUtils.getApplicationContext().getPackageName() + "."
                                + FEEDBACK_REPORT_TYPE);
    }

    private static void promptForFeedback(final Tab tab) {
        final ChromeActivity activity = tab.getActivity();
        SimpleConfirmInfoBarBuilder.Listener listener = new SimpleConfirmInfoBarBuilder.Listener() {
            @Override
            public void onInfoBarDismissed() {}

            @Override
            public boolean onInfoBarButtonClicked(boolean isPrimary) {
                if (isPrimary) {
                    startFeedback(tab);
                } else {
                    VrFeedbackStatus.setFeedbackOptOut(true);
                }
                return false;
            }
        };

        SimpleConfirmInfoBarBuilder.create(tab, listener,
                InfoBarIdentifier.VR_FEEDBACK_INFOBAR_ANDROID, R.drawable.vr_services,
                activity.getString(R.string.vr_shell_feedback_infobar_description),
                activity.getString(R.string.vr_shell_feedback_infobar_feedback_button),
                activity.getString(R.string.no_thanks), true /* autoExpire  */);
    }

    /**
     * Prompts the user to enter feedback for their VR Browsing experience.
     */
    private void promptForFeedbackIfNeeded(boolean stayingInChrome) {
        // We only prompt for feedback if:
        // 1) The user hasn't explicitly opted-out of it in the past
        // 2) The user has performed VR browsing
        // 3) The user is exiting VR and going back into 2D Chrome
        // 4) Every n'th visit (where n = mFeedbackFrequency)

        if (!activitySupportsExitFeedback(mActivity)) return;
        if (!stayingInChrome) return;
        if (VrFeedbackStatus.getFeedbackOptOut()) return;
        if (!mVrBrowserUsed) return;

        int exitCount = VrFeedbackStatus.getUserExitedAndEntered2DCount();
        VrFeedbackStatus.setUserExitedAndEntered2DCount((exitCount + 1) % mFeedbackFrequency);

        if (exitCount > 0) return;

        promptForFeedback(mActivity.getActivityTab());
    }

    private static boolean isVrCoreCompatible(
            VrCoreVersionChecker versionChecker, Tab tabToShowInfobarIn) {
        int vrCoreCompatibility = versionChecker.getVrCoreCompatibility();

        if (vrCoreCompatibility == VrCoreVersionChecker.VR_NOT_AVAILABLE
                || vrCoreCompatibility == VrCoreVersionChecker.VR_OUT_OF_DATE) {
            promptToUpdateVrServices(vrCoreCompatibility, tabToShowInfobarIn);
        }

        return vrCoreCompatibility == VrCoreVersionChecker.VR_READY;
    }

    private static void promptToUpdateVrServices(int vrCoreCompatibility, Tab tab) {
        if (tab == null) {
            return;
        }
        final Activity activity = tab.getActivity();
        String infobarText;
        String buttonText;
        if (vrCoreCompatibility == VrCoreVersionChecker.VR_NOT_AVAILABLE) {
            // Supported, but not installed. Ask user to install instead of upgrade.
            infobarText = activity.getString(R.string.vr_services_check_infobar_install_text);
            buttonText = activity.getString(R.string.vr_services_check_infobar_install_button);
        } else if (vrCoreCompatibility == VrCoreVersionChecker.VR_OUT_OF_DATE) {
            infobarText = activity.getString(R.string.vr_services_check_infobar_update_text);
            buttonText = activity.getString(R.string.vr_services_check_infobar_update_button);
        } else {
            Log.e(TAG, "Unknown VrCore compatibility: " + vrCoreCompatibility);
            return;
        }

        SimpleConfirmInfoBarBuilder.Listener listener = new SimpleConfirmInfoBarBuilder.Listener() {
            @Override
            public void onInfoBarDismissed() {}

            @Override
            public boolean onInfoBarButtonClicked(boolean isPrimary) {
                activity.startActivity(
                        new Intent(Intent.ACTION_VIEW, Uri.parse(VR_CORE_MARKET_URI)));
                return false;
            }
        };
        SimpleConfirmInfoBarBuilder.create(tab, listener,
                InfoBarIdentifier.VR_SERVICES_UPGRADE_ANDROID, R.drawable.vr_services, infobarText,
                buttonText, null, true);
    }

    private boolean createVrShell() {
        assert mVrShell == null;
        if (mVrClassesWrapper == null) return false;
        if (mActivity.getCompositorViewHolder() == null) return false;
        mTabModelSelector = mActivity.getCompositorViewHolder().detachForVr();
        if (mTabModelSelector == null) return false;
        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskWrites();
        try {
            mVrShell = mVrClassesWrapper.createVrShell(mActivity, this, mTabModelSelector);
        } finally {
            StrictMode.setThreadPolicy(oldPolicy);
        }
        return mVrShell != null;
    }

    private void addVrViews() {
        FrameLayout decor = (FrameLayout) mActivity.getWindow().getDecorView();
        LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        decor.addView(mVrShell.getContainer(), params);
        mActivity.onEnterVr();
    }

    private void removeVrViews() {
        mActivity.onExitVr();
        FrameLayout decor = (FrameLayout) mActivity.getWindow().getDecorView();
        decor.removeView(mVrShell.getContainer());
    }

    private void setupVrModeWindowFlags() {
        if (mRestoreSystemUiVisibilityFlag == -1) {
            mRestoreSystemUiVisibilityFlag = mActivity.getWindow().getDecorView()
                    .getSystemUiVisibility();
        }
        mActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mActivity.getWindow().getDecorView().setSystemUiVisibility(VR_SYSTEM_UI_FLAGS);
    }

    private void clearVrModeWindowFlags() {
        mActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (mRestoreSystemUiVisibilityFlag != -1) {
            mActivity.getWindow().getDecorView()
                    .setSystemUiVisibility(mRestoreSystemUiVisibilityFlag);
        }
        mRestoreSystemUiVisibilityFlag = -1;
    }

    private void addOverlayView() {
        if (mOverlayView != null) return;
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        FrameLayout decor = (FrameLayout) mActivity.getWindow().getDecorView();
        mOverlayView = new View(mActivity);
        mOverlayView.setBackgroundColor(Color.BLACK);
        decor.addView(mOverlayView, -1, params);
    }

    private void removeOverlayView() {
        if (mOverlayView == null) return;
        FrameLayout decor = (FrameLayout) sInstance.mActivity.getWindow().getDecorView();
        decor.removeView(mOverlayView);
        mOverlayView = null;
    }

    /**
     * Clean up VrShell, and associated native objects.
     */
    private void destroyVrShell() {
        if (mVrShell != null) {
            mVrShell.getContainer().setOnSystemUiVisibilityChangeListener(null);
            mVrShell.teardown();
            mVrShell = null;
            if (mActivity.getCompositorViewHolder() != null) {
                mActivity.getCompositorViewHolder().onExitVr(mTabModelSelector);
            }
            mTabModelSelector = null;
        }
    }

    /**
     * @param api The VrDaydreamApi object this delegate will use instead of the default one
     */
    @VisibleForTesting
    public void overrideDaydreamApiForTesting(VrDaydreamApi api) {
        mVrDaydreamApi = api;
    }

    /**
     * @return The VrShell for the VrShellDelegate instance
     */
    @VisibleForTesting
    public static VrShell getVrShellForTesting() {
        return sInstance == null ? null : sInstance.mVrShell;
    }

    /**
     * @param versionChecker The VrCoreVersionChecker object this delegate will use
     */
    @VisibleForTesting
    public void overrideVrCoreVersionCheckerForTesting(VrCoreVersionChecker versionChecker) {
        mVrCoreVersionChecker = versionChecker;
    }

    /**
     * @param frequency Sets how often to show the feedback prompt.
     */
    @VisibleForTesting
    public void setFeedbackFrequencyForTesting(int frequency) {
        mFeedbackFrequency = frequency;
    }

    /**
     * @return Pointer to the native VrShellDelegate object.
     */
    @CalledByNative
    private long getNativePointer() {
        return mNativeVrShellDelegate;
    }

    private void destroy() {
        if (sInstance == null) return;
        shutdownVr(false /* disableVrMode */, false /* canReenter */, false /* stayingInChrome */);
        if (mNativeVrShellDelegate != 0) nativeDestroy(mNativeVrShellDelegate);
        mNativeVrShellDelegate = 0;
        ApplicationStatus.unregisterActivityStateListener(this);
        sInstance = null;
    }

    private native long nativeInit();
    private static native void nativeOnLibraryAvailable();
    private native void nativeSetPresentResult(long nativeVrShellDelegate, boolean result);
    private native void nativeDisplayActivate(long nativeVrShellDelegate);
    private native void nativeUpdateVSyncInterval(long nativeVrShellDelegate, long timebaseNanos,
            double intervalSeconds);
    private native void nativeOnPause(long nativeVrShellDelegate);
    private native void nativeOnResume(long nativeVrShellDelegate);
    private native void nativeUpdateNonPresentingContext(long nativeVrShellDelegate, long context);
    private native void nativeDestroy(long nativeVrShellDelegate);
}

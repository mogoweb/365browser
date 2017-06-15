// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.ui.base;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PermissionInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Process;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.View;

import org.chromium.base.ActivityState;
import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.ApplicationStatus;
import org.chromium.base.BuildInfo;
import org.chromium.base.Callback;
import org.chromium.base.ContextUtils;
import org.chromium.ui.UiUtils;

import java.lang.ref.WeakReference;

/**
 * The class provides the WindowAndroid's implementation which requires
 * Activity Instance.
 * Only instantiate this class when you need the implemented features.
 */
public class ActivityWindowAndroid
        extends WindowAndroid
        implements ApplicationStatus.ActivityStateListener, View.OnLayoutChangeListener {
    // Constants used for intent request code bounding.
    private static final int REQUEST_CODE_PREFIX = 1000;
    private static final int REQUEST_CODE_RANGE_SIZE = 100;

    private static final String PERMISSION_QUERIED_KEY_PREFIX = "HasRequestedAndroidPermission::";

    private final Handler mHandler;
    private final SparseArray<PermissionCallback> mOutstandingPermissionRequests;

    private int mNextRequestCode;

    /**
     * Creates an Activity-specific WindowAndroid with associated intent functionality.
     * TODO(jdduke): Remove this overload when all callsites have been updated to
     * indicate their activity state listening preference.
     * @param context Context wrapping an activity associated with the WindowAndroid.
     */
    public ActivityWindowAndroid(Context context) {
        this(context, true);
    }

    /**
     * Creates an Activity-specific WindowAndroid with associated intent functionality.
     * @param context Context wrapping an activity associated with the WindowAndroid.
     * @param listenToActivityState Whether to listen to activity state changes.
     */
    public ActivityWindowAndroid(Context context, boolean listenToActivityState) {
        super(context);
        Activity activity = activityFromContext(context);
        if (activity == null) {
            throw new IllegalArgumentException("Context is not and does not wrap an Activity");
        }
        mHandler = new Handler();
        mOutstandingPermissionRequests = new SparseArray<PermissionCallback>();
        if (listenToActivityState) {
            ApplicationStatus.registerStateListenerForActivity(this, activity);
        }

        setAndroidPermissionDelegate(new ActivityAndroidPermissionDelegate());
    }

    @Override
    protected void registerKeyboardVisibilityCallbacks() {
        Activity activity = getActivity().get();
        if (activity == null) return;
        View content = activity.findViewById(android.R.id.content);
        mIsKeyboardShowing = UiUtils.isKeyboardShowing(getActivity().get(), content);
        content.addOnLayoutChangeListener(this);
    }

    @Override
    protected void unregisterKeyboardVisibilityCallbacks() {
        Activity activity = getActivity().get();
        if (activity == null) return;
        activity.findViewById(android.R.id.content).removeOnLayoutChangeListener(this);
    }

    @Override
    public int showCancelableIntent(
            PendingIntent intent, IntentCallback callback, Integer errorId) {
        Activity activity = getActivity().get();
        if (activity == null) return START_INTENT_FAILURE;

        int requestCode = generateNextRequestCode();

        try {
            activity.startIntentSenderForResult(
                    intent.getIntentSender(), requestCode, new Intent(), 0, 0, 0);
        } catch (SendIntentException e) {
            return START_INTENT_FAILURE;
        }

        storeCallbackData(requestCode, callback, errorId);
        return requestCode;
    }

    @Override
    public int showCancelableIntent(Intent intent, IntentCallback callback, Integer errorId) {
        Activity activity = getActivity().get();
        if (activity == null) return START_INTENT_FAILURE;

        int requestCode = generateNextRequestCode();

        try {
            activity.startActivityForResult(intent, requestCode);
        } catch (ActivityNotFoundException e) {
            return START_INTENT_FAILURE;
        }

        storeCallbackData(requestCode, callback, errorId);
        return requestCode;
    }

    @Override
    public int showCancelableIntent(Callback<Integer> intentTrigger, IntentCallback callback,
            Integer errorId) {
        Activity activity = getActivity().get();
        if (activity == null) return START_INTENT_FAILURE;

        int requestCode = generateNextRequestCode();

        intentTrigger.onResult(requestCode);

        storeCallbackData(requestCode, callback, errorId);
        return requestCode;
    }

    @Override
    public void cancelIntent(int requestCode) {
        Activity activity = getActivity().get();
        if (activity == null) return;
        activity.finishActivity(requestCode);
    }

    /**
     * Responds to the intent result if the intent was created by the native window.
     * @param requestCode Request code of the requested intent.
     * @param resultCode Result code of the requested intent.
     * @param data The data returned by the intent.
     * @return Boolean value of whether the intent was started by the native window.
     */
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentCallback callback = mOutstandingIntents.get(requestCode);
        mOutstandingIntents.delete(requestCode);
        String errorMessage = mIntentErrors.remove(requestCode);

        if (callback != null) {
            callback.onIntentCompleted(this, resultCode, data);
            return true;
        } else {
            if (errorMessage != null) {
                showCallbackNonExistentError(errorMessage);
                return true;
            }
        }
        return false;
    }

    private String getHasRequestedPermissionKey(String permission) {
        String permissionQueriedKey = permission;
        // Prior to O, permissions were granted at the group level.  Post O, each permission is
        // granted individually.
        if (!BuildInfo.isAtLeastO()) {
            try {
                // Runtime permissions are controlled at the group level.  So when determining
                // whether we have requested a particular permission before, we should check whether
                // we have requested any permission in that group as that mimics the logic in the
                // Android framework.
                //
                // e.g. Requesting first the permission ACCESS_FINE_LOCATION will result in Chrome
                //      treating ACCESS_COARSE_LOCATION as if it had already been requested as well.
                PermissionInfo permissionInfo =
                        getApplicationContext().getPackageManager().getPermissionInfo(
                                permission, PackageManager.GET_META_DATA);

                if (!TextUtils.isEmpty(permissionInfo.group)) {
                    permissionQueriedKey = permissionInfo.group;
                }
            } catch (NameNotFoundException e) {
                // Unknown permission.  Default back to the permission name instead of the group.
            }
        }

        return PERMISSION_QUERIED_KEY_PREFIX + permissionQueriedKey;
    }

    /**
     * Responds to a pending permission result.
     * @param requestCode The unique code for the permission request.
     * @param permissions The list of permissions in the result.
     * @param grantResults Whether the permissions were granted.
     * @return Whether the permission request corresponding to a pending permission request.
     */
    public boolean onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        Activity activity = getActivity().get();
        assert activity != null;

        SharedPreferences.Editor editor = ContextUtils.getAppSharedPreferences().edit();
        for (int i = 0; i < permissions.length; i++) {
            editor.putBoolean(getHasRequestedPermissionKey(permissions[i]), true);
        }
        editor.apply();

        PermissionCallback callback = mOutstandingPermissionRequests.get(requestCode);
        mOutstandingPermissionRequests.delete(requestCode);
        if (callback == null) return false;
        callback.onRequestPermissionsResult(permissions, grantResults);
        return true;
    }

    @Override
    public WeakReference<Activity> getActivity() {
        return new WeakReference<Activity>(activityFromContext(getContext().get()));
    }

    @Override
    public void onActivityStateChange(Activity activity, int newState) {
        if (newState == ActivityState.STOPPED) {
            onActivityStopped();
        } else if (newState == ActivityState.STARTED) {
            onActivityStarted();
        }
    }

    @Override
    public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft,
            int oldTop, int oldRight, int oldBottom) {
        keyboardVisibilityPossiblyChanged(UiUtils.isKeyboardShowing(getActivity().get(), v));
    }

    private int generateNextRequestCode() {
        int requestCode = REQUEST_CODE_PREFIX + mNextRequestCode;
        mNextRequestCode = (mNextRequestCode + 1) % REQUEST_CODE_RANGE_SIZE;
        return requestCode;
    }

    private void storeCallbackData(int requestCode, IntentCallback callback, Integer errorId) {
        mOutstandingIntents.put(requestCode, callback);
        mIntentErrors.put(
                requestCode, errorId == null ? null : mApplicationContext.getString(errorId));
    }

    private class ActivityAndroidPermissionDelegate implements AndroidPermissionDelegate {
        @Override
        public boolean hasPermission(String permission) {
            return ApiCompatibilityUtils.checkPermission(
                    mApplicationContext, permission, Process.myPid(), Process.myUid())
                    == PackageManager.PERMISSION_GRANTED;
        }

        @Override
        public boolean canRequestPermission(String permission) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false;

            Activity activity = getActivity().get();
            if (activity == null) return false;

            if (isPermissionRevokedByPolicy(permission)) {
                return false;
            }

            if (activity.shouldShowRequestPermissionRationale(permission)) {
                return true;
            }

            // Check whether we have ever asked for this permission by checking whether we saved
            // a preference associated with it before.
            String permissionQueriedKey = getHasRequestedPermissionKey(permission);
            SharedPreferences prefs = ContextUtils.getAppSharedPreferences();
            if (!prefs.getBoolean(permissionQueriedKey, false)) return true;

            return false;
        }

        @Override
        public boolean isPermissionRevokedByPolicy(String permission) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false;

            Activity activity = getActivity().get();
            if (activity == null) return false;

            return activity.getPackageManager().isPermissionRevokedByPolicy(
                    permission, activity.getPackageName());
        }

        @Override
        public void requestPermissions(
                final String[] permissions, final PermissionCallback callback) {
            if (requestPermissionsInternal(permissions, callback)) return;

            // If the permission request was not sent successfully, just post a response to the
            // callback with whatever the current permission state is for all the requested
            // permissions.  The response is posted to keep the async behavior of this method
            // consistent.
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    int[] results = new int[permissions.length];
                    for (int i = 0; i < permissions.length; i++) {
                        results[i] = hasPermission(permissions[i])
                                ? PackageManager.PERMISSION_GRANTED
                                : PackageManager.PERMISSION_DENIED;
                    }
                    callback.onRequestPermissionsResult(permissions, results);
                }
            });
        }

        /**
         * Issues the permission request and returns whether it was sent successfully.
         */
        private boolean requestPermissionsInternal(
                String[] permissions, PermissionCallback callback) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false;
            Activity activity = getActivity().get();
            if (activity == null) return false;

            int requestCode = generateNextRequestCode();
            mOutstandingPermissionRequests.put(requestCode, callback);
            activity.requestPermissions(permissions, requestCode);
            return true;
        }
    }
}

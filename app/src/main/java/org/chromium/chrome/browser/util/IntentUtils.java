// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.util;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcelable;

import org.chromium.base.Log;
import org.chromium.base.VisibleForTesting;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Utilities dealing with extracting information from intents.
 */
public class IntentUtils {
    private static final String TAG = "IntentUtils";

    /**
     * Retrieves a list of components that would handle the given intent.
     * @param context The application context.
     * @param intent The intent which we are interested in.
     * @return The list of component names.
     */
    public static List<ComponentName> getIntentHandlers(Context context, Intent intent) {
        List<ResolveInfo> list = context.getPackageManager().queryIntentActivities(intent, 0);
        List<ComponentName> nameList = new ArrayList<ComponentName>();
        for (ResolveInfo r : list) {
            nameList.add(new ComponentName(r.activityInfo.packageName, r.activityInfo.name));
        }
        return nameList;
    }

    /**
     * Just like {@link Intent#getBooleanExtra(String, boolean)} but doesn't throw exceptions.
     */
    public static boolean safeGetBooleanExtra(Intent intent, String name, boolean defaultValue) {
        try {
            return intent.getBooleanExtra(name, defaultValue);
        } catch (Throwable t) {
            // Catches un-parceling exceptions.
            Log.e(TAG, "getBooleanExtra failed on intent " + intent);
            return defaultValue;
        }
    }

    /**
     * Just like {@link Intent#getIntExtra(String, int)} but doesn't throw exceptions.
     */
    public static int safeGetIntExtra(Intent intent, String name, int defaultValue) {
        try {
            return intent.getIntExtra(name, defaultValue);
        } catch (Throwable t) {
            // Catches un-parceling exceptions.
            Log.e(TAG, "getIntExtra failed on intent " + intent);
            return defaultValue;
        }
    }

    /**
     * Just like {@link Intent#getLongExtra(String, long)} but doesn't throw exceptions.
     */
    public static long safeGetLongExtra(Intent intent, String name, long defaultValue) {
        try {
            return intent.getLongExtra(name, defaultValue);
        } catch (Throwable t) {
            // Catches un-parceling exceptions.
            Log.e(TAG, "getLongExtra failed on intent " + intent);
            return defaultValue;
        }
    }

    /**
     * Just like {@link Intent#getStringExtra(String)} but doesn't throw exceptions.
     */
    public static String safeGetStringExtra(Intent intent, String name) {
        try {
            return intent.getStringExtra(name);
        } catch (Throwable t) {
            // Catches un-parceling exceptions.
            Log.e(TAG, "getStringExtra failed on intent " + intent);
            return null;
        }
    }

    /**
     * Just like {@link Bundle#getString(String)} but doesn't throw exceptions.
     */
    public static String safeGetString(Bundle bundle, String name) {
        try {
            return bundle.getString(name);
        } catch (Throwable t) {
            // Catches un-parceling exceptions.
            Log.e(TAG, "getString failed on bundle " + bundle);
            return null;
        }
    }

    /**
     * Just like {@link Intent#getBundleExtra(String)} but doesn't throw exceptions.
     */
    public static Bundle safeGetBundleExtra(Intent intent, String name) {
        try {
            return intent.getBundleExtra(name);
        } catch (Throwable t) {
            // Catches un-parceling exceptions.
            Log.e(TAG, "getBundleExtra failed on intent " + intent);
            return null;
        }
    }

    /**
     * Just like {@link Bundle#getParcelable(String)} but doesn't throw exceptions.
     */
    public static <T extends Parcelable> T safeGetParcelable(Bundle bundle, String name) {
        try {
            return bundle.getParcelable(name);
        } catch (Throwable t) {
            // Catches un-parceling exceptions.
            Log.e(TAG, "getParcelable failed on bundle " + bundle);
            return null;
        }
    }

    /**
     * Just like {@link Intent#getParcelableExtra(String)} but doesn't throw exceptions.
     */
    public static <T extends Parcelable> T safeGetParcelableExtra(Intent intent, String name) {
        try {
            return intent.getParcelableExtra(name);
        } catch (Throwable t) {
            // Catches un-parceling exceptions.
            Log.e(TAG, "getParcelableExtra failed on intent " + intent);
            return null;
        }
    }

    /**
     * Just link {@link Intent#getParcelableArrayListExtra(String)} but doesn't throw exceptions.
     */
    public static <T extends Parcelable> ArrayList<T> getParcelableArrayListExtra(
            Intent intent, String name) {
        try {
            return intent.getParcelableArrayListExtra(name);
        } catch (Throwable t) {
            // Catches un-parceling exceptions.
            Log.e(TAG, "getParcelableArrayListExtra failed on intent " + intent);
            return null;
        }
    }

    /**
     * Just like {@link Intent#getStringArrayListExtra(String)} but doesn't throw exceptions.
     */
    public static ArrayList<String> safeGetStringArrayListExtra(Intent intent, String name) {
        try {
            return intent.getStringArrayListExtra(name);
        } catch (Throwable t) {
            // Catches un-parceling exceptions.
            Log.e(TAG, "getStringArrayListExtra failed on intent " + intent);
            return null;
        }
    }

    /**
     * Just like {@link Intent#getByteArrayExtra(String)} but doesn't throw exceptions.
     */
    public static byte[] safeGetByteArrayExtra(Intent intent, String name) {
        try {
            return intent.getByteArrayExtra(name);
        } catch (Throwable t) {
            // Catches un-parceling exceptions.
            Log.e(TAG, "getByteArrayExtra failed on intent " + intent);
            return null;
        }
    }

    /**
     * @return a Binder from an Intent, or null.
     *
     * Creates a temporary copy of the extra Bundle, which is required as
     * Intent#getBinderExtra() doesn't exist, but Bundle.getBinder() does.
     */
    public static IBinder safeGetBinderExtra(Intent intent, String name) {
        if (!intent.hasExtra(name)) return null;
        try {
            Bundle extras = intent.getExtras();
            // Bundle#getBinder() is public starting at API level 18, but exists
            // in previous SDKs as a hidden method named "getIBinder" (which
            // still exists as of L MR1 but is hidden and deprecated).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                return extras.getBinder(name);
            } else {
                Method getBinderMethod = Bundle.class.getMethod("getIBinder", String.class);
                return (IBinder) getBinderMethod.invoke(extras, name);
            }
        } catch (Throwable t) {
            // Catches un-parceling exceptions.
            Log.e(TAG, "getBinder failed on intent " + intent);
            return null;
        }
    }

    /**
     * Inserts a {@link Binder} value into an Intent as an extra.
     *
     * This is the same as {@link Bundle#putBinder()}, but works below API level
     * 18.
     *
     * @param intent Intent to put the binder into.
     * @param name Key.
     * @param binder Binder object.
     */
    @VisibleForTesting
    public static void safePutBinderExtra(Intent intent, String name, IBinder binder) {
        if (intent == null) return;
        Bundle bundle = new Bundle();
        try {
            // Bundle#putBinder() is public starting at API level 18, but exists
            // in previous SDKs as a hidden method named "putIBinder" (which
            // still exists as of L MR1 but is hidden and deprecated).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                bundle.putBinder(name, binder);
            } else {
                Method putBinderMethod =
                        Bundle.class.getMethod("putIBinder", String.class, IBinder.class);
                putBinderMethod.invoke(bundle, name, binder);
            }
        } catch (Throwable t) {
            // Catches parceling exceptions.
            Log.e(TAG, "putBinder failed on bundle " + bundle);
        }
        intent.putExtras(bundle);
    }
}

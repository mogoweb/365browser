// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.webapps;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.AsyncTask;

import org.chromium.chrome.browser.ShortcutHelper;

/**
 * This is a class used to store data about an installed webapp. It uses SharedPreferences
 * to persist the data to disk. Every time {@link WebappDataStorage#open(Context, String)}
 * is used, the last used time is updated. It is not updated however, if
 * {@link WebappDataStorage#getLastUsedTime(Context, String, FetchCallback<Long>)} is used.
 */
public class WebappDataStorage {

    static final String SHARED_PREFS_FILE_PREFIX = "webapp_";
    static final String KEY_SPLASH_ICON = "splash_icon";
    static final String KEY_LAST_USED = "last_used";

    private final SharedPreferences mPreferences;

    /**
     * Opens an instance of WebappDataStorage for the webapp specified.
     * @param context  The context to open the SharedPreferences.
     * @param webappId The ID of the webapp which is being opened.
     */
    public static WebappDataStorage open(Context context, String webappId) {
        WebappDataStorage storage = new WebappDataStorage(
                context.getApplicationContext(), webappId);
        storage.updateLastUsedTime();
        return storage;
    }

    /**
     * Asynchronously retrieves the time which this WebappDataStorage was last
     * opened using {@link WebappDataStorage#open(Context, String)}.
     * @param context  The context to read the SharedPreferences file.
     * @param webappId The ID of the webapp the used time is being read for.
     * @param callback Called when the last used time has been retrieved.
     */
    public static void getLastUsedTime(Context context, String webappId,
            FetchCallback<Long> callback) {
        new WebappDataStorage(context.getApplicationContext(), webappId)
                .getLastUsedTime(callback);
    }

    private WebappDataStorage(Context context, String webappId) {
        mPreferences = context.getSharedPreferences(
                SHARED_PREFS_FILE_PREFIX + webappId, Context.MODE_PRIVATE);
    }

    /*
     * Asynchronously retrieves the splash screen image associated with the
     * current webapp.
     * @param callback Called when the splash screen image has been retrieved.
     *                 May be null if no image was found.
     */
    public void getSplashScreenImage(FetchCallback<Bitmap> callback) {
        new BitmapFetchTask(KEY_SPLASH_ICON, callback).execute();
    }

    /*
     * Update the information associated with the webapp with the specified data.
     * @param splashScreenImage The image which should be shown on the splash screen of the webapp.
     */
    public void updateSplashScreenImage(Bitmap splashScreenImage) {
        new UpdateTask(splashScreenImage).execute();
    }

    private void getLastUsedTime(final FetchCallback<Long> callback) {
        new AsyncTask<Void, Void, Long>() {
            @Override
            protected final Long doInBackground(Void... nothing) {
                return mPreferences.getLong(KEY_LAST_USED, -1L);
            }

            @Override
            protected final void onPostExecute(Long result) {
                assert result != -1L;
                callback.onDataRetrieved(result);
            }
        }.execute();
    }

    private WebappDataStorage updateLastUsedTime() {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected final Void doInBackground(Void... nothing) {
                mPreferences.edit()
                        .putLong(KEY_LAST_USED, System.currentTimeMillis())
                        .commit();
                return null;
            }
        }.execute();
        return this;
    }

    /**
     * Called after data has been retrieved from storage.
     */
    public interface FetchCallback<T> {
        public void onDataRetrieved(T readObject);
    }

    private final class BitmapFetchTask extends AsyncTask<Void, Void, Bitmap> {

        private final String mKey;
        private final FetchCallback<Bitmap> mCallback;

        public BitmapFetchTask(String key, FetchCallback<Bitmap> callback) {
            mKey = key;
            mCallback = callback;
        }

        @Override
        protected final Bitmap doInBackground(Void... nothing) {
            return ShortcutHelper.decodeBitmapFromString(mPreferences.getString(mKey, null));
        }

        @Override
        protected final void onPostExecute(Bitmap result) {
            mCallback.onDataRetrieved(result);
        }
    }

    private final class UpdateTask extends AsyncTask<Void, Void, Void> {

        private final Bitmap mSplashImage;

        public UpdateTask(Bitmap splashImage) {
            mSplashImage = splashImage;
        }

        @Override
        protected Void doInBackground(Void... nothing) {
            mPreferences.edit()
                    .putString(KEY_SPLASH_ICON, ShortcutHelper.encodeBitmapAsString(mSplashImage))
                    .commit();
            return null;
        }
    }
}
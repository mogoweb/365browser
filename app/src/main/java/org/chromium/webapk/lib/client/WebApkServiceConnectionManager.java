// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.webapk.lib.client;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.IBinder;
import android.util.Log;

import org.chromium.webapk.lib.runtime_library.IWebApkApi;

import java.util.ArrayList;
import java.util.Hashtable;

/**
 * Manages static global connections between the Chrome application and "WebAPK services". Each
 * WebAPK has its own "WebAPK service".
 */
public class WebApkServiceConnectionManager {
    /**
     * Interface for getting notified once Chrome is connected to the WebAPK service.
     */
    public interface ConnectionCallback {
        /**
         * Called once Chrome is connected to the WebAPK service.
         * @param api The WebAPK service API.
         */
        public void onConnected(IWebApkApi api);
    }

    /**
     * Managed connection to WebAPK service.
     */
    private static class Connection implements ServiceConnection {
        /**
         * Whether the connection to the service was established.
         */
        private boolean mHasConnected;

        /**
         * Callbacks to call once the connection is established.
         */
        private ArrayList<ConnectionCallback> mCallbacks = new ArrayList<ConnectionCallback>();

        /**
         * WebAPK IBinder interface.
         */
        private IWebApkApi mApi;

        public boolean hasConnected() {
            return mHasConnected;
        }

        public IWebApkApi getApi() {
            return mApi;
        }

        public void addCallback(ConnectionCallback callback) {
            mCallbacks.add(callback);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {}

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mHasConnected = true;
            mApi = IWebApkApi.Stub.asInterface(service);
            Log.d(TAG, String.format("Got IWebApkApi: %s", mApi));

            for (ConnectionCallback callback : mCallbacks) {
                callback.onConnected(mApi);
            }
            mCallbacks.clear();
        }
    }

    private static final String CATEGORY_WEBAPK_API = "android.intent.category.WEBAPK_API";

    private static final String TAG = "cr_WebApk";

    private static WebApkServiceConnectionManager sInstance;

    /**
     * Mapping of WebAPK package to WebAPK service connection.
     */
    private Hashtable<String, Connection> mConnections = new Hashtable<String, Connection>();

    public static WebApkServiceConnectionManager getInstance() {
        if (sInstance == null) {
            sInstance = new WebApkServiceConnectionManager();
        }
        return sInstance;
    }

    /**
     * Connects Chrome application to WebAPK service. Can be called from any thread.
     * @param appContext Application context.
     * @param webApkPackage WebAPK package to create connection for.
     * @param callback Callback to call after connection has been established. Called synchronously
     *   if the connection is already established.
     */
    public void connect(final Context appContext, final String webApkPackage,
            final ConnectionCallback callback) {
        Connection connection = mConnections.get(webApkPackage);
        if (connection != null) {
            if (connection.hasConnected()) {
                callback.onConnected(connection.getApi());
            } else {
                connection.addCallback(callback);
            }
            return;
        }

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                Connection newConnection = new Connection();
                newConnection.addCallback(callback);
                Intent intent = createConnectIntent(webApkPackage);

                try {
                    appContext.bindService(intent, newConnection, Context.BIND_AUTO_CREATE);

                } catch (SecurityException e) {
                    Log.w(TAG, "Security failed binding.", e);
                    return null;
                }

                mConnections.put(webApkPackage, newConnection);
                return null;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * Disconnects Chrome application from WebAPK service. Can be called from any thread.
     * @param appContext Application context.
     * @param webApkPackage WebAPK package for the service to disconnect from.
     */
    public void disconnect(final Context appContext, String webApkPackage) {
        if (webApkPackage == null) {
            return;
        }

        final Connection connection = mConnections.remove(webApkPackage);
        if (connection == null) {
            return;
        }

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                appContext.unbindService(connection);
                return null;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    WebApkServiceConnectionManager() {
    }

    /**
     * Creates intent to connect to WebAPK service.
     * @param webApkPackage The package name of the WebAPK to connect to.
     */
    private static Intent createConnectIntent(String webApkPackage) {
        Intent intent = new Intent();
        intent.addCategory(CATEGORY_WEBAPK_API);
        intent.setPackage(webApkPackage);
        return intent;
    }
}

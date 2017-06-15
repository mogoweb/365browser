// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.components.precache;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import org.chromium.base.VisibleForTesting;

/**
 * Wrapper class for NetworkInfo and ConnectivityManager.
 */
public class NetworkInfoDelegate {
    private NetworkInfo mNetworkInfo;
    private ConnectivityManager mConnectivityManager;

    @VisibleForTesting
    NetworkInfoDelegate() {}

    public NetworkInfoDelegate(Context context) {
        getNetworkInfo(context);
    }

    protected void getNetworkInfo(Context context) {
        mConnectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        mNetworkInfo = mConnectivityManager.getActiveNetworkInfo();
    }

    protected boolean isValid() {
        return mNetworkInfo != null;
    }

    protected int getType() {
        return mNetworkInfo.getType();
    }

    protected boolean isAvailable() {
        return mNetworkInfo.isAvailable();
    }

    protected boolean isConnected() {
        return mNetworkInfo.isConnected();
    }

    protected boolean isRoaming() {
        return mNetworkInfo.isRoaming();
    }

    protected boolean isActiveNetworkMetered() {
        return mConnectivityManager.isActiveNetworkMetered();
    }
}

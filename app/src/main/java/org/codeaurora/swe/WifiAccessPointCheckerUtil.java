/*
 *  Copyright (c) 2016 The Linux Foundation. All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions are
 *  met:
 *      * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 *  THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 *  WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 *  MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 *  ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 *  BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 *  CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 *  SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 *  BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 *  WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 *  OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 *  IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package org.codeaurora.swe;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.support.v4.content.ContextCompat;
import android.view.Gravity;
import android.widget.TextView;
import android.widget.Toast;

import org.chromium.chrome.R;
import org.chromium.ui.base.WindowAndroid;
import org.chromium.ui.base.WindowAndroid.PermissionCallback;

import java.util.List;

/**
 * Check Wifi status and prompt user if not connected.
 * If no access point is found then prompt to enable data.
 */
public class WifiAccessPointCheckerUtil {

    private static boolean mNetworkShouldNotify = true;

    public static void checkWifiAccessPoint(final Context context,
        final WindowAndroid windowAndroid) {
        boolean checkWifiAccessPoint =
                context.getResources().getBoolean(R.bool.swe_feature_network_notifier);
        WifiManager wifiMgr = (WifiManager)
                context.getSystemService(Context.WIFI_SERVICE);

        Resources resources = context.getResources();
        final String dataSettingAction =
                resources.getString(R.string.swe_def_action_wifi_selection_data_connections);
        final String wifiSelectionAction =
                resources.getString(R.string.swe_def_intent_pick_network);

        if (!checkWifiAccessPoint
                || dataSettingAction.isEmpty()
                || wifiSelectionAction.isEmpty()
                || !mNetworkShouldNotify
                /*|| !wifiMgr.isWifiEnabled()*/) {
            return;
        }

        // WifiManager.ScanResult() method requires Location permission. In
        // Android M user must be prompted at runtime to access this permission
        if (checkWifiAccessPoint && (android.os.Build.VERSION.SDK_INT >=
              android.os.Build.VERSION_CODES.M) &&
                ContextCompat.checkSelfPermission(context,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
            PermissionCallback permissionCallback = new PermissionCallback() {
                @Override
                public void onRequestPermissionsResult(
                        String[] permissions, int[] grantResults) {
                    if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        checkWifiAccessPointInternal(context,
                                dataSettingAction,
                                wifiSelectionAction);
                    }
                }
            };
            windowAndroid.requestPermissions(
                    new
                    String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION},
                    permissionCallback);
            return;
        }

        checkWifiAccessPointInternal(context, dataSettingAction,
                wifiSelectionAction);
    }

    private static void checkWifiAccessPointInternal(final Context context,
                                                     String dataSettingAction,
                                                     String wifiSelectionAction) {
        WifiManager wifiMgr = (WifiManager)
                context.getSystemService(Context.WIFI_SERVICE);

        ConnectivityManager conMgr = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = conMgr.getActiveNetworkInfo();

        if (networkInfo == null || (networkInfo != null &&
                (networkInfo.getType() != ConnectivityManager.TYPE_WIFI))) {

            List<ScanResult> list = wifiMgr.getScanResults();

            if (wifiMgr.isWifiEnabled() == false || list == null || list.size() == 0) {
                // Have no AP's for Wifi's fall back to data
                startIntent(dataSettingAction, context);
            } else {
                // Request to Select Wifi AP
                Toast toast = Toast.makeText(context,
                    context.getString(R.string.swe_wifi_select_message),
                    Toast.LENGTH_LONG);
                TextView textView = (TextView)
                  toast.getView().findViewById(android.R.id.message);
                if (textView != null) {
                    textView.setGravity(Gravity.CENTER);
                }
                toast.show();

                // Start Wifi Selection Activity
                startIntent(wifiSelectionAction, context);
            }
            // Notify only once when the application is started
            mNetworkShouldNotify = false;
        }
        return;
    }

    private static void startIntent(final String action, final Context context) {
        try {
            Intent intent = new Intent(action);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            String err_msg = context.getString(
                    R.string.swe_action_not_found, action);
            Toast.makeText(context, err_msg,
                    Toast.LENGTH_LONG).show();
        }

    }
}

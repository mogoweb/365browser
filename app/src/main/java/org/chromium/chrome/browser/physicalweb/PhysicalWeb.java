// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.physicalweb;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;

import org.chromium.base.ContextUtils;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.IntentHandler;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.chrome.browser.preferences.privacy.PrivacyPreferencesManager;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.search_engines.TemplateUrlService;
import org.chromium.components.location.LocationUtils;

/**
 * This class provides the basic interface to the Physical Web feature.
 */
public class PhysicalWeb {
    public static final int OPTIN_NOTIFY_MAX_TRIES = 1;
    private static final String PHYSICAL_WEB_SHARING_PREFERENCE = "physical_web_sharing";
    private static final String FEATURE_NAME = "PhysicalWeb";
    private static final String PHYSICAL_WEB_SHARING_FEATURE_NAME = "PhysicalWebSharing";
    private static final int MIN_ANDROID_VERSION = 18;

    /**
     * Evaluates whether the environment is one in which the Physical Web should
     * be enabled.
     * @return true if the PhysicalWeb should be enabled
     */
    public static boolean featureIsEnabled() {
        return ChromeFeatureList.isEnabled(FEATURE_NAME)
                && Build.VERSION.SDK_INT >= MIN_ANDROID_VERSION;
    }

    /**
     * Checks whether the Physical Web preference is switched to On.
     *
     * @return boolean {@code true} if the preference is On.
     */
    public static boolean isPhysicalWebPreferenceEnabled() {
        return PrivacyPreferencesManager.getInstance().isPhysicalWebEnabled();
    }

    /**
     * Checks whether the Physical Web Sharing feature is enabled.
     *
     * @return boolean {@code true} if the feature is enabled
     */
    public static boolean sharingIsEnabled() {
        return ChromeFeatureList.isEnabled(PHYSICAL_WEB_SHARING_FEATURE_NAME);
    }

    /**
     * Checks whether the user has consented to use the Sharing feature.
     *
     * @return boolean {@code true} if the feature is enabled
     */
    public static boolean sharingIsOptedIn() {
        return ContextUtils.getAppSharedPreferences()
            .getBoolean(PHYSICAL_WEB_SHARING_PREFERENCE, false);
    }

    /**
     * Sets the preference that the user has opted into use the Sharing feature.
     */
    public static void setSharingOptedIn() {
        ContextUtils.getAppSharedPreferences()
                .edit()
                .putBoolean(PHYSICAL_WEB_SHARING_PREFERENCE, true)
                .apply();
    }

    /**
     * Checks whether the Physical Web onboard flow is active and the user has
     * not yet elected to either enable or decline the feature.
     *
     * @return boolean {@code true} if onboarding is complete.
     */
    public static boolean isOnboarding() {
        return PrivacyPreferencesManager.getInstance().isPhysicalWebOnboarding();
    }

    /**
     * Performs various Physical Web operations that should happen on startup.
     */
    public static void onChromeStart() {
        // In the case that the user has disabled our flag and restarted, this is a minimal code
        // path to disable our subscription to Nearby.
        if (!featureIsEnabled()) {
            new NearbyBackgroundSubscription(NearbySubscription.UNSUBSCRIBE).run();
            return;
        }

        // If this user is in the default state, we need to check if we should enable Physical Web.
        if (isOnboarding() && shouldAutoEnablePhysicalWeb()) {
            PrivacyPreferencesManager.getInstance().setPhysicalWebEnabled(true);
        }

        updateScans();
        // The PhysicalWebUma call in this method should be called only when the native library
        // is loaded.  This is always the case on chrome startup.
        PhysicalWebUma.uploadDeferredMetrics();

        // We can remove this block after M60.
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                ContextUtils.getAppSharedPreferences().edit()
                        .remove("physical_web_notify_count")
                        .apply();
                return null;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * Checks if this device should have Physical Web automatically enabled.
     */
    private static boolean shouldAutoEnablePhysicalWeb() {
        LocationUtils locationUtils = LocationUtils.getInstance();
        return locationUtils.isSystemLocationSettingEnabled()
                && locationUtils.hasAndroidLocationPermission()
                && TemplateUrlService.getInstance().isDefaultSearchEngineGoogle()
                && !Profile.getLastUsedProfile().isOffTheRecord();
    }

    /**
     * Starts the Activity that shows the list of Physical Web URLs.
     */
    public static void showUrlList() {
        IntentHandler.startChromeLauncherActivityForTrustedIntent(
                new Intent(Intent.ACTION_VIEW, Uri.parse(UrlConstants.PHYSICAL_WEB_URL))
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    /**
     * Check if bluetooth is on and enabled.
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static boolean bluetoothIsEnabled() {
        Context context = ContextUtils.getApplicationContext();
        BluetoothManager bluetoothManager =
                (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    /**
     * Check if the device bluetooth hardware supports BLE advertisements.
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static boolean hasBleAdvertiseCapability() {
        Context context = ContextUtils.getApplicationContext();
        BluetoothManager bluetoothManager =
                (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
        return bluetoothAdapter != null && bluetoothAdapter.getBluetoothLeAdvertiser() != null;
    }

    /**
     * Examines the environment in order to decide whether we should begin or end a scan.
     */
    public static void updateScans() {
        LocationUtils locationUtils = LocationUtils.getInstance();
        if (!locationUtils.hasAndroidLocationPermission()
                || !locationUtils.isSystemLocationSettingEnabled()
                || !isPhysicalWebPreferenceEnabled()) {
            new NearbyBackgroundSubscription(NearbySubscription.UNSUBSCRIBE).run();
            return;
        }

        new NearbyBackgroundSubscription(NearbySubscription.SUBSCRIBE).run();
    }
}

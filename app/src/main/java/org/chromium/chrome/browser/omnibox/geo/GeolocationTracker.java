// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.omnibox.geo;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Process;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.SuppressFBWarnings;

/**
 * Keeps track of the device's location, allowing synchronous location requests.
 * getLastKnownLocation() returns the current best estimate of the location. If possible, call
 * refreshLastKnownLocation() several seconds before a location is needed to maximize the chances
 * that the location is known.
 */
class GeolocationTracker {

    private static SelfCancelingListener sListener;
    private static Location sNetworkLocationForTesting;
    private static Location sGpsLocationForTesting;
    private static boolean sUseLocationForTesting;
    private static long sLocationAgeForTesting;
    private static boolean sUseLocationAgeForTesting;

    private static class SelfCancelingListener implements LocationListener {

        // Length of time before the location request should be canceled. This timeout ensures the
        // device doesn't get stuck in an infinite loop trying and failing to get a location, which
        // would cause battery drain. See: http://crbug.com/309917
        private static final int REQUEST_TIMEOUT_MS = 60 * 1000;  // 60 sec.

        private final LocationManager mLocationManager;
        private final Handler mHandler;
        private final Runnable mCancelRunnable;

        private SelfCancelingListener(LocationManager manager) {
            mLocationManager = manager;
            mHandler = new Handler();
            mCancelRunnable = new Runnable() {
                @Override
                public void run() {
                    mLocationManager.removeUpdates(SelfCancelingListener.this);
                    sListener = null;
                }
            };
            mHandler.postDelayed(mCancelRunnable, REQUEST_TIMEOUT_MS);
        }

        @Override
        public void onLocationChanged(Location location) {
            mHandler.removeCallbacks(mCancelRunnable);
            sListener = null;
        }

        @Override
        public void onProviderDisabled(String provider) { }

        @Override
        public void onProviderEnabled(String provider) { }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) { }
    }

    /**
     * Returns the age of location is milliseconds.
     * Note: the age will be invalid if the system clock has been changed since the location was
     * created. If the apparent age is negative, Long.MAX_VALUE will be returned.
     */
    static long getLocationAge(Location location) {
        if (sUseLocationAgeForTesting) return sLocationAgeForTesting;
        long age = System.currentTimeMillis() - location.getTime();
        return age >= 0 ? age : Long.MAX_VALUE;
    }

    /**
     * Returns the last known location or null if none is available.
     *
     * @param includeGpsFallback Whether the gps provider should also be used as a fallback.
     *        Otherwise only the network provider will be used.
     */
    static Location getLastKnownLocation(Context context, boolean includeGpsFallback) {
        if (sUseLocationForTesting) {
            return chooseLocation(
                    sNetworkLocationForTesting, sGpsLocationForTesting, includeGpsFallback);
        }

        if (!hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)) {
            // Do not call location manager without permissions
            return null;
        }

        LocationManager locationManager =
                (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        Location networkLocation =
                locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        // If no GPS location has been request, just return the network location. For efficiency,
        // don't even get the GPS location.
        if (!includeGpsFallback) {
            return networkLocation;
        }
        Location gpsLocation = null;
        if (hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)) {
            // Only try to get GPS location when ACCESS_FINE_LOCATION is granted.
            gpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        }
        return chooseLocation(networkLocation, gpsLocation, includeGpsFallback);
    }

    /**
     * Requests an updated location if the last known location is older than maxAge milliseconds.
     *
     * Note: this must be called only on the UI thread.
     */
    @SuppressFBWarnings("LI_LAZY_INIT_UPDATE_STATIC")
    static void refreshLastKnownLocation(Context context, long maxAge) {
        ThreadUtils.assertOnUiThread();

        if (!hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)) {
            return;
        }

        // We're still waiting for a location update.
        if (sListener != null) return;

        LocationManager locationManager =
                (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        Location location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        if (location == null || getLocationAge(location) > maxAge) {
            String provider = LocationManager.NETWORK_PROVIDER;
            if (locationManager.isProviderEnabled(provider)) {
                sListener = new SelfCancelingListener(locationManager);
                locationManager.requestSingleUpdate(provider, sListener, null);
            }
        }
    }

    @VisibleForTesting
    static void setLocationForTesting(
            Location networkLocationForTesting, Location gpsLocationForTesting) {
        sNetworkLocationForTesting = networkLocationForTesting;
        sGpsLocationForTesting = gpsLocationForTesting;
        sUseLocationForTesting = true;
    }

    @VisibleForTesting
    static void setLocationAgeForTesting(Long locationAgeForTesting) {
        if (locationAgeForTesting == null) {
            sUseLocationAgeForTesting = false;
            return;
        }
        sLocationAgeForTesting = locationAgeForTesting;
        sUseLocationAgeForTesting = true;
    }

    private static boolean hasPermission(Context context, String permission) {
        return ApiCompatibilityUtils.checkPermission(
                       context, permission, Process.myPid(), Process.myUid())
                == PackageManager.PERMISSION_GRANTED;
    }

    private static Location chooseLocation(
            Location networkLocation, Location gpsLocation, boolean includeGpsFallback) {
        if (!includeGpsFallback || gpsLocation == null) {
            return networkLocation;
        }

        if (networkLocation == null) {
            return gpsLocation;
        }

        // Both are not null, take the younger one.
        return networkLocation.getTime() > gpsLocation.getTime() ? networkLocation : gpsLocation;
    }
}

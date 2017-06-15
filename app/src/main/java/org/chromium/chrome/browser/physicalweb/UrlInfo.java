// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.physicalweb;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

/**
 * This class represents a scanned URL and information associated with that URL.
 */
class UrlInfo {
    private static final String URL_KEY = "url";
    private static final String DISTANCE_KEY = "distance";
    private static final String FIRST_SEEN_TIMESTAMP_KEY = "first_seen_timestamp";
    private static final String DEVICE_ADDRESS_KEY = "device_address";
    private static final String HAS_BEEN_DISPLAYED_KEY = "has_been_displayed";
    private final String mUrl;
    private final long mFirstSeenTimestamp;
    private double mDistance;
    private String mDeviceAddress;
    private boolean mHasBeenDisplayed;

    public UrlInfo(String url, double distance, long firstSeenTimestamp) {
        mUrl = url;
        mDistance = distance;
        mFirstSeenTimestamp = firstSeenTimestamp;
        mDeviceAddress = null;
        mHasBeenDisplayed = false;
    }

    /**
     * Constructs a simple UrlInfo with only a URL.
     */
    public UrlInfo(String url) {
        this(url, -1.0, System.currentTimeMillis());
    }

    /**
     * Gets the URL represented by this object.
     * @param The URL.
     */
    public String getUrl() {
        return mUrl;
    }

    /**
     * Sets the distance of the URL from the scanner in meters.
     * @param distance The estimated distance of the URL from the scanner in meters.
     */
    public UrlInfo setDistance(double distance) {
        mDistance = distance;
        return this;
    }

    /**
     * Gets the distance of the URL from the scanner in meters.
     * @return The estimated distance of the URL from the scanner in meters.
     */
    public double getDistance() {
        return mDistance;
    }

    /**
     * Gets the timestamp of when the URL was first scanned.
     * This timestamp is recorded using System.currentTimeMillis().
     * @return The first seen timestamp.
     */
    public long getFirstSeenTimestamp() {
        return mFirstSeenTimestamp;
    }

    /**
     * Sets the device address for the BLE beacon that last emitted this URL.
     * @param deviceAddress the new device address, matching the
     *        BluetoothAdapter.checkBluetoothAddress format.
     */
    public UrlInfo setDeviceAddress(String deviceAddress) {
        mDeviceAddress = deviceAddress;
        return this;
    }

    /**
     * Gets the device address for the BLE beacon that last emitted this URL.
     * @return The device address.
     */
    public String getDeviceAddress() {
        return mDeviceAddress;
    }

    /**
     * Marks this URL as having been displayed to the user.
     */
    public UrlInfo setHasBeenDisplayed() {
        mHasBeenDisplayed = true;
        return this;
    }

    /**
     * Tells if we've displayed this URL.
     * @return Whether we've displayed this URL.
     */
    public boolean hasBeenDisplayed() {
        return mHasBeenDisplayed;
    }

    /**
     * Creates a JSON object that represents this data structure.
     * @return a JSON serialization of this data structure.
     * @throws JSONException if the values cannot be deserialized.
     */
    public JSONObject jsonSerialize() throws JSONException {
        return new JSONObject()
                .put(URL_KEY, mUrl)
                .put(DISTANCE_KEY, mDistance)
                .put(FIRST_SEEN_TIMESTAMP_KEY, mFirstSeenTimestamp)
                .put(DEVICE_ADDRESS_KEY, mDeviceAddress)
                .put(HAS_BEEN_DISPLAYED_KEY, mHasBeenDisplayed);
    }

    /**
     * Populates a UrlInfo with data from a given JSON object.
     * @param jsonObject a serialized UrlInfo.
     * @return The UrlInfo represented by the serialized object.
     * @throws JSONException if the values cannot be serialized.
     */
    public static UrlInfo jsonDeserialize(JSONObject jsonObject) throws JSONException {
        UrlInfo urlInfo = new UrlInfo(jsonObject.getString(URL_KEY),
                jsonObject.getDouble(DISTANCE_KEY), jsonObject.getLong(FIRST_SEEN_TIMESTAMP_KEY))
                                  .setDeviceAddress(jsonObject.optString(DEVICE_ADDRESS_KEY));
        if (jsonObject.optBoolean(HAS_BEEN_DISPLAYED_KEY, false)) {
            urlInfo.setHasBeenDisplayed();
        }
        return urlInfo;
    }

    /**
     * Represents the UrlInfo as a String.
     */
    @Override
    public String toString() {
        return String.format(Locale.getDefault(), "%s %f %d %b", mUrl, mDistance,
                mFirstSeenTimestamp, mHasBeenDisplayed);
    }
}

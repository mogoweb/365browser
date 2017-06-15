// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.omnibox.geo;

import android.support.annotation.IntDef;

import org.chromium.base.ApiCompatibilityUtils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Visible networks. Stores the data of connected and visible networks.
 */
class VisibleNetworks {
    private static final String TAG = "VisibleNetworks";

    @Nullable
    private final VisibleWifi mConnectedWifi;
    @Nullable
    private final VisibleCell mConnectedCell;
    @Nullable
    private final Set<VisibleWifi> mAllVisibleWifis;
    @Nullable
    private final Set<VisibleCell> mAllVisibleCells;

    private VisibleNetworks(@Nullable VisibleWifi connectedWifi,
            @Nullable VisibleCell connectedCell, @Nullable Set<VisibleWifi> allVisibleWifis,
            @Nullable Set<VisibleCell> allVisibleCells) {
        mConnectedWifi = connectedWifi;
        mConnectedCell = connectedCell;
        mAllVisibleWifis = allVisibleWifis;
        mAllVisibleCells = allVisibleCells;
    }

    static VisibleNetworks create(@Nullable VisibleWifi connectedWifi,
            @Nullable VisibleCell connectedCell, @Nullable Set<VisibleWifi> allVisibleWifis,
            @Nullable Set<VisibleCell> allVisibleCells) {
        return new VisibleNetworks(connectedWifi, connectedCell, allVisibleWifis, allVisibleCells);
    }

    /**
     * Returns the connected {@link VisibleWifi} or null if the connected wifi is unknown.
     */
    @Nullable
    VisibleWifi connectedWifi() {
        return mConnectedWifi;
    }

    /**
     * Returns the connected {@link VisibleCell} or null if the connected cell is unknown.
     */
    @Nullable
    VisibleCell connectedCell() {
        return mConnectedCell;
    }

    /**
     * Returns the current set of {@link VisibleWifi}s that are visible (including not connected
     * networks), or null if the set is unknown.
     */
    @Nullable
    Set<VisibleWifi> allVisibleWifis() {
        return mAllVisibleWifis;
    }

    /**
     * Returns the current set of {@link VisibleCell}s that are visible (including not connected
     * networks), or null if the set is unknown.
     */
    @Nullable
    Set<VisibleCell> allVisibleCells() {
        return mAllVisibleCells;
    }

    /**
     * Returns whether this object is empty, meaning there is no visible networks at all.
     */
    final boolean isEmpty() {
        Set<VisibleWifi> allVisibleWifis = allVisibleWifis();
        Set<VisibleCell> allVisibleCells = allVisibleCells();
        return connectedWifi() == null && connectedCell() == null
                && (allVisibleWifis == null || allVisibleWifis.size() == 0)
                && (allVisibleCells == null || allVisibleCells.size() == 0);
    }

    /**
     * Compares the specified object with this VisibleNetworks for equality.  Returns
     * {@code true} if the given object is a VisibleNetworks and has identical values for
     * all of its fields.
     */
    @Override
    public boolean equals(Object object) {
        if (!(object instanceof VisibleNetworks)) {
            return false;
        }
        VisibleNetworks that = (VisibleNetworks) object;
        return ApiCompatibilityUtils.objectEquals(mConnectedWifi, that.connectedWifi())
                && ApiCompatibilityUtils.objectEquals(mConnectedCell, that.connectedCell())
                && ApiCompatibilityUtils.objectEquals(mAllVisibleWifis, that.allVisibleWifis())
                && ApiCompatibilityUtils.objectEquals(mAllVisibleCells, that.allVisibleCells());
    }

    private static int objectsHashCode(Object o) {
        return o != null ? o.hashCode() : 0;
    }

    private static int objectsHash(Object... a) {
        return Arrays.hashCode(a);
    }

    @Override
    public int hashCode() {
        return objectsHash(mConnectedWifi, mConnectedCell, objectsHashCode(mAllVisibleWifis),
                objectsHashCode(mAllVisibleCells));
    }

    /**
     * Specification of a visible wifi.
     */
    static class VisibleWifi {
        static final VisibleWifi NO_WIFI_INFO = VisibleWifi.create(null, null, null, null);

        @Nullable
        private final String mSsid;
        @Nullable
        private final String mBssid;
        @Nullable
        private final Integer mLevel;
        @Nullable
        private final Long mTimestampMs;

        private VisibleWifi(@Nullable String ssid, @Nullable String bssid, @Nullable Integer level,
                @Nullable Long timestampMs) {
            mSsid = ssid;
            mBssid = bssid;
            mLevel = level;
            mTimestampMs = timestampMs;
        }

        static VisibleWifi create(@Nullable String ssid, @Nullable String bssid,
                @Nullable Integer level, @Nullable Long timestampMs) {
            return new VisibleWifi(ssid, bssid, level, timestampMs);
        }

        /**
         * Returns the SSID of the visible Wifi, or null if unknown.
         */
        @Nullable
        String ssid() {
            return mSsid;
        }

        /**
         * Returns the BSSID of the visible Wifi, or null if unknown.
         */
        @Nullable
        String bssid() {
            return mBssid;
        }

        /**
         * Returns the signal level in dBm (RSSI), {@code null} if unknown.
         */
        @Nullable
        Integer level() {
            return mLevel;
        }

        /**
         * Returns the timestamp in Ms, {@code null} if unknown.
         */
        @Nullable
        Long timestampMs() {
            return mTimestampMs;
        }

        /**
         * Compares the specified object with this VisibleWifi for equality.  Returns
         * {@code true} if the given object is a VisibleWifi and has identical values for
         * all of its fields except level and timestampMs.
         */
        @Override
        public boolean equals(Object object) {
            if (!(object instanceof VisibleWifi)) {
                return false;
            }

            VisibleWifi that = (VisibleWifi) object;
            return ApiCompatibilityUtils.objectEquals(mSsid, that.ssid())
                    && ApiCompatibilityUtils.objectEquals(mBssid, that.bssid());
        }

        @Override
        public int hashCode() {
            return VisibleNetworks.objectsHash(mSsid, mBssid);
        }

        /**
         * Encodes a VisibleWifi into its corresponding PartnerLocationDescriptor.VisibleNetwork
         * proto.
         */
        PartnerLocationDescriptor.VisibleNetwork toProto(boolean connected) {
            PartnerLocationDescriptor.VisibleNetwork visibleNetwork =
                    new PartnerLocationDescriptor.VisibleNetwork();

            PartnerLocationDescriptor.VisibleNetwork.WiFi wifi =
                    new PartnerLocationDescriptor.VisibleNetwork.WiFi();

            wifi.bssid = bssid();
            wifi.levelDbm = level();

            visibleNetwork.wifi = wifi;
            visibleNetwork.timestampMs = timestampMs();
            visibleNetwork.connected = connected;

            return visibleNetwork;
        }
    }

    /**
     * Specification of a visible cell.
     */
    static class VisibleCell {
        static final VisibleCell UNKNOWN_VISIBLE_CELL =
                VisibleCell.builder(VisibleCell.UNKNOWN_RADIO_TYPE).build();
        static final VisibleCell UNKNOWN_MISSING_LOCATION_PERMISSION_VISIBLE_CELL =
                VisibleCell.builder(VisibleCell.UNKNOWN_MISSING_LOCATION_PERMISSION_RADIO_TYPE)
                        .build();

        /**
         * Represents all possible values of radio type that we track.
         */
        @Retention(RetentionPolicy.SOURCE)
        @IntDef({UNKNOWN_RADIO_TYPE, UNKNOWN_MISSING_LOCATION_PERMISSION_RADIO_TYPE,
                CDMA_RADIO_TYPE, GSM_RADIO_TYPE, LTE_RADIO_TYPE, WCDMA_RADIO_TYPE})
        @interface RadioType {}
        static final int UNKNOWN_RADIO_TYPE = 0;
        static final int UNKNOWN_MISSING_LOCATION_PERMISSION_RADIO_TYPE = 1;
        static final int CDMA_RADIO_TYPE = 2;
        static final int GSM_RADIO_TYPE = 3;
        static final int LTE_RADIO_TYPE = 4;
        static final int WCDMA_RADIO_TYPE = 5;

        static Builder builder(@RadioType int radioType) {
            return new VisibleCell.Builder().setRadioType(radioType);
        }

        @RadioType
        private final int mRadioType;
        @Nullable
        private final Integer mCellId;
        @Nullable
        private final Integer mLocationAreaCode;
        @Nullable
        private final Integer mMobileCountryCode;
        @Nullable
        private final Integer mMobileNetworkCode;
        @Nullable
        private final Integer mPrimaryScramblingCode;
        @Nullable
        private final Integer mPhysicalCellId;
        @Nullable
        private final Integer mTrackingAreaCode;
        @Nullable
        private Long mTimestampMs;

        private VisibleCell(Builder builder) {
            mRadioType = builder.mRadioType;
            mCellId = builder.mCellId;
            mLocationAreaCode = builder.mLocationAreaCode;
            mMobileCountryCode = builder.mMobileCountryCode;
            mMobileNetworkCode = builder.mMobileNetworkCode;
            mPrimaryScramblingCode = builder.mPrimaryScramblingCode;
            mPhysicalCellId = builder.mPhysicalCellId;
            mTrackingAreaCode = builder.mTrackingAreaCode;
            mTimestampMs = builder.mTimestampMs;
        }

        /**
         * Returns the radio type of the visible cell.
         */
        @RadioType
        int radioType() {
            return mRadioType;
        }

        /**
         * Returns the gsm cell id, {@code null} if unknown.
         */
        @Nullable
        Integer cellId() {
            return mCellId;
        }

        /**
         * Returns the gsm location area code, {@code null} if unknown.
         */
        @Nullable
        Integer locationAreaCode() {
            return mLocationAreaCode;
        }

        /**
         * Returns the mobile country code, {@code null} if unknown or GSM.
         */
        @Nullable
        Integer mobileCountryCode() {
            return mMobileCountryCode;
        }

        /**
         * Returns the mobile network code, {@code null} if unknown or GSM.
         */
        @Nullable
        Integer mobileNetworkCode() {
            return mMobileNetworkCode;
        }

        /**
         * On a UMTS network, returns the primary scrambling code of the serving cell, {@code null}
         * if unknown or GSM.
         */
        @Nullable
        Integer primaryScramblingCode() {
            return mPrimaryScramblingCode;
        }

        /**
         * Returns the physical cell id, {@code null} if unknown or not LTE.
         */
        @Nullable
        Integer physicalCellId() {
            return mPhysicalCellId;
        }

        /**
         * Returns the tracking area code, {@code null} if unknown or not LTE.
         */
        @Nullable
        Integer trackingAreaCode() {
            return mTrackingAreaCode;
        }

        /**
         * Returns the timestamp in Ms, {@code null} if unknown.
         */
        @Nullable
        Long timestampMs() {
            return mTimestampMs;
        }

        /**
         * Compares the specified object with this VisibleCell for equality.  Returns
         * {@code true} if the given object is a VisibleWifi and has identical values for
         * all of its fields except timestampMs.
         */
        @Override
        public boolean equals(Object object) {
            if (!(object instanceof VisibleCell)) {
                return false;
            }
            VisibleCell that = (VisibleCell) object;
            return ApiCompatibilityUtils.objectEquals(mRadioType, that.radioType())
                    && ApiCompatibilityUtils.objectEquals(mCellId, that.cellId())
                    && ApiCompatibilityUtils.objectEquals(
                               mLocationAreaCode, that.locationAreaCode())
                    && ApiCompatibilityUtils.objectEquals(
                               mMobileCountryCode, that.mobileCountryCode())
                    && ApiCompatibilityUtils.objectEquals(
                               mMobileNetworkCode, that.mobileNetworkCode())
                    && ApiCompatibilityUtils.objectEquals(
                               mPrimaryScramblingCode, that.primaryScramblingCode())
                    && ApiCompatibilityUtils.objectEquals(mPhysicalCellId, that.physicalCellId())
                    && ApiCompatibilityUtils.objectEquals(
                               mTrackingAreaCode, that.trackingAreaCode());
        }

        @Override
        public int hashCode() {
            return VisibleNetworks.objectsHash(mRadioType, mCellId, mLocationAreaCode,
                    mMobileCountryCode, mMobileNetworkCode, mPrimaryScramblingCode, mPhysicalCellId,
                    mTrackingAreaCode);
        }

        /**
         * Encodes a VisibleCell into its corresponding PartnerLocationDescriptor.VisibleNetwork
         * proto.
         */
        PartnerLocationDescriptor.VisibleNetwork toProto(boolean connected) {
            PartnerLocationDescriptor.VisibleNetwork visibleNetwork =
                    new PartnerLocationDescriptor.VisibleNetwork();

            PartnerLocationDescriptor.VisibleNetwork.Cell cell =
                    new PartnerLocationDescriptor.VisibleNetwork.Cell();

            switch (radioType()) {
                case VisibleCell.CDMA_RADIO_TYPE:
                    cell.type = PartnerLocationDescriptor.VisibleNetwork.Cell.CDMA;
                    break;
                case VisibleCell.GSM_RADIO_TYPE:
                    cell.type = PartnerLocationDescriptor.VisibleNetwork.Cell.GSM;
                    break;
                case VisibleCell.LTE_RADIO_TYPE:
                    cell.type = PartnerLocationDescriptor.VisibleNetwork.Cell.LTE;
                    break;
                case VisibleCell.WCDMA_RADIO_TYPE:
                    cell.type = PartnerLocationDescriptor.VisibleNetwork.Cell.WCDMA;
                    break;
                case VisibleCell.UNKNOWN_RADIO_TYPE:
                case VisibleCell.UNKNOWN_MISSING_LOCATION_PERMISSION_RADIO_TYPE:
                default:
                    cell.type = PartnerLocationDescriptor.VisibleNetwork.Cell.UNKNOWN;
                    break;
            }
            cell.cellId = cellId();
            cell.locationAreaCode = locationAreaCode();
            cell.mobileCountryCode = mobileCountryCode();
            cell.mobileNetworkCode = mobileNetworkCode();
            cell.primaryScramblingCode = primaryScramblingCode();
            cell.physicalCellId = physicalCellId();
            cell.trackingAreaCode = trackingAreaCode();

            visibleNetwork.cell = cell;
            visibleNetwork.timestampMs = timestampMs();
            visibleNetwork.connected = connected;

            return visibleNetwork;
        }

        /**
         * A {@link VisibleCell} builder.
         */
        static class Builder {
            @RadioType
            private int mRadioType;
            @Nullable
            private Integer mCellId;
            @Nullable
            private Integer mLocationAreaCode;
            @Nullable
            private Integer mMobileCountryCode;
            @Nullable
            private Integer mMobileNetworkCode;
            @Nullable
            private Integer mPrimaryScramblingCode;
            @Nullable
            private Integer mPhysicalCellId;
            @Nullable
            private Integer mTrackingAreaCode;
            @Nullable
            private Long mTimestampMs;

            Builder setRadioType(@RadioType int radioType) {
                mRadioType = radioType;
                return this;
            }

            Builder setCellId(@Nullable Integer cellId) {
                mCellId = cellId;
                return this;
            }

            Builder setLocationAreaCode(@Nullable Integer locationAreaCode) {
                mLocationAreaCode = locationAreaCode;
                return this;
            }

            Builder setMobileCountryCode(@Nullable Integer mobileCountryCode) {
                mMobileCountryCode = mobileCountryCode;
                return this;
            }

            Builder setMobileNetworkCode(@Nullable Integer mobileNetworkCode) {
                mMobileNetworkCode = mobileNetworkCode;
                return this;
            }

            Builder setPrimaryScramblingCode(@Nullable Integer primaryScramblingCode) {
                mPrimaryScramblingCode = primaryScramblingCode;
                return this;
            }

            Builder setPhysicalCellId(@Nullable Integer physicalCellId) {
                mPhysicalCellId = physicalCellId;
                return this;
            }

            Builder setTrackingAreaCode(@Nullable Integer trackingAreaCode) {
                mTrackingAreaCode = trackingAreaCode;
                return this;
            }

            Builder setTimestamp(@Nullable Long timestampMs) {
                mTimestampMs = timestampMs;
                return this;
            }

            VisibleCell build() {
                return new VisibleCell(this);
            }
        }
    }
}
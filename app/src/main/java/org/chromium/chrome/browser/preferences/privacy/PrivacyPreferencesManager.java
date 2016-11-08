// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.privacy;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import org.chromium.base.CommandLine;
import org.chromium.base.ContextUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeSwitches;
import org.chromium.chrome.browser.device.DeviceClassManager;
import org.chromium.chrome.browser.physicalweb.PhysicalWeb;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;

/**
 * Reads, writes, and migrates preferences related to network usage and privacy.
 */
public class PrivacyPreferencesManager implements CrashReportingPermissionManager{

    static final String PREF_CRASH_DUMP_UPLOAD = "crash_dump_upload";
    static final String PREF_CRASH_DUMP_UPLOAD_NO_CELLULAR = "crash_dump_upload_no_cellular";
    static final String PREF_METRICS_REPORTING = "metrics_reporting";
    private static final String PREF_METRICS_IN_SAMPLE = "in_metrics_sample";
    private static final String PREF_NETWORK_PREDICTIONS = "network_predictions";
    private static final String PREF_BANDWIDTH_OLD = "prefetch_bandwidth";
    private static final String PREF_BANDWIDTH_NO_CELLULAR_OLD = "prefetch_bandwidth_no_cellular";
    private static final String PREF_CELLULAR_EXPERIMENT = "cellular_experiment";
    private static final String PREF_BLOCK_SCREEN_OBSERVERS = "block_screen_observers";
    private static final String ALLOW_PRERENDER_OLD = "allow_prefetch";
    private static final String PREF_PHYSICAL_WEB = "physical_web";
    private static final String INCOGNITO_ONLY_PREFERENCE = "incognito_only_preference";
    private static final int PHYSICAL_WEB_OFF = 0;
    private static final int PHYSICAL_WEB_ON = 1;
    private static final int PHYSICAL_WEB_ONBOARDING = 2;

    private static PrivacyPreferencesManager sInstance;

    private final Context mContext;
    private final SharedPreferences mSharedPreferences;

    private boolean mCrashUploadingCommandLineDisabled;
    private final String mCrashDumpNeverUpload;
    private final String mCrashDumpWifiOnlyUpload;
    private final String mCrashDumpAlwaysUpload;

    @VisibleForTesting
    PrivacyPreferencesManager(Context context) {
        mContext = context;
        mSharedPreferences = ContextUtils.getAppSharedPreferences();

        // Crash dump uploading preferences.
        // We default the command line flag to disable uploads unless altered on deferred startup
        // to prevent unwanted uploads at startup. If the command line flag to enable uploading is
        // turned on, the other options for when to upload (depending on user/network preferences
        // apply.
        mCrashUploadingCommandLineDisabled = true;
        mCrashDumpNeverUpload = context.getString(R.string.crash_dump_never_upload_value);
        mCrashDumpWifiOnlyUpload = context.getString(R.string.crash_dump_only_with_wifi_value);
        mCrashDumpAlwaysUpload = context.getString(R.string.crash_dump_always_upload_value);
    }

    public static PrivacyPreferencesManager getInstance() {
        if (sInstance == null) {
            sInstance = new PrivacyPreferencesManager(ContextUtils.getApplicationContext());
        }
        return sInstance;
    }

    /**
     * Returns the Crash Dump Upload preference value.
     * @return String value of the preference.
     */
    public String getPrefCrashDumpUploadPreference() {
        return mSharedPreferences.getString(PREF_CRASH_DUMP_UPLOAD, mCrashDumpNeverUpload);
    }

    /**
     * Migrate and delete old preferences.  Note that migration has to happen in Android-specific
     * code because we need to access ALLOW_PRERENDER sharedPreference.
     * TODO(bnc) https://crbug.com/394845. This change is planned for M38. After a year or so, it
     * would be worth considering removing this migration code (also removing accessors in
     * PrefServiceBridge and pref_service_bridge), and reverting to default for users
     * who had set preferences but have not used Chrome for a year. This change would be subject to
     * privacy review.
     */
    public void migrateNetworkPredictionPreferences() {
        PrefServiceBridge prefService = PrefServiceBridge.getInstance();

        // See if PREF_NETWORK_PREDICTIONS is an old boolean value.
        boolean predictionOptionIsBoolean = false;
        try {
            mSharedPreferences.getString(PREF_NETWORK_PREDICTIONS, "");
        } catch (ClassCastException ex) {
            predictionOptionIsBoolean = true;
        }

        // Nothing to do if the user or this migration code has already set the new
        // preference.
        if (!predictionOptionIsBoolean
                && prefService.obsoleteNetworkPredictionOptionsHasUserSetting()) {
            return;
        }

        // Nothing to do if the old preferences are unset.
        if (!predictionOptionIsBoolean
                && !mSharedPreferences.contains(PREF_BANDWIDTH_OLD)
                && !mSharedPreferences.contains(PREF_BANDWIDTH_NO_CELLULAR_OLD)) {
            return;
        }

        // Migrate if the old preferences are at their default values.
        // (Note that for PREF_BANDWIDTH*, if the setting is default, then there is no way to tell
        // whether the user has set it.)
        final String prefBandwidthDefault = BandwidthType.PRERENDER_ON_WIFI.title();
        final String prefBandwidth =
                mSharedPreferences.getString(PREF_BANDWIDTH_OLD, prefBandwidthDefault);
        boolean prefBandwidthNoCellularDefault = true;
        boolean prefBandwidthNoCellular = mSharedPreferences.getBoolean(
                PREF_BANDWIDTH_NO_CELLULAR_OLD, prefBandwidthNoCellularDefault);

        if (!(prefBandwidthDefault.equals(prefBandwidth))
                || (prefBandwidthNoCellular != prefBandwidthNoCellularDefault)) {
            boolean newValue = true;
            // Observe PREF_BANDWIDTH on mobile network capable devices.
            if (isMobileNetworkCapable()) {
                if (mSharedPreferences.contains(PREF_BANDWIDTH_OLD)) {
                    BandwidthType prefetchBandwidthTypePref = BandwidthType.getBandwidthFromTitle(
                            prefBandwidth);
                    if (BandwidthType.NEVER_PRERENDER.equals(prefetchBandwidthTypePref)) {
                        newValue = false;
                    } else if (BandwidthType.PRERENDER_ON_WIFI.equals(prefetchBandwidthTypePref)) {
                        newValue = true;
                    } else if (BandwidthType.ALWAYS_PRERENDER.equals(prefetchBandwidthTypePref)) {
                        newValue = true;
                    }
                }
            // Observe PREF_BANDWIDTH_NO_CELLULAR on devices without mobile network.
            } else {
                if (mSharedPreferences.contains(PREF_BANDWIDTH_NO_CELLULAR_OLD)) {
                    if (prefBandwidthNoCellular) {
                        newValue = true;
                    } else {
                        newValue = false;
                    }
                }
            }
            // Save new value in Chrome PrefService.
            prefService.setNetworkPredictionEnabled(newValue);
        }

        // Delete old sharedPreferences.
        SharedPreferences.Editor sharedPreferencesEditor = mSharedPreferences.edit();
        // Delete PREF_BANDWIDTH and PREF_BANDWIDTH_NO_CELLULAR: just migrated these options.
        if (mSharedPreferences.contains(PREF_BANDWIDTH_OLD)) {
            sharedPreferencesEditor.remove(PREF_BANDWIDTH_OLD);
        }
        if (mSharedPreferences.contains(PREF_BANDWIDTH_NO_CELLULAR_OLD)) {
            sharedPreferencesEditor.remove(PREF_BANDWIDTH_NO_CELLULAR_OLD);
        }
        // Also delete ALLOW_PRERENDER, which was updated based on PREF_BANDWIDTH[_NO_CELLULAR] and
        // network connectivity type, therefore does not carry additional information.
        if (mSharedPreferences.contains(ALLOW_PRERENDER_OLD)) {
            sharedPreferencesEditor.remove(ALLOW_PRERENDER_OLD);
        }
        // Delete bool PREF_NETWORK_PREDICTIONS so that string values can be stored. Note that this
        // SharedPreference carries no information, because it used to be overwritten by
        // kNetworkPredictionEnabled on startup, and now it is overwritten by
        // kNetworkPredictionOptions on startup.
        if (mSharedPreferences.contains(PREF_NETWORK_PREDICTIONS)) {
            sharedPreferencesEditor.remove(PREF_NETWORK_PREDICTIONS);
        }
        sharedPreferencesEditor.apply();
    }

    private NetworkInfo getActiveNetworkInfo() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        return connectivityManager.getActiveNetworkInfo();
    }

    protected boolean isNetworkAvailable() {
        NetworkInfo networkInfo = getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }

    protected boolean isWiFiOrEthernetNetwork() {
        NetworkInfo networkInfo = getActiveNetworkInfo();
        return networkInfo != null
                && (networkInfo.getType() == ConnectivityManager.TYPE_WIFI
                        || networkInfo.getType() == ConnectivityManager.TYPE_ETHERNET);
    }

    protected boolean isMobileNetworkCapable() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        // Android telephony team said it is OK to continue using getNetworkInfo() for our purposes.
        // We cannot use ConnectivityManager#getAllNetworks() because that one only reports enabled
        // networks. See crbug.com/532455.
        @SuppressWarnings("deprecation")
        NetworkInfo networkInfo = connectivityManager
                .getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
        return networkInfo != null;
    }

    /**
     * Checks whether prerender should be allowed and updates the preference if it is not set yet.
     * @return Whether prerendering should be allowed.
     */
    public boolean shouldPrerender() {
        if (!DeviceClassManager.enablePrerendering()) return false;
        migrateNetworkPredictionPreferences();
        return PrefServiceBridge.getInstance().canPrefetchAndPrerender();
    }

    /**
     * Check whether to allow uploading usage and crash reporting. The option should be either
     * "always upload", or "wifi only" with current connection being wifi/ethernet for the
     * three-choice pref or ON for the new two-choice pref.
     *
     * @return boolean whether to allow uploading crash dump.
     */
    private boolean allowUploadCrashDump() {
        if (isCellularExperimentEnabled()) return isUsageAndCrashReportingEnabled();

        if (isMobileNetworkCapable()) {
            String option =
                    mSharedPreferences.getString(PREF_CRASH_DUMP_UPLOAD, mCrashDumpNeverUpload);
            return option.equals(mCrashDumpAlwaysUpload)
                    || (option.equals(mCrashDumpWifiOnlyUpload) && isWiFiOrEthernetNetwork());
        }

        return mSharedPreferences.getBoolean(PREF_CRASH_DUMP_UPLOAD_NO_CELLULAR, false);
    }

    /**
     * Check whether usage and crash reporting set to ON. Also initializes the new pref if
     * necessary.
     *
     * @return boolean whether usage and crash reporting set to ON.
     */
    public boolean isUsageAndCrashReportingEnabled() {
        // If the preference is not set initialize it based on the old preference value.
        if (!mSharedPreferences.contains(PREF_METRICS_REPORTING)) {
            setUsageAndCrashReporting(isUploadCrashDumpEnabled());
        }
        return mSharedPreferences.getBoolean(PREF_METRICS_REPORTING, false);
    }

    /**
     * Sets the usage and crash reporting preference ON or OFF.
     *
     * @param enabled A boolean corresponding whether usage and crash reports uploads are allowed.
     */
    public void setUsageAndCrashReporting(boolean enabled) {
        mSharedPreferences.edit().putBoolean(PREF_METRICS_REPORTING, enabled).apply();
    }

    /**
     * Sets whether cellular experiment is enabled or not.
     */
    @VisibleForTesting
    public void setCellularExperiment(boolean enabled) {
        mSharedPreferences.edit().putBoolean(PREF_CELLULAR_EXPERIMENT, enabled).apply();
    }

    /**
     * Checks whether user is assigned to experimental group for enabling new cellular uploads
     * functionality.
     *
     * @return boolean whether user is assigned to experimental group.
     */
    public boolean isCellularExperimentEnabled() {
        return mSharedPreferences.getBoolean(PREF_CELLULAR_EXPERIMENT, false);
    }

    /**
     * Sets the screen capture blocking is enabled or not.
     */
    @VisibleForTesting
    public void setBlockScreenObservers(boolean enabled) {
        mSharedPreferences.edit().putBoolean(PREF_BLOCK_SCREEN_OBSERVERS, enabled).apply();
    }

    /**
     * Checks whether screen capture blocking is enabled or not.
     *
     * @return boolean whether the screen capture blocking is enabled.
     */
    public boolean isBlockScreenObserversEnabled() {
        return mSharedPreferences.getBoolean(PREF_BLOCK_SCREEN_OBSERVERS, false);
    }


    /**
     * Sets whether this client is in-sample. See
     * {@link org.chromium.chrome.browser.metrics.UmaUtils#isClientInMetricsSample} for details.
     */
    public void setClientInMetricsSample(boolean inSample) {
        mSharedPreferences.edit().putBoolean(PREF_METRICS_IN_SAMPLE, inSample).apply();
    }

    /**
     * Checks whether this client is in-sample. See
     * {@link org.chromium.chrome.browser.metrics.UmaUtils#isClientInMetricsSample} for details.
     *
     * @returns boolean Whether client is in-sample.
     */
    public boolean isClientInMetricsSample() {
        // The default value is true to avoid sampling out crashes that occur before native code has
        // been initialized on first run. We'd rather have some extra crashes than none from that
        // time.
        return mSharedPreferences.getBoolean(PREF_METRICS_IN_SAMPLE, true);
    }

    /**
     * Sets the crash upload preference, which determines whether crash dumps will be uploaded
     * always, never, or only on wifi.
     *
     * @param when A String denoting when crash dump uploading is allowed. One of
     *             R.array.crash_upload_values.
     */
    public void setUploadCrashDump(String when) {
        // Set the crash upload preference regardless of the current connection status.
        boolean canUpload = !when.equals(mCrashDumpNeverUpload);
        PrefServiceBridge.getInstance().setCrashReportingEnabled(canUpload);
    }

    /**
     * Provides a way to remove disabling crash uploading entirely.
     * Enable crash uploading based on user's preference when an overriding flag
     * does not exist in commandline.
     * Used to differentiate from tests that trigger crashers intentionally, so these crashers are
     * not uploaded.
     */
    public void enablePotentialCrashUploading() {
        mCrashUploadingCommandLineDisabled = false;
    }

    /**
     * Check whether crash dump upload preference is set to allow uploads or is set to not allow
     * uploads for any connection type.
     *
     * @return boolean {@code true} if the option is set to allow uploads.
     */
    public boolean isUploadCrashDumpEnabled() {
        if (isMobileNetworkCapable()) {
            return !mSharedPreferences.getString(PREF_CRASH_DUMP_UPLOAD, mCrashDumpNeverUpload)
                            .equals(mCrashDumpNeverUpload);
        }

        return mSharedPreferences.getBoolean(PREF_CRASH_DUMP_UPLOAD_NO_CELLULAR, false);
    }

    /**
     * Sets the value for whether crash stacks may be uploaded.
     */
    public void initCrashUploadPreference(boolean allowCrashUpload) {
        SharedPreferences.Editor ed = mSharedPreferences.edit();
        if (isMobileNetworkCapable()) {
            if (allowCrashUpload) {
                ed.putString(PREF_CRASH_DUMP_UPLOAD, mCrashDumpWifiOnlyUpload);
            } else {
                ed.putString(PREF_CRASH_DUMP_UPLOAD, mCrashDumpNeverUpload);
            }
        } else {
            ed.putString(PREF_CRASH_DUMP_UPLOAD, mCrashDumpNeverUpload);
            ed.putBoolean(PREF_CRASH_DUMP_UPLOAD_NO_CELLULAR, allowCrashUpload);
        }
        ed.apply();
        if (isCellularExperimentEnabled()) setUsageAndCrashReporting(allowCrashUpload);
        syncUsageAndCrashReportingPrefs();
    }

    /**
     * Check whether to allow uploading crash dump now.
     * {@link #allowUploadCrashDump()} should return {@code true},
     * and the network should be connected as well.
     *
     * This function should not result in a native call as it can be called in circumstances where
     * natives are not guaranteed to be loaded.
     *
     * @return whether to allow uploading crash dump now.
     */
    @Override
    public boolean isUploadPermitted() {
        return !mCrashUploadingCommandLineDisabled && isNetworkAvailable()
                && (allowUploadCrashDump() || isUploadEnabledForTests());
    }

    /**
     * Check whether to allow UMA uploading.
     *
     * TODO(asvitkine): This is temporary split up from isUploadPermitted() above with
     * the |mCrashUploadingCommandLineDisabled| check removed, in order to diagnose if
     * that check is responsible for decreased UMA uploads in M49. http://crbug.com/602703
     *
     * This function should not result in a native call as it can be called in circumstances where
     * natives are not guaranteed to be loaded.
     *
     * @return whether to allow UMA uploading.
     */
    @Override
    public boolean isUmaUploadPermitted() {
        return isNetworkAvailable() && (allowUploadCrashDump() || isUploadEnabledForTests());
    }

    /**
     * Check whether not to disable uploading crash dump by command line flag.
     * If command line flag disables crash dump uploading, do not retry, but also do not delete.
     * TODO(jchinlee): this is not quite a boolean. Depending on other refactoring, change to enum.
     *
     * @return whether experimental flag doesn't disable uploading crash dump.
     */
    @Override
    public boolean isUploadCommandLineDisabled() {
        return mCrashUploadingCommandLineDisabled;
    }

    /**
     * Check whether the user allows uploading.
     * This doesn't take network condition or experimental state (i.e. disabling upload) into
     * consideration.
     * A crash dump may be retried if this check passes.
     *
     * @return whether user's preference allows uploading crash dump.
     */
    @Override
    public boolean isUploadUserPermitted() {
        // If user is in cellular experiment read new two-option prefs.
        if (isCellularExperimentEnabled()) return isUsageAndCrashReportingEnabled();

        // If user is not in cellular experiment read old three-option prefs.
        return isUploadCrashDumpEnabled();
    }

    /**
     * Check whether uploading crash dump should be in constrained mode based on user experiments
     * and current connection type. This function shows whether in general uploads should be limited
     * for this user and does not determine whether crash uploads are currently possible or not. Use
     * |isUploadPermitted| function for that before calling |isUploadLimited|.
     *
     * @return whether uploading logic should be constrained.
     */
    @Override
    public boolean isUploadLimited() {
        return isCellularExperimentEnabled() && !isWiFiOrEthernetNetwork();
    }

    /**
     * Sets the Physical Web preference, which enables background scanning for bluetooth beacons
     * and displays a notification when beacons are found.
     *
     * @param enabled A boolean indicating whether to notify on nearby beacons.
     */
    public void setPhysicalWebEnabled(boolean enabled) {
        int state = enabled ? PHYSICAL_WEB_ON : PHYSICAL_WEB_OFF;
        boolean isOnboarding = isPhysicalWebOnboarding();
        mSharedPreferences.edit().putInt(PREF_PHYSICAL_WEB, state).apply();
        if (enabled) {
            if (!isOnboarding) {
                PhysicalWeb.startPhysicalWeb();
            }
        } else {
            PhysicalWeb.stopPhysicalWeb();
        }
    }

    /**
     * Check whether the user is still in the Physical Web onboarding flow.
     *
     * @return boolean {@code true} if onboarding is not yet complete.
     */
    public boolean isPhysicalWebOnboarding() {
        int state = mSharedPreferences.getInt(PREF_PHYSICAL_WEB, PHYSICAL_WEB_ONBOARDING);
        return (state == PHYSICAL_WEB_ONBOARDING);
    }

    /**
     * Check whether Physical Web is configured to notify on nearby beacons.
     *
     * @return boolean {@code true} if the feature is enabled.
     */
    public boolean isPhysicalWebEnabled() {
        int state = mSharedPreferences.getInt(PREF_PHYSICAL_WEB, PHYSICAL_WEB_ONBOARDING);
        return (state == PHYSICAL_WEB_ON);
    }

    /**
     * Check whether the command line switch is used to force uploading if at all possible. Used by
     * test devices to avoid UI manipulation.
     *
     * @return whether uploading should be enabled if at all possible.
     */
    @Override
    public boolean isUploadEnabledForTests() {
        return CommandLine.getInstance().hasSwitch(ChromeSwitches.FORCE_CRASH_DUMP_UPLOAD);
    }

    /**
     * Update usage and crash preferences based on Android preferences in case they are out of
     * sync.
     */
    public void syncUsageAndCrashReportingPrefs() {
        boolean isUploadUserPermitted = isUploadUserPermitted();
        if (isCellularExperimentEnabled()) {
            PrefServiceBridge.getInstance().setMetricsReportingEnabled(isUploadUserPermitted);
        }

        PrefServiceBridge.getInstance().setCrashReportingEnabled(isUploadUserPermitted);
    }

    /**
     * Check whether Incognito Only preference is enabled
     *
     * @return whether Incognito Only preference is enabled, false by default
     */
    public boolean isIncognitoOnlyEnabled() {
        return mSharedPreferences.getBoolean(INCOGNITO_ONLY_PREFERENCE, false);
    }

    /**
     * Sets the Incognito Only preference, which enables the browser to switch to incognito only
     * mode
     *
     * @param enabled A boolean indicating whether to switch to incognito only mode.
     */
    public void setIncognitoOnlyEnabled(boolean enabled) {
        mSharedPreferences.edit().putBoolean(INCOGNITO_ONLY_PREFERENCE, enabled).apply();
    }
}

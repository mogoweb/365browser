/*
 * Copyright (c) 2016, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.chromium.chrome.browser.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.StrictMode;
import android.os.AsyncTask;
import android.text.TextUtils;

import org.chromium.base.ObserverList;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeSwitches;
import org.chromium.chrome.browser.UpdateCheckService;
import org.chromium.chrome.browser.util.Logger;
import org.chromium.base.ContextUtils;
import org.chromium.chrome.browser.preferences.website.ContentSetting;
import org.chromium.chrome.browser.preferences.website.SiteSettingsCategory;
import org.chromium.chrome.browser.preferences.website.Website;
import org.chromium.chrome.browser.preferences.website.WebsitePermissionsFetcher;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.content.browser.SecureConnect;
import org.chromium.content_public.browser.LoadUrlParams;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Formatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Handler for secure connect that deals with initializing and handling
 * its settings
 */
public class SecureConnectPreferenceHandler {
    public static final String EXTRA_SECURE_CONNECT_PARCEL = "extra_secure_connect_info";

    private static final String SECURE_CONNECT_MAINFRAME_KEY = "secure_connect_mainframe";
    private static final String SECURE_CONNECT_SUBFRAME_KEY = "secure_connect_subframe";

    private static boolean mSecureConnectSetupComplete = false;
    private static boolean mSecureConnectInitComplete = false;
    private static HashMap<String, ContentSetting> mIncognitoPermissions;

    private static final String SECURE_CONNECT_SERVICE_PREF = "secure_connect_update_service";

    private static ObserverList<SecureConnect.Listener> mListenerObservers =
            new ObserverList<SecureConnect.Listener>();

    public static void addObserver(SecureConnect.Listener obs) {
        mListenerObservers.addObserver(obs);
    }

    public static void removeObserver(SecureConnect.Listener obs) {
        mListenerObservers.removeObserver(obs);
    }

    public static ArrayList<SecureConnect.URLInfo> getSortedRules(SecureConnect.URLInfo[] urls,
                                                          HashMap<String, Boolean> updatedRules) {
        if (urls == null) return new ArrayList<>();
        Set<String> infoSet = new HashSet<>();
        ArrayList<SecureConnect.URLInfo> sortedList = new ArrayList<>();
        for (SecureConnect.URLInfo urlInfo : urls) {
            if (!infoSet.contains(urlInfo.mRulesetName)) {
                if (updatedRules != null && updatedRules.containsKey(urlInfo.mRulesetName)) {
                    urlInfo = new SecureConnect.URLInfo(urlInfo.mURL, urlInfo.mUpgraded,
                                    urlInfo.mRulesetName, updatedRules.get(urlInfo.mRulesetName),
                                    urlInfo.mDisableReason);
                }
                infoSet.add(urlInfo.mRulesetName);
                sortedList.add(urlInfo);
            }
        }

        Collections.sort(sortedList, new URLInfoComparator());
        return sortedList;
    }

    private static class URLInfoComparator implements Comparator<SecureConnect.URLInfo> {

        @Override
        public int compare(SecureConnect.URLInfo lhs, SecureConnect.URLInfo rhs) {
            if (!lhs.mRulesetEnabled || !rhs.mRulesetEnabled) {
                if (lhs.mRulesetEnabled) return 1;
                else if (rhs.mRulesetEnabled) return -1;
            }
            return 0;
        }
    }

    public static class StatusParcel implements Parcelable {
        SecureConnect.Info mInfo;

        public StatusParcel(SecureConnect.Info info) {
            mInfo = info;
        }

        protected StatusParcel(Parcel in) {
            boolean hasMainframe = in.readInt() == 1;
            int numUrlsNotUpgraded = in.readInt();
            int subFrameUrlCount = in.readInt();

            SecureConnect.URLInfo mainFrame = null;
            SecureConnect.URLInfo[] subFrames = new SecureConnect.URLInfo[subFrameUrlCount];

            if (hasMainframe) mainFrame = readURLInfo(in);
            for (int i = 0; i < subFrameUrlCount; i++) {
                subFrames[i] = readURLInfo(in);
            }
            mInfo = new SecureConnect.Info(mainFrame, subFrames, numUrlsNotUpgraded);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            if (mInfo == null) return;

            dest.writeInt(mInfo.hasMainFrameUrl() ? 1 : 0);
            dest.writeInt(mInfo.mNumURLsNotUpgraded);
            dest.writeInt(mInfo.getSubFrameUrlUpgradeCount());

            if (mInfo.hasMainFrameUrl()) writeURLInfo(dest, mInfo.mMainFrameURL);
            for (int i = 0; i < mInfo.getSubFrameUrlUpgradeCount(); i++) {
                writeURLInfo(dest, mInfo.mSubFrameURLs[i]);
            }
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<StatusParcel> CREATOR = new Creator<StatusParcel>() {
            @Override
            public StatusParcel createFromParcel(Parcel in) {
                return new StatusParcel(in);
            }

            @Override
            public StatusParcel[] newArray(int size) {
                return new StatusParcel[size];
            }
        };

        public SecureConnect.Info getInfo() {
            return mInfo;
        }

        private void writeURLInfo(Parcel dest, SecureConnect.URLInfo info) {
            dest.writeString(info.mURL);
            dest.writeInt(info.mUpgraded ? 1 : 0);
            dest.writeString(info.mRulesetName);
            dest.writeInt(info.mRulesetEnabled ? 1 : 0);
            dest.writeString(info.mDisableReason);
        }

        private SecureConnect.URLInfo readURLInfo(Parcel in) {
            return new SecureConnect.URLInfo(
                    in.readString(),    // mURL
                    in.readInt() == 1,  // mUpgraded
                    in.readString(),    // mRulesetName
                    in.readInt() == 1,  // mRulesetEnabled
                    in.readString()     // mDisableReason
            );
        }
    }

    public static StatusParcel getParcel(ContentViewCore cvc) {
        if (!isInitialized()) return null;

        SecureConnect.Info info = getInfo(cvc);
        return new StatusParcel(info);
    }

    /**
     * Sets up secure connect when the browser initializes.
     */
    static public void applyInitialPreferences() {
        if (SecureConnect.isInitialized() && !mSecureConnectSetupComplete) {
            PrefServiceBridge.getInstance().setSecureConnectDefault();
            boolean allowed = PrefServiceBridge.getInstance().isSecureConnectEnabled();
            setSecureConnectEnabled(allowed);

            SecureConnect.getInstance().setListener(new SecureConnect.Listener() {
                @Override
                public void onPageUpgrade(String url, Boolean mainFrame) {
                    updateSecureConnectStats(mainFrame);
                    for (SecureConnect.Listener listener : mListenerObservers) {
                        listener.onPageUpgrade(url, mainFrame);
                    }
                }

                @Override
                public void onUpgradeFailed(String origin, String url, String redirect, int error) {
                    for (SecureConnect.Listener listener : mListenerObservers) {
                        listener.onUpgradeFailed(origin, url, redirect, error);
                    }
                }
            });

            WebsitePermissionsFetcher fetcher = new WebsitePermissionsFetcher(
                    new WebsitePermissionsFetcher.WebsitePermissionsCallback() {
                        @Override
                        public void onWebsitePermissionsAvailable(
                                Collection<Website> websites) {
                            ArrayList<String> allowList = new ArrayList<>();
                            ArrayList<String> blockList = new ArrayList<>();

                            for (Website site : websites) {
                                ContentSetting permission = site.getSecureConnectPermission();

                                if (permission != null) {
                                    if (permission == ContentSetting.ALLOW) {
                                        allowList.add(site.getAddress().getOrigin());
                                    } else if (permission == ContentSetting.BLOCK) {
                                        blockList.add(site.getAddress().getOrigin());
                                    }
                                }
                            }
                            if (!allowList.isEmpty()) {
                                SecureConnect.getInstance().setPermissionForOrigins(
                                        allowList.toArray(new String[allowList.size()]),
                                        SecureConnect.PERMISSION_ENABLE, false);
                            }

                            if (!blockList.isEmpty()) {
                                SecureConnect.getInstance().setPermissionForOrigins(
                                        blockList.toArray(new String[blockList.size()]),
                                        SecureConnect.PERMISSION_DISABLE, false);
                            }
                            mSecureConnectSetupComplete = true;
                        }
                    }
            );

            fetcher.fetchPreferencesForCategory(SiteSettingsCategory.fromString(SiteSettingsCategory
                    .CATEGORY_SECURE_CONNECT));
        }
    }

    public static void setSecureConnectEnabled(boolean enabled) {
        if (!SecureConnect.isInitialized()) return;
        SecureConnect.getInstance().setDefaultPermission(enabled);
    }

    public static boolean isInitialized() {
        return SecureConnect.isInitialized();
    }

    public static void initializeGlobalInstance(Context ctx) {
        if (!mSecureConnectInitComplete) {
            final StrictMode.ThreadPolicy oldPolicy = StrictMode.getThreadPolicy();
            StrictMode.ThreadPolicy tmpPolicy = new StrictMode.ThreadPolicy.Builder(oldPolicy)
                    .permitDiskReads()
                    .permitDiskWrites()
                    .build();
            StrictMode.setThreadPolicy(tmpPolicy);

            mSecureConnectInitComplete = true;
            SecureConnect.Initialize(ctx);
            final String serverURL = ctx.getString(R.string.swe_secure_connect_ruleset_url);
            if (TextUtils.isEmpty(serverURL) || serverURL.equalsIgnoreCase("about:blank"))
                return;

            final String fileName = ctx.getApplicationInfo().dataDir + "/secure_connect/rules.dat";
            new UpdateCheckService(ctx, SECURE_CONNECT_SERVICE_PREF, serverURL, fileName,
                    new UpdateCheckService.UpdateServiceEventListener() {
                        @Override
                        public void updateComplete(boolean success) {
                            if (!success)
                                return;

                            File file = new File(fileName);
                            if (SecureConnect.isInitialized() &&
                                    SecureConnect.getInstance().validateRules(file)) {
                                SecureConnect.getInstance().unloadAllRules();
                                SecureConnect.getInstance().loadRules(file);
                                SecureConnect.getInstance().commitRules();
                            }
                        }

                        @Override
                        public void updateProgress(int bytesRead) {

                        }

                        @Override
                        public boolean overrideInterval() {
                            return false;
                        }
                    }).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

            StrictMode.setThreadPolicy(oldPolicy);
        }
    }

    public static void useDefaultPermissionForOrigins(String origin, boolean isIncognito) {
        if (!isInitialized()) return;
        String[] origins = new String[1];
        origins[0] = origin;
        SecureConnect.getInstance().setPermissionForOrigins(origins,
                SecureConnect.PERMISSION_USE_DEFAULT, isIncognito);
    }

    public static void setSecureConnectSettingForOrigin(String origin, boolean enabled,
                                                        boolean isIncognito) {
        if (!isInitialized()) return;
        String[] origins = new String[1];
        origins[0] = origin;
        int permission = enabled ? SecureConnect.PERMISSION_ENABLE
                                 : SecureConnect.PERMISSION_DISABLE;
        SecureConnect.getInstance().setPermissionForOrigins(origins, permission, isIncognito);
    }

    private static void updateSecureConnectStats(boolean mainFrame) {
        SharedPreferences sharedPreferences = ContextUtils.getAppSharedPreferences();
        String key = mainFrame ? SECURE_CONNECT_MAINFRAME_KEY : SECURE_CONNECT_SUBFRAME_KEY;
        long lastCount = sharedPreferences.getLong(key, 0);
        sharedPreferences.edit().putLong(key, ++lastCount).apply();
    }

    public static long getPageUpgradeCount(boolean mainFrame) {
        SharedPreferences sharedPreferences = ContextUtils.getAppSharedPreferences();
        String key = mainFrame ? SECURE_CONNECT_MAINFRAME_KEY : SECURE_CONNECT_SUBFRAME_KEY;
        return sharedPreferences.getLong(key, 0);
    }

    public static String[] getDisabledRulesets() {
        if (isInitialized()) {
            return SecureConnect.getInstance().getRulesets(true);
        }
        return null;
    }

    public static SecureConnect.Info getInfo(ContentViewCore contentViewCore) {
        if (!SecureConnect.isInitialized()) return null;
        return SecureConnect.getInstance().getInfo(contentViewCore);
    }

    public static void addIncognitoOrigin(String origin, ContentSetting permission) {
        setSecureConnectSettingForOrigin(origin, permission == ContentSetting.ALLOW, true);
        if (mIncognitoPermissions == null) {
            mIncognitoPermissions = new HashMap<>();
        }
        mIncognitoPermissions.put(origin, permission);
    }

    public static ContentSetting getSettingForIncognitoOrigin(String origin) {
        if (mIncognitoPermissions != null && mIncognitoPermissions.containsKey(origin)) {
            return mIncognitoPermissions.get(origin);
        }
        return null;
    }

    public static void clearIncognitoOrigin(String origin) {
        if (mIncognitoPermissions != null && mIncognitoPermissions.containsKey(origin)) {
            mIncognitoPermissions.remove(origin);
            useDefaultPermissionForOrigins(origin, true);
        }
    }

    public static void onIncognitoSessionFinish() {
        mIncognitoPermissions = null;
        if (isInitialized())
            SecureConnect.getInstance().resetAllIncognitoPermissions();
    }

    public static void updateRuleset(String ruleset, boolean enabled) {
        if (isInitialized()) {
            PrefServiceBridge.getInstance().requestReload();
            SecureConnect.getInstance().changeRulesetState(ruleset, enabled);
        }
    }

    public static void setSecureContentOnlyMode(boolean enabled) {
        if (isInitialized()) {
            SecureConnect.getInstance().setSecureContentOnlyMode(enabled);
        }
    }

    public static boolean getSecureContentOnlyEnabled() {
        if (isInitialized()) {
            return SecureConnect.getInstance().getSecureContentOnlyMode();
        }
        return false;
    }

    /**
     * This method tries to load the original url before secure connect's redirect.
     * If a setting for the origin url was modified, this will ensure that setting
     * has a chance to take effect.
     * @param tab that might have been upgraded.
     */
    public static void reloadTabIfNeeded(Tab tab) {
        if (tab == null) return;

        SecureConnect.Info info = getInfo(tab.getContentViewCore());
        if (info != null && info.wasMainFrameUpgraded()) {
            tab.loadUrl(new LoadUrlParams(info.mMainFrameURL.mURL));
        } else {
            tab.reload();
        }
    }

    /**
     * This method gets a formatted string for the preference that controls
     * Secure Connect.
     * @param resources to get strings.
     * @param info The Secure Connect information for a tab
     * @param value The new value for which a status message is required.
     * @param moduleName The displayed name of Secure Connect. (The library can control this)
     * @return A formatted message based on the input parameters.
     */
    public static String getStatusMessage(Resources resources, SecureConnect.Info info,
                                          ContentSetting value, String moduleName) {
        Formatter formatter = new Formatter();
        if (info != null) {
            String subFrameUpgradeCountString = "<b>" + info.getSubFrameUrlUpgradeCount() + "</b>";
            if (info.hasMixedContent()) {
                // Mixed content. Show a warning. Doesn't depend on value.
                return resources.getString(R.string.tooltip_favicon_page_mixed_content);
            } else if (info.wasMainFrameUpgraded()) {
                // Mainframe redirected. Show previous website and SubFrame count if any.
                String mainFrameUrl = "<b>" + info.mMainFrameURL.mURL + "</b>";
                if (value == ContentSetting.ALLOW) {
                    String title = resources.getString(R.string.secure_connect_was_redirected);
                    if (info.getSubFrameUrlUpgradeCount() > 0) {
                        if (info.getSubFrameUrlUpgradeCount() == 1) {
                            title = resources.getString(
                                    R.string.secure_connect_was_redirected_with_connection);
                        } else {
                            title = resources.getString(
                                    R.string.secure_connect_was_redirected_with_connection_plural);
                        }
                        return formatter.format(title, subFrameUpgradeCountString, mainFrameUrl)
                                .toString();
                    }
                    return formatter.format(title, mainFrameUrl).toString();
                } else {
                    return formatter.format(resources.getString(R.string
                            .secure_connect_disable_for_redirected_origin, moduleName,
                            mainFrameUrl)).toString();
                }
            } else if (info.getSubFrameUrlUpgradeCount() > 0 && value == ContentSetting.ALLOW) {
                // SubFrames were redirected. Show the count.
                if (info.getSubFrameUrlUpgradeCount() > 1) {
                    return formatter.format(resources.getString(
                            R.string.secure_connect_subframes_upgraded_plural),
                            subFrameUpgradeCountString).toString();
                } else {
                    return formatter.format(resources.getString(
                            R.string.secure_connect_subframes_upgraded),
                            subFrameUpgradeCountString).toString();
                }
            }
        }

        if (value == ContentSetting.ALLOW) {
            return formatter.format(resources.getString(R.string.secure_connect_enabled_for_origin)
                    , moduleName).toString();
        } else {
            return formatter.format(resources.getString(R.string.secure_connect_disabled_for_origin)
                    , moduleName).toString();
        }
    }
}

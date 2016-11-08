/*
 * Copyright (c) 2015-2016, The Linux Foundation. All rights reserved.
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

package org.chromium.chrome.browser.preferences.website;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.URLUtil;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.ApplicationStatus;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ContentSettingsType;
import org.chromium.chrome.browser.favicon.FaviconHelper;
import org.chromium.chrome.browser.preferences.BrowserPrivacyMeterPreference;
import org.chromium.chrome.browser.preferences.BrowserSecurityInfoPreference;
import org.chromium.chrome.browser.preferences.BrowserSwitchPreferenceCategory;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.preferences.SecureConnectPreferenceHandler;
import org.chromium.chrome.browser.preferences.TextMessagePreference;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.util.ColorUtils;
import org.chromium.chrome.browser.widget.TintedImageView;
import org.chromium.components.security_state.ConnectionSecurityLevel;
import org.chromium.components.url_formatter.UrlFormatter;
import org.chromium.content.browser.SecureConnect;
import org.chromium.content.browser.WebDefender;
import org.chromium.content.browser.WebRefiner;

import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.Formatter;
import java.util.Locale;

public class BrowserSingleWebsitePreferences extends SingleWebsitePreferences {

    public static final String EXTRA_SECURITY_CERT_LEVEL = "org.chromium.chrome.preferences." +
            "website_security_cert_level";
    public static final String EXTRA_FAVICON = "org.chromium.chrome.preferences.favicon";
    public static final String EXTRA_WEB_REFINER_ADS_INFO = "website_refiner_ads_info";
    public static final String EXTRA_WEB_REFINER_TRACKER_INFO = "website_refiner_tracker_info";
    public static final String EXTRA_WEB_REFINER_MALWARE_INFO = "website_refiner_malware_info";
    public static final String EXTRA_INCOGNITO = "website_incognito";
    public static final String WEBDEFENDER_SETTING = "webDefender_setting";
    public static final String WEBREFINER_SETTING = "webRefiner_setting";
    public static final String SECURE_CONNECT_SETTING = "secure_connect_setting";

    private static final String PREF_TEMPORARY_DOMAINS = "tracker_domains";

    private int mSiteColor = -1;
    private String mWebRefinerMessages;
    private boolean mIsIncognito;

    private Website mSiteBeforeRedirect;
    private BrowserSecurityInfoPreference mSecurityInfo;
    private Preference mSecurityInfoCategory;
    private WebDefender.ProtectionStatus mWebDefenderStatus;
    private SecureConnect.Info mSecureConnectPageInfo;
    private BrowserPrivacyMeterPreference mPrivacyMeter;
    private String mSmartProtectName;
    private String mWebRefinerName;
    private String mWebDefenderName;
    private String mSecureConnectName;
    private int mSmartProtectColor;
    private int mWebRefinerBlockedCount;
    BrowserSwitchPreferenceCategory mWebRefinerCategory;
    BrowserSwitchPreferenceCategory mWebDefenderCategory;
    BrowserSwitchPreferenceCategory mSecureConnectCategory;

    public static class WebRefinerStatsInfo {
        public int mBlockedCount;
        public String mFormattedMsg;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
        View view = super.onCreateView(inflater, container, bundle);
        ListView list = null;
        if (view != null) {
            list = (ListView) view.findViewById(android.R.id.list);
        }

        if (list == null) {
            return view;
        }

        list.setOnHierarchyChangeListener(
                new ViewGroup.OnHierarchyChangeListener() {
                    @Override
                    public void onChildViewAdded(View parent, View child) {
                        onChildViewAddedToHierarchy(parent, child);
                    }

                    @Override
                    public void onChildViewRemoved(View parent, View child) {

                    }
                }
        );
        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        Bundle arguments = getArguments();
        if (arguments != null) {
            WebRefinerStatsInfo info = getWebRefinerInformation(getResources(), arguments);
            mWebRefinerBlockedCount = info.mBlockedCount;
            mWebRefinerMessages = info.mFormattedMsg;
            mIsIncognito = arguments.getBoolean(EXTRA_INCOGNITO);
            WebDefenderPreferenceHandler.StatusParcel parcel = arguments.getParcelable(
                    SmartProtectDetailsPreferences.EXTRA_SMARTPROTECT_PARCEL);
            if (parcel != null)
                mWebDefenderStatus = parcel.getStatus();
            SecureConnectPreferenceHandler.StatusParcel secureConnectParcel =
                    arguments.getParcelable(SecureConnectPreferenceHandler.
                            EXTRA_SECURE_CONNECT_PARCEL);
            if (secureConnectParcel != null) {
                mSecureConnectPageInfo = secureConnectParcel.getInfo();
                if (mSecureConnectPageInfo.wasMainFrameUpgraded()) {
                    mSiteBeforeRedirect = new Website(WebsiteAddress
                            .create(mSecureConnectPageInfo.mMainFrameURL.mURL), null);
                }
            }
        }
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    protected void onAllSitesAvailable(Collection<Website> allSites) {
        if (mSiteBeforeRedirect != null) {
            mSiteBeforeRedirect = mergePermissionInfoForTopLevelOrigin(
                    mSiteBeforeRedirect.getAddress(), allSites);
        }
    }

    @Override
    protected Drawable getEnabledIcon(int contentType) {
        if (mSiteColor == -1) return super.getEnabledIcon(contentType);
        Drawable icon = ApiCompatibilityUtils.getDrawable(getResources(),
                ContentSettingsResources.getIcon(contentType));

        if (icon == null || contentType == ContentSettingsType.CONTENT_SETTINGS_TYPE_WEBDEFENDER
                || contentType == ContentSettingsType.CONTENT_SETTINGS_TYPE_WEBREFINER
                || contentType == ContentSettingsType.CONTENT_SETTINGS_TYPE_SECURE_CONNECT)
            return icon;
        icon.mutate();
        icon.setColorFilter(mSiteColor, PorterDuff.Mode.SRC_IN);
        return icon;
    }

    private int getIndex(ContentSetting value) {
        int returnValue;
        switch(value) {
            case ALLOW:
            case ASK:
                returnValue = 0;
                break;
            case BLOCK:
                returnValue = 1;
                break;
            case ALLOW_24H:
                returnValue = 2;
                break;
            default:
                returnValue = -1;
                break;
        }
        return returnValue;
    }

    private boolean add24HourItem(Preference preference) {
        switch(preference.getKey()) {
            case PREF_LOCATION_ACCESS:
            case PREF_CAMERA_CAPTURE_PERMISSION:
            case PREF_MIC_CAPTURE_PERMISSION:
                return true;
            default:
                return false;
        }
    }

    @Override
    protected void setUpListPreference(Preference preference, ContentSetting value) {
        if (mIsIncognito) {
            getPreferenceScreen().removePreference(preference);
        } else {
            boolean hasWrittenValue = value != null;
            if(add24HourItem(preference)) {
                if (value == null) {
                    value = getGlobalDefaultPermission(preference);
                    if (value == null) {
                        getPreferenceScreen().removePreference(preference);
                        return;
                    }
                }

                ListPreference listPreference = (ListPreference) preference;
                int contentType = getContentSettingsTypeFromPreferenceKey(preference.getKey());

                int listNum = 3;
                CharSequence[] keys = new String[listNum];
                CharSequence[] descriptions = new String[listNum];
                keys[0] = ContentSetting.ALLOW.toString();
                keys[1] = ContentSetting.BLOCK.toString();
                keys[2] = ContentSetting.ALLOW_24H.toString();

                descriptions[0] = getResources().getString(
                        ContentSettingsResources.getSiteSummary(ContentSetting.ALLOW));
                descriptions[1] = getResources().getString(
                        ContentSettingsResources.getSiteSummary(ContentSetting.BLOCK));
                descriptions[2] = getResources().getString(
                        ContentSettingsResources.getSiteSummary(ContentSetting.ALLOW_24H));

                if (value == ContentSetting.ALLOW_24H) {
                    Date expiry = new Date(PrefServiceBridge.getExpiryForContentType(contentType,
                            mSite.getAddress().getOrigin()));
                    Date now = new Date();
                    if (now.after(expiry)) {
                        value = getGlobalDefaultPermission(preference);
                    }
                }

                listPreference.setEntryValues(keys);
                listPreference.setEntries(descriptions);
                int index = getIndex(value);
                if (index < 0) { //reset to default if index was out of bounds(version upgrade)
                    value = getGlobalDefaultPermission(preference);
                    index = getIndex(ContentSetting.ASK);
                }
                listPreference.setValueIndex(index);
                int explanationResourceId = ContentSettingsResources.getExplanation(contentType);
                if (explanationResourceId != 0) {
                    listPreference.setTitle(explanationResourceId);
                }

                if (listPreference.isEnabled()) {
                    SiteSettingsCategory category =
                            SiteSettingsCategory.fromContentSettingsType(contentType);
                    if (category != null && !category.enabledInAndroid(getActivity())) {
                        listPreference.setIcon(category.getDisabledInAndroidIcon(getActivity()));
                        listPreference.setEnabled(false);
                    } else {
                        listPreference.setIcon(getEnabledIcon(contentType));
                    }
                } else {
                    listPreference.setIcon(
                        ContentSettingsResources.getDisabledIcon(contentType, getResources()));
                }
                preference.setSummary("%s");
                updateSummary(preference, contentType, value);
                listPreference.setOnPreferenceChangeListener(this);
            } else {
                super.setUpListPreference(preference, value);
            }
            int type = getContentSettingsTypeFromPreferenceKey(preference.getKey());
            if (PrefServiceBridge.getInstance().isRestrictedToSecureOrigins(type)
                    && !URLUtil.isHttpsUrl(mSite.getAddress().getOrigin()) && !hasWrittenValue) {
                preference.setEnabled(false);
                preference.setIcon(R.drawable.ic_sp_level_warning);
            }
        }
    }

    private String getFormattedExpiry(Date expiry) {
        String remainingTimeString = getResources().getString(
                R.string.website_settings_permission_allowed_until);
        SimpleDateFormat simplifiedDate =
                new SimpleDateFormat(" EEE, MMM d, hh:mm aaa ", Locale.getDefault());

        return remainingTimeString + simplifiedDate.format(expiry);
    }

    protected void setupBrowserPreferences() {
        TextMessagePreference siteTitle = (TextMessagePreference) findPreference("site_title");
        Bundle args = getArguments();
        if (siteTitle != null && args != null) {
            byte[] data = args.getByteArray(EXTRA_FAVICON);
            if (data != null) {
                Bitmap bm = BitmapFactory.decodeByteArray(data, 0, data.length);
                if (bm != null) {
                    Bitmap bitmap = Bitmap.createScaledBitmap(bm, 150, 150, true);
                    Drawable drawable = new BitmapDrawable(getResources(), bitmap);
                    siteTitle.setIcon(drawable);
                }
            }
        }

        mSecurityInfo = (BrowserSecurityInfoPreference)
                findPreference("site_security_info");
        mSecurityInfoCategory = findPreference("site_security_info_title");
        if (mSecurityInfo != null) {
            int securityLevel = (args != null) ? args.getInt(EXTRA_SECURITY_CERT_LEVEL) : 0;
            mSecurityInfo.setupSecurityInformation(securityLevel);
        }

        mSmartProtectColor = ApiCompatibilityUtils.getColor(getResources(), R.color.smart_protect);
        mSmartProtectName = getResources().getString(R.string.swe_security_branding_label);

        mWebDefenderName = getString(ContentSettingsResources
                .getTitle(ContentSettingsType.CONTENT_SETTINGS_TYPE_WEBDEFENDER));

        mWebRefinerName = getString(ContentSettingsResources
                .getTitle(ContentSettingsType.CONTENT_SETTINGS_TYPE_WEBREFINER));

        mSecureConnectName = getString(ContentSettingsResources
                .getTitle(ContentSettingsType.CONTENT_SETTINGS_TYPE_SECURE_CONNECT));

        SmartProtectPreferenceCategory smartProtect =
                (SmartProtectPreferenceCategory) findPreference("smartprotect_title");
        mPrivacyMeter = (BrowserPrivacyMeterPreference) findPreference("webdefender_privacy_meter");
        String title = (mSiteAddress != null)
                ? mSiteAddress.getTitle() : mSite.getAddress().getTitle();
        if (mPrivacyMeter != null) {
            mPrivacyMeter.setSummary(title);
            mPrivacyMeter.setupPrivacyMeter(getWebDefenderPermission(), getWebRefinerPermission(),
                    mWebDefenderStatus, mWebRefinerBlockedCount);
        }
        Preference details = findPreference("webdefender_details");
        mWebDefenderCategory =
                (BrowserSwitchPreferenceCategory) findPreference("webdefender_title");
        mWebRefinerCategory =
                (BrowserSwitchPreferenceCategory) findPreference("webrefiner_title");
        mSecureConnectCategory =
                (BrowserSwitchPreferenceCategory) findPreference("secure_connect_title");

        if (!WebDefenderPreferenceHandler.isInitialized()
                && !WebRefinerPreferenceHandler.isInitialized()
                && !SecureConnectPreferenceHandler.isInitialized()) {
            getPreferenceScreen().removePreference(details);
            getPreferenceScreen().removePreference(mPrivacyMeter);
            getPreferenceScreen().removePreference(smartProtect);
            return;
        }

        mWebRefinerCategory.setDisplayProperties(mWebRefinerName, mSmartProtectColor, 0,
                getWebRefinerPermission(),
                new BrowserSwitchPreferenceCategory.ModulePreferenceToggled() {
                    @Override
                    public void onSettingToggled(boolean newSetting) {
                        mPrivacyMeter.refreshMeter(getWebDefenderPermission(), newSetting);
                        setWebRefinerPermission(newSetting);
                    }
                });
        mWebDefenderCategory.setDisplayProperties(mWebDefenderName, mSmartProtectColor, 0,
                getWebDefenderPermission(),
                new BrowserSwitchPreferenceCategory.ModulePreferenceToggled() {
                    @Override
                    public void onSettingToggled(boolean newSetting) {
                        mPrivacyMeter.refreshMeter(getWebDefenderPermission(), newSetting);
                        setWebDefenderPermission(newSetting);
                        mPrivacyMeter.refreshMeter(newSetting, getWebRefinerPermission());
                    }
                });
        mSecureConnectCategory.setDisplayProperties(mSecureConnectName, mSmartProtectColor, 0,
                getSecureConnectPermission(),
                new BrowserSwitchPreferenceCategory.ModulePreferenceToggled() {
                    @Override
                    public void onSettingToggled(boolean newSetting) {
                        setSecureConnectPermission(newSetting);
                    }
                });

        smartProtect.setTitle(mSmartProtectName);
        smartProtect.setTitleAttributes(mSmartProtectColor, Color.WHITE);
        String supportURL = getResources().getString(R.string.swe_security_docs_url);
        if (!TextUtils.isEmpty(supportURL))
            smartProtect.setSupportURL(supportURL);
    }

    @Override
    protected void displayExtraSitePermissions(Preference preference) {
        super.displayExtraSitePermissions(preference);

        if (PREF_WEBREFINER_PERMISSION.equals(preference.getKey())) {
            if (!WebRefinerPreferenceHandler.isInitialized()) {
                getPreferenceScreen().removePreference(preference);
                getPreferenceScreen().removePreference(mWebRefinerCategory);
                return;
            }

            preference.setOnPreferenceClickListener(this);
            setTextForPreference(preference,
                    getWebRefinerPermission() ? ContentSetting.ALLOW : ContentSetting.BLOCK);

            preference.setIcon(
                    getEnabledIcon(ContentSettingsType.CONTENT_SETTINGS_TYPE_WEBREFINER));
        } else if (PREF_WEBDEFENDER_PERMISSION.equals(preference.getKey())) {
            if (!WebDefenderPreferenceHandler.isInitialized()) {
                getPreferenceScreen().removePreference(preference);
                getPreferenceScreen().removePreference(mWebDefenderCategory);
                return;
            }

            preference.setOnPreferenceClickListener(this);
            preference.setEnabled(true);
            preference.setSelectable(true);
            setTextForPreference(preference,
                    getWebDefenderPermission() ? ContentSetting.ALLOW : ContentSetting.BLOCK);

            preference.setIcon(
                    getEnabledIcon(ContentSettingsType.CONTENT_SETTINGS_TYPE_WEBDEFENDER));
        } else if (preference.getKey().equals("webdefender_details")) {
            preference.setOnPreferenceClickListener(this);
        } else if (PREF_SECURE_CONNECT_PERMISSION.equals(preference.getKey())) {
            boolean hasUpgradedSubframes =
                    mSecureConnectPageInfo != null &&
                            mSecureConnectPageInfo.getSubFrameUrlUpgradeCount() > 0;
            boolean shouldRemove = !SecureConnectPreferenceHandler.isInitialized()
                    || (mSiteBeforeRedirect == null && mSite.getSecureConnectInfo() == null
                        && !hasUpgradedSubframes && SecureConnectPreferenceHandler.
                        getSettingForIncognitoOrigin(mSite.getAddress().getOrigin()) == null);

            if (shouldRemove) {
                getPreferenceScreen().removePreference(preference);
                getPreferenceScreen().removePreference(mSecureConnectCategory);
                return;
            }

            preference.setOnPreferenceClickListener(this);
            setTextForPreference(preference, getSecureConnectPermission() ?
                    ContentSetting.ALLOW : ContentSetting.BLOCK);

            preference.setIcon(
                    getEnabledIcon(ContentSettingsType.CONTENT_SETTINGS_TYPE_SECURE_CONNECT));
        }
    }

    @Override
    protected int getContentSettingsTypeFromPreferenceKey(String preferenceKey) {
        int type = super.getContentSettingsTypeFromPreferenceKey(preferenceKey);
        if (type != 0)
            return type;

        switch (preferenceKey) {
            case PREF_WEBREFINER_PERMISSION:
                return ContentSettingsType.CONTENT_SETTINGS_TYPE_WEBREFINER;
            case PREF_WEBDEFENDER_PERMISSION:
                return ContentSettingsType.CONTENT_SETTINGS_TYPE_WEBDEFENDER;
            case PREF_SECURE_CONNECT_PERMISSION:
                return ContentSettingsType.CONTENT_SETTINGS_TYPE_SECURE_CONNECT;
            default:
                return 0;
        }
    }

    private void setTextForPreference(Preference preference, ContentSetting value) {
        if (PREF_WEBREFINER_PERMISSION.equals(preference.getKey())) {
            preference.setTitle(value == ContentSetting.ALLOW
                    ? (mWebRefinerMessages != null) ?
                    Html.fromHtml(mWebRefinerMessages)
                    : getResources().getString(R.string.website_settings_webrefiner_enabled)
                    : getResources().getString(R.string.website_settings_webrefiner_disabled));
        } else if (PREF_WEBDEFENDER_PERMISSION.equals(preference.getKey())) {
            preference.setTitle(value == ContentSetting.ALLOW
                    ? (mWebDefenderStatus != null) ?
                    WebDefenderPreferenceHandler.getOverviewMessage(getResources(),
                            mWebDefenderStatus)
                    : getResources().getString(R.string.website_settings_webdefender_enabled)
                    : WebDefenderPreferenceHandler.getDisabledMessage(getResources(),
                    mWebDefenderStatus));
        } else if (PREF_SECURE_CONNECT_PERMISSION.equals(preference.getKey())) {
            preference.setTitle(Html.fromHtml(SecureConnectPreferenceHandler
                    .getStatusMessage(getResources(), mSecureConnectPageInfo, value,
                            mSecureConnectName)));
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (!PREF_WEBREFINER_PERMISSION.equals(preference.getKey()) &&
                !PREF_WEBDEFENDER_PERMISSION.equals(preference.getKey()) &&
                !PREF_SECURE_CONNECT_PERMISSION.equals(preference.getKey())) {
            int contentType = getContentSettingsTypeFromPreferenceKey(preference.getKey());
            ContentSetting permission = ContentSetting.fromString((String) newValue);
            createPermissionInfo(preference, permission);
            requestReloadForOrigin();
            preference.setSummary("%s");
            super.onPreferenceChange(preference, newValue);
            updateSummary(preference, contentType, permission);
            updateSecurityPreferenceVisibility();
        }
        return true;
    }

    @Override
    protected void resetSite() {
        requestReloadForOrigin();
        if (mIsIncognito) {
            WebRefinerPreferenceHandler.clearIncognitoOrigin(mSite.getAddress().getOrigin());
            WebDefenderPreferenceHandler.clearIncognitoOrigin(mSite.getAddress().getOrigin());
            SecureConnectPreferenceHandler.clearIncognitoOrigin(mSite.getAddress().getOrigin());
            getActivity().finish();
            return;
        }

        mSite.setWebRefinerPermission(ContentSetting.DEFAULT);
        mSite.setWebDefenderPermission(ContentSetting.DEFAULT);
        mSite.setSecureConnectPermission(ContentSetting.DEFAULT);
        PreferenceScreen screen = getPreferenceScreen();
        screen.removePreference(mWebRefinerCategory);
        screen.removePreference(mWebDefenderCategory);
        screen.removePreference(mSecureConnectCategory);

        super.resetSite();
    }

    @Override
    protected void requestReloadForOrigin() {
        String origin = (mSiteAddress != null)
                ? mSiteAddress.getOrigin() : mSite.getAddress().getOrigin();
        PrefServiceBridge.getInstance().addOriginForReload(origin);
    }

    @Override
    protected void updateSecurityPreferenceVisibility() {
        PreferenceScreen screen = getPreferenceScreen();
        Preference privacyInfoCategory = findPreference("site_security_info_title");
        if (mSecurityInfo == null) return;
        mSecurityInfo.refreshInformation(mSite);

        if (!mSecurityInfo.hasInformation() && screen != null
                && privacyInfoCategory != null) {
            screen.removePreference(mSecurityInfoCategory);
        } else if (mSecurityInfo.hasInformation() && screen != null
                && privacyInfoCategory == null) {
            screen.addPreference(mSecurityInfoCategory);
        }
    }

    @Override
    protected boolean hasUsagePreferences() {
        if (mIsIncognito) {
            Preference preference = findPreference(PREF_CLEAR_DATA);
            if (preference != null) {
                getPreferenceScreen().removePreference(preference);
            }
        }
        return super.hasUsagePreferences();
    }

    @Override
    protected void updateSummary(Preference preference, int contentType, ContentSetting value){
        if (ContentSettingsResources.getDefaultEnabledValue(contentType).equals(ContentSetting.ASK)
                && value == ContentSetting.ASK) {
            preference.setSummary(R.string.website_settings_category_ask);
        } else if (value == ContentSetting.ALLOW_24H) {
            Date expiry = new Date(PrefServiceBridge.getExpiryForContentType(contentType,
                    mSite.getAddress().getOrigin()));
            Date now = new Date();
            if (!now.after(expiry)) {
                preference.setSummary(getFormattedExpiry(expiry));
            }
        }
    }

    /**
     * Get the global setting value for the given preference.
     * @param preference The ListPreference to be checked.
     */
    @Override
    protected ContentSetting getGlobalDefaultPermission(Preference preference) {
        String preferenceKey = preference.getKey();
        int contentType = getContentSettingsTypeFromPreferenceKey(preferenceKey);
        ContentSetting defaultValue;
        boolean isEnabled;
        if (PREF_AUTOPLAY_PERMISSION.equals(preferenceKey)) {
            isEnabled = PrefServiceBridge.getInstance().isAutoplayEnabled();
        } else if (PREF_BACKGROUND_SYNC_PERMISSION.equals(preferenceKey)) {
            isEnabled = PrefServiceBridge.getInstance().isBackgroundSyncAllowed();
        } else if (PREF_CAMERA_CAPTURE_PERMISSION.equals(preferenceKey)) {
            isEnabled = PrefServiceBridge.getInstance().isCameraEnabled();
        } else if (PREF_COOKIES_PERMISSION.equals(preferenceKey)) {
            isEnabled = PrefServiceBridge.getInstance().isAcceptCookiesEnabled();
        } else if (PREF_FULLSCREEN_PERMISSION.equals(preferenceKey)) {
            isEnabled = PrefServiceBridge.getInstance().isFullscreenAllowed();
        } else if (PREF_JAVASCRIPT_PERMISSION.equals(preferenceKey)) {
            isEnabled = PrefServiceBridge.getInstance().javaScriptEnabled();
        } else if (PREF_LOCATION_ACCESS.equals(preferenceKey)) {
            isEnabled = PrefServiceBridge.getInstance().isAllowLocationEnabled();
        } else if (PREF_MIC_CAPTURE_PERMISSION.equals(preferenceKey)) {
            isEnabled = PrefServiceBridge.getInstance().isMicEnabled();
        } else if (PREF_POPUP_PERMISSION.equals(preferenceKey)) {
            isEnabled = PrefServiceBridge.getInstance().popupsEnabled();
        } else if (PREF_PROTECTED_MEDIA_IDENTIFIER_PERMISSION.equals(preferenceKey)) {
            isEnabled = PrefServiceBridge.getInstance().isProtectedMediaIdentifierEnabled();
        } else if (PREF_NOTIFICATIONS_PERMISSION.equals(preferenceKey)) {
            isEnabled = PrefServiceBridge.getInstance().isNotificationsEnabled();
        } else if (PREF_WEBREFINER_PERMISSION.equals(preferenceKey)) {
            isEnabled = PrefServiceBridge.getInstance().isWebRefinerEnabled();
        } else if (PREF_WEBDEFENDER_PERMISSION.equals(preferenceKey)) {
            isEnabled = PrefServiceBridge.getInstance().isWebDefenderEnabled();
        } else if (PREF_SECURE_CONNECT_PERMISSION.equals(preferenceKey)) {
            isEnabled = PrefServiceBridge.getInstance().isSecureConnectEnabled();
        } else {
            return null;
        }

        if (isEnabled) {
            defaultValue = ContentSettingsResources.getDefaultEnabledValue(contentType);
        } else {
            defaultValue = ContentSettingsResources.getDefaultDisabledValue(contentType);
        }

        return defaultValue;
    }

    /**
     * Create Info object for the given preference if it does not exist.
     * @param preference The ListPreference to initialize.
     * @param permission The ContentSetting to initialize it to.
     */
    private void createPermissionInfo(Preference preference, ContentSetting permission) {
        String preferenceKey = preference.getKey();
        int contentType = getContentSettingsTypeFromPreferenceKey(preferenceKey);
        if (PREF_AUTOPLAY_PERMISSION.equals(preferenceKey)
                && mSite.getAutoplayException() == null) {
            mSite.setAutoplayException(new ContentSettingException(contentType,
                    mSite.getAddress().getOrigin(),
                    permission,
                    "policy"));
        }
        if (PREF_BACKGROUND_SYNC_PERMISSION.equals(preferenceKey)
                && mSite.getBackgroundSyncException() == null) {
            mSite.setBackgroundSyncException(new ContentSettingException(contentType,
                    mSite.getAddress().getOrigin(),
                    permission,
                    "policy"));
        }
        if (PREF_CAMERA_CAPTURE_PERMISSION.equals(preferenceKey) && mSite.getCameraInfo() == null) {
            mSite.setCameraInfo(new CameraInfo(mSite.getAddress().getOrigin(),
                    null,
                    mIsIncognito));
         } else if (PREF_COOKIES_PERMISSION.equals(preferenceKey)
                && mSite.getCookiePermission() == null) {
             mSite.setCookieException(new ContentSettingException(contentType,
                     mSite.getAddress().getOrigin(),
                     permission,
                     "policy"));
        } else if (PREF_FULLSCREEN_PERMISSION.equals(preferenceKey)
                && mSite.getFullscreenInfo() == null) {
            mSite.setFullscreenInfo(new FullscreenInfo(mSite.getAddress().getOrigin(),
                    null,
                    mIsIncognito));
        } else if (PREF_JAVASCRIPT_PERMISSION.equals(preferenceKey)
                && mSite.getJavaScriptPermission() == null) {
            mSite.setJavaScriptException(new ContentSettingException(contentType,
                    mSite.getAddress().getOrigin(),
                    permission,
                    "policy"));
        } else if (PREF_LOCATION_ACCESS.equals(preferenceKey)
                && mSite.getGeolocationInfo() == null) {
            mSite.setGeolocationInfo(new GeolocationInfo(mSite.getAddress().getOrigin(),
                    null,
                    mIsIncognito));
        } else if (PREF_MIC_CAPTURE_PERMISSION.equals(preferenceKey)
                && mSite.getMicrophoneInfo() == null) {
            mSite.setMicrophoneInfo(new MicrophoneInfo(mSite.getAddress().getOrigin(),
                    null,
                    mIsIncognito));
        } else if (PREF_POPUP_PERMISSION.equals(preferenceKey)
                && mSite.getPopupException() == null) {
            mSite.setPopupException(new ContentSettingException(contentType,
                    mSite.getAddress().getOrigin(),
                    permission,
                    "policy"));
        } else if (PREF_PROTECTED_MEDIA_IDENTIFIER_PERMISSION.equals(preferenceKey)
                && mSite.getProtectedMediaIdentifierInfo() == null) {
            mSite.setProtectedMediaIdentifierInfo(
                    new ProtectedMediaIdentifierInfo(mSite.getAddress().getOrigin(),
                            mSite.getAddress().getOrigin(),
                            mIsIncognito));
        } else if (PREF_NOTIFICATIONS_PERMISSION.equals(preferenceKey)
                && mSite.getNotificationInfo() == null) {
            mSite.setNotificationInfo(
                    new NotificationInfo(mSite.getAddress().getOrigin(),
                            null,
                            mIsIncognito));
        } else if (PREF_WEBREFINER_PERMISSION.equals(preferenceKey)
                && mSite.getWebRefinerInfo() == null) {
            mSite.setWebRefinerInfo(
                    new WebRefinerInfo(mSite.getAddress().getOrigin(), null, mIsIncognito));
        } else if (PREF_WEBDEFENDER_PERMISSION.equals(preferenceKey)
                && mSite.getWebDefenderInfo() == null) {
            mSite.setWebDefenderInfo(
                    new WebDefenderInfo(mSite.getAddress().getOrigin(), null, mIsIncognito));
        } else if (PREF_SECURE_CONNECT_PERMISSION.equals(preferenceKey)
                && mSite.getSecureConnectInfo() == null) {
            mSite.setSecureConnectInfo(
                    new SecureConnectInfo(mSite.getAddress().getOrigin(), null, mIsIncognito));
        }
    }

    private boolean getWebRefinerPermission() {
        ContentSetting setting = null;
        if (mIsIncognito) {
            setting = WebRefinerPreferenceHandler.getSettingForIncognitoOrigin(
                    mSite.getAddress().getOrigin());
            // Try to inherit the non-incognito setting
            if (setting == null) setting = mSite.getWebRefinerPermission();
        } else {
            setting = mSite.getWebRefinerPermission();
        }

        if (setting != null) {
            String permission = setting.toString();
            return permission.equalsIgnoreCase(ContentSetting.ALLOW.toString());
        }

        return PrefServiceBridge.getInstance().isWebRefinerEnabled();
    }

    private void setWebRefinerPermission(boolean value) {
        Preference preference = findPreference(PREF_WEBREFINER_PERMISSION);
        ContentSetting permission = (value) ? ContentSetting.ALLOW : ContentSetting.BLOCK;
        requestReloadForOrigin();
        setTextForPreference(preference, permission);
        createPermissionInfo(preference, permission);
        if (mIsIncognito) {
            WebRefinerPreferenceHandler.addIncognitoOrigin(mSite.getAddress().getOrigin(),
                    permission);
        } else {
            mSite.setWebRefinerPermission(permission);
        }
    }

    private boolean getWebDefenderPermission() {
        ContentSetting setting = null;
        if (mIsIncognito) {
            setting = WebDefenderPreferenceHandler.getSettingForIncognitoOrigin(
                    mSite.getAddress().getOrigin());
            // Try to inherit the non-incognito setting
            if (setting == null) setting = mSite.getWebDefenderPermission();
        } else {
            setting = mSite.getWebDefenderPermission();
        }

        if (setting != null) {
            String permission = setting.toString();
            return permission.equalsIgnoreCase(ContentSetting.ALLOW.toString());
        }

        return PrefServiceBridge.getInstance().isWebDefenderEnabled();
    }

    private void setWebDefenderPermission(boolean value) {
        Preference preference = findPreference(PREF_WEBDEFENDER_PERMISSION);
        ContentSetting permission = (value) ? ContentSetting.ALLOW : ContentSetting.BLOCK;
        PrefServiceBridge.getInstance().requestReload();
        setTextForPreference(preference, permission);
        createPermissionInfo(preference, permission);
        if (mIsIncognito) {
            WebDefenderPreferenceHandler.addIncognitoOrigin(mSite.getAddress().getOrigin(),
                    permission);
        } else {
            mSite.setWebDefenderPermission(permission);
        }
    }

    private boolean getSecureConnectPermission() {
        ContentSetting setting = null;

        if (mIsIncognito) {
            setting = SecureConnectPreferenceHandler.getSettingForIncognitoOrigin(
                    mSite.getAddress().getOrigin());
            // Try to inherit the non-incognito setting
            if (setting == null) setting = mSite.getSecureConnectPermission();
        } else {
            setting = mSite.getSecureConnectPermission();
            if (setting == null && mSiteBeforeRedirect != null) {
                setting = mSiteBeforeRedirect.getSecureConnectPermission();
            }
        }

        if (setting != null) {
            String permission = setting.toString();
            return permission.equalsIgnoreCase(ContentSetting.ALLOW.toString());
        }

        return PrefServiceBridge.getInstance().isSecureConnectEnabled();
    }

    private void setSecureConnectPermission(boolean value) {
        Preference preference = findPreference(PREF_SECURE_CONNECT_PERMISSION);
        ContentSetting permission = (value) ? ContentSetting.ALLOW : ContentSetting.BLOCK;
        PrefServiceBridge.getInstance().requestReload();
        setTextForPreference(preference, permission);
        boolean hasRedirectMainFrame = mSiteBeforeRedirect != null;
        boolean hasRedirectedSubFrames = mSecureConnectPageInfo != null
                && mSecureConnectPageInfo.getSubFrameUrlUpgradeCount() > 0;

        if (mIsIncognito) {
            if (hasRedirectMainFrame) {
                SecureConnectPreferenceHandler.addIncognitoOrigin(
                        mSiteBeforeRedirect.getAddress().getOrigin(), permission);
            } else {
                SecureConnectPreferenceHandler.addIncognitoOrigin(
                        mSite.getAddress().getOrigin(), permission);
            }
        } else {
            if (mSite.getSecureConnectInfo() == null) {
                if (hasRedirectMainFrame) {
                    mSiteBeforeRedirect.setSecureConnectInfo(new SecureConnectInfo(
                            mSiteBeforeRedirect.getAddress().getOrigin(),
                            null,
                            mIsIncognito));
                    mSiteBeforeRedirect.setSecureConnectPermission(permission);
                } else if (hasRedirectedSubFrames) {
                    createPermissionInfo(preference, permission);
                    mSite.setSecureConnectPermission(permission);
                }
            } else {
                mSite.setSecureConnectPermission(permission);
            }
        }
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        String preferenceKey = preference.getKey();

        if (preferenceKey.equals("webdefender_details")) {
            if (preference.getFragment() != null &&
                    getActivity() instanceof OnPreferenceStartFragmentCallback) {
                Bundle args = getArguments();
                if (args != null) {
                    args.putBoolean(WEBDEFENDER_SETTING, getWebDefenderPermission());
                    args.putBoolean(WEBREFINER_SETTING, getWebRefinerPermission());
                    args.putBoolean(SECURE_CONNECT_SETTING, getSecureConnectPermission());
                    Bundle extra = preference.getExtras();
                    extra.putAll(args);
                }
                return ((OnPreferenceStartFragmentCallback)getActivity()).onPreferenceStartFragment(
                        this, preference);
            }
            return false;
        }

        return super.onPreferenceClick(preference);
    }

    public void onChildViewAddedToHierarchy(View parent, View child) {

        TextView view = (TextView) child.findViewById(android.R.id.title);

        if (view != null) {
            /** Handled by {@link BrowserSwitchPreferenceCategory} for these prefrences **/
            if (view.getText().equals(mSmartProtectName)
                    || view.getText().equals(mWebDefenderName)
                    || view.getText().equals(mWebRefinerName)
                    || view.getText().equals(mSecureConnectName)) {
                return;
            }
        }

        if (mSiteColor != -1) {
            if (child.getId() == R.id.browser_pref_cat_first ||
                    child.getId() == R.id.browser_pref_cat) {
                if (view != null) {
                    view.setTextColor(mSiteColor);
                }
            }

            Button btn = (Button) child.findViewById(R.id.button_preference);
            if (btn != null) {
                int btnColor = mSiteColor;
                if (btn.getText().equals(getResources().getText(R.string.page_info_details_link))) {
                    btnColor = mSmartProtectColor;
                }

                btn.setBackgroundColor(btnColor);
            }
            ImageView imageView = (ImageView) child.findViewById(R.id.clear_site_data);
            if (imageView != null && imageView instanceof TintedImageView) {
                ColorStateList colorList = ColorStateList.valueOf(mSiteColor);
                ((TintedImageView) imageView).setTint(colorList);
            }
        }
    }

    private void appendActionBarDisplayOptions(ActionBar bar, int extraOptions) {
        int options = bar.getDisplayOptions();
        options |= extraOptions;
        bar.setDisplayOptions(options);
    }

    private void setStatusBarColor(int color) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            final Activity activity = ApplicationStatus.getLastTrackedFocusedActivity();
            activity.getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            mSiteColor = ColorUtils.getDarkenedColorForStatusBar(color);
            activity.getWindow().setStatusBarColor(mSiteColor);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() instanceof AppCompatActivity) {
            AppCompatActivity activity = (AppCompatActivity) getActivity();
            ActionBar bar = activity.getSupportActionBar();
            Bundle args = getArguments();
            if (bar != null && args != null) {
                byte[] data = args.getByteArray(EXTRA_FAVICON);
                if (data != null) {
                    Bitmap bm = BitmapFactory.decodeByteArray(data, 0, data.length);
                    if (bm != null) {
                        Bitmap bitmap = Bitmap.createScaledBitmap(bm, 150, 150, true);
                        int color = FaviconHelper.getDominantColorForBitmap(bitmap);
                        appendActionBarDisplayOptions(bar,
                                ActionBar.DISPLAY_SHOW_HOME | ActionBar.DISPLAY_SHOW_TITLE);
                        bar.setHomeButtonEnabled(true);
                        bar.setBackgroundDrawable(new ColorDrawable(
                                ColorUtils.computeActionBarColor(color)
                        ));
                        setStatusBarColor(color);
                        String title = (mSiteAddress != null)
                                ? mSiteAddress.getTitle() : mSite.getAddress().getTitle();

                        bar.setTitle("  " + title);
                    }
                }
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        clearTemporaryTrackerDomainList(getActivity());
    }

    public static void clearTemporaryTrackerDomainList(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (TextUtils.isEmpty(prefs.getString(PREF_TEMPORARY_DOMAINS, ""))) return;
        prefs.edit().putString(PREF_TEMPORARY_DOMAINS, "").apply();
    }

    public static WebRefinerStatsInfo getWebRefinerInformation(Resources res, Bundle args) {
        int ads = args.getInt(EXTRA_WEB_REFINER_ADS_INFO, 0);
        int count = ads;
        String[] strings = new String[3];
        int index = 0;

        WebRefinerStatsInfo info = new WebRefinerStatsInfo();

        if (ads > 0) {
            strings[index++] = "<b>" + ads + "</b>" + " " +
                res.getString((ads > 1) ? R.string.webrefiner_ads_plural : R.string.webrefiner_ads);
        }

        int trackers = args.getInt(EXTRA_WEB_REFINER_TRACKER_INFO, 0);
        count += trackers;
        if (trackers > 0) {
            strings[index++] = "<b>" + trackers + "</b>" + " " +
                    res.getString((trackers > 1)
                            ? R.string.webrefiner_trackers_plural : R.string.webrefiner_trackers);

        }

        int malware = args.getInt(EXTRA_WEB_REFINER_MALWARE_INFO, 0);
        count += malware;
        if (malware > 0) {
            strings[index++] = "<b>" + malware + "</b>" + " " +
                    res.getString(R.string.webrefiner_malware);
        }
        if (index > 0) {
            String[] formats = new String[3];
            formats[0] = res.getString(R.string.webrefiner_one_message);
            formats[1] = res.getString(R.string.webrefiner_two_message);
            formats[2] = res.getString(R.string.webrefiner_three_message);

            Formatter formatter = new Formatter();
            formatter.format(formats[index - 1], strings[0], strings[1], strings[2]);
            info.mFormattedMsg = formatter.toString();
        } else {
            info.mFormattedMsg = null;
        }

        info.mBlockedCount = count;

        return info;
    }

    /**
     * Creates a Bundle with the correct arguments for opening this fragment for
     * the website with the given url and icon.
     *
     * @param url The URL to open the fragment with. This is a complete url including scheme,
     *            domain, port,  path, etc.
     * @param icon The favicon for the URL
     *
     * @param tab The tab for the url
     * @return The bundle to attach to the preferences intent.
     */
    public static Bundle createFragmentArgsForSite(String url, Bitmap icon, Tab tab) {
        Bundle fragmentArgs = new Bundle();
        // TODO(mvanouwerkerk): Define a pure getOrigin method in UrlUtilities that is the
        // equivalent of the call below, because this is perfectly fine for non-display purposes.
        String origin = UrlFormatter.formatUrlForSecurityDisplay(url, true);
        fragmentArgs.putString(SingleWebsitePreferences.EXTRA_ORIGIN, origin);

        if (icon != null) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            icon.compress(Bitmap.CompressFormat.PNG, 100, baos);
            fragmentArgs.putByteArray(EXTRA_FAVICON, baos.toByteArray());
        }

        // Add webrefiner related messages
        int ads, trackers, malware;
        ads = trackers = malware = 0;
        if (tab != null) {
            fragmentArgs.putBoolean(EXTRA_INCOGNITO, tab.isIncognito());
            fragmentArgs.putInt(BrowserSingleWebsitePreferences.EXTRA_SECURITY_CERT_LEVEL,
                    tab.getSecurityLevel());
            if (tab.getContentViewCore() != null) {
                WebRefiner.PageInfo pageInfo =
                        WebRefinerPreferenceHandler.getPageInfo(tab.getContentViewCore());
                if (pageInfo != null) {
                    for (WebRefiner.MatchedURLInfo urlInfo : pageInfo.mMatchedURLInfoList) {
                        if (urlInfo.mActionTaken == WebRefiner.MatchedURLInfo.ACTION_BLOCKED) {
                            switch (urlInfo.mMatchedFilterCategory) {
                                case WebRefiner.RuleSet.CATEGORY_ADS:
                                    ads++;
                                    break;
                                case WebRefiner.RuleSet.CATEGORY_TRACKERS:
                                    trackers++;
                                    break;
                                case WebRefiner.RuleSet.CATEGORY_MALWARE_DOMAINS:
                                    malware++;
                                    break;
                            }
                        }
                    }
                }
                fragmentArgs.putInt(EXTRA_WEB_REFINER_ADS_INFO, ads);
                fragmentArgs.putInt(EXTRA_WEB_REFINER_TRACKER_INFO, trackers);
                fragmentArgs.putInt(EXTRA_WEB_REFINER_MALWARE_INFO, malware);
            }

            if (WebDefenderPreferenceHandler.isInitialized()) {
                WebDefenderPreferenceHandler.StatusParcel parcel =
                        WebDefenderPreferenceHandler.getStatus(tab.getContentViewCore());

                fragmentArgs.putParcelable(
                        SmartProtectDetailsPreferences.EXTRA_SMARTPROTECT_PARCEL, parcel);
            }

            if (SecureConnectPreferenceHandler.isInitialized()) {
                SecureConnectPreferenceHandler.StatusParcel parcel =
                        SecureConnectPreferenceHandler.getParcel(tab.getContentViewCore());

                fragmentArgs.putParcelable(SecureConnectPreferenceHandler.
                        EXTRA_SECURE_CONNECT_PARCEL, parcel);
            }
        }

        return fragmentArgs;
    }

    //Because we expose all settings to the user always, we want to show the warning about
    //Android's permission management to explain why some settings are disabled.
    protected boolean showWarningFor(int type) {
        if (mIsIncognito || !URLUtil.isHttpsUrl(mSite.getAddress().getOrigin())) return false;
        switch (type) {
            case ContentSettingsType.CONTENT_SETTINGS_TYPE_GEOLOCATION:
                break;
            case ContentSettingsType.CONTENT_SETTINGS_TYPE_MEDIASTREAM_CAMERA:
                break;
            case ContentSettingsType.CONTENT_SETTINGS_TYPE_MEDIASTREAM_MIC:
                break;
            default:
                return false;
        }
        SiteSettingsCategory category = SiteSettingsCategory.fromContentSettingsType(type);
        return category != null && category.showPermissionBlockedMessage(getActivity());
    }
}

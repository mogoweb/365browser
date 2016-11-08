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
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.PaintDrawable;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Base64InputStream;
import android.util.Base64OutputStream;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ListView;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.ApplicationStatus;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ContentSettingsType;
import org.chromium.chrome.browser.preferences.BrowserPrivacyMeterPreference;
import org.chromium.chrome.browser.preferences.ExpandablePreferenceGroup;
import org.chromium.chrome.browser.preferences.SecureConnectDetails;
import org.chromium.chrome.browser.preferences.SecureConnectItemPreference;
import org.chromium.chrome.browser.preferences.SecureConnectPreferenceHandler;
import org.chromium.chrome.browser.preferences.TextMessagePreference;
import org.chromium.chrome.browser.util.ColorUtils;
import org.chromium.chrome.browser.widget.TintedDrawable;
import org.chromium.content.browser.SecureConnect;
import org.chromium.content.browser.WebDefender;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;

import java.util.Comparator;
import java.util.HashMap;

/**
 * Fragment to show smart protect details.
 */
public class SmartProtectDetailsPreferences extends PreferenceFragment
        implements Preference.OnPreferenceClickListener {
    public static final String EXTRA_SMARTPROTECT_PARCEL = "extra_smartprotect_parcel";
    public static final String DOMAIN_PREF = "tracker_domains";
    public static final String SECURE_CONNECT_RULESET_PREF = "secure_connect_rulesets";
    public static final String SECURE_CONNECT_MAINFRAME_PREF = "secure_connect_details_mainframe";
    public static final String SMART_PROTECT_DETAILS_PREF = "smart_protect_details";
    public static final int GREEN = Color.parseColor("#008F02");
    public static final int YELLOW = Color.parseColor("#CBB325");
    public static final int RED = Color.parseColor("#AA232A");
    public static final int GRAY = Color.GRAY;
    private static final int BAR_GRAPH_HEIGHT_DP = 15;
    private static final String WEB_DEFENDER_GROUP = "web_defender_group";
    private static final String SECURE_CONNECT_GROUP = "secure_connect_group";

    private boolean mIsIncognito;
    private int mMaxBarGraphWidth;
    private int mBarGraphHeight;
    private WebDefender.ProtectionStatus mWebDefenderStatus;
    private SecureConnect.Info mSecureConnectStatus;
    private HashMap<String, Integer> mUpdatedTrackerDomains;
    private HashMap<String, Boolean> mUpdatedRulesets;
    private HashMap<String, Boolean> mUpdatedMainframe;
    ArrayList<SecureConnect.URLInfo> mSortedSecureConnectRulesets;
    private BrowserPrivacyMeterPreference mMeterPreference;
    private ExpandablePreferenceGroup mWebDefenderGroup;
    private ExpandablePreferenceGroup mSecureConnectGroup;
    private String mTitle;
    private int mSmartProtectColor;
    private int mWebRefinerBlockedCount;
    private boolean mWebDefenderEnabled;
    private boolean mWebRefinerEnabled;
    private boolean mSecureConnectEnabled;

    private Preference mWebDefenderVectorList;
    private Preference mWebDefenderVectorChart;
    private SecureConnectItemPreference mSecureConnectMainframe;
    private Preference mSecureConnectList;

    private WebDefenderVectorsRecyclerView mVectorsRecyclerView;
    private SecureConnectDetails mSecureConnectRecycler;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
        View view = super.onCreateView(inflater, container, bundle);
        if (view == null) return view;
        ListView list = (ListView) view.findViewById(android.R.id.list);

        if (list == null) return view;

        ViewGroup.LayoutParams params = list.getLayoutParams();
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        list.setLayoutParams(params);
        list.setPadding(0, list.getPaddingTop(), 0, list.getPaddingBottom());
        list.setDivider(null);
        list.setDividerHeight(0);

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
        super.onActivityCreated(savedInstanceState);
        addPreferencesFromResource(R.xml.smartprotect_details_preferences);
        mSmartProtectColor = ApiCompatibilityUtils.getColor(getResources(), R.color.smart_protect);
        readUpdatedInfo(DOMAIN_PREF);
        readUpdatedInfo(SECURE_CONNECT_RULESET_PREF);
        readUpdatedInfo(SECURE_CONNECT_MAINFRAME_PREF);

        Bundle arguments = getArguments();
        if (arguments != null) {
            mIsIncognito = arguments.getBoolean(BrowserSingleWebsitePreferences.EXTRA_INCOGNITO);

            Object extraSite = arguments.getSerializable(SingleWebsitePreferences.EXTRA_SITE);
            Object extraOrigin = arguments.getSerializable(SingleWebsitePreferences.EXTRA_ORIGIN);
            mWebDefenderEnabled = arguments.getBoolean(
                    BrowserSingleWebsitePreferences.WEBDEFENDER_SETTING);
            mWebRefinerEnabled = arguments.getBoolean(
                    BrowserSingleWebsitePreferences.WEBREFINER_SETTING);
            mSecureConnectEnabled = arguments.getBoolean(
                    BrowserSingleWebsitePreferences.SECURE_CONNECT_SETTING);

            if (extraSite != null && extraOrigin == null) {
                Website site = (Website) extraSite;
                mTitle = site.getAddress().getTitle();
            } else if (extraOrigin != null && extraSite == null) {
                WebsiteAddress siteAddress = WebsiteAddress.create((String) extraOrigin);
                if (siteAddress != null) mTitle = siteAddress.getTitle();
            }
            mWebRefinerBlockedCount =
            arguments.getInt(BrowserSingleWebsitePreferences.EXTRA_WEB_REFINER_ADS_INFO, 0)
            + arguments.getInt(BrowserSingleWebsitePreferences.EXTRA_WEB_REFINER_TRACKER_INFO, 0)
            + arguments.getInt(BrowserSingleWebsitePreferences.EXTRA_WEB_REFINER_MALWARE_INFO, 0);
            WebDefenderPreferenceHandler.StatusParcel parcel =
                    arguments.getParcelable(EXTRA_SMARTPROTECT_PARCEL);
            if (parcel != null) mWebDefenderStatus = parcel.getStatus();
            SecureConnectPreferenceHandler.StatusParcel statusParcel =
                arguments.getParcelable(SecureConnectPreferenceHandler.EXTRA_SECURE_CONNECT_PARCEL);
            if (statusParcel != null) mSecureConnectStatus = statusParcel.getInfo();
        }

        String  settingName = getResources().getString(R.string.swe_security_branding_label);
        getActivity().setTitle(settingName);

        // Setup the expandable web defender views
        setupWebDefenderGroup();
        // Setup the expandable secure connect views
        setupSecureConnectGroup();

        setupOverviewPanel("web_refiner_info_title",
                ContentSettingsType.CONTENT_SETTINGS_TYPE_WEBREFINER,
                WebRefinerPreferenceHandler.isInitialized());
        setupPreference(ContentSettingsType.CONTENT_SETTINGS_TYPE_WEBREFINER, arguments);

        setupOverviewPanel("web_defender_info_title",
                ContentSettingsType.CONTENT_SETTINGS_TYPE_WEBDEFENDER,
                WebDefenderPreferenceHandler.isInitialized());
        setupPreference(ContentSettingsType.CONTENT_SETTINGS_TYPE_WEBDEFENDER, arguments);

        setupOverviewPanel("secure_connect_info_title",
                ContentSettingsType.CONTENT_SETTINGS_TYPE_SECURE_CONNECT,
                SecureConnectPreferenceHandler.isInitialized());
        setupPreference(ContentSettingsType.CONTENT_SETTINGS_TYPE_SECURE_CONNECT, arguments);

        if (mTitle != null) {
            TextMessagePreference siteTitle = (TextMessagePreference) findPreference("site_title");
            if (siteTitle != null && arguments != null) {
                siteTitle.setTitle(mTitle);
                byte[] data = arguments.getByteArray(BrowserSingleWebsitePreferences.EXTRA_FAVICON);
                if (data != null) {
                    Bitmap bm = BitmapFactory.decodeByteArray(data, 0, data.length);
                    if (bm != null) {
                        Bitmap bitmap = Bitmap.createScaledBitmap(bm, 150, 150, true);
                        Drawable drawable = new BitmapDrawable(getResources(), bitmap);
                        siteTitle.setIcon(drawable);
                    }
                }
            }
        }

        if (mMeterPreference != null) {
            mMeterPreference.setSummary(mTitle);
            mMeterPreference.setupPrivacyMeter(mWebDefenderEnabled,
                    mWebRefinerEnabled, mWebDefenderStatus, mWebRefinerBlockedCount);
        }
    }

    private void setupSecureConnectGroup() {
        mSecureConnectGroup = (ExpandablePreferenceGroup) findPreference(SECURE_CONNECT_GROUP);
        mSecureConnectGroup.setOnPreferenceClickListener(this);
        if (mSecureConnectStatus != null) {
            mSortedSecureConnectRulesets = SecureConnectPreferenceHandler
                    .getSortedRules(mSecureConnectStatus.mSubFrameURLs,
                            mUpdatedRulesets);
            mSecureConnectGroup.setGroupTitle(R.string.advanced_settings);
            TintedDrawable icon = TintedDrawable.constructTintedDrawable(getResources(),
                    mSecureConnectGroup.getExpanded() ?
                            R.drawable.ic_expanded : R.drawable.ic_collapsed);
            mSecureConnectGroup.setIcon(icon);
            mSecureConnectMainframe =
                    (SecureConnectItemPreference) findPreference("secure_connect_main_frame");
            if (mSecureConnectMainframe != null) {
                if (mSecureConnectStatus.hasMainFrameUrl()) {
                    SecureConnect.URLInfo mainFrame = mSecureConnectStatus.mMainFrameURL;
                    if (mUpdatedMainframe != null) {
                        mSecureConnectMainframe.setTitle(mUpdatedMainframe.keySet().iterator().next());
                        mSecureConnectMainframe.setChecked(
                                mUpdatedMainframe.values().iterator().next());
                    } else {
                        mSecureConnectMainframe.setTitle(mainFrame.mRulesetName);
                        mSecureConnectMainframe.setChecked(mainFrame.mRulesetEnabled);
                    }
                    if (mSortedSecureConnectRulesets.contains(mainFrame)) {
                        mSortedSecureConnectRulesets.remove(mainFrame);
                    }
                }
                mSecureConnectMainframe.setOnPreferenceClickListener(this);
                getPreferenceScreen().removePreference(mSecureConnectMainframe);
            }
            mSecureConnectList = findPreference("secure_connect_list");
            if (mSecureConnectList != null)
                getPreferenceScreen().removePreference(mSecureConnectList);
            mSecureConnectGroup.removeAll();
        } else {
            getPreferenceScreen().removePreference(mSecureConnectGroup);
        }
    }
    private void setupWebDefenderGroup() {
        mWebDefenderGroup = (ExpandablePreferenceGroup)
                findPreference(WEB_DEFENDER_GROUP);
        mWebDefenderGroup.setOnPreferenceClickListener(this);
        mWebDefenderGroup.setGroupTitle(R.string.advanced_settings);
        TintedDrawable icon = TintedDrawable.constructTintedDrawable(getResources(),
                mWebDefenderGroup.getExpanded()
                        ? R.drawable.ic_expanded : R.drawable.ic_collapsed);
        mWebDefenderGroup.setIcon(icon);
        mWebDefenderVectorList = findPreference("vector_list");
        if (mWebDefenderVectorList != null)
            getPreferenceScreen().removePreference(mWebDefenderVectorList);
        mWebDefenderVectorChart = findPreference("vector_chart");
        if (mWebDefenderVectorChart != null)
            getPreferenceScreen().removePreference(mWebDefenderVectorChart);
        mWebDefenderGroup.removeAll();
    }

    /**
      *  Does all the necessary setup for each contentSetting that is displayed.
      * @param contentSettingsType
      * @param args
      */
    private void setupPreference(int contentSettingsType, Bundle args) {
    switch(contentSettingsType) {
        case ContentSettingsType.CONTENT_SETTINGS_TYPE_WEBREFINER:
            if (WebRefinerPreferenceHandler.isInitialized()) {
                TextMessagePreference webrefiner_details =
                                (TextMessagePreference) findPreference("webrefiner_details");
                BrowserSingleWebsitePreferences.WebRefinerStatsInfo info =
                                BrowserSingleWebsitePreferences.getWebRefinerInformation(
                                                getResources(), args);
                PreferenceCategory prefCat =
                                (PreferenceCategory) findPreference("web_refiner_info_title");
                if (info != null && !TextUtils.isEmpty(info.mFormattedMsg)) {
                        webrefiner_details.setTitle(Html.fromHtml(info.mFormattedMsg));
                    } else if (webrefiner_details != null) {
                        prefCat.removePreference(webrefiner_details);
                    }

                        TextMessagePreference webrefiner_overview =
                                (TextMessagePreference) findPreference("webrefiner_overview");
                String overviewText = getString(R.string.web_refiner_about_text);
                webrefiner_overview.setTitle(overviewText);
            }
        case ContentSettingsType.CONTENT_SETTINGS_TYPE_WEBDEFENDER:
            mMeterPreference =
                        (BrowserPrivacyMeterPreference) findPreference("webdefender_privacy_meter");
            TextMessagePreference webdefender_details =
                        (TextMessagePreference) findPreference("webdefender_details");

                TextMessagePreference webdefender_overview =
                        (TextMessagePreference) findPreference("webdefender_overview");

            if (mWebDefenderStatus == null || mWebDefenderStatus.mTrackerDomains == null
            || !WebDefenderPreferenceHandler.isInitialized()
            || mWebDefenderStatus.mTrackerDomains.length == 0) {
                PreferenceCategory prefCat =
                                (PreferenceCategory) findPreference("web_defender_info_title");
                if (webdefender_details != null)
                        prefCat.removePreference(webdefender_details);
                getPreferenceScreen().removePreference(mWebDefenderGroup);
            }

            String overviewText = getString(R.string.web_defender_about_text);
            if (webdefender_overview != null) {
                webdefender_overview.setTitle(overviewText);
            }

            if (mWebDefenderStatus != null && webdefender_details != null
                && mWebDefenderStatus.mTrackerDomains.length != 0) {
                webdefender_details.setTitle(mWebDefenderEnabled
                            ? WebDefenderPreferenceHandler.getOverviewMessage(getResources(),
                                mWebDefenderStatus)
                            : WebDefenderPreferenceHandler.getDisabledMessage(getResources(),
                                mWebDefenderStatus));
            }
        case ContentSettingsType.CONTENT_SETTINGS_TYPE_SECURE_CONNECT:
            if (SecureConnectPreferenceHandler.isInitialized()) {
                TextMessagePreference secureConnectDetails =
                        (TextMessagePreference) findPreference("secure_connect_details");
                PreferenceCategory prefCat =
                        (PreferenceCategory) findPreference("secure_connect_info_title");
                String statusMessage = SecureConnectPreferenceHandler
                        .getStatusMessage(getResources(), mSecureConnectStatus,
                                mSecureConnectEnabled ? ContentSetting.ALLOW : ContentSetting.BLOCK,
                                getString(R.string.secure_connect_title));
                if (mSecureConnectStatus != null && !TextUtils.isEmpty(statusMessage)) {
                    secureConnectDetails.setTitle(Html.fromHtml(statusMessage));
                } else if (secureConnectDetails != null) {
                    prefCat.removePreference(secureConnectDetails);
                }

                TextMessagePreference secureConnectOverview =
                        (TextMessagePreference) findPreference("secure_connect_overview");
                String secureConnectOverviewText = getString(R.string.secure_connect_about_text);
                secureConnectOverview.setTitle(secureConnectOverviewText);

                if (mSecureConnectStatus == null || (!mSecureConnectStatus.hasMainFrameUrl()
                        && mSecureConnectStatus.mSubFrameURLs.length == 0)) {
                    getPreferenceScreen().removePreference(mSecureConnectGroup);
                }
            } else {
                getPreferenceScreen().removePreference(mSecureConnectGroup);
            }
        default:
            break;
        }
    }

    private void setupOverviewPanel(String prefCatName, int contentSettingType,
                                    boolean available) {
        SmartProtectPreferenceCategory prefCat =
                (SmartProtectPreferenceCategory) findPreference(prefCatName);
        if (prefCat != null) {
            if (available) {
                String titleName = getString(ContentSettingsResources.getTitle(contentSettingType));
                prefCat.setTitle(titleName);
                prefCat.setTitleAttributes(ApiCompatibilityUtils.getColor(getResources(),
                        android.R.color.transparent), mSmartProtectColor);

                String url = getString(R.string.swe_security_docs_url);
                if (!TextUtils.isEmpty(url))
                    prefCat.setSupportURL(url);
            } else {
                PreferenceScreen screen = getPreferenceScreen();
                if (screen != null)
                    screen.removePreference(prefCat);
            }
        }
    }


    @Override
    @SuppressWarnings("unchecked")
    public void onStart() {
        super.onStart();
    }

    private void readUpdatedInfo(String preference) {
        SharedPreferences prefs = getActivity()
                .getSharedPreferences(SMART_PROTECT_DETAILS_PREF, Context.MODE_PRIVATE);
        String domains = prefs.getString(preference, "");
        if (TextUtils.isEmpty(domains)) return;
        byte[] bytes = domains.getBytes();
        if (bytes.length == 0)
            return;
        ByteArrayInputStream byteArray = new ByteArrayInputStream(bytes);
        Base64InputStream base64InputStream = new Base64InputStream(byteArray, Base64.DEFAULT);
        ObjectInputStream in;
        try {
            in = new ObjectInputStream(base64InputStream);
            Object input = in.readObject();
            if (input != null && input instanceof HashMap) {
                if (TextUtils.equals(DOMAIN_PREF, preference)) {
                    mUpdatedTrackerDomains = (HashMap<String, Integer>) input;
                } else if (TextUtils.equals(SECURE_CONNECT_RULESET_PREF, preference)) {
                    mUpdatedRulesets = (HashMap<String, Boolean>) input;
                } else if (TextUtils.equals(SECURE_CONNECT_MAINFRAME_PREF, preference)) {
                    mUpdatedMainframe = (HashMap<String, Boolean>) input;

                }
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
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

            int statusBarColor = ColorUtils.getDarkenedColorForStatusBar(color);
            activity.getWindow().setStatusBarColor(statusBarColor);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() instanceof AppCompatActivity) {
            AppCompatActivity activity = (AppCompatActivity) getActivity();
            ActionBar bar = activity.getSupportActionBar();

            Bitmap bitmap = BitmapFactory.decodeResource(getResources(),
                    R.drawable.img_deco_smartprotect_webdefender);

            appendActionBarDisplayOptions(bar,
                    ActionBar.DISPLAY_SHOW_HOME | ActionBar.DISPLAY_SHOW_TITLE);
            bar.setHomeButtonEnabled(true);
            bar.setIcon(new BitmapDrawable(getResources(), bitmap));
            bar.setBackgroundDrawable(new ColorDrawable(
                    ColorUtils.computeActionBarColor(mSmartProtectColor)
            ));

            setStatusBarColor(mSmartProtectColor);
        }

        Display display = getActivity().getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        mMaxBarGraphWidth = (int) (Math.min(size.x, size.y) * 0.15);
        mBarGraphHeight = (int) (BAR_GRAPH_HEIGHT_DP *
                (getActivity().getResources().getDisplayMetrics().density));
    }

    private static Drawable generateBarDrawable(Resources res, int width, int height, int color) {
        PaintDrawable drawable = new PaintDrawable(color);
        drawable.setIntrinsicWidth(width);
        drawable.setIntrinsicHeight(height);
        drawable.setBounds(0, 0, width, height);

        Bitmap thumb = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(thumb);
        drawable.draw(c);

        return new BitmapDrawable(res, thumb);
    }

    private Drawable normalizedBarGraphDrawable(int value, int maxValue) {
        if (value == 0)
            return null;

        int normalizedWidth = mMaxBarGraphWidth * value / maxValue;

        return generateBarDrawable(getResources(), normalizedWidth,
                mBarGraphHeight, mSmartProtectColor);
    }

    private String getStringForCount(int count) {
        if (count == 0)
            return getResources().getString(
                    R.string.website_settings_webdefender_tracking_not_detected);

        return Integer.toString(count);
    }

    public void onChildViewAddedToHierarchy(View parent, View child) {
        TextView title = (TextView) child.findViewById(android.R.id.title);

        if (child.getId() == R.id.browser_pref_cat
                || child.getId() == R.id.browser_pref_cat_first) {
            if (title != null) {
                title.setTextColor(mSmartProtectColor);
            }
        }

        if (child.getId() == R.id.webdefender_vectorchart_layout) {
            int numCookieTrackers = 0;
            int numStorageTrackers = 0;
            int numFingerprintTrackers = 0;
            int numFontEnumTrackers = 0;

            if (mWebDefenderStatus != null) {
                for (int i = 0; i < mWebDefenderStatus.mTrackerDomains.length; i++) {
                    if ((mWebDefenderStatus.mTrackerDomains[i].mTrackingMethods &
                            WebDefender.TrackerDomain.TRACKING_METHOD_HTTP_COOKIES) != 0) {
                        numCookieTrackers++;
                    }
                    if ((mWebDefenderStatus.mTrackerDomains[i].mTrackingMethods &
                            WebDefender.TrackerDomain.TRACKING_METHOD_HTML5_LOCAL_STORAGE) != 0) {
                        numStorageTrackers++;
                    }
                    if ((mWebDefenderStatus.mTrackerDomains[i].mTrackingMethods &
                            WebDefender.TrackerDomain.TRACKING_METHOD_CANVAS_FINGERPRINT) != 0) {
                        numFingerprintTrackers++;
                    }
                }
            }

            int max = Math.max(Math.max(numCookieTrackers, numFingerprintTrackers),
                    Math.max(numStorageTrackers, numFontEnumTrackers));

            TextView view = (TextView) child.findViewById(R.id.cookie_storage);
            if (view != null) {
                view.setText(getStringForCount(numCookieTrackers));
                view.setCompoundDrawablesWithIntrinsicBounds(
                        normalizedBarGraphDrawable(numCookieTrackers, max), null, null, null);
            }
            view = (TextView) child.findViewById(R.id.html5_storage);
            if (view != null) {
                view.setText(getStringForCount(numStorageTrackers));
                view.setCompoundDrawablesWithIntrinsicBounds(
                        normalizedBarGraphDrawable(numStorageTrackers, max), null, null, null);
            }
            view = (TextView) child.findViewById(R.id.fingerprinting);
            if (view != null) {
                view.setText(getStringForCount(numFingerprintTrackers));
                view.setCompoundDrawablesWithIntrinsicBounds(
                        normalizedBarGraphDrawable(numFingerprintTrackers, max), null, null, null);
            }
            view = (TextView) child.findViewById(R.id.font_enumeration);
            if (view != null) {
                view.setText(getStringForCount(numFontEnumTrackers));
                view.setCompoundDrawablesWithIntrinsicBounds(
                        normalizedBarGraphDrawable(numFontEnumTrackers, max), null, null, null);
            }
        } else if (child.getId() == R.id.webdefender_vectorlist_layout) {
            WebDefenderVectorsRecyclerView view =
                    (WebDefenderVectorsRecyclerView) child.findViewById(R.id.webdefender_vectors);
            if (view != null && mWebDefenderStatus != null) {
                mVectorsRecyclerView = view;
                mVectorsRecyclerView.setUpdatedDomains(mUpdatedTrackerDomains);
                view.updateVectorArray(sortDomains(mWebDefenderStatus.mTrackerDomains));
            }
        } else if (child.getId() == R.id.secure_connect_details_layout) {
            SecureConnectDetails view = (SecureConnectDetails)
                    child.findViewById(R.id.secure_connect_recycler);
            if (view != null && mSecureConnectStatus != null) {
                mSecureConnectRecycler = view;
                mSecureConnectRecycler.setUpdatedRulesets(mUpdatedRulesets);
                mSecureConnectRecycler.updateRulsetArray(mSortedSecureConnectRulesets);
            }
        }
    }

    private WebDefender.TrackerDomain[] sortDomains(WebDefender.TrackerDomain[] trackerDomains) {
        Comparator<WebDefender.TrackerDomain> trackerDomainComparator =
                new Comparator<WebDefender.TrackerDomain>() {
            @Override
            public int compare(WebDefender.TrackerDomain lhs, WebDefender.TrackerDomain rhs) {
                if (mUpdatedTrackerDomains != null
                        && (mUpdatedTrackerDomains.containsKey(lhs.mName)
                        || mUpdatedTrackerDomains.containsKey(rhs.mName))) {

                    int lAction;
                    int rAction;
                    if (mUpdatedTrackerDomains.containsKey(lhs.mName)
                            && !mUpdatedTrackerDomains.containsKey(rhs.mName)) {
                        lAction = mUpdatedTrackerDomains.get(lhs.mName);
                        if (lAction < 0) return 0;
                        return -1;
                    } else if (!mUpdatedTrackerDomains.containsKey(lhs.mName)
                            && mUpdatedTrackerDomains.containsKey(rhs.mName)) {
                        rAction = mUpdatedTrackerDomains.get(rhs.mName);
                        if (rAction < 0) return 0;
                        return 1;
                    } else {
                        lAction = mUpdatedTrackerDomains.get(lhs.mName);
                        rAction = mUpdatedTrackerDomains.get(rhs.mName);
                        if (lAction >=0 && rAction < 0) return -1;
                        else if (lAction >0 && rAction <= 0) return 1;
                        else return 0;
                    }
                }
                //list is reverse sorted to handle the way recyclerview adds elements.
                else if (lhs.mUsesUserDefinedProtectiveAction
                        || rhs.mUsesUserDefinedProtectiveAction) {
                    if (lhs.mUsesUserDefinedProtectiveAction
                            && rhs.mUsesUserDefinedProtectiveAction) {
                        return 0;
                    } else {
                        return (lhs.mUsesUserDefinedProtectiveAction) ? -1 : 1;
                    }
                } else if (lhs.mProtectiveAction != rhs.mProtectiveAction) {
                    if (lhs.mProtectiveAction
                            == WebDefender.TrackerDomain.PROTECTIVE_ACTION_BLOCK_URL) {
                        return -1;
                    } else if (lhs.mProtectiveAction
                            == WebDefender.TrackerDomain.PROTECTIVE_ACTION_BLOCK_COOKIES
                            && rhs.mProtectiveAction
                            != WebDefender.TrackerDomain.PROTECTIVE_ACTION_BLOCK_URL) {
                        return -1;
                    } else {
                        return 1;
                    }
                } else if (lhs.mPotentialTracker || rhs.mPotentialTracker) {
                    if (lhs.mPotentialTracker
                            && rhs.mPotentialTracker) {
                        return 0;
                    } else {
                        return (lhs.mPotentialTracker) ? -1 : 1;
                    }
                } else {
                    return 0;
                }
            }
        };
        Arrays.sort(trackerDomains, trackerDomainComparator);
        return trackerDomains;
    }

    @Override
    public void onStop () {
        super.onStop();

        if (mVectorsRecyclerView != null) {
            HashMap<String, Integer> updatedDomains;
            updatedDomains = mVectorsRecyclerView.getUpdatedDomains();
            if (updatedDomains != null) {
                writeUpdatedInfo(updatedDomains, DOMAIN_PREF);
            }
        }

        if (mSecureConnectRecycler != null) {
            HashMap<String, Boolean> updatedRulesets;
            updatedRulesets = mSecureConnectRecycler.getUpdatedDomains();
            if (updatedRulesets != null) {
                writeUpdatedInfo(updatedRulesets, SECURE_CONNECT_RULESET_PREF);
            }
        }

        if (mSecureConnectMainframe != null) {
            HashMap<String, Boolean> updatedMainFrame = new HashMap<>();
            updatedMainFrame.put(mSecureConnectMainframe.getTitle().toString(),
                    mSecureConnectMainframe.getChecked());
            writeUpdatedInfo(updatedMainFrame, SECURE_CONNECT_MAINFRAME_PREF);
        }
    }

    private void writeUpdatedInfo(Object updatedInfo, String preference) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ObjectOutputStream objectOutputStream;
        SharedPreferences prefs = getActivity()
                .getSharedPreferences(SMART_PROTECT_DETAILS_PREF, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        try {
            objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
            objectOutputStream.writeObject(updatedInfo);
            byte[] data = byteArrayOutputStream.toByteArray();
            objectOutputStream.close();
            byteArrayOutputStream.close();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Base64OutputStream base64OutputStream = new Base64OutputStream(out, Base64.DEFAULT);
            base64OutputStream.write(data);
            base64OutputStream.close();
            out.close();

            editor.putString(preference, new String(out.toByteArray()));
            editor.apply();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (WEB_DEFENDER_GROUP.equals(preference.getKey()) && mWebDefenderGroup != null) {
            mWebDefenderGroup.setExpanded(!mWebDefenderGroup.getExpanded());
            TintedDrawable icon = TintedDrawable.constructTintedDrawable(getResources(),
                    mWebDefenderGroup.getExpanded()
                            ? R.drawable.ic_expanded : R.drawable.ic_collapsed);
            mWebDefenderGroup.setIcon(icon);
            if (!mWebDefenderGroup.getExpanded()) {
                mWebDefenderGroup.removeAll();
            } else {
                if (mWebDefenderStatus.mTrackerDomains.length != 0)
                    mWebDefenderGroup.addPreference(mWebDefenderVectorList);
                mWebDefenderGroup.addPreference(mWebDefenderVectorChart);
            }
            return true;
        } else if (SECURE_CONNECT_GROUP.equals(preference.getKey())
                && mSecureConnectGroup != null) {
            mSecureConnectGroup.setExpanded((!mSecureConnectGroup.getExpanded()));
            TintedDrawable icon = TintedDrawable.constructTintedDrawable(getResources(),
                    mSecureConnectGroup.getExpanded()
                            ? R.drawable.ic_expanded : R.drawable.ic_collapsed);
            mSecureConnectGroup.setIcon(icon);
            if(!mSecureConnectGroup.getExpanded()) {
                mSecureConnectGroup.removeAll();
            } else if (mSecureConnectStatus != null) {
                if (mSecureConnectMainframe != null && mSecureConnectStatus.hasMainFrameUrl()) {
                    mSecureConnectGroup.addPreference(mSecureConnectMainframe);
                }
                if (mSortedSecureConnectRulesets.size() > 0)
                    mSecureConnectGroup.addPreference(mSecureConnectList);
            }
        } else if (mSecureConnectMainframe.getKey().equals(preference.getKey())) {
            SecureConnectPreferenceHandler.updateRuleset(
                    mSecureConnectMainframe.getTitle().toString(),
                    mSecureConnectMainframe.getChecked());
        }
        return false;
    }
}

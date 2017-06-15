// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.AccessibilityDelegate;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.BaseAdapter;
import android.widget.RadioButton;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.Log;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.chrome.browser.locale.LocaleManager;
import org.chromium.chrome.browser.omnibox.geo.GeolocationHeader;
import org.chromium.chrome.browser.preferences.website.ContentSetting;
import org.chromium.chrome.browser.preferences.website.GeolocationInfo;
import org.chromium.chrome.browser.preferences.website.SingleWebsitePreferences;
import org.chromium.chrome.browser.preferences.website.WebsitePreferenceBridge;
import org.chromium.chrome.browser.search_engines.TemplateUrlService;
import org.chromium.chrome.browser.search_engines.TemplateUrlService.TemplateUrl;
import org.chromium.components.location.LocationUtils;
import org.chromium.ui.text.SpanApplier;
import org.chromium.ui.text.SpanApplier.SpanInfo;

import java.util.ArrayList;
import java.util.List;

/**
* A custom adapter for listing search engines.
*/
public class SearchEngineAdapter extends BaseAdapter
        implements TemplateUrlService.LoadListener, TemplateUrlService.TemplateUrlServiceObserver,
                OnClickListener {
    private static final String TAG = "cr_SearchEngines";

    private static final int VIEW_TYPE_ITEM = 0;
    private static final int VIEW_TYPE_DIVIDER = 1;
    private static final int VIEW_TYPE_COUNT = 2;

    /** The current context. */
    private Context mContext;

    /** The layout inflater to use for the custom views. */
    private LayoutInflater mLayoutInflater;

    /** The list of prepopluated and default search engines. */
    private List<TemplateUrl> mPrepopulatedSearchEngines = new ArrayList<>();

    /** The list of recently visited search engines. */
    private List<TemplateUrl> mRecentSearchEngines = new ArrayList<>();

    /**
     * The position (index into mPrepopulatedSearchEngines) of the currently selected search engine.
     * Can be -1 if current search engine is managed and set to something other than the
     * pre-populated values.
     */
    private int mSelectedSearchEnginePosition = -1;

    /** The position of the default search engine before user's action. */
    private int mInitialEnginePosition = -1;

    private boolean mHasLoadObserver;

    private boolean mIsLocationPermissionChanged;

    /**
     * Construct a SearchEngineAdapter.
     * @param context The current context.
     */
    public SearchEngineAdapter(Context context) {
        mContext = context;
        mLayoutInflater = (LayoutInflater) mContext.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
    }

    /**
     * Start the adapter to gather the available search engines and listen for updates.
     */
    public void start() {
        refreshData();
        TemplateUrlService.getInstance().addObserver(this);
    }

    /**
     * Stop the adapter from listening for future search engine updates.
     */
    public void stop() {
        if (mHasLoadObserver) {
            TemplateUrlService.getInstance().unregisterLoadListener(this);
            mHasLoadObserver = false;
        }
        TemplateUrlService.getInstance().removeObserver(this);
    }

    @VisibleForTesting
    String getValueForTesting() {
        return Integer.toString(mSelectedSearchEnginePosition);
    }

    @VisibleForTesting
    String setValueForTesting(String value) {
        return searchEngineSelected(Integer.parseInt(value));
    }

    @VisibleForTesting
    String getKeywordForTesting(int index) {
        return toKeyword(index);
    }

    /**
     * Initialize the search engine list.
     */
    private void refreshData() {
        TemplateUrlService templateUrlService = TemplateUrlService.getInstance();
        if (!templateUrlService.isLoaded()) {
            mHasLoadObserver = true;
            templateUrlService.registerLoadListener(this);
            templateUrlService.load();
            return;  // Flow continues in onTemplateUrlServiceLoaded below.
        }

        List<TemplateUrl> templateUrls = templateUrlService.getSearchEngines();
        boolean forceRefresh = mIsLocationPermissionChanged;
        mIsLocationPermissionChanged = false;
        if (!didSearchEnginesChange(templateUrls)) {
            if (forceRefresh) notifyDataSetChanged();
            return;
        }

        mPrepopulatedSearchEngines = new ArrayList<>();
        mRecentSearchEngines = new ArrayList<>();

        for (int i = 0; i < templateUrls.size(); i++) {
            TemplateUrl templateUrl = templateUrls.get(i);
            if (templateUrl.getType() == TemplateUrlService.TYPE_PREPOPULATED
                    || templateUrl.getType() == TemplateUrlService.TYPE_DEFAULT) {
                mPrepopulatedSearchEngines.add(templateUrl);
            } else {
                mRecentSearchEngines.add(templateUrl);
            }
        }

        int defaultSearchEngineIndex =
                TemplateUrlService.getInstance().getDefaultSearchEngineIndex();

        // Convert the TemplateUrl index into an index of mSearchEngines.
        mSelectedSearchEnginePosition = -1;
        for (int i = 0; i < mPrepopulatedSearchEngines.size(); ++i) {
            if (mPrepopulatedSearchEngines.get(i).getIndex() == defaultSearchEngineIndex) {
                mSelectedSearchEnginePosition = i;
            }
        }

        for (int i = 0; i < mRecentSearchEngines.size(); ++i) {
            if (mRecentSearchEngines.get(i).getIndex() == defaultSearchEngineIndex) {
                // Add one to offset the title for the recent search engine list.
                mSelectedSearchEnginePosition = i + computeStartIndexForRecentSearchEngines();
            }
        }

        if (mSelectedSearchEnginePosition == -1) {
            throw new IllegalStateException(
                    "Default search engine index did not match any available search engines.");
        }

        mInitialEnginePosition = mSelectedSearchEnginePosition;

        notifyDataSetChanged();
    }

    private static boolean containsTemplateUrl(
            List<TemplateUrl> templateUrls, TemplateUrl targetTemplateUrl) {
        for (int i = 0; i < templateUrls.size(); i++) {
            TemplateUrl templateUrl = templateUrls.get(i);
            // Explicitly excluding TemplateUrlType and Index as they might change if a search
            // engine is set as default.
            if (templateUrl.getIsPrepopulated() == targetTemplateUrl.getIsPrepopulated()
                    && TextUtils.equals(templateUrl.getKeyword(), targetTemplateUrl.getKeyword())
                    && TextUtils.equals(
                               templateUrl.getShortName(), targetTemplateUrl.getShortName())) {
                return true;
            }
        }
        return false;
    }

    private boolean didSearchEnginesChange(List<TemplateUrl> templateUrls) {
        if (templateUrls.size()
                != mPrepopulatedSearchEngines.size() + mRecentSearchEngines.size()) {
            return true;
        }
        for (int i = 0; i < templateUrls.size(); i++) {
            TemplateUrl templateUrl = templateUrls.get(i);
            if (!containsTemplateUrl(mPrepopulatedSearchEngines, templateUrl)
                    && !SearchEngineAdapter.containsTemplateUrl(
                               mRecentSearchEngines, templateUrl)) {
                return true;
            }
        }
        return false;
    }

    private String toKeyword(int position) {
        if (position < mPrepopulatedSearchEngines.size()) {
            return mPrepopulatedSearchEngines.get(position).getKeyword();
        } else {
            position -= computeStartIndexForRecentSearchEngines();
            return mRecentSearchEngines.get(position).getKeyword();
        }
    }

    // BaseAdapter:

    @Override
    public int getCount() {
        int size = 0;
        if (mPrepopulatedSearchEngines != null) {
            size += mPrepopulatedSearchEngines.size();
        }
        if (mRecentSearchEngines != null && mRecentSearchEngines.size() != 0) {
            // Account for the header by adding one to the size.
            size += mRecentSearchEngines.size() + 1;
        }
        return size;
    }

    @Override
    public int getViewTypeCount() {
        return VIEW_TYPE_COUNT;
    }

    @Override
    public Object getItem(int pos) {
        if (pos < mPrepopulatedSearchEngines.size()) {
            return mPrepopulatedSearchEngines.get(pos);
        } else if (pos > mPrepopulatedSearchEngines.size()) {
            pos -= computeStartIndexForRecentSearchEngines();
            return mRecentSearchEngines.get(pos);
        }
        return null;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getItemViewType(int position) {
        if (position == mPrepopulatedSearchEngines.size() && mRecentSearchEngines.size() != 0) {
            return VIEW_TYPE_DIVIDER;
        } else {
            return VIEW_TYPE_ITEM;
        }
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView;
        int itemViewType = getItemViewType(position);
        if (convertView == null) {
            view = mLayoutInflater.inflate(
                    itemViewType == VIEW_TYPE_DIVIDER && mRecentSearchEngines.size() != 0
                            ? R.layout.search_engine_recent_title
                            : R.layout.search_engine,
                    null);
        }
        if (itemViewType == VIEW_TYPE_DIVIDER) {
            return view;
        }

        view.setOnClickListener(this);
        view.setTag(position);

        // TODO(finnur): There's a tinting bug in the AppCompat lib (see http://crbug.com/474695),
        // which causes the first radiobox to always appear selected, even if it is not. It is being
        // addressed, but in the meantime we should use the native RadioButton instead.
        RadioButton radioButton = (RadioButton) view.findViewById(R.id.radiobutton);
        // On Lollipop this removes the redundant animation ring on selection but on older versions
        // it would cause the radio button to disappear.
        // TODO(finnur): Remove the encompassing if statement once we go back to using the AppCompat
        // control.
        final boolean selected = position == mSelectedSearchEnginePosition;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            radioButton.setBackgroundResource(0);
        }
        radioButton.setChecked(selected);

        TextView description = (TextView) view.findViewById(R.id.name);
        Resources resources = mContext.getResources();

        TemplateUrl templateUrl = (TemplateUrl) getItem(position);
        description.setText(templateUrl.getShortName());

        TextView url = (TextView) view.findViewById(R.id.url);
        url.setText(templateUrl.getKeyword());
        if (TextUtils.isEmpty(templateUrl.getKeyword())) {
            url.setVisibility(View.GONE);
        }

        // To improve the explore-by-touch experience, the radio button is hidden from accessibility
        // and instead, "checked" or "not checked" is read along with the search engine's name, e.g.
        // "google.com checked" or "google.com not checked".
        radioButton.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        description.setAccessibilityDelegate(new AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityEvent(View host, AccessibilityEvent event) {
                super.onInitializeAccessibilityEvent(host, event);
                event.setChecked(selected);
            }

            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setCheckable(true);
                info.setChecked(selected);
            }
        });

        TextView link = (TextView) view.findViewById(R.id.location_permission);
        link.setVisibility(selected ? View.VISIBLE : View.GONE);
        if (TemplateUrlService.getInstance().getSearchEngineUrlFromTemplateUrl(
                templateUrl.getKeyword()) == null) {
            Log.e(TAG, "Invalid template URL found: %s", templateUrl);
            assert false;
            link.setVisibility(View.GONE);
        } else if (selected) {
            if (!locationShouldBeShown(templateUrl)) {
                link.setVisibility(View.GONE);
            } else {
                ForegroundColorSpan linkSpan = new ForegroundColorSpan(
                        ApiCompatibilityUtils.getColor(resources, R.color.google_blue_700));
                // If location is allowed but system location is off, show a message explaining how
                // to turn location on.
                if (locationEnabled(templateUrl)
                        && !LocationUtils.getInstance().isSystemLocationSettingEnabled()) {
                    link.setText(SpanApplier.applySpans(
                            mContext.getString(R.string.android_location_off),
                            new SpanInfo("<link>", "</link>", linkSpan)));
                } else {
                    String message = mContext.getString(locationEnabled(templateUrl)
                                    ? R.string.search_engine_location_allowed
                                    : R.string.search_engine_location_blocked);
                    SpannableString messageWithLink = new SpannableString(message);
                    messageWithLink.setSpan(linkSpan, 0, messageWithLink.length(), 0);
                    link.setText(messageWithLink);
                }

                link.setOnClickListener(this);
            }
        }

        return view;
    }

    // TemplateUrlService.LoadListener

    @Override
    public void onTemplateUrlServiceLoaded() {
        TemplateUrlService.getInstance().unregisterLoadListener(this);
        mHasLoadObserver = false;
        refreshData();
    }

    @Override
    public void onTemplateURLServiceChanged() {
        refreshData();
    }

    // OnClickListener:

    @Override
    public void onClick(View view) {
        if (view.getTag() == null) {
            onLocationLinkClicked();
        } else {
            searchEngineSelected((int) view.getTag());
        }
    }

    private String searchEngineSelected(int position) {
        // Record the change in search engine.
        mSelectedSearchEnginePosition = position;

        String keyword = toKeyword(mSelectedSearchEnginePosition);
        TemplateUrlService.getInstance().setSearchEngine(keyword);

        // If the user has manually set the default search engine, disable auto switching.
        boolean manualSwitch = mSelectedSearchEnginePosition != mInitialEnginePosition;
        if (manualSwitch) {
            RecordUserAction.record("SearchEngine_ManualChange");
            LocaleManager.getInstance().setSearchEngineAutoSwitch(false);
        }
        notifyDataSetChanged();
        return keyword;
    }

    private void onLocationLinkClicked() {
        mIsLocationPermissionChanged = true;
        if (!LocationUtils.getInstance().isSystemLocationSettingEnabled()) {
            mContext.startActivity(LocationUtils.getInstance().getSystemLocationSettingsIntent());
        } else {
            Intent settingsIntent = PreferencesLauncher.createIntentForSettingsPage(
                    mContext, SingleWebsitePreferences.class.getName());
            String url = TemplateUrlService.getInstance().getSearchEngineUrlFromTemplateUrl(
                    toKeyword(mSelectedSearchEnginePosition));
            Bundle fragmentArgs = SingleWebsitePreferences.createFragmentArgsForSite(url);
            fragmentArgs.putBoolean(SingleWebsitePreferences.EXTRA_LOCATION,
                    locationEnabled((TemplateUrl) getItem(mSelectedSearchEnginePosition)));
            settingsIntent.putExtra(Preferences.EXTRA_SHOW_FRAGMENT_ARGUMENTS, fragmentArgs);
            mContext.startActivity(settingsIntent);
        }
    }

    private String getSearchEngineUrl(TemplateUrl templateUrl) {
        if (templateUrl == null) {
            Log.e(TAG, "Invalid null template URL found");
            assert false;
            return "";
        }

        String url = TemplateUrlService.getInstance().getSearchEngineUrlFromTemplateUrl(
                templateUrl.getKeyword());
        if (url == null) {
            Log.e(TAG, "Invalid template URL found: %s", templateUrl);
            assert false;
            return "";
        }

        return url;
    }

    private boolean locationShouldBeShown(TemplateUrl templateUrl) {
        String url = getSearchEngineUrl(templateUrl);
        if (url.isEmpty()) return false;

        // Do not show location if the scheme isn't HTTPS.
        Uri uri = Uri.parse(url);
        if (!UrlConstants.HTTPS_SCHEME.equals(uri.getScheme())) return false;

        // Only show the location setting if it is explicitly enabled or disabled.
        GeolocationInfo locationSettings = new GeolocationInfo(url, null, false);
        ContentSetting locationPermission = locationSettings.getContentSetting();
        if (locationPermission != ContentSetting.ASK) return true;

        if (ChromeFeatureList.isEnabled(ChromeFeatureList.CONSISTENT_OMNIBOX_GEOLOCATION)) {
            return WebsitePreferenceBridge.shouldUseDSEGeolocationSetting(url, false);
        }

        return GeolocationHeader.isGeoHeaderEnabledForUrl(url, false);
    }

    private boolean locationEnabled(TemplateUrl templateUrl) {
        String url = getSearchEngineUrl(templateUrl);
        if (url.isEmpty()) return false;

        GeolocationInfo locationSettings = new GeolocationInfo(url, null, false);
        ContentSetting locationPermission = locationSettings.getContentSetting();
        if (locationPermission == ContentSetting.ASK) {
            // Handle the case where the geoHeader being sent when no permission has been specified.
            if (ChromeFeatureList.isEnabled(ChromeFeatureList.CONSISTENT_OMNIBOX_GEOLOCATION)) {
                if (WebsitePreferenceBridge.shouldUseDSEGeolocationSetting(url, false)) {
                    return WebsitePreferenceBridge.getDSEGeolocationSetting();
                }
            } else if (GeolocationHeader.isGeoHeaderEnabledForUrl(url, false)) {
                return true;
            }
        }

        return locationPermission == ContentSetting.ALLOW;
    }

    private int computeStartIndexForRecentSearchEngines() {
        // If there are custom search engines to show, add 1 for showing the  "Recently visited"
        // header.
        if (mRecentSearchEngines.size() > 0) {
            return mPrepopulatedSearchEngines.size() + 1;
        } else {
            return mPrepopulatedSearchEngines.size();
        }
    }
}

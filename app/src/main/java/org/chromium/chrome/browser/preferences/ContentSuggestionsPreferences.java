// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceFragment;
import android.support.annotation.IntDef;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.help.HelpAndFeedback;
import org.chromium.chrome.browser.ntp.ContentSuggestionsNotificationHelper;
import org.chromium.chrome.browser.ntp.snippets.ContentSuggestionsNotificationAction;
import org.chromium.chrome.browser.ntp.snippets.ContentSuggestionsNotificationOptOut;
import org.chromium.chrome.browser.ntp.snippets.SnippetsBridge;
import org.chromium.chrome.browser.profiles.Profile;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Settings fragment that allows the user to configure content suggestions.
 */
// TODO(https://crbug.com/710636): Remove when the suggestions preference design is stabilised.
public class ContentSuggestionsPreferences extends PreferenceFragment {
    private static final String PREF_MAIN_SWITCH = "suggestions_switch";
    private static final String PREF_NOTIFICATIONS_SWITCH = "suggestions_notifications_switch";
    private static final String PREF_CAVEATS = "suggestions_caveats";
    private static final String PREF_LEARN_MORE = "suggestions_learn_more";

    private static final String LAUNCH_SOURCE_EXTRA = "source";

    @IntDef({LAUNCH_SOURCE_SETTINGS, LAUNCH_SOURCE_NOTIFICATION})
    @Retention(RetentionPolicy.SOURCE)
    public @interface LaunchSource {}
    public static final int LAUNCH_SOURCE_SETTINGS = 0;
    public static final int LAUNCH_SOURCE_NOTIFICATION = 1;

    private boolean mIsEnabled;

    // Preferences, modified as the state of the screen changes.
    private ChromeSwitchPreference mFeatureSwitch;
    private ChromeSwitchPreference mNotificationsSwitch;
    private Preference mCaveatsDescription;
    private Preference mLearnMoreButton;

    /**
     * Creates an intent for launching the content suggestions settings page.
     * @param context The current Activity, or an application context if no Activity is available.
     * @param source Where the intent is going to be launched from. See {@link LaunchSource}
     */
    public static Intent createLaunchIntent(Context context, @LaunchSource int source) {
        Intent intent = PreferencesLauncher.createIntentForSettingsPage(
                context, ContentSuggestionsPreferences.class.getName());
        intent.putExtra(LAUNCH_SOURCE_EXTRA, source);
        return intent;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.suggestions_preferences);
        setHasOptionsMenu(true);
        finishSwitchInitialisation();

        boolean isEnabled = SnippetsBridge.areRemoteSuggestionsEnabled();
        mIsEnabled = !isEnabled; // Opposite so that we trigger side effects below.
        updatePreferences(isEnabled);

        @LaunchSource
        int launchSource =
                getActivity().getIntent().getIntExtra(LAUNCH_SOURCE_EXTRA, LAUNCH_SOURCE_SETTINGS);
        if (launchSource == LAUNCH_SOURCE_NOTIFICATION) {
            ContentSuggestionsNotificationHelper.recordNotificationAction(
                    ContentSuggestionsNotificationAction.OPEN_SETTINGS);
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.clear();
        MenuItem help =
                menu.add(Menu.NONE, R.id.menu_id_targeted_help, Menu.NONE, R.string.menu_help);
        help.setIcon(R.drawable.ic_help_and_feedback);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_id_targeted_help) {
            // TODO(dgn): The help page needs to be added and the context reserved.
            HelpAndFeedback.getInstance(getActivity())
                    .show(getActivity(), getString(R.string.help_context_suggestions),
                            Profile.getLastUsedProfile(), null);
            return true;
        }
        return false;
    }

    /**
     * Switches preference screens depending on whether the remote suggestions are enabled/disabled.
     * @param isEnabled Indicates whether the remote suggestions are enabled.
     */
    public void updatePreferences(boolean isEnabled) {
        if (mIsEnabled == isEnabled) return;

        mFeatureSwitch.setChecked(isEnabled);
        mIsEnabled = isEnabled;

        if (canShowNotificationsSwitch()) {
            mFeatureSwitch.setSummaryOn(R.string.suggestions_feature_switch_on_summary);
            setNotificationsPrefState(true);
            mNotificationsSwitch.setChecked(
                    SnippetsBridge.areContentSuggestionsNotificationsEnabled());
            setCaveatsPrefState(false);
        } else {
            mFeatureSwitch.setSummaryOn(R.string.text_on);
            setNotificationsPrefState(false);
            setCaveatsPrefState(true);
        }
    }

    private boolean canShowNotificationsSwitch() {
        if (!ChromeFeatureList.isEnabled(ChromeFeatureList.CONTENT_SUGGESTIONS_NOTIFICATIONS)) {
            return false;
        }
        return mIsEnabled;
    }

    private void setNotificationsPrefState(boolean visible) {
        if (visible) {
            if (findPreference(PREF_NOTIFICATIONS_SWITCH) != null) return;
            getPreferenceScreen().addPreference(mNotificationsSwitch);
        } else {
            getPreferenceScreen().removePreference(mNotificationsSwitch);
        }
    }

    private void setCaveatsPrefState(boolean visible) {
        if (visible) {
            if (findPreference(PREF_CAVEATS) != null) return;
            getPreferenceScreen().addPreference(mCaveatsDescription);
            getPreferenceScreen().addPreference(mLearnMoreButton);
        } else {
            getPreferenceScreen().removePreference(mCaveatsDescription);
            getPreferenceScreen().removePreference(mLearnMoreButton);
        }
    }

    private void finishSwitchInitialisation() {
        mFeatureSwitch = (ChromeSwitchPreference) findPreference(PREF_MAIN_SWITCH);
        mNotificationsSwitch = (ChromeSwitchPreference) findPreference(PREF_NOTIFICATIONS_SWITCH);
        mCaveatsDescription = findPreference(PREF_CAVEATS);
        mLearnMoreButton = findPreference(PREF_LEARN_MORE);

        mFeatureSwitch.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                boolean isEnabled = (boolean) newValue;
                SnippetsBridge.setRemoteSuggestionsEnabled(isEnabled);
                // TODO(dgn): Is there a way to have a visual feedback of when the remote
                // suggestions service has completed being turned on or off?
                updatePreferences(isEnabled);

                if (isEnabled) {
                    RecordUserAction.record("ContentSuggestions.RemoteSuggestionsPreferenceOn");
                } else {
                    RecordUserAction.record("ContentSuggestions.RemoteSuggestionsPreferenceOff");
                }

                return true;
            }
        });
        mFeatureSwitch.setManagedPreferenceDelegate(new ManagedPreferenceDelegate() {
            @Override
            public boolean isPreferenceControlledByPolicy(Preference preference) {
                return SnippetsBridge.areRemoteSuggestionsManaged();
            }

            @Override
            public boolean isPreferenceControlledByCustodian(Preference preference) {
                return SnippetsBridge.areRemoteSuggestionsManagedByCustodian();
            }
        });

        mNotificationsSwitch.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                boolean isEnabled = (boolean) newValue;
                SnippetsBridge.setContentSuggestionsNotificationsEnabled(isEnabled);

                if (isEnabled) {
                    RecordUserAction.record("ContentSuggestions.NotificationsPreferenceOn");
                } else {
                    RecordUserAction.record("ContentSuggestions.NotificationsPreferenceOff");
                    ContentSuggestionsNotificationHelper.recordNotificationOptOut(
                            ContentSuggestionsNotificationOptOut.EXPLICIT);
                }

                return true;
            }
        });
    }
}

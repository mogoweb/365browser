// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.privacy;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.support.annotation.Nullable;
import android.support.graphics.drawable.VectorDrawableCompat;
import android.widget.ListView;

import org.chromium.base.VisibleForTesting;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.browsing_data.BrowsingDataType;
import org.chromium.chrome.browser.browsing_data.ClearBrowsingDataTab;
import org.chromium.chrome.browser.browsing_data.TimePeriod;
import org.chromium.chrome.browser.help.HelpAndFeedback;
import org.chromium.chrome.browser.historyreport.AppIndexingReporter;
import org.chromium.chrome.browser.multiwindow.MultiWindowUtils;
import org.chromium.chrome.browser.preferences.ButtonPreference;
import org.chromium.chrome.browser.preferences.ClearBrowsingDataCheckBoxPreference;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.preferences.PreferenceUtils;
import org.chromium.chrome.browser.preferences.SpinnerPreference;
import org.chromium.chrome.browser.preferences.TextMessageWithLinkAndIconPreference;
import org.chromium.chrome.browser.preferences.privacy.BrowsingDataCounterBridge.BrowsingDataCounterCallback;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.tabmodel.TabModel.TabLaunchType;
import org.chromium.chrome.browser.tabmodel.document.TabDelegate;
import org.chromium.chrome.browser.widget.TintedDrawable;
import org.chromium.components.signin.ChromeSigninController;

import java.util.Arrays;
import java.util.EnumSet;

/**
 * Preference screen that allows the user to clear browsing data.
 * The user can choose which types of data to clear (history, cookies, etc), and the time range
 * from which to clear data.
 */
public class ClearBrowsingDataPreferences extends PreferenceFragment
        implements BrowsingDataBridge.ImportantSitesCallback,
                   BrowsingDataBridge.OnClearBrowsingDataListener,
                   BrowsingDataBridge.OtherFormsOfBrowsingHistoryListener,
                   Preference.OnPreferenceClickListener, Preference.OnPreferenceChangeListener {
    /**
     * Represents a single item in the dialog.
     */
    private static class Item implements BrowsingDataCounterCallback,
                                         Preference.OnPreferenceClickListener {
        private static final int MIN_DP_FOR_ICON = 360;
        private final ClearBrowsingDataPreferences mParent;
        private final DialogOption mOption;
        private final ClearBrowsingDataCheckBoxPreference mCheckbox;
        private BrowsingDataCounterBridge mCounter;
        private boolean mShouldAnnounceCounterResult;

        public Item(ClearBrowsingDataPreferences parent,
                    DialogOption option,
                    ClearBrowsingDataCheckBoxPreference checkbox,
                    boolean selected,
                    boolean enabled) {
            super();
            mParent = parent;
            mOption = option;
            mCheckbox = checkbox;
            mCounter = new BrowsingDataCounterBridge(
                    this, mOption.getDataType(), mParent.getPreferenceType());

            mCheckbox.setOnPreferenceClickListener(this);
            mCheckbox.setEnabled(enabled);
            mCheckbox.setChecked(selected);

            if (ClearBrowsingDataTabsFragment.isFeatureEnabled()) {
                int dp = mParent.getResources().getConfiguration().smallestScreenWidthDp;
                if (dp >= MIN_DP_FOR_ICON) {
                    if (option.iconIsBitmap()) {
                        Drawable icon = TintedDrawable.constructTintedDrawable(
                                mParent.getResources(), option.getIcon(), R.color.google_grey_600);
                        mCheckbox.setIcon(icon);
                    } else {
                        Drawable icon = VectorDrawableCompat.create(mParent.getResources(),
                                option.getIcon(), mParent.getActivity().getTheme());
                        mCheckbox.setIcon(icon);
                    }
                }
            } else {
                // No summary when unchecked. The redesigned basic and advanced
                // CBD views will always show the checkbox summary.
                mCheckbox.setSummaryOff("");
            }
        }

        public void destroy() {
            mCounter.destroy();
        }

        public DialogOption getOption() {
            return mOption;
        }

        public boolean isSelected() {
            return mCheckbox.isChecked();
        }

        @Override
        public boolean onPreferenceClick(Preference preference) {
            assert mCheckbox == preference;

            mParent.updateButtonState();
            mShouldAnnounceCounterResult = true;
            PrefServiceBridge.getInstance().setBrowsingDataDeletionPreference(
                    mOption.getDataType(), mParent.getPreferenceType(), mCheckbox.isChecked());
            return true;
        }

        @Override
        public void onCounterFinished(String result) {
            // The new dialog will always show the summary, the old one only when checked.
            if (ClearBrowsingDataTabsFragment.isFeatureEnabled()) {
                mCheckbox.setSummary(result);
            } else {
                mCheckbox.setSummaryOn(result);
            }
            if (mShouldAnnounceCounterResult) {
                mCheckbox.announceForAccessibility(result);
            }
        }

        /**
         * Sets whether the BrowsingDataCounter result should be announced. This is when the counter
         * recalculation was caused by a checkbox state change (as opposed to fragment
         * initialization or time period change).
         */
        public void setShouldAnnounceCounterResult(boolean value) {
            mShouldAnnounceCounterResult = value;
        }
    }

    @VisibleForTesting
    public static final String PREF_HISTORY = "clear_history_checkbox";
    @VisibleForTesting
    public static final String PREF_COOKIES = "clear_cookies_checkbox";
    private static final String PREF_CACHE = "clear_cache_checkbox";
    private static final String PREF_PASSWORDS = "clear_passwords_checkbox";
    private static final String PREF_FORM_DATA = "clear_form_data_checkbox";
    private static final String PREF_SITE_SETTINGS = "clear_site_settings_checkbox";

    @VisibleForTesting
    public static final String PREF_GOOGLE_SUMMARY = "google_summary";
    @VisibleForTesting
    public static final String PREF_GENERAL_SUMMARY = "general_summary";
    private static final String PREF_TIME_RANGE = "time_period_spinner";

    /** The "Clear" button preference. */
    @VisibleForTesting
    public static final String PREF_CLEAR_BUTTON = "clear_button";

    /** The tag used for logging. */
    public static final String TAG = "ClearBrowsingDataPreferences";

    /** The histogram for the dialog about other forms of browsing history. */
    private static final String DIALOG_HISTOGRAM =
            "History.ClearBrowsingData.ShownHistoryNoticeAfterClearing";

    /** The web history URL. */
    private static final String WEB_HISTORY_URL =
            "https://history.google.com/history/?utm_source=chrome_cbd";

    /**
     * Used for the onActivityResult pattern. The value is arbitrary, just to distinguish from other
     * activities that we might be using onActivityResult with as well.
     */
    private static final int IMPORTANT_SITES_DIALOG_CODE = 1;

    private static final int IMPORTANT_SITES_PERCENTAGE_BUCKET_COUNT = 20;

    /**
     * The various data types that can be cleared via this screen.
     */
    public enum DialogOption {
        CLEAR_HISTORY(
                BrowsingDataType.HISTORY, PREF_HISTORY, R.drawable.ic_history_grey600_24dp, true),
        CLEAR_COOKIES_AND_SITE_DATA(
                BrowsingDataType.COOKIES, PREF_COOKIES, R.drawable.permission_cookie, true),
        CLEAR_CACHE(BrowsingDataType.CACHE, PREF_CACHE, R.drawable.ic_collections_grey, false),
        CLEAR_PASSWORDS(
                BrowsingDataType.PASSWORDS, PREF_PASSWORDS, R.drawable.ic_vpn_key_grey, false),
        CLEAR_FORM_DATA(BrowsingDataType.FORM_DATA, PREF_FORM_DATA, R.drawable.ic_edit_24dp, true),
        CLEAR_SITE_SETTINGS(BrowsingDataType.SITE_SETTINGS, PREF_SITE_SETTINGS,
                R.drawable.ic_tv_options_input_settings_rotated_grey, false);

        private final int mDataType;
        private final String mPreferenceKey;
        private final int mIcon;
        private final boolean mIsBitmap;

        private DialogOption(int dataType, String preferenceKey, int icon, boolean isBitmap) {
            mDataType = dataType;
            mPreferenceKey = preferenceKey;
            mIcon = icon;
            mIsBitmap = isBitmap;
        }

        /**
         * @return The {@link BrowsingDataType} this option represents.
         */
        public int getDataType() {
            return mDataType;
        }

        /**
         * @return The key of the corresponding preference.
         */
        public String getPreferenceKey() {
            return mPreferenceKey;
        }

        /**
         * @return The resource id for the icon that is used to display this option.
         */
        public int getIcon() {
            return mIcon;
        }

        /**
         * @return Whether the icon is a bitmap. Otherwise it's a vector.
         */
        public boolean iconIsBitmap() {
            return mIsBitmap;
        }
    }

    /**
     * An option to be shown in the time period spiner.
     */
    protected static class TimePeriodSpinnerOption {
        private int mTimePeriod;
        private String mTitle;

        /**
         * Constructs this time period spinner option.
         * @param timePeriod The time period represented as an int from the shared enum
         *     {@link TimePeriod}.
         * @param title The text that will be used to represent this item in the spinner.
         */
        public TimePeriodSpinnerOption(int timePeriod, String title) {
            mTimePeriod = timePeriod;
            mTitle = title;
        }

        /**
         * @return The time period represented as an int from the shared enum {@link TimePeriod}
         */
        public int getTimePeriod() {
            return mTimePeriod;
        }

        @Override
        public String toString() {
            return mTitle;
        }
    }

    private OtherFormsOfHistoryDialogFragment mDialogAboutOtherFormsOfBrowsingHistory;
    private boolean mIsDialogAboutOtherFormsOfBrowsingHistoryEnabled;

    private ProgressDialog mProgressDialog;
    private Item[] mItems;

    // This is a constant on the C++ side.
    private int mMaxImportantSites;
    // This is the sorted list of important registerable domains. If null, then we haven't finished
    // fetching them yet.
    private String[] mSortedImportantDomains;
    // These are the reasons the above domains were chosen as important.
    private int[] mSortedImportantDomainReasons;
    // These are full url examples of the domains above. We use them for favicons.
    private String[] mSortedExampleOrigins;
    // This is the dialog we show to the user that lets them 'uncheck' (or exclude) the above
    // important domains from being cleared.
    private ConfirmImportantSitesDialogFragment mConfirmImportantSitesDialog;

    /**
     * @return The currently selected DialogOptions.
     */
    protected final EnumSet<DialogOption> getSelectedOptions() {
        EnumSet<DialogOption> selected = EnumSet.noneOf(DialogOption.class);
        for (Item item : mItems) {
            if (item.isSelected()) selected.add(item.getOption());
        }
        return selected;
    }

    /**
     * Requests the browsing data corresponding to the given dialog options to be deleted.
     * @param options The dialog options whose corresponding data should be deleted.
     */
    private final void clearBrowsingData(EnumSet<DialogOption> options,
            @Nullable String[] blacklistedDomains, @Nullable int[] blacklistedDomainReasons,
            @Nullable String[] ignoredDomains, @Nullable int[] ignoredDomainReasons) {
        showProgressDialog();

        int[] dataTypes = new int[options.size()];
        int i = 0;
        for (DialogOption option : options) {
            dataTypes[i] = option.getDataType();
            ++i;
        }

        Object spinnerSelection =
                ((SpinnerPreference) findPreference(PREF_TIME_RANGE)).getSelectedOption();
        int timePeriod = ((TimePeriodSpinnerOption) spinnerSelection).getTimePeriod();
        if (blacklistedDomains != null && blacklistedDomains.length != 0) {
            BrowsingDataBridge.getInstance().clearBrowsingDataExcludingDomains(this, dataTypes,
                    timePeriod, blacklistedDomains, blacklistedDomainReasons, ignoredDomains,
                    ignoredDomainReasons);
        } else {
            BrowsingDataBridge.getInstance().clearBrowsingData(this, dataTypes, timePeriod);
        }

        // Clear all reported entities.
        AppIndexingReporter.getInstance().clearHistory();
    }

    private void dismissProgressDialog() {
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            mProgressDialog.dismiss();
        }
        mProgressDialog = null;
    }

    /**
     * Returns the Array of dialog options. Options are displayed in the same
     * order as they appear in the array.
     */
    protected DialogOption[] getDialogOptions() {
        return new DialogOption[] {
                DialogOption.CLEAR_HISTORY,
                DialogOption.CLEAR_COOKIES_AND_SITE_DATA,
                DialogOption.CLEAR_CACHE,
                DialogOption.CLEAR_PASSWORDS,
                DialogOption.CLEAR_FORM_DATA
        };
    }

    /**
     * Returns whether this preference page is a basic or advanced tab in order to use separate
     * preferences.
     */
    protected int getPreferenceType() {
        return ClearBrowsingDataTab.ADVANCED;
    }

    /**
     * Returns the Array of time periods. Options are displayed in the same order as they appear
     * in the array.
     */
    protected TimePeriodSpinnerOption[] getTimePeriodSpinnerOptions() {
        Activity activity = getActivity();

        TimePeriodSpinnerOption[] options = new TimePeriodSpinnerOption[] {
                new TimePeriodSpinnerOption(TimePeriod.LAST_HOUR,
                        activity.getString(R.string.clear_browsing_data_period_hour)),
                new TimePeriodSpinnerOption(TimePeriod.LAST_DAY,
                        activity.getString(R.string.clear_browsing_data_period_day)),
                new TimePeriodSpinnerOption(TimePeriod.LAST_WEEK,
                        activity.getString(R.string.clear_browsing_data_period_week)),
                new TimePeriodSpinnerOption(TimePeriod.FOUR_WEEKS,
                        activity.getString(R.string.clear_browsing_data_period_four_weeks)),
                new TimePeriodSpinnerOption(TimePeriod.ALL_TIME,
                        activity.getString(R.string.clear_browsing_data_period_everything))};

        return options;
    }

    /**
     * Decides whether a given dialog option should be selected when the dialog is initialized.
     *
     * @param option The option in question.
     * @return boolean Whether the given option should be preselected.
     */
    private boolean isOptionSelectedByDefault(DialogOption option) {
        return PrefServiceBridge.getInstance().getBrowsingDataDeletionPreference(
                option.getDataType(), getPreferenceType());
    }

    /**
     * Called when clearing browsing data completes.
     * Implements the ChromePreferences.OnClearBrowsingDataListener interface.
     */
    @Override
    public void onBrowsingDataCleared() {
        if (getActivity() == null) return;

        // If the user deleted their browsing history, the dialog about other forms of history
        // is enabled, and it has never been shown before, show it. Note that opening a new
        // DialogFragment is only possible if the Activity is visible.
        //
        // If conditions to show the dialog about other forms of history are not met, just close
        // this preference screen.
        if (MultiWindowUtils.isActivityVisible(getActivity())
                && getSelectedOptions().contains(DialogOption.CLEAR_HISTORY)
                && mIsDialogAboutOtherFormsOfBrowsingHistoryEnabled
                && !OtherFormsOfHistoryDialogFragment.wasDialogShown(getActivity())) {
            mDialogAboutOtherFormsOfBrowsingHistory = new OtherFormsOfHistoryDialogFragment();
            mDialogAboutOtherFormsOfBrowsingHistory.show(getActivity());
            dismissProgressDialog();
            RecordHistogram.recordBooleanHistogram(DIALOG_HISTOGRAM, true);
        } else {
            dismissProgressDialog();
            getActivity().finish();
            RecordHistogram.recordBooleanHistogram(DIALOG_HISTOGRAM, false);
        }
    }

    /**
     * Returns if we should show the important sites dialog. We check to see if
     * <ol>
     * <li>We've fetched the important sites,
     * <li>there are important sites,
     * <li>the feature is enabled, and
     * <li>we have cache or cookies selected.
     * </ol>
     */
    private boolean shouldShowImportantSitesDialog() {
        if (!ChromeFeatureList.isEnabled(ChromeFeatureList.IMPORTANT_SITES_IN_CBD)) return false;
        EnumSet<DialogOption> selectedOptions = getSelectedOptions();
        if (!selectedOptions.contains(DialogOption.CLEAR_CACHE)
                && !selectedOptions.contains(DialogOption.CLEAR_COOKIES_AND_SITE_DATA)) {
            return false;
        }
        boolean haveImportantSites =
                mSortedImportantDomains != null && mSortedImportantDomains.length != 0;
        RecordHistogram.recordBooleanHistogram(
                "History.ClearBrowsingData.ImportantDialogShown", haveImportantSites);
        return haveImportantSites;
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (preference.getKey().equals(PREF_CLEAR_BUTTON)) {
            onClearButtonClicked();
            return true;
        }
        return false;
    }

    /**
     * Either shows the important sites dialog or clears browsing data according to the selected
     * options.
     */
    protected final void onClearButtonClicked() {
        if (shouldShowImportantSitesDialog()) {
            showImportantDialogThenClear();
            return;
        }
        // If sites haven't been fetched, just clear the browsing data regularly rather than
        // waiting to show the important sites dialog.
        clearBrowsingData(getSelectedOptions(), null, null, null, null);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object value) {
        if (preference.getKey().equals(PREF_TIME_RANGE)) {
            // Inform the items that a recalculation is going to happen as a result of the time
            // period change.
            for (Item item : mItems) {
                item.setShouldAnnounceCounterResult(false);
            }

            PrefServiceBridge.getInstance().setBrowsingDataDeletionTimePeriod(
                    getPreferenceType(), ((TimePeriodSpinnerOption) value).getTimePeriod());
            return true;
        }
        return false;
    }

    /**
     * Disable the "Clear" button if none of the options are selected. Otherwise, enable it.
     */
    protected void updateButtonState() {
        ButtonPreference clearButton = (ButtonPreference) findPreference(PREF_CLEAR_BUTTON);
        if (clearButton == null) return;
        boolean isEnabled = !getSelectedOptions().isEmpty();
        clearButton.setEnabled(isEnabled);
    }

    /**
     * @return The id of the preference xml that should be displayed.
     */
    protected int getPreferenceXmlId() {
        return R.xml.clear_browsing_data_preferences;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        RecordUserAction.record("ClearBrowsingData_DialogCreated");
        mMaxImportantSites = BrowsingDataBridge.getMaxImportantSites();
        BrowsingDataBridge.getInstance().requestInfoAboutOtherFormsOfBrowsingHistory(this);
        getActivity().setTitle(R.string.clear_browsing_data_title);
        PreferenceUtils.addPreferencesFromResource(this, getPreferenceXmlId());
        DialogOption[] options = getDialogOptions();
        mItems = new Item[options.length];
        for (int i = 0; i < options.length; i++) {
            boolean enabled = true;

            // It is possible to disable the deletion of browsing history.
            if (options[i] == DialogOption.CLEAR_HISTORY
                    && !PrefServiceBridge.getInstance().canDeleteBrowsingHistory()) {
                enabled = false;
                PrefServiceBridge.getInstance().setBrowsingDataDeletionPreference(
                        DialogOption.CLEAR_HISTORY.getDataType(), ClearBrowsingDataTab.BASIC,
                        false);
                PrefServiceBridge.getInstance().setBrowsingDataDeletionPreference(
                        DialogOption.CLEAR_HISTORY.getDataType(), ClearBrowsingDataTab.ADVANCED,
                        false);
            }

            mItems[i] = new Item(
                this,
                options[i],
                (ClearBrowsingDataCheckBoxPreference) findPreference(options[i].getPreferenceKey()),
                isOptionSelectedByDefault(options[i]),
                enabled);
        }

        // Not all checkboxes defined in the layout are necessarily handled by this class
        // or a particular subclass. Hide those that are not.
        EnumSet<DialogOption> unboundOptions = EnumSet.allOf(DialogOption.class);
        unboundOptions.removeAll(Arrays.asList(getDialogOptions()));
        for (DialogOption option : unboundOptions) {
            getPreferenceScreen().removePreference(findPreference(option.getPreferenceKey()));
        }

        // The time range selection spinner.
        SpinnerPreference spinner = (SpinnerPreference) findPreference(PREF_TIME_RANGE);
        spinner.setOnPreferenceChangeListener(this);
        TimePeriodSpinnerOption[] spinnerOptions = getTimePeriodSpinnerOptions();
        int selectedTimePeriod = PrefServiceBridge.getInstance().getBrowsingDataDeletionTimePeriod(
                getPreferenceType());
        int spinnerOptionIndex = -1;
        for (int i = 0; i < spinnerOptions.length; ++i) {
            if (spinnerOptions[i].getTimePeriod() == selectedTimePeriod) {
                spinnerOptionIndex = i;
                break;
            }
        }
        assert spinnerOptionIndex != -1;
        spinner.setOptions(spinnerOptions, spinnerOptionIndex);

        initClearButtonPreference();
        initFootnote();

        if (ChromeFeatureList.isEnabled(ChromeFeatureList.IMPORTANT_SITES_IN_CBD)) {
            BrowsingDataBridge.fetchImportantSites(this);
        }
    }

    /**
     * Initialize the ButtonPreference.
     */
    protected void initClearButtonPreference() {
        ButtonPreference clearButton = (ButtonPreference) findPreference(PREF_CLEAR_BUTTON);
        clearButton.setOnPreferenceClickListener(this);
        clearButton.setShouldDisableView(true);
    }

    /**
     * Set the texts that notify the user about data in their google account and that deleting
     * cookies doesn't sign you out of chrome.
     */
    protected void initFootnote() {
        // The general information footnote informs users about data that will not be deleted.
        // If the user is signed in, it also informs users about the behavior of synced deletions.
        // and we show an additional Google-specific footnote. This footnote informs users that they
        // will not be signed out of their Google account, and if the web history service indicates
        // that they have other forms of browsing history, then also about that.
        TextMessageWithLinkAndIconPreference google_summary =
                (TextMessageWithLinkAndIconPreference) findPreference(PREF_GOOGLE_SUMMARY);
        TextMessageWithLinkAndIconPreference general_summary =
                (TextMessageWithLinkAndIconPreference) findPreference(PREF_GENERAL_SUMMARY);

        google_summary.setLinkClickDelegate(new Runnable() {
            @Override
            public void run() {
                new TabDelegate(false /* incognito */).launchUrl(
                        WEB_HISTORY_URL, TabLaunchType.FROM_CHROME_UI);
            }
        });
        general_summary.setLinkClickDelegate(new Runnable() {
            @Override
            public void run() {
                HelpAndFeedback.getInstance(getActivity()).show(
                        getActivity(),
                        getResources().getString(R.string.help_context_clear_browsing_data),
                        Profile.getLastUsedProfile(),
                        null);
            }
        });
        if (ChromeSigninController.get().isSignedIn()) {
            general_summary.setSummary(
                    R.string.clear_browsing_data_footnote_sync_and_site_settings);
        } else {
            getPreferenceScreen().removePreference(google_summary);
            general_summary.setSummary(R.string.clear_browsing_data_footnote_site_settings);
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        // Now that the dialog's view has been created, update the button state.
        updateButtonState();

        // Remove the dividers between checkboxes, and make sure the individual widgets can be
        // focused.
        ListView view = (ListView) getView().findViewById(android.R.id.list);
        view.setDivider(null);
        view.setItemsCanFocus(true);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        dismissProgressDialog();
        for (Item item : mItems) {
            item.destroy();
        }
    }

    // We either show the dialog, or modify the current one to display our messages.  This avoids
    // a dialog flash.
    private final void showProgressDialog() {
        if (getActivity() == null) return;
        mProgressDialog = ProgressDialog.show(getActivity(),
                getActivity().getString(R.string.clear_browsing_data_progress_title),
                getActivity().getString(R.string.clear_browsing_data_progress_message), true,
                false);
    }

    @VisibleForTesting
    ProgressDialog getProgressDialog() {
        return mProgressDialog;
    }

    @VisibleForTesting
    ConfirmImportantSitesDialogFragment getImportantSitesDialogFragment() {
        return mConfirmImportantSitesDialog;
    }

    /**
     * This method shows the important sites dialog. After the dialog is shown, we correctly clear.
     */
    private void showImportantDialogThenClear() {
        mConfirmImportantSitesDialog = ConfirmImportantSitesDialogFragment.newInstance(
                mSortedImportantDomains, mSortedImportantDomainReasons, mSortedExampleOrigins);
        mConfirmImportantSitesDialog.setTargetFragment(this, IMPORTANT_SITES_DIALOG_CODE);
        mConfirmImportantSitesDialog.show(
                getFragmentManager(), ConfirmImportantSitesDialogFragment.FRAGMENT_TAG);
    }

    @Override
    public void showNoticeAboutOtherFormsOfBrowsingHistory() {
        if (getActivity() == null) return;

        TextMessageWithLinkAndIconPreference google_summary =
                (TextMessageWithLinkAndIconPreference) findPreference(PREF_GOOGLE_SUMMARY);
        if (google_summary == null) return;
        google_summary.setSummary(
                R.string.clear_browsing_data_footnote_signed_and_other_forms_of_history);
    }

    @Override
    public void enableDialogAboutOtherFormsOfBrowsingHistory() {
        if (getActivity() == null) return;

        mIsDialogAboutOtherFormsOfBrowsingHistoryEnabled = true;
    }

    /**
     * Used only to access the dialog about other forms of browsing history from tests.
     */
    @VisibleForTesting
    OtherFormsOfHistoryDialogFragment getDialogAboutOtherFormsOfBrowsingHistory() {
        return mDialogAboutOtherFormsOfBrowsingHistory;
    }

    @Override
    public void onImportantRegisterableDomainsReady(String[] domains, String[] exampleOrigins,
            int[] importantReasons, boolean dialogDisabled) {
        if (domains == null || dialogDisabled) return;
        // mMaxImportantSites is a constant on the C++ side. While 0 is valid, use 1 as the minimum
        // because histogram code assumes a min >= 1; the underflow bucket will record the 0s.
        RecordHistogram.recordLinearCountHistogram("History.ClearBrowsingData.NumImportant",
                domains.length, 1, mMaxImportantSites + 1, mMaxImportantSites + 1);
        mSortedImportantDomains = Arrays.copyOf(domains, domains.length);
        mSortedImportantDomainReasons = Arrays.copyOf(importantReasons, importantReasons.length);
        mSortedExampleOrigins = Arrays.copyOf(exampleOrigins, exampleOrigins.length);
    }

    /**
     * This is the callback for the important domain dialog. We should only clear if we get the
     * positive button response.
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == IMPORTANT_SITES_DIALOG_CODE && resultCode == Activity.RESULT_OK) {
            // Deselected means that the user is excluding the domain from being cleared.
            String[] deselectedDomains = data.getStringArrayExtra(
                    ConfirmImportantSitesDialogFragment.DESELECTED_DOMAINS_TAG);
            int[] deselectedDomainReasons = data.getIntArrayExtra(
                    ConfirmImportantSitesDialogFragment.DESELECTED_DOMAIN_REASONS_TAG);
            String[] ignoredDomains = data.getStringArrayExtra(
                    ConfirmImportantSitesDialogFragment.IGNORED_DOMAINS_TAG);
            int[] ignoredDomainReasons = data.getIntArrayExtra(
                    ConfirmImportantSitesDialogFragment.IGNORED_DOMAIN_REASONS_TAG);
            if (deselectedDomains != null && mSortedImportantDomains != null) {
                // mMaxImportantSites is a constant on the C++ side.
                RecordHistogram.recordCustomCountHistogram(
                        "History.ClearBrowsingData.ImportantDeselectedNum",
                        deselectedDomains.length, 1, mMaxImportantSites + 1,
                        mMaxImportantSites + 1);
                RecordHistogram.recordCustomCountHistogram(
                        "History.ClearBrowsingData.ImportantIgnoredNum", ignoredDomains.length, 1,
                        mMaxImportantSites + 1, mMaxImportantSites + 1);
                // We put our max at 20 instead of 100 to reduce the number of empty buckets (as
                // our maximum denominator is 5).
                RecordHistogram.recordEnumeratedHistogram(
                        "History.ClearBrowsingData.ImportantDeselectedPercent",
                        deselectedDomains.length * IMPORTANT_SITES_PERCENTAGE_BUCKET_COUNT
                                / mSortedImportantDomains.length,
                        IMPORTANT_SITES_PERCENTAGE_BUCKET_COUNT + 1);
                RecordHistogram.recordEnumeratedHistogram(
                        "History.ClearBrowsingData.ImportantIgnoredPercent",
                        ignoredDomains.length * IMPORTANT_SITES_PERCENTAGE_BUCKET_COUNT
                                / mSortedImportantDomains.length,
                        IMPORTANT_SITES_PERCENTAGE_BUCKET_COUNT + 1);
            }
            clearBrowsingData(getSelectedOptions(), deselectedDomains, deselectedDomainReasons,
                    ignoredDomains, ignoredDomainReasons);
        }
    }
}

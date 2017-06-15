// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.payments;

import android.app.ProgressDialog;
import android.os.Handler;
import android.util.Pair;

import org.chromium.base.Callback;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.autofill.PersonalDataManager;
import org.chromium.chrome.browser.autofill.PersonalDataManager.AutofillProfile;
import org.chromium.chrome.browser.autofill.PersonalDataManager.GetSubKeysRequestDelegate;
import org.chromium.chrome.browser.autofill.PhoneNumberUtil;
import org.chromium.chrome.browser.payments.ui.EditorFieldModel;
import org.chromium.chrome.browser.payments.ui.EditorFieldModel.EditorFieldValidator;
import org.chromium.chrome.browser.payments.ui.EditorModel;
import org.chromium.chrome.browser.preferences.autofill.AutofillProfileBridge;
import org.chromium.chrome.browser.preferences.autofill.AutofillProfileBridge.AddressField;
import org.chromium.chrome.browser.preferences.autofill.AutofillProfileBridge.AddressUiComponent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * An address editor. Can be used for either shipping or billing address editing.
 */
public class AddressEditor
        extends EditorBase<AutofillAddress> implements GetSubKeysRequestDelegate {
    private final Handler mHandler = new Handler();
    private final Map<Integer, EditorFieldModel> mAddressFields = new HashMap<>();
    private final Set<CharSequence> mPhoneNumbers = new HashSet<>();
    @Nullable
    private AutofillProfileBridge mAutofillProfileBridge;
    @Nullable
    private EditorFieldModel mCountryField;
    @Nullable
    private EditorFieldModel mPhoneField;
    @Nullable
    private EditorFieldValidator mPhoneValidator;
    @Nullable
    private List<AddressUiComponent> mAddressUiComponents;
    private boolean mAdminAreasLoaded;
    private String mRecentlySelectedCountry;
    private Runnable mCountryChangeCallback;
    private AutofillProfile mProfile;
    private EditorModel mEditor;
    private ProgressDialog mProgressDialog;

    /**
     * Adds the given phone number to the autocomplete set, if it's valid.
     *
     * @param phoneNumber The phone number to possibly add.
     */
    public void addPhoneNumberIfValid(@Nullable CharSequence phoneNumber) {
        if (getPhoneValidator().isValid(phoneNumber)) mPhoneNumbers.add(phoneNumber);
    }

    /**
     * Builds and shows an editor model with the following fields.
     *
     * [ country dropdown   ] <----- country dropdown is always present.
     * [ an address field   ] \
     * [ an address field   ]  \
     *         ...               <-- field order, presence, required, and labels depend on country.
     * [ an address field   ]  /
     * [ an address field   ] /
     * [ phone number field ] <----- phone is always present and required.
     */
    @Override
    public void edit(
            @Nullable final AutofillAddress toEdit, final Callback<AutofillAddress> callback) {
        super.edit(toEdit, callback);

        if (mAutofillProfileBridge == null) mAutofillProfileBridge = new AutofillProfileBridge();

        // If |toEdit| is null, we're creating a new autofill profile with the country code of the
        // default locale on this device.
        boolean isNewAddress = toEdit == null;

        // Ensure that |address| and |mProfile| are always not null.
        final AutofillAddress address =
                isNewAddress ? new AutofillAddress(mContext, new AutofillProfile()) : toEdit;
        mProfile = address.getProfile();

        // The title of the editor depends on whether we're adding a new address or editing an
        // existing address.
        mEditor =
                new EditorModel(isNewAddress ? mContext.getString(R.string.autofill_create_profile)
                                             : toEdit.getEditTitle());

        // When edit is called, a new form is started, so the country on the
        // dropdown list is not changed. => mRecentlySelectedCountry should be null.
        mRecentlySelectedCountry = null;

        // The country dropdown is always present on the editor.
        if (mCountryField == null) {
            mCountryField = EditorFieldModel.createDropdown(
                    mContext.getString(R.string.autofill_profile_editor_country),
                    AutofillProfileBridge.getSupportedCountries(),
                    null /* hint */);
        }

        // Changing the country will update which fields are in the model. The actual fields are not
        // discarded, so their contents are preserved.
        mCountryField.setDropdownCallback(new Callback<Pair<String, Runnable>>() {
            /**
             * If the selected country on the country dropdown list is changed,
             * the first element of eventData is the recently selected dropdown key,
             * the second element is the callback to invoke for when the dropdown
             * change has been processed.
             */
            @Override
            public void onResult(Pair<String, Runnable> eventData) {
                mEditor.removeAllFields();
                showProgressDialog();
                mRecentlySelectedCountry = eventData.first;
                mCountryChangeCallback = eventData.second;
                loadAdminAreasForCountry(mRecentlySelectedCountry);
            }
        });

        // Country dropdown is cached, so the selected item needs to be updated for the new profile
        // that's being edited. This will not fire the dropdown callback.
        mCountryField.setValue(AutofillAddress.getCountryCode(mProfile));

        // There's a finite number of fields for address editing. Changing the country will re-order
        // and relabel the fields. The meaning of each field remains the same.
        if (mAddressFields.isEmpty()) {
            // City, dependent locality, and organization don't have any special formatting hints.
            mAddressFields.put(AddressField.LOCALITY, EditorFieldModel.createTextInput());
            mAddressFields.put(AddressField.DEPENDENT_LOCALITY, EditorFieldModel.createTextInput());
            mAddressFields.put(AddressField.ORGANIZATION, EditorFieldModel.createTextInput());

            // Sorting code and postal code (a.k.a. ZIP code) should show both letters and digits on
            // the keyboard, if possible.
            mAddressFields.put(AddressField.SORTING_CODE, EditorFieldModel.createTextInput(
                    EditorFieldModel.INPUT_TYPE_HINT_ALPHA_NUMERIC));
            mAddressFields.put(AddressField.POSTAL_CODE, EditorFieldModel.createTextInput(
                    EditorFieldModel.INPUT_TYPE_HINT_ALPHA_NUMERIC));

            // Street line field can contain \n to indicate line breaks.
            mAddressFields.put(AddressField.STREET_ADDRESS, EditorFieldModel.createTextInput(
                    EditorFieldModel.INPUT_TYPE_HINT_STREET_LINES));

            // Android has special formatting rules for names.
            mAddressFields.put(AddressField.RECIPIENT, EditorFieldModel.createTextInput(
                    EditorFieldModel.INPUT_TYPE_HINT_PERSON_NAME));
        }


        // Phone number is present and required for all countries.
        if (mPhoneField == null) {
            mPhoneField = EditorFieldModel.createTextInput(EditorFieldModel.INPUT_TYPE_HINT_PHONE,
                    mContext.getString(R.string.autofill_profile_editor_phone_number),
                    mPhoneNumbers, getPhoneValidator(), null,
                    mContext.getString(R.string.payments_field_required_validation_message),
                    mContext.getString(R.string.payments_phone_invalid_validation_message), null);
        }

        // Phone number field is cached, so its value needs to be updated for every new profile
        // that's being edited.
        mPhoneField.setValue(mProfile.getPhoneNumber());

        // If the user clicks [Cancel], send |toEdit| address back to the caller, which was the
        // original state (could be null, a complete address, a partial address).
        mEditor.setCancelCallback(new Runnable() {
            @Override
            public void run() {
                // This makes sure that onSubKeysReceived returns early if it's
                // ever called when Cancel has already occurred.
                mAdminAreasLoaded = true;
                PersonalDataManager.getInstance().cancelPendingGetSubKeys();
                callback.onResult(toEdit);
            }
        });

        // If the user clicks [Done], save changes on disk, mark the address "complete," and send it
        // back to the caller.
        mEditor.setDoneCallback(new Runnable() {
            @Override
            public void run() {
                mAdminAreasLoaded = true;
                PersonalDataManager.getInstance().cancelPendingGetSubKeys();
                commitChanges(mProfile);
                address.completeAddress(mProfile);
                callback.onResult(address);
            }
        });

        loadAdminAreasForCountry(mProfile.getCountryCode());
    }

    private void showProgressDialog() {
        mProgressDialog = new ProgressDialog(mContext);
        mProgressDialog.setMessage(mContext.getText(R.string.payments_loading_message));
        mProgressDialog.show();
    }

    private void dismissProgressDialog() {
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            mProgressDialog.dismiss();
        }
        mProgressDialog = null;
    }

    /** Saves the edited profile on disk. */
    private void commitChanges(AutofillProfile profile) {
        // Country code and phone number are always required and are always collected from the
        // editor model.
        profile.setCountryCode(mCountryField.getValue().toString());
        profile.setPhoneNumber(mPhoneField.getValue().toString());

        // Autofill profile bridge normalizes the language code for the autofill profile.
        profile.setLanguageCode(mAutofillProfileBridge.getCurrentBestLanguageCode());

        // Collect data from all visible fields and store it in the autofill profile.
        Set<Integer> visibleFields = new HashSet<>();
        for (int i = 0; i < mAddressUiComponents.size(); i++) {
            AddressUiComponent component = mAddressUiComponents.get(i);
            visibleFields.add(component.id);
            if (component.id != AddressField.COUNTRY) {
                setProfileField(profile, component.id, mAddressFields.get(component.id).getValue());
            }
        }

        // Clear the fields that are hidden from the user interface, so
        // AutofillAddress.toPaymentAddress() will send them to the renderer as empty strings.
        for (Map.Entry<Integer, EditorFieldModel> entry : mAddressFields.entrySet()) {
            if (!visibleFields.contains(entry.getKey())) {
                setProfileField(profile, entry.getKey(), "");
            }
        }

        // Save the edited autofill profile locally.
        profile.setGUID(PersonalDataManager.getInstance().setProfileToLocal(mProfile));
        profile.setIsLocal(true);
    }

    /** Writes the given value into the specified autofill profile field. */
    private static void setProfileField(
            AutofillProfile profile, int field, @Nullable CharSequence value) {
        assert profile != null;
        switch (field) {
            case AddressField.COUNTRY:
                profile.setCountryCode(ensureNotNull(value));
                return;
            case AddressField.ADMIN_AREA:
                profile.setRegion(ensureNotNull(value));
                return;
            case AddressField.LOCALITY:
                profile.setLocality(ensureNotNull(value));
                return;
            case AddressField.DEPENDENT_LOCALITY:
                profile.setDependentLocality(ensureNotNull(value));
                return;
            case AddressField.SORTING_CODE:
                profile.setSortingCode(ensureNotNull(value));
                return;
            case AddressField.POSTAL_CODE:
                profile.setPostalCode(ensureNotNull(value));
                return;
            case AddressField.STREET_ADDRESS:
                profile.setStreetAddress(ensureNotNull(value));
                return;
            case AddressField.ORGANIZATION:
                profile.setCompanyName(ensureNotNull(value));
                return;
            case AddressField.RECIPIENT:
                profile.setFullName(ensureNotNull(value));
                return;
        }

        assert false;
    }

    private static String ensureNotNull(@Nullable CharSequence value) {
        return value == null ? "" : value.toString();
    }

    private void setAddressFieldValuesFromCache() {
        // Address fields are cached, so their values need to be updated for every new profile
        // that's being edited.
        for (Map.Entry<Integer, EditorFieldModel> entry : mAddressFields.entrySet()) {
            entry.getValue().setValue(AutofillAddress.getProfileField(mProfile, entry.getKey()));
        }
    }

    @Override
    public void onSubKeysReceived(String[] adminAreas) {
        if (mAdminAreasLoaded) return;
        mAdminAreasLoaded = true;

        // If Chrome can't get admin areas from the server or there is no admin area on the server,
        // then use the text field.
        // Otherwise, use the dropdown list.
        if (adminAreas == null || adminAreas.length == 0) {
            mAddressFields.put(AddressField.ADMIN_AREA, EditorFieldModel.createTextInput());
        } else {
            mAddressFields.put(AddressField.ADMIN_AREA, EditorFieldModel.createDropdown());
        }

        // Admin areas need to be fetched in two cases:
        // 1. Initial loading of the form.
        // 2. When the selected country is changed in the form.
        // mRecentlySelectedCountry is not null if and only if it's the second case
        if (mRecentlySelectedCountry != null) {
            dismissProgressDialog();
            // Both country code and language code dictate which fields should be added to the
            // editor.
            // For example, "US" will not add dependent locality to the editor. A "JP" address will
            // start with a person's full name or a with a prefecture name, depending on whether the
            // language code is "ja-Latn" or "ja".
            addAddressFieldsToEditor(
                    mRecentlySelectedCountry, Locale.getDefault().getLanguage(), adminAreas);
            // Notify EditorDialog that the fields in the model have changed. EditorDialog should
            // re-read the model and update the UI accordingly.
            mHandler.post(mCountryChangeCallback);
        } else {
            // This should be called when all required fields are put in mAddressField.
            setAddressFieldValuesFromCache();
            addAddressFieldsToEditor(
                    mProfile.getCountryCode(), mProfile.getLanguageCode(), adminAreas);
            mEditorDialog.show(mEditor);
        }
    }

    /** Requests the list of admin areas. */
    private void loadAdminAreasForCountry(String countryCode) {
        // Used to check if the callback is called (for the cancellation).
        mAdminAreasLoaded = false;

        // For tests, the time-out is set to 0. In this case, we should not
        // fetch the admin-areas, and show a text-field instead.
        // This is to have the tests independent of the network status.
        if (PersonalDataManager.getInstance().getRequestTimeoutMS() == 0) {
            onSubKeysReceived(null);
            return;
        }

        // In each rule, admin area keys are saved under sub-keys of country.
        PersonalDataManager.getInstance().loadRulesForSubKeys(countryCode);
        PersonalDataManager.getInstance().getRegionSubKeys(countryCode, this);
    }

    /**
     * Adds fields to the editor model based on the country and language code of
     * the profile that's being edited.
     */
    private void addAddressFieldsToEditor(
            String countryCode, String languageCode, String[] adminAreas) {
        mAddressUiComponents =
                mAutofillProfileBridge.getAddressUiComponents(countryCode, languageCode);
        // In terms of order, country must be the first field.
        mEditor.addField(mCountryField);
        for (int i = 0; i < mAddressUiComponents.size(); i++) {
            AddressUiComponent component = mAddressUiComponents.get(i);

            EditorFieldModel field = mAddressFields.get(component.id);
            // Labels depend on country, e.g., state is called province in some countries. These are
            // already localized.
            field.setLabel(component.label);
            field.setIsFullLine(component.isFullLine || component.id == AddressField.LOCALITY
                    || component.id == AddressField.DEPENDENT_LOCALITY);

            if (component.id == AddressField.ADMIN_AREA && field.isDropdownField()) {
                field.setDropdownKeyValues(
                        mAutofillProfileBridge.getAdminAreaDropdownList(adminAreas));
            }

            // Libaddressinput formats do not always require the full name (RECIPIENT), but
            // PaymentRequest does.
            if (component.isRequired || component.id == AddressField.RECIPIENT) {
                field.setRequiredErrorMessage(mContext.getString(
                        R.string.payments_field_required_validation_message));
            } else {
                field.setRequiredErrorMessage(null);
            }
            mEditor.addField(field);
        }
        // Phone number must be the last field.
        mEditor.addField(mPhoneField);
    }

    private EditorFieldValidator getPhoneValidator() {
        if (mPhoneValidator == null) {
            mPhoneValidator = new EditorFieldValidator() {
                @Override
                public boolean isValid(@Nullable CharSequence value) {
                    return value != null && PhoneNumberUtil.isValidNumber(value.toString());
                }

                @Override
                public boolean isLengthMaximum(@Nullable CharSequence value) {
                    return false;
                }
            };
        }
        return mPhoneValidator;
    }
}

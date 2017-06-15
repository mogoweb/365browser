// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.privacy;

import org.chromium.chrome.browser.browsing_data.ClearBrowsingDataTab;

/**
 * A more advanced version of {@link ClearBrowsingDataPreferences} with more dialog options and less
 * explanatory text.
 */
public class ClearBrowsingDataPreferencesAdvanced extends ClearBrowsingDataPreferencesTab {
    // TODO(dullweber): Add more options.

    @Override
    protected int getPreferenceType() {
        return ClearBrowsingDataTab.ADVANCED;
    }

    @Override
    protected DialogOption[] getDialogOptions() {
        return new DialogOption[] {DialogOption.CLEAR_HISTORY,
                DialogOption.CLEAR_COOKIES_AND_SITE_DATA, DialogOption.CLEAR_CACHE,
                DialogOption.CLEAR_PASSWORDS, DialogOption.CLEAR_FORM_DATA,
                DialogOption.CLEAR_SITE_SETTINGS};
    }
}

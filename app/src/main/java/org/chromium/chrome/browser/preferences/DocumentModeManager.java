// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.chromium.base.ThreadUtils;

/**
 * Tracks opt out status for document mode
 */
public class DocumentModeManager {

    public static final String OPT_OUT_STATE = "opt_out_state";
    public static final int OPT_OUT_PROMO_DISMISSED = 1;
    public static final int OPTED_OUT_OF_DOCUMENT_MODE = 2;
    public static final String OPT_OUT_SHOWN_COUNT = "opt_out_shown_count";
    public static final String OPT_OUT_CLEAN_UP_PENDING = "opt_out_clean_up_pending";
    public static final String OPT_OUT_PREVIOUS_STATE = "opt_out_previous_state";

    private static DocumentModeManager sManager;

    private final SharedPreferences mSharedPreferences;

    private DocumentModeManager(Context context) {
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    }

    /**
     * Get the static instance of DocumentModeManager if it exists, else create it.
     * @param context The current Android context
     * @return the DocumentModeManager singleton
     */
    public static DocumentModeManager getInstance(Context context) {
        ThreadUtils.assertOnUiThread();
        if (sManager == null) {
            sManager = new DocumentModeManager(context);
        }
        return sManager;
    }

    /**
     * @return Whether the user set a preference to not use the document mode.
     */
    public boolean isOptedOutOfDocumentMode() {
        return mSharedPreferences.getInt(OPT_OUT_STATE, 0) == OPTED_OUT_OF_DOCUMENT_MODE;
    }

    /**
     * @return Whether the user dismissed the opt out promo.
     */
    public boolean isOptOutPromoDismissed() {
        return mSharedPreferences.getInt(OPT_OUT_STATE, 0) == OPT_OUT_PROMO_DISMISSED;
    }

    /**
     * Sets the opt out preference.
     * @param state One of OPTED_OUT_OF_DOCUMENT_MODE or OPT_OUT_PROMO_DISMISSED.
     */
    public void setOptedOutState(int state) {
        final int previousState = mSharedPreferences.getInt(OPT_OUT_STATE, 0);
        SharedPreferences.Editor sharedPreferencesEditor = mSharedPreferences.edit();
        sharedPreferencesEditor.putInt(OPT_OUT_PREVIOUS_STATE, previousState);
        sharedPreferencesEditor.putInt(OPT_OUT_STATE, state);
        sharedPreferencesEditor.apply();
    }

    /**
     * Saves the current state of the opt out preference to later match against it.
     */
    public void savePreviousOptOutState() {
        final int previousState = mSharedPreferences.getInt(OPT_OUT_STATE, 0);
        SharedPreferences.Editor sharedPreferencesEditor = mSharedPreferences.edit();
        sharedPreferencesEditor.putInt(OPT_OUT_PREVIOUS_STATE, previousState);
        sharedPreferencesEditor.apply();
    }

    /**
     * @return Whether the opt out state has changed since the last savePreviousOptOutState.
     */
    public boolean didOptOutStateChange() {
        return mSharedPreferences.getInt(OPT_OUT_STATE, 0)
                != mSharedPreferences.getInt(OPT_OUT_PREVIOUS_STATE, 0);
    }

    /**
     * Increments a preference that keeps track of how many times the opt out message has been
     * shown on home screen.
     */
    public void incrementOptOutShownCount() {
        SharedPreferences.Editor sharedPreferencesEditor = mSharedPreferences.edit();
        sharedPreferencesEditor.putLong(OPT_OUT_SHOWN_COUNT, getOptOutShownCount() + 1);
        sharedPreferencesEditor.apply();
    }

    /**
     * @return The number of times the opt out message has been shown so far.
     */
    public long getOptOutShownCount() {
        return mSharedPreferences.getLong(OPT_OUT_SHOWN_COUNT, 0);
    }

    /**
     * @return Whether we need to clean up old document activity tasks from Recents.
     */
    public boolean isOptOutCleanUpPending() {
        return mSharedPreferences.getBoolean(OPT_OUT_CLEAN_UP_PENDING, false);
    }

    /**
     * Mark that we need to clean up old documents from Recents or reset it after the task
     * is done.
     * @param pending Whether we need to clean up.
     */
    public void setOptOutCleanUpPending(boolean pending) {
        SharedPreferences.Editor sharedPreferencesEditor = mSharedPreferences.edit();
        sharedPreferencesEditor.putBoolean(OPT_OUT_CLEAN_UP_PENDING, pending);
        sharedPreferencesEditor.apply();
    }
}

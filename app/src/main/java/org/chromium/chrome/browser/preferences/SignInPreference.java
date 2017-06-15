// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.preference.Preference;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;

import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.firstrun.FirstRunSignInProcessor;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.profiles.ProfileDownloader;
import org.chromium.chrome.browser.signin.AccountManagementFragment;
import org.chromium.chrome.browser.signin.AccountSigninActivity;
import org.chromium.chrome.browser.signin.SigninAccessPoint;
import org.chromium.chrome.browser.signin.SigninManager;
import org.chromium.chrome.browser.signin.SigninManager.SignInAllowedObserver;
import org.chromium.chrome.browser.sync.ProfileSyncService;
import org.chromium.chrome.browser.sync.ProfileSyncService.SyncStateChangedListener;
import org.chromium.chrome.browser.util.ViewUtils;
import org.chromium.components.signin.ChromeSigninController;
import org.chromium.components.sync.AndroidSyncSettings;

/**
 * A preference that displays "Sign in to Chrome" when the user is not sign in, and displays
 * the user's name, email, profile image and sync error icon if necessary when the user is signed
 * in.
 */
public class SignInPreference extends Preference
        implements SignInAllowedObserver, ProfileDownloader.Observer,
                   AndroidSyncSettings.AndroidSyncSettingsObserver, SyncStateChangedListener {
    private boolean mViewEnabled;
    private boolean mShowingPromo;

    /**
     * Constructor for inflating from XML.
     */
    public SignInPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        update();
    }

    /**
     * Starts listening for updates to the sign-in and sync state.
     */
    public void registerForUpdates() {
        SigninManager manager = SigninManager.get(getContext());
        manager.addSignInAllowedObserver(this);
        ProfileDownloader.addObserver(this);
        FirstRunSignInProcessor.updateSigninManagerFirstRunCheckDone(getContext());
        AndroidSyncSettings.registerObserver(getContext(), this);
        ProfileSyncService syncService = ProfileSyncService.get();
        if (syncService != null) {
            syncService.addSyncStateChangedListener(this);
        }
    }

    /**
     * Stops listening for updates to the sign-in and sync state. Every call to registerForUpdates()
     * must be matched with a call to this method.
     */
    public void unregisterForUpdates() {
        SigninManager manager = SigninManager.get(getContext());
        manager.removeSignInAllowedObserver(this);
        ProfileDownloader.removeObserver(this);
        AndroidSyncSettings.unregisterObserver(getContext(), this);
        ProfileSyncService syncService = ProfileSyncService.get();
        if (syncService != null) {
            syncService.removeSyncStateChangedListener(this);
        }
    }

    /**
     * Updates the title, summary, and image based on the current sign-in state.
     */
    private void update() {
        String accountName = ChromeSigninController.get().getSignedInAccountName();
        if (SigninManager.get(getContext()).isSigninDisabledByPolicy()) {
            setupSigninDisabled();
            mShowingPromo = false;
        } else if (accountName == null) {
            setupNotSignedIn();

            if (!mShowingPromo) {
                // This user action should be recorded when message with sign-in prompt is shown
                RecordUserAction.record("Signin_Impression_FromSettings");
            }
            mShowingPromo = true;
        } else {
            setupSignedIn(accountName);
            mShowingPromo = false;
        }

        setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                return AccountSigninActivity.startIfAllowed(
                        getContext(), SigninAccessPoint.SETTINGS);
            }
        });
    }

    private void setupSigninDisabled() {
        setTitle(R.string.sign_in_to_chrome);
        setSummary(R.string.sign_in_to_chrome_disabled_summary);
        setFragment(null);
        setIcon(ManagedPreferencesUtils.getManagedByEnterpriseIconId());
        setWidgetLayoutResource(0);
        setViewEnabled(false);
    }

    private void setupNotSignedIn() {
        setTitle(R.string.sign_in_to_chrome);
        setSummary(R.string.sign_in_to_chrome_summary);
        setFragment(null);
        setIcon(R.drawable.account_management_no_picture);
        setWidgetLayoutResource(0);
        setViewEnabled(true);
    }

    private void setupSignedIn(String accountName) {
        String title = AccountManagementFragment.getCachedUserName(accountName);
        if (title == null) {
            Profile profile = Profile.getLastUsedProfile();
            String cachedName = ProfileDownloader.getCachedFullName(profile);
            Bitmap cachedBitmap = ProfileDownloader.getCachedAvatar(profile);
            if (TextUtils.isEmpty(cachedName) || cachedBitmap == null) {
                AccountManagementFragment.startFetchingAccountInformation(
                        getContext(), profile, accountName);
            }
            title = TextUtils.isEmpty(cachedName) ? accountName : cachedName;
        }
        setTitle(title);
        setSummary(SyncPreference.getSyncStatusSummary(getContext()));
        setFragment(AccountManagementFragment.class.getName());

        Resources resources = getContext().getResources();
        Bitmap bitmap = AccountManagementFragment.getUserPicture(accountName, resources);
        setIcon(new BitmapDrawable(resources, bitmap));

        setWidgetLayoutResource(
                SyncPreference.showSyncErrorIcon(getContext()) ? R.layout.sync_error_widget : 0);

        setViewEnabled(true);
    }

    // This just changes visual representation. Actual enabled flag in preference stays
    // always true to receive clicks (necessary to show "Managed by administator" toast).
    private void setViewEnabled(boolean enabled) {
        if (mViewEnabled == enabled) {
            return;
        }
        mViewEnabled = enabled;
        notifyChanged();
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        ViewUtils.setEnabledRecursive(view, mViewEnabled);
    }

    // ProfileSyncServiceListener implementation:

    @Override
    public void syncStateChanged() {
        update();
    }

    // SignInAllowedObserver

    @Override
    public void onSignInAllowedChanged() {
        update();
    }

    // ProfileDownloader.Observer

    @Override
    public void onProfileDownloaded(String accountId, String fullName, String givenName,
            Bitmap bitmap) {
        AccountManagementFragment.updateUserNamePictureCache(accountId, fullName, bitmap);
        update();
    }

    // AndroidSyncSettings.AndroidSyncSettingsObserver
    @Override
    public void androidSyncSettingsChanged() {
        update();
    }
}

// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.firstrun;

import android.accounts.Account;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.ApplicationStatus;
import org.chromium.base.CommandLine;
import org.chromium.base.FieldTrialList;
import org.chromium.base.Log;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.ChromeSwitches;
import org.chromium.chrome.browser.IntentHandler;
import org.chromium.chrome.browser.IntentHandler.ExternalAppId;
import org.chromium.chrome.browser.locale.LocaleManager;
import org.chromium.chrome.browser.net.spdyproxy.DataReductionProxySettings;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.preferences.privacy.PrivacyPreferencesManager;
import org.chromium.chrome.browser.services.AndroidEduAndChildAccountHelper;
import org.chromium.chrome.browser.signin.SigninManager;
import org.chromium.chrome.browser.util.FeatureUtilities;
import org.chromium.chrome.browser.util.IntentUtils;
import org.chromium.components.signin.AccountManagerHelper;
import org.chromium.components.signin.ChromeSigninController;

import java.lang.ref.WeakReference;
import java.util.List;

/**
 * A helper to determine what should be the sequence of First Run Experience screens, and whether
 * it should be run.
 *
 * Usage:
 * new FirstRunFlowSequencer(activity, launcherProvidedProperties) {
 *     override onFlowIsKnown
 * }.start();
 */
public abstract class FirstRunFlowSequencer  {
    /**
     * Sending an intent with this extra will skip the First Run Experience.
     */
    public static final String SKIP_FIRST_RUN_EXPERIENCE = "skip_first_run_experience";

    private static final int FIRST_RUN_EXPERIENCE_REQUEST_CODE = 101;
    private static final String TAG = "firstrun";

    private final Activity mActivity;
    private final Bundle mLaunchProperties;

    // The following are initialized via initializeSharedState().
    private boolean mIsAndroidEduDevice;
    private boolean mHasChildAccount;
    private Account[] mGoogleAccounts;
    private boolean mOnlyOneAccount;
    private boolean mForceEduSignIn;

    /**
     * Callback that is called once the flow is determined.
     * If the properties is null, the First Run experience needs to finish and
     * restart the original intent if necessary.
     * @param freProperties Properties to be used in the First Run activity, or null.
     */
    public abstract void onFlowIsKnown(Bundle freProperties);

    public FirstRunFlowSequencer(Activity activity, Bundle launcherProvidedProperties) {
        mActivity = activity;
        mLaunchProperties = launcherProvidedProperties;
    }

    /**
     * Starts determining parameters for the First Run.
     * Once finished, calls onFlowIsKnown().
     */
    public void start() {
        if (CommandLine.getInstance().hasSwitch(ChromeSwitches.DISABLE_FIRST_RUN_EXPERIENCE)
                || ApiCompatibilityUtils.isDemoUser(mActivity)) {
            onFlowIsKnown(null);
            return;
        }

        new AndroidEduAndChildAccountHelper() {
            @Override
            public void onParametersReady() {
                initializeSharedState(isAndroidEduDevice(), hasChildAccount());
                processFreEnvironmentPreNative();
            }
        }.start(mActivity.getApplicationContext());
    }

    @VisibleForTesting
    protected boolean isFirstRunFlowComplete() {
        return FirstRunStatus.getFirstRunFlowComplete();
    }

    @VisibleForTesting
    protected boolean isSignedIn() {
        return ChromeSigninController.get().isSignedIn();
    }

    @VisibleForTesting
    protected boolean isSyncAllowed() {
        SigninManager signinManager = SigninManager.get(mActivity.getApplicationContext());
        return FeatureUtilities.canAllowSync(mActivity) && !signinManager.isSigninDisabledByPolicy()
                && signinManager.isSigninSupported();
    }

    @VisibleForTesting
    protected Account[] getGoogleAccounts() {
        return AccountManagerHelper.get().getGoogleAccounts();
    }

    @VisibleForTesting
    protected boolean hasAnyUserSeenToS() {
        return ToSAckedReceiver.checkAnyUserHasSeenToS(mActivity);
    }

    @VisibleForTesting
    protected boolean shouldSkipFirstUseHints() {
        return ApiCompatibilityUtils.shouldSkipFirstUseHints(mActivity.getContentResolver());
    }

    @VisibleForTesting
    protected boolean isFirstRunEulaAccepted() {
        return PrefServiceBridge.getInstance().isFirstRunEulaAccepted();
    }

    protected boolean shouldShowDataReductionPage() {
        return !DataReductionProxySettings.getInstance().isDataReductionProxyManaged()
                && FieldTrialList.findFullName("DataReductionProxyFREPromo").startsWith("Enabled");
    }

    @VisibleForTesting
    protected boolean shouldShowSearchEnginePage() {
        int searchPromoType = LocaleManager.getInstance().getSearchEnginePromoShowType();
        return searchPromoType == LocaleManager.SEARCH_ENGINE_PROMO_SHOW_NEW
                || searchPromoType == LocaleManager.SEARCH_ENGINE_PROMO_SHOW_EXISTING;
    }

    @VisibleForTesting
    protected void setDefaultMetricsAndCrashReporting() {
        PrivacyPreferencesManager.getInstance().setUsageAndCrashReporting(
                FirstRunActivity.DEFAULT_METRICS_AND_CRASH_REPORTING);
    }

    @VisibleForTesting
    protected void setFirstRunFlowSignInComplete() {
        FirstRunSignInProcessor.setFirstRunFlowSignInComplete(
                mActivity.getApplicationContext(), true);
    }

    void initializeSharedState(boolean isAndroidEduDevice, boolean hasChildAccount) {
        mIsAndroidEduDevice = isAndroidEduDevice;
        mHasChildAccount = hasChildAccount;
        mGoogleAccounts = getGoogleAccounts();
        mOnlyOneAccount = mGoogleAccounts.length == 1;
        // EDU devices should always have exactly 1 google account, which will be automatically
        // signed-in. All FRE screens are skipped in this case.
        mForceEduSignIn = mIsAndroidEduDevice && mOnlyOneAccount && !isSignedIn();
    }

    void processFreEnvironmentPreNative() {
        if (isFirstRunFlowComplete()) {
            assert isFirstRunEulaAccepted();
            // We do not need any interactive FRE.
            onFlowIsKnown(null);
            return;
        }

        if (!mLaunchProperties.getBoolean(FirstRunActivity.EXTRA_USE_FRE_FLOW_SEQUENCER)) {
            // If EXTRA_USE_FRE_FLOW_SEQUENCER is not set, it means we should use the properties as
            // provided instead of setting them up. However, the properties as provided may not yet
            // have post-native properties computed, so the Runnable still needs to be passed.
            onFlowIsKnown(mLaunchProperties);
            return;
        }

        Bundle freProperties = new Bundle();
        freProperties.putAll(mLaunchProperties);
        freProperties.remove(FirstRunActivity.EXTRA_USE_FRE_FLOW_SEQUENCER);

        // In the full FRE we always show the Welcome page, except on EDU devices.
        boolean showWelcomePage = !mForceEduSignIn;
        freProperties.putBoolean(FirstRunActivity.SHOW_WELCOME_PAGE, showWelcomePage);
        freProperties.putBoolean(AccountFirstRunFragment.IS_CHILD_ACCOUNT, mHasChildAccount);

        // Set a boolean to indicate we need to do post native setup via the runnable below.
        freProperties.putBoolean(FirstRunActivity.POST_NATIVE_SETUP_NEEDED, true);

        // Initialize usage and crash reporting according to the default value.
        // The user can explicitly enable or disable the reporting on the Welcome page.
        // This is controlled by the administrator via a policy on EDU devices.
        setDefaultMetricsAndCrashReporting();

        onFlowIsKnown(freProperties);
        if (mHasChildAccount || mForceEduSignIn) {
            // Child and Edu forced signins are processed independently.
            setFirstRunFlowSignInComplete();
        }
    }

    /**
     * Called onNativeInitialized() a given flow as completed.
     * @param activity An activity.
     * @param data Resulting FRE properties bundle.
     */
    public void onNativeInitialized(Bundle freProperties) {
        if (!freProperties.getBoolean(FirstRunActivity.POST_NATIVE_SETUP_NEEDED)) return;

        // We show the sign-in page if sync is allowed, and not signed in, and this is not
        // an EDU device, and
        // - no "skip the first use hints" is set, or
        // - "skip the first use hints" is set, but there is at least one account.
        boolean offerSignInOk = isSyncAllowed() && !isSignedIn() && !mForceEduSignIn
                && (!shouldSkipFirstUseHints() || mGoogleAccounts.length > 0);
        freProperties.putBoolean(FirstRunActivity.SHOW_SIGNIN_PAGE, offerSignInOk);
        if (offerSignInOk || mForceEduSignIn) {
            // If the user has accepted the ToS in the Setup Wizard and there is exactly
            // one account, or if the device has a child account, or if the device is an
            // Android EDU device and there is exactly one account, preselect the sign-in
            // account and force the selection if necessary.
            if ((hasAnyUserSeenToS() && mOnlyOneAccount) || mHasChildAccount || mForceEduSignIn) {
                freProperties.putString(
                        AccountFirstRunFragment.FORCE_SIGNIN_ACCOUNT_TO, mGoogleAccounts[0].name);
                freProperties.putBoolean(AccountFirstRunFragment.PRESELECT_BUT_ALLOW_TO_CHANGE,
                        !mForceEduSignIn && !mHasChildAccount);
            }
        }

        freProperties.putBoolean(
                FirstRunActivity.SHOW_DATA_REDUCTION_PAGE, shouldShowDataReductionPage());
        freProperties.putBoolean(
                FirstRunActivity.SHOW_SEARCH_ENGINE_PAGE, shouldShowSearchEnginePage());
        freProperties.remove(FirstRunActivity.POST_NATIVE_SETUP_NEEDED);
    }

    /**
     * Marks a given flow as completed.
     * @param activity An activity.
     * @param data Resulting FRE properties bundle.
     */
    public static void markFlowAsCompleted(Activity activity, Bundle data) {
        // When the user accepts ToS in the Setup Wizard (see ToSAckedReceiver), we do not
        // show the ToS page to the user because the user has already accepted one outside FRE.
        if (!PrefServiceBridge.getInstance().isFirstRunEulaAccepted()) {
            PrefServiceBridge.getInstance().setEulaAccepted();
        }

        // Mark the FRE flow as complete and set the sign-in flow preferences if necessary.
        FirstRunSignInProcessor.finalizeFirstRunFlowState(activity, data);
    }

    /**
     * Checks if the First Run needs to be launched.
     * @param context The context.
     * @param fromIntent The intent that was used to launch Chrome.
     * @param forLightweightFre Whether this is a check for the Lightweight First Run Experience.
     * @return The intent to launch the First Run Experience if necessary, or null.
     */
    @Nullable
    public static Intent checkIfFirstRunIsNecessary(
            Context context, Intent fromIntent, boolean forLightweightFre) {
        // If FRE is disabled (e.g. in tests), proceed directly to the intent handling.
        if (CommandLine.getInstance().hasSwitch(ChromeSwitches.DISABLE_FIRST_RUN_EXPERIENCE)
                || ApiCompatibilityUtils.isDemoUser(context)) {
            return null;
        }

        if (fromIntent != null && fromIntent.getBooleanExtra(SKIP_FIRST_RUN_EXPERIENCE, false)) {
            return null;
        }

        // If Chrome isn't opened via the Chrome icon, and the user accepted the ToS
        // in the Setup Wizard, skip any First Run Experience screens and proceed directly
        // to the intent handling.
        final boolean fromChromeIcon =
                fromIntent != null && TextUtils.equals(fromIntent.getAction(), Intent.ACTION_MAIN);
        if (!fromChromeIcon && ToSAckedReceiver.checkAnyUserHasSeenToS(context)) return null;

        final boolean baseFreComplete = FirstRunStatus.getFirstRunFlowComplete();
        if (!baseFreComplete) {
            if (forLightweightFre
                    && CommandLine.getInstance().hasSwitch(
                               ChromeSwitches.ENABLE_LIGHTWEIGHT_FIRST_RUN_EXPERIENCE)) {
                if (!FirstRunStatus.shouldSkipWelcomePage()
                        && !FirstRunStatus.getLightweightFirstRunFlowComplete()) {
                    return createLightweightFirstRunIntent(context, fromChromeIcon);
                }
            } else {
                return createGenericFirstRunIntent(context, fromChromeIcon);
            }
        }

        // Promo pages are removed, so there is nothing else to show in FRE.
        return null;
    }

    private static Intent createLightweightFirstRunIntent(Context context, boolean fromChromeIcon) {
        Intent intent = new Intent();
        intent.setClassName(context, LightweightFirstRunActivity.class.getName());
        intent.putExtra(FirstRunActivity.EXTRA_COMING_FROM_CHROME_ICON, fromChromeIcon);
        intent.putExtra(FirstRunActivity.EXTRA_START_LIGHTWEIGHT_FRE, true);
        return intent;
    }

    /**
     * @return A generic intent to show the First Run Activity.
     * @param context        The context.
     * @param fromChromeIcon Whether Chrome is opened via the Chrome icon.
    */
    public static Intent createGenericFirstRunIntent(Context context, boolean fromChromeIcon) {
        Intent intent = new Intent();
        intent.setClassName(context, FirstRunActivity.class.getName());
        intent.putExtra(FirstRunActivity.EXTRA_COMING_FROM_CHROME_ICON, fromChromeIcon);
        intent.putExtra(FirstRunActivity.EXTRA_USE_FRE_FLOW_SEQUENCER, true);
        return intent;
    }

    /**
     * Adds fromIntent as a PendingIntent to the firstRunIntent. This should be used to add a
     * PendingIntent that will be sent when first run is either completed or canceled.
     *
     * @param caller            The context that corresponds to the Intent.
     * @param firstRunIntent    The intent that will be used to start first run.
     * @param fromIntent        The intent that was used to launch Chrome.
     * @param requiresBroadcast Whether or not the fromIntent must be broadcasted.
     */
    private static void addPendingIntent(
            Context caller, Intent firstRunIntent, Intent fromIntent, boolean requiresBroadcast) {
        PendingIntent pendingIntent = null;
        int pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_ONE_SHOT;
        if (requiresBroadcast) {
            pendingIntent = PendingIntent.getBroadcast(
                    caller, FIRST_RUN_EXPERIENCE_REQUEST_CODE, fromIntent, pendingIntentFlags);
        } else {
            pendingIntent = PendingIntent.getActivity(
                    caller, FIRST_RUN_EXPERIENCE_REQUEST_CODE, fromIntent, pendingIntentFlags);
        }
        firstRunIntent.putExtra(FirstRunActivity.EXTRA_CHROME_LAUNCH_INTENT, pendingIntent);
    }

    /**
     * Tries to launch the First Run Experience.  If the Activity was launched with the wrong Intent
     * flags, we first relaunch it to make sure it runs in its own task, then trigger First Run.
     *
     * @param caller            Activity instance that is checking if first run is necessary.
     * @param intent            Intent used to launch the caller.
     * @param requiresBroadcast Whether or not the Intent triggers a BroadcastReceiver.
     * @return Whether startup must be blocked (e.g. via Activity#finish or dropping the Intent).
     */
    public static boolean launch(Context caller, Intent intent, boolean requiresBroadcast) {
        // Check if the user just came back from the FRE.
        boolean firstRunActivityResult = IntentUtils.safeGetBooleanExtra(
                intent, FirstRunActivity.EXTRA_FIRST_RUN_ACTIVITY_RESULT, false);
        boolean firstRunComplete = IntentUtils.safeGetBooleanExtra(
                intent, FirstRunActivity.EXTRA_FIRST_RUN_COMPLETE, false);
        if (firstRunActivityResult && !firstRunComplete) {
            Log.d(TAG, "User failed to complete the FRE.  Aborting");
            return true;
        }

        // Tries to launch the Generic First Run Experience for intent from GSA.
        boolean showLightweightFre =
                IntentHandler.determineExternalIntentSource(caller.getPackageName(), intent)
                != ExternalAppId.GSA;

        // Check if the user needs to go through First Run at all.
        Intent freIntent = checkIfFirstRunIsNecessary(caller, intent, showLightweightFre);
        if (freIntent == null) return false;

        Log.d(TAG, "Redirecting user through FRE.");
        if ((intent.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0) {
            if (CommandLine.getInstance().hasSwitch(
                        ChromeSwitches.ENABLE_LIGHTWEIGHT_FIRST_RUN_EXPERIENCE)) {
                boolean isGenericFreActive = false;
                List<WeakReference<Activity>> activities = ApplicationStatus.getRunningActivities();
                for (WeakReference<Activity> weakActivity : activities) {
                    Activity activity = weakActivity.get();
                    if (activity == null) {
                        continue;
                    } else if (activity instanceof LightweightFirstRunActivity) {
                        // A Generic or a new Lightweight First Run Experience will be launched
                        // below, so finish the old Lightweight First Run Experience.
                        activity.setResult(Activity.RESULT_CANCELED);
                        activity.finish();
                        continue;
                    } else if (activity instanceof FirstRunActivity) {
                        isGenericFreActive = true;
                        continue;
                    }
                }

                if (isGenericFreActive) {
                    // Launch the Generic First Run Experience if it was previously active.
                    freIntent = createGenericFirstRunIntent(
                            caller, TextUtils.equals(intent.getAction(), Intent.ACTION_MAIN));
                }
            }

            // Add a PendingIntent so that the intent used to launch Chrome will be resent when
            // First Run is completed or canceled.
            addPendingIntent(caller, freIntent, intent, requiresBroadcast);
            freIntent.putExtra(FirstRunActivity.EXTRA_FINISH_ON_TOUCH_OUTSIDE, true);

            if (!(caller instanceof Activity)) freIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            IntentUtils.safeStartActivity(caller, freIntent);
        } else {
            // First Run requires that the Intent contains NEW_TASK so that it doesn't sit on top
            // of something else.
            Intent newIntent = new Intent(intent);
            newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            IntentUtils.safeStartActivity(caller, newIntent);
        }
        return true;
    }
}

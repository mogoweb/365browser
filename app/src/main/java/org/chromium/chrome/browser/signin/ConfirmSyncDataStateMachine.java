// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.signin;

import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.os.Handler;
import android.support.annotation.IntDef;
import android.text.TextUtils;

import org.chromium.base.Callback;
import org.chromium.base.ThreadUtils;
import org.chromium.chrome.browser.signin.ConfirmImportSyncDataDialog.ImportSyncType;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * This class takes care of the various dialogs that must be shown when the user changes the
 * account they are syncing to (either directly, or by signing in to a new account). Most of the
 * complexity is due to many of the decisions getting answered through callbacks.
 *
 * This class progresses along the following state machine:
 *
 *       E-----\  G--\
 *       ^     |  ^  |
 *       |     v  |  v
 * A->B->C->D->+->F->H
 *    |        ^
 *    v        |
 *    \--------/
 *
 * Where:
 * A - Start
 * B - Decision: progress to C if the user signed in previously to a different account, F otherwise.
 * C - Decision: progress to E if we are switching from a managed account, D otherwise.
 * D - Action: show Import Data Dialog.
 * E - Action: show Switching from Managed Account Dialog.
 * F - Decision: progress to G if we are switching to a managed account, H otherwise.
 * G - Action: show Switching to Managed Account Dialog.
 * H - End: perform {@link ConfirmImportSyncDataDialog.Listener#onConfirm} with the result of the
 *     Import Data Dialog, if displayed or true if switching from a managed account.
 *
 * At any dialog, the user can cancel the dialog and end the whole process (resulting in
 * {@link ConfirmImportSyncDataDialog.Listener#onCancel}).
 */
public class ConfirmSyncDataStateMachine
        implements ConfirmImportSyncDataDialog.Listener, ConfirmManagedSyncDataDialog.Listener {
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({BEFORE_OLD_ACCOUNT_DIALOG, BEFORE_NEW_ACCOUNT_DIALOG, AFTER_NEW_ACCOUNT_DIALOG, DONE})
    private @interface State {}
    private static final int BEFORE_OLD_ACCOUNT_DIALOG = 0;  // Start of state B.
    private static final int BEFORE_NEW_ACCOUNT_DIALOG = 1;  // Start of state F.
    private static final int AFTER_NEW_ACCOUNT_DIALOG = 2;   // Start of state H.
    private static final int DONE = 4;

    @State private int mState = BEFORE_OLD_ACCOUNT_DIALOG;

    private static final int ACCOUNT_CHECK_TIMEOUT_MS = 30000;

    private final ConfirmImportSyncDataDialog.Listener mCallback;
    private final String mOldAccountName;
    private final String mNewAccountName;
    private final boolean mCurrentlyManaged;
    private final FragmentManager mFragmentManager;
    private final Context mContext;
    private final ImportSyncType mImportSyncType;
    private final ConfirmSyncDataStateMachineDelegate mDelegate;
    private final Handler mHandler = new Handler();

    private boolean mWipeData;
    private Boolean mNewAccountManaged;
    private Runnable mCheckTimeoutRunnable;

    /**
     * Run this state machine, displaying the appropriate dialogs.
     * @param callback One of the two functions of the {@link ConfirmImportSyncDataDialog.Listener}
     *         are guaranteed to be called.
     */
    public static void run(String oldAccountName, String newAccountName,
            ImportSyncType importSyncType, FragmentManager fragmentManager, Context context,
            ConfirmImportSyncDataDialog.Listener callback) {
        // Includes implicit not-null assertion.
        assert !newAccountName.equals("") : "New account name must be provided.";

        ConfirmSyncDataStateMachine stateMachine = new ConfirmSyncDataStateMachine(oldAccountName,
                newAccountName, importSyncType, fragmentManager, context, callback);
        stateMachine.progress();
    }

    /**
     * If any of the dialogs used by this state machine are shown, cancel them. If this state
     * machine is running and a dialog is being shown, the given
     * {@link ConfirmImportSyncDataDialog.Listener#onCancel())} is called.
     */
    public static void cancelAllDialogs(FragmentManager fragmentManager) {
        cancelDialog(fragmentManager,
                ConfirmImportSyncDataDialog.CONFIRM_IMPORT_SYNC_DATA_DIALOG_TAG);
        cancelDialog(fragmentManager,
                ConfirmManagedSyncDataDialog.CONFIRM_IMPORT_SYNC_DATA_DIALOG_TAG);
    }

    private static void cancelDialog(FragmentManager fragmentManager, String tag) {
        Fragment fragment = fragmentManager.findFragmentByTag(tag);

        if (fragment == null) return;
        DialogFragment dialogFragment = (DialogFragment) fragment;

        if (dialogFragment.getDialog() == null) return;
        dialogFragment.getDialog().cancel();
    }

    private ConfirmSyncDataStateMachine(String oldAccountName, String newAccountName,
            ImportSyncType importSyncType, FragmentManager fragmentManager, Context context,
            ConfirmImportSyncDataDialog.Listener callback) {
        ThreadUtils.assertOnUiThread();

        mOldAccountName = oldAccountName;
        mNewAccountName = newAccountName;
        mImportSyncType = importSyncType;
        mFragmentManager = fragmentManager;
        mContext = context;
        mCallback = callback;

        mCurrentlyManaged = SigninManager.get(context).getManagementDomain() != null;

        mDelegate = new ConfirmSyncDataStateMachineDelegate(mContext);

        // New account management status isn't needed right now, but fetching it
        // can take a few seconds, so we kick it off early.
        requestNewAccountManagementStatus();
    }

    /**
     * This will progress the state machine, by moving the state along and then by either calling
     * itself directly or creating a dialog. If the dialog is dismissed or answered negatively the
     * entire flow is over, if it is answered positively one of the onConfirm functions is called
     * and this function is called again.
     */
    private void progress() {
        switch (mState) {
            case BEFORE_OLD_ACCOUNT_DIALOG:
                mState = BEFORE_NEW_ACCOUNT_DIALOG;

                if (TextUtils.isEmpty(mOldAccountName) || mNewAccountName.equals(mOldAccountName)) {
                    // If there is no old account or the user is just logging back into whatever
                    // they were previously logged in as, progress past the old account checks.
                    progress();
                } else if (mCurrentlyManaged
                        && mImportSyncType == ImportSyncType.SWITCHING_SYNC_ACCOUNTS) {
                    // We only care about the user's previous account being managed if they are
                    // switching accounts.

                    mWipeData = true;

                    // This will call back into onConfirm() on success.
                    ConfirmManagedSyncDataDialog.showSwitchFromManagedAccountDialog(this,
                            mFragmentManager, mContext.getResources(),
                            SigninManager.extractDomainName(mOldAccountName),
                            mOldAccountName, mNewAccountName);
                } else {
                    // This will call back into onConfirm(boolean wipeData) on success.
                    ConfirmImportSyncDataDialog.showNewInstance(mOldAccountName, mNewAccountName,
                            mImportSyncType, mFragmentManager, this);
                }

                break;
            case BEFORE_NEW_ACCOUNT_DIALOG:
                mState = AFTER_NEW_ACCOUNT_DIALOG;
                if (mNewAccountManaged != null) {
                    // No need to show dialog if account management status is already known
                    handleNewAccountManagementStatus();
                } else {
                    showProgressDialog();
                    scheduleTimeout();
                }
                break;
            case AFTER_NEW_ACCOUNT_DIALOG:
                mState = DONE;
                mCallback.onConfirm(mWipeData);
                break;
            case DONE:
                throw new IllegalStateException("Can't progress from DONE state!");
        }
    }

    private void requestNewAccountManagementStatus() {
        SigninManager.isUserManaged(mNewAccountName, new Callback<Boolean>() {
            @Override
            public void onResult(Boolean result) {
                setIsNewAccountManaged(result);
            }
        });
    }

    private void setIsNewAccountManaged(Boolean isManaged) {
        assert isManaged != null;
        mNewAccountManaged = isManaged;
        if (mState == AFTER_NEW_ACCOUNT_DIALOG) {
            cancelTimeout();
            handleNewAccountManagementStatus();
        }
    }

    private void handleNewAccountManagementStatus() {
        assert mNewAccountManaged != null;
        assert mState == AFTER_NEW_ACCOUNT_DIALOG;

        mDelegate.dismissAllDialogs();

        if (mNewAccountManaged) {
            // Show 'logging into managed account' dialog
            // This will call back into onConfirm on success.
            ConfirmManagedSyncDataDialog.showSignInToManagedAccountDialog(
                    ConfirmSyncDataStateMachine.this, mFragmentManager, mContext.getResources(),
                    SigninManager.extractDomainName(mNewAccountName));
        } else {
            progress();
        }
    }

    private void showProgressDialog() {
        mDelegate.showFetchManagementPolicyProgressDialog(
                new ConfirmSyncDataStateMachineDelegate.ProgressDialogListener() {
                    @Override
                    public void onCancel() {
                        ConfirmSyncDataStateMachine.this.onCancel();
                    }
                });
    }

    private void scheduleTimeout() {
        if (mCheckTimeoutRunnable == null) {
            mCheckTimeoutRunnable = new Runnable() {
                @Override
                public void run() {
                    checkTimeout();
                }
            };
        }
        mHandler.postDelayed(mCheckTimeoutRunnable, ACCOUNT_CHECK_TIMEOUT_MS);
    }

    private void cancelTimeout() {
        if (mCheckTimeoutRunnable == null) {
            return;
        }
        mHandler.removeCallbacks(mCheckTimeoutRunnable);
        mCheckTimeoutRunnable = null;
    }

    private void checkTimeout() {
        assert mState == AFTER_NEW_ACCOUNT_DIALOG;
        assert mNewAccountManaged == null;

        mDelegate.showFetchManagementPolicyTimeoutDialog(
                new ConfirmSyncDataStateMachineDelegate.TimeoutDialogListener() {
                    @Override
                    public void onCancel() {
                        ConfirmSyncDataStateMachine.this.onCancel();
                    }

                    @Override
                    public void onRetry() {
                        requestNewAccountManagementStatus();
                        scheduleTimeout();
                        showProgressDialog();
                    }
                });
    }

    // ConfirmImportSyncDataDialog.Listener implementation.
    @Override
    public void onConfirm(boolean wipeData) {
        mWipeData = wipeData;
        progress();
    }

    // ConfirmManagedSyncDataDialog.Listener implementation.
    @Override
    public void onConfirm() {
        progress();
    }

    // ConfirmImportSyncDataDialog.Listener & ConfirmManagedSyncDataDialog.Listener implementation.
    @Override
    public void onCancel() {
        cancelTimeout();
        mDelegate.dismissAllDialogs();

        mState = DONE;
        mCallback.onCancel();
    }
}


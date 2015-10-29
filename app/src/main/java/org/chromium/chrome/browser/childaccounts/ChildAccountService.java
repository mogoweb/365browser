// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.childaccounts;

import android.accounts.Account;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.content.Context;
import android.content.Intent;

import org.chromium.base.CommandLine;
import org.chromium.base.Log;
import org.chromium.base.ThreadUtils;
import org.chromium.base.TraceEvent;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.browser.ChromeSwitches;
import org.chromium.chrome.browser.services.AccountsChangedReceiver;
import org.chromium.chrome.browser.signin.SigninManager;
import org.chromium.sync.signin.AccountManagerHelper;
import org.chromium.sync.signin.ChromeSigninController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import javax.annotation.Nullable;

/**
 * This class detects child accounts and enables special treatment for them.
 */
public class ChildAccountService {

    private static final String TAG = "cr.ChildAccountService";

    /**
     * The maximum amount of time to wait for the initial child account check, in milliseconds.
     */
    private static final int CHILD_ACCOUNT_TIMEOUT_MS = 1000;

    private static ChildAccountService sChildAccountService;

    private final Context mContext;

    /**
     * Non-null if the the child account status has been determined.
     */
    private Boolean mHasChildAccount;

    /**
     * Non-null while a child account check is in progress. Note that if the child account status
     * has been previously determined, the externally visible status only changes when the check
     * finishes. This means that before that, calls to {@link #checkHasChildAccount} and
     * {@link #hasChildAccount} will return the last value, even if it is now stale.
     */
    private AccountManagerFuture<Boolean> mAccountManagerFuture;

    /**
     * Non-empty while the initial child account check is in progress.
     */
    private final List<HasChildAccountCallback> mCallbacks = new ArrayList<>();

    protected ChildAccountService(Context context) {
        mContext = context;
        AccountsChangedReceiver.addObserver(
                new AccountsChangedReceiver.AccountsChangedObserver() {
                    @Override
                    public void onAccountsChanged(Context context, Intent intent) {
                        ThreadUtils.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                recheckChildAccountStatus();
                            }
                        });
                    }
                });
    }

    /**
     * Returns the shared ChildAccountService instance, creating one if necessary.
     *
     * @param context The context to initialize the ChildAccountService with.
     * @return The shared instance.
     */
    public static ChildAccountService getInstance(Context context) {
        ThreadUtils.assertOnUiThread();
        if (sChildAccountService == null) {
            sChildAccountService = new ChildAccountService(context.getApplicationContext());
        }
        return sChildAccountService;
    }

    /**
     * A callback to return the result of {@link #checkHasChildAccount}.
     */
    public static interface HasChildAccountCallback {

        /**
         * @param hasChildAccount Whether there is exactly one child account on the device.
         */
        public void onChildAccountChecked(boolean hasChildAccount);

    }

    /**
     * Checks for the presence of child accounts on the device.
     *
     * @param callback Will be called with the result (see
     *            {@link HasChildAccountCallback#onChildAccountChecked}). The callback is guaranteed
     *            to be called on a future turn of the event loop, even if the result can be
     *            determined immediately.
     */
    public void checkHasChildAccount(final HasChildAccountCallback callback) {
        if (mHasChildAccount != null || maybeUpdatePredeterminedChildAccountStatus()) {
            postCallback(callback);
            return;
        }
        mCallbacks.add(callback);
        if (mAccountManagerFuture == null) requestChildAccountStatus();
    }

    private void postCallback(final HasChildAccountCallback callback) {
        final boolean hasChildAccount = mHasChildAccount;
        ThreadUtils.postOnUiThread(new Runnable() {
            @Override
            public void run() {
                callback.onChildAccountChecked(hasChildAccount);
            }
        });
    }

    /**
     * Updates the child account status if it can be determined immediately.
     *
     * @return Whether the child account status was updated.
     */
    private boolean maybeUpdatePredeterminedChildAccountStatus() {
        Boolean predeterminedChildAccountStatus = getPredeterminedChildStatus();
        if (predeterminedChildAccountStatus == null) return false;
        setHasChildAccount(predeterminedChildAccountStatus);
        return true;
    }

    /**
     * @return The child account status if it can be determined immediately, or null otherwise.
     */
    @Nullable
    private Boolean getPredeterminedChildStatus() {
        if (!nativeIsChildAccountDetectionEnabled()) {
            Log.v(TAG, "Child account detection disabled");
            return false;
        }
        AccountManagerHelper accountManagerHelper = AccountManagerHelper.get(mContext);
        // This isn't strictly necessary, as getGoogleAccounts() will return an empty list if the
        // GET_ACCOUNTS permission is not granted, but it makes the behavior explicit.
        if (!accountManagerHelper.hasGetAccountsPermission()) {
            Log.v(TAG, "GET_ACCOUNTS permission not granted");
            return false;
        }
        Account[] googleAccounts = accountManagerHelper.getGoogleAccounts();
        if (googleAccounts.length != 1) {
            if (CommandLine.getInstance().hasSwitch(ChromeSwitches.CHILD_ACCOUNT)) {
                Log.w(TAG, "Ignoring --" + ChromeSwitches.CHILD_ACCOUNT + " command line flag "
                        + "because there are " + googleAccounts.length + " Google accounts on the "
                        + "device");
            } else {
                Log.v(TAG, googleAccounts.length + " Google accounts on the device");
            }
            return false;
        }
        String childAccountName =
                CommandLine.getInstance().getSwitchValue(ChromeSwitches.CHILD_ACCOUNT);
        String accountName = googleAccounts[0].name;
        if (childAccountName != null && accountName.equals(childAccountName)) {
            Log.v(TAG, "Child account forced via command line for " + childAccountName);
            return true;
        }
        return null;
    }

    private void requestChildAccountStatus() {
        assert mAccountManagerFuture == null;

        final Timer timer = new Timer();
        final int traceId = System.identityHashCode(this);
        TraceEvent.startAsync("ChildAccountService.checkFeatures", traceId);
        AccountManagerHelper accountManagerHelper = AccountManagerHelper.get(mContext);
        final AccountManagerFuture<Boolean> future = accountManagerHelper.checkChildAccount(
                accountManagerHelper.getSingleGoogleAccount(),
                new AccountManagerCallback<Boolean>() {
                    @Override
                    public void run(AccountManagerFuture<Boolean> future) {
                        TraceEvent.finishAsync("ChildAccountService.checkFeatures", traceId);

                        timer.cancel();

                        assert future.isDone();

                        // Ignore any future that is not the current one.
                        if (future == mAccountManagerFuture) {
                            setHasChildAccount(getFutureResult());
                        }
                    }
                });

        // Add a timeout during the initial check, to avoid blocking startup for too long.
        if (mHasChildAccount == null) {
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    if (!future.isDone()) {
                        Log.v(TAG, "AM request timed out");
                        future.cancel(true);
                    }
                }
            }, CHILD_ACCOUNT_TIMEOUT_MS);
        }
        mAccountManagerFuture = future;
    }

    private boolean getFutureResult() {
        boolean result = false;
        try {
            result = mAccountManagerFuture.getResult();
            Log.v(TAG, "AM future result:" + result);
        } catch (OperationCanceledException e) {
            Log.e(TAG, "Timed out fetching child account flag: ", e);
        } catch (AuthenticatorException | IOException e) {
            Log.e(TAG, "Error while fetching child account flag: ", e);
        } finally {
            mAccountManagerFuture = null;
        }
        return result;
    }

    /**
     * Returns whether there is a child account on the device. If the initial check has not
     * completed yet, this will return a cached value from the last run (which might be stale now).
     * Because this method might call into native code, it may only be called after the native
     * library and the profile have been loaded.
     *
     * @return Whether there is a child account on the device.
     */
    public boolean hasChildAccount() {
        ThreadUtils.assertOnUiThread();

        if (mHasChildAccount == null) return nativeGetIsChildAccount();

        return mHasChildAccount;
    }

    private void setHasChildAccount(boolean hasChildAccount) {
        Boolean oldHasChildAccount = mHasChildAccount;
        mHasChildAccount = hasChildAccount;
        for (HasChildAccountCallback callback : mCallbacks) {
            postCallback(callback);
        }
        mCallbacks.clear();

        onChildAccountStatusUpdated(oldHasChildAccount);
    }

    /**
     * Called when the child account status has been determined or updated.
     * Can be overridden by subclasses to avoid native calls and calls into dependencies in testing.
     *
     * @param oldValue The old child account status. This is null when the child account status
     *         has been determined for the first time after the browser has started.
     */
    protected void onChildAccountStatusUpdated(Boolean oldValue) {
        Log.v(TAG, "hasChildAccount: " + mHasChildAccount + " oldHasChildAccount: " + oldValue);
        if (mHasChildAccount) {
            if (oldValue == null) {
                // This is the first time we have determined the child account status, which means
                // the browser is starting up. If we are not signed in yet, the startup code will
                // sign in and call us back in onChildAccountSigninComplete().
                if (ChromeSigninController.get(mContext).getSignedInUser() == null) return;
            } else if (!oldValue.booleanValue()) {
                // We have switched from no child account to child account while the browser
                // is running. Sign in (which will call us back in onChildAccountSigninComplete()).
                SigninManager signinManager = SigninManager.get(mContext);
                Account account = AccountManagerHelper.get(mContext).getSingleGoogleAccount();
                signinManager.signInToSelectedAccount(null, account,
                        SigninManager.SIGNIN_TYPE_FORCED_CHILD_ACCOUNT,
                        SigninManager.SIGNIN_SYNC_IMMEDIATELY, false, null);
                return;
            }
        }
        // Fallthrough for all other cases: Propagate child account status to native code.
        // This is a no-op if the child account status does not change.
        nativeSetIsChildAccount(mHasChildAccount);
    }

    /**
     * Called when the browser has been signed in to the child account.
     */
    public void onChildAccountSigninComplete() {
        nativeSetIsChildAccount(true);
    }

    @VisibleForTesting
    void recheckChildAccountStatus() {
        // Cancel the AccountManagerFuture if it is running.
        if (mAccountManagerFuture != null) {
            mAccountManagerFuture.cancel(true);
            mAccountManagerFuture = null;
        }
        if (!maybeUpdatePredeterminedChildAccountStatus()) {
            requestChildAccountStatus();
        }
    }

    @CalledByNative
    private static void onInvalidationReceived() {
        assert ThreadUtils.runningOnUiThread();
        if (sChildAccountService == null) return;
        sChildAccountService.recheckChildAccountStatus();
    }

    /**
     * If this returns false, Chrome will assume there are no child accounts on the device,
     * and no further checks will be made, which has the effect of a kill switch.
     * Can be overridden by subclasses to avoid native calls in testing.
     *
     * @return Whether child account detection is enabled.
     */
    protected native boolean nativeIsChildAccountDetectionEnabled();

    /**
     * Returns the previously determined value of whether there is a child account on the device.
     * Can be overridden by subclasses to avoid native calls in testing.
     *
     * @return The previously determined value of whether there is a child account on the device.
     */
    protected native boolean nativeGetIsChildAccount();

    private native void nativeSetIsChildAccount(boolean isChild);
}

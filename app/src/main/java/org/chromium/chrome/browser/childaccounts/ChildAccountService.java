// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.childaccounts;

import android.accounts.Account;
import android.app.Activity;
import android.content.Context;

import org.chromium.base.Callback;
import org.chromium.base.ThreadUtils;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.components.signin.AccountManagerHelper;
import org.chromium.ui.base.WindowAndroid;

/**
 * This class serves as a simple interface for querying the child account information. It has two
 * methods for querying the child account information; checkHasChildAccount(...) which is
 * asynchronous and queries the system directly for the information and the synchronous
 * isChildAccount() which asks the native side assuming it has been set correctly already.
 *
 * The former method is used by ForcedSigninProcessor and FirstRunFlowSequencer to detect child
 * accounts since the native side is only activated on signing in. Once signed in by the
 * ForcedSigninProcessor, the ChildAccountInfoFetcher will notify the native side and also takes
 * responsibility for monitoring changes and taking a suitable action.
 *
 * The class also provides an interface through which a client can listen for child account status
 * changes. When the SupervisedUserContentProvider forces sign-in it waits for a status change
 * before querying the URL filters.
 */
public class ChildAccountService {
    private ChildAccountService() {
        // Only for static usage.
    }

    /**
     * Checks for the presence of child accounts on the device.
     *
     * @param callback A callback which will be called with the result.
     */
    public static void checkHasChildAccount(Context context, final Callback<Boolean> callback) {
        ThreadUtils.assertOnUiThread();
        final AccountManagerHelper helper = AccountManagerHelper.get();
        helper.getGoogleAccounts(new Callback<Account[]>() {
            @Override
            public void onResult(Account[] accounts) {
                if (accounts.length != 1) {
                    callback.onResult(false);
                } else {
                    helper.checkChildAccount(accounts[0], callback);
                }
            }
        });
    }

    /**
     * Set a callback to be called the next time a child account status change is received
     * @param callback the callback to be called when the status changes.
     */
    public static void listenForStatusChange(Callback<Boolean> callback) {
        nativeListenForChildStatusReceived(callback);
    }

    @CalledByNative
    private static void reauthenticateChildAccount(
            WindowAndroid windowAndroid, String accountName, final long nativeCallback) {
        ThreadUtils.assertOnUiThread();

        Activity activity = windowAndroid.getActivity().get();
        if (activity == null) {
            ThreadUtils.postOnUiThread(new Runnable() {
                @Override
                public void run() {
                    nativeOnReauthenticationResult(nativeCallback, false);
                }
            });
            return;
        }

        Account account = AccountManagerHelper.createAccountFromName(accountName);
        AccountManagerHelper.get().updateCredentials(account, activity, new Callback<Boolean>() {
            @Override
            public void onResult(Boolean result) {
                nativeOnReauthenticationResult(nativeCallback, result);
            }
        });
    }

    private static native void nativeListenForChildStatusReceived(Callback<Boolean> callback);

    private static native void nativeOnReauthenticationResult(
            long callbackPtr, boolean reauthSuccessful);
}

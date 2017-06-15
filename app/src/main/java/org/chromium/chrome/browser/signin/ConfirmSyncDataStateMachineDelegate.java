// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.signin;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;

import org.chromium.chrome.R;

/**
 * Class to decouple ConfirmSyncDataStateMachine from UI code and dialog management.
 */
public class ConfirmSyncDataStateMachineDelegate {
    /**
     * Listener to receive events from progress dialog. If the dialog is not dismissed by showing
     * other dialog or calling {@link ConfirmSyncDataStateMachineDelegate#dismissAllDialogs},
     * then {@link #onCancel} will be called once.
     */
    public interface ProgressDialogListener {
        /**
         * This method is called when user cancels the dialog in any way.
         */
        void onCancel();
    }

    /**
     * Listener to receive events from timeout dialog. If the dialog is not dismissed by showing
     * other dialog or calling {@link ConfirmSyncDataStateMachineDelegate#dismissAllDialogs},
     * then either {@link #onCancel} or {@link #onRetry} will be called once.
     */
    public interface TimeoutDialogListener {
        /**
         * This method is called when user cancels the dialog in any way.
         */
        void onCancel();

        /**
         * This method is called when user clicks retry button.
         */
        void onRetry();
    }

    private final Context mContext;

    private Dialog mProgressDialog;
    private AlertDialog mTimeoutAlertDialog;

    public ConfirmSyncDataStateMachineDelegate(Context context) {
        mContext = context;
    }

    /**
     * Shows progress dialog. Will dismiss other dialogs shown, if any.
     *
     * @param listener The {@link ProgressDialogListener} that will be notified about user actions.
     */
    public void showFetchManagementPolicyProgressDialog(final ProgressDialogListener listener) {
        dismissAllDialogs();
        mProgressDialog = new AlertDialog.Builder(mContext, R.style.SigninAlertDialogTheme)
                                  .setView(R.layout.signin_progress_bar_dialog)
                                  .setNegativeButton(R.string.cancel,
                                          new DialogInterface.OnClickListener() {
                                              @Override
                                              public void onClick(DialogInterface dialog, int i) {
                                                  dialog.cancel();
                                              }
                                          })
                                  .setOnCancelListener(new DialogInterface.OnCancelListener() {
                                      @Override
                                      public void onCancel(DialogInterface dialog) {
                                          listener.onCancel();
                                      }
                                  })
                                  .create();
        mProgressDialog.show();
    }

    /**
     * Shows timeout dialog. Will dismiss other dialogs shown, if any.
     *
     * @param listener The {@link TimeoutDialogListener} that will be notified about user actions.
     */
    public void showFetchManagementPolicyTimeoutDialog(final TimeoutDialogListener listener) {
        dismissAllDialogs();
        mTimeoutAlertDialog =
                new AlertDialog.Builder(mContext, R.style.SigninAlertDialogTheme)
                        .setTitle(R.string.sign_in_timeout_title)
                        .setMessage(R.string.sign_in_timeout_message)
                        .setNegativeButton(R.string.cancel,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.cancel();
                                    }
                                })
                        .setPositiveButton(R.string.retry,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        listener.onRetry();
                                    }
                                })
                        .setOnCancelListener(new DialogInterface.OnCancelListener() {
                            @Override
                            public void onCancel(DialogInterface dialog) {
                                listener.onCancel();
                            }
                        })
                        .create();
        mTimeoutAlertDialog.show();
    }

    /**
     * Dismisses all dialogs.
     */
    public void dismissAllDialogs() {
        if (mProgressDialog != null) mProgressDialog.dismiss();
        mProgressDialog = null;

        if (mTimeoutAlertDialog != null) mTimeoutAlertDialog.dismiss();
        mTimeoutAlertDialog = null;
    }
}

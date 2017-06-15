// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.locale;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.widget.Button;

import org.chromium.base.ActivityState;
import org.chromium.base.ApplicationStatus;
import org.chromium.base.Callback;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.library_loader.LibraryLoader;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.locale.LocaleManager.SearchEnginePromoType;
import org.chromium.chrome.browser.search_engines.TemplateUrlService;
import org.chromium.chrome.browser.widget.PromoDialog;
import org.chromium.chrome.browser.widget.RadioButtonLayout;
import org.chromium.ui.base.WindowAndroid;

/** A dialog that forces the user to choose a default search engine. */
public class DefaultSearchEnginePromoDialog extends PromoDialog {
    /** Notified about events happening to the dialog. */
    public static interface DefaultSearchEnginePromoDialogObserver {
        void onDialogShown(DefaultSearchEnginePromoDialog shownDialog);
    }
    private static DefaultSearchEnginePromoDialogObserver sObserver;

    /** Used to determine the promo dialog contents. */
    @SearchEnginePromoType
    private final int mDialogType;

    /** Called when the dialog is dismissed after the user has chosen a search engine. */
    private final Callback<Boolean> mOnDismissed;

    /** Encapsulates most of the logic for filling the dialog and handling clicks. */
    private DefaultSearchEngineDialogHelper mHelper;

    /**
     * Construct and show the dialog.  Will be asynchronous if the TemplateUrlService has not yet
     * been loaded.
     *
     * @param context     Context to build the dialog with.
     * @param dialogType  Type of dialog to show.
     * @param onDismissed Notified about whether the user chose an engine when it got dismissed.
     */
    public static void show(final Context context, @SearchEnginePromoType final int dialogType,
            @Nullable final Callback<Boolean> onDismissed) {
        assert LibraryLoader.isInitialized();

        // Load up the search engines.
        final TemplateUrlService instance = TemplateUrlService.getInstance();
        instance.registerLoadListener(new TemplateUrlService.LoadListener() {
            @Override
            public void onTemplateUrlServiceLoaded() {
                instance.unregisterLoadListener(this);

                Activity activity = WindowAndroid.activityFromContext(context);
                if (ApplicationStatus.getStateForActivity(activity) == ActivityState.DESTROYED) {
                    if (onDismissed != null) onDismissed.onResult(false);
                    return;
                }

                new DefaultSearchEnginePromoDialog(context, dialogType, onDismissed).show();
            }
        });
        if (!instance.isLoaded()) instance.load();
    }

    private DefaultSearchEnginePromoDialog(
            Context context, int dialogType, @Nullable Callback<Boolean> onDismissed) {
        super(context);
        mDialogType = dialogType;
        mOnDismissed = onDismissed;
        setOnDismissListener(this);

        // No one should be able to bypass this dialog by clicking outside or by hitting back.
        setCancelable(false);
        setCanceledOnTouchOutside(false);
    }

    @Override
    protected DialogParams getDialogParams() {
        PromoDialog.DialogParams params = new PromoDialog.DialogParams();
        params.headerStringResource = R.string.search_engine_dialog_title;
        params.footerStringResource = R.string.search_engine_dialog_footer;
        params.primaryButtonStringResource = R.string.ok;
        return params;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Button okButton = (Button) findViewById(R.id.button_primary);
        okButton.setEnabled(false);

        RadioButtonLayout radioButtons = new RadioButtonLayout(getContext());
        radioButtons.setId(R.id.default_search_engine_dialog_options);
        addControl(radioButtons);

        Runnable dismissRunnable = new Runnable() {
            @Override
            public void run() {
                dismiss();
            }
        };
        mHelper = new DefaultSearchEngineDialogHelper(
                mDialogType, radioButtons, okButton, dismissRunnable);
    }

    @Override
    public void show() {
        super.show();
        if (mDialogType == LocaleManager.SEARCH_ENGINE_PROMO_SHOW_NEW) {
            RecordUserAction.record("SearchEnginePromo.NewDevice.Shown.Dialog");
        } else if (mDialogType == LocaleManager.SEARCH_ENGINE_PROMO_SHOW_EXISTING) {
            RecordUserAction.record("SearchEnginePromo.ExistingDevice.Shown.Dialog");
        }
        if (sObserver != null) sObserver.onDialogShown(this);
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        if (mHelper.getCurrentlySelectedKeyword() == null) {
            // This shouldn't happen, but in case it does, finish the Activity so that the user has
            // to respond to the dialog next time.
            if (getOwnerActivity() != null) getOwnerActivity().finish();
        }

        if (mOnDismissed != null) {
            mOnDismissed.onResult(mHelper.getCurrentlySelectedKeyword() != null);
        }
    }

    /** See {@link #sObserver}. */
    @VisibleForTesting
    @Nullable
    public static void setObserverForTests(DefaultSearchEnginePromoDialogObserver observer) {
        sObserver = observer;
    }
}

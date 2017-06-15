// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.widget;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.FrameLayout;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;

/**
 * Generic builder for promo dialogs.
 */
public abstract class PromoDialog
        extends Dialog implements View.OnClickListener, DialogInterface.OnDismissListener {
    /** Parameters that can be used to create a new PromoDialog. */
    public static class DialogParams {
        /**
         * Optional: Resource ID of the Drawable to use for the promo illustration.
         * This parameter and {@link #vectorDrawableResource} are mutually exclusive.
         */
        public int drawableResource;

        /**
         * Optional: Resource ID of the VectorDrawable to use for the promo illustration.
         * This parameter and {@link #drawableResource} are mutually exclusive.
         */
        public int vectorDrawableResource;

        /** Resource ID of the String to show as the promo title. */
        public int headerStringResource;

        /** Optional: Resource ID of the String to show as descriptive text. */
        public int subheaderStringResource;

        /** Optional: Resource ID of the String to show as footer text. */
        public int footerStringResource;

        /** Optional: Resource ID of the String to show on the primary/ok button. */
        public int primaryButtonStringResource;

        /** Optional: Resource ID of the String to show on the secondary/cancel button. */
        public int secondaryButtonStringResource;
    }

    private static final int[] CLICKABLE_BUTTON_IDS = {R.id.button_primary, R.id.button_secondary};

    private final FrameLayout mScrimView;
    private final PromoDialogLayout mDialogLayout;

    protected PromoDialog(Context context) {
        super(context, R.style.PromoDialog);

        mScrimView = new FrameLayout(context);
        mScrimView.setBackgroundColor(ApiCompatibilityUtils.getColor(
                context.getResources(), R.color.modal_dialog_scrim_color));
        LayoutInflater.from(context).inflate(R.layout.promo_dialog_layout, mScrimView, true);

        mDialogLayout = (PromoDialogLayout) mScrimView.findViewById(R.id.promo_dialog_layout);
        mDialogLayout.initialize(getDialogParams());
    }

    /**
     * Adds a View to the layout within the scrollable area.
     * See {@link PromoDialogLayout#addControl}.
     */
    protected void addControl(View control) {
        mDialogLayout.addControl(control);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(mScrimView);

        // Force the window to allow the dialog contents be as wide as necessary.
        getWindow().setLayout(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);

        // Connect all the buttons to this class.
        for (int interactiveViewId : CLICKABLE_BUTTON_IDS) {
            View view = findViewById(interactiveViewId);
            if (view != null) view.setOnClickListener(this);
        }
    }

    @Override
    public void onClick(View view) {}

    /** Returns a set of {@link DialogParams} that define what is shown in the promo dialog. */
    protected abstract DialogParams getDialogParams();
}

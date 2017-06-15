// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.photo_picker;

import android.content.Context;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;

import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;
import org.chromium.ui.PhotoPickerListener;

/**
 * UI for the photo chooser that shows on the Android platform as a result of
 * &lt;input type=file accept=image &gt; form element.
 */
public class PhotoPickerDialog extends AlertDialog {
    // The category we're showing photos for.
    private PickerCategoryView mCategoryView;

    /**
     * The PhotoPickerDialog constructor.
     * @param context The context to use.
     * @param listener The listener object that gets notified when an action is taken.
     * @param multiSelectionAllowed Whether the photo picker should allow multiple items to be
     *                              selected.
     */
    public PhotoPickerDialog(
            Context context, PhotoPickerListener listener, boolean multiSelectionAllowed) {
        super(context, R.style.FullscreenWhite);

        // Initialize the main content view.
        mCategoryView = new PickerCategoryView(context);
        mCategoryView.initialize(this, listener, multiSelectionAllowed);
        setView(mCategoryView);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setLayout(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    @Override
    public void dismiss() {
        super.dismiss();
        mCategoryView.onDialogDismissed();
    }

    @VisibleForTesting
    public PickerCategoryView getCategoryViewForTesting() {
        return mCategoryView;
    }
}

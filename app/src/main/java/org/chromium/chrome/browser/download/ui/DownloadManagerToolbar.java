// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download.ui;

import android.content.Context;
import android.support.v7.widget.AppCompatSpinner;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Spinner;

import org.chromium.base.VisibleForTesting;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.download.ui.DownloadManagerUi.DownloadUiObserver;
import org.chromium.chrome.browser.widget.selection.SelectableListToolbar;

import java.util.List;

/**
 * Handles toolbar functionality for the {@link DownloadManagerUi}.
 */
public class DownloadManagerToolbar extends SelectableListToolbar<DownloadHistoryItemWrapper>
        implements DownloadUiObserver {
    private Spinner mSpinner;

    public DownloadManagerToolbar(Context context, AttributeSet attrs) {
        super(context, attrs);
        inflateMenu(R.menu.download_manager_menu);
    }

    /**
     * Initializes the spinner for the download filter.
     * @param adapter The adapter associated with the spinner.
     */
    public void initializeFilterSpinner(FilterAdapter adapter) {
        mSpinner = new AppCompatSpinner(this.getContext());
        mSpinner.setAdapter(adapter);
        mSpinner.setOnItemSelectedListener(adapter);
        addView(mSpinner);
    }

    /**
     * Removes the close button from the toolbar.
     */
    public void removeCloseButton() {
        getMenu().removeItem(R.id.close_menu_id);
    }

    @Override
    public void onFilterChanged(int filter) {
        mSpinner.setSelection(filter);
    }

    @Override
    public void onSelectionStateChange(List<DownloadHistoryItemWrapper> selectedItems) {
        boolean wasSelectionEnabled = mIsSelectionEnabled;
        super.onSelectionStateChange(selectedItems);

        mSpinner.setVisibility((mIsSelectionEnabled || mIsSearching) ? GONE : VISIBLE);
        if (mIsSelectionEnabled) {
            int numSelected = mSelectionDelegate.getSelectedItems().size();

            // If the share or delete menu items are shown in the overflow menu instead of as an
            // action, there may not be views associated with them.
            View shareButton = findViewById(R.id.selection_mode_share_menu_id);
            if (shareButton != null) {
                shareButton.setContentDescription(getResources().getQuantityString(
                        R.plurals.accessibility_share_selected_items,
                                numSelected, numSelected));
            }

            View deleteButton = findViewById(R.id.selection_mode_delete_menu_id);
            if (deleteButton != null) {
                deleteButton.setContentDescription(getResources().getQuantityString(
                        R.plurals.accessibility_remove_selected_items,
                        numSelected, numSelected));
            }

            if (!wasSelectionEnabled) {
                RecordUserAction.record("Android.DownloadManager.SelectionEstablished");
            }
        }
    }

    @Override
    protected void onDataChanged(int numItems) {
        super.onDataChanged(numItems);
        getMenu().findItem(R.id.info_menu_id).setVisible(numItems > 0);
    }

    @Override
    public void onManagerDestroyed() {
        mSpinner.setAdapter(null);
    }

    @Override
    public void showSearchView() {
        super.showSearchView();
        mSpinner.setVisibility(GONE);
    }

    @Override
    public void hideSearchView() {
        super.hideSearchView();
        mSpinner.setVisibility(VISIBLE);
    }

    /** Returns the {@link Spinner}. */
    @VisibleForTesting
    public Spinner getSpinnerForTests() {
        return mSpinner;
    }
}

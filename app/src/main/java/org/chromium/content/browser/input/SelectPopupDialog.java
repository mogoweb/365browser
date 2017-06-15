// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.input;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.util.SparseBooleanArray;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;

import org.chromium.content.R;
import org.chromium.content.browser.ContentViewCore;

import java.util.List;

/**
 * Handles the popup dialog for the <select> HTML tag support.
 */
public class SelectPopupDialog implements SelectPopup {
    private static final int[] SELECT_DIALOG_ATTRS = {
        R.attr.select_dialog_multichoice,
        R.attr.select_dialog_singlechoice
    };

    // The dialog hosting the popup list view.
    private final AlertDialog mListBoxPopup;
    private final ContentViewCore mContentViewCore;

    private boolean mSelectionNotified;

    public SelectPopupDialog(ContentViewCore contentViewCore, Context windowContext,
            List<SelectPopupItem> items, boolean multiple, int[] selected) {
        mContentViewCore = contentViewCore;

        final ListView listView = new ListView(windowContext);
        // setCacheColorHint(0) is required to prevent a black background in WebView on Lollipop:
        // crbug.com/653026
        listView.setCacheColorHint(0);

        AlertDialog.Builder b = new AlertDialog.Builder(windowContext)
                .setView(listView)
                .setCancelable(true);
        setInverseBackgroundForced(b);

        if (multiple) {
            b.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    notifySelection(getSelectedIndices(listView));
                }
            });
            b.setNegativeButton(android.R.string.cancel,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            notifySelection(null);
                        }
                    });
        }
        mListBoxPopup = b.create();
        final SelectPopupAdapter adapter = new SelectPopupAdapter(
                mListBoxPopup.getContext(), getSelectDialogLayout(multiple), items);
        listView.setAdapter(adapter);
        listView.setFocusableInTouchMode(true);

        if (multiple) {
            listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
            for (int i = 0; i < selected.length; ++i) {
                listView.setItemChecked(selected[i], true);
            }
        } else {
            listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
            listView.setOnItemClickListener(new OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View v,
                        int position, long id) {
                    notifySelection(getSelectedIndices(listView));
                    mListBoxPopup.dismiss();
                }
            });
            if (selected.length > 0) {
                listView.setSelection(selected[0]);
                listView.setItemChecked(selected[0], true);
            }
        }
        mListBoxPopup.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                notifySelection(null);
            }
        });
    }

    @SuppressWarnings("deprecation")
    private static void setInverseBackgroundForced(AlertDialog.Builder builder) {
        // This is needed for pre-Holo themes (e.g. android:Theme.Black), which can be used in
        // WebView. See http://crbug.com/596626. This can be removed if/when this class starts
        // using android.support.v7.app.AlertDialog.
        builder.setInverseBackgroundForced(true);
    }

    private int getSelectDialogLayout(boolean isMultiChoice) {
        int resourceId;
        TypedArray styledAttributes = mListBoxPopup.getContext().obtainStyledAttributes(
                R.style.SelectPopupDialog, SELECT_DIALOG_ATTRS);
        resourceId = styledAttributes.getResourceId(isMultiChoice ? 0 : 1, 0);
        styledAttributes.recycle();
        return resourceId;
    }

    private static int[] getSelectedIndices(ListView listView) {
        SparseBooleanArray sparseArray = listView.getCheckedItemPositions();
        int selectedCount = 0;
        for (int i = 0; i < sparseArray.size(); ++i) {
            if (sparseArray.valueAt(i)) {
                selectedCount++;
            }
        }
        int[] indices = new int[selectedCount];
        for (int i = 0, j = 0; i < sparseArray.size(); ++i) {
            if (sparseArray.valueAt(i)) {
                indices[j++] = sparseArray.keyAt(i);
            }
        }
        return indices;
    }

    private void notifySelection(int[] indicies) {
        if (mSelectionNotified) return;
        mContentViewCore.selectPopupMenuItems(indicies);
        mSelectionNotified = true;
    }

    @Override
    public void show() {
        try {
            mListBoxPopup.show();
        } catch (WindowManager.BadTokenException e) {
            notifySelection(null);
        }
    }

    @Override
    public void hide(boolean sendsCancelMessage) {
        if (sendsCancelMessage) {
            mListBoxPopup.cancel();
            notifySelection(null);
        } else {
            mSelectionNotified = true;
            mListBoxPopup.cancel();
        }
    }
}

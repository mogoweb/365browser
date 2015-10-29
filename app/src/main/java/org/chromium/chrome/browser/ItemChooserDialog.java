// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.chromium.chrome.R;
import org.chromium.ui.base.DeviceFormFactor;
import org.chromium.ui.widget.TextViewWithClickableSpans;

import java.util.List;

/**
 * A general-purpose dialog for presenting a list of things to pick from.
 */
public class ItemChooserDialog {
    /**
     * An interface to implement to get a callback when something has been
     * selected.
     */
    public interface ItemSelectedCallback {
        /**
         * Returns the user selection.
         *
         * @param id The id of the item selected. Blank if the dialog was closed
         * without selecting anything.
         */
        void onItemSelected(String id);
    }

    /**
     * A class representing one data row in the picker.
     */
    public static class ItemChooserRow {
        private final String mKey;
        private final String mDescription;

        public ItemChooserRow(String key, String description) {
            mKey = key;
            mDescription = description;
        }
    }

    /**
     * The labels to show in the dialog.
     */
    public static class ItemChooserLabels {
        // The title at the top of the dialog.
        public final SpannableString mTitle;
        // The message to show while results are trickling in.
        public final String mSearching;
        // The message to show when no results were produced.
        public final SpannableString mNoneFound;
        // A status message to show above the button row.
        public final SpannableString mStatus;
        // The label for the positive button (e.g. Select/Pair).
        public final String mPositiveButton;

        public ItemChooserLabels(SpannableString title, String searching, SpannableString noneFound,
                SpannableString status, String positiveButton) {
            mTitle = title;
            mSearching = searching;
            mNoneFound = noneFound;
            mStatus = status;
            mPositiveButton = positiveButton;
        }
    }

    /**
     * An adapter for keeping track of which items to show in the dialog.
     */
    private class ItemAdapter extends ArrayAdapter<ItemChooserRow>
            implements AdapterView.OnItemClickListener {
        private final LayoutInflater mInflater;

        // The background color of the highlighted item.
        private final int mBackgroundHighlightColor;

        // The color of the non-highlighted text.
        private final int mDefaultTextColor;

        // The zero-based index of the item currently selected in the dialog,
        // or -1 (INVALID_POSITION) if nothing is selected.
        private int mSelectedItem = ListView.INVALID_POSITION;

        public ItemAdapter(Context context, int resource) {
            super(context, resource);

            mInflater = LayoutInflater.from(context);

            mBackgroundHighlightColor = getContext().getResources().getColor(
                    R.color.light_active_color);
            mDefaultTextColor = getContext().getResources().getColor(
                    R.color.default_text_color);
        }

        @Override
        public void clear() {
            mSelectedItem = ListView.INVALID_POSITION;
            mConfirmButton.setEnabled(false);
            super.clear();
        }

        /**
         * Returns the key of the currently selected item or blank if nothing is
         * selected.
         */
        public String getSelectedItemKey() {
            ItemChooserRow row = getItem(mSelectedItem);
            if (row == null) return "";
            return row.mKey;
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView view;
            if (convertView instanceof TextView) {
                view = (TextView) convertView;
            } else {
                view = (TextView) mInflater.inflate(
                        R.layout.item_chooser_dialog_row, parent, false);
            }

            // Set highlighting for currently selected item.
            if (position == mSelectedItem) {
                view.setBackgroundColor(mBackgroundHighlightColor);
                view.setTextColor(Color.WHITE);
            } else {
                view.setBackground(null);
                view.setTextColor(mDefaultTextColor);
            }

            ItemChooserRow item = getItem(position);
            view.setText(item.mDescription);
            return view;
        }

        @Override
        public void onItemClick(AdapterView<?> adapter, View view, int position, long id) {
            mSelectedItem = position;
            mConfirmButton.setEnabled(true);
            mItemAdapter.notifyDataSetChanged();
        }
    }

    private Context mContext;

    // The dialog this class encapsulates.
    private Dialog mDialog;

    // The callback to notify when the user selected an item.
    private ItemSelectedCallback mItemSelectedCallback;

    // Individual UI elements.
    private TextViewWithClickableSpans mTitle;
    private TextViewWithClickableSpans mNotFoundMessage;
    private ProgressBar mProgressBar;
    private ListView mListView;
    private TextView mStatus;
    private Button mConfirmButton;

    // The labels to display in the dialog.
    private ItemChooserLabels mLabels;

    // The adapter containing the items to show in the dialog.
    private ItemAdapter mItemAdapter;

    // How much of the height of the screen should be taken up by the listview.
    private static final double LISTVIEW_HEIGHT_PERCENT = 0.30;
    // The minimum height of the listview in the dialog (in dp).
    private static final int MIN_HEIGHT_DP = 56;
    // The maximum height of the listview in the dialog (in dp).
    private static final int MAX_HEIGHT_DP = 400;

    /**
     * Creates the ItemChooserPopup and displays it (and starts waiting for data).
     *
     * @param context Context which is used for launching a dialog.
     * @param callback The callback used to communicate back what was selected.
     * @param labels The labels to show in the dialog.
     */
    public ItemChooserDialog(
            Context context, ItemSelectedCallback callback, ItemChooserLabels labels) {
        mContext = context;
        mItemSelectedCallback = callback;
        mLabels = labels;

        LinearLayout dialogContainer = (LinearLayout) LayoutInflater.from(
                mContext).inflate(R.layout.item_chooser_dialog, null);

        mListView = (ListView) dialogContainer.findViewById(R.id.items);
        mProgressBar = (ProgressBar) dialogContainer.findViewById(R.id.progress);
        mStatus = (TextView) dialogContainer.findViewById(R.id.status);
        mTitle = (TextViewWithClickableSpans) dialogContainer.findViewById(
                R.id.dialog_title);
        mNotFoundMessage =
                (TextViewWithClickableSpans) dialogContainer.findViewById(
                        R.id.not_found_message);

        mTitle.setText(labels.mTitle);
        mTitle.setMovementMethod(LinkMovementMethod.getInstance());

        mNotFoundMessage.setText(labels.mNoneFound);
        mNotFoundMessage.setMovementMethod(LinkMovementMethod.getInstance());

        mStatus.setText(labels.mSearching);
        mStatus.setMovementMethod(LinkMovementMethod.getInstance());

        mConfirmButton = (Button) dialogContainer.findViewById(R.id.positive);
        mConfirmButton.setText(labels.mPositiveButton);
        mConfirmButton.setEnabled(false);
        mConfirmButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mItemSelectedCallback.onItemSelected(
                        mItemAdapter.getSelectedItemKey());
                mDialog.dismiss();
            }
        });

        mItemAdapter = new ItemAdapter(mContext, R.layout.item_chooser_dialog_row);
        mListView.setAdapter(mItemAdapter);
        mListView.setEmptyView(mNotFoundMessage);
        mNotFoundMessage.setVisibility(View.GONE);
        mListView.setOnItemClickListener(mItemAdapter);
        mListView.setDivider(null);

        // The list is the main element in the dialog and it should grow and
        // shrink according to the size of the screen available (clamped to a
        // min and a max).
        View listViewContainer = dialogContainer.findViewById(R.id.container);
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager manager = (WindowManager) mContext.getSystemService(
                Context.WINDOW_SERVICE);
        manager.getDefaultDisplay().getMetrics(metrics);

        float density = context.getResources().getDisplayMetrics().density;
        int height = (int) (metrics.heightPixels * LISTVIEW_HEIGHT_PERCENT);
        height = Math.min(height, Math.round(MAX_HEIGHT_DP * density));
        height = Math.max(height, Math.round(MIN_HEIGHT_DP * density));
        listViewContainer.setLayoutParams(
                new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, height));

        showDialogForView(dialogContainer);
    }

    private void showDialogForView(View view) {
        mDialog = new Dialog(mContext);
        mDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        mDialog.addContentView(view,
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                                              LinearLayout.LayoutParams.MATCH_PARENT));

        Window window = mDialog.getWindow();
        if (!DeviceFormFactor.isTablet(mContext)) {
            // On smaller screens, make the dialog fill the width of the screen,
            // and appear at the top.
            window.setBackgroundDrawable(new ColorDrawable(Color.WHITE));
            window.setGravity(Gravity.TOP);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                             ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        mDialog.show();
    }

    /**
     * Add items to show in the dialog.
     *
     * @param list The list of items to show. This function can be called
     * multiple times to add more items and new items will be appended to
     * the end of the list. An empty list should be used if there are no
     * items to show.
     */
    public void showList(List<ItemChooserRow> list) {
        mProgressBar.setVisibility(View.GONE);
        mStatus.setText(mLabels.mStatus);

        if (list.isEmpty()) {
            boolean showEmptyMessage = mItemAdapter.isEmpty();
            mNotFoundMessage.setVisibility(
                    showEmptyMessage ? View.VISIBLE : View.GONE);
            mListView.setVisibility(showEmptyMessage ? View.GONE : View.VISIBLE);
        } else {
            mListView.setVisibility(View.VISIBLE);
            mItemAdapter.addAll(list);
        }
    }

    /**
     * Clear all items from the dialog.
     */
    public void clear() {
        mItemAdapter.clear();
    }
}

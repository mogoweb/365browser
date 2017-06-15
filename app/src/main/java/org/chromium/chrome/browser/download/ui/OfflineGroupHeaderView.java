// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.text.format.Formatter;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.download.DownloadUtils;
import org.chromium.chrome.browser.download.ui.DownloadHistoryAdapter.SubsectionHeader;
import org.chromium.chrome.browser.download.ui.DownloadItemSelectionDelegate.SubsectionHeaderSelectionObserver;
import org.chromium.chrome.browser.widget.DateDividedAdapter.TimedItem;
import org.chromium.chrome.browser.widget.TintedImageView;
import org.chromium.chrome.browser.widget.selection.SelectableItemView;

import java.util.Date;
import java.util.Set;

/**
 * A header that presents users the option to view or hide the suggested offline pages.
 */
public class OfflineGroupHeaderView
        extends SelectableItemView<TimedItem> implements SubsectionHeaderSelectionObserver {
    private final int mIconBackgroundColorSelected;
    private final ColorStateList mIconForegroundColorList;

    private SubsectionHeader mHeader;
    private DownloadHistoryAdapter mAdapter;
    private DownloadItemSelectionDelegate mSelectionDelegate;

    private TextView mPageCountHeader;
    private TextView mFileSizeView;
    private ImageView mExpandImage;
    private TintedImageView mIconView;

    public OfflineGroupHeaderView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mIconBackgroundColorSelected =
                ApiCompatibilityUtils.getColor(getResources(), R.color.google_grey_600);
        mIconForegroundColorList = DownloadUtils.getIconForegroundColorList(context);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mIconView = (TintedImageView) findViewById(R.id.icon_view);
        mPageCountHeader = (TextView) findViewById(R.id.page_count_text);
        mFileSizeView = (TextView) findViewById(R.id.filesize_view);
        mExpandImage = (ImageView) findViewById(R.id.expand_icon);
    }

    /**
     * @param adapter The adapter associated with this header.
     */
    public void setAdapter(DownloadHistoryAdapter adapter) {
        mAdapter = adapter;
    }

    @Override
    public void setChecked(boolean checked) {
        super.setChecked(checked);
        updateCheckIcon(checked);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mSelectionDelegate != null) {
            setChecked(mSelectionDelegate.isHeaderSelected(mHeader));
        }
    }

    /**
     * Updates the properties of this view.
     * @param header The associated {@link SubsectionHeader}.
     */
    @SuppressLint("StringFormatMatches")
    public void displayHeader(SubsectionHeader header) {
        this.mHeader = header;
        // TODO(crbug.com/635567): Fix lint properly.
        mPageCountHeader.setText(getResources().getString(
                R.string.download_manager_offline_header_title, header.getItemCount()));
        mFileSizeView.setText(Formatter.formatFileSize(getContext(), header.getTotalFileSize()));
        updateExpandIcon(header.isExpanded());
        setChecked(mSelectionDelegate.isHeaderSelected(header));
        setBackgroundResourceForGroupPosition(mHeader.isFirstInGroup(), mHeader.isLastInGroup());
    }

    private void updateExpandIcon(boolean expanded) {
        mExpandImage.setImageResource(expanded ? R.drawable.ic_collapsed : R.drawable.ic_expanded);
        mExpandImage.setContentDescription(
                getResources().getString(expanded ? R.string.accessibility_collapse_offline_pages
                                                  : R.string.accessibility_expand_offline_pages));
    }

    private void updateCheckIcon(boolean checked) {
        if (checked) {
            mIconView.setBackgroundColor(mIconBackgroundColorSelected);
            mIconView.setImageResource(R.drawable.ic_check_googblue_24dp);
            mIconView.setTint(mIconForegroundColorList);
        } else {
            mIconView.setBackgroundResource(R.color.light_active_color);
            mIconView.setImageResource(R.drawable.ic_chrome);
            mIconView.setTint(null);
        }
    }

    @Override
    public void onClick() {
        boolean newState = !mHeader.isExpanded();
        mAdapter.setSubsectionExpanded(new Date(mHeader.getTimestamp()), newState);
    }

    @Override
    protected boolean isSelectionModeActive() {
        return mSelectionDelegate.isSelectionEnabled();
    }

    @Override
    protected boolean toggleSelectionForItem(TimedItem item) {
        return mSelectionDelegate.toggleSelectionForSubsection(mHeader);
    }

    /**
     * Sets the selection delegate and registers |this| as
     * an observer. The delegate must be set before the item can respond to click events.
     * {@link SelectionDelegate} expects all the views to be of same type i.e.
     * SelectableItemView<DownloadHistoryItemWrapper>, whereas DownloadItemSelectionDelegate can
     * handle multiple types. This view being of type  SelectableItemView<TimedItem>, we need
     * to use a DownloadItemSelectionDelegate instead of SelectionDelegate.
     * @param delegate The selection delegate that will inform this item of selection changes.
     */
    public void setSelectionDelegate(DownloadItemSelectionDelegate delegate) {
        if (mSelectionDelegate == delegate) return;

        if (mSelectionDelegate != null) {
            mSelectionDelegate.removeObserver(this);
        }
        mSelectionDelegate = delegate;
        mSelectionDelegate.addObserver(this);
    }

    @Override
    public void onSubsectionHeaderSelectionStateChanged(Set<SubsectionHeader> selectedHeaders) {
        boolean isChecked = selectedHeaders.contains(mHeader);
        setChecked(isChecked);
    }
}

// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download.ui;

import android.graphics.drawable.Drawable;
import android.support.annotation.LayoutRes;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.TextView;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.download.ui.DownloadManagerUi.DownloadUiObserver;
import org.chromium.chrome.browser.widget.TintedDrawable;

/** An adapter that allows selecting an item from a dropdown spinner. */
class FilterAdapter
        extends BaseAdapter implements AdapterView.OnItemSelectedListener, DownloadUiObserver {
    private DownloadManagerUi mManagerUi;

    @Override
    public int getCount() {
        return DownloadFilter.getFilterCount();
    }

    @Override
    public Object getItem(int position) {
        return DownloadFilter.FILTER_LIST[position];
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        TextView labelView =
                getTextViewFromResource(convertView, R.layout.download_manager_spinner_drop_down);
        labelView.setText(DownloadFilter.getStringIdForFilter(position));
        int iconId = DownloadFilter.getDrawableForFilter(position);
        Drawable iconDrawable = TintedDrawable.constructTintedDrawable(
                mManagerUi.getActivity().getResources(), iconId, R.color.descriptive_text_color);
        labelView.setCompoundDrawablesWithIntrinsicBounds(iconDrawable, null, null, null);

        return labelView;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        TextView labelView =
                getTextViewFromResource(convertView, R.layout.download_manager_spinner);
        labelView.setText(position == 0 ? R.string.menu_downloads
                                        : DownloadFilter.getStringIdForFilter(position));
        return labelView;
    }

    private TextView getTextViewFromResource(View convertView, @LayoutRes int resId) {
        TextView labelView = null;
        if (convertView instanceof TextView) {
            labelView = (TextView) convertView;
        } else {
            labelView =
                    (TextView) LayoutInflater.from(mManagerUi.getActivity()).inflate(resId, null);
        }

        return labelView;
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        mManagerUi.onFilterChanged(position);
    }

    public void initialize(DownloadManagerUi manager) {
        mManagerUi = manager;
    }

    @Override
    public void onFilterChanged(int filter) {}

    @Override
    public void onManagerDestroyed() {
        mManagerUi = null;
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {}
}

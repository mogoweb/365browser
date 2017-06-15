// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.contextmenu;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.Space;
import android.widget.TextView;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.share.ShareHelper;

import java.util.List;

/**
 * Takes a list of {@link ContextMenuItem} and puts them in an adapter meant to be used within a
 * list view.
 */
class TabularContextMenuListAdapter extends BaseAdapter {
    private final List<ContextMenuItem> mMenuItems;
    private final Activity mActivity;
    private final Runnable mOnDirectShare;

    /**
     * Adapter for the tabular context menu UI
     * @param menuItems The list of items to display in the view.
     * @param activity Used to inflate the layout.
     */
    TabularContextMenuListAdapter(
            List<ContextMenuItem> menuItems, Activity activity, Runnable onDirectShare) {
        mMenuItems = menuItems;
        mActivity = activity;
        mOnDirectShare = onDirectShare;
    }

    @Override
    public int getCount() {
        return mMenuItems.size();
    }

    @Override
    public Object getItem(int position) {
        return mMenuItems.get(position);
    }

    @Override
    public long getItemId(int position) {
        return mMenuItems.get(position).getMenuId();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ContextMenuItem menuItem = mMenuItems.get(position);
        ViewHolderItem viewHolder;

        if (convertView == null) {
            LayoutInflater inflater = LayoutInflater.from(mActivity);
            convertView = inflater.inflate(R.layout.tabular_context_menu_row, null);

            viewHolder = new ViewHolderItem();
            viewHolder.mIcon = (ImageView) convertView.findViewById(R.id.context_menu_icon);
            viewHolder.mText = (TextView) convertView.findViewById(R.id.context_text);
            viewHolder.mShareIcon =
                    (ImageView) convertView.findViewById(R.id.context_menu_share_icon);
            viewHolder.mRightPadding =
                    (Space) convertView.findViewById(R.id.context_menu_right_padding);

            convertView.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolderItem) convertView.getTag();
        }

        viewHolder.mText.setText(menuItem.getTitle(mActivity));
        Drawable icon = menuItem.getDrawable(mActivity);
        viewHolder.mIcon.setImageDrawable(icon);
        viewHolder.mIcon.setVisibility(icon != null ? View.VISIBLE : View.INVISIBLE);

        if (menuItem == ChromeContextMenuItem.SHARE_IMAGE) {
            Intent shareIntent = ShareHelper.getShareImageIntent(null);
            final Pair<Drawable, CharSequence> shareInfo =
                    ShareHelper.getShareableIconAndName(mActivity, shareIntent);
            if (shareInfo.first != null) {
                viewHolder.mShareIcon.setImageDrawable(shareInfo.first);
                viewHolder.mShareIcon.setVisibility(View.VISIBLE);
                viewHolder.mShareIcon.setContentDescription(mActivity.getString(
                        R.string.accessibility_menu_share_via, shareInfo.second));
                viewHolder.mShareIcon.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        mOnDirectShare.run();
                    }
                });
                viewHolder.mRightPadding.setVisibility(View.GONE);
            }
        } else {
            viewHolder.mShareIcon.setVisibility(View.GONE);
            viewHolder.mRightPadding.setVisibility(View.VISIBLE);
        }

        return convertView;
    }

    private static class ViewHolderItem {
        ImageView mIcon;
        TextView mText;
        ImageView mShareIcon;
        Space mRightPadding;
    }
}

// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.annotation.SuppressLint;
import android.graphics.drawable.Drawable;
import android.view.Menu;
import android.view.MenuItem;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * MenuDescriptor contains the information on how a menu needs to be changed relative to
 * its initial (inflated) state. It can be used to modify a menu, it can also be compared
 * to MenuDescriptor to figure out whether a menu needs to be changed.
 */
@SuppressLint("UseSparseArrays")
public class MenuDescriptor {
    /**
     * ItemDescriptor contains the information about delayed menu.add() action.
     */
    private static class ItemDescriptor {
        public int mItemId;
        public int mGroupId;
        public int mOrder;
        public CharSequence mTitle;
        public Drawable mIcon;

        public ItemDescriptor(
                int groupId, int itemId, int order, CharSequence title, Drawable icon) {
            mItemId = itemId;
            mGroupId = groupId;
            mOrder = order;
            mTitle = title;
            mIcon = icon;
        }

        public boolean equals(Object obj) {
            if (obj == null || !(obj instanceof ItemDescriptor)) return false;
            ItemDescriptor rhs = (ItemDescriptor) obj;
            return mItemId == rhs.mItemId && mGroupId == rhs.mGroupId && mOrder == rhs.mOrder
                    && isTitleEqual(mTitle, rhs.mTitle);
        }

        public int hashCode() {
            return mItemId;
        }

        private static boolean isTitleEqual(CharSequence a, CharSequence b) {
            if (a == null && b == null) return true;
            if (a == null || b == null) return false;
            return a.toString().contentEquals(b);
        }
    }

    private Map<Integer, ItemDescriptor> mAdded = new HashMap<Integer, ItemDescriptor>();
    private Set<Integer> mRemoved = new HashSet<Integer>();

    public MenuDescriptor() {}

    public void addItem(int groupId, int itemId, int order, CharSequence title, Drawable icon) {
        mAdded.put(itemId, new ItemDescriptor(groupId, itemId, order, title, icon));
    }

    public void removeItem(int itemId) {
        mRemoved.add(itemId);
    }

    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof MenuDescriptor)) return false;
        MenuDescriptor rhs = (MenuDescriptor) obj;
        return mRemoved.equals(rhs.mRemoved) && mAdded.equals(rhs.mAdded);
    }

    public int hashCode() {
        return 1;
    }

    public void apply(Menu menu) {
        for (int id : mRemoved) {
            menu.removeItem(id);
        }

        for (Map.Entry<Integer, ItemDescriptor> entry : mAdded.entrySet()) {
            ItemDescriptor descr = entry.getValue();
            MenuItem item = menu.add(descr.mGroupId, descr.mItemId, descr.mOrder, descr.mTitle);
            if (descr.mIcon != null) item.setIcon(descr.mIcon);
        }
    }
}

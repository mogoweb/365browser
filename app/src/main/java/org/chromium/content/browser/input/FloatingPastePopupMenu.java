// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.input;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import org.chromium.content.R;
import org.chromium.content.browser.SelectionPopupController;
import org.chromium.ui.base.DeviceFormFactor;

/**
 * Paste popup implementation based on floating ActionModes.
 */
@TargetApi(Build.VERSION_CODES.M)
public class FloatingPastePopupMenu implements PastePopupMenu {
    private final View mParent;
    private final PastePopupMenuDelegate mDelegate;
    private final Context mContext;

    private ActionMode mActionMode;
    private Rect mSelectionRect;

    public FloatingPastePopupMenu(Context context, View parent, PastePopupMenuDelegate delegate) {
        assert Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;

        mParent = parent;
        mDelegate = delegate;
        mContext = context;
    }

    @Override
    public void show(Rect selectionRect) {
        mSelectionRect = selectionRect;
        if (mActionMode != null) {
            mActionMode.invalidateContentRect();
            return;
        }

        ensureActionMode();
    }

    @Override
    public void hide() {
        if (mActionMode != null) {
            mActionMode.finish();
            mActionMode = null;
        }
    }

    private void ensureActionMode() {
        if (mActionMode != null) return;

        ActionMode actionMode = mParent.startActionMode(
                new ActionModeCallback(), ActionMode.TYPE_FLOATING);
        if (actionMode != null) {
            // crbug.com/651706
            LGEmailActionModeWorkaround.runIfNecessary(mContext, actionMode);

            assert actionMode.getType() == ActionMode.TYPE_FLOATING;
            mActionMode = actionMode;
        }
    }

    private class ActionModeCallback extends ActionMode.Callback2 {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            createPasteMenu(mode, menu);
            return true;
        }

        private void createPasteMenu(ActionMode mode, Menu menu) {
            mode.setTitle(DeviceFormFactor.isTablet()
                            ? mContext.getString(R.string.actionbar_textselection_title)
                            : null);
            mode.setSubtitle(null);
            SelectionPopupController.initializeMenu(mContext, mode, menu);
            if (!mDelegate.canPaste()) menu.removeItem(R.id.select_action_menu_paste);
            if (!mDelegate.canSelectAll()) menu.removeItem(R.id.select_action_menu_select_all);
            if (!mDelegate.canPasteAsPlainText()) {
                menu.removeItem(R.id.select_action_menu_paste_as_plain_text);
            }
            // TODO(ctzsm): Remove runtime title set after O SDK rolls.
            MenuItem item = menu.findItem(R.id.select_action_menu_paste_as_plain_text);
            if (item != null) {
                item.setTitle(mContext.getResources().getIdentifier(
                        "paste_as_plain_text", "string", "android"));
            }
            menu.removeItem(R.id.select_action_menu_cut);
            menu.removeItem(R.id.select_action_menu_copy);
            menu.removeItem(R.id.select_action_menu_share);
            menu.removeItem(R.id.select_action_menu_web_search);
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            int id = item.getItemId();
            if (id == R.id.select_action_menu_paste) {
                mDelegate.paste();
                mode.finish();
            }
            if (id == R.id.select_action_menu_paste_as_plain_text) {
                mDelegate.pasteAsPlainText();
                mode.finish();
            }
            if (id == R.id.select_action_menu_select_all) {
                mDelegate.selectAll();
                mode.finish();
            }
            return true;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            mActionMode = null;
        }

        @Override
        public void onGetContentRect(ActionMode mode, View view, Rect outRect) {
            outRect.set(mSelectionRect);
        }
    };
}

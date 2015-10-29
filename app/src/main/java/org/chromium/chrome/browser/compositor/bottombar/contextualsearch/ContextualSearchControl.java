// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.bottombar.contextualsearch;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewParent;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.chromium.chrome.R;
import org.chromium.ui.resources.dynamics.ViewResourceAdapter;

/**
 * Root ControlContainer for the Contextual Search panel.
 * Handles user interaction with the Contextual Search control.
 * Based on ToolbarControlContainer.
 */
public class ContextualSearchControl extends LinearLayout {
    /**
     * Object Replacement Character that is used in place of HTML objects that cannot be represented
     * as text (e.g. images). Contextual search panel should not be displaying such characters as
     * they get shown as [obj] character.
     */
    private static final String OBJ_CHARACTER = "\uFFFC";

    private static final float RESOLVED_SEARCH_TERM_SIDE_PADDING_DP = 40.f;
    private final int mSidePaddingPx;

    private ViewResourceAdapter mResourceAdapter;

    private TextView mSelectionText;
    private TextView mStartText;
    private TextView mEndText;

    private boolean mIsDirty = false;

    /**
     * Constructs a new control container.
     * <p>
     * This constructor is used when inflating from XML.
     *
     * @param context The context used to build this view.
     * @param attrs The attributes used to determine how to construct this view.
     */
    public ContextualSearchControl(Context context, AttributeSet attrs) {
        super(context, attrs);

        final float pxToDp = 1.0f / context.getResources().getDisplayMetrics().density;
        mSidePaddingPx = Math.round(RESOLVED_SEARCH_TERM_SIDE_PADDING_DP / pxToDp);
    }

    /**
     * @return The {@link ViewResourceAdapter} that exposes this {@link View} as a CC resource.
     */
    public ViewResourceAdapter getResourceAdapter() {
        return mResourceAdapter;
    }

    @Override
    public void onFinishInflate() {
        super.onFinishInflate();

        mSelectionText = (TextView) findViewById(R.id.main_text);
        mStartText = (TextView) findViewById(R.id.surrounding_text_start);
        mEndText = (TextView) findViewById(R.id.surrounding_text_end);

        mResourceAdapter = new ViewResourceAdapter(findViewById(R.id.contextual_search_view));
    }

    @Override
    public ViewParent invalidateChildInParent(int[] location, Rect dirty) {
        ViewParent parent = super.invalidateChildInParent(location, dirty);
        // TODO(pedrosimonetti): ViewGroup#invalidateChildInParent() is being called multiple
        // times with different rectangles (for each of the individual repaints it seems). This
        // means in order to invalidate it only once we need to keep track of the dirty state,
        // and call ViewResourceAdapter#invalidate() only once per change of state, passing
        // "null" to indicate that the whole area should be invalidated. This can be deleted
        // if we stop relying on an Android View to render our Search Bar Text.
        if (mIsDirty && mResourceAdapter != null) {
            mIsDirty = false;
            mResourceAdapter.invalidate(null);
        }
        return parent;
    }

    /**
     * Sets the search context to display in the control.
     * @param selection The portion of the context that represents the user's selection.
     * @param start The portion of the context from its start to the selection.
     * @param end The portion of the context the selection to its end.
     */
    public void setSearchContext(String selection, String start, String end) {
        mSelectionText.setPadding(0, 0, 0, 0);
        mSelectionText.setText(sanitizeText(selection));
        mStartText.setText(sanitizeText(start));
        mEndText.setText(sanitizeText(end));
        mIsDirty = true;
    }

    /**
     * Sets the resolved search search to display in the control.
     * @param searchTerm The string that represents the resolved search term.
     */
    public void setCentralText(String searchTerm) {
        mSelectionText.setPadding(mSidePaddingPx, 0, mSidePaddingPx, 0);
        mSelectionText.setText(searchTerm);
        mStartText.setText("");
        mEndText.setText("");
        mIsDirty = true;
    }

    private String sanitizeText(String text) {
        if (text == null) return null;
        return text.replace(OBJ_CHARACTER, " ");
    }
}

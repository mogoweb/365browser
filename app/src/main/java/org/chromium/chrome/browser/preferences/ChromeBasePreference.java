// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences;

import android.content.Context;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import org.chromium.chrome.R;

/**
 * A preference that supports some Chrome-specific customizations:
 *
 * 1. This preference supports being managed. If this preference is managed (as determined by its
 *    ManagedPreferenceDelegate), it updates its appearance and behavior appropriately: shows an
 *    enterprise icon, disables clicks, etc.
 *
 * 2. This preference can have a multiline title.
 */
public class ChromeBasePreference extends Preference {
    private ManagedPreferenceDelegate mManagedPrefDelegate;
    private boolean mUseReducedPadding;

    /**
     * Constructor for use in Java.
     */
    public ChromeBasePreference(Context context) {
        super(context);
    }

    /**
     * Constructor for inflating from XML.
     */
    public ChromeBasePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * Sets the ManagedPreferenceDelegate which will determine whether this preference is managed.
     */
    public void setManagedPreferenceDelegate(ManagedPreferenceDelegate delegate) {
        mManagedPrefDelegate = delegate;
        if (mManagedPrefDelegate != null) mManagedPrefDelegate.initPreference(this);
    }

    public void setUseReducedPadding(boolean useReducedPadding) {
        this.mUseReducedPadding = useReducedPadding;
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        ((TextView) view.findViewById(android.R.id.title)).setSingleLine(false);
        if (mManagedPrefDelegate != null) mManagedPrefDelegate.onBindViewToPreference(this, view);
        if (mUseReducedPadding) reducePadding(view);
    }

    @Override
    protected void onClick() {
        if (mManagedPrefDelegate != null && mManagedPrefDelegate.onClickPreference(this)) return;
        super.onClick();
    }

    private void reducePadding(View view) {
        View innerLayout = (View) view.findViewById(android.R.id.title).getParent();

        if (getIcon() != null) {
            // When there is an icon, it is bigger than the text (account name here) and it already
            // has the appropriate padding. So we let the icon dictate the top and bottom padding
            // for the preference and just let the text get centered in that space.
            // TODO(dgn): would look ugly in account names that are 2+ lines, but unlikely to occur.
            innerLayout.setPadding(
                    innerLayout.getPaddingLeft(), 0, innerLayout.getPaddingRight(), 0);
            return;
        }

        int topPaddingPx = getContext().getResources().getDimensionPixelOffset(
                R.dimen.pref_child_account_reduced_padding);
        innerLayout.setPadding(innerLayout.getPaddingLeft(), topPaddingPx,
                innerLayout.getPaddingRight(), innerLayout.getPaddingBottom());
    }
}

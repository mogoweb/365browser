// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.infobar.translate;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.design.widget.TabLayout;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;

import org.chromium.chrome.R;

/**
 * TabLayout shown in the TranslateCompactInfoBar.
 */
public class TranslateTabLayout extends TabLayout {
    // The tab in which a spinning progress bar is showing.
    private Tab mTabShowingProgressBar;

    /**
     * Constructor for inflating from XML.
     */
    public TranslateTabLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * Add new Tabs with title strings.
     * @param titles Titles of the tabs to be added.
     */
    public void addTabs(CharSequence... titles) {
        for (CharSequence title : titles) {
            addTabWithTitle(title);
        }
    }

    /**
     * Add a new Tab with the title string.
     * @param tabTitle Title string of the new tab.
     */
    public void addTabWithTitle(CharSequence tabTitle) {
        TranslateTabContent tabContent =
                (TranslateTabContent) LayoutInflater.from(getContext())
                        .inflate(R.layout.infobar_translate_tab_content, this, false);
        // Set text color using tabLayout's ColorStateList.  So that the title text will change
        // color when selected and unselected.
        tabContent.setTextColor(getTabTextColors());
        tabContent.setText(tabTitle);

        Tab tab = newTab();
        tab.setCustomView(tabContent);
        tab.setContentDescription(tabTitle);
        super.addTab(tab);
    }

    /**
     * Replace the title string of a tab.
     * @param tabPos   The position of the tab to modify.
     * @param tabTitle The new title string.
     */
    public void replaceTabTitle(int tabPos, CharSequence tabTitle) {
        if (tabPos < 0 || tabPos >= getTabCount()) {
            return;
        }
        Tab tab = getTabAt(tabPos);
        ((TranslateTabContent) tab.getCustomView()).setText(tabTitle);
        tab.setContentDescription(tabTitle);
    }

    /**
     * Show the spinning progress bar on a specified tab.
     * @param tabPos The position of the tab to show the progress bar.
     */
    public void showProgressBarOnTab(int tabPos) {
        if (tabPos < 0 || tabPos >= getTabCount() || mTabShowingProgressBar != null) {
            return;
        }
        mTabShowingProgressBar = getTabAt(tabPos);

        // TODO(martiw) See if we need to setContentDescription as "Translating" here.

        if (tabIsSupported(mTabShowingProgressBar)) {
            ((TranslateTabContent) mTabShowingProgressBar.getCustomView()).showProgressBar();
        }
    }

    /**
     * Hide the spinning progress bar in the tabs.
     */
    public void hideProgressBar() {
        if (mTabShowingProgressBar == null) return;

        if (tabIsSupported(mTabShowingProgressBar)) {
            ((TranslateTabContent) mTabShowingProgressBar.getCustomView()).hideProgressBar();
        }

        mTabShowingProgressBar = null;
    }

    // Overrided to block children's touch event when showing progress bar.
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // Allow touches to propagate to children only if the layout can be interacted with.
        if (mTabShowingProgressBar != null) {
            return true;
        }
        return super.onInterceptTouchEvent(ev);
    }

    /** Check if the tab is supported in TranslateTabLayout. */
    private boolean tabIsSupported(Tab tab) {
        return (tab.getCustomView() instanceof TranslateTabContent);
    }

    // Overrided to make sure only supported Tabs can be added.
    @Override
    public void addTab(@NonNull Tab tab, int position, boolean setSelected) {
        if (!tabIsSupported(tab)) {
            throw new IllegalArgumentException();
        }
        super.addTab(tab, position, setSelected);
    }

    // Overrided to make sure only supported Tabs can be added.
    @Override
    public void addTab(@NonNull Tab tab, boolean setSelected) {
        if (!tabIsSupported(tab)) {
            throw new IllegalArgumentException();
        }
        super.addTab(tab, setSelected);
    }
}

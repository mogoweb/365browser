// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.privacy;

import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.text.TextUtilsCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.preferences.Preferences;

import java.util.Locale;

/**
 * Fragment with a {@link TabLayout} containing a basic and an advanced version of the CBD dialog.
 */
public class ClearBrowsingDataTabsFragment extends Fragment {
    public static final int CBD_TAB_COUNT = 2;

    public ClearBrowsingDataTabsFragment() {
        // TODO(dullweber): Remove this migration after after three milestones (probably M61)
        PrefServiceBridge.getInstance().migrateBrowsingDataPreferences();
    }

    /**
     * @return Returns whether the CBD dialog with tabs is enabled.
     */
    public static boolean isFeatureEnabled() {
        return ChromeFeatureList.isEnabled(ChromeFeatureList.TABS_IN_CBD);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    /*
     * RTL is broken for ViewPager: https://code.google.com/p/android/issues/detail?id=56831
     * This class works around this issue by inserting the tabs in inverse order if RTL is active.
     * The TabLayout needs to be set to LTR for this to work.
     * TODO(dullweber): Extract the RTL code into a wrapper class if other places in Chromium need
     * it as well.
     */
    private static int adjustIndexForDirectionality(int index) {
        if (TextUtilsCompat.getLayoutDirectionFromLocale(Locale.getDefault())
                == ViewCompat.LAYOUT_DIRECTION_RTL) {
            return CBD_TAB_COUNT - 1 - index;
        }
        return index;
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment.
        View view = inflater.inflate(R.layout.clear_browsing_data_tabs, container, false);

        // Get the ViewPager and set its PagerAdapter so that it can display items.
        ViewPager viewPager = (ViewPager) view.findViewById(R.id.clear_browsing_data_viewpager);
        viewPager.setAdapter(
                new ClearBrowsingDataPagerAdapter(getFragmentManager(), getActivity()));

        // Give the TabLayout the ViewPager.
        TabLayout tabLayout = (TabLayout) view.findViewById(R.id.clear_browsing_data_tabs);
        tabLayout.setupWithViewPager(viewPager);
        tabLayout.addOnTabSelectedListener(new TabSelectListener());
        int tabIndex = adjustIndexForDirectionality(
                PrefServiceBridge.getInstance().getLastSelectedClearBrowsingDataTab());
        TabLayout.Tab tab = tabLayout.getTabAt(tabIndex);
        if (tab != null) {
            tab.select();
        }

        // Remove elevation to avoid shadow between title and tabs.
        Preferences activity = (Preferences) getActivity();
        activity.getSupportActionBar().setElevation(0.0f);

        return view;
    }

    private static class ClearBrowsingDataPagerAdapter extends FragmentPagerAdapter {
        private final Context mContext;

        ClearBrowsingDataPagerAdapter(FragmentManager fm, Context context) {
            super(fm);
            mContext = context;
        }

        @Override
        public int getCount() {
            return CBD_TAB_COUNT;
        }

        @Override
        public Fragment getItem(int position) {
            position = adjustIndexForDirectionality(position);
            switch (position) {
                case 0:
                    return new ClearBrowsingDataPreferencesBasic();
                case 1:
                    return new ClearBrowsingDataPreferencesAdvanced();
                default:
                    throw new RuntimeException("invalid position: " + position);
            }
        }

        @Override
        public CharSequence getPageTitle(int position) {
            position = adjustIndexForDirectionality(position);
            switch (position) {
                case 0:
                    return mContext.getString(R.string.clear_browsing_data_basic_tab_title);
                case 1:
                    return mContext.getString(R.string.prefs_section_advanced);
                default:
                    throw new RuntimeException("invalid position: " + position);
            }
        }
    }

    private static class TabSelectListener implements TabLayout.OnTabSelectedListener {
        @Override
        public void onTabSelected(TabLayout.Tab tab) {
            int tabIndex = adjustIndexForDirectionality(tab.getPosition());
            PrefServiceBridge.getInstance().setLastSelectedClearBrowsingDataTab(tabIndex);
        }

        @Override
        public void onTabUnselected(TabLayout.Tab tab) {}

        @Override
        public void onTabReselected(TabLayout.Tab tab) {}
    }
}

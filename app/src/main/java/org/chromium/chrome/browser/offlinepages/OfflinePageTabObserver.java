// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.offlinepages;

import android.content.Context;

import org.chromium.base.Log;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.snackbar.SnackbarManager;
import org.chromium.chrome.browser.snackbar.SnackbarManager.SnackbarController;
import org.chromium.chrome.browser.tab.EmptyTabObserver;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabObserver;
import org.chromium.net.NetworkChangeNotifier;

import java.util.HashSet;
import java.util.Set;

/**
 * A class that observes events for a tab which has an associated offline page.  This will be
 * created when needed (for instance, we want to show a snackbar when the tab is shown or we want
 * to show a snackbar if the device connects).  It will be removed when the user navigates away
 * from the offline tab.
 */
public class OfflinePageTabObserver
        extends EmptyTabObserver implements NetworkChangeNotifier.ConnectionTypeObserver {
    private static final String TAG = "OfflinePageTO";
    private Context mContext;
    private SnackbarManager mSnackbarManager;
    private SnackbarController mSnackbarController;
    private final Set<Integer> mObservedTabs = new HashSet<Integer>();
    private boolean mWasSnackbarShown;
    private Tab mCurrentTab;
    private boolean mIsObservingNetworkChanges;

    private static TabHelper sTabHelper;
    private static OfflinePageTabObserver sInstance;

    /**
     * Helper class that allows mocking access to the tab methods, without having to have a tab,
     * which makes things testable.
     */
    static class TabHelper {
        public int getTabId(Tab tab) {
            if (tab == null) return Tab.INVALID_TAB_ID;
            return tab.getId();
        }

        public boolean isOfflinePage(Tab tab) {
            return tab != null && tab.isOfflinePage();
        }

        public boolean isTabShowing(Tab tab) {
            return tab != null && !tab.isFrozen() && !tab.isHidden();
        }

        public void addObserver(Tab tab, TabObserver observer) {
            if (tab != null && observer != null) {
                tab.addObserver(observer);
            }
        }

        public void removeObserver(Tab tab, TabObserver observer) {
            if (tab != null && observer != null) {
                tab.removeObserver(observer);
            }
        }
    }

    static void setTabHelperForTesting(TabHelper tabHelper) {
        sTabHelper = tabHelper;
    }

    static TabHelper getTabHelper() {
        if (sTabHelper == null) {
            sTabHelper = new TabHelper();
        }
        return sTabHelper;
    }

    static void init(Context context, SnackbarManager manager, SnackbarController controller) {
        sInstance = new OfflinePageTabObserver(context, manager, controller);
    }

    static OfflinePageTabObserver getInstance() {
        return sInstance;
    }

    @VisibleForTesting
    static void setInstanceForTesting(OfflinePageTabObserver instance) {
        sInstance = instance;
    }

    /**
     * Create and attach a tab observer if we don't already have one, otherwise update it.
     * @param tab The tab we are adding an observer for.
     */
    public static void addObserverForTab(Tab tab) {
        assert getInstance() != null;
        getInstance().startObservingTab(tab);
    }

    /**
     * Builds a new OfflinePageTabObserver.
     * @param context Android context.
     * @param snackbarManager The snackbar manager to show and dismiss snackbars.
     * @param snackbarController Controller to use to build the snackbar.
     */
    OfflinePageTabObserver(Context context, SnackbarManager snackbarManager,
            SnackbarController snackbarController) {
        mContext = context;
        mSnackbarManager = snackbarManager;
        mSnackbarController = snackbarController;

        // The first time observer is created snackbar has net yet been shown.
        mWasSnackbarShown = false;
        mIsObservingNetworkChanges = false;
    }

    // Methods from EmptyTabObserver
    @Override
    public void onShown(Tab tab) {
        if (!getTabHelper().isOfflinePage(tab)) return;

        // Whenever we get a new tab shown, we will give a reload snackbar a chance to be shown,
        // therefor the state is reset to false. Also the currently shown tab is captured.
        mWasSnackbarShown = false;
        mCurrentTab = tab;
        if (isConnected() && !wasSnackbarShown()) {
            Log.d(TAG, "onShown, showing 'delayed' snackbar");
            showReloadSnackbar();
            // TODO(fgorski): Move the variable assignment to the method above, once
            // OfflinePageUtils can be mocked.
            mWasSnackbarShown = true;
        }
    }

    @Override
    public void onHidden(Tab hiddenTab) {
        mWasSnackbarShown = false;
        mCurrentTab = null;
        // In case any snackbars are showing, dismiss them before we switch tabs.
        mSnackbarManager.dismissSnackbars(mSnackbarController);
    }

    @Override
    public void onDestroyed(Tab tab) {
        Log.d(TAG, "onDestroyed");
        stopObservingTab(tab);
    }

    @Override
    public void onUrlUpdated(Tab tab) {
        Log.d(TAG, "onUrlUpdated");
        if (!getTabHelper().isOfflinePage(tab)) {
            stopObservingTab(tab);
        }
        // In case any snackbars are showing, dismiss them before we navigate away.
        mSnackbarManager.dismissSnackbars(mSnackbarController);
    }

    void startObservingTab(Tab tab) {
        // If the tab does not contain an offline page, we don't care to track it.
        if (!getTabHelper().isOfflinePage(tab)) return;

        // TODO(fgorski): check for one of 2 things:
        // 1. can we presume that we start observing the current tab, always
        // 2. will onShown happen right after and then we don't need the next 2 lines:
        mCurrentTab = tab;
        mWasSnackbarShown = false;

        // If we are not observing the tab yet, let's.
        if (!isObservingTab(tab)) {
            int tabId = getTabHelper().getTabId(tab);
            mObservedTabs.add(tabId);
            getTabHelper().addObserver(tab, this);
        }
        if (!isObservingNetworkChanges()) {
            startObservingNetworkChanges();
            mIsObservingNetworkChanges = true;
        }
        if (getTabHelper().isTabShowing(tab) && isConnected()) {
            showReloadSnackbar();
            // TODO(fgorski): Move the variable assignment to the method above, once
            // OfflinePageUtils can be mocked.
            mWasSnackbarShown = true;
        }
    }

    /**
     * Removes the observer for a tab with the specified tabId.
     * @param tabId ID of a tab that was observed.
     */
    void stopObservingTab(Tab tab) {
        if (isObservingTab(tab)) {
            int tabId = getTabHelper().getTabId(tab);
            mObservedTabs.remove(tabId);
            getTabHelper().removeObserver(tab, this);
        }
        if (mObservedTabs.isEmpty() && isObservingNetworkChanges()) {
            stopObservingNetworkChanges();
            mIsObservingNetworkChanges = false;
        }
        mWasSnackbarShown = false;
        mCurrentTab = null;
    }

    // Methods from ConnectionTypeObserver.
    @Override
    public void onConnectionTypeChanged(int connectionType) {
        Log.d(TAG, "Got connectivity event, connectionType: " + connectionType + ", controller "
                        + mSnackbarController);

        Log.d(TAG, "Connection changed, connected " + isConnected());
        // Shows or hides the snackbar as needed.  This also adds some hysterisis - if we keep
        // connecting and disconnecting, we don't want to flash the snackbar.  It will timeout after
        // several seconds.
        if (isConnected() && getTabHelper().isTabShowing(mCurrentTab) && !wasSnackbarShown()) {
            Log.d(TAG, "Connection became available, show reload snackbar.");
            showReloadSnackbar();
            // TODO(fgorski): Move the variable assignment to the method above, once
            // OfflinePageUtils can be mocked.
            mWasSnackbarShown = true;
        }
    }

    @VisibleForTesting
    boolean isObservingTab(Tab tab) {
        return mObservedTabs.contains(getTabHelper().getTabId(tab));
    }

    @VisibleForTesting
    boolean isObservingNetworkChanges() {
        return mIsObservingNetworkChanges;
    }

    @VisibleForTesting
    boolean isConnected() {
        return OfflinePageUtils.isConnected();
    }

    @VisibleForTesting
    boolean wasSnackbarShown() {
        return mWasSnackbarShown;
    }

    @VisibleForTesting
    void showReloadSnackbar() {
        OfflinePageUtils.showReloadSnackbar(mContext, mSnackbarManager, mSnackbarController,
                getTabHelper().getTabId(mCurrentTab));
    }

    @VisibleForTesting
    void startObservingNetworkChanges() {
        NetworkChangeNotifier.addConnectionTypeObserver(this);
    }

    @VisibleForTesting
    void stopObservingNetworkChanges() {
        NetworkChangeNotifier.removeConnectionTypeObserver(this);
    }
}

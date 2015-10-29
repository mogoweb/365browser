// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.webapps;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;

import org.chromium.base.StreamUtil;
import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.IntentHandler;
import org.chromium.chrome.browser.ShortcutHelper;
import org.chromium.chrome.browser.TabState;
import org.chromium.chrome.browser.UrlUtilities;
import org.chromium.chrome.browser.compositor.layouts.content.TabContentManager;
import org.chromium.chrome.browser.contextmenu.ContextMenuHelper;
import org.chromium.chrome.browser.contextmenu.ContextMenuParams;
import org.chromium.chrome.browser.contextmenu.ContextMenuPopulator;
import org.chromium.chrome.browser.tab.ChromeTab;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabUma.TabCreationState;
import org.chromium.chrome.browser.tabmodel.TabModel.TabLaunchType;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.content_public.browser.WebContents;
import org.chromium.content_public.browser.WebContentsObserver;
import org.chromium.ui.base.Clipboard;
import org.chromium.ui.base.PageTransition;
import org.chromium.ui.base.WindowAndroid;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;

/**
 * A tab that will be used for FullScreenActivity. See {@link FullScreenActivity} for more.
 */
@SuppressFBWarnings("URF_UNREAD_FIELD")
public class FullScreenActivityTab extends ChromeTab {
    private static final String TAG = "FullScreenActivityTab";

    /**
     * A delegate to determine top controls visibility.
     */
    public interface TopControlsVisibilityDelegate {
        /**
         * Determines whether top controls should be shown.
         *
         * @param uri The URI to display.
         * @param securityLevel Security level of the Tab.
         * @return Whether the URL bar should be visible or not.
         */
        boolean shouldShowTopControls(String uri, int securityLevel);
    }

    static final String BUNDLE_TAB_ID = "tabId";
    static final String BUNDLE_TAB_URL = "tabUrl";

    private WebContentsObserver mObserver;
    private TopControlsVisibilityDelegate mTopControlsVisibilityDelegate;

    private FullScreenActivityTab(ChromeActivity activity, WindowAndroid window,
            TopControlsVisibilityDelegate topControlsVisibilityDelegate) {
        super(INVALID_TAB_ID, activity, false, window, TabLaunchType.FROM_MENU_OR_OVERVIEW,
                INVALID_TAB_ID, null, null);
        initializeFullScreenActivityTab(
                activity.getTabContentManager(), false, topControlsVisibilityDelegate);
    }

    private FullScreenActivityTab(int id, ChromeActivity activity, WindowAndroid window,
            TabState state, TopControlsVisibilityDelegate topControlsVisibilityDelegate) {
        super(id, activity, false, window, TabLaunchType.FROM_RESTORE, Tab.INVALID_TAB_ID,
                TabCreationState.FROZEN_ON_RESTORE, state);
        initializeFullScreenActivityTab(
                activity.getTabContentManager(), true, topControlsVisibilityDelegate);
    }

    private void initializeFullScreenActivityTab(TabContentManager tabContentManager,
            boolean unfreeze, TopControlsVisibilityDelegate topControlsVisibilityDelegate) {
        initialize(null, tabContentManager, false);
        if (unfreeze) unfreezeContents();
        mObserver = createWebContentsObserver();
        mTopControlsVisibilityDelegate = topControlsVisibilityDelegate;
    }

    /**
     * Saves the state of the tab out to the {@link Bundle}.
     */
    void saveInstanceState(Bundle outState) {
        outState.putInt(BUNDLE_TAB_ID, getId());
        outState.putString(BUNDLE_TAB_URL, getUrl());
    }

    /**
     * @return WebContentsObserver that watches for changes.
     */
    private WebContentsObserver createWebContentsObserver() {
        return new WebContentsObserver(getWebContents()) {
            @Override
            public void didCommitProvisionalLoadForFrame(
                    long frameId, boolean isMainFrame, String url, int transitionType) {
                if (isMainFrame) {
                    // Notify the renderer to permanently hide the top controls since they do
                    // not apply to fullscreen content views.
                    updateTopControlsState(getTopControlsStateConstraints(),
                            getTopControlsStateConstraints(), true);
                }
            }
        };
    }

    @Override
    protected void initContentViewCore(WebContents webContents) {
        super.initContentViewCore(webContents);
        getContentViewCore().setFullscreenRequiredForOrientationLock(false);
    }

    /**
     * Loads the given {@code url}.
     * @param url URL to load.
     */
    public void loadUrl(String url) {
        loadUrl(new LoadUrlParams(url, PageTransition.AUTO_TOPLEVEL));
    }

    /**
     * Saves the tab data out to a file.
     */
    void saveState(File activityDirectory) {
        File tabFile = getTabFile(activityDirectory, getId());

        FileOutputStream foutput = null;
        try {
            foutput = new FileOutputStream(tabFile);
            TabState.saveState(foutput, getState(), false);
        } catch (FileNotFoundException exception) {
            Log.e(TAG, "Failed to save out tab state.", exception);
        } catch (IOException exception) {
            Log.e(TAG, "Failed to save out tab state.", exception);
        } finally {
            StreamUtil.closeQuietly(foutput);
        }
    }

    /**
     * @return {@link File} pointing at the tab state for this Activity.
     */
    private static File getTabFile(File activityDirectory, int tabId) {
        return new File(activityDirectory, TabState.getTabStateFilename(tabId, false));
    }

    /**
     * Creates the {@link FullScreenActivityTab} used by the FullScreenActivity.
     * If the {@code savedInstanceState} exists, then the user did not intentionally close the app
     * by swiping it away in the recent tasks list.  In that case, we try to restore the tab from
     * disk.
     * @param activity Activity that will own the Tab.
     * @param directory Directory associated with the Activity.  Null implies tab state isn't saved.
     * @param savedInstanceState Bundle saved out when the app was killed by Android.  May be null.
     * @param topControlsVisibilityDelegate Delegate to determine top controls visibility.
     * @return {@link FullScreenActivityTab} for the Activity.
     */
    public static FullScreenActivityTab create(ChromeActivity activity, WindowAndroid window,
            File directory, Bundle savedInstanceState,
            TopControlsVisibilityDelegate topControlsVisibilityDelegate) {
        FullScreenActivityTab tab = null;

        int tabId = Tab.INVALID_TAB_ID;
        String tabUrl = null;
        if (savedInstanceState != null) {
            tabId = savedInstanceState.getInt(BUNDLE_TAB_ID, INVALID_TAB_ID);
            tabUrl = savedInstanceState.getString(BUNDLE_TAB_URL);
        }

        if (tabId != Tab.INVALID_TAB_ID && tabUrl != null && directory != null) {
            FileInputStream stream = null;
            try {
                // Restore the tab.
                stream = new FileInputStream(getTabFile(directory, tabId));
                TabState tabState = TabState.readState(stream, false);
                tab = new FullScreenActivityTab(
                        tabId, activity, window, tabState, topControlsVisibilityDelegate);
            } catch (FileNotFoundException exception) {
                Log.e(TAG, "Failed to restore tab state.", exception);
            } catch (IOException exception) {
                Log.e(TAG, "Failed to restore tab state.", exception);
            } finally {
                StreamUtil.closeQuietly(stream);
            }
        }

        if (tab == null) {
            // Create a new tab.
            tab = new FullScreenActivityTab(activity, window, topControlsVisibilityDelegate);
        }

        return tab;
    }

    @Override
    protected ContextMenuPopulator createContextMenuPopulator() {
        return new ContextMenuPopulator() {
            private final Clipboard mClipboard;

            // public ContextMenuPopulator()
            {
                mClipboard = new Clipboard(getApplicationContext());
            }

            @Override
            public boolean shouldShowContextMenu(ContextMenuParams params) {
                return params != null && params.isAnchor();
            }

            @Override
            public boolean onItemSelected(ContextMenuHelper helper, ContextMenuParams params,
                    int itemId) {
                if (itemId == org.chromium.chrome.R.id.contextmenu_copy_link_address) {
                    String url = params.getUnfilteredLinkUrl();
                    mClipboard.setText(url, url);
                    return true;
                } else if (itemId == org.chromium.chrome.R.id.contextmenu_copy_link_text) {
                    String text = params.getLinkText();
                    mClipboard.setText(text, text);
                    return true;
                } else if (itemId == R.id.menu_id_open_in_chrome) {
                    // TODO(dfalcantara): Merge into the TabDelegate. (https://crbug.com/451453)
                    Intent chromeIntent =
                            new Intent(Intent.ACTION_VIEW, Uri.parse(params.getLinkUrl()));
                    chromeIntent.setPackage(getApplicationContext().getPackageName());
                    chromeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                    boolean activityStarted = false;
                    if (params.getPageUrl() != null) {
                        try {
                            URI pageUri = URI.create(params.getPageUrl());
                            if (UrlUtilities.isInternalScheme(pageUri)) {
                                IntentHandler.startChromeLauncherActivityForTrustedIntent(
                                        chromeIntent, getApplicationContext());
                                activityStarted = true;
                            }
                        } catch (IllegalArgumentException ex) {
                            // Ignore the exception for creating the URI and launch the intent
                            // without the trusted intent extras.
                        }
                    }

                    if (!activityStarted) {
                        getApplicationContext().startActivity(chromeIntent);
                        activityStarted = true;
                    }
                    return true;
                }

                return false;
            }

            @Override
            public void buildContextMenu(ContextMenu menu, Context context,
                    ContextMenuParams params) {
                menu.add(Menu.NONE, org.chromium.chrome.R.id.contextmenu_copy_link_address,
                        Menu.NONE, org.chromium.chrome.R.string.contextmenu_copy_link_address);

                String linkText = params.getLinkText();
                if (linkText != null) linkText = linkText.trim();

                if (!TextUtils.isEmpty(linkText)) {
                    menu.add(Menu.NONE, org.chromium.chrome.R.id.contextmenu_copy_link_text,
                            Menu.NONE, org.chromium.chrome.R.string.contextmenu_copy_link_text);
                }

                menu.add(Menu.NONE, R.id.menu_id_open_in_chrome, Menu.NONE,
                        R.string.menu_open_in_chrome);
            }
        };
    }

    @Override
    protected boolean isHidingTopControlsEnabled() {
        if (getFullscreenManager() == null) return true;
        if (getFullscreenManager().getPersistentFullscreenMode()) return true;
        if (mTopControlsVisibilityDelegate == null) return false;
        return !mTopControlsVisibilityDelegate.shouldShowTopControls(getUrl(), getSecurityLevel());
    }

    @Override
    public boolean isShowingTopControlsEnabled() {
        // On webapp activity and embedd content view activity, it's either hiding or showing.
        // Users cannot change the visibility state by sliding it in or out.
        return !isHidingTopControlsEnabled();
    }

    @Override
    protected FullScreenTabWebContentsDelegateAndroid createWebContentsDelegate() {
        return new FullScreenTabWebContentsDelegateAndroid();
    }

    private class FullScreenTabWebContentsDelegateAndroid
            extends TabChromeWebContentsDelegateAndroidImpl {
        @Override
        public void activateContents() {
            if (!(mActivity instanceof WebappActivity)) return;

            WebappInfo webappInfo = ((WebappActivity) mActivity).getWebappInfo();
            String url = webappInfo.uri().toString();

            // Create an Intent that will be fired toward the WebappLauncherActivity, which in turn
            // will fire an Intent to launch the correct WebappActivity.  On L+ this could probably
            // be changed to call AppTask.moveToFront(), but for backwards compatibility we relaunch
            // it the hard way.
            Intent intent = new Intent();
            intent.setAction(WebappLauncherActivity.ACTION_START_WEBAPP);
            intent.setPackage(mActivity.getPackageName());
            webappInfo.setWebappIntentExtras(intent);

            intent.putExtra(ShortcutHelper.EXTRA_MAC, ShortcutHelper.getEncodedMac(mActivity, url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getApplicationContext().startActivity(intent);
        }
    }
}

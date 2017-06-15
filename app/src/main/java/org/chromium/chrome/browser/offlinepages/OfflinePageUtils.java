// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.offlinepages;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;

import org.chromium.base.ActivityState;
import org.chromium.base.ApplicationStatus;
import org.chromium.base.Callback;
import org.chromium.base.FileUtils;
import org.chromium.base.Log;
import org.chromium.base.StreamUtil;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.share.ShareHelper;
import org.chromium.chrome.browser.snackbar.Snackbar;
import org.chromium.chrome.browser.snackbar.SnackbarManager;
import org.chromium.chrome.browser.snackbar.SnackbarManager.SnackbarController;
import org.chromium.chrome.browser.tab.EmptyTabObserver;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.tabmodel.TabModelSelectorTabModelObserver;
import org.chromium.components.bookmarks.BookmarkId;
import org.chromium.components.offlinepages.SavePageResult;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.content_public.browser.WebContents;
import org.chromium.net.ConnectionType;
import org.chromium.net.NetworkChangeNotifier;
import org.chromium.ui.base.PageTransition;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * A class holding static util functions for offline pages.
 */
public class OfflinePageUtils {
    private static final String TAG = "OfflinePageUtils";
    /** Background task tag to differentiate from other task types */
    public static final String TASK_TAG = "OfflinePageUtils";

    public static final String EXTERNAL_MHTML_FILE_PATH = "offline-pages";

    private static final int DEFAULT_SNACKBAR_DURATION_MS = 6 * 1000; // 6 second

    private static final long STORAGE_ALMOST_FULL_THRESHOLD_BYTES = 10L * (1 << 20); // 10M

    /**
     * Bit flags to be OR-ed together to build the context of a tab restore to be used to identify
     * the appropriate TabRestoreType in a lookup table.
     */
    private static final int BIT_ONLINE = 1;
    private static final int BIT_CANT_SAVE_OFFLINE = 1 << 2;
    private static final int BIT_OFFLINE_PAGE = 1 << 3;
    private static final int BIT_LAST_N = 1 << 4;

    // Used instead of the constant so tests can override the value.
    private static int sSnackbarDurationMs = DEFAULT_SNACKBAR_DURATION_MS;

    /** Instance carrying actual implementation of utility methods. */
    private static Internal sInstance;

    private static File sOfflineSharingDirectory;

    /**
     * Tracks the observers of ChromeActivity's TabModelSelectors. This is weak so the activity can
     * be garbage collected without worrying about this map.  The RecentTabTracker is held here so
     * that it can be destroyed when the ChromeActivity gets a new TabModelSelector.
     */
    private static Map<ChromeActivity, RecentTabTracker> sTabModelObservers = new HashMap<>();

    /**
     * Interface for implementation of offline page utilities, that can be implemented for testing.
     * We are using an internal interface, so that instance methods can have the same names as
     * static methods.
     */
    @VisibleForTesting
    interface Internal {
        /** Returns offline page bridge for specified profile. */
        OfflinePageBridge getOfflinePageBridge(Profile profile);

        /** Returns whether the network is connected. */
        boolean isConnected();

        /**
         * Checks if an offline page is shown for the tab.
         * @param tab The tab to be reloaded.
         * @return True if the offline page is opened.
         */
        boolean isOfflinePage(Tab tab);

        /**
         * Returns whether the tab is showing offline preview.
         * @param tab The current tab.
         */
        boolean isShowingOfflinePreview(Tab tab);

        /**
         * Shows the "reload" snackbar for the given tab.
         * @param context The application context.
         * @param snackbarManager Class that shows the snackbar.
         * @param snackbarController Class to control the snackbar.
         * @param tabId Id of a tab that the snackbar is related to.
         */
        void showReloadSnackbar(Context context, SnackbarManager snackbarManager,
                final SnackbarController snackbarController, int tabId);
    }

    private static class OfflinePageUtilsImpl implements Internal {
        @Override
        public OfflinePageBridge getOfflinePageBridge(Profile profile) {
            return OfflinePageBridge.getForProfile(profile);
        }

        @Override
        public boolean isConnected() {
            return NetworkChangeNotifier.isOnline();
        }

        @Override
        public boolean isOfflinePage(Tab tab) {
            WebContents webContents = tab.getWebContents();
            if (webContents == null) return false;
            OfflinePageBridge offlinePageBridge =
                    getInstance().getOfflinePageBridge(tab.getProfile());
            if (offlinePageBridge == null) return false;
            return offlinePageBridge.isOfflinePage(webContents);
        }

        @Override
        public boolean isShowingOfflinePreview(Tab tab) {
            OfflinePageBridge offlinePageBridge = getOfflinePageBridge(tab.getProfile());
            if (offlinePageBridge == null) return false;
            return offlinePageBridge.isShowingOfflinePreview(tab.getWebContents());
        }

        @Override
        public void showReloadSnackbar(Context context, SnackbarManager snackbarManager,
                final SnackbarController snackbarController, int tabId) {
            if (tabId == Tab.INVALID_TAB_ID) return;

            Log.d(TAG, "showReloadSnackbar called with controller " + snackbarController);
            Snackbar snackbar =
                    Snackbar.make(context.getString(R.string.offline_pages_viewing_offline_page),
                                    snackbarController, Snackbar.TYPE_ACTION,
                                    Snackbar.UMA_OFFLINE_PAGE_RELOAD)
                            .setSingleLine(false)
                            .setAction(context.getString(R.string.reload), tabId);
            snackbar.setDuration(sSnackbarDurationMs);
            snackbarManager.showSnackbar(snackbar);
        }
    }

    /**
     * Contains values from the histogram enum OfflinePagesTabRestoreType used for reporting the
     * OfflinePages.TabRestore metric.
     */
    private static class TabRestoreType {
        public static final int WHILE_ONLINE = 0;
        public static final int WHILE_ONLINE_CANT_SAVE_FOR_OFFLINE_USAGE = 1;
        public static final int WHILE_ONLINE_TO_OFFLINE_PAGE = 2;
        public static final int WHILE_ONLINE_TO_OFFLINE_PAGE_FROM_LAST_N = 3;
        public static final int WHILE_OFFLINE = 4;
        public static final int WHILE_OFFLINE_CANT_SAVE_FOR_OFFLINE_USAGE = 5;
        public static final int WHILE_OFFLINE_TO_OFFLINE_PAGE = 6;
        public static final int WHILE_OFFLINE_TO_OFFLINE_PAGE_FROM_LAST_N = 7;
        public static final int FAILED = 8;
        public static final int CRASHED = 9;
        // NOTE: always keep this entry at the end. Add new result types only immediately above this
        // line. Make sure to update the corresponding histogram enum accordingly.
        public static final int COUNT = 10;
    }

    private static Internal getInstance() {
        if (sInstance == null) {
            sInstance = new OfflinePageUtilsImpl();
        }
        return sInstance;
    }

    /**
     * Returns the number of free bytes on the storage.
     */
    public static long getFreeSpaceInBytes() {
        return Environment.getDataDirectory().getUsableSpace();
    }

    /**
     * Returns the number of total bytes on the storage.
     */
    public static long getTotalSpaceInBytes() {
        return Environment.getDataDirectory().getTotalSpace();
    }

    /** Returns whether the network is connected. */
    public static boolean isConnected() {
        return getInstance().isConnected();
    }

    /*
     * Save an offline copy for the bookmarked page asynchronously.
     *
     * @param bookmarkId The ID of the page to save an offline copy.
     * @param tab A {@link Tab} object.
     * @param callback The callback to be invoked when the offline copy is saved.
     */
    public static void saveBookmarkOffline(BookmarkId bookmarkId, Tab tab) {
        // If bookmark ID is missing there is nothing to save here.
        if (bookmarkId == null) return;

        // Making sure the feature is enabled.
        if (!OfflinePageBridge.isOfflineBookmarksEnabled()) return;

        // Making sure tab is worth keeping.
        if (shouldSkipSavingTabOffline(tab)) return;

        OfflinePageBridge offlinePageBridge = getInstance().getOfflinePageBridge(tab.getProfile());
        if (offlinePageBridge == null) return;

        WebContents webContents = tab.getWebContents();
        ClientId clientId = ClientId.createClientIdForBookmarkId(bookmarkId);

        offlinePageBridge.savePage(webContents, clientId, new OfflinePageBridge.SavePageCallback() {
            @Override
            public void onSavePageDone(int savePageResult, String url, long offlineId) {
                // Result of the call is ignored.
            }
        });
    }

    /**
     * Indicates whether we should skip saving the given tab as an offline page.
     * A tab shouldn't be saved offline if it shows an error page or a sad tab page.
     */
    private static boolean shouldSkipSavingTabOffline(Tab tab) {
        WebContents webContents = tab.getWebContents();
        return tab.isShowingErrorPage() || tab.isShowingSadTab() || webContents == null
                || webContents.isDestroyed() || webContents.isIncognito();
    }

    /**
     * Strips scheme from the original URL of the offline page. This is meant to be used by UI.
     * @param onlineUrl an online URL to from which the scheme is removed
     * @return onlineUrl without the scheme
     */
    public static String stripSchemeFromOnlineUrl(String onlineUrl) {
        onlineUrl = onlineUrl.trim();
        // Offline pages are only saved for https:// and http:// schemes.
        if (onlineUrl.startsWith(UrlConstants.HTTPS_URL_PREFIX)) {
            return onlineUrl.substring(8);
        } else if (onlineUrl.startsWith(UrlConstants.HTTP_URL_PREFIX)) {
            return onlineUrl.substring(7);
        } else {
            return onlineUrl;
        }
    }

    /**
     * Shows the snackbar for the current tab to provide offline specific information if needed.
     * @param tab The current tab.
     */
    public static void showOfflineSnackbarIfNecessary(Tab tab) {
        // Set up the tab observer to watch for the tab being shown (not hidden) and a valid
        // connection. When both conditions are met a snackbar is shown.
        OfflinePageTabObserver.addObserverForTab(tab);
    }

    protected void showReloadSnackbarInternal(Context context, SnackbarManager snackbarManager,
            final SnackbarController snackbarController, int tabId) {}

    /**
     * Shows the "reload" snackbar for the given tab.
     * @param context The application context.
     * @param snackbarManager Class that shows the snackbar.
     * @param snackbarController Class to control the snackbar.
     * @param tabId Id of a tab that the snackbar is related to.
     */
    public static void showReloadSnackbar(Context context, SnackbarManager snackbarManager,
            final SnackbarController snackbarController, int tabId) {
        getInstance().showReloadSnackbar(context, snackbarManager, snackbarController, tabId);
    }

    /**
     * Records UMA data when the Offline Pages Background Load service awakens.
     * @param context android context
     */
    public static void recordWakeupUMA(Context context, long taskScheduledTimeMillis) {
        DeviceConditions deviceConditions = DeviceConditions.getCurrentConditions(context);
        if (deviceConditions == null) return;

        // Report charging state.
        RecordHistogram.recordBooleanHistogram(
                "OfflinePages.Wakeup.ConnectedToPower", deviceConditions.isPowerConnected());

        // Report battery percentage.
        RecordHistogram.recordPercentageHistogram(
                "OfflinePages.Wakeup.BatteryPercentage", deviceConditions.getBatteryPercentage());

        // Report the default network found (or none, if we aren't connected).
        int connectionType = deviceConditions.getNetConnectionType();
        Log.d(TAG, "Found default network of type " + connectionType);
        RecordHistogram.recordEnumeratedHistogram("OfflinePages.Wakeup.NetworkAvailable",
                connectionType, ConnectionType.CONNECTION_LAST + 1);

        // Collect UMA on the time since the request started.
        long nowMillis = System.currentTimeMillis();
        long delayInMilliseconds = nowMillis - taskScheduledTimeMillis;
        if (delayInMilliseconds <= 0) {
            return;
        }
        RecordHistogram.recordLongTimesHistogram(
                "OfflinePages.Wakeup.DelayTime",
                delayInMilliseconds,
                TimeUnit.MILLISECONDS);
    }

    /**
     * Share an offline copy of the current page.
     * @param shareDirectly Whether it should share directly with the activity that was most
     *                      recently used to share.
     * @param saveLastUsed Whether to save the chosen activity for future direct sharing.
     * @param mainActivity Activity that is used to access package manager.
     * @param text Text to be shared. If both |text| and |url| are supplied, they are concatenated
     *             with a space.
     * @param screenshotUri Screenshot of the page to be shared.
     * @param callback Optional callback to be called when user makes a choice. Will not be called
     *                 if receiving a response when the user makes a choice is not supported (see
     *                 TargetChosenReceiver#isSupported()).
     * @param currentTab The current tab for which sharing is being done.
     */
    public static void shareOfflinePage(final boolean shareDirectly, final boolean saveLastUsed,
            final Activity mainActivity, final String text, final Uri screenshotUri,
            final ShareHelper.TargetChosenCallback callback, final Tab currentTab) {
        final String url = currentTab.getUrl();
        final String title = currentTab.getTitle();
        final OfflinePageBridge offlinePageBridge =
                OfflinePageBridge.getForProfile(currentTab.getProfile());

        if (offlinePageBridge == null) {
            Log.e(TAG, "Unable to perform sharing on current tab.");
            return;
        }

        OfflinePageItem offlinePage = offlinePageBridge.getOfflinePage(currentTab.getWebContents());
        if (offlinePage != null) {
            // If we're currently on offline page get the saved file directly.
            prepareFileAndShare(shareDirectly, saveLastUsed, mainActivity, title, text,
                                url, screenshotUri, callback, offlinePage.getFilePath());
            return;
        }

        // If this is an online page, share the offline copy of it.
        Callback<OfflinePageItem> prepareForSharing = onGotOfflinePageItemToShare(shareDirectly,
                saveLastUsed, mainActivity, title, text, url, screenshotUri, callback);
        offlinePageBridge.selectPageForOnlineUrl(url, currentTab.getId(),
                selectPageForOnlineUrlCallback(currentTab.getWebContents(), offlinePageBridge,
                        prepareForSharing));
    }

    /**
     * Callback for receiving the OfflinePageItem and use it to call prepareForSharing.
     * @param shareDirectly Whether it should share directly with the activity that was most
     *                      recently used to share.
     * @param mainActivity Activity that is used to access package manager
     * @param title Title of the page.
     * @param onlineUrl Online URL associated with the offline page that is used to access the
     *                  offline page file path.
     * @param screenshotUri Screenshot of the page to be shared.
     * @param mContext The application context.
     * @return a callback of OfflinePageItem
     */
    private static Callback<OfflinePageItem> onGotOfflinePageItemToShare(
            final boolean shareDirectly, final boolean saveLastUsed, final Activity mainActivity,
            final String title, final String text, final String onlineUrl, final Uri screenshotUri,
            final ShareHelper.TargetChosenCallback callback) {
        return new Callback<OfflinePageItem>() {
            @Override
            public void onResult(OfflinePageItem item) {
                String offlineFilePath = (item == null) ? null : item.getFilePath();
                prepareFileAndShare(shareDirectly, saveLastUsed, mainActivity, title, text,
                        onlineUrl, screenshotUri, callback, offlineFilePath);
            }
        };
    }

    /**
     * Takes the offline page item from selectPageForOnlineURL. If it exists, invokes
     * |prepareForSharing| with it.  Otherwise, saves a page for the online URL and invokes
     * |prepareForSharing| with the result when it's ready.
     * @param webContents Contents of the page to save.
     * @param offlinePageBridge A static copy of the offlinePageBridge.
     * @param prepareForSharing Callback of a single OfflinePageItem that is used to call
     *                          prepareForSharing
     * @return a callback of OfflinePageItem
     */
    private static Callback<OfflinePageItem> selectPageForOnlineUrlCallback(
            final WebContents webContents, final OfflinePageBridge offlinePageBridge,
            final Callback<OfflinePageItem> prepareForSharing) {
        return new Callback<OfflinePageItem>() {
            @Override
            public void onResult(OfflinePageItem item) {
                if (item == null) {
                    // If the page has no offline copy, save the page offline.
                    ClientId clientId = ClientId.createGuidClientIdForNamespace(
                            OfflinePageBridge.SHARE_NAMESPACE);
                    offlinePageBridge.savePage(webContents, clientId,
                            savePageCallback(prepareForSharing, offlinePageBridge));
                    return;
                }
                // If the online page has offline copy associated with it, use the file directly.
                prepareForSharing.onResult(item);
            }
        };
    }

    /**
     * Saves the web page loaded into web contents. If page saved successfully, get the offline
     * page item with the save page result and use it to invoke |prepareForSharing|. Otherwise,
     * invokes |prepareForSharing| with null.
     * @param prepareForSharing Callback of a single OfflinePageItem that is used to call
     *                          prepareForSharing
     * @param offlinePageBridge A static copy of the offlinePageBridge.
     * @return a call back of a list of OfflinePageItem
     */
    private static OfflinePageBridge.SavePageCallback savePageCallback(
            final Callback<OfflinePageItem> prepareForSharing,
            final OfflinePageBridge offlinePageBridge) {
        return new OfflinePageBridge.SavePageCallback() {
            @Override
            public void onSavePageDone(int savePageResult, String url, long offlineId) {
                if (savePageResult != SavePageResult.SUCCESS) {
                    Log.e(TAG, "Unable to save the page.");
                    prepareForSharing.onResult(null);
                    return;
                }

                offlinePageBridge.getPageByOfflineId(offlineId, prepareForSharing);
            }
        };
    }

    /**
     * If file path of offline page is not null, do file operations needed for the page to be
     * shared. Otherwise, only share the online url.
     * @param shareDirectly Whether it should share directly with the activity that was most
     *                      recently used to share.
     * @param saveLastUsed Whether to save the chosen activity for future direct sharing.
     * @param activity Activity that is used to access package manager
     * @param title Title of the page.
     * @param text Text to be shared. If both |text| and |url| are supplied, they are concatenated
     *             with a space.
     * @param onlineUrl Online URL associated with the offline page that is used to access the
     *                  offline page file path.
     * @param screenshotUri Screenshot of the page to be shared.
     * @param callback Optional callback to be called when user makes a choice. Will not be called
     *                 if receiving a response when the user makes a choice is not supported (on
     *                 older Android versions).
     * @param filePath File path of the offline page.
     */
    private static void prepareFileAndShare(final boolean shareDirectly, final boolean saveLastUsed,
            final Activity activity, final String title, final String text, final String onlineUrl,
            final Uri screenshotUri, final ShareHelper.TargetChosenCallback callback,
            final String filePath) {
        new AsyncTask<Void, Void, File>() {
            @Override
            protected File doInBackground(Void... params) {
                if (filePath == null) return null;

                File offlinePageOriginal = new File(filePath);
                File shareableDir = getDirectoryForOfflineSharing(activity);

                if (shareableDir == null) {
                    Log.e(TAG, "Unable to create subdirectory in shareable directory");
                    return null;
                }

                String fileName = rewriteOfflineFileName(offlinePageOriginal.getName());
                File offlinePageShareable = new File(shareableDir, fileName);

                if (offlinePageShareable.exists()) {
                    try {
                        // Old shareable files are stored in an external directory, which may cause
                        // problems when:
                        // 1. Files been changed by external sources.
                        // 2. Difference in file size that results in partial overwrite.
                        // Thus the file is deleted before we make a new copy.
                        offlinePageShareable.delete();
                    } catch (SecurityException e) {
                        Log.e(TAG, "Failed to delete: " + offlinePageOriginal.getName(), e);
                        return null;
                    }
                }
                if (copyToShareableLocation(offlinePageOriginal, offlinePageShareable)) {
                    return offlinePageShareable;
                }

                return null;
            }

            @Override
            protected void onPostExecute(File offlinePageShareable) {
                Uri offlineUri = null;
                if (offlinePageShareable != null) {
                    offlineUri = Uri.fromFile(offlinePageShareable);
                }
                ShareHelper.share(shareDirectly, saveLastUsed, activity, title, text, onlineUrl,
                        offlineUri, screenshotUri, callback);
            }
        }.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
    }

    /**
     * Copies the file from internal storage to a sharable directory.
     * @param src The original file to be copied.
     * @param dst The destination file.
     */
    @VisibleForTesting
    static boolean copyToShareableLocation(File src, File dst) {
        FileInputStream inputStream = null;
        FileOutputStream outputStream = null;

        try {
            inputStream = new FileInputStream(src);
            outputStream = new FileOutputStream(dst);

            FileChannel inChannel = inputStream.getChannel();
            FileChannel outChannel = outputStream.getChannel();
            inChannel.transferTo(0, inChannel.size(), outChannel);
        } catch (IOException e) {
            Log.e(TAG, "Failed to copy the file: " + src.getName(), e);
            return false;
        } finally {
            StreamUtil.closeQuietly(inputStream);
            StreamUtil.closeQuietly(outputStream);
        }
        return true;
    }

    /**
     * Gets the directory to use for sharing offline pages, creating it if necessary.
     * @param context Context that is used to access external cache directory.
     * @return Path to the directory where shared files are stored.
     */
    @VisibleForTesting
    static File getDirectoryForOfflineSharing(Context context) {
        if (sOfflineSharingDirectory == null) {
            sOfflineSharingDirectory =
                    new File(context.getExternalCacheDir(), EXTERNAL_MHTML_FILE_PATH);
        }
        if (!sOfflineSharingDirectory.exists() && !sOfflineSharingDirectory.mkdir()) {
            sOfflineSharingDirectory = null;
        }
        return sOfflineSharingDirectory;
    }

    /**
     * Rewrite file name so that it does not contain periods except the one to separate the file
     * extension.
     * This step is used to ensure that file name can be recognized by intent filter (.*\\.mhtml")
     * as Android's path pattern only matches the first dot that appears in a file path.
     * @pram fileName Name of the offline page file.
     */
    @VisibleForTesting
    static String rewriteOfflineFileName(String fileName) {
        fileName = fileName.replaceAll("\\s+", "");
        return fileName.replaceAll("\\.(?=.*\\.)", "_");
    }

    /**
     * Clears all shared mhtml files.
     * @param context Context that is used to access external cache directory.
     */
    public static void clearSharedOfflineFiles(final Context context) {
        if (!OfflinePageBridge.isPageSharingEnabled()) return;
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                File offlinePath = getDirectoryForOfflineSharing(context);
                if (offlinePath != null) {
                    FileUtils.recursivelyDeleteFile(offlinePath);
                }
                return null;
            }
        }.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
    }

    /**
     * Retrieves the extra request header to reload the offline page.
     * @param tab The current tab.
     * @return The extra request header string.
     */
    public static String getOfflinePageHeaderForReload(Tab tab) {
        OfflinePageBridge offlinePageBridge = getInstance().getOfflinePageBridge(tab.getProfile());
        if (offlinePageBridge == null) return "";
        return offlinePageBridge.getOfflinePageHeaderForReload(tab.getWebContents());
    }

    /**
     * A load url parameters to open offline version of the offline page (i.e. to ensure no
     * automatic redirection based on the connection status).
     * @param url       The url of the offline page to open.
     * @param offlineId The ID of the offline page to open.
     * @return The LoadUrlParams with a special header.
     */
    public static LoadUrlParams getLoadUrlParamsForOpeningOfflineVersion(
            String url, long offlineId) {
        LoadUrlParams params = new LoadUrlParams(url);
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("X-Chrome-offline", "persist=1 reason=download id=" + Long.toString(offlineId));
        params.setExtraHeaders(headers);
        return params;
    }

    /**
     * @return True if an offline preview is being shown.
     * @param tab The current tab.
     */
    public static boolean isShowingOfflinePreview(Tab tab) {
        return getInstance().isShowingOfflinePreview(tab);
    }

    /**
     * Checks if an offline page is shown for the tab.
     * @param tab The tab to be reloaded.
     * @return True if the offline page is opened.
     */
    public static boolean isOfflinePage(Tab tab) {
        return getInstance().isOfflinePage(tab);
    }

    /**
     * Retrieves the offline page that is shown for the tab.
     * @param tab The tab to be reloaded.
     * @return The offline page if tab currently displays it, null otherwise.
     */
    public static OfflinePageItem getOfflinePage(Tab tab) {
        WebContents webContents = tab.getWebContents();
        if (webContents == null) return null;
        OfflinePageBridge offlinePageBridge = getInstance().getOfflinePageBridge(tab.getProfile());
        if (offlinePageBridge == null) return null;
        return offlinePageBridge.getOfflinePage(webContents);
    }

    /**
     * Reloads specified tab, which should allow to open an online version of the page.
     * @param tab The tab to be reloaded.
     */
    public static void reload(Tab tab) {
        // If current page is an offline page, reload it with custom behavior defined in extra
        // header respected.
        LoadUrlParams params =
                new LoadUrlParams(tab.getOriginalUrl(), PageTransition.RELOAD);
        params.setVerbatimHeaders(getOfflinePageHeaderForReload(tab));
        tab.loadUrl(params);
    }

    /**
     * Navigates the given tab to the saved local snapshot of the offline page identified by the URL
     * and the offline ID. No automatic redirection is happening based on the connection status.
     * @param url       The URL of the offine page.
     * @param offlineId The ID of the offline page.
     * @param tab       The tab to navigate to the page.
     */
    public static void openInExistingTab(String url, long offlineId, Tab tab) {
        LoadUrlParams params =
                OfflinePageUtils.getLoadUrlParamsForOpeningOfflineVersion(url, offlineId);
        // Extra headers are not read in loadUrl, but verbatim headers are.
        params.setVerbatimHeaders(params.getExtraHeadersString());
        tab.loadUrl(params);
    }

    /**
     * Tracks tab creation and closure for the Recent Tabs feature.  UI needs to stop showing
     * recent offline pages as soon as the tab is closed.  The TabModel is used to get profile
     * information because Tab's profile is tied to the native WebContents, which may not exist at
     * tab adding or tab closing time.
     */
    private static class RecentTabTracker extends TabModelSelectorTabModelObserver {
        /**
         * The single, stateless TabRestoreTracker instance to monitor all tab restores.
         */
        private static final TabRestoreTracker sTabRestoreTracker = new TabRestoreTracker();

        private TabModelSelector mTabModelSelector;

        public RecentTabTracker(TabModelSelector selector) {
            super(selector);
            mTabModelSelector = selector;
        }

        @Override
        public void didAddTab(Tab tab, TabModel.TabLaunchType type) {
            tab.addObserver(sTabRestoreTracker);

            Profile profile = mTabModelSelector.getModel(tab.isIncognito()).getProfile();
            OfflinePageBridge bridge = OfflinePageBridge.getForProfile(profile);
            if (bridge == null) return;
            bridge.registerRecentTab(tab.getId());
        }

        @Override
        public void willCloseTab(Tab tab, boolean animate) {
            Profile profile = mTabModelSelector.getModel(tab.isIncognito()).getProfile();
            OfflinePageBridge bridge = OfflinePageBridge.getForProfile(profile);
            if (bridge == null) return;

            WebContents webContents = tab.getWebContents();
            if (webContents != null) bridge.willCloseTab(webContents);
        }

        @Override
        public void didCloseTab(int tabId, boolean incognito) {
            Profile profile = mTabModelSelector.getModel(incognito).getProfile();
            OfflinePageBridge bridge = OfflinePageBridge.getForProfile(profile);
            if (bridge == null) return;

            // First, unregister the tab with the UI.
            bridge.unregisterRecentTab(tabId);

            // Then, delete any "Last N" offline pages as well.  This is an optimization because
            // the UI will no longer show the page, and the page would also be cleaned up by GC
            // given enough time.
            ClientId clientId =
                    new ClientId(OfflinePageBridge.LAST_N_NAMESPACE, Integer.toString(tabId));
            List<ClientId> clientIds = new ArrayList<>();
            clientIds.add(clientId);

            bridge.deletePagesByClientId(clientIds, new Callback<Integer>() {
                @Override
                public void onResult(Integer result) {
                    // Result is ignored.
                }
            });
        }
    }

    /**
     * Starts tracking the tab models in the given selector for tab addition and closure,
     * destroying obsolete observers as necessary.
     */
    public static void observeTabModelSelector(
            ChromeActivity activity, TabModelSelector tabModelSelector) {
        RecentTabTracker previousObserver =
                sTabModelObservers.put(activity, new RecentTabTracker(tabModelSelector));
        if (previousObserver != null) {
            previousObserver.destroy();
        } else {
            // This is the 1st time we see this activity so register a state listener with it.
            ApplicationStatus.registerStateListenerForActivity(
                    new ApplicationStatus.ActivityStateListener() {
                        @Override
                        public void onActivityStateChange(Activity activity, int newState) {
                            if (newState == ActivityState.DESTROYED) {
                                sTabModelObservers.remove(activity).destroy();
                                ApplicationStatus.unregisterActivityStateListener(this);
                            }
                        }
                    },
                    activity);
        }
    }

    private static class TabRestoreTracker extends EmptyTabObserver {
        /**
         * If the tab was being restored, reports that it successfully finished reloading its
         * contents.
         */
        @Override
        public void onPageLoadFinished(Tab tab) {
            if (!tab.isBeingRestored()) return;

            // We first compute the bitwise tab restore context.
            int tabRestoreContext = 0;
            if (isConnected()) tabRestoreContext |= BIT_ONLINE;
            OfflinePageItem page = getOfflinePage(tab);
            if (page != null) {
                tabRestoreContext |= BIT_OFFLINE_PAGE;
                if (page.getClientId().getNamespace().equals(OfflinePageBridge.LAST_N_NAMESPACE)) {
                    tabRestoreContext |= BIT_LAST_N;
                }
            } else if (!OfflinePageBridge.canSavePage(tab.getUrl()) || tab.isIncognito()) {
                tabRestoreContext |= BIT_CANT_SAVE_OFFLINE;
            }

            // Now determine the correct tab restore type based on the context.
            int tabRestoreType;
            switch (tabRestoreContext) {
                case BIT_ONLINE:
                    tabRestoreType = TabRestoreType.WHILE_ONLINE;
                    break;
                case BIT_ONLINE | BIT_CANT_SAVE_OFFLINE:
                    tabRestoreType = TabRestoreType.WHILE_ONLINE_CANT_SAVE_FOR_OFFLINE_USAGE;
                    break;
                case BIT_ONLINE | BIT_OFFLINE_PAGE:
                    tabRestoreType = TabRestoreType.WHILE_ONLINE_TO_OFFLINE_PAGE;
                    break;
                case BIT_ONLINE | BIT_OFFLINE_PAGE | BIT_LAST_N:
                    tabRestoreType = TabRestoreType.WHILE_ONLINE_TO_OFFLINE_PAGE_FROM_LAST_N;
                    break;
                case 0: // offline (not BIT_ONLINE present).
                    tabRestoreType = TabRestoreType.WHILE_OFFLINE;
                    break;
                case BIT_CANT_SAVE_OFFLINE:
                    tabRestoreType = TabRestoreType.WHILE_OFFLINE_CANT_SAVE_FOR_OFFLINE_USAGE;
                    break;
                case BIT_OFFLINE_PAGE:
                    tabRestoreType = TabRestoreType.WHILE_OFFLINE_TO_OFFLINE_PAGE;
                    break;
                case BIT_OFFLINE_PAGE | BIT_LAST_N:
                    tabRestoreType = TabRestoreType.WHILE_OFFLINE_TO_OFFLINE_PAGE_FROM_LAST_N;
                    break;
                default:
                    assert false;
                    return;
            }
            recordTabRestoreHistogram(tabRestoreType, tab.getUrl());
        }

        /**
         * If the tab was being restored, reports that it failed reloading its contents.
         */
        @Override
        public void onPageLoadFailed(Tab tab, int errorCode) {
            if (tab.isBeingRestored()) recordTabRestoreHistogram(TabRestoreType.FAILED, null);
        }

        /**
         * If the tab was being restored, reports that it crashed while doing so.
         */
        @Override
        public void onCrash(Tab tab, boolean sadTabShown) {
            if (tab.isBeingRestored()) recordTabRestoreHistogram(TabRestoreType.CRASHED, null);
        }
    }

    private static void recordTabRestoreHistogram(int tabRestoreType, String url) {
        Log.d(TAG, "Concluded tab restore: type=" + tabRestoreType + ", url=" + url);
        RecordHistogram.recordEnumeratedHistogram(
                "OfflinePages.TabRestore", tabRestoreType, TabRestoreType.COUNT);
    }

    @VisibleForTesting
    static void setInstanceForTesting(Internal instance) {
        sInstance = instance;
    }

    @VisibleForTesting
    public static void setSnackbarDurationForTesting(int durationMs) {
        sSnackbarDurationMs = durationMs;
    }
}

// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.download.ui;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.support.graphics.drawable.VectorDrawableCompat;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar.OnMenuItemClickListener;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.ViewGroup;

import org.chromium.base.ContextUtils;
import org.chromium.base.FileUtils;
import org.chromium.base.ObserverList;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.BasicNativePage;
import org.chromium.chrome.browser.download.DownloadManagerService;
import org.chromium.chrome.browser.download.DownloadUtils;
import org.chromium.chrome.browser.offlinepages.downloads.OfflinePageDownloadBridge;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.snackbar.Snackbar;
import org.chromium.chrome.browser.snackbar.SnackbarManager;
import org.chromium.chrome.browser.snackbar.SnackbarManager.SnackbarController;
import org.chromium.chrome.browser.widget.TintedDrawable;
import org.chromium.chrome.browser.widget.selection.SelectableListLayout;
import org.chromium.chrome.browser.widget.selection.SelectableListToolbar;
import org.chromium.chrome.browser.widget.selection.SelectableListToolbar.SearchDelegate;
import org.chromium.chrome.browser.widget.selection.SelectionDelegate;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Displays and manages the UI for the download manager.
 */

public class DownloadManagerUi implements OnMenuItemClickListener, SearchDelegate {

    /**
     * Interface to observe the changes in the download manager ui. This should be implemented by
     * the ui components that is shown, in order to let them get proper notifications.
     */
    public interface DownloadUiObserver {
        /**
         * Called when the filter has been changed by the user.
         */
        public void onFilterChanged(int filter);

        /**
         * Called when the download manager is not shown anymore.
         */
        public void onManagerDestroyed();
    }

    private static class DownloadBackendProvider implements BackendProvider {
        private OfflinePageDownloadBridge mOfflinePageBridge;
        private SelectionDelegate<DownloadHistoryItemWrapper> mSelectionDelegate;
        private ThumbnailProvider mThumbnailProvider;

        DownloadBackendProvider() {
            Resources resources = ContextUtils.getApplicationContext().getResources();
            int iconSize = resources.getDimensionPixelSize(R.dimen.downloads_item_icon_size);

            mOfflinePageBridge = new OfflinePageDownloadBridge(Profile.getLastUsedProfile());
            mSelectionDelegate = new DownloadItemSelectionDelegate();
            mThumbnailProvider = new ThumbnailProviderImpl(iconSize);
        }

        @Override
        public DownloadDelegate getDownloadDelegate() {
            return DownloadManagerService.getDownloadManagerService();
        }

        @Override
        public OfflinePageDownloadBridge getOfflinePageBridge() {
            return mOfflinePageBridge;
        }

        @Override
        public ThumbnailProvider getThumbnailProvider() {
            return mThumbnailProvider;
        }

        @Override
        public SelectionDelegate<DownloadHistoryItemWrapper> getSelectionDelegate() {
            return mSelectionDelegate;
        }

        @Override
        public void destroy() {
            getOfflinePageBridge().destroy();

            mThumbnailProvider.destroy();
            mThumbnailProvider = null;
        }
    }

    private class UndoDeletionSnackbarController implements SnackbarController {
        @Override
        public void onAction(Object actionData) {
            @SuppressWarnings("unchecked")
            List<DownloadHistoryItemWrapper> items = (List<DownloadHistoryItemWrapper>) actionData;

            // Deletion was undone. Add items back to the adapter.
            mHistoryAdapter.unmarkItemsForDeletion(items);

            RecordUserAction.record("Android.DownloadManager.UndoDelete");
        }

        @Override
        public void onDismissNoAction(Object actionData) {
            @SuppressWarnings("unchecked")
            List<DownloadHistoryItemWrapper> items = (List<DownloadHistoryItemWrapper>) actionData;

            // Deletion was not undone. Remove downloads from backend.
            final ArrayList<File> filesToDelete = new ArrayList<>();

            // Some types of DownloadHistoryItemWrappers delete their own files when #remove()
            // is called. Determine which files are not deleted by the #remove() call.
            for (int i = 0; i < items.size(); i++) {
                DownloadHistoryItemWrapper wrappedItem  = items.get(i);
                if (!wrappedItem.remove()) filesToDelete.add(wrappedItem.getFile());
            }

            // Delete the files associated with the download items (if necessary) using a single
            // AsyncTask that batch deletes all of the files. The thread pool has a finite
            // number of tasks that can be queued at once. If too many tasks are queued an
            // exception is thrown. See crbug.com/643811.
            if (filesToDelete.size() != 0) {
                new AsyncTask<Void, Void, Void>() {
                    @Override
                    public Void doInBackground(Void... params) {
                        FileUtils.batchDeleteFiles(filesToDelete);
                        return null;
                    }
                }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            }

            RecordUserAction.record("Android.DownloadManager.Delete");
        }
    }

    private static BackendProvider sProviderForTests;

    private final DownloadHistoryAdapter mHistoryAdapter;
    private final FilterAdapter mFilterAdapter;
    private final ObserverList<DownloadUiObserver> mObservers = new ObserverList<>();
    private final BackendProvider mBackendProvider;
    private final SnackbarManager mSnackbarManager;

    private final UndoDeletionSnackbarController mUndoDeletionSnackbarController;
    private final RecyclerView mRecyclerView;

    private BasicNativePage mNativePage;
    private Activity mActivity;
    private ViewGroup mMainView;
    private DownloadManagerToolbar mToolbar;
    private SelectableListLayout<DownloadHistoryItemWrapper> mSelectableListLayout;
    private boolean mIsSeparateActivity;

    /**
     * Constructs a new DownloadManagerUi.
     * @param activity The {@link Activity} associated with the download manager.
     * @param isOffTheRecord Whether an off-the-record tab is currently being displayed.
     * @param parentComponent The {@link ComponentName} of the parent activity.
     * @param isSeparateActivity Whether the download manager UI will be shown in a separate
     *                           activity than the main Chrome activity.
     * @param snackbarManager The {@link SnackbarManager} used to display snackbars.
     */
    @SuppressWarnings("unchecked") // mSelectableListLayout
    public DownloadManagerUi(Activity activity, boolean isOffTheRecord,
            ComponentName parentComponent, boolean isSeparateActivity,
            SnackbarManager snackbarManager) {
        mActivity = activity;
        mBackendProvider =
                sProviderForTests == null ? new DownloadBackendProvider() : sProviderForTests;
        mSnackbarManager = snackbarManager;

        mMainView = (ViewGroup) LayoutInflater.from(activity).inflate(R.layout.download_main, null);

        mSelectableListLayout = (SelectableListLayout<DownloadHistoryItemWrapper>)
                mMainView.findViewById(R.id.selectable_list);

        mSelectableListLayout.initializeEmptyView(
                VectorDrawableCompat.create(
                        mActivity.getResources(), R.drawable.downloads_big, mActivity.getTheme()),
                R.string.download_manager_ui_empty, R.string.download_manager_no_results);

        mHistoryAdapter = new DownloadHistoryAdapter(isOffTheRecord, parentComponent);
        mRecyclerView = mSelectableListLayout.initializeRecyclerView(mHistoryAdapter);

        // Prevent every progress update from causing a transition animation.
        mRecyclerView.getItemAnimator().setChangeDuration(0);

        mFilterAdapter = new FilterAdapter();
        mFilterAdapter.initialize(this);
        addObserver(mFilterAdapter);

        mToolbar = (DownloadManagerToolbar) mSelectableListLayout.initializeToolbar(
                R.layout.download_manager_toolbar, mBackendProvider.getSelectionDelegate(), 0, null,
                R.id.normal_menu_group, R.id.selection_mode_menu_group, null, this);
        mToolbar.initializeFilterSpinner(mFilterAdapter);
        mToolbar.initializeSearchView(this, R.string.download_manager_search, R.id.search_menu_id);
        addObserver(mToolbar);

        mSelectableListLayout.configureWideDisplayStyle();
        mHistoryAdapter.initialize(mBackendProvider, mSelectableListLayout.getUiConfig());
        addObserver(mHistoryAdapter);

        mUndoDeletionSnackbarController = new UndoDeletionSnackbarController();
        enableStorageInfoHeader(mHistoryAdapter.shouldShowStorageInfoHeader());

        mIsSeparateActivity = isSeparateActivity;
        if (!mIsSeparateActivity) mToolbar.removeCloseButton();
    }

    /**
     * Sets the {@link BasicNativePage} that holds this manager.
     */
    public void setBasicNativePage(BasicNativePage delegate) {
        mNativePage = delegate;
    }

    /**
     * Called when the activity/native page is destroyed.
     */
    public void onDestroyed() {
        for (DownloadUiObserver observer : mObservers) {
            observer.onManagerDestroyed();
            removeObserver(observer);
        }

        dismissUndoDeletionSnackbars();

        mBackendProvider.destroy();

        mSelectableListLayout.onDestroyed();
    }

    /**
     * Called when the UI needs to react to the back button being pressed.
     *
     * @return Whether the back button was handled.
     */
    public boolean onBackPressed() {
        if (mBackendProvider.getSelectionDelegate().isSelectionEnabled()) {
            mBackendProvider.getSelectionDelegate().clearSelection();
            return true;
        }
        return false;
    }

    /**
     * @return The view that shows the main download UI.
     */
    public ViewGroup getView() {
        return mMainView;
    }

    /**
     * See {@link SelectableListLayout#detachToolbarView()}.
     */
    public SelectableListToolbar<DownloadHistoryItemWrapper> detachToolbarView() {
        return mSelectableListLayout.detachToolbarView();
    }

    /**
     * @return The vertical scroll offset of the content view.
     */
    public int getVerticalScrollOffset() {
        return mRecyclerView.computeVerticalScrollOffset();
    }

    /**
     * Sets the download manager to the state that the url represents.
     */
    public void updateForUrl(String url) {
        int filter = DownloadFilter.getFilterFromUrl(url);
        onFilterChanged(filter);
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        if (item.getItemId() == R.id.close_menu_id && mIsSeparateActivity) {
            mActivity.finish();
            return true;
        } else if (item.getItemId() == R.id.selection_mode_delete_menu_id) {
            deleteSelectedItems();
            return true;
        } else if (item.getItemId() == R.id.selection_mode_share_menu_id) {
            shareSelectedItems();
            return true;
        } else if (item.getItemId() == R.id.info_menu_id) {
            enableStorageInfoHeader(!mHistoryAdapter.shouldShowStorageInfoHeader());
            return true;
        } else if (item.getItemId() == R.id.search_menu_id) {
            // The header should be removed as soon as a search is started. It will be added back in
            // DownloadHistoryAdatper#filter() when the search is ended.
            mHistoryAdapter.removeHeader();
            mSelectableListLayout.onStartSearch();
            mToolbar.showSearchView();
            RecordUserAction.record("Android.DownloadManager.Search");
            return true;
        }
        return false;
    }

    /**
     * Adds a {@link DownloadUiObserver} to observe the changes in the download manager.
     */
    public void addObserver(DownloadUiObserver observer) {
        mObservers.addObserver(observer);
    }

    /**
     * Removes a {@link DownloadUiObserver} that were added in
     * {@link #addObserver(DownloadUiObserver)}
     */
    public void removeObserver(DownloadUiObserver observer) {
        mObservers.removeObserver(observer);
    }

    /**
     * @return The activity that holds the download UI.
     */
    Activity getActivity() {
        return mActivity;
    }

    /**
     * @return The BackendProvider associated with the download UI.
     */
    public BackendProvider getBackendProvider() {
        return mBackendProvider;
    }

    /** Called when the filter has been changed by the user. */
    void onFilterChanged(int filter) {
        mBackendProvider.getSelectionDelegate().clearSelection();
        mToolbar.hideSearchView();

        for (DownloadUiObserver observer : mObservers) {
            observer.onFilterChanged(filter);
        }

        if (mNativePage != null) {
            mNativePage.onStateChange(DownloadFilter.getUrlForFilter(filter));
        }

        RecordHistogram.recordEnumeratedHistogram("Android.DownloadManager.Filter", filter,
                DownloadFilter.FILTER_BOUNDARY);
    }

    @Override
    public void onSearchTextChanged(String query) {
        mHistoryAdapter.search(query);
    }

    @Override
    public void onEndSearch() {
        mSelectableListLayout.onEndSearch();
        mHistoryAdapter.onEndSearch();
    }

    private void shareSelectedItems() {
        List<DownloadHistoryItemWrapper> selectedItems =
                mBackendProvider.getSelectionDelegate().getSelectedItems();
        assert selectedItems.size() > 0;

        mActivity.startActivity(Intent.createChooser(createShareIntent(),
                mActivity.getString(R.string.share_link_chooser_title)));

        // TODO(twellington): ideally the intent chooser would be started with
        //                    startActivityForResult() and the selection would only be cleared after
        //                    receiving an OK response. See crbug.com/638916.
        mBackendProvider.getSelectionDelegate().clearSelection();
    }

    private void enableStorageInfoHeader(boolean show) {
        // Finish any running or pending animations right away.
        if (mRecyclerView.getItemAnimator() != null) {
            mRecyclerView.getItemAnimator().endAnimations();
        }

        mHistoryAdapter.setShowStorageInfoHeader(show);
        MenuItem infoMenuItem = mToolbar.getMenu().findItem(R.id.info_menu_id);
        Drawable iconDrawable = TintedDrawable.constructTintedDrawable(mActivity.getResources(),
                R.drawable.btn_info,
                show ? R.color.light_active_color : R.color.default_text_color);
        infoMenuItem.setIcon(iconDrawable);
        infoMenuItem.setTitle(show ? R.string.hide_info : R.string.show_info);
    }

    /**
     * @return An Intent to share the selected items.
     */
    @VisibleForTesting
    public Intent createShareIntent() {
        List<DownloadHistoryItemWrapper> selectedItems =
                mBackendProvider.getSelectionDelegate().getSelectedItems();
        return DownloadUtils.createShareIntent(selectedItems);
    }

    private void deleteSelectedItems() {
        List<DownloadHistoryItemWrapper> selectedItems =
                mBackendProvider.getSelectionDelegate().getSelectedItems();
        final List<DownloadHistoryItemWrapper> itemsToDelete = getItemsForDeletion();

        mBackendProvider.getSelectionDelegate().clearSelection();

        if (itemsToDelete.isEmpty()) return;

        mHistoryAdapter.markItemsForDeletion(itemsToDelete);

        boolean singleItemDeleted = selectedItems.size() == 1;
        String snackbarText = singleItemDeleted ? selectedItems.get(0).getDisplayFileName() :
                String.format(Locale.getDefault(), "%d", selectedItems.size());
        int snackbarTemplateId = singleItemDeleted ? R.string.undo_bar_delete_message
                : R.string.undo_bar_multiple_downloads_delete_message;

        Snackbar snackbar = Snackbar.make(snackbarText, mUndoDeletionSnackbarController,
                Snackbar.TYPE_ACTION, Snackbar.UMA_DOWNLOAD_DELETE_UNDO);
        snackbar.setAction(mActivity.getString(R.string.undo), itemsToDelete);
        snackbar.setTemplateText(mActivity.getString(snackbarTemplateId));

        mSnackbarManager.showSnackbar(snackbar);
    }

    private List<DownloadHistoryItemWrapper> getItemsForDeletion() {
        List<DownloadHistoryItemWrapper> selectedItems =
                mBackendProvider.getSelectionDelegate().getSelectedItems();
        List<DownloadHistoryItemWrapper> itemsToRemove = new ArrayList<>();
        Set<String> filePathsToRemove = new HashSet<>();

        for (DownloadHistoryItemWrapper item : selectedItems) {
            if (!filePathsToRemove.contains(item.getFilePath())) {
                Set<DownloadHistoryItemWrapper> itemsForFilePath =
                        mHistoryAdapter.getItemsForFilePath(item.getFilePath());
                if (itemsForFilePath != null) {
                    itemsToRemove.addAll(itemsForFilePath);
                }
                filePathsToRemove.add(item.getFilePath());
            }
        }

        return itemsToRemove;
    }

    private void dismissUndoDeletionSnackbars() {
        mSnackbarManager.dismissSnackbars(mUndoDeletionSnackbarController);
    }

    @VisibleForTesting
    public SnackbarManager getSnackbarManagerForTesting() {
        return mSnackbarManager;
    }

    /** Returns the {@link DownloadManagerToolbar}. */
    @VisibleForTesting
    public DownloadManagerToolbar getDownloadManagerToolbarForTests() {
        return mToolbar;
    }

    /** Returns the {@link DownloadHistoryAdapter}. */
    @VisibleForTesting
    public DownloadHistoryAdapter getDownloadHistoryAdapterForTests() {
        return mHistoryAdapter;
    }

    /** Sets a BackendProvider that is used in place of a real one. */
    @VisibleForTesting
    public static void setProviderForTests(BackendProvider provider) {
        sProviderForTests = provider;
    }
}

// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.bookmarks;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.bookmarks.BookmarkBridge.BookmarkItem;
import org.chromium.chrome.browser.bookmarks.BookmarkBridge.BookmarkModelObserver;
import org.chromium.chrome.browser.bookmarks.BookmarkPromoHeader.PromoHeaderShowingChangeListener;
import org.chromium.chrome.browser.widget.displaystyle.MarginResizer;
import org.chromium.chrome.browser.widget.selection.SelectableListLayout;
import org.chromium.components.bookmarks.BookmarkId;

import java.util.ArrayList;
import java.util.List;

/**
 * BaseAdapter for {@link RecyclerView}. It manages bookmarks to list there.
 */
class BookmarkItemsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> implements
        BookmarkUIObserver, PromoHeaderShowingChangeListener {
    private static final int PROMO_HEADER_VIEW = 0;
    private static final int FOLDER_VIEW = 1;
    private static final int BOOKMARK_VIEW = 2;

    private static final int MAXIMUM_NUMBER_OF_SEARCH_RESULTS = 500;
    private static final String EMPTY_QUERY = null;

    private final List<List<? extends Object>> mSections;
    private final List<Object> mPromoHeaderSection = new ArrayList<>();
    private final List<BookmarkId> mFolderSection = new ArrayList<>();
    private final List<BookmarkId> mBookmarkSection = new ArrayList<>();

    private final List<BookmarkRow> mBookmarkRows = new ArrayList<>();
    private final List<BookmarkRow> mFolderRows = new ArrayList<>();

    private final List<BookmarkId> mTopLevelFolders = new ArrayList<>();

    private BookmarkDelegate mDelegate;
    private Context mContext;
    private BookmarkPromoHeader mPromoHeaderManager;
    private String mSearchText;

    private BookmarkModelObserver mBookmarkModelObserver = new BookmarkModelObserver() {
        @Override
        public void bookmarkNodeChanged(BookmarkItem node) {
            assert mDelegate != null;
            int position = getPositionForBookmark(node.getId());
            if (position >= 0) notifyItemChanged(position);
        }

        @Override
        public void bookmarkNodeRemoved(BookmarkItem parent, int oldIndex, BookmarkItem node,
                boolean isDoingExtensiveChanges) {
            assert mDelegate != null;
            if (node.isFolder()) {
                mDelegate.notifyStateChange(BookmarkItemsAdapter.this);
            } else {
                int deletedPosition = getPositionForBookmark(node.getId());
                if (deletedPosition >= 0) {
                    removeItem(deletedPosition);
                }
            }
        }

        @Override
        public void bookmarkModelChanged() {
            assert mDelegate != null;
            mDelegate.notifyStateChange(BookmarkItemsAdapter.this);

            if (mDelegate.getCurrentState() == BookmarkUIState.STATE_SEARCHING
                    && !TextUtils.equals(mSearchText, EMPTY_QUERY)) {
                search(mSearchText);
            }
        }
    };

    BookmarkItemsAdapter(Context context) {
        mContext = context;

        mSections = new ArrayList<>();
        mSections.add(mPromoHeaderSection);
        mSections.add(mFolderSection);
        mSections.add(mBookmarkSection);
    }

    BookmarkId getItem(int position) {
        return (BookmarkId) getSection(position).get(toSectionPosition(position));
    }

    private int toSectionPosition(int globalPosition) {
        int sectionPosition = globalPosition;
        for (List<?> section : mSections) {
            if (sectionPosition < section.size()) break;
            sectionPosition -= section.size();
        }
        return sectionPosition;
    }

    private List<? extends Object> getSection(int position) {
        int i = position;
        for (List<? extends Object> section : mSections) {
            if (i < section.size()) {
                return section;
            }
            i -= section.size();
        }
        return null;
    }

    /**
     * @return The position of the given bookmark in adapter. Will return -1 if not found.
     */
    private int getPositionForBookmark(BookmarkId bookmark) {
        assert bookmark != null;
        int position = -1;
        for (int i = 0; i < getItemCount(); i++) {
            if (bookmark.equals(getItem(i))) {
                position = i;
                break;
            }
        }
        return position;
    }

    /**
     * Set folders and bookmarks to show.
     * @param folders This can be null if there is no folders to show.
     */
    private void setBookmarks(List<BookmarkId> folders, List<BookmarkId> bookmarks) {
        if (folders == null) folders = new ArrayList<BookmarkId>();

        mFolderSection.clear();
        mFolderSection.addAll(folders);
        mBookmarkSection.clear();
        mBookmarkSection.addAll(bookmarks);

        updateHeaderAndNotify();
    }

    private void removeItem(int position) {
        List<?> section = getSection(position);
        assert section == mFolderSection || section == mBookmarkSection;
        section.remove(toSectionPosition(position));
        notifyItemRemoved(position);

        if (section == mBookmarkSection && !mBookmarkSection.isEmpty()) {
            for (BookmarkRow row : mBookmarkRows) {
                BookmarkId id = row.getItem();
                setBackgroundResourceForBookmarkRow(row, id);
            }
        } else if (!mFolderSection.isEmpty()) {
            for (BookmarkRow row : mFolderRows) {
                BookmarkId id = row.getItem();
                setBackgroundResourceForFolderRow(row, id);
            }
        }
    }

    // RecyclerView.Adapter implementation.

    @Override
    public int getItemCount() {
        int count = 0;
        for (List<?> section : mSections) {
            count += section.size();
        }
        return count;
    }

    @Override
    public int getItemViewType(int position) {
        List<?> section = getSection(position);

        if (section == mPromoHeaderSection) {
            return PROMO_HEADER_VIEW;
        } else if (section == mFolderSection) {
            return FOLDER_VIEW;
        } else if (section == mBookmarkSection) {
            return BOOKMARK_VIEW;
        }

        assert false : "Invalid position requested";
        return -1;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        assert mDelegate != null;

        switch (viewType) {
            case PROMO_HEADER_VIEW:
                ViewHolder promoView = mPromoHeaderManager.createHolder(parent);
                MarginResizer.createWithViewAdapter(promoView.itemView,
                        mDelegate.getSelectableListLayout().getUiConfig(),
                        parent.getResources().getDimensionPixelSize(
                                R.dimen.signin_and_sync_view_padding),
                        SelectableListLayout.getDefaultListItemLateralShadowSizePx(
                                parent.getResources()));
                return promoView;
            case FOLDER_VIEW:
                BookmarkFolderRow folder = (BookmarkFolderRow) LayoutInflater.from(
                        parent.getContext()).inflate(R.layout.bookmark_folder_row, parent, false);
                folder.onBookmarkDelegateInitialized(mDelegate);
                folder.configureWideDisplayStyle(mDelegate.getSelectableListLayout().getUiConfig());
                mFolderRows.add(folder);
                return new ItemViewHolder(folder);
            case BOOKMARK_VIEW:
                BookmarkItemRow item = (BookmarkItemRow) LayoutInflater.from(
                        parent.getContext()).inflate(R.layout.bookmark_item_row, parent, false);
                item.onBookmarkDelegateInitialized(mDelegate);
                item.configureWideDisplayStyle(mDelegate.getSelectableListLayout().getUiConfig());
                mBookmarkRows.add(item);
                return new ItemViewHolder(item);
            default:
                assert false;
                return null;
        }
    }

    @SuppressFBWarnings("BC_UNCONFIRMED_CAST")
    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        BookmarkId id = getItem(position);

        switch (getItemViewType(position)) {
            case PROMO_HEADER_VIEW:
                break;
            case FOLDER_VIEW:
                ((BookmarkRow) holder.itemView).setBookmarkId(id);
                setBackgroundResourceForFolderRow(((BookmarkRow) holder.itemView), id);
                break;
            case BOOKMARK_VIEW:
                ((BookmarkRow) holder.itemView).setBookmarkId(id);
                setBackgroundResourceForBookmarkRow((BookmarkRow) holder.itemView, id);
                break;
            default:
                assert false : "View type not supported!";
        }
    }

    // PromoHeaderShowingChangeListener implementation.

    @Override
    public void onPromoHeaderShowingChanged(boolean isShowing) {
        assert mDelegate != null;
        if (mDelegate.getCurrentState() != BookmarkUIState.STATE_FOLDER) {
            return;
        }

        updateHeaderAndNotify();
    }

    // BookmarkUIObserver implementations.

    @Override
    public void onBookmarkDelegateInitialized(BookmarkDelegate delegate) {
        mDelegate = delegate;
        mDelegate.addUIObserver(this);
        mDelegate.getModel().addObserver(mBookmarkModelObserver);
        mPromoHeaderManager = new BookmarkPromoHeader(mContext, this);
        populateTopLevelFoldersList();
    }

    @Override
    public void onDestroy() {
        mDelegate.removeUIObserver(this);
        mDelegate.getModel().removeObserver(mBookmarkModelObserver);
        mDelegate = null;

        mPromoHeaderManager.destroy();
    }

    @Override
    public void onFolderStateSet(BookmarkId folder) {
        assert mDelegate != null;

        mSearchText = EMPTY_QUERY;

        if (folder.equals(mDelegate.getModel().getRootFolderId())) {
            setBookmarks(mTopLevelFolders, new ArrayList<BookmarkId>());
        } else {
            setBookmarks(mDelegate.getModel().getChildIDs(folder, true, false),
                    mDelegate.getModel().getChildIDs(folder, false, true));
        }
    }

    @Override
    public void onSearchStateSet() {
        updateHeaderAndNotify();
    }

    @Override
    public void onSelectionStateChange(List<BookmarkId> selectedBookmarks) {}

    /**
     * Synchronously searches for the given query.
     * @param query The query text to search for.
     */
    void search(String query) {
        mSearchText = query.toString().trim();
        List<BookmarkId> results =
                mDelegate.getModel().searchBookmarks(mSearchText, MAXIMUM_NUMBER_OF_SEARCH_RESULTS);
        setBookmarks(null, results);
    }

    private static class ItemViewHolder extends RecyclerView.ViewHolder {
        private ItemViewHolder(View view) {
            super(view);
        }
    }

    private void updateHeaderAndNotify() {
        updateHeader();
        notifyDataSetChanged();
    }

    private void updateHeader() {
        if (mDelegate == null) return;

        int currentUIState = mDelegate.getCurrentState();
        if (currentUIState == BookmarkUIState.STATE_LOADING) return;

        mPromoHeaderSection.clear();

        if (currentUIState == BookmarkUIState.STATE_SEARCHING) return;

        assert currentUIState == BookmarkUIState.STATE_FOLDER : "Unexpected UI state";
        if (mPromoHeaderManager.shouldShow()) {
            mPromoHeaderSection.add(null);
        }
    }

    private void populateTopLevelFoldersList() {
        BookmarkId desktopNodeId = mDelegate.getModel().getDesktopFolderId();
        BookmarkId mobileNodeId = mDelegate.getModel().getMobileFolderId();
        BookmarkId othersNodeId = mDelegate.getModel().getOtherFolderId();

        if (mDelegate.getModel().isFolderVisible(mobileNodeId)) {
            mTopLevelFolders.add(mobileNodeId);
        }
        if (mDelegate.getModel().isFolderVisible(desktopNodeId)) {
            mTopLevelFolders.add(desktopNodeId);
        }
        if (mDelegate.getModel().isFolderVisible(othersNodeId)) {
            mTopLevelFolders.add(othersNodeId);
        }
    }

    @VisibleForTesting
    public BookmarkDelegate getDelegateForTesting() {
        return mDelegate;
    }

    private void setBackgroundResourceForBookmarkRow(BookmarkRow row, BookmarkId id) {
        row.setBackgroundResourceForGroupPosition(id.equals(mBookmarkSection.get(0)),
                id.equals(mBookmarkSection.get(mBookmarkSection.size() - 1)));
    }

    private void setBackgroundResourceForFolderRow(BookmarkRow row, BookmarkId id) {
        row.setBackgroundResourceForGroupPosition(id.equals(mFolderSection.get(0)),
                id.equals(mFolderSection.get(mFolderSection.size() - 1)));
    }
}

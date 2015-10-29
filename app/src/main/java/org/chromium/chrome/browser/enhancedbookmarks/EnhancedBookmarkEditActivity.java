// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.enhancedbookmarks;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.chromium.base.Log;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.BookmarksBridge.BookmarkItem;
import org.chromium.chrome.browser.BookmarksBridge.BookmarkModelObserver;
import org.chromium.chrome.browser.UrlUtilities;
import org.chromium.chrome.browser.offlinepages.OfflinePageBridge;
import org.chromium.chrome.browser.offlinepages.OfflinePageBridge.DeletePageCallback;
import org.chromium.chrome.browser.offlinepages.OfflinePageBridge.SavePageCallback;
import org.chromium.chrome.browser.offlinepages.OfflinePageItem;
import org.chromium.chrome.browser.widget.EmptyAlertEditText;
import org.chromium.chrome.browser.widget.TintedDrawable;
import org.chromium.components.bookmarks.BookmarkId;
import org.chromium.components.offlinepages.DeletePageResult;
import org.chromium.components.offlinepages.SavePageResult;
import org.chromium.content_public.browser.WebContents;

/**
 * The activity that enables the user to modify the title, url and parent folder of a bookmark.
 */
public class EnhancedBookmarkEditActivity extends EnhancedBookmarkActivityBase {
    /** The intent extra specifying the ID of the bookmark to be edited. */
    public static final String INTENT_BOOKMARK_ID = "EnhancedBookmarkEditActivity.BookmarkId";
    public static final String INTENT_WEB_CONTENTS = "EnhancedBookmarkEditActivity.WebContents";

    private static final String TAG = "cr.BookmarkEdit";

    private EnhancedBookmarksModel mEnhancedBookmarksModel;
    private BookmarkId mBookmarkId;
    private EmptyAlertEditText mTitleEditText;
    private EmptyAlertEditText mUrlEditText;
    private TextView mFolderTextView;

    private WebContents mWebContents;

    private MenuItem mDeleteButton;

    private BookmarkModelObserver mBookmarkModelObserver = new BookmarkModelObserver() {
        @Override
        public void bookmarkNodeRemoved(BookmarkItem parent, int oldIndex, BookmarkItem node,
                boolean isDoingExtensiveChanges) {
            if (mBookmarkId.equals(node.getId())) {
                finish();
            }
        }

        @Override
        public void bookmarkNodeMoved(BookmarkItem oldParent, int oldIndex, BookmarkItem newParent,
                int newIndex) {
            BookmarkId movedBookmark = mEnhancedBookmarksModel.getChildAt(newParent.getId(),
                    newIndex);
            if (movedBookmark.equals(mBookmarkId)) {
                mFolderTextView.setText(newParent.getTitle());
            }
        }

        @Override
        public void bookmarkNodeChanged(BookmarkItem node) {
            if (mBookmarkId.equals(node.getId()) || node.getId().equals(
                    mEnhancedBookmarksModel.getBookmarkById(mBookmarkId).getParentId())) {
                updateViewContent();
            }
        }

        @Override
        public void bookmarkModelChanged() {
            if (mEnhancedBookmarksModel.doesBookmarkExist(mBookmarkId)) {
                updateViewContent();
            } else {
                Log.wtf(TAG, "The bookmark was deleted somehow during bookmarkModelChange!",
                        new Exception(TAG));
                finish();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EnhancedBookmarkUtils.setTaskDescriptionInDocumentMode(this,
                getString(R.string.edit_bookmark));
        mEnhancedBookmarksModel = new EnhancedBookmarksModel();
        mBookmarkId = BookmarkId.getBookmarkIdFromString(
                getIntent().getStringExtra(INTENT_BOOKMARK_ID));
        mEnhancedBookmarksModel.addObserver(mBookmarkModelObserver);
        assert mEnhancedBookmarksModel.getBookmarkById(mBookmarkId).isEditable();

        setContentView(R.layout.eb_edit);
        mTitleEditText = (EmptyAlertEditText) findViewById(R.id.title_text);
        mFolderTextView = (TextView) findViewById(R.id.folder_text);
        mUrlEditText = (EmptyAlertEditText) findViewById(R.id.url_text);

        mFolderTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EnhancedBookmarkFolderSelectActivity.startFolderSelectActivity(
                        EnhancedBookmarkEditActivity.this, mBookmarkId);
            }
        });

        if (OfflinePageBridge.isEnabled()) {
            // Make offline page section visible and find controls.
            findViewById(R.id.offline_page_group).setVisibility(View.VISIBLE);
            getIntent().setExtrasClassLoader(WebContents.class.getClassLoader());
            mWebContents = getIntent().getParcelableExtra(INTENT_WEB_CONTENTS);
            updateOfflineSection();
        }

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        updateViewContent();
    }

    private void updateViewContent() {
        BookmarkItem bookmarkItem = mEnhancedBookmarksModel.getBookmarkById(mBookmarkId);

        if (!TextUtils.equals(mTitleEditText.getTrimmedText(), bookmarkItem.getTitle())) {
            mTitleEditText.setText(bookmarkItem.getTitle());
        }
        String folderTitle = mEnhancedBookmarksModel.getBookmarkTitle(bookmarkItem.getParentId());
        if (!TextUtils.equals(mFolderTextView.getText(), folderTitle)) {
            mFolderTextView.setText(folderTitle);
        }
        if (!TextUtils.equals(mUrlEditText.getTrimmedText(), bookmarkItem.getUrl())) {
            mUrlEditText.setText(bookmarkItem.getUrl());
        }
        mUrlEditText.setEnabled(bookmarkItem.isUrlEditable());
        mFolderTextView.setEnabled(bookmarkItem.isMovable());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        mDeleteButton = menu.add(R.string.enhanced_bookmark_action_bar_delete)
                .setIcon(TintedDrawable.constructTintedDrawable(
                        getResources(), R.drawable.btn_trash))
                .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item == mDeleteButton) {
            // Log added for detecting delete button double clicking.
            Log.i(TAG, "Delete button pressed by user! isFinishing() == " + isFinishing());

            mEnhancedBookmarksModel.deleteBookmark(mBookmarkId);
            finish();
            return true;
        } else if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStop() {
        if (mEnhancedBookmarksModel.doesBookmarkExist(mBookmarkId)) {
            if (!mTitleEditText.isEmpty()) {
                mEnhancedBookmarksModel.setBookmarkTitle(
                        mBookmarkId, mTitleEditText.getTrimmedText());
            }

            if (!mUrlEditText.isEmpty()
                    && mEnhancedBookmarksModel.getBookmarkById(mBookmarkId).isUrlEditable()) {
                String fixedUrl = UrlUtilities.fixupUrl(mUrlEditText.getTrimmedText());
                if (fixedUrl != null) mEnhancedBookmarksModel.setBookmarkUrl(mBookmarkId, fixedUrl);
            }
        }

        super.onStop();
    }
    @Override
    protected void onDestroy() {
        mEnhancedBookmarksModel.removeObserver(mBookmarkModelObserver);
        mEnhancedBookmarksModel.destroy();
        mEnhancedBookmarksModel = null;
        super.onDestroy();
    }

    private void updateOfflineSection() {
        Button saveRemoveVisitButton = (Button) findViewById(R.id.offline_page_save_remove_button);
        TextView offlinePageInfoTextView = (TextView) findViewById(R.id.offline_page_info_text);

        OfflinePageItem offlinePage = mEnhancedBookmarksModel.getOfflinePageBridge()
                .getPageByBookmarkId(mBookmarkId);
        if (offlinePage != null) {
            // Offline page exists. Show information and button to remove.
            offlinePageInfoTextView.setText(getString(R.string.bookmark_offline_page_size,
                    Formatter.formatFileSize(this, offlinePage.getFileSize())));
            updateButtonToDeleteOfflinePage(saveRemoveVisitButton);
        } else if (mWebContents != null) {
            // Offline page is not saved, but a bookmarked page is opened. Show save button.
            offlinePageInfoTextView.setText(getString(R.string.bookmark_offline_page_none));
            updateButtonToSaveOfflinePage(saveRemoveVisitButton);
        } else {
            // Offline page is not saved, and edit page was opened from the bookmarks UI, which
            // means there is no action the user can take any action - hide button.
            offlinePageInfoTextView.setText(getString(R.string.bookmark_offline_page_visit));
            updateButtonToVisitOfflinePage(saveRemoveVisitButton);
        }
    }

    private void updateButtonToDeleteOfflinePage(final Button button) {
        button.setText(getString(R.string.remove));
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mEnhancedBookmarksModel.getOfflinePageBridge().deletePage(
                        mBookmarkId, new DeletePageCallback() {
                            @Override
                            public void onDeletePageDone(int deletePageResult) {
                                if (deletePageResult == DeletePageResult.SUCCESS) {
                                    updateOfflineSection();
                                }
                                // TODO(fgorski): Add snackbar upon failure.
                            }
                        });
                button.setClickable(false);
            }
        });
    }

    private void updateButtonToSaveOfflinePage(final Button button) {
        button.setText(getString(R.string.save));
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mEnhancedBookmarksModel.getOfflinePageBridge().savePage(mWebContents, mBookmarkId,
                        new SavePageCallback() {
                            @Override
                            public void onSavePageDone(int savePageResult, String url) {
                                if (savePageResult == SavePageResult.SUCCESS) {
                                    updateOfflineSection();
                                }
                                // TODO(fgorski): Add snackbar upon failure.
                            }
                        });
                button.setClickable(false);
            }
        });
    }

    private void updateButtonToVisitOfflinePage(Button button) {
        button.setText(getString(R.string.bookmark_btn_offline_page_visit));
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openBookmark();
            }
        });
    }

    private void openBookmark() {
        Intent intent = new Intent();
        intent.putExtra(EnhancedBookmarkActivity.INTENT_VISIT_BOOKMARK_ID, mBookmarkId.toString());
        setResult(RESULT_OK, intent);
        finish();
    }
}

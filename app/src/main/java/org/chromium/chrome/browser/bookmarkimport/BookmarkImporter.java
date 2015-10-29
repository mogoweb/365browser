// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.bookmarkimport;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

import org.chromium.chrome.browser.ChromeBrowserProvider;
import org.chromium.chrome.browser.ChromeBrowserProviderClient;
import org.chromium.chrome.browser.bookmark.BookmarkColumns;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;

/**
 * Imports bookmarks from another browser into Chrome.
 */
public abstract class BookmarkImporter {
    private static final String TAG = "BookmarkImporter";

    /** Class containing the results of a bookmark import operation */
    public static class ImportResults {
        public int newBookmarks; // Number of new bookmarks that could be imported.
        public int numImported; // Number of bookmarks that were successfully imported.
        public long rootFolderId; // ID of the folder where the bookmarks were imported.
    }

    /** Listener for asynchronous import events. */
    public interface OnBookmarksImportedListener {
        /**
         * Triggered after finishing the bookmark importing operation.
         * @param results Results of the importing operation. Will be null in case of failure.
        */
        public void onBookmarksImported(ImportResults results);
    }

    /** Object defining an imported bookmark. */
    static class Bookmark {
        // To be provided by the bookmark extractors.
        public long id; // Local id of the imported bookmark. Value ROOT_FOLDER_ID is reserved.
        public long parentId; // Import id of the parent node.
        public boolean isFolder; // True if the object describes a bookmark folder.
        public String url; // URL of the bookmark. Required for non-folders.
        public String title; // Title of the bookmark.
        public Long created; // Creation date (timestamp) of the bookmark. Optional.
        public Long lastVisit; // Date (timestamp) of the last visit. Optional.
        public Long visits; // Number of visits to the page. Optional.
        public byte[] favicon; // Favicon of the bookmark. Optional.

        // For auxiliary use while importing. Not to be set by the bookmark extractors.
        public long nativeId;
        public Bookmark parent;
        public ArrayList<Bookmark> entries = new ArrayList<Bookmark>();
        public boolean processed;
    }

    /** Closable iterator for available bookmarks. */
    public interface BookmarkIterator extends Iterator<Bookmark> {
        public void close();
    }

    /**
     * Returns an array of iterators to the available bookmarks.
     * The first one is tried and in case of complete importing failure the second one is then used
     * and so on until the array is exhausted. Note that no new bookmarks is not a failure.
     *
     * Called by an async task.
     */
    protected abstract BookmarkIterator[] availableBookmarks();

    /** Imported bookmark id reserved for the root folder. */
    static final long ROOT_FOLDER_ID = 0;

    // Auxiliary query constants.
    private static final Integer VALUE_IS_BOOKMARK = 1;
    private static final String SELECT_IS_BOOKMARK = BookmarkColumns.BOOKMARK + "="
            + VALUE_IS_BOOKMARK.toString();
    private static final String HAS_URL = BookmarkColumns.URL + "=?";
    private static final String[] EXISTS_PROJECTION = new String[]{ BookmarkColumns.URL };

    protected final Context mContext;

    private ImportBookmarksTask mTask;

    protected BookmarkImporter(Context context) {
        mContext = context;
    }

    /** Asynchronously import bookmarks from another browser */
    public void importBookmarks(OnBookmarksImportedListener listener) {
        mTask = new ImportBookmarksTask(listener);
        mTask.execute();
    }

    public void cancel() {
        mTask.cancel(true);
    }

    /**
     * Handles loading Android Browser bookmarks in a background thread.
     */
    private class ImportBookmarksTask extends AsyncTask<Void, Void, ImportResults> {
        private final OnBookmarksImportedListener mBookmarksImportedListener;

        ImportBookmarksTask(OnBookmarksImportedListener listener) {
            mBookmarksImportedListener = listener;
        }

        @Override
        protected ImportResults doInBackground(Void... params) {
            BookmarkIterator[] iterators = null;
            try {
                iterators = availableBookmarks();
            } catch (Exception e) {
                Log.w(TAG, "Unexpected exception while requesting available bookmarks: "
                        + e.getMessage());
                return null;
            }

            if (iterators == null) {
                Log.e(TAG, "No bookmark iterators found.");
                return null;
            }

            for (BookmarkIterator iterator : iterators) {
                ImportResults results = importFromIterator(iterator);
                if (results != null) return results;
            }

            return null;
        }

        @Override
        protected void onPostExecute(ImportResults results) {
            if (mBookmarksImportedListener != null) {
                mBookmarksImportedListener.onBookmarksImported(results);
            }
        }

        private ImportResults importFromIterator(BookmarkIterator bookmarkIterator) {
            try {
                if (bookmarkIterator == null) return null;

                // Get a snapshot of the bookmarks.
                LinkedHashMap<Long, Bookmark> idMap = new LinkedHashMap<Long, Bookmark>();
                HashSet<String> urlSet = new HashSet<String>();

                // The root folder is used for hierarchy reconstruction purposes only.
                // Bookmarks are directly imported into the Mobile Bookmarks folder.
                Bookmark rootFolder = createRootFolderBookmark();
                idMap.put(ROOT_FOLDER_ID, rootFolder);

                int failedImports = 0;
                while (bookmarkIterator.hasNext()) {
                    Bookmark bookmark = bookmarkIterator.next();
                    if (bookmark == null) {
                        ++failedImports;
                        continue;
                    }

                    // Check for duplicate ids.
                    if (idMap.containsKey(bookmark.id)) {
                        Log.e(TAG, "Duplicate bookmark id: " +  bookmark.id
                                + ". Dropping bookmark.");
                        ++failedImports;
                        continue;
                    }

                    // Check for duplicate URLs.
                    if (!bookmark.isFolder && urlSet.contains(bookmark.url)) {
                        Log.i(TAG, "More than one bookmark pointing to " + bookmark.url
                                + ". Keeping only the first one for consistency with Chromium.");
                        continue;
                    }

                    // Reject bookmarks that already exist in the native model.
                    if (alreadyExists(bookmark)) continue;

                    idMap.put(bookmark.id, bookmark);
                    urlSet.add(bookmark.url);
                }
                bookmarkIterator.close();

                // Abort if no new bookmarks to import.
                ImportResults results = new ImportResults();
                results.rootFolderId = rootFolder.nativeId;
                results.newBookmarks = idMap.size() + failedImports - 1;
                if (results.newBookmarks == 0) return results;

                // Check if all imports failed.
                if (idMap.size() == 1 && failedImports > 0) return null;

                // Recreate the folder hierarchy and import it.
                recreateFolderHierarchy(idMap);
                importBookmarkHierarchy(rootFolder, results);

                return results;
            } catch (Exception e) {
                Log.w(TAG, "Unexpected exception while importing bookmarks: " + e.getMessage());
                return null;
            }
        }

        private ContentValues getBookmarkValues(Bookmark bookmark) {
            ContentValues values = new ContentValues();
            values.put(BookmarkColumns.BOOKMARK, VALUE_IS_BOOKMARK);
            values.put(BookmarkColumns.URL, bookmark.url);
            values.put(BookmarkColumns.TITLE, bookmark.title);
            values.put(ChromeBrowserProvider.BOOKMARK_PARENT_ID_PARAM, bookmark.parent.nativeId);
            if (bookmark.created != null) values.put(BookmarkColumns.CREATED, bookmark.created);
            if (bookmark.lastVisit != null) values.put(BookmarkColumns.DATE, bookmark.lastVisit);
            if (bookmark.visits != null) {
                // TODO(michaelbai) http://crbug.com/149376, http://b/6362473
                // See android_provider_backend.cc IsHistoryAndBookmarkRowValid().
                if (bookmark.created != null && bookmark.lastVisit != null
                        && bookmark.visits.longValue() > 2
                        && bookmark.lastVisit.longValue() - bookmark.created.longValue()
                                > bookmark.visits.longValue()) {
                    values.put(BookmarkColumns.VISITS, bookmark.visits);
                }
            }
            if (bookmark.favicon != null) values.put(BookmarkColumns.FAVICON, bookmark.favicon);
            return values;
        }

        private boolean alreadyExists(Bookmark bookmark) {
            // Folders are re-used if they already exist. No need to filter them out.
            if (bookmark.isFolder) return false;

            Cursor cursor = mContext.getContentResolver().query(
                    ChromeBrowserProvider.getBookmarksApiUri(mContext), EXISTS_PROJECTION,
                    SELECT_IS_BOOKMARK + " AND " + HAS_URL, new String[]{ bookmark.url }, null);
            if (cursor != null) {
                boolean exists = cursor.getCount() > 0;
                cursor.close();
                return exists;
            }
            return false;
        }

        private void recreateFolderHierarchy(LinkedHashMap<Long, Bookmark> idMap) {
            for (Bookmark bookmark : idMap.values()) {
                if (bookmark.id == ROOT_FOLDER_ID) continue;

                // Look for invalid parent ids and self-cycles.
                if (!idMap.containsKey(bookmark.parentId) || bookmark.parentId == bookmark.id) {
                    bookmark.parent = idMap.get(ROOT_FOLDER_ID);
                    bookmark.parent.entries.add(bookmark);
                    continue;
                }

                bookmark.parent = idMap.get(bookmark.parentId);
                bookmark.parent.entries.add(bookmark);
            }
        }

        private Bookmark createRootFolderBookmark() {
            Bookmark root = new Bookmark();
            root.id = ROOT_FOLDER_ID;
            root.nativeId = ChromeBrowserProviderClient.getMobileBookmarksFolderId(mContext);
            root.parentId = ROOT_FOLDER_ID;
            root.parent = root;
            root.isFolder = true;
            return root;
        }

        private void importBookmarkHierarchy(Bookmark bookmark, ImportResults results) {
            // Avoid cycles in the hierarchy that could lead to infinite loops.
            if (bookmark.processed) return;
            bookmark.processed = true;

            if (bookmark.isFolder) {
                if (bookmark.id != ROOT_FOLDER_ID) {
                    bookmark.nativeId = ChromeBrowserProviderClient.createBookmarksFolderOnce(
                            mContext, bookmark.title, bookmark.parent.nativeId);
                    ++results.numImported;
                }

                if (bookmark.nativeId == ChromeBrowserProviderClient.INVALID_BOOKMARK_ID
                        && bookmark.id != ROOT_FOLDER_ID) {
                    Log.e(TAG, "Error creating the folder '" + bookmark.title
                            + "'. Skipping entries.");
                    return;
                }

                for (Bookmark entry : bookmark.entries) {
                    if (entry.parent != bookmark) {
                        Log.w(TAG, "Hierarchy error in bookmark '" + bookmark.title
                                + "'. Skipping.");
                        continue;
                    }
                    importBookmarkHierarchy(entry, results);
                }
            } else {
                sanitizeBookmarkDates(bookmark);
                ContentValues values = getBookmarkValues(bookmark);
                try {
                    // Check if the URL already exists in the database.
                    String[] urlArgs = new String[]{ bookmark.url };
                    Uri bookmarksApiUri = ChromeBrowserProvider.getBookmarksApiUri(mContext);
                    Cursor history = mContext.getContentResolver().query(
                            bookmarksApiUri, null, HAS_URL, urlArgs, null);
                    boolean alreadyExists = history != null && history.getCount() > 0;
                    if (history != null) history.close();

                    if (alreadyExists) {
                        // If so, update the existing information.
                        if (mContext.getContentResolver().update(
                                bookmarksApiUri, values, HAS_URL, urlArgs) == 0) {
                            throw new IllegalArgumentException(
                                    "Couldn't update the existing history information");
                        }
                    } else {
                        // Otherwise insert the new information.
                        if (mContext.getContentResolver().insert(
                                bookmarksApiUri, values) == null) {
                            throw new IllegalArgumentException(
                                    "Couldn't insert the bookmark");
                        }
                    }
                    ++results.numImported;
                } catch (IllegalArgumentException e) {
                    Log.w(TAG, "Error inserting bookmark " + bookmark.title + ": "
                            + e.getMessage());
                }
            }
        }

        // Sanitize timestamp inputs as the provider backend might reject some of the bookmarks
        // if the values are inconsistent.
        private void sanitizeBookmarkDates(Bookmark bookmark) {
            final long now = System.currentTimeMillis();
            if (bookmark.created != null && bookmark.created.longValue() > now) {
                bookmark.created = Long.valueOf(now);
            }

            if (bookmark.lastVisit != null && bookmark.lastVisit.longValue() > now) {
                bookmark.lastVisit = Long.valueOf(now);
            }

            if (bookmark.created != null && bookmark.lastVisit != null
                    && bookmark.created.longValue() > bookmark.lastVisit.longValue()) {
                bookmark.created = bookmark.lastVisit;
            }

            // The provider backend assumes one visit per timestamp and actually checks this.
            if (bookmark.lastVisit != null && bookmark.created != null && bookmark.visits != null) {
                long maxVisits = bookmark.lastVisit.longValue() - bookmark.created.longValue() + 1;
                if (bookmark.visits.longValue() > maxVisits) {
                    bookmark.visits = Long.valueOf(maxVisits);
                }
            }
        }
    }
}

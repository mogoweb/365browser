// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.photo_picker;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;

import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.widget.selection.SelectableListLayout;
import org.chromium.chrome.browser.widget.selection.SelectionDelegate;
import org.chromium.ui.PhotoPickerListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A class for keeping track of common data associated with showing photos in
 * the photo picker, for example the RecyclerView and the bitmap caches.
 */
public class PickerCategoryView extends RelativeLayout
        implements FileEnumWorkerTask.FilesEnumeratedCallback, RecyclerView.RecyclerListener,
                   DecoderServiceHost.ServiceReadyCallback, View.OnClickListener {
    private static final int KILOBYTE = 1024;

    // The dialog that owns us.
    private PhotoPickerDialog mDialog;

    // The view containing the RecyclerView and the toolbar, etc.
    private SelectableListLayout<PickerBitmap> mSelectableListLayout;

    // Our context.
    private Context mContext;

    // The list of images on disk, sorted by last-modified first.
    private List<PickerBitmap> mPickerBitmaps;

    // True if multi-selection is allowed in the picker.
    private boolean mMultiSelectionAllowed;

    // The callback to notify the listener of decisions reached in the picker.
    private PhotoPickerListener mListener;

    // The host class for the decoding service.
    private DecoderServiceHost mDecoderServiceHost;

    // The RecyclerView showing the images.
    private RecyclerView mRecyclerView;

    // The {@link PickerAdapter} for the RecyclerView.
    private PickerAdapter mPickerAdapter;

    // The layout manager for the RecyclerView.
    private GridLayoutManager mLayoutManager;

    // The decoration to use for the RecyclerView.
    private GridSpacingItemDecoration mSpacingDecoration;

    // The {@link SelectionDelegate} keeping track of which images are selected.
    private SelectionDelegate<PickerBitmap> mSelectionDelegate;

    // A low-resolution cache for images. Helpful for cache misses from the high-resolution cache
    // to avoid showing gray squares (we show pixelated versions instead until image can be loaded
    // off disk, which is much less jarring).
    private LruCache<String, Bitmap> mLowResBitmaps;

    // A high-resolution cache for images.
    private LruCache<String, Bitmap> mHighResBitmaps;

    /**
     * The number of columns to show. Note: mColumns and mPadding (see below) should both be even
     * numbers or both odd, not a mix (the column padding will not be of uniform thickness if they
     * are a mix).
     */
    private int mColumns;

    // The padding between columns. See also comment for mColumns.
    private int mPadding;

    // The size of the bitmaps (equal length for width and height).
    private int mImageSize;

    // A worker task for asynchronously enumerating files off the main thread.
    private FileEnumWorkerTask mWorkerTask;

    // Whether the connection to the service has been established.
    private boolean mServiceReady;

    // A list of files to use for testing (instead of reading files on disk).
    private static List<PickerBitmap> sTestFiles;

    public PickerCategoryView(Context context) {
        super(context);
        postConstruction(context);
    }

    /**
     * A helper function for initializing the PickerCategoryView.
     * @param context The context to use.
     */
    @SuppressWarnings("unchecked") // mSelectableListLayout
    private void postConstruction(Context context) {
        mContext = context;

        mDecoderServiceHost = new DecoderServiceHost(this);
        mDecoderServiceHost.bind(mContext);

        enumerateBitmaps();

        mSelectionDelegate = new SelectionDelegate<PickerBitmap>();

        View root = LayoutInflater.from(context).inflate(R.layout.photo_picker_dialog, this);
        mSelectableListLayout =
                (SelectableListLayout<PickerBitmap>) root.findViewById(R.id.selectable_list);

        mPickerAdapter = new PickerAdapter(this);
        mRecyclerView = mSelectableListLayout.initializeRecyclerView(mPickerAdapter);
        PhotoPickerToolbar toolbar = (PhotoPickerToolbar) mSelectableListLayout.initializeToolbar(
                R.layout.photo_picker_toolbar, mSelectionDelegate,
                R.string.photo_picker_select_images, null, 0, 0, R.color.default_primary_color,
                null);
        toolbar.setNavigationOnClickListener(this);
        Button doneButton = (Button) toolbar.findViewById(R.id.done);
        doneButton.setOnClickListener(this);

        calculateGridMetrics();

        mLayoutManager = new GridLayoutManager(mContext, mColumns);
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setLayoutManager(mLayoutManager);
        mSpacingDecoration = new GridSpacingItemDecoration(mColumns, mPadding);
        mRecyclerView.addItemDecoration(mSpacingDecoration);

        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / KILOBYTE);
        final int cacheSizeLarge = maxMemory / 2; // 1/2 of the available memory.
        final int cacheSizeSmall = maxMemory / 8; // 1/8th of the available memory.
        mLowResBitmaps = new LruCache<String, Bitmap>(cacheSizeSmall);
        mHighResBitmaps = new LruCache<String, Bitmap>(cacheSizeLarge);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        calculateGridMetrics();
        mLayoutManager.setSpanCount(mColumns);
        mRecyclerView.removeItemDecoration(mSpacingDecoration);
        mSpacingDecoration = new GridSpacingItemDecoration(mColumns, mPadding);
        mRecyclerView.addItemDecoration(mSpacingDecoration);
    }

    /**
     * Severs the connection to the decoding utility process and cancels any outstanding requests.
     */
    public void onDialogDismissed() {
        if (mWorkerTask != null) {
            mWorkerTask.cancel(true);
            mWorkerTask = null;
        }

        if (mDecoderServiceHost != null) {
            mDecoderServiceHost.unbind(mContext);
            mDecoderServiceHost = null;
        }
    }

    /**
     * Initializes the PickerCategoryView object.
     * @param dialog The dialog showing us.
     * @param listener The listener who should be notified of actions.
     * @param multiSelectionAllowed Whether to allow the user to select more than one image.
     */
    public void initialize(
            PhotoPickerDialog dialog, PhotoPickerListener listener, boolean multiSelectionAllowed) {
        if (!multiSelectionAllowed) mSelectionDelegate.setSingleSelectionMode();

        mDialog = dialog;
        mMultiSelectionAllowed = multiSelectionAllowed;
        mListener = listener;
    }

    // FileEnumWorkerTask.FilesEnumeratedCallback:

    @Override
    public void filesEnumeratedCallback(List<PickerBitmap> files) {
        mPickerBitmaps = files;
        processBitmaps();
    }

    // DecoderServiceHost.ServiceReadyCallback:

    @Override
    public void serviceReady() {
        mServiceReady = true;
        processBitmaps();
    }

    // RecyclerView.RecyclerListener:

    @Override
    public void onViewRecycled(RecyclerView.ViewHolder holder) {
        PickerBitmapViewHolder bitmapHolder = (PickerBitmapViewHolder) holder;
        String filePath = bitmapHolder.getFilePath();
        if (filePath != null) {
            getDecoderServiceHost().cancelDecodeImage(filePath);
        }
    }

    // OnClickListener:

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.done) {
            notifyPhotosSelected();
        } else {
            mListener.onPickerUserAction(PhotoPickerListener.Action.CANCEL, null);
        }

        mDialog.dismiss();
    }

    /**
     * Start loading of bitmaps, once files have been enumerated and service is
     * ready to decode.
     */
    private void processBitmaps() {
        if (mServiceReady && mPickerBitmaps != null) {
            mPickerAdapter.notifyDataSetChanged();
        }
    }

    // Simple accessors:

    public int getImageSize() {
        return mImageSize;
    }

    public SelectionDelegate<PickerBitmap> getSelectionDelegate() {
        return mSelectionDelegate;
    }

    public List<PickerBitmap> getPickerBitmaps() {
        return mPickerBitmaps;
    }

    public DecoderServiceHost getDecoderServiceHost() {
        return mDecoderServiceHost;
    }

    public LruCache<String, Bitmap> getLowResBitmaps() {
        return mLowResBitmaps;
    }

    public LruCache<String, Bitmap> getHighResBitmaps() {
        return mHighResBitmaps;
    }

    public boolean isMultiSelectAllowed() {
        return mMultiSelectionAllowed;
    }

    /**
     * Notifies the listener that the user selected to launch the gallery.
     */
    public void showGallery() {
        mListener.onPickerUserAction(PhotoPickerListener.Action.LAUNCH_GALLERY, null);
    }

    /**
     * Notifies the listener that the user selected to launch the camera intent.
     */
    public void showCamera() {
        mListener.onPickerUserAction(PhotoPickerListener.Action.LAUNCH_CAMERA, null);
    }

    /**
     * Calculates image size and how many columns can fit on-screen.
     */
    private void calculateGridMetrics() {
        Rect appRect = new Rect();
        ((Activity) mContext).getWindow().getDecorView().getWindowVisibleDisplayFrame(appRect);

        int width = appRect.width();
        int minSize =
                mContext.getResources().getDimensionPixelSize(R.dimen.photo_picker_tile_min_size);
        mPadding = mContext.getResources().getDimensionPixelSize(R.dimen.photo_picker_tile_gap);
        mColumns = Math.max(1, (width - mPadding) / (minSize + mPadding));
        mImageSize = (width - mPadding * (mColumns + 1)) / (mColumns);

        // Make sure columns and padding are either both even or both odd.
        if (((mColumns % 2) == 0) != ((mPadding % 2) == 0)) {
            mPadding++;
        }
    }

    /**
     * Asynchronously enumerates bitmaps on disk.
     */
    private void enumerateBitmaps() {
        if (sTestFiles != null) {
            filesEnumeratedCallback(sTestFiles);
            return;
        }

        if (mWorkerTask != null) {
            mWorkerTask.cancel(true);
        }

        mWorkerTask =
                new FileEnumWorkerTask(this, new MimeTypeFileFilter(Arrays.asList("image/*")));
        mWorkerTask.execute();
    }

    /**
     * Notifies any listeners that one or more photos have been selected.
     */
    private void notifyPhotosSelected() {
        List<PickerBitmap> selectedFiles = mSelectionDelegate.getSelectedItems();
        String[] photos = new String[selectedFiles.size()];
        int i = 0;
        for (PickerBitmap bitmap : selectedFiles) {
            photos[i++] = bitmap.getFilePath();
        }

        mListener.onPickerUserAction(PhotoPickerListener.Action.PHOTOS_SELECTED, photos);
    }

    /**
     * A class for implementing grid spacing between items.
     */
    private class GridSpacingItemDecoration extends RecyclerView.ItemDecoration {
        // The number of spans to account for.
        private int mSpanCount;

        // The amount of spacing to use.
        private int mSpacing;

        public GridSpacingItemDecoration(int spanCount, int spacing) {
            mSpanCount = spanCount;
            mSpacing = spacing;
        }

        @Override
        public void getItemOffsets(
                Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
            int left = 0, right = 0, top = 0, bottom = 0;
            int position = parent.getChildAdapterPosition(view);

            if (position >= 0) {
                int column = position % mSpanCount;

                left = mSpacing - ((column * mSpacing) / mSpanCount);
                right = (column + 1) * mSpacing / mSpanCount;

                if (position < mSpanCount) {
                    top = mSpacing;
                }
                bottom = mSpacing;
            }

            outRect.set(left, top, right, bottom);
        }
    }

    /** Sets a list of files to use as data for the dialog. For testing use only. */
    @VisibleForTesting
    public static void setTestFiles(List<PickerBitmap> testFiles) {
        sTestFiles = new ArrayList<>(testFiles);
    }

    @VisibleForTesting
    public SelectionDelegate<PickerBitmap> getSelectionDelegateForTesting() {
        return mSelectionDelegate;
    }
}

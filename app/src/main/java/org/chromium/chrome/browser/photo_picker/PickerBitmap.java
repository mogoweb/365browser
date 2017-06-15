// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.photo_picker;

import android.support.annotation.IntDef;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.annotations.SuppressFBWarnings;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * A class to keep track of the meta data associated with a an image in the photo picker.
 */
@SuppressFBWarnings("EQ_COMPARETO_USE_OBJECT_EQUALS")
public class PickerBitmap implements Comparable<PickerBitmap> {
    // The possible types of tiles involved in the viewer.
    @IntDef({PICTURE, CAMERA, GALLERY})
    @Retention(RetentionPolicy.SOURCE)
    public @interface TileTypes {}
    public static final int PICTURE = 0;
    public static final int CAMERA = 1;
    public static final int GALLERY = 2;

    // The file path to the bitmap to show.
    private String mFilePath;

    // When the bitmap was last modified on disk.
    private long mLastModified;

    // The type of tile involved.
    @TileTypes
    private int mType;

    /**
     * The PickerBitmap constructor.
     * @param filePath The file path to the bitmap to show.
     * @param lastModified When the bitmap was last modified on disk.
     * @param type The type of tile involved.
     */
    public PickerBitmap(String filePath, long lastModified, @TileTypes int type) {
        mFilePath = filePath;
        mLastModified = lastModified;
        mType = type;
    }

    /**
     * Accessor for the filepath.
     * @return The file path for this PickerBitmap object.
     */
    public String getFilePath() {
        return mFilePath;
    }

    /**
     * Accessor for the tile type.
     * @return The type of tile involved for this bitmap object.
     */
    @TileTypes
    public int type() {
        return mType;
    }

    /**
     * A comparison function for PickerBitmaps (results in a last-modified first sort).
     * @param other The PickerBitmap to compare it to.
     * @return 0, 1, or -1, depending on which is bigger.
     */
    @Override
    public int compareTo(PickerBitmap other) {
        return ApiCompatibilityUtils.compareLong(other.mLastModified, mLastModified);
    }
}

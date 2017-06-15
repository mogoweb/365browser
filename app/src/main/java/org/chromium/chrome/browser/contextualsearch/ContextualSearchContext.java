// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.contextualsearch;

import android.text.TextUtils;

import org.chromium.base.annotations.CalledByNative;

import javax.annotation.Nullable;

/**
 * Provides a context in which to search, and links to the native ContextualSearchContext.
 * Includes the selection, selection offsets, surrounding page content, etc.
 * Requires an override of #onSelectionChanged to call when a non-empty selection is established
 * or changed.
 */
public abstract class ContextualSearchContext {
    static final int INVALID_SELECTION_OFFSET = -1;

    // Pointer to the native instance of this class.
    private long mNativePointer;

    // Whether this context has had the required properties set so it can Resolve a Search Term.
    private boolean mHasSetResolveProperties;

    // A shortened version of the actual text content surrounding the selection, or null if not yet
    // established.
    private String mSurroundingText;

    // The start and end offsets of the selection within the text content.
    private int mSelectionStartOffset = INVALID_SELECTION_OFFSET;
    private int mSelectionEndOffset = INVALID_SELECTION_OFFSET;

    // The initial word selected by a Tap, or null.
    private String mInitialSelectedWord;

    // The original encoding of the base page.
    private String mEncoding;

    /**
     * Constructs a context that tracks the selection and some amount of page content.
     */
    ContextualSearchContext() {
        mNativePointer = nativeInit();
        mHasSetResolveProperties = false;
    }

    /**
     * Updates a context to be able to resolve a search term and have a large amount of
     * page content.
     * @param homeCountry The country where the user usually resides, or an empty string if not
     *        known.
     * @param maySendBasePageUrl Whether policy allows sending the base-page URL to the server.
     */
    void setResolveProperties(String homeCountry, boolean maySendBasePageUrl) {
        mHasSetResolveProperties = true;
        nativeSetResolveProperties(getNativePointer(), homeCountry, maySendBasePageUrl);
    }

    /**
     * This method should be called to clean up storage when an instance of this class is
     * no longer in use.  The nativeDestroy will call the destructor on the native instance.
     */
    void destroy() {
        assert mNativePointer != 0;
        nativeDestroy(mNativePointer);
        mNativePointer = 0;

        // Also zero out private data that may be sizable.
        mSurroundingText = null;
    }

    /**
     * Sets the surrounding text and selection offsets.
     * @param encoding The original encoding of the base page.
     * @param surroundingText The text from the base page surrounding the selection.
     * @param startOffset The offset of start the selection.
     * @param endOffset The offset of the end of the selection
     */
    void setSurroundingText(
            String encoding, String surroundingText, int startOffset, int endOffset) {
        mEncoding = encoding;
        mSurroundingText = surroundingText;
        mSelectionStartOffset = startOffset;
        mSelectionEndOffset = endOffset;
        // Notify of an initial selection if it's not empty.
        if (endOffset > startOffset) onSelectionChanged();
    }

    /**
     * @return The text that surrounds the selection, or {@code null} if none yet known.
     */
    @Nullable
    String getSurroundingText() {
        return mSurroundingText;
    }

    /**
     * @return The offset into the surrounding text of the start of the selection, or
     *         {@link #INVALID_SELECTION_OFFSET} if not yet established.
     */
    int getSelectionStartOffset() {
        return mSelectionStartOffset;
    }

    /**
     * @return The offset into the surrounding text of the end of the selection, or
     *         {@link #INVALID_SELECTION_OFFSET} if not yet established.
     */
    int getSelectionEndOffset() {
        return mSelectionEndOffset;
    }

    /**
     * @return The original encoding of the base page.
     */
    String getEncoding() {
        return mEncoding;
    }

    /**
     * @return The initial word selected by a Tap.
     */
    String getInitialSelectedWord() {
        return mInitialSelectedWord;
    }

    /**
     * @return The text content that follows the selection (one side of the surrounding text).
     */
    String getTextContentFollowingSelection() {
        if (mSurroundingText != null && mSelectionEndOffset > 0
                && mSelectionEndOffset <= mSurroundingText.length()) {
            return mSurroundingText.substring(mSelectionEndOffset);
        } else {
            return "";
        }
    }

    /**
     * @return Whether this context can Resolve the Search Term.
     */
    boolean canResolve() {
        return mHasSetResolveProperties && mSelectionStartOffset != INVALID_SELECTION_OFFSET
                && mSelectionEndOffset != INVALID_SELECTION_OFFSET
                && mSelectionEndOffset > mSelectionStartOffset;
    }

    /**
     * Notifies of an adjustment that has been applied to the start and end of the selection.
     * @param startAdjust A signed value indicating the direction of the adjustment to the start of
     *        the selection (typically a negative value when the selection expands).
     * @param endAdjust A signed value indicating the direction of the adjustment to the end of
     *        the selection (typically a positive value when the selection expands).
     */
    void onSelectionAdjusted(int startAdjust, int endAdjust) {
        // Fully track the selection as it changes.
        mSelectionStartOffset += startAdjust;
        mSelectionEndOffset += endAdjust;
        if (TextUtils.isEmpty(mInitialSelectedWord) && !TextUtils.isEmpty(mSurroundingText)) {
            // TODO(donnd): investigate the root cause of crbug.com/725027 that requires this
            // additional validation to prevent this substring call from crashing!
            if (mSelectionEndOffset < mSelectionStartOffset
                    || mSelectionEndOffset > mSurroundingText.length()) {
                return;
            }

            mInitialSelectedWord =
                    mSurroundingText.substring(mSelectionStartOffset, mSelectionEndOffset);
        }
        nativeAdjustSelection(getNativePointer(), startAdjust, endAdjust);
        // Notify of changes.
        onSelectionChanged();
    }

    /**
     * Notifies this instance that the selection has been changed.
     */
    abstract void onSelectionChanged();

    // TODO(donnd): Add a test for this class!

    // ============================================================================================
    // Native callback support.
    // ============================================================================================

    @CalledByNative
    private long getNativePointer() {
        assert mNativePointer != 0;
        return mNativePointer;
    }

    // ============================================================================================
    // Native methods.
    // ============================================================================================
    private native long nativeInit();
    private native void nativeDestroy(long nativeContextualSearchContext);
    private native void nativeSetResolveProperties(
            long nativeContextualSearchContext, String homeCountry, boolean maySendBasePageUrl);
    private native void nativeAdjustSelection(
            long nativeContextualSearchContext, int startAdjust, int endAdjust);
}

// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.omnibox;

import android.content.Context;
import android.graphics.Rect;
import android.os.StrictMode;
import android.text.Editable;
import android.text.Selection;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.widget.EditText;

import org.chromium.base.Log;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.widget.VerticallyFixedEditText;

/**
 * An {@link EditText} that shows autocomplete text at the end.
 */
public class AutocompleteEditText extends VerticallyFixedEditText {
    private static final String TAG = "cr_AutocompleteEdit";

    private static final boolean DEBUG = false;

    private final AutocompleteSpan mAutocompleteSpan;
    private final AccessibilityManager mAccessibilityManager;

    /**
     * Whether default TextView scrolling should be disabled because autocomplete has been added.
     * This allows the user entered text to be shown instead of the end of the autocomplete.
     */
    private boolean mDisableTextScrollingFromAutocomplete;

    private boolean mInBatchEditMode;
    private int mBeforeBatchEditAutocompleteIndex = -1;
    private String mBeforeBatchEditFullText;
    private boolean mSelectionChangedInBatchMode;
    private boolean mTextDeletedInBatchMode;

    // Set to true when the text is modified programmatically. Initially set to true until the old
    // state has been loaded.
    private boolean mIgnoreTextChangeFromAutocomplete = true;
    private boolean mLastEditWasDelete;

    private boolean mIgnoreImeForTest;

    public AutocompleteEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        mAutocompleteSpan = new AutocompleteSpan();
        mAccessibilityManager =
                (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
    }

    /**
     * Sets whether text changes should trigger autocomplete.
     *
     * @param ignoreAutocomplete Whether text changes should be ignored and no auto complete
     *                           triggered.
     */
    public void setIgnoreTextChangesForAutocomplete(boolean ignoreAutocomplete) {
        if (DEBUG) Log.i(TAG, "setIgnoreTextChangesForAutocomplete: " + ignoreAutocomplete);
        mIgnoreTextChangeFromAutocomplete = ignoreAutocomplete;
    }

    /** @return Text that includes autocomplete. */
    public String getTextWithAutocomplete() {
        return getEditableText() != null ? getEditableText().toString() : "";
    }

    /**
     * @return Whether the current cursor position is at the end of the user typed text (i.e.
     *         at the beginning of the inline autocomplete text if present otherwise the very
     *         end of the current text).
     */
    private boolean isCursorAtEndOfTypedText() {
        final int selectionStart = getSelectionStart();
        final int selectionEnd = getSelectionEnd();

        int expectedSelectionStart = getText().getSpanStart(mAutocompleteSpan);
        int expectedSelectionEnd = getText().length();
        if (expectedSelectionStart < 0) {
            expectedSelectionStart = expectedSelectionEnd;
        }

        return selectionStart == expectedSelectionStart && selectionEnd == expectedSelectionEnd;
    }

    /**
     * @return Whether the URL is currently in batch edit mode triggered by an IME.  No external
     *         text changes should be triggered while this is true.
     */
    // isInBatchEditMode is a package protected method on TextView, so we intentionally chose
    // a different name.
    private boolean isHandlingBatchInput() {
        return mInBatchEditMode;
    }

    /**
     * @return The user text without the autocomplete text.
     */
    public String getTextWithoutAutocomplete() {
        int autoCompleteIndex = getText().getSpanStart(mAutocompleteSpan);
        if (autoCompleteIndex < 0) {
            return getTextWithAutocomplete();
        } else {
            return getTextWithAutocomplete().substring(0, autoCompleteIndex);
        }
    }

    /** @return Whether any autocomplete information is specified on the current text. */
    @VisibleForTesting
    public boolean hasAutocomplete() {
        return getText().getSpanStart(mAutocompleteSpan) >= 0
                || mAutocompleteSpan.mAutocompleteText != null
                || mAutocompleteSpan.mUserText != null;
    }

    /**
     * Whether we want to be showing inline autocomplete results. We don't want to show them as the
     * user deletes input. Also if there is a composition (e.g. while using the Japanese IME),
     * we must not autocomplete or we'll destroy the composition.
     * @return Whether we want to be showing inline autocomplete results.
     */
    public boolean shouldAutocomplete() {
        if (mLastEditWasDelete) return false;
        Editable text = getText();

        return isCursorAtEndOfTypedText() && !isHandlingBatchInput()
                && BaseInputConnection.getComposingSpanEnd(text)
                == BaseInputConnection.getComposingSpanStart(text);
    }

    @Override
    public void onBeginBatchEdit() {
        if (DEBUG) Log.i(TAG, "onBeginBatchEdit");
        mBeforeBatchEditAutocompleteIndex = getText().getSpanStart(mAutocompleteSpan);
        mBeforeBatchEditFullText = getText().toString();

        super.onBeginBatchEdit();
        mInBatchEditMode = true;
        mTextDeletedInBatchMode = false;
    }

    @Override
    public void onEndBatchEdit() {
        if (DEBUG) Log.i(TAG, "onEndBatchEdit");
        super.onEndBatchEdit();
        mInBatchEditMode = false;
        if (mSelectionChangedInBatchMode) {
            validateSelection(getSelectionStart(), getSelectionEnd());
            mSelectionChangedInBatchMode = false;
        }

        String newText = getText().toString();
        if (!TextUtils.equals(mBeforeBatchEditFullText, newText)
                || getText().getSpanStart(mAutocompleteSpan) != mBeforeBatchEditAutocompleteIndex) {
            // If the text being typed is a single character that matches the next character in the
            // previously visible autocomplete text, we reapply the autocomplete text to prevent
            // a visual flickering when the autocomplete text is cleared and then quickly reapplied
            // when the next round of suggestions is received.
            if (shouldAutocomplete() && mBeforeBatchEditAutocompleteIndex != -1
                    && mBeforeBatchEditFullText != null
                    && mBeforeBatchEditFullText.startsWith(newText) && !mTextDeletedInBatchMode
                    && newText.length() - mBeforeBatchEditAutocompleteIndex == 1) {
                setAutocompleteText(newText, mBeforeBatchEditFullText.substring(newText.length()));
            }
            notifyAutocompleteTextStateChanged(mTextDeletedInBatchMode, true);
        }

        mTextDeletedInBatchMode = false;
        mBeforeBatchEditAutocompleteIndex = -1;
        mBeforeBatchEditFullText = null;
    }

    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        if (DEBUG) Log.i(TAG, "onSelectionChanged -- selStart: %d, selEnd: %d", selStart, selEnd);
        if (!mInBatchEditMode) {
            int beforeTextLength = getText().length();
            if (validateSelection(selStart, selEnd)) {
                boolean textDeleted = getText().length() < beforeTextLength;
                notifyAutocompleteTextStateChanged(textDeleted, false);
            }
        } else {
            mSelectionChangedInBatchMode = true;
        }
        super.onSelectionChanged(selStart, selEnd);
    }

    /**
     * Validates the selection and clears the autocomplete span if needed.  The autocomplete text
     * will be deleted if the selection occurs entirely before the autocomplete region.
     *
     * @param selStart The start of the selection.
     * @param selEnd The end of the selection.
     * @return Whether the autocomplete span was removed as a result of this validation.
     */
    private boolean validateSelection(int selStart, int selEnd) {
        int spanStart = getText().getSpanStart(mAutocompleteSpan);
        int spanEnd = getText().getSpanEnd(mAutocompleteSpan);

        if (DEBUG) {
            Log.i(TAG, "validateSelection -- selStart: %d, selEnd: %d, spanStart: %d, spanEnd: %d",
                    selStart, selEnd, spanStart, spanEnd);
        }

        if (spanStart >= 0 && (spanStart != selStart || spanEnd != selEnd)) {
            CharSequence previousAutocompleteText = mAutocompleteSpan.mAutocompleteText;

            // On selection changes, the autocomplete text has been accepted by the user or needs
            // to be deleted below.
            mAutocompleteSpan.clearSpan();

            // The autocomplete text will be deleted any time the selection occurs entirely before
            // the start of the autocomplete text.  This is required because certain keyboards will
            // insert characters temporarily when starting a key entry gesture (whether it be
            // swyping a word or long pressing to get a special character).  When this temporary
            // character appears, Chrome may decide to append some autocomplete, but the keyboard
            // will then remove this temporary character only while leaving the autocomplete text
            // alone.  See crbug/273763 for more details.
            if (selEnd <= spanStart
                    && TextUtils.equals(previousAutocompleteText,
                               getText().subSequence(spanStart, getText().length()))) {
                getText().delete(spanStart, getText().length());
            }
            return true;
        }
        return false;
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        if (!focused) mAutocompleteSpan.clearSpan();
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
    }

    @Override
    public boolean bringPointIntoView(int offset) {
        if (mDisableTextScrollingFromAutocomplete) return false;
        return super.bringPointIntoView(offset);
    }

    @Override
    public boolean onPreDraw() {
        boolean retVal = super.onPreDraw();
        if (mDisableTextScrollingFromAutocomplete) {
            // super.onPreDraw will put the selection at the end of the text selection, but
            // in the case of autocomplete we want the last typed character to be shown, which
            // is the start of selection.
            mDisableTextScrollingFromAutocomplete = false;
            bringPointIntoView(getSelectionStart());
            retVal = true;
        }
        return retVal;
    }

    /**
     * Autocompletes the text on the url bar and selects the text that was not entered by the
     * user. Using append() instead of setText() to preserve the soft-keyboard layout.
     * @param userText user The text entered by the user.
     * @param inlineAutocompleteText The suggested autocompletion for the user's text.
     */
    public void setAutocompleteText(CharSequence userText, CharSequence inlineAutocompleteText) {
        if (DEBUG) {
            Log.i(TAG, "setAutocompleteText -- userText: %s, inlineAutocompleteText: %s", userText,
                    inlineAutocompleteText);
        }
        boolean emptyAutocomplete = TextUtils.isEmpty(inlineAutocompleteText);

        if (!emptyAutocomplete) mDisableTextScrollingFromAutocomplete = true;

        int autocompleteIndex = userText.length();

        String previousText = getTextWithAutocomplete();
        CharSequence newText = TextUtils.concat(userText, inlineAutocompleteText);

        setIgnoreTextChangesForAutocomplete(true);

        if (!TextUtils.equals(previousText, newText)) {
            // The previous text may also have included autocomplete text, so we only
            // append the new autocomplete text that has changed.
            if (TextUtils.indexOf(newText, previousText) == 0) {
                append(newText.subSequence(previousText.length(), newText.length()));
            } else {
                replaceAllTextFromAutocomplete(newText.toString());
            }
        }

        if (getSelectionStart() != autocompleteIndex || getSelectionEnd() != getText().length()) {
            setSelection(autocompleteIndex, getText().length());

            if (inlineAutocompleteText.length() != 0) {
                // Sending a TYPE_VIEW_TEXT_SELECTION_CHANGED accessibility event causes the
                // previous TYPE_VIEW_TEXT_CHANGED event to be swallowed. As a result the user
                // hears the autocomplete text but *not* the text they typed. Instead we send a
                // TYPE_ANNOUNCEMENT event, which doesn't swallow the text-changed event.
                announceForAccessibility(inlineAutocompleteText);
            }
        }

        if (emptyAutocomplete) {
            mAutocompleteSpan.clearSpan();
        } else {
            mAutocompleteSpan.setSpan(userText, inlineAutocompleteText);
        }

        setIgnoreTextChangesForAutocomplete(false);
    }

    /**
     * Returns the length of the autocomplete text currently displayed, zero if none is
     * currently displayed.
     */
    public int getAutocompleteLength() {
        int autoCompleteIndex = getText().getSpanStart(mAutocompleteSpan);
        if (autoCompleteIndex < 0) return 0;
        return getText().length() - autoCompleteIndex;
    }

    @Override
    protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
        if (DEBUG) {
            Log.i(TAG, "onTextChanged -- text: %s, start: %d, lengthBefore: %d, lengthAfter: %d",
                    text, start, lengthBefore, lengthAfter);
        }

        super.onTextChanged(text, start, lengthBefore, lengthAfter);
        boolean textDeleted = lengthAfter == 0;
        if (!mInBatchEditMode) {
            notifyAutocompleteTextStateChanged(textDeleted, true);
        } else {
            mTextDeletedInBatchMode = textDeleted;
        }
    }

    @Override
    public void setText(CharSequence text, BufferType type) {
        if (DEBUG) Log.i(TAG, "setText -- text: %s", text);

        mDisableTextScrollingFromAutocomplete = false;

        // Avoid setting the same text to the URL bar as it will mess up the scroll/cursor
        // position.
        // Setting the text is also quite expensive, so only do it when the text has changed
        // (since we apply spans when the URL is not focused, we only optimize this when the
        // URL is being edited).
        if (!TextUtils.equals(getEditableText(), text)) {
            // Certain OEM implementations of setText trigger disk reads. crbug.com/633298
            StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskReads();
            try {
                super.setText(text, type);
            } finally {
                StrictMode.setThreadPolicy(oldPolicy);
            }
        }

        // Verify the autocomplete is still valid after the text change.
        // Note: mAutocompleteSpan may be still null here if setText() is called in View
        // constructor.
        if (mAutocompleteSpan != null && mAutocompleteSpan.mUserText != null
                && mAutocompleteSpan.mAutocompleteText != null) {
            if (getText().getSpanStart(mAutocompleteSpan) < 0) {
                mAutocompleteSpan.clearSpan();
            } else {
                clearAutocompleteSpanIfInvalid();
            }
        }
    }

    private void clearAutocompleteSpanIfInvalid() {
        Editable editableText = getEditableText();
        CharSequence previousUserText = mAutocompleteSpan.mUserText;
        CharSequence previousAutocompleteText = mAutocompleteSpan.mAutocompleteText;
        if (editableText.length()
                != (previousUserText.length() + previousAutocompleteText.length())) {
            mAutocompleteSpan.clearSpan();
        } else if (TextUtils.indexOf(getText(), previousUserText) != 0
                || TextUtils.indexOf(getText(), previousAutocompleteText, previousUserText.length())
                        != 0) {
            mAutocompleteSpan.clearSpan();
        }
    }

    @Override
    public void sendAccessibilityEventUnchecked(AccessibilityEvent event) {
        if (mIgnoreTextChangeFromAutocomplete) {
            if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED
                    || event.getEventType() == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
                return;
            }
        }
        super.sendAccessibilityEventUnchecked(event);
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        // Certain OEM implementations of onInitializeAccessibilityNodeInfo trigger disk reads
        // to access the clipboard.  crbug.com/640993
        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskReads();
        try {
            super.onInitializeAccessibilityNodeInfo(info);
        } finally {
            StrictMode.setThreadPolicy(oldPolicy);
        }
    }

    @VisibleForTesting
    public InputConnectionWrapper getInputConnection() {
        return mInputConnection;
    }

    @VisibleForTesting
    public void setIgnoreImeForTest(boolean ignore) {
        mIgnoreImeForTest = ignore;
    }

    private InputConnectionWrapper mInputConnection = new InputConnectionWrapper(null, true) {
        private final char[] mTempSelectionChar = new char[1];

        @Override
        public boolean commitText(CharSequence text, int newCursorPosition) {
            if (DEBUG) Log.i(TAG, "commitText: [%s]", text);
            Editable currentText = getText();
            if (currentText == null) return super.commitText(text, newCursorPosition);

            int selectionStart = Selection.getSelectionStart(currentText);
            int selectionEnd = Selection.getSelectionEnd(currentText);
            int autocompleteIndex = currentText.getSpanStart(mAutocompleteSpan);
            // If the text being committed is a single character that matches the next character
            // in the selection (assumed to be the autocomplete text), we only move the text
            // selection instead clearing the autocomplete text causing flickering as the
            // autocomplete text will appear once the next suggestions are received.
            //
            // To be confident that the selection is an autocomplete, we ensure the selection
            // is at least one character and the end of the selection is the end of the
            // currently entered text.
            if (newCursorPosition == 1 && selectionStart > 0 && selectionStart != selectionEnd
                    && selectionEnd >= currentText.length() && autocompleteIndex == selectionStart
                    && text.length() == 1) {
                currentText.getChars(selectionStart, selectionStart + 1, mTempSelectionChar, 0);
                if (mTempSelectionChar[0] == text.charAt(0)) {
                    // Since the text isn't changing, TalkBack won't read out the typed characters.
                    // To work around this, explicitly send an accessibility event. crbug.com/416595
                    if (mAccessibilityManager != null && mAccessibilityManager.isEnabled()) {
                        AccessibilityEvent event = AccessibilityEvent.obtain(
                                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED);
                        event.setFromIndex(selectionStart);
                        event.setRemovedCount(0);
                        event.setAddedCount(1);
                        event.setBeforeText(currentText.toString().substring(0, selectionStart));
                        sendAccessibilityEventUnchecked(event);
                    }

                    setAutocompleteText(currentText.subSequence(0, selectionStart + 1),
                            currentText.subSequence(selectionStart + 1, selectionEnd));
                    if (!mInBatchEditMode) {
                        notifyAutocompleteTextStateChanged(false, false);
                    }
                    return true;
                }
            }

            boolean retVal = super.commitText(text, newCursorPosition);

            // Ensure the autocomplete span is removed if it is no longer valid after committing the
            // text.
            if (getText().getSpanStart(mAutocompleteSpan) >= 0) clearAutocompleteSpanIfInvalid();

            return retVal;
        }

        @Override
        public boolean setComposingText(CharSequence text, int newCursorPosition) {
            if (DEBUG) Log.i(TAG, "setComposingText: [%s]", text);
            Editable currentText = getText();
            int autoCompleteSpanStart = currentText.getSpanStart(mAutocompleteSpan);
            if (autoCompleteSpanStart >= 0) {
                int composingEnd = BaseInputConnection.getComposingSpanEnd(currentText);

                // On certain device/keyboard combinations, the composing regions are specified
                // with a noticeable delay after the initial character is typed, and in certain
                // circumstances it does not check that the current state of the text matches the
                // expectations of it's composing region.
                // For example, you can be typing:
                //   chrome://f
                // Chrome will autocomplete to:
                //   chrome://f[lags]
                // And after the autocomplete has been set, the keyboard will set the composing
                // region to the last character and it assumes it is 'f' as it was the last
                // character the keyboard sent.  If we commit this composition, the text will
                // look like:
                //   chrome://flag[f]
                // And if we use the autocomplete clearing logic below, it will look like:
                //   chrome://f[f]
                // To work around this, we see if the composition matches all the characters prior
                // to the autocomplete and just readjust the composing region to be that subset.
                //
                // See crbug.com/366732
                if (composingEnd == currentText.length() && autoCompleteSpanStart >= text.length()
                        && TextUtils.equals(
                                   currentText.subSequence(autoCompleteSpanStart - text.length(),
                                           autoCompleteSpanStart),
                                   text)) {
                    setComposingRegion(
                            autoCompleteSpanStart - text.length(), autoCompleteSpanStart);
                }

                // Once composing text is being modified, the autocomplete text has been accepted
                // or has to be deleted.
                mAutocompleteSpan.clearSpan();
                Selection.setSelection(currentText, autoCompleteSpanStart);
                currentText.delete(autoCompleteSpanStart, currentText.length());
            }
            return super.setComposingText(text, newCursorPosition);
        }
    };

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        if (DEBUG) Log.i(TAG, "onCreateInputConnection");
        return createInputConnection(super.onCreateInputConnection(outAttrs));
    }

    @VisibleForTesting
    public InputConnection createInputConnection(InputConnection target) {
        mInputConnection.setTarget(target);
        if (mIgnoreImeForTest) return null;
        return mInputConnection;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (mIgnoreImeForTest) return true;
        return super.dispatchKeyEvent(event);
    }

    private void notifyAutocompleteTextStateChanged(boolean textDeleted, boolean updateDisplay) {
        if (DEBUG) {
            Log.i(TAG, "notifyAutocompleteTextStateChanged: DEL[%b] DIS[%b] IGN[%b]", textDeleted,
                    updateDisplay, mIgnoreTextChangeFromAutocomplete);
        }
        if (mIgnoreTextChangeFromAutocomplete) return;
        if (!hasFocus()) return;
        mLastEditWasDelete = textDeleted;
        onAutocompleteTextStateChanged(textDeleted, updateDisplay);
    }

    /**
     * This is called when autocomplete replaces the whole text.
     *
     * @param text The text.
     */
    protected void replaceAllTextFromAutocomplete(String text) {
        setText(text);
    }

    /**
     * This is called when autocomplete text state changes.
     * @param textDeleted True if text is just deleted.
     * @param updateDisplay True if string is changed.
     */
    public void onAutocompleteTextStateChanged(boolean textDeleted, boolean updateDisplay) {}

    /**
     * Simple span used for tracking the current autocomplete state.
     */
    private class AutocompleteSpan {
        private CharSequence mUserText;
        private CharSequence mAutocompleteText;

        /**
         * Adds the span to the current text.
         * @param userText The user entered text.
         * @param autocompleteText The autocomplete text being appended.
         */
        public void setSpan(CharSequence userText, CharSequence autocompleteText) {
            Editable text = getText();
            text.removeSpan(this);
            mAutocompleteText = autocompleteText;
            mUserText = userText;
            text.setSpan(this, userText.length(), text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        /** Removes this span from the current text and clears the internal state. */
        public void clearSpan() {
            getText().removeSpan(this);
            mAutocompleteText = null;
            mUserText = null;
        }
    }
}
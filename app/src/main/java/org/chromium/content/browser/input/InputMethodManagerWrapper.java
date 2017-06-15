// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.input;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.IBinder;
import android.os.ResultReceiver;
import android.os.StrictMode;
import android.view.View;
import android.view.inputmethod.CursorAnchorInfo;
import android.view.inputmethod.InputMethodManager;

import org.chromium.base.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Wrapper around Android's InputMethodManager
 */
public class InputMethodManagerWrapper {
    private static final boolean DEBUG_LOGS = false;
    private static final String TAG = "cr_Ime";

    private final Context mContext;

    public InputMethodManagerWrapper(Context context) {
        if (DEBUG_LOGS) Log.i(TAG, "Constructor");
        mContext = context;
    }

    private InputMethodManager getInputMethodManager() {
        return (InputMethodManager) mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
    }

    /**
     * @see android.view.inputmethod.InputMethodManager#restartInput(View)
     */
    public void restartInput(View view) {
        if (DEBUG_LOGS) Log.i(TAG, "restartInput");
        getInputMethodManager().restartInput(view);
    }

    /**
     * @see android.view.inputmethod.InputMethodManager#showSoftInput(View, int, ResultReceiver)
     */
    public void showSoftInput(View view, int flags, ResultReceiver resultReceiver) {
        if (DEBUG_LOGS) Log.i(TAG, "showSoftInput");
        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskWrites();  // crbug.com/616283
        try {
            getInputMethodManager().showSoftInput(view, flags, resultReceiver);
        } finally {
            StrictMode.setThreadPolicy(oldPolicy);
        }
    }

    /**
     * @see android.view.inputmethod.InputMethodManager#isActive(View)
     */
    public boolean isActive(View view) {
        final boolean active = getInputMethodManager().isActive(view);
        if (DEBUG_LOGS) Log.i(TAG, "isActive: " + active);
        return active;
    }

    /**
     * @see InputMethodManager#hideSoftInputFromWindow(IBinder, int, ResultReceiver)
     */
    public boolean hideSoftInputFromWindow(IBinder windowToken, int flags,
            ResultReceiver resultReceiver) {
        if (DEBUG_LOGS) Log.i(TAG, "hideSoftInputFromWindow");
        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskWrites();  // crbug.com/616283
        try {
            return getInputMethodManager().hideSoftInputFromWindow(
                    windowToken, flags, resultReceiver);
        } finally {
            StrictMode.setThreadPolicy(oldPolicy);
        }
    }

    /**
     * @see android.view.inputmethod.InputMethodManager#updateSelection(View, int, int, int, int)
     */
    public void updateSelection(View view, int selStart, int selEnd,
            int candidatesStart, int candidatesEnd) {
        if (DEBUG_LOGS) {
            Log.i(TAG, "updateSelection: SEL [%d, %d], COM [%d, %d]", selStart, selEnd,
                    candidatesStart, candidatesEnd);
        }
        getInputMethodManager().updateSelection(view, selStart, selEnd, candidatesStart,
                candidatesEnd);
    }

    /**
     * @see android.view.inputmethod.InputMethodManager#updateCursorAnchorInfo(View,
     * CursorAnchorInfo)
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void updateCursorAnchorInfo(View view, CursorAnchorInfo cursorAnchorInfo) {
        if (DEBUG_LOGS) Log.i(TAG, "updateCursorAnchorInfo");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getInputMethodManager().updateCursorAnchorInfo(view, cursorAnchorInfo);
        }
    }

    /**
     * @see android.view.inputmethod.InputMethodManager
     * #updateExtractedText(View,int, ExtractedText)
     */
    void updateExtractedText(View view, int token, android.view.inputmethod.ExtractedText text) {
        if (DEBUG_LOGS) Log.d(TAG, "updateExtractedText");
        getInputMethodManager().updateExtractedText(view, token, text);
    }

    /**
     * Notify that a user took some action with the current input method. Without this call
     * an input method app may wait longer when the user switches methods within the app.
     */
    public void notifyUserAction() {
        // On N and above, this is not needed.
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) return;
        if (DEBUG_LOGS) Log.i(TAG, "notifyUserAction");
        InputMethodManager manager = getInputMethodManager();
        try {
            Method method = InputMethodManager.class.getMethod("notifyUserAction");
            method.invoke(manager);
        } catch (NoSuchMethodException | IllegalAccessException | IllegalArgumentException
                | InvocationTargetException e) {
            if (DEBUG_LOGS) Log.i(TAG, "notifyUserAction failed");
            return;
        }
    }
}

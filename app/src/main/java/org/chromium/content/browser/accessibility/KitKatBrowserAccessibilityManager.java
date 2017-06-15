// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser.accessibility;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Bundle;
import android.view.accessibility.AccessibilityNodeInfo;

import org.chromium.base.annotations.JNINamespace;
import org.chromium.content.browser.ContentViewCore;

/**
 * Subclass of BrowserAccessibilityManager for KitKat.
 */
@JNINamespace("content")
@TargetApi(Build.VERSION_CODES.KITKAT)
public class KitKatBrowserAccessibilityManager extends BrowserAccessibilityManager {
    private String mSupportedHtmlElementTypes;

    KitKatBrowserAccessibilityManager(long nativeBrowserAccessibilityManagerAndroid,
            ContentViewCore contentViewCore) {
        super(nativeBrowserAccessibilityManagerAndroid, contentViewCore);
        mSupportedHtmlElementTypes = nativeGetSupportedHtmlElementTypes(
                nativeBrowserAccessibilityManagerAndroid);
    }

    @Override
    protected void setAccessibilityNodeInfoKitKatAttributes(AccessibilityNodeInfo node,
            boolean isRoot, boolean isEditableText, String roleDescription, int selectionStartIndex,
            int selectionEndIndex) {
        Bundle bundle = node.getExtras();
        bundle.putCharSequence("AccessibilityNodeInfo.roleDescription", roleDescription);
        if (isRoot) {
            bundle.putCharSequence("ACTION_ARGUMENT_HTML_ELEMENT_STRING_VALUES",
                    mSupportedHtmlElementTypes);
        }
        if (isEditableText) {
            node.setEditable(true);
            node.setTextSelection(selectionStartIndex, selectionEndIndex);
        }
    }
}

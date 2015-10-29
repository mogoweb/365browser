// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.widget;

import android.content.Context;
import android.support.v7.widget.SwitchCompat;
import android.util.AttributeSet;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Switch;

/**
 * This class fixes the accessibility of SwitchCompat so it's read as "on switch" instead of
 * "checkbox checked". http://crbug.com/441702
 *
 * TalkBack doesn't recognize the SwitchCompat class, so it reads events with that classname as
 * "Checkbox". This works around the bug by marking accessibility events from SwitchCompat with the
 * Switch class name, which TalkBack recognizes.
 *
 * TODO(newt): Delete this class once the support library is fixed. http://b/19110477
 */
public class ChromeSwitchCompat extends SwitchCompat {

    /**
     * Constructor for inflating from XML.
     */
    public ChromeSwitchCompat(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        super.onInitializeAccessibilityEvent(event);
        event.setClassName(Switch.class.getName());
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(Switch.class.getName());
    }
}

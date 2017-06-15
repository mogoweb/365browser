// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.ui.text;

import android.text.TextPaint;
import android.text.style.ClickableSpan;

/**
* Show a clickable link with underlines turned off.
*/
public abstract class NoUnderlineClickableSpan extends ClickableSpan {
    // Disable underline on the link text.
    @Override
    public void updateDrawState(TextPaint textPaint) {
        super.updateDrawState(textPaint);
        textPaint.setUnderlineText(false);
    }
}

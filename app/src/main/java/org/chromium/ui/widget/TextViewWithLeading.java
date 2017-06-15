// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.ui.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.TextView;

import org.chromium.ui.R;

/**
 * A TextView with the added leading property.
 * Leading is the distance between the baselines of successive lines of text (so the space between
 * rules on ruled paper). This class performs the calculation to setup leading correctly and allows
 * it to be set in XML. It overwrites android:lineSpacingExtra and android:lineSpacingMultiplier.
 */
public class TextViewWithLeading extends TextView {
    // TODO(peconn): Add a lint check to ensure no lineSpacingExtr or lineSpacingMultiplier.
    public TextViewWithLeading(Context context, AttributeSet attrs) {
        super(context, attrs);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.TextViewWithLeading, 0, 0);
        if (a.hasValue(R.styleable.TextViewWithLeading_leading)) {
            final float leading = a.getDimension(R.styleable.TextViewWithLeading_leading, 0f);
            final float oldLeading = getPaint().getFontMetrics(null);
            setLineSpacing(leading - oldLeading, 1f);
        }

        a.recycle();
    }
}
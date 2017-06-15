// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.ui.text.NoUnderlineClickableSpan;
import org.chromium.ui.text.SpanApplier;

/**
 * A preference representing one browsing data type in ClearBrowsingDataPreferencesTab.
 * This class allows clickable links inside the checkbox summary.
 */
public class ClearBrowsingDataTabCheckBoxPreference extends ClearBrowsingDataCheckBoxPreference {
    private Runnable mLinkClickDelegate;
    private boolean mHasClickableSpans;

    /**
     * Constructor for inflating from XML.
     */
    public ClearBrowsingDataTabCheckBoxPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * @param linkClickDelegate A Runnable that is executed when a link inside the summary is
     *                          clicked.
     */
    public void setLinkClickDelegate(Runnable linkClickDelegate) {
        mLinkClickDelegate = linkClickDelegate;
    }

    @Override
    public View onCreateView(ViewGroup parent) {
        View view = super.onCreateView(parent);

        final TextView textView = (TextView) view.findViewById(android.R.id.summary);

        // TODO(dullweber): Rethink how the link can be made accessible to TalkBack before launch.
        // Create custom onTouch listener to be able to respond to click events inside the summary.
        textView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            @SuppressLint("ClickableViewAccessibility")
            public boolean onTouch(View v, MotionEvent event) {
                if (!mHasClickableSpans) {
                    return false;
                }
                // Find out which character was touched.
                int offset = textView.getOffsetForPosition(event.getX(), event.getY());
                // Check if this character contains a span.
                Spanned text = (Spanned) textView.getText();
                ClickableSpan[] types = text.getSpans(offset, offset, ClickableSpan.class);

                if (types.length > 0) {
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        for (ClickableSpan type : types) {
                            type.onClick(textView);
                        }
                    }
                    return true;
                } else {
                    return false;
                }
            }
        });

        return view;
    }

    @Override
    protected void setupLayout(LinearLayout view) {
        // Override to remove layout customizations from super class.

        // Adjust icon padding.
        int padding = getContext().getResources().getDimensionPixelSize(R.dimen.pref_icon_padding);
        ImageView icon = (ImageView) view.findViewById(android.R.id.icon);
        ApiCompatibilityUtils.setPaddingRelative(
                icon, padding, icon.getPaddingTop(), 0, icon.getPaddingBottom());
    }

    @Override
    public void setSummary(CharSequence summary) {
        // If there is no link in the summary, invoke the default behavior.
        String summaryString = summary.toString();
        if (!summaryString.contains("<link>") || !summaryString.contains("</link>")) {
            super.setSummary(summary);
            return;
        }

        // Linkify <link></link> span.
        final SpannableString summaryWithLink = SpanApplier.applySpans(summaryString,
                new SpanApplier.SpanInfo("<link>", "</link>", new NoUnderlineClickableSpan() {
                    @Override
                    public void onClick(View widget) {
                        if (mLinkClickDelegate != null) mLinkClickDelegate.run();
                    }
                }));

        mHasClickableSpans = true;
        super.setSummary(summaryWithLink);
    }
}

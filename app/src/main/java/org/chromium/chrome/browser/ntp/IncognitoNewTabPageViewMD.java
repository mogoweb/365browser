// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp;

import android.content.Context;
import android.support.annotation.IdRes;
import android.support.annotation.StringRes;
import android.text.style.BulletSpan;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.ui.text.SpanApplier;

/**
 * The Material Design New Tab Page for use in the Incognito profile. This is an extension
 * of the IncognitoNewTabPageView class with improved text content and a more responsive design.
 */
public class IncognitoNewTabPageViewMD extends IncognitoNewTabPageView {
    private Context mContext;

    /** Default constructor needed to inflate via XML. */
    public IncognitoNewTabPageViewMD(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    @Override
    protected void onFinishInflate() {
        populateBulletpoints(R.id.new_tab_incognito_features, R.string.new_tab_otr_not_saved);
        populateBulletpoints(R.id.new_tab_incognito_warning, R.string.new_tab_otr_visible);

        super.onFinishInflate();
    }

    /**
     * @param element Resource ID of the element to be populated with the bulletpoints.
     * @param content String ID to serve as the text of |element|. Must contain an <em></em> span,
     *         which will be emphasized, and three <li> items, which will be converted to
     *         bulletpoints.
     * Populates |element| with |content|.
     */
    private void populateBulletpoints(@IdRes int element, @StringRes int content) {
        TextView view = (TextView) findViewById(element);
        String text = mContext.getResources().getString(content);

        // TODO(msramek): Unfortunately, our strings are missing the closing "</li>" tag, which
        // is not a problem when they're used in the Desktop WebUI (omitting the tag is valid in
        // HTML5), but it is a problem for SpanApplier. Update the strings and remove this regex.
        // Note that modifying the strings is a non-trivial operation as they went through a special
        // translation process.
        text = text.replaceAll("<li>([^<]+)\n", "<li>$1</li>\n");

        // Disambiguate the <li><li> spans for SpanApplier.
        text = text.replaceFirst("<li>(.*)</li>", "<li1>$1</li1>");
        text = text.replaceFirst("<li>(.*)</li>", "<li2>$1</li2>");
        text = text.replaceFirst("<li>(.*)</li>", "<li3>$1</li3>");

        // Remove the <ul></ul> tags which serve no purpose here.
        text = text.replaceAll("</?ul>", "");

        view.setText(SpanApplier.applySpans(text,
                new SpanApplier.SpanInfo("<em>", "</em>",
                        new ForegroundColorSpan(ApiCompatibilityUtils.getColor(
                                mContext.getResources(), R.color.incognito_emphasis))),
                new SpanApplier.SpanInfo("<li1>", "</li1>", new BulletSpan()),
                new SpanApplier.SpanInfo("<li2>", "</li2>", new BulletSpan()),
                new SpanApplier.SpanInfo("<li3>", "</li3>", new BulletSpan())));
    }
}

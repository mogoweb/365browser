// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.infobar;

import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.dom_distiller.ReaderModeManager;
import org.chromium.chrome.browser.tab.Tab;

/**
 * This is the InfoBar implementation of the Reader Mode UI. This is used in place of the
 * {@link OverlayPanel} implementation when Chrome Home is enabled.
 */
public class ReaderModeInfoBar extends InfoBar {
    /** A handle to the {@link ReaderModeManager} to trigger page navigations. */
    private static ReaderModeManager sManager;

    /**
     * Default constructor.
     */
    private ReaderModeInfoBar() {
        super(R.drawable.infobar_mobile_friendly, null, null);
    }

    @Override
    protected boolean usesCompactLayout() {
        return true;
    }

    @Override
    protected void createCompactLayoutContent(InfoBarCompactLayout layout) {
        TextView prompt = new TextView(getContext());
        prompt.setText(R.string.reader_view_text);
        prompt.setSingleLine();
        prompt.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getContext().getResources().getDimension(R.dimen.infobar_text_size));
        prompt.setTextColor(
                ApiCompatibilityUtils.getColor(layout.getResources(), R.color.default_text_color));
        prompt.setGravity(Gravity.CENTER_VERTICAL);

        prompt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO(mdjones): Trigger navigation from manager.
            }
        });

        layout.addContent(prompt, 1f);
    }

    public void onCloseButtonClicked() {
        super.onCloseButtonClicked();

        // TODO(mdjones): Notifiy the manager that the infobar was closed.
    }

    /**
     * Create and show the Reader Mode {@link InfoBar}.
     * @param tab The tab that the {@link InfoBar} should be shown in.
     * @param manager The {@link ReaderModeManager} for this instance of Chrome.
     */
    public static void showReaderModeInfoBar(Tab tab, ReaderModeManager manager) {
        sManager = manager;
        nativeCreate(tab);
    }

    /**
     * @return An instance of the {@link ReaderModeInfoBar}.
     */
    @CalledByNative
    private static ReaderModeInfoBar create() {
        return new ReaderModeInfoBar();
    }

    private static native void nativeCreate(Tab tab);
}

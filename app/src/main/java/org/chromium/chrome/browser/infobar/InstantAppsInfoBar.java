// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.infobar;

import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.widget.ImageView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.instantapps.InstantAppsBannerData;
import org.chromium.chrome.browser.widget.DualControlLayout;
import org.chromium.ui.text.SpanApplier;
import org.chromium.ui.text.SpanApplier.SpanInfo;

/**
 * Infobar that asks the user whether they want to use an instant app for a particular website.
 */
public class InstantAppsInfoBar extends ConfirmInfoBar {

    private InstantAppsBannerData mData;

    protected InstantAppsInfoBar(InstantAppsBannerData data) {
        super(0, data.getIcon(), data.getAppName(), null, null, null);
        mData = data;
    }

    @Override
    public void createContent(InfoBarLayout layout) {
        super.createContent(layout);

        int launchButtonColor = ApiCompatibilityUtils.getColor(getContext().getResources(),
                R.color.app_banner_install_button_bg);

        String title =
                getContext().getString(R.string.instant_apps_info_bar_label, mData.getAppName());
        SpannableString result = SpanApplier.applySpans(title, new SpanInfo("<style>", "</style>",
                new ForegroundColorSpan(launchButtonColor)));

        layout.setIsUsingBigIcon();
        layout.setMessage(result);
        layout.getMessageLayout().addDescription(mData.getUrl());
        layout.getPrimaryButton().setText(R.string.instant_apps_open_in_app);
        layout.getPrimaryButton().setButtonColor(launchButtonColor);
    }

    @Override
    protected void setButtons(InfoBarLayout layout, String primaryText, String secondaryText) {
        ImageView playLogo = new ImageView(layout.getContext());
        playLogo.setImageResource(R.drawable.google_play);
        layout.setBottomViews(primaryText, playLogo, DualControlLayout.ALIGN_APART);
    }

    @CalledByNative
    private static InfoBar create(InstantAppsBannerData data) {
        return new InstantAppsInfoBar(data);
    }

}

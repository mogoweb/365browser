// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp;

import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.browser.ntp.LogoBridge.Logo;
import org.chromium.chrome.browser.ntp.LogoBridge.LogoObserver;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.ui.base.PageTransition;

import java.util.concurrent.TimeUnit;

import jp.tomorrowkey.android.gifplayer.BaseGifImage;

/**
 * An implementation of {@link LogoView.Delegate}.
 */
public class LogoDelegateImpl implements LogoView.Delegate {
    // UMA enum constants. CTA means the "click-to-action" icon.
    private static final String LOGO_SHOWN_UMA_NAME = "NewTabPage.LogoShown";
    private static final int STATIC_LOGO_SHOWN = 0;
    private static final int CTA_IMAGE_SHOWN = 1;

    private static final String LOGO_SHOWN_TIME_UMA_NAME = "NewTabPage.LogoShownTime";

    private static final String LOGO_CLICK_UMA_NAME = "NewTabPage.LogoClick";
    private static final int STATIC_LOGO_CLICKED = 0;
    private static final int CTA_IMAGE_CLICKED = 1;
    private static final int ANIMATED_LOGO_CLICKED = 2;

    private final Tab mTab;
    private LogoView mLogoView;

    private LogoBridge mLogoBridge;
    private String mOnLogoClickUrl;
    private String mAnimatedLogoUrl;

    private boolean mShouldRecordLoadTime = true;

    private boolean mIsDestroyed;

    /**
     * Construct a new {@link LogoDelegateImpl}.
     * @param tab The tab showing the logo view.
     * @param logoView The view that shows the search provider logo.
     */
    public LogoDelegateImpl(Tab tab, LogoView logoView) {
        mTab = tab;
        mLogoView = logoView;
        mLogoBridge = new LogoBridge(tab.getProfile());
    }

    @Override
    public void destroy() {
        mIsDestroyed = true;
    }

    @Override
    public void onLogoClicked(boolean isAnimatedLogoShowing) {
        if (mIsDestroyed) return;

        if (!isAnimatedLogoShowing && mAnimatedLogoUrl != null) {
            RecordHistogram.recordSparseSlowlyHistogram(LOGO_CLICK_UMA_NAME, CTA_IMAGE_CLICKED);
            mLogoView.showLoadingView();
            mLogoBridge.getAnimatedLogo(new LogoBridge.AnimatedLogoCallback() {
                @Override
                public void onAnimatedLogoAvailable(BaseGifImage animatedLogoImage) {
                    if (mIsDestroyed) return;
                    mLogoView.playAnimatedLogo(animatedLogoImage);
                }
            }, mAnimatedLogoUrl);
        } else if (mOnLogoClickUrl != null) {
            RecordHistogram.recordSparseSlowlyHistogram(LOGO_CLICK_UMA_NAME,
                    isAnimatedLogoShowing ? ANIMATED_LOGO_CLICKED : STATIC_LOGO_CLICKED);
            mTab.loadUrl(new LoadUrlParams(mOnLogoClickUrl, PageTransition.LINK));
        }
    }

    @Override
    public void getSearchProviderLogo(final LogoObserver logoObserver) {
        if (mIsDestroyed) return;

        final long loadTimeStart = System.currentTimeMillis();

        LogoObserver wrapperCallback = new LogoObserver() {
            @Override
            public void onLogoAvailable(Logo logo, boolean fromCache) {
                if (mIsDestroyed) return;
                mOnLogoClickUrl = logo != null ? logo.onClickUrl : null;
                mAnimatedLogoUrl = logo != null ? logo.animatedLogoUrl : null;
                if (logo != null) {
                    RecordHistogram.recordSparseSlowlyHistogram(LOGO_SHOWN_UMA_NAME,
                            logo.animatedLogoUrl == null ? STATIC_LOGO_SHOWN : CTA_IMAGE_SHOWN);
                    if (mShouldRecordLoadTime) {
                        long loadTime = System.currentTimeMillis() - loadTimeStart;
                        RecordHistogram.recordMediumTimesHistogram(
                                LOGO_SHOWN_TIME_UMA_NAME, loadTime, TimeUnit.MILLISECONDS);
                    }
                }
                // If there currently is no Doodle, don't record the time if a refresh happens
                // later.
                mShouldRecordLoadTime = false;
                logoObserver.onLogoAvailable(logo, fromCache);
            }
        };

        mLogoBridge.getCurrentLogo(wrapperCallback);
    }
}

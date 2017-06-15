// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewTreeObserver.OnScrollChangedListener;
import android.widget.ScrollView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.help.HelpAndFeedback;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.widget.FadingShadow;
import org.chromium.chrome.browser.widget.FadingShadowView;
import org.chromium.chrome.browser.widget.bottomsheet.BottomSheet.BottomSheetContent;
import org.chromium.chrome.browser.widget.bottomsheet.BottomSheetContentController;

/**
 * Provides content to be displayed inside the Home tab of the bottom sheet in incognito mode.
 */
public class IncognitoBottomSheetContent implements BottomSheetContent {
    private final View mView;
    private final ScrollView mScrollView;

    /**
     * Constructs a new IncognitoBottomSheetContent.
     * @param activity The {@link Activity} displaying this bottom sheet content.
     */
    public IncognitoBottomSheetContent(final Activity activity) {
        LayoutInflater inflater = LayoutInflater.from(activity);
        mView = inflater.inflate(R.layout.incognito_bottom_sheet_content, null);

        View learnMore = mView.findViewById(R.id.learn_more);
        learnMore.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                HelpAndFeedback.getInstance(activity).show(activity,
                        activity.getString(R.string.help_context_incognito_learn_more),
                        Profile.getLastUsedProfile(), null);
            }
        });

        final FadingShadowView shadow = (FadingShadowView) mView.findViewById(R.id.shadow);
        shadow.init(
                ApiCompatibilityUtils.getColor(mView.getResources(), R.color.toolbar_shadow_color),
                FadingShadow.POSITION_TOP);

        mScrollView = (ScrollView) mView.findViewById(R.id.scroll_view);
        mScrollView.getViewTreeObserver().addOnScrollChangedListener(new OnScrollChangedListener() {
            @Override
            public void onScrollChanged() {
                boolean shadowVisible = mScrollView.canScrollVertically(-1);
                shadow.setVisibility(shadowVisible ? View.VISIBLE : View.GONE);
            }
        });
    }

    @Override
    public View getContentView() {
        return mView;
    }

    @Override
    public View getToolbarView() {
        return null;
    }

    @Override
    public boolean isUsingLightToolbarTheme() {
        return false;
    }

    @Override
    public boolean isIncognitoThemedContent() {
        return true;
    }

    @Override
    public int getVerticalScrollOffset() {
        return mScrollView.getScrollY();
    }

    @Override
    public void destroy() {}

    @Override
    public int getType() {
        return BottomSheetContentController.TYPE_INCOGNITO_HOME;
    }
}

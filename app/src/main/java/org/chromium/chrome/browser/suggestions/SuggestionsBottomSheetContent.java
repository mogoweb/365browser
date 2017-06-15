// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.suggestions;

import android.annotation.SuppressLint;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.OnScrollListener;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.Callback;
import org.chromium.base.DiscardableReferencePool;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.NativePageHost;
import org.chromium.chrome.browser.ntp.ContextMenuManager;
import org.chromium.chrome.browser.ntp.ContextMenuManager.TouchEnabledDelegate;
import org.chromium.chrome.browser.ntp.cards.NewTabPageAdapter;
import org.chromium.chrome.browser.ntp.snippets.SnippetArticle;
import org.chromium.chrome.browser.ntp.snippets.SnippetsBridge;
import org.chromium.chrome.browser.ntp.snippets.SuggestionsSource;
import org.chromium.chrome.browser.offlinepages.OfflinePageBridge;
import org.chromium.chrome.browser.omnibox.LocationBar;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.snackbar.SnackbarManager;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.widget.FadingShadow;
import org.chromium.chrome.browser.widget.FadingShadowView;
import org.chromium.chrome.browser.widget.bottomsheet.BottomSheet;
import org.chromium.chrome.browser.widget.bottomsheet.BottomSheetContentController;
import org.chromium.chrome.browser.widget.displaystyle.UiConfig;
import org.chromium.ui.widget.Toast;

import java.util.List;
import java.util.Locale;

/**
 * Provides content to be displayed inside of the Home tab of bottom sheet.
 */
public class SuggestionsBottomSheetContent implements BottomSheet.BottomSheetContent {
    private static SuggestionsSource sSuggestionsSourceForTesting;
    private static SuggestionsEventReporter sEventReporterForTesting;

    private final View mView;
    private final FadingShadowView mShadowView;
    private final SuggestionsRecyclerView mRecyclerView;
    private final ContextMenuManager mContextMenuManager;
    private final SuggestionsUiDelegateImpl mSuggestionsUiDelegate;
    private final TileGroup.Delegate mTileGroupDelegate;
    private final SuggestionsSheetVisibilityChangeObserver mBottomSheetObserver;

    public SuggestionsBottomSheetContent(final ChromeActivity activity, final BottomSheet sheet,
            TabModelSelector tabModelSelector, SnackbarManager snackbarManager) {
        Profile profile = Profile.getLastUsedProfile();
        SuggestionsNavigationDelegate navigationDelegate =
                new SuggestionsNavigationDelegateImpl(activity, profile, sheet, tabModelSelector);
        mTileGroupDelegate = new TileGroupDelegateImpl(
                activity, profile, tabModelSelector, navigationDelegate, snackbarManager);
        mSuggestionsUiDelegate = createSuggestionsDelegate(
                profile, navigationDelegate, sheet, activity.getReferencePool());

        mView = LayoutInflater.from(activity).inflate(
                R.layout.suggestions_bottom_sheet_content, null);
        mRecyclerView = (SuggestionsRecyclerView) mView.findViewById(R.id.recycler_view);

        TouchEnabledDelegate touchEnabledDelegate = new TouchEnabledDelegate() {
            @Override
            public void setTouchEnabled(boolean enabled) {
                activity.getBottomSheet().setTouchEnabled(enabled);
            }
        };
        mContextMenuManager =
                new ContextMenuManager(activity, navigationDelegate, touchEnabledDelegate);
        activity.getWindowAndroid().addContextMenuCloseListener(mContextMenuManager);
        mSuggestionsUiDelegate.addDestructionObserver(new DestructionObserver() {
            @Override
            public void onDestroy() {
                activity.getWindowAndroid().removeContextMenuCloseListener(mContextMenuManager);
            }
        });

        UiConfig uiConfig = new UiConfig(mRecyclerView);

        final NewTabPageAdapter adapter = new NewTabPageAdapter(mSuggestionsUiDelegate,
                /* aboveTheFoldView = */ null, uiConfig, OfflinePageBridge.getForProfile(profile),
                mContextMenuManager, mTileGroupDelegate);
        mRecyclerView.init(uiConfig, mContextMenuManager, adapter);

        mBottomSheetObserver = new SuggestionsSheetVisibilityChangeObserver(this, activity) {
            @Override
            public void onSheetOpened() {
                mRecyclerView.scrollToPosition(0);
                adapter.refreshSuggestions();
                mSuggestionsUiDelegate.getEventReporter().onSurfaceOpened();
                mRecyclerView.getScrollEventReporter().reset();

                if (ChromeFeatureList.isEnabled(
                            ChromeFeatureList.CONTEXTUAL_SUGGESTIONS_CAROUSEL)
                        && sheet.getActiveTab() != null) {
                    updateContextualSuggestions(sheet.getActiveTab().getUrl());
                }

                super.onSheetOpened();
            }

            @Override
            public void onContentShown() {
                SuggestionsMetrics.recordSurfaceVisible();
            }

            @Override
            public void onContentHidden() {
                SuggestionsMetrics.recordSurfaceHidden();
            }

            @Override
            public void onContentStateChanged(@BottomSheet.SheetState int contentState) {
                if (contentState == BottomSheet.SHEET_STATE_HALF) {
                    SuggestionsMetrics.recordSurfaceHalfVisible();
                } else if (contentState == BottomSheet.SHEET_STATE_FULL) {
                    SuggestionsMetrics.recordSurfaceFullyVisible();
                }
            }
        };

        mShadowView = (FadingShadowView) mView.findViewById(R.id.shadow);
        mShadowView.init(
                ApiCompatibilityUtils.getColor(mView.getResources(), R.color.toolbar_shadow_color),
                FadingShadow.POSITION_TOP);

        mRecyclerView.addOnScrollListener(new OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                boolean shadowVisible = mRecyclerView.canScrollVertically(-1);
                mShadowView.setVisibility(shadowVisible ? View.VISIBLE : View.GONE);
            }
        });

        final LocationBar locationBar = (LocationBar) sheet.findViewById(R.id.location_bar);
        mRecyclerView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            @SuppressLint("ClickableViewAccessibility")
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (locationBar != null && locationBar.isUrlBarFocused()) {
                    locationBar.setUrlBarFocus(false);
                }

                // Never intercept the touch event.
                return false;
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
        return false;
    }

    @Override
    public int getVerticalScrollOffset() {
        return mRecyclerView.computeVerticalScrollOffset();
    }

    @Override
    public void destroy() {
        mBottomSheetObserver.onDestroy();
        mSuggestionsUiDelegate.onDestroy();
        mTileGroupDelegate.destroy();
    }

    @Override
    public int getType() {
        return BottomSheetContentController.TYPE_SUGGESTIONS;
    }

    private void updateContextualSuggestions(String url) {
        mSuggestionsUiDelegate.getSuggestionsSource().fetchContextualSuggestions(
                url, new Callback<List<SnippetArticle>>() {
                    @Override
                    public void onResult(List<SnippetArticle> result) {
                        String text = String.format(
                                Locale.US, "Received %d contextual suggestions", result.size());
                        Toast.makeText(mRecyclerView.getContext(), text, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    public static void setSuggestionsSourceForTesting(SuggestionsSource suggestionsSource) {
        sSuggestionsSourceForTesting = suggestionsSource;
    }

    public static void setEventReporterForTesting(SuggestionsEventReporter eventReporter) {
        sEventReporterForTesting = eventReporter;
    }

    private static SuggestionsUiDelegateImpl createSuggestionsDelegate(Profile profile,
            SuggestionsNavigationDelegate navigationDelegate, NativePageHost host,
            DiscardableReferencePool referencePool) {
        SnippetsBridge snippetsBridge = null;
        SuggestionsSource suggestionsSource;
        SuggestionsEventReporter eventReporter;

        if (sSuggestionsSourceForTesting == null) {
            snippetsBridge = new SnippetsBridge(profile);
            suggestionsSource = snippetsBridge;
        } else {
            suggestionsSource = sSuggestionsSourceForTesting;
        }

        if (sEventReporterForTesting == null) {
            eventReporter = new SuggestionsEventReporterBridge();
        } else {
            eventReporter = sEventReporterForTesting;
        }

        SuggestionsUiDelegateImpl delegate = new SuggestionsUiDelegateImpl(
                suggestionsSource, eventReporter, navigationDelegate, profile, host, referencePool);
        if (snippetsBridge != null) delegate.addDestructionObserver(snippetsBridge);

        return delegate;
    }
}

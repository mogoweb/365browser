// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.widget.bottomsheet;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.support.annotation.IntDef;
import android.support.design.internal.BottomNavigationItemView;
import android.support.design.internal.BottomNavigationMenuView;
import android.support.design.widget.BottomNavigationView;
import android.support.design.widget.BottomNavigationView.OnNavigationItemSelectedListener;
import android.util.AttributeSet;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import org.chromium.base.ActivityState;
import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.ApplicationStatus;
import org.chromium.base.ApplicationStatus.ActivityStateListener;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.bookmarks.BookmarkSheetContent;
import org.chromium.chrome.browser.download.DownloadSheetContent;
import org.chromium.chrome.browser.history.HistorySheetContent;
import org.chromium.chrome.browser.ntp.IncognitoBottomSheetContent;
import org.chromium.chrome.browser.snackbar.SnackbarManager;
import org.chromium.chrome.browser.suggestions.SuggestionsBottomSheetContent;
import org.chromium.chrome.browser.tabmodel.EmptyTabModelSelectorObserver;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.util.MathUtils;
import org.chromium.chrome.browser.widget.bottomsheet.BottomSheet.BottomSheetContent;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Displays and controls a {@link BottomNavigationView} fixed to the bottom of the
 * {@link BottomSheet}. Also manages {@link BottomSheetContent} displayed in the BottomSheet.
 */
public class BottomSheetContentController extends BottomNavigationView
        implements OnNavigationItemSelectedListener {
    /** The different types of content that may be displayed in the bottom sheet. */
    @IntDef({TYPE_SUGGESTIONS, TYPE_DOWNLOADS, TYPE_BOOKMARKS, TYPE_HISTORY, TYPE_INCOGNITO_HOME,
            TYPE_PLACEHOLDER})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ContentType {}
    public static final int TYPE_SUGGESTIONS = 0;
    public static final int TYPE_DOWNLOADS = 1;
    public static final int TYPE_BOOKMARKS = 2;
    public static final int TYPE_HISTORY = 3;
    public static final int TYPE_INCOGNITO_HOME = 4;
    public static final int TYPE_PLACEHOLDER = 5;

    // R.id.action_home is overloaded, so an invalid ID is used to reference the incognito version
    // of the home content.
    private static final int INCOGNITO_HOME_ID = -1;

    // Since the placeholder content cannot be triggered by a navigation item like the others, this
    // value must also be an invalid ID.
    private static final int PLACEHOLDER_ID = -2;

    private final Map<Integer, BottomSheetContent> mBottomSheetContents = new HashMap<>();

    private final BottomSheetObserver mBottomSheetObserver = new EmptyBottomSheetObserver() {
        @Override
        public void onSheetOffsetChanged(float heightFraction) {
            // If the omnibox is not focused, allow the navigation bar to set its Y translation.
            if (!mOmniboxHasFocus) {
                float offsetY =
                        (mBottomSheet.getMinOffset() - mBottomSheet.getSheetOffsetFromBottom())
                        + mDistanceBelowToolbarPx;
                setTranslationY(Math.max(offsetY, 0f));

                if (mBottomSheet.getTargetSheetState() != BottomSheet.SHEET_STATE_PEEK
                        && mSelectedItemId == PLACEHOLDER_ID) {
                    showBottomSheetContent(R.id.action_home);
                }
            }
            setVisibility(MathUtils.areFloatsEqual(heightFraction, 0f) ? View.GONE : View.VISIBLE);

            mSnackbarManager.dismissAllSnackbars();
        }

        @Override
        public void onSheetOpened() {
            if (!mDefaultContentInitialized && mTabModelSelector.getCurrentTab() != null) {
                initializeDefaultContent();
            }
        }

        @Override
        public void onSheetClosed() {
            if (mSelectedItemId != 0 && mSelectedItemId != R.id.action_home) {
                showBottomSheetContent(R.id.action_home);
            }

            Iterator<Entry<Integer, BottomSheetContent>> contentIterator =
                    mBottomSheetContents.entrySet().iterator();
            while (contentIterator.hasNext()) {
                Entry<Integer, BottomSheetContent> entry = contentIterator.next();
                if (entry.getKey() == R.id.action_home || entry.getKey() == INCOGNITO_HOME_ID) {
                    continue;
                }

                entry.getValue().destroy();
                contentIterator.remove();
            }

            // TODO(twellington): determine a policy for destroying the
            //                    SuggestionsBottomSheetContent.
        }

        @Override
        public void onSheetContentChanged(BottomSheetContent newContent) {
            if (mBottomSheet.isSheetOpen()) announceBottomSheetContentSelected();

            if (!mShouldOpenSheetOnNextContentChange) return;

            mShouldOpenSheetOnNextContentChange = false;
            if (!mBottomSheet.isSheetOpen()) {
                mBottomSheet.setSheetState(BottomSheet.SHEET_STATE_FULL, true);
            }
        }

        @Override
        public void onSheetLayout(int windowHeight, int containerHeight) {
            setTranslationY(containerHeight - windowHeight);
        }
    };

    private BottomSheet mBottomSheet;
    private TabModelSelector mTabModelSelector;
    private SnackbarManager mSnackbarManager;
    private float mDistanceBelowToolbarPx;
    private int mSelectedItemId;
    private boolean mDefaultContentInitialized;
    private ChromeActivity mActivity;
    private boolean mShouldOpenSheetOnNextContentChange;
    private PlaceholderSheetContent mPlaceholderContent;
    private boolean mOmniboxHasFocus;

    public BottomSheetContentController(Context context, AttributeSet atts) {
        super(context, atts);

        mPlaceholderContent = new PlaceholderSheetContent(context);
    }

    /**
     * Initializes the {@link BottomSheetContentController}.
     * @param bottomSheet The {@link BottomSheet} associated with this bottom nav.
     * @param controlContainerHeight The height of the control container in px.
     * @param tabModelSelector The {@link TabModelSelector} for the application.
     * @param activity The {@link ChromeActivity} that owns the BottomSheet.
     */
    public void init(BottomSheet bottomSheet, int controlContainerHeight,
            TabModelSelector tabModelSelector, ChromeActivity activity) {
        mBottomSheet = bottomSheet;
        mBottomSheet.addObserver(mBottomSheetObserver);
        mActivity = activity;
        mTabModelSelector = tabModelSelector;
        mTabModelSelector.addObserver(new EmptyTabModelSelectorObserver() {
            @Override
            public void onTabModelSelected(TabModel newModel, TabModel oldModel) {
                updateVisuals(newModel.isIncognito());
                showBottomSheetContent(R.id.action_home);
                mPlaceholderContent.setIsIncognito(newModel.isIncognito());

                // Release incognito bottom sheet content so that it can be garbage collected.
                if (!newModel.isIncognito()
                        && mBottomSheetContents.containsKey(INCOGNITO_HOME_ID)) {
                    mBottomSheetContents.get(INCOGNITO_HOME_ID).destroy();
                    mBottomSheetContents.remove(INCOGNITO_HOME_ID);
                }
            }
        });

        Resources res = getContext().getResources();
        mDistanceBelowToolbarPx = controlContainerHeight
                + res.getDimensionPixelOffset(R.dimen.bottom_nav_space_from_toolbar);

        setOnNavigationItemSelectedListener(this);
        disableShiftingMode();

        mSnackbarManager = new SnackbarManager(
                mActivity, (ViewGroup) activity.findViewById(R.id.bottom_sheet_snackbar_container));
        mSnackbarManager.onStart();

        ApplicationStatus.registerStateListenerForActivity(new ActivityStateListener() {
            @Override
            public void onActivityStateChange(Activity activity, int newState) {
                if (newState == ActivityState.STARTED) mSnackbarManager.onStart();
                if (newState == ActivityState.STOPPED) mSnackbarManager.onStop();
            }
        }, mActivity);
    }

    /**
     * Initialize the default {@link BottomSheetContent}.
     */
    public void initializeDefaultContent() {
        if (mDefaultContentInitialized) return;
        showBottomSheetContent(R.id.action_home);
        mDefaultContentInitialized = true;
    }

    /**
     * Shows the specified {@link BottomSheetContent} and opens the {@link BottomSheet}.
     * @param itemId The menu item id of the {@link BottomSheetContent} to show.
     */
    public void showContentAndOpenSheet(int itemId) {
        if (itemId != mSelectedItemId) {
            mShouldOpenSheetOnNextContentChange = true;
            selectItem(itemId);
        } else if (!mBottomSheet.isSheetOpen()) {
            mBottomSheet.setSheetState(BottomSheet.SHEET_STATE_FULL, true);
        }
    }

    /**
     * A notification that the omnibox focus state is changing.
     * @param hasFocus Whether or not the omnibox has focus.
     */
    public void onOmniboxFocusChange(boolean hasFocus) {
        mOmniboxHasFocus = hasFocus;

        // If the omnibox is being focused, show the placeholder.
        if (hasFocus && mBottomSheet.getSheetState() != BottomSheet.SHEET_STATE_HALF
                && mBottomSheet.getSheetState() != BottomSheet.SHEET_STATE_FULL) {
            mBottomSheet.showContent(mPlaceholderContent);
            mBottomSheet.endTransitionAnimations();
            if (mSelectedItemId > 0) getMenu().findItem(mSelectedItemId).setChecked(false);
            mSelectedItemId = PLACEHOLDER_ID;
        }

        if (!hasFocus && mBottomSheet.getCurrentSheetContent() == mPlaceholderContent) {
            showBottomSheetContent(R.id.action_home);
        }
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        if (mSelectedItemId == item.getItemId()) return false;

        mBottomSheet.defocusOmnibox();

        mSnackbarManager.dismissAllSnackbars();
        showBottomSheetContent(item.getItemId());
        return true;
    }

    // TODO(twellington): remove this once the support library is updated to allow disabling
    //                    shifting mode or determines shifting mode based on the width of the
    //                    child views.
    private void disableShiftingMode() {
        BottomNavigationMenuView menuView = (BottomNavigationMenuView) getChildAt(0);
        try {
            Field shiftingMode = menuView.getClass().getDeclaredField("mShiftingMode");
            shiftingMode.setAccessible(true);
            shiftingMode.setBoolean(menuView, false);
            shiftingMode.setAccessible(false);
            for (int i = 0; i < menuView.getChildCount(); i++) {
                BottomNavigationItemView item = (BottomNavigationItemView) menuView.getChildAt(i);
                item.setShiftingMode(false);
                // Set the checked value so that the view will be updated.
                item.setChecked(item.getItemData().isChecked());
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            // Do nothing if reflection fails.
        }
    }

    private BottomSheetContent getSheetContentForId(int navItemId) {
        if (mTabModelSelector.isIncognitoSelected() && navItemId == R.id.action_home) {
            navItemId = INCOGNITO_HOME_ID;
        }

        BottomSheetContent content = mBottomSheetContents.get(navItemId);
        if (content != null) return content;

        if (navItemId == R.id.action_home) {
            content = new SuggestionsBottomSheetContent(
                    mActivity, mBottomSheet, mTabModelSelector, mSnackbarManager);
        } else if (navItemId == R.id.action_downloads) {
            content = new DownloadSheetContent(
                    mActivity, mTabModelSelector.getCurrentModel().isIncognito(), mSnackbarManager);
        } else if (navItemId == R.id.action_bookmarks) {
            content = new BookmarkSheetContent(mActivity, mSnackbarManager);
        } else if (navItemId == R.id.action_history) {
            content = new HistorySheetContent(mActivity, mSnackbarManager);
        } else if (navItemId == INCOGNITO_HOME_ID) {
            content = new IncognitoBottomSheetContent(mActivity);
        }

        mBottomSheetContents.put(navItemId, content);
        return content;
    }

    private void showBottomSheetContent(int navItemId) {
        // There are some bugs related to programatically selecting menu items that are fixed in
        // newer support library versions.
        // TODO(twellington): remove this after the support library is rolled.
        if (mSelectedItemId > 0) getMenu().findItem(mSelectedItemId).setChecked(false);
        mSelectedItemId = navItemId;
        getMenu().findItem(mSelectedItemId).setChecked(true);

        BottomSheetContent newContent = getSheetContentForId(mSelectedItemId);
        mBottomSheet.showContent(newContent);
    }

    private void announceBottomSheetContentSelected() {
        if (mSelectedItemId == R.id.action_home) {
            announceForAccessibility(getResources().getString(R.string.bottom_sheet_home_tab));
        } else if (mSelectedItemId == R.id.action_downloads) {
            announceForAccessibility(getResources().getString(R.string.bottom_sheet_downloads_tab));
        } else if (mSelectedItemId == R.id.action_bookmarks) {
            announceForAccessibility(getResources().getString(R.string.bottom_sheet_bookmarks_tab));
        } else if (mSelectedItemId == R.id.action_history) {
            announceForAccessibility(getResources().getString(R.string.bottom_sheet_history_tab));
        }
    }

    private void updateVisuals(boolean isIncognitoTabModelSelected) {
        setBackgroundResource(isIncognitoTabModelSelected ? R.color.incognito_primary_color
                                                          : R.color.default_primary_color);

        ColorStateList tint = ApiCompatibilityUtils.getColorStateList(getResources(),
                isIncognitoTabModelSelected ? R.color.bottom_nav_tint_incognito
                                            : R.color.bottom_nav_tint);
        setItemIconTintList(tint);
        setItemTextColor(tint);
    }

    /**
     * @param itemId The id of the MenuItem to select.
     */
    @VisibleForTesting
    public void selectItem(int itemId) {
        // TODO(twellington): A #setSelectedItemId() method was added to the support library
        //                    recently. Replace this custom implementation with that method after
        //                    the support library is rolled.
        onNavigationItemSelected(getMenu().findItem(itemId));
    }

    @VisibleForTesting
    public int getSelectedItemIdForTests() {
        // TODO(twellington): A #getSelectedItemId() method was added to the support library
        //                    recently. Replace this custom implementation with that method after
        //                    the support library is rolled.
        return mSelectedItemId;
    }
}

/*
 * Copyright (c) 2015-2016, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package org.chromium.chrome.browser.ntp;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.TabLayout;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.NativePage;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.chrome.browser.bookmarks.BookmarkPage;
import org.chromium.chrome.browser.compositor.layouts.content.InvalidationAwareThumbnailProvider;
import org.chromium.chrome.browser.favicon.FaviconHelper;
import org.chromium.chrome.browser.favicon.LargeIconBridge.LargeIconCallback;
import org.chromium.chrome.browser.search_engines.TemplateUrlService;
import org.chromium.chrome.browser.search_engines.TemplateUrlService.TemplateUrlServiceObserver;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.toolbar.ToolbarFavicon;
import org.chromium.chrome.browser.util.ViewUtils;
import org.chromium.ui.base.DeviceFormFactor;

import java.util.ArrayList;

public class BrowserNewTabPage extends NewTabPage
        implements NativePage, InvalidationAwareThumbnailProvider, TemplateUrlServiceObserver {

    private final TabLayout mNTPTabLayout;
    private final LinearLayout mNTPLinearLayout;

    private final ChromeActivity mActivity;

    private final ViewPager mPager;
    private final FloatingActionButton mSearch;

    private boolean mNeedsCapture;
    private ArrayList<BrowserNTPTab> mTabs;

    private static final int TAB_NO_ICON = 0;

    private class NTPPageAdapter extends PagerAdapter {
        @Override
        public int getCount() {
            return mTabs.size();
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return object == view;
        }

        @Override
        public Object instantiateItem(ViewGroup collection, int position) {
            if (position >= mTabs.size())
                return null;

            NativePage page = mTabs.get(position).getPage();
            View view = page.getView();
            if (page instanceof BrowserNewTabPage)
                view = BrowserNewTabPage.super.getView();
            view.requestFocus();
            collection.addView(view);
            return view;
        }

        @Override
        public void destroyItem(ViewGroup collection, int position, Object view) {
            collection.removeView((View)view);
        }
    }

    /**
     * Constructs a Tabbed NewTabPage.
     * @param activity The activity used for context to create the new tab page's View.
     * @param tab The Tab that is showing this new tab page.
     * @param tabModelSelector The TabModelSelector used to open tabs.
     */
    public BrowserNewTabPage(ChromeActivity activity, Tab tab,
                             TabModelSelector tabModelSelector, String url) {
        super(activity, tab, tabModelSelector);

        mActivity = activity;

        mTabs = new ArrayList<>();

        Resources resources= mActivity.getResources();

        NewTabPageView newTabPageView = (NewTabPageView) super.getView();
        View view = newTabPageView.findViewById(R.id.ntp_content);
        if (view != null) {
            view.setPadding(view.getPaddingLeft(),0,view.getPaddingRight(),view.getPaddingBottom());
        }
        view = newTabPageView.findViewById(R.id.ntp_scrollview);
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams)view.getLayoutParams();
        layoutParams.setMargins(layoutParams.leftMargin, layoutParams.topMargin,
                layoutParams.rightMargin, 0);
        view.setLayoutParams(layoutParams);
        mTabs.add(new BrowserNTPTab(this, resources.getString(R.string.most_visited),
                R.drawable.ic_deco_launch_frequent));

        // Remove NTP chromium style tab bar
        view = newTabPageView.findViewById(R.id.ntp_toolbar);
        if (view != null)
            view.setVisibility(View.GONE);

        LayoutInflater inflater = LayoutInflater.from(activity);

        NativePage bookmarksPage = NativePageFactory.createNativePageForURL(
                UrlConstants.BOOKMARKS_URL, null, tab, tabModelSelector, activity, true);
        if (bookmarksPage != null) {
            view = bookmarksPage.getView();
            if (view != null) {
                view = ((ViewGroup) view).getChildAt(0);
                view.setPadding(view.getPaddingLeft(), 0,
                        view.getPaddingRight(), view.getPaddingBottom());
            }
            mTabs.add(new BrowserNTPTab(bookmarksPage, resources.getString(R.string.ntp_bookmarks),
                    R.drawable.ic_deco_launch_bookmarked));
        }

        RecentTabsPage recentTabsPage = (RecentTabsPage) NativePageFactory
                .sNativePageBuilder.buildRecentTabsPage(activity, tab);

        view = recentTabsPage.getView().findViewById(R.id.odp_listview);
        if (view != null) {
            ViewGroup parent = (ViewGroup) view.getParent();
            if (parent != null) {
                parent.setPadding(parent.getPaddingLeft(), 0, parent.getPaddingRight(),
                        parent.getPaddingBottom());

                parent = (ViewGroup) parent.getParent();
                if (parent != null) {
                    parent.setPadding(parent.getPaddingLeft(), 0, parent.getPaddingRight(),
                            parent.getPaddingBottom());
                }
            }
        }
        mTabs.add(new BrowserNTPTab(recentTabsPage, resources.getString(R.string.recent_tabs)
                , R.drawable.ic_deco_launch_historic));

        mNTPLinearLayout = (LinearLayout) inflater.inflate(R.layout.browser_new_tab_page, null);

        mSearch = (FloatingActionButton) mNTPLinearLayout.findViewById(R.id.ntp_search);

        mSearch.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        mSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mNewTabPageManager.focusSearchBox(false, null);
            }
        });
        mSearch.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (mNewTabPageManager.isVoiceSearchEnabled()) {
                    mNewTabPageManager.focusSearchBox(true, null);
                    return true;
                }
                return false;
            }
        });

        mPager = (ViewPager) mNTPLinearLayout.findViewById(R.id.browser_ntp_views);
        if (mPager != null) {
            mPager.setId(tab.getId() + 1);
            mPager.setAdapter(new NTPPageAdapter());
            mPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
                @Override
                public void onPageScrolled(int position, float positionOffset,
                                           int positionOffsetPixels) {
                    if (positionOffset != 0) {
                        mNeedsCapture = true;
                    }
                }

                @Override
                public void onPageSelected(int position) {
                    mNeedsCapture = true;
                }

                @Override
                public void onPageScrollStateChanged(int state) {
                    mNeedsCapture = true;
                }
            });

            mNTPTabLayout = (TabLayout) mNTPLinearLayout.findViewById(R.id.browser_ntp_tablayout);
            if (mNTPTabLayout != null) {
                final ViewPager pager = mPager;
                mNTPTabLayout.setupWithViewPager(mPager);
                setupTabs();
                mNTPTabLayout.setOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
                    @Override
                    public void onTabSelected(TabLayout.Tab tab) {
                        pager.setCurrentItem(tab.getPosition());
                    }

                    @Override
                    public void onTabUnselected(TabLayout.Tab tab) {

                    }

                    @Override
                    public void onTabReselected(TabLayout.Tab tab) {
                        pager.setCurrentItem(tab.getPosition());
                    }
                });
                updateSearchEngineArtifacts();
            }
        } else {
            mNTPTabLayout = null;
        }

        NativePageFactory.NativePageType type = NativePageFactory.nativePageType(url, null, false);
        switch (type) {
            case BOOKMARKS:
                showBookmarksPage();
                break;
            case RECENT_TABS:
                showRecentTabs();
                break;
            default:
                break;
        }
    }

    private void setupTabs() {
        for (int i = 0; i < mNTPTabLayout.getTabCount(); i++) {
            TabLayout.Tab layoutTab = mNTPTabLayout.getTabAt(i);
            BrowserNTPTab ntpTab = mTabs.get(i);
            if (layoutTab == null) continue;

            if (ntpTab.getIcon() == TAB_NO_ICON || DeviceFormFactor.isTablet(mActivity)) {
                layoutTab.setText(ntpTab.getTitle());
            } else {
                layoutTab.setIcon(ntpTab.getIcon());
            }
            layoutTab.setContentDescription(ntpTab.getTitle());
        }
    }

    private class LargeIconForNTP implements LargeIconCallback {
        @Override
        public void onLargeIconAvailable(Bitmap icon, int fallbackColor) {
            int color;
            if (TemplateUrlService.getInstance().isDefaultSearchEngineGoogle()) {
                color = ToolbarFavicon.OVERRIDE_SEARCHENGINE_COLOR;
            } else {
                if (icon == null) {
                    color = fallbackColor;
                } else {
                    color = FaviconHelper.getDominantColorForBitmap(icon);
                }
            }
            mNTPTabLayout.setBackgroundColor(color);
            mSearch.setBackgroundTintList(ColorStateList.valueOf(color));
        }
    }


    private void updateSearchEngineArtifacts() {
        TemplateUrlService service = TemplateUrlService.getInstance();
        if (service == null || mNTPTabLayout == null) return;

        TemplateUrlService.TemplateUrl searchEngine = service.getDefaultSearchEngineTemplateUrl();

        if (searchEngine == null) return;

        int index = searchEngine.getIndex();
        String favicon_url = service.getSearchEngineFavicon(index);

        LargeIconForNTP callback = new LargeIconForNTP();
        mNewTabPageManager.getLargeIconForUrl(favicon_url,
                ToolbarFavicon.SEARCHENGINE_FAVICON_MIN_SIZE_PX, callback);
    }

    @Override
    protected void updateSearchProviderHasLogo() {
        mSearchProviderHasLogo = false;
    }

    @Override
    public void onTemplateURLServiceChanged() {
        super.onTemplateURLServiceChanged();
        updateSearchEngineArtifacts();
    }

    @Override
    public View getView() {
        return mNTPLinearLayout;
    }

    private NativePage getCurrentNativePage() {
        int index = mPager.getCurrentItem();
        return mTabs.get(index).getPage();
    }

    @Override
    public String getUrl() {
        NativePage page = getCurrentNativePage();
        if (page instanceof BrowserNewTabPage) {
            return super.getUrl();
        }

        return page.getUrl();
    }

    @Override
    public String getTitle() {
        NativePage page = getCurrentNativePage();
        if (page instanceof BrowserNewTabPage) {
            return super.getTitle();
        }

        return page.getTitle();
    }

    @Override
    public int getBackgroundColor() {
        NativePage page = getCurrentNativePage();
        if (page instanceof BrowserNewTabPage) {
            return super.getBackgroundColor();
        }

        return page.getBackgroundColor();
    }

    @Override
    public String getHost() {
        NativePage page = getCurrentNativePage();
        if (page instanceof BrowserNewTabPage) {
            return super.getHost();
        }

        return page.getHost();
    }

    @Override
    public void updateForUrl(String url) {
        NativePage page = getCurrentNativePage();
        if (page instanceof BrowserNewTabPage) {
            super.updateForUrl(url);
            return;
        }

        page.updateForUrl(url);
    }

    @Override
    public void destroy() {
        NativePage page = getCurrentNativePage();
        if (page instanceof BrowserNewTabPage) {
            super.destroy();
            return;
        }

        page.destroy();
    }

    private InvalidationAwareThumbnailProvider getCurrentThumbnailProvider(NativePage page) {
        if (page instanceof InvalidationAwareThumbnailProvider) {
            return (InvalidationAwareThumbnailProvider) page;
        }

        return null;
    }

    @Override
    public boolean shouldCaptureThumbnail() {
        if (mNeedsCapture) return true;
        NativePage page = getCurrentNativePage();
        if (page instanceof BrowserNewTabPage) {
            return super.shouldCaptureThumbnail();
        }

        InvalidationAwareThumbnailProvider provider = getCurrentThumbnailProvider(page);
        return (provider != null) && provider.shouldCaptureThumbnail();
    }

    @Override
    public void captureThumbnail(Canvas canvas) {
        mNeedsCapture = false;
        NativePage page = getCurrentNativePage();
        ViewUtils.captureBitmap(mNTPLinearLayout, canvas);
        //Update the captures for each page. Ugly but minimal code change.
        if (page instanceof BrowserNewTabPage) {
            ((NewTabPageView) super.getView()).onExternalCapture();
            //update view bounds for this guy
        } else if (page instanceof RecentTabsPage) {
            ((RecentTabsPage)page).onExternalCapture();
        } else if (page instanceof BookmarkPage) {
            ((BookmarkPage)page).onExternalCapture();
        }
    }


    public void showBookmarksPage() {
        int index = mTabs.indexOf(new BrowserNTPTab(mActivity.getString(R.string.ntp_bookmarks)));
        mPager.setCurrentItem(index);
    }

    public void showRecentTabs() {
        int index = mTabs.indexOf(new BrowserNTPTab(mActivity.getString(R.string.recent_tabs)));
        mPager.setCurrentItem(index);
    }

    /**
     * This class holds visible Native Pages and any related information.
     */
    private class BrowserNTPTab {
        private String mTabTitle;
        private int mTabIcon;
        private NativePage mPage;

        /**
         *
         * @param page The Native Page associated with this BrowserNTPTab
         * @param title The title of the Page to be shown on the tabs in the tablayout.
         * @param icon_id The resource ID of the drawable to show in the tabs in the tablayout.
         *                Use @BrowserNewTabPage.TAB_NO_ICON if no icon exists
         */
        public BrowserNTPTab(NativePage page, String title, int icon_id) {
            mPage = page;
            mTabTitle = title;
            mTabIcon = icon_id;
        }

        /**
         * This constructor is used for comparisons. BrowserNTPTabs are compared by their title.
         * @param title The Title that should be used for comparisons.
         */
        public BrowserNTPTab(String title) {
            this(null, title, TAB_NO_ICON);
        }

        public NativePage getPage() {
            return mPage;
        }

        public String getTitle() {
            return mTabTitle;
        }

        public int getIcon() {
            return mTabIcon;
        }

        @Override
        public boolean equals(Object object) {
            if (object == this) return true;
            if (!(object instanceof BrowserNTPTab)) return false;
            BrowserNTPTab input = (BrowserNTPTab) object;
            return TextUtils.equals(input.getTitle(), mTabTitle);
        }

        @Override
        public int hashCode() {
            return 31 *mTabTitle.hashCode();
        }
    }
}

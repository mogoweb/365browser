// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.layouts;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;

import org.chromium.chrome.browser.compositor.LayerTitleCache;
import org.chromium.chrome.browser.compositor.bottombar.contextualsearch.ContextualSearchPanel;
import org.chromium.chrome.browser.compositor.bottombar.contextualsearch.ContextualSearchPanelHost;
import org.chromium.chrome.browser.compositor.layouts.content.TabContentManager;
import org.chromium.chrome.browser.compositor.layouts.eventfilter.EventFilter;
import org.chromium.chrome.browser.compositor.scene_layer.ContextualSearchSceneLayer;
import org.chromium.chrome.browser.compositor.scene_layer.SceneLayer;
import org.chromium.chrome.browser.fullscreen.ChromeFullscreenManager;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.ui.resources.ResourceManager;

import java.util.List;

/**
 * A {@link Layout} that can show a Contextual Search overlay that shows at the
 * bottom and can be swiped upwards.
 */
public abstract class ContextualSearchSupportedLayout extends Layout {
    /**
     * The {@link ContextualSearchPanelHost} that allows the {@link ContextualSearchPanel} to
     * communicate back to the Layout.
     */
    protected final ContextualSearchPanelHost mContextualSearchPanelHost;

    /**
     * The {@link ContextualSearchPanel} that represents the Contextual Search UI.
     */
    protected final ContextualSearchPanel mSearchPanel;

    /**
     * The {@link SceneLayer} that renders contextual search UI.
     */
    private final ContextualSearchSceneLayer mContextualSearchSceneLayer;

    /**
     * Size of half pixel in dps.
     */
    private final float mHalfPixelDp;

    /**
     * @param context The current Android context.
     * @param updateHost The {@link LayoutUpdateHost} view for this layout.
     * @param renderHost The {@link LayoutRenderHost} view for this layout.
     * @param eventFilter The {@link EventFilter} that is needed for this view.
     * @param panel The {@link ContextualSearchPanel} that represents the Contextual Search UI.
     */
    public ContextualSearchSupportedLayout(Context context, LayoutUpdateHost updateHost,
            LayoutRenderHost renderHost, EventFilter eventFilter, ContextualSearchPanel panel) {
        super(context, updateHost, renderHost, eventFilter);

        mContextualSearchPanelHost = new ContextualSearchPanelHost() {
            @Override
            public void hideLayout(boolean immediately) {
                ContextualSearchSupportedLayout.this.hideContextualSearch(immediately);
            }
        };

        mSearchPanel = panel;
        float dpToPx = context.getResources().getDisplayMetrics().density;
        mHalfPixelDp = 0.5f / dpToPx;
        mContextualSearchSceneLayer = new ContextualSearchSceneLayer(dpToPx, panel);
    }

    @Override
    public void attachViews(ViewGroup container) {
        mSearchPanel.setContainerView(container);
    }

    @Override
    public void getAllViews(List<View> views) {
        // TODO(dtrainor): If we move ContextualSearch to an overlay, pull the views from there
        // instead in Layout.java.
        if (mSearchPanel != null && mSearchPanel.getManagementDelegate() != null) {
            ContentViewCore content =
                    mSearchPanel.getManagementDelegate().getSearchContentViewCore();
            if (content != null) views.add(content.getContainerView());
        }
        super.getAllViews(views);
    }

    @Override
    public void getAllContentViewCores(List<ContentViewCore> contents) {
        // TODO(dtrainor): If we move ContextualSearch to an overlay, pull the content from there
        // instead in Layout.java.
        if (mSearchPanel != null && mSearchPanel.getManagementDelegate() != null) {
            ContentViewCore content =
                    mSearchPanel.getManagementDelegate().getSearchContentViewCore();
            if (content != null) contents.add(content);
        }
        super.getAllContentViewCores(contents);
    }

    @Override
    public void show(long time, boolean animate) {
        mSearchPanel.setHost(mContextualSearchPanelHost);
        super.show(time, animate);
    }

    /**
     * Hides the Contextual Search Supported Layout.
     * @param immediately Whether it should be hidden immediately.
     */
    protected void hideContextualSearch(boolean immediately) {
        // NOTE(pedrosimonetti): To be implemented by a supported Layout.
    }

    @Override
    protected void notifySizeChanged(float width, float height, int orientation) {
        super.notifySizeChanged(width, height, orientation);

        // NOTE(pedrosimonetti): Due to some floating point madness, getHeight() and
        // getHeightMinusTopControls() might not always be the same when the Toolbar is
        // visible. For this reason, we're comparing to see if the difference between them
        // is less than half pixel. If so, it means the Toolbar is visible.
        final boolean isToolbarVisible = getHeight() - getHeightMinusTopControls() <= mHalfPixelDp;
        mSearchPanel.onSizeChanged(width, height, isToolbarVisible);
    }

    @Override
    protected boolean onUpdateAnimation(long time, boolean jumpToEnd) {
        boolean parentAnimating = super.onUpdateAnimation(time, jumpToEnd);
        boolean panelAnimating = mSearchPanel.onUpdateAnimation(time, jumpToEnd);
        return panelAnimating || parentAnimating;
    }

    @Override
    protected SceneLayer getSceneLayer() {
        return mContextualSearchSceneLayer;
    }

    @Override
    protected void updateSceneLayer(Rect viewport, Rect contentViewport,
            LayerTitleCache layerTitleCache, TabContentManager tabContentManager,
            ResourceManager resourceManager, ChromeFullscreenManager fullscreenManager) {
        super.updateSceneLayer(viewport, contentViewport, layerTitleCache, tabContentManager,
                resourceManager, fullscreenManager);
        if (!mSearchPanel.isShowing()) return;

        if (mContextualSearchSceneLayer == null || mSearchPanel.getManagementDelegate() == null) {
            return;
        }

        ContentViewCore contentViewCore =
                mSearchPanel.getManagementDelegate().getSearchContentViewCore();
        mContextualSearchSceneLayer.update(contentViewCore, resourceManager);
    }
}

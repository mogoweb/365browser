// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.layouts;

import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import org.chromium.base.ObserverList;
import org.chromium.base.TraceEvent;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.chrome.browser.compositor.LayerTitleCache;
import org.chromium.chrome.browser.compositor.layouts.Layout.Orientation;
import org.chromium.chrome.browser.compositor.layouts.components.LayoutTab;
import org.chromium.chrome.browser.compositor.layouts.components.VirtualView;
import org.chromium.chrome.browser.compositor.layouts.content.TabContentManager;
import org.chromium.chrome.browser.compositor.layouts.eventfilter.EdgeSwipeHandler;
import org.chromium.chrome.browser.compositor.layouts.eventfilter.EventFilter;
import org.chromium.chrome.browser.compositor.scene_layer.SceneLayer;
import org.chromium.chrome.browser.contextualsearch.ContextualSearchManagementDelegate;
import org.chromium.chrome.browser.dom_distiller.ReaderModeManagerDelegate;
import org.chromium.chrome.browser.fullscreen.ChromeFullscreenManager;
import org.chromium.chrome.browser.fullscreen.FullscreenManager;
import org.chromium.chrome.browser.tabmodel.EmptyTabModelSelectorObserver;
import org.chromium.chrome.browser.tabmodel.TabCreatorManager;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.ui.base.SPenSupport;
import org.chromium.ui.resources.ResourceManager;
import org.chromium.ui.resources.dynamics.DynamicResourceLoader;

import java.util.List;

/**
 * A class that is responsible for managing an active {@link Layout} to show to the screen.  This
 * includes lifecycle managment like showing/hiding this {@link Layout}.
 */
public abstract class LayoutManager implements LayoutUpdateHost, LayoutProvider {
    /** Sampling at 60 fps. */
    private static final long FRAME_DELTA_TIME_MS = 16;

    /** Used to convert pixels to dp. */
    protected final float mPxToDp;

    /** The {@link LayoutManagerHost}, who is responsible for showing the active {@link Layout}. */
    protected final LayoutManagerHost mHost;

    /** The last X coordinate of the last {@link MotionEvent#ACTION_DOWN} event. */
    protected int mLastTapX;

    /** The last Y coordinate of the last {@link MotionEvent#ACTION_DOWN} event. */
    protected int mLastTapY;

    // External Dependencies
    private TabModelSelector mTabModelSelector;
    private ViewGroup mContentContainer;

    // External Observers
    private final ObserverList<SceneChangeObserver> mSceneChangeObservers;

    // Current Layout State
    private Layout mActiveLayout;
    private Layout mNextActiveLayout;

    // Current Event Fitler State
    private EventFilter mActiveEventFilter;

    // Internal State
    private int mFullscreenToken = FullscreenManager.INVALID_TOKEN;
    private boolean mUpdateRequested;

    // Whether or not the last layout was showing the browser controls.
    private boolean mPreviousLayoutShowingToolbar;

    // Used to store the visible viewport and not create a new Rect object every frame.
    private final RectF mCachedVisibleViewport = new RectF();
    private final RectF mCachedWindowViewport = new RectF();

    private final RectF mCachedRect = new RectF();
    private final PointF mCachedPoint = new PointF();

    // Whether the currently active event filter has changed.
    private boolean mIsNewEventFilter;

    /**
     * Creates a {@link LayoutManager} instance.
     * @param host A {@link LayoutManagerHost} instance.
     */
    public LayoutManager(LayoutManagerHost host) {
        mHost = host;
        mPxToDp = 1.f / mHost.getContext().getResources().getDisplayMetrics().density;
        mSceneChangeObservers = new ObserverList<SceneChangeObserver>();
    }

    /**
     * @return The actual current time of the app in ms.
     */
    public static long time() {
        return SystemClock.uptimeMillis();
    }

    /**
     * Gives the {@link LayoutManager} a chance to intercept and process touch events from the
     * Android {@link View} system.
     * @param e                 The {@link MotionEvent} that might be intercepted.
     * @param isKeyboardShowing Whether or not the keyboard is showing.
     * @return                  Whether or not this current touch gesture should be intercepted and
     *                          continually forwarded to this class.
     */
    public boolean onInterceptTouchEvent(MotionEvent e, boolean isKeyboardShowing) {
        if (mActiveLayout == null) return false;

        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            mLastTapX = (int) e.getX();
            mLastTapY = (int) e.getY();
        }

        PointF offsets = getMotionOffsets(e);

        EventFilter layoutFilter =
                mActiveLayout.findInterceptingEventFilter(e, offsets, isKeyboardShowing);
        mIsNewEventFilter = layoutFilter != mActiveEventFilter;
        mActiveEventFilter = layoutFilter;

        if (mActiveEventFilter != null) mActiveLayout.unstallImmediately();
        return mActiveEventFilter != null;
    }

    /**
     * Gives the {@link LayoutManager} a chance to process the touch events from the Android
     * {@link View} system.
     * @param e A {@link MotionEvent} instance.
     * @return  Whether or not {@code e} was consumed.
     */
    public boolean onTouchEvent(MotionEvent e) {
        if (mActiveEventFilter == null) return false;

        // Make sure the first event through the filter is an ACTION_DOWN.
        if (mIsNewEventFilter && e.getActionMasked() != MotionEvent.ACTION_DOWN) {
            MotionEvent downEvent = MotionEvent.obtain(e);
            downEvent.setAction(MotionEvent.ACTION_DOWN);
            if (!onTouchEventInternal(downEvent)) return false;
        }
        mIsNewEventFilter = false;

        return onTouchEventInternal(e);
    }

    private boolean onTouchEventInternal(MotionEvent e) {
        boolean consumed = mActiveEventFilter.onTouchEvent(e);
        PointF offsets = getMotionOffsets(e);
        if (offsets != null) mActiveEventFilter.setCurrentMotionEventOffsets(offsets.x, offsets.y);
        return consumed;
    }

    private PointF getMotionOffsets(MotionEvent e) {
        int actionMasked = SPenSupport.convertSPenEventAction(e.getActionMasked());

        if (actionMasked == MotionEvent.ACTION_DOWN
                || actionMasked == MotionEvent.ACTION_HOVER_ENTER) {
            getViewportPixel(mCachedRect);

            mCachedPoint.set(-mCachedRect.left, -mCachedRect.top);
            return mCachedPoint;
        } else if (actionMasked == MotionEvent.ACTION_UP
                || actionMasked == MotionEvent.ACTION_CANCEL
                || actionMasked == MotionEvent.ACTION_HOVER_EXIT) {
            mCachedPoint.set(0, 0);
            return mCachedPoint;
        }

        return null;
    }

    /**
     * Updates the state of the active {@link Layout} if needed.  This updates the animations and
     * cascades the changes to the tabs.
     */
    public void onUpdate() {
        TraceEvent.begin("LayoutDriver:onUpdate");
        onUpdate(time(), FRAME_DELTA_TIME_MS);
        TraceEvent.end("LayoutDriver:onUpdate");
    }

    /**
     * Updates the state of the layout.
     * @param timeMs The time in milliseconds.
     * @param dtMs   The delta time since the last update in milliseconds.
     * @return       Whether or not the {@link LayoutManager} needs more updates.
     */
    @VisibleForTesting
    public boolean onUpdate(long timeMs, long dtMs) {
        if (!mUpdateRequested) return false;
        mUpdateRequested = false;
        final Layout layout = getActiveLayout();
        if (layout != null && layout.onUpdate(timeMs, dtMs) && layout.isHiding()) {
            layout.doneHiding();
        }
        return mUpdateRequested;
    }

    /**
     * Initializes the {@link LayoutManager}.  Must be called before using this object.
     * @param selector                 A {@link TabModelSelector} instance.
     * @param creator                  A {@link TabCreatorManager} instance.
     * @param content                  A {@link TabContentManager} instance.
     * @param androidContentContainer  A {@link ViewGroup} for Android views to be bound to.
     * @param contextualSearchDelegate A {@link ContextualSearchDelegate} instance.
     * @param readerModeDelegate       A {@link ReaderModeManagerDelegate} instance.
     * @param dynamicResourceLoader    A {@link DynamicResourceLoader} instance.
     */
    public void init(TabModelSelector selector, TabCreatorManager creator,
            TabContentManager content, ViewGroup androidContentContainer,
            ContextualSearchManagementDelegate contextualSearchDelegate,
            ReaderModeManagerDelegate readerModeDelegate,
            DynamicResourceLoader dynamicResourceLoader) {
        mTabModelSelector = selector;
        mContentContainer = androidContentContainer;

        if (mNextActiveLayout != null) startShowing(mNextActiveLayout, true);

        updateLayoutForTabModelSelector();
    }

    /**
     * Cleans up and destroys this object.  It should not be used after this.
     */
    public void destroy() {
        mSceneChangeObservers.clear();
    }

    /**
     * @param observer Adds {@code observer} to be notified when the active {@code Layout} changes.
     */
    public void addSceneChangeObserver(SceneChangeObserver observer) {
        mSceneChangeObservers.addObserver(observer);
    }

    /**
     * @param observer Removes {@code observer}.
     */
    public void removeSceneChangeObserver(SceneChangeObserver observer) {
        mSceneChangeObservers.removeObserver(observer);
    }

    @Override
    public SceneLayer getUpdatedActiveSceneLayer(LayerTitleCache layerTitleCache,
            TabContentManager tabContentManager, ResourceManager resourceManager,
            ChromeFullscreenManager fullscreenManager) {
        // Update the android browser controls state.
        if (fullscreenManager != null) {
            fullscreenManager.setHideBrowserControlsAndroidView(
                    mActiveLayout.forceHideBrowserControlsAndroidView());
        }

        getViewportPixel(mCachedVisibleViewport);
        mHost.getWindowViewport(mCachedWindowViewport);
        return mActiveLayout.getUpdatedSceneLayer(mCachedWindowViewport, mCachedVisibleViewport,
                layerTitleCache, tabContentManager, resourceManager, fullscreenManager);
    }

    /**
     * Called when the viewport has been changed.  Override this to be notified when
     * {@link #pushNewViewport(Rect, Rect, int)} calls actually change the current viewport.
     */
    public void onViewportChanged() {
        if (getActiveLayout() != null) {
            mHost.getWindowViewport(mCachedWindowViewport);
            mHost.getVisibleViewport(mCachedVisibleViewport);
            getActiveLayout().sizeChanged(mCachedVisibleViewport, mCachedWindowViewport,
                    mHost.getHeightMinusBrowserControls(), getOrientation());
        }
    }

    /**
     * @return The default {@link Layout} to show when {@link Layout}s get hidden and the next
     *         {@link Layout} to show isn't known.
     */
    protected abstract Layout getDefaultLayout();

    // TODO(dtrainor): Remove these from this control class.  Split the interface?
    @Override public abstract void initLayoutTabFromHost(final int tabId);

    @Override
    public abstract LayoutTab createLayoutTab(int id, boolean incognito, boolean showCloseButton,
            boolean isTitleNeeded, float maxContentWidth, float maxContentHeight);

    @Override public abstract void releaseTabLayout(int id);

    /**
     * @return The {@link TabModelSelector} instance this class knows about.
     */
    protected TabModelSelector getTabModelSelector() {
        return mTabModelSelector;
    }

    /**
     * @return The next {@link Layout} that will be shown.  If no {@link Layout} has been set
     *         since the last time {@link #startShowing(Layout, boolean)} was called, this will be
     *         {@link #getDefaultLayout()}.
     */
    protected Layout getNextLayout() {
        return mNextActiveLayout != null ? mNextActiveLayout : getDefaultLayout();
    }

    @Override
    public Layout getActiveLayout() {
        return mActiveLayout;
    }

    @Override
    public void getViewportPixel(RectF rect) {
        if (getActiveLayout() == null) {
            mHost.getWindowViewport(rect);
            return;
        }

        switch (getActiveLayout().getViewportMode()) {
            case ALWAYS_FULLSCREEN:
                mHost.getWindowViewport(rect);
                break;

            case ALWAYS_SHOWING_BROWSER_CONTROLS:
                mHost.getViewportFullControls(rect);
                break;

            case USE_PREVIOUS_BROWSER_CONTROLS_STATE:
                if (mPreviousLayoutShowingToolbar) {
                    mHost.getViewportFullControls(rect);
                } else {
                    mHost.getWindowViewport(rect);
                }
                break;

            case DYNAMIC_BROWSER_CONTROLS:
            default:
                mHost.getVisibleViewport(rect);
        }
    }

    @Override
    public ChromeFullscreenManager getFullscreenManager() {
        return mHost != null ? mHost.getFullscreenManager() : null;
    }

    @Override
    public void requestUpdate() {
        if (!mUpdateRequested) mHost.requestRender();
        mUpdateRequested = true;
    }

    @Override
    public void startHiding(int nextTabId, boolean hintAtTabSelection) {
        requestUpdate();
        if (hintAtTabSelection) {
            for (SceneChangeObserver observer : mSceneChangeObservers) {
                observer.onTabSelectionHinted(nextTabId);
            }
        }
    }

    @SuppressFBWarnings("RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE")
    @Override
    public void doneHiding() {
        // TODO: If next layout is default layout clear caches (should this be a sub layout thing?)

        assert mNextActiveLayout != null : "Need to have a next active layout.";
        if (mNextActiveLayout != null) {
            startShowing(mNextActiveLayout, true);
        }
    }

    @Override
    public void doneShowing() {}

    /**
     * Should be called by control logic to show a new {@link Layout}.
     *
     * TODO(dtrainor, clholgat): Clean up the show logic to guarantee startHiding/doneHiding get
     * called.
     *
     * @param layout  The new {@link Layout} to show.
     * @param animate Whether or not {@code layout} should animate as it shows.
     */
    protected void startShowing(Layout layout, boolean animate) {
        assert mTabModelSelector != null : "init() must be called first.";
        assert layout != null : "Can't show a null layout.";

        // Set the new layout
        setNextLayout(null);
        Layout oldLayout = getActiveLayout();
        if (oldLayout != layout) {
            if (oldLayout != null) {
                oldLayout.detachViews();
            }
            layout.contextChanged(mHost.getContext());
            layout.attachViews(mContentContainer);
            mActiveLayout = layout;
        }

        ChromeFullscreenManager fullscreenManager = mHost.getFullscreenManager();
        if (fullscreenManager != null) {
            mPreviousLayoutShowingToolbar = !fullscreenManager.areBrowserControlsOffScreen();

            // Release any old fullscreen token we were holding.
            fullscreenManager.getBrowserVisibilityDelegate().hideControlsPersistent(
                    mFullscreenToken);
            mFullscreenToken = FullscreenManager.INVALID_TOKEN;

            // Grab a new fullscreen token if this layout can't be in fullscreen.
            if (getActiveLayout().forceShowBrowserControlsAndroidView()) {
                mFullscreenToken =
                        fullscreenManager.getBrowserVisibilityDelegate().showControlsPersistent();
            }
        }

        onViewportChanged();
        getActiveLayout().show(time(), animate);
        mHost.setContentOverlayVisibility(getActiveLayout().shouldDisplayContentOverlay());
        mHost.requestRender();

        // Notify observers about the new scene.
        for (SceneChangeObserver observer : mSceneChangeObservers) {
            observer.onSceneChange(getActiveLayout());
        }
    }

    /**
     * Sets the next {@link Layout} to show after the current {@link Layout} is finished and is done
     * hiding.
     * @param layout The new {@link Layout} to show.
     */
    public void setNextLayout(Layout layout) {
        mNextActiveLayout = (layout == null) ? getDefaultLayout() : layout;
    }

    @Override
    public boolean isActiveLayout(Layout layout) {
        return layout == mActiveLayout;
    }

    /**
     * Get a list of virtual views for accessibility.
     *
     * @param views A List to populate with virtual views.
     */
    public abstract void getVirtualViews(List<VirtualView> views);

    /**
     * @return The {@link EdgeSwipeHandler} responsible for processing swipe events for the toolbar.
     */
    public abstract EdgeSwipeHandler getTopSwipeHandler();

    /**
     * Should be called when the user presses the back button on the phone.
     * @return Whether or not the back button was consumed by the active {@link Layout}.
     */
    public abstract boolean onBackPressed();

    private int getOrientation() {
        if (mHost.getWidth() > mHost.getHeight()) {
            return Orientation.LANDSCAPE;
        } else {
            return Orientation.PORTRAIT;
        }
    }

    /**
     * Updates the Layout for the state of the {@link TabModelSelector} after initialization.
     * If the TabModelSelector is not yet initialized when this function is called, a
     * {@link TabModelSelectorObserver} is created to listen for when it is ready.
     */
    private void updateLayoutForTabModelSelector() {
        if (mTabModelSelector.isTabStateInitialized() && getActiveLayout() != null) {
            getActiveLayout().onTabStateInitialized();
        } else {
            mTabModelSelector.addObserver(new EmptyTabModelSelectorObserver() {
                @Override
                public void onTabStateInitialized() {
                    if (getActiveLayout() != null) getActiveLayout().onTabStateInitialized();

                    final EmptyTabModelSelectorObserver observer = this;
                    new Handler().post(new Runnable() {
                        @Override
                        public void run() {
                            mTabModelSelector.removeObserver(observer);
                        }
                    });
                }
            });
        }
    }
}

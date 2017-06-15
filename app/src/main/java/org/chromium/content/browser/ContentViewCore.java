// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.assist.AssistStructure.ViewNode;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Pair;
import android.view.ActionMode;
import android.view.DragEvent;
import android.view.HapticFeedbackConstants;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStructure;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityManager.AccessibilityStateChangeListener;
import android.view.accessibility.AccessibilityNodeProvider;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import org.chromium.base.ObserverList;
import org.chromium.base.ObserverList.RewindableIterator;
import org.chromium.base.TraceEvent;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.content.browser.accessibility.BrowserAccessibilityManager;
import org.chromium.content.browser.accessibility.captioning.CaptioningBridgeFactory;
import org.chromium.content.browser.accessibility.captioning.SystemCaptioningBridge;
import org.chromium.content.browser.accessibility.captioning.TextTrackSettings;
import org.chromium.content.browser.input.ImeAdapter;
import org.chromium.content.browser.input.InputMethodManagerWrapper;
import org.chromium.content.browser.input.SelectPopup;
import org.chromium.content.browser.input.SelectPopupDialog;
import org.chromium.content.browser.input.SelectPopupDropdown;
import org.chromium.content.browser.input.SelectPopupItem;
import org.chromium.content_public.browser.AccessibilitySnapshotCallback;
import org.chromium.content_public.browser.AccessibilitySnapshotNode;
import org.chromium.content_public.browser.ActionModeCallbackHelper;
import org.chromium.content_public.browser.GestureStateListener;
import org.chromium.content_public.browser.ImeEventObserver;
import org.chromium.content_public.browser.WebContents;
import org.chromium.content_public.browser.WebContentsObserver;
import org.chromium.device.gamepad.GamepadList;
import org.chromium.ui.base.DeviceFormFactor;
import org.chromium.ui.base.EventForwarder;
import org.chromium.ui.base.ViewAndroidDelegate;
import org.chromium.ui.base.WindowAndroid;
import org.chromium.ui.display.DisplayAndroid;
import org.chromium.ui.display.DisplayAndroid.DisplayAndroidObserver;

import java.lang.annotation.Annotation;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Provides a Java-side 'wrapper' around a WebContent (native) instance.
 * Contains all the major functionality necessary to manage the lifecycle of a ContentView without
 * being tied to the view system.
 *
 * WARNING: ContentViewCore is in the process of being broken up. Please do not add new stuff.
 * See https://crbug.com/598880.
 */
@JNINamespace("content")
public class ContentViewCore implements AccessibilityStateChangeListener, DisplayAndroidObserver,
                                        SystemCaptioningBridge.SystemCaptioningBridgeListener,
                                        WindowAndroidProvider, ImeEventObserver {
    private static final String TAG = "cr_ContentViewCore";

    // Used to avoid enabling zooming in / out if resulting zooming will
    // produce little visible difference.
    private static final float ZOOM_CONTROLS_EPSILON = 0.007f;

    // If the embedder adds a JavaScript interface object that contains an indirect reference to
    // the ContentViewCore, then storing a strong ref to the interface object on the native
    // side would prevent garbage collection of the ContentViewCore (as that strong ref would
    // create a new GC root).
    // For that reason, we store only a weak reference to the interface object on the
    // native side. However we still need a strong reference on the Java side to
    // prevent garbage collection if the embedder doesn't maintain their own ref to the
    // interface object - the Java side ref won't create a new GC root.
    // This map stores those references. We put into the map on addJavaScriptInterface()
    // and remove from it in removeJavaScriptInterface(). The annotation class is stored for
    // the purpose of migrating injected objects from one instance of CVC to another, which
    // is used by Android WebView to support WebChromeClient.onCreateWindow scenario.
    private final Map<String, Pair<Object, Class>> mJavaScriptInterfaces =
            new HashMap<String, Pair<Object, Class>>();

    // Additionally, we keep track of all Java bound JS objects that are in use on the
    // current page to ensure that they are not garbage collected until the page is
    // navigated. This includes interface objects that have been removed
    // via the removeJavaScriptInterface API and transient objects returned from methods
    // on the interface object. Note we use HashSet rather than Set as the native side
    // expects HashSet (no bindings for interfaces).
    private final HashSet<Object> mRetainedJavaScriptObjects = new HashSet<Object>();

    /**
     * A {@link WebContentsObserver} that listens to frame navigation events.
     */
    private static class ContentViewWebContentsObserver extends WebContentsObserver {
        // Using a weak reference avoids cycles that might prevent GC of WebView's WebContents.
        private final WeakReference<ContentViewCore> mWeakContentViewCore;

        ContentViewWebContentsObserver(ContentViewCore contentViewCore) {
            super(contentViewCore.getWebContents());
            mWeakContentViewCore = new WeakReference<ContentViewCore>(contentViewCore);
        }

        @Override
        public void didFinishNavigation(String url, boolean isInMainFrame, boolean isErrorPage,
                boolean hasCommitted, boolean isSameDocument, boolean isFragmentNavigation,
                Integer pageTransition, int errorCode, String errorDescription,
                int httpStatusCode) {
            if (hasCommitted && isInMainFrame && !isSameDocument) {
                resetPopupsAndInput();
            }
        }

        @Override
        public void renderProcessGone(boolean wasOomProtected) {
            resetPopupsAndInput();
            ContentViewCore contentViewCore = mWeakContentViewCore.get();
            if (contentViewCore == null) return;
            contentViewCore.mImeAdapter.resetAndHideKeyboard();
        }

        private void resetPopupsAndInput() {
            ContentViewCore contentViewCore = mWeakContentViewCore.get();
            if (contentViewCore == null) return;
            contentViewCore.mIsMobileOptimizedHint = false;
            contentViewCore.hidePopupsAndClearSelection();
            contentViewCore.resetScrollInProgress();
        }
    }

    /**
     * Interface that consumers of {@link ContentViewCore} must implement to allow the proper
     * dispatching of view methods through the containing view.
     *
     * <p>
     * All methods with the "super_" prefix should be routed to the parent of the
     * implementing container view.
     */
    @SuppressWarnings("javadoc")
    public interface InternalAccessDelegate {
        /**
         * @see View#onKeyUp(keyCode, KeyEvent)
         */
        boolean super_onKeyUp(int keyCode, KeyEvent event);

        /**
         * @see View#dispatchKeyEvent(KeyEvent)
         */
        boolean super_dispatchKeyEvent(KeyEvent event);

        /**
         * @see View#onGenericMotionEvent(MotionEvent)
         */
        boolean super_onGenericMotionEvent(MotionEvent event);

        /**
         * @see View#onConfigurationChanged(Configuration)
         */
        void super_onConfigurationChanged(Configuration newConfig);

        /**
         * @see View#onScrollChanged(int, int, int, int)
         */
        void onScrollChanged(int lPix, int tPix, int oldlPix, int oldtPix);

        /**
         * @see View#awakenScrollBars()
         */
        boolean awakenScrollBars();

        /**
         * @see View#awakenScrollBars(int, boolean)
         */
        boolean super_awakenScrollBars(int startDelay, boolean invalidate);
    }

    private final Context mContext;
    private final String mProductVersion;
    private ViewGroup mContainerView;
    private InternalAccessDelegate mContainerViewInternals;
    private WebContents mWebContents;
    private WebContentsObserver mWebContentsObserver;

    // Native pointer to C++ ContentViewCoreImpl object which will be set by nativeInit().
    private long mNativeContentViewCore;

    private boolean mAttachedToWindow;
    private final ObserverList<GestureStateListener> mGestureStateListeners;
    private final RewindableIterator<GestureStateListener> mGestureStateListenersIterator;

    private PopupZoomer mPopupZoomer;
    private SelectPopup mSelectPopup;
    private long mNativeSelectPopupSourceFrame;

    // Only valid when focused on a text / password field.
    private ImeAdapter mImeAdapter;

    // Size of the viewport in physical pixels as set from onSizeChanged.
    private int mViewportWidthPix;
    private int mViewportHeightPix;
    private int mTopControlsHeightPix;
    private int mBottomControlsHeightPix;
    private boolean mTopControlsShrinkBlinkSize;

    // Cached copy of all positions and scales as reported by the renderer.
    private final RenderCoordinates mRenderCoordinates;

    // Whether joystick scroll is enabled.  It's disabled when an editable field is focused.
    private boolean mJoystickScrollEnabled = true;

    private boolean mIsMobileOptimizedHint;

    private SelectionPopupController mSelectionPopupController;
    private boolean mPreserveSelectionOnNextLossOfFocus;

    // Whether native accessibility, i.e. without any script injection, is allowed.
    private boolean mNativeAccessibilityAllowed;

    // Whether native accessibility, i.e. without any script injection, has been enabled.
    private boolean mNativeAccessibilityEnabled;

    // Handles native accessibility, i.e. without any script injection.
    private BrowserAccessibilityManager mBrowserAccessibilityManager;

    // System accessibility service.
    private final AccessibilityManager mAccessibilityManager;

    // If true, the web contents are obscured by another view and we shouldn't
    // return an AccessibilityNodeProvider or process touch exploration events.
    private boolean mIsObscuredByAnotherView;

    // Notifies the ContentViewCore when platform closed caption settings have changed
    // if they are supported. Otherwise does nothing.
    private final SystemCaptioningBridge mSystemCaptioningBridge;

    // Accessibility touch exploration state.
    private boolean mTouchExplorationEnabled;

    // Whether accessibility focus should be set to the page when it finishes loading.
    // This only applies if an accessibility service like TalkBack is running.
    // This is desirable behavior for a browser window, but not for an embedded
    // WebView.
    private boolean mShouldSetAccessibilityFocusOnPageLoad;

    // Whether a touch scroll sequence is active, used to hide text selection
    // handles. Note that a scroll sequence will *always* bound a pinch
    // sequence, so this will also be true for the duration of a pinch gesture.
    private boolean mTouchScrollInProgress;

    // The outstanding fling start events that hasn't got fling end yet. It may be > 1 because
    // onNativeFlingStopped() is called asynchronously.
    private int mPotentiallyActiveFlingCount;

    /**
     * PID used to indicate an invalid render process.
     */
    // Keep in sync with the value returned from ContentViewCoreImpl::GetCurrentRendererProcessId()
    // if there is no render process.
    public static final int INVALID_RENDER_PROCESS_PID = 0;

    // Offsets for the events that passes through this ContentViewCore.
    private float mCurrentTouchOffsetX;
    private float mCurrentTouchOffsetY;

    // True if we want to disable Android native event batching and use compositor event queue.
    private boolean mShouldRequestUnbufferedDispatch;

    // Whether the ContentViewCore requires the WebContents to be fullscreen in order to lock the
    // screen orientation.
    private boolean mFullscreenRequiredForOrientationLock = true;

    // A ViewAndroidDelegate that delegates to the current container view.
    private ViewAndroidDelegate mViewAndroidDelegate;

    private Boolean mHasViewFocus;

    // The list of observers that are notified when ContentViewCore changes its WindowAndroid.
    private final ObserverList<WindowAndroidChangedObserver> mWindowAndroidChangedObservers;

    /**
     * @param webContents The {@link WebContents} to find a {@link ContentViewCore} of.
     * @return            A {@link ContentViewCore} that is connected to {@code webContents} or
     *                    {@code null} if none exists.
     */
    public static ContentViewCore fromWebContents(WebContents webContents) {
        return nativeFromWebContentsAndroid(webContents);
    }

    /**
     * Constructs a new ContentViewCore. Embedders must call initialize() after constructing
     * a ContentViewCore and before using it.
     *
     * @param context The context used to create this.
     */
    public ContentViewCore(Context context, String productVersion) {
        mContext = context;
        mProductVersion = productVersion;
        mRenderCoordinates = new RenderCoordinates();
        mAccessibilityManager = (AccessibilityManager)
                getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
        mSystemCaptioningBridge = CaptioningBridgeFactory.getSystemCaptioningBridge(mContext);
        mGestureStateListeners = new ObserverList<GestureStateListener>();
        mGestureStateListenersIterator = mGestureStateListeners.rewindableIterator();

        mWindowAndroidChangedObservers = new ObserverList<WindowAndroidChangedObserver>();
    }

    /**
     * @return The context used for creating this ContentViewCore.
     */
    @CalledByNative
    public Context getContext() {
        return mContext;
    }

    /**
     * @return The ViewGroup that all view actions of this ContentViewCore should interact with.
     */
    public ViewGroup getContainerView() {
        return mContainerView;
    }

    /**
     * @return The WebContents currently being rendered.
     */
    public WebContents getWebContents() {
        return mWebContents;
    }

    /**
     * @return The WindowAndroid associated with this ContentViewCore.
     */
    @Override
    public WindowAndroid getWindowAndroid() {
        if (mNativeContentViewCore == 0) return null;
        return nativeGetJavaWindowAndroid(mNativeContentViewCore);
    }

    /**
     * @return The SelectionPopupController that handles select action mode on web contents.
     */
    @VisibleForTesting
    public SelectionPopupController getSelectionPopupControllerForTesting() {
        return mSelectionPopupController;
    }

    @VisibleForTesting
    public void setSelectionPopupControllerForTesting(SelectionPopupController actionMode) {
        mSelectionPopupController = actionMode;
    }

    @Override
    public void addWindowAndroidChangedObserver(WindowAndroidChangedObserver observer) {
        mWindowAndroidChangedObservers.addObserver(observer);
    }

    @Override
    public void removeWindowAndroidChangedObserver(WindowAndroidChangedObserver observer) {
        mWindowAndroidChangedObservers.removeObserver(observer);
    }

    /**
     *
     * @param browserControlsHeightPix       The height of the browser controls in pixels.
     * @param browserControlsShrinkBlinkSize The Y amount in pixels to shrink the viewport by.  This
     *                                   specifies how much smaller the Blink layout size should be
     *                                   relative to the size of this View.
     */
    public void setTopControlsHeight(int topControlsHeightPix, boolean topControlsShrinkBlinkSize) {
        if (topControlsHeightPix == mTopControlsHeightPix
                && topControlsShrinkBlinkSize == mTopControlsShrinkBlinkSize) {
            return;
        }

        mTopControlsHeightPix = topControlsHeightPix;
        mTopControlsShrinkBlinkSize = topControlsShrinkBlinkSize;
        if (mNativeContentViewCore != 0) nativeWasResized(mNativeContentViewCore);
    }

    /**
     * Sets the height of the bottom controls. If necessary, triggers a renderer resize.
     */
    public void setBottomControlsHeight(int bottomControlHeightPix) {
        if (mBottomControlsHeightPix == bottomControlHeightPix) return;
        mBottomControlsHeightPix = bottomControlHeightPix;
        if (mNativeContentViewCore != 0) nativeWasResized(mNativeContentViewCore);
    }

    public void addImeEventObserver(ImeEventObserver imeEventObserver) {
        mImeAdapter.addEventObserver(imeEventObserver);
    }

    @VisibleForTesting
    public void setImeAdapterForTest(ImeAdapter imeAdapter) {
        mImeAdapter = imeAdapter;
    }

    @VisibleForTesting
    public ImeAdapter getImeAdapterForTest() {
        return mImeAdapter;
    }

    /**
     *
     * @param viewDelegate Delegate to add/remove anchor views.
     * @param internalDispatcher Handles dispatching all hidden or super methods to the
     *                           containerView.
     * @param webContents A WebContents instance to connect to.
     * @param windowAndroid An instance of the WindowAndroid.
     */
    // Perform important post-construction set up of the ContentViewCore.
    // We do not require the containing view in the constructor to allow embedders to create a
    // ContentViewCore without having fully created its containing view. The containing view
    // is a vital component of the ContentViewCore, so embedders must exercise caution in what
    // they do with the ContentViewCore before calling initialize().
    // We supply the nativeWebContents pointer here rather than in the constructor to allow us
    // to set the private browsing mode at a later point for the WebView implementation.
    // Note that the caller remains the owner of the nativeWebContents and is responsible for
    // deleting it after destroying the ContentViewCore.
    public void initialize(ViewAndroidDelegate viewDelegate,
            InternalAccessDelegate internalDispatcher, WebContents webContents,
            WindowAndroid windowAndroid) {
        mViewAndroidDelegate = viewDelegate;
        setContainerView(viewDelegate.getContainerView());
        long windowNativePointer = windowAndroid.getNativePointer();
        assert windowNativePointer != 0;

        final float dipScale = windowAndroid.getDisplay().getDipScale();

        mRenderCoordinates.reset();
        mRenderCoordinates.setDeviceScaleFactor(dipScale, windowAndroid.getContext());

        mNativeContentViewCore = nativeInit(webContents, mViewAndroidDelegate, windowNativePointer,
                dipScale, mRetainedJavaScriptObjects);
        mWebContents = nativeGetWebContentsAndroid(mNativeContentViewCore);

        setContainerViewInternals(internalDispatcher);

        initPopupZoomer(mContext);
        mImeAdapter = new ImeAdapter(
                mWebContents, mContainerView, new InputMethodManagerWrapper(mContext));
        mImeAdapter.addEventObserver(this);

        mSelectionPopupController = new SelectionPopupController(
                mContext, windowAndroid, webContents, mContainerView, mRenderCoordinates);
        mSelectionPopupController.setCallback(ActionModeCallbackHelper.EMPTY_CALLBACK);
        mSelectionPopupController.setContainerView(mContainerView);

        mWebContentsObserver = new ContentViewWebContentsObserver(this);

        mShouldRequestUnbufferedDispatch = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                && ContentFeatureList.isEnabled(ContentFeatureList.REQUEST_UNBUFFERED_DISPATCH);
    }

    /**
     * Updates the native {@link ContentViewCore} with a new window. This moves the NativeView and
     * attached it to the new NativeWindow linked with the given {@link WindowAndroid}.
     * @param windowAndroid The new {@link WindowAndroid} for this {@link ContentViewCore}.
     */
    public void updateWindowAndroid(WindowAndroid windowAndroid) {
        removeDisplayAndroidObserver();
        long windowNativePointer = windowAndroid == null ? 0 : windowAndroid.getNativePointer();
        nativeUpdateWindowAndroid(mNativeContentViewCore, windowNativePointer);

        // TODO(yusufo): Rename this call to be general for tab reparenting.
        // Clean up cached popups that may have been created with an old activity.
        mSelectPopup = null;
        destroyPastePopup();

        addDisplayAndroidObserverIfNeeded();

        for (WindowAndroidChangedObserver observer : mWindowAndroidChangedObservers) {
            observer.onWindowAndroidChanged(windowAndroid);
        }
    }

    private EventForwarder getEventForwarder() {
        return getWebContents().getEventForwarder();
    }

    /**
     * Set {@link ActionMode.Callback} used by {@link SelectionPopupController}.
     * @param callback ActionMode.Callback instance.
     */
    public void setActionModeCallback(ActionMode.Callback callback) {
        mSelectionPopupController.setCallback(callback);
    }

    private void addDisplayAndroidObserverIfNeeded() {
        if (!mAttachedToWindow) return;
        WindowAndroid windowAndroid = getWindowAndroid();
        if (windowAndroid != null) {
            DisplayAndroid display = windowAndroid.getDisplay();
            display.addObserver(this);
            onRotationChanged(display.getRotation());
            onDIPScaleChanged(display.getDipScale());
        }
    }

    private void removeDisplayAndroidObserver() {
        WindowAndroid windowAndroid = getWindowAndroid();
        if (windowAndroid != null) {
            windowAndroid.getDisplay().removeObserver(this);
        }
    }

    /**
     * Sets a new container view for this {@link ContentViewCore}.
     *
     * <p>WARNING: This method can also be used to replace the existing container view,
     * but you should only do it if you have a very good reason to. Replacing the
     * container view has been designed to support fullscreen in the Webview so it
     * might not be appropriate for other use cases.
     *
     * <p>This method only performs a small part of replacing the container view and
     * embedders are responsible for:
     * <ul>
     *     <li>Disconnecting the old container view from this ContentViewCore</li>
     *     <li>Updating the InternalAccessDelegate</li>
     *     <li>Reconciling the state of this ContentViewCore with the new container view</li>
     *     <li>Tearing down and recreating the native GL rendering where appropriate</li>
     *     <li>etc.</li>
     * </ul>
     */
    public void setContainerView(ViewGroup containerView) {
        try {
            TraceEvent.begin("ContentViewCore.setContainerView");
            if (mContainerView != null) {
                hideSelectPopupWithCancelMessage();
                mPopupZoomer.hide(false);
                mImeAdapter.setContainerView(containerView);
            }

            mContainerView = containerView;
            mContainerView.setClickable(true);
            if (mSelectionPopupController != null) {
                mSelectionPopupController.setContainerView(containerView);
            }
        } finally {
            TraceEvent.end("ContentViewCore.setContainerView");
        }
    }

    @CalledByNative
    private void onNativeContentViewCoreDestroyed(long nativeContentViewCore) {
        assert nativeContentViewCore == mNativeContentViewCore;
        mNativeContentViewCore = 0;
    }

    /**
     * Set the Container view Internals.
     * @param internalDispatcher Handles dispatching all hidden or super methods to the
     *                           containerView.
     */
    public void setContainerViewInternals(InternalAccessDelegate internalDispatcher) {
        mContainerViewInternals = internalDispatcher;
    }

    @VisibleForTesting
    void initPopupZoomer(Context context) {
        mPopupZoomer = new PopupZoomer(context);
        mPopupZoomer.setOnVisibilityChangedListener(new PopupZoomer.OnVisibilityChangedListener() {
            // mContainerView can change, but this OnVisibilityChangedListener can only be used
            // to add and remove views from the mContainerViewAtCreation.
            private final ViewGroup mContainerViewAtCreation = mContainerView;

            @Override
            public void onPopupZoomerShown(final PopupZoomer zoomer) {
                mContainerViewAtCreation.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mContainerViewAtCreation.indexOfChild(zoomer) == -1) {
                            mContainerViewAtCreation.addView(zoomer);
                        }
                    }
                });
            }

            @Override
            public void onPopupZoomerHidden(final PopupZoomer zoomer) {
                mContainerViewAtCreation.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mContainerViewAtCreation.indexOfChild(zoomer) != -1) {
                            mContainerViewAtCreation.removeView(zoomer);
                            mContainerViewAtCreation.invalidate();
                        }
                    }
                });
            }
        });
        PopupZoomer.OnTapListener listener = new PopupZoomer.OnTapListener() {
            // mContainerView can change, but this OnTapListener can only be used
            // with the mContainerViewAtCreation.
            private final ViewGroup mContainerViewAtCreation = mContainerView;

            @Override
            public void onResolveTapDisambiguation(
                    long timeMs, float x, float y, boolean isLongPress) {
                if (mNativeContentViewCore == 0) return;
                mContainerViewAtCreation.requestFocus();
                nativeResolveTapDisambiguation(mNativeContentViewCore, timeMs, x, y, isLongPress);
            }
        };
        mPopupZoomer.setOnTapListener(listener);
    }

    @VisibleForTesting
    public void setPopupZoomerForTest(PopupZoomer popupZoomer) {
        mPopupZoomer = popupZoomer;
    }

    /**
     * Destroy the internal state of the ContentView. This method may only be
     * called after the ContentView has been removed from the view system. No
     * other methods may be called on this ContentView after this method has
     * been called.
     * Warning: destroy() is not guranteed to be called in Android WebView.
     * Any object that relies solely on destroy() being called to be cleaned up
     * will leak in Android WebView. If appropriate, consider clean up in
     * onDetachedFromWindow() which is guaranteed to be called in Android WebView.
     */
    public void destroy() {
        removeDisplayAndroidObserver();
        if (mNativeContentViewCore != 0) {
            nativeOnJavaContentViewCoreDestroyed(mNativeContentViewCore);
        }
        mWebContentsObserver.destroy();
        mWebContentsObserver = null;
        mImeAdapter.resetAndHideKeyboard();
        mWebContents = null;
        mNativeContentViewCore = 0;
        mJavaScriptInterfaces.clear();
        mRetainedJavaScriptObjects.clear();
        for (mGestureStateListenersIterator.rewind(); mGestureStateListenersIterator.hasNext();) {
            mGestureStateListenersIterator.next().onDestroyed();
        }
        mGestureStateListeners.clear();
        hidePopupsAndPreserveSelection();
        destroyPastePopup();

        // See warning in javadoc before adding more clean up code here.
    }

    /**
     * Returns true initially, false after destroy() has been called.
     * It is illegal to call any other public method after destroy().
     */
    public boolean isAlive() {
        return mNativeContentViewCore != 0;
    }

    /**
     * @return Viewport width in physical pixels as set from onSizeChanged.
     */
    @CalledByNative
    public int getViewportWidthPix() {
        return mViewportWidthPix;
    }

    /**
     * @return Viewport height in physical pixels as set from onSizeChanged.
     */
    @CalledByNative
    public int getViewportHeightPix() {
        return mViewportHeightPix;
    }

    /**
     * @return The amount that the viewport size given to Blink is shrunk by the URL-bar..
     */
    @CalledByNative
    public boolean doBrowserControlsShrinkBlinkSize() {
        return mTopControlsShrinkBlinkSize;
    }

    @CalledByNative
    public int getTopControlsHeightPix() {
        return mTopControlsHeightPix;
    }

    @CalledByNative
    public int getBottomControlsHeightPix() {
        return mBottomControlsHeightPix;
    }

    /**
     * @return Current device scale factor (maps DIP pixels to physical pixels).
     */
    @VisibleForTesting
    public float getDeviceScaleFactor() {
        return mRenderCoordinates.getDeviceScaleFactor();
    }

    /**
     * @return Current page scale factor (maps CSS pixels to DIP pixels).
     */
    @VisibleForTesting
    public float getPageScaleFactor() {
        return mRenderCoordinates.getPageScaleFactor();
    }

    /**
     * @see android.webkit.WebView#getContentHeight()
     */
    public float getContentHeightCss() {
        return mRenderCoordinates.getContentHeightCss();
    }

    /**
     * @see android.webkit.WebView#getContentWidth()
     */
    public float getContentWidthCss() {
        return mRenderCoordinates.getContentWidthCss();
    }

    /**
     * @return The selected text (empty if no text selected).
     */
    @VisibleForTesting
    public String getSelectedText() {
        return mSelectionPopupController.getSelectedText();
    }

    /**
     * @return Whether the current focused node is editable.
     */
    public boolean isFocusedNodeEditable() {
        return mSelectionPopupController.isSelectionEditable();
    }

    /**
     * @return Whether the HTML5 gamepad API is active.
     */
    public boolean isGamepadAPIActive() {
        return GamepadList.isGamepadAPIActive();
    }

    @CalledByNative
    private void requestDisallowInterceptTouchEvent() {
        mContainerView.requestDisallowInterceptTouchEvent(true);
    }

    /**
     * @return Whether a scroll targeting web content is in progress.
     */
    public boolean isScrollInProgress() {
        return mTouchScrollInProgress || mPotentiallyActiveFlingCount > 0;
    }

    private void setTouchScrollInProgress(boolean inProgress) {
        mTouchScrollInProgress = inProgress;
        mSelectionPopupController.setScrollInProgress(inProgress, isScrollInProgress());
    }

    @SuppressWarnings("unused")
    @CalledByNative
    private void onFlingStartEventConsumed() {
        mPotentiallyActiveFlingCount++;
        setTouchScrollInProgress(false);
        for (mGestureStateListenersIterator.rewind();
                    mGestureStateListenersIterator.hasNext();) {
            mGestureStateListenersIterator.next().onFlingStartGesture(
                    computeVerticalScrollOffset(), computeVerticalScrollExtent());
        }
    }

    @SuppressWarnings("unused")
    @CalledByNative
    private void onFlingCancelEventAck() {
        updateGestureStateListener(GestureEventType.FLING_CANCEL);
    }

    @SuppressWarnings("unused")
    @CalledByNative
    private void onScrollBeginEventAck() {
        setTouchScrollInProgress(true);
        updateGestureStateListener(GestureEventType.SCROLL_START);
    }

    @SuppressWarnings("unused")
    @CalledByNative
    private void onScrollUpdateGestureConsumed() {
        for (mGestureStateListenersIterator.rewind();
                mGestureStateListenersIterator.hasNext();) {
            mGestureStateListenersIterator.next().onScrollUpdateGestureConsumed();
        }
        destroyPastePopup();
    }

    @SuppressWarnings("unused")
    @CalledByNative
    private void onScrollEndEventAck() {
        setTouchScrollInProgress(false);
        updateGestureStateListener(GestureEventType.SCROLL_END);
    }

    @SuppressWarnings("unused")
    @CalledByNative
    private void onPinchBeginEventAck() {
        updateGestureStateListener(GestureEventType.PINCH_BEGIN);
    }

    @SuppressWarnings("unused")
    @CalledByNative
    private void onPinchEndEventAck() {
        updateGestureStateListener(GestureEventType.PINCH_END);
    }

    @SuppressWarnings("unused")
    @CalledByNative
    private void onSingleTapEventAck(boolean consumed) {
        for (mGestureStateListenersIterator.rewind();
                mGestureStateListenersIterator.hasNext();) {
            mGestureStateListenersIterator.next().onSingleTap(consumed);
        }
        destroyPastePopup();
    }

    @SuppressWarnings("unused")
    @CalledByNative
    private void onShowUnhandledTapUIIfNeeded(int x, int y) {
        mSelectionPopupController.onShowUnhandledTapUIIfNeeded(x, y);
    }

    @SuppressWarnings("unused")
    @CalledByNative
    private void onSelectWordAroundCaretAck(boolean didSelect, int startAdjust, int endAdjust) {
        mSelectionPopupController.onSelectWordAroundCaretAck(didSelect, startAdjust, endAdjust);
    }

    /**
     * Called just prior to a tap or press gesture being forwarded to the renderer.
     */
    @SuppressWarnings("unused")
    @CalledByNative
    private boolean filterTapOrPressEvent(int type, int x, int y) {
        if (type == GestureEventType.LONG_PRESS && offerLongPressToEmbedder()) {
            return true;
        }
        updateForTapOrPress(type, x, y);
        return false;
    }

    @VisibleForTesting
    public void sendDoubleTapForTest(long timeMs, int x, int y) {
        if (mNativeContentViewCore == 0) return;
        nativeDoubleTap(mNativeContentViewCore, timeMs, x, y);
    }

    /**
     * Flings the viewport with velocity vector (velocityX, velocityY).
     * @param timeMs the current time.
     * @param velocityX fling speed in x-axis.
     * @param velocityY fling speed in y-axis.
     * @param fromGamepad true if generated by gamepad (which will make this fixed-velocity fling)
     */
    public void flingViewport(long timeMs, float velocityX, float velocityY, boolean fromGamepad) {
        if (mNativeContentViewCore == 0) return;
        nativeFlingCancel(mNativeContentViewCore, timeMs, fromGamepad);
        if (velocityX == 0 && velocityY == 0) return;
        nativeScrollBegin(
                mNativeContentViewCore, timeMs, 0f, 0f, velocityX, velocityY, true, fromGamepad);
        nativeFlingStart(
                mNativeContentViewCore, timeMs, 0, 0, velocityX, velocityY, true, fromGamepad);
    }

    /**
     * Cancel any fling gestures active.
     * @param timeMs Current time (in milliseconds).
     */
    public void cancelFling(long timeMs) {
        if (mNativeContentViewCore == 0) return;
        nativeFlingCancel(mNativeContentViewCore, timeMs, false);
    }

    /**
     * Add a listener that gets alerted on gesture state changes.
     * @param listener Listener to add.
     */
    public void addGestureStateListener(GestureStateListener listener) {
        mGestureStateListeners.addObserver(listener);
    }

    /**
     * Removes a listener that was added to watch for gesture state changes.
     * @param listener Listener to remove.
     */
    public void removeGestureStateListener(GestureStateListener listener) {
        mGestureStateListeners.removeObserver(listener);
    }

    void updateGestureStateListener(int gestureType) {
        for (mGestureStateListenersIterator.rewind();
                mGestureStateListenersIterator.hasNext();) {
            GestureStateListener listener = mGestureStateListenersIterator.next();
            switch (gestureType) {
                case GestureEventType.PINCH_BEGIN:
                    listener.onPinchStarted();
                    break;
                case GestureEventType.PINCH_END:
                    listener.onPinchEnded();
                    break;
                case GestureEventType.FLING_END:
                    listener.onFlingEndGesture(
                            computeVerticalScrollOffset(),
                            computeVerticalScrollExtent());
                    break;
                case GestureEventType.SCROLL_START:
                    listener.onScrollStarted(
                            computeVerticalScrollOffset(),
                            computeVerticalScrollExtent());
                    break;
                case GestureEventType.SCROLL_END:
                    listener.onScrollEnded(
                            computeVerticalScrollOffset(),
                            computeVerticalScrollExtent());
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * To be called when the ContentView is shown.
     */
    public void onShow() {
        assert mWebContents != null;
        mWebContents.onShow();
        setAccessibilityState(mAccessibilityManager.isEnabled());
        restoreSelectionPopupsIfNecessary();
    }

    /**
     * @return The ID of the renderer process that backs this tab or
     *         {@link #INVALID_RENDER_PROCESS_PID} if there is none.
     */
    public int getCurrentRenderProcessId() {
        return nativeGetCurrentRenderProcessId(mNativeContentViewCore);
    }

    /**
     * To be called when the ContentView is hidden.
     */
    public void onHide() {
        assert mWebContents != null;
        hidePopupsAndPreserveSelection();
        mWebContents.onHide();
    }

    private void hidePopupsAndClearSelection() {
        mSelectionPopupController.destroyActionModeAndUnselect();
        destroyPastePopup();
        hideSelectPopupWithCancelMessage();
        mPopupZoomer.hide(false);
        if (mWebContents != null) mWebContents.dismissTextHandles();
    }

    @CalledByNative
    private void hidePopupsAndPreserveSelection() {
        mSelectionPopupController.destroyActionModeAndKeepSelection();
        destroyPastePopup();
        hideSelectPopupWithCancelMessage();
        mPopupZoomer.hide(false);
    }

    private void restoreSelectionPopupsIfNecessary() {
        mSelectionPopupController.restoreSelectionPopupsIfNecessary();
    }

    /**
     * Hide action mode and put into destroyed state.
     */
    public void destroySelectActionMode() {
        mSelectionPopupController.finishActionMode();
    }

    public boolean isSelectActionBarShowing() {
        return mSelectionPopupController.isActionModeValid();
    }

    private void resetGestureDetection() {
        if (mNativeContentViewCore == 0) return;
        nativeResetGestureDetection(mNativeContentViewCore);
    }

    /**
     * @see View#onAttachedToWindow()
     */
    @SuppressWarnings("javadoc")
    public void onAttachedToWindow() {
        mAttachedToWindow = true;
        addDisplayAndroidObserverIfNeeded();
        setAccessibilityState(mAccessibilityManager.isEnabled());
        updateTextSelectionUI(true);
        GamepadList.onAttachedToWindow(mContext);
        mAccessibilityManager.addAccessibilityStateChangeListener(this);
        mSystemCaptioningBridge.addListener(this);
        mImeAdapter.onViewAttachedToWindow();
    }

    /**
     * Update the text selection UI depending on the focus of the page. This will hide the selection
     * handles and selection popups if focus is lost.
     * TODO(mdjones): This was added as a temporary measure to hide text UI while Reader Mode or
     * Contextual Search are showing. This should be removed in favor of proper focusing of the
     * panel's ContentViewCore (which is currently not being added to the view hierarchy).
     * @param focused If the ContentViewCore currently has focus.
     */
    public void updateTextSelectionUI(boolean focused) {
        setTextHandlesTemporarilyHidden(!focused);
        if (focused) {
            restoreSelectionPopupsIfNecessary();
        } else {
            hidePopupsAndPreserveSelection();
        }
    }

    /**
     * @see View#onDetachedFromWindow()
     */
    @SuppressWarnings("javadoc")
    @SuppressLint("MissingSuperCall")
    public void onDetachedFromWindow() {
        mAttachedToWindow = false;
        mImeAdapter.onViewDetachedFromWindow();
        removeDisplayAndroidObserver();
        GamepadList.onDetachedFromWindow();
        mAccessibilityManager.removeAccessibilityStateChangeListener(this);

        // WebView uses PopupWindows for handle rendering, which may remain
        // unintentionally visible even after the WebView has been detached.
        // Override the handle visibility explicitly to address this, but
        // preserve the underlying selection for detachment cases like screen
        // locking and app switching.
        updateTextSelectionUI(false);
        mSystemCaptioningBridge.removeListener(this);
    }

    /**
     * @see View#onCreateInputConnection(EditorInfo)
     */
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        boolean allowKeyboardLearning = getWebContents() != null && !getWebContents().isIncognito();
        return mImeAdapter.onCreateInputConnection(outAttrs, allowKeyboardLearning);
    }

    /**
     * @see View#onCheckIsTextEditor()
     */
    public boolean onCheckIsTextEditor() {
        return mImeAdapter.hasTextInputType();
    }

    /**
     * @see View#onConfigurationChanged(Configuration)
     */
    @SuppressWarnings("javadoc")
    public void onConfigurationChanged(Configuration newConfig) {
        try {
            TraceEvent.begin("ContentViewCore.onConfigurationChanged");
            mImeAdapter.onKeyboardConfigurationChanged(newConfig);
            mContainerViewInternals.super_onConfigurationChanged(newConfig);
            // To request layout has side effect, but it seems OK as it only happen in
            // onConfigurationChange and layout has to be changed in most case.
            mContainerView.requestLayout();
        } finally {
            TraceEvent.end("ContentViewCore.onConfigurationChanged");
        }
    }

    /**
     * @see View#onSizeChanged(int, int, int, int)
     */
    @SuppressWarnings("javadoc")
    public void onSizeChanged(int wPix, int hPix, int owPix, int ohPix) {
        if (getViewportWidthPix() == wPix && getViewportHeightPix() == hPix) return;

        mViewportWidthPix = wPix;
        mViewportHeightPix = hPix;
        if (mNativeContentViewCore != 0) {
            nativeWasResized(mNativeContentViewCore);
        }

        updateAfterSizeChanged();
    }

    @CalledByNative
    private void onTouchDown(MotionEvent event) {
        if (mShouldRequestUnbufferedDispatch) requestUnbufferedDispatch(event);
        cancelRequestToScrollFocusedEditableNodeIntoView();
    }

    private void updateAfterSizeChanged() {
        mPopupZoomer.hide(false);

        // Execute a delayed form focus operation because the OSK was brought
        // up earlier.
        Rect focusPreOSKViewportRect = mImeAdapter.getFocusPreOSKViewportRect();
        if (!focusPreOSKViewportRect.isEmpty()) {
            Rect rect = new Rect();
            getContainerView().getWindowVisibleDisplayFrame(rect);
            if (!rect.equals(focusPreOSKViewportRect)) {
                // Only assume the OSK triggered the onSizeChanged if width was preserved.
                if (rect.width() == focusPreOSKViewportRect.width()) {
                    assert mWebContents != null;
                    mWebContents.scrollFocusedEditableNodeIntoView();
                }
                cancelRequestToScrollFocusedEditableNodeIntoView();
            }
        }
    }

    private void cancelRequestToScrollFocusedEditableNodeIntoView() {
        // Zero-ing the rect will prevent |updateAfterSizeChanged()| from
        // issuing the delayed form focus event.
        mImeAdapter.getFocusPreOSKViewportRect().setEmpty();
    }

    /**
     * When the activity pauses, the content should lose focus.
     * TODO(mthiesse): See crbug.com/686232 for context. Desktop platforms use keyboard focus to
     * trigger blur/focus, and the equivalent to this on Android is Window focus. However, we don't
     * use Window focus because of the complexity around popups stealing Window focus.
     */
    public void onPause() {
        onFocusChanged(false, true);
    }

    /**
     * When the activity resumes, the View#onFocusChanged may not be called, so we should restore
     * the View focus state.
     */
    public void onResume() {
        onFocusChanged(getContainerView().hasFocus(), true);
    }

    /**
     * @see View#onWindowFocusChanged(boolean)
     */
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        mImeAdapter.onWindowFocusChanged(hasWindowFocus);
        if (!hasWindowFocus) resetGestureDetection();
        mSelectionPopupController.onWindowFocusChanged(hasWindowFocus);
        for (mGestureStateListenersIterator.rewind(); mGestureStateListenersIterator.hasNext();) {
            mGestureStateListenersIterator.next().onWindowFocusChanged(hasWindowFocus);
        }
    }

    public void onFocusChanged(boolean gainFocus, boolean hideKeyboardOnBlur) {
        if (mHasViewFocus != null && mHasViewFocus == gainFocus) return;
        mHasViewFocus = gainFocus;
        mImeAdapter.onViewFocusChanged(gainFocus, hideKeyboardOnBlur);

        mJoystickScrollEnabled = gainFocus && !isFocusedNodeEditable();

        if (gainFocus) {
            restoreSelectionPopupsIfNecessary();
        } else {
            cancelRequestToScrollFocusedEditableNodeIntoView();
            if (mPreserveSelectionOnNextLossOfFocus) {
                mPreserveSelectionOnNextLossOfFocus = false;
                hidePopupsAndPreserveSelection();
            } else {
                hidePopupsAndClearSelection();
                // Clear the selection. The selection is cleared on destroying IME
                // and also here since we may receive destroy first, for example
                // when focus is lost in webview.
                clearSelection();
            }
        }
        if (mNativeContentViewCore != 0) nativeSetFocus(mNativeContentViewCore, gainFocus);
    }

    /**
     * @see View#onKeyUp(int, KeyEvent)
     */
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (mPopupZoomer.isShowing() && keyCode == KeyEvent.KEYCODE_BACK) {
            mPopupZoomer.backButtonPressed();
            return true;
        }
        return mContainerViewInternals.super_onKeyUp(keyCode, event);
    }

    /**
     * @see View#dispatchKeyEvent(KeyEvent)
     */
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (GamepadList.dispatchKeyEvent(event)) return true;
        if (!shouldPropagateKeyEvent(event)) {
            return mContainerViewInternals.super_dispatchKeyEvent(event);
        }

        if (mImeAdapter.dispatchKeyEvent(event)) return true;

        return mContainerViewInternals.super_dispatchKeyEvent(event);
    }

    /**
     * Check whether a key should be propagated to the embedder or not.
     * We need to send almost every key to Blink. However:
     * 1. We don't want to block the device on the renderer for
     * some keys like menu, home, call.
     * 2. There are no WebKit equivalents for some of these keys
     * (see app/keyboard_codes_win.h)
     * Note that these are not the same set as KeyEvent.isSystemKey:
     * for instance, AKEYCODE_MEDIA_* will be dispatched to webkit*.
     */
    private static boolean shouldPropagateKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_HOME
                || keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_CALL
                || keyCode == KeyEvent.KEYCODE_ENDCALL || keyCode == KeyEvent.KEYCODE_POWER
                || keyCode == KeyEvent.KEYCODE_HEADSETHOOK || keyCode == KeyEvent.KEYCODE_CAMERA
                || keyCode == KeyEvent.KEYCODE_FOCUS || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE
                || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            return false;
        }
        return true;
    }

    /**
     * @see View#onHoverEvent(MotionEvent)
     * Mouse move events are sent on hover move.
     */
    public boolean onHoverEvent(MotionEvent event) {
        TraceEvent.begin("onHoverEvent");

        MotionEvent offset = createOffsetMotionEvent(event);
        try {
            if (mBrowserAccessibilityManager != null && !mIsObscuredByAnotherView
                    && mBrowserAccessibilityManager.onHoverEvent(offset)) {
                return true;
            }

            return getEventForwarder().onMouseEvent(event);
        } finally {
            offset.recycle();
            TraceEvent.end("onHoverEvent");
        }
    }

    /**
     * Removes noise from joystick motion events.
     */
    private static float getFilteredAxisValue(MotionEvent event, int axis) {
        final float kJoystickScrollDeadzone = 0.2f;
        float axisValWithNoise = event.getAxisValue(axis);
        if (Math.abs(axisValWithNoise) > kJoystickScrollDeadzone) return axisValWithNoise;
        return 0f;
    }

    /**
     * @see View#onGenericMotionEvent(MotionEvent)
     */
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (GamepadList.onGenericMotionEvent(event)) return true;
        if ((event.getSource() & InputDevice.SOURCE_CLASS_POINTER) != 0) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_SCROLL:
                    getEventForwarder().onMouseWheelEvent(event.getEventTime(), event.getX(),
                            event.getY(), event.getAxisValue(MotionEvent.AXIS_HSCROLL),
                            event.getAxisValue(MotionEvent.AXIS_VSCROLL),
                            mRenderCoordinates.getWheelScrollFactor());
                    return true;
                case MotionEvent.ACTION_BUTTON_PRESS:
                case MotionEvent.ACTION_BUTTON_RELEASE:
                    // TODO(mustaq): Should we include MotionEvent.TOOL_TYPE_STYLUS here?
                    // crbug.com/592082
                    if (event.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE) {
                        return getEventForwarder().onMouseEvent(event);
                    }
            }
        } else if ((event.getSource() & InputDevice.SOURCE_CLASS_JOYSTICK) != 0) {
            if (mJoystickScrollEnabled) {
                float velocityX = getFilteredAxisValue(event, MotionEvent.AXIS_X);
                float velocityY = getFilteredAxisValue(event, MotionEvent.AXIS_Y);
                flingViewport(event.getEventTime(), -velocityX, -velocityY, true);
                return true;
            }
        }
        return mContainerViewInternals.super_onGenericMotionEvent(event);
    }

    /**
     * Sets the current amount to offset incoming touch events by (including MotionEvent and
     * DragEvent). This is used to handle content moving and not lining up properly with the
     * android input system.
     * @param dx The X offset in pixels to shift touch events.
     * @param dy The Y offset in pixels to shift touch events.
     */
    public void setCurrentTouchEventOffsets(float dx, float dy) {
        mCurrentTouchOffsetX = dx;
        mCurrentTouchOffsetY = dy;
        getEventForwarder().setCurrentTouchEventOffsets(dx, dy);
    }

    private MotionEvent createOffsetMotionEvent(MotionEvent src) {
        MotionEvent dst = MotionEvent.obtain(src);
        dst.offsetLocation(mCurrentTouchOffsetX, mCurrentTouchOffsetY);
        return dst;
    }

    /**
     * @see View#scrollBy(int, int)
     * Currently the ContentView scrolling happens in the native side. In
     * the Java view system, it is always pinned at (0, 0). scrollBy() and scrollTo()
     * are overridden, so that View's mScrollX and mScrollY will be unchanged at
     * (0, 0). This is critical for drawing ContentView correctly.
     */
    public void scrollBy(float dxPix, float dyPix) {
        if (mNativeContentViewCore == 0) return;
        if (dxPix == 0 && dyPix == 0) return;
        long time = SystemClock.uptimeMillis();
        // It's a very real (and valid) possibility that a fling may still
        // be active when programatically scrolling. Cancelling the fling in
        // such cases ensures a consistent gesture event stream.
        if (mPotentiallyActiveFlingCount > 0) {
            nativeFlingCancel(mNativeContentViewCore, time, false);
        }
        // x/y represents starting location of scroll.
        nativeScrollBegin(mNativeContentViewCore, time, 0f, 0f, -dxPix, -dyPix, true, false);
        nativeScrollBy(mNativeContentViewCore, time, 0f, 0f, dxPix, dyPix);
        nativeScrollEnd(mNativeContentViewCore, time);
    }

    /**
     * @see View#scrollTo(int, int)
     */
    public void scrollTo(float xPix, float yPix) {
        if (mNativeContentViewCore == 0) return;
        final float xCurrentPix = mRenderCoordinates.getScrollXPix();
        final float yCurrentPix = mRenderCoordinates.getScrollYPix();
        final float dxPix = xPix - xCurrentPix;
        final float dyPix = yPix - yCurrentPix;
        scrollBy(dxPix, dyPix);
    }

    // NOTE: this can go away once ContentView.getScrollX() reports correct values.
    //       see: b/6029133
    public int getNativeScrollXForTest() {
        return mRenderCoordinates.getScrollXPixInt();
    }

    // NOTE: this can go away once ContentView.getScrollY() reports correct values.
    //       see: b/6029133
    public int getNativeScrollYForTest() {
        return mRenderCoordinates.getScrollYPixInt();
    }

    /**
     * @see View#computeHorizontalScrollExtent()
     */
    @SuppressWarnings("javadoc")
    public int computeHorizontalScrollExtent() {
        return mRenderCoordinates.getLastFrameViewportWidthPixInt();
    }

    /**
     * @see View#computeHorizontalScrollOffset()
     */
    @SuppressWarnings("javadoc")
    public int computeHorizontalScrollOffset() {
        return mRenderCoordinates.getScrollXPixInt();
    }

    /**
     * @see View#computeHorizontalScrollRange()
     */
    @SuppressWarnings("javadoc")
    public int computeHorizontalScrollRange() {
        return mRenderCoordinates.getContentWidthPixInt();
    }

    /**
     * @see View#computeVerticalScrollExtent()
     */
    @SuppressWarnings("javadoc")
    public int computeVerticalScrollExtent() {
        return mRenderCoordinates.getLastFrameViewportHeightPixInt();
    }

    /**
     * @see View#computeVerticalScrollOffset()
     */
    @SuppressWarnings("javadoc")
    public int computeVerticalScrollOffset() {
        return mRenderCoordinates.getScrollYPixInt();
    }

    /**
     * @see View#computeVerticalScrollRange()
     */
    @SuppressWarnings("javadoc")
    public int computeVerticalScrollRange() {
        return mRenderCoordinates.getContentHeightPixInt();
    }

    // End FrameLayout overrides.

    /**
     * @see View#awakenScrollBars(int, boolean)
     */
    @SuppressWarnings("javadoc")
    public boolean awakenScrollBars(int startDelay, boolean invalidate) {
        // For the default implementation of ContentView which draws the scrollBars on the native
        // side, calling this function may get us into a bad state where we keep drawing the
        // scrollBars, so disable it by always returning false.
        if (mContainerView.getScrollBarStyle() == View.SCROLLBARS_INSIDE_OVERLAY) {
            return false;
        } else {
            return mContainerViewInternals.super_awakenScrollBars(startDelay, invalidate);
        }
    }

    private void updateForTapOrPress(int type, float xPix, float yPix) {
        if (type != GestureEventType.SINGLE_TAP_CONFIRMED
                && type != GestureEventType.SINGLE_TAP_UP
                && type != GestureEventType.LONG_PRESS
                && type != GestureEventType.LONG_TAP) {
            return;
        }

        if (mContainerView.isFocusable() && mContainerView.isFocusableInTouchMode()
                && !mContainerView.isFocused())  {
            mContainerView.requestFocus();
        }

        if (!mPopupZoomer.isShowing()) mPopupZoomer.setLastTouch(xPix, yPix);
    }

    public void updateMultiTouchZoomSupport(boolean supportsMultiTouchZoom) {
        if (mNativeContentViewCore == 0) return;
        nativeSetMultiTouchZoomSupportEnabled(mNativeContentViewCore, supportsMultiTouchZoom);
    }

    public void updateDoubleTapSupport(boolean supportsDoubleTap) {
        if (mNativeContentViewCore == 0) return;
        nativeSetDoubleTapSupportEnabled(mNativeContentViewCore, supportsDoubleTap);
    }

    public void selectPopupMenuItems(int[] indices) {
        if (mNativeContentViewCore != 0) {
            nativeSelectPopupMenuItems(mNativeContentViewCore, mNativeSelectPopupSourceFrame,
                                       indices);
        }
        mNativeSelectPopupSourceFrame = 0;
        mSelectPopup = null;
    }

    /**
     * Send the screen orientation value to the renderer.
     */
    @VisibleForTesting
    void sendOrientationChangeEvent(int orientation) {
        if (mNativeContentViewCore == 0) return;

        nativeSendOrientationChangeEvent(mNativeContentViewCore, orientation);
    }

    public ActionModeCallbackHelper getActionModeCallbackHelper() {
        return mSelectionPopupController;
    }

    private void showSelectActionMode() {
        mSelectionPopupController.showActionModeOrClearOnFailure();
    }

    public void clearSelection() {
        mSelectionPopupController.clearSelection();
    }

    /**
     * Ensure the selection is preserved the next time the view loses focus.
     */
    public void preserveSelectionOnNextLossOfFocus() {
        mPreserveSelectionOnNextLossOfFocus = true;
    }

    private void setTextHandlesTemporarilyHidden(boolean hide) {
        if (mNativeContentViewCore == 0) return;
        nativeSetTextHandlesTemporarilyHidden(mNativeContentViewCore, hide);
    }

    @SuppressWarnings("unused")
    @CalledByNative
    private void updateFrameInfo(float scrollOffsetX, float scrollOffsetY, float pageScaleFactor,
            float minPageScaleFactor, float maxPageScaleFactor, float contentWidth,
            float contentHeight, float viewportWidth, float viewportHeight, float topBarShownPix,
            boolean topBarChanged, boolean isMobileOptimizedHint) {
        TraceEvent.begin("ContentViewCore:updateFrameInfo");
        mIsMobileOptimizedHint = isMobileOptimizedHint;
        // Adjust contentWidth/Height to be always at least as big as
        // the actual viewport (as set by onSizeChanged).
        final float deviceScale = mRenderCoordinates.getDeviceScaleFactor();
        contentWidth = Math.max(contentWidth,
                mViewportWidthPix / (deviceScale * pageScaleFactor));
        contentHeight = Math.max(contentHeight,
                mViewportHeightPix / (deviceScale * pageScaleFactor));

        final boolean contentSizeChanged =
                contentWidth != mRenderCoordinates.getContentWidthCss()
                || contentHeight != mRenderCoordinates.getContentHeightCss();
        final boolean scaleLimitsChanged =
                minPageScaleFactor != mRenderCoordinates.getMinPageScaleFactor()
                || maxPageScaleFactor != mRenderCoordinates.getMaxPageScaleFactor();
        final boolean pageScaleChanged =
                pageScaleFactor != mRenderCoordinates.getPageScaleFactor();
        final boolean scrollChanged =
                pageScaleChanged
                || scrollOffsetX != mRenderCoordinates.getScrollX()
                || scrollOffsetY != mRenderCoordinates.getScrollY();

        final boolean needHidePopupZoomer = contentSizeChanged || scrollChanged;

        if (needHidePopupZoomer) mPopupZoomer.hide(true);

        if (scrollChanged) {
            mContainerViewInternals.onScrollChanged(
                    (int) mRenderCoordinates.fromLocalCssToPix(scrollOffsetX),
                    (int) mRenderCoordinates.fromLocalCssToPix(scrollOffsetY),
                    (int) mRenderCoordinates.getScrollXPix(),
                    (int) mRenderCoordinates.getScrollYPix());
        }

        mRenderCoordinates.updateFrameInfo(scrollOffsetX, scrollOffsetY, contentWidth,
                contentHeight, viewportWidth, viewportHeight, pageScaleFactor, minPageScaleFactor,
                maxPageScaleFactor, topBarShownPix);

        if (scrollChanged || topBarChanged) {
            for (mGestureStateListenersIterator.rewind();
                    mGestureStateListenersIterator.hasNext();) {
                mGestureStateListenersIterator.next().onScrollOffsetOrExtentChanged(
                        computeVerticalScrollOffset(),
                        computeVerticalScrollExtent());
            }
        }

        if (scaleLimitsChanged) {
            for (mGestureStateListenersIterator.rewind();
                    mGestureStateListenersIterator.hasNext();) {
                mGestureStateListenersIterator.next().onScaleLimitsChanged(
                        minPageScaleFactor, maxPageScaleFactor);
            }
        }

        if (mBrowserAccessibilityManager != null) {
            mBrowserAccessibilityManager.notifyFrameInfoInitialized();
        }

        TraceEvent.end("ContentViewCore:updateFrameInfo");
    }

    // ImeEventObserver

    @Override
    public void onImeEvent() {
        mPopupZoomer.hide(true);
    }

    @Override
    public void onNodeAttributeUpdated(boolean editable, boolean password) {
        mJoystickScrollEnabled = !editable;
        mSelectionPopupController.updateSelectionState(editable, password);
    }

    /**
     * Called (from native) when the <select> popup needs to be shown.
     * @param anchorView View anchored for popup.
     * @param nativeSelectPopupSourceFrame The native RenderFrameHost that owns the popup.
     * @param items           Items to show.
     * @param enabled         POPUP_ITEM_TYPEs for items.
     * @param multiple        Whether the popup menu should support multi-select.
     * @param selectedIndices Indices of selected items.
     */
    @SuppressWarnings("unused")
    @CalledByNative
    private void showSelectPopup(View anchorView, long nativeSelectPopupSourceFrame, String[] items,
            int[] enabled, boolean multiple, int[] selectedIndices, boolean rightAligned) {
        if (mContainerView.getParent() == null || mContainerView.getVisibility() != View.VISIBLE) {
            mNativeSelectPopupSourceFrame = nativeSelectPopupSourceFrame;
            selectPopupMenuItems(null);
            return;
        }

        hidePopupsAndClearSelection();
        assert mNativeSelectPopupSourceFrame == 0 : "Zombie popup did not clear the frame source";

        assert items.length == enabled.length;
        List<SelectPopupItem> popupItems = new ArrayList<SelectPopupItem>();
        for (int i = 0; i < items.length; i++) {
            popupItems.add(new SelectPopupItem(items[i], enabled[i]));
        }
        if (DeviceFormFactor.isTablet() && !multiple && !isTouchExplorationEnabled()) {
            mSelectPopup = new SelectPopupDropdown(
                    this, anchorView, popupItems, selectedIndices, rightAligned);
        } else {
            if (getWindowAndroid() == null) return;
            Context windowContext = getWindowAndroid().getContext().get();
            if (windowContext == null) return;
            mSelectPopup = new SelectPopupDialog(
                    this, windowContext, popupItems, multiple, selectedIndices);
        }
        mNativeSelectPopupSourceFrame = nativeSelectPopupSourceFrame;
        mSelectPopup.show();
    }

    /**
     * Called when the <select> popup needs to be hidden.
     */
    @CalledByNative
    private void hideSelectPopup() {
        if (mSelectPopup == null) return;
        mSelectPopup.hide(false);
        mSelectPopup = null;
        mNativeSelectPopupSourceFrame = 0;
    }

    /**
     * Called when the <select> popup needs to be hidden. This calls
     * nativeSelectPopupMenuItems() with null indices.
     */
    private void hideSelectPopupWithCancelMessage() {
        if (mSelectPopup != null) mSelectPopup.hide(true);
    }

    /**
     * @return The visible select popup being shown.
     */
    @VisibleForTesting
    public SelectPopup getSelectPopupForTest() {
        return mSelectPopup;
    }

    @SuppressWarnings("unused")
    @CalledByNative
    private void showDisambiguationPopup(Rect targetRect, Bitmap zoomedBitmap) {
        mPopupZoomer.setBitmap(zoomedBitmap);
        mPopupZoomer.show(targetRect);
    }

    @SuppressWarnings("unused")
    @CalledByNative
    private MotionEventSynthesizer createMotionEventSynthesizer() {
        return new MotionEventSynthesizer(getContainerView(), this);
    }

    @SuppressWarnings("unused")
    @CalledByNative
    private void performLongPressHapticFeedback() {
        mContainerView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
    }

    @CalledByNative
    private void showPastePopup(
            int left, int top, int right, int bottom, boolean canSelectAll, boolean canEditRichly) {
        mSelectionPopupController.createAndShowPastePopup(
                left, top, right, bottom, canSelectAll, canEditRichly);
    }

    private void destroyPastePopup() {
        mSelectionPopupController.destroyPastePopup();
    }

    @SuppressWarnings("unused")
    @CalledByNative
    private void onRenderProcessChange() {
        // Immediately sync closed caption settings to the new render process.
        mSystemCaptioningBridge.syncToListener(this);
    }

    /**
     * @see View#hasFocus()
     */
    @CalledByNative
    private boolean hasFocus() {
        return ViewUtils.hasFocus(mContainerView);
    }

    /**
     * Checks whether the ContentViewCore can be zoomed in.
     *
     * @return True if the ContentViewCore can be zoomed in.
     */
    // This method uses the term 'zoom' for legacy reasons, but relates
    // to what chrome calls the 'page scale factor'.
    public boolean canZoomIn() {
        final float zoomInExtent = mRenderCoordinates.getMaxPageScaleFactor()
                - mRenderCoordinates.getPageScaleFactor();
        return zoomInExtent > ZOOM_CONTROLS_EPSILON;
    }

    /**
     * Checks whether the ContentViewCore can be zoomed out.
     *
     * @return True if the ContentViewCore can be zoomed out.
     */
    // This method uses the term 'zoom' for legacy reasons, but relates
    // to what chrome calls the 'page scale factor'.
    public boolean canZoomOut() {
        final float zoomOutExtent = mRenderCoordinates.getPageScaleFactor()
                - mRenderCoordinates.getMinPageScaleFactor();
        return zoomOutExtent > ZOOM_CONTROLS_EPSILON;
    }

    /**
     * Zooms in the ContentViewCore by 25% (or less if that would result in
     * zooming in more than possible).
     *
     * @return True if there was a zoom change, false otherwise.
     */
    // This method uses the term 'zoom' for legacy reasons, but relates
    // to what chrome calls the 'page scale factor'.
    public boolean zoomIn() {
        if (!canZoomIn()) {
            return false;
        }
        return pinchByDelta(1.25f);
    }

    /**
     * Zooms out the ContentViewCore by 20% (or less if that would result in
     * zooming out more than possible).
     *
     * @return True if there was a zoom change, false otherwise.
     */
    // This method uses the term 'zoom' for legacy reasons, but relates
    // to what chrome calls the 'page scale factor'.
    public boolean zoomOut() {
        if (!canZoomOut()) {
            return false;
        }
        return pinchByDelta(0.8f);
    }

    /**
     * Resets the zoom factor of the ContentViewCore.
     *
     * @return True if there was a zoom change, false otherwise.
     */
    // This method uses the term 'zoom' for legacy reasons, but relates
    // to what chrome calls the 'page scale factor'.
    public boolean zoomReset() {
        // The page scale factor is initialized to mNativeMinimumScale when
        // the page finishes loading. Thus sets it back to mNativeMinimumScale.
        if (!canZoomOut()) return false;
        return pinchByDelta(
                mRenderCoordinates.getMinPageScaleFactor()
                        / mRenderCoordinates.getPageScaleFactor());
    }

    /**
     * Simulate a pinch zoom gesture.
     *
     * @param delta the factor by which the current page scale should be multiplied by.
     * @return whether the gesture was sent.
     */
    private boolean pinchByDelta(float delta) {
        if (mNativeContentViewCore == 0) return false;

        long timeMs = SystemClock.uptimeMillis();
        int xPix = getViewportWidthPix() / 2;
        int yPix = getViewportHeightPix() / 2;

        nativePinchBegin(mNativeContentViewCore, timeMs, xPix, yPix);
        nativePinchBy(mNativeContentViewCore, timeMs, xPix, yPix, delta);
        nativePinchEnd(mNativeContentViewCore, timeMs);

        return true;
    }

    /**
     * Enables or disables inspection of JavaScript objects added via
     * {@link #addJavascriptInterface(Object, String)} by means of Object.keys() method and
     * &quot;for .. in&quot; loop. Being able to inspect JavaScript objects is useful
     * when debugging hybrid Android apps, but can't be enabled for legacy applications due
     * to compatibility risks.
     *
     * @param allow Whether to allow JavaScript objects inspection.
     */
    public void setAllowJavascriptInterfacesInspection(boolean allow) {
        nativeSetAllowJavascriptInterfacesInspection(mNativeContentViewCore, allow);
    }

    /**
     * Returns JavaScript interface objects previously injected via
     * {@link #addJavascriptInterface(Object, String)}.
     *
     * @return the mapping of names to interface objects and corresponding annotation classes
     */
    public Map<String, Pair<Object, Class>> getJavascriptInterfaces() {
        return mJavaScriptInterfaces;
    }

    /**
     * This will mimic {@link #addPossiblyUnsafeJavascriptInterface(Object, String, Class)}
     * and automatically pass in {@link JavascriptInterface} as the required annotation.
     *
     * @param object The Java object to inject into the ContentViewCore's JavaScript context.  Null
     *               values are ignored.
     * @param name   The name used to expose the instance in JavaScript.
     */
    public void addJavascriptInterface(Object object, String name) {
        addPossiblyUnsafeJavascriptInterface(object, name, JavascriptInterface.class);
    }

    /**
     * This method injects the supplied Java object into the ContentViewCore.
     * The object is injected into the JavaScript context of the main frame,
     * using the supplied name. This allows the Java object to be accessed from
     * JavaScript. Note that that injected objects will not appear in
     * JavaScript until the page is next (re)loaded. For example:
     * <pre> view.addJavascriptInterface(new Object(), "injectedObject");
     * view.loadData("<!DOCTYPE html><title></title>", "text/html", null);
     * view.loadUrl("javascript:alert(injectedObject.toString())");</pre>
     * <p><strong>IMPORTANT:</strong>
     * <ul>
     * <li> addJavascriptInterface() can be used to allow JavaScript to control
     * the host application. This is a powerful feature, but also presents a
     * security risk. Use of this method in a ContentViewCore containing
     * untrusted content could allow an attacker to manipulate the host
     * application in unintended ways, executing Java code with the permissions
     * of the host application. Use extreme care when using this method in a
     * ContentViewCore which could contain untrusted content. Particular care
     * should be taken to avoid unintentional access to inherited methods, such
     * as {@link Object#getClass()}. To prevent access to inherited methods,
     * pass an annotation for {@code requiredAnnotation}.  This will ensure
     * that only methods with {@code requiredAnnotation} are exposed to the
     * Javascript layer.  {@code requiredAnnotation} will be passed to all
     * subsequently injected Java objects if any methods return an object.  This
     * means the same restrictions (or lack thereof) will apply.  Alternatively,
     * {@link #addJavascriptInterface(Object, String)} can be called, which
     * automatically uses the {@link JavascriptInterface} annotation.
     * <li> JavaScript interacts with Java objects on a private, background
     * thread of the ContentViewCore. Care is therefore required to maintain
     * thread safety.</li>
     * </ul></p>
     *
     * @param object             The Java object to inject into the
     *                           ContentViewCore's JavaScript context. Null
     *                           values are ignored.
     * @param name               The name used to expose the instance in
     *                           JavaScript.
     * @param requiredAnnotation Restrict exposed methods to ones with this
     *                           annotation.  If {@code null} all methods are
     *                           exposed.
     *
     */
    public void addPossiblyUnsafeJavascriptInterface(Object object, String name,
            Class<? extends Annotation> requiredAnnotation) {
        if (mNativeContentViewCore != 0 && object != null) {
            mJavaScriptInterfaces.put(name, new Pair<Object, Class>(object, requiredAnnotation));
            nativeAddJavascriptInterface(mNativeContentViewCore, object, name, requiredAnnotation);
        }
    }

    /**
     * Removes a previously added JavaScript interface with the given name.
     *
     * @param name The name of the interface to remove.
     */
    public void removeJavascriptInterface(String name) {
        mJavaScriptInterfaces.remove(name);
        if (mNativeContentViewCore != 0) {
            nativeRemoveJavascriptInterface(mNativeContentViewCore, name);
        }
    }

    /**
     * Return the current scale of the ContentView.
     * @return The current page scale factor.
     */
    @VisibleForTesting
    public float getScale() {
        return mRenderCoordinates.getPageScaleFactor();
    }

    @Override
    public void onAccessibilityStateChanged(boolean enabled) {
        setAccessibilityState(enabled);
    }

    /**
     * Determines whether or not this ContentViewCore can handle this accessibility action.
     * @param action The action to perform.
     * @return Whether or not this action is supported.
     */
    public boolean supportsAccessibilityAction(int action) {
        // TODO(dmazzoni): implement this in BrowserAccessibilityManager.
        return false;
    }

    /**
     * Attempts to perform an accessibility action on the web content.  If the accessibility action
     * cannot be processed, it returns {@code null}, allowing the caller to know to call the
     * super {@link View#performAccessibilityAction(int, Bundle)} method and use that return value.
     * Otherwise the return value from this method should be used.
     * @param action The action to perform.
     * @param arguments Optional action arguments.
     * @return Whether the action was performed or {@code null} if the call should be delegated to
     *         the super {@link View} class.
     */
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        // TODO(dmazzoni): implement this in BrowserAccessibilityManager.
        return false;
    }

    /**
     * Set the BrowserAccessibilityManager, used for native accessibility
     * (not script injection). This is only set when system accessibility
     * has been enabled.
     * @param manager The new BrowserAccessibilityManager.
     */
    public void setBrowserAccessibilityManager(BrowserAccessibilityManager manager) {
        mBrowserAccessibilityManager = manager;

        if (mBrowserAccessibilityManager != null && mRenderCoordinates.hasFrameInfo()) {
            mBrowserAccessibilityManager.notifyFrameInfoInitialized();
        }

        if (mBrowserAccessibilityManager == null) mNativeAccessibilityEnabled = false;
    }

    /**
     * Get the BrowserAccessibilityManager, used for native accessibility
     * (not script injection). This will return null when system accessibility
     * is not enabled.
     * @return This view's BrowserAccessibilityManager.
     */
    public BrowserAccessibilityManager getBrowserAccessibilityManager() {
        return mBrowserAccessibilityManager;
    }

    /**
     * If native accessibility is enabled and no other views are temporarily
     * obscuring this one, returns an AccessibilityNodeProvider that
     * implements native accessibility for this view. Returns null otherwise.
     * Lazily initializes native accessibility here if it's allowed.
     * @return The AccessibilityNodeProvider, if available, or null otherwise.
     */
    public AccessibilityNodeProvider getAccessibilityNodeProvider() {
        if (mIsObscuredByAnotherView) return null;

        if (mBrowserAccessibilityManager != null) {
            return mBrowserAccessibilityManager.getAccessibilityNodeProvider();
        }

        if (mNativeAccessibilityAllowed && !mNativeAccessibilityEnabled
                && mNativeContentViewCore != 0) {
            mNativeAccessibilityEnabled = true;
            nativeSetAccessibilityEnabled(mNativeContentViewCore, true);
        }

        return null;
    }

    /**
     * Set whether or not the web contents are obscured by another view.
     * If true, we won't return an accessibility node provider or respond
     * to touch exploration events.
     */
    public void setObscuredByAnotherView(boolean isObscured) {
        if (isObscured != mIsObscuredByAnotherView) {
            mIsObscuredByAnotherView = isObscured;
            getContainerView().sendAccessibilityEvent(
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    public void onProvideVirtualStructure(
            final ViewStructure structure, final boolean ignoreScrollOffset) {
        // Do not collect accessibility tree in incognito mode
        if (getWebContents().isIncognito()) {
            structure.setChildCount(0);
            return;
        }
        structure.setChildCount(1);
        final ViewStructure viewRoot = structure.asyncNewChild(0);
        getWebContents().requestAccessibilitySnapshot(new AccessibilitySnapshotCallback() {
            @Override
            public void onAccessibilitySnapshot(AccessibilitySnapshotNode root) {
                viewRoot.setClassName("");
                viewRoot.setHint(mProductVersion);
                if (root == null) {
                    viewRoot.asyncCommit();
                    return;
                }
                createVirtualStructure(viewRoot, root, ignoreScrollOffset);
            }
        });
    }

    // When creating the View structure, the left and top are relative to the parent node.
    @TargetApi(Build.VERSION_CODES.M)
    private void createVirtualStructure(ViewStructure viewNode, AccessibilitySnapshotNode node,
            final boolean ignoreScrollOffset) {
        viewNode.setClassName(node.className);
        if (node.hasSelection) {
            viewNode.setText(node.text, node.startSelection, node.endSelection);
        } else {
            viewNode.setText(node.text);
        }
        int left = (int) mRenderCoordinates.fromLocalCssToPix(node.x);
        int top = (int) mRenderCoordinates.fromLocalCssToPix(node.y);
        int width = (int) mRenderCoordinates.fromLocalCssToPix(node.width);
        int height = (int) mRenderCoordinates.fromLocalCssToPix(node.height);

        Rect boundsInParent = new Rect(left, top, left + width, top + height);
        if (node.isRootNode) {
            // Offset of the web content relative to the View.
            boundsInParent.offset(0, (int) mRenderCoordinates.getContentOffsetYPix());
            if (!ignoreScrollOffset) {
                boundsInParent.offset(-(int) mRenderCoordinates.getScrollXPix(),
                        -(int) mRenderCoordinates.getScrollYPix());
            }
        }

        viewNode.setDimens(boundsInParent.left, boundsInParent.top, 0, 0, width, height);
        viewNode.setChildCount(node.children.size());
        if (node.hasStyle) {
            // The text size should be in physical pixels, not CSS pixels.
            float textSize = mRenderCoordinates.fromLocalCssToPix(node.textSize);

            int style = (node.bold ? ViewNode.TEXT_STYLE_BOLD : 0)
                    | (node.italic ? ViewNode.TEXT_STYLE_ITALIC : 0)
                    | (node.underline ? ViewNode.TEXT_STYLE_UNDERLINE : 0)
                    | (node.lineThrough ? ViewNode.TEXT_STYLE_STRIKE_THRU : 0);
            viewNode.setTextStyle(textSize, node.color, node.bgcolor, style);
        }
        for (int i = 0; i < node.children.size(); i++) {
            createVirtualStructure(viewNode.asyncNewChild(i), node.children.get(i), true);
        }
        viewNode.asyncCommit();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void requestUnbufferedDispatch(MotionEvent touchDownEvent) {
        mContainerView.requestUnbufferedDispatch(touchDownEvent);
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    @Override
    public void onSystemCaptioningChanged(TextTrackSettings settings) {
        if (mNativeContentViewCore == 0) return;
        nativeSetTextTrackSettings(mNativeContentViewCore,
                settings.getTextTracksEnabled(), settings.getTextTrackBackgroundColor(),
                settings.getTextTrackFontFamily(), settings.getTextTrackFontStyle(),
                settings.getTextTrackFontVariant(), settings.getTextTrackTextColor(),
                settings.getTextTrackTextShadow(), settings.getTextTrackTextSize());
    }

    /**
     * Called when the processed text is replied from an activity that supports
     * Intent.ACTION_PROCESS_TEXT.
     * @param resultCode the code that indicates if the activity successfully processed the text
     * @param data the reply that contains the processed text.
     */
    public void onReceivedProcessTextResult(int resultCode, Intent data) {
        mSelectionPopupController.onReceivedProcessTextResult(resultCode, data);
    }

    /**
     * Returns true if accessibility is on and touch exploration is enabled.
     */
    public boolean isTouchExplorationEnabled() {
        return mTouchExplorationEnabled;
    }

    /**
     * Turns browser accessibility on or off.
     * If |state| is |false|, this turns off both native and injected accessibility.
     * Otherwise, if accessibility script injection is enabled, this will enable the injected
     * accessibility scripts. Native accessibility is enabled on demand.
     */
    public void setAccessibilityState(boolean state) {
        if (!state) {
            mNativeAccessibilityAllowed = false;
            mTouchExplorationEnabled = false;
        } else {
            mNativeAccessibilityAllowed = true;
            mTouchExplorationEnabled = mAccessibilityManager.isTouchExplorationEnabled();
        }
    }

    /**
     * Return whether or not we should set accessibility focus on page load.
     */
    public boolean shouldSetAccessibilityFocusOnPageLoad() {
        return mShouldSetAccessibilityFocusOnPageLoad;
    }

    /**
     * Sets whether or not we should set accessibility focus on page load.
     * This only applies if an accessibility service like TalkBack is running.
     * This is desirable behavior for a browser window, but not for an embedded
     * WebView.
     */
    public void setShouldSetAccessibilityFocusOnPageLoad(boolean on) {
        mShouldSetAccessibilityFocusOnPageLoad = on;
    }

    /**
     *
     * @return The cached copy of render positions and scales.
     */
    public RenderCoordinates getRenderCoordinates() {
        return mRenderCoordinates;
    }

    /**
     * @return Whether the current page seems to be mobile-optimized. This hint is based upon
     *         rendered frames and may return different values when called multiple times for the
     *         same page (particularly during page load).
     */
    public boolean getIsMobileOptimizedHint() {
        return mIsMobileOptimizedHint;
    }

    @CalledByNative
    private static Rect createRect(int x, int y, int right, int bottom) {
        return new Rect(x, y, right, bottom);
    }

    public void setBackgroundOpaque(boolean opaque) {
        if (mNativeContentViewCore != 0) {
            nativeSetBackgroundOpaque(mNativeContentViewCore, opaque);
        }
    }

    /**
     * @see View#onDragEvent(DragEvent)
     */
    @TargetApi(Build.VERSION_CODES.N)
    public boolean onDragEvent(DragEvent event) {
        if (mNativeContentViewCore == 0 || Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
            return false;
        }

        ClipDescription clipDescription = event.getClipDescription();

        // text/* will match text/uri-list, text/html, text/plain.
        String[] mimeTypes =
                clipDescription == null ? new String[0] : clipDescription.filterMimeTypes("text/*");

        if (event.getAction() == DragEvent.ACTION_DRAG_STARTED) {
            // TODO(hush): support dragging more than just text.
            return mimeTypes != null && mimeTypes.length > 0
                    && nativeIsTouchDragDropEnabled(mNativeContentViewCore);
        }

        StringBuilder content = new StringBuilder("");
        if (event.getAction() == DragEvent.ACTION_DROP) {
            // TODO(hush): obtain dragdrop permissions, when dragging files into Chrome/WebView is
            // supported. Not necessary to do so for now, because only text dragging is supported.
            ClipData clipData = event.getClipData();
            final int itemCount = clipData.getItemCount();
            for (int i = 0; i < itemCount; i++) {
                ClipData.Item item = clipData.getItemAt(i);
                content.append(item.coerceToStyledText(mContainerView.getContext()));
            }
        }

        int[] locationOnScreen = new int[2];
        mContainerView.getLocationOnScreen(locationOnScreen);

        float xPix = event.getX() + mCurrentTouchOffsetX;
        float yPix = event.getY() + mCurrentTouchOffsetY;

        int xCss = (int) mRenderCoordinates.fromPixToDip(xPix);
        int yCss = (int) mRenderCoordinates.fromPixToDip(yPix);
        int screenXCss = (int) mRenderCoordinates.fromPixToDip(xPix + locationOnScreen[0]);
        int screenYCss = (int) mRenderCoordinates.fromPixToDip(yPix + locationOnScreen[1]);

        nativeOnDragEvent(mNativeContentViewCore, event.getAction(), xCss, yCss, screenXCss,
                screenYCss, mimeTypes, content.toString());
        return true;
    }

    /**
     * Offer a long press gesture to the embedding View, primarily for WebView compatibility.
     *
     * @return true if the embedder handled the event.
     */
    private boolean offerLongPressToEmbedder() {
        return mContainerView.performLongClick();
    }

    /**
     * Reset scroll and fling accounting, notifying listeners as appropriate.
     * This is useful as a failsafe when the input stream may have been interruped.
     */
    private void resetScrollInProgress() {
        if (!isScrollInProgress()) return;

        final boolean touchScrollInProgress = mTouchScrollInProgress;
        final int potentiallyActiveFlingCount = mPotentiallyActiveFlingCount;

        mPotentiallyActiveFlingCount = 0;
        setTouchScrollInProgress(false);
        if (touchScrollInProgress) updateGestureStateListener(GestureEventType.SCROLL_END);
        if (potentiallyActiveFlingCount > 0) updateGestureStateListener(GestureEventType.FLING_END);
    }

    @CalledByNative
    private void onNativeFlingStopped() {
        if (mPotentiallyActiveFlingCount > 0) {
            mPotentiallyActiveFlingCount--;
            updateGestureStateListener(GestureEventType.FLING_END);
        }
        // Note that mTouchScrollInProgress should normally be false at this
        // point, but we reset it anyway as another failsafe.
        setTouchScrollInProgress(false);
    }

    // DisplayAndroidObserver method.
    @Override
    public void onRotationChanged(int rotation) {
        // ActionMode#invalidate() won't be able to re-layout the floating
        // action mode menu items according to the new rotation. So Chrome
        // has to re-create the action mode.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && mSelectionPopupController.isActionModeValid()) {
            hidePopupsAndPreserveSelection();
            showSelectActionMode();
        }

        int rotationDegrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                rotationDegrees = 0;
                break;
            case Surface.ROTATION_90:
                rotationDegrees = 90;
                break;
            case Surface.ROTATION_180:
                rotationDegrees = 180;
                break;
            case Surface.ROTATION_270:
                rotationDegrees = -90;
                break;
            default:
                throw new IllegalStateException(
                        "Display.getRotation() shouldn't return that value");
        }

        sendOrientationChangeEvent(rotationDegrees);
    }

    // DisplayAndroidObserver method.
    @Override
    public void onDIPScaleChanged(float dipScale) {
        WindowAndroid windowAndroid = getWindowAndroid();
        if (windowAndroid == null || mNativeContentViewCore == 0) return;

        mRenderCoordinates.setDeviceScaleFactor(dipScale, getWindowAndroid().getContext());
        nativeSetDIPScale(mNativeContentViewCore, dipScale);
    }

    /**
     * Set whether the ContentViewCore requires the WebContents to be fullscreen in order to lock
     * the screen orientation.
     */
    public void setFullscreenRequiredForOrientationLock(boolean value) {
        mFullscreenRequiredForOrientationLock = value;
    }

    @CalledByNative
    private boolean isFullscreenRequiredForOrientationLock() {
        return mFullscreenRequiredForOrientationLock;
    }

    /**
     * Sets the client that can process and augment existing text selection, e.g. Contextual Search.
     * @param selectionClient The client that receives related notifications.
     */
    public void setSelectionClient(SelectionClient selectionClient) {
        mSelectionPopupController.setSelectionClient(selectionClient);
    }

    // TODO(timav): Use |TextClassifier| instead of |Object| after we switch to Android SDK 26.
    /**
     * Sets TextClassifier for Smart Text selection.
     */
    public void setTextClassifier(Object textClassifier) {
        mSelectionPopupController.setTextClassifier(textClassifier);
    }

    // TODO(timav): Use |TextClassifier| instead of |Object| after we switch to Android SDK 26.
    /**
     * Returns TextClassifier that is used for Smart Text selection. If the custom classifier
     * has been set with setTextClassifier, returns that object, otherwise returns the system
     * classifier.
     */
    public Object getTextClassifier() {
        return mSelectionPopupController.getTextClassifier();
    }

    // TODO(timav): Use |TextClassifier| instead of |Object| after we switch to Android SDK 26.
    /**
     * Returns the TextClassifier which has been set with setTextClassifier(), or null.
     */
    public Object getCustomTextClassifier() {
        return mSelectionPopupController.getCustomTextClassifier();
    }

    private native long nativeInit(WebContents webContents, ViewAndroidDelegate viewAndroidDelegate,
            long windowAndroidPtr, float dipScale, HashSet<Object> retainedObjectSet);
    private static native ContentViewCore nativeFromWebContentsAndroid(WebContents webContents);

    private native void nativeUpdateWindowAndroid(
            long nativeContentViewCoreImpl, long windowAndroidPtr);
    private native WebContents nativeGetWebContentsAndroid(long nativeContentViewCoreImpl);
    private native WindowAndroid nativeGetJavaWindowAndroid(long nativeContentViewCoreImpl);

    private native void nativeOnJavaContentViewCoreDestroyed(long nativeContentViewCoreImpl);

    private native void nativeSetFocus(long nativeContentViewCoreImpl, boolean focused);

    private native void nativeSetDIPScale(long nativeContentViewCoreImpl, float dipScale);

    private native void nativeSendOrientationChangeEvent(
            long nativeContentViewCoreImpl, int orientation);

    private native void nativeScrollBegin(long nativeContentViewCoreImpl, long timeMs, float x,
            float y, float hintX, float hintY, boolean targetViewport, boolean fromGamepad);

    private native void nativeScrollEnd(long nativeContentViewCoreImpl, long timeMs);

    private native void nativeScrollBy(
            long nativeContentViewCoreImpl, long timeMs, float x, float y,
            float deltaX, float deltaY);

    private native void nativeFlingStart(long nativeContentViewCoreImpl, long timeMs, float x,
            float y, float vx, float vy, boolean targetViewport, boolean fromGamepad);

    private native void nativeFlingCancel(
            long nativeContentViewCoreImpl, long timeMs, boolean fromGamepad);

    private native void nativeDoubleTap(
            long nativeContentViewCoreImpl, long timeMs, float x, float y);

    private native void nativeResolveTapDisambiguation(
            long nativeContentViewCoreImpl, long timeMs, float x, float y, boolean isLongPress);

    private native void nativePinchBegin(
            long nativeContentViewCoreImpl, long timeMs, float x, float y);

    private native void nativePinchEnd(long nativeContentViewCoreImpl, long timeMs);

    private native void nativePinchBy(long nativeContentViewCoreImpl, long timeMs,
            float anchorX, float anchorY, float deltaScale);

    private native void nativeSetTextHandlesTemporarilyHidden(
            long nativeContentViewCoreImpl, boolean hidden);

    private native void nativeResetGestureDetection(long nativeContentViewCoreImpl);

    private native void nativeSetDoubleTapSupportEnabled(
            long nativeContentViewCoreImpl, boolean enabled);

    private native void nativeSetMultiTouchZoomSupportEnabled(
            long nativeContentViewCoreImpl, boolean enabled);

    private native void nativeSelectPopupMenuItems(long nativeContentViewCoreImpl,
            long nativeSelectPopupSourceFrame, int[] indices);

    private native int nativeGetCurrentRenderProcessId(long nativeContentViewCoreImpl);

    private native void nativeSetAllowJavascriptInterfacesInspection(
            long nativeContentViewCoreImpl, boolean allow);

    private native void nativeAddJavascriptInterface(long nativeContentViewCoreImpl, Object object,
            String name, Class requiredAnnotation);

    private native void nativeRemoveJavascriptInterface(long nativeContentViewCoreImpl,
            String name);

    private native void nativeWasResized(long nativeContentViewCoreImpl);

    private native void nativeSetAccessibilityEnabled(
            long nativeContentViewCoreImpl, boolean enabled);

    private native void nativeSetTextTrackSettings(long nativeContentViewCoreImpl,
            boolean textTracksEnabled, String textTrackBackgroundColor, String textTrackFontFamily,
            String textTrackFontStyle, String textTrackFontVariant, String textTrackTextColor,
            String textTrackTextShadow, String textTrackTextSize);

    private native void nativeSetBackgroundOpaque(long nativeContentViewCoreImpl, boolean opaque);
    private native boolean nativeIsTouchDragDropEnabled(long nativeContentViewCoreImpl);
    private native void nativeOnDragEvent(long nativeContentViewCoreImpl, int action, int x, int y,
            int screenX, int screenY, String[] mimeTypes, String content);
}

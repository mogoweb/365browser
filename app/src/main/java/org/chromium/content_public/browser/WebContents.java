// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content_public.browser;

import android.os.Handler;
import android.os.Parcelable;

import org.chromium.base.VisibleForTesting;
import org.chromium.content.browser.RenderCoordinates;
import org.chromium.ui.OverscrollRefreshHandler;
import org.chromium.ui.base.EventForwarder;
import org.chromium.ui.base.WindowAndroid;

/**
 * The WebContents Java wrapper to allow communicating with the native WebContents object.
 *
 * Note about serialization and {@link Parcelable}:
 *   This object is serializable and deserializable as long as it is done in the same process.  That
 * means it can be passed between Activities inside this process, but not preserved beyond the
 * process lifetime.  This class will automatically deserialize into {@code null} if a deserialize
 * attempt happens in another process.
 *
 * To properly deserialize a custom Parcelable the right class loader must be used.  See below for
 * some examples.
 *
 * Intent Serialization/Deserialization Example:
 * intent.putExtra("WEBCONTENTSKEY", webContents);
 * // ... send to other location ...
 * intent.setExtrasClassLoader(WebContents.class.getClassLoader());
 * webContents = intent.getParcelableExtra("WEBCONTENTSKEY");
 *
 * Bundle Serialization/Deserialization Example:
 * bundle.putParcelable("WEBCONTENTSKEY", webContents);
 * // ... send to other location ...
 * bundle.setClassLoader(WebContents.class.getClassLoader());
 * webContents = bundle.get("WEBCONTENTSKEY");
 */
public interface WebContents extends Parcelable {
    /**
     * @return The top level WindowAndroid associated with this WebContents.  This can be null.
     */
    WindowAndroid getTopLevelNativeWindow();

    /**
     * Deletes the Web Contents object.
     */
    void destroy();

    /**
     * @return Whether or not the native object associated with this WebContent is destroyed.
     */
    boolean isDestroyed();

    /**
     * @return The navigation controller associated with this WebContents.
     */
    NavigationController getNavigationController();

    /**
     * @return  The main frame associated with this WebContents.
     */
    RenderFrameHost getMainFrame();

    /**
     * @return The title for the current visible page.
     */
    String getTitle();

    /**
     * @return The URL for the current visible page.
     */
    String getVisibleUrl();

    /**
     * @return Whether this WebContents is loading a resource.
     */
    boolean isLoading();

    /**
     * @return Whether this WebContents is loading and and the load is to a different top-level
     *         document (rather than being a navigation within the same document).
     */
    boolean isLoadingToDifferentDocument();

    /**
     * Stop any pending navigation.
     */
    void stop();

    // TODO (amaralp): Only used in content. Should be moved out of public interface.
    /**
     * Cut the selected content.
     */
    void cut();

    // TODO (amaralp): Only used in content. Should be moved out of public interface.
    /**
     * Copy the selected content.
     */
    void copy();

    // TODO (amaralp): Only used in content. Should be moved out of public interface.
    /**
     * Paste content from the clipboard.
     */
    void paste();

    // TODO (amaralp): Only used in content. Should be moved out of public interface.
    /**
     * Paste content from the clipboard without format.
     */
    void pasteAsPlainText();

    // TODO (amaralp): Only used in content. Should be moved out of public interface.
    /**
     * Replace the selected text with the {@code word}.
     */
    void replace(String word);

    // TODO (amaralp): Only used in content. Should be moved out of public interface.
    /**
     * Select all content.
     */
    void selectAll();

    // TODO (amaralp): Only used in content. Should be moved out of public interface.
    /**
     * Collapse the selection to the end of selection range.
     */
    void collapseSelection();

    /**
     * To be called when the ContentView is hidden.
     */
    void onHide();

    /**
     * To be called when the ContentView is shown.
     */
    void onShow();

    // TODO (amaralp): Only used in content. Should be moved out of public interface.
    /**
     * Removes handles used in text selection.
     */
    void dismissTextHandles();

    // TODO (amaralp): Only used in content. Should be moved out of public interface.
    /**
     * Shows paste popup menu at point
     */
    void showContextMenuAtPoint(int x, int y);

    /**
     * Suspends all media players for this WebContents.  Note: There may still
     * be activities generating audio, so setAudioMuted() should also be called
     * to ensure all audible activity is silenced.
     */
    void suspendAllMediaPlayers();

    /**
     * Sets whether all audio output from this WebContents is muted.
     *
     * @param mute Set to true to mute the WebContents, false to unmute.
     */
    void setAudioMuted(boolean mute);

    /**
     * Get the Background color from underlying RenderWidgetHost for this WebContent.
     */
    int getBackgroundColor();

    /**
     * Shows an interstitial page driven by the passed in delegate.
     *
     * @param url The URL being blocked by the interstitial.
     * @param interstitialPageDelegateAndroid The delegate handling the interstitial.
     */
    @VisibleForTesting
    void showInterstitialPage(
            String url, long interstitialPageDelegateAndroid);

    /**
     * @return Whether the page is currently showing an interstitial, such as a bad HTTPS page.
     */
    boolean isShowingInterstitialPage();

    /**
     * @return Whether the location bar should be focused by default for this page.
     */
    boolean focusLocationBarByDefault();

    /**
     * If the view is ready to draw contents to the screen. In hardware mode,
     * the initialization of the surface texture may not occur until after the
     * view has been added to the layout. This method will return {@code true}
     * once the texture is actually ready.
     */
    boolean isReady();

     /**
     * Inform WebKit that Fullscreen mode has been exited by the user.
     */
    void exitFullscreen();

    /**
     * Changes whether hiding the browser controls is enabled.
     *
     * @param enableHiding Whether hiding the browser controls should be enabled or not.
     * @param enableShowing Whether showing the browser controls should be enabled or not.
     * @param animate Whether the transition should be animated or not.
     */
    void updateBrowserControlsState(boolean enableHiding, boolean enableShowing, boolean animate);

    /**
     * Brings the Editable to the visible area while IME is up to make easier for inputing text.
     */
    void scrollFocusedEditableNodeIntoView();

    /**
     * Selects the word around the caret, if any.
     * The caller can check if selection actually occurred by listening to OnSelectionChanged.
     */
    void selectWordAroundCaret();

    /**
     * Adjusts the selection starting and ending points by the given amount.
     * A negative amount moves the selection towards the beginning of the document, a positive
     * amount moves the selection towards the end of the document.
     * @param startAdjust The amount to adjust the start of the selection.
     * @param endAdjust The amount to adjust the end of the selection.
     */
    public void adjustSelectionByCharacterOffset(int startAdjust, int endAdjust);

    /**
     * Get the URL of the current page.
     *
     * @return The URL of the current page.
     */
    String getUrl();

    /**
     * Gets the last committed URL. It represents the current page that is
     * displayed in this WebContents. It represents the current security context.
     *
     * @return The last committed URL.
     */
    String getLastCommittedUrl();

    /**
     * Get the InCognito state of WebContents.
     *
     * @return whether this WebContents is in InCognito mode or not
     */
    boolean isIncognito();

    /**
     * Resumes the requests for a newly created window.
     */
    void resumeLoadingCreatedWebContents();

    /**
     * Injects the passed Javascript code in the current page and evaluates it.
     * If a result is required, pass in a callback.
     *
     * It is not possible to use this method to evaluate JavaScript on web
     * content, only on WebUI pages.
     *
     * @param script The Javascript to execute.
     * @param callback The callback to be fired off when a result is ready. The script's
     *                 result will be json encoded and passed as the parameter, and the call
     *                 will be made on the main thread.
     *                 If no result is required, pass null.
     */
    void evaluateJavaScript(String script, JavaScriptCallback callback);

    /**
     * Injects the passed Javascript code in the current page and evaluates it.
     * If a result is required, pass in a callback.
     *
     * @param script The Javascript to execute.
     * @param callback The callback to be fired off when a result is ready. The script's
     *                 result will be json encoded and passed as the parameter, and the call
     *                 will be made on the main thread.
     *                 If no result is required, pass null.
     */
    @VisibleForTesting
    void evaluateJavaScriptForTests(String script, JavaScriptCallback callback);

    /**
     * Adds a log message to dev tools console. |level| must be a value of
     * org.chromium.content_public.common.ConsoleMessageLevel.
     */
    void addMessageToDevToolsConsole(int level, String message);

    /**
     * Post a message to a frame.
     *
     * @param frameName The name of the frame. If the name is null the message is posted
     *                  to the main frame.
     * @param message   The message
     * @param targetOrigin  The target origin. If the target origin is a "*" or a
     *                  empty string, it indicates a wildcard target origin.
     * @param sentPorts The sent message ports, if any. Pass null if there is no
     *                  message ports to pass.
     */
    void postMessageToFrame(String frameName, String message,
            String sourceOrigin, String targetOrigin, MessagePort[] ports);

    /**
     * Creates a message channel for sending postMessage requests and returns the ports for
     * each end of the channel.
     * @param service The message port service to register the channel with.
     * @return The ports that forms the ends of the message channel created.
     */
    MessagePort[] createMessageChannel();

    /**
     * Returns whether the initial empty page has been accessed by a script from another
     * page. Always false after the first commit.
     *
     * @return Whether the initial empty page has been accessed by a script.
     */
    boolean hasAccessedInitialDocument();

    /**
     * This returns the theme color as set by the theme-color meta tag.
     * <p>
     * The color returned may retain non-fully opaque alpha components.  A value of
     * {@link android.graphics.Color#TRANSPARENT} means there was no theme color specified.
     *
     * @return The theme color for the content as set by the theme-color meta tag.
     */
    int getThemeColor();

    /**
     * Initiate extraction of text, HTML, and other information for clipping puposes (smart clip)
     * from the rectangle area defined by starting positions (x and y), and width and height.
     */
    void requestSmartClipExtract(
            int x, int y, int width, int height, RenderCoordinates coordinateSpace);

    /**
     * Register a handler to handle smart clip data once extraction is done.
     */
    void setSmartClipResultHandler(final Handler smartClipHandler);

    /**
     * Requests a snapshop of accessibility tree. The result is provided asynchronously
     * using the callback
     * @param callback The callback to be called when the snapshot is ready. The callback
     *                 cannot be null.
     */
    void requestAccessibilitySnapshot(AccessibilitySnapshotCallback callback);

    /**
     * Returns {@link EventForwarder} which is used to forward input/view events
     * to native content layer.
     */
    EventForwarder getEventForwarder();

    /**
     * Add an observer to the WebContents
     *
     * @param observer The observer to add.
     */
    void addObserver(WebContentsObserver observer);

    /**
     * Remove an observer from the WebContents
     *
     * @param observer The observer to remove.
     */
    void removeObserver(WebContentsObserver observer);

    /**
     * Sets a handler to handle swipe to refresh events.
     *
     * @param handler The handler to install.
     */
    void setOverscrollRefreshHandler(OverscrollRefreshHandler handler);

    /**
     * Requests an image snapshot of the content.
     *
     * @param width The width of the resulting bitmap, or 0 for "auto."
     * @param height The height of the resulting bitmap, or 0 for "auto."
     * @param callback May be called synchronously, or at a later point, to deliver the bitmap
     *                 result (or a failure code).
     */
    public void getContentBitmapAsync(int width, int height, ContentBitmapCallback callback);

    /**
     * Reloads all the Lo-Fi images in this WebContents.
     */
    public void reloadLoFiImages();

    /**
     * Sends a request to download the given image {@link url}.
     * This method delegates the call to the downloadImage() method of native WebContents.
     * @param url The URL of the image to download.
     * @param isFavicon Whether the image is a favicon. If true, the cookies are not sent and not
     *                 accepted during download.
     * @param maxBitmapSize The maximum bitmap size. Bitmaps with pixel sizes larger than {@link
     *                 max_bitmap_size} are filtered out from the bitmap results. If there are no
     *                 bitmap results <= {@link max_bitmap_size}, the smallest bitmap is resized to
     *                 {@link max_bitmap_size} and is the only result. A {@link max_bitmap_size} of
     *                 0 means unlimited.
     * @param bypassCache If true, {@link url} is requested from the server even if it is present in
     *                 the browser cache.
     * @param callback The callback which will be called when the bitmaps are received from the
     *                 renderer.
     * @return The unique id of the download request
     */
    public int downloadImage(String url, boolean isFavicon, int maxBitmapSize,
            boolean bypassCache, ImageDownloadCallback callback);

    /**
     * Whether the WebContents has an active fullscreen video with native or custom controls.
     * The WebContents must be fullscreen when this method is called.
     */
    public boolean hasActiveEffectivelyFullscreenVideo();

    /**
     * Issues a fake notification about the renderer being killed.
     *
     * @param wasOomProtected True if the renderer was protected from the OS out-of-memory killer
     *                        (e.g. renderer for the currently selected tab)
     */
    public void simulateRendererKilledForTesting(boolean wasOomProtected);

    /**
     * Notifies the WebContents about the new persistent video status. It should be called whenever
     * the value changes.
     *
     * @param value Whether there is a persistent video associated with this WebContents.
     */
    public void setHasPersistentVideo(boolean value);
}

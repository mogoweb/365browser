// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.SearchManager;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Rect;
import android.os.Build;
import android.provider.Browser;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.ParagraphStyle;
import android.text.style.UpdateAppearance;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;

import org.chromium.base.BuildInfo;
import org.chromium.base.Log;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.content.R;
import org.chromium.content.browser.input.FloatingPastePopupMenu;
import org.chromium.content.browser.input.LGEmailActionModeWorkaround;
import org.chromium.content.browser.input.LegacyPastePopupMenu;
import org.chromium.content.browser.input.PastePopupMenu;
import org.chromium.content.browser.input.PastePopupMenu.PastePopupMenuDelegate;
import org.chromium.content_public.browser.ActionModeCallbackHelper;
import org.chromium.content_public.browser.WebContents;
import org.chromium.ui.base.DeviceFormFactor;
import org.chromium.ui.base.WindowAndroid;
import org.chromium.ui.touch_selection.SelectionEventType;

import java.util.List;

/**
 * A class that handles input-related web content selection UI like action mode
 * and paste popup view. It wraps an {@link ActionMode} created by the associated view,
 * providing modified interaction with it.
 *
 * Embedders can use {@link ActionModeCallbackHelper} implemented by this class
 * to create {@link ActionMode.Callback} instance and configure the selection action
 * mode tasks to their requirements.
 */
@JNINamespace("content")
@TargetApi(Build.VERSION_CODES.M)
public class SelectionPopupController extends ActionModeCallbackHelper {
    private static final String TAG = "SelectionPopupCtlr"; // 20 char limit

    /**
     * Android Intent size limitations prevent sending over a megabyte of data. Limit
     * query lengths to 100kB because other things may be added to the Intent.
     */
    private static final int MAX_SHARE_QUERY_LENGTH = 100000;

    // Default delay for reshowing the {@link ActionMode} after it has been
    // hidden. This avoids flickering issues if there are trailing rect
    // invalidations after the ActionMode is shown. For example, after the user
    // stops dragging a selection handle, in turn showing the ActionMode, the
    // selection change response will be asynchronous. 300ms should accomodate
    // most such trailing, async delays.
    private static final int SHOW_DELAY_MS = 300;

    // A large value to force text processing menu items to be at the end of the
    // context menu. Chosen to be bigger than the order of possible items in the
    // XML template.
    // TODO(timav): remove this constant and use show/hide for Assist item instead
    // of adding and removing it once we switch to Android O SDK. The show/hide method
    // does not require ordering information.
    private static final int MENU_ITEM_ORDER_TEXT_PROCESS_START = 100;

    private final Context mContext;
    private final WindowAndroid mWindowAndroid;
    private final WebContents mWebContents;
    private final RenderCoordinates mRenderCoordinates;
    private ActionMode.Callback mCallback;

    // Selection rectangle in DIP.
    private final Rect mSelectionRect = new Rect();

    // Self-repeating task that repeatedly hides the ActionMode. This is
    // required because ActionMode only exposes a temporary hide routine.
    private final Runnable mRepeatingHideRunnable;

    private View mView;
    private ActionMode mActionMode;
    private MenuDescriptor mActionMenuDescriptor;

    // Bit field for mappings from menu item to a flag indicating it is allowed.
    private int mAllowedMenuItems;

    private boolean mHidden;

    private boolean mEditable;
    private boolean mIsPasswordType;
    private boolean mIsInsertion;
    private boolean mCanSelectAllForPastePopup;
    private boolean mCanEditRichlyForPastePopup;

    private boolean mUnselectAllOnDismiss;
    private String mLastSelectedText;

    // Tracks whether a selection is currently active.  When applied to selected text, indicates
    // whether the last selected text is still highlighted.
    private boolean mHasSelection;

    // Lazily created paste popup menu, triggered either via long press in an
    // editable region or from tapping the insertion handle.
    private PastePopupMenu mPastePopupMenu;
    private boolean mWasPastePopupShowingOnInsertionDragStart;

    // The client that processes textual selection, or null if none exists.
    private SelectionClient mSelectionClient;

    // The classificaton result of the selected text if the selection exists and
    // SmartSelectionProvider was able to classify it, otherwise null.
    private SmartSelectionProvider.Result mClassificationResult;

    // The resource ID for Assist menu item.
    private int mAssistMenuItemId;

    // This variable is set to true when showActionMode() is postponed till classification result
    // arrives or till the selection is adjusted based on the classification result.
    private boolean mPendingShowActionMode;

    // Whether a scroll is in progress.
    private boolean mScrollInProgress;

    /**
     * Create {@link SelectionPopupController} instance.
     * @param context Context for action mode.
     * @param window WindowAndroid instance.
     * @param webContents WebContents instance.
     * @param view Container view.
     * @param renderCoordinates Coordinates info used to position elements.
     */
    public SelectionPopupController(Context context, WindowAndroid window, WebContents webContents,
            View view, RenderCoordinates renderCoordinates) {
        mContext = context;
        mWindowAndroid = window;
        mWebContents = webContents;
        mView = view;
        mRenderCoordinates = renderCoordinates;

        // The menu items are allowed by default.
        mAllowedMenuItems = MENU_ITEM_SHARE | MENU_ITEM_WEB_SEARCH | MENU_ITEM_PROCESS_TEXT;
        mRepeatingHideRunnable = new Runnable() {
            @Override
            public void run() {
                assert mHidden;
                final long hideDuration = getDefaultHideDuration();
                // Ensure the next hide call occurs before the ActionMode reappears.
                mView.postDelayed(mRepeatingHideRunnable, hideDuration - 1);
                hideActionModeTemporarily(hideDuration);
            }
        };

        mSelectionClient =
                SmartSelectionClient.create(new SmartSelectionCallback(), window, webContents);

        // TODO(timav): Use android.R.id.textAssist for the Assist item id once we switch to
        // Android O SDK and remove |mAssistMenuItemId|.
        if (BuildInfo.isAtLeastO()) {
            mAssistMenuItemId =
                    mContext.getResources().getIdentifier("textAssist", "id", "android");
        }

        nativeInit(webContents);
    }

    /**
     * Update the container view.
     */
    void setContainerView(View view) {
        assert view != null;

        // Cleans up action mode before switching to a new container view.
        if (isActionModeValid()) finishActionMode();
        mUnselectAllOnDismiss = true;
        destroyPastePopup();

        mView = view;
    }

    /**
     * Set the action mode callback.
     * @param callback ActionMode.Callback handling the callbacks from action mode.
     */
    void setCallback(ActionMode.Callback callback) {
        mCallback = callback;
    }

    @Override
    public boolean isActionModeValid() {
        return mActionMode != null;
    }

    // True if action mode is initialized to a working (not a no-op) mode.
    private boolean isActionModeSupported() {
        return mCallback != EMPTY_CALLBACK;
    }

    @Override
    public void setAllowedMenuItems(int allowedMenuItems) {
        mAllowedMenuItems = allowedMenuItems;
    }

    /**
     * Show (activate) android action mode by starting it.
     *
     * <p>Action mode in floating mode is tried first, and then falls back to
     * a normal one.
     * <p> If the action mode cannot be created the selection is cleared.
     */
    public void showActionModeOrClearOnFailure() {
        mPendingShowActionMode = false;

        if (!isActionModeSupported() || !hasSelection()) return;

        // Just refresh the view if action mode already exists.
        if (isActionModeValid()) {
            // Try/catch necessary for framework bug, crbug.com/446717.
            try {
                mActionMode.invalidate();
            } catch (NullPointerException e) {
                Log.w(TAG, "Ignoring NPE from ActionMode.invalidate() as workaround for L", e);
            }
            hideActionMode(false);
            return;
        }

        assert mWebContents != null;
        ActionMode actionMode = supportsFloatingActionMode()
                ? startFloatingActionMode()
                : mView.startActionMode(mCallback);
        if (actionMode != null) {
            // This is to work around an LGE email issue. See crbug.com/651706 for more details.
            LGEmailActionModeWorkaround.runIfNecessary(mContext, actionMode);
        }
        mActionMode = actionMode;
        mUnselectAllOnDismiss = true;

        if (!isActionModeValid()) clearSelection();
    }

    @TargetApi(Build.VERSION_CODES.M)
    private ActionMode startFloatingActionMode() {
        ActionMode actionMode = mView.startActionMode(
                new FloatingActionModeCallback(this, mCallback), ActionMode.TYPE_FLOATING);
        return actionMode;
    }

    void createAndShowPastePopup(
            int left, int top, int right, int bottom, boolean canSelectAll, boolean canEditRichly) {
        if (mView.getParent() == null || mView.getVisibility() != View.VISIBLE) {
            return;
        }

        if (!supportsFloatingActionMode() && !canPaste()) return;
        destroyPastePopup();
        mSelectionRect.set(left, top, right, bottom);
        mCanSelectAllForPastePopup = canSelectAll;
        mCanEditRichlyForPastePopup = canEditRichly;
        PastePopupMenuDelegate delegate = new PastePopupMenuDelegate() {
            @Override
            public void paste() {
                mWebContents.paste();
                mWebContents.dismissTextHandles();
            }

            @Override
            public void pasteAsPlainText() {
                mWebContents.pasteAsPlainText();
                mWebContents.dismissTextHandles();
            }

            @Override
            public boolean canPaste() {
                return SelectionPopupController.this.canPaste();
            }

            @Override
            public void selectAll() {
                SelectionPopupController.this.selectAll();
            }

            @Override
            public boolean canSelectAll() {
                return SelectionPopupController.this.canSelectAll();
            }

            @Override
            public boolean canPasteAsPlainText() {
                return SelectionPopupController.this.canPasteAsPlainText();
            }
        };
        Context windowContext = mWindowAndroid.getContext().get();
        if (windowContext == null) return;
        if (supportsFloatingActionMode()) {
            mPastePopupMenu = new FloatingPastePopupMenu(windowContext, mView, delegate);
        } else {
            mPastePopupMenu = new LegacyPastePopupMenu(windowContext, mView, delegate);
        }
        showPastePopup();
    }

    private void showPastePopup() {
        try {
            mPastePopupMenu.show(getSelectionRectRelativeToContainingView());
        } catch (WindowManager.BadTokenException e) {
        }
    }

    @Override
    public boolean supportsFloatingActionMode() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
    }

    void destroyPastePopup() {
        if (isPastePopupShowing()) {
            mPastePopupMenu.hide();
            mPastePopupMenu = null;
        }
    }

    @VisibleForTesting
    public boolean isPastePopupShowing() {
        return mPastePopupMenu != null;
    }

    // Composition methods for android.view.ActionMode

    /**
     * @see ActionMode#finish()
     */
    @Override
    public void finishActionMode() {
        mPendingShowActionMode = false;
        mHidden = false;
        if (mView != null) mView.removeCallbacks(mRepeatingHideRunnable);

        if (isActionModeValid()) {
            mActionMode.finish();

            // Should be nulled out in case #onDestroyActionMode() is not invoked in response.
            mActionMode = null;
            mActionMenuDescriptor = null;
        }
    }

    /**
     * @see ActionMode#invalidateContentRect()
     */
    public void invalidateContentRect() {
        if (supportsFloatingActionMode() && isActionModeValid()) {
            mActionMode.invalidateContentRect();
        }
    }

    /**
     * @see ActionMode#onWindowFocusChanged()
     */
    void onWindowFocusChanged(boolean hasWindowFocus) {
        if (supportsFloatingActionMode() && isActionModeValid()) {
            mActionMode.onWindowFocusChanged(hasWindowFocus);
        }
    }

    void setScrollInProgress(boolean touchScrollInProgress, boolean scrollInProgress) {
        mScrollInProgress = scrollInProgress;

        // The active fling count reflected in |scrollInProgress| isn't reliable with WebView,
        // so only use the active touch scroll signal for hiding. The fling animation
        // movement will naturally hide the ActionMode by invalidating its content rect.
        hideActionMode(touchScrollInProgress);
    }

    /**
     * Hide or reveal the ActionMode. Note that this only has visible
     * side-effects if the underlying ActionMode supports hiding.
     * @param hide whether to hide or show the ActionMode.
     */
    private void hideActionMode(boolean hide) {
        if (!canHideActionMode()) return;
        if (mHidden == hide) return;
        mHidden = hide;
        if (mHidden) {
            mRepeatingHideRunnable.run();
        } else {
            mView.removeCallbacks(mRepeatingHideRunnable);
            // To show the action mode that is being hidden call hide() again with a short delay.
            hideActionModeTemporarily(SHOW_DELAY_MS);
        }
    }

    /**
     * @see ActionMode#hide(long)
     */
    private void hideActionModeTemporarily(long duration) {
        assert canHideActionMode();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (isActionModeValid()) mActionMode.hide(duration);
        }
    }

    private boolean canHideActionMode() {
        return supportsFloatingActionMode()
                && isActionModeValid()
                && mActionMode.getType() == ActionMode.TYPE_FLOATING;
    }

    private long getDefaultHideDuration() {
        if (supportsFloatingActionMode()) {
            return ViewConfiguration.getDefaultActionModeHideDuration();
        }
        return 2000;
    }

    // Default handlers for action mode callbacks.

    @Override
    public void onCreateActionMode(ActionMode mode, Menu menu) {
        mode.setTitle(DeviceFormFactor.isTablet()
                        ? mContext.getString(R.string.actionbar_textselection_title)
                        : null);
        mode.setSubtitle(null);
        createActionMenu(mode, menu);
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        menu.removeGroup(R.id.select_action_menu_default_items);
        menu.removeGroup(R.id.select_action_menu_assist_items);
        menu.removeGroup(R.id.select_action_menu_text_processing_menus);
        createActionMenu(mode, menu);
        return true;
    }

    /**
     * Initialize the menu by populating all the available items. Embedders should remove
     * the items that are not relevant to the input text being edited.
     */
    public static void initializeMenu(Context context, ActionMode mode, Menu menu) {
        try {
            mode.getMenuInflater().inflate(R.menu.select_action_menu, menu);
        } catch (Resources.NotFoundException e) {
            // TODO(tobiasjs) by the time we get here we have already
            // caused a resource loading failure to be logged. WebView
            // resource access needs to be improved so that this
            // logspam can be avoided.
            new MenuInflater(context).inflate(R.menu.select_action_menu, menu);
        }
    }

    private void createActionMenu(ActionMode mode, Menu menu) {
        initializeMenu(mContext, mode, menu);

        mActionMenuDescriptor = createActionMenuDescriptor();
        mActionMenuDescriptor.apply(menu);

        if (isInsertion() || isSelectionPassword()) return;

        initializeTextProcessingMenu(menu);
    }

    private MenuDescriptor createActionMenuDescriptor() {
        MenuDescriptor descriptor = new MenuDescriptor();

        updateAssistMenuItem(descriptor);

        // TODO(ctzsm): Remove "paste as plain text" for now, need to add it back when
        // crrev.com/2785853002 landed.
        descriptor.removeItem(R.id.select_action_menu_paste_as_plain_text);

        if (!isSelectionEditable() || !canPaste()) {
            descriptor.removeItem(R.id.select_action_menu_paste);
        }

        if (isInsertion()) {
            descriptor.removeItem(R.id.select_action_menu_select_all);
            descriptor.removeItem(R.id.select_action_menu_cut);
            descriptor.removeItem(R.id.select_action_menu_copy);
            descriptor.removeItem(R.id.select_action_menu_share);
            descriptor.removeItem(R.id.select_action_menu_web_search);
            return descriptor;
        }

        if (!isSelectionEditable()) {
            descriptor.removeItem(R.id.select_action_menu_cut);
        }

        if (isSelectionEditable() || !isSelectActionModeAllowed(MENU_ITEM_SHARE)) {
            descriptor.removeItem(R.id.select_action_menu_share);
        }

        if (isSelectionEditable() || isIncognito()
                || !isSelectActionModeAllowed(MENU_ITEM_WEB_SEARCH)) {
            descriptor.removeItem(R.id.select_action_menu_web_search);
        }

        if (isSelectionPassword()) {
            descriptor.removeItem(R.id.select_action_menu_copy);
            descriptor.removeItem(R.id.select_action_menu_cut);
        }

        return descriptor;
    }

    private boolean needsActionMenuUpdate() {
        return !createActionMenuDescriptor().equals(mActionMenuDescriptor);
    }

    private boolean canPaste() {
        ClipboardManager clipMgr = (ClipboardManager)
                mContext.getSystemService(Context.CLIPBOARD_SERVICE);
        return clipMgr.hasPrimaryClip();
    }

    // Check if this Spanned is formatted text.
    private boolean hasStyleSpan(Spanned spanned) {
        // Only check against those three classes below, which could affect text appearance, since
        // there are other kind of classes won't affect appearance.
        Class<?>[] styleClasses = {
                CharacterStyle.class, ParagraphStyle.class, UpdateAppearance.class};
        for (Class<?> clazz : styleClasses) {
            if (spanned.nextSpanTransition(-1, spanned.length(), clazz) < spanned.length()) {
                return true;
            }
        }
        return false;
    }

    // Check if need to show "paste as plain text" option.
    // Don't show "paste as plain text" when "paste" and "paste as plain text" would do exactly the
    // same.
    @VisibleForTesting
    public boolean canPasteAsPlainText() {
        // String resource "paste_as_plain_text" only exist in O.
        // Also this is an O feature, we need to make it consistant with TextView.
        if (!BuildInfo.isAtLeastO()) return false;
        if (!mCanEditRichlyForPastePopup) return false;
        ClipboardManager clipMgr =
                (ClipboardManager) mContext.getSystemService(Context.CLIPBOARD_SERVICE);
        if (!clipMgr.hasPrimaryClip()) return false;

        ClipData clipData = clipMgr.getPrimaryClip();
        ClipDescription description = clipData.getDescription();
        CharSequence text = clipData.getItemAt(0).getText();
        boolean isPlainType = description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN);
        // On Android, Spanned could be copied to Clipboard as plain_text MIME type, but in some
        // cases, Spanned could have text format, we need to show "paste as plain text" when
        // that happens.
        if (isPlainType && (text instanceof Spanned)) {
            Spanned spanned = (Spanned) text;
            if (hasStyleSpan(spanned)) return true;
        }
        return description.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML);
    }

    private void updateAssistMenuItem(MenuDescriptor descriptor) {
        // There is no Assist functionality before Android O.
        if (!BuildInfo.isAtLeastO() || mAssistMenuItemId == 0) return;

        // The assist menu item ID has to be equal to android.R.id.textAssist. Until we compile
        // with Android O SDK where this ID is defined we replace the corresponding inflated
        // item with an item with the proper ID.
        // TODO(timav): Use android.R.id.textAssist for the Assist item id once we switch to
        // Android O SDK and remove |mAssistMenuItemId|.

        if (mClassificationResult != null && mClassificationResult.hasNamedAction()) {
            descriptor.addItem(R.id.select_action_menu_assist_items, mAssistMenuItemId, 1,
                    mClassificationResult.label, mClassificationResult.icon);
        }
    }

    /**
     * Intialize the menu items for processing text, if there is any.
     */
    private void initializeTextProcessingMenu(Menu menu) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || !isSelectActionModeAllowed(MENU_ITEM_PROCESS_TEXT)) {
            return;
        }

        PackageManager packageManager = mContext.getPackageManager();
        List<ResolveInfo> supportedActivities =
                packageManager.queryIntentActivities(createProcessTextIntent(), 0);
        for (int i = 0; i < supportedActivities.size(); i++) {
            ResolveInfo resolveInfo = supportedActivities.get(i);
            CharSequence label = resolveInfo.loadLabel(mContext.getPackageManager());
            menu.add(R.id.select_action_menu_text_processing_menus, Menu.NONE,
                    MENU_ITEM_ORDER_TEXT_PROCESS_START + i, label)
                    .setIntent(createProcessTextIntentForResolveInfo(resolveInfo))
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private static Intent createProcessTextIntent() {
        return new Intent().setAction(Intent.ACTION_PROCESS_TEXT).setType("text/plain");
    }

    @TargetApi(Build.VERSION_CODES.M)
    private Intent createProcessTextIntentForResolveInfo(ResolveInfo info) {
        boolean isReadOnly = !isSelectionEditable();
        return createProcessTextIntent()
                .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, isReadOnly)
                .setClassName(info.activityInfo.packageName, info.activityInfo.name);
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        if (!isActionModeValid()) return true;

        int id = item.getItemId();
        int groupId = item.getGroupId();

        if (id == mAssistMenuItemId) {
            doAssistAction();
            mode.finish();
        } else if (id == R.id.select_action_menu_select_all) {
            selectAll();
        } else if (id == R.id.select_action_menu_cut) {
            cut();
            mode.finish();
        } else if (id == R.id.select_action_menu_copy) {
            copy();
            mode.finish();
        } else if (id == R.id.select_action_menu_paste) {
            paste();
            mode.finish();
        } else if (id == R.id.select_action_menu_share) {
            share();
            mode.finish();
        } else if (id == R.id.select_action_menu_web_search) {
            search();
            mode.finish();
        } else if (groupId == R.id.select_action_menu_text_processing_menus) {
            processText(item.getIntent());
            // The ActionMode is not dismissed to match the behavior with
            // TextView in Android M.
        } else {
            return false;
        }
        return true;
    }

    @Override
    public void onDestroyActionMode() {
        mActionMode = null;
        mActionMenuDescriptor = null;
        if (mUnselectAllOnDismiss) {
            mWebContents.dismissTextHandles();
            clearSelection();
        }
    }

    /**
     * Called when an ActionMode needs to be positioned on screen, potentially occluding view
     * content. Note this may be called on a per-frame basis.
     *
     * @param mode The ActionMode that requires positioning.
     * @param view The View that originated the ActionMode, in whose coordinates the Rect should
     *             be provided.
     * @param outRect The Rect to be populated with the content position.
     */
    @Override
    public void onGetContentRect(ActionMode mode, View view, Rect outRect) {
        outRect.set(getSelectionRectRelativeToContainingView());
    }

    private Rect getSelectionRectRelativeToContainingView() {
        float deviceScale = mRenderCoordinates.getDeviceScaleFactor();
        Rect viewSelectionRect = new Rect((int) (mSelectionRect.left * deviceScale),
                (int) (mSelectionRect.top * deviceScale),
                (int) (mSelectionRect.right * deviceScale),
                (int) (mSelectionRect.bottom * deviceScale));

        // The selection coordinates are relative to the content viewport, but we need
        // coordinates relative to the containing View.
        viewSelectionRect.offset(0, (int) mRenderCoordinates.getContentOffsetYPix());
        return viewSelectionRect;
    }

    /**
     * Perform an action that depends on the semantics of the selected text.
     */
    @VisibleForTesting
    void doAssistAction() {
        if (mClassificationResult == null || !mClassificationResult.hasNamedAction()) return;

        assert mClassificationResult.onClickListener != null
                || mClassificationResult.intent != null;

        if (mClassificationResult.onClickListener != null) {
            mClassificationResult.onClickListener.onClick(mView);
            return;
        }

        if (mClassificationResult.intent != null) {
            Context context = mWindowAndroid.getContext().get();
            if (context == null) return;

            context.startActivity(mClassificationResult.intent);
            return;
        }
    }

    /**
     * Perform a select all action.
     */
    @VisibleForTesting
    void selectAll() {
        mWebContents.selectAll();
        mClassificationResult = null;
        if (needsActionMenuUpdate()) showActionModeOrClearOnFailure();

        // Even though the above statement logged a SelectAll user action, we want to
        // track whether the focus was in an editable field, so log that too.
        if (isSelectionEditable()) {
            RecordUserAction.record("MobileActionMode.SelectAllWasEditable");
        } else {
            RecordUserAction.record("MobileActionMode.SelectAllWasNonEditable");
        }
    }

    /**
     * Perform a cut (to clipboard) action.
     */
    @VisibleForTesting
    void cut() {
        mWebContents.cut();
    }

    /**
     * Perform a copy (to clipboard) action.
     */
    @VisibleForTesting
    void copy() {
        mWebContents.copy();
    }

    /**
     * Perform a paste action.
     */
    @VisibleForTesting
    void paste() {
        mWebContents.paste();
    }

    /**
     * Perform a paste as plain text action.
     */
    @VisibleForTesting
    void pasteAsPlainText() {
        mWebContents.pasteAsPlainText();
    }

    /**
     * Perform a share action.
     */
    @VisibleForTesting
    void share() {
        RecordUserAction.record("MobileActionMode.Share");
        String query = sanitizeQuery(getSelectedText(), MAX_SHARE_QUERY_LENGTH);
        if (TextUtils.isEmpty(query)) return;

        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, query);
        try {
            Intent i = Intent.createChooser(send, mContext.getString(R.string.actionbar_share));
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(i);
        } catch (android.content.ActivityNotFoundException ex) {
            // If no app handles it, do nothing.
        }
    }

    /**
     * Perform a processText action (translating the text, for example).
     */
    private void processText(Intent intent) {
        RecordUserAction.record("MobileActionMode.ProcessTextIntent");
        assert Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;

        String query = sanitizeQuery(getSelectedText(), MAX_SEARCH_QUERY_LENGTH);
        if (TextUtils.isEmpty(query)) return;

        intent.putExtra(Intent.EXTRA_PROCESS_TEXT, query);

        // Intent is sent by WindowAndroid by default.
        try {
            mWindowAndroid.showIntent(intent, new WindowAndroid.IntentCallback() {
                @Override
                public void onIntentCompleted(WindowAndroid window, int resultCode, Intent data) {
                    onReceivedProcessTextResult(resultCode, data);
                }
            }, null);
        } catch (android.content.ActivityNotFoundException ex) {
            // If no app handles it, do nothing.
        }
    }

    /**
     * Perform a search action.
     */
    @VisibleForTesting
    void search() {
        RecordUserAction.record("MobileActionMode.WebSearch");
        String query = sanitizeQuery(getSelectedText(), MAX_SEARCH_QUERY_LENGTH);
        if (TextUtils.isEmpty(query)) return;

        Intent i = new Intent(Intent.ACTION_WEB_SEARCH);
        i.putExtra(SearchManager.EXTRA_NEW_SEARCH, true);
        i.putExtra(SearchManager.QUERY, query);
        i.putExtra(Browser.EXTRA_APPLICATION_ID, mContext.getPackageName());
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            mContext.startActivity(i);
        } catch (android.content.ActivityNotFoundException ex) {
            // If no app handles it, do nothing.
        }
    }

    /**
     * @return true if the current selection is of password type.
     */
    @VisibleForTesting
    boolean isSelectionPassword() {
        return mIsPasswordType;
    }

    /**
     * @return true iff the current selection is editable (e.g. text within an input field).
     */
    boolean isSelectionEditable() {
        return mEditable;
    }

    /**
     * @return true if the current selection is an insertion point.
     */
    @VisibleForTesting
    public boolean isInsertion() {
        return mIsInsertion;
    }

    /**
     * @return true if the current selection can select all.
     */
    @VisibleForTesting
    public boolean canSelectAll() {
        return mCanSelectAllForPastePopup;
    }

    /**
     * @return true if the current selection is for incognito content.
     *         Note: This should remain constant for the callback's lifetime.
     */
    private boolean isIncognito() {
        return mWebContents.isIncognito();
    }

    /**
     * @see ActionModeCallbackHelper#sanitizeQuery(String, int)
     */
    public static String sanitizeQuery(String query, int maxLength) {
        if (TextUtils.isEmpty(query) || query.length() < maxLength) return query;
        Log.w(TAG, "Truncating oversized query (" + query.length() + ").");
        return query.substring(0, maxLength) + "…";
    }

    /**
     * @param actionModeItem the flag for the action mode item in question. The valid flags are
     *        {@link #MENU_ITEM_SHARE}, {@link #MENU_ITEM_WEB_SEARCH}, and
     *        {@link #MENU_ITEM_PROCESS_TEXT}.
     * @return true if the menu item action is allowed. Otherwise, the menu item
     *         should be removed from the menu.
     */
    private boolean isSelectActionModeAllowed(int actionModeItem) {
        boolean isAllowedByClient = (mAllowedMenuItems & actionModeItem) != 0;
        if (actionModeItem == MENU_ITEM_SHARE) {
            return isAllowedByClient && isShareAvailable();
        }
        return isAllowedByClient;
    }

    @Override
    public void onReceivedProcessTextResult(int resultCode, Intent data) {
        if (mWebContents == null || resultCode != Activity.RESULT_OK || data == null) return;

        // Do not handle the result if no text is selected or current selection is not editable.
        if (!hasSelection() || !isSelectionEditable()) return;

        CharSequence result = data.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
        if (result != null) {
            // TODO(hush): Use a variant of replace that re-selects the replaced text.
            // crbug.com/546710
            mWebContents.replace(result.toString());
        }
    }

    void restoreSelectionPopupsIfNecessary() {
        if (hasSelection() && !isActionModeValid()) {
            showActionModeOrClearOnFailure();
        }
    }

    // All coordinates are in DIP.
    @CalledByNative
    private void onSelectionEvent(int eventType, int left, int top, int right, int bottom) {
        // Ensure the provided selection coordinates form a non-empty rect, as required by
        // the selection action mode.
        if (left == right) ++right;
        if (top == bottom) ++bottom;
        switch (eventType) {
            case SelectionEventType.SELECTION_HANDLES_SHOWN:
                mSelectionRect.set(left, top, right, bottom);
                mHasSelection = true;
                mUnselectAllOnDismiss = true;
                if (mSelectionClient != null
                        && mSelectionClient.requestSelectionPopupUpdates(true /* suggest */)) {
                    // Rely on |mSelectionClient| sending a classification request and the request
                    // always calling onClassified() callback.
                    mPendingShowActionMode = true;
                } else {
                    showActionModeOrClearOnFailure();
                }
                break;

            case SelectionEventType.SELECTION_HANDLES_MOVED:
                mSelectionRect.set(left, top, right, bottom);
                if (mPendingShowActionMode) {
                    showActionModeOrClearOnFailure();
                } else {
                    invalidateContentRect();
                }
                break;

            case SelectionEventType.SELECTION_HANDLES_CLEARED:
                mHasSelection = false;
                mUnselectAllOnDismiss = false;
                mSelectionRect.setEmpty();
                if (mSelectionClient != null) mSelectionClient.cancelAllRequests();
                finishActionMode();
                break;

            case SelectionEventType.SELECTION_HANDLE_DRAG_STARTED:
                hideActionMode(true);
                break;

            case SelectionEventType.SELECTION_HANDLE_DRAG_STOPPED:
                if (mSelectionClient != null
                        && mSelectionClient.requestSelectionPopupUpdates(false /* suggest */)) {
                    // Rely on |mSelectionClient| sending a classification request and the request
                    // always calling onClassified() callback.
                } else {
                    hideActionMode(false);
                }
                break;

            case SelectionEventType.INSERTION_HANDLE_SHOWN:
                mSelectionRect.set(left, top, right, bottom);
                mIsInsertion = true;
                break;

            case SelectionEventType.INSERTION_HANDLE_MOVED:
                mSelectionRect.set(left, top, right, bottom);
                if (!mScrollInProgress && isPastePopupShowing()) {
                    showPastePopup();
                } else {
                    destroyPastePopup();
                }
                break;

            case SelectionEventType.INSERTION_HANDLE_TAPPED:
                if (mWasPastePopupShowingOnInsertionDragStart) {
                    destroyPastePopup();
                } else {
                    mWebContents.showContextMenuAtPoint(mSelectionRect.left, mSelectionRect.bottom);
                }
                mWasPastePopupShowingOnInsertionDragStart = false;
                break;

            case SelectionEventType.INSERTION_HANDLE_CLEARED:
                destroyPastePopup();
                mIsInsertion = false;
                mSelectionRect.setEmpty();
                break;

            case SelectionEventType.INSERTION_HANDLE_DRAG_STARTED:
                mWasPastePopupShowingOnInsertionDragStart = isPastePopupShowing();
                destroyPastePopup();
                break;

            case SelectionEventType.INSERTION_HANDLE_DRAG_STOPPED:
                if (mWasPastePopupShowingOnInsertionDragStart) {
                    mWebContents.showContextMenuAtPoint(mSelectionRect.left, mSelectionRect.bottom);
                }
                mWasPastePopupShowingOnInsertionDragStart = false;
                break;

            default:
                assert false : "Invalid selection event type.";
        }

        if (mSelectionClient != null) {
            final float deviceScale = mRenderCoordinates.getDeviceScaleFactor();
            int xAnchorPix = (int) (mSelectionRect.left * deviceScale);
            int yAnchorPix = (int) (mSelectionRect.bottom * deviceScale);
            mSelectionClient.onSelectionEvent(eventType, xAnchorPix, yAnchorPix);
        }
    }

    /**
     * Clears the current text selection. Note that we will try to move cursor to selection
     * end if applicable.
     */
    void clearSelection() {
        if (mWebContents == null || !isActionModeSupported()) return;
        mWebContents.collapseSelection();
        mClassificationResult = null;
    }

    @CalledByNative
    private void onSelectionChanged(String text) {
        mLastSelectedText = text;
        if (mSelectionClient != null) {
            mSelectionClient.onSelectionChanged(text);
        }
    }

    // The client that implements selection augmenting functionality, or null if none exists.
    void setSelectionClient(SelectionClient selectionClient) {
        mSelectionClient = selectionClient;

        mClassificationResult = null;

        assert !mPendingShowActionMode;
        assert !mHidden;
    }

    void onShowUnhandledTapUIIfNeeded(int x, int y) {
        if (mSelectionClient != null) {
            mSelectionClient.showUnhandledTapUIIfNeeded(x, y);
        }
    }

    void onSelectWordAroundCaretAck(boolean didSelect, int startAdjust, int endAdjust) {
        if (mSelectionClient != null) {
            mSelectionClient.selectWordAroundCaretAck(didSelect, startAdjust, endAdjust);
        }
    }

    void destroyActionModeAndUnselect() {
        mUnselectAllOnDismiss = true;
        finishActionMode();
    }

    void destroyActionModeAndKeepSelection() {
        mUnselectAllOnDismiss = false;
        finishActionMode();
    }

    void updateSelectionState(boolean editable, boolean isPassword) {
        if (!editable) destroyPastePopup();
        if (editable != isSelectionEditable() || isPassword != isSelectionPassword()) {
            mEditable = editable;
            mIsPasswordType = isPassword;
            if (isActionModeValid()) mActionMode.invalidate();
        }
    }

    /**
     * @return Whether the page has an active, touch-controlled selection region.
     */
    @VisibleForTesting
    public boolean hasSelection() {
        return mHasSelection;
    }

    @Override
    public String getSelectedText() {
        return hasSelection() ? mLastSelectedText : "";
    }

    private boolean isShareAvailable() {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        return mContext.getPackageManager().queryIntentActivities(intent,
                PackageManager.MATCH_DEFAULT_ONLY).size() > 0;
    }

    // TODO(timav): Use |TextClassifier| instead of |Object| after we switch to Android SDK 26.
    /**
     * Sets TextClassifier for Smart Text selection.
     */
    public void setTextClassifier(Object textClassifier) {
        if (mSelectionClient != null) mSelectionClient.setTextClassifier(textClassifier);
    }

    // TODO(timav): Use |TextClassifier| instead of |Object| after we switch to Android SDK 26.
    /**
     * Returns TextClassifier that is used for Smart Text selection. If the custom classifier
     * has been set with setTextClassifier, returns that object, otherwise returns the system
     * classifier.
     */
    public Object getTextClassifier() {
        return mSelectionClient == null ? null : mSelectionClient.getTextClassifier();
    }

    // TODO(timav): Use |TextClassifier| instead of |Object| after we switch to Android SDK 26.
    /**
     * Returns the TextClassifier which has been set with setTextClassifier(), or null.
     */
    public Object getCustomTextClassifier() {
        return mSelectionClient == null ? null : mSelectionClient.getCustomTextClassifier();
    }

    // The callback class that delivers result from a SmartSelectionClient.
    private class SmartSelectionCallback implements SmartSelectionProvider.ResultCallback {
        @Override
        public void onClassified(SmartSelectionProvider.Result result) {
            // If the selection does not exist any more, discard |result|.
            if (!hasSelection()) {
                assert !mHidden;
                assert mClassificationResult == null;
                mPendingShowActionMode = false;
                return;
            }

            // Do not allow classifier to shorten the selection. If the suggested selection is
            // smaller than the original we throw away classification result and show the menu.
            // TODO(amaralp): This was added to fix the SelectAll problem in
            // http://crbug.com/714106. Once we know the cause of the original selection we can
            // remove this check.
            if (result.startAdjust > 0 || result.endAdjust < 0) {
                mClassificationResult = null;
                mPendingShowActionMode = false;
                showActionModeOrClearOnFailure();
                return;
            }

            // The classificationresult is a property of the selection. Keep it even the action
            // mode has been dismissed.
            mClassificationResult = result;

            // Do not recreate the action mode if it has been cancelled (by ActionMode.finish())
            // and not recreated after that.
            if (!mPendingShowActionMode && !isActionModeValid()) {
                assert !mHidden;
                return;
            }

            // Update the selection range if needed.
            if (!(result.startAdjust == 0 && result.endAdjust == 0)) {
                // This call causes SELECTION_HANDLES_MOVED event.
                mWebContents.adjustSelectionByCharacterOffset(result.startAdjust, result.endAdjust);

                // Remain pending until SELECTION_HANDLES_MOVED arrives.
                if (mPendingShowActionMode) return;
            }

            // Rely on this method to clear |mHidden| and unhide the action mode.
            showActionModeOrClearOnFailure();
        }
    };

    private native void nativeInit(WebContents webContents);
}

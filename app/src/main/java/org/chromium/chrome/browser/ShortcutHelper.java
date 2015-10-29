// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.TypedValue;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.ApplicationStatus;
import org.chromium.base.Log;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.webapps.WebappLauncherActivity;
import org.chromium.chrome.browser.widget.RoundedIconGenerator;
import org.chromium.content_public.common.ScreenOrientationConstants;
import org.chromium.ui.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;

/**
 * This is a helper class to create shortcuts on the Android home screen.
 */
public class ShortcutHelper {
    public static final String EXTRA_ICON = "org.chromium.chrome.browser.webapp_icon";
    public static final String EXTRA_ID = "org.chromium.chrome.browser.webapp_id";
    public static final String EXTRA_MAC = "org.chromium.chrome.browser.webapp_mac";
    // EXTRA_TITLE is present for backward compatibility reasons
    public static final String EXTRA_TITLE = "org.chromium.chrome.browser.webapp_title";
    public static final String EXTRA_NAME = "org.chromium.chrome.browser.webapp_name";
    public static final String EXTRA_SHORT_NAME = "org.chromium.chrome.browser.webapp_short_name";
    public static final String EXTRA_URL = "org.chromium.chrome.browser.webapp_url";
    public static final String EXTRA_ORIENTATION = ScreenOrientationConstants.EXTRA_ORIENTATION;
    public static final String EXTRA_SOURCE = "org.chromium.chrome.browser.webapp_source";
    public static final String EXTRA_THEME_COLOR = "org.chromium.chrome.browser.theme_color";
    public static final String EXTRA_BACKGROUND_COLOR =
            "org.chromium.chrome.browser.background_color";
    public static final String REUSE_URL_MATCHING_TAB_ELSE_NEW_TAB =
            "REUSE_URL_MATCHING_TAB_ELSE_NEW_TAB";

    // This value is equal to kInvalidOrMissingColor in the C++ content::Manifest struct.
    public static final long MANIFEST_COLOR_INVALID_OR_MISSING = ((long) Integer.MAX_VALUE) + 1;

    private static final String TAG = "cr.Shortcuts";
    // There is no public string defining this intent so if Home changes the value, we
    // have to update this string.
    private static final String INSTALL_SHORTCUT = "com.android.launcher.action.INSTALL_SHORTCUT";
    private static final int DEFAULT_RGB_VALUE = 145;
    private static final int INSET_DIMENSION_FOR_TOUCHICON = 1;
    private static final int TOUCHICON_BORDER_RADII_DP = 4;
    private static final int GENERATED_ICON_SIZE_DP = 40;
    private static final int GENERATED_ICON_ROUNDED_CORNERS_DP = 2;
    private static final int GENERATED_ICON_FONT_SIZE_DP = 16;

    /** Broadcasts Intents out Android for adding the shortcut. */
    public static class Delegate {
        /**
         * Broadcasts an intent to all interested BroadcastReceivers.
         * @param context The Context to use.
         * @param intent The intent to broadcast.
         */
        public void sendBroadcast(Context context, Intent intent) {
            context.sendBroadcast(intent);
        }

        /**
         * Returns the name of the fullscreen Activity to use when launching shortcuts.
         */
        public String getFullscreenAction() {
            return WebappLauncherActivity.ACTION_START_WEBAPP;
        }
    }

    private static Delegate sDelegate = new Delegate();

    /**
     * Sets the delegate to use.
     */
    @VisibleForTesting
    public static void setDelegateForTests(Delegate delegate) {
        sDelegate = delegate;
    }

    /**
     * Called when we have to fire an Intent to add a shortcut to the homescreen.
     * If the webpage indicated that it was capable of functioning as a webapp, it is added as a
     * shortcut to a webapp Activity rather than as a general bookmark. User is sent to the
     * homescreen as soon as the shortcut is created.
     */
    @SuppressWarnings("unused")
    @CalledByNative
    private static void addShortcut(Context context, String url, String userTitle, String name,
            String shortName, Bitmap icon, boolean isWebappCapable, int orientation, int source,
            long themeColor, long backgroundColor) {
        Intent shortcutIntent;
        if (isWebappCapable) {
            // Encode the icon as a base64 string (Launcher drops Bitmaps in the Intent).
            String encodedIcon = encodeBitmapAsString(icon);

            // Add the shortcut as a launcher icon for a full-screen Activity.
            shortcutIntent = new Intent();
            shortcutIntent.setAction(sDelegate.getFullscreenAction());
            shortcutIntent.putExtra(EXTRA_ICON, encodedIcon);
            shortcutIntent.putExtra(EXTRA_ID, UUID.randomUUID().toString());
            shortcutIntent.putExtra(EXTRA_NAME, name);
            shortcutIntent.putExtra(EXTRA_SHORT_NAME, shortName);
            shortcutIntent.putExtra(EXTRA_URL, url);
            shortcutIntent.putExtra(EXTRA_ORIENTATION, orientation);
            shortcutIntent.putExtra(EXTRA_MAC, getEncodedMac(context, url));
            shortcutIntent.putExtra(EXTRA_THEME_COLOR, themeColor);
            shortcutIntent.putExtra(EXTRA_BACKGROUND_COLOR, backgroundColor);
        } else {
            // Add the shortcut as a launcher icon to open in the browser Activity.
            shortcutIntent = createShortcutIntent(url);
        }

        // Always attach a source (one of add to homescreen menu item, app banner, or unknown) to
        // the intent. This allows us to distinguish where a shortcut was added from in metrics.
        shortcutIntent.putExtra(EXTRA_SOURCE, source);
        shortcutIntent.setPackage(context.getPackageName());
        sDelegate.sendBroadcast(
                context, createAddToHomeIntent(url, userTitle, icon, shortcutIntent));

        // Alert the user about adding the shortcut.
        final String shortUrl = UrlUtilities.getDomainAndRegistry(url, true);
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                Context applicationContext = ApplicationStatus.getApplicationContext();
                String toastText =
                        applicationContext.getString(R.string.added_to_homescreen, shortUrl);
                Toast toast = Toast.makeText(applicationContext, toastText, Toast.LENGTH_SHORT);
                toast.show();
            }
        });
    }

    /**
     * Creates an intent that will add a shortcut to the home screen.
     * @param shortcutIntent Intent to fire when the shortcut is activated.
     * @param url URL of the shortcut.
     * @param title Title of the shortcut.
     * @param icon Image that represents the shortcut.
     * @return Intent for the shortcut.
     */
    public static Intent createAddToHomeIntent(String url, String title,
            Bitmap icon, Intent shortcutIntent) {
        Intent i = new Intent(INSTALL_SHORTCUT);
        i.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
        i.putExtra(Intent.EXTRA_SHORTCUT_NAME, title);
        i.putExtra(Intent.EXTRA_SHORTCUT_ICON, icon);
        return i;
    }

    /**
     * Creates an intent that will add a shortcut to the home screen.
     * @param url Url of the shortcut.
     * @param title Title of the shortcut.
     * @param icon Image that represents the shortcut.
     * @return Intent for the shortcut.
     */
    public static Intent createAddToHomeIntent(String url, String title, Bitmap icon) {
        Intent shortcutIntent = createShortcutIntent(url);
        return createAddToHomeIntent(url, title, icon, shortcutIntent);
    }

    /**
     * Shortcut intent for icon on homescreen.
     * @param url Url of the shortcut.
     * @return Intent for onclick action of the shortcut.
     */
    public static Intent createShortcutIntent(String url) {
        Intent shortcutIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        shortcutIntent.putExtra(REUSE_URL_MATCHING_TAB_ELSE_NEW_TAB, true);
        return shortcutIntent;
    }

    /**
     * Utility method to check if a shortcut can be added to the homescreen.
     * @param context Context used to get the package manager.
     * @return if a shortcut can be added to the homescreen under the current profile.
     */
    public static boolean isAddToHomeIntentSupported(Context context) {
        PackageManager pm = context.getPackageManager();
        Intent i = new Intent(INSTALL_SHORTCUT);
        List<ResolveInfo> receivers = pm.queryBroadcastReceivers(
                i, PackageManager.GET_INTENT_FILTERS);
        return !receivers.isEmpty();
    }

    /**
     * Creates an icon to be associated with this shortcut. If available, the touch icon
     * will be used, else we draw our own.
     * @param context Context used to create the intent.
     * @param icon Image representing the shortcut.
     * @param url URL of the shortcut.
     * @param rValue Red component of the dominant icon color.
     * @param gValue Green component of the dominant icon color.
     * @param bValue Blue component of the dominant icon color.
     * @return Bitmap Either the touch-icon or the newly created favicon.
     */
    public static Bitmap createLauncherIcon(Context context, Bitmap icon, String url, int rValue,
            int gValue, int bValue) {
        Bitmap bitmap = null;
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        final int iconSize = am.getLauncherLargeIconSize();
        final int iconDensity = am.getLauncherLargeIconDensity();
        try {
            bitmap = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            if (icon == null) {
                icon = getBitmapFromResourceId(context, R.drawable.globe_favicon, iconDensity);
                rValue = gValue = bValue = DEFAULT_RGB_VALUE;
            }
            final int smallestSide = iconSize;
            if (icon.getWidth() >= smallestSide / 2 && icon.getHeight() >= smallestSide / 2) {
                drawTouchIconToCanvas(context, icon, canvas);
            } else {
                drawWidgetBackgroundToCanvas(context, canvas, iconDensity, url,
                        Color.rgb(rValue, gValue, bValue));
            }
            canvas.setBitmap(null);
        } catch (OutOfMemoryError e) {
            Log.w(TAG, "OutOfMemoryError while trying to draw bitmap on canvas.");
        }
        return bitmap;
    }

    /**
     * Compresses a bitmap into a PNG and converts into a Base64 encoded string.
     * The encoded string can be decoded using {@link decodeBitmapFromString(String)}.
     * @param bitmap The Bitmap to compress and encode.
     * @return the String encoding the Bitmap.
     */
    public static String encodeBitmapAsString(Bitmap bitmap) {
        if (bitmap == null) return "";
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        return Base64.encodeToString(output.toByteArray(), Base64.DEFAULT);
    }

    /**
     * Decodes a Base64 string into a Bitmap. Used to decode Bitmaps encoded by
     * {@link encodeBitmapAsString(Bitmap)}.
     * @param encodedString the Base64 String to decode.
     * @return the Bitmap which was encoded by the String.
     */
    public static Bitmap decodeBitmapFromString(String encodedString) {
        if (TextUtils.isEmpty(encodedString)) return null;
        byte[] decoded = Base64.decode(encodedString, Base64.DEFAULT);
        return BitmapFactory.decodeByteArray(decoded, 0, decoded.length);
    }

    private static Bitmap getBitmapFromResourceId(Context context, int id, int density) {
        Drawable drawable = ApiCompatibilityUtils.getDrawableForDensity(
                context.getResources(), id, density);

        if (drawable instanceof BitmapDrawable) {
            BitmapDrawable bd = (BitmapDrawable) drawable;
            return bd.getBitmap();
        }
        assert false : "The drawable was not a bitmap drawable as expected";
        return null;
    }

    /**
     * Use touch-icon or higher-resolution favicon and round the corners.
     * @param context    Context used to get resources.
     * @param touchIcon  Touch icon bitmap.
     * @param canvas     Canvas that holds the touch icon.
     */
    private static void drawTouchIconToCanvas(Context context, Bitmap touchIcon, Canvas canvas) {
        Rect iconBounds = new Rect(0, 0, canvas.getWidth(), canvas.getHeight());
        Rect src = new Rect(0, 0, touchIcon.getWidth(), touchIcon.getHeight());
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setFilterBitmap(true);
        canvas.drawBitmap(touchIcon, src, iconBounds, paint);
        // Convert dp to px.
        int borderRadii = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                TOUCHICON_BORDER_RADII_DP, context.getResources().getDisplayMetrics());
        Path path = new Path();
        path.setFillType(Path.FillType.INVERSE_WINDING);
        RectF rect = new RectF(iconBounds);
        rect.inset(INSET_DIMENSION_FOR_TOUCHICON, INSET_DIMENSION_FOR_TOUCHICON);
        path.addRoundRect(rect, borderRadii, borderRadii, Path.Direction.CW);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        canvas.drawPath(path, paint);
    }

    /**
     * Draw document icon to canvas.
     * @param context     Context used to get bitmap resources.
     * @param canvas      Canvas that holds the document icon.
     * @param iconDensity Density information to get bitmap resources.
     * @param url         URL of the shortcut.
     * @param color       Color for the document icon's folding and the bottom strip.
     */
    private static void drawWidgetBackgroundToCanvas(
            Context context, Canvas canvas, int iconDensity, String url, int color) {
        Rect iconBounds = new Rect(0, 0, canvas.getWidth(), canvas.getHeight());
        Bitmap bookmarkWidgetBg =
                getBitmapFromResourceId(context, R.mipmap.bookmark_widget_bg, iconDensity);

        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(bookmarkWidgetBg, null, iconBounds, paint);

        float density = (float) iconDensity / DisplayMetrics.DENSITY_MEDIUM;
        int iconSize = (int) (GENERATED_ICON_SIZE_DP * density);
        int iconRoundedEdge = (int) (GENERATED_ICON_ROUNDED_CORNERS_DP * density);
        int iconFontSize = (int) (GENERATED_ICON_FONT_SIZE_DP * density);

        RoundedIconGenerator generator = new RoundedIconGenerator(
                iconSize, iconSize, iconRoundedEdge, color, iconFontSize);
        Bitmap icon = generator.generateIconForUrl(url);
        if (icon == null) return; // Bookmark URL does not have a domain.
        canvas.drawBitmap(icon, iconBounds.exactCenterX() - icon.getWidth() / 2.0f,
                iconBounds.exactCenterY() - icon.getHeight() / 2.0f, null);
    }

    /**
     * @return String that can be used to verify that a WebappActivity is being started by Chrome.
     */
    public static String getEncodedMac(Context context, String url) {
        // The only reason we convert to a String here is because Android inexplicably eats a
        // byte[] when adding the shortcut -- the Bundle received by the launched Activity even
        // lacks the key for the extra.
        byte[] mac = WebappAuthenticator.getMacForUrl(context, url);
        return Base64.encodeToString(mac, Base64.DEFAULT);
    }
}

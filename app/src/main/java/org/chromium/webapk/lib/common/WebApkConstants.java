// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.webapk.lib.common;

/**
 * Stores WebAPK related constants.
 */
public final class WebApkConstants {
    public static final String WEBAPK_PACKAGE_PREFIX = "org.chromium.webapk";

    // WebAPK id prefix. The id is used for storing WebAPK data in Chrome's SharedPreferences.
    public static final String WEBAPK_ID_PREFIX = "webapk:";

    // Used for sending Android Manifest properties to WebappLauncherActivity.
    public static final String EXTRA_WEBAPK_DISPLAY_MODE =
            "org.chromium.webapk.lib.common.webapk_display_mode";
    public static final String EXTRA_WEBAPK_ORIENTATION =
            "org.chromium.webapk.lib.common.webapk_orientation";

    // These EXTRA_* values must stay in sync with
    // {@link org.chromium.chrome.browser.ShortcutHelper}.
    public static final String EXTRA_ID = "org.chromium.chrome.browser.webapp_id";
    public static final String EXTRA_ICON = "org.chromium.chrome.browser.webapp_icon";
    public static final String EXTRA_SHORT_NAME = "org.chromium.chrome.browser.webapp_short_name";
    public static final String EXTRA_NAME = "org.chromium.chrome.browser.webapp_name";
    public static final String EXTRA_URL = "org.chromium.chrome.browser.webapp_url";
    public static final String EXTRA_SCOPE = "org.chromium.chrome.browser.webapp_scope";
    public static final String EXTRA_SOURCE = "org.chromium.chrome.browser.webapp_source";
    public static final String EXTRA_THEME_COLOR = "org.chromium.chrome.browser.theme_color";
    public static final String EXTRA_BACKGROUND_COLOR =
            "org.chromium.chrome.browser.background_color";
    public static final String EXTRA_IS_ICON_GENERATED =
            "org.chromium.chrome.browser.is_icon_generated";
    public static final String EXTRA_WEBAPK_PACKAGE_NAME =
            "org.chromium.chrome.browser.webapk_package_name";
    public static final String EXTRA_WEB_MANIFEST_URL =
            "org.chromium.chrome.browser.web_manifest_url";
}

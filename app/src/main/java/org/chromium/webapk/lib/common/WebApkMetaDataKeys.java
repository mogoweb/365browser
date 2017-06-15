// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.webapk.lib.common;

/**
 * <meta-data> keys for WebAPK Android Manifest.
 */
public final class WebApkMetaDataKeys {
    public static final String SHELL_APK_VERSION = "org.chromium.webapk.shell_apk.shellApkVersion";
    public static final String RUNTIME_HOST = "org.chromium.webapk.shell_apk.runtimeHost";
    public static final String START_URL = "org.chromium.webapk.shell_apk.startUrl";
    public static final String NAME = "org.chromium.webapk.shell_apk.name";
    public static final String SHORT_NAME = "org.chromium.webapk.shell_apk.shortName";
    public static final String SCOPE = "org.chromium.webapk.shell_apk.scope";
    public static final String DISPLAY_MODE = "org.chromium.webapk.shell_apk.displayMode";
    public static final String ORIENTATION = "org.chromium.webapk.shell_apk.orientation";
    public static final String THEME_COLOR = "org.chromium.webapk.shell_apk.themeColor";
    public static final String BACKGROUND_COLOR = "org.chromium.webapk.shell_apk.backgroundColor";
    public static final String ICON_ID = "org.chromium.webapk.shell_apk.iconId";
    // TODO(hanxi): crbug.com/665549. Remove {@link ICON_URL} and {@link ICON_MURMUR2_HASH}.
    public static final String ICON_URL = "org.chromium.webapk.shell_apk.iconUrl";
    public static final String ICON_MURMUR2_HASH = "org.chromium.webapk.shell_apk.iconMurmur2Hash";

    public static final String ICON_URLS_AND_ICON_MURMUR2_HASHES =
            "org.chromium.webapk.shell_apk.iconUrlsAndIconMurmur2Hashes";
    public static final String WEB_MANIFEST_URL = "org.chromium.webapk.shell_apk.webManifestUrl";
}

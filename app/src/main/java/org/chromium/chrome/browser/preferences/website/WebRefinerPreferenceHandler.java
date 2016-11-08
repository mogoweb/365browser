/*
 * Copyright (c) 2015, The Linux Foundation. All rights reserved.
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

package org.chromium.chrome.browser.preferences.website;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.StrictMode;
import android.os.StrictMode.ThreadPolicy;
import android.util.Log;

import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.content.browser.WebRefiner;
import org.chromium.content.browser.WebRefinerListener;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Handler for webrefiner that deals with initializing and handling webrefiner
 * related settings
 */
public class WebRefinerPreferenceHandler {
    private static final String WEB_REFINER_CONFIG_RAW_RES_NAME = "web_refiner_conf";
    private static boolean mWebRefinerInitializationComplete = false;

    private static boolean mWebRefinerSetupComplete = false;
    private static HashMap<String, ContentSetting> mIncognitoPermissions;

    private static String readConfigurationFromRawRes(Context ctx, final String rawResFileName) {
        StringBuilder configString = new StringBuilder();
        boolean result = true;
        try {
            InputStream is = null;
            try {
                Resources res = ctx.getResources();
                int id = res.getIdentifier(rawResFileName, "raw", ctx.getPackageName());

                if (id == 0) {
                    //Let's try to get the correct package name using ApplicationInfo.icon identifier.
                    ApplicationInfo appInfo = ctx.getPackageManager().getApplicationInfo(ctx.getPackageName(), PackageManager.GET_META_DATA);
                    String packageName = res.getResourcePackageName(appInfo.icon);
                    id = res.getIdentifier(rawResFileName, "raw", packageName);
                }
                if (id != 0) {
                    is = res.openRawResource(id);
                    byte[] buffer = new byte[1024];
                    int readLength;
                    while ((readLength = is.read(buffer)) != -1) {
                        configString.append(new String(buffer, 0, readLength));
                    }
                    Log.d(WebRefiner.LOGTAG, "Copied configuration from res/raw/" + rawResFileName);
                } else {
                    result = false;
                    Log.e(WebRefiner.LOGTAG, "Configuration resource 'res/raw/" + rawResFileName + "' not found !");
                }
            } finally {
                if (is != null) {
                    is.close();
                }
            }
        } catch (Exception e) {
            Log.e(WebRefiner.LOGTAG, e.getMessage());
            result = false;
        }
        return configString.toString();
    }

    static public void initializeGlobalInstance(Context ctx) {
        if (!mWebRefinerInitializationComplete) {
            final StrictMode.ThreadPolicy oldPolicy = StrictMode.getThreadPolicy();
            ThreadPolicy tmpPolicy = new ThreadPolicy.Builder(oldPolicy)
                               .permitDiskReads()
                               .permitDiskWrites()
                               .build();
            StrictMode.setThreadPolicy(tmpPolicy);

            mWebRefinerInitializationComplete = true;

            String configuration = readConfigurationFromRawRes(ctx, WEB_REFINER_CONFIG_RAW_RES_NAME);
            if (configuration != null) {
                WebRefiner.Initialize(ctx, configuration);
            } else {
                // Let the initialization deliberately fail.
                WebRefiner.Initialize(ctx, "none");
            }

            if (!WebRefiner.isInitialized()) {
                Log.e(WebRefiner.LOGTAG, "Failed to initialize WebRefiner instance !");
            } else {
                WebRefiner.getInstance().setListener(new WebRefinerListener() {
                    @Override
                    public void onPageSessionEnded(String url, long downloadedDataSize, int urlsRequested, int urlsBlocked) {
                        //Log.d(WebRefiner.LOGTAG, "WebRefinerListener.onPageSessionEnded : Page [" + url + "] downloaded : " + downloadedDataSize
                        //        + " bytes, requested " + urlsRequested + " URLs and blocked " + urlsBlocked + " URLs");
                    }
                });
            }
            StrictMode.setThreadPolicy(oldPolicy);
        }
    }

    static public void applyInitialPreferences() {
        if (WebRefiner.isInitialized() && !mWebRefinerSetupComplete) {

            boolean allowed = PrefServiceBridge.getInstance().isWebRefinerEnabled();
            WebRefiner.getInstance().setDefaultPermission(allowed);

            WebsitePermissionsFetcher fetcher = new WebsitePermissionsFetcher(
                    new WebsitePermissionsFetcher.WebsitePermissionsCallback() {
                @Override
                public void onWebsitePermissionsAvailable(
                                Collection<Website> websites) {
                    ArrayList<String> allowList = new ArrayList<>();
                    ArrayList<String> blockList = new ArrayList<>();

                    for (Website site : websites) {
                        ContentSetting permission = site.getWebRefinerPermission();
                        if (permission != null) {
                            if (permission == ContentSetting.ALLOW) {
                                allowList.add(site.getAddress().getOrigin());
                            } else if (permission == ContentSetting.BLOCK) {
                                blockList.add(site.getAddress().getOrigin());
                            }
                        }
                    }
                    if (!allowList.isEmpty()) {
                        WebRefiner.getInstance().setPermissionForOrigins(
                                allowList.toArray(new String[allowList.size()]), WebRefiner.PERMISSION_ENABLE, false);
                    }

                    if (!blockList.isEmpty()) {
                        WebRefiner.getInstance().setPermissionForOrigins(
                                blockList.toArray(new String[blockList.size()]), WebRefiner.PERMISSION_DISABLE, false);
                    }
                    mWebRefinerSetupComplete = true;
                }
            }
            );
            fetcher.fetchPreferencesForCategory(SiteSettingsCategory.fromString(SiteSettingsCategory
                    .CATEGORY_WEBREFINER));
        }
    }

    static public void setWebRefinerEnabled(boolean enabled) {
        if (!WebRefiner.isInitialized()) return;
        WebRefiner.getInstance().setDefaultPermission(enabled);
    }

    static public void setWebRefinerSettingForOrigin(String origin,
                                                     boolean enabled, boolean isIncognito) {
        if (!WebRefiner.isInitialized()) return;
        String[] origins = new String[1];
        origins[0] = origin;
        int permission = enabled ? WebRefiner.PERMISSION_ENABLE : WebRefiner.PERMISSION_DISABLE;
        WebRefiner.getInstance().setPermissionForOrigins(origins, permission, isIncognito);
    }

    public static int getBlockedURLCount(ContentViewCore contentViewCore) {
        if (!WebRefiner.isInitialized()) return 0;
        return WebRefiner.getInstance().getBlockedURLCount(contentViewCore);
    }

    public static WebRefiner.PageInfo getPageInfo(ContentViewCore contentViewCore) {
        if (!WebRefiner.isInitialized()) return null;
        return WebRefiner.getInstance().getPageInfo(contentViewCore);
    }

    public static void useDefaultPermissionForOrigins(String origin, boolean isIncognito) {
        if (!WebRefiner.isInitialized()) return;
        String[] origins = new String[1];
        origins[0] = origin;
        WebRefiner.getInstance()
                .setPermissionForOrigins(origins, WebRefiner.PERMISSION_USE_DEFAULT, isIncognito);
    }

    public static boolean isInitialized() {
        return WebRefiner.isInitialized();
    }

    public static void addIncognitoOrigin(String origin, ContentSetting permission) {
        setWebRefinerSettingForOrigin(origin, permission == ContentSetting.ALLOW, true);
        if (mIncognitoPermissions == null) {
            mIncognitoPermissions = new HashMap<>();
        }
        mIncognitoPermissions.put(origin, permission);
    }

    public static ContentSetting getSettingForIncognitoOrigin(String origin) {

        if (mIncognitoPermissions != null && mIncognitoPermissions.containsKey(origin)) {
            return mIncognitoPermissions.get(origin);
        }
        return null;
    }

    public static void clearIncognitoOrigin(String origin) {
        if (mIncognitoPermissions != null && mIncognitoPermissions.containsKey(origin)) {
            mIncognitoPermissions.remove(origin);
            useDefaultPermissionForOrigins(origin, true);
        }
    }

    public static void onIncognitoSessionFinish() {
        mIncognitoPermissions = null;
        if (WebRefiner.isInitialized())
            WebRefiner.getInstance().resetAllIncognitoPermissions();
    }
}

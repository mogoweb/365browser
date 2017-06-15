// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.webapps;

import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;

import org.chromium.base.ContextUtils;
import org.chromium.base.library_loader.LibraryProcessType;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.base.process_launcher.ChildProcessCreationParams;
import org.chromium.chrome.browser.externalnav.ExternalNavigationParams;
import org.chromium.chrome.browser.metrics.WebApkUma;
import org.chromium.chrome.browser.tab.BrowserControlsVisibilityDelegate;
import org.chromium.chrome.browser.tab.InterceptNavigationDelegateImpl;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabDelegateFactory;
import org.chromium.chrome.browser.tab.TabRedirectHandler;
import org.chromium.components.navigation_interception.NavigationParams;
import org.chromium.webapk.lib.client.WebApkServiceConnectionManager;

import java.util.concurrent.TimeUnit;

/**
 * An Activity is designed for WebAPKs (native Android apps) and displays a webapp in a nearly
 * UI-less Chrome.
 */
public class WebApkActivity extends WebappActivity {
    /** Manages whether to check update for the WebAPK, and starts update check if needed. */
    private WebApkUpdateManager mUpdateManager;

    /** Indicates whether launching renderer in WebAPK process is enabled. */
    private boolean mCanLaunchRendererInWebApkProcess;

    private final ChildProcessCreationParams mDefaultParams =
            ChildProcessCreationParams.getDefault();

    /** The start time that the activity becomes focused. */
    private long mStartTime;

    @Override
    protected WebappInfo createWebappInfo(Intent intent) {
        return (intent == null) ? WebApkInfo.createEmpty() : WebApkInfo.create(intent);
    }

    @Override
    protected void initializeUI(Bundle savedInstance) {
        super.initializeUI(savedInstance);
        getActivityTab().setWebappManifestScope(mWebappInfo.scopeUri().toString());
    }

    @Override
    protected TabDelegateFactory createTabDelegateFactory() {
        return new WebappDelegateFactory(this) {
            @Override
            public InterceptNavigationDelegateImpl createInterceptNavigationDelegate(Tab tab) {
                return new InterceptNavigationDelegateImpl(tab) {
                    @Override
                    public ExternalNavigationParams.Builder buildExternalNavigationParams(
                            NavigationParams navigationParams,
                            TabRedirectHandler tabRedirectHandler, boolean shouldCloseTab) {
                        ExternalNavigationParams.Builder builder =
                                super.buildExternalNavigationParams(
                                        navigationParams, tabRedirectHandler, shouldCloseTab);
                        builder.setWebApkPackageName(getWebApkPackageName());
                        return builder;
                    }
                };
            }

            @Override
            public boolean canShowAppBanners(Tab tab) {
                // Do not show app banners for WebAPKs regardless of the current page URL.
                // A WebAPK can display a page outside of its WebAPK scope if a page within the
                // WebAPK scope navigates via JavaScript while the WebAPK is in the background.
                return false;
            }

            @Override
            public BrowserControlsVisibilityDelegate createBrowserControlsVisibilityDelegate(
                    Tab tab) {
                return new WebApkBrowserControlsDelegate(WebApkActivity.this, tab);
            }
        };
    }

    @Override
    public void finishNativeInitialization() {
        super.finishNativeInitialization();
        if (!isInitialized()) return;
        mCanLaunchRendererInWebApkProcess = ChromeWebApkHost.canLaunchRendererInWebApkProcess();
    }

    @Override
    public void onStop() {
        super.onStop();
        WebApkServiceConnectionManager.getInstance().disconnect(
                ContextUtils.getApplicationContext(), getWebApkPackageName());
    }

    @Override
    public void onStopWithNative() {
        super.onStopWithNative();
        if (mUpdateManager != null && mUpdateManager.requestPendingUpdate()) {
            WebApkUma.recordUpdateRequestSent(WebApkUma.UPDATE_REQUEST_SENT_ONSTOP);
        }
    }

    /**
     * Returns the WebAPK's package name.
     */
    public String getWebApkPackageName() {
        return getWebappInfo().webApkPackageName();
    }

    @Override
    public void onResumeWithNative() {
        super.onResumeWithNative();

        // When launching Chrome renderer in WebAPK process is enabled, WebAPK hosts Chrome's
        // renderer processes by declaring the Chrome's renderer service in its AndroidManifest.xml
        // and sets {@link ChildProcessCreationParams} for WebAPK's renderer process so the
        // {@link ChildProcessLauncher} knows which application's renderer service to connect to.
        initializeChildProcessCreationParams(mCanLaunchRendererInWebApkProcess);
    }

    @Override
    public void onResume() {
        super.onResume();
        mStartTime = SystemClock.elapsedRealtime();
    }

    @Override
    protected void recordIntentToCreationTime(long timeMs) {
        super.recordIntentToCreationTime(timeMs);

        RecordHistogram.recordTimesHistogram(
                "MobileStartup.IntentToCreationTime.WebApk", timeMs, TimeUnit.MILLISECONDS);
    }

    @Override
    protected void onDeferredStartupWithStorage(WebappDataStorage storage) {
        super.onDeferredStartupWithStorage(storage);

        WebApkInfo info = (WebApkInfo) mWebappInfo;
        WebApkUma.recordShellApkVersion(info.shellApkVersion(), info.webApkPackageName());

        mUpdateManager = new WebApkUpdateManager(WebApkActivity.this, storage);
        mUpdateManager.updateIfNeeded(getActivityTab(), info);
    }

    @Override
    protected void onDeferredStartupWithNullStorage() {
        super.onDeferredStartupWithNullStorage();

        // Register the WebAPK. The WebAPK was registered when it was created, but may also become
        // unregistered after a user clears Chrome's data.
        WebappRegistry.getInstance().register(
                mWebappInfo.id(), new WebappRegistry.FetchWebappDataStorageCallback() {
                    @Override
                    public void onWebappDataStorageRetrieved(WebappDataStorage storage) {
                        // Initialize the time of the last is-update-needed check with the
                        // registration time. This prevents checking for updates on the first run.
                        storage.updateTimeOfLastCheckForUpdatedWebManifest();

                        onDeferredStartupWithStorage(storage);
                    }
                });
    }

    @Override
    public void onPause() {
        super.onPause();
        initializeChildProcessCreationParams(false);
    }

    @Override
    public void onPauseWithNative() {
        WebApkUma.recordWebApkSessionDuration(SystemClock.elapsedRealtime() - mStartTime);
        super.onPauseWithNative();
    }

    /**
     * Initializes {@link ChildProcessCreationParams} as a WebAPK's renderer process if
     * {@link isForWebApk}} is true; as Chrome's child process otherwise.
     * @param isForWebApk: Whether the {@link ChildProcessCreationParams} is initialized as a
     *                     WebAPK renderer process.
     */
    private void initializeChildProcessCreationParams(boolean isForWebApk) {
        // TODO(hanxi): crbug.com/664530. WebAPKs shouldn't use a global ChildProcessCreationParams.
        ChildProcessCreationParams params = mDefaultParams;
        if (isForWebApk) {
            boolean isExternalService = false;
            boolean bindToCaller = false;
            params = new ChildProcessCreationParams(getWebappInfo().webApkPackageName(),
                    isExternalService, LibraryProcessType.PROCESS_CHILD, bindToCaller);
        }
        ChildProcessCreationParams.registerDefault(params);
    }

    @Override
    protected void onDestroyInternal() {
        if (mUpdateManager != null) {
            mUpdateManager.destroy();
        }
        super.onDestroyInternal();
    }
}

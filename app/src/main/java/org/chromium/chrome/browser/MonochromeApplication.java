// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import com.android.webview.chromium.MonochromeLibraryPreloader;

import org.chromium.base.library_loader.LibraryLoader;
import org.chromium.base.library_loader.LibraryProcessType;
import org.chromium.base.process_launcher.ChildProcessCreationParams;

/**
 * This is Application class for Monochrome.
 *
 * You shouldn't add anything else in this file, this class is split off from
 * normal chrome in order to access Android system API through Android WebView
 * glue layer and have monochrome specific code.
 */
public class MonochromeApplication extends ChromeApplication {
    @Override
    public void onCreate() {
        super.onCreate();
        LibraryLoader.setNativeLibraryPreloader(new MonochromeLibraryPreloader());
        // ChildProcessCreationParams is only needed for browser process, though it is
        // created and set in all processes.
        boolean bindToCaller = false;
        ChildProcessCreationParams.registerDefault(new ChildProcessCreationParams(getPackageName(),
                true /* isExternalService */, LibraryProcessType.PROCESS_CHILD, bindToCaller));
    }
}

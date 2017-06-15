// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.vr_shell;

import android.app.Activity;
import android.content.Context;

import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;

/**
 * Abstracts away the VrClassesWrapperImpl class, which may or may not be present at runtime
 * depending on compile flags.
 */
public interface VrClassesWrapper {
    /**
     * Creates a NonPresentingGvrContextImpl instance.
     */
    public NonPresentingGvrContext createNonPresentingGvrContext(ChromeActivity activity);

    /**
     * Creates a VrShellImpl instance.
     */
    public VrShell createVrShell(
            ChromeActivity activity, VrShellDelegate delegate, TabModelSelector tabModelSelector);

    /**
     * Creates a VrDaydreamApImpl instance.
     */
    public VrDaydreamApi createVrDaydreamApi(Activity activity);

    /**
     * Creates a VrDaydreamApImpl instance.
     */
    @VisibleForTesting
    public VrDaydreamApi createVrDaydreamApi(Context context);

    /**
    * Creates a VrCoreVersionCheckerImpl instance.
    */
    public VrCoreVersionChecker createVrCoreVersionChecker();

    /**
     * Sets VR Mode to |enabled|.
     */
    public void setVrModeEnabled(Activity activity, boolean enabled);
}

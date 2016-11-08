// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.mojo;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.browser.payments.PaymentRequestFactory;
import org.chromium.chrome.browser.webshare.ShareServiceImplementationFactory;
import org.chromium.content.browser.InterfaceRegistry;
import org.chromium.content_public.browser.WebContents;
import org.chromium.mojom.payments.PaymentRequest;
import org.chromium.mojom.webshare.ShareService;

/**
 * Registers interfaces exposed by Chrome in the given registry.
 */
class ChromeInterfaceRegistrar {
    @CalledByNative
    private static void exposeInterfacesToFrame(
            InterfaceRegistry registry, WebContents webContents) {
        registry.addInterface(PaymentRequest.MANAGER, new PaymentRequestFactory(webContents));
        registry.addInterface(
                ShareService.MANAGER, new ShareServiceImplementationFactory(webContents));
    }
}

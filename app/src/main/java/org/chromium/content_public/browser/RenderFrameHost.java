// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content_public.browser;

import org.chromium.services.service_manager.InterfaceProvider;

/**
 * The RenderFrameHost Java wrapper to allow communicating with the native RenderFrameHost object.
 *
 */
public interface RenderFrameHost {
    /**
     * Get the last committed URL of the frame.
     *
     * @return The last committed URL of the frame.
     */
    String getLastCommittedURL();

    /**
     * Returns an InterfaceProvider that provides access to interface implementations provided by
     * the corresponding RenderFrame. This provides access to interfaces implemented in the renderer
     * to Java code in the browser process.
     *
     * @return The InterfaceProvider for the frame.
     */
    InterfaceProvider getRemoteInterfaces();
}

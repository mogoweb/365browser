// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content_public.browser;

/**
 * Interface for the classes that need to be notified of IME changes.
 */
public interface ImeEventObserver {
    /**
     * Called to notify the delegate about synthetic/real key events before sending to renderer.
     */
    void onImeEvent();

    /**
     * Called when the focused node attribute is updated.
     * @param editable {@code true} if the node becomes editable; else {@code false}.
     * @param password indicates the node is of type password if {@code true}.
     */
    void onNodeAttributeUpdated(boolean editable, boolean password);
}

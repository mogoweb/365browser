// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.payments.ui;

/** The interface for editor fields that handle validation and display of errors. */
interface Validatable {
    /**
     * Updates the error display.
     *
     * @param showError If true, displays the error message.  If false, clears it.
     */
    void updateDisplayedError(boolean showError);

    /** @return True if this field is valid. */
    boolean isValid();

    /** Scrolls to and focuses the field to bring user's attention to it. */
    void scrollToAndFocus();
}

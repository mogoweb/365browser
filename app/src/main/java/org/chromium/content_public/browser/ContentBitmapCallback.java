// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content_public.browser;

import android.graphics.Bitmap;

/**
* An interface used to get notified of the completion of content bitmap acquisition.
*/
public interface ContentBitmapCallback {
    /**
     * Called when bitmap version of the content is acquired.
     *
     * @param bitmap content snapshot in the format of {@link Bitmap}, or an empty
     *        bitmap if the operation failed.
     * @param response one of response types as defined in
     *        {@link org.chromium.content_public.browser.readback_types.ReadbackResponse}
     */
    void onFinishGetBitmap(Bitmap bitmap, int response);
}

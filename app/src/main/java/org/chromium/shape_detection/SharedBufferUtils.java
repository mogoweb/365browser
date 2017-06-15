// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.shape_detection;

import android.graphics.Bitmap;

import com.google.android.gms.vision.Frame;

import org.chromium.mojo.system.MojoException;
import org.chromium.mojo.system.SharedBufferHandle;
import org.chromium.mojo.system.SharedBufferHandle.MapFlags;

import java.nio.ByteBuffer;

/**
 * Utility class to convert a SharedBufferHandle to a GMS core YUV Frame.
 */
public class SharedBufferUtils {
    public static Frame convertToFrame(
            SharedBufferHandle frameData, final int width, final int height) {
        final long numPixels = (long) width * height;
        // TODO(mcasas): https://crbug.com/670028 homogeneize overflow checking.
        if (!frameData.isValid() || width <= 0 || height <= 0 || numPixels > (Long.MAX_VALUE / 4)) {
            return null;
        }

        // Mapping |frameData| will fail if the intended mapped size is larger
        // than its actual capacity, which is limited by the appropriate
        // mojo::edk::Configuration entry.
        ByteBuffer imageBuffer = frameData.map(0, numPixels * 4, MapFlags.none());
        if (imageBuffer.capacity() <= 0) {
            return null;
        }

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(imageBuffer);
        try {
            frameData.unmap(imageBuffer);
            frameData.close();
        } catch (MojoException e) {
        }

        try {
            // This constructor implies a pixel format conversion to YUV.
            return new Frame.Builder().setBitmap(bitmap).build();
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return null;
        }
    }
}

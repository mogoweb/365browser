// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.components.precache;

import android.content.Context;

/**
 * Factory for creating network info delegates.
 */
public class NetworkInfoDelegateFactory {
    NetworkInfoDelegate getNetworkInfoDelegate(Context context) {
        return new NetworkInfoDelegate(context);
    }
}

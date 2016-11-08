// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.webapk.lib.runtime_library;

import android.app.Notification;

/**
 * Interface for communicating between WebAPK service and Chrome.
 */
interface IWebApkApi {
    // Gets the id of the icon to represent WebAPK notifications in status bar.
    int getSmallIconId();

    // Display a notification.
    void notifyNotification(String platformTag, int platformID, in Notification notification);

    // Cancel a notification.
    void cancelNotification(String platformTag, int platformID);
}

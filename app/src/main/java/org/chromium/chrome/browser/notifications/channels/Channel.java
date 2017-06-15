// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.notifications.channels;

/**
 * Helper class that corresponds to the Android NotificationChannel class,
 * which we cannot use directly until our compileSdkVersion is bumped to O.
 *
 * Only the methods & properties we use have been added, others may be added if the need arises.
 */
public class Channel {
    private final String mId;
    private final CharSequence mName;
    private final int mImportance;
    private final String mGroupId;

    public Channel(String id, CharSequence name, int importance, String groupId) {
        mId = id;
        mName = name;
        mImportance = importance;
        mGroupId = groupId;
    }

    public String getId() {
        return mId;
    }

    public CharSequence getName() {
        return mName;
    }

    public int getImportance() {
        return mImportance;
    }

    public String getGroupId() {
        return mGroupId;
    }
}
// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.website;

/**
 * Java counterpart to C++ ContentSetting enum.
 *
 * TODO(newt): Reconcile this class with the less-capable auto-generated ContentSetting class
 * (org.chromium.chrome.browser.ContentSetting), once this has been upstreamed.
 */
public enum ContentSetting {
    DEFAULT(0),
    ALLOW(1),
    BLOCK(2),
    ASK(3);  // Only used for default values.

    private int mValue;

    /**
     * Converts the enum value to int.
     */
    public int toInt() {
        return mValue;
    }

    /**
     * Converts a ContentSetting to its equivalent C++ integer enum value.
     * @param v The enum to convert.
     * @return The int value represented by the ContentSetting, or -1 if null is passed in.
     */
    public static int toInt(ContentSetting v) {
        if (v == null) return -1;
        return v.mValue;
    }

    /**
     * Converts an int to its equivalent ContentSetting.
     * @param i The integer to convert.
     * @return What value the enum is representing (or null if failed).
     */
    public static ContentSetting fromInt(int i) {
        for (ContentSetting enumValue : ContentSetting.values()) {
            if (enumValue.toInt() == i) return enumValue;
        }
        return null;
    }

    ContentSetting(int value) {
        this.mValue = value;
    }

    /**
     * Converts a string value to one of the enum values.
     * @param value The string representation of the internal value to use.
     * @return The enum the string represents, or WEBSITE_SETTINGS_INVALID if
     *         the conversion failed.
     */
    public static ContentSetting fromString(String value) {
        int parsed = Integer.parseInt(value);
        for (ContentSetting enumValue : ContentSetting.values()) {
            if (enumValue.toInt() == parsed) return enumValue;
        }

        return null;
    }

    /**
     * Converts the enum value to string.
     * @return The string that the enum represents.
     */
    @Override
    public String toString() {
        return String.valueOf(mValue);
    }
}
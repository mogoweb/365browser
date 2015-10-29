// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.website;

import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;

import java.io.Serializable;

/**
 * Exception information for a given origin.
 */
@SuppressFBWarnings("NM_CLASS_NOT_EXCEPTION")
public class ContentSettingException implements Serializable {
    private final int mContentSettingType;
    private final String mPattern;
    private final String mSetting;
    private final String mSource;

    /**
     * Construct a ContentSettingException.
     * @param type The content setting type this exception covers.
     * @param pattern The host/domain pattern this exception covers.
     * @param source The source for this exception, e.g. "policy".
     */
    public ContentSettingException(
            int type, String pattern, String setting, String source) {
        mContentSettingType = type;
        mPattern = pattern;
        mSetting = setting;
        mSource = source;
    }

    public String getPattern() {
        return mPattern;
    }

    public String getSetting() {
        return mSetting;
    }

    public String getSource() {
        return mSource;
    }

    /**
     * Returns the content setting value for this pattern, if one exists.
     */
    public ContentSetting getContentSetting() {
        if (mSetting.equals(PrefServiceBridge.EXCEPTION_SETTING_ALLOW)) {
            return ContentSetting.ALLOW;
        } else if (mSetting.equals(PrefServiceBridge.EXCEPTION_SETTING_BLOCK)) {
            return ContentSetting.BLOCK;
        } else {
            return null;
        }
    }

    /**
     * Sets the content setting value for this pattern.
     */
    public void setContentSetting(ContentSetting value) {
        if (value != null) {
            PrefServiceBridge.getInstance().nativeSetContentSettingForPattern(
                    mContentSettingType, mPattern, value == ContentSetting.ALLOW
                            ? ContentSetting.ALLOW.toInt()
                            : ContentSetting.BLOCK.toInt());
        } else {
            PrefServiceBridge.getInstance().nativeSetContentSettingForPattern(
                    mContentSettingType, mPattern, ContentSetting.DEFAULT.toInt());
        }
    }
}

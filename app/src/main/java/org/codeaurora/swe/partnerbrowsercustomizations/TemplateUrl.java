/*
* Copyright (c) 2016, The Linux Foundation. All rights reserved.
*
* Redistribution and use in source and binary forms, with or without
* modification, are permitted provided that the following conditions are
* met:
*     * Redistributions of source code must retain the above copyright
*       notice, this list of conditions and the following disclaimer.
*     * Redistributions in binary form must reproduce the above
*       copyright notice, this list of conditions and the following
*       disclaimer in the documentation and/or other materials provided
*       with the distribution.
*     * Neither the name of The Linux Foundation nor the names of its
*       contributors may be used to endorse or promote products derived
*       from this software without specific prior written permission.
*
* THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
* WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
* MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
* ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
* BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
* CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
* SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
* BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
* WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
* OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
* IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package org.codeaurora.swe.partnerbrowsercustomizations;

import android.text.TextUtils;

public class TemplateUrl {
    private final int mIndex;
    private final String mShortName;
    private final String mKeyword;
    private final String mFaviconUrl;
    private final String mSearchURL;
    private final boolean mDefault;
    private final boolean mDefaultIncognito;


    public TemplateUrl(int index, String shortName, String keyword,
                       String searchURL, String faviconUrl, int isDefault, int isDefaultIncognito) {
        mIndex = index;
        mShortName = shortName;
        mKeyword = keyword;
        mSearchURL = searchURL;
        mFaviconUrl = faviconUrl;
        mDefault = (isDefault == 1);
        mDefaultIncognito = (isDefaultIncognito == 1);
    }

    public int getIndex() {
        return mIndex;
    }

    public String getShortName() {
        return mShortName;
    }

    public String getKeyword() {
        return mKeyword;
    }

    public String getSearchURL() {
        return mSearchURL;
    }

    public String getFaviconUrl() {
        return mFaviconUrl;
    }

    public Boolean isDefault() {
        return mDefault;
    }

    public Boolean isDefaultIncognito() {
        return mDefaultIncognito;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + mIndex;
        result = prime * result + ((mKeyword == null) ? 0 : mKeyword.hashCode());
        result = prime * result + ((mShortName == null) ? 0 : mShortName.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof TemplateUrl)) return false;
        TemplateUrl otherTemplateUrl = (TemplateUrl) other;
        return mIndex == otherTemplateUrl.mIndex
                && TextUtils.equals(mShortName, otherTemplateUrl.mShortName)
                && TextUtils.equals(mKeyword, otherTemplateUrl.mKeyword)
                && TextUtils.equals(mSearchURL, otherTemplateUrl.mSearchURL);
    }
}
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

import java.util.ArrayList;

class Bookmark {
    /** Root bookmark id reserved for the implied root of the bookmarks */
    public static final long ROOT_FOLDER_ID = 0;

    /** ID used to indicate an invalid bookmark node. */
    static final long INVALID_BOOKMARK_ID = -1;

    // To be provided by the bookmark extractors.
    /** Local id of the read bookmark */
    long mId;
    /** Read id of the parent node */
    long mParentId;
    /** True if it's folder */
    boolean mIsFolder;
    /** URL of the bookmark. Required for non-folders. */
    String mUrl;
    /** Title of the bookmark. */
    String mTitle;
    /** .PNG Favicon of the bookmark. Optional. Not used for folders. */
    byte[] mFavicon;
    /** .PNG TouchIcon of the bookmark. Optional. Not used for folders. */
    byte[] mTouchicon;

    /** The parent node if any */
    Bookmark mParent;
    ArrayList<Bookmark> mEntries = new ArrayList<Bookmark>();

    public long getId() {
        return mId;
    }

    public void setId(long id) {
        this.mId = id;
    }

    public long getParentId() {
        return mParentId;
    }

    public void setParentId(long parentId) {
        this.mParentId = parentId;
    }

    public boolean isFolder() {
        return mIsFolder;
    }

    public void setFolder(boolean folder) {
        this.mIsFolder = folder;
    }

    public String getUrl() {
        return mUrl;
    }

    public void setUrl(String url) {
        this.mUrl = url;
    }

    public String getTitle() {
        return mTitle;
    }

    public void setTitle(String title) {
        this.mTitle = title;
    }

    public byte[] getFavicon() {
        return mFavicon;
    }

    public void setFavicon(byte[] favicon) {
        this.mFavicon = favicon;
    }

    public byte[] getTouchicon() {
        return mTouchicon;
    }

    public void setTouchicon(byte[] touchicon) {
        this.mTouchicon = touchicon;
    }

    public Bookmark getParent() {
        return mParent;
    }

    public void setParent(Bookmark parent) {
        this.mParent = parent;
    }

    public ArrayList<Bookmark> getEntries() {
        return mEntries;
    }

    public void setEntries(ArrayList<Bookmark> entries) {
        this.mEntries = entries;
    }
}

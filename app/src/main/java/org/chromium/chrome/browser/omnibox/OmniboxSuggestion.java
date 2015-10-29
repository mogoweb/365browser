// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.omnibox;

import android.text.TextUtils;

import org.chromium.base.VisibleForTesting;

/**
 * Container class with information about each omnibox suggestion item.
 */
@VisibleForTesting
public class OmniboxSuggestion {

    private final Type mType;
    private final String mDisplayText;
    private final String mDescription;
    private final String mAnswerContents;
    private final String mAnswerType;
    private final SuggestionAnswer mAnswer;
    private final String mFillIntoEdit;
    private final String mUrl;
    private final String mFormattedUrl;
    private final int mRelevance;
    private final int mTransition;
    private final boolean mIsStarred;
    private final boolean mIsDeletable;

    /**
     * This should be kept in sync with AutocompleteMatch::Type
     * (see components/omnibox/autocomplete_match_type.h).
     * Negative types are specific to Chrome on Android front-end.
     */
    public static enum Type {
        VOICE_SUGGEST        (-100), // A suggested search from the voice recognizer.

        URL_WHAT_YOU_TYPED    (0),   // The input as a URL.
        HISTORY_URL           (1),   // A past page whose URL contains the input.
        HISTORY_TITLE         (2),   // A past page whose title contains the input.
        HISTORY_BODY          (3),   // A past page whose body contains the input.
        HISTORY_KEYWORD       (4),   // A past page whose keyword contains the input.
        NAVSUGGEST            (5),   // A suggested URL.
        SEARCH_WHAT_YOU_TYPED (6),   // The input as a search query (with the default
                                     // engine).
        SEARCH_HISTORY        (7),   // A past search (with the default engine)
                                     // containing the input.
        SEARCH_SUGGEST        (8),   // A suggested search (with the default engine).
        SEARCH_SUGGEST_ENTITY (9),   // A suggested search for an entity.
        SEARCH_SUGGEST_TAIL   (10),  // A suggested search (with the default engine)
                                     // to complete the tail part of the input.
        SEARCH_SUGGEST_PERSONALIZED (11), // A personalized suggested search.
        SEARCH_SUGGEST_PROFILE (12), // A personalized suggested search for a
                                     // Google+ profile.
        SEARCH_OTHER_ENGINE   (13),  // A search with a non-default engine.
        OPEN_HISTORY_PAGE     (17);  // A synthetic result that opens the history page
                                     // to search for the input.

        private final int mNativeType;

        Type(int nativeType) {
            mNativeType = nativeType;
        }

        static Type getTypeFromNativeType(int nativeType) {
            for (Type t : Type.values()) {
                if (t.mNativeType == nativeType) return t;
            }

            return URL_WHAT_YOU_TYPED;
        }

        public boolean isHistoryUrl() {
            return this == HISTORY_URL || this == HISTORY_TITLE
                    || this == HISTORY_BODY || this == HISTORY_KEYWORD;
        }

        public boolean isUrl() {
            return this == URL_WHAT_YOU_TYPED || this.isHistoryUrl() || this == NAVSUGGEST;
        }

        /**
         * @return The ID of the type used by the native code.
         */
        public int nativeType() {
            return mNativeType;
        }
    }

    public OmniboxSuggestion(int nativeType, int relevance, int transition,
            String text, String description, String answerContents,
            String answerType, String fillIntoEdit, String url,
            String formattedUrl, boolean isStarred, boolean isDeletable) {
        mType = Type.getTypeFromNativeType(nativeType);
        mRelevance = relevance;
        mTransition = transition;
        mDisplayText = text;
        mDescription = description;
        mAnswerContents = answerContents;
        mAnswerType = answerType;
        mFillIntoEdit = TextUtils.isEmpty(fillIntoEdit) ? text : fillIntoEdit;
        mUrl = url;
        mFormattedUrl = formattedUrl;
        mIsStarred = isStarred;
        mIsDeletable = isDeletable;

        if (!TextUtils.isEmpty(mAnswerContents)) {
            // If any errors are encountered parsing the answer contents, this will return null and
            // hasAnswer will return false, just as if there were no answer contents at all.
            mAnswer = SuggestionAnswer.parseAnswerContents(mAnswerContents);
        } else {
            mAnswer = null;
        }
    }

    public Type getType() {
        return mType;
    }

    public int getTransition() {
        return mTransition;
    }

    public String getDisplayText() {
        return mDisplayText;
    }

    public String getDescription() {
        return mDescription;
    }

    public String getAnswerContents() {
        return mAnswerContents;
    }

    public String getAnswerType() {
        return mAnswerType;
    }

    public SuggestionAnswer getAnswer() {
        return mAnswer;
    }

    public boolean hasAnswer() {
        return mAnswer != null;
    }

    public String getFillIntoEdit() {
        return mFillIntoEdit;
    }

    public String getUrl() {
        return mUrl;
    }

    public String getFormattedUrl() {
        return mFormattedUrl;
    }

    public boolean isUrlSuggestion() {
        return mType.isUrl();
    }

    /**
     * @return Whether this suggestion represents a starred/bookmarked URL.
     */
    public boolean isStarred() {
        return mIsStarred;
    }

    public boolean isDeletable() {
        return mIsDeletable;
    }

    /**
     * @return The relevance score of this suggestion.
     */
    public int getRelevance() {
        return mRelevance;
    }

    @Override
    public String toString() {
        return mType + " relevance=" +  mRelevance + " \"" + mDisplayText + "\" -> " + mUrl;
    }

    @Override
    public int hashCode() {
        int hash = 37 * mType.mNativeType + mDisplayText.hashCode() + mFillIntoEdit.hashCode()
                + (mIsStarred ? 1 : 0) + (mIsDeletable ? 1 : 0);
        if (mAnswerContents != null) {
            hash = hash + mAnswerContents.hashCode();
        }
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof OmniboxSuggestion)) {
            return false;
        }

        OmniboxSuggestion suggestion = (OmniboxSuggestion) obj;

        boolean answersAreEqual =
                (mAnswerContents == null && suggestion.mAnswerContents == null)
                || (mAnswerContents != null
                && suggestion.mAnswerContents != null
                && mAnswerContents.equals(suggestion.mAnswerContents));
        return mType == suggestion.mType
                && mFillIntoEdit.equals(suggestion.mFillIntoEdit)
                && mDisplayText.equals(suggestion.mDisplayText)
                && answersAreEqual
                && mIsStarred == suggestion.mIsStarred
                && mIsDeletable == suggestion.mIsDeletable;
    }
}

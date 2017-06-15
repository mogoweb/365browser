// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.components.signin;

/**
 * AuthException abstracts away authenticator specific exceptions behind a single interface.
 * It is used for passing information that is useful for better handling of errors.
 */
public class AuthException extends Exception {
    private final boolean mIsTransientError;

    /**
     * A simple constructor that stores all the error handling information and makes it available to
     * the handler.
     * @param isTransientError Whether the error is transient and we can retry.
     */
    public AuthException(boolean isTransientError, Exception exception) {
        super(exception);
        mIsTransientError = isTransientError;
    }

    /**
     * @return Whether the error is transient and we can retry.
     */
    public boolean isTransientError() {
        return mIsTransientError;
    }
}

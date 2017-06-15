// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.app;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Locale;

import javax.annotation.concurrent.Immutable;

/**
 * A class to hold information passed from the browser process to each
 * service one when using the chromium linker. For more information, read the
 * technical notes in Linker.java.
 */
@Immutable
public class ChromiumLinkerParams implements Parcelable {
    // Use this base address to load native shared libraries. If 0, ignore other members.
    public final long mBaseLoadAddress;

    // If true, wait for a shared RELRO Bundle just after loading the libraries.
    public final boolean mWaitForSharedRelro;

    // If not empty, name of Linker.TestRunner implementation that needs to be
    // registered in the service process.
    public final String mTestRunnerClassNameForTesting;

    // If mTestRunnerClassNameForTesting is not empty, the Linker implementation
    // to force for testing.
    public final int mLinkerImplementationForTesting;

    public ChromiumLinkerParams(long baseLoadAddress, boolean waitForSharedRelro) {
        mBaseLoadAddress = baseLoadAddress;
        mWaitForSharedRelro = waitForSharedRelro;
        mTestRunnerClassNameForTesting = null;
        mLinkerImplementationForTesting = 0;
    }

    /**
     * Use this constructor to create a LinkerParams instance for testing.
     */
    public ChromiumLinkerParams(long baseLoadAddress,
                                boolean waitForSharedRelro,
                                String testRunnerClassName,
                                int linkerImplementation) {
        mBaseLoadAddress = baseLoadAddress;
        mWaitForSharedRelro = waitForSharedRelro;
        mTestRunnerClassNameForTesting = testRunnerClassName;
        mLinkerImplementationForTesting = linkerImplementation;
    }

    ChromiumLinkerParams(Parcel in) {
        mBaseLoadAddress = in.readLong();
        mWaitForSharedRelro = in.readInt() != 0;
        mTestRunnerClassNameForTesting = in.readString();
        mLinkerImplementationForTesting = in.readInt();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(mBaseLoadAddress);
        dest.writeInt(mWaitForSharedRelro ? 1 : 0);
        dest.writeString(mTestRunnerClassNameForTesting);
        dest.writeInt(mLinkerImplementationForTesting);
    }

    public static final Parcelable.Creator<ChromiumLinkerParams> CREATOR =
            new Parcelable.Creator<ChromiumLinkerParams>() {
                @Override
                public ChromiumLinkerParams createFromParcel(Parcel in) {
                    return new ChromiumLinkerParams(in);
                }

                @Override
                public ChromiumLinkerParams[] newArray(int size) {
                    return new ChromiumLinkerParams[size];
                }
            };

    // For debugging traces only.
    @Override
    public String toString() {
        return String.format(Locale.US,
                "LinkerParams(baseLoadAddress:0x%x, waitForSharedRelro:%s, "
                        + "testRunnerClassName:%s, linkerImplementation:%d",
                mBaseLoadAddress, Boolean.toString(mWaitForSharedRelro),
                mTestRunnerClassNameForTesting, mLinkerImplementationForTesting);
    }
}

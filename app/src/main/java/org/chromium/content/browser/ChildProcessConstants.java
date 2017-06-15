// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

/**
 * Constants to be used by child processes.
 */
public interface ChildProcessConstants {
    // Below are the names for the items placed in the bind or start command intent.
    // Note that because that intent maybe reused if a service is restarted, none should be process
    // specific.

    // Key in the binding Intent's Bundle for the ChromiumLinkerParams.
    public static final String EXTRA_LINKER_PARAMS =
            "com.google.android.apps.chrome.extra.linker_params";
    public static final String EXTRA_BIND_TO_CALLER =
            "com.google.android.apps.chrome.extra.bind_to_caller";

    // Below are the names for the items placed in the Bundle passed in the
    // IChildProcessService.setupConnection call, once the connection has been established.

    // Key for the command line.
    public static final String EXTRA_COMMAND_LINE =
            "com.google.android.apps.chrome.extra.command_line";

    // Key for the file descriptors that should be mapped in the child process..
    public static final String EXTRA_FILES = "com.google.android.apps.chrome.extra.extraFiles";

    // Key for the number of CPU cores.
    public static final String EXTRA_CPU_COUNT = "com.google.android.apps.chrome.extra.cpu_count";

    // Key for the CPU features mask.
    public static final String EXTRA_CPU_FEATURES =
            "com.google.android.apps.chrome.extra.cpu_features";
}

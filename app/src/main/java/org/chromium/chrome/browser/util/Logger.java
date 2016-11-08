/*
 *  Copyright (c) 2012, The Linux Foundation. All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions are
 *  met:
 *      * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 *  THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 *  WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 *  MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 *  ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 *  BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 *  CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 *  SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 *  BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 *  WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 *  OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 *  IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package org.chromium.chrome.browser.util;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

/**
 * Helper for logging from the SWE Java code. Easy to turn it off globally,
 * integrates a single tag name for all the project.
 */

public class Logger {

    private static boolean INFO_ON = true;
    private static boolean VERBOSE_ON = false;
    private static boolean WARN_ON = true;
    private static boolean ERROR_ON = true;

    public static void enableVerboseLogging () {
        i(Logger.class.getCanonicalName(), "Verbose logging enabled");
        VERBOSE_ON = true;
    }

    public static void notImplemented(String logTag) {
        apiError(logTag, "notImplemented: "
                + new Throwable().getStackTrace()[1].toString().replace(
                Logger.class.getPackage().getName(), ""));
    }

    public static void notImplemented(String logTag, String message) {
        apiError(logTag, "notImplemented: " + message + ": " +
                new Throwable().getStackTrace()[1].toString().replace(
                        Logger.class.getPackage().getName(), ""));
    }

    public static void i(String logTag, String msg) {
        if (INFO_ON)
            Log.i(logTag, msg);
    }

    public static void v(String logTag, String msg) {
        if (VERBOSE_ON)
            Log.v(logTag, msg);
    }

    public static void w(String logTag, String msg) {
        if (WARN_ON)
            Log.w(logTag, msg);
    }

    public static void e(String logTag, String msg) {
        if (ERROR_ON)
            Log.e(logTag, msg);
    }

    public static void i(String logTag, String msg, Throwable tr) {
        if (INFO_ON)
            Log.i(logTag, msg);
    }

    public static void v(String logTag, String msg, Throwable tr) {
        if (VERBOSE_ON)
            Log.v(logTag, msg);
    }

    public static void w(String logTag, String msg, Throwable tr) {
        if (WARN_ON)
            Log.w(logTag, msg);
    }

    public static void e(String logTag, String msg, Throwable tr) {
        if (ERROR_ON)
            Log.e(logTag, msg);
    }

    public static void apiError(String logTag, String msg) {
        if (ERROR_ON)
            Log.e(logTag, msg);
    }

    public static void apiAssert(String logTag, boolean trueCondition) {
        if (ERROR_ON) {
            if (!trueCondition)
                apiError(logTag,
                        "ASSERTION at: " +
                                new Throwable().getStackTrace()[1].toString().replace(
                                        Logger.class.getPackage().getName(), ""));
        }
    }

    public static void error(String logTag, String msg) {
        if (ERROR_ON)
            Log.e(logTag, msg);
    }

    public static void dumpTrace(Exception e) {
        if (VERBOSE_ON)
            Log.v("Trace", " ", e);
    }

    public static void userToastPassive(String string, Context context) {
        Toast.makeText(context, string, Toast.LENGTH_SHORT).show();
    }

    public static void developerToastPassive(String string, Context context) {
        Toast.makeText(context, string, Toast.LENGTH_SHORT).show();
    }

}

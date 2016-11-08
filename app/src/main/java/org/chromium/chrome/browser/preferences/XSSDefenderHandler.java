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


package org.chromium.chrome.browser.preferences;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.base.CommandLine;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.ChromeSwitches;
import org.chromium.chrome.browser.UpdateCheckService;
import org.chromium.chrome.R;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.os.AsyncTask;
import android.os.Handler;
import android.text.format.DateUtils;
import android.text.TextUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;


@JNINamespace("xss_defender")
public class XSSDefenderHandler extends AsyncTask<Void, Void, Boolean> {
    private static final String libraryName = "libswexssdefender";
    private static final String origLibraryName = libraryName + ".so";
    private static final String newLibraryName = libraryName + ".so_new";
    private static final String oldLibraryName = libraryName + ".so_old";
    private static final String LOGTAG = "XSSDefenderHandler";
    private static final String XSS_DEFENDER_SERVICE_PREF = "xss_defender_update_service";

    private String newLibraryFullPath;
    private String oldLibraryFullPath;
    private static Context context_;

    XSSDefenderHandler(Context context) {
        context_ = context;
    }

    public static void initializeGlobalInstance(Context context) {
        if (!CommandLine.getInstance().hasSwitch(ChromeSwitches.DISABLE_XSS_DEFENDER))
            new XSSDefenderHandler(context).execute();
    }

    // Move library from content/public/android/java/res/raw/ to APK files folder
    private boolean moveLibraryFromRes() {
        // Lookup library id from resources using name
        // Dynamic lookup avoids build failures when the library is missing
        int libResourcesId = context_.getResources().getIdentifier(libraryName,
                "raw", context_.getPackageName());
        if (libResourcesId > 0) {
            // Copy library to the APK files folder
            String path = getLibraryFullPath();
            Log.i(LOGTAG, "Move " + origLibraryName + " from resources to: " +
                     path);

            byte[] buff = new byte[2048];
            int read = 0;
            try {
                InputStream in = context_.getResources().openRawResource(
                        libResourcesId);
                FileOutputStream out = new FileOutputStream(path);
                while ((read = in.read(buff)) > 0) {
                    out.write(buff, 0, read);
                }
                in.close();
                out.close();
                // Set moved file to readable
                File file = new File(path);
                file.setReadable(true, false);
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(LOGTAG, "FAILED: Move " + origLibraryName +
                         " from resources to: " +  path);
             }
        }
        else {
            Log.e(LOGTAG, libraryName + " is not found under resources (content/public/android/java/res/raw/)");
            return false;
        }
        return true;
    }

    private boolean moveLibrary(String inputFile, String outputFile) {
        Log.i(LOGTAG, "Move " + inputFile + " to: " + outputFile);

        try {
            File inputFileHandler = new File(inputFile);
            File outputFileHandler = new File(outputFile);
            inputFileHandler.renameTo(outputFileHandler);
            outputFileHandler.setReadable(true, false);
        } catch (SecurityException | NullPointerException e) {
            e.printStackTrace();
            Log.e(LOGTAG, "FAILED: Move " + inputFile + " to: " + outputFile);
            return false;
        }
        return true;
    }

    @CalledByNative
    protected static void setLibraryVersion(int version) {
        if (context_ == null) return;
        SharedPreferences.Editor editor = context_.getSharedPreferences(XSS_DEFENDER_SERVICE_PREF, Context.MODE_PRIVATE).edit();
        editor.putLong(UpdateCheckService.UPDATE_VERSION_CODE, version);
        editor.commit();
    }

    @CalledByNative
    protected static String getOldLibraryName() {
        return oldLibraryName;
    }

    @CalledByNative
    protected static String getLibraryFullPath() {
        return getLibraryFullPathByName(origLibraryName);
    }

    @CalledByNative
    protected static String getLibraryFullPathByName(String libName) {
        if (context_ == null)
            return "";
        return new File(context_.getFilesDir(), libName).getPath();
    }

    private boolean libraryExists() {
        File file = new File(getLibraryFullPath());
        if (file.exists())
            return true;

        return false;
    }

    protected Boolean doInBackground(Void... params) {
        if (!libraryExists())
            moveLibraryFromRes();

        // Get the paths neede for update
        newLibraryFullPath = getLibraryFullPathByName(newLibraryName);
        oldLibraryFullPath = getLibraryFullPathByName(oldLibraryName);
        return true;
    }

    protected void onPostExecute(Boolean result) {
        // Enable updates only if XSS-Defender is enabled in settings
        if (PrefServiceBridge.getInstance().isXSSDefenderEnabled()) {
            Handler delayedUpdate = new Handler();
            // Delay update check 5 mins to avoid races
            delayedUpdate.postDelayed(new UpdateCheckServiceDelayed(), DateUtils.SECOND_IN_MILLIS * 60 * 5);
        }
    }

    private class UpdateCheckServiceDelayed implements Runnable {
        public void run(){
            final String serverURL = context_.getString(R.string.swe_xss_defender_update_server_url);
            if (TextUtils.isEmpty(serverURL) || serverURL.equalsIgnoreCase("about:blank"))
                return;
            new UpdateCheckService(context_, XSS_DEFENDER_SERVICE_PREF,
                    serverURL, newLibraryFullPath,
                    new UpdateCheckService.UpdateServiceEventListener() {
                        @Override
                        public void updateComplete(boolean success) {
                            if (!success)
                                return;
                            // keep a copy of the old library
                            moveLibrary(getLibraryFullPath(), oldLibraryFullPath);
                            // Replace the old library with the new updated one
                            moveLibrary(newLibraryFullPath, getLibraryFullPath());
                        }

                        @Override
                        public void updateProgress(int bytesRead) {
                        }

                        @Override
                        public boolean overrideInterval() {
                            return false;
                        }
                    }).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }
}

// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.crash;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import org.chromium.base.StreamUtil;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.browser.preferences.privacy.CrashReportingPermissionManager;
import org.chromium.chrome.browser.preferences.privacy.PrivacyPreferencesManager;
import org.chromium.chrome.browser.util.HttpURLConnectionFactory;
import org.chromium.chrome.browser.util.HttpURLConnectionFactoryImpl;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.zip.GZIPOutputStream;

/**
 * This class tries to upload a minidump to the crash server.
 *
 * It is implemented as a Callable<Boolean> and returns true on successful uploads,
 * and false otherwise.
 */
public class MinidumpUploadCallable implements Callable<Boolean> {
    private static final String TAG = "MinidumpUploadCallable";
    @VisibleForTesting protected static final int LOG_SIZE_LIMIT_BYTES = 1024 * 1024; // 1MB
    @VisibleForTesting protected static final int LOG_UPLOAD_LIMIT_PER_DAY = 5;

    @VisibleForTesting
    protected static final String PREF_LAST_UPLOAD_DAY = "crash_dump_last_upload_day";
    @VisibleForTesting protected static final String PREF_UPLOAD_COUNT = "crash_dump_upload_count";

    @VisibleForTesting
    protected static final String CRASH_URL_STRING = "https://clients2.google.com/cr/report";

    @VisibleForTesting
    protected static final String CONTENT_TYPE_TMPL = "multipart/form-data; boundary=%s";

    private final File mFileToUpload;
    private final File mLogfile;
    private final HttpURLConnectionFactory mHttpURLConnectionFactory;
    private final CrashReportingPermissionManager mPermManager;
    private final SharedPreferences mSharedPreferences;

    public MinidumpUploadCallable(File fileToUpload, File logfile, Context context) {
        this(fileToUpload, logfile, new HttpURLConnectionFactoryImpl(),
                PrivacyPreferencesManager.getInstance(context),
                PreferenceManager.getDefaultSharedPreferences(context));
    }

    public MinidumpUploadCallable(File fileToUpload, File logfile,
            HttpURLConnectionFactory httpURLConnectionFactory,
            CrashReportingPermissionManager permManager, SharedPreferences sharedPreferences) {
        mFileToUpload = fileToUpload;
        mLogfile = logfile;
        mHttpURLConnectionFactory = httpURLConnectionFactory;
        mPermManager = permManager;
        mSharedPreferences = sharedPreferences;
    }

    @Override
    public Boolean call() {
        if (!mPermManager.isUploadPermitted()) {
            Log.i(TAG, "Minidump upload is not permitted");
            return false;
        }

        boolean isLimited = mPermManager.isUploadLimited();
        if (isLimited && !isUploadSizeAndFrequencyAllowed()) {
            Log.i(TAG, "Minidump cannot currently be uploaded due to constraints");
            return false;
        }

        HttpURLConnection connection =
                mHttpURLConnectionFactory.createHttpURLConnection(CRASH_URL_STRING);
        if (connection == null) {
            return false;
        }

        FileInputStream minidumpInputStream = null;
        try {
            if (!configureConnectionForHttpPost(connection)) {
                return false;
            }
            minidumpInputStream = new FileInputStream(mFileToUpload);
            streamCopy(minidumpInputStream, new GZIPOutputStream(connection.getOutputStream()));
            boolean status = handleExecutionResponse(connection);

            if (isLimited) updateUploadPrefs();
            return status;
        } catch (IOException e) {
            // For now just log the stack trace.
            Log.w(TAG, "Error while uploading " + mFileToUpload.getName(), e);
            return false;
        } finally {
            connection.disconnect();

            if (minidumpInputStream != null) {
                StreamUtil.closeQuietly(minidumpInputStream);
            }
        }
    }

    /**
     * Configures a HttpURLConnection to send a HTTP POST request for uploading the minidump.
     *
     * This also reads the content-type from the minidump file.
     *
     * @param connection the HttpURLConnection to configure
     * @return true if successful.
     * @throws IOException
     */
    private boolean configureConnectionForHttpPost(HttpURLConnection connection)
            throws IOException {
        // Read the boundary which we need for the content type.
        String boundary = readBoundary();
        if (boundary == null) {
            return false;
        }

        connection.setDoOutput(true);
        connection.setRequestProperty("Connection", "Keep-Alive");
        connection.setRequestProperty("Content-Encoding", "gzip");
        connection.setRequestProperty("Content-Type", String.format(CONTENT_TYPE_TMPL, boundary));
        return true;
    }

    /**
     * Reads the HTTP response and cleans up successful uploads.
     *
     * @param connection the connection to read the response from
     * @return true if the upload was successful, false otherwise.
     * @throws IOException
     */
    private Boolean handleExecutionResponse(HttpURLConnection connection) throws IOException {
        int responseCode = connection.getResponseCode();
        if (isSuccessful(responseCode)) {
            String responseContent = getResponseContentAsString(connection);
            // The crash server returns the crash ID.
            String id = responseContent != null ? responseContent : "unknown";
            Log.i(TAG, "Minidump " + mFileToUpload.getName() + " uploaded successfully, id: " + id);

            // TODO(acleung): MinidumpUploadService is in charge of renaming while this class is
            // in charge of deleting. We should move all the file system operations into
            // MinidumpUploadService instead.
            cleanupMinidumpFile();

            try {
                appendUploadedEntryToLog(id);
            } catch (IOException ioe) {
                Log.e(TAG, "Fail to write uploaded entry to log file");
            }
            return true;
        } else {
            // Log the results of the upload. Note that periodic upload failures aren't bad
            // because we will need to throttle uploads in the future anyway.
            String msg = String.format(Locale.US,
                    "Failed to upload %s with code: %d (%s).",
                    mFileToUpload.getName(), responseCode, connection.getResponseMessage());
            Log.i(TAG, msg);

            // TODO(acleung): The return status informs us about why an upload might be
            // rejected. The next logical step is to put the reasons in an UMA histogram.
            return false;
        }
    }

    /**
     * Records the upload entry to a log file
     * similar to what is done in chrome/app/breakpad_linux.cc
     *
     * @param id The crash ID return from the server.
     */
    private void appendUploadedEntryToLog(String id) throws IOException {
        FileWriter writer = new FileWriter(mLogfile, /* Appending */ true);

        // The log entries are formated like so:
        //  seconds_since_epoch,crash_id
        StringBuilder sb = new StringBuilder();
        sb.append(System.currentTimeMillis() / 1000);
        sb.append(",");
        sb.append(id);
        sb.append('\n');

        try {
            // Since we are writing one line at a time, lets forget about BufferWriters.
            writer.write(sb.toString());
        } finally {
            writer.close();
        }
    }

    /**
     * Get the boundary from the file, we need it for the content-type.
     *
     * @return the boundary if found, else null.
     * @throws IOException
     */
    private String readBoundary() throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(mFileToUpload));
        String boundary = reader.readLine();
        reader.close();
        if (boundary == null || boundary.trim().isEmpty()) {
            Log.e(TAG, "Ignoring invalid crash dump: '" + mFileToUpload + "'");
            return null;
        }
        boundary = boundary.trim();
        if (!boundary.startsWith("--") || boundary.length() < 10) {
            Log.e(TAG, "Ignoring invalidly bound crash dump: '" + mFileToUpload + "'");
            return null;
        }
        boundary = boundary.substring(2);  // Remove the initial --
        return boundary;
    }

    /**
     * Mark file we just uploaded for cleanup later.
     *
     * We do not immediately delete the file for testing reasons,
     * but if marking the file fails, we do delete it right away.
     */
    private void cleanupMinidumpFile() {
        if (!CrashFileManager.tryMarkAsUploaded(mFileToUpload)) {
            Log.w(TAG, "Unable to mark " + mFileToUpload + " as uploaded.");
            if (!mFileToUpload.delete()) {
                Log.w(TAG, "Cannot delete " + mFileToUpload);
            }
        }
    }

    /**
     * Checks whether crash upload satisfies the size and frequency constraints.
     *
     * @return whether crash upload satisfies the size and frequency constraints.
     */
    private boolean isUploadSizeAndFrequencyAllowed() {
        // Check upload size constraint.
        if (mFileToUpload.length() > LOG_SIZE_LIMIT_BYTES) return false;

        // Check upload frequency constraint.
        // If pref doesn't exist then in both cases default value 0 will be returned and comparison
        // always would be true.
        if (mSharedPreferences.getInt(PREF_LAST_UPLOAD_DAY, 0) != getCurrentDay()) return true;
        return mSharedPreferences.getInt(PREF_UPLOAD_COUNT, 0) < LOG_UPLOAD_LIMIT_PER_DAY;
    }

    /**
     * Updates preferences used for determining crash upload constraints.
     */
    private void updateUploadPrefs() {
        SharedPreferences.Editor editor = mSharedPreferences.edit();

        int day = getCurrentDay();
        int prevCount = mSharedPreferences.getInt(PREF_UPLOAD_COUNT, 0);
        if (mSharedPreferences.getInt(PREF_LAST_UPLOAD_DAY, 0) != day) {
            prevCount = 0;
        }
        editor.putInt(PREF_LAST_UPLOAD_DAY, day).putInt(PREF_UPLOAD_COUNT, prevCount + 1).apply();
    }

    /**
     * Returns number of current day in a year starting from 1. Overridden in tests.
     */
    protected int getCurrentDay() {
        return Calendar.getInstance().get(Calendar.YEAR) * 365
                + Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
    }

    /**
     * Returns whether the response code indicates a successful HTTP request.
     *
     * @param responseCode the response code
     * @return true if response code indicates success, false otherwise.
     */
    private static boolean isSuccessful(int responseCode) {
        return responseCode == 200 || responseCode == 201 || responseCode == 202;
    }

    /**
     * Reads the response from |connection| as a String.
     *
     * @param connection the connection to read the response from.
     * @return the content of the response.
     * @throws IOException
     */
    private static String getResponseContentAsString(HttpURLConnection connection)
            throws IOException {
        String responseContent = null;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        streamCopy(connection.getInputStream(), baos);
        if (baos.size() > 0) {
            responseContent = baos.toString();
        }
        return responseContent;
    }

    /**
     * Copies all available data from |inStream| to |outStream|. Closes both
     * streams when done.
     *
     * @param inStream the stream to read
     * @param outStream the stream to write to
     * @throws IOException
     */
    private static void streamCopy(InputStream inStream,
                                   OutputStream outStream) throws IOException {
        byte[] temp = new byte[4096];
        int bytesRead = inStream.read(temp);
        while (bytesRead >= 0) {
            outStream.write(temp, 0, bytesRead);
            bytesRead = inStream.read(temp);
        }
        inStream.close();
        outStream.close();
    }
}

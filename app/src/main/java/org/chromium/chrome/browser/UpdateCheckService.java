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

package org.chromium.chrome.browser;

import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.util.Logger;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.File;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.FileNotFoundException;
import java.net.URL;
import java.net.URLConnection;
import java.net.HttpURLConnection;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class UpdateCheckService extends AsyncTask<Void, Integer, Boolean> {

    public interface UpdateServiceEventListener{
        void updateComplete(boolean success);
        void updateProgress(int bytesRead);
        boolean overrideInterval();
    }

    public class EmptyUpdateServiceEventListener implements UpdateServiceEventListener {
        @Override
        public void updateComplete(boolean success) { }

        @Override
        public void updateProgress(int bytesRead) { }

        @Override
        public boolean overrideInterval() { return false; }
    }

    private static final String LOGTAG = "UpdateCheckService";
    public static final long DEFAULT_UPDATE_INTERVAL = 3600 * 24; //in seconds (one day)

    //JSON File
    public static final String JSON_VERSION_CODE = "versioncode";
    public static final String JSON_URL = "download-url";
    public static final String JSON_MIN_INTERVAL = "interval";
    public static final String JSON_VERSION_STRING = "versionstring";
    public static final String JSON_MD5 = "md5";

    // Profile Settings
    public static final String UPDATE_TIMESTAMP = "update_timestamp";
    public static final String UPDATE_INTERVAL = "update_interval";
    public static final String UPDATE_VERSION_CODE = "update_versioncode";

    // Download Settings
    private final int MAX_FILE_SIZE = 20 * 1024 * 1024; //20 MB
    private final int CONNECTION_TIMEOUT_MS = 15000; //15 seconds
    private final int READ_TIMEOUT_MS = 15000; //15 seconds
    private final int MAX_READ_RETRY = 5;

    // JSON Variables
    private long interval = DEFAULT_UPDATE_INTERVAL;

    private int latestVersionCode;

    // Local Variables
    private String mDownloadPath;
    private String mServicePref;
    private String mServerURL;
    private Context mContext;
    private UpdateServiceEventListener mCallback;

    public UpdateCheckService(Context ctx,
                              String pref_name,
                              String serverURL,
                              String path,
                              UpdateServiceEventListener evtConsumer) {
        mServerURL = serverURL;
        mDownloadPath = path;
        mServicePref = pref_name;
        mContext = ctx;

        if (evtConsumer == null) {
            mCallback = new EmptyUpdateServiceEventListener();
        } else {
            mCallback = evtConsumer;
        }

        Logger.i(LOGTAG, "Constructed UCS. Pref" + pref_name + " path: " + path);
    }

    protected Boolean doInBackground(Void... params) {
        ConnectivityManager connectivityManager = (ConnectivityManager)
                mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = connectivityManager.getActiveNetworkInfo();

        if (info == null) {
            return false;
        }

        if (info.getType() != ConnectivityManager.TYPE_WIFI &&
                info.getType() != ConnectivityManager.TYPE_ETHERNET &&
                !PrefServiceBridge.getInstance().getSecurityUpdatesOnCellular()) {
            return false;
        }


        long server_interval = getSharedPreferences().getLong(UPDATE_INTERVAL, 0);
        if (server_interval > 0) {
            interval = server_interval;
        }

        long next_update_time =
                getSharedPreferences().getLong(UPDATE_TIMESTAMP, 0) + (interval * 1000);

        if (next_update_time < System.currentTimeMillis() || mCallback.overrideInterval()) {
            Logger.i(LOGTAG, "check for update now: ");
            return handleUpdateCheck();
        } else {
            Logger.i(LOGTAG, "check discarded next_update_time:" + next_update_time +
                    " > Current Time:" + System.currentTimeMillis());
        }

        return false;
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
        //Logger.i(LOGTAG, "onProgressUpdate(): " + String.valueOf(values[0]));
        mCallback.updateProgress(values[0]);
    }


    protected void onPostExecute(Boolean result) {
        Logger.i(LOGTAG, "Download done. Result " + result);
        mCallback.updateComplete(result);
    }

    private SharedPreferences getSharedPreferences() {
        return mContext.getSharedPreferences(mServicePref, Context.MODE_PRIVATE);
    }

    private void updatePrefs(String name, long val){
        SharedPreferences.Editor editor = getSharedPreferences().edit();
        editor.putLong(name, val);
        editor.commit();
    }

    private boolean handleUpdateCheck() {
        InputStream stream = null;
        boolean return_value = false;

        if (!TextUtils.isEmpty(mServerURL)) {
            try {
                URLConnection connection = new URL(mServerURL).openConnection();
                connection.setUseCaches(false);
                stream = connection.getInputStream();
                String result = readJSONStream(stream);
                Logger.i(LOGTAG, "handleUpdateCheck result : " + result);
                JSONObject jsonResult = (JSONObject) new JSONTokener(result).nextValue();
                latestVersionCode = jsonResult.getInt(JSON_VERSION_CODE);
                String downloadUrl = (String) jsonResult.get(JSON_URL);
                String latestVersion = (String) jsonResult.get(JSON_VERSION_STRING);
                String md5 = (String) jsonResult.get(JSON_MD5);

                if (jsonResult.has(JSON_MIN_INTERVAL)) {
                    interval = jsonResult.getInt(JSON_MIN_INTERVAL);
                    updatePrefs(UPDATE_INTERVAL, interval);
                }

                long vc = getSharedPreferences().getLong(UPDATE_VERSION_CODE, 0);
                if (vc < latestVersionCode) {
                    // Download new Library
                    int attempts = 0;
                    boolean downloadSuccess = false;
                    do{
                        //replace new with file name based on component
                        downloadSuccess = downloadFile(downloadUrl, md5);
                        attempts++;
                    } while(!downloadSuccess && attempts < MAX_READ_RETRY);
                    if( downloadSuccess ){
                        updatePrefs(UPDATE_VERSION_CODE, latestVersionCode);
                        return_value = true;
                    }
                }
                stream.close();
            } catch (Exception e) {
                Logger.e(LOGTAG, "handleUpdateCheck Exception : " + e.toString());
            } finally {
                // always update the timestamp
                updatePrefs(UPDATE_TIMESTAMP, System.currentTimeMillis());
            }
        }

        return return_value;
    }

    private String readJSONStream(InputStream is) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        try {
            String line = reader.readLine();
            while (line != null) {
                sb.append(line + "\n");
                line = reader.readLine();
            }
        } catch (Exception e) {
            Logger.e(LOGTAG, "convertStreamToString Exception : " + e.toString());
        } finally {
            try {
                is.close();
            } catch (Exception e) {
                Logger.e(LOGTAG, "convertStreamToString Exception : " + e.toString());
            }
        }
        return sb.toString();
    }

    private Boolean downloadFile(String downloadUrl, String md5) {
        if (downloadUrl == null || downloadUrl.isEmpty()){
            return false;
        }
        Logger.i(LOGTAG, "Start Download ");
        Boolean result = true;
        try {
            InputStream is = null;
            ByteArrayOutputStream os = null;
            HttpURLConnection conn = null;
            try {
                URL url = new URL(downloadUrl);
                String protocol = url.getProtocol();
                conn = (HttpURLConnection) url.openConnection();
                if (protocol.equals("https")) {
                    SSLContext sslContext = SSLContext.getInstance("TLS");
                    sslContext.init(null, null, new java.security.SecureRandom());
                    ((HttpsURLConnection) conn).setSSLSocketFactory(sslContext.getSocketFactory());
                }

                conn.setInstanceFollowRedirects(false);
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(CONNECTION_TIMEOUT_MS);
                conn.setReadTimeout(READ_TIMEOUT_MS);
                conn.connect();

                is = new BufferedInputStream(conn.getInputStream());
                os = new ByteArrayOutputStream();

                // Download file
                int bufferLength;
                boolean invalidData = false;
                byte[] buffer = new byte[4096];
                while ( (bufferLength = is.read(buffer)) != -1 && !invalidData) {
                    os.write(buffer, 0, bufferLength);
                    publishProgress(os.size());
                    if (os.size() > MAX_FILE_SIZE) {
                        os.close();
                        invalidData = true;
                    }
                }

                if( !invalidData ){
                    // Create file
                    File file = new File(mDownloadPath);
                    file.createNewFile();
                    file.setReadable(true, false);

                    //write the bytes in file
                    FileOutputStream fos = new FileOutputStream(file);
                    fos.write(os.toByteArray());
                    fos.close();
                    String ShaResult = checkSum(file);
                    if(md5.substring(0, ShaResult.length()) == ShaResult) {
                        Logger.i(LOGTAG, "Md5 mismatch:" + ShaResult + " != " + md5);
                        result = false;
                    }
                    Logger.i(LOGTAG, "Download complete save to: " + mDownloadPath);
                }
                else {
                    result = false;
                }

            } catch (Exception e) {
                Logger.e(LOGTAG, "Download Error: " + e.getMessage());
                result = false;
            } finally {
                try {
                    if (conn != null) {
                        conn.disconnect();
                    }
                } finally {
                    try {
                        if (is != null) {
                            is.close();
                        }
                    } finally {
                        if (os != null) {
                            os.close();
                        }
                    }
                }
            }
        } catch (Exception e) {
            result = false;
        }
        return result;
    }

    private String checkSum(File file)
            throws IOException, FileNotFoundException, NoSuchAlgorithmException
    {
        MessageDigest sha1 = MessageDigest.getInstance("MD5");
        try (InputStream input = new FileInputStream(file)) {
            //sha1.update(Files.readAllBytes(Paths.get(path)));
            byte[] buffer = new byte[8192];
            int len = input.read(buffer);

            while (len != -1) {
                sha1.update(buffer, 0, len);
                len = input.read(buffer);
            }

            //Convert Hex to String
            byte [] shaByte = sha1.digest();
            StringBuilder sb = new StringBuilder();
            for(int index = 0; index < shaByte.length; index++) {
                int value = shaByte[index] & 0xFF;
                sb.append(Integer.toHexString(value));
            }
            return sb.toString();
        }
    }
}

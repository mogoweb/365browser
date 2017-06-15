// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.components.variations.firstrun;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.metrics.CachedMetrics.SparseHistogramSample;
import org.chromium.base.metrics.CachedMetrics.TimesHistogramSample;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

/**
 * Fetches the variations seed before the actual first run of Chrome.
 */
public class VariationsSeedFetcher {
    private static final String TAG = "VariationsSeedFetch";
    private static final String VARIATIONS_SERVER_URL =
            "https://clientservices.googleapis.com/chrome-variations/seed?osname=android";

    private static final int BUFFER_SIZE = 4096;
    private static final int READ_TIMEOUT = 3000; // time in ms
    private static final int REQUEST_TIMEOUT = 1000; // time in ms

    // Values for the "Variations.FirstRun.SeedFetchResult" sparse histogram, which also logs
    // HTTP result codes. These are negative so that they don't conflict with the HTTP codes.
    // These values should not be renumbered or re-used since they are logged to UMA.
    private static final int SEED_FETCH_RESULT_UNKNOWN_HOST_EXCEPTION = -3;
    private static final int SEED_FETCH_RESULT_TIMEOUT = -2;
    private static final int SEED_FETCH_RESULT_IOEXCEPTION = -1;

    @VisibleForTesting
    static final String VARIATIONS_INITIALIZED_PREF = "variations_initialized";

    // Synchronization lock
    private static final Object sLock = new Object();

    private static VariationsSeedFetcher sInstance;

    @VisibleForTesting
    VariationsSeedFetcher() {}

    public static VariationsSeedFetcher get() {
        // TODO(aberent) Check not running on UI thread. Doing so however makes Robolectric testing
        // of dependent classes difficult.
        synchronized (sLock) {
            if (sInstance == null) {
                sInstance = new VariationsSeedFetcher();
            }
            return sInstance;
        }
    }

    /**
     * Override the VariationsSeedFetcher, typically with a mock, for testing classes that depend on
     * this one.
     * @param fetcher the mock.
     */
    @VisibleForTesting
    public static void setVariationsSeedFetcherForTesting(VariationsSeedFetcher fetcher) {
        sInstance = fetcher;
    }

    @VisibleForTesting
    protected HttpURLConnection getServerConnection(String restrictMode)
            throws MalformedURLException, IOException {
        String urlString = VARIATIONS_SERVER_URL;
        if (restrictMode != null && !restrictMode.isEmpty()) {
            urlString += "&restrict=" + restrictMode;
        }
        URL url = new URL(urlString);
        return (HttpURLConnection) url.openConnection();
    }

    /**
     * Fetch the first run variations seed.
     * @param restrictMode The restrict mode parameter to pass to the server via a URL param.
     */
    public void fetchSeed(String restrictMode) {
        assert !ThreadUtils.runningOnUiThread();
        // Prevent multiple simultaneous fetches
        synchronized (sLock) {
            Context context = ContextUtils.getApplicationContext();
            SharedPreferences prefs = ContextUtils.getAppSharedPreferences();
            // Early return if an attempt has already been made to fetch the seed, even if it
            // failed. Only attempt to get the initial Java seed once, since a failure probably
            // indicates a network problem that is unlikely to be resolved by a second attempt.
            // Note that VariationsSeedBridge.hasNativePref() is a pure Java function, reading an
            // Android preference that is set when the seed is fetched by the native code.
            if (prefs.getBoolean(VARIATIONS_INITIALIZED_PREF, false)
                    || VariationsSeedBridge.hasNativePref()) {
                return;
            }
            downloadContent(context, restrictMode);
            prefs.edit().putBoolean(VARIATIONS_INITIALIZED_PREF, true).apply();
        }
    }

    private void recordFetchResultOrCode(int resultOrCode) {
        SparseHistogramSample histogram =
                new SparseHistogramSample("Variations.FirstRun.SeedFetchResult");
        histogram.record(resultOrCode);
    }

    private void recordSeedFetchTime(long timeDeltaMillis) {
        Log.i(TAG, "Fetched first run seed in " + timeDeltaMillis + " ms");
        TimesHistogramSample histogram = new TimesHistogramSample(
                "Variations.FirstRun.SeedFetchTime", TimeUnit.MILLISECONDS);
        histogram.record(timeDeltaMillis);
    }

    private void recordSeedConnectTime(long timeDeltaMillis) {
        TimesHistogramSample histogram = new TimesHistogramSample(
                "Variations.FirstRun.SeedConnectTime", TimeUnit.MILLISECONDS);
        histogram.record(timeDeltaMillis);
    }

    private void downloadContent(Context context, String restrictMode) {
        HttpURLConnection connection = null;
        try {
            long startTimeMillis = SystemClock.elapsedRealtime();
            connection = getServerConnection(restrictMode);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setConnectTimeout(REQUEST_TIMEOUT);
            connection.setDoInput(true);
            connection.setRequestProperty("A-IM", "gzip");
            connection.connect();
            int responseCode = connection.getResponseCode();
            recordFetchResultOrCode(responseCode);
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "Non-OK response code = %d", responseCode);
                return;
            }

            recordSeedConnectTime(SystemClock.elapsedRealtime() - startTimeMillis);
            // Convert the InputStream into a byte array.
            byte[] rawSeed = getRawSeed(connection);
            String signature = getHeaderFieldOrEmpty(connection, "X-Seed-Signature");
            String country = getHeaderFieldOrEmpty(connection, "X-Country");
            String date = getHeaderFieldOrEmpty(connection, "Date");
            boolean isGzipCompressed = getHeaderFieldOrEmpty(connection, "IM").equals("gzip");
            VariationsSeedBridge.setVariationsFirstRunSeed(
                    rawSeed, signature, country, date, isGzipCompressed);
            recordSeedFetchTime(SystemClock.elapsedRealtime() - startTimeMillis);
        } catch (SocketTimeoutException e) {
            recordFetchResultOrCode(SEED_FETCH_RESULT_TIMEOUT);
            Log.w(TAG, "SocketTimeoutException fetching first run seed: ", e);
        } catch (UnknownHostException e) {
            recordFetchResultOrCode(SEED_FETCH_RESULT_UNKNOWN_HOST_EXCEPTION);
            Log.w(TAG, "UnknownHostException fetching first run seed: ", e);
        } catch (IOException e) {
            recordFetchResultOrCode(SEED_FETCH_RESULT_IOEXCEPTION);
            Log.w(TAG, "IOException fetching first run seed: ", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String getHeaderFieldOrEmpty(HttpURLConnection connection, String name) {
        String headerField = connection.getHeaderField(name);
        if (headerField == null) {
            return "";
        }
        return headerField.trim();
    }

    private byte[] getRawSeed(HttpURLConnection connection) throws IOException {
        InputStream inputStream = null;
        try {
            inputStream = connection.getInputStream();
            return convertInputStreamToByteArray(inputStream);
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
        }
    }

    private byte[] convertInputStreamToByteArray(InputStream inputStream) throws IOException {
        ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
        byte[] buffer = new byte[BUFFER_SIZE];
        int charactersReadCount = 0;
        while ((charactersReadCount = inputStream.read(buffer)) != -1) {
            byteBuffer.write(buffer, 0, charactersReadCount);
        }
        return byteBuffer.toByteArray();
    }
}

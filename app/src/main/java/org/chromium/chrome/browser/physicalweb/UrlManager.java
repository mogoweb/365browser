// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.physicalweb;

import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import org.json.JSONException;
import org.json.JSONObject;

import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.base.ObserverList;
import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.library_loader.LibraryProcessType;
import org.chromium.content.browser.BrowserStartupController;
import org.chromium.content.browser.BrowserStartupController.StartupCallback;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * This class stores URLs which are discovered by scanning for Physical Web beacons.
 *
 * There are two sets of URLs maintained:
 * - Those which are currently nearby, as tracked by calls to addUrl/removeUrl
 * - Those which have ever resolved through the Physical Web Service (e.g. are known to produce
 *     good results).
 */
class UrlManager {
    private static final String TAG = "PhysicalWeb";
    private static final String PREFS_VERSION_KEY = "physicalweb_version";
    private static final String PREFS_ALL_URLS_KEY = "physicalweb_all_urls";
    private static final String PREFS_PWS_RESULTS_KEY = "physicalweb_pws_results";
    private static final int PREFS_VERSION = 4;
    private static final long MAX_CACHE_TIME = 24 * 60 * 60 * 1000;  // 1 Day
    private static final int MAX_CACHE_SIZE = 100;
    private final ObserverList<Listener> mObservers;
    private final Set<String> mNearbyUrls;
    private final Map<String, UrlInfo> mUrlInfoMap;
    private final Map<String, PwsResult> mPwsResultMap;
    private final PriorityQueue<String> mUrlsSortedByTimestamp;
    private PwsClient mPwsClient;
    private long mNativePhysicalWebDataSourceAndroid;

    /**
     * Interface for observers that should be notified when the nearby URL list changes.
     */
    public interface Listener {
        /**
         * Callback called when one or more URLs are added to the URL list.
         * @param urls A set of UrlInfos containing nearby URLs resolvable with our resolution
         * service.
         */
        void onDisplayableUrlsAdded(Collection<UrlInfo> urls);
    }

    /**
     * Construct the UrlManager.
     */
    @VisibleForTesting
    public UrlManager() {
        mPwsClient = new PwsClientImpl();
        mObservers = new ObserverList<Listener>();
        mNearbyUrls = new HashSet<>();
        mUrlInfoMap = new HashMap<>();
        mPwsResultMap = new HashMap<>();
        mUrlsSortedByTimestamp = new PriorityQueue<String>(1, new Comparator<String>() {
            @Override
            public int compare(String url1, String url2) {
                Long timestamp1 = Long.valueOf(mUrlInfoMap.get(url1).getFirstSeenTimestamp());
                Long timestamp2 = Long.valueOf(mUrlInfoMap.get(url2).getFirstSeenTimestamp());
                return timestamp1.compareTo(timestamp2);
            }
        });
        initSharedPreferences();
        registerNativeInitStartupCallback();
    }

    // "Initialization on demand holder idiom"
    private static class LazyHolder {
        private static final UrlManager INSTANCE = new UrlManager();
    }


    /**
     * Get a singleton instance of this class.
     * @return A singleton instance of this class.
     */
    @CalledByNative
    public static UrlManager getInstance() {
        return LazyHolder.INSTANCE;
    }

    /**
     * Add an observer to be notified on changes to the nearby URL list.
     * @param observer The observer to add.
     */
    public void addObserver(Listener observer) {
        mObservers.addObserver(observer);
    }

    /**
     * Remove an observer from the observer list.
     * @param observer The observer to remove.
     */
    public void removeObserver(Listener observer) {
        mObservers.removeObserver(observer);
    }

    /**
     * Add a URL to the store of URLs.
     * @param urlInfo The URL to add.
     */
    @VisibleForTesting
    public void addUrl(UrlInfo urlInfo) {
        Log.d(TAG, "URL found: %s", urlInfo);
        urlInfo = updateCacheEntry(urlInfo);
        garbageCollect();
        putCachedUrlInfoMap();

        // In the rare event that our entry is immediately garbage collected from the cache, we
        // should stop here.
        if (!mUrlInfoMap.containsKey(urlInfo.getUrl())) {
            return;
        }

        if (mNearbyUrls.contains(urlInfo.getUrl())) {
            // The URL has been seen before. Notify listeners with the new distance estimate.
            if (urlInfo.getDistance() >= 0.0 && mPwsResultMap.containsKey(urlInfo.getUrl())) {
                safeNotifyNativeListenersOnDistanceChanged(urlInfo.getUrl(), urlInfo.getDistance());
            }
            return;
        }

        // This is a new URL. Add it to the nearby set.
        mNearbyUrls.add(urlInfo.getUrl());

        if (!PhysicalWeb.isOnboarding() && !mPwsResultMap.containsKey(urlInfo.getUrl())) {
            // We need to resolve the URL.
            resolveUrl(urlInfo);
            return;
        }

        registerNewDisplayableUrl(urlInfo);
    }

    /**
     * Remove a URL from the store of URLs.
     * @param urlInfo The URL to remove.
     */
    public void removeUrl(UrlInfo urlInfo) {
        Log.d(TAG, "URL lost: %s", urlInfo);

        if (!mNearbyUrls.contains(urlInfo.getUrl())) {
            return;
        }

        mNearbyUrls.remove(urlInfo.getUrl());

        // If the URL was previously displayable (both nearby and resolved) and is now no longer
        // nearby, notify listeners that the URL is lost.
        if (mPwsResultMap.containsKey(urlInfo.getUrl())) {
            safeNotifyNativeListenersOnLost(urlInfo.getUrl());
        }
    }

    /**
     * Get the list of URLs which are both nearby and resolved through PWS.
     * @return A set of nearby and resolved URLs, sorted by distance.
     */
    @VisibleForTesting
    public List<UrlInfo> getUrls() {
        return getUrls(false);
    }

    /**
     * Get the list of URLs which are both nearby and resolved through PWS.
     * @param allowUnresolved If true, include unresolved URLs only if the
     * resolved URL list is empty.
     * @return A set of nearby URLs, sorted by distance.
     */
    @VisibleForTesting
    public List<UrlInfo> getUrls(boolean allowUnresolved) {
        Set<String> resolvedUrls = mPwsResultMap.keySet();
        Set<String> intersection = new HashSet<>(mNearbyUrls);
        intersection.retainAll(resolvedUrls);
        Log.d(TAG, "Get URLs With: %d nearby, %d resolved, and %d in intersection.",
                mNearbyUrls.size(), resolvedUrls.size(), intersection.size());

        List<UrlInfo> urlInfos = null;
        if (allowUnresolved && resolvedUrls.isEmpty()) {
            urlInfos = getUrlInfoList(mNearbyUrls);
        } else {
            urlInfos = getUrlInfoList(intersection);
        }
        Collections.sort(urlInfos, new Comparator<UrlInfo>() {
            @Override
            public int compare(UrlInfo urlInfo1, UrlInfo urlInfo2) {
                Double distance1 = Double.valueOf(urlInfo1.getDistance());
                Double distance2 = Double.valueOf(urlInfo2.getDistance());
                return distance1.compareTo(distance2);
            }
        });

        // Keep only the first UrlInfo with a given groupid. The list is already
        // sorted by distance, so the first instance will be the closest.
        return deduplicateByGroupId(urlInfos);
    }

    public UrlInfo getUrlInfoByUrl(String url) {
        return mUrlInfoMap.get(url);
    }

    public Set<String> getNearbyUrls() {
        return mNearbyUrls;
    }

    public Set<String> getResolvedUrls() {
        return mPwsResultMap.keySet();
    }

    /**
     * Gets all UrlInfos and PwsResults for URLs that are nearby and resolved.
     * @param nativePhysicalWebCollection A pointer to the native PhysicalWebCollection container
     *                                    which will receive the list of nearby URL metadata.
     */
    @CalledByNative
    public void getPwCollection(long nativePhysicalWebCollection) {
        List<UrlInfo> nearbyUrlInfos = getUrlInfoList(mNearbyUrls);
        for (UrlInfo urlInfo : nearbyUrlInfos) {
            String requestUrl = urlInfo.getUrl();
            PwsResult pwsResult = mPwsResultMap.get(requestUrl);
            if (pwsResult != null) {
                nativeAppendMetadataItem(nativePhysicalWebCollection, requestUrl,
                        urlInfo.getDistance(), urlInfo.getFirstSeenTimestamp(), pwsResult.siteUrl,
                        pwsResult.iconUrl, pwsResult.title, pwsResult.description,
                        pwsResult.groupId);
            }
        }
    }

    /**
     * Forget all stored URLs.
     */
    public void clearAllUrls() {
        clearNearbyUrls();
        mUrlsSortedByTimestamp.clear();
        mUrlInfoMap.clear();
        mPwsResultMap.clear();
        putCachedUrlInfoMap();
        putCachedPwsResultMap();
    }

    /**
     * Forget all nearby URLs.
     */
    public void clearNearbyUrls() {
        HashSet<String> intersection = new HashSet<>(mNearbyUrls);
        intersection.retainAll(mPwsResultMap.keySet());

        mNearbyUrls.clear();

        // Only notify listeners for URLs that were previously displayable (both nearby and
        // resolved).
        for (String url : intersection) {
            safeNotifyNativeListenersOnLost(url);
        }
    }

    private List<UrlInfo> getUrlInfoList(Set<String> urls) {
        List<UrlInfo> result = new ArrayList<>();
        for (String url : urls) {
            result.add(mUrlInfoMap.get(url));
        }
        return result;
    }

    /**
     * Given a list of UrlInfos, returns the list with only one UrlInfo for each unique group ID.
     * When multiple UrlInfos have the same group ID, all but the first instance are discarded.
     * @param urlInfos A list of UrlInfos, possibly containing duplicates.
     * @return A list of deduplicated UrlInfos.
     */
    private List<UrlInfo> deduplicateByGroupId(List<UrlInfo> urlInfos) {
        List<UrlInfo> deduplicatedUrlInfos = new ArrayList<>();
        Set<String> groupIds = new HashSet<>();
        for (UrlInfo urlInfo : urlInfos) {
            PwsResult pwsResult = mPwsResultMap.get(urlInfo.getUrl());
            if (pwsResult != null) {
                if (pwsResult.groupId == null || groupIds.contains(pwsResult.groupId)) {
                    continue;
                }
                groupIds.add(pwsResult.groupId);
            }
            deduplicatedUrlInfos.add(urlInfo);
        }
        return deduplicatedUrlInfos;
    }

    /**
     * Adds a URL that has been resolved by the PWS.
     * @param pwsResult The meta data associated with the resolved URL.
     */
    private void addResolvedUrl(PwsResult pwsResult) {
        Log.d(TAG, "PWS resolved: %s", pwsResult.requestUrl);
        if (mPwsResultMap.containsKey(pwsResult.requestUrl)) {
            return;
        }

        mPwsResultMap.put(pwsResult.requestUrl, pwsResult);
        putCachedPwsResultMap();

        if (!mNearbyUrls.contains(pwsResult.requestUrl)
                || !mUrlInfoMap.containsKey(pwsResult.requestUrl)) {
            return;
        }
        registerNewDisplayableUrl(mUrlInfoMap.get(pwsResult.requestUrl));
    }

    private void removeResolvedUrl(UrlInfo url) {
        Log.d(TAG, "PWS unresolved: %s", url);
        mPwsResultMap.remove(url.getUrl());
        putCachedPwsResultMap();
    }

    private void initSharedPreferences() {
        // Check the version.
        final SharedPreferences prefs = ContextUtils.getAppSharedPreferences();
        if (prefs.getInt(PREFS_VERSION_KEY, 0) != PREFS_VERSION) {
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    prefs.edit()
                            .putInt(PREFS_VERSION_KEY, PREFS_VERSION)
                            // This clean up code can be deleted in m59.
                            .remove("physicalweb_nearby_urls")
                            .remove("org.chromium.chrome.browser.physicalweb.VERSION")
                            .remove("org.chromium.chrome.browser.physicalweb"
                                    + ".BOTTOM_BAR_DISPLAY_COUNT")
                            // This clean up code can be deleted in m60
                            .remove("physical_web_ignore_other_clients")
                            .apply();
                    return null;
                }
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            return;
        }

        // Read the cache.
        for (String serializedUrl : prefs.getStringSet(PREFS_ALL_URLS_KEY, new HashSet<String>())) {
            try {
                JSONObject jsonObject = new JSONObject(serializedUrl);
                UrlInfo urlInfo = UrlInfo.jsonDeserialize(jsonObject);
                mUrlInfoMap.put(urlInfo.getUrl(), urlInfo);
                mUrlsSortedByTimestamp.add(urlInfo.getUrl());
            } catch (JSONException e) {
                Log.e(TAG, "Could not deserialize UrlInfo", e);
            }
        }
        for (String serializedPwsResult : prefs.getStringSet(PREFS_PWS_RESULTS_KEY,
                new HashSet<String>())) {
            try {
                JSONObject jsonObject = new JSONObject(serializedPwsResult);
                PwsResult pwsResult = PwsResult.jsonDeserialize(jsonObject);
                mPwsResultMap.put(pwsResult.requestUrl, pwsResult);
            } catch (JSONException e) {
                Log.e(TAG, "Could not deserialize PwsResult", e);
            }
        }
        garbageCollect();
    }

    private void setStringSetInSharedPreferences(String preferenceName, Set<String> urls) {
        ContextUtils.getAppSharedPreferences().edit()
                .putStringSet(preferenceName, urls)
                .apply();
    }

    private void putCachedUrlInfoMap() {
        Set<String> serializedUrls = new HashSet<>();
        for (UrlInfo url : mUrlInfoMap.values()) {
            try {
                serializedUrls.add(url.jsonSerialize().toString());
            } catch (JSONException e) {
                Log.e(TAG, "Could not serialize UrlInfo", e);
            }
        }

        setStringSetInSharedPreferences(PREFS_ALL_URLS_KEY, serializedUrls);
    }

    private void putCachedPwsResultMap() {
        Set<String> serializedPwsResults = new HashSet<>();
        for (PwsResult pwsResult : mPwsResultMap.values()) {
            try {
                serializedPwsResults.add(pwsResult.jsonSerialize().toString());
            } catch (JSONException e) {
                Log.e(TAG, "Could not serialize PwsResult", e);
            }
        }

        setStringSetInSharedPreferences(PREFS_PWS_RESULTS_KEY, serializedPwsResults);
    }

    /**
     * Updates a cache entry with new information.
     * When we reencounter a URL, only its distance should update.
     * @param urlInfo This should be a freshly discovered UrlInfo, though it does not have to be
     * previously undiscovered.
     * @return The updated cache entry
     */
    private UrlInfo updateCacheEntry(UrlInfo urlInfo) {
        UrlInfo currentUrlInfo = mUrlInfoMap.get(urlInfo.getUrl());
        if (currentUrlInfo == null) {
            mUrlInfoMap.put(urlInfo.getUrl(), urlInfo);
            currentUrlInfo = urlInfo;
        } else {
            mUrlsSortedByTimestamp.remove(urlInfo.getUrl());
            if (urlInfo.getDistance() > 0.0) {
                currentUrlInfo.setDistance(urlInfo.getDistance());
            }
            if (urlInfo.getDeviceAddress() != null) {
                currentUrlInfo.setDeviceAddress(urlInfo.getDeviceAddress());
            }
        }
        mUrlsSortedByTimestamp.add(urlInfo.getUrl());
        return currentUrlInfo;
    }

    private void resolveUrl(final UrlInfo url) {
        Set<UrlInfo> urls = new HashSet<UrlInfo>(Arrays.asList(url));
        final long timestamp = SystemClock.elapsedRealtime();
        mPwsClient.resolve(urls, new PwsClient.ResolveScanCallback() {
            @Override
            public void onPwsResults(final Collection<PwsResult> pwsResults) {
                long duration = SystemClock.elapsedRealtime() - timestamp;
                PhysicalWebUma.onBackgroundPwsResolution(duration);
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        for (PwsResult pwsResult : pwsResults) {
                            String requestUrl = pwsResult.requestUrl;
                            if (url.getUrl().equalsIgnoreCase(requestUrl)) {
                                addResolvedUrl(pwsResult);
                                return;
                            }
                        }
                        removeResolvedUrl(url);
                    }
                });
            }
        });
    }

    private void registerNewDisplayableUrl(UrlInfo urlInfo) {
        // Notify listeners about the new displayable URL.
        Collection<UrlInfo> urlInfos = new ArrayList<>();
        urlInfos.add(urlInfo);
        Collection<UrlInfo> wrappedUrlInfos = Collections.unmodifiableCollection(urlInfos);
        for (Listener observer : mObservers) {
            observer.onDisplayableUrlsAdded(wrappedUrlInfos);
        }

        safeNotifyNativeListenersOnFound(urlInfo.getUrl());
        if (urlInfo.getDistance() >= 0.0) {
            safeNotifyNativeListenersOnDistanceChanged(urlInfo.getUrl(), urlInfo.getDistance());
        }
    }

    private void garbageCollect() {
        for (String url = mUrlsSortedByTimestamp.peek(); url != null;
                url = mUrlsSortedByTimestamp.peek()) {
            UrlInfo urlInfo = mUrlInfoMap.get(url);
            if ((System.currentTimeMillis() - urlInfo.getFirstSeenTimestamp() <= MAX_CACHE_TIME
                        && mUrlsSortedByTimestamp.size() <= MAX_CACHE_SIZE)
                    || mNearbyUrls.contains(url)) {
                Log.d(TAG, "Not garbage collecting: ", urlInfo);
                break;
            }
            Log.d(TAG, "Garbage collecting: ", urlInfo);
            // The min value cannot have changed at this point, so it's OK to just remove via
            // poll().
            mUrlsSortedByTimestamp.poll();
            mUrlInfoMap.remove(url);
            mPwsResultMap.remove(url);
        }
    }

    /**
     * Register a StartupCallback to initialize the native portion of the JNI bridge.
     */
    private void registerNativeInitStartupCallback() {
        ThreadUtils.postOnUiThread(new Runnable() {
            @Override
            public void run() {
                BrowserStartupController.get(LibraryProcessType.PROCESS_BROWSER)
                        .addStartupCompletedObserver(new StartupCallback() {
                            @Override
                            public void onSuccess(boolean alreadyStarted) {
                                mNativePhysicalWebDataSourceAndroid = nativeInit();
                                for (UrlInfo urlInfo : getUrls()) {
                                    safeNotifyNativeListenersOnFound(urlInfo.getUrl());
                                }
                            }

                            @Override
                            public void onFailure() {
                                // Startup failed.
                            }
                        });
            }
        });
    }

    /**
     * Checks if we have initialized the native library and received a handle to the data source.
     * @return true if the data source handle is non-null.
     */
    private boolean isNativeInitialized() {
        return mNativePhysicalWebDataSourceAndroid != 0;
    }

    /**
     * Notify native listeners that a new Physical Web URL was discovered.
     * No message will be sent if the feature is in the Onboarding state.
     * @param url The Physical Web URL.
     */
    private void safeNotifyNativeListenersOnFound(final String url) {
        if (!isNativeInitialized() || PhysicalWeb.isOnboarding()) {
            return;
        }

        ThreadUtils.postOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (isNativeInitialized()) {
                    nativeOnFound(mNativePhysicalWebDataSourceAndroid, url);
                }
            }
        });
    }

    /**
     * Notify native listeners that a previously-discovered Physical Web URL is no longer nearby.
     * No message will be sent if the feature is in the Onboarding state.
     * @param url The Physical Web URL.
     */
    private void safeNotifyNativeListenersOnLost(final String url) {
        if (!isNativeInitialized() || PhysicalWeb.isOnboarding()) {
            return;
        }

        ThreadUtils.postOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (isNativeInitialized()) {
                    nativeOnLost(mNativePhysicalWebDataSourceAndroid, url);
                }
            }
        });
    }

    /**
     * Notify native listeners with an updated estimate of the distance to the broadcasting device.
     * No message will be sent if the feature is in the Onboarding state.
     * @param url The Physical Web URL.
     * @param distanceEstimate The updated distance estimate.
     */
    private void safeNotifyNativeListenersOnDistanceChanged(
            final String url, final double distanceEstimate) {
        if (!isNativeInitialized() || PhysicalWeb.isOnboarding()) {
            return;
        }

        ThreadUtils.postOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (isNativeInitialized()) {
                    nativeOnDistanceChanged(mNativePhysicalWebDataSourceAndroid, url,
                            distanceEstimate);
                }
            }
        });
    }

    @VisibleForTesting
    void overridePwsClientForTesting(PwsClient pwsClient) {
        mPwsClient = pwsClient;
    }

    @VisibleForTesting
    static void clearPrefsForTesting() {
        ContextUtils.getAppSharedPreferences().edit()
                .remove(PREFS_VERSION_KEY)
                .remove(PREFS_ALL_URLS_KEY)
                .remove(PREFS_PWS_RESULTS_KEY)
                .apply();
    }

    @VisibleForTesting
    static String getVersionKey() {
        return PREFS_VERSION_KEY;
    }

    @VisibleForTesting
    static int getVersion() {
        return PREFS_VERSION;
    }

    @VisibleForTesting
    boolean containsInAnyCache(String url) {
        return mNearbyUrls.contains(url)
                || mPwsResultMap.containsKey(url)
                || mUrlInfoMap.containsKey(url)
                || mUrlsSortedByTimestamp.contains(url);
    }

    @VisibleForTesting
    int getMaxCacheSize() {
        return MAX_CACHE_SIZE;
    }

    private native long nativeInit();
    private native void nativeAppendMetadataItem(long nativePhysicalWebCollection,
            String requestUrl, double distanceEstimate, long firstSeenTimestamp, String siteUrl,
            String iconUrl, String title, String description, String groupId);
    private native void nativeOnFound(long nativePhysicalWebDataSourceAndroid, String url);
    private native void nativeOnLost(long nativePhysicalWebDataSourceAndroid, String url);
    private native void nativeOnDistanceChanged(
            long nativePhysicalWebDataSourceAndroid, String url, double distanceChanged);
}

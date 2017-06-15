// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.metrics;

import org.chromium.base.ObserverList;
import org.chromium.base.ThreadUtils;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.content_public.browser.WebContents;

/**
 * Receives the page load metrics updates from AndroidPageLoadMetricsObserver, and notifies the
 * observers.
 *
 * Threading: everything here must happen on the UI thread.
 */
public class PageLoadMetrics {
    public static final String FIRST_CONTENTFUL_PAINT = "firstContentfulPaint";
    public static final String NAVIGATION_START = "navigationStart";
    public static final String LOAD_EVENT_START = "loadEventStart";

    /** Observer for page load metrics. */
    public interface Observer {
        /**
         * Called when the first contentful paint page load metric is available.
         *
         * @param webContents the WebContents this metrics is related to.
         * @param navigationStartTick Absolute navigation start time, as TimeTicks.
         * @param firstContentfulPaintMs Time to first contentful paint from navigation start.
         */
        public void onFirstContentfulPaint(
                WebContents webContents, long navigationStartTick, long firstContentfulPaintMs);

        /**
         * Called when the load event start metric is available.
         *
         * @param webContents the WebContents this metrics is related to.
         * @param navigationStartTick Absolute navigation start time, as TimeTicks.
         * @param loadEventStartMs Time to load event start from navigation start.
         */
        public void onLoadEventStart(
                WebContents webContents, long navigationStartTick, long loadEventStartMs);
    }

    private static ObserverList<Observer> sObservers;

    /** Adds an observer. */
    public static boolean addObserver(Observer observer) {
        ThreadUtils.assertOnUiThread();
        if (sObservers == null) sObservers = new ObserverList<>();
        return sObservers.addObserver(observer);
    }

    /** Removes an observer. */
    public static boolean removeObserver(Observer observer) {
        ThreadUtils.assertOnUiThread();
        if (sObservers == null) return false;
        return sObservers.removeObserver(observer);
    }

    @CalledByNative
    static void onFirstContentfulPaint(
            WebContents webContents, long navigationStartTick, long firstContentfulPaintMs) {
        ThreadUtils.assertOnUiThread();
        if (sObservers == null) return;
        for (Observer observer : sObservers) {
            observer.onFirstContentfulPaint(
                    webContents, navigationStartTick, firstContentfulPaintMs);
        }
    }

    @CalledByNative
    static void onLoadEventStart(
            WebContents webContents, long navigationStartTick, long loadEventStartMs) {
        ThreadUtils.assertOnUiThread();
        if (sObservers == null) return;
        for (Observer observer : sObservers) {
            observer.onLoadEventStart(webContents, navigationStartTick, loadEventStartMs);
        }
    }

    private PageLoadMetrics() {}
}

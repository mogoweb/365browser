// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.init;

import android.os.AsyncTask;

import org.chromium.base.Callback;
import org.chromium.base.ContextUtils;
import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.library_loader.LibraryLoader;
import org.chromium.base.library_loader.LibraryProcessType;
import org.chromium.base.library_loader.ProcessInitException;
import org.chromium.chrome.browser.ChromeActivitySessionTracker;
import org.chromium.chrome.browser.ChromeVersionInfo;
import org.chromium.components.variations.firstrun.VariationsSeedFetcher;
import org.chromium.content.browser.ChildProcessLauncher;

import java.util.concurrent.Executor;

/**
 * Runs asynchronous startup task that need to be run before the native side is
 * started. Currently it runs two tasks:
 * - Native library loading
 * - Fetching the variations seed on first run
 */
public abstract class AsyncInitTaskRunner {
    private boolean mFetchingVariations;
    private boolean mLibraryLoaded;

    private LoadTask mLoadTask;
    private FetchSeedTask mFetchSeedTask;

    @VisibleForTesting
    boolean shouldFetchVariationsSeedDuringFirstRun() {
        return ChromeVersionInfo.isOfficialBuild();
    }

    private class LoadTask extends AsyncTask<Void, Void, Boolean> {
        @Override
        protected Boolean doInBackground(Void... params) {
            try {
                LibraryLoader libraryLoader = LibraryLoader.get(LibraryProcessType.PROCESS_BROWSER);
                libraryLoader.ensureInitialized();
                // The prefetch is done after the library load for two reasons:
                // - It is easier to know the library location after it has
                // been loaded.
                // - Testing has shown that this gives the best compromise,
                // by avoiding performance regression on any tested
                // device, and providing performance improvement on
                // some. Doing it earlier delays UI inflation and more
                // generally startup on some devices, most likely by
                // competing for IO.
                // For experimental results, see http://crbug.com/460438.
                libraryLoader.asyncPrefetchLibrariesToMemory();
            } catch (ProcessInitException e) {
                return false;
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            mLibraryLoaded = result;
            tasksPossiblyComplete(mLibraryLoaded);
        }
    }

    private class FetchSeedTask extends AsyncTask<Void, Void, Void> {
        private final String mRestrictMode;

        public FetchSeedTask(String restrictMode) {
            mRestrictMode = restrictMode;
        }

        @Override
        protected Void doInBackground(Void... params) {
            VariationsSeedFetcher.get().fetchSeed(mRestrictMode);
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            mFetchingVariations = false;
            tasksPossiblyComplete(true);
        }
    }

    /**
     * Starts the background tasks.
     * @param allocateChildConnection Whether a spare child connection should be allocated. Set to
     *                                false if you know that no new renderer is needed.
     * @param fetchVariationSeed Whether to initialize the variations seed, if it hasn't been
     *                          initialized in a previous run.
     */
    public void startBackgroundTasks(boolean allocateChildConnection, boolean fetchVariationSeed) {
        ThreadUtils.assertOnUiThread();
        assert mLoadTask == null;
        if (fetchVariationSeed && shouldFetchVariationsSeedDuringFirstRun()) {
            mFetchingVariations = true;
            ChromeActivitySessionTracker sessionTracker =
                    ChromeActivitySessionTracker.getInstance();
            sessionTracker.getVariationsRestrictModeValue(new Callback<String>() {
                @Override
                public void onResult(String restrictMode) {
                    mFetchSeedTask = new FetchSeedTask(restrictMode);
                    mFetchSeedTask.executeOnExecutor(getExecutor());
                }
            });
        }

        if (allocateChildConnection) {
            ChildProcessLauncher.warmUp(ContextUtils.getApplicationContext());
        }
        mLoadTask = new LoadTask();
        mLoadTask.executeOnExecutor(getExecutor());
    }

    private void tasksPossiblyComplete(boolean result) {
        ThreadUtils.assertOnUiThread();

        if (!result) {
            mLoadTask.cancel(true);
            if (mFetchSeedTask != null) mFetchSeedTask.cancel(true);
            onFailure();
        }

        if (mLibraryLoaded && !mFetchingVariations) {
            onSuccess();
        }
    }

    @VisibleForTesting
    protected Executor getExecutor() {
        return AsyncTask.THREAD_POOL_EXECUTOR;
    }

    /**
     * Handle successful completion of the Async initialization tasks.
     */
    protected abstract void onSuccess();

    /**
     * Handle failed completion of the Async initialization tasks.
     */
    protected abstract void onFailure();
}

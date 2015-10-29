// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.webapps;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.ActivityManager.AppTask;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import org.chromium.base.ApplicationStatus;
import org.chromium.chrome.browser.document.DocumentUtils;

import java.io.File;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages directories created to store data for webapps.
 *
 * Directories managed by this class are all subdirectories of the app_WebappActivity/ directory,
 * which each WebappActivity using a directory named either for its Webapp's ID in Document mode,
 * or the index of the WebappActivity if it is a subclass of the WebappManagedActivity class (which
 * are used in pre-L devices to allow multiple WebappActivities launching).
 */
public class WebappDirectoryManager extends AsyncTask<Void, Void, Void> {
    private static final String TAG = "WebappDirectoryCleaner";
    private static final String WEBAPP_DIRECTORY_NAME = "WebappActivity";

    /** Whether or not the class has already started trying to clean up obsolete directories. */
    private static final AtomicBoolean sMustCleanUpOldDirectories = new AtomicBoolean(true);

    /** Scheme used for Intents fired for WebappActivity instances. */
    private final String mWebappScheme;

    /** Directories that will be deleted. */
    private final HashSet<File> mDirectoriesToDelete;

    /**
     * Constructs a WebappDirectoryManager, which will manage the deletion of directories
     * corresponding to webapps that no longer need their data.
     *
     * Should be called by WebappActivities after they have restored all the data they need from
     * their directory.
     *
     * @param directory Directory that must be deleted.  Corresponds to the current webapp.
     * @param webappScheme Scheme used for WebappActivities when building their Intent data URIs.
     * @param deleteOldDirectories Whether directories for old WebappActivities should be purged.
     */
    public WebappDirectoryManager(
            final File directory, String webappScheme, boolean deleteOldDirectories) {
        mWebappScheme = webappScheme;

        mDirectoriesToDelete = new HashSet<File>();
        mDirectoriesToDelete.add(directory);

        if (deleteOldDirectories && sMustCleanUpOldDirectories.getAndSet(false)) {
            assert Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
            Context context = ApplicationStatus.getApplicationContext();
            cleanUpOldWebappDirectories(
                    mDirectoriesToDelete, context.getApplicationInfo().dataDir);
        }
    }

    @Override
    protected Void doInBackground(Void... params) {
        for (File directory : mDirectoriesToDelete) {
            if (isCancelled()) return null;

            File[] files = directory.listFiles();
            if (files != null) {
                // Delete all the files in the directory.
                for (File file : files) {
                    if (!file.delete()) Log.e(TAG, "Failed to delete file: " + file.getPath());
                }
            }

            // Delete the directory itself.
            if (!directory.delete()) {
                Log.e(TAG, "Failed to delete directory: " + directory.getPath());
            }
        }
        return null;
    }

    /**
     * Removes all directories using the old pre-K directory structure, which used directories named
     * app_WebappActivity*.  Also deletes directories corresponding to WebappActivities that are no
     * longer listed in Android's recents, since these will be unable to restore their data, anyway.
     * @param directoriesToDelete Set to append directory names to.
     * @param baseDirectory Base directory of all of Chrome's persisted files.
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void cleanUpOldWebappDirectories(
            HashSet<File> directoriesToDelete, String baseDirectory) {
        Context context = ApplicationStatus.getApplicationContext();

        String webappDirectoryAppBaseName =
                context.getDir(WEBAPP_DIRECTORY_NAME, Context.MODE_PRIVATE).getName();

        // Figure out what WebappActivities are still listed in Android's recents menu.
        HashSet<String> liveWebapps = new HashSet<String>();
        ActivityManager manager =
                (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (AppTask task : manager.getAppTasks()) {
            Intent intent = DocumentUtils.getBaseIntentFromTask(task);
            if (intent == null) continue;

            Uri data = intent.getData();
            if (data != null && TextUtils.equals(mWebappScheme, data.getScheme())) {
                liveWebapps.add(data.getHost());
            }

            // WebappManagedActivities have titles from "WebappActivity0" through "WebappActivity9".
            ComponentName component = intent.getComponent();
            if (component != null) {
                String fullClassName = component.getClassName();
                int lastPeriodIndex = fullClassName.lastIndexOf(".");
                if (lastPeriodIndex != -1) {
                    String className = fullClassName.substring(lastPeriodIndex + 1);
                    if (className.startsWith(WEBAPP_DIRECTORY_NAME)
                            && className.length() > WEBAPP_DIRECTORY_NAME.length()) {
                        String activityIndex = className.substring(WEBAPP_DIRECTORY_NAME.length());
                        liveWebapps.add(activityIndex);
                    }
                }
            }
        }

        // Delete all webapp directories in the main directory.
        File dataDirectory = new File(baseDirectory);
        File[] files = dataDirectory.listFiles();
        if (files != null) {
            for (File file : files) {
                String filename = file.getName();
                if (!filename.startsWith(webappDirectoryAppBaseName)) continue;
                if (filename.length() == webappDirectoryAppBaseName.length()) continue;
                directoriesToDelete.add(file);
            }
        }

        // Clean out webapp directories no longer corresponding to tasks in Recents.
        File webappBaseDirectory = context.getDir(WEBAPP_DIRECTORY_NAME, Context.MODE_PRIVATE);
        if (webappBaseDirectory.exists()) {
            files = webappBaseDirectory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (!liveWebapps.contains(file.getName())) directoriesToDelete.add(file);
                }
            }
        }
    }

    /**
     * Returns the name of the directory for this webapp.
     * @param identifier ID for the webapp.  Used as a subdirectory name.
     * @return File for storing information about the webapp.
     */
    public static File getWebappDirectory(String identifier) {
        Context context = ApplicationStatus.getApplicationContext();
        File baseDirectory = context.getDir(WEBAPP_DIRECTORY_NAME, Context.MODE_PRIVATE);
        File webappDirectory = new File(baseDirectory, identifier);
        if (!webappDirectory.exists() && !webappDirectory.mkdir()) {
            Log.e(TAG, "Failed to create webapp directory.");
        }
        return webappDirectory;
    }
}

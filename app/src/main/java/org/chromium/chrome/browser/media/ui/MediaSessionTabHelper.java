// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.media.ui;

import org.chromium.base.ApplicationStatus;
import org.chromium.base.Log;
import org.chromium.chrome.browser.UrlUtilities;
import org.chromium.chrome.browser.tab.EmptyTabObserver;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabObserver;
import org.chromium.content_public.browser.WebContents;
import org.chromium.content_public.browser.WebContentsObserver;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * A tab helper responsible for enabling/disabling media controls and passing
 * media actions from the controls to the {@link org.chromium.content.browser.MediaSession}
 */
public class MediaSessionTabHelper {
    private static final String TAG = "cr.MediaSession";

    private Tab mTab;
    private WebContents mWebContents;
    private WebContentsObserver mWebContentsObserver;

    private MediaPlaybackListener mControlsListener = new MediaPlaybackListener() {
        @Override
        public void onPlay() {
            assert mWebContents != null;
            mWebContents.resumeMediaSession();
        }

        @Override
        public void onPause() {
            assert mWebContents != null;
            mWebContents.suspendMediaSession();
        }

        @Override
        public void onStop() {
            assert mWebContents != null;
            mWebContents.stopMediaSession();
        }
    };

    private WebContentsObserver createWebContentsObserver(WebContents webContents) {
        return new WebContentsObserver(webContents) {
            @Override
            public void destroy() {
                if (mTab == null) {
                    NotificationMediaPlaybackControls.clear();
                } else {
                    NotificationMediaPlaybackControls.hide(mTab.getId());
                }
                super.destroy();
            }

            @Override
            public void mediaSessionStateChanged(boolean isControllable, boolean isPaused) {
                assert mTab != null;
                if (!isControllable) {
                    NotificationMediaPlaybackControls.hide(mTab.getId());
                    return;
                }
                String origin = mTab.getUrl();
                try {
                    origin = UrlUtilities.getOriginForDisplay(new URI(origin), true);
                } catch (URISyntaxException e) {
                    Log.e(TAG, "Unable to parse the origin from the URL. "
                            + "Showing the full URL instead.");
                }
                NotificationMediaPlaybackControls.show(
                        ApplicationStatus.getApplicationContext(),
                        new MediaNotificationInfo(
                                mTab.getTitle(),
                                isPaused,
                                origin,
                                mTab.getId(),
                                mTab.isIncognito(),
                                mControlsListener));
            }
        };

    }

    private void setWebContents(WebContents webContents) {
        if (mWebContents == webContents) return;

        cleanupWebContents();
        mWebContents = webContents;
        if (mWebContents != null) mWebContentsObserver = createWebContentsObserver(mWebContents);
    }

    private void cleanupWebContents() {
        if (mWebContentsObserver != null) mWebContentsObserver.destroy();
        mWebContentsObserver = null;
        mWebContents = null;
    }

    private final TabObserver mTabObserver = new EmptyTabObserver() {
        @Override
        public void onContentChanged(Tab tab) {
            assert tab == mTab;
            setWebContents(tab.getWebContents());
        }

        @Override
        public void onDestroyed(Tab tab) {
            assert mTab == tab;

            cleanupWebContents();

            NotificationMediaPlaybackControls.hide(mTab.getId());
            mTab.removeObserver(this);
            mTab = null;
        }
    };

    private MediaSessionTabHelper(Tab tab) {
        mTab = tab;
        mTab.addObserver(mTabObserver);
        if (mTab.getWebContents() != null) setWebContents(tab.getWebContents());
    }

    /**
     * Creates the {@link MediaSessionTabHelper} for the given {@link Tab}.
     * @param tab the tab to attach the helper to.
     */
    public static void createForTab(Tab tab) {
        new MediaSessionTabHelper(tab);
    }
}

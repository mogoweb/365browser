/*
 * Copyright (c) 2015-2016, The Linux Foundation. All rights reserved.
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

package org.chromium.chrome.browser.preferences.website;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.content.browser.WebDefender;

import java.util.Arrays;
import java.util.HashMap;

public class WebDefenderVectorsRecyclerView extends RecyclerView {
    Context mContext;
    int mDefaultOptionIndex;
    WebDefender.TrackerDomain[] mTrackerDomains;
    HashMap<String, Integer> mModifiedTrackerDomains = new HashMap<>();
    String mRadioButtonOptions[];

    public void setUpdatedDomains(HashMap<String, Integer> updatedTrackerDomains) {
        if (updatedTrackerDomains != null)
            mModifiedTrackerDomains = updatedTrackerDomains;
    }

    public class VectorListAdapter
            extends RecyclerView.Adapter<VectorListAdapter.VectorViewHolder> {

        public class VectorViewHolder extends RecyclerView.ViewHolder {
            TextView mTitle;
            TextView mSummary;
            ImageView mCookieVector;
            ImageView mFingerprintVector;
            ImageView mStorageVector;
            ImageView mFontEnumVector;
            int mPosition;
            int mSelection;

            private void updateWebDefender(VectorViewHolder holder, int which, int pos) {
                int action = -1;

                switch (which) {
                    case 0:
                        action = WebDefender.TrackerDomain.PROTECTIVE_ACTION_UNBLOCK;
                        break;
                    case 1:
                        action = WebDefender.TrackerDomain.PROTECTIVE_ACTION_BLOCK_COOKIES;
                        break;
                    case 2:
                        action = WebDefender.TrackerDomain.PROTECTIVE_ACTION_BLOCK_URL;
                        break;
                    case 3:
                    default:
                        WebDefender.TrackerDomain domains[] = {
                                new WebDefender.TrackerDomain(mTrackerDomains[pos].mName,
                                        WebDefender.TrackerDomain.PROTECTIVE_ACTION_UNBLOCK,
                                        WebDefender.TrackerDomain.PROTECTIVE_ACTION_UNBLOCK, true,
                                        WebDefender.TrackerDomain.TRACKING_METHOD_NONE, false)
                        };
                        mModifiedTrackerDomains.put(domains[0].mName, action);
                        WebDefender.getInstance().resetProtectiveActionsForTrackerDomains(domains);
                        holder.mSelection = Arrays.asList(mRadioButtonOptions)
                                .indexOf(actionToSummary(action));
                        holder.mTitle.setTypeface(null, Typeface.NORMAL);
                        holder.mSummary.setTextColor(getColorForAction(action));
                        holder.mSummary.setText(
                                R.string.website_settings_webdefender_vector_system_default);
                        return;
                }

                holder.mSelection = Arrays.asList(mRadioButtonOptions)
                        .indexOf(actionToSummary(action));
                holder.mTitle.setTypeface(null, Typeface.BOLD);
                String summary = actionToSummary(action);
                holder.mSummary.setTextColor(getColorForAction(action));
                holder.mSummary.setText(summary);

                WebDefender.TrackerDomain domains[] = {
                        new WebDefender.TrackerDomain(mTrackerDomains[pos].mName, action, action,
                                true, WebDefender.TrackerDomain.TRACKING_METHOD_NONE, false)
                };
                mModifiedTrackerDomains.put(domains[0].mName, action);
                WebDefender.getInstance().overrideProtectiveActionsForTrackerDomains(domains);
            }

            public VectorViewHolder(View v) {
                super(v);
                mTitle = (TextView) v.findViewById(R.id.title);
                mSummary = (TextView) v.findViewById(R.id.summary);

                mCookieVector = (ImageView) v.findViewById(R.id.cookie_vector);
                mFingerprintVector = (ImageView) v.findViewById(R.id.fingerprinting_vector);
                mStorageVector = (ImageView) v.findViewById(R.id.html5_storage_vector);
                mFontEnumVector = (ImageView) v.findViewById(R.id.font_enumeration_vector);

                v.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            final int pos = mPosition;
                            final VectorViewHolder holder = VectorViewHolder.this;
                            new AlertDialog.Builder(mContext)
                                .setSingleChoiceItems(R.array.webdefender_vector_action,
                                    mSelection,
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            updateWebDefender(holder, which, pos);
                                            PrefServiceBridge.getInstance().requestReload();
                                            dialog.dismiss();
                                        }
                                    })
                                .show();
                        }
                    }
                );
            }
        }

        @Override
        public VectorViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(
                    R.layout.webdefender_vector_item, parent, false);
            return new VectorViewHolder(view);
        }

        private String actionToSummary(int action) {
            switch (action) {
                case WebDefender.TrackerDomain.PROTECTIVE_ACTION_UNBLOCK:
                    return getResources().getString(
                            R.string.website_settings_webdefender_vector_allow);
                case WebDefender.TrackerDomain.PROTECTIVE_ACTION_BLOCK_COOKIES:
                    return getResources().getString(
                            R.string.website_settings_webdefender_vector_strip);
                case WebDefender.TrackerDomain.PROTECTIVE_ACTION_BLOCK_URL:
                    return getResources().getString(
                            R.string.website_settings_webdefender_vector_not_connect);
                default:
                    return getResources().getString(
                            R.string.website_settings_webdefender_vector_system_default);
            }
        }

        @Override
        public void onBindViewHolder(VectorViewHolder holder, int position) {
            holder.mPosition = position;
            holder.mSelection = mDefaultOptionIndex;
            if (holder.mTitle != null) {
                holder.mTitle.setTypeface(null, Typeface.NORMAL);
                holder.mTitle.setText(mTrackerDomains[position].mName);
            }

            int action = (mTrackerDomains[position].mUsesUserDefinedProtectiveAction)
                    ? mTrackerDomains[position].mUserDefinedProtectiveAction
                    : mModifiedTrackerDomains.containsKey(mTrackerDomains[position].mName)
                    ? mModifiedTrackerDomains.get(mTrackerDomains[position].mName)
                    : mTrackerDomains[position].mProtectiveAction;

            String summary = actionToSummary(action);

            if (mTrackerDomains[position].mUsesUserDefinedProtectiveAction ||
                    mModifiedTrackerDomains.containsKey(mTrackerDomains[position].mName)) {
                holder.mSelection = Arrays.asList(mRadioButtonOptions).indexOf(summary);
                if (holder.mTitle != null &&
                        (mModifiedTrackerDomains.containsKey(mTrackerDomains[position].mName) &&
                         mModifiedTrackerDomains.get(mTrackerDomains[position].mName) >= 0)) {
                    holder.mTitle.setTypeface(null, Typeface.BOLD);
                }
            }

            if (holder.mSummary != null) {
                holder.mSummary.setText(summary);
                holder.mSummary.setTextColor(getColorForAction(action));
            }

            if (holder.mCookieVector != null) {
                if ((mTrackerDomains[position].mTrackingMethods &
                        WebDefender.TrackerDomain.TRACKING_METHOD_HTTP_COOKIES) != 0) {
                    holder.mCookieVector.setVisibility(VISIBLE);
                } else {
                    holder.mCookieVector.setVisibility(GONE);
                }
            }

            if (holder.mStorageVector != null) {
                if ((mTrackerDomains[position].mTrackingMethods &
                        WebDefender.TrackerDomain.TRACKING_METHOD_HTML5_LOCAL_STORAGE) != 0) {
                    holder.mStorageVector.setVisibility(VISIBLE);
                } else {
                    holder.mStorageVector.setVisibility(GONE);
                }
            }

            if (holder.mFingerprintVector != null)
                if ((mTrackerDomains[position].mTrackingMethods &
                        WebDefender.TrackerDomain.TRACKING_METHOD_CANVAS_FINGERPRINT) != 0) {
                    holder.mFingerprintVector.setVisibility(VISIBLE);
                } else {
                    holder.mFingerprintVector.setVisibility(GONE);
                }

            if (holder.mFontEnumVector != null)
                holder.mFontEnumVector.setVisibility(GONE);
        }

        @Override
        public int getItemCount() {
            if (mTrackerDomains != null)
                return mTrackerDomains.length;

            return 0;
        }
    }

    private int getColorForAction(int action) {
        switch (action) {
            case WebDefender.TrackerDomain.PROTECTIVE_ACTION_UNBLOCK:
                return SmartProtectDetailsPreferences.GREEN;
            case WebDefender.TrackerDomain.PROTECTIVE_ACTION_BLOCK_COOKIES:
                return SmartProtectDetailsPreferences.YELLOW;
            case WebDefender.TrackerDomain.PROTECTIVE_ACTION_BLOCK_URL:
                return SmartProtectDetailsPreferences.RED;
            default:
                return SmartProtectDetailsPreferences.GRAY;
        }
    }

    public HashMap<String, Integer> getUpdatedDomains() {
        if (mModifiedTrackerDomains.isEmpty()) return null;
        return mModifiedTrackerDomains;
    }

    public void updateVectorArray(WebDefender.TrackerDomain[] trackerDomains) {
        mTrackerDomains = trackerDomains;
    }

    /**
     * Constructs a new instance of WebDefender vectors recycler view.
     */
    public WebDefenderVectorsRecyclerView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mContext = context;

        mRadioButtonOptions = getResources()
                .getStringArray(R.array.webdefender_vector_action);

        mDefaultOptionIndex = Arrays.asList(mRadioButtonOptions).indexOf(
                getResources().getString(
                        R.string.website_settings_webdefender_vector_system_default));

        setLayoutManager(new LinearLayoutManager(context));
        scrollToPosition(0);

        VectorListAdapter adapter = new VectorListAdapter();
        setAdapter(adapter);
        adapter.notifyDataSetChanged();

    }
}

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

import android.content.Context;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.CompoundButton;

import org.chromium.chrome.R;
import org.chromium.content.browser.SecureConnect;

import java.util.ArrayList;
import java.util.HashMap;

public class SecureConnectDetails extends RecyclerView {
    ArrayList<SecureConnect.URLInfo> mRulesets;
    HashMap<String, Boolean> mModifiedRulesets = new HashMap<>();

    public void setUpdatedRulesets(HashMap<String, Boolean> updatedRulesets) {
        if (updatedRulesets != null)
            mModifiedRulesets = updatedRulesets;
    }

    public class RulesetAdapter
            extends RecyclerView.Adapter<RulesetAdapter.DetailsViewHolder> {

        public class DetailsViewHolder extends RecyclerView.ViewHolder {
            SecureConnectDetailItem mItem;

            private void updateSecureConnect(DetailsViewHolder holder, boolean isChecked) {
                String rule = mRulesets.get(getAdapterPosition()).mRulesetName;
                mModifiedRulesets.put(rule, isChecked);
                SecureConnectPreferenceHandler.updateRuleset(rule, isChecked);
            }

            public DetailsViewHolder(SecureConnectDetailItem item) {
                super(item);
                mItem = item;
            }
        }

        @Override
        public DetailsViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            SecureConnectDetailItem item = (SecureConnectDetailItem) LayoutInflater.from(
                    parent.getContext()).inflate(R.layout.secure_connect_detail_item,
                    parent, false);
            return new DetailsViewHolder(item);
        }

        @Override
        public void onBindViewHolder(final DetailsViewHolder holder, int position) {
            String ruleSet = mRulesets.get(position).mRulesetName;
            holder.mItem.setTitle(ruleSet);
            boolean checked = mModifiedRulesets.containsKey(ruleSet)
                        ? mModifiedRulesets.get(ruleSet)
                        : mRulesets.get(position).mRulesetEnabled;
            holder.mItem.setListener(null); // Clear the old listener if there is one.
            holder.mItem.setChecked(checked);
            holder.mItem.setListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    holder.updateSecureConnect(holder, isChecked);
                }
            });
        }

        @Override
        public int getItemCount() {
            if (mRulesets != null) return mRulesets.size();
            return 0;
        }
    }

    public HashMap<String, Boolean> getUpdatedDomains() {
        if (mModifiedRulesets.isEmpty()) return null;
        return mModifiedRulesets;
    }

    public void updateRulsetArray(ArrayList<SecureConnect.URLInfo> rulesets) {
        mRulesets = rulesets;
    }

    /**
     * Constructs a new instance of WebDefender vectors recycler view.
     */
    public SecureConnectDetails(Context context, AttributeSet attrs) {
        super(context, attrs);

        setLayoutManager(new LinearLayoutManager(context));
        scrollToPosition(0);

        RulesetAdapter adapter = new RulesetAdapter();
        setAdapter(adapter);
        adapter.notifyDataSetChanged();
    }
}

// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp.cards;

import android.support.v7.widget.RecyclerView;
import android.view.View;

import org.chromium.base.Callback;

/**
 * Holds metadata about an item we want to display on the NTP. An item can be anything that will be
 * displayed on the NTP {@link RecyclerView}.
 */
public class NewTabPageViewHolder extends RecyclerView.ViewHolder {
    /**
     * A single instance of {@link UpdateLayoutParamsCallback} that can be reused as it has no
     * state.
     */
    public static final UpdateLayoutParamsCallback UPDATE_LAYOUT_PARAMS_CALLBACK =
            new UpdateLayoutParamsCallback();

    /**
     * Constructs a {@link NewTabPageViewHolder} used to display an part of the NTP (e.g., header,
     * article snippet, above-the-fold view, etc.)
     *
     * @param itemView The {@link View} for this item
     */
    public NewTabPageViewHolder(View itemView) {
        super(itemView);
    }

    /**
     * Whether this item can be swiped and dismissed. The default implementation disallows it.
     * @return {@code true} if the item can be swiped and dismissed, {@code false} otherwise.
     */
    public boolean isDismissable() {
        return false;
    }

    /**
     * Update the layout params for the view holder.
     */
    public void updateLayoutParams() {
    }

    protected RecyclerView.LayoutParams getParams() {
        return (RecyclerView.LayoutParams) itemView.getLayoutParams();
    }

    /**
     * A callback to perform a partial bind on a {@link NewTabPageViewHolder}.
     * @see org.chromium.chrome.browser.ntp.cards.InnerNode#notifyItemChanged(int,
     * PartialBindCallback)
     *
     * This empty class is used to strengthen type assertions, as those would be less useful with a
     * generic class due to type erasure.
     */
    public abstract static class PartialBindCallback extends Callback<NewTabPageViewHolder> {}

    /**
     * Callback to update the layout params for the view holder.
     */
    public static class UpdateLayoutParamsCallback extends PartialBindCallback {
        @Override
        public void onResult(NewTabPageViewHolder holder) {
            holder.updateLayoutParams();
        }
    }
}

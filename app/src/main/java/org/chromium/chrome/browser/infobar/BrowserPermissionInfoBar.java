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
package org.chromium.chrome.browser.infobar;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import org.chromium.chrome.R;

public class BrowserPermissionInfoBar extends PermissionInfoBar {
    private ArrayAdapter<String> mSpinnerAdapter;
    private Spinner mSpinner;
    private final boolean mUseSpinner;

    public BrowserPermissionInfoBar(int iconDrawableId, Bitmap iconBitmap, String message,
                                    String linkText, String primaryButtonText,
                                    String secondaryButtonText,
                                    boolean showPersistenceToggle) {
        super(iconDrawableId, iconBitmap, message,
                linkText, primaryButtonText, secondaryButtonText, showPersistenceToggle);
        mUseSpinner = showPersistenceToggle;
    }

    @Override
    protected boolean isPersistSwitchOn() {
        if (mUseSpinner && mSpinner != null) {
            String selection = mSpinner.getSelectedItem().toString();
            return !TextUtils.equals(selection, getContext().getResources()
                    .getString(R.string.infobar_permission_allow_for_now));
        } else {
            return false;
        }

    }

    private void setupSpinner(InfoBarLayout layout) {
        Resources res = getContext().getResources();
        mSpinnerAdapter = new InfoBarControlLayout.InfoBarArrayAdapter<>(
                getContext(), res.getString(R.string.infobar_permission_spinner_title));
        InfoBarControlLayout controlLayout = layout.addControlLayout();
        mSpinner = controlLayout.addSpinner(R.id.infobar_extra_check, mSpinnerAdapter);
        mSpinnerAdapter.addAll(res.getString(R.string.infobar_permission_allow_forever),
                res.getString(R.string.infobar_permission_allow_for_24),
                res.getString(R.string.infobar_permission_allow_for_now));
        mSpinner.setSelection(0);
    }

    @Override
    public void createContent(InfoBarLayout layout) {
        super.createContent(layout);

        if (mUseSpinner) setupSpinner(layout);
    }
    @Override
    protected void onButtonClickedInternal(boolean isPrimaryButton) {
        if(isPrimaryButton && mSpinner != null) {
            Resources res = getContext().getResources();
            String selection = mSpinner.getSelectedItem().toString();
            if (res.getString(R.string.infobar_permission_allow_forever).
                    equals(selection)) {
                onButtonClicked(ActionType.OK);
            } else if (res.getString(R.string.infobar_permission_allow_for_now).equals(selection)) {
                onButtonClicked(ActionType.OK);
            } else if (res.getString(R.string.infobar_permission_allow_for_24).equals(selection)) {
                onButtonClicked(ActionType.OK_24H);
            } else {
                onCloseButtonClicked();
            }
        } else {
            onButtonClicked(ActionType.CANCEL);
        }
    }
}

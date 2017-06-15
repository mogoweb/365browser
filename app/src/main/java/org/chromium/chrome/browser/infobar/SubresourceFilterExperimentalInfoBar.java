// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.infobar;

import android.support.v7.widget.SwitchCompat;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ResourceId;
import org.chromium.ui.widget.ButtonCompat;

/**
 * This infobar appears when the user proceeds through a Safe Browsing warning interstitial that is
 * displayed when the site ahead contains deceptive embedded content. It explains to the user that
 * some subresources were filtered and presents the "Details" link. If the link is pressed, the full
 * infobar with detailed text, toggle, and action button appears. The toggle gives the user an
 * ability to reload the page with the content we've blocked previously.
 */
public class SubresourceFilterExperimentalInfoBar
        extends ConfirmInfoBar implements OnCheckedChangeListener {
    private final String mMessage;
    private final String mFollowUpMessage;
    private final String mOKButtonText;
    private final String mReloadButtonText;
    private final String mToggleText;
    private boolean mShowExplanation;
    private boolean mReloadIsToggled;
    private ButtonCompat mButton;

    @CalledByNative
    private static InfoBar show(int enumeratedIconId, String message, String oKButtonText,
            String reloadButtonText, String toggleText, String followUpMessage) {
        return new SubresourceFilterExperimentalInfoBar(
                ResourceId.mapToDrawableId(enumeratedIconId), message, oKButtonText,
                reloadButtonText, toggleText, followUpMessage);
    }

    private SubresourceFilterExperimentalInfoBar(int iconDrawbleId, String message,
            String oKButtonText, String reloadButtonText, String toggleText,
            String followUpMessage) {
        super(iconDrawbleId, null, message, null, null, null); //, oKButtonText, reloadButtonText);
        mFollowUpMessage = followUpMessage;
        mMessage = message;
        mOKButtonText = oKButtonText;
        mReloadButtonText = reloadButtonText;
        mToggleText = toggleText;
    }

    @Override
    public void createContent(InfoBarLayout layout) {
        super.createContent(layout);
        if (mShowExplanation) {
            layout.setMessage(mFollowUpMessage);
            setButtons(layout, mOKButtonText, null);
            InfoBarControlLayout controlLayout = layout.addControlLayout();

            // Add a toggle button and ensure the button text is changed when the toggle changes.
            View switchView = controlLayout.addSwitch(
                    0, 0, mToggleText, R.id.subresource_filter_infobar_toggle, false);
            SwitchCompat toggle =
                    (SwitchCompat) switchView.findViewById(R.id.subresource_filter_infobar_toggle);
            toggle.setOnCheckedChangeListener(this);
            mButton = layout.getPrimaryButton();

            // Ensure that the button does not resize when switching text.
            // TODO(csharrison,dfalcantara): setMinEms is wrong. Code should measure both pieces of
            // text and set the min width using those measurements. See crbug.com/708719.
            mButton.setMinEms(Math.max(mOKButtonText.length(), mReloadButtonText.length()));
        } else {
            String link = layout.getContext().getString(R.string.details_link);
            layout.setMessage(mMessage);
            layout.appendMessageLinkText(link);
        }
    }

    @Override
    public void onLinkClicked() {
        mShowExplanation = true;
        replaceView(createView());
        super.onLinkClicked();
    }

    @Override
    public void onButtonClicked(final boolean isPrimaryButton) {
        onButtonClicked(mReloadIsToggled ? ActionType.CANCEL : ActionType.OK);
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        assert mButton != null;
        mButton.setText(isChecked ? mReloadButtonText : mOKButtonText);
        mReloadIsToggled = isChecked;
    }
}

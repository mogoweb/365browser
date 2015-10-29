// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.infobar;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ResourceId;
import org.chromium.chrome.browser.password_manager.Credential;
import org.chromium.chrome.browser.signin.AccountManagementFragment;

/**
 * An infobar offers the user the ability to choose credentials for
 * authentication. User is presented with username along with avatar and
 * full name in case they are available.
 */
public class AccountChooserInfoBar extends InfoBar {
    private long mNativePtr;
    private final Credential[] mCredentials;
    private final ImageView[] mAvatarViews;

    /**
     * Creates and shows the infobar wich allows user to choose credentials for login.
     * @param enumeratedIconId Enum ID corresponding to the icon that the infobar will show.
     * @param credentials Credentials to display in the infobar.
     */
    @CalledByNative
    private static InfoBar show(int enumeratedIconId, Credential[] credentials) {
        return new AccountChooserInfoBar(ResourceId.mapToDrawableId(enumeratedIconId), credentials);
    }

    /**
     * Creates and shows the infobar  which allows user to choose credentials.
     * @param iconDrawableId Drawable ID corresponding to the icon that the infobar will show.
     * @param credentials Credentials to display in the infobar.
     */
    public AccountChooserInfoBar(int iconDrawableId, Credential[] credentials) {
        super(null /* Infobar Listener */, iconDrawableId, null /* bitmap*/,
                null /* message to show */);
        mCredentials = credentials.clone();
        mAvatarViews = new ImageView[mCredentials.length];
    }

    @Override
    public void onButtonClicked(boolean isPrimaryButton) {
        onCloseButtonClicked();
    }

    @Override
    public void createContent(InfoBarLayout layout) {
        layout.setMessage(getContext().getString(R.string.account_chooser_infobar_title));
        createAccountsView(layout);
        layout.setButtons(getContext().getString(R.string.no_thanks), null);
    }

    private void createAccountsView(InfoBarLayout layout) {
        ViewGroup accountsView = (ViewGroup) LayoutInflater.from(getContext()).inflate(
                R.layout.account_chooser_infobar_list, null, false);
        ArrayAdapter<Credential> adapter = generateAccountsArrayAdapter(getContext(), mCredentials);
        ListView listView = (ListView) accountsView.findViewById(R.id.account_list);
        listView.setAdapter(adapter);
        float numVisibleItems = adapter.getCount() > 2 ? 2.5f : adapter.getCount();
        int listViewHeight = (int) (numVisibleItems * getContext().getResources().getDimension(
                R.dimen.account_chooser_infobar_item_height));
        listView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, listViewHeight));
        layout.setCustomContent(accountsView);
    }

    private ArrayAdapter<Credential> generateAccountsArrayAdapter(
            Context context, Credential[] credentials) {
        return new ArrayAdapter<Credential>(context, 0, credentials) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView =
                            LayoutInflater.from(getContext())
                                    .inflate(R.layout.account_chooser_infobar_item, parent, false);
                } else {
                    int oldPosition = (int) convertView.getTag();
                    mAvatarViews[oldPosition] = null;
                }
                convertView.setTag(position);
                ImageView avatarView = (ImageView) convertView.findViewById(R.id.profile_image);
                mAvatarViews[position] = avatarView;
                TextView usernameView = (TextView) convertView.findViewById(R.id.username);
                TextView smallTextView = (TextView) convertView.findViewById(R.id.display_name);
                Credential credential = getItem(position);
                usernameView.setText(credential.getUsername());
                String smallText = credential.getFederation().isEmpty()
                        ? credential.getFederation()
                        : credential.getDisplayName();
                smallTextView.setText(smallText);
                Bitmap avatar = credential.getAvatar();
                if (avatar != null) {
                    avatarView.setImageBitmap(avatar);
                } else {
                    avatarView.setImageResource(R.drawable.account_management_no_picture);
                }
                final int currentCredentialIndex = credential.getIndex();
                final int credentialType = credential.getType();
                convertView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        nativeOnCredentialClicked(
                                mNativePtr, currentCredentialIndex, credentialType);
                    }
                });
                return convertView;
            }
        };
    }

    @CalledByNative
    private void imageFetchComplete(int index, Bitmap avatarBitmap) {
        avatarBitmap = AccountManagementFragment.makeRoundUserPicture(avatarBitmap);
        if (index >= 0 && index < mCredentials.length) {
            mCredentials[index].setBitmap(avatarBitmap);
        }
        if (index >= 0 && index < mAvatarViews.length && mAvatarViews[index] != null) {
            mAvatarViews[index].setImageBitmap(avatarBitmap);
        }
    }

    @CalledByNative
    private void setNativePtr(long nativePtr) {
        mNativePtr = nativePtr;
    }

    @Override
    protected void onNativeDestroyed() {
        mNativePtr = 0;
        super.onNativeDestroyed();
    }

    private native void nativeOnCredentialClicked(
            long nativeAccountChooserInfoBar, int credentialId, int credentialType);
}

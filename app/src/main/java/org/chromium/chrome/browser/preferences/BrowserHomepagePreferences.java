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
package org.chromium.chrome.browser.preferences;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.preference.DialogPreference;
import android.text.InputType;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.incognito.IncognitoOnlyModeUtil;
import org.chromium.chrome.browser.partnercustomizations.HomepageManager;
import org.chromium.chrome.browser.partnercustomizations.PartnerBrowserCustomizations;
import org.chromium.components.url_formatter.UrlFormatter;


import java.util.ArrayList;
import java.util.List;

public class BrowserHomepagePreferences extends DialogPreference {
    public static final String CURRENT_URL = "CURRENT_URL";
    public static final String ABOUT_BLANK = "about:blank";
    private HomePageAdapter mHomePageAdapter;
    private HomepageManager mHomepageManager;
    protected String mCurrentURL;
    private String mDisplayString;
    private enum HomepageType {
        PROVIDER_DEFAULT_PAGE,
        CURRENT_PAGE,
        BLANK,
        MOST_VISITED_PAGE,
        USER_CUSTOM_PAGE
    };

    public BrowserHomepagePreferences(Context context, AttributeSet attrs) {
        super(context, attrs);
        setEnabled(true);
        mHomepageManager = HomepageManager.getInstance(context);
        mHomePageAdapter = new HomePageAdapter(context);
    }

    private void updateHomePage(String shortName, String url){
        mDisplayString = TextUtils.isEmpty(url) ? shortName : shortName + " ("+url+ ")";
        setSummary(mDisplayString);
    }

    public String getDisplayString() {
        return mDisplayString;
    }

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        super.onPrepareDialogBuilder(builder);
        builder.setNegativeButton(null, null)
                .setPositiveButton(R.string.close, null)
                .setSingleChoiceItems(mHomePageAdapter, 0, null);
    }

    public void setCurrentURL(String currentURL) {
        if (currentURL != null) {
            mCurrentURL = UrlFormatter.fixupUrl(currentURL);
            if (mHomePageAdapter.getIndex(HomepageType.CURRENT_PAGE) == -1) {
                mHomePageAdapter.addCurrentUrlOption(mCurrentURL);
            } else {
                HomePageUrl curr = (HomePageUrl) mHomePageAdapter.getItem(
                        mHomePageAdapter.getIndex(HomepageType.CURRENT_PAGE));
                curr.setUrl(currentURL);
            }
        }
    }

    static class HomePageUrl {
        private final HomepageType mType;
        private String mUrl;
        private final String mShortName;
        private final int mPosition;

        public HomePageUrl(HomepageType type, String shortName, String url, int position) {
            mType = type;
            mShortName = shortName;
            mUrl = url;
            mPosition = position;
        }

        public HomepageType getHomepageType() {
            return mType;
        }

        public String getShortName() {
            return mShortName;
        }

        public String getUrl() {
            return mUrl;
        }

        protected void setUrl(String url) {
            mUrl = url;
        }

        public int getPosition() {
            return mPosition;
        }

    }

    class HomePageAdapter extends BaseAdapter implements View.OnClickListener {
        private Context mContext;
        private List<HomePageUrl> mHomePageUrls;
        private LayoutInflater mLayoutInflater;
        private int mSelectedPosition;

        public HomePageAdapter(Context ctx) {
            mContext = ctx;
            mHomePageUrls = new ArrayList<HomePageUrl>();
            mLayoutInflater = (LayoutInflater) mContext.getSystemService(
                    Context.LAYOUT_INFLATER_SERVICE);
            initOptions();
        }

        private void initOptions() {
            Resources res = mContext.getResources();
            // DefaultPage(Provider/default)
            if (mHomepageManager.getPrefHomepageEnabled()) {
                mHomePageUrls.add(new HomePageUrl(HomepageType.PROVIDER_DEFAULT_PAGE,
                        res.getString(R.string.default_homepage),
                        PartnerBrowserCustomizations.getHomePageUrl(), mHomePageUrls.size()));
            } else {
                mHomePageUrls.add(new HomePageUrl(HomepageType.PROVIDER_DEFAULT_PAGE,
                        res.getString(R.string.default_homepage),
                        res.getString(R.string.default_homepage_url), mHomePageUrls.size()));
            }
            // Most Visited
            if (!IncognitoOnlyModeUtil.getInstance().isIncognitoOnlyModeEnabled()) {
                mHomePageUrls.add(new HomePageUrl(HomepageType.MOST_VISITED_PAGE,
                    res.getString(R.string.most_visited_sites_homepage), "", mHomePageUrls.size()));
            }
            // Current URL
            if (mCurrentURL != null) {
                addCurrentUrlOption(mCurrentURL);
            }
            // Blank
            mHomePageUrls.add(new HomePageUrl(HomepageType.BLANK,
                    res.getString(R.string.blank_homepage), ABOUT_BLANK, mHomePageUrls.size()));
            // User Custom Page
            String customURL = mHomepageManager.getPrefHomepageCustomUri();
            if (customURL.equals("") || customURL.equals(ABOUT_BLANK))
                customURL = res.getString(R.string.options_homepage_edit_title);
            mHomePageUrls.add(new HomePageUrl(HomepageType.USER_CUSTOM_PAGE,
                    res.getString(R.string.other_homepage), customURL, mHomePageUrls.size()));

            int savedIndex;
            if (mHomepageManager.getPrefHomepageUseDefaultUri())
                savedIndex = getIndex(HomepageType.PROVIDER_DEFAULT_PAGE);
            else
                savedIndex = getIndexForCustomURI();
            homePageSelected(savedIndex);
        }

        protected void addCurrentUrlOption(String url ) {
            Resources res = mContext.getResources();
            if (mCurrentURL != null) {
                mHomePageUrls.add(new HomePageUrl(HomepageType.CURRENT_PAGE,
                        res.getString(R.string.current_page_homepage), url, mHomePageUrls.size()));
                notifyDataSetChanged();
            }
        }

        private int getIndex(HomepageType hpt) {
            for (int i = 0; i < mHomePageUrls.size(); i++) {
                HomepageType type = ((HomePageUrl) getItem(i)).getHomepageType();
                if (type == hpt)
                    return i;
            }
            return -1;
        }

        private int getIndexForCustomURI() {
            String currentHomePage = mHomepageManager.getPrefHomepageCustomUri();
            if (currentHomePage.equals(ABOUT_BLANK)) {
                return getIndex(HomepageType.BLANK);
            } else if (currentHomePage.equals("")) {
                return getIndex(HomepageType.MOST_VISITED_PAGE);
            } else if (currentHomePage.equals(mCurrentURL)) {
                return getIndex(HomepageType.CURRENT_PAGE);
            } else {
                return getIndex(HomepageType.USER_CUSTOM_PAGE);
            }
        }

        @Override
        public int getCount() {
            return mHomePageUrls.size();
        }

        @Override
        public Object getItem(int position) {
            return mHomePageUrls.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Resources res = mContext.getResources();
            View view = convertView;
            if (convertView == null) {
                view = mLayoutInflater.inflate(R.layout.search_engine, null);
            }
            view.setOnClickListener(this);
            view.setTag(position);
            final boolean selected = position == mSelectedPosition;
            RadioButton radioButton = (RadioButton) view.findViewById(R.id.radiobutton);
            radioButton.setChecked(selected);
            TextView description = (TextView) view.findViewById(R.id.description);
            HomePageUrl hp = (HomePageUrl) getItem(position);
            description.setText(hp.getShortName());
            TextView link = (TextView) view.findViewById(R.id.link);
            String url = TextUtils.isEmpty(hp.getUrl()) ? "" : hp.getUrl();
            if (selected && !url.isEmpty()) {
                link.setVisibility(View.VISIBLE);
                ForegroundColorSpan linkSpan = new ForegroundColorSpan(
                        res.getColor(R.color.pref_accent_color));
                SpannableString messageWithLink = new SpannableString(url);
                messageWithLink.setSpan(linkSpan, 0, messageWithLink.length(), 0);
                link.setText(messageWithLink);
            } else {
                link.setVisibility(View.GONE);
            }
            // link click only for custom page
            if (hp.getHomepageType() == HomepageType.USER_CUSTOM_PAGE)
                link.setOnClickListener(this);
            return view;
        }

        private void homePageSelected(int position) {
            mSelectedPosition = position;
            HomePageUrl hp = (HomePageUrl) getItem(position);
            String url = TextUtils.isEmpty(hp.getUrl()) ? "" : hp.getUrl();
            BrowserHomepagePreferences.this.updateHomePage(hp.getShortName(), url);
            if (getIndex(HomepageType.PROVIDER_DEFAULT_PAGE) == position) {
                mHomepageManager.setPrefHomepageUseDefaultUri(true);
            } else {
                mHomepageManager.setPrefHomepageCustomUri(url);
                mHomepageManager.setPrefHomepageUseDefaultUri(false);
            }
            notifyDataSetChanged();
        }

        private void showDialog(String title, final HomePageUrl hp) {
            final EditText editText = new EditText(mContext);
            String url = TextUtils.isEmpty(hp.getUrl()) ? "" : hp.getUrl();
            editText.setText(url);
            editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
            editText.setSelectAllOnFocus(true);
            editText.setSingleLine(true);
            final AlertDialog dialog = new AlertDialog.Builder(mContext)
                    .setTitle(title)
                    .setView(editText)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            String homepage = editText.getText().toString().trim();
                            hp.setUrl(UrlFormatter.fixupUrl(homepage));
                            homePageSelected(getIndex(HomepageType.USER_CUSTOM_PAGE));
                        }
                    })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            InputMethodManager imm = (InputMethodManager)mContext.getSystemService(
                                    Context.INPUT_METHOD_SERVICE);
                            imm.hideSoftInputFromWindow(editText.getWindowToken(), 0);
                            dialog.cancel();
                        }
                    })
                    .create();
            editText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                        return true;
                    }
                    return false;
                }
            });
            dialog.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            dialog.show();
        }

        // OnClickListener
        @Override
        public void onClick(View view) {
            if (view.getTag() == null) {
                View parent = (View) ((view.getParent()).getParent());
                int position = (int) parent.getTag();
                HomePageUrl hp = (HomePageUrl) getItem(position);
                if (hp.getHomepageType() == HomepageType.USER_CUSTOM_PAGE)
                    showDialog(mContext.getResources().getString(
                            R.string.options_homepage_edit_label)
                            , hp);
                return;
            }
            int position = (int) view.getTag();
            HomePageUrl currOption = (HomePageUrl) getItem(mSelectedPosition);
            // Auto show the dialog when USER_CUSTOM_PAGE is already selected
            if (currOption.getHomepageType() == HomepageType.USER_CUSTOM_PAGE &&
                    position == currOption.getPosition()) {
                showDialog(mContext.getResources().getString(
                        R.string.options_homepage_edit_label), currOption);
            } else {
                homePageSelected(position);
            }
        }
    }
}

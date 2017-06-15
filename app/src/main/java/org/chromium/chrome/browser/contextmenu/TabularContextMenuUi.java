// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.contextmenu;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.support.design.widget.TabLayout;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.Callback;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;

import java.util.ArrayList;
import java.util.List;

/**
 * A custom dialog that separates each group into separate tabs. It uses a dialog instead.
 */
public class TabularContextMenuUi implements ContextMenuUi, AdapterView.OnItemClickListener {
    private Dialog mDialog;
    private Callback<Integer> mCallback;
    private int mMenuItemHeight;
    private ImageView mHeaderImageView;
    private Runnable mOnShareItemClicked;

    public TabularContextMenuUi(Runnable onShareItemClicked) {
        mOnShareItemClicked = onShareItemClicked;
    }

    @Override
    public void displayMenu(Activity activity, ContextMenuParams params,
            List<Pair<Integer, List<ContextMenuItem>>> items, Callback<Integer> onItemClicked,
            final Runnable onMenuShown, final Runnable onMenuClosed) {
        mCallback = onItemClicked;
        mDialog = createDialog(activity, params, items);

        mDialog.getWindow().setBackgroundDrawable(ApiCompatibilityUtils.getDrawable(
                activity.getResources(), R.drawable.white_with_rounded_corners));

        mDialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialogInterface) {
                onMenuShown.run();
            }
        });

        mDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                onMenuClosed.run();
            }
        });

        mDialog.show();
    }

    /**
     * Returns the fully complete dialog based off the params and the itemGroups.
     * @param activity Used to inflate the dialog.
     * @param params Used to get the header title.
     * @param itemGroups If there is more than one group it will create a paged view.
     * @return Returns a final dialog that does not have a background can be displayed using
     *         {@link AlertDialog#show()}.
     */
    private Dialog createDialog(Activity activity, ContextMenuParams params,
            List<Pair<Integer, List<ContextMenuItem>>> itemGroups) {
        Dialog dialog = new Dialog(activity);
        dialog.setContentView(createPagerView(activity, params, itemGroups));
        return dialog;
    }

    /**
     * Creates a ViewPageAdapter based off the given list of views.
     * @param activity Used to inflate the new ViewPager
     * @param params Used to get the header text.
     * @param itemGroups The list of views to put into the ViewPager. The string is the title of the
     *                   tab
     * @return Returns a complete tabular context menu view.
     */
    @VisibleForTesting
    View createPagerView(Activity activity, ContextMenuParams params,
            List<Pair<Integer, List<ContextMenuItem>>> itemGroups) {
        View view = LayoutInflater.from(activity).inflate(R.layout.tabular_context_menu, null);

        List<Pair<String, ViewGroup>> viewGroups = new ArrayList<>();
        int maxCount = 0;
        for (int i = 0; i < itemGroups.size(); i++) {
            Pair<Integer, List<ContextMenuItem>> itemGroup = itemGroups.get(i);
            maxCount = Math.max(maxCount, itemGroup.second.size());
        }
        for (int i = 0; i < itemGroups.size(); i++) {
            Pair<Integer, List<ContextMenuItem>> itemGroup = itemGroups.get(i);
            // TODO(tedchoc): Pass the ContextMenuGroup identifier to determine if it's an image.
            boolean isImageTab = itemGroup.first == R.string.contextmenu_image_title;
            viewGroups.add(new Pair<>(activity.getString(itemGroup.first),
                    createContextMenuPageUi(
                            activity, params, itemGroup.second, isImageTab, maxCount)));
        }
        if (itemGroups.size() == 1) {
            viewGroups.get(0)
                    .second.getChildAt(0)
                    .findViewById(R.id.context_header_layout)
                    .setBackgroundResource(R.color.google_grey_100);
        }

        TabularContextMenuViewPager pager =
                (TabularContextMenuViewPager) view.findViewById(R.id.custom_pager);
        pager.setAdapter(new TabularContextMenuPagerAdapter(viewGroups));

        TabLayout tabLayout = (TabLayout) view.findViewById(R.id.tab_layout);
        if (itemGroups.size() <= 1) {
            tabLayout.setVisibility(View.GONE);
        } else {
            tabLayout.setupWithViewPager((ViewPager) view.findViewById(R.id.custom_pager));
        }

        return view;
    }

    /**
     * Creates the view of a context menu. Based off the Context Type, it'll adjust the list of
     * items and display only the ones that'll be on that specific group.
     * @param activity Used to get the resources of an item.
     * @param params used to create the header text.
     * @param items A set of Items to display in a context menu. Filtered based off the type.
     * @param isImage Whether or not the view should have an image layout or not.
     * @param maxCount The maximum amount of {@link ContextMenuItem}s that could exist in this view
     *                 or any other views calculated in the context menu. Used to estimate the size
     *                 of the list.
     * @return Returns a filled LinearLayout with all the context menu items.
     */
    @VisibleForTesting
    ViewGroup createContextMenuPageUi(Activity activity, ContextMenuParams params,
            List<ContextMenuItem> items, boolean isImage, int maxCount) {
        ViewGroup baseLayout = (ViewGroup) LayoutInflater.from(activity).inflate(
                R.layout.tabular_context_menu_page, null);
        ListView listView = (ListView) baseLayout.findViewById(R.id.selectable_items);

        displayHeaderIfVisibleItems(params, baseLayout);
        if (isImage) {
            // #displayHeaderIfVisibleItems() sets these two views to GONE if the header text is
            // empty but they should still be visible because we have an image to display.
            baseLayout.findViewById(R.id.context_header_layout).setVisibility(View.VISIBLE);
            baseLayout.findViewById(R.id.context_divider).setVisibility(View.VISIBLE);
            displayImageHeader(baseLayout, params, activity.getResources());
        }

        // Set the list adapter and get the height to display it appropriately in a dialog.
        Runnable onDirectShare = new Runnable() {
            @Override
            public void run() {
                mOnShareItemClicked.run();
                mDialog.dismiss();
            }
        };
        TabularContextMenuListAdapter listAdapter =
                new TabularContextMenuListAdapter(items, activity, onDirectShare);
        ViewGroup.LayoutParams layoutParams = listView.getLayoutParams();
        layoutParams.height = measureApproximateListViewHeight(listView, listAdapter, maxCount);
        listView.setLayoutParams(layoutParams);
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener(this);

        return baseLayout;
    }

    private void displayHeaderIfVisibleItems(ContextMenuParams params, ViewGroup baseLayout) {
        String headerText = ChromeContextMenuPopulator.createHeaderText(params);
        final TextView headerTextView =
                (TextView) baseLayout.findViewById(R.id.context_header_text);
        if (TextUtils.isEmpty(headerText)) {
            baseLayout.findViewById(R.id.context_header_layout).setVisibility(View.GONE);
            headerTextView.setVisibility(View.GONE);
            baseLayout.findViewById(R.id.context_divider).setVisibility(View.GONE);
            return;
        }
        headerTextView.setVisibility(View.VISIBLE);
        headerTextView.setText(headerText);
        headerTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (headerTextView.getMaxLines() == Integer.MAX_VALUE) {
                    headerTextView.setMaxLines(1);
                    headerTextView.setEllipsize(TextUtils.TruncateAt.END);
                } else {
                    headerTextView.setMaxLines(Integer.MAX_VALUE);
                    headerTextView.setEllipsize(null);
                }
            }
        });
    }

    private void displayImageHeader(
            ViewGroup baseLayout, ContextMenuParams params, Resources resources) {
        mHeaderImageView = (ImageView) baseLayout.findViewById(R.id.context_header_image);
        TextView headerTextView = (TextView) baseLayout.findViewById(R.id.context_header_text);
        // We'd prefer the header text is the title text instead of the link text for images.
        String headerText = params.getTitleText();
        if (!TextUtils.isEmpty(headerText)) {
            headerTextView.setText(headerText);
        }
        setBackgroundForImageView(mHeaderImageView, resources);
    }

    /**
     * This creates a checkerboard style background displayed before the image is shown.
     */
    private void setBackgroundForImageView(ImageView imageView, Resources resources) {
        Drawable drawable =
                ApiCompatibilityUtils.getDrawable(resources, R.drawable.checkerboard_background);
        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(),
                drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        BitmapDrawable bm = new BitmapDrawable(resources, bitmap);
        bm.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        imageView.setVisibility(View.VISIBLE);
        imageView.setBackground(bm);
    }

    /**
     * To save time measuring the height, this method gets an item if the height has not been
     * previous measured and multiplies it by count of the total amount of items. It is fine if the
     * height too small as the ListView will scroll through the other values.
     * @param listView The ListView to measure the surrounding padding.
     * @param listAdapter The adapter which contains the items within the list.
     * @return Returns the combined height of the padding of the ListView and the approximate height
     *         of the ListView based off the an item.
     */
    private int measureApproximateListViewHeight(
            ListView listView, BaseAdapter listAdapter, int maxCount) {
        int totalHeight = listView.getPaddingTop() + listView.getPaddingBottom();
        if (mMenuItemHeight == 0 && !listAdapter.isEmpty()) {
            View view = listAdapter.getView(0, null, listView);
            view.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            mMenuItemHeight = view.getMeasuredHeight();
        }
        return totalHeight + mMenuItemHeight * maxCount;
    }

    /**
     * When an thumbnail is retrieved for the header of an image, this will set the header to
     * that particular bitmap.
     */
    public void onImageThumbnailRetrieved(Bitmap bitmap) {
        if (mHeaderImageView != null) {
            mHeaderImageView.setImageBitmap(bitmap);
        }
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
        mDialog.dismiss();
        mCallback.onResult((int) id);
    }
}

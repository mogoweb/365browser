// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.datareduction;

import static android.text.format.DateUtils.FORMAT_NO_YEAR;
import static android.text.format.DateUtils.FORMAT_SHOW_DATE;

import static org.chromium.third_party.android.datausagechart.ChartDataUsageView.DAYS_IN_CHART;

import android.annotation.SuppressLint;
import android.content.Context;
import android.preference.Preference;
import android.text.format.DateUtils;
import android.text.format.Formatter;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.chromium.base.Callback;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.net.spdyproxy.DataReductionProxySettings;
import org.chromium.third_party.android.datausagechart.ChartDataUsageView;
import org.chromium.third_party.android.datausagechart.NetworkStats;
import org.chromium.third_party.android.datausagechart.NetworkStatsHistory;

import java.util.List;
import java.util.TimeZone;

/**
 * Preference used to display statistics on data reduction.
 */
public class DataReductionStatsPreference extends Preference {
    private NetworkStatsHistory mOriginalNetworkStatsHistory;
    private NetworkStatsHistory mReceivedNetworkStatsHistory;

    private TextView mOriginalSizeTextView;
    private TextView mReceivedSizeTextView;
    private TextView mDataSavingsTextView;
    private TextView mDataUsageTextView;
    private TextView mPercentReductionTextView;
    private TextView mStartDateTextView;
    private TextView mEndDateTextView;
    private Button mResetStatisticsButton;
    private ChartDataUsageView mChartDataUsageView;
    private DataReductionSiteBreakdownView mDataReductionBreakdownView;
    private long mLeftPosition;
    private long mRightPosition;
    private Long mCurrentTime;
    private String mOriginalTotalPhrase;
    private String mSavingsTotalPhrase;
    private String mReceivedTotalPhrase;
    private String mPercentReductionPhrase;
    private String mStartDatePhrase;
    private String mEndDatePhrase;

    public DataReductionStatsPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        if (ChromeFeatureList.isEnabled(ChromeFeatureList.DATA_REDUCTION_SITE_BREAKDOWN)) {
            setWidgetLayoutResource(R.layout.data_reduction_stats_layout);
        } else {
            setWidgetLayoutResource(R.layout.data_reduction_old_stats_layout);
        }
    }

    @Override
    public boolean isEnabled() {
        return super.isEnabled();
    }

    /**
     * Updates the preference screen to convey current statistics on data reduction.
     */
    public void updateReductionStatistics() {
        long original[] = DataReductionProxySettings.getInstance().getOriginalNetworkStatsHistory();
        long received[] = DataReductionProxySettings.getInstance().getReceivedNetworkStatsHistory();

        mCurrentTime = DataReductionProxySettings.getInstance().getDataReductionLastUpdateTime();
        mRightPosition = mCurrentTime + DateUtils.HOUR_IN_MILLIS
                - TimeZone.getDefault().getOffset(mCurrentTime);
        mLeftPosition = mCurrentTime - DateUtils.DAY_IN_MILLIS * DAYS_IN_CHART;
        mOriginalNetworkStatsHistory = getNetworkStatsHistory(original, DAYS_IN_CHART);
        mReceivedNetworkStatsHistory = getNetworkStatsHistory(received, DAYS_IN_CHART);

        if (mDataReductionBreakdownView != null) {
            DataReductionProxySettings.getInstance().queryDataUsage(
                    DAYS_IN_CHART, new Callback<List<DataReductionDataUseItem>>() {
                        @Override
                        public void onResult(List<DataReductionDataUseItem> result) {
                            mDataReductionBreakdownView.onQueryDataUsageComplete(result);
                        }
                    });
        }
    }

    private static NetworkStatsHistory getNetworkStatsHistory(long[] history, int days) {
        if (days > history.length) days = history.length;
        NetworkStatsHistory networkStatsHistory = new NetworkStatsHistory(
                DateUtils.DAY_IN_MILLIS, days, NetworkStatsHistory.FIELD_RX_BYTES);

        DataReductionProxySettings config = DataReductionProxySettings.getInstance();
        long time = config.getDataReductionLastUpdateTime() - days * DateUtils.DAY_IN_MILLIS;
        for (int i = history.length - days, bucket = 0; i < history.length; i++, bucket++) {
            NetworkStats.Entry entry = new NetworkStats.Entry();
            entry.rxBytes = history[i];
            long startTime = time + (DateUtils.DAY_IN_MILLIS * bucket);
            // Spread each day's record over the first hour of the day.
            networkStatsHistory.recordData(startTime, startTime + DateUtils.HOUR_IN_MILLIS, entry);
        }
        return networkStatsHistory;
    }

    private void setDetailText() {
        updateDetailData();
        mPercentReductionTextView.setText(mPercentReductionPhrase);
        mStartDateTextView.setText(mStartDatePhrase);
        mEndDateTextView.setText(mEndDatePhrase);
        if (mDataUsageTextView != null) mDataUsageTextView.setText(mReceivedTotalPhrase);
        if (mDataSavingsTextView != null) mDataSavingsTextView.setText(mSavingsTotalPhrase);
        if (mOriginalSizeTextView != null) mOriginalSizeTextView.setText(mOriginalTotalPhrase);
        if (mReceivedSizeTextView != null) mReceivedSizeTextView.setText(mReceivedTotalPhrase);
    }

    /**
     * Keep the graph labels LTR oriented. In RTL languages, numbers and plots remain LTR.
     */
    @SuppressLint("RtlHardcoded")
    private void forceLayoutGravityOfGraphLabels() {
        ((FrameLayout.LayoutParams) mStartDateTextView.getLayoutParams()).gravity = Gravity.LEFT;
        ((FrameLayout.LayoutParams) mEndDateTextView.getLayoutParams()).gravity = Gravity.RIGHT;
    }

    /**
     * Sets up a data usage chart and text views containing data reduction statistics.
     * @param view The current view.
     */
    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        mDataUsageTextView = (TextView) view.findViewById(R.id.data_reduction_usage);
        mDataSavingsTextView = (TextView) view.findViewById(R.id.data_reduction_savings);
        mOriginalSizeTextView = (TextView) view.findViewById(R.id.data_reduction_original_size);
        mReceivedSizeTextView = (TextView) view.findViewById(R.id.data_reduction_compressed_size);
        mPercentReductionTextView = (TextView) view.findViewById(R.id.data_reduction_percent);
        mStartDateTextView = (TextView) view.findViewById(R.id.data_reduction_start_date);
        mEndDateTextView = (TextView) view.findViewById(R.id.data_reduction_end_date);
        mDataReductionBreakdownView =
                (DataReductionSiteBreakdownView) view.findViewById(R.id.breakdown);
        forceLayoutGravityOfGraphLabels();
        if (mOriginalNetworkStatsHistory == null) updateReductionStatistics();
        setDetailText();

        mChartDataUsageView = (ChartDataUsageView) view.findViewById(R.id.chart);
        mChartDataUsageView.bindOriginalNetworkStats(mOriginalNetworkStatsHistory);
        mChartDataUsageView.bindCompressedNetworkStats(mReceivedNetworkStatsHistory);
        mChartDataUsageView.setVisibleRange(
                mCurrentTime - DateUtils.DAY_IN_MILLIS * DAYS_IN_CHART,
                mCurrentTime + DateUtils.HOUR_IN_MILLIS, mLeftPosition, mRightPosition);

        View dataReductionProxyUnreachableWarning =
                view.findViewById(R.id.data_reduction_proxy_unreachable);
        if (DataReductionProxySettings.getInstance().isDataReductionProxyUnreachable()) {
            dataReductionProxyUnreachableWarning.setVisibility(View.VISIBLE);
        } else {
            dataReductionProxyUnreachableWarning.setVisibility(View.GONE);
        }

        mResetStatisticsButton = (Button) view.findViewById(R.id.data_reduction_reset_statistics);
        if (mResetStatisticsButton != null) {
            mResetStatisticsButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    DataReductionProxySettings.getInstance().clearDataSavingStatistics();
                    updateReductionStatistics();
                    setDetailText();
                    notifyChanged();
                    DataReductionProxyUma.dataReductionProxyUIAction(
                            DataReductionProxyUma.ACTION_STATS_RESET);
                }
            });
        }
    }

    /**
     * Update data reduction statistics whenever the chart's inspection
     * range changes. In particular, this creates strings describing the total
     * original size of all data received over the date range, the total size
     * of all data received (after compression), the percent data reduction
     * and the range of dates over which these statistics apply.
     */
    // TODO(crbug.com/635567): Fix this properly.
    @SuppressLint("DefaultLocale")
    private void updateDetailData() {
        final long start = mLeftPosition;
        // Include up to the last second of the currently selected day.
        final long end = mRightPosition;
        final Context context = getContext();

        final long compressedTotalBytes = mReceivedNetworkStatsHistory.getTotalBytes();
        mReceivedTotalPhrase = Formatter.formatFileSize(context, compressedTotalBytes);

        final long originalTotalBytes = mOriginalNetworkStatsHistory.getTotalBytes();
        mOriginalTotalPhrase = Formatter.formatFileSize(context, originalTotalBytes);
        mSavingsTotalPhrase =
                Formatter.formatFileSize(context, originalTotalBytes - compressedTotalBytes);

        float percentage = 0.0f;
        if (originalTotalBytes > 0L && originalTotalBytes > compressedTotalBytes) {
            percentage = (originalTotalBytes - compressedTotalBytes) / (float) originalTotalBytes;
        }
        mPercentReductionPhrase = String.format("%.0f%%", 100.0 * percentage);

        mStartDatePhrase = formatDate(context, start);
        mEndDatePhrase = formatDate(context, end);

        DataReductionProxyUma.dataReductionProxyUserViewedSavings(
                compressedTotalBytes, originalTotalBytes, 100.0 * percentage);
    }

    private static String formatDate(Context context, long millisSinceEpoch) {
        final int flags = FORMAT_SHOW_DATE | FORMAT_NO_YEAR;
        return DateUtils.formatDateTime(context, millisSinceEpoch, flags).toString();
    }
}

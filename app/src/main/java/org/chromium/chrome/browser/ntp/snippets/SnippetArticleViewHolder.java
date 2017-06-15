// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.ntp.snippets;

import android.annotation.SuppressLint;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.media.ThumbnailUtils;
import android.os.StrictMode;
import android.os.SystemClock;
import android.support.annotation.IntDef;
import android.support.v4.text.BidiFormatter;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.Callback;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.download.DownloadUtils;
import org.chromium.chrome.browser.download.ui.DownloadFilter;
import org.chromium.chrome.browser.download.ui.ThumbnailProvider;
import org.chromium.chrome.browser.download.ui.ThumbnailProviderImpl;
import org.chromium.chrome.browser.favicon.FaviconHelper.FaviconImageCallback;
import org.chromium.chrome.browser.favicon.FaviconHelper.IconAvailabilityCallback;
import org.chromium.chrome.browser.ntp.ContextMenuManager;
import org.chromium.chrome.browser.ntp.ContextMenuManager.ContextMenuItemId;
import org.chromium.chrome.browser.ntp.cards.CardViewHolder;
import org.chromium.chrome.browser.ntp.cards.CardsVariationParameters;
import org.chromium.chrome.browser.ntp.cards.ImpressionTracker;
import org.chromium.chrome.browser.ntp.cards.NewTabPageViewHolder;
import org.chromium.chrome.browser.ntp.cards.SuggestionsCategoryInfo;
import org.chromium.chrome.browser.suggestions.SuggestionsMetrics;
import org.chromium.chrome.browser.suggestions.SuggestionsRecyclerView;
import org.chromium.chrome.browser.suggestions.SuggestionsUiDelegate;
import org.chromium.chrome.browser.widget.TintedImageView;
import org.chromium.chrome.browser.widget.displaystyle.DisplayStyleObserver;
import org.chromium.chrome.browser.widget.displaystyle.DisplayStyleObserverAdapter;
import org.chromium.chrome.browser.widget.displaystyle.HorizontalDisplayStyle;
import org.chromium.chrome.browser.widget.displaystyle.UiConfig;
import org.chromium.chrome.browser.widget.displaystyle.VerticalDisplayStyle;
import org.chromium.ui.mojom.WindowOpenDisposition;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

/**
 * A class that represents the view for a single card snippet.
 */
public class SnippetArticleViewHolder extends CardViewHolder implements ImpressionTracker.Listener {
    /**
     * A single instance of {@link RefreshOfflineBadgeVisibilityCallback} that can be reused as it
     * has no state.
     */
    public static final RefreshOfflineBadgeVisibilityCallback
            REFRESH_OFFLINE_BADGE_VISIBILITY_CALLBACK = new RefreshOfflineBadgeVisibilityCallback();

    private static final String ARTICLE_AGE_FORMAT_STRING = " - %s";
    private static final int FADE_IN_ANIMATION_TIME_MS = 300;
    private static final int[] FAVICON_SERVICE_SUPPORTED_SIZES = {16, 24, 32, 48, 64};
    private static final String FAVICON_SERVICE_FORMAT =
            "https://s2.googleusercontent.com/s2/favicons?domain=%s&src=chrome_newtab_mobile&sz=%d&alt=404";
    private static final int PUBLISHER_FAVICON_MINIMUM_SIZE_PX = 16;

    private static final int THUMBNAIL_SOURCE_ARTICLE = 0;
    private static final int THUMBNAIL_SOURCE_DOWNLOAD = 1;

    private final SuggestionsUiDelegate mUiDelegate;
    private final UiConfig mUiConfig;
    private final ThumbnailProvider mThumbnailProvider;

    private final TextView mHeadlineTextView;
    private final TextView mPublisherTextView;
    private final TextView mArticleSnippetTextView;
    private final TextView mArticleAgeTextView;
    private final TintedImageView mThumbnailView;
    private final ImageView mOfflineBadge;
    private final View mPublisherBar;

    private final int mThumbnailSize;
    /** Total horizontal space occupied by the thumbnail, sum of its size and margin. */
    private final int mThumbnailFootprintPx;
    private final boolean mUseFaviconService;
    private final int mIconBackgroundColor;
    private final ColorStateList mIconForegroundColorList;

    private FetchImageCallback mImageCallback;
    private ThumbnailCallback mDownloadThumbnailCallback;
    private SnippetArticle mArticle;
    private SuggestionsCategoryInfo mCategoryInfo;
    private int mPublisherFaviconSizePx;

    /**
     * Constructs a {@link SnippetArticleViewHolder} item used to display snippets.
     * @param parent The SuggestionsRecyclerView that is going to contain the newly created view.
     * @param contextMenuManager The manager responsible for the context menu.
     * @param uiDelegate The delegate object used to open an article, fetch thumbnails, etc.
     * @param uiConfig The NTP UI configuration object used to adjust the article UI.
     */
    public SnippetArticleViewHolder(SuggestionsRecyclerView parent,
            ContextMenuManager contextMenuManager, SuggestionsUiDelegate uiDelegate,
            UiConfig uiConfig) {
        super(R.layout.new_tab_page_snippets_card, parent, uiConfig, contextMenuManager);

        mUiDelegate = uiDelegate;
        mUiConfig = uiConfig;

        mThumbnailView = (TintedImageView) itemView.findViewById(R.id.article_thumbnail);
        mThumbnailSize =
                itemView.getResources().getDimensionPixelSize(R.dimen.snippets_thumbnail_size);

        mHeadlineTextView = (TextView) itemView.findViewById(R.id.article_headline);
        mPublisherTextView = (TextView) itemView.findViewById(R.id.article_publisher);
        mArticleSnippetTextView = (TextView) itemView.findViewById(R.id.article_snippet);
        mArticleAgeTextView = (TextView) itemView.findViewById(R.id.article_age);
        mPublisherBar = itemView.findViewById(R.id.publisher_bar);
        mOfflineBadge = (ImageView) itemView.findViewById(R.id.offline_icon);

        mThumbnailFootprintPx = mThumbnailSize
                + itemView.getResources().getDimensionPixelSize(R.dimen.snippets_thumbnail_margin);
        mUseFaviconService = CardsVariationParameters.isFaviconServiceEnabled();

        mIconBackgroundColor = DownloadUtils.getIconBackgroundColor(parent.getContext());
        mIconForegroundColorList = DownloadUtils.getIconForegroundColorList(parent.getContext());

        // TODO(bauerb): Share ThumbnailProvider between instances
        mThumbnailProvider = new ThumbnailProviderImpl(mThumbnailSize);

        new ImpressionTracker(itemView, this);
        new DisplayStyleObserverAdapter(itemView, uiConfig, new DisplayStyleObserver() {
            @Override
            public void onDisplayStyleChanged(UiConfig.DisplayStyle newDisplayStyle) {
                updateLayout();
            }
        });
    }

    @Override
    public void onImpression() {
        if (mArticle != null && mArticle.trackImpression()) {
            mUiDelegate.getEventReporter().onSuggestionShown(mArticle);
            mRecyclerView.onSnippetImpression();
        }
    }

    @Override
    public void onCardTapped() {
        SuggestionsMetrics.recordCardTapped();
        int windowDisposition = WindowOpenDisposition.CURRENT_TAB;
        mUiDelegate.getEventReporter().onSuggestionOpened(
                mArticle, windowDisposition, mUiDelegate.getSuggestionsRanker());
        mUiDelegate.getNavigationDelegate().openSnippet(windowDisposition, mArticle);
    }

    @Override
    public void openItem(int windowDisposition) {
        mUiDelegate.getEventReporter().onSuggestionOpened(
                mArticle, windowDisposition, mUiDelegate.getSuggestionsRanker());
        mUiDelegate.getNavigationDelegate().openSnippet(windowDisposition, mArticle);
    }

    @Override
    public String getUrl() {
        return mArticle.mUrl;
    }

    @Override
    public boolean isItemSupported(@ContextMenuItemId int menuItemId) {
        Boolean isSupported = mCategoryInfo.isContextMenuItemSupported(menuItemId);
        if (isSupported != null) return isSupported;

        return super.isItemSupported(menuItemId);
    }

    @Override
    public void onContextMenuCreated() {
        mUiDelegate.getEventReporter().onSuggestionMenuOpened(mArticle);
    }

    /**
     * Updates ViewHolder with data.
     * @param article The snippet to take the data from.
     * @param categoryInfo The info of the category which the snippet belongs to.
     */
    public void onBindViewHolder(
            final SnippetArticle article, SuggestionsCategoryInfo categoryInfo) {
        super.onBindViewHolder();

        mArticle = article;
        mCategoryInfo = categoryInfo;
        updateLayout();

        mHeadlineTextView.setText(mArticle.mTitle);

        // The favicon of the publisher should match the TextView height.
        int widthSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        int heightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        mPublisherTextView.measure(widthSpec, heightSpec);
        mPublisherFaviconSizePx = mPublisherTextView.getMeasuredHeight();

        mArticleSnippetTextView.setText(mArticle.mPreviewText);
        mPublisherTextView.setText(getPublisherString(mArticle));
        mArticleAgeTextView.setText(getArticleAge(mArticle));
        setThumbnail();

        // Set the favicon of the publisher.
        // We start initialising with the default favicon to reserve the space and prevent the text
        // from moving later.
        setDefaultFaviconOnView();
        try {
            long faviconFetchStartTimeMs = SystemClock.elapsedRealtime();
            URI pageUrl = new URI(mArticle.mUrl);
            if (!article.isArticle() || !SnippetsConfig.isFaviconsFromNewServerEnabled()) {
                // The old code path. Remove when the experiment is successful.
                // Currently, we have to use this for non-articles, due to privacy.
                fetchFaviconFromLocalCache(pageUrl, true, faviconFetchStartTimeMs);
            } else {
                // The new code path.
                fetchFaviconFromLocalCacheOrGoogleServer(faviconFetchStartTimeMs);
            }
        } catch (URISyntaxException e) {
            // Do nothing, stick to the default favicon.
        }

        mOfflineBadge.setVisibility(View.GONE);
        refreshOfflineBadgeVisibility();
    }

    /**
     * Updates the layout taking into account screen dimensions and the type of snippet displayed.
     */
    private void updateLayout() {
        final int horizontalStyle = mUiConfig.getCurrentDisplayStyle().horizontal;
        final int verticalStyle = mUiConfig.getCurrentDisplayStyle().vertical;
        final int layout = mCategoryInfo.getCardLayout();

        boolean showHeadline = shouldShowHeadline();
        boolean showDescription = shouldShowDescription(horizontalStyle, verticalStyle, layout);
        boolean showThumbnail = shouldShowThumbnail(horizontalStyle, verticalStyle, layout);

        mHeadlineTextView.setVisibility(showHeadline ? View.VISIBLE : View.GONE);
        mArticleSnippetTextView.setVisibility(showDescription ? View.VISIBLE : View.GONE);
        mThumbnailView.setVisibility(showThumbnail ? View.VISIBLE : View.GONE);

        ViewGroup.MarginLayoutParams publisherBarParams =
                (ViewGroup.MarginLayoutParams) mPublisherBar.getLayoutParams();

        if (showDescription) {
            publisherBarParams.topMargin = mPublisherBar.getResources().getDimensionPixelSize(
                    R.dimen.snippets_publisher_margin_top_with_article_snippet);
        } else if (showHeadline) {
            // When we show a headline and not a description, we reduce the top margin of the
            // publisher bar.
            publisherBarParams.topMargin = mPublisherBar.getResources().getDimensionPixelSize(
                    R.dimen.snippets_publisher_margin_top_without_article_snippet);
        } else {
            // When there is no headline and no description, we remove the top margin of the
            // publisher bar.
            publisherBarParams.topMargin = 0;
        }

        ApiCompatibilityUtils.setMarginEnd(
                publisherBarParams, showThumbnail ? mThumbnailFootprintPx : 0);
        mPublisherBar.setLayoutParams(publisherBarParams);
    }

    /** If the title is empty (or contains only whitespace characters), we do not show it. */
    private boolean shouldShowHeadline() {
        return !mArticle.mTitle.trim().isEmpty();
    }

    private boolean shouldShowDescription(int horizontalStyle, int verticalStyle, int layout) {
        // Minimal cards don't have a description.
        if (layout == ContentSuggestionsCardLayout.MINIMAL_CARD) return false;

        // When the screen is too small (narrow or flat) we don't show the description to have more
        // space for the header.
        if (horizontalStyle == HorizontalDisplayStyle.NARROW) return false;
        if (verticalStyle == VerticalDisplayStyle.FLAT) return false;

        // When article's description is empty, we do not want empty space.
        if (mArticle != null && TextUtils.isEmpty(mArticle.mPreviewText)) return false;

        return ChromeFeatureList.isEnabled(ChromeFeatureList.CONTENT_SUGGESTIONS_SHOW_SUMMARY);
    }

    private boolean shouldShowThumbnail(int horizontalStyle, int verticalStyle, int layout) {
        // Minimal cards don't have a thumbnail
        if (layout == ContentSuggestionsCardLayout.MINIMAL_CARD) return false;

        return true;
    }

    private static String getPublisherString(SnippetArticle article) {
        // We format the publisher here so that having a publisher name in an RTL language
        // doesn't mess up the formatting on an LTR device and vice versa.
        return BidiFormatter.getInstance().unicodeWrap(article.mPublisher);
    }

    private static String getArticleAge(SnippetArticle article) {
        if (article.mPublishTimestampMilliseconds == 0) return "";

        // DateUtils.getRelativeTimeSpanString(...) calls through to TimeZone.getDefault(). If this
        // has never been called before it loads the current time zone from disk. In most likelihood
        // this will have been called previously and the current time zone will have been cached,
        // but in some cases (eg instrumentation tests) it will cause a strict mode violation.
        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskReads();
        CharSequence relativeTimeSpan;
        try {
            long time = SystemClock.elapsedRealtime();
            relativeTimeSpan =
                    DateUtils.getRelativeTimeSpanString(article.mPublishTimestampMilliseconds,
                            System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
            RecordHistogram.recordTimesHistogram("Android.StrictMode.SnippetUIBuildTime",
                    SystemClock.elapsedRealtime() - time, TimeUnit.MILLISECONDS);
        } finally {
            StrictMode.setThreadPolicy(oldPolicy);
        }

        // We add a dash before the elapsed time, e.g. " - 14 minutes ago".
        return String.format(ARTICLE_AGE_FORMAT_STRING,
                BidiFormatter.getInstance().unicodeWrap(relativeTimeSpan));
    }

    private void setThumbnailFromBitmap(Bitmap thumbnail) {
        assert thumbnail != null;
        assert !thumbnail.isRecycled();
        assert thumbnail.getWidth() == mThumbnailSize;
        assert thumbnail.getHeight() == mThumbnailSize;

        mThumbnailView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        mThumbnailView.setBackground(null);
        mThumbnailView.setImageBitmap(thumbnail);
        mThumbnailView.setTint(null);
    }

    private void setThumbnailFromFileType(int fileType) {
        mThumbnailView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        mThumbnailView.setBackgroundColor(mIconBackgroundColor);
        mThumbnailView.setImageResource(
                DownloadUtils.getIconResId(fileType, DownloadUtils.ICON_SIZE_36_DP));
        mThumbnailView.setTint(mIconForegroundColorList);
    }

    private void setDownloadThumbnail() {
        assert mArticle.isDownload();
        if (!mArticle.isAssetDownload()) {
            setThumbnailFromFileType(DownloadFilter.FILTER_PAGE);
            return;
        }

        int fileType = DownloadFilter.fromMimeType(mArticle.getAssetDownloadMimeType());
        if (fileType == DownloadFilter.FILTER_IMAGE) {
            // For image downloads, attempt to fetch a thumbnail.
            cancelImageFetch();
            mImageCallback = new FetchImageCallback(this, mArticle, THUMBNAIL_SOURCE_DOWNLOAD);
            mDownloadThumbnailCallback = new ThumbnailCallback(mImageCallback, mArticle);
            Bitmap thumbnail = mThumbnailProvider.getThumbnail(mDownloadThumbnailCallback);
            if (thumbnail != null) {
                // If there is already a thumbnail available, use it immediately, otherwise fall
                // through to using the default icon for the type. Once the thumbnail is fetched
                // it will be faded in.
                mArticle.setThumbnailBitmap(mUiDelegate.getReferencePool().put(thumbnail));
                setThumbnailFromBitmap(thumbnail);
                return;
            }
        }
        setThumbnailFromFileType(fileType);
    }

    private void setThumbnail() {
        // If there's still a pending thumbnail fetch, cancel it.
        cancelImageFetch();

        // mThumbnailView's visibility is modified in updateLayout().
        if (mThumbnailView.getVisibility() != View.VISIBLE) return;
        Bitmap thumbnail = mArticle.getThumbnailBitmap();
        if (thumbnail != null) {
            setThumbnailFromBitmap(thumbnail);
            return;
        }

        if (mArticle.isDownload()) {
            setDownloadThumbnail();
            return;
        }

        // Temporarily set placeholder and then fetch the thumbnail from a provider.
        mThumbnailView.setBackground(null);
        mThumbnailView.setImageResource(R.drawable.ic_snippet_thumbnail_placeholder);
        mThumbnailView.setTint(null);
        mImageCallback = new FetchImageCallback(this, mArticle, THUMBNAIL_SOURCE_ARTICLE);
        mUiDelegate.getSuggestionsSource().fetchSuggestionImage(mArticle, mImageCallback);
    }

    /** Updates the visibility of the card's offline badge by checking the bound article's info. */
    private void refreshOfflineBadgeVisibility() {
        boolean visible = mArticle.getOfflinePageOfflineId() != null || mArticle.isAssetDownload();
        if (visible == (mOfflineBadge.getVisibility() == View.VISIBLE)) return;
        mOfflineBadge.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void cancelImageFetch() {
        if (mImageCallback != null) {
            mImageCallback.cancel();
            mImageCallback = null;
        }

        if (mDownloadThumbnailCallback != null) {
            mThumbnailProvider.cancelRetrieval(mDownloadThumbnailCallback);
            mDownloadThumbnailCallback = null;
        }
    }

    private void fadeThumbnailIn(SnippetArticle snippet, Bitmap thumbnail, boolean owned) {
        mImageCallback = null;
        if (thumbnail == null) return; // Nothing to do, we keep the placeholder.

        // We need to crop and scale the downloaded bitmap, as the ImageView we set it on won't be
        // able to do so when using a TransitionDrawable (as opposed to the straight bitmap).
        // That's a limitation of TransitionDrawable, which doesn't handle layers of varying sizes.
        if (thumbnail.getHeight() != mThumbnailSize || thumbnail.getWidth() != mThumbnailSize) {
            // Resize the thumbnail. If we fully own the input bitmap (e.g. it isn't cached anywhere
            // else), recycle the input image in the process.
            thumbnail = ThumbnailUtils.extractThumbnail(thumbnail, mThumbnailSize, mThumbnailSize,
                    owned ? ThumbnailUtils.OPTIONS_RECYCLE_INPUT : 0);
        }

        // Store the bitmap to skip the download task next time we display this snippet.
        snippet.setThumbnailBitmap(mUiDelegate.getReferencePool().put(thumbnail));

        // Cross-fade between the placeholder and the thumbnail. We cross-fade because the incoming
        // image may have transparency and we don't want the previous image showing up behind.
        Drawable[] layers = {mThumbnailView.getDrawable(),
                new BitmapDrawable(mThumbnailView.getResources(), thumbnail)};
        TransitionDrawable transitionDrawable = new TransitionDrawable(layers);
        mThumbnailView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        mThumbnailView.setBackground(null);
        mThumbnailView.setImageDrawable(transitionDrawable);
        mThumbnailView.setTint(null);
        transitionDrawable.setCrossFadeEnabled(true);
        transitionDrawable.startTransition(FADE_IN_ANIMATION_TIME_MS);
    }

    private void fetchFaviconFromLocalCacheOrGoogleServer(final long faviconFetchStartTimeMs) {
        // Set the desired size to 0 to specify we do not want to resize in c++, we'll resize here.
        mUiDelegate.getSuggestionsSource().fetchSuggestionFavicon(mArticle,
                PUBLISHER_FAVICON_MINIMUM_SIZE_PX, /* desiredSizePx */ 0, new Callback<Bitmap>() {
                    @Override
                    public void onResult(Bitmap image) {
                        recordFaviconFetchTime(faviconFetchStartTimeMs);
                        if (image == null) return;
                        setFaviconOnView(image);
                    }
                });
    }

    private void recordFaviconFetchTime(long faviconFetchStartTimeMs) {
        RecordHistogram.recordMediumTimesHistogram(
                "NewTabPage.ContentSuggestions.ArticleFaviconFetchTime",
                SystemClock.elapsedRealtime() - faviconFetchStartTimeMs, TimeUnit.MILLISECONDS);
    }

    private void recordFaviconFetchResult(
            @FaviconFetchResult int result, long faviconFetchStartTimeMs) {
        // Record the histogram for articles only to have a fair comparision.
        if (!mArticle.isArticle()) return;
        RecordHistogram.recordEnumeratedHistogram(
                "NewTabPage.ContentSuggestions.ArticleFaviconFetchResult", result,
                FaviconFetchResult.COUNT);
        recordFaviconFetchTime(faviconFetchStartTimeMs);
    }

    private void fetchFaviconFromLocalCache(final URI snippetUri, final boolean fallbackToService,
            final long faviconFetchStartTimeMs) {
        mUiDelegate.getLocalFaviconImageForURL(
                getSnippetDomain(snippetUri), mPublisherFaviconSizePx, new FaviconImageCallback() {
                    @Override
                    public void onFaviconAvailable(Bitmap image, String iconUrl) {
                        if (image != null) {
                            setFaviconOnView(image);
                            recordFaviconFetchResult(fallbackToService
                                            ? FaviconFetchResult.SUCCESS_CACHED
                                            : FaviconFetchResult.SUCCESS_FETCHED,
                                    faviconFetchStartTimeMs);
                        } else if (fallbackToService) {
                            if (!fetchFaviconFromService(snippetUri, faviconFetchStartTimeMs)) {
                                recordFaviconFetchResult(
                                        FaviconFetchResult.FAILURE, faviconFetchStartTimeMs);
                            }
                        }
                        // Else do nothing, we already have the placeholder set.
                    }
                });
    }

    // TODO(crbug.com/635567): Fix this properly.
    @SuppressLint("DefaultLocale")
    private boolean fetchFaviconFromService(
            final URI snippetUri, final long faviconFetchStartTimeMs) {
        if (!mUseFaviconService) return false;
        int sizePx = getFaviconServiceSupportedSize();
        if (sizePx == 0) return false;

        // Replace the default icon by another one from the service when it is fetched.
        mUiDelegate.ensureIconIsAvailable(
                getSnippetDomain(snippetUri), // Store to the cache for the whole domain.
                String.format(FAVICON_SERVICE_FORMAT, snippetUri.getHost(), sizePx),
                /*useLargeIcon=*/false, /*isTemporary=*/true, new IconAvailabilityCallback() {
                    @Override
                    public void onIconAvailabilityChecked(boolean newlyAvailable) {
                        if (!newlyAvailable) {
                            recordFaviconFetchResult(
                                    FaviconFetchResult.FAILURE, faviconFetchStartTimeMs);
                            return;
                        }
                        // The download succeeded, the favicon is in the cache; fetch it.
                        fetchFaviconFromLocalCache(
                                snippetUri, /*fallbackToService=*/false, faviconFetchStartTimeMs);
                    }
                });
        return true;
    }

    private int getFaviconServiceSupportedSize() {
        // Take the smallest size larger than mFaviconSizePx.
        for (int size : FAVICON_SERVICE_SUPPORTED_SIZES) {
            if (size > mPublisherFaviconSizePx) return size;
        }
        // Or at least the largest available size (unless too small).
        int largestSize =
                FAVICON_SERVICE_SUPPORTED_SIZES[FAVICON_SERVICE_SUPPORTED_SIZES.length - 1];
        if (mPublisherFaviconSizePx <= largestSize * 1.5) return largestSize;
        return 0;
    }

    private String getSnippetDomain(URI snippetUri) {
        return String.format("%s://%s", snippetUri.getScheme(), snippetUri.getHost());
    }

    private void setDefaultFaviconOnView() {
        setFaviconOnView(ApiCompatibilityUtils.getDrawable(
                mPublisherTextView.getContext().getResources(), R.drawable.default_favicon));
    }

    private void setFaviconOnView(Bitmap image) {
        setFaviconOnView(new BitmapDrawable(mPublisherTextView.getContext().getResources(), image));
    }

    private void setFaviconOnView(Drawable drawable) {
        drawable.setBounds(0, 0, mPublisherFaviconSizePx, mPublisherFaviconSizePx);
        ApiCompatibilityUtils.setCompoundDrawablesRelative(
                mPublisherTextView, drawable, null, null, null);
        mPublisherTextView.setVisibility(View.VISIBLE);
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({THUMBNAIL_SOURCE_ARTICLE, THUMBNAIL_SOURCE_DOWNLOAD})
    private @interface ThumbnailSource {}

    private static class FetchImageCallback extends Callback<Bitmap> {
        private SnippetArticleViewHolder mViewHolder;
        private final SnippetArticle mSnippet;
        @ThumbnailSource
        private final int mSource;

        public FetchImageCallback(SnippetArticleViewHolder viewHolder, SnippetArticle snippet,
                @ThumbnailSource int source) {
            mViewHolder = viewHolder;
            mSnippet = snippet;
            mSource = source;
        }

        @Override
        public void onResult(Bitmap image) {
            if (mViewHolder == null) return;
            mViewHolder.fadeThumbnailIn(mSnippet, image, mSource == THUMBNAIL_SOURCE_ARTICLE);
        }

        public void cancel() {
            // TODO(treib): Pass the "cancel" on to the actual image fetcher.
            mViewHolder = null;
        }
    }

    private static class ThumbnailCallback implements ThumbnailProvider.ThumbnailRequest {
        private final SnippetArticle mSnippet;
        private final FetchImageCallback mCallback;

        public ThumbnailCallback(FetchImageCallback callback, SnippetArticle snippet) {
            assert snippet != null;
            assert callback != null;

            mSnippet = snippet;
            mCallback = callback;
        }

        @Override
        public String getFilePath() {
            return mSnippet.getAssetDownloadFile().getAbsolutePath();
        }

        @Override
        public void onThumbnailRetrieved(String filePath, Bitmap thumbnail) {
            assert !thumbnail.isRecycled();

            if (!getFilePath().equals(filePath)) return;
            if (thumbnail.getWidth() <= 0 || thumbnail.getHeight() <= 0) return;

            mCallback.onResult(thumbnail);
        }
    }

    /**
     * Callback to refresh the offline badge visibility.
     */
    public static class RefreshOfflineBadgeVisibilityCallback extends PartialBindCallback {
        @Override
        public void onResult(NewTabPageViewHolder holder) {
            assert holder instanceof SnippetArticleViewHolder;
            ((SnippetArticleViewHolder) holder).refreshOfflineBadgeVisibility();
        }
    }
}

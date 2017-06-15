// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.widget;

import android.os.AsyncTask;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.Adapter;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.text.format.DateUtils;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.chromium.base.Log;
import org.chromium.base.annotations.SuppressFBWarnings;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.widget.DateDividedAdapter.ItemGroup;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;

/**
 * An {@link Adapter} that works with a {@link RecyclerView}. It sorts the given {@link List} of
 * {@link TimedItem}s according to their date, and divides them into sub lists and displays them in
 * different sections.
 * <p>
 * Subclasses should not care about the how date headers are placed in the list. Instead, they
 * should call {@link #loadItems(List)} with a list of {@link TimedItem}, and this adapter will
 * insert the headers automatically.
 */
public abstract class DateDividedAdapter extends Adapter<RecyclerView.ViewHolder> {

    /**
     * Interface that the {@link Adapter} uses to interact with the items it manages.
     */
    public abstract static class TimedItem {
        /** Value indicating that a TimedItem is not currently being displayed. */
        public static final int INVALID_POSITION = -1;

        /** Position of the TimedItem in the list, or {@link #INVALID_POSITION} if not shown. */
        private int mPosition = INVALID_POSITION;

        private boolean mIsFirstInGroup;
        private boolean mIsLastInGroup;

        /** See {@link #mPosition}. */
        private final void setPosition(int position) {
            mPosition = position;
        }

        /** See {@link #mPosition}. */
        public final int getPosition() {
            return mPosition;
        }

        /**
         * @param isFirst Whether this item is the first in its group.
         */
        public final void setIsFirstInGroup(boolean isFirst) {
            mIsFirstInGroup = isFirst;
        }

        /**
         * @param isLast Whether this item is the last in its group.
         */
        public final void setIsLastInGroup(boolean isLast) {
            mIsLastInGroup = isLast;
        }

        /**
         * @return Whether this item is the first in its group.
         */
        public boolean isFirstInGroup() {
            return mIsFirstInGroup;
        }

        /**
         * @return Whether this item is the last in its group.
         */
        public boolean isLastInGroup() {
            return mIsLastInGroup;
        }

        /** @return The timestamp for this item. */
        public abstract long getTimestamp();

        /**
         * Returns an ID that uniquely identifies this TimedItem and doesn't change.
         * To avoid colliding with IDs generated for Date headers, at least one of the upper 32
         * bits of the long should be set.
         * @return ID that can uniquely identify the TimedItem.
         */
        public abstract long getStableId();
    }

    /**
     * A {@link RecyclerView.ViewHolder} that displays a date header.
     */
    public static class DateViewHolder extends RecyclerView.ViewHolder {
        private TextView mTextView;

        public DateViewHolder(View view) {
            super(view);
            if (view instanceof TextView) mTextView = (TextView) view;
        }

        /**
         * @param date The date that this DateViewHolder should display.
         */
        public void setDate(Date date) {
            // Calender.getInstance() may take long time to run, so Calendar object should be reused
            // as much as possible.
            Pair<Calendar, Calendar> pair = getCachedCalendars();
            Calendar cal1 = pair.first, cal2 = pair.second;
            cal1.setTimeInMillis(System.currentTimeMillis());
            cal2.setTime(date);

            StringBuilder builder = new StringBuilder();
            if (compareCalendar(cal1, cal2) == 0) {
                builder.append(mTextView.getContext().getString(R.string.today));
                builder.append(" - ");
            } else {
                // Set cal1 to yesterday.
                cal1.add(Calendar.DATE, -1);
                if (compareCalendar(cal1, cal2) == 0) {
                    builder.append(mTextView.getContext().getString(R.string.yesterday));
                    builder.append(" - ");
                }
            }
            builder.append(DateUtils.formatDateTime(mTextView.getContext(), date.getTime(),
                    DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_SHOW_DATE
                    | DateUtils.FORMAT_SHOW_YEAR));
            mTextView.setText(builder);
        }
    }

    protected static class BasicViewHolder extends RecyclerView.ViewHolder {
        public BasicViewHolder(View itemView) {
            super(itemView);
        }
    }

    protected static class SubsectionHeaderViewHolder extends RecyclerView.ViewHolder {
        private View mView;

        public SubsectionHeaderViewHolder(View itemView) {
            super(itemView);
            mView = itemView;
        }

        public View getView() {
            return mView;
        }
    }

    /**
     * A bucket of items with the same date.
     */
    public static class ItemGroup {
        private final Date mDate;
        private final List<TimedItem> mItems = new ArrayList<>();

        /** Index of the header, relative to the full list.  Must be set only once.*/
        private int mIndex;

        private boolean mIsSorted;
        private boolean mIsListHeader;
        private boolean mIsListFooter;

        public ItemGroup(long timestamp) {
            mDate = new Date(timestamp);
            mIsSorted = true;
        }

        public void addItem(TimedItem item) {
            mItems.add(item);
            mIsSorted = mItems.size() == 1;
        }

        public void removeItem(TimedItem item) {
            mItems.remove(item);
        }

        /** Records the position of all the TimedItems in this group, relative to the full list. */
        public void setPosition(int index) {
            assert mIndex == 0 || mIndex == TimedItem.INVALID_POSITION;
            mIndex = index;

            sortIfNeeded();
            for (int i = 0; i < mItems.size(); i++) {
                index += 1;
                TimedItem item = mItems.get(i);
                item.setPosition(index);
                item.setIsFirstInGroup(i == 0);
                item.setIsLastInGroup(i == mItems.size() - 1);
            }
        }

        /** Unsets the position of all TimedItems in this group. */
        public void resetPosition() {
            mIndex = TimedItem.INVALID_POSITION;
            for (TimedItem item : mItems) item.setPosition(TimedItem.INVALID_POSITION);
        }

        /**
         * @return Whether the given date happens in the same day as the items in this group.
         */
        public boolean isSameDay(Date otherDate) {
            return compareDate(mDate, otherDate) == 0;
        }

        /**
         * @return The size of this group.
         */
        public int size() {
            if (mIsListHeader || mIsListFooter) return 1;

            // Plus 1 to account for the date header.
            return mItems.size() + 1;
        }

        public TimedItem getItemAt(int index) {
            // 0 is allocated to the date header. The list header has no items.
            if (index <= 0 || mIsListHeader || mIsListFooter) return null;

            sortIfNeeded();
            return mItems.get(index - 1);
        }

        /**
         * Rather than sorting the list each time a new item is added, the list is sorted when
         * something requires a correct ordering of the items.
         */
        protected void sortIfNeeded() {
            if (mIsSorted) return;
            mIsSorted = true;

            Collections.sort(mItems, new Comparator<TimedItem>() {
                @Override
                public int compare(TimedItem lhs, TimedItem rhs) {
                    return compareItem(lhs, rhs);
                }
            });
        }

        protected int compareItem(TimedItem lhs, TimedItem rhs) {
            // More recent items are listed first.  Ideally we'd use Long.compare, but that
            // is an API level 19 call for some inexplicable reason.
            long timeDelta = lhs.getTimestamp() - rhs.getTimestamp();
            if (timeDelta > 0) {
                return -1;
            } else if (timeDelta == 0) {
                return 0;
            } else {
                return 1;
            }
        }
    }

    // Cached async tasks to get the two Calendar objects, which are used when comparing dates.
    private static final AsyncTask<Void, Void, Calendar> sCal1 = createCalendar();
    private static final AsyncTask<Void, Void, Calendar> sCal2 = createCalendar();

    public static final int TYPE_FOOTER = -2;
    public static final int TYPE_HEADER = -1;
    public static final int TYPE_DATE = 0;
    public static final int TYPE_NORMAL = 1;
    public static final int TYPE_SUBSECTION_HEADER = 2;

    private static final String TAG = "DateDividedAdapter";

    private int mSize;
    private boolean mHasListHeader;
    private boolean mHasListFooter;

    private SortedSet<ItemGroup> mGroups = new TreeSet<>(new Comparator<ItemGroup>() {
        @Override
        public int compare(ItemGroup lhs, ItemGroup rhs) {
            if (lhs == rhs) return 0;

            // There should only be at most one list header and one list footer in the SortedSet.
            if (lhs.mIsListHeader || rhs.mIsListFooter) return -1;
            if (lhs.mIsListFooter || rhs.mIsListHeader) return 1;

            return compareDate(lhs.mDate, rhs.mDate);
        }
    });

    /**
     * Creates a {@link ViewHolder} in the given view parent.
     * @see #onCreateViewHolder(ViewGroup, int)
     */
    protected abstract ViewHolder createViewHolder(ViewGroup parent);

    /**
     * Creates a {@link BasicViewHolder} in the given view parent for the header.
     * @see #onCreateViewHolder(ViewGroup, int)
     */
    @Nullable
    protected BasicViewHolder createHeader(ViewGroup parent) {
        return null;
    }

    /**
     * Creates a {@link BasicViewHolder} in the given view parent for the footer.
     * See {@link #onCreateViewHolder(ViewGroup, int)}.
     */
    @Nullable
    protected BasicViewHolder createFooter(ViewGroup parent) {
        return null;
    }

    /**
     * Creates a {@link DateViewHolder} in the given view parent.
     * @see #onCreateViewHolder(ViewGroup, int)
     */
    protected DateViewHolder createDateViewHolder(ViewGroup parent) {
        return new DateViewHolder(LayoutInflater.from(parent.getContext()).inflate(
                getTimedItemViewResId(), parent, false));
    }

    /**
     * Creates a {@link ViewHolder} for a subsection in the given view parent.
     * @see #onCreateViewHolder(ViewGroup, int)
     */
    @Nullable
    protected SubsectionHeaderViewHolder createSubsectionHeader(ViewGroup parent) {
        return null;
    }

    /**
     * Helper function to determine whether an item is a subsection header.
     * @param timedItem The item.
     * @return Whether the item is a subsection header.
     */
    protected boolean isSubsectionHeader(TimedItem timedItem) {
        return false;
    }

    /**
     * Binds the {@link ViewHolder} with the given {@link TimedItem}.
     * @see #onBindViewHolder(ViewHolder, int)
     */
    protected abstract void bindViewHolderForTimedItem(ViewHolder viewHolder, TimedItem item);

    /**
     * Binds the {@link SubsectionHeaderViewHolder} with the given {@link TimedItem}.
     * @see #onBindViewHolder(ViewHolder, int)
     */
    protected void bindViewHolderForSubsectionHeader(
            SubsectionHeaderViewHolder holder, TimedItem timedItem) {}

    /**
     * Gets the resource id of the view showing the date header.
     * Contract for subclasses: this view should be a {@link TextView}.
     */
    protected abstract int getTimedItemViewResId();

    /**
     * Loads a list of {@link TimedItem}s to this adapter. Previous data will not be removed. Call
     * {@link #clear(boolean)} to remove previous items.
     */
    public void loadItems(List<? extends TimedItem> timedItems) {
        for (TimedItem timedItem : timedItems) {
            Date date = new Date(timedItem.getTimestamp());
            boolean found = false;
            for (ItemGroup group : mGroups) {
                if (group.isSameDay(date)) {
                    found = true;
                    group.addItem(timedItem);
                    mSize++;
                    break;
                }
            }
            if (!found) {
                // Create a new ItemGroup with the date for the new item. This increases the
                // size by two because we add new views for the date and the item itself.
                ItemGroup newGroup = createGroup(timedItem.getTimestamp());
                newGroup.addItem(timedItem);
                mGroups.add(newGroup);
                mSize += 2;
            }
        }

        setGroupPositions();
        notifyDataSetChanged();
    }

    /**
     * Creates and returns an item group for a given day.
     * @param timestamp A timestamp from which the date is determined.
     * @return The item group.
     */
    protected ItemGroup createGroup(long timestamp) {
        return new ItemGroup(timestamp);
    }

    /**
     * Tells each group where they start in the list.
     */
    private void setGroupPositions() {
        int startIndex = 0;
        for (ItemGroup group : mGroups) {
            group.resetPosition();
            group.setPosition(startIndex);
            startIndex += group.size();
        }
    }

    /**
     * Adds a header as the first group in this adapter.
     */
    public void addHeader() {
        assert mSize == 0;

        ItemGroup header = new ItemGroup(Long.MAX_VALUE);
        header.mIsListHeader = true;

        mGroups.add(header);
        mSize++;
        mHasListHeader = true;
    }

    /**
     * Removes the list header.
     */
    public void removeHeader() {
        if (!mHasListHeader) return;

        mGroups.remove(mGroups.first());
        mSize--;
        mHasListHeader = false;

        setGroupPositions();
        notifyDataSetChanged();
    }

    /**
     * Whether the adapter has a list header.
     */
    public boolean hasListHeader() {
        return mHasListHeader;
    }

    /**
     * Adds a footer as the last group in this adapter.
     */
    public void addFooter() {
        if (mHasListFooter) return;

        ItemGroup footer = new ItemGroup(Long.MIN_VALUE);
        footer.mIsListFooter = true;

        mGroups.add(footer);
        mSize++;
        mHasListFooter = true;
    }

    /**
     * Removes the footer group if present.
     */
    public void removeFooter() {
        if (!mHasListFooter) return;

        mGroups.remove(mGroups.last());
        mSize--;
        mHasListFooter = false;
    }

    /**
     * Removes all items from this adapter.
     * @param notifyDataSetChanged Whether to notify that the data set has been changed.
     */
    public void clear(boolean notifyDataSetChanged) {
        mSize = 0;
        mHasListHeader = false;
        mHasListFooter = false;

        // Unset the positions of all items in the list.
        for (ItemGroup group : mGroups) group.resetPosition();
        mGroups.clear();

        if (notifyDataSetChanged) notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        if (!hasStableIds()) return RecyclerView.NO_ID;

        Pair<Date, TimedItem> pair = getItemAt(position);
        return pair.second == null ? getStableIdFromDate(pair.first) : pair.second.getStableId();
    }

    /**
     * Gets the item at the given position. For date headers and the list header, {@link TimedItem}
     * will be null; for normal items, {@link Date} will be null.
     */
    public Pair<Date, TimedItem> getItemAt(int position) {
        Pair<ItemGroup, Integer> pair = getGroupAt(position);
        ItemGroup group = pair.first;
        return new Pair<>(group.mDate, group.getItemAt(pair.second));
    }

    @Override
    public final int getItemViewType(int position) {
        Pair<ItemGroup, Integer> pair = getGroupAt(position);
        ItemGroup group = pair.first;
        if (pair.second == TYPE_HEADER) {
            return TYPE_HEADER;
        } else if (pair.second == TYPE_FOOTER) {
            return TYPE_FOOTER;
        } else if (pair.second == 0) {
            return TYPE_DATE;
        } else if (isSubsectionHeader(group.getItemAt(pair.second))) {
            return TYPE_SUBSECTION_HEADER;
        } else {
            return TYPE_NORMAL;
        }
    }

    @Override
    public final RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        if (viewType == TYPE_DATE) {
            return createDateViewHolder(parent);
        } else if (viewType == TYPE_NORMAL) {
            return createViewHolder(parent);
        } else if (viewType == TYPE_HEADER) {
            return createHeader(parent);
        } else if (viewType == TYPE_FOOTER) {
            return createFooter(parent);
        } else if (viewType == TYPE_SUBSECTION_HEADER) {
            return createSubsectionHeader(parent);
        }
        assert false;
        return null;
    }

    @Override
    public final void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        Pair<Date, TimedItem> pair = getItemAt(position);
        if (holder instanceof DateViewHolder) {
            ((DateViewHolder) holder).setDate(pair.first);
        } else if (holder instanceof SubsectionHeaderViewHolder) {
            bindViewHolderForSubsectionHeader((SubsectionHeaderViewHolder) holder, pair.second);
        } else if (!(holder instanceof BasicViewHolder)) {
            bindViewHolderForTimedItem(holder, pair.second);
        }
    }

    @Override
    public final int getItemCount() {
        return mSize;
    }

    /**
     * Utility method to traverse all groups and find the {@link ItemGroup} for the given position.
     */
    protected Pair<ItemGroup, Integer> getGroupAt(int position) {
        // TODO(ianwen): Optimize the performance if the number of groups becomes too large.
        if (mHasListHeader && position == 0) {
            assert mGroups.first().mIsListHeader;
            return new Pair<>(mGroups.first(), TYPE_HEADER);
        }

        if (mHasListFooter && position == mSize - 1) {
            assert mGroups.last().mIsListFooter;
            return new Pair<>(mGroups.last(), TYPE_FOOTER);
        }

        int i = position;
        for (ItemGroup group : mGroups) {
            if (i >= group.size()) {
                i -= group.size();
            } else {
                return new Pair<>(group, i);
            }
        }
        assert false;
        return null;
    }

    /**
     * @param item The item to remove from the adapter.
     */
    // #getGroupAt() asserts false before returning null, causing findbugs to complain about
    // a redundant nullcheck even though getGroupAt can return null.
    @SuppressFBWarnings({"RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE"})
    protected void removeItem(TimedItem item) {
        Pair<ItemGroup, Integer> groupPair = getGroupAt(item.getPosition());
        if (groupPair == null) {
            Log.e(TAG,
                    "Failed to find group for item during remove. Item position: "
                            + item.getPosition() + ", total size: " + mSize);
            return;
        }

        ItemGroup group = groupPair.first;
        group.removeItem(item);
        mSize--;

        // Remove the group if only the date header is left.
        if (group.size() == 1) {
            mGroups.remove(group);
            mSize--;
        }

        setGroupPositions();
        notifyDataSetChanged();
    }

    /**
     * Creates a long ID that identifies a particular day in history.
     * @param date Date to process.
     * @return Long that has the day of the year (1-365) in the lowest 16 bits and the year in the
     *         next 16 bits over.
     */
    private static long getStableIdFromDate(Date date) {
        Pair<Calendar, Calendar> pair = getCachedCalendars();
        Calendar calendar = pair.first;
        calendar.setTime(date);
        long dayOfYear = calendar.get(Calendar.DAY_OF_YEAR);
        long year = calendar.get(Calendar.YEAR);
        return (year << 16) + dayOfYear;
    }

    /**
     * Compares two {@link Date}s. Note if you already have two {@link Calendar} objects, use
     * {@link #compareCalendar(Calendar, Calendar)} instead.
     * @return 0 if date1 and date2 are in the same day; 1 if date1 is before date2; -1 otherwise.
     */
    protected static int compareDate(Date date1, Date date2) {
        Pair<Calendar, Calendar> pair = getCachedCalendars();
        Calendar cal1 = pair.first, cal2 = pair.second;
        cal1.setTime(date1);
        cal2.setTime(date2);
        return compareCalendar(cal1, cal2);
    }

    /**
     * @return 0 if cal1 and cal2 are in the same day; 1 if cal1 happens before cal2; -1 otherwise.
     */
    private static int compareCalendar(Calendar cal1, Calendar cal2) {
        boolean sameDay = cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)
                && cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
        if (sameDay) {
            return 0;
        } else if (cal1.before(cal2)) {
            return 1;
        } else {
            return -1;
        }
    }

    /**
     * Convenient getter for {@link #sCal1} and {@link #sCal2}.
     */
    private static Pair<Calendar, Calendar> getCachedCalendars() {
        Calendar cal1, cal2;
        try {
            cal1 = sCal1.get();
            cal2 = sCal2.get();
        } catch (InterruptedException | ExecutionException e) {
            // We've tried our best. If AsyncTask really does not work, we give up. :(
            cal1 = Calendar.getInstance();
            cal2 = Calendar.getInstance();
        }
        return new Pair<>(cal1, cal2);
    }

    /**
     * Wraps {@link Calendar#getInstance()} in an {@link AsyncTask} to avoid Strict mode violation.
     */
    private static AsyncTask<Void, Void, Calendar> createCalendar() {
        return new AsyncTask<Void, Void, Calendar>() {
            @Override
            protected Calendar doInBackground(Void... unused) {
                return Calendar.getInstance();
            }
        }.execute();
    }
}

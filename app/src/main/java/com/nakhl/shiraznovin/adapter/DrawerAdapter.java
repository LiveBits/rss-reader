
package com.nakhl.shiraznovin.adapter;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.nakhl.shiraznovin.MainApplication;
import com.nakhl.shiraznovin.R;
import com.nakhl.shiraznovin.provider.FeedData;
import com.nakhl.shiraznovin.provider.FeedData.EntryColumns;
import com.nakhl.shiraznovin.utils.PrefUtils;
import com.nakhl.shiraznovin.utils.StringUtils;
import com.nakhl.shiraznovin.utils.UiUtils;

import java.util.LinkedHashMap;
import java.util.Map;

public class DrawerAdapter extends BaseAdapter {

    private static final int POS_ID = 0;
    private static final int POS_URL = 1;
    private static final int POS_NAME = 2;
    private static final int POS_IS_GROUP = 3;
    private static final int POS_ICON = 4;
    private static final int POS_LAST_UPDATE = 5;
    private static final int POS_ERROR = 6;
    private static final int POS_UNREAD = 7;

    private static final String COLON = MainApplication.getContext().getString(R.string.colon);

    private static final int CACHE_MAX_ENTRIES = 100;
    private final Map<Long, String> mFormattedDateCache = new LinkedHashMap<Long, String>(CACHE_MAX_ENTRIES + 1, .75F, true) {
        @Override
        public boolean removeEldestEntry(Entry<Long, String> eldest) {
            return size() > CACHE_MAX_ENTRIES;
        }
    };

    private int mSelectedItem;
    private final Context mContext;
    private Cursor mFeedsCursor;
    private int mAllUnreadNumber, mFavoritesNumber;

    public DrawerAdapter(Context context, Cursor feedCursor) {
        mContext = context;
        mFeedsCursor = feedCursor;

        updateNumbers();
    }
    public void setSelectedItem(int selectedItem) {
        mSelectedItem = selectedItem;
    }


    public void setCursor(Cursor feedCursor) {
        mFeedsCursor = feedCursor;

        updateNumbers();
        notifyDataSetChanged();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.item_drawer_list, parent, false);

            ViewHolder holder = new ViewHolder();
            holder.iconView = (ImageView) convertView.findViewById(android.R.id.icon);
            holder.titleTxt = (TextView) convertView.findViewById(android.R.id.text1);
            holder.stateTxt = (TextView) convertView.findViewById(android.R.id.text2);
            holder.unreadTxt = (TextView) convertView.findViewById(R.id.unread_count);
            holder.separator = convertView.findViewById(R.id.separator);
            holder.separator.setBackgroundColor(ContextCompat.getColor(mContext, PrefUtils.getBoolean(PrefUtils.LIGHT_THEME, true) ? R.color.light_dividers:R.color.dark_dividers));
            convertView.setTag(R.id.holder, holder);
        }
        ViewHolder holder = (ViewHolder) convertView.getTag(R.id.holder);

        if(holder != null) {
            if (position == mSelectedItem) {
                holder.titleTxt.setTextColor(ContextCompat.getColor(mContext, PrefUtils.getBoolean(PrefUtils.LIGHT_THEME, true) ? R.color.light_primary_color : R.color.dark_primary_color));
            } else {
                holder.titleTxt.setTextColor(ContextCompat.getColor(mContext, PrefUtils.getBoolean(PrefUtils.LIGHT_THEME, true) ? R.color.light_base_text : R.color.dark_base_text));
            }

            // default init
            holder.iconView.setImageDrawable(null);
            holder.titleTxt.setText("");
            //holder.titleTxt.setAllCaps(false);
            holder.stateTxt.setVisibility(View.GONE);
            holder.unreadTxt.setText("");
            convertView.setPadding(0, 0, 0, 0);
            holder.separator.setVisibility(View.GONE);

            if (position == 0 || position == 1) {
                holder.titleTxt.setText(position == 0 ? R.string.all : R.string.favorites);
                holder.iconView.setImageResource(position == 0 ? R.drawable.ic_statusbar_rss : R.drawable.ic_star);
                if (position == mSelectedItem) {
                    holder.iconView.setColorFilter(ContextCompat.getColor(mContext, PrefUtils.getBoolean(PrefUtils.LIGHT_THEME, true) ? R.color.light_primary_color:R.color.dark_primary_color));
                } else {
                    holder.iconView.setColorFilter(ContextCompat.getColor(mContext, PrefUtils.getBoolean(PrefUtils.LIGHT_THEME, true) ? R.color.light_base_text:R.color.dark_base_text));
                }

                int unread = position == 0 ? mAllUnreadNumber : mFavoritesNumber;
                if (unread != 0) {
                    holder.unreadTxt.setText(String.valueOf(unread));
                }
            }
            /*if (mFeedsCursor != null && mFeedsCursor.moveToPosition(position - 5)) {
                holder.titleTxt.setText((mFeedsCursor.isNull(POS_NAME) ? mFeedsCursor.getString(POS_URL) : mFeedsCursor.getString(POS_NAME)));

                if (mFeedsCursor.getInt(POS_IS_GROUP) == 1) {
                    //holder.titleTxt.setAllCaps(true);
                    holder.separator.setVisibility(View.VISIBLE);
                    holder.iconView.setImageResource(R.drawable.ic_folder);
                    if (position == mSelectedItem) {
                        holder.iconView.setColorFilter(ContextCompat.getColor(mContext, PrefUtils.getBoolean(PrefUtils.LIGHT_THEME, true) ? R.color.light_primary_color : R.color.dark_primary_color));
                    } else {
                        holder.iconView.setColorFilter(ContextCompat.getColor(mContext, PrefUtils.getBoolean(PrefUtils.LIGHT_THEME, true) ? R.color.light_base_text : R.color.dark_base_text));
                    }
                } else {
                    holder.stateTxt.setVisibility(View.VISIBLE);

                    if (mFeedsCursor.isNull(POS_ERROR)) {
                        long timestamp = mFeedsCursor.getLong(POS_LAST_UPDATE);

                        // Date formatting is expensive, look at the cache
                        String formattedDate = mFormattedDateCache.get(timestamp);
                        if (formattedDate == null) {

                            formattedDate = mContext.getString(R.string.update) + COLON;

                            if (timestamp == 0) {
                                formattedDate += mContext.getString(R.string.never);
                            } else {
                                formattedDate += StringUtils.getDateTimeString(timestamp);
                            }

                            mFormattedDateCache.put(timestamp, formattedDate);
                        }

                        holder.stateTxt.setText(formattedDate);
                    } else {
                        holder.stateTxt.setText(new StringBuilder(mContext.getString(R.string.error)).append(COLON).append(mFeedsCursor.getString(POS_ERROR)));
                    }

                    final long feedId = mFeedsCursor.getLong(POS_ID);
                    Bitmap bitmap = UiUtils.getFaviconBitmap(feedId, mFeedsCursor, POS_ICON);

                    if (bitmap != null) {
                        holder.iconView.setImageBitmap(bitmap);
                    } else {
                        holder.iconView.setImageResource(R.mipmap.ic_launcher);
                    }

                    int unread = mFeedsCursor.getInt(POS_UNREAD);
                    if (unread != 0) {
                        holder.unreadTxt.setText(String.valueOf(unread));
                    }
                }
                if((mFeedsCursor.isNull(POS_NAME) ? mFeedsCursor.getString(POS_URL) : mFeedsCursor.getString(POS_NAME)).startsWith("ERROR:")){
                    LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    convertView = inflater.inflate(R.layout.item_drawer_null, parent, false);
                    return convertView;
                }
            }*/
        }
        return convertView;
    }

    @Override
    public int getCount() {
        if (mFeedsCursor != null) {
            return mFeedsCursor.getCount() + 5;
        }
        return 0;
    }

    @Override
    public Object getItem(int position) {
        return null;
    }

    @Override
    public long getItemId(int position) {
        if (mFeedsCursor != null && mFeedsCursor.moveToPosition(position - 5)) {
            return mFeedsCursor.getLong(POS_ID);
        }

        return -1;
    }

    public byte[] getItemIcon(int position) {
        if (mFeedsCursor != null && mFeedsCursor.moveToPosition(position - 5)) {
            return mFeedsCursor.getBlob(POS_ICON);
        }

        return null;
    }

    public String getItemName(int position) {
        if (mFeedsCursor != null && mFeedsCursor.moveToPosition(position - 5)) {
            return mFeedsCursor.isNull(POS_NAME) ? mFeedsCursor.getString(POS_URL) : mFeedsCursor.getString(POS_NAME);
        }

        return null;
    }

    public boolean isItemAGroup(int position) {
        return mFeedsCursor != null && mFeedsCursor.moveToPosition(position - 5) && mFeedsCursor.getInt(POS_IS_GROUP) == 1;

    }

    private void updateNumbers() {
        mAllUnreadNumber = mFavoritesNumber = 0;

        // Gets the numbers of entries (should be in a thread, but it's way easier like this and it shouldn't be so slow)
        Cursor numbers = mContext.getContentResolver().query(EntryColumns.CONTENT_URI, new String[]{FeedData.ALL_UNREAD_NUMBER, FeedData.FAVORITES_NUMBER}, null, null, null);
        if (numbers != null) {
            if (numbers.moveToFirst()) {
                mAllUnreadNumber = numbers.getInt(0);
                mFavoritesNumber = numbers.getInt(1);
            }
            numbers.close();
        }
    }

    private static class ViewHolder {
        public ImageView iconView;
        public TextView titleTxt;
        public TextView stateTxt;
        public TextView unreadTxt;
        public View separator;
    }
}

/**
 * spaRSS
 * <p/>
 * Copyright (c) 2015 Arnaud Renaud-Goud
 * Copyright (c) 2012-2015 Frederic Julian
 * <p/>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p/>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nakhl.shiraznovin.activity;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.LoaderManager;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Loader;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Toast;

import com.melnykov.fab.FloatingActionButton;

import com.nakhl.shiraznovin.Constants;
import com.nakhl.shiraznovin.R;
import com.nakhl.shiraznovin.adapter.DrawerAdapter;
import com.nakhl.shiraznovin.fragment.EntriesListFragment;
import com.nakhl.shiraznovin.provider.FeedData;
import com.nakhl.shiraznovin.provider.FeedData.EntryColumns;
import com.nakhl.shiraznovin.provider.FeedData.FeedColumns;
import com.nakhl.shiraznovin.provider.FeedDataContentProvider;
import com.nakhl.shiraznovin.service.FetcherService;
import com.nakhl.shiraznovin.service.RefreshService;
import com.nakhl.shiraznovin.utils.PrefUtils;
import com.nakhl.shiraznovin.utils.UiUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;

public class HomeActivity extends BaseActivity implements LoaderManager.LoaderCallbacks<Cursor>{

    static final String FEED_SEARCH_TITLE = "title";
    static final String FEED_SEARCH_URL = "url";
    static final String FEED_SEARCH_DESC = "contentSnippet";
    private static final String STATE_CURRENT_TAB = "STATE_CURRENT_TAB";

    private static final String STATE_CURRENT_DRAWER_POS = "STATE_CURRENT_DRAWER_POS";

    private static final String FEED_UNREAD_NUMBER = "(SELECT " + Constants.DB_COUNT + " FROM " + EntryColumns.TABLE_NAME + " WHERE " +
            EntryColumns.IS_READ + " IS NULL AND " + EntryColumns.FEED_ID + '=' + FeedColumns.TABLE_NAME + '.' + FeedColumns._ID + ')';

    private static final String WHERE_UNREAD_ONLY = "(SELECT " + Constants.DB_COUNT + " FROM " + EntryColumns.TABLE_NAME + " WHERE " +
            EntryColumns.IS_READ + " IS NULL AND " + EntryColumns.FEED_ID + "=" + FeedColumns.TABLE_NAME + '.' + FeedColumns._ID + ") > 0" +
            " OR (" + FeedColumns.IS_GROUP + "=1 AND (SELECT " + Constants.DB_COUNT + " FROM " + FeedData.ENTRIES_TABLE_WITH_FEED_INFO +
            " WHERE " + EntryColumns.IS_READ + " IS NULL AND " + FeedColumns.GROUP_ID + '=' + FeedColumns.TABLE_NAME + '.' + FeedColumns._ID +
            ") > 0)";

    private static final int LOADER_ID = 0;
    private static final int SEARCH_DRAWER_POSITION = -1;

    private EntriesListFragment mEntriesFragment;
    private DrawerLayout mDrawerLayout;
    private View mLeftDrawer;
    private ListView mDrawerList;
    private DrawerAdapter mDrawerAdapter;
    private ActionBarDrawerToggle mDrawerToggle;
    private FloatingActionButton mDrawerHideReadButton;
    private final SharedPreferences.OnSharedPreferenceChangeListener mShowReadListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (PrefUtils.SHOW_READ.equals(key)) {
                getLoaderManager().restartLoader(LOADER_ID, null, HomeActivity.this);

                if (mDrawerHideReadButton != null) {
                    UiUtils.updateHideReadButton(mDrawerHideReadButton);
                }
            }
        }
    };
    private CharSequence mTitle;
    private BitmapDrawable mIcon;
    private int mCurrentDrawerPos;

    private boolean mCanQuit = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //UiUtils.setPreferenceTheme(this);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_home);

        //getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        mEntriesFragment = (EntriesListFragment) getFragmentManager().findFragmentById(R.id.entries_list_fragment);

        mTitle = getTitle();

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mLeftDrawer = findViewById(R.id.left_drawer);
        mDrawerList = (ListView) findViewById(R.id.drawer_list);
        mDrawerList.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        mDrawerList.setOnItemClickListener(new ListView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                selectDrawerItem(position);
                if (mDrawerLayout != null) {
                    mDrawerLayout.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mDrawerLayout.closeDrawer(mLeftDrawer);
                        }
                    }, 50);
                }
            }
        });

        mLeftDrawer.setBackgroundColor((ContextCompat.getColor(getApplicationContext(), PrefUtils.getBoolean(PrefUtils.LIGHT_THEME, true) ? R.color.light_primary_color : R.color.dark_primary_color)));
        mDrawerList.setBackgroundColor((ContextCompat.getColor(getApplicationContext(), PrefUtils.getBoolean(PrefUtils.LIGHT_THEME, true) ? R.color.light_background : R.color.dark_primary_color_light)));
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (mDrawerLayout != null) {
            mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);
            mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, R.string.drawer_open, R.string.drawer_close) {
                @Override
                public void onDrawerSlide(View drawerView, float slideOffset) {
                    super.onDrawerSlide(drawerView, 0);
                }
            };
            mDrawerLayout.setDrawerListener(mDrawerToggle);

            if (PrefUtils.getBoolean(PrefUtils.LEFT_PANEL, false)) {
                mDrawerLayout.openDrawer(mLeftDrawer);
            }
        }

        mDrawerHideReadButton = (FloatingActionButton) mLeftDrawer.findViewById(R.id.hide_read_button);
        if (mDrawerHideReadButton != null) {
            mDrawerHideReadButton.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View view) {
                    UiUtils.displayHideReadButtonAction(HomeActivity.this);
                    return true;
                }
            });
            UiUtils.updateHideReadButton(mDrawerHideReadButton);
            UiUtils.addEmptyFooterView(mDrawerList, 90);
        }

        if (savedInstanceState != null) {
            mCurrentDrawerPos = savedInstanceState.getInt(STATE_CURRENT_DRAWER_POS);
        }

        getLoaderManager().initLoader(LOADER_ID, null, this);

        if (PrefUtils.getBoolean(PrefUtils.REFRESH_ENABLED, true)) {
            // starts the service independent to this activity
            startService(new Intent(this, RefreshService.class));
        } else {
            stopService(new Intent(this, RefreshService.class));
        }
        if (PrefUtils.getBoolean(PrefUtils.REFRESH_ON_OPEN_ENABLED, false)) {
            if (!PrefUtils.getBoolean(PrefUtils.IS_REFRESHING, false)) {
                startService(new Intent(HomeActivity.this, FetcherService.class).setAction(FetcherService.ACTION_REFRESH_FEEDS));
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(STATE_CURRENT_DRAWER_POS, mCurrentDrawerPos);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        PrefUtils.registerOnPrefChangeListener(mShowReadListener);
    }

    @Override
    protected void onPause() {
        PrefUtils.unregisterOnPrefChangeListener(mShowReadListener);
        super.onPause();
    }

    @Override
    public void finish() {
        if (mDrawerLayout != null) {
            if(mDrawerLayout.isDrawerOpen(mLeftDrawer)) {
                mDrawerLayout.closeDrawer(mLeftDrawer);
                return;
            }
        }

        if (mCanQuit) {
            super.finish();
            return;
        }

        Toast.makeText(this, R.string.back_again_to_quit, Toast.LENGTH_SHORT).show();
        mCanQuit = true;
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                mCanQuit = false;
            }
        }, 3000);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        // We reset the current drawer position
        selectDrawerItem(0);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (mDrawerToggle != null && mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void onClickHideRead(View view) {
        if (!PrefUtils.getBoolean(PrefUtils.SHOW_READ, true)) {
            PrefUtils.putBoolean(PrefUtils.SHOW_READ, true);
        } else {
            PrefUtils.putBoolean(PrefUtils.SHOW_READ, false);
        }
    }

    public void onClickEditFeeds(View view) {
        startActivity(new Intent(this, EditFeedsListActivity.class));
    }

    public void onClickSearch(View view) {
        selectDrawerItem(SEARCH_DRAWER_POSITION);
        if (mDrawerLayout != null) {
            mDrawerLayout.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mDrawerLayout.closeDrawer(mLeftDrawer);
                }
            }, 50);
        }
    }

    public void onClickSettings(View view) {
        startActivity(new Intent(this, GeneralPrefsActivity.class));
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        if (mDrawerToggle != null) {
            mDrawerToggle.syncState();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mDrawerToggle != null) {
            mDrawerToggle.onConfigurationChanged(newConfig);
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        CursorLoader cursorLoader = new CursorLoader(this, FeedColumns.GROUPED_FEEDS_CONTENT_URI, new String[]{FeedColumns._ID, FeedColumns.URL, FeedColumns.NAME,
                FeedColumns.IS_GROUP, FeedColumns.ICON, FeedColumns.LAST_UPDATE, FeedColumns.ERROR, FEED_UNREAD_NUMBER},
                PrefUtils.getBoolean(PrefUtils.SHOW_READ, true) ? "" : WHERE_UNREAD_ONLY, null, null
        );
        cursorLoader.setUpdateThrottle(Constants.UPDATE_THROTTLE_DELAY);
        return cursorLoader;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        if (mDrawerAdapter != null) {
            mDrawerAdapter.setCursor(cursor);
        } else {
            mDrawerAdapter = new DrawerAdapter(this, cursor);
            mDrawerList.post(new Runnable() {
                public void run() {
                    mDrawerList.setAdapter(mDrawerAdapter);
                    selectDrawerItem(mCurrentDrawerPos);
                }
            });
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
        if (mDrawerAdapter == null)
            return;

        mDrawerAdapter.setCursor(null);
    }

    private void selectDrawerItem(int position) {
        mCurrentDrawerPos = position;

        if (mDrawerAdapter == null)
            return;

        mDrawerAdapter.setSelectedItem(position);
        mIcon = null;

        Uri newUri = null;
        boolean showFeedInfo = true;

        switch (position) {
            case SEARCH_DRAWER_POSITION:
                newUri = EntryColumns.SEARCH_URI(mEntriesFragment.getCurrentSearch());
                break;
            case 0:
                newUri = EntryColumns.ALL_ENTRIES_CONTENT_URI;
                break;
            case 1:
                newUri = EntryColumns.FAVORITES_CONTENT_URI;
                break;
            default:
                    long feedOrGroupId = mDrawerAdapter.getItemId(position);
                    if (mDrawerAdapter.isItemAGroup(position)) {
                        newUri = EntryColumns.ENTRIES_FOR_GROUP_CONTENT_URI(feedOrGroupId);
                    } else {
                        byte[] iconBytes = mDrawerAdapter.getItemIcon(position);
                        Bitmap bitmap = UiUtils.getScaledBitmap(iconBytes, 24);
                        if (bitmap != null) {
                            mIcon = new BitmapDrawable(getResources(), bitmap);
                        }

                        newUri = EntryColumns.ENTRIES_FOR_FEED_CONTENT_URI(feedOrGroupId);
                        showFeedInfo = false;
                    }
                mTitle = mDrawerAdapter.getItemName(position);
                break;
        }

        if (!newUri.equals(mEntriesFragment.getUri())) {
            mEntriesFragment.setData(newUri, showFeedInfo);
        }

        mDrawerList.setItemChecked(position, true);

        // First open => we open the drawer for you
        if (PrefUtils.getBoolean(PrefUtils.FIRST_OPEN, true)) {
            PrefUtils.putBoolean(PrefUtils.FIRST_OPEN, false);
            if (mDrawerLayout != null) {
                mDrawerLayout.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mDrawerLayout.openDrawer(mLeftDrawer);
                    }
                }, 500);
            }

            /*AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.welcome_title)
                    .setItems(new CharSequence[]{getString(R.string.google_news_title), getString(R.string.add_custom_feed)}, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            if (which == 1) {
                                startActivity(new Intent(Intent.ACTION_INSERT).setData(FeedColumns.CONTENT_URI));
                            } else {
                                startActivity(new Intent(HomeActivity.this, AddGoogleNewsActivity.class));
                            }
                        }
                    });
            builder.show();*/

            addFeed();
        }
        refreshTitle(0);
    }

    private void addFeed() {

        final String name = getText(R.string.app_name).toString();
        final String urlOrSearch = "salamdena.ir";
        final String cookieName = "";
        final String cookieValue = "";
        final TypedArray selectedValues = getResources().obtainTypedArray(R.array.settings_keep_time_values);
        final Integer keepTime = 1; //every day

        if (urlOrSearch.isEmpty()) {
            Toast.makeText(this, R.string.error_feed_error, Toast.LENGTH_SHORT).show();
        }

        if (true) {
            final ProgressDialog pd = new ProgressDialog(HomeActivity.this);
            pd.setMessage(getString(R.string.loading));
            pd.setCancelable(true);
            pd.setIndeterminate(true);
            pd.show();

            getLoaderManager().restartLoader(1, null, new LoaderManager.LoaderCallbacks<ArrayList<HashMap<String, String>>>() {

                @Override
                public Loader<ArrayList<HashMap<String, String>>> onCreateLoader(int id, Bundle args) {
                    String encodedSearchText = urlOrSearch;
                    try {
                        encodedSearchText = URLEncoder.encode(urlOrSearch, Constants.UTF8);
                    } catch (UnsupportedEncodingException ignored) {
                    }

                    return new GetFeedSearchResultsLoader(HomeActivity.this, encodedSearchText);
                }

                @Override
                public void onLoadFinished(Loader<ArrayList<HashMap<String, String>>> loader, final ArrayList<HashMap<String, String>> data) {
                    pd.cancel();

                    if (data == null) {
                        Toast.makeText(HomeActivity.this, R.string.error, Toast.LENGTH_SHORT).show();
                    } else if (data.isEmpty()) {
                        Toast.makeText(HomeActivity.this, R.string.no_result, Toast.LENGTH_SHORT).show();
                    } else {
                        AlertDialog.Builder builder = new AlertDialog.Builder(HomeActivity.this);
                        builder.setTitle(R.string.feed_search);

                        // create the grid item mapping
                        String[] from = new String[]{FEED_SEARCH_TITLE, FEED_SEARCH_DESC};
                        int[] to = new int[]{android.R.id.text1, android.R.id.text2};

                        // fill in the grid_item layout
                        SimpleAdapter adapter = new SimpleAdapter(HomeActivity.this, data, R.layout.item_search_result, from,
                                to);
                        builder.setAdapter(adapter, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                FeedDataContentProvider.addFeed(HomeActivity.this, data.get(which).get(FEED_SEARCH_URL), name.isEmpty() ? data.get(which).get(FEED_SEARCH_TITLE) : name, true, cookieName, cookieValue, keepTime);

                                finish();
                            }
                        });
                        builder.show();
                    }
                }

                @Override
                public void onLoaderReset(Loader<ArrayList<HashMap<String, String>>> loader) {
                }
            });
        }
        //else {
        //    FeedDataContentProvider.addFeed(HomeActivity.this, urlOrSearch, name, true, cookieName, cookieValue, keepTime);
        //}

        //insert in database
        ContentResolver cr = getContentResolver();

        String url = null;
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(FeedColumns.CONTENT_URI, FeedColumns.PROJECTION_ID,
                    FeedColumns.URL + Constants.DB_ARG, new String[]{urlOrSearch}, null);

            if (cursor != null && cursor.moveToFirst() && !getIntent().getData().getLastPathSegment().equals(cursor.getString(0))) {
                Toast.makeText(HomeActivity.this, R.string.error_feed_url_exists, Toast.LENGTH_LONG).show();
            } else {
                ContentValues values = new ContentValues();

                if (!urlOrSearch.startsWith(Constants.HTTP_SCHEME) && !urlOrSearch.startsWith(Constants.HTTPS_SCHEME)) {
                    url = Constants.HTTP_SCHEME + urlOrSearch;
                }
                values.put(FeedColumns.URL, url);

                String loginHTTPAuth = "";
                String passwordHTTPAuth = "";

                values.put(FeedColumns.NAME, name.trim().length() > 0 ? name : null);
                values.put(FeedColumns.RETRIEVE_FULLTEXT, 1);
                values.put(FeedColumns.COOKIE_NAME, cookieName.trim().length() > 0 ? cookieName : "");
                values.put(FeedColumns.COOKIE_VALUE, cookieValue.trim().length() > 0 ? cookieValue : "");
                values.put(FeedColumns.HTTP_AUTH_LOGIN, loginHTTPAuth.trim().length() > 0 ? loginHTTPAuth : "");
                values.put(FeedColumns.HTTP_AUTH_PASSWORD, passwordHTTPAuth.trim().length() > 0 ? passwordHTTPAuth : "");
                values.put(FeedColumns.KEEP_TIME, 0);
                values.put(FeedColumns.FETCH_MODE, 0);
                values.putNull(FeedColumns.ERROR);

                cr.update(getIntent().getData(), values, null, null);
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public void refreshTitle(int mNewEntriesNumber) {
        switch (mCurrentDrawerPos) {
            case SEARCH_DRAWER_POSITION:
                getSupportActionBar().setTitle(android.R.string.search_go);
                getSupportActionBar().setIcon(R.drawable.ic_search);
                break;
            case 0:
                getSupportActionBar().setTitle(R.string.all);
                getSupportActionBar().setIcon(R.drawable.ic_statusbar_rss);
                break;
            case 1:
                getSupportActionBar().setTitle(R.string.favorites);
                getSupportActionBar().setIcon(R.drawable.ic_star);
                break;
            default:
                getSupportActionBar().setTitle(mTitle);
                if (mIcon != null) {
                    getSupportActionBar().setIcon(mIcon);
                } else {
                    getSupportActionBar().setIcon(null);
                }
                break;
        }
        if (mNewEntriesNumber != 0) {
            getSupportActionBar().setTitle(getSupportActionBar().getTitle().toString() + " (" + String.valueOf(mNewEntriesNumber) + ")" );
        }
        invalidateOptionsMenu();
    }
}
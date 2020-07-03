package com.nakhl.shiraznovin.activity;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.view.MenuItem;
import android.widget.TextView;

import com.nakhl.shiraznovin.R;
import com.nakhl.shiraznovin.fragment.AboutFragment;
import com.nakhl.shiraznovin.utils.UiUtils;

public class AboutActivity extends BaseActivity {

    private AboutFragment mAboutFragment;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        UiUtils.setPreferenceTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        //mAboutFragment = (AboutFragment) getFragmentManager().findFragmentById(R.id.about_fragment);
        if (savedInstanceState == null) { // Put the data only the first time (the fragment will save its state)
            //mAboutFragment.setData(getIntent().getData());
        }

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        String title;
        PackageManager manager = this.getPackageManager();
        try {
            PackageInfo info = manager.getPackageInfo(this.getPackageName(), 0);
            title = "spaRSS version " + info.versionName;
        } catch (NameNotFoundException unused) {
            title = "spaRSS";
        }

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        switch (menuItem.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
        }
        return (super.onOptionsItemSelected(menuItem));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        //mEntryFragment.setData(intent.getData());
    }

}


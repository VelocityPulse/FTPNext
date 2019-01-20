package com.example.ftpnext;

import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.example.ftpnext.core.AppCore;
import com.example.ftpnext.core.LogManager;
import com.example.ftpnext.database.DataBase;
import com.example.ftpnext.database.TableTest1.TableTest1;

import java.util.List;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private static String TAG = "MAIN ACTIVITY";

    private AppCore mAppCore = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar lToolBar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(lToolBar);

        FloatingActionButton lFloatingActionButton = (FloatingActionButton) findViewById(R.id.fab);
        lFloatingActionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "You have to be connected.", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        DrawerLayout lDrawer = (DrawerLayout) findViewById(R.id.drawer_layout);

        ActionBarDrawerToggle lToggle = new ActionBarDrawerToggle(
                this, lDrawer, lToolBar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);

        lDrawer.addDrawerListener(lToggle);
        lToggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        mAppCore = new AppCore(this);
        mAppCore.startApplication();


        DataBase.getTableTest1Dao().add(new TableTest1(10));

        List<TableTest1> lTableTest1s = DataBase.getTableTest1Dao().fetchAll();
        for (TableTest1 lTableTest1 : lTableTest1s) {
            LogManager.info(TAG, "table test value : " + lTableTest1.getValue());
        }

        DataBase.getTableTest1Dao().delete(lTableTest1s.get(1).getId());
        LogManager.info(TAG, "deleted");

        lTableTest1s = DataBase.getTableTest1Dao().fetchAll();
        for (TableTest1 lTableTest1 : lTableTest1s) {
            LogManager.info(TAG, "table test value : " + lTableTest1.getValue());
        }


    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    // ID OF MENU BUTTONS
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        Log.i(TAG, "id : " + id);
        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_camera) {
            Log.i(TAG, "nav camera clicked");
            // Handle the camera action
        } else if (id == R.id.nav_gallery) {
            Log.i(TAG, "nav gallery clicked");
        } else if (id == R.id.nav_slideshow) {
            Log.i(TAG, "nav slideshow clicked");
        } else if (id == R.id.nav_manage) {
            Log.i(TAG, "nav manage clicked");
        } else if (id == R.id.nav_share) {
            Log.i(TAG, "nav share clicked");
        } else if (id == R.id.nav_send) {
            Log.i(TAG, "nav send clicked");
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }
}

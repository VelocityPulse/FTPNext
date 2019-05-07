package com.example.ftpnext;

import android.app.ActivityOptions;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;

import com.example.ftpnext.core.AppCore;
import com.example.ftpnext.core.LogManager;
import com.example.ftpnext.database.DataBase;
import com.example.ftpnext.database.DataBaseTests;
import com.example.ftpnext.database.FTPServerTable.FTPServer;
import com.example.ftpnext.database.FTPServerTable.FTPServerDAO;
import com.example.ftpnext.database.TableTest1.TableTest1;

import java.util.List;

/*
TODO : Resume when screen change orientation

Ideas :
    Queues of file to download even if the connection fail
    Wifi download/upload only
    Notification view of transfer
    Check if the download can continue after a stop
    Connection with ssh
    Multiple theme
 */

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private static String TAG = "MAIN ACTIVITY";

    private AppCore mAppCore = null;

    private LinearLayout mMainRootLinearLayout;
    private MainRecyclerViewAdapter mAdapter;
    private FloatingActionButton mFloatingActionButton;
    private boolean mIsBlockedScrollView;
    private View mRootView;

    private FTPServerDAO mFTPServerDAO;

    @Override
    protected void onCreate(Bundle iSavedInstanceState) {
        super.onCreate(iSavedInstanceState);
        // TODO introduce animation / splash screen
//        getWindow().requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS);
//        Slide lSlide = new Slide();
//        lSlide.setSlideEdge(Gravity.BOTTOM);
//        getWindow().setEnterTransition(lSlide);

        setContentView(R.layout.activity_main);

        initializeGUI();
        initialize();

        runTests();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // TODO save the form for change orientation of phone
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
//        LogManager.error(String.valueOf(mFormAnimationManager.isFormOpen()));

//        if (drawer.isDrawerOpen(GravityCompat.START)) {
//            drawer.closeDrawer(GravityCompat.START);
//        } else if (mFormAnimationManager.isFormOpen()) {
//            mFormAnimationManager.closeForm();
//        } else {
        super.onBackPressed();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
//        if (!mFormAnimationManager.isFormOpen())
//            getMenuInflater().inflate(R.menu.main, menu);
//        else
        getMenuInflater().inflate(R.menu.activity_main_form, menu);
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

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    private void initializeGUI() {

        Toolbar lToolBar = findViewById(R.id.toolbar);
        setSupportActionBar(lToolBar);

        mFloatingActionButton = findViewById(R.id.floating_action_button);
        mFloatingActionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Sample snackbar
//                Snackbar.make(view, "You have to be connected.", Snackbar.LENGTH_LONG)
//                        .setAction("Action", null).show();

                startConfigureFTPServerActivity();
            }
        });

        DrawerLayout lDrawer = findViewById(R.id.drawer_layout);

        ActionBarDrawerToggle lToggle = new ActionBarDrawerToggle(
                this, lDrawer, lToolBar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);

        lDrawer.addDrawerListener(lToggle);
        lToggle.syncState();

        RecyclerView lRecyclerView = findViewById(R.id.main_recycler_view);
        lRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new MainRecyclerViewAdapter(lRecyclerView, this);
        lRecyclerView.setAdapter(mAdapter);

        mRootView = findViewById(R.id.main_root_linear_layout);
    }

    public void initialize() {
        LogManager.info(TAG, "Initialize");
        mAppCore = new AppCore(this);
        mAppCore.startApplication();
        mFTPServerDAO = DataBase.getFTPServerDAO();

        List<FTPServer> lFTPServers = mFTPServerDAO.fetchAll();
        for (FTPServer lFTPServer : lFTPServers) {
            mAdapter.insertItem(lFTPServer.getName());
        }
    }

    public void onActivityResult(int iRequestCode, int iResultCode, Intent iResultData) {
        if (iRequestCode == ConfigureFTPServerActivity.ACTIVITY_REQUEST_CODE) {
            if (iResultCode == ConfigureFTPServerActivity.ACTIVITY_RESULT_ADD_SUCCESS) {
                int lId;
                if ((lId = iResultData.getIntExtra(ConfigureFTPServerActivity.KEY_DATABASE_ID, -1)) != -1) {
                    mAdapter.insertItem(mFTPServerDAO.fetchById(lId).getName());
                }
            }
        }
    }

    private void startConfigureFTPServerActivity() {
        Intent lIntent = new Intent(MainActivity.this, ConfigureFTPServerActivity.class);
        startActivityForResult(lIntent,
                ConfigureFTPServerActivity.ACTIVITY_REQUEST_CODE,
                ActivityOptions.makeSceneTransitionAnimation(MainActivity.this).toBundle());
    }

    private void runTests() {
        DataBaseTests.runTests(new TableTest1(), DataBase.getTableTest1DAO());
        DataBaseTests.runTests(new FTPServer(), DataBase.getFTPServerDAO());

//        for (int i = 0; i < 20; i++) {
//            mAdapter.mItemList.add("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" + i);
//        }


//        TextInputLayout lTextInputLayout = (TextInputLayout) findViewById(R.id.form_server_host);
//        lTextInputLayout.setError("error");
//        lTextInputLayout.setErrorTextColor(ColorStateList.valueOf(0xFFDE4255));
    }

    public void OnClickServerItem(View view) {
        LogManager.info("click");
    }
}

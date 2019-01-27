package com.example.ftpnext;

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
import android.view.ViewTreeObserver;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Transformation;

import com.example.ftpnext.core.AppCore;
import com.example.ftpnext.core.LogManager;
import com.example.ftpnext.database.DataBase;
import com.example.ftpnext.database.DataBaseTests;
import com.example.ftpnext.database.FTPHostTable.FTPHost;
import com.example.ftpnext.database.TableTest1.TableTest1;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private static String TAG = "MAIN ACTIVITY";

    private AppCore mAppCore = null;
    private LinearLayoutManager mLayoutManager;
    private RecyclerView mRecyclerView;
    private MainRecyclerViewAdapter mAdapter;
    private HostFormAnimationManager mHostFormAnimationManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeGUI();


        mAppCore = new AppCore(this);
        mAppCore.startApplication();

        runTests();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // TODO save the form for change orientation of phone
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        LogManager.error(String.valueOf(mHostFormAnimationManager.isFormOpen()));
        if (/*mHostFormAnimationManager.isFormOpen()*/true) {
            mHostFormAnimationManager.closeForm();
        } else {
            if (drawer.isDrawerOpen(GravityCompat.START)) {
                drawer.closeDrawer(GravityCompat.START);
            } else {
                super.onBackPressed();
            }
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

    private void initializeGUI() {
        Toolbar lToolBar = findViewById(R.id.toolbar);
        setSupportActionBar(lToolBar);

        final FloatingActionButton lFloatingActionButton = findViewById(R.id.floating_action_button);
        lFloatingActionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
//                Snackbar.make(view, "You have to be connected.", Snackbar.LENGTH_LONG)
//                        .setAction("Action", null).show();

                mAdapter.mItemList.add("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
                mAdapter.notifyDataSetChanged();
                mHostFormAnimationManager.openForm();
            }
        });

        // TODO block left nav in form mode
        DrawerLayout lDrawer = findViewById(R.id.drawer_layout);

        ActionBarDrawerToggle lToggle = new ActionBarDrawerToggle(
                this, lDrawer, lToolBar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);


        mRecyclerView = findViewById(R.id.main_recycler_view);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new MainRecyclerViewAdapter(new ArrayList<String>());
        mRecyclerView.setAdapter(mAdapter);


        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (dy > 0 || dy < 0 && lFloatingActionButton.isShown())
                    lFloatingActionButton.hide();
            }

            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {

                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    lFloatingActionButton.show();
                }
                super.onScrollStateChanged(recyclerView, newState);
            }
        });

        // TODO rename this
        View mainListdebug = findViewById(R.id.main_host_list);
        View mainRootLinearLayout = findViewById(R.id.main_root_linear_layout);

        mHostFormAnimationManager = new HostFormAnimationManager(mainListdebug, mainRootLinearLayout);

    }

    private void runTests() {

        DataBaseTests.runTests(new TableTest1(), DataBase.getTableTest1DAO());
        DataBaseTests.runTests(new FTPHost(), DataBase.getFTPHostDAO());

    }

    private class HostFormAnimationManager extends Animation {
        private int mTargetHeight;
        private int mStartHeight;
        private View mRootView;
        private View mHostView;
        private int mRootViewHeight;
        private int mHostViewHeight;
        private boolean mIsFormOpen;
        private boolean mIsAnimating;

        public HostFormAnimationManager(View iView, View iRootView) {
            mHostView = iView;
            mRootView = iRootView;

            this.setInterpolator(new DecelerateInterpolator(5F));
            this.setDuration(getResources().getInteger(R.integer.form_animation_time));

            ViewTreeObserver observer = mRootView.getViewTreeObserver();
            observer.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    mRootViewHeight = mRootView.getHeight();
                    mHostViewHeight = mHostView.getHeight();
//                    LogManager.info(TAG, "main root linear layout updated : " + mHostViewHeight);
                }
            });

        }

        public void openForm() {
            LogManager.error("open form");
            mTargetHeight = 0;
            mStartHeight = mRootViewHeight;
            if (!mIsAnimating)
                mHostView.clearAnimation();
            else
                mStartHeight = mHostViewHeight;
            mHostView.startAnimation(this);
            mIsAnimating = true;
            mIsFormOpen = true;
        }

        public void closeForm() {
            LogManager.error("close form");
            mTargetHeight = mRootViewHeight;
            mStartHeight = 0;
            if (!mIsAnimating)
                mHostView.clearAnimation();
            else
                mStartHeight = mHostViewHeight;
            mHostView.startAnimation(this);
            mIsAnimating = true;
            mIsFormOpen = false;
        }

        @Override
        protected void applyTransformation(float iInterpolatedTime, Transformation iT) {
            //to support decent animation, change new height as Nico S. recommended in comments
            int newHeight = (int) (mStartHeight + (mTargetHeight - mStartHeight) * iInterpolatedTime);

//            LogManager.info(String.valueOf(newHeight) + " | " + mTargetHeight + " | " + mStartHeight);
            mHostView.getLayoutParams().height = newHeight;
            mHostView.requestLayout();

            if (newHeight == mTargetHeight)
                mIsAnimating = false;
        }

        public boolean isFormOpen() {
            return mIsFormOpen;
        }

        public boolean isAnimating() {
            return mIsAnimating;
        }
    }
}

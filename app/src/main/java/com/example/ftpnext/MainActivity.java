package com.example.ftpnext;

import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
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
import android.view.animation.Transformation;
import android.widget.LinearLayout;

import com.example.ftpnext.core.AppCore;
import com.example.ftpnext.core.LogManager;
import com.example.ftpnext.database.DataBase;
import com.example.ftpnext.database.FTPHostTable.FTPHost;
import com.example.ftpnext.database.TableTest1.TableTest1;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private static String TAG = "MAIN ACTIVITY";

    private AppCore mAppCore = null;
    private LinearLayoutManager mLayoutManager;
    private RecyclerView mRecyclerView;
    private MainRecyclerViewAdapter mAdapter;
    private LinearLayout mainListdebug;
    private ResizeAnimation resizeAnimation;
    private int size;

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



            resizeAnimation = new ResizeAnimation(
                    mainListdebug,
                    0,
                    size
            );
            resizeAnimation.setDuration(1000);

            mainListdebug.startAnimation(resizeAnimation);
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

        final FloatingActionButton lFloatingActionButton = findViewById(R.id.fab);
        lFloatingActionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "You have to be connected.", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();

                mAdapter.mItemList.add("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
                mAdapter.notifyDataSetChanged();

            }
        });

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

        mainListdebug = (LinearLayout) findViewById(R.id.mainlist_debug);

        final ViewTreeObserver observer= mainListdebug.getViewTreeObserver();
        observer.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                size = mainListdebug.getHeight();
                LogManager.info("debug " + size);
//                observer.removeGlobalOnLayoutListener(this);
            }
        });




    }

    class ResizeAnimation extends Animation {
        final int targetHeight;
        int startHeight;
        View view;

        public ResizeAnimation(View view, int targetHeight, int startHeight) {
            this.view = view;
            this.targetHeight = targetHeight;
            this.startHeight = startHeight;
        }

        @Override
        protected void applyTransformation(float interpolatedTime, Transformation t) {
            //to support decent animation, change new heigt as Nico S. recommended in comments
            int newHeight = (int) (startHeight+(targetHeight - startHeight) * interpolatedTime);

            LogManager.info(String.valueOf(newHeight) + " | " + targetHeight + " | " + startHeight);
            view.getLayoutParams().height = newHeight;
            view.requestLayout();
        }

        @Override
        public void initialize(int width, int height, int parentWidth, int parentHeight) {
            super.initialize(width, height, parentWidth, parentHeight);
        }

        @Override
        public boolean willChangeBounds() {
            return true;
        }

    }


    private void runTests() {

        DataBase.getTableTest1DAO().add(new TableTest1(10));
        DataBase.getTableTest1DAO().add(new TableTest1(12));

        List<TableTest1> lTableTest1s = DataBase.getTableTest1DAO().fetchAll();
        for (TableTest1 lTableTest1 : lTableTest1s) {
            LogManager.info(TAG, "table test value : " + lTableTest1.getValue());
        }

        DataBase.getTableTest1DAO().delete(lTableTest1s.get(1).getDataBaseId());
        LogManager.info(TAG, "deleted");

        lTableTest1s = DataBase.getTableTest1DAO().fetchAll();
        for (TableTest1 lTableTest1 : lTableTest1s) {
            LogManager.info(TAG, "table test value : " + lTableTest1.getValue());
        }

        DataBase.getFTPHostDAO().add(new FTPHost());
        DataBase.getFTPHostDAO().add(new FTPHost());

        List<FTPHost> lFTPHosts = DataBase.getFTPHostDAO().fetchAll();
        for (FTPHost lFTPHost : lFTPHosts) {
            LogManager.info(TAG, "table test value : " + lFTPHost);
        }

        DataBase.getFTPHostDAO().delete(lFTPHosts.get(1).getDataBaseId());
        LogManager.info(TAG, "deleted");

        lFTPHosts = DataBase.getFTPHostDAO().fetchAll();
        for (FTPHost lFTPHost : lFTPHosts) {
            LogManager.info(TAG, "table test value : " + lFTPHost);
        }
    }
}

package com.example.ftpnext;

import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;

import com.example.ftpnext.adapters.MainRecyclerViewAdapter;
import com.example.ftpnext.core.AppCore;
import com.example.ftpnext.core.LogManager;
import com.example.ftpnext.database.DataBase;
import com.example.ftpnext.database.DataBaseTests;
import com.example.ftpnext.database.FTPServerTable.FTPServer;
import com.example.ftpnext.database.FTPServerTable.FTPServerDAO;
import com.example.ftpnext.database.TableTest1.TableTest1;

import org.apache.commons.net.ftp.FTPClient;

import java.net.InetAddress;
import java.util.List;

/*
TODO : Resume when screen change orientation
TODO : Add a security network connection / network authorisation

Ideas :
    - Remember the last folder used for create a server
    - Queues of file to download even if the connection fail
    - Wifi download/upload only
    - Notification view of transfer
    - Check if the download can continue after a stop
    - Connection with ssh
    - Multiple theme
    - Add a shortcut of a server on the desktop
    - A FTPTransferActivity for show / manage all the transfers
 */

public class MainActivity extends AppCompatActivity {

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

        setContentView(R.layout.activity_main);

        initializeGUI();
        initializeAdapter();
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
        super.onBackPressed();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_parameters) {

            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void initializeGUI() {

        Toolbar lToolBar = findViewById(R.id.toolbar);
        setSupportActionBar(lToolBar);

        mFloatingActionButton = findViewById(R.id.floating_action_button);
        mFloatingActionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startConfigureFTPServerActivity(ConfigureFTPServerActivity.NO_DATABASE_ID);
            }
        });


        mRootView = findViewById(R.id.main_root_linear_layout);
    }

    private void initializeAdapter() {
        RecyclerView lRecyclerView = findViewById(R.id.main_recycler_view);
        lRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new MainRecyclerViewAdapter(lRecyclerView, this);
        lRecyclerView.setAdapter(mAdapter);

        DividerItemDecoration mDividerItemDecoration = new DividerItemDecoration(this, DividerItemDecoration.VERTICAL);
        lRecyclerView.addItemDecoration(mDividerItemDecoration);

        mAdapter.setOnLongClickListener(new MainRecyclerViewAdapter.OnLongClickListener() {
            @Override
            public void onLongClick(final int iServerID) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                // TODO add string xml
                builder.setTitle("title")
                        .setItems(R.array.string_array_name, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface iDialog, int iWhich) {
                                if (iWhich == 0) {
                                    new AlertDialog.Builder(MainActivity.this)
                                            .setTitle("Deleting :")
                                            .setMessage("Are you sure to delete this server ?")
                                            .setPositiveButton("yes", new DialogInterface.OnClickListener() {
                                                public void onClick(DialogInterface dialog, int which) {
                                                    mFTPServerDAO.delete(iServerID);
                                                    mAdapter.removeItem(iServerID);
                                                }
                                            })
                                            .setNegativeButton("no", null)
                                            .show();
                                } else if (iWhich == 1)
                                    startConfigureFTPServerActivity(iServerID);
                            }
                        });
                builder.create();
                builder.show();
            }
        });

        mAdapter.setOnClickListener(new MainRecyclerViewAdapter.OnClickListener() {
            @Override
            public void onClick(int iServerID) {
                startFTPNavigationActivity(iServerID);
            }
        });
    }

    private void initialize() {
        mAppCore = new AppCore(this);
        mAppCore.startApplication();
        mFTPServerDAO = DataBase.getFTPServerDAO();

        List<FTPServer> lFTPServers = mFTPServerDAO.fetchAll();
        for (FTPServer lFTPServer : lFTPServers) {
            mAdapter.insertItem(lFTPServer);
        }
    }

    public void onActivityResult(int iRequestCode, int iResultCode, Intent iResultData) {
        if (iRequestCode == ConfigureFTPServerActivity.ACTIVITY_REQUEST_CODE) {
            if (iResultData != null) {
                int lId = iResultData.getIntExtra(ConfigureFTPServerActivity.KEY_DATABASE_ID, -1);

                if (lId != -1) {
                    if (iResultCode == ConfigureFTPServerActivity.ACTIVITY_RESULT_ADD_SUCCESS)
                        mAdapter.insertItem(mFTPServerDAO.fetchById(lId));
                    else if (iResultCode == ConfigureFTPServerActivity.ACTIVITY_RESULT_UPDATE_SUCCESS)
                        LogManager.error("update success");
                    mAdapter.updateItem(mFTPServerDAO.fetchById(lId));
                }
            }
        }
    }

    private void startFTPNavigationActivity(final int iServerID) {
        final FTPServer lFTPServer = mFTPServerDAO.fetchById(iServerID);

        if (lFTPServer == null) {
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Error")
                    .setMessage("FTP Server cannot be found")
                    .setPositiveButton("Ok", null)
                    .show();
            return;
        }

        new AlertDialog.Builder(MainActivity.this)
                .setTitle("Connection...")
                .setView(R.layout.loading_icon)
                .setNegativeButton("Cancel", null)
                .show();

        Thread thread = new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    try {
                        LogManager.error(TAG, lFTPServer.toString());

                        FTPClient ftpClient = new FTPClient();
                        ftpClient.connect(InetAddress.getByName(lFTPServer.getServer()));
                        ftpClient.login(lFTPServer.getUser(), lFTPServer.getPass());
                        System.out.println("status :: " + ftpClient.getStatus());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        thread.start();

        Intent lIntent = new Intent(MainActivity.this, FTPNavigationActivity.class);

        startActivityForResult(lIntent, FTPNavigationActivity.ACTIVITY_REQUEST_CODE);
    }

    private void startConfigureFTPServerActivity(int iFTPServerId) {
        Intent lIntent = new Intent(MainActivity.this, ConfigureFTPServerActivity.class);

        lIntent.putExtra(ConfigureFTPServerActivity.KEY_DATABASE_ID, iFTPServerId);
        startActivityForResult(lIntent,
                ConfigureFTPServerActivity.ACTIVITY_REQUEST_CODE,
                ActivityOptions.makeSceneTransitionAnimation(MainActivity.this).toBundle());
    }

    private void runTests() {
        // Note : RunTests will automatically increment the database ID
        DataBaseTests.runTests(new TableTest1(), DataBase.getTableTest1DAO());
//        DataBaseTests.runTests(new FTPServer(), DataBase.getFTPServerDAO());

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

package com.vpulse.ftpnext;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.vpulse.ftpnext.adapters.MainRecyclerViewAdapter;
import com.vpulse.ftpnext.commons.Utils;
import com.vpulse.ftpnext.core.AppCore;
import com.vpulse.ftpnext.core.LogManager;
import com.vpulse.ftpnext.database.DataBase;
import com.vpulse.ftpnext.database.FTPServerTable.FTPServer;
import com.vpulse.ftpnext.database.FTPServerTable.FTPServerDAO;
import com.vpulse.ftpnext.ftpnavigation.FTPNavigationActivity;
import com.vpulse.ftpnext.ftpservices.AFTPConnection;
import com.vpulse.ftpnext.ftpservices.AFTPConnection.ErrorCodeDescription;
import com.vpulse.ftpnext.ftpservices.FTPServices;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/*
TODO : Resume when screen change orientation
TODO : Add a security network connection / network authorisation
TODO : Securities authorization popups
Ideas :
    - Research mode in FTPNavigationActivity
    - Ask to respawn to the last folder visited if diff than root
    - Display to type of file/dir rights xxx-xxx-xxx or read
    - Remember the last folder used for create a server
    - Queues of file to download even if the connection fail
    - Wifi download/upload only
    - Notification view of transfer
    - Check if the download can continue after a stop
    - Connection with ssh
    - Multiple theme
    - Add a shortcut of a server on the desktop
    - A FTPTransferActivity for show / manage all the transfers
    - Ask to show adds
    - Stop phone(?) after download
    - Airplane mode after download
    - Song after download...
    - Duplicate server
    - Sort per type, name, size, and date

    - Log historic connection for the user

 */

/*
Parameters ideas :
    - Download without wifi
 */

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MAIN ACTIVITY";

    private static final int ACTIVITY_REQUEST_CODE_READ_EXTERNAL_STORAGE = 1;
    private static final int ACTIVITY_REQUEST_CODE_CONFIGURE_SERVER = 2;
    private static final int ACTIVITY_REQUEST_CODE_NAVIGATION = 3;
    private static final int ACTIVITY_REQUEST_CODE_SETTINGS = 4;
    private static final int ACTIVITY_REQUEST_CODE_INTERNET = 5;


    private MainRecyclerViewAdapter mAdapter;
    private boolean mIsRunning;
    private boolean mIsBusy;

    private Dialog mDialog;

    private FTPServerDAO mFTPServerDAO;

    @Override
    protected void onCreate(Bundle iSavedInstanceState) {
        super.onCreate(iSavedInstanceState);

        setContentView(R.layout.activity_main);

        initializeGUI();
        initializeAdapter();
        initialize();
        initializePermissions();

        LayoutInflater inflater = this.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.loading_icon, null);

        runTests();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // TODO save the form for change orientation of phone
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mDialog != null && mDialog.isShowing())
            mDialog.dismiss();
        mIsRunning = false;
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu iMenu) {
        getMenuInflater().inflate(R.menu.main, iMenu);
        return true;
    }

    @Override public boolean onOptionsItemSelected(MenuItem iMenuItem) {
        int id = iMenuItem.getItemId();
        if (id == R.id.action_parameters) {
            startSettingsActivity();
            return true;
        }

        return super.onOptionsItemSelected(iMenuItem);
    }

    private void initializeGUI() {
        Toolbar lToolBar = findViewById(R.id.toolbar);
        setSupportActionBar(lToolBar);

        FloatingActionButton lFloatingActionButton = findViewById(R.id.navigation_floating_action_button);
        lFloatingActionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startFTPConfigureServerActivity(FTPConfigureServerActivity.NO_DATABASE_ID);
            }
        });
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
                // TODO strings
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
                                    startFTPConfigureServerActivity(iServerID);
                            }
                        });
                builder.create();
                builder.show();
            }
        });

        mAdapter.setOnClickListener(new MainRecyclerViewAdapter.OnClickListener() {
            @Override
            public void onClick(int iServerID) {
                onServerClicked(iServerID);
            }
        });
    }

    private void initialize() {
        AppCore.getInstance().startApplication(this);

        mFTPServerDAO = DataBase.getFTPServerDAO();
        mIsRunning = true;

        List<FTPServer> lFTPServers = mFTPServerDAO.fetchAll();
        for (FTPServer lFTPServer : lFTPServers) {
            mAdapter.insertItem(lFTPServer);
        }
    }

    private void initializePermissions() {
//        if (AppCore.isTheFirstRun()) {
        String[] lPermissionsReadWrite = new String[]{
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE};

        ActivityCompat.requestPermissions(this,
                lPermissionsReadWrite,
                ACTIVITY_REQUEST_CODE_READ_EXTERNAL_STORAGE);

    }

    @Override
    public void onRequestPermissionsResult(int iRequestCode,
                                           @NotNull String[] iPermissions, int[] iGrantResults) {
        switch (iRequestCode) {
            case ACTIVITY_REQUEST_CODE_READ_EXTERNAL_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                if (iGrantResults.length > 0 && iGrantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    LogManager.info(TAG, "PERMISSION OK : READ_EXTERNAL_STORAGE");
                } else {
                    LogManager.info(TAG, "PERMISSION DENY : READ_EXTERNAL_STORAGE");
                }

                // TODO : Make an automatic permission algorithm
                String[] lPermissionNetwork = new String[]{
                        Manifest.permission.INTERNET
                };
                ActivityCompat.requestPermissions(this,
                        lPermissionNetwork,
                        ACTIVITY_REQUEST_CODE_INTERNET);

            }
        }
    }

    public void onActivityResult(int iRequestCode, int iResultCode, Intent iResultData) {
        if (iRequestCode == ACTIVITY_REQUEST_CODE_CONFIGURE_SERVER) {
            overridePendingTransition(R.anim.no_animation, R.anim.activity_fade_out_centered);

            if (iResultData != null) {
                int lId = iResultData.getIntExtra(FTPConfigureServerActivity.KEY_DATABASE_ID, -1);

                if (lId != -1) {
                    if (iResultCode == FTPConfigureServerActivity.ACTIVITY_RESULT_ADD_SUCCESS)
                        mAdapter.insertItem(mFTPServerDAO.fetchById(lId));
                    else if (iResultCode == FTPConfigureServerActivity.ACTIVITY_RESULT_UPDATE_SUCCESS)
                        mAdapter.updateItem(mFTPServerDAO.fetchById(lId));
                }
            }
        }
    }

    private void onServerClicked(final int iServerID) {
        if (mIsBusy) {
            LogManager.error(TAG, "Already trying a connection");
            return;
        }

//        if (ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET) !=
//                PackageManager.PERMISSION_GRANTED) {
//
//            ActivityCompat.requestPermissions(this,
//                    new String[] {Manifest.permission.INTERNET},
//                    ACTIVITY_REQUEST_CODE_INTERNET);
//            return;
//        }


        final FTPServer lFTPServer = mFTPServerDAO.fetchById(iServerID);
        final ProgressDialog lLoadingAlertDialog;
        mIsBusy = true;

        if (lFTPServer == null) {
            Utils.createErrorAlertDialog(this, "FTP Server cannot be found").show();
            return;
        }

        lLoadingAlertDialog = Utils.initProgressDialog(this, null);
        lLoadingAlertDialog.setContentView(R.layout.loading_icon);
        lLoadingAlertDialog.setTitle("Connection..."); // TODO : strings
        lLoadingAlertDialog.create();
        lLoadingAlertDialog.show();

        final FTPServices lNewFTPServices = new FTPServices(lFTPServer);

        lNewFTPServices.connect(new AFTPConnection.OnConnectionResult() {
            @Override
            public void onSuccess() {
                Utils.cancelAlertDialogOnUIThread(MainActivity.this, lLoadingAlertDialog);

                startFTPNavigationActivity(iServerID);
                mIsBusy = false;
            }

            @Override
            public void onFail(final AFTPConnection.ErrorCodeDescription iErrorEnum, final int iErrorCode) {
                if (iErrorEnum == ErrorCodeDescription.ERROR_CONNECTION_INTERRUPTED)
                    return;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (mIsRunning) {
                            lLoadingAlertDialog.cancel();

                            if (lNewFTPServices.isConnecting())
                                lNewFTPServices.abortConnection();
                            lNewFTPServices.destroyConnection();

                            mDialog = new AlertDialog.Builder(MainActivity.this)
                                    .setTitle("Error") // TODO string
                                    .setMessage("Connection has failed..." +
                                            "\nMessage : " + iErrorEnum.name() +
                                            "\nCode : " + iErrorCode)
                                    .setPositiveButton("Ok", null)
                                    .setOnDismissListener(new DialogInterface.OnDismissListener() {
                                        @Override
                                        public void onDismiss(DialogInterface dialog) {
                                            mIsBusy = false;
                                        }
                                    })
                                    .create();
                            mDialog.show();
                        }
                        lNewFTPServices.destroyConnection();
                    }
                });
            }
        });
    }

    private void startFTPNavigationActivity(int iServerID) {
        Intent lIntent = new Intent(MainActivity.this, FTPNavigationActivity.class);
        lIntent.putExtra(FTPNavigationActivity.KEY_DATABASE_ID, iServerID);
        lIntent.putExtra(FTPNavigationActivity.KEY_DIRECTORY_PATH, FTPNavigationActivity.ROOT_DIRECTORY);
        startActivityForResult(lIntent, ACTIVITY_REQUEST_CODE_NAVIGATION);
    }

    private void startFTPConfigureServerActivity(int iFTPServerId) {
        Intent lIntent = new Intent(MainActivity.this, FTPConfigureServerActivity.class);

        lIntent.putExtra(FTPConfigureServerActivity.KEY_DATABASE_ID, iFTPServerId);
        startActivityForResult(lIntent, ACTIVITY_REQUEST_CODE_CONFIGURE_SERVER);
        overridePendingTransition(R.anim.activity_fade_in_centered, R.anim.no_animation);
    }

    private void startSettingsActivity() {
        Intent lIntent = new Intent(MainActivity.this, SettingsActivity.class);

        startActivityForResult(lIntent, ACTIVITY_REQUEST_CODE_SETTINGS);
    }

    private void runTests() {


        // Note : RunTests will automatically increment the database ID
//        DataBaseTests.runTests(new TableTest1(), DataBase.getTableTest1DAO());
//        DataBaseTests.runTests(new FTPServer(), DataBase.getFTPServerDAO());
//        DataBaseTests.runTests(new PendingFile(), DataBase.getPendingFileDAO());

//        for (int i = 0; i < 20; i++) {
//            mAdapter.mItemList.add("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" + i);
//        }

//        TextInputLayout lTextInputLayout = (TextInputLayout) findViewById(R.id.form_server_host);
//        lTextInputLayout.setError("error");
//        lTextInputLayout.setErrorTextColor(ColorStateList.valueOf(0xFFDE4255));
    }
}

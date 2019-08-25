package com.example.ftpnext;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;

import com.example.ftpnext.adapters.NavigationRecyclerViewAdapter;
import com.example.ftpnext.commons.Utils;
import com.example.ftpnext.core.LogManager;
import com.example.ftpnext.database.DataBase;
import com.example.ftpnext.database.FTPServerTable.FTPServer;
import com.example.ftpnext.database.FTPServerTable.FTPServerDAO;

import org.apache.commons.net.ftp.FTPFile;

public class FTPNavigationActivity extends AppCompatActivity {
    public static final int ACTIVITY_REQUEST_CODE = 1;
    public static final int NO_DATABASE_ID = -1;

    public static final String KEY_DATABASE_ID = "KEY_DATABASE_ID";
    public static final String KEY_DIRECTORY_PATH = "KEY_DIRECTORY_PATH";
    public static final String KEY_IS_LARGE_DIRECTORY = "KEY_IS_LARGE_DIRECTORY";
    public static final String KEY_IS_ROOT_CONNECTION = "KEY_IS_ROOT_CONNECTION";

    private static final String TAG = "FTP NAVIGATION ACTIVITY";
    private static final int LARGE_DIRECTORY_SIZE = 30000;
    private static final int BAD_CONNECTION_TIME = 600;
    private static final String ROOT_DIRECTORY = "/";

    private boolean mIsRootConnection;
    private boolean mIsRunning;
    private FTPServer mFTPServer;
    private FTPConnection mFTPConnection;
    private FTPServerDAO mFTPServerDAO;
    private NavigationRecyclerViewAdapter mAdapter;
    private String mDirectoryPath;
    private boolean mDirectoryFetchFinished;
    private ProgressDialog mBadConnectionDialog;
    private ProgressDialog mLargeDirDialog;
    //    private AlertDialog mLargeDirDialog;
    private AlertDialog mErrorAlertDialog;

    @Override
    protected void onCreate(Bundle iSavedInstanceState) {
        super.onCreate(iSavedInstanceState);
        setContentView(R.layout.activity_ftp_navigation);

        mIsRunning = true;
        initializeGUI();
        initializeAdapter();
        initialize();

    }

    @Override
    protected void onResume() {
        super.onResume();
        LogManager.info(TAG, "On resume");
    }

    @Override
    protected void onDestroy() {
        mIsRunning = false;

        if (mErrorAlertDialog != null)
            mErrorAlertDialog.dismiss();
        if (mBadConnectionDialog != null)
            mBadConnectionDialog.dismiss();
        if (mLargeDirDialog != null)
            mLargeDirDialog.dismiss();

        if (mIsRootConnection) {
            mFTPConnection.disconnect();
        } else if (mFTPConnection.isFetchingFolders())
            mFTPConnection.abortFetchDirectoryContent();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
//        onDestroy();
    }

    private void initializeGUI() {
        Toolbar lToolBar = findViewById(R.id.toolbar);
        setSupportActionBar(lToolBar);

        // TODO add a floating button
    }

    private void initializeAdapter() {
        RecyclerView lRecyclerView = findViewById(R.id.navigation_recycler_view);
        lRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new NavigationRecyclerViewAdapter(lRecyclerView, this);
        lRecyclerView.setAdapter(mAdapter);

        DividerItemDecoration mDividerItemDecoration = new DividerItemDecoration(this, DividerItemDecoration.VERTICAL);
        lRecyclerView.addItemDecoration(mDividerItemDecoration);

        mAdapter.setOnClickListener(new NavigationRecyclerViewAdapter.OnClickListener() {
            @Override
            public void onClick(FTPFile iFTPFile) {
                if (iFTPFile.isDirectory()) {
                    if (iFTPFile.hasPermission(FTPFile.USER_ACCESS, FTPFile.READ_PERMISSION)
                            || iFTPFile.hasPermission(FTPFile.USER_ACCESS, FTPFile.WRITE_PERMISSION))
                        startFTPNavigationActivity(iFTPFile);
                    else
                        Utils.createErrorAlertDialog(FTPNavigationActivity.this, "You don't have enough permission");
                }
            }
        });
    }

    @SuppressLint("StaticFieldLeak")
    private void initialize() {
        mFTPServerDAO = DataBase.getFTPServerDAO();

        Bundle lBundle = this.getIntent().getExtras();

        // Server ID
        int lServerId = lBundle.getInt(KEY_DATABASE_ID);
        if (lServerId != NO_DATABASE_ID) {
            mFTPServer = mFTPServerDAO.fetchById(lServerId);
        }

        //Is root directory
        mIsRootConnection = lBundle.getBoolean(KEY_IS_ROOT_CONNECTION, false);

        // FTPServer fetch
        mFTPServer = mFTPServerDAO.fetchById(lServerId);
        if (mFTPServer == null) {
            Utils.createErrorAlertDialog(this, "Navigation page has failed...").show();
            return;
        }

        // Large directory loading
        if (lBundle.getBoolean(KEY_IS_LARGE_DIRECTORY)) {
            mLargeDirDialog = Utils.initProgressDialog(this, new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    dialog.dismiss();
                    onBackPressed();
                }
            });
            mLargeDirDialog.setTitle("Large directory"); // TODO : strings
            mLargeDirDialog.create();
            mLargeDirDialog.show();
        }

        //Bad Connection
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!mDirectoryFetchFinished && mLargeDirDialog == null) { // in case if dialog has been canceled
                    mBadConnectionDialog = Utils.initProgressDialog(FTPNavigationActivity.this, new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            dialog.dismiss();
                            onBackPressed();
                        }
                    });
                    mBadConnectionDialog.setTitle("Loading..."); //TODO : strings
                    mBadConnectionDialog.create();
                    mBadConnectionDialog.show();
                }
            }
        }, BAD_CONNECTION_TIME);

        // Directory path
        mDirectoryPath = lBundle.getString(KEY_DIRECTORY_PATH, ROOT_DIRECTORY);

        // FTP Connection
        mFTPConnection = FTPConnection.getFTPConnection(lServerId);

        if (mFTPConnection.isFetchingFolders()) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    while (mFTPConnection.isFetchingFolders()) {
                        try {
                            LogManager.info(TAG, "Waiting fetch stopping");
                            Thread.sleep(150);
                        } catch (InterruptedException iE) {
                            iE.printStackTrace();
                        }
                    }
                    initializeConnection();
                }
            }).start();
        } else
            initializeConnection();
    }

    private void initializeConnection() {
        mFTPConnection.fetchDirectoryContent(mDirectoryPath, new FTPConnection.OnFetchDirectoryResult() {
            @Override
            public void onSuccess(final FTPFile[] iFTPFiles) {
                mDirectoryFetchFinished = true;
                if (mBadConnectionDialog != null)
                    mBadConnectionDialog.dismiss();
                if (mLargeDirDialog != null)
                    mLargeDirDialog.dismiss();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mAdapter.setData(iFTPFiles);
                    }
                });
            }

            @Override
            public void onFail(final int iErrorCode) {
                mDirectoryFetchFinished = true;
                if (mBadConnectionDialog != null)
                    mBadConnectionDialog.dismiss();
                if (mLargeDirDialog != null)
                    mLargeDirDialog.dismiss();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (mIsRunning) {
                            mErrorAlertDialog = new AlertDialog.Builder(FTPNavigationActivity.this)
                                    .setTitle("Error") // TODO string
                                    .setMessage("Connection has failed...\nCode : " + FTPConnection.getErrorMessage(iErrorCode))
                                    .setPositiveButton("Ok", null)
                                    .create();
                            mErrorAlertDialog.show();
                        }
                    }
                });
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void startFTPNavigationActivity(FTPFile iFTPFile) {
        Intent lIntent = new Intent(FTPNavigationActivity.this, FTPNavigationActivity.class);
        lIntent.putExtra(FTPNavigationActivity.KEY_DATABASE_ID, mFTPServer.getDataBaseId());
        lIntent.putExtra(FTPNavigationActivity.KEY_DIRECTORY_PATH, mDirectoryPath + "/" + iFTPFile.getName());
        if (iFTPFile.getSize() > LARGE_DIRECTORY_SIZE)
            lIntent.putExtra(FTPNavigationActivity.KEY_IS_LARGE_DIRECTORY, true);
        startActivityForResult(lIntent, FTPNavigationActivity.ACTIVITY_REQUEST_CODE);
    }
}

package com.example.ftpnext;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;

import com.example.ftpnext.adapters.NavigationRecyclerViewAdapter;
import com.example.ftpnext.commons.Utils;
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

    private static final String TAG = "FTP NAVIGATION ACTIVITY";
    private static final int LARGE_DIRECTORY_SIZE = 30000;
    private static final String ROOT_DIRECTORY = "/";

    private FTPServer mFTPServer;
    private FTPConnection mFTPConnection;
    private FTPServerDAO mFTPServerDAO;
    private NavigationRecyclerViewAdapter mAdapter;
    private String mDirectoryPath;
    private AlertDialog mLargeDirAlertDialog;

    @Override
    protected void onCreate(Bundle iSavedInstanceState) {
        super.onCreate(iSavedInstanceState);
        setContentView(R.layout.activity_ftp_navigation);

        initializeGUI();
        initializeAdapter();
        initialize();
    }

    private void initializeGUI() {
        Toolbar lToolBar = findViewById(R.id.toolbar);
        setSupportActionBar(lToolBar);

        // TODO add a loading popup
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


    private void initialize() {
        mFTPServerDAO = DataBase.getFTPServerDAO();

        Bundle lBundle = this.getIntent().getExtras();

        // Server ID
        int lServerId = lBundle.getInt(KEY_DATABASE_ID);
        if (lServerId != NO_DATABASE_ID) {
            mFTPServer = mFTPServerDAO.fetchById(lServerId);
        }

        // FTPServer fetch
        mFTPServer = mFTPServerDAO.fetchById(lServerId);
        if (mFTPServer == null) {
            Utils.createErrorAlertDialog(this, "Navigation page has failed...").show();
            return;
        }

        // Large directory loading
        if (lBundle.getBoolean(KEY_IS_LARGE_DIRECTORY)) {
            mLargeDirAlertDialog = new android.app.AlertDialog.Builder(FTPNavigationActivity.this)
                    .setTitle("Large directory...")
                    .setView(R.layout.loading_icon)
                    .setNegativeButton("Cancel", null)
                    .setOnCancelListener(new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            // TODO Back
                        }
                    })
                    .create();
            mLargeDirAlertDialog.show();
        }

        // Directory path
        mDirectoryPath = lBundle.getString(KEY_DIRECTORY_PATH, ROOT_DIRECTORY);

        // FTP Connection
        mFTPConnection = FTPConnection.getFTPConnection(lServerId);
        mFTPConnection.fetchDirectoryContent(mDirectoryPath, new FTPConnection.OnFetchDirectoryResult() {
            @Override
            public void onSuccess(final FTPFile[] iFTPFiles) {
                if (mLargeDirAlertDialog != null)
                    mLargeDirAlertDialog.dismiss();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mAdapter.setData(iFTPFiles);
                    }
                });
            }

            @Override
            public void onFail(String iErrorMessage) {
                Utils.createErrorAlertDialog(FTPNavigationActivity.this, "Navigation page has failed...").show();
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

    //TODO : on last destroy, disconnect
    //TODO : on disconnection, destroy all
}

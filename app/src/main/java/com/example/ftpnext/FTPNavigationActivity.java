package com.example.ftpnext;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.view.ViewCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.BounceInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

import com.example.ftpnext.adapters.NavigationRecyclerViewAdapter;
import com.example.ftpnext.commons.Utils;
import com.example.ftpnext.core.AppCore;
import com.example.ftpnext.core.LogManager;
import com.example.ftpnext.database.DataBase;
import com.example.ftpnext.database.FTPServerTable.FTPServer;
import com.example.ftpnext.database.FTPServerTable.FTPServerDAO;

import org.apache.commons.net.ftp.FTPFile;

import java.util.Arrays;

public class FTPNavigationActivity extends AppCompatActivity {
    public static final int ACTIVITY_REQUEST_CODE = 1;
    public static final int NO_DATABASE_ID = -1;
    public static final String ROOT_DIRECTORY = "/";


    public static final String KEY_DATABASE_ID = "KEY_DATABASE_ID";
    public static final String KEY_DIRECTORY_PATH = "KEY_DIRECTORY_PATH";

    private static final String TAG = "FTP NAVIGATION ACTIVITY";
    private static final int LARGE_DIRECTORY_SIZE = 30000;
    private static final int BAD_CONNECTION_TIME = 600;

    private boolean mIsRunning;

    private FTPServer mFTPServer;
    private FTPConnection mFTPConnection;
    private FTPServerDAO mFTPServerDAO;

    private NavigationRecyclerViewAdapter mCurrentAdapter;
    private FrameLayout mRecyclerSection;
    private String mDirectoryPath;
    private boolean mDirectoryFetchFinished;
    private boolean mIsLargeDirectory;

    private ProgressDialog mBadConnectionDialog;
    private ProgressDialog mLargeDirDialog;
    private ProgressDialog mReconnectDialog;
    private AlertDialog mErrorAlertDialog;

    private Bundle mBundle;

    private boolean mIsFABOpen;
    private FloatingActionButton mFAB;
    private FloatingActionButton mFAB1;
    private FloatingActionButton mFAB2;

    @Override
    protected void onCreate(Bundle iSavedInstanceState) {
        LogManager.info(TAG, "On create");
        super.onCreate(iSavedInstanceState);
        setContentView(R.layout.activity_ftp_navigation);

        mIsRunning = true;
        initializeGUI();
        initialize();
        runFetchProcedures(mDirectoryPath, mIsLargeDirectory, false);
    }

    @Override
    protected void onResume() {
        LogManager.info(TAG, "On resume");
        super.onResume();
        if (mFTPConnection == null) {
            initialize();
            runFetchProcedures(mDirectoryPath, false, true);
        }
    }

    @Override
    protected void onDestroy() {
        LogManager.info(TAG, "On destroy");
        mIsRunning = false;

        dismissAllDialogs();

        if (mFTPConnection != null)
            mFTPConnection.destroyConnection();
        else if (mFTPConnection != null && mFTPConnection.isFetchingFolders())
            mFTPConnection.abortFetchDirectoryContent();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (mIsFABOpen) {
            closeFABMenu();
            return;
        }

        if (mCurrentAdapter.getPreviousAdapter() != null) {
            destroyCurrentAdapter();
            return;
        }

        super.onBackPressed();
    }

    private void initializeGUI() {
        Toolbar lToolBar = findViewById(R.id.toolbar);
        setSupportActionBar(lToolBar);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        mFAB = findViewById(R.id.navigation_floating_action_button);
        mFAB1 = findViewById(R.id.fab1);
        mFAB2 = findViewById(R.id.fab2);
        mFAB.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!mIsFABOpen) {
                    openFABMenu();
                } else {
                    closeFABMenu();
                }
            }
        });

        mRecyclerSection = findViewById(R.id.navigation_recycler_section);
    }

    private void openFABMenu() {
        mIsFABOpen = true;
        ViewCompat.animate(mFAB)
                .rotation(45F)
                .withLayer()
                .setDuration(500L)
                .setInterpolator(new BounceInterpolator())
                .start();

        ((View) mFAB1).setVisibility(View.VISIBLE);
        ((View) mFAB2).setVisibility(View.VISIBLE);
        mFAB1.animate().translationY(-getResources().getDimension(R.dimen.sub_fab_floor_1)).
                setInterpolator(new DecelerateInterpolator(AppCore.FLOATING_ACTION_BUTTON_INTERPOLATOR));
        mFAB2.animate().translationY(-getResources().getDimension(R.dimen.sub_fab_floor_2)).
                setInterpolator(new DecelerateInterpolator(AppCore.FLOATING_ACTION_BUTTON_INTERPOLATOR));
    }

    private void closeFABMenu() {
        mIsFABOpen = false;
        ViewCompat.animate(mFAB)
                .rotation(0.0F)
                .withLayer()
                .setDuration(500L)
                .setInterpolator(new BounceInterpolator())
                .start();

        mFAB1.animate().translationY(0).withEndAction(new Runnable() {
            @Override
            public void run() {
                ((View) mFAB1).setVisibility(View.GONE);
            }
        });
        mFAB2.animate().translationY(0).withEndAction(new Runnable() {
            @Override
            public void run() {
                ((View) mFAB2).setVisibility(View.GONE);
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        finish();
        return super.onOptionsItemSelected(item);
    }

    private void destroyCurrentAdapter() {
        final NavigationRecyclerViewAdapter lDeprecatedAdapter = mCurrentAdapter;
        lDeprecatedAdapter.disappearOnRightAndDestroy(new Runnable() {
            @Override
            public void run() {
                lDeprecatedAdapter.getRecyclerView().setAdapter(null);
                mRecyclerSection.removeView(lDeprecatedAdapter.getRecyclerView());
            }
        });
        lDeprecatedAdapter.getPreviousAdapter().appearOnLeft();
        mCurrentAdapter = lDeprecatedAdapter.getPreviousAdapter();
        mCurrentAdapter.setNextAdapter(null);
    }

    private void inflateNewAdapter(FTPFile[] iFTPFiles, String iDirectoryPath, boolean iForceVerticalAppear) {
        RecyclerView lNewRecyclerView = (RecyclerView) View.inflate(this, R.layout.navigation_recycler_view, null);
        mRecyclerSection.addView(lNewRecyclerView);

        lNewRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        NavigationRecyclerViewAdapter lNewAdapter = new NavigationRecyclerViewAdapter(lNewRecyclerView, this, iDirectoryPath, false);
        if (mCurrentAdapter != null) {
            lNewAdapter.setPreviousAdapter(mCurrentAdapter);
            mCurrentAdapter.setNextAdapter(lNewAdapter);
            mCurrentAdapter.disappearOnLeft();
        }

        if (mCurrentAdapter != null && !iForceVerticalAppear)
            lNewAdapter.appearOnRight();
        else
            lNewAdapter.appearVertically();

        lNewRecyclerView.setAdapter(lNewAdapter);

        DividerItemDecoration mDividerItemDecoration = new DividerItemDecoration(this, DividerItemDecoration.VERTICAL);
        lNewRecyclerView.addItemDecoration(mDividerItemDecoration);

        lNewAdapter.setOnClickListener(new NavigationRecyclerViewAdapter.OnClickListener() {
            @Override
            public void onClick(FTPFile iFTPFile) {
                if (iFTPFile.isDirectory()) {
                    if (iFTPFile.hasPermission(FTPFile.USER_ACCESS, FTPFile.READ_PERMISSION)
                            || iFTPFile.hasPermission(FTPFile.USER_ACCESS, FTPFile.WRITE_PERMISSION)) {
                        mDirectoryPath = mDirectoryPath + "/" + iFTPFile.getName();
                        mIsLargeDirectory = iFTPFile.getSize() > LARGE_DIRECTORY_SIZE;
                        runFetchProcedures(mDirectoryPath, mIsLargeDirectory, false);
                    } else
                        Utils.createErrorAlertDialog(FTPNavigationActivity.this, "You don't have enough permission");
                }
            }
        });

        lNewAdapter.setData(iFTPFiles);
        mCurrentAdapter = lNewAdapter;
    }

    private void  initialize() {
        mFTPServerDAO = DataBase.getFTPServerDAO();

        mBundle = this.getIntent().getExtras();

        // Server ID
        int lServerId = mBundle.getInt(KEY_DATABASE_ID);
        if (lServerId != NO_DATABASE_ID) {
            mFTPServer = mFTPServerDAO.fetchById(lServerId);
        } else {
            LogManager.error(TAG, "Server id is not initialized");
        }

        // FTPServer fetch
        mFTPServer = mFTPServerDAO.fetchById(lServerId);
        if (mFTPServer == null) {
            Utils.createErrorAlertDialog(this, "Navigation page has failed...").show();
            return;
        }

        // Directory path
        mDirectoryPath = mBundle.getString(KEY_DIRECTORY_PATH, ROOT_DIRECTORY);

        // FTP Connection
        mFTPConnection = FTPConnection.getFTPConnection(lServerId);
    }

    private void runFetchProcedures(String iDirectoryPath, boolean iIsLargeDirectory, final boolean iRecovering) {
        dismissAllDialogs();
        mReconnectDialog = null;
        mLargeDirDialog = null;
        mBadConnectionDialog = null;
        mErrorAlertDialog = null;
        mDirectoryFetchFinished = false;

        if (mFTPConnection == null) {
            LogManager.error(TAG, "FTPConnection instance is null");
            LogManager.error(TAG, Arrays.toString(new Exception("FTPConnection instance is null").getStackTrace()));
            new AlertDialog.Builder(FTPNavigationActivity.this)
                    .setTitle("Error") // TODO string
                    .setMessage("Error unknown")
                    .setCancelable(false)
                    .setPositiveButton("Terminate", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            finishAllNavigationActivities();
                        }
                    })
                    .create()
                    .show();
            return;
        }

        // Bad connection, Large dir, Reconnect dialog
        initializeDialogs(iIsLargeDirectory);

        // Waiting fetch stop
        if (mFTPConnection.isFetchingFolders()) { // if another activity didn't stop its fetch yet
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
                    initializeFetchDirectory(iRecovering);
                }
            }).start();
        } else
            initializeFetchDirectory(iRecovering);
    }

    private void initializeDialogs(boolean iIsLargeDirectory) {
        // Reconnect dialog
        mFTPConnection.setOnConnectionLost(new FTPConnection.OnConnectionLost() {
            @Override
            public void onConnectionLost() {
                if (!mIsRunning)
                    return;
                mFTPConnection.abortFetchDirectoryContent();
                dismissAllDialogs();

                mReconnectDialog = Utils.initProgressDialog(FTPNavigationActivity.this,
                        new DialogInterface.OnCancelListener() {
                            @Override
                            public void onCancel(DialogInterface dialog) {
                                dialog.dismiss();
                                mFTPConnection.abortConnection();
                                finishAllNavigationActivities();
                            }
                        });
                mReconnectDialog.setTitle("Reconnection..."); // TODO : strings
                mReconnectDialog.create();
                mReconnectDialog.show();
                mFTPConnection.reconnect(new FTPConnection.OnConnectionRecover() {
                    @Override
                    public void onConnectionRecover() {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                runFetchProcedures(mDirectoryPath, mIsLargeDirectory, true);
                            }
                        });
                    }

                    @Override
                    public void onConnectionDenied(final FTPConnection.CONNECTION_STATUS iErrorCode) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                new AlertDialog.Builder(FTPNavigationActivity.this)
                                        .setTitle("Reconnection denied") // TODO string
                                        .setMessage("Reconnection has failed...\nCode : " + iErrorCode.name())
                                        .setCancelable(false)
                                        .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                dialog.dismiss();
                                                finishAllNavigationActivities();
                                            }
                                        })
                                        .create()
                                        .show();
                            }
                        });
                    }
                });
            }
        });

        // Large directory loading
        if (iIsLargeDirectory) {
            mLargeDirDialog = Utils.initProgressDialog(this, new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    dialog.dismiss();
                    finish();
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
                LogManager.error(TAG, "check for loading dialog");
                if (!mDirectoryFetchFinished && mLargeDirDialog == null) { // in case if dialog has been canceled
                    mBadConnectionDialog = Utils.initProgressDialog(FTPNavigationActivity.this, new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            dialog.dismiss();
                            finish();
                        }
                    });
                    mBadConnectionDialog.setTitle("Loading..."); //TODO : strings
                    mBadConnectionDialog.create();
                    LogManager.error(TAG, "showing for loading dialog");
                    if (!mDirectoryFetchFinished)
                        mBadConnectionDialog.show();
                    else
                        mBadConnectionDialog = null;
                }
            }
        }, BAD_CONNECTION_TIME);
    }

    private void initializeFetchDirectory(final boolean iRecovering) {
        mFTPConnection.fetchDirectoryContent(mDirectoryPath, new FTPConnection.OnFetchDirectoryResult() {
            @Override
            public void onSuccess(final FTPFile[] iFTPFiles) {
                mDirectoryFetchFinished = true;
                LogManager.error(TAG, "mDirectoryFetchFinished == true");
                if (mBadConnectionDialog != null && mBadConnectionDialog.isShowing())
                    mBadConnectionDialog.dismiss();
                if (mLargeDirDialog != null)
                    mLargeDirDialog.dismiss();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        inflateNewAdapter(iFTPFiles, mDirectoryPath, iRecovering);
                    }
                });
            }

            @Override
            public void onFail(final FTPConnection.CONNECTION_STATUS iErrorCode) {
                mDirectoryFetchFinished = true;
                if (mBadConnectionDialog != null)
                    mBadConnectionDialog.dismiss();
                if (mLargeDirDialog != null)
                    mLargeDirDialog.dismiss();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (mIsRunning && (mReconnectDialog == null || !mReconnectDialog.isShowing())) {
                            mErrorAlertDialog = new AlertDialog.Builder(FTPNavigationActivity.this)
                                    .setTitle("Error") // TODO string
                                    .setMessage("Connection has failed...\nCode : " + iErrorCode.name())
                                    .setCancelable(false)
                                    .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface iDialog, int iWhich) {
                                            iDialog.dismiss();
                                            finish();
                                        }
                                    })
                                    .create();
                            mErrorAlertDialog.show();

                        }
                    }
                });
            }
        });
    }

    private void dismissAllDialogs() {
        if (mReconnectDialog != null)
            mReconnectDialog.dismiss();
        if (mLargeDirDialog != null)
            mLargeDirDialog.dismiss();
        if (mBadConnectionDialog != null)
            mBadConnectionDialog.dismiss();
        if (mErrorAlertDialog != null)
            mErrorAlertDialog.dismiss();
    }

    private void finishAllNavigationActivities() {
        mIsRunning = false;
        dismissAllDialogs();
        if (mFTPConnection != null)
            mFTPConnection.destroyConnection();

        Intent lIntent = new Intent(this, MainActivity.class);
        lIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(lIntent);
    }

//    @Override
//    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
//        super.onActivityResult(requestCode, resultCode, data);
//    }
//

//    private void startFTPNavigationActivity(FTPFile iFTPFile) {
//        Intent lIntent = new Intent(FTPNavigationActivity.this, FTPNavigationActivity.class);
//        lIntent.putExtra(FTPNavigationActivity.KEY_DATABASE_ID, mFTPServer.getDataBaseId());
//        lIntent.putExtra(FTPNavigationActivity.KEY_DIRECTORY_PATH, mDirectoryPath + "/" + iFTPFile.getName());
//        if (iFTPFile.getSize() > LARGE_DIRECTORY_SIZE)
//            lIntent.putExtra(FTPNavigationActivity.KEY_IS_LARGE_DIRECTORY, true);
//        startActivityForResult(lIntent, FTPNavigationActivity.ACTIVITY_REQUEST_CODE);
//    }

}
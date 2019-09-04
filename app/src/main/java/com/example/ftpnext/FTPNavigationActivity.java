package com.example.ftpnext;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.TextInputLayout;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.BounceInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.AutoCompleteTextView;
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

    private ProgressDialog mLoadingDialog;
    private ProgressDialog mLargeDirDialog;
    private ProgressDialog mReconnectDialog;
    private AlertDialog mErrorAlertDialog;
    private AlertDialog mCreateFolderDialog;

    private Bundle mBundle;

    private boolean mIsFABOpen;
    private FloatingActionButton mMainFAB;
    private FloatingActionButton mCreateFolderFAB;
    private FloatingActionButton mUploadFileFAB;

    @Override
    protected void onCreate(Bundle iSavedInstanceState) {
        LogManager.info(TAG, "On create");
        super.onCreate(iSavedInstanceState);
        setContentView(R.layout.activity_ftp_navigation);

        mIsRunning = true;
        initializeGUI();
        initialize();
        if (mFTPConnection == null)
            buildFTPConnection(true, true);
        else
            runFetchProcedures(mDirectoryPath, mIsLargeDirectory, false);
    }

    @Override
    protected void onResume() {
        LogManager.info(TAG, "On resume");
        super.onResume();
        if (mFTPConnection == null) {
            initialize();
            if (mFTPConnection == null)
                buildFTPConnection(true, true);
            else
                runFetchProcedures(mDirectoryPath, mIsLargeDirectory, true);
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

        if (mCurrentAdapter.isInSelectionMode()) {
            mCurrentAdapter.setSelectionMode(false);
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

        mMainFAB = findViewById(R.id.navigation_floating_action_button);
        mCreateFolderFAB = findViewById(R.id.navigation_fab_create_folder);
        mUploadFileFAB = findViewById(R.id.navigation_fab_upload_file);
        mMainFAB.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!mIsFABOpen) {
                    openFABMenu();
                } else {
                    closeFABMenu();
                }
            }
        });

        mCreateFolderFAB.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                createDialogFolderClicked();
            }
        });
        mUploadFileFAB.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onUploadFileClicked();
            }
        });

        mRecyclerSection = findViewById(R.id.navigation_recycler_section);
    }

    private void openFABMenu() {
        if (!mIsFABOpen) {
            mIsFABOpen = true;
            ViewCompat.animate(mMainFAB)
                    .rotation(45F)
                    .withLayer()
                    .setDuration(500L)
                    .setInterpolator(new BounceInterpolator())
                    .start();

            ((View) mCreateFolderFAB).setVisibility(View.VISIBLE);
            ((View) mUploadFileFAB).setVisibility(View.VISIBLE);
            mCreateFolderFAB.animate().translationY(-getResources().getDimension(R.dimen.sub_fab_floor_1)).
                    setInterpolator(new DecelerateInterpolator(AppCore.FLOATING_ACTION_BUTTON_INTERPOLATOR));
            mUploadFileFAB.animate().translationY(-getResources().getDimension(R.dimen.sub_fab_floor_2)).
                    setInterpolator(new DecelerateInterpolator(AppCore.FLOATING_ACTION_BUTTON_INTERPOLATOR));
        }
    }

    private void closeFABMenu() {
        if (mIsFABOpen) {
            mIsFABOpen = false;
            ViewCompat.animate(mMainFAB)
                    .rotation(0.0F)
                    .withLayer()
                    .setDuration(500L)
                    .setInterpolator(new BounceInterpolator())
                    .start();

            mCreateFolderFAB.animate().translationY(0).withEndAction(new Runnable() {
                @Override
                public void run() {
                    ((View) mCreateFolderFAB).setVisibility(View.GONE);
                }
            });
            mUploadFileFAB.animate().translationY(0).withEndAction(new Runnable() {
                @Override
                public void run() {
                    ((View) mUploadFileFAB).setVisibility(View.GONE);
                }
            });
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        onBackPressed();
        return true;
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
        lDeprecatedAdapter.setNextAdapter(null);
        mDirectoryPath = mCurrentAdapter.getDirectoryPath();
    }

    private void inflateNewAdapter(FTPFile[] iFTPFiles, String iDirectoryPath, boolean iForceVerticalAppear) {
        SwipeRefreshLayout lSwipeRefreshLayout = (SwipeRefreshLayout) View.inflate(this, R.layout.navigation_recycler_layout, null);
        lSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                runFetchProcedures(mDirectoryPath, false, true);
            }
        });

        lSwipeRefreshLayout.setColorSchemeResources(
                R.color.colorPrimaryLight,
                R.color.colorSecondaryLight,
                R.color.colorPrimaryDark);

        mRecyclerSection.addView(lSwipeRefreshLayout);

        RecyclerView lNewRecyclerView = lSwipeRefreshLayout.findViewById(R.id.navigation_recycler_view);

        lNewRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        final NavigationRecyclerViewAdapter lNewAdapter = new NavigationRecyclerViewAdapter(
                this,
                lNewRecyclerView,
                lSwipeRefreshLayout,
                iDirectoryPath,
                false);

        if (mCurrentAdapter != null) {
            lNewAdapter.setPreviousAdapter(mCurrentAdapter);
            mCurrentAdapter.setNextAdapter(lNewAdapter);
            mCurrentAdapter.disappearOnLeft();
        }
        if (mCurrentAdapter != null && !iForceVerticalAppear)
            lNewAdapter.appearOnRight();
        else
            lNewAdapter.appearVertically();

        DividerItemDecoration mDividerItemDecoration = new DividerItemDecoration(this, DividerItemDecoration.VERTICAL);

        lNewRecyclerView.addItemDecoration(mDividerItemDecoration);
        lNewRecyclerView.setAdapter(lNewAdapter);
        lNewAdapter.setOnClickListener(new NavigationRecyclerViewAdapter.OnClickListener() {
            @Override
            public void onClick(FTPFile iFTPFile) {
                closeFABMenu();

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

        lNewAdapter.setOnLongClickListener(new NavigationRecyclerViewAdapter.OnLongClickListener() {
            @Override
            public void onLongClick(FTPFile iFTPFile) {
                if (!lNewAdapter.isInSelectionMode()) {
                    lNewAdapter.setSelectionMode(true);
                }
            }
        });

        lNewAdapter.setData(iFTPFiles);
        mCurrentAdapter = lNewAdapter;
    }

    private void initialize() {
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
        if (mDirectoryPath == null)
            mDirectoryPath = mBundle.getString(KEY_DIRECTORY_PATH, ROOT_DIRECTORY);

        // FTP Connection
        mFTPConnection = FTPConnection.getFTPConnection(lServerId);
    }

    private void runFetchProcedures(String iDirectoryPath, boolean iIsLargeDirectory, final boolean iRecovering) {
        dismissAllDialogs();
        mReconnectDialog = null;
        mLargeDirDialog = null;
        mLoadingDialog = null;
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
        // TODO : This on connection lost will be re-set each runProcedures
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
                if (!mDirectoryFetchFinished && mLargeDirDialog == null) { // in case if dialog has been canceled
                    if (mCurrentAdapter != null && mCurrentAdapter.getSwipeRefreshLayout().isRefreshing())
                        return;

                    mLoadingDialog = Utils.initProgressDialog(FTPNavigationActivity.this, new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            dialog.dismiss();
                            finish();
                        }
                    });
                    mLoadingDialog.setTitle("Loading..."); //TODO : strings
                    mLoadingDialog.create();
                    if (!mDirectoryFetchFinished)
                        mLoadingDialog.show();
                    else
                        mLoadingDialog = null;
                }
            }
        }, BAD_CONNECTION_TIME);
    }

    private void initializeFetchDirectory(final boolean iRecovering) {
        mFTPConnection.fetchDirectoryContent(mDirectoryPath, new FTPConnection.OnFetchDirectoryResult() {
            @Override
            public void onSuccess(final FTPFile[] iFTPFiles) {
                mDirectoryFetchFinished = true;
                if (mLoadingDialog != null && mLoadingDialog.isShowing())
                    mLoadingDialog.dismiss();
                if (mLargeDirDialog != null)
                    mLargeDirDialog.dismiss();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (iRecovering) {
                            mCurrentAdapter.setData(iFTPFiles);
                            mCurrentAdapter.appearVertically();
                            mCurrentAdapter.getSwipeRefreshLayout().setRefreshing(false);
                        } else
                            inflateNewAdapter(iFTPFiles, mDirectoryPath, iRecovering);
                    }
                });
            }

            @Override
            public void onFail(final FTPConnection.CONNECTION_STATUS iErrorCode) {
                mDirectoryFetchFinished = true;
                if (mLoadingDialog != null)
                    mLoadingDialog.dismiss();
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

    private void createDialogFolderClicked() {
        FTPFile lEnclosingDirectory = mFTPConnection.getCurrentDirectory();
        if (lEnclosingDirectory != null && !lEnclosingDirectory.hasPermission(FTPFile.USER_ACCESS, FTPFile.WRITE_PERMISSION)) {
            mErrorAlertDialog = new AlertDialog.Builder(FTPNavigationActivity.this)
                    .setTitle("Error") // TODO string
                    .setMessage("Creation has failed...\nYou need permissions")
                    .setCancelable(false)
                    .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface iDialog, int iWhich) {
                            iDialog.dismiss();
                        }
                    })
                    .create();
            mErrorAlertDialog.show();
            return;
        }

        mLoadingDialog = Utils.initProgressDialog(FTPNavigationActivity.this, new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                dialog.dismiss();
                finish();
            }
        });
        mLoadingDialog.setTitle("Loading..."); //TODO : strings
        mLoadingDialog.create();

        final AlertDialog.Builder lBuilder = new AlertDialog.Builder(this);
        lBuilder.setTitle("Create new folder"); // TODO : strings

        View lTextSection = View.inflate(this, R.layout.dialog_create_folder, null);
        final TextInputLayout lTextInputLayout = lTextSection.findViewById(R.id.name_edit_text_layout);
        final AutoCompleteTextView lEditTextView = lTextSection.findViewById(R.id.name_edit_text);

        lEditTextView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence iS, int iStart, int iCount, int iAfter) {
            }

            @Override
            public void onTextChanged(CharSequence iS, int iStart, int iBefore, int iCount) {
            }

            @Override
            public void afterTextChanged(Editable iEditable) {
                if (iEditable != null) {
                    String lString = iEditable.toString();
                    if (!Utils.isNullOrEmpty(lString.trim())) {
                        lTextInputLayout.setErrorEnabled(false);
                        mCreateFolderDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true);

                    } else {
                        lTextInputLayout.setError("Obligatory");
                        mCreateFolderDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
                    }
                }
            }
        });

        lBuilder.setView(lTextSection);
        lBuilder.setCancelable(false);
        lBuilder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String lName = lTextInputLayout.getEditText().getText().toString().trim();
                if (!Utils.isNullOrEmpty(lName)) {
                    dialog.dismiss();
                    createFolder(lName);
                }
            }
        });
        lBuilder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        lBuilder.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                closeFABMenu();
            }
        });

        mCreateFolderDialog = lBuilder.create();
        mCreateFolderDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        mCreateFolderDialog.show();
        mCreateFolderDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
    }

    private void createFolder(String iName) {
        mFTPConnection.createDirectory(mDirectoryPath, iName, new FTPConnection.OnCreateDirectoryResult() {
            @Override
            public void onSuccess(final FTPFile iNewDirectory) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mLoadingDialog.dismiss();
                        mCurrentAdapter.insertItem(iNewDirectory, 0);
                    }
                });
            }

            @Override
            public void onFail(final FTPConnection.CONNECTION_STATUS iErrorCode) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mLoadingDialog.dismiss();
                        if (mIsRunning && (mReconnectDialog == null || !mReconnectDialog.isShowing())) {
                            mErrorAlertDialog = new AlertDialog.Builder(FTPNavigationActivity.this)
                                    .setTitle("Error") // TODO string
                                    .setMessage("Creation has failed...\nCode : " + iErrorCode.name())
                                    .setCancelable(false)
                                    .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface iDialog, int iWhich) {
                                            iDialog.dismiss();
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

    private void onUploadFileClicked() {
        // TODO : upload file
    }

    private void dismissAllDialogs() {
        if (mReconnectDialog != null)
            mReconnectDialog.dismiss();
        if (mLargeDirDialog != null)
            mLargeDirDialog.dismiss();
        if (mLoadingDialog != null)
            mLoadingDialog.dismiss();
        if (mErrorAlertDialog != null)
            mErrorAlertDialog.dismiss();
    }

    private void buildFTPConnection(final boolean iIsRecovering, final boolean iRunFetchOnSuccess) {
        LogManager.info(TAG, "Rebuild FTP Connection");
        if (mFTPServer == null) {
            LogManager.error(TAG, "mFTPServer is null");
            LogManager.error(TAG, Arrays.toString(new Exception("mFTPServer instance is null").getStackTrace()));
        }

        mFTPConnection = new FTPConnection(mFTPServer);

        mLoadingDialog = Utils.initProgressDialog(this, new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                dialog.dismiss();
                if (mFTPConnection.isConnecting())
                    mFTPConnection.abortConnection();
                mFTPConnection.destroyConnection();
            }
        });
        mLoadingDialog.setContentView(R.layout.loading_icon);
        if (iIsRecovering)
            mLoadingDialog.setTitle("Reconnection..."); // TODO : strings
        else
            mLoadingDialog.setTitle("Connection..."); // TODO : strings
        mLoadingDialog.create();
        mLoadingDialog.show();

        mFTPConnection.connect(new FTPConnection.OnConnectResult() {
            @Override
            public void onSuccess() {
                dismissAllDialogs();
                if (iRunFetchOnSuccess) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            runFetchProcedures(mDirectoryPath, mIsLargeDirectory, false);
                        }
                    });
                }
            }

            @Override
            public void onFail(final FTPConnection.CONNECTION_STATUS iErrorCode) {
                if (iErrorCode == FTPConnection.CONNECTION_STATUS.ERROR_CONNECTION_INTERRUPTED)
                    return;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (mIsRunning) {
                            mLoadingDialog.dismiss();

                            new AlertDialog.Builder(FTPNavigationActivity.this)
                                    .setTitle("Error") // TODO string
                                    .setMessage((iIsRecovering ? "Reconnection" : "Connection") + " failed...\nCode : " + iErrorCode)
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
                        }
                        mFTPConnection.destroyConnection();
                    }
                });
            }
        });
    }

    // TODO : remove this code
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
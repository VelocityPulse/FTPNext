package com.example.ftpnext;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
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
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.BounceInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

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
    private static final int BAD_CONNECTION_TIME = 200;

    private static final int NAVIGATION_MESSAGE_CONNECTION_SUCCESS = 10;
    private static final int NAVIGATION_MESSAGE_CONNECTION_FAIL = 11;
    private static final int NAVIGATION_MESSAGE_CONNECTION_LOST = 12;
    private static final int NAVIGATION_MESSAGE_RECONNECT_SUCCESS = 13;
    private static final int NAVIGATION_MESSAGE_RECONNECT_FAIL = 14;
    private static final int NAVIGATION_MESSAGE_CREATE_FOLDER_SUCCESS = 15;
    private static final int NAVIGATION_MESSAGE_CREATE_FOLDER_FAIL = 16;
    private static final int NAVIGATION_MESSAGE_DIRECTORY_SUCCESS_FETCH = 17;
    private static final int NAVIGATION_MESSAGE_DIRECTORY_SUCCESS_RECOVERING = 18;
    private static final int NAVIGATION_MESSAGE_DIRECTORY_FAIL_FETCH = 19;
    private static final int NAVIGATION_MESSAGE_DIRECTORY_FAIL_UPDATE = 20;

    private static final int NAVIGATION_ORDER_DISMISS_DIALOGS = 100;
    private static final int NAVIGATION_ORDER_DISMISS_LOADING_DIALOGS = 101;
    private static final int NAVIGATION_ORDER_STOP_DELETING = 102;
    private static final int NAVIGATION_ORDER_REFRESH_DATA = 103;
    private static final int NAVIGATION_ORDER_SELECTED_MODE_ON = 104;
    private static final int NAVIGATION_ORDER_SELECTED_MODE_OFF = 105;

    private boolean mIsRunning;

    private FTPServer mFTPServer;
    private FTPConnection mFTPConnection;
    private FTPServerDAO mFTPServerDAO;
    private int mErrorCode;

    private NavigationRecyclerViewAdapter mCurrentAdapter;
    private FrameLayout mRecyclerSection;
    private String mDirectoryPath;
    private boolean mDirectoryFetchFinished;
    private boolean mIsLargeDirectory;

    private ProgressDialog mLoadingDialog;
    private ProgressDialog mCancelingDialog;
    private ProgressDialog mLargeDirDialog;
    private ProgressDialog mReconnectDialog;
    private AlertDialog mErrorAlertDialog;
    private AlertDialog mCreateFolderDialog;
    private AlertDialog mDeletingInfoDialog;
    private AlertDialog mDeletingErrorDialog;

    private Bundle mBundle;

    private boolean mIsFABOpen;
    private FloatingActionButton mMainFAB;
    private FloatingActionButton mCreateFolderFAB;
    private FloatingActionButton mUploadFileFAB;
    private Handler mHandler;

    @Override
    protected void onCreate(Bundle iSavedInstanceState) {
        LogManager.info(TAG, "On create");
        super.onCreate(iSavedInstanceState);
        setContentView(R.layout.activity_ftp_navigation);

        mIsRunning = true;
        initializeGUI();
        initializeHandler();
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
        LogManager.info(TAG, "On back pressed"); // TODO : Code the back pressed of delete dialogs
        if (mIsFABOpen) {
            closeFABMenu();
            return;
        }

        if (mFTPConnection.isBusy()) {
            LogManager.debug(TAG, "Canceling onBackPressed");
            return;
        }

        if (mCurrentAdapter.isInSelectionMode()) {
            mCurrentAdapter.setSelectionMode(false);
            showFABMenu();
            return;
        }

        if (mCurrentAdapter.getPreviousAdapter() != null) {
            destroyCurrentAdapter();
            mFTPConnection.updateWorkingDirectory(mDirectoryPath);
            return;
        }

        super.onBackPressed();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu iMenu) {
        getMenuInflater().inflate(R.menu.navigation, iMenu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
            case R.id.action_delete:
                if (mCurrentAdapter.isInSelectionMode())
                    createDialogDeleteSelection();
                else
                    mCurrentAdapter.setSelectionMode(true);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void initializeHandler() {
        mHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                FTPConnection.ERROR_CODE_DESCRIPTION lErrorDescription;
                FTPFile[] lFiles;

                if (!mIsRunning)
                    return;

                switch (msg.what) {

                    case NAVIGATION_ORDER_DISMISS_DIALOGS:
                        LogManager.info(TAG, "Handle : NAVIGATION_ORDER_DISMISS_DIALOGS");
                        dismissAllDialogs();
                        break;

                    case NAVIGATION_ORDER_DISMISS_LOADING_DIALOGS:
                        LogManager.info(TAG, "Handle : NAVIGATION_ORDER_DISMISS_LOADING_DIALOGS");
                        if (mLoadingDialog != null)
                            mLoadingDialog.dismiss();
                        if (mLargeDirDialog != null)
                            mLargeDirDialog.dismiss();
                        break;

                    case NAVIGATION_ORDER_REFRESH_DATA:
                        LogManager.info(TAG, "Handle : NAVIGATION_ORDER_REFRESH_DATA");
                        if (!mFTPConnection.isReconnecting() && mDirectoryFetchFinished) {
                            mCurrentAdapter.getSwipeRefreshLayout().setRefreshing(true);
                            runFetchProcedures(mDirectoryPath, false, true);
                        }
                        break;

                    case NAVIGATION_ORDER_SELECTED_MODE_ON:
                        LogManager.info(TAG, "Handle : NAVIGATION_ORDER_SELECTED_MODE_ON");
                        if (mCurrentAdapter != null && !mCurrentAdapter.isInSelectionMode()) {
                            mCurrentAdapter.setSelectionMode(true);
                            mCurrentAdapter.getSwipeRefreshLayout().setEnabled(false);
                        }
                        break;

                    case NAVIGATION_ORDER_SELECTED_MODE_OFF:
                        LogManager.info(TAG, "Handle : NAVIGATION_ORDER_SELECTED_MODE_OFF");
                        if (mCurrentAdapter != null && mCurrentAdapter.isInSelectionMode()) {
                            mCurrentAdapter.setSelectionMode(false);
                            mCurrentAdapter.getSwipeRefreshLayout().setEnabled(true);
                        }
                        break;

                    case NAVIGATION_MESSAGE_RECONNECT_SUCCESS:
                        LogManager.info(TAG, "Handle : NAVIGATION_MESSAGE_RECONNECT_SUCCESS");
                        if (mFTPConnection.isDeletingFiles()) {
                            mReconnectDialog.cancel();
                            if (mDeletingErrorDialog != null)
                                mDeletingErrorDialog.show();
                            else if (mDeletingInfoDialog != null) // TODO : Try to reconnect during a long delete without warn
                                mDeletingInfoDialog.show();
//                            else
//                                mFTPConnection.resumeDeleting();
                        } else
                            runFetchProcedures(mDirectoryPath, mIsLargeDirectory, true);
                        break;

                    case NAVIGATION_MESSAGE_CONNECTION_SUCCESS:
                        LogManager.info(TAG, "Handle : NAVIGATION_MESSAGE_CONNECTION_SUCCESS");
                        runFetchProcedures(mDirectoryPath, mIsLargeDirectory, true);
                        break;

                    case NAVIGATION_MESSAGE_CONNECTION_FAIL:
                        LogManager.info(TAG, "Handle : NAVIGATION_MESSAGE_CONNECTION_FAIL");
                        boolean lIsRecovering = msg.arg1 == 1;
                        lErrorDescription = (FTPConnection.ERROR_CODE_DESCRIPTION) msg.obj;
                        new AlertDialog.Builder(FTPNavigationActivity.this)
                                .setTitle("Error") // TODO string
                                .setMessage((lIsRecovering ? "Reconnection" : "Connection") + " failed..." +
                                        "\nError : " + lErrorDescription +
                                        "\nCode :" + mErrorCode)
                                .setCancelable(false)
                                .setNegativeButton("Terminate", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                        finish();
                                    }
                                })
                                .create()
                                .show();
                        if (mFTPConnection.isConnecting())
                            mFTPConnection.abortConnection();
                        break;

                    case NAVIGATION_MESSAGE_CONNECTION_LOST:
                        LogManager.info(TAG, "Handle : NAVIGATION_MESSAGE_CONNECTION_LOST");
                        mFTPConnection.abortFetchDirectoryContent();
                        if (mFTPConnection.isDeletingFiles()) {
                            mFTPConnection.pauseDeleting();
                            if (mDeletingInfoDialog != null)
                                mDeletingInfoDialog.hide();
                            if (mDeletingErrorDialog != null)
                                mDeletingErrorDialog.hide();
                        } else
                            dismissAllDialogs();
                        mReconnectDialog.show();

                        mFTPConnection.reconnect(new FTPConnection.OnConnectionRecover() {
                            @Override
                            public void onConnectionRecover() {
                                mHandler.sendEmptyMessage(NAVIGATION_MESSAGE_RECONNECT_SUCCESS);
                            }

                            @Override
                            public void onConnectionDenied(final FTPConnection.ERROR_CODE_DESCRIPTION iErrorEnum,
                                                           int iErrorCode) {
                                if (mFTPConnection != null)
                                    mFTPConnection.disconnect();
                                mErrorCode = iErrorCode;
                                mHandler.sendEmptyMessage(NAVIGATION_ORDER_DISMISS_DIALOGS);
                                mHandler.sendMessage(Message.obtain(
                                        mHandler,
                                        NAVIGATION_MESSAGE_RECONNECT_FAIL,
                                        iErrorEnum
                                ));
                            }
                        });
                        break;

                    case NAVIGATION_MESSAGE_CREATE_FOLDER_SUCCESS:
                        LogManager.info(TAG, "Handle : NAVIGATION_MESSAGE_CREATE_FOLDER_SUCCESS");
                        // TODO : Sort items
                        FTPFile lNewDirectory = (FTPFile) msg.obj;
                        mLoadingDialog.dismiss();
                        mCurrentAdapter.insertItem(lNewDirectory, 0);
                        mCurrentAdapter.getRecyclerView().scrollToPosition(0);
                        break;

                    case NAVIGATION_MESSAGE_CREATE_FOLDER_FAIL:
                        LogManager.info(TAG, "Handle : NAVIGATION_MESSAGE_CREATE_FOLDER_FAIL");
                        lErrorDescription = (FTPConnection.ERROR_CODE_DESCRIPTION) msg.obj;
                        mLoadingDialog.dismiss();
                        if (mIsRunning && (mReconnectDialog == null || !mReconnectDialog.isShowing())) {
                            mErrorAlertDialog = new AlertDialog.Builder(FTPNavigationActivity.this)
                                    .setTitle("Error") // TODO string
                                    .setMessage("Creation has failed...\nCode : " + lErrorDescription.name())
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
                        break;

                    case NAVIGATION_MESSAGE_RECONNECT_FAIL:
                        LogManager.info(TAG, "Handle : NAVIGATION_MESSAGE_RECONNECT_FAIL");
                        lErrorDescription = (FTPConnection.ERROR_CODE_DESCRIPTION) msg.obj;
                        new AlertDialog.Builder(FTPNavigationActivity.this)
                                .setTitle("Reconnection denied") // TODO string
                                .setMessage("Reconnection has failed...\nCode : " + lErrorDescription.name())
                                .setCancelable(false)
                                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                        finish();
                                    }
                                })
                                .create()
                                .show();
                        break;

                    case NAVIGATION_MESSAGE_DIRECTORY_SUCCESS_RECOVERING:
                        LogManager.info(TAG, "Handle : NAVIGATION_MESSAGE_DIRECTORY_SUCCESS_RECOVERING");
                        lFiles = (FTPFile[]) msg.obj;
                        mCurrentAdapter.setData(lFiles);
                        mCurrentAdapter.appearVertically();
                        mCurrentAdapter.getSwipeRefreshLayout().setRefreshing(false);
                        break;

                    case NAVIGATION_MESSAGE_DIRECTORY_SUCCESS_FETCH:
                        LogManager.info(TAG, "Handle : NAVIGATION_MESSAGE_DIRECTORY_SUCCESS_FETCH");
                        lFiles = (FTPFile[]) msg.obj;
                        inflateNewAdapter(lFiles, mDirectoryPath, false);
                        break;

                    case NAVIGATION_MESSAGE_DIRECTORY_FAIL_FETCH:
                        LogManager.info(TAG, "Handle : NAVIGATION_MESSAGE_DIRECTORY_FAIL_FETCH");
                        mDirectoryFetchFinished = true;
                        lErrorDescription = (FTPConnection.ERROR_CODE_DESCRIPTION) msg.obj;
                        if (mIsRunning && (mReconnectDialog == null || !mReconnectDialog.isShowing())) {
                            mErrorAlertDialog = new AlertDialog.Builder(FTPNavigationActivity.this)
                                    .setTitle("Error") // TODO string
                                    .setMessage("Connection has failed...\nCode : " + lErrorDescription.name())
                                    .setCancelable(false)
                                    .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface iDialog, int iWhich) {
                                            iDialog.dismiss();
                                            if (mCurrentAdapter.getPreviousAdapter() == null)
                                                onBackPressed();
                                        }
                                    })
                                    .create();
                            mErrorAlertDialog.show();
                        }
                        break;

                    case NAVIGATION_ORDER_STOP_DELETING:
                        LogManager.info(TAG, "Handle : NAVIGATION_ORDER_STOP_DELETING");
                        if (mDeletingInfoDialog != null && mDeletingInfoDialog.isShowing())
                            mDeletingInfoDialog.cancel();
                        if (mDeletingErrorDialog != null && mDeletingErrorDialog.isShowing())
                            mDeletingErrorDialog.cancel();
                        mFTPConnection.abortDeleting();
                        mDeletingInfoDialog = null;
                        mDeletingErrorDialog = null;
                        mHandler.sendEmptyMessage(NAVIGATION_ORDER_REFRESH_DATA);
                        break;
                }
            }
        };
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

    private void hideFABMenu() {
        mMainFAB.hide();
        mCreateFolderFAB.hide();
        mUploadFileFAB.hide();
    }

    private void showFABMenu() {
        mMainFAB.show();
        mCreateFolderFAB.show();
        mUploadFileFAB.show();
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

    private void destroyCurrentAdapter() {
        LogManager.info(TAG, "Destroy current adapter");
        final NavigationRecyclerViewAdapter lDeprecatedAdapter = mCurrentAdapter;
        lDeprecatedAdapter.disappearOnRightAndDestroy(new Runnable() {
            @Override
            public void run() {
                lDeprecatedAdapter.getRecyclerView().setAdapter(null);
                mRecyclerSection.removeView(lDeprecatedAdapter.getSwipeRefreshLayout());
            }
        });
        mCurrentAdapter = lDeprecatedAdapter.getPreviousAdapter();
        mCurrentAdapter.appearFromLeft();
        mCurrentAdapter.setNextAdapter(null);
        mDirectoryPath = mCurrentAdapter.getDirectoryPath();
    }

    private void inflateNewAdapter(FTPFile[] iFTPFiles, String iDirectoryPath, boolean iForceVerticalAppear) {
        LogManager.info(TAG, "Inflate new adapter");
        SwipeRefreshLayout lSwipeRefreshLayout = (SwipeRefreshLayout) View.inflate(this, R.layout.navigation_recycler_layout, null);
        lSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                LogManager.error(TAG, "On refresh");
                mHandler.sendEmptyMessage(NAVIGATION_ORDER_REFRESH_DATA);
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
                mRecyclerSection,
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
            lNewAdapter.appearFromRight();
        else
            lNewAdapter.appearVertically();

        DividerItemDecoration mDividerItemDecoration = new DividerItemDecoration(this, DividerItemDecoration.VERTICAL);

        lNewRecyclerView.addItemDecoration(mDividerItemDecoration);
        lNewRecyclerView.setAdapter(lNewAdapter);
        lNewAdapter.setOnClickListener(new NavigationRecyclerViewAdapter.OnClickListener() {
            @Override
            public void onClick(FTPFile iFTPFile) {
                if (lNewAdapter.isInSelectionMode()) {
                    lNewAdapter.switchCheckBox(iFTPFile);
                } else {
                    closeFABMenu();

                    if (iFTPFile.isDirectory()) {
//                        if (iFTPFile.hasPermission(FTPFile.USER_ACCESS, FTPFile.READ_PERMISSION)
//                                || iFTPFile.hasPermission(FTPFile.USER_ACCESS, FTPFile.WRITE_PERMISSION)) {
                        mIsLargeDirectory = iFTPFile.getSize() > LARGE_DIRECTORY_SIZE;
                        runFetchProcedures(mDirectoryPath + "/" + iFTPFile.getName(), mIsLargeDirectory, false);
//                        } else
//                            Utils.createErrorAlertDialog(FTPNavigationActivity.this, "You don't have enough permission");
                    } else {
                        // If it is a file
                        lNewAdapter.setItemsClickable(true);
                    }
                }
            }
        });

        lNewAdapter.setOnLongClickListener(new NavigationRecyclerViewAdapter.OnLongClickListener() {
            @Override
            public void onLongClick(FTPFile iFTPFile) {
                if (!lNewAdapter.isInSelectionMode()) {
                    closeFABMenu();
                    hideFABMenu();
                    lNewAdapter.setSelectionMode(true);
                    lNewAdapter.setSelectedCheckBox(iFTPFile, true);
                }
            }
        });

        lNewAdapter.setData(iFTPFiles);
        mCurrentAdapter = lNewAdapter;
    }

    private void initialize() {
        if (!AppCore.isApplicationStarted()) {
            new AppCore(this).startApplication();
        }

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

        if (mFTPConnection == null)
            LogManager.debug(TAG, "FTP CONNECTION NULL");

        // Reconnect dialog
        if (mFTPConnection != null) {
            mFTPConnection.setOnConnectionLost(new FTPConnection.OnConnectionLost() {
                @Override
                public void onConnectionLost() {
                    mHandler.sendEmptyMessage(NAVIGATION_MESSAGE_CONNECTION_LOST);
                }
            });
        }

        // Bad connection, Large dir, Reconnect dialog
        initializeDialogs();
    }

    private void runFetchProcedures(final String iDirectoryPath, boolean iIsLargeDirectory, final boolean iRecovering) {
        dismissAllDialogs();
        mLargeDirDialog.cancel();
        mReconnectDialog.cancel();
        mLoadingDialog.cancel();
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
                            finish();
                        }
                    })
                    .create()
                    .show();
            return;
        }

        if (iIsLargeDirectory)
            mLargeDirDialog.show();

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
                    initializeFetchDirectory(iDirectoryPath, iRecovering);
                }
            }).start();
        } else
            initializeFetchDirectory(iDirectoryPath, iRecovering);
    }

    private void initializeDialogs() {
        // Reconnection dialog
        mReconnectDialog = Utils.initProgressDialog(this, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                mFTPConnection.abortConnection();
                finish();
            }
        });
        mReconnectDialog.setCancelable(false);
        mReconnectDialog.setTitle("Reconnection..."); // TODO : strings
        mReconnectDialog.create();

        // Large directory dialog
        mLargeDirDialog = Utils.initProgressDialog(this, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface iDialog, int iWhich) {
                iDialog.dismiss();
                mCancelingDialog.show();
                mFTPConnection.abortFetchDirectoryContent();
            }
        });
        mLargeDirDialog.setCancelable(false);
        mLargeDirDialog.setTitle("Large directory"); // TODO : strings
        mLargeDirDialog.create();

        // Loading dialog
        mLoadingDialog = Utils.initProgressDialog(this, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface iDialog, int iWhich) {
                iDialog.dismiss();
                onBackPressed();
            }
        });
        mLoadingDialog.setTitle("Loading..."); //TODO : strings
        mLoadingDialog.create();

        mCancelingDialog = new ProgressDialog(this);
        mCancelingDialog.setContentView(R.layout.loading_icon);
        mCancelingDialog.setCancelable(false);
        mCancelingDialog.setCanceledOnTouchOutside(false);
        mCancelingDialog.setTitle("Canceling..."); //TODO : strings
        mCancelingDialog.create();

        //Bad Connection
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!mDirectoryFetchFinished && (mLargeDirDialog == null || !mLargeDirDialog.isShowing())) { // in case if dialog has been canceled
                    if (mCurrentAdapter != null && mCurrentAdapter.getSwipeRefreshLayout().isRefreshing())
                        return;

                    if (!mDirectoryFetchFinished) {
                        mLoadingDialog.setTitle("Loading..."); //TODO : strings
                        mLoadingDialog.show();
                    }
                }
            }
        }, BAD_CONNECTION_TIME);
    }

    private void initializeFetchDirectory(final String iDirectoryPath, final boolean iRecovering) {
        mFTPConnection.fetchDirectoryContent(iDirectoryPath, new FTPConnection.OnFetchDirectoryResult() {
            @Override
            public void onSuccess(final FTPFile[] iFTPFiles) {
                mDirectoryPath = iDirectoryPath;
                mDirectoryFetchFinished = true;
                mHandler.sendEmptyMessage(NAVIGATION_ORDER_DISMISS_LOADING_DIALOGS);
                if (iRecovering) {
                    mHandler.sendMessage(Message.obtain(
                            mHandler,
                            NAVIGATION_MESSAGE_DIRECTORY_SUCCESS_RECOVERING,
                            iFTPFiles
                    ));
                } else
                    mHandler.sendMessage(Message.obtain(
                            mHandler,
                            NAVIGATION_MESSAGE_DIRECTORY_SUCCESS_FETCH,
                            iFTPFiles));
            }

            @Override
            public void onFail(final FTPConnection.ERROR_CODE_DESCRIPTION iErrorEnum, int iErrorCode) {
                mErrorCode = iErrorCode;
                mHandler.sendEmptyMessage(NAVIGATION_ORDER_DISMISS_LOADING_DIALOGS);
                mHandler.sendMessage(Message.obtain(
                        mHandler,
                        NAVIGATION_MESSAGE_DIRECTORY_FAIL_FETCH,
                        iErrorEnum
                ));
            }

            @Override
            public void onInterrupt() {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mCancelingDialog != null && mCancelingDialog.isShowing())
                            mCancelingDialog.dismiss();
                        mCurrentAdapter.setItemsClickable(true);
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

        mLoadingDialog.setTitle("Loading..."); //TODO : strings
        mLoadingDialog.show();

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

                mHandler.sendMessage(Message.obtain(
                        mHandler,
                        NAVIGATION_MESSAGE_CREATE_FOLDER_SUCCESS,
                        iNewDirectory
                ));
            }

            @Override
            public void onFail(final FTPConnection.ERROR_CODE_DESCRIPTION iErrorEnum, int iErrorCode) {
                mErrorCode = iErrorCode;
                mHandler.sendMessage(Message.obtain(
                        mHandler,
                        NAVIGATION_MESSAGE_CREATE_FOLDER_SUCCESS,
                        iErrorEnum
                ));
            }
        });
    }

    private void createDialogDeleteSelection() {
        final FTPFile[] lSelectedFiles = mCurrentAdapter.getSelection();

        if (lSelectedFiles.length == 0) {
            mErrorAlertDialog = new AlertDialog.Builder(FTPNavigationActivity.this)
                    .setTitle("Error") // TODO string
                    .setMessage("Select something")
                    .setCancelable(false)
                    .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface iDialog, int iWhich) {
                            iDialog.dismiss();
                        }
                    })
                    .create();
            mErrorAlertDialog.show();
        } else {
            mDeletingInfoDialog = new AlertDialog.Builder(this)
                    .setTitle("Deleting :")
                    .setMessage("Are you sure to delete the selection ? (" + lSelectedFiles.length + " files)")
                    .setPositiveButton("yes", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface iDialog, int iWhich) {
                            iDialog.dismiss();
                            deleteFile(lSelectedFiles);
                        }
                    })
                    .setNegativeButton("cancel", null)
                    .show();
        }
    }

    private void deleteFile(FTPFile[] iSelectedFiles) {
        mHandler.sendEmptyMessage(NAVIGATION_ORDER_SELECTED_MODE_OFF);
        mFTPConnection.deleteFiles(iSelectedFiles, mFTPConnection.new OnDeleteListener() {

            ProgressBar mProgressDirectory;
            ProgressBar mProgressSubDirectory;
            TextView mTextViewDirectory;
            TextView mTextViewSubDirectory;

            @Override
            public void onStartDelete() {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        final AlertDialog.Builder lBuilder = new AlertDialog.Builder(FTPNavigationActivity.this);
                        lBuilder.setTitle("Deleting"); // TODO : strings

                        View lDialogDoubleProgress = View.inflate(FTPNavigationActivity.this, R.layout.dialog_double_progress, null);
                        mProgressDirectory = lDialogDoubleProgress.findViewById(R.id.dialog_double_progress_progress_1);
                        mTextViewDirectory = lDialogDoubleProgress.findViewById(R.id.dialog_double_progress_text_1);
                        mProgressSubDirectory = lDialogDoubleProgress.findViewById(R.id.dialog_double_progress_progress_2);
                        mTextViewSubDirectory = lDialogDoubleProgress.findViewById(R.id.dialog_double_progress_text_2);

                        lBuilder.setView(lDialogDoubleProgress);
                        lBuilder.setCancelable(false);
                        lBuilder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                mHandler.sendEmptyMessage(NAVIGATION_ORDER_STOP_DELETING);
                            }
                        });
                        mDeletingInfoDialog = lBuilder.create();
                        mDeletingInfoDialog.show();
                    }
                });
            }

            @Override
            public void onProgressDirectory(final int iDirectoryProgress, final int iTotalDirectories, final String iDirectoryName) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mTextViewDirectory.setText(iDirectoryName);
                        mProgressDirectory.setMax(iTotalDirectories);
                        mProgressDirectory.setProgress(iDirectoryProgress);
                    }
                });
            }

            @Override
            public void onProgressSubDirectory(final int iSubDirectoryProgress, final int iTotalSubDirectories, final String iSubDirectoryName) {
//                super.onProgressSubDirectory(iSubDirectoryProgress, iTotalSubDirectories, iSubDirectoryName);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mTextViewSubDirectory.setText(iSubDirectoryName);
                        mProgressSubDirectory.setMax(iTotalSubDirectories);
                        mProgressSubDirectory.setProgress(iSubDirectoryProgress);
                    }
                });
            }

            @Override
            public void onRightAccessFail(final FTPFile iFTPFile) {
                super.onRightAccessFail(iFTPFile);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        final AlertDialog.Builder lBuilder = new AlertDialog.Builder(FTPNavigationActivity.this);
                        lBuilder.setTitle("Permission error"); // TODO : strings
                        final View lDialogDeleteError = View.inflate(FTPNavigationActivity.this, R.layout.dialog_delete_error, null);
                        final CheckBox lCheckBox = lDialogDeleteError.findViewById(R.id.dialog_delete_error_checkbox);
                        final TextView lTextView = lDialogDeleteError.findViewById(R.id.dialog_delete_error_text);

                        lTextView.setText("Remember this choice : "); // TODO : strings
                        lBuilder.setView(lDialogDeleteError);
                        String lMessage =
                                "\nNot enough permissions to delete this " +
                                        (iFTPFile.isDirectory() ? "directory \n" : "file \n") +
                                        iFTPFile.getName();
                        lBuilder.setMessage(lMessage);
                        lBuilder.setCancelable(false);
                        lBuilder.setPositiveButton("Ignore", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface iDialog, int iWhich) {
                                iDialog.dismiss();
                                mDeletingErrorDialog = null;
                                if (mDeletingInfoDialog != null)
                                    mDeletingInfoDialog.show();
                                mFTPConnection.setDeletingByPassRightErrors(lCheckBox.isChecked());
                                mFTPConnection.resumeDeleting();
                            }
                        });

                        lBuilder.setNegativeButton("Stop", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface iDialog, int iWhich) {
                                iDialog.dismiss();
                                mDeletingErrorDialog = null; // check at null used in NAVIGATION_MESSAGE_RECONNECT_SUCCESS
                                mCurrentAdapter.setSelectionMode(false);
                                mFTPConnection.abortDeleting();
                            }
                        });
                        mDeletingErrorDialog = lBuilder.create();
                        mDeletingInfoDialog.hide();
                        mDeletingErrorDialog.show();
                    }
                });
            }

            @Override
            public void onFinish() {
                if (mDeletingInfoDialog != null) {
                    mDeletingInfoDialog.cancel();
                    mDeletingInfoDialog = null; // check at null used in NAVIGATION_MESSAGE_RECONNECT_SUCCESS
                }
                if (mDeletingErrorDialog != null) {
                    mDeletingErrorDialog.cancel();
                    mDeletingErrorDialog = null;
                }

                mHandler.sendEmptyMessage(NAVIGATION_ORDER_DISMISS_DIALOGS);
                mHandler.sendEmptyMessage(NAVIGATION_ORDER_REFRESH_DATA);
            }

            @Override
            public void onFail(final FTPFile iFTPFile) {
                super.onFail(iFTPFile);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        final AlertDialog.Builder lBuilder = new AlertDialog.Builder(FTPNavigationActivity.this);
                        lBuilder.setTitle("Delete error"); // TODO : strings
                        final View lDialogDeleteError = View.inflate(FTPNavigationActivity.this, R.layout.dialog_delete_error, null);
                        final CheckBox lCheckBox = lDialogDeleteError.findViewById(R.id.dialog_delete_error_checkbox);
                        final TextView lTextView = lDialogDeleteError.findViewById(R.id.dialog_delete_error_text);

                        lTextView.setText("Remember this choice : "); // TODO : strings
                        lBuilder.setView(lDialogDeleteError);
                        String lMessage =
                                "\nImpossible to delete the " +
                                        (iFTPFile.isDirectory() ? "directory \n" : "file \n") +
                                        iFTPFile.getName();
                        lBuilder.setMessage(lMessage);
                        lBuilder.setCancelable(false);
                        lBuilder.setPositiveButton("Ignore", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface iDialog, int iWhich) {
                                iDialog.dismiss();
                                mDeletingErrorDialog = null; // check at null used in NAVIGATION_MESSAGE_RECONNECT_SUCCESS
                                if (mDeletingInfoDialog != null)
                                    mDeletingInfoDialog.show();
                                mFTPConnection.setDeletingByPassFailErrors(lCheckBox.isChecked());
                                mFTPConnection.resumeDeleting();
                            }
                        });

                        lBuilder.setNegativeButton("Stop", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface iDialog, int iWhich) {
                                iDialog.dismiss();
                                mDeletingErrorDialog = null;
                                mCurrentAdapter.setSelectionMode(false);
                                mFTPConnection.abortDeleting();
                            }
                        });
                        if (mDeletingInfoDialog != null)
                            mDeletingInfoDialog.hide();

                        mDeletingErrorDialog = lBuilder.create();
                        mDeletingErrorDialog.show();
                    }
                });
            }
        });
    }

    private void onUploadFileClicked() {
        // TODO : upload file
    }

    // TODO : Rename feature

    private void dismissAllDialogs() {
        if (mReconnectDialog != null)
            mReconnectDialog.cancel();
        if (mLargeDirDialog != null)
            mLargeDirDialog.cancel();
        if (mLoadingDialog != null)
            mLoadingDialog.cancel();
        if (mErrorAlertDialog != null)
            mErrorAlertDialog.cancel();
        if (mDeletingInfoDialog != null)
            mDeletingInfoDialog.cancel();
        if (mDeletingErrorDialog != null)
            mDeletingErrorDialog.cancel();
    }

    @SuppressWarnings("SameParameterValue")
    private void buildFTPConnection(final boolean iIsRecovering, final boolean iRunFetchOnSuccess) {
        LogManager.info(TAG, "Rebuild FTP Connection");
        if (mFTPServer == null) {
            LogManager.error(TAG, "mFTPServer is null");
            LogManager.error(TAG, Arrays.toString(new Exception("mFTPServer instance is null").getStackTrace()));
            return;
        }

        mFTPConnection = new FTPConnection(mFTPServer);

        if (iIsRecovering)
            mLoadingDialog.setTitle("Reconnection..."); // TODO : strings
        else
            mLoadingDialog.setTitle("Connection..."); // TODO : strings
        mLoadingDialog.show();

        mFTPConnection.connect(new FTPConnection.OnConnectResult() {
            @Override
            public void onSuccess() {
                mHandler.sendEmptyMessage(NAVIGATION_ORDER_DISMISS_DIALOGS);
                if (iRunFetchOnSuccess)
                    mHandler.sendEmptyMessage(NAVIGATION_MESSAGE_CONNECTION_SUCCESS);
            }

            @Override
            public void onFail(final FTPConnection.ERROR_CODE_DESCRIPTION iErrorEnum, int iErrorCode) {
                if (iErrorEnum == FTPConnection.ERROR_CODE_DESCRIPTION.ERROR_CONNECTION_INTERRUPTED)
                    return;
                mErrorCode = iErrorCode;
                mHandler.sendEmptyMessage(NAVIGATION_ORDER_DISMISS_DIALOGS);
                mHandler.sendMessage(Message.obtain(
                        mHandler,
                        NAVIGATION_MESSAGE_CONNECTION_FAIL,
                        iIsRecovering ? 1 : 0,
                        0,
                        iErrorEnum));
            }
        });
    }
}
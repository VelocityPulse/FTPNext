package com.example.ftpnext;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.TextInputLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
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

import com.example.ftpnext.adapters.NarrowTransferAdapter;
import com.example.ftpnext.adapters.NavigationRecyclerViewAdapter;
import com.example.ftpnext.commons.Utils;
import com.example.ftpnext.core.AppCore;
import com.example.ftpnext.core.LogManager;
import com.example.ftpnext.database.DataBase;
import com.example.ftpnext.database.FTPServerTable.FTPServer;
import com.example.ftpnext.database.FTPServerTable.FTPServerDAO;
import com.example.ftpnext.database.PendingFileTable.PendingFile;
import com.example.ftpnext.ftpservices.AFTPConnection;
import com.example.ftpnext.ftpservices.FTPServices;
import com.example.ftpnext.ftpservices.FTPTransfer;

import org.apache.commons.net.ftp.FTPFile;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class FTPNavigationActivity extends AppCompatActivity {

    private static final String TAG = "FTP NAVIGATION ACTIVITY";

    private static final int ACTIVITY_REQUEST_CODE_READ_EXTERNAL_STORAGE = 1;

    public static final int NO_DATABASE_ID = -1;
    public static final String ROOT_DIRECTORY = "/";

    public static final String KEY_DATABASE_ID = "KEY_DATABASE_ID";
    public static final String KEY_DIRECTORY_PATH = "KEY_DIRECTORY_PATH";

    private static final int LARGE_DIRECTORY_SIZE = 30000;
    private static final int BAD_CONNECTION_TIME = 50;

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

    private OnPermissionAnswer mOnPermissionAnswer;

    private FTPServer mFTPServer;
    private FTPServices mFTPServices;
    private FTPServerDAO mFTPServerDAO;
    private int mErrorCode;

    private NavigationRecyclerViewAdapter mCurrentAdapter;
    private NarrowTransferAdapter mNarrowTransferAdapter;
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
    private AlertDialog mIndexingPendingFilesDialog;
    private AlertDialog mDownloadingDialog;
    private AlertDialog mDeletingInfoDialog;
    private AlertDialog mDeletingErrorDialog;

    private Handler mHandler;

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
        initializeHandler();
        initialize();
        if (mFTPServices == null)
            buildFTPConnection(true, true);
        else
            runFetchProcedures(mDirectoryPath, mIsLargeDirectory, false);
    }

    @Override
    protected void onResume() {
        LogManager.info(TAG, "On resume");
        super.onResume();
        if (mFTPServices == null) {
            initialize();
            if (mFTPServices == null)
                buildFTPConnection(true, true);
            else
                runFetchProcedures(mDirectoryPath, mIsLargeDirectory, true);
        } else {
            if (!AppCore.getNetworkManager().isNetworkAvailable()) {
                mHandler.sendEmptyMessage(NAVIGATION_MESSAGE_CONNECTION_LOST);
            } else {
                LogManager.info(TAG, "Network apparently still available");
            }
        }
    }

    // Orientation is manager in android manifest with android:configChanges

    @Override
    protected void onDestroy() {
        LogManager.info(TAG, "On destroy");
        mIsRunning = false;

        dismissAllDialogs();

        if (mFTPServices != null && mFTPServices.isFetchingFolders())
            mFTPServices.abortFetchDirectoryContent();
        if (mFTPServices != null)
            mFTPServices.destroyConnection();

        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        LogManager.info(TAG, "On back pressed");
        if (mIsFABOpen) {
            closeFABMenu();
            return;
        }

        if (mFTPServices.isBusy()) {
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
            mFTPServices.updateWorkingDirectory(mDirectoryPath);
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
            case R.id.action_download:
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                        PackageManager.PERMISSION_GRANTED) {

                    String[] lPermissions = new String[]{
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE};

                    requestPermission(
                            this,
                            lPermissions,
                            ACTIVITY_REQUEST_CODE_READ_EXTERNAL_STORAGE,
                            new OnPermissionAnswer() {
                                @Override
                                public void onAccepted() {
                                    onClickDownload();
                                }
                            });

                }
                onClickDownload();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void requestPermission(Activity iActivity, final String[] iPermissions,
                                   int iRequestCode, OnPermissionAnswer iOnPermissionAnswer) {

        mOnPermissionAnswer = iOnPermissionAnswer;
        ActivityCompat.requestPermissions(this,
                iPermissions,
                iRequestCode);
    }

    @Override
    public void onRequestPermissionsResult(int iRequestCode,
                                           @NotNull String[] iPermissions, @NotNull int[] iGrantResults) {

        switch (iRequestCode) {
            case ACTIVITY_REQUEST_CODE_READ_EXTERNAL_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                if (iGrantResults.length > 0 && iGrantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    LogManager.info(TAG, "PERMISSION OK : READ_EXTERNAL_STORAGE");
                    if (mOnPermissionAnswer != null)
                        mOnPermissionAnswer.onAccepted();
                } else {
                    LogManager.info(TAG, "PERMISSION DENY : READ_EXTERNAL_STORAGE");
                    if (mOnPermissionAnswer != null)
                        mOnPermissionAnswer.onDenied();
                }
            }
        }
    }

    private void initializeHandler() {
        mHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                AFTPConnection.ErrorCodeDescription lErrorDescription;
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
                        if (!mFTPServices.isReconnecting() && mDirectoryFetchFinished) {
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
                        if (mFTPServices.isDeletingFiles()) {
                            mReconnectDialog.cancel();
                            if (mDeletingErrorDialog != null)
                                mDeletingErrorDialog.show();
                            else if (mDeletingInfoDialog != null) // TODO : Try to reconnect during a long delete without warn
                                mDeletingInfoDialog.show();
//                            else
//                                mFTPServices.resumeDeleting();
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
                        lErrorDescription = (AFTPConnection.ErrorCodeDescription) msg.obj;
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
                        if (mFTPServices.isConnecting())
                            mFTPServices.abortConnection();
                        break;

                    case NAVIGATION_MESSAGE_CONNECTION_LOST:
                        LogManager.info(TAG, "Handle : NAVIGATION_MESSAGE_CONNECTION_LOST");
                        mFTPServices.abortFetchDirectoryContent();
                        if (mFTPServices.isDeletingFiles()) {
                            mFTPServices.pauseDeleting();
                            if (mDeletingInfoDialog != null)
                                mDeletingInfoDialog.hide();
                            if (mDeletingErrorDialog != null)
                                mDeletingErrorDialog.hide();
                        } else
                            dismissAllDialogs();
                        mReconnectDialog.show();

                        mFTPServices.reconnect(new AFTPConnection.OnConnectionRecover() {
                            @Override
                            public void onConnectionRecover() {
                                mHandler.sendEmptyMessage(NAVIGATION_MESSAGE_RECONNECT_SUCCESS);
                            }

                            @Override
                            public void onConnectionDenied(final AFTPConnection.ErrorCodeDescription iErrorEnum,
                                                           int iErrorCode) {
                                if (mFTPServices != null)
                                    mFTPServices.disconnect();
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
                        lErrorDescription = (AFTPConnection.ErrorCodeDescription) msg.obj;
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
                        lErrorDescription = (AFTPConnection.ErrorCodeDescription) msg.obj;
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
                        lErrorDescription = (AFTPConnection.ErrorCodeDescription) msg.obj;
                        if (mCurrentAdapter != null)
                            mCurrentAdapter.setItemsClickable(true);
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
                        mFTPServices.abortDeleting();
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

    private void inflateNewAdapter(FTPFile[] iFTPFiles, String iDirectoryPath, final boolean iForceVerticalAppear) {
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

        DividerItemDecoration lDividerItemDecoration = new DividerItemDecoration(this, DividerItemDecoration.VERTICAL);

        lNewRecyclerView.addItemDecoration(lDividerItemDecoration);
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
                        lNewAdapter.setItemsClickable(false);
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


        final NavigationRecyclerViewAdapter lCurrentAdapterSavedStatus = mCurrentAdapter;
        final boolean lIsTheFirstAdapter = mCurrentAdapter == null;
        lNewAdapter.setOnFirstViewHolderCreation(new NavigationRecyclerViewAdapter.OnFirstViewHolderCreation() {
            @Override
            public void onCreation() {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        // if it's not the first list
                        if (!lIsTheFirstAdapter) {
                            lNewAdapter.setPreviousAdapter(lCurrentAdapterSavedStatus);
                            lCurrentAdapterSavedStatus.setNextAdapter(lNewAdapter);
                            lCurrentAdapterSavedStatus.disappearOnLeft();
                        }

                        // if it's the first list or need to appear vertically
                        if (lIsTheFirstAdapter || iForceVerticalAppear) {
                            lNewAdapter.appearVertically();
                        } else {
                            lNewAdapter.appearFromRight();
                        }
                    }
                });
            }
        });

        lNewAdapter.setData(iFTPFiles);
        mCurrentAdapter = lNewAdapter;
    }

    private void initialize() {
        mFTPServerDAO = DataBase.getFTPServerDAO();

        Bundle lBundle = this.getIntent().getExtras();

        // Server ID
        int lServerId = lBundle.getInt(KEY_DATABASE_ID);
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
            mDirectoryPath = lBundle.getString(KEY_DIRECTORY_PATH, ROOT_DIRECTORY);

        // FTP Connection
        mFTPServices = FTPServices.getFTPServicesInstance(lServerId);

        if (mFTPServices == null)
            LogManager.debug(TAG, "FTP CONNECTION NULL");

        // Reconnect dialog
        if (mFTPServices != null) {
            mFTPServices.setIOnConnectionLost(new AFTPConnection.IOnConnectionLost() {
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

        if (mFTPServices == null) {
            LogManager.error(TAG, "AFTPConnection instance is null");
            LogManager.error(TAG, Arrays.toString(new Exception("AFTPConnection instance is null").getStackTrace()));
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
        if (mFTPServices.isFetchingFolders()) { // if another activity didn't stop its fetch yet
            new Thread(new Runnable() {
                @Override
                public void run() {
                    while (mFTPServices.isFetchingFolders()) {
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
        // Loading dialog
        mLoadingDialog = Utils.initProgressDialog(this, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface iDialog, int iWhich) {
                iDialog.dismiss();
                onBackPressed();
            }
        });
        mLoadingDialog.setTitle("Loading..."); // TODO : strings
        mLoadingDialog.create();

        // Reconnection dialog
        mReconnectDialog = Utils.initProgressDialog(this, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                mFTPServices.abortConnection();
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
                mFTPServices.abortFetchDirectoryContent();
            }
        });
        mLargeDirDialog.setCancelable(false);
        mLargeDirDialog.setTitle("Large directory"); // TODO : strings
        mLargeDirDialog.create();

        mCancelingDialog = new ProgressDialog(this);
        mCancelingDialog.setContentView(R.layout.loading_icon);
        mCancelingDialog.setCancelable(false);
        mCancelingDialog.setCanceledOnTouchOutside(false);
        mCancelingDialog.setTitle("Canceling..."); //TODO : strings
        mCancelingDialog.create();
    }

    private void initializeFetchDirectory(final String iDirectoryPath, final boolean iRecovering) {

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

        mFTPServices.fetchDirectoryContent(iDirectoryPath, new FTPServices.IOnFetchDirectoryResult() {
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
            public void onFail(final AFTPConnection.ErrorCodeDescription iErrorEnum, int iErrorCode) {
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
        FTPFile lEnclosingDirectory = mFTPServices.getCurrentDirectory();
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

        mFTPServices.createDirectory(mDirectoryPath, iName, new FTPServices.OnCreateDirectoryResult() {
            @Override
            public void onSuccess(final FTPFile iNewDirectory) {

                mHandler.sendMessage(Message.obtain(
                        mHandler,
                        NAVIGATION_MESSAGE_CREATE_FOLDER_SUCCESS,
                        iNewDirectory
                ));
            }

            @Override
            public void onFail(final AFTPConnection.ErrorCodeDescription iErrorEnum, int iErrorCode) {
                mErrorCode = iErrorCode;
                mHandler.sendMessage(Message.obtain(
                        mHandler,
                        NAVIGATION_MESSAGE_CREATE_FOLDER_SUCCESS,
                        iErrorEnum
                ));
            }
        });
    }

    private void onUploadFileClicked() {
        // TODO : upload file
    }

    private void createDialogDownloadSelection() {
        LogManager.info(TAG, "Create dialog download selection");
        final FTPFile[] lSelectedFiles = mCurrentAdapter.getSelection();

        if (lSelectedFiles.length == 0)
            createDialogError("Select something.").show();
        else {
            mIndexingPendingFilesDialog = new AlertDialog.Builder(this)
                    .setTitle("Downloading :") // TODO : Strings
                    .setMessage("Do you confirm the download of " + lSelectedFiles.length + " files ?")
                    .setPositiveButton("yes", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface iDialog, int iWhich) {
                            iDialog.dismiss();
                            indexesFilesForDownload(lSelectedFiles);
                        }
                    })
                    .setNegativeButton("cancel", null)
                    .show();
        }
    }

    private void onClickDownload() {
        if (mCurrentAdapter.isInSelectionMode())
            createDialogDownloadSelection();
        else
            createDialogError("Select something.").show();
    }

    private void indexesFilesForDownload(final FTPFile[] iSelectedFiles) {
        LogManager.info(TAG, "Download file");
        mHandler.sendEmptyMessage(NAVIGATION_ORDER_SELECTED_MODE_OFF);

        mFTPServices.indexingPendingFilesProcedure(iSelectedFiles, new FTPServices.OnIndexingPendingFilesListener() {

            TextView mIndexingFolderText;
            TextView mIndexingFileText;

            @Override
            public void onStart() {
                LogManager.info(TAG, "Indexing listener : On start");
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {

                        final AlertDialog.Builder lBuilder = new AlertDialog.Builder(FTPNavigationActivity.this);
                        View mIndexingPendingFilesView = View.inflate(FTPNavigationActivity.this,
                                R.layout.dialog_indexing_progress, null);

                        mIndexingFolderText = mIndexingPendingFilesView.findViewById(R.id.dialog_indexing_folder);
                        mIndexingFileText = mIndexingPendingFilesView.findViewById(R.id.dialog_indexing_file);

                        lBuilder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface iDialog, int iWhich) {
                                iDialog.dismiss();
                                mFTPServices.abortIndexingPendingFiles();
                            }
                        });

                        lBuilder.setCancelable(false);
                        lBuilder.setView(mIndexingPendingFilesView);
                        lBuilder.setMessage("Indexing files :"); // TODO : strings
                        mIndexingPendingFilesDialog = lBuilder.create();
                        mIndexingPendingFilesDialog.show();
                    }
                });
            }

            @Override
            public void onFetchingFolder(final String iPath) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mIndexingFolderText.setText(iPath);
                    }
                });
            }

            @Override
            public void onNewIndexedFile(final PendingFile iPendingFile) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mIndexingFileText.setText(iPendingFile.getName());
                    }
                });
            }

            @Override
            public void onResult(boolean isSuccess, PendingFile[] iPendingFiles) {
                LogManager.info(TAG, "Indexing : On result");
                if (!isSuccess)
                    return;

                DataBase.getPendingFileDAO().deleteAll(); // TODO : DATA BASE RESET HERE
                DataBase.getPendingFileDAO().add(iPendingFiles);

                if (mIndexingPendingFilesDialog != null) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mIndexingPendingFilesDialog.cancel();
                        }
                    });
                }

                DownloadFiles(iPendingFiles);

            }
        });
    }

    private void DownloadFiles(final PendingFile[] iPendingFiles) {
        final FTPTransfer lFTPTransfer = new FTPTransfer(mFTPServer.getDataBaseId());

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                final AlertDialog.Builder lBuilder = new AlertDialog.Builder(FTPNavigationActivity.this);

                View lDownloadingDialogView = View.inflate(FTPNavigationActivity.this,
                        R.layout.dialog_download_progress, null);

                RecyclerView lNarrowTransferRecyclerView = lDownloadingDialogView.findViewById(R.id.narrow_transfer_recycler_view);
                lNarrowTransferRecyclerView.setLayoutManager(new LinearLayoutManager(FTPNavigationActivity.this));
                mNarrowTransferAdapter = new NarrowTransferAdapter(iPendingFiles);

                if (iPendingFiles.length > 1) {
                    DividerItemDecoration lDividerItemDecoration = new DividerItemDecoration(
                            FTPNavigationActivity.this, DividerItemDecoration.VERTICAL);
                    lNarrowTransferRecyclerView.addItemDecoration(lDividerItemDecoration);
                }

                lNarrowTransferRecyclerView.setAdapter(mNarrowTransferAdapter);

                lBuilder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface iDialog, int iWhich) {
                        iDialog.dismiss();
                        lFTPTransfer.abortTransfer();
                    }
                });

//                lBuilder.setNeutralButton("Background", new DialogInterface.OnClickListener() {
//                    @Override
//                    public void onClick(DialogInterface iDialog, int iWhich) {
//                        iDialog.dismiss();
//                    }
//                });

                lBuilder.setCancelable(false);
                lBuilder.setView(lDownloadingDialogView);
                lBuilder.setMessage("Downloading ..."); // TODO : strings
                mDownloadingDialog = lBuilder.create();
                mDownloadingDialog.show();
            }
        });

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                lFTPTransfer.downloadFiles(iPendingFiles, lFTPTransfer.new OnTransferListener() {
                    @Override
                    public void onConnected(PendingFile iPendingFile) {

                    }

                    @Override
                    public void onConnectionLost(PendingFile iPendingFile) {

                    }

                    @Override
                    public void onStartNewFile(final PendingFile iPendingFile) {
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                mNarrowTransferAdapter.updatePendingFileData(iPendingFile);
                            }
                        });
                    }

                    @Override
                    public void onDownloadProgress(final PendingFile iPendingFile, final long iProgress, final long iSize) {
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                mNarrowTransferAdapter.updatePendingFileData(iPendingFile);
                            }
                        });
                    }

                    @Override
                    public void onDownloadSuccess(PendingFile iPendingFile) {

                    }

                    @Override
                    public void onRightAccessFail(PendingFile iPendingFile) {

                    }

                    @Override
                    public void onFail(PendingFile iPendingFile) {

                    }

                    @Override
                    public void onStop() {

                    }
                });

            }
        });
    }

    private void createDialogDeleteSelection() {
        final FTPFile[] lSelectedFiles = mCurrentAdapter.getSelection();

        if (lSelectedFiles.length == 0)
            createDialogError("Select something.").show();
        else {
            mDeletingInfoDialog = new AlertDialog.Builder(this)
                    .setTitle("Deleting :") // TODO : Strings
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
        mFTPServices.deleteFiles(iSelectedFiles, mFTPServices.new OnDeleteListener() {

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
                        lBuilder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() { // TODO : Strings
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
                                mFTPServices.setDeletingByPassRightErrors(lCheckBox.isChecked());
                                mFTPServices.resumeDeleting();
                            }
                        });

                        lBuilder.setNegativeButton("Stop", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface iDialog, int iWhich) {
                                iDialog.dismiss();
                                mDeletingErrorDialog = null; // check at null used in NAVIGATION_MESSAGE_RECONNECT_SUCCESS
                                mCurrentAdapter.setSelectionMode(false);
                                mFTPServices.abortDeleting();
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
                                mFTPServices.setDeletingByPassFailErrors(lCheckBox.isChecked());
                                mFTPServices.resumeDeleting();
                            }
                        });

                        lBuilder.setNegativeButton("Stop", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface iDialog, int iWhich) {
                                iDialog.dismiss();
                                mDeletingErrorDialog = null;
                                mCurrentAdapter.setSelectionMode(false);
                                mFTPServices.abortDeleting();
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

    private AlertDialog createDialogError(String iMessage) { // TODO : Replace by resources
        mErrorAlertDialog = new AlertDialog.Builder(FTPNavigationActivity.this)
                .setTitle("Error") // TODO : string
                .setMessage(iMessage)
                .setCancelable(false)
                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface iDialog, int iWhich) {
                        iDialog.dismiss();
                    }
                })
                .create();
        return mErrorAlertDialog;
    }

    private void dismissAllDialogs() {
        if (mLoadingDialog != null)
            mLoadingDialog.cancel();
        if (mIndexingPendingFilesDialog != null)
            mIndexingPendingFilesDialog.cancel();
        if (mReconnectDialog != null)
            mReconnectDialog.cancel();
        if (mLargeDirDialog != null)
            mLargeDirDialog.cancel();
        if (mErrorAlertDialog != null)
            mErrorAlertDialog.cancel();
        if (mDownloadingDialog != null)
            mDownloadingDialog.cancel();
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

        mFTPServices = new FTPServices(mFTPServer);

        if (iIsRecovering)
            mLoadingDialog.setTitle("Reconnection..."); // TODO : strings
        else
            mLoadingDialog.setTitle("Connection..."); // TODO : strings
        mLoadingDialog.show();

        mFTPServices.connect(new AFTPConnection.OnConnectionResult() {
            @Override
            public void onSuccess() {
                mHandler.sendEmptyMessage(NAVIGATION_ORDER_DISMISS_DIALOGS);
                if (iRunFetchOnSuccess)
                    mHandler.sendEmptyMessage(NAVIGATION_MESSAGE_CONNECTION_SUCCESS);
            }

            @Override
            public void onFail(final AFTPConnection.ErrorCodeDescription iErrorEnum, int iErrorCode) {
                if (iErrorEnum == AFTPConnection.ErrorCodeDescription.ERROR_CONNECTION_INTERRUPTED)
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

    private abstract class OnPermissionAnswer {
        void onAccepted() {
        }

        void onDenied() {
        }
    }
}
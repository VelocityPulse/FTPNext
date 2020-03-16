package com.vpulse.ftpnext.ftpnavigation;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.TextInputLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.BounceInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.AutoCompleteTextView;
import android.widget.FrameLayout;

import com.vpulse.ftpnext.R;
import com.vpulse.ftpnext.adapters.NavigationRecyclerViewAdapter;
import com.vpulse.ftpnext.commons.Utils;
import com.vpulse.ftpnext.core.AppCore;
import com.vpulse.ftpnext.core.LogManager;
import com.vpulse.ftpnext.database.DataBase;
import com.vpulse.ftpnext.database.FTPServerTable.FTPServer;
import com.vpulse.ftpnext.database.FTPServerTable.FTPServerDAO;
import com.vpulse.ftpnext.ftpservices.AFTPConnection;
import com.vpulse.ftpnext.ftpservices.FTPServices;
import com.vpulse.ftpnext.ftpservices.FTPTransfer;

import org.apache.commons.net.ftp.FTPFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.vpulse.ftpnext.ftpnavigation.NavigationFetchDir.LARGE_DIRECTORY_SIZE;

public class FTPNavigationActivity extends AppCompatActivity {

    public static final int NO_DATABASE_ID = -1;
    public static final String ROOT_DIRECTORY = "/";
    public static final String KEY_DATABASE_ID = "KEY_DATABASE_ID";
    public static final String KEY_DIRECTORY_PATH = "KEY_DIRECTORY_PATH";

    protected static final int NAVIGATION_MESSAGE_CONNECTION_SUCCESS = 10;
    protected static final int NAVIGATION_MESSAGE_CONNECTION_FAIL = 11;
    protected static final int NAVIGATION_MESSAGE_CONNECTION_LOST = 12;
    protected static final int NAVIGATION_MESSAGE_RECONNECT_SUCCESS = 13;
    protected static final int NAVIGATION_MESSAGE_RECONNECT_FAIL = 14;
    protected static final int NAVIGATION_MESSAGE_CREATE_FOLDER_SUCCESS = 15;
    protected static final int NAVIGATION_MESSAGE_CREATE_FOLDER_FAIL = 16;
    protected static final int NAVIGATION_MESSAGE_NEW_DIRECTORY_SUCCESS_FETCH = 17;
    protected static final int NAVIGATION_MESSAGE_DIRECTORY_SUCCESS_UPDATE = 18;
    protected static final int NAVIGATION_MESSAGE_DIRECTORY_FAIL_FETCH = 19;
    protected static final int NAVIGATION_MESSAGE_DIRECTORY_FAIL_UPDATE = 20;

    protected static final int NAVIGATION_ORDER_DISMISS_DIALOGS = 100;
    protected static final int NAVIGATION_ORDER_DISMISS_LOADING_DIALOGS = 101;
    protected static final int NAVIGATION_ORDER_FETCH_DIRECTORY = 102;
    protected static final int NAVIGATION_ORDER_REFRESH_DATA = 103;
    protected static final int NAVIGATION_ORDER_SELECTED_MODE_ON = 104;
    protected static final int NAVIGATION_ORDER_SELECTED_MODE_OFF = 105;

    private static final String TAG = "FTP NAVIGATION ACTIVITY";
    private static final int ACTIVITY_REQUEST_CODE_READ_EXTERNAL_STORAGE = 1;
    private static final int ACTIVITY_REQUEST_CODE_SELECT_FILES = 2;

    protected boolean mIsShowingDownload;
    protected boolean mIsLargeDirectory;

    protected NavigationRecyclerViewAdapter mCurrentAdapter;

    protected ProgressDialog mLoadingDialog;
    protected ProgressDialog mCancelingDialog;
    protected ProgressDialog mLargeDirDialog;
    protected ProgressDialog mReconnectDialog;

    protected AlertDialog mErrorAlertDialog;
    protected AlertDialog mCreateFolderDialog;
    protected AlertDialog mIndexingPendingFilesDialog;
    protected AlertDialog mDownloadingDialog;
    protected AlertDialog mChooseExistingFileAction;
    protected AlertDialog mDeletingInfoDialog;
    protected AlertDialog mDeletingErrorDialog;

    protected Handler mHandler;
    protected boolean mWasOnPause;
    protected boolean mIsDirectoryFetchFinished;

    protected FTPServer mFTPServer;
    protected FTPServices mFTPServices;
    protected String mDirectoryPath;
    protected int mErrorCode;

    private boolean mIsRunning;

    private NavigationTransfer mNavigationTransfer;
    private NavigationFetchDir mNavigationFetchDir;
    private NavigationDelete mNavigationDelete;

    private OnPermissionAnswer mOnPermissionAnswer;
    private FrameLayout mRecyclerSection;

    private FloatingActionButton mMainFAB;
    private FloatingActionButton mCreateFolderFAB;
    private FloatingActionButton mUploadFileFAB;
    private boolean mIsFABOpen;

    @Override
    protected void onCreate(Bundle iSavedInstanceState) {
        LogManager.info(TAG, "On create");
        super.onCreate(iSavedInstanceState);
        setContentView(R.layout.activity_ftp_navigation);

        mIsRunning = true;
        initializeGUI();
        initializeHandler();
        initialize();
        retrieveFTPServices(false);
    }

    @Override
    protected void onResume() {
        LogManager.info(TAG, "On resume");
        super.onResume();

        LogManager.info(TAG, "Was on pause :" + mWasOnPause);
        if (mWasOnPause)
            retrieveFTPServices(true);
        mNavigationDelete.onResume();
        mNavigationFetchDir.onResume();
        mNavigationTransfer.onResume();
    }

    @Override
    protected void onPause() {
        LogManager.info(TAG, "On pause");
        mWasOnPause = true;
        super.onPause();
    }

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

    private void terminateNavigation() {
        mFTPServices.destroyConnection();

        if (mIsShowingDownload) {
            FTPTransfer[] lFTPTransfers = FTPTransfer.getFTPTransferInstance(mFTPServer.getDataBaseId());

            for (FTPTransfer lItem : lFTPTransfers) {
                lItem.destroyConnection();
            }
        }
        finish();
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
                    mNavigationDelete.createDialogDeleteSelection();
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
                                    onDownloadClicked();
                                }
                            });

                }
                onDownloadClicked();
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

    @Override
    protected void onActivityResult(int iRequestCode, int iResultCode, @Nullable Intent iData) {
        super.onActivityResult(iRequestCode, iResultCode, iData);

        if (iRequestCode == ACTIVITY_REQUEST_CODE_SELECT_FILES) {
            if (iResultCode == RESULT_OK) {
                List<Uri> lUriList = new ArrayList<>();
                if (iData.getData() == null) {
                    ClipData lCD = iData.getClipData();
                    int i = -1;
                    while (++i < lCD.getItemCount())
                        lUriList.add(lCD.getItemAt(i).getUri());
                } else
                    lUriList.add(iData.getData());
                if (lUriList.size() > 0)
                    mNavigationTransfer.
            }
        }
    }

    private void initialize() {
        FTPServerDAO lFTPServerDAO = DataBase.getFTPServerDAO();

        Bundle lBundle = this.getIntent().getExtras();

        // Server ID
        int lServerId = lBundle.getInt(KEY_DATABASE_ID);
        if (lServerId != NO_DATABASE_ID) {
            mFTPServer = lFTPServerDAO.fetchById(lServerId);
        } else {
            LogManager.error(TAG, "Server id is not initialized");
        }

        // FTPServer fetch
        mFTPServer = lFTPServerDAO.fetchById(lServerId);
        if (mFTPServer == null) {
            Utils.createErrorAlertDialog(this, "Navigation page has failed...").show();
            finish();
        }

        // Directory path
        if (mDirectoryPath == null)
            mDirectoryPath = lBundle.getString(KEY_DIRECTORY_PATH, ROOT_DIRECTORY);

        // Download procedures
        mNavigationTransfer = new NavigationTransfer(this, mHandler);

        // Fetch directory procedures
        mNavigationFetchDir = new NavigationFetchDir(this, mHandler);

        // Delete procedures
        mNavigationDelete = new NavigationDelete(this, mHandler);

        // Bad connection, Large dir, Reconnect dialog
        initializeDialogs();
    }

    private void retrieveFTPServices(final boolean iIsUpdating) {
        LogManager.info(TAG, "Retrieve FTP Services");

        mFTPServices = FTPServices.getFTPServicesInstance(mFTPServer.getDataBaseId());

        if (mFTPServices == null) {
            // Can happens on android studio apply changes
            LogManager.debug(TAG, "FTP CONNECTION NULL");
            buildFTPConnection();
        } else {
            LogManager.info(TAG, "FTP Services fully recovered by get instance");
            LogManager.info(TAG, "FTP Services instance busy : " + mFTPServices.isBusy());
            if (mFTPServices.isBusy())
                return;

            if (!AppCore.getNetworkManager().isNetworkAvailable()) {
                mHandler.sendEmptyMessage(NAVIGATION_MESSAGE_CONNECTION_LOST);
            } else if (!mFTPServices.isLocallyConnected()) {
                mHandler.sendEmptyMessage(NAVIGATION_MESSAGE_CONNECTION_LOST);
            } else {
                mFTPServices.isRemotelyConnected(new AFTPConnection.OnRemotelyConnectedResult() {
                    @Override
                    public void onResult(boolean iResult) {
                        if (iResult) {
                            if (!mIsShowingDownload)
                                mHandler.sendMessage(Message.obtain(
                                        mHandler,
                                        NAVIGATION_ORDER_FETCH_DIRECTORY,
                                        iIsUpdating));
                        } else
                            mHandler.sendEmptyMessage(NAVIGATION_MESSAGE_CONNECTION_LOST);
                    }
                });
            }
        }

        mFTPServices.setOnConnectionLost(new AFTPConnection.OnConnectionLost() {
            @Override
            public void onConnectionLost() {
                mHandler.sendEmptyMessage(NAVIGATION_MESSAGE_CONNECTION_LOST);
            }
        });
    }

    private void buildFTPConnection() {
        LogManager.info(TAG, "Build FTP Connection");
        if (mFTPServer == null) {
            LogManager.error(TAG, "mFTPServer is null");
            LogManager.error(TAG, Arrays.toString(new Exception("mFTPServer instance is null").getStackTrace()));
            return;
        }

        mFTPServices = new FTPServices(mFTPServer);

        mLoadingDialog.setTitle("Connection..."); // TODO : strings
        mLoadingDialog.show();

        mFTPServices.connect(new AFTPConnection.OnConnectionResult() {
            @Override
            public void onSuccess() {
                mHandler.sendEmptyMessage(NAVIGATION_ORDER_DISMISS_DIALOGS);
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
                        1,
                        0,
                        iErrorEnum));
            }
        });
    }

    private void initializeGUI() {
        getSupportActionBar().setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setCustomView(R.layout.action_bar_navigation);

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
                onCreateFolderClicked();
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

    private void initializeHandler() {
        mHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message iMsg) {
                AFTPConnection.ErrorCodeDescription lErrorDescription;
                FTPFile[] lFiles;

                if (!mIsRunning)
                    return;

                switch (iMsg.what) {

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

                    case NAVIGATION_ORDER_FETCH_DIRECTORY:
                        LogManager.info(TAG, "Handle : NAVIGATION_ORDER_FETCH_DIRECTORY");
                        boolean lIsUpdating = (boolean) iMsg.obj;
                        mNavigationFetchDir.runFetchProcedures(mDirectoryPath, mIsLargeDirectory, lIsUpdating);
                        break;

                    case NAVIGATION_ORDER_REFRESH_DATA:
                        LogManager.info(TAG, "Handle : NAVIGATION_ORDER_REFRESH_DATA");
                        if (!mFTPServices.isReconnecting() && mIsDirectoryFetchFinished) {
                            mCurrentAdapter.getSwipeRefreshLayout().setRefreshing(true);
                            mNavigationFetchDir.runFetchProcedures(mDirectoryPath, false, true);
                        }
                        break;

                    case NAVIGATION_MESSAGE_RECONNECT_SUCCESS:
                        LogManager.info(TAG, "Handle : NAVIGATION_MESSAGE_RECONNECT_SUCCESS");
                        dismissAllDialogsExcepted(mDownloadingDialog, mChooseExistingFileAction);
                        if (!mIsShowingDownload)
                            mNavigationFetchDir.runFetchProcedures(mDirectoryPath, mIsLargeDirectory, true);
                        break;

                    case NAVIGATION_MESSAGE_CONNECTION_SUCCESS:
                        LogManager.info(TAG, "Handle : NAVIGATION_MESSAGE_CONNECTION_SUCCESS");
                        mNavigationFetchDir.runFetchProcedures(mDirectoryPath, mIsLargeDirectory, true);
                        break;

                    case NAVIGATION_MESSAGE_CONNECTION_FAIL:
                        LogManager.info(TAG, "Handle : NAVIGATION_MESSAGE_CONNECTION_FAIL");
                        boolean lIsRecovering = iMsg.arg1 == 1;
                        lErrorDescription = (AFTPConnection.ErrorCodeDescription) iMsg.obj;
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
                        mFTPServices.abortDeleting();
                        dismissAllDialogsExcepted(
                                mDownloadingDialog,
                                mChooseExistingFileAction,
                                mReconnectDialog);

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
                        FTPFile lNewDirectory = (FTPFile) iMsg.obj;
                        mLoadingDialog.dismiss();
                        mCurrentAdapter.insertItem(lNewDirectory, 0);
                        mCurrentAdapter.getRecyclerView().scrollToPosition(0);
                        break;

                    case NAVIGATION_MESSAGE_CREATE_FOLDER_FAIL:
                        LogManager.info(TAG, "Handle : NAVIGATION_MESSAGE_CREATE_FOLDER_FAIL");
                        lErrorDescription = (AFTPConnection.ErrorCodeDescription) iMsg.obj;
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
                        dismissAllDialogs();
                        lErrorDescription = (AFTPConnection.ErrorCodeDescription) iMsg.obj;
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

                    case NAVIGATION_MESSAGE_DIRECTORY_SUCCESS_UPDATE:
                        LogManager.info(TAG, "Handle : NAVIGATION_MESSAGE_DIRECTORY_SUCCESS_UPDATE");
                        lFiles = (FTPFile[]) iMsg.obj;
                        if (mCurrentAdapter == null)
                            inflateNewAdapter(lFiles, mDirectoryPath, true);
                        else {
                            mCurrentAdapter.setData(lFiles);
                            mCurrentAdapter.appearVertically();
                            mCurrentAdapter.getSwipeRefreshLayout().setRefreshing(false);
                        }
                        break;

                    case NAVIGATION_MESSAGE_NEW_DIRECTORY_SUCCESS_FETCH:
                        LogManager.info(TAG, "Handle : NAVIGATION_MESSAGE_NEW_DIRECTORY_SUCCESS_FETCH");
                        lFiles = (FTPFile[]) iMsg.obj;
                        inflateNewAdapter(lFiles, mDirectoryPath, false);
                        break;

                    case NAVIGATION_MESSAGE_DIRECTORY_FAIL_FETCH:
                        LogManager.info(TAG, "Handle : NAVIGATION_MESSAGE_DIRECTORY_FAIL_FETCH");
                        mIsDirectoryFetchFinished = true;
                        lErrorDescription = (AFTPConnection.ErrorCodeDescription) iMsg.obj;
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
                }
            }
        };
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
                terminateNavigation();
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
                R.color.primaryLight,
                R.color.secondaryLight,
                R.color.primaryDark);

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
                        mNavigationFetchDir.runFetchProcedures(mDirectoryPath + "/" + iFTPFile.getName(), mIsLargeDirectory, false);
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

        if (iFTPFiles.length == 0)
            mCurrentAdapter.getOnFirstViewHolderCreation().onCreation();
    }

    private void createDialogFolderClicked() {
        final AlertDialog.Builder lBuilder = new AlertDialog.Builder(this);
        lBuilder.setTitle("Create new folder"); // TODO : strings

        View lTextSection = View.inflate(this, R.layout.dialog_create_folder, null);
        final TextInputLayout lTextInputLayout = lTextSection.findViewById(R.id.name_edit_text_layout);
        final AutoCompleteTextView lEditTextView = lTextSection.findViewById(R.id.name_edit_text);

        final String[] lNames = mCurrentAdapter.getNames();
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
                        for (String lItem : lNames) {
                            if (lString.equals(lItem)) {
                                lTextInputLayout.setError("Already used"); // TODO : Strings
                                mCreateFolderDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
                                return;
                            }
                        }

                        lTextInputLayout.setErrorEnabled(false);
                        mCreateFolderDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true);
                    } else {
                        lTextInputLayout.setError("Obligatory"); // TODO : Strings
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
        mCreateFolderDialog.show();
        mCreateFolderDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
        mCreateFolderDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
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

    private void onCreateFolderClicked() {
        FTPFile lEnclosingDirectory = mFTPServices.getCurrentDirectory();
        if (lEnclosingDirectory != null && !lEnclosingDirectory.hasPermission(FTPFile.USER_ACCESS, FTPFile.WRITE_PERMISSION)) {
            mErrorAlertDialog = new AlertDialog.Builder(FTPNavigationActivity.this)
                    .setTitle("Error") // TODO string
                    .setMessage("Creation has failed...\nYou don't have the permissions")
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

        createDialogFolderClicked();
    }

    private void onUploadFileClicked() {
        FTPFile lEnclosingDirectory = mFTPServices.getCurrentDirectory();
        if (lEnclosingDirectory != null && !lEnclosingDirectory.hasPermission(FTPFile.USER_ACCESS, FTPFile.WRITE_PERMISSION)) {
            mErrorAlertDialog = new AlertDialog.Builder(FTPNavigationActivity.this)
                    .setTitle("Error") // TODO string
                    .setMessage("Can't upload here\nYou don't have the permissions")
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

        Intent lIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        lIntent.setType("*/*");
        lIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        lIntent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
        startActivityForResult(lIntent, ACTIVITY_REQUEST_CODE_SELECT_FILES);
    }

    private void onDownloadClicked() {
        if (mCurrentAdapter.isInSelectionMode())
            mNavigationTransfer.createDialogDownloadSelection();
        else
            mCurrentAdapter.setSelectionMode(true);
    }

    public AlertDialog createDialogError(String iMessage) { // TODO : Replace by resources
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

    protected String getCurrentDirectoryPath() {
        String oCurrentDirectoryPath = mFTPServices.getCurrentDirectory().getName();
        if (oCurrentDirectoryPath.endsWith("/"))
            oCurrentDirectoryPath += "/";

        return oCurrentDirectoryPath;
    }

    protected void dismissAllDialogs() {
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
        if (mChooseExistingFileAction != null)
            mChooseExistingFileAction.cancel();
        if (mDeletingInfoDialog != null)
            mDeletingInfoDialog.cancel();
        if (mDeletingErrorDialog != null)
            mDeletingErrorDialog.cancel();
    }

    protected void dismissAllDialogsExcepted(Dialog... iToNotDismiss) {
        List lDialogList = Arrays.asList(iToNotDismiss);

        if (mLoadingDialog != null && lDialogList.contains(mLoadingDialog))
            mLoadingDialog.cancel();
        if (mIndexingPendingFilesDialog != null && !lDialogList.contains(mIndexingPendingFilesDialog))
            mIndexingPendingFilesDialog.cancel();
        if (mReconnectDialog != null && !lDialogList.contains(mReconnectDialog))
            mReconnectDialog.cancel();
        if (mLargeDirDialog != null && !lDialogList.contains(mLargeDirDialog))
            mLargeDirDialog.cancel();
        if (mErrorAlertDialog != null && !lDialogList.contains(mErrorAlertDialog))
            mErrorAlertDialog.cancel();
        if (mDownloadingDialog != null && !lDialogList.contains(mDownloadingDialog))
            mDownloadingDialog.cancel();
        if (mChooseExistingFileAction != null && !lDialogList.contains(mChooseExistingFileAction))
            mChooseExistingFileAction.cancel();
        if (mDeletingInfoDialog != null && !lDialogList.contains(mDeletingInfoDialog))
            mDeletingInfoDialog.cancel();
        if (mDeletingErrorDialog != null && !lDialogList.contains(mDeletingErrorDialog))
            mDeletingErrorDialog.cancel();
    }

    private abstract class OnPermissionAnswer {
        void onAccepted() {
        }

        void onDenied() {
        }
    }
}
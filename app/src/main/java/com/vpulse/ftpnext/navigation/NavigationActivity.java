package com.vpulse.ftpnext.navigation;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.BounceInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.vpulse.ftpnext.R;
import com.vpulse.ftpnext.adapters.NavigationRecyclerViewAdapter;
import com.vpulse.ftpnext.commons.Utils;
import com.vpulse.ftpnext.commons.interfaces.AfterTextChanged;
import com.vpulse.ftpnext.commons.interfaces.OnEndAnimation;
import com.vpulse.ftpnext.commons.interfaces.OnStartAnimation;
import com.vpulse.ftpnext.core.AppCore;
import com.vpulse.ftpnext.core.LogManager;
import com.vpulse.ftpnext.core.PreferenceManager;
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

import static com.vpulse.ftpnext.navigation.NavigationFetchDir.LARGE_DIRECTORY_SIZE;

public class NavigationActivity extends AppCompatActivity {

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

    protected static final int NAVIGATION_MESSAGE_DIRECTORY_SUCCESS_UPDATE = 17;
    protected static final int NAVIGATION_MESSAGE_DIRECTORY_SUCCESS_FETCH = 18;
    protected static final int NAVIGATION_MESSAGE_DIRECTORY_FAIL_UPDATE = 20;
    protected static final int NAVIGATION_MESSAGE_DIRECTORY_FAIL_FETCH = 19;

    protected static final int NAVIGATION_MESSAGE_TRANSFER_FINISHED = 21;
    protected static final int NAVIGATION_MESSAGE_DELETE_FINISHED = 23;

    protected static final int NAVIGATION_ORDER_DISMISS_DIALOGS = 100;
    protected static final int NAVIGATION_ORDER_DISMISS_LOADING_DIALOGS = 101;
    protected static final int NAVIGATION_ORDER_FETCH_DIRECTORY = 102;
    protected static final int NAVIGATION_ORDER_REFRESH_DATA = 103;
    protected static final int NAVIGATION_ORDER_SELECTED_MODE_ON = 104;
    protected static final int NAVIGATION_ORDER_SELECTED_MODE_OFF = 105;
    protected static final int NAVIGATION_ORDER_RECONNECT = 106;

    private static final String TAG = "FTP NAVIGATION ACTIVITY";
    private static final int ACTIVITY_REQUEST_CODE_READ_EXTERNAL_STORAGE = 1;
    private static final int ACTIVITY_REQUEST_CODE_SELECT_FILES = 2;

    protected NavigationRecyclerViewAdapter mCurrentAdapter;

    protected AlertDialog mCancelingDialog;
    protected AlertDialog mReconnectDialog;

    protected AlertDialog mFTPServiceConnectionDialog;
    protected AlertDialog mFetchDirLoadingDialog;
    protected AlertDialog mFetchLargeDirDialog;
    protected AlertDialog mErrorADialog;
    protected AlertDialog mSuccessDialog;
    protected AlertDialog mCreateFolderDialog;
    protected AlertDialog mIndexingPendingFilesDialog;
    protected AlertDialog mTransferDialog;
    protected AlertDialog mChooseExistingFileActionDialog;
    protected AlertDialog mDeletingInfoDialog;
    protected AlertDialog mDeletingErrorDialog;

    protected Handler mHandler;
    protected boolean mWasOnPause;
    protected boolean mIsDirectoryFetchFinished;
    protected boolean mIsShowingTransfer;
    protected boolean mIsLargeDirectory;

    protected FTPServer mFTPServer;
    protected FTPServices mFTPServices;
    protected String mDirectoryPath;
    protected int mErrorCode;

    private boolean mIsRunning;

    private NavigationTransfer mNavigationTransfer;
    private NavigationFetchDir mNavigationFetchDir;
    private NavigationDelete mNavigationDelete;
    private NavigationNewFolder mNavigationNewFolder;

    private OnPermissionAnswer mOnPermissionAnswer;
    private FrameLayout mRecyclerSection;

    private FloatingActionButton mMainFAB;
    private FloatingActionButton mCreateFolderFAB;
    private FloatingActionButton mUploadFileFAB;
    private TextInputEditText mSearchEditText;
    private TextInputLayout mSearchEditTextLayout;
    private LinearLayout mSearchBar;
    private Toolbar mToolBar;

    private boolean mIsFABOpen;
    private boolean mIsResumeFromActivityResult;
    private boolean mIsSearchDisplayed;

    @Override
    protected void onCreate(Bundle iSavedInstanceState) {
        LogManager.info(TAG, "On create");
        super.onCreate(iSavedInstanceState);
        if (PreferenceManager.isDarkTheme())
            setTheme(R.style.AppTheme_Dark);
        setContentView(R.layout.activity_navigation);

        mIsRunning = true;
        initializeGUI();
        initializeHandler();
        initialize();
        initializeFTPServices(false, false);
    }

    @Override
    protected void onResume() {
        LogManager.info(TAG, "On resume");
        super.onResume();

        LogManager.info(TAG, "Was on pause : " + mWasOnPause);
        if (mWasOnPause)
            initializeFTPServices(true, mIsResumeFromActivityResult);
        mIsResumeFromActivityResult = false;
        mNavigationDelete.onResume();
        mNavigationFetchDir.onResume();
        mNavigationTransfer.onResume();
        mNavigationNewFolder.onResume();
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

    @Override
    public void onBackPressed() {
        LogManager.info(TAG, "On back pressed");

        if (mFTPServices.isBusy())
            return;

        if (mIsFABOpen)
            closeFABMenu();

        if (mIsSearchDisplayed) {
            hideSearchBar();
            return;
        }

        if (mCurrentAdapter.isInSelectionMode()) {
            closeSelectionMode();
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
    public boolean onCreateOptionsMenu(final Menu iMenu) {
        getMenuInflater().inflate(R.menu.navigation, iMenu);

        return true;
    }

    private boolean showMenuMore(View iAnchor) {

        // Inflate the popup_layout.xml
        LayoutInflater layoutInflater = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View layout = layoutInflater.inflate(R.layout.action_navigation_more, mToolBar, false);

        // Creating the PopupWindow
        PopupWindow lMenuMorePopUp = new PopupWindow(layout,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true);

        layout.findViewById(R.id.popup_action_download).setOnClickListener((iView) -> {
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
            lMenuMorePopUp.dismiss();
        });

        layout.findViewById(R.id.popup_action_delete).setOnClickListener((iView) -> {
            onDeleteClicked();
            lMenuMorePopUp.dismiss();
        });


        lMenuMorePopUp.setWidth(LinearLayout.LayoutParams.WRAP_CONTENT);
        lMenuMorePopUp.setHeight(LinearLayout.LayoutParams.WRAP_CONTENT);
        lMenuMorePopUp.setFocusable(true);
        lMenuMorePopUp.showAsDropDown(iAnchor);

        return true;
    }

    private boolean showMenuSort(View iAnchor) {

        // Inflate the popup_layout.xml
        LayoutInflater layoutInflater = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View lLayout = layoutInflater.inflate(R.layout.action_navigation_sort, mToolBar, false);

        // Creating the PopupWindow
        PopupWindow lMenuSortPopUp = new PopupWindow(lLayout,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true);

        lLayout.findViewById(R.id.popup_action_a_to_z).setOnClickListener((iView) -> {
            lMenuSortPopUp.dismiss();
            mCurrentAdapter.setComparator(new NavigationRecyclerViewAdapter.AtoZComparator());
            mCurrentAdapter.notifyComparatorChanged();
        });

        lLayout.findViewById(R.id.popup_action_z_to_a).setOnClickListener((iView) -> {
            lMenuSortPopUp.dismiss();
            mCurrentAdapter.setComparator(new NavigationRecyclerViewAdapter.ZtoAComparator());
            mCurrentAdapter.notifyComparatorChanged();
        });

        lLayout.findViewById(R.id.popup_action_recent).setOnClickListener((iView) -> {
            lMenuSortPopUp.dismiss();
            mCurrentAdapter.setComparator(new NavigationRecyclerViewAdapter.RecentComparator());
            mCurrentAdapter.notifyComparatorChanged();
        });

        lMenuSortPopUp.setWidth(LinearLayout.LayoutParams.WRAP_CONTENT);
        lMenuSortPopUp.setHeight(LinearLayout.LayoutParams.WRAP_CONTENT);
        lMenuSortPopUp.setFocusable(true);
        lMenuSortPopUp.showAsDropDown(iAnchor);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem iItem) {
        switch (iItem.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
            case R.id.action_search:
                showSearchBar();
                return true;
            case R.id.action_more:
                showMenuMore(mToolBar.findViewById(R.id.action_more));
                return true;
            case R.id.action_choose_sort:
                showMenuSort(mToolBar.findViewById(R.id.action_choose_sort));
                return true;
            default:
                return super.onOptionsItemSelected(iItem);
        }
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

        // onActivityResult() is called before onResume()
        mIsResumeFromActivityResult = true;

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
                    mNavigationTransfer.createDialogUploadSelection(lUriList.toArray(new Uri[0]));
            }
        }
    }

    private void terminateNavigation() {
        mFTPServices.destroyConnection();

        if (mIsShowingTransfer) {
            FTPTransfer[] lFTPTransfers = FTPTransfer.getFTPTransferInstance(mFTPServer.getDataBaseId());

            for (FTPTransfer lItem : lFTPTransfers) {
                lItem.destroyConnection();
            }
        }
        finish();
    }

    private void requestPermission(Activity iActivity, final String[] iPermissions,
                                   int iRequestCode, OnPermissionAnswer iOnPermissionAnswer) {
        mOnPermissionAnswer = iOnPermissionAnswer;
        ActivityCompat.requestPermissions(this,
                iPermissions,
                iRequestCode);
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

        // New folder procedures
        mNavigationNewFolder = new NavigationNewFolder(this, mHandler);

        // Bad connection, Large dir, Reconnect dialog
        initializeDialogs();
    }

    private void initializeFTPServices(final boolean iIsUpdating,
                                       final boolean iBlockFetchDirIfSuccessRecovery) {
        LogManager.info(TAG, "Retrieve FTP Services");

        mFTPServices = FTPServices.getFTPServicesInstance(mFTPServer.getDataBaseId());

        if (mFTPServices == null) {
            // Can happens on android studio apply changes
            LogManager.error(TAG, "FTP CONNECTION NULL");
            Utils.showLongToast(this, "FTP CONNECTION NULL");
            initializeFTPConnection();
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
                mFTPServices.isRemotelyConnectedAsync(new AFTPConnection.OnRemotelyConnectedResult() {
                    @Override
                    public void onResult(boolean iResult) {
                        if (iResult) {
                            if (!mIsShowingTransfer && !iBlockFetchDirIfSuccessRecovery)
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

    private void initializeFTPConnection() {
        LogManager.info(TAG, "Build FTP Connection");
        if (mFTPServer == null) {
            LogManager.error(TAG, "mFTPServer is null");
            LogManager.error(TAG, Arrays.toString(new Exception("mFTPServer instance is null").getStackTrace()));
            return;
        }

        mFTPServices = new FTPServices(mFTPServer);

//        mFetchDirLoadingDialog.setTitle("Connection..."); // TODO : strings
//        mFetchDirLoadingDialog.show();

        mFTPServiceConnectionDialog = Utils.createProgressDialog(this,
                "Connection...",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface iDialog, int iWhich) {
                        iDialog.dismiss();
                        mFTPServices.destroyConnection();
                        finish();
                    }
                });

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
        mToolBar = findViewById(R.id.navigation_toolbar);
        setSupportActionBar(mToolBar);

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
                mNavigationNewFolder.createDialogNewFolder();
            }
        });
        mUploadFileFAB.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onUploadFileClicked();
            }
        });

        mRecyclerSection = findViewById(R.id.navigation_recycler_section);
        mSearchBar = findViewById(R.id.navigation_search_bar);

        mSearchEditTextLayout = findViewById(R.id.search_edit_text_layout);
        mSearchEditText = findViewById(R.id.search_edit_text);

        View lSearchBarHomeLayout = findViewById(R.id.navigation_home_layout);
        lSearchBarHomeLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });
        View lSearchBarHomeButton = findViewById(R.id.navigation_home_button);
        lSearchBarHomeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });

        mSearchEditTextLayout.setEndIconOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideSearchBar();
            }
        });
        mSearchEditText.addTextChangedListener(new AfterTextChanged() {
            @Override
            public void afterTextChanged(Editable s) {
                mCurrentAdapter.getFilter().filter(String.valueOf(s));
            }
        });

        final View lRootView = findViewById(android.R.id.content);
        lRootView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                Rect lMeasureRect = new Rect(); //you should cache this, onGlobalLayout can get called often
                lRootView.getWindowVisibleDisplayFrame(lMeasureRect);

                // lMeasureRect.bottom is the position above soft keypad
                int lKeypadHeight = lRootView.getRootView().getHeight() - lMeasureRect.bottom;

                if (lKeypadHeight > 0) {
                    // keyboard is opened
                } else {
                    // keyboard is closed
                    if (mIsSearchDisplayed)
                        onBackPressed();
                }
            }
        });
    }

    private void initializeHandler() {
        mHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(@NotNull Message iMsg) {
                AFTPConnection.ErrorCodeDescription lErrorDescription;
                FTPFile[] lFiles;

                if (!mIsRunning || isFinishing())
                    return;

                switch (iMsg.what) {

                    case NAVIGATION_ORDER_DISMISS_DIALOGS:
                        LogManager.info(TAG, "Handle : NAVIGATION_ORDER_DISMISS_DIALOGS");
                        dismissAllDialogs();
                        break;

                    case NAVIGATION_ORDER_DISMISS_LOADING_DIALOGS:
                        LogManager.info(TAG, "Handle : NAVIGATION_ORDER_DISMISS_LOADING_DIALOGS");
                        if (mFetchDirLoadingDialog != null)
                            mFetchDirLoadingDialog.dismiss();
                        if (mFetchLargeDirDialog != null)
                            mFetchLargeDirDialog.dismiss();
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

                    case NAVIGATION_ORDER_RECONNECT:
                        LogManager.info(TAG, "Handle : NAVIGATION_ORDER_RECONNECT");
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


                    case NAVIGATION_MESSAGE_RECONNECT_SUCCESS:
                        LogManager.info(TAG, "Handle : NAVIGATION_MESSAGE_RECONNECT_SUCCESS");
                        dismissAllDialogsExcepted(mTransferDialog, mChooseExistingFileActionDialog);
                        if (!mIsShowingTransfer)
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
                        new AlertDialog.Builder(NavigationActivity.this)
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
                                mTransferDialog,
                                mChooseExistingFileActionDialog,
                                mReconnectDialog);

                        if (!mIsShowingTransfer)
                            mHandler.sendEmptyMessage(NAVIGATION_ORDER_RECONNECT);
                        break;

                    case NAVIGATION_MESSAGE_TRANSFER_FINISHED:
                    case NAVIGATION_MESSAGE_DELETE_FINISHED:
                        LogManager.info(TAG, "Handle : NAVIGATION_MESSAGE_DOWNLOAD_FINISHED or " +
                                "NAVIGATION_MESSAGE_DELETE_FINISHED");
                        showFABMenu();

                        mFTPServices.isRemotelyConnectedAsync(new AFTPConnection.OnRemotelyConnectedResult() {
                            @Override
                            public void onResult(boolean iResult) {
                                if (!iResult) {
                                    dismissAllDialogs();
                                    mHandler.sendEmptyMessage(NAVIGATION_ORDER_RECONNECT);
                                } else {
                                    mHandler.sendEmptyMessage(NAVIGATION_ORDER_REFRESH_DATA);
                                }
                            }
                        });

                        break;

                    case NAVIGATION_MESSAGE_CREATE_FOLDER_SUCCESS:
                        LogManager.info(TAG, "Handle : NAVIGATION_MESSAGE_CREATE_FOLDER_SUCCESS");
                        // TODO : Sort items
                        FTPFile lNewDirectory = (FTPFile) iMsg.obj;
                        mFetchDirLoadingDialog.dismiss();
                        mCurrentAdapter.insertItemPersistently(lNewDirectory, 0);
                        mCurrentAdapter.getRecyclerView().scrollToPosition(0);
                        break;

                    case NAVIGATION_MESSAGE_CREATE_FOLDER_FAIL:
                        LogManager.info(TAG, "Handle : NAVIGATION_MESSAGE_CREATE_FOLDER_FAIL");
                        lErrorDescription = (AFTPConnection.ErrorCodeDescription) iMsg.obj;
                        mFetchDirLoadingDialog.dismiss();
                        if (mIsRunning && (mReconnectDialog == null || !mReconnectDialog.isShowing())) {
                            mErrorADialog = new AlertDialog.Builder(NavigationActivity.this)
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
                            mErrorADialog.show();
                        }
                        break;

                    case NAVIGATION_MESSAGE_RECONNECT_FAIL:
                        LogManager.info(TAG, "Handle : NAVIGATION_MESSAGE_RECONNECT_FAIL");
                        dismissAllDialogs();
                        lErrorDescription = (AFTPConnection.ErrorCodeDescription) iMsg.obj;
                        new AlertDialog.Builder(NavigationActivity.this)
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

                    case NAVIGATION_MESSAGE_DIRECTORY_SUCCESS_FETCH:
                        LogManager.info(TAG, "Handle : NAVIGATION_MESSAGE_NEW_DIRECTORY_SUCCESS_FETCH");
                        mHandler.sendEmptyMessage(NAVIGATION_ORDER_DISMISS_DIALOGS);
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
                            mErrorADialog = new AlertDialog.Builder(NavigationActivity.this)
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
                            mErrorADialog.show();
                        }
                        if (mCurrentAdapter != null &&
                                mCurrentAdapter.getSwipeRefreshLayout().isRefreshing())
                            mCurrentAdapter.getSwipeRefreshLayout().setRefreshing(false);
                        break;
                }
            }
        };
    }

    private void initializeDialogs() {
        mErrorADialog = Utils.createErrorAlertDialog(this, "");
        mSuccessDialog = Utils.createSuccessAlertDialog(this, "");

        // Loading dialog
        mFetchDirLoadingDialog = Utils.createProgressDialog(this, null);
        mFetchDirLoadingDialog.setTitle("Loading..."); // TODO : strings
        mFetchDirLoadingDialog.create();

        // Reconnection dialog
        mReconnectDialog = Utils.createProgressDialog(this, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                terminateNavigation();
            }
        });
        mReconnectDialog.setCancelable(false);
        mReconnectDialog.setTitle("Reconnection..."); // TODO : strings
        mReconnectDialog.create();
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

    protected void closeFABMenu() {
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

    protected void closeSelectionMode() {
        mCurrentAdapter.setSelectionMode(false);
        showFABMenu();
    }

    protected void hideFABMenu() {
        mMainFAB.hide();
        mCreateFolderFAB.hide();
        mUploadFileFAB.hide();
    }

    private void showFABMenu() {
        mMainFAB.show();
        mCreateFolderFAB.show();
        mUploadFileFAB.show();
    }

    private void showSearchBar() {
        if (mIsSearchDisplayed)
            return;

        mIsSearchDisplayed = true;
        Animation fadeOut = new AlphaAnimation(1, 0);
        fadeOut.setInterpolator(new DecelerateInterpolator()); //add this
        fadeOut.setDuration(200);
        fadeOut.setAnimationListener(new OnEndAnimation() {
            @Override
            public void onAnimationEnd(Animation animation) {
                mToolBar.setVisibility(View.INVISIBLE);
            }
        });

        mToolBar.startAnimation(fadeOut);
        Utils.showKeyboard(this, mSearchEditText);
        hideFABMenu();
    }

    private void hideSearchBar() {
        if (!mIsSearchDisplayed)
            return;

        mIsSearchDisplayed = false;
        Animation fadeIn = new AlphaAnimation(0, 1);
        fadeIn.setInterpolator(new DecelerateInterpolator()); //add this
        fadeIn.setDuration(200);
        fadeIn.setAnimationListener(new OnStartAnimation() {
            @Override
            public void onAnimationStart(Animation animation) {
                mToolBar.setVisibility(View.VISIBLE);
            }
        });
        mCurrentAdapter.getFilter().filter("");

        mToolBar.startAnimation(fadeIn);
        mHandler.postDelayed(() -> {
            Utils.hideKeyboard(this);
        }, 200);
        showFABMenu();
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

    private void inflateNewAdapter(FTPFile[] iFTPFiles, final String iDirectoryPath, final boolean iForceVerticalAppear) {
        LogManager.info(TAG, "Inflate new adapter");

        SwipeRefreshLayout lSwipeRefreshLayout = (SwipeRefreshLayout) View.inflate(this, R.layout.navigation_recycler_layout, null);
        lSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                mHandler.sendEmptyMessage(NAVIGATION_ORDER_REFRESH_DATA);
            }
        });
        lSwipeRefreshLayout.setColorSchemeResources(
                R.color.lightAccent,
                R.color.lightPrimary,
                R.color.lightPrimaryDark);

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

                    if (iFTPFile.isDirectory()) {
                        hideSearchBar();
                        lNewAdapter.setItemsClickable(false);
                        mIsLargeDirectory = iFTPFile.getSize() > LARGE_DIRECTORY_SIZE;
                        mNavigationFetchDir.runFetchProcedures(
                                iDirectoryPath + iFTPFile.getName() + "/", mIsLargeDirectory, false);
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
                        closeFABMenu();

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

    private void onUploadFileClicked() {
        FTPFile lEnclosingDirectory = mFTPServices.getCurrentDirectory();
        if (lEnclosingDirectory != null && !lEnclosingDirectory.hasPermission(FTPFile.USER_ACCESS, FTPFile.WRITE_PERMISSION)) {
            String lErrorMessage = "Can't upload here\nYou don't have the permissions";
            createDialogError(lErrorMessage).show();
            return;
        }

        Intent lIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        lIntent.setType("*/*");
        lIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        lIntent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);

        startActivityForResult(lIntent, ACTIVITY_REQUEST_CODE_SELECT_FILES);
    }

    private void onDeleteClicked() {
        if (mCurrentAdapter.isInSelectionMode()) {
            if (mCurrentAdapter.getSelection().length == 0) {
                showFABMenu();
                mCurrentAdapter.setSelectionMode(false);
            } else
                mNavigationDelete.createDialogDeleteSelection();
        } else {
            hideFABMenu();
            mCurrentAdapter.setSelectionMode(true);
        }
    }

    private void onDownloadClicked() {
        if (mCurrentAdapter.isInSelectionMode()) {
            if (mCurrentAdapter.getSelection().length == 0) {
                showFABMenu();
                mCurrentAdapter.setSelectionMode(false);
            } else
                mNavigationTransfer.createDialogDownloadSelection();
        } else {
            hideFABMenu();
            mCurrentAdapter.setSelectionMode(true);
        }
    }

    protected AlertDialog createDialogError(String iMessage) { // TODO : Replace by resources ID
        mErrorADialog = new AlertDialog.Builder(NavigationActivity.this)
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
        return mErrorADialog;
    }

    protected void dismissAllDialogs() {
        if (mErrorADialog != null)
            mErrorADialog.cancel();
        if (mSuccessDialog != null)
            mSuccessDialog.cancel();
        if (mFetchDirLoadingDialog != null)
            mFetchDirLoadingDialog.cancel();
        if (mIndexingPendingFilesDialog != null)
            mIndexingPendingFilesDialog.cancel();
        if (mReconnectDialog != null)
            mReconnectDialog.cancel();
        if (mFetchLargeDirDialog != null)
            mFetchLargeDirDialog.cancel();
        if (mTransferDialog != null)
            mTransferDialog.cancel();
        if (mChooseExistingFileActionDialog != null)
            mChooseExistingFileActionDialog.cancel();
        if (mDeletingInfoDialog != null)
            mDeletingInfoDialog.cancel();
        if (mDeletingErrorDialog != null)
            mDeletingErrorDialog.cancel();
        if (mFTPServiceConnectionDialog != null)
            mFTPServiceConnectionDialog.cancel();
    }

    protected void dismissAllDialogsExcepted(DialogInterface... iToNotDismiss) {
        List lDialogList = Arrays.asList(iToNotDismiss);

        if (mErrorADialog != null && !lDialogList.contains(mErrorADialog))
            mErrorADialog.cancel();
        if (mSuccessDialog != null && !lDialogList.contains(mSuccessDialog))
            mSuccessDialog.cancel();
        if (mFetchDirLoadingDialog != null && lDialogList.contains(mFetchDirLoadingDialog))
            mFetchDirLoadingDialog.cancel();
        if (mIndexingPendingFilesDialog != null && !lDialogList.contains(mIndexingPendingFilesDialog))
            mIndexingPendingFilesDialog.cancel();
        if (mReconnectDialog != null && !lDialogList.contains(mReconnectDialog))
            mReconnectDialog.cancel();
        if (mFetchLargeDirDialog != null && !lDialogList.contains(mFetchLargeDirDialog))
            mFetchLargeDirDialog.cancel();
        if (mTransferDialog != null && !lDialogList.contains(mTransferDialog))
            mTransferDialog.cancel();
        if (mChooseExistingFileActionDialog != null && !lDialogList.contains(mChooseExistingFileActionDialog))
            mChooseExistingFileActionDialog.cancel();
        if (mDeletingInfoDialog != null && !lDialogList.contains(mDeletingInfoDialog))
            mDeletingInfoDialog.cancel();
        if (mDeletingErrorDialog != null && !lDialogList.contains(mDeletingErrorDialog))
            mDeletingErrorDialog.cancel();
        if (mFTPServiceConnectionDialog != null && !lDialogList.contains(mFTPServiceConnectionDialog))
            mFTPServiceConnectionDialog.cancel();
    }

    private abstract class OnPermissionAnswer {
        void onAccepted() {
        }

        void onDenied() {
        }
    }
}
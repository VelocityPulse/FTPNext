package com.vpulse.ftpnext.navigation;

import android.content.DialogInterface;
import android.os.Handler;
import android.os.Message;

import androidx.appcompat.app.AlertDialog;

import com.vpulse.ftpnext.commons.Utils;
import com.vpulse.ftpnext.core.LogManager;
import com.vpulse.ftpnext.ftpservices.AFTPConnection;
import com.vpulse.ftpnext.ftpservices.FTPServices;

import org.apache.commons.net.ftp.FTPFile;

import java.util.Arrays;

import static com.vpulse.ftpnext.navigation.NavigationActivity.NAVIGATION_MESSAGE_DIRECTORY_FAIL_FETCH;
import static com.vpulse.ftpnext.navigation.NavigationActivity.NAVIGATION_MESSAGE_DIRECTORY_SUCCESS_FETCH;
import static com.vpulse.ftpnext.navigation.NavigationActivity.NAVIGATION_MESSAGE_DIRECTORY_SUCCESS_UPDATE;
import static com.vpulse.ftpnext.navigation.NavigationActivity.NAVIGATION_ORDER_DISMISS_LOADING_DIALOGS;

public class NavigationFetchDir {

    protected static final int LARGE_DIRECTORY_SIZE = 30000;

    private final static String TAG = "NAVIGATION FETCH DIR";

    private static final int BAD_CONNECTION_TIME = 50;

    private final NavigationActivity mContextActivity;
    private final Handler mHandler;

    private NavigationFetchDir() throws InstantiationException {
        mContextActivity = null;
        mHandler = null;
        throw new InstantiationException("Constructor not allowed");
    }

    protected NavigationFetchDir(NavigationActivity iContextActivity, Handler iHandler) {
        mContextActivity = iContextActivity;
        mHandler = iHandler;
        initializeDialogs();
    }

    protected void onResume() {

    }

    private void initializeDialogs() {
        // Canceling dialog
        mContextActivity.mCancelingDialog = Utils.initProgressDialogNoButton(mContextActivity);
        mContextActivity.mCancelingDialog.setTitle("Canceling..."); //TODO : strings
        mContextActivity.mCancelingDialog.create(); // TODO test if necessary

        // Large directory dialog
        mContextActivity.mLargeDirDialog = Utils.initProgressDialog(mContextActivity, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface iDialog, int iWhich) {
                iDialog.dismiss();
                mContextActivity.mCancelingDialog.show();
                mContextActivity.mFTPServices.abortFetchDirectoryContent();
            }
        });
        mContextActivity.mLargeDirDialog.setCancelable(false);
        mContextActivity.mLargeDirDialog.setTitle("Large directory"); // TODO : strings
        mContextActivity.mLargeDirDialog.create();
    }

    protected void runFetchProcedures(final String iDirectoryPath, boolean iIsLargeDirectory,
                                      final boolean isForAnUpdate) {
        LogManager.info(TAG, "Run fetch procedures for : \"" + iDirectoryPath + "\"");

        mContextActivity.dismissAllDialogs();
        mContextActivity.mErrorADialog = null;
        mContextActivity.mIsDirectoryFetchFinished = false;

        if (mContextActivity.mFTPServices == null) {
            LogManager.error(TAG, "AFTPConnection instance is null");
            LogManager.error(TAG, Arrays.toString(new Exception("AFTPConnection instance is null").getStackTrace()));
            new AlertDialog.Builder(mContextActivity)
                    .setTitle("Error") // TODO string
                    .setMessage("Error unknown")
                    .setCancelable(false)
                    .setPositiveButton("Terminate", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            mContextActivity.finish();
                        }
                    })
                    .create()
                    .show();
            return;
        }

        if (iIsLargeDirectory)
            mContextActivity.mLargeDirDialog.show();

        // Waiting fetch stop
        if (mContextActivity.mFTPServices.isFetchingFolders()) { // if another activity didn't stop its fetch yet
            new Thread(new Runnable() {
                @Override
                public void run() {
                    while (mContextActivity.mFTPServices.isFetchingFolders()) {
                        try {
                            LogManager.info(TAG, "Waiting fetch stopping");
                            Thread.sleep(150);
                        } catch (InterruptedException iE) {
                            iE.printStackTrace();
                        }
                    }
                    initFetchDirectoryContent(iDirectoryPath, isForAnUpdate);
                }
            }).start();
        } else
            initFetchDirectoryContent(iDirectoryPath, isForAnUpdate);
    }


    private void initFetchDirectoryContent(final String iDirectoryPath, final boolean iIsForAnUpdate) {

        //Bad Connection
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                // in case if dialog has been canceled
                if (!mContextActivity.mIsDirectoryFetchFinished &&
                        (mContextActivity.mLargeDirDialog == null ||
                                !mContextActivity.mLargeDirDialog.isShowing())) {

                    if (mContextActivity.mCurrentAdapter != null &&
                            mContextActivity.mCurrentAdapter.getSwipeRefreshLayout().isRefreshing())
                        return;

                    if (!mContextActivity.mIsDirectoryFetchFinished) {
                        mContextActivity.mLoadingDialog.setTitle("Loading..."); //TODO : strings
                        mContextActivity.mLoadingDialog.show();
                    }
                }
            }
        }, BAD_CONNECTION_TIME);

        mContextActivity.mFTPServices.fetchDirectoryContent(iDirectoryPath, new FTPServices.IOnFetchDirectoryResult() {
            @Override
            public void onSuccess(final FTPFile[] iFTPFiles) {
                mContextActivity.mDirectoryPath = iDirectoryPath;
                mContextActivity.mIsDirectoryFetchFinished = true;
                mHandler.sendEmptyMessage(NAVIGATION_ORDER_DISMISS_LOADING_DIALOGS);
                if (iIsForAnUpdate) {
                    mHandler.sendMessage(Message.obtain(
                            mHandler,
                            NAVIGATION_MESSAGE_DIRECTORY_SUCCESS_UPDATE,
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
                mContextActivity.mErrorCode = iErrorCode;
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
                        if (mContextActivity.mCancelingDialog != null && mContextActivity.mCancelingDialog.isShowing())
                            mContextActivity.mCancelingDialog.dismiss();
                        mContextActivity.mCurrentAdapter.setItemsClickable(true);
                    }
                });
            }
        });
    }
}
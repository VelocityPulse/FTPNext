package com.vpulse.ftpnext.navigation

import android.os.Handler
import android.os.Message
import androidx.appcompat.app.AlertDialog
import com.vpulse.ftpnext.commons.Utils
import com.vpulse.ftpnext.core.LogManager
import com.vpulse.ftpnext.ftpservices.AFTPConnection.ErrorCodeDescription
import com.vpulse.ftpnext.ftpservices.FTPServices.IOnFetchDirectoryResult
import org.apache.commons.net.ftp.FTPFile
import java.util.*

class NavigationFetchDir(iContextActivity: NavigationActivity?, iHandler: Handler?) {

    private val mContextActivity: NavigationActivity? = iContextActivity
    private val mHandler: Handler? = iHandler

    init {
        initializeDialogs()
    }

    fun onResume() {}
    private fun initializeDialogs() { // Canceling dialog
        mContextActivity!!.mCancelingDialog = Utils.createProgressDialog(
            mContextActivity, "Canceling...", "Terminate"
        ) { _, _ -> mContextActivity.finish() }
        mContextActivity.mCancelingDialog!!.setTitle("Canceling...") //TODO : strings
        mContextActivity.mCancelingDialog!!.create() // TODO test if necessary

        // Large directory dialog
        mContextActivity.mFetchLargeDirDialog = Utils.createProgressDialog(
            mContextActivity
        ) { iDialog, iWhich ->
            iDialog.dismiss()
            if (mContextActivity.mFTPServices!!.isFetchingFolders) {
                mContextActivity.mCancelingDialog!!.show()
                mContextActivity.mFTPServices!!.abortFetchDirectoryContent()
            }
        }
        mContextActivity.mFetchLargeDirDialog!!.setCancelable(false)
        mContextActivity.mFetchLargeDirDialog!!.setTitle("Large directory") // TODO : strings
        mContextActivity.mFetchLargeDirDialog!!.create()
    }

    fun runFetchProcedures(iDirectoryPath: String?,
                           iIsLargeDirectory: Boolean,
                           isForAnUpdate: Boolean
    ) {
        LogManager.info(TAG, "Run fetch procedures for : \"$iDirectoryPath\"")
        mContextActivity!!.dismissAllDialogs()
        mContextActivity.mErrorADialog = null
        mContextActivity.mIsDirectoryFetchFinished = false
        if (mContextActivity.mFTPServices == null) {
            LogManager.error(TAG, "AFTPConnection instance is null")
            LogManager.error(
                TAG, Arrays.toString(Exception("AFTPConnection instance is null").stackTrace)
            )
            AlertDialog.Builder((mContextActivity)).setTitle("Error") // TODO string
                .setMessage("Error unknown").setCancelable(false).setPositiveButton(
                    "Terminate"
                ) { dialog, which ->
                    dialog.dismiss()
                    mContextActivity.finish()
                }.create().show()
            return
        }
        if (iIsLargeDirectory) mContextActivity.mFetchLargeDirDialog!!.show()

        // Waiting fetch stop
        if (mContextActivity.mFTPServices!!.isFetchingFolders) { // if another activity didn't stop its fetch yet
            Thread(object : Runnable {
                override fun run() {
                    while (mContextActivity.mFTPServices!!.isFetchingFolders) {
                        try {
                            LogManager.info(TAG, "Waiting fetch stopping")
                            Thread.sleep(150)
                        } catch (iE: InterruptedException) {
                            iE.printStackTrace()
                        }
                    }
                    initFetchDirectoryContent(iDirectoryPath, isForAnUpdate)
                }
            }).start()
        } else initFetchDirectoryContent(iDirectoryPath, isForAnUpdate)
    }

    private fun initFetchDirectoryContent(iDirectoryPath: String?, iIsForAnUpdate: Boolean) {

        //Bad Connection
        mHandler!!.postDelayed(object : Runnable {
            override fun run() { // in case if dialog has been canceled
                if (!mContextActivity!!.mIsDirectoryFetchFinished && (mContextActivity.mFetchLargeDirDialog == null || !mContextActivity.mFetchLargeDirDialog!!.isShowing)) {
                    if (mContextActivity.mCurrentAdapter != null && mContextActivity.mCurrentAdapter!!.swipeRefreshLayout.isRefreshing) return
                    if (!mContextActivity.mIsDirectoryFetchFinished) {
                        mContextActivity.mFetchDirLoadingDialog = Utils.createProgressDialog(
                            mContextActivity, "Loading"
                        ) { iDialog, iWhich ->
                            iDialog.dismiss()
                            mContextActivity.mCancelingDialog!!.show()
                            mContextActivity.mFTPServices!!.abortFetchDirectoryContent()
                        }
                        mContextActivity.mFetchDirLoadingDialog!!.show()
                    }
                }
            }
        }, BAD_CONNECTION_TIME.toLong())
        mContextActivity!!.mFTPServices!!.fetchDirectoryContent(
            iDirectoryPath,
            object : IOnFetchDirectoryResult {
                override fun onSuccess(iFTPFiles: Array<FTPFile?>?) {
                    mContextActivity.mDirectoryPath = iDirectoryPath
                    mContextActivity.mIsDirectoryFetchFinished = true
                    mHandler.sendEmptyMessage(NavigationActivity.Companion.NAVIGATION_ORDER_DISMISS_LOADING_DIALOGS)
                    if (iIsForAnUpdate) {
                        mHandler.sendMessage(
                            Message.obtain(
                                mHandler,
                                NavigationActivity.Companion.NAVIGATION_MESSAGE_DIRECTORY_SUCCESS_UPDATE,
                                iFTPFiles
                            )
                        )
                    } else mHandler.sendMessage(
                        Message.obtain(
                            mHandler,
                            NavigationActivity.Companion.NAVIGATION_MESSAGE_DIRECTORY_SUCCESS_FETCH,
                            iFTPFiles
                        )
                    )
                }

                override fun onFail(iErrorEnum: ErrorCodeDescription?, iErrorCode: Int) {
                    mContextActivity.mErrorCode = iErrorCode
                    mHandler.sendEmptyMessage(NavigationActivity.Companion.NAVIGATION_ORDER_DISMISS_LOADING_DIALOGS)
                    mHandler.sendMessage(
                        Message.obtain(
                            mHandler,
                            NavigationActivity.Companion.NAVIGATION_MESSAGE_DIRECTORY_FAIL_FETCH,
                            iErrorEnum
                        )
                    )
                }

                override fun onInterrupt() {
                    mHandler.post(Runnable {
                        if (mContextActivity.mCancelingDialog != null && mContextActivity.mCancelingDialog!!.isShowing) {
                            mContextActivity.mCancelingDialog!!.dismiss()
                        }
                        if (mContextActivity.mCurrentAdapter == null) {
                            mContextActivity.finish()
                        } else {
                            mContextActivity.mCurrentAdapter!!.setItemsClickable(true)
                        }
                    })
                }
            })
    }

    companion object {
        val LARGE_DIRECTORY_SIZE: Int = 30000
        private val TAG: String = "NAVIGATION FETCH DIR"
        private val BAD_CONNECTION_TIME: Int = 50
    }
}
package com.vpulse.ftpnext.navigation

import android.content.DialogInterface
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.app.AlertDialog
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.core.text.HtmlCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.vpulse.ftpnext.R
import com.vpulse.ftpnext.adapters.NarrowTransferAdapter
import com.vpulse.ftpnext.commons.FileUtils
import com.vpulse.ftpnext.commons.Utils
import com.vpulse.ftpnext.core.*
import com.vpulse.ftpnext.database.DataBase
import com.vpulse.ftpnext.database.PendingFileTable.PendingFile
import com.vpulse.ftpnext.ftpservices.FTPLogManager
import com.vpulse.ftpnext.ftpservices.FTPLogManager.OnNewFTPLogColored
import com.vpulse.ftpnext.ftpservices.FTPServices.OnIndexingPendingFilesListener
import com.vpulse.ftpnext.ftpservices.FTPTransfer
import com.vpulse.ftpnext.ftpservices.FTPTransfer.OnTransferListener
import org.apache.commons.net.ftp.FTPFile
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe

class NavigationTransfer {
    private val mFTPTransferList: ArrayList<FTPTransfer>?
    private val mContextActivity: NavigationActivity?
    private val mUniversalTransferListener: OnTransferListener?
    private val mHandler: Handler?
    private var mNarrowTransferAdapter: NarrowTransferAdapter? = null
    private var mCanAutoScrollInLogView = false
    private lateinit var mPendingFiles: Array<PendingFile>
    private var mPendingFileErrors = 0

    constructor(iContextActivity: NavigationActivity?, iHandler: Handler?) {
        mHandler = iHandler
        mContextActivity = iContextActivity
        mFTPTransferList = ArrayList()
        mUniversalTransferListener = initializeUniversalTransferListener()
        EventBus.getDefault().register(this)
    }

    @Subscribe
    fun onEvent(iMessageEvent: MessageEvent?) {
    }

    fun onResume() {
        if (mFTPTransferList!!.size > 0 && mContextActivity!!.mIsShowingTransfer) {
            mNarrowTransferAdapter!!.notifyDataSetChanged()
        }
    }

    fun onDestroy() {
        EventBus.getDefault().unregister(this)
    }

    fun createDialogUploadSelection(iUris: Array<Uri>) {
        if (iUris.isEmpty()) {
            mContextActivity!!.createDialogError("Select something").show()
        } else {
            showUploadConfirmation(iUris)
        }
    }

    fun createDialogDownloadSelection() {
        val lSelectedFiles = mContextActivity!!.mCurrentAdapter!!.selection
        if (lSelectedFiles.size == 0) mContextActivity.createDialogError("Select something.").show() else showDownloadConfirmation(lSelectedFiles)
    }

    private fun indexFilesForDownload(iSelectedFiles: Array<FTPFile>) {
        LogManager.info(TAG, "Download file")
        mHandler!!.sendEmptyMessage(NavigationActivity.NAVIGATION_ORDER_SELECTED_MODE_OFF)
        mContextActivity!!.mFTPServices!!.indexingPendingFilesProcedure(iSelectedFiles, object : OnIndexingPendingFilesListener {
            var mIndexingFolderText: TextView? = null
            var mIndexingFileText: TextView? = null
            override fun onStart() {
                LogManager.info(TAG, "Indexing listener : On start")
                mHandler.post {
                    val lBuilder = AlertDialog.Builder(mContextActivity)
                    val lIndexingPendingFilesView = View.inflate(mContextActivity,
                            R.layout.dialog_indexing_progress, null)
                    mIndexingFolderText = lIndexingPendingFilesView.findViewById(R.id.dialog_indexing_folder)
                    mIndexingFileText = lIndexingPendingFilesView.findViewById(R.id.dialog_indexing_file)
                    lBuilder.setNegativeButton("Cancel") { iDialog, _ ->
                        iDialog.dismiss()
                        mContextActivity.mFTPServices!!.abortIndexingPendingFiles()
                        mContextActivity.mHandler!!.sendEmptyMessage(NavigationActivity.NAVIGATION_MESSAGE_TRANSFER_FINISHED)
                    }
                    lBuilder.setCancelable(false)
                    lBuilder.setView(lIndexingPendingFilesView)
                    lBuilder.setTitle("Indexing files :") // TODO : strings
                    mContextActivity.mIndexingPendingFilesDialog = lBuilder.create()
                    mContextActivity.mIndexingPendingFilesDialog!!.show()
                }
            }

            override fun onFetchingFolder(iPath: String?) {
                mHandler.post { mIndexingFolderText!!.text = iPath }
            }

            override fun onNewIndexedFile(iPendingFile: PendingFile?) {
                mHandler.post { mIndexingFileText!!.text = iPendingFile!!.name }
            }

            override fun onResult(isSuccess: Boolean, iPendingFiles: Array<PendingFile>?) {
                LogManager.info(TAG, "Indexing : On result")
                if (!isSuccess) return
                DataBase.pendingFileDAO!!.deleteAll() // TODO : DATA BASE RESET HERE
                DataBase.pendingFileDAO!!.add(iPendingFiles)
                if (mContextActivity.mIndexingPendingFilesDialog != null) {
                    mHandler.post { mContextActivity!!.mIndexingPendingFilesDialog!!.cancel() }
                }
                downloadFiles(iPendingFiles!!)
            }
        })
    }

    private fun indexFilesForUpload(iUris: Array<Uri>): Array<PendingFile> {
        val lPendingFileList: MutableList<PendingFile> = ArrayList()
        for (lItem in iUris) {
//            String lLocalAbsolutePath = Utils.getRealPathFromURI(mContextActivity, lItem);
            val lLocalAbsolutePath = FileUtils.getPathFromDocumentUri(mContextActivity as Context, lItem)
            var lNameOnly: String?
            var lLocalPathOnly: String?
            var lRemotePathOnly: String?
            if (lLocalAbsolutePath!!.contains("/")) {
                lNameOnly = lLocalAbsolutePath.substring(lLocalAbsolutePath.lastIndexOf("/") + 1)
                lLocalPathOnly = lLocalAbsolutePath.substring(0, lLocalAbsolutePath.lastIndexOf("/"))
                lLocalPathOnly += "/"
            } else {
                lNameOnly = lLocalAbsolutePath
                lLocalPathOnly = lLocalAbsolutePath
            }
            lRemotePathOnly = mContextActivity!!.mFTPServices!!.currentDirectoryPath
            lPendingFileList.add(PendingFile(
                    mContextActivity.mFTPServer!!.dataBaseId,
                    LoadDirection.UPLOAD,
                    false,
                    lNameOnly,
                    lRemotePathOnly,
                    lLocalPathOnly,
                    PreferenceManager.existingFileAction
            ))
        }
        return lPendingFileList.toTypedArray()
    }

    private fun uploadFiles(iUris: Array<Uri>) {
        LogManager.info(TAG, "Upload files")
        destroyAllTransferConnections()
        mPendingFiles = indexFilesForUpload(iUris)
        mPendingFileErrors = 0
        createNarrowTransferDialog(mPendingFiles, LoadDirection.UPLOAD)
        var lI = -1
        val lMaxSimultaneousDownload = PreferenceManager.maxTransfers
        while (++lI < lMaxSimultaneousDownload && lI < mPendingFiles.size) createNewFTPTransfer(LoadDirection.UPLOAD)
    }

    private fun downloadFiles(iPendingFiles: Array<PendingFile>) {
        LogManager.info(TAG, "Download files")
        destroyAllTransferConnections()
        mPendingFiles = iPendingFiles
        mPendingFileErrors = 0
        createNarrowTransferDialog(mPendingFiles, LoadDirection.DOWNLOAD)
        var lI = -1
        val lMaxSimultaneousDownload = PreferenceManager.maxTransfers
        while (++lI < lMaxSimultaneousDownload && lI < mPendingFiles.size) createNewFTPTransfer(LoadDirection.DOWNLOAD)
    }

    private fun createNarrowTransferDialog(iPendingFiles: Array<PendingFile>, iLoadDirection: LoadDirection) {
        mHandler!!.post {
            val lBuilder = AlertDialog.Builder(mContextActivity!!)
            val lDownloadingDialogView = View.inflate(mContextActivity,
                    R.layout.dialog_download_progress, null)
            val lNarrowTransferRecyclerView = lDownloadingDialogView.findViewById<RecyclerView>(R.id.narrow_transfer_recycler_view)
            lNarrowTransferRecyclerView.layoutManager = LinearLayoutManager(mContextActivity)
            val lSortButton = lDownloadingDialogView.findViewById<Button>(R.id.narrow_transfer_recycler_button_sort)
            val lLogView = lDownloadingDialogView.findViewById<TextView>(R.id.narrow_transfer_log_view)
            val lScrollView = lDownloadingDialogView.findViewById<ScrollView>(R.id.narrow_transfer_scroll_view)
            lScrollView.isSmoothScrollingEnabled = true
            mCanAutoScrollInLogView = true
            lLogView.setOnTouchListener { v, event ->
                if (event.action == MotionEvent.ACTION_DOWN) mCanAutoScrollInLogView = false
                false
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                lScrollView.setOnScrollChangeListener { iV, iScrollX, iScrollY, iOldScrollX, iOldScrollY ->
                    if (!lScrollView.canScrollVertically(1)) {
                        mCanAutoScrollInLogView = true
                    }
                }
            }
            val lOnNewFTPLogColored = object : OnNewFTPLogColored {
                var mCount = 0
                var mCompleteLog = ""

                override fun onNewFTPLogColored(iLog: String?) {
                    mCount++
                    if (mCount > 150) mCompleteLog = mCompleteLog.substring(mCompleteLog.indexOf("<br/>") + 5)
                    mCompleteLog += "<br/>$iLog"
                    val s = HtmlCompat.fromHtml(mCompleteLog, HtmlCompat.FROM_HTML_MODE_LEGACY)
                    mHandler.post {
                        lLogView.text = s
                        if (mCanAutoScrollInLogView) lScrollView.fullScroll(ScrollView.FOCUS_DOWN)
                    }
                }
            }
            FTPLogManager.subscribeOnNewFTPLogColored(lOnNewFTPLogColored)
            if (mPendingFiles.size > 1) {
                val lDividerItemDecoration = DividerItemDecoration(
                        mContextActivity, DividerItemDecoration.VERTICAL)
                lNarrowTransferRecyclerView.addItemDecoration(lDividerItemDecoration)
            }
            mNarrowTransferAdapter = NarrowTransferAdapter(iPendingFiles, mContextActivity)
            mNarrowTransferAdapter!!.setSortButton(lSortButton)
            lNarrowTransferRecyclerView.adapter = mNarrowTransferAdapter
            lBuilder.setNegativeButton("Cancel") { dialog, which ->
                destroyAllTransferConnections()
                mContextActivity.mIsShowingTransfer = false
                FTPLogManager.unsubscribeOnNewFTPLogColored(lOnNewFTPLogColored)
                mContextActivity.mHandler!!.sendEmptyMessage(NavigationActivity.NAVIGATION_MESSAGE_TRANSFER_FINISHED)
            }

//                lBuilder.setNeutralButton("Background", new DialogInterface.OnClickListener() {
//                    @Override
//                    public void onClick(DialogInterface iDialog, int iWhich) {
//                        iDialog.dismiss();
//                    }
//                });
            lBuilder.setOnDismissListener {
                FTPLogManager.unsubscribeOnNewFTPLogColored(lOnNewFTPLogColored)
                destroyAllTransferConnections()
            }
            lBuilder.setCancelable(false)
            lBuilder.setView(lDownloadingDialogView)
            if (iLoadDirection == LoadDirection.DOWNLOAD) lBuilder.setTitle("Downloading ...") // TODO : strings
            else if (iLoadDirection == LoadDirection.UPLOAD) lBuilder.setTitle("Uploading ...")
            mContextActivity.mTransferDialog = lBuilder.create()
            mContextActivity!!.mTransferDialog!!.show()
            mContextActivity.mIsShowingTransfer = true
        }
    }

    private fun createNewFTPTransfer(iLoadDirection: LoadDirection) {
        mHandler!!.post {
            val lFTPTransfer = FTPTransfer(mContextActivity!!.mFTPServer!!.dataBaseId)
            mFTPTransferList!!.add(lFTPTransfer)
            if (iLoadDirection == LoadDirection.DOWNLOAD) lFTPTransfer.downloadFiles(mPendingFiles, mUniversalTransferListener!!) else if (iLoadDirection == LoadDirection.UPLOAD) lFTPTransfer.uploadFiles(mPendingFiles, mUniversalTransferListener!!)
        }
    }

    private fun createExistingFileDialog(iPendingFile: PendingFile) {
        val lBuilder = AlertDialog.Builder(mContextActivity!!)
        val lAskExistingFileAction = View.inflate(mContextActivity, R.layout.dialog_existing_file_action, null)
        val lRadioGroup = lAskExistingFileAction.findViewById<RadioGroup>(R.id.existing_action_radio_group)
        val lDoNotAskAgainCheckBox = lAskExistingFileAction.findViewById<CheckBox>(R.id.existing_action_do_not_ask_again)
        lBuilder.setView(lAskExistingFileAction)
        lBuilder.setCancelable(false)
        lBuilder.setTitle("File already existing...") // TODO : strings
        lBuilder.setMessage(iPendingFile.name)
        lBuilder.setPositiveButton("continue") { iDialog, iWhich ->
            iDialog.dismiss()
            handleExistingFileAction(
                    lRadioGroup.checkedRadioButtonId,
                    lDoNotAskAgainCheckBox.isChecked,
                    iPendingFile)
        }
        lBuilder.setNegativeButton("cancel") { iDialog, iWhich ->
            mHandler!!.sendEmptyMessage(NavigationActivity.NAVIGATION_ORDER_DISMISS_DIALOGS)
            mContextActivity.mTransferDialog!!.dismiss()
            destroyAllTransferConnections()
        }
        mHandler!!.post {
            mContextActivity.mChooseExistingFileActionDialog = lBuilder.create()
            mContextActivity!!.mChooseExistingFileActionDialog!!.show()
        }
    }

    private fun handleExistingFileAction(iCheckedButtonId: Int, iCheckedBox: Boolean,
                                         iPendingFile: PendingFile) {
        var lExistingFileAction = ExistingFileAction.REPLACE_FILE
        when (iCheckedButtonId) {
            R.id.existing_action_ignore -> lExistingFileAction = ExistingFileAction.IGNORE
            R.id.existing_action_resume -> lExistingFileAction = ExistingFileAction.RESUME_FILE_TRANSFER
            R.id.existing_action_replace -> lExistingFileAction = ExistingFileAction.REPLACE_FILE
            R.id.existing_action_replace_if_size_diff -> lExistingFileAction = ExistingFileAction.REPLACE_IF_SIZE_IS_DIFFERENT
            R.id.existing_action_replace_if_more_recent -> lExistingFileAction = ExistingFileAction.REPLACE_IF_FILE_IS_MORE_RECENT
            R.id.existing_action_replace_if_size_diff_or_more_recent -> lExistingFileAction = ExistingFileAction.REPLACE_IF_SIZE_IS_DIFFERENT_OR_FILE_IS_MORE_RECENT
            R.id.existing_action_rename -> lExistingFileAction = ExistingFileAction.RENAME_FILE
        }
        if (iCheckedBox) {
            for (lItem in mPendingFiles) {
                if (lItem.existingFileAction == ExistingFileAction.NOT_DEFINED) lItem.existingFileAction = lExistingFileAction
            }
        } else {
            iPendingFile.existingFileAction = lExistingFileAction
        }
        FTPTransfer.notifyExistingFileActionIsDefined()
    }

    private fun initializeUniversalTransferListener(): OnTransferListener {
        return object : OnTransferListener {
            override fun onConnected(iPendingFile: PendingFile?) {}
            override fun onConnectionLost(iPendingFile: PendingFile?) {
                mHandler!!.post { mNarrowTransferAdapter!!.updatePendingFileData(iPendingFile!!) }
            }

            override fun onTransferSuccess(iPendingFile: PendingFile?) {
                mHandler!!.post {
                    DataBase.pendingFileDAO!!.delete(iPendingFile)
                    mNarrowTransferAdapter!!.addPendingFileToRemove(iPendingFile!!)
                }
            }

            override fun onStateUpdateRequested(iPendingFile: PendingFile?) {
                mHandler!!.post { mNarrowTransferAdapter!!.updatePendingFileData(iPendingFile!!) }
            }

            override fun onExistingFile(iPendingFile: PendingFile?) {
                mHandler!!.post { createExistingFileDialog(iPendingFile!!) }
            }

            override fun onFail(iPendingFile: PendingFile?) {
                mPendingFileErrors++
                mHandler!!.post {
                    mNarrowTransferAdapter!!.showError(iPendingFile!!)
                    DataBase.pendingFileDAO!!.delete(iPendingFile)
                }
            }

            override fun onStop(iFTPTransfer: FTPTransfer?) {
                mFTPTransferList!!.remove(iFTPTransfer)
                if (mFTPTransferList.size == 0) {
                    if (mPendingFileErrors == 0) {
                        mHandler!!.post {
                            if (!mContextActivity!!.mSuccessDialog!!.isShowing &&
                                    mNarrowTransferAdapter!!.itemCountOmitPendingFile == 0) {
                                mContextActivity.mTransferDialog!!.dismiss()
                                showSuccessTransfer()
                            }
                        }
                    } else {
                        mHandler!!.post {
                            mContextActivity!!.mTransferDialog!!.getButton(DialogInterface.BUTTON_NEGATIVE).text = "Finish" // TODO : Strings
                        }
                    }
                }
            }
        }
    }

    private fun showSuccessTransfer() {
        if (mContextActivity!!.isFinishing) {
            LogManager.error(TAG, "Show success transfer called but isFinishing == true")
            return
        }
        val lMessage = "All files has been transferred" // TODO : Strings
        mContextActivity.mSuccessDialog = Utils.createSuccessAlertDialog(mContextActivity, lMessage)
        mContextActivity.mSuccessDialog!!.setOnDismissListener {
            destroyAllTransferConnections()
            mContextActivity.mIsShowingTransfer = false
            mContextActivity.mHandler!!.sendEmptyMessage(NavigationActivity.NAVIGATION_MESSAGE_TRANSFER_FINISHED)
        }
        mContextActivity.mSuccessDialog!!.show()
    }

    private fun destroyAllTransferConnections() {
        for (lItem in mFTPTransferList!!) lItem.destroyConnection()
        mFTPTransferList.clear()
    }

    private fun showUploadConfirmation(iUris: Array<Uri>) {
        mContextActivity!!.mIndexingPendingFilesDialog = Utils.createAlertDialog(
                mContextActivity,
                "Uploading",  // TODO : Strings
                "Do you confirm the upload of " + iUris.size + " files ?",
                "yes",
                { iDialog, _ ->
                    iDialog.dismiss()
                    uploadFiles(iUris)
                },
                "cancel",
                null)
        mContextActivity.mIndexingPendingFilesDialog!!.show()
    }

    private fun showDownloadConfirmation(iSelectedFiles: Array<FTPFile>) {
        mContextActivity!!.mIndexingPendingFilesDialog = Utils.createAlertDialog(
                mContextActivity,
                "Downloading",  // TODO : Strings
                "Do you confirm the download of " + iSelectedFiles.size + " files ?",
                "yes",
                { iDialog, iWhich ->
                    iDialog.dismiss()
                    indexFilesForDownload(iSelectedFiles)
                },
                "cancel",
                null)
        mContextActivity.mIndexingPendingFilesDialog!!.show()
    }

    companion object {
        private const val TAG = "NAVIGATION DOWNLOAD"
    }
}
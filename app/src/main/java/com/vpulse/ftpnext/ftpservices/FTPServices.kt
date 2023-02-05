package com.vpulse.ftpnext.ftpservices

import com.vpulse.ftpnext.commons.FTPFileUtils
import com.vpulse.ftpnext.commons.Utils
import com.vpulse.ftpnext.core.*
import com.vpulse.ftpnext.database.FTPServerTable.FTPServer
import com.vpulse.ftpnext.database.PendingFileTable.PendingFile
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.FTPReply
import org.greenrobot.eventbus.Subscribe
import java.io.IOException

class FTPServices : AFTPConnection {
    private var mCurrentDirectory: FTPFile? = null
    private var mDirectoryFetchThread: Thread? = null
    private var mDeleteFileThread: Thread? = null
    private var mCreateDirectoryThread: Thread? = null
    private var mIndexingFilesThread: Thread? = null
    private var mDirectoryFetchInterrupted: Boolean = false
    private var mDeleteFileInterrupted: Boolean = false
    private var mCreateDirectoryInterrupted: Boolean = false
    private var mIndexingFilesInterrupted: Boolean = false
    private var isDeletingPaused: Boolean = false
    private var mByPassDeletingRightErrors: Boolean = false
    private var mByPassDeletingFailErrors: Boolean = false

    constructor(iFTPServer: FTPServer?) : super(iFTPServer) {
        if (sFTPServicesInstances == null) sFTPServicesInstances = ArrayList()
        sFTPServicesInstances!!.add(this)
    }

    @Subscribe
    fun onEvent(iMessageEvent: MessageEvent?) {
    }

    override fun destroyConnection() {
        LogManager.info(TAG, "Destroy connection")
        super.destroyConnection()
        sFTPServicesInstances!!.remove(this)
    }

    override fun disconnect() {
        LogManager.info(TAG, "Disconnect")
        if (isLocallyConnected) {
            if (isFetchingFolders) abortFetchDirectoryContent()
            if (isDeletingFiles && !isReconnecting) abortDeleting()
            if (isIndexingPendingFiles) abortIndexingPendingFiles()
        }
    }

    fun updateWorkingDirectory(iNewWorkingDirectory: String?) {
        LogManager.info(TAG, "Update working directory")
        if (!isLocallyConnected) return
        mHandlerConnection!!.post(object : Runnable {
            override fun run() {
                try {
                    val lWorkingDir: FTPFile? = mFTPClient!!.mlistFile(iNewWorkingDirectory)
                    FTPLogManager.pushStatusLog(
                        "Updating current working dir to \"$iNewWorkingDirectory\""
                    )
                    if (lWorkingDir != null && lWorkingDir.isDirectory) {
                        mCurrentDirectory = lWorkingDir
                    }
                } catch (iE: Exception) {
                    iE.printStackTrace()
                }
            }
        })
    }

    // TODO : Aborting fetch is extremely long to timeout and stop, check if we can decrease the timeout
    fun abortFetchDirectoryContent() {
        LogManager.info(TAG, "Abort fetch directory contents")
        if (isFetchingFolders) {
            FTPLogManager.pushStatusLog("Aborting fetch directory")
            mDirectoryFetchInterrupted = true
            mHandlerConnection!!.post(object : Runnable {
                override fun run() {
                    try {
                        mFTPClient!!.abort()
                    } catch (iE: IOException) {
                        iE.printStackTrace()
                    }
                }
            })
        }
    }

    fun abortIndexingPendingFiles() {
        LogManager.info(TAG, "Abort indexing pending files")
        if (isIndexingPendingFiles) {
            FTPLogManager.pushStatusLog("Aborting indexing")
            mIndexingFilesInterrupted = true
        }
    }

    fun abortDeleting() {
        LogManager.info(TAG, "Abort deleting")
        if (isDeletingFiles) {
            FTPLogManager.pushStatusLog("Aborting deleting")
            mDeleteFileInterrupted = true
        }
        mHandlerConnection!!.post(object : Runnable {
            override fun run() {
                try {
                    mFTPClient!!.abort()
                } catch (iE: IOException) {
                    iE.printStackTrace()
                }
            }
        })
    }

    fun fetchDirectoryContent(iPath: String?, iOnFetchDirectoryResult: IOnFetchDirectoryResult) {
        LogManager.info(TAG, "Fetch directory contents")
        if (!isLocallyConnected) {
            LogManager.error(TAG, "Connection not established")
            return
        }
        if (isFetchingFolders) {
            LogManager.info(TAG, "Already fetching something")
            return
        }
        mDirectoryFetchInterrupted = false
        mDirectoryFetchThread = Thread(object : Runnable {
            override fun run() {
                FTPLogManager.pushStatusLog("Fetching content of \"" + iPath + "\"")
                val lLeavingDirectory: FTPFile? = mCurrentDirectory
                try { // Sometimes, isFetchingDirectory returned false while it was actually
                    // Trying to fetch a dir, but the thread wasn't started yet
                    var lTargetDirectory: FTPFile? = mFTPClient!!.mlistFile(iPath)
                    if (lTargetDirectory == null) { // Experimenting a fix for the following to-do
                        mFTPClient.completePendingCommand()
                    }
                    lTargetDirectory = mFTPClient.mlistFile(iPath)
                    if (lTargetDirectory == null) {
                        iOnFetchDirectoryResult.onFail(
                            ErrorCodeDescription.ERROR, mFTPClient.replyCode
                        )
                        LogManager.error(TAG, "Critical error : lTargetDirectory == null")
                        return
                    }
                    if (!lTargetDirectory.isDirectory) { // TODO : put null security for release
                        iOnFetchDirectoryResult.onFail(
                            ErrorCodeDescription.ERROR_NOT_A_DIRECTORY, FTPReply.FILE_UNAVAILABLE
                        )
                        return
                    }
                    if (!lTargetDirectory.hasPermission(
                            FTPFile.USER_ACCESS, FTPFile.EXECUTE_PERMISSION
                        )) {
                        iOnFetchDirectoryResult.onFail(
                            ErrorCodeDescription.ERROR_EXECUTE_PERMISSION_MISSED, 633
                        )
                        return
                    }
                    if (!lTargetDirectory.hasPermission(
                            FTPFile.USER_ACCESS, FTPFile.READ_PERMISSION
                        )) {
                        iOnFetchDirectoryResult.onFail(
                            ErrorCodeDescription.ERROR_READ_PERMISSION_MISSED, 633
                        )
                        return
                    }
                    if (mDirectoryFetchInterrupted) {
                        iOnFetchDirectoryResult.onInterrupt()
                        return
                    }
                    mFTPClient.enterLocalPassiveMode()
                    mFTPClient.changeWorkingDirectory(iPath)
                    val lFiles: Array<FTPFile?> = mFTPClient.listFiles()
                    if (mDirectoryFetchInterrupted) throw InterruptedException()
                    updateWorkingDirectory(lTargetDirectory.name)
                    mFTPClient.enterLocalActiveMode()
                    if (mDirectoryFetchInterrupted) throw InterruptedException()
                    if (!FTPReply.isPositiveCompletion(mFTPClient.replyCode)) throw IOException(
                        mFTPClient.replyString
                    )
                    if (mDirectoryFetchInterrupted) throw InterruptedException()
                    FTPLogManager.pushSuccessLog("Fetching \"" + iPath + "\"")
                    iOnFetchDirectoryResult.onSuccess(lFiles)
                } catch (iE: InterruptedException) {
                    if (lLeavingDirectory != null) updateWorkingDirectory(lLeavingDirectory.name)
                    iOnFetchDirectoryResult.onInterrupt()
                } catch (iE: Exception) {
                    iE.printStackTrace()
                    if (!mDirectoryFetchInterrupted) {
                        if (mFTPClient!!.replyCode == 450) iOnFetchDirectoryResult.onFail(
                            ErrorCodeDescription.ERROR_NOT_REACHABLE, mFTPClient.replyCode
                        ) else iOnFetchDirectoryResult.onFail(
                            ErrorCodeDescription.ERROR, mFTPClient.replyCode
                        )
                    } else iOnFetchDirectoryResult.onInterrupt()
                }
            }
        })
        mDirectoryFetchThread!!.name = "FTP Dir fetch"
        mDirectoryFetchThread!!.start()
    }

    fun createDirectory(iPath: String?,
                        iName: String,
                        iOnCreateDirectoryResult: OnCreateDirectoryResult?
    ) {
        LogManager.info(TAG, "Create directory")
        LogManager.info(TAG, "Directory Path : " + iPath)
        LogManager.info(TAG, "Directory Name : " + iName)
        if (!isLocallyConnected) {
            LogManager.error(TAG, "Connection not established")
            return
        } else if (isCreatingFolder) {
            LogManager.error(TAG, "Is already creating directory")
            return
        }
        mCreateDirectoryInterrupted = false
        mCreateDirectoryThread = Thread(object : Runnable {
            override fun run() {
                try {
                    val lExistingFile: FTPFile? = mFTPClient!!.mlistFile(iPath + "/" + iName)
                    if (lExistingFile != null && lExistingFile.isDirectory) {
                        iOnCreateDirectoryResult?.onFail(
                            ErrorCodeDescription.ERROR_DIRECTORY_ALREADY_EXISTING,
                            FTPReply.STORAGE_ALLOCATION_EXCEEDED
                        )
                        return
                    }
                    FTPLogManager.pushStatusLog("Creating directory \"" + iPath + "\"")
                    if (!mFTPClient.makeDirectory(iPath + "/" + iName)) {
                        FTPLogManager.pushErrorLog("Creation failed")
                        throw IOException("Creation failed.")
                    }
                    if (mCreateDirectoryInterrupted) {
                        iOnCreateDirectoryResult?.onFail(
                            ErrorCodeDescription.ERROR, 0
                        )
                        return
                    }
                    val lCreatedFile: FTPFile = mFTPClient.mlistFile(iPath + "/" + iName)
                    lCreatedFile.name = iName
                    iOnCreateDirectoryResult?.onSuccess(
                        lCreatedFile
                    )
                } catch (iE: IOException) {
                    iE.printStackTrace()
                    iOnCreateDirectoryResult?.onFail(
                        ErrorCodeDescription.ERROR, FTPReply.UNRECOGNIZED_COMMAND
                    )
                }
            }
        })
        mCreateDirectoryThread!!.name = "FTP Create dir"
        mCreateDirectoryThread!!.start()
    }

    fun deleteFile(iFTPFile: FTPFile, iOnDeleteListener: OnDeleteListener) {
        deleteFiles(arrayOf(iFTPFile), iOnDeleteListener)
    }

    fun deleteFiles(iSelection: Array<FTPFile>, iOnDeleteListener: OnDeleteListener) {
        LogManager.info(TAG, "Delete files")
        if (!isLocallyConnected) {
            LogManager.error(TAG, "Connection not established")
            return
        } else if (isDeletingFiles) {
            LogManager.error(TAG, "Is already deleting files")
            return
        }
        isDeletingPaused = false
        mByPassDeletingRightErrors = false
        mByPassDeletingFailErrors = false
        mDeleteFileInterrupted = false
        mDeleteFileThread = Thread(object : Runnable {
            @Throws(IOException::class, InterruptedException::class)
            private fun recursiveDeletion(iFTPFile: FTPFile, iProgress: Int, iTotal: Int) {
                while (isDeletingPaused && !mDeleteFileInterrupted) Thread.sleep(
                    DELETE_THREAD_SLEEP_TIME.toLong()
                )
                if (mDeleteFileInterrupted) return
                LogManager.info(
                    TAG, "Recursive deletion : " + iFTPFile.name + " " + iFTPFile.isDirectory
                )
                if (iFTPFile.isDirectory) {
                    iOnDeleteListener.onProgressDirectory(
                        iProgress, iTotal, iFTPFile.name
                    )

                    // DIRECTORY
                    if (iFTPFile.hasPermission(FTPFile.USER_ACCESS, FTPFile.READ_PERMISSION)) {
                        val lFiles: Array<FTPFile> = mFTPClient!!.listFiles(iFTPFile.name)
                        var lProgress: Int = 0
                        for (lFile: FTPFile in lFiles) {
                            iOnDeleteListener.onProgressDirectory(
                                iProgress, iTotal, iFTPFile.name
                            )
                            lFile.name = iFTPFile.name + "/" + lFile.name
                            recursiveDeletion(lFile, lProgress++, lFiles.size)
                            while (isDeletingPaused && !mDeleteFileInterrupted) Thread.sleep(
                                DELETE_THREAD_SLEEP_TIME.toLong()
                            )
                            if (mDeleteFileInterrupted) return
                        }
                        iOnDeleteListener.onProgressDirectory(
                            iProgress, iTotal, iFTPFile.name
                        )
                        iOnDeleteListener.onProgressSubDirectory(
                            0, 0, ""
                        )
                        while (isDeletingPaused && !mDeleteFileInterrupted) Thread.sleep(
                            DELETE_THREAD_SLEEP_TIME.toLong()
                        )
                        if (mDeleteFileInterrupted) return
                        if (iFTPFile.hasPermission(FTPFile.USER_ACCESS, FTPFile.WRITE_PERMISSION)) {
                            LogManager.info(TAG, "Will delete dir : " + iFTPFile.name)
                            val lReply: Boolean = mFTPClient.removeDirectory(iFTPFile.name)
                            if (!lReply && !mByPassDeletingFailErrors) iOnDeleteListener.onFail(
                                iFTPFile
                            ) // TODO : Watch folder error is not triggered !
                        } else if (!mByPassDeletingRightErrors) iOnDeleteListener.onRightAccessFail(
                            iFTPFile
                        )
                    } else if (!mByPassDeletingRightErrors) iOnDeleteListener.onRightAccessFail(
                        iFTPFile
                    )
                } else if (iFTPFile.isFile) { // FILE
                    iOnDeleteListener.onProgressSubDirectory(
                        iProgress, iTotal, FTPFileUtils.getFileName(iFTPFile)
                    )
                    if (iFTPFile.hasPermission(FTPFile.USER_ACCESS, FTPFile.WRITE_PERMISSION)) {
                        val lReply: Boolean = mFTPClient!!.deleteFile(iFTPFile.name)
                        if (!lReply) {
                            FTPLogManager.pushErrorLog("Delete \"" + iFTPFile.name + "\"")
                            iOnDeleteListener.onFail(iFTPFile)
                        } else FTPLogManager.pushSuccessLog("Delete \"" + iFTPFile.name + "\"")
                    } else if (!mByPassDeletingRightErrors) {
                        isDeletingPaused = true
                        FTPLogManager.pushErrorLog("Right access fail \"" + iFTPFile.name + "\"")
                        iOnDeleteListener.onRightAccessFail(iFTPFile)
                    }
                }
            }

            override fun run() {
                LogManager.info(TAG, "Thread delete files")
                try {
                    mFTPClient!!.enterLocalPassiveMode() // PASSIVE MODE
                    iOnDeleteListener.onStartDelete()
                    var lProgress: Int = 0
                    for (lFTPFile: FTPFile? in iSelection!!) {
                        val lAbsoluteFile: FTPFile? =
                            mFTPClient.mlistFile(mCurrentDirectory!!.name + "/" + lFTPFile!!.name)
                        if (lAbsoluteFile != null) {
                            iOnDeleteListener.onProgressDirectory(
                                lProgress, iSelection.size, lFTPFile.name
                            )
                            recursiveDeletion(lAbsoluteFile, lProgress++, iSelection.size)
                        } else {
                            LogManager.error(TAG, "Error with : " + lFTPFile.name)
                            iOnDeleteListener.onFail(lFTPFile)
                            isDeletingPaused = true
                        }
                        iOnDeleteListener.onProgressDirectory(
                            lProgress, iSelection.size, lFTPFile.name
                        )
                    }
                    while (isDeletingPaused && !mDeleteFileInterrupted) Thread.sleep(
                        DELETE_THREAD_SLEEP_TIME.toLong()
                    )
                    if (mDeleteFileInterrupted) return
                    mFTPClient.enterLocalActiveMode() // ACTIVE MODE
                    iOnDeleteListener.onFinish()
                } catch (iE: Exception) {
                    iE.printStackTrace()
                }
            }
        })
        mDeleteFileThread!!.name = "FTP Delete dir"
        mDeleteFileThread!!.start()
    }

    fun indexingPendingFilesProcedure(iSelectedFiles: Array<FTPFile>,
                                      iOnResult: OnIndexingPendingFilesListener
    ) {
        LogManager.info(TAG, "Create pending files procedure")
        if (!isLocallyConnected) {
            LogManager.error(TAG, "Is not connected")
            if (iOnResult != null) iOnResult.onResult(false, null)
            return
        }
        if (isIndexingPendingFiles) {
            LogManager.error(TAG, "Is already creating pending files")
            if (iOnResult != null) iOnResult.onResult(false, null)
            return
        }
        indexingPendingFiles(
            fTPServer!!.dataBaseId, iSelectedFiles, LoadDirection.DOWNLOAD, iOnResult
        )
    }

    private fun indexingPendingFiles(iServerId: Int,
                                     iSelectedFiles: Array<FTPFile>,
                                     iLoadDirection: LoadDirection,
                                     iIndexingListener: OnIndexingPendingFilesListener
    ) {
        LogManager.info(TAG, "Create pending files")
        val oPendingFiles: MutableList<PendingFile> =
            ArrayList() // Removing "/" if we are at the root
        var mTmp = when (mCurrentDirectory!!.name) {
            "/" -> ""
            else -> mCurrentDirectory!!.name
        }
        if (!mTmp.endsWith("/")) mTmp += "/"
        val mCurrentLocation: String = mTmp
        mIndexingFilesInterrupted = false
        mIndexingFilesThread = Thread(object : Runnable {
            override fun run() {
                iIndexingListener.onStart()

                // While on the selected files visible by the user
                for (lItem: FTPFile in iSelectedFiles) {
                    if (mIndexingFilesInterrupted) {
                        iIndexingListener.onResult(false, null)
                        return
                    }
                    if (lItem.isDirectory) { // Passing the folder and the folder name as the relative path to directory
                        // In recursive folder, it can't be lItem.getName() to iEnclosureName
                        // Because iRelativePathToDirectory is used to move in the hierarchy
                        recursiveFolder(lItem.name)
                        if (mIndexingFilesInterrupted) {
                            iIndexingListener.onResult(false, null)
                            return
                        }
                    } else {
                        val lPendingFile: PendingFile = PendingFile(
                            iServerId,
                            iLoadDirection,
                            false,
                            lItem.name,
                            mCurrentLocation,
                            fTPServer!!.absolutePath,
                            PreferenceManager.existingFileAction
                        )
                        FTPLogManager.pushSuccessLog("Indexing \"" + lItem.name + "\"")
                        oPendingFiles.add(lPendingFile)
                        iIndexingListener.onNewIndexedFile(lPendingFile)
                    }
                }
                if (mIndexingFilesInterrupted) {
                    iIndexingListener.onResult(false, null)
                    return
                }
                FTPLogManager.pushSuccessLog("Finishing indexing")
                iIndexingListener.onResult(true, oPendingFiles.toTypedArray())
            }

            private fun recursiveFolder(iRelativePathToDirectory: String) {
                LogManager.info(TAG, "Recursive create pending files")
                var lFilesOfFolder: Array<FTPFile>? = null
                if (mIndexingFilesInterrupted) {
                    iIndexingListener.onResult(false, null)
                    return
                }
                iIndexingListener.onFetchingFolder(mCurrentLocation + iRelativePathToDirectory + "/")
                mFTPClient!!.enterLocalPassiveMode()
                while (lFilesOfFolder == null) {
                    if (mIndexingFilesInterrupted) {
                        iIndexingListener.onResult(false, null)
                        return
                    }
                    try { // Necessary to use iRelativePathToDirectory because iDirectory always represents
                        // the directory name, and not his own sub path
                        lFilesOfFolder =
                            mFTPClient.mlistDir(mCurrentLocation + iRelativePathToDirectory)
                        if (mIndexingFilesInterrupted) {
                            iIndexingListener.onResult(false, null)
                            return
                        }
                    } catch (iE: Exception) {
                        iE.printStackTrace()
                        if (!isLocallyConnected || AppCore.networkManager!!.isNetworkAvailable) {
                            mIndexingFilesThread!!.interrupt()
                            return
                        }
                    }
                }
                mFTPClient.enterLocalActiveMode()
                for (lItem: FTPFile in lFilesOfFolder) {
                    if (lItem.isDirectory) { // Adding a directory to the relative path to directory
                        recursiveFolder(iRelativePathToDirectory + "/" + lItem.name)
                        if (mIndexingFilesInterrupted) {
                            iIndexingListener.onResult(false, null)
                            return
                        }
                    } else {
                        val lPendingFile: PendingFile = PendingFile(
                            iServerId,
                            iLoadDirection,
                            false,
                            lItem.name,
                            "$mCurrentLocation$iRelativePathToDirectory/",
                            fTPServer!!.absolutePath + iRelativePathToDirectory + "/",
                            PreferenceManager.existingFileAction
                        )
                        FTPLogManager.pushSuccessLog("Indexing \"" + lItem.name + "\"")
                        oPendingFiles.add(lPendingFile)

                        // Sleep for nicer view in the dialog
                        Utils.sleep(1)
                        iIndexingListener.onNewIndexedFile(lPendingFile)
                        if (mIndexingFilesInterrupted) {
                            iIndexingListener.onResult(false, null)
                            return
                        }
                    }
                }
            }
        })
        mIndexingFilesThread!!.name = "FTP Indexing"
        mIndexingFilesThread!!.start()
    }

    // Display :
    val isFetchingFolders: Boolean
        get() { // Display :
            LogManager.info(
                TAG,
                ("isFetchingFolders : " + isLocallyConnected + " && " + (mDirectoryFetchThread != null && mDirectoryFetchThread!!.isAlive))
            )
            return isLocallyConnected && (mDirectoryFetchThread != null && mDirectoryFetchThread!!.isAlive)
        }
    val isCreatingFolder: Boolean
        get() {
            return isLocallyConnected && (mCreateDirectoryThread != null) && mCreateDirectoryThread!!.isAlive
        }
    val isDeletingFiles: Boolean
        get() {
            return isLocallyConnected && (mDeleteFileThread != null) && mDeleteFileThread!!.isAlive
        }
    val isIndexingPendingFiles: Boolean
        get() {
            return isLocallyConnected && (mIndexingFilesThread != null) && mIndexingFilesThread!!.isAlive
        }

    // Display :
    //        LogManager.debug(TAG, " " + isConnecting() + " " + isReconnecting() + " " + isFetchingFolders() + " "
    //                + isCreatingFolder() + " " + isDeletingFiles());
    override val isBusy: Boolean
        get() { // Display :
            //        LogManager.debug(TAG, " " + isConnecting() + " " + isReconnecting() + " " + isFetchingFolders() + " "
            //                + isCreatingFolder() + " " + isDeletingFiles());
            return (isConnecting || isReconnecting || isFetchingFolders || isCreatingFolder || isDeletingFiles || isIndexingPendingFiles)
        }

    override val connectionType: Int = CONNECTION_SERVICES_TYPE

    fun resumeDeleting() {
        isDeletingPaused = false
    }

    fun setDeletingByPassRightErrors(iValue: Boolean) {
        mByPassDeletingRightErrors = iValue
    }

    fun setDeletingByPassFailErrors(iValue: Boolean) {
        mByPassDeletingFailErrors = iValue
    }

    open interface IOnFetchDirectoryResult {
        fun onSuccess(iFTPFiles: Array<FTPFile?>?)
        fun onFail(iErrorEnum: ErrorCodeDescription?, iErrorCode: Int)
        fun onInterrupt()
    }

    open interface OnCreateDirectoryResult {
        fun onSuccess(iNewDirectory: FTPFile?)
        fun onFail(iErrorEnum: ErrorCodeDescription?, iErrorCode: Int)
    }

    open interface OnIndexingPendingFilesListener {
        fun onStart()
        fun onFetchingFolder(iPath: String?)
        fun onNewIndexedFile(iPendingFile: PendingFile?)
        fun onResult(isSuccess: Boolean, iPendingFiles: Array<PendingFile>?)
    }

    abstract class OnDeleteListener(val ftpServices: FTPServices) {
        abstract fun onStartDelete()
        abstract fun onProgressDirectory(iDirectoryProgress: Int,
                                         iTotalDirectories: Int,
                                         iDirectoryName: String?
        )

        abstract fun onProgressSubDirectory(iSubDirectoryProgress: Int,
                                            iTotalSubDirectories: Int,
                                            iSubDirectoryName: String?
        )

        open fun onRightAccessFail(iFTPFile: FTPFile) {
            ftpServices.isDeletingPaused = true
        }

        abstract fun onFinish()

        open fun onFail(iFTPFile: FTPFile?) {
            ftpServices.isDeletingPaused = true
        }
    }


    companion object {
        private val TAG: String = "FTP SERVICES"
        private val DELETE_THREAD_SLEEP_TIME: Int = 500
        private val ITEM_FETCHED_BY_GROUP: Int = 25
        private var sFTPServicesInstances: MutableList<FTPServices>? = null
        fun getFTPServicesInstance(iServerId: Int): FTPServices? {
            LogManager.info(TAG, "Get FTP services instance")
            if (sFTPServicesInstances == null) sFTPServicesInstances = ArrayList()
            for (lServices: FTPServices in sFTPServicesInstances!!) {
                if (lServices.fTPServerId == iServerId) return lServices
            }
            return null
        }
    }
}
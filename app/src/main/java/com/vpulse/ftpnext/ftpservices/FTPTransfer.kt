package com.vpulse.ftpnext.ftpservices

import com.vpulse.ftpnext.commons.Utils
import com.vpulse.ftpnext.core.*
import com.vpulse.ftpnext.database.DataBase
import com.vpulse.ftpnext.database.FTPServerTable.FTPServer
import com.vpulse.ftpnext.database.PendingFileTable.PendingFile
import com.vpulse.ftpnext.ftpservices.FTPTransfer
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPFile
import org.greenrobot.eventbus.Subscribe
import java.io.*

class FTPTransfer : AFTPConnection {
    private var mOnTransferListener: OnTransferListener? = null
    private var mCandidate: PendingFile? = null
    private var mTransferThread: Thread? = null
    private var mIsTransferring: Boolean = false
    private var mIsInterrupted: Boolean = false
    private var mTimer: Long = 0
    private var mBytesTransferred: Long = 0
    private var mHasAlreadyBeenConnected: Boolean = false
    private var mTurn: Int = 0
    private var mSpeedAverage1: Long = 0
    private var mSpeedAverage2: Long = 0
    private var mSpeedAverage3: Long = 0
    private var mSpeedAverage4: Long = 0
    private var mSpeedAverage5: Long = 0

    constructor(iFTPServer: FTPServer?) : super(iFTPServer) {
        if (sFTPTransferInstances == null) sFTPTransferInstances = ArrayList()
        sFTPTransferInstances!!.add(this)
    }

    constructor(iServerId: Int) : super(iServerId) {
        LogManager.info(TAG, "Constructor")
        if (sFTPTransferInstances == null) sFTPTransferInstances = ArrayList()
        sFTPTransferInstances!!.add(this)
    }

    @Subscribe
    fun onEvent(iMessageEvent: MessageEvent?) {
    }

    override fun destroyConnection() {
        LogManager.info(TAG, "Destroy connection")
        mHandlerConnection!!.post(object : Runnable {
            override fun run() {
                if (isTransferring) {
                    mHandlerConnection!!.postDelayed(this, 500)
                    return
                }
                if (mCandidate != null) {
                    mCandidate!!.isSelected = false
                }
                super@FTPTransfer.destroyConnection()
                sFTPTransferInstances!!.remove(this@FTPTransfer)
            }
        })
        abortTransfer()
    }

    private fun initializeListeners(iOnTransferListener: OnTransferListener?) {
        setOnConnectionLost(object : OnConnectionLost {
            override fun onConnectionLost() {
                if (mCandidate != null) {
                    mCandidate!!.remainingTimeInMin = 0
                    mCandidate!!.speedInByte = 0
                    mCandidate!!.isConnected = false
                    iOnTransferListener?.onConnectionLost(mCandidate)
                }
                reconnect(object : OnConnectionRecover {
                    override fun onConnectionRecover() {
                        mCandidate!!.isConnected = true
                        iOnTransferListener?.onConnected(mCandidate)
                    }

                    override fun onConnectionDenied(iErrorEnum: ErrorCodeDescription,
                                                    iErrorCode: Int
                    ) {
                        if (iErrorEnum == ErrorCodeDescription.ERROR_SERVER_DENIED_CONNECTION) FTPLogManager.pushErrorLog(
                            "Server denied connection ..."
                        ) else FTPLogManager.pushErrorLog("Impossible to reconnect ...")
                        iOnTransferListener?.onStop(this@FTPTransfer)
                        if (!mCandidate!!.isFinished && mCandidate!!.isSelected) {
                            mCandidate!!.isSelected = false
                            mOnTransferListener!!.onStateUpdateRequested(mCandidate)
                        }
                        destroyConnection()
                    }
                })
            }
        })
    }

    private fun notifyTransferProgress(iTotalBytesTransferred: Long,
                                       iBytesTransferred: Int,
                                       iStreamSize: Long,
                                       iForceNotify: Boolean = false
    ) {
        mBytesTransferred += iBytesTransferred.toLong()
        val lCurrentTimeMillis: Long = System.currentTimeMillis()
        val lElapsedTime: Float = (lCurrentTimeMillis - mTimer).toFloat()
        if (lElapsedTime > UPDATE_TRANSFER_TIMER || iForceNotify) {
            var lImmediateSpeedInKoS: Long = ((mBytesTransferred / lElapsedTime) * 1000).toLong()
            if (lImmediateSpeedInKoS < 0) lImmediateSpeedInKoS = 0
            settingAverageSpeed(lImmediateSpeedInKoS)
            mCandidate!!.speedInByte = averageSpeed
            val lRemainingTime: Float =
                mCandidate!!.size.toFloat() / mCandidate!!.speedInByte.toFloat()
            mCandidate!!.remainingTimeInMin = lRemainingTime.toInt()
            mCandidate!!.setProgress(iTotalBytesTransferred.toInt())
            mOnTransferListener!!.onStateUpdateRequested(mCandidate)
            mBytesTransferred = 0
            mTimer = lCurrentTimeMillis
        }
    }

    val isTransferring: Boolean
        get() {
            return mTransferThread != null && mTransferThread!!.isAlive
        }

    fun abortTransfer() {
        mIsInterrupted = true
    }

    override val isBusy: Boolean
        get() {
            return mTransferThread != null
        }
    override val connectionType: Int
        protected get() {
            return AFTPConnection.Companion.CONNECTION_TRANSFER_TYPE
        }

    fun downloadFiles(iSelection: Array<PendingFile>, iOnTransferListener: OnTransferListener) {
        LogManager.info(TAG, "Download files")
        if (isTransferring) {
            LogManager.error(TAG, "Transfer not finished")
            return
        }
        mIsInterrupted = false
        mTransferThread = Thread(object : Runnable {
            override fun run() {
                mOnTransferListener = iOnTransferListener
                initializeListeners(mOnTransferListener)
                while (!mIsInterrupted) {

                    // ---------------- INIT PENDING FILE
                    mCandidate = selectAvailableCandidate(iSelection)

                    // Stopping all transfer activities
                    if (mCandidate == null) {
                        mOnTransferListener!!.onStop(this@FTPTransfer)
                        destroyConnection()
                        break
                    }
                    LogManager.info(TAG, "CANDIDATE : \n" + mCandidate.toString())
                    DataBase.Companion.pendingFileDAO!!.update(mCandidate!!)
                    mOnTransferListener!!.onStateUpdateRequested(mCandidate)
                    if (mIsInterrupted) break

                    // ---------------- INIT CONNECTION
                    connectionLooper()
                    if (mIsInterrupted) break
                    mOnTransferListener!!.onConnected(mCandidate)

                    // ---------------- INIT NAMES
                    val lLocalFullPath: String = mCandidate!!.localPath + mCandidate!!.name
                    val lRemoteFullPath: String = mCandidate!!.remotePath + mCandidate!!.name
                    LogManager.info(
                        TAG,
                        ("\nGoing to write on the local path :\n\t" + lLocalFullPath + "\nGoing to fetch from the server path :\n\t" + lRemoteFullPath)
                    )

                    // ---------------- INIT FTP FILE
                    mFTPClient!!.enterLocalPassiveMode()
                    var lRemoteFile: FTPFile?
                    try {
                        lRemoteFile = mFTPClient.mlistFile(lRemoteFullPath)
                    } catch (iE: Exception) {
                        iE.printStackTrace()
                        Utils.sleep(USER_WAIT_BREAK.toLong()) // Break the while true speed
                        continue
                    }
                    if (lRemoteFile == null) {
                        mCandidate!!.isAnError = true
                        mOnTransferListener!!.onFail(mCandidate)
                        FTPLogManager.pushErrorLog(
                            "Failed to recover remote file : \"" + lRemoteFullPath + "\""
                        )
                        continue
                    }

                    // ---------------- INIT LOCAL FILE
                    val lLocalFile: File = File(lLocalFullPath)
                    try {
                        if (!lLocalFile.exists()) {
                            if (lLocalFile.parentFile.mkdirs()) // TODO : Test with lLocalFile = "/"
                                LogManager.info(TAG, "mkdir success")
                            try {
                                if (lLocalFile.createNewFile()) LogManager.info(
                                    TAG, "Local creation success"
                                ) else {
                                    LogManager.info(TAG, "File already exists")
                                    FTPLogManager.pushStatusLog(
                                        ("File already exists : \"" + lLocalFullPath + "\"")
                                    )
                                }
                            } catch (iE: Exception) {
                                iE.printStackTrace()
                                LogManager.error(TAG, "Impossible to create new file")
                                mCandidate!!.isAnError = true
                                mOnTransferListener!!.onFail(mCandidate)
                                FTPLogManager.pushErrorLog(
                                    ("Impossible to write on the local storage at path : \"" + lLocalFullPath + "\"")
                                )
                                if (mIsInterrupted) break
                                continue
                            }
                        } else {
                            while (mCandidate!!.existingFileAction == ExistingFileAction.NOT_DEFINED && !mIsInterrupted) {
                                existingFileLooper()
                            }
                            if (mIsInterrupted) break
                            if (!manageDownloadExistingAction(lRemoteFile, lLocalFile)) continue
                            mCandidate!!.setProgress(lLocalFile.length().toInt())
                        }
                    } catch (iE: Exception) {
                        iE.printStackTrace()
                        mCandidate!!.isAnError = true
                        mOnTransferListener!!.onFail(mCandidate)
                        FTPLogManager.pushErrorLog(
                            ("Impossible to write on the local storage at path : \"" + lLocalFullPath + "\"")
                        )
                        continue
                    }

                    // ---------------- DOWNLOAD
                    var lFinished: Boolean = false
                    while (!lFinished) {
                        if (mIsInterrupted) break
                        connectionLooper()
                        if (mIsInterrupted) break
                        if (!setBinaryFileType()) {
                            Utils.sleep(USER_WAIT_BREAK.toLong()) // Break the while speed
                            continue
                        }

                        // Re-enter in local passive mode in case of a disconnection
                        mFTPClient.enterLocalPassiveMode()
                        var lLocalStream: OutputStream? = null
                        var lRemoteStream: InputStream? = null
                        try {
                            lLocalStream = BufferedOutputStream(FileOutputStream(lLocalFile, true))
                            val bytesArray: ByteArray = ByteArray(8192)
                            var lBytesRead: Int
                            var lTotalRead: Int = lLocalFile.length().toInt()
                            val lFinalSize: Int = lRemoteFile.size.toInt()
                            mCandidate!!.size = lFinalSize
                            mCandidate!!.setProgress(lLocalFile.length().toInt())
                            mFTPClient.restartOffset = mCandidate!!.progress.toLong()
                            lRemoteStream = mFTPClient.retrieveFileStream(lRemoteFullPath)
                            if (lRemoteStream == null) {
                                LogManager.error(TAG, "Remote stream null")
                                mCandidate!!.isAnError = true
                                mOnTransferListener!!.onFail(mCandidate)
                                FTPLogManager.pushErrorLog(
                                    ("Impossible to retrieve remote file stream : \"" + lRemoteFullPath + "\"")
                                )
                                disconnect()
                                break
                            }
                            mCandidate!!.isAnError = false
                            FTPLogManager.pushStatusLog(
                                "Start download of \"" + mCandidate!!.name + "\""
                            )
                            mCandidate!!.isConnected = true // ---------------- DOWNLAND LOOP
                            while ((lRemoteStream.read(bytesArray)
                                    .also({ lBytesRead = it })) != -1) {
                                mIsTransferring = true
                                lTotalRead += lBytesRead
                                lLocalStream.write(bytesArray, 0, lBytesRead)
                                notifyTransferProgress(
                                    lTotalRead.toLong(), lBytesRead, lFinalSize.toLong()
                                )
                                if (mIsInterrupted) break
                            } // ---------------- DOWNLAND LOOP
                            notifyTransferProgress(
                                lTotalRead.toLong(), lBytesRead, lFinalSize.toLong(), true
                            )
                            mIsTransferring = false
                            lFinished = true
                            if (mIsInterrupted) break

                            // Closing streams necessary before complete pending command
                            closeDownloadStreams(lLocalStream, lRemoteStream)
                            try {
                                mFTPClient.completePendingCommand()
                            } catch (iE: IOException) {
                                iE.printStackTrace()
                                mCandidate!!.isAnError = true
                                mOnTransferListener!!.onFail(mCandidate)
                                disconnect()
                                FTPLogManager.pushErrorLog(
                                    ("Failed to complete the transfer : \"" + mCandidate!!.name + "\"")
                                )
                                break
                            }
                            mFTPClient.enterLocalActiveMode()
                            finishCandidateSuccessfully()
                        } catch (iE: Exception) {
                            iE.printStackTrace()
                            mIsTransferring = false
                        } finally {
                            closeDownloadStreams(lLocalStream, lRemoteStream)
                            Utils.sleep(USER_WAIT_BREAK.toLong()) // Wait the connexion update status
                        } // While download end
                    }
                    mCandidate!!.isSelected = false
                    Utils.sleep(TRANSFER_FINISH_BREAK.toLong()) // While candidate end
                }
                mTransferThread = null
            }
        })
        mTransferThread!!.name = "FTP Download"
        mTransferThread!!.start()
    }

    fun uploadFiles(iSelection: Array<PendingFile>, iOnTransferListener: OnTransferListener) {
        LogManager.info(TAG, "Upload files")
        if (isTransferring) {
            LogManager.error(TAG, "Transfer not finished")
            return
        }
        mIsInterrupted = false
        mTransferThread = Thread(object : Runnable {
            override fun run() {
                mOnTransferListener = iOnTransferListener
                initializeListeners(mOnTransferListener)
                while (!mIsInterrupted) {

                    // ---------------- INIT PENDING FILE
                    mCandidate = selectAvailableCandidate(iSelection)

                    // Stopping all transfer activities
                    if (mCandidate == null) {
                        mOnTransferListener!!.onStop(this@FTPTransfer)
                        destroyConnection()
                        break
                    }
                    LogManager.info(TAG, "CANDIDATE : \n" + mCandidate.toString())
                    DataBase.pendingFileDAO!!.update(mCandidate!!)
                    mOnTransferListener!!.onStateUpdateRequested(mCandidate)
                    if (mIsInterrupted) break

                    // ---------------- INIT CONNECTION
                    connectionLooper()
                    if (mIsInterrupted) break
                    mOnTransferListener!!.onConnected(mCandidate)

                    // ---------------- INIT NAMES
                    val lFullLocalPath: String = mCandidate!!.localPath + mCandidate!!.name
                    val lFullRemotePath: String = mCandidate!!.remotePath + mCandidate!!.name
                    LogManager.info(
                        TAG,
                        ("\nGoing to write on the remote path :\n\t" + lFullRemotePath + "\nGoing to read from the local path :\n\t" + lFullLocalPath)
                    )

                    // ---------------- INIT REMOTE FILE
                    val lLocalFile: File = File(lFullLocalPath)
                    if (!lLocalFile.exists()) {
                        LogManager.error(TAG, "Local file doesn't exist")
                        mCandidate!!.isAnError = true
                        mOnTransferListener!!.onFail(mCandidate)
                        FTPLogManager.pushErrorLog(
                            ("Failed to retrieve local file : \"" + lFullLocalPath + "\"")
                        )
                        continue
                    }
                    mFTPClient!!.enterLocalPassiveMode()
                    var lRemoteFile: FTPFile?
                    try {
                        lRemoteFile = mFTPClient.mlistFile(lFullRemotePath)
                    } catch (iE: Exception) {
                        iE.printStackTrace() // Possibly socket error, retry another time.
                        continue
                    }

                    // IF REMOTE FILE DOESN'T EXISTS
                    if (lRemoteFile == null) {
                        try {
                            if (createRecursiveDirectories(mCandidate!!.remotePath)) LogManager.info(
                                TAG, "Success creating folders"
                            ) else throw IOException("Impossible to create new file")
                        } catch (iE: IOException) {
                            iE.printStackTrace()
                            LogManager.error(TAG, "Impossible to create new file")
                            FTPLogManager.pushErrorLog("Impossible to create path")
                            mCandidate!!.isAnError = true
                            mOnTransferListener!!.onFail(mCandidate)
                            continue
                        }
                    } else {
                        while (mCandidate!!.existingFileAction == ExistingFileAction.NOT_DEFINED && !mIsInterrupted) {
                            existingFileLooper()
                        }
                        if (mIsInterrupted) break
                        if (!manageUploadExistingAction(lRemoteFile, lLocalFile)) continue
                    }

                    // ---------------- UPLOAD
                    var lFinished: Boolean = false
                    while (!lFinished) {
                        if (mIsInterrupted) break
                        connectionLooper()
                        if (mIsInterrupted) break
                        try {
                            lRemoteFile = mFTPClient.mlistFile(lFullRemotePath)
                        } catch (iE: Exception) {
                            iE.printStackTrace() // Possibly socket error, retry another time.
                            continue
                        }
                        if (!setBinaryFileType()) {
                            Utils.sleep(USER_WAIT_BREAK.toLong()) // Break the while speed
                            continue
                        }

                        // Re-enter in local passive mode in case of a disconnection
                        mFTPClient.enterLocalPassiveMode()
                        var lLocalStream: FileInputStream? = null
                        var lRemoteStream: OutputStream? = null
                        try {
                            lLocalStream = FileInputStream(lLocalFile)
                            val bytesArray: ByteArray = ByteArray(16384)
                            var lBytesRead: Int
                            var lTotalRead: Int = when (lRemoteFile) {
                                null -> 0
                                else -> lRemoteFile.size.toInt()
                            }
                            val lFinalSize: Int = lLocalFile.length().toInt()
                            mCandidate!!.size = lFinalSize
                            mCandidate!!.setProgress(lTotalRead)
                            lRemoteStream = mFTPClient.appendFileStream(lFullRemotePath)
                            if (lRemoteStream == null) {
                                LogManager.error(TAG, "lRemoteStream == null")
                                mCandidate!!.isAnError = true
                                mOnTransferListener!!.onFail(mCandidate)
                                disconnect()
                                FTPLogManager.pushErrorLog(
                                    ("Failed to retrieve remote file stream : \"" + lFullRemotePath + "\"")
                                )
                                break
                            }
                            mCandidate!!.isConnected = true
                            lLocalStream.skip(lTotalRead.toLong()) // ---------------- UPLOAD LOOP
                            while ((lLocalStream.read(bytesArray)
                                    .also({ lBytesRead = it })) != -1) {
                                mIsTransferring = true
                                lTotalRead += lBytesRead
                                lRemoteStream.write(bytesArray, 0, lBytesRead)
                                notifyTransferProgress(
                                    lTotalRead.toLong(), lBytesRead, lFinalSize.toLong()
                                )
                                if (mIsInterrupted) break
                            } // ---------------- UPLOAD LOOP
                            notifyTransferProgress(
                                lTotalRead.toLong(), lBytesRead, lFinalSize.toLong(), true
                            )
                            mIsTransferring = false
                            lFinished = true
                            if (mIsInterrupted) break

                            // Closing streams necessary before complete pending command
                            closeUploadStreams(lLocalStream, lRemoteStream)
                            try {
                                mFTPClient.completePendingCommand()
                            } catch (iE: IOException) {
                                iE.printStackTrace()
                                mCandidate!!.isAnError = true
                                mOnTransferListener!!.onFail(mCandidate)
                                disconnect()
                                FTPLogManager.pushErrorLog(
                                    ("Failed to complete the transfer : \"" + mCandidate!!.name + "\"")
                                )
                                break
                            }
                            mFTPClient.enterLocalActiveMode()
                            finishCandidateSuccessfully()
                        } catch (iE: Exception) {
                            iE.printStackTrace()
                            mIsTransferring = false
                        } finally {
                            closeUploadStreams(lLocalStream, lRemoteStream)
                            Utils.sleep(USER_WAIT_BREAK.toLong()) // Wait the connexion update status
                        } // While upload end
                    }

                    // Issue : The remote file was removed after a fast wifi re connexion
                    // Or when we killed the app
                    // Answer : It appears that it is a FTP server problem and absolutely not a
                    // ftp client problem
                    Utils.sleep(TRANSFER_FINISH_BREAK.toLong())
                    mCandidate!!.isSelected = false // While candidate end
                }
                mTransferThread = null
            }
        })
        mTransferThread!!.name = "FTP Upload"
        mTransferThread!!.start()
    }

    private fun selectAvailableCandidate(iSelection: Array<PendingFile>): PendingFile? {
        synchronized(FTPTransfer::class.java) {
            val oRet: PendingFile
            for (lItem: PendingFile in iSelection) {
                if (!lItem.isSelected && !lItem.isFinished && !lItem.isAnError) {
                    if (mCandidate != null) // Security init
                        mCandidate!!.isSelected = false
                    oRet = lItem
                    oRet.isSelected = true
                    return oRet
                }
            }
        }
        return null
    }

    private fun finishCandidateSuccessfully() {
        mCandidate!!.speedInByte= 0
        mCandidate!!.remainingTimeInMin = 0
        mCandidate!!.setFinished(true)
        mCandidate!!.setProgress(mCandidate!!.size)
        mOnTransferListener!!.onStateUpdateRequested(mCandidate)
        mOnTransferListener!!.onTransferSuccess(mCandidate)
        FTPLogManager.pushSuccessLog("Download of \"" + mCandidate!!.name + "\"")
    }

    private fun connectionLooper() {
        while (!isRemotelyConnected && !mIsInterrupted) {
            if (!isReconnecting) {
                if (isLocallyConnected) disconnect()
                while (isLocallyConnected && mIsInterrupted) {
                    Utils.sleep(AFTPConnection.Companion.RECONNECTION_WAITING_TIME.toLong())
                }
                connect(object : OnConnectionResult {
                    override fun onSuccess() {
                        mHasAlreadyBeenConnected = true
                    }

                    override fun onFail(iErrorEnum: ErrorCodeDescription, iErrorCode: Int) {
                        LogManager.error(TAG, "Connection loop failed to connect")
                        if (iErrorEnum == ErrorCodeDescription.ERROR_SERVER_DENIED_CONNECTION) {
                            FTPLogManager.pushErrorLog("Server denied connection ...")
                            mCandidate!!.isSelected = false
                            mOnTransferListener!!.onStateUpdateRequested(mCandidate)
                            if (!mCandidate!!.isFinished && mHasAlreadyBeenConnected) mOnTransferListener!!.onStateUpdateRequested(
                                mCandidate
                            )
                            if (mOnTransferListener != null) mOnTransferListener!!.onStop(this@FTPTransfer)
                            destroyConnection()
                        }
                    }
                })
            }
            while (!isLocallyConnected || isConnecting || isReconnecting) {
                Utils.sleep(200)
                if (mIsInterrupted) {
                    break
                }
            }
            Utils.sleep(1000)
        }
        LogManager.info(TAG, "Is connected : true")
    }

    private fun existingFileLooper() {
        LogManager.info(TAG, "Existing file looper")
        while (mIsAskingActionForExistingFile && !mIsInterrupted) {
            Utils.sleep(USER_WAIT_BREAK.toLong())
        }
        if (mIsInterrupted) return
        synchronized(FTPTransfer::class.java) {
            if (mCandidate!!.existingFileAction == ExistingFileAction.NOT_DEFINED && !mIsAskingActionForExistingFile) {
                mIsAskingActionForExistingFile = true
                mOnTransferListener!!.onExistingFile(mCandidate)
                while (mIsAskingActionForExistingFile && !mIsInterrupted) Utils.sleep(
                    USER_WAIT_BREAK.toLong()
                )
            }
        }
    }

    /**
     * @param iRemoteFile the remote FTPFile
     * @param iLocalFile  the local File
     * @return false if it should pass continue (pass to the next candidate). True if
     * it should pass to the upload
     */
    private fun manageDownloadExistingAction(iRemoteFile: FTPFile, iLocalFile: File): Boolean {
        when (mCandidate!!.existingFileAction) {
            ExistingFileAction.REPLACE_FILE -> iLocalFile.delete()
            ExistingFileAction.RESUME_FILE_TRANSFER -> {}
            ExistingFileAction.REPLACE_IF_SIZE_IS_DIFFERENT -> if (iLocalFile.length() != iRemoteFile.size) iLocalFile.delete()
            ExistingFileAction.REPLACE_IF_FILE_IS_MORE_RECENT, ExistingFileAction.REPLACE_IF_SIZE_IS_DIFFERENT_OR_FILE_IS_MORE_RECENT, ExistingFileAction.IGNORE -> {
                mCandidate!!.setProgress(iLocalFile.length().toInt())
                mCandidate!!.setFinished(true)
                mOnTransferListener!!.onTransferSuccess(mCandidate)
                return false
            }
            else -> {
                mCandidate!!.setProgress(iLocalFile.length().toInt())
                mCandidate!!.setFinished(true)
                mOnTransferListener!!.onTransferSuccess(mCandidate)
                return false
            }
        }
        return true
    }

    /**
     * @param iRemoteFile the remote FTPFile
     * @param iLocalFile  the local File
     * @return false if it should pass continue (pass to the next candidate). True if
     * it should pass to the upload
     */
    private fun manageUploadExistingAction(iRemoteFile: FTPFile, iLocalFile: File): Boolean {
        when (mCandidate!!.existingFileAction) {
            ExistingFileAction.RESUME_FILE_TRANSFER -> {}
            ExistingFileAction.REPLACE_FILE -> try {
                mFTPClient!!.deleteFile(iRemoteFile.name)
            } catch (iE: IOException) {
                iE.printStackTrace()
            }
            ExistingFileAction.REPLACE_IF_SIZE_IS_DIFFERENT -> if (iRemoteFile.size != iLocalFile.length()) {
                try {
                    mFTPClient!!.deleteFile(iRemoteFile.name)
                } catch (iE: IOException) {
                    iE.printStackTrace()
                }
            }
            ExistingFileAction.REPLACE_IF_FILE_IS_MORE_RECENT, ExistingFileAction.REPLACE_IF_SIZE_IS_DIFFERENT_OR_FILE_IS_MORE_RECENT, ExistingFileAction.IGNORE -> {
                mCandidate!!.setProgress(iLocalFile.length().toInt())
                mCandidate!!.setFinished(true)
                mOnTransferListener!!.onTransferSuccess(mCandidate)
                return false
            }
            else -> {
                mCandidate!!.setProgress(iLocalFile.length().toInt())
                mCandidate!!.setFinished(true)
                mOnTransferListener!!.onTransferSuccess(mCandidate)
                return false
            }
        }
        return true
    }

    @Throws(IOException::class)
    private fun createRecursiveDirectories(iFullPath: String?): Boolean {
        var iFullPath: String? = iFullPath
        val lOriginalWorkingDir: String = mFTPClient!!.printWorkingDirectory()
        if (iFullPath!!.startsWith("/")) iFullPath = iFullPath.substring(1)
        val lPathElements: Array<String>? =
            iFullPath.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (lPathElements == null || lPathElements.isEmpty()) return true
        for (lDir: String in lPathElements) {
            val lExists: Boolean = mFTPClient.changeWorkingDirectory(lDir)
            if (!lExists) {
                if (mFTPClient.makeDirectory(lDir)) mFTPClient.changeWorkingDirectory(lDir) else {
                    mFTPClient.changeWorkingDirectory(lOriginalWorkingDir)
                    return false
                }
            }
        }
        mFTPClient.changeWorkingDirectory(lOriginalWorkingDir)
        return true
    }

    private fun closeDownloadStreams(iLocalStream: OutputStream?, iRemoteStream: InputStream?) {
        LogManager.info(TAG, "Closing download streams")
        try {
            if (iLocalStream != null) iLocalStream.close()
            if (iRemoteStream != null) iRemoteStream.close()
            LogManager.info(TAG, "Closing streams success")
        } catch (iEx: IOException) {
            LogManager.error(TAG, "Closing streams not working")
            iEx.printStackTrace()
        }
    }

    private fun closeUploadStreams(iLocalStream: FileInputStream?, iRemoteStream: OutputStream?) {
        LogManager.info(TAG, "Closing upload streams")
        try {
            if (iLocalStream != null) iLocalStream.close()
            if (iRemoteStream != null) iRemoteStream.close()
            LogManager.info(TAG, "Closing streams success")
        } catch (iEx: IOException) {
            LogManager.error(TAG, "Closing streams not working")
            iEx.printStackTrace()
        }
    }

    private fun setBinaryFileType(): Boolean {
        try {
            mFTPClient!!.setFileType(FTP.BINARY_FILE_TYPE)
            return true
        } catch (iE: IOException) {
            iE.printStackTrace()
            return false
        }
    }

    private fun settingAverageSpeed(iValue: Long) {
        if (mTurn == 0) mSpeedAverage1 = iValue else if (mTurn == 1) mSpeedAverage2 =
            iValue else if (mTurn == 2) mSpeedAverage3 =
            iValue else if (mTurn == 3) mSpeedAverage4 = iValue
        mTurn++
        if (mTurn == 4) {
            mSpeedAverage5 = iValue
            mTurn = 0
        }
    }

    private val averageSpeed: Long
        private get() {
            return ((mSpeedAverage1 + mSpeedAverage2 + mSpeedAverage3 + mSpeedAverage4 + mSpeedAverage5)) / 5
        }

    open interface OnTransferListener {
        fun onConnected(iPendingFile: PendingFile?)
        fun onConnectionLost(iPendingFile: PendingFile?)
        fun onTransferSuccess(iPendingFile: PendingFile?)
        fun onStateUpdateRequested(iPendingFile: PendingFile?)

        /**
         * Called when the file to download is already existing on the local storage.
         * You should call [.notifyExistingFileActionIsDefined] to make recover all the transfers
         *
         * @param iPendingFile Already existing file
         */
        fun onExistingFile(iPendingFile: PendingFile?)

        /**
         * @param iPendingFile File that it's impossible to download for any error
         */
        fun onFail(iPendingFile: PendingFile?) // TODO : Maybe add a error status

        /**
         * FTPTransfer has nothing to do anymore
         */
        fun onStop(iFTPTransfer: FTPTransfer?)
    }

    companion object {
        private val TAG: String = "FTP TRANSFER"
        private val UPDATE_TRANSFER_TIMER: Long = 75
        private val TRANSFER_FINISH_BREAK: Int = 100
        private val USER_WAIT_BREAK: Int = 300
        private var sFTPTransferInstances: MutableList<FTPTransfer>? = null
        private var mIsAskingActionForExistingFile: Boolean = false
        fun getFTPTransferInstance(iServerId: Int): Array<FTPTransfer> {
            LogManager.info(TAG, "Get FTP transfer instance")
            if (sFTPTransferInstances == null) sFTPTransferInstances = ArrayList()
            val lFTPTransferList: MutableList<FTPTransfer> = ArrayList()
            for (lItem: FTPTransfer in sFTPTransferInstances!!) {
                if (lItem.fTPServerId == iServerId) lFTPTransferList.add(lItem)
            }
            return lFTPTransferList.toTypedArray()
        }

        fun notifyExistingFileActionIsDefined() {
            mIsAskingActionForExistingFile = false
        }
    }
}
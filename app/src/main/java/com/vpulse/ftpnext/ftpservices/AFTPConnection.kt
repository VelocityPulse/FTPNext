package com.vpulse.ftpnext.ftpservices

import android.net.Network
import android.os.*
import com.vpulse.ftpnext.commons.Utils
import com.vpulse.ftpnext.core.*
import com.vpulse.ftpnext.core.NetworkManager.OnNetworkAvailable
import com.vpulse.ftpnext.core.NetworkManager.OnNetworkLost
import com.vpulse.ftpnext.database.DataBase
import com.vpulse.ftpnext.database.FTPServerTable.FTPServer
import com.vpulse.ftpnext.database.FTPServerTable.FTPServerDAO
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPConnectionClosedException
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.FTPReply
import org.greenrobot.eventbus.EventBus
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException

abstract class AFTPConnection {
    protected val mFTPClient: FTPClient?
    protected var mFTPServerDAO: FTPServerDAO?
    var fTPServer: FTPServer?
    var currentDirectory: FTPFile? = null

    protected var mHandlerConnection: Handler? = null
    private var mConnectionThread: Thread? = null
    private var mReconnectThread: Thread? = null
    private var mConnectionInterrupted: Boolean = false
    private var mReconnectionInterrupted: Boolean = false
    private var mOnNetworkAvailableCallback: OnNetworkAvailable? = null
    private var mOnNetworkLostCallback: OnNetworkLost? = null
    private var mOnConnectionLost: OnConnectionLost? = null

    constructor(iFTPServer: FTPServer?) {
        LogManager.info(TAG, "Constructor")
        mFTPServerDAO = DataBase.fTPServerDAO
        fTPServer = iFTPServer
        mFTPClient = FTPClient()
        initializeNetworkMonitoring()
        sInstanceNumber++
        startHandlerThread()
        EventBus.getDefault().register(this)
    }

    constructor(iServerId: Int) {
        LogManager.info(TAG, "Constructor")
        mFTPServerDAO = DataBase.fTPServerDAO
        fTPServer = mFTPServerDAO!!.fetchById(iServerId)
        mFTPClient = FTPClient()
        initializeNetworkMonitoring()
        sInstanceNumber++
        startHandlerThread()
        EventBus.getDefault().register(this)
    }

    open fun destroyConnection() {
        LogManager.info(TAG, "Destroy connection")
        sInstanceNumber--
        AppCore.networkManager!!.unsubscribeOnNetworkAvailable(mOnNetworkAvailableCallback)
        AppCore.networkManager!!.unsubscribeOnNetworkLost(mOnNetworkLostCallback)

        if (isConnecting) {
            abortConnection()
        }
        if (isReconnecting) {
            abortReconnection()
        }
        if (isLocallyConnected) {
            disconnect()
        }
        if (mHandlerConnection != null) {
            mHandlerConnection!!.removeCallbacksAndMessages(null)
            mHandlerConnection!!.looper.quitSafely()
        }
        EventBus.getDefault().unregister(this)
    }

    private fun initializeNetworkMonitoring() {
        LogManager.info(TAG, "Initialize network monitoring")
        mOnNetworkAvailableCallback = object : OnNetworkAvailable {
            override fun onNetworkAvailable(iIsWifi: Boolean, iNewNetwork: Network?) {
                LogManager.info(TAG, "On network available")
                if (isReconnecting) {
                    LogManager.info(TAG, "Already reconnecting")
                    return
                }
                if (mOnConnectionLost != null) {
                    mOnConnectionLost!!.onConnectionLost()
                }
            }
        }

        mOnNetworkLostCallback = object : OnNetworkLost {
            override fun onNetworkLost() {
                LogManager.info(TAG, "On network lost")
                if (isLocallyConnected) {
                    disconnect()
                    FTPLogManager.pushErrorLog("Connection lost")
                }
                if (isReconnecting) {
                    LogManager.info(TAG, "Already reconnecting")
                    return
                }
                if (mOnConnectionLost != null) mOnConnectionLost!!.onConnectionLost()
            }
        }
        AppCore.networkManager!!.subscribeNetworkAvailable(mOnNetworkAvailableCallback)
        AppCore.networkManager!!.subscribeOnNetworkLost(mOnNetworkLostCallback)
    }

    fun abortReconnection() {
        LogManager.info(TAG, "Abort reconnect")
        if (isReconnecting) {
            FTPLogManager.pushStatusLog("Aborting reconnection")
            mReconnectionInterrupted = true
        }
    }

    fun abortConnection() {
        LogManager.info(TAG, "Abort connection")
        if (mFTPClient!!.isConnected) {
            disconnect()
            return
        }
        if (isConnecting) {
            FTPLogManager.pushStatusLog("Aborting connection")
            mConnectionInterrupted = true
            mHandlerConnection!!.post(object : Runnable {
                override fun run() {
                    try {
                        mFTPClient.disconnect()
                    } catch (iE: IOException) {
                        iE.printStackTrace()
                    }
                }
            })
        }
    }

    fun reconnect(onConnectionRecover: OnConnectionRecover?) {
        LogManager.info(TAG, "Reconnect")
        mReconnectionInterrupted = false
        mReconnectThread = Thread(object : Runnable {
            override fun run() {
                if (isLocallyConnected) disconnect()
                while (isLocallyConnected) {
                    Utils.sleep(RECONNECTION_WAITING_TIME.toLong())
                }
                while (!isLocallyConnected && !mReconnectionInterrupted) {
                    if (!isConnecting) {
                        if (isLocallyConnected) disconnect()
                        while (isLocallyConnected) {
                            Utils.sleep(RECONNECTION_WAITING_TIME.toLong())
                        }
                        connect(object : OnConnectionResult {
                            override fun onSuccess() {
                                LogManager.info(TAG, "Reconnect success")
                                if (onConnectionRecover != null) {
                                    onConnectionRecover.onConnectionRecover()
                                }
                            }

                            override fun onFail(iErrorEnum: ErrorCodeDescription, iErrorCode: Int
                            ) {
                                LogManager.error(TAG, "Reconnect fail")
                                if (onConnectionRecover != null) {
                                    if (iErrorEnum == ErrorCodeDescription.ERROR_FAILED_LOGIN) {
                                        onConnectionRecover.onConnectionDenied(
                                            iErrorEnum, iErrorCode
                                        )
                                        mConnectionInterrupted = true
                                    } else if (iErrorEnum == ErrorCodeDescription.ERROR_ALREADY_CONNECTED) {
                                        onConnectionRecover.onConnectionRecover()
                                        mConnectionInterrupted = true
                                    } else if (iErrorEnum == ErrorCodeDescription.ERROR_SERVER_DENIED_CONNECTION) {
                                        onConnectionRecover.onConnectionDenied(
                                            iErrorEnum, iErrorCode
                                        )
                                        mConnectionInterrupted = true
                                    }
                                }
                            }
                        })
                    }
                    LogManager.info(TAG, "Reconnection waiting...")
                    Utils.sleep(RECONNECTION_WAITING_TIME.toLong())
                }
                LogManager.info(TAG, "Reconnection leaving")
            }
        })
        mReconnectThread!!.name = "FTP Reconnection"
        mReconnectThread!!.start()
    }

    open fun disconnect() {
        LogManager.info(TAG, "Disconnect")
        if (isLocallyConnected) {
            try {
                mFTPClient!!.disconnect()
            } catch (iE: IOException) {
                iE.printStackTrace()
            }
        } else {
            Exception("Thread disconnection but not connected").printStackTrace()
        }
    }

    @Synchronized
    fun connect(onConnectionResult: OnConnectionResult?) {
        LogManager.info(TAG, "Connect")
        if (isLocallyConnected) {
            LogManager.error(TAG, "Trying a connection but is already connected")
            Exception("already connected").printStackTrace()
            onConnectionResult?.onFail(
                ErrorCodeDescription.ERROR_ALREADY_CONNECTED, FTPReply.CANNOT_OPEN_DATA_CONNECTION
            )
            return
        } else if (isConnecting) {
            LogManager.error(TAG, "Trying a connection but is already connecting")
            Exception("already connecting").printStackTrace()
            onConnectionResult?.onFail(
                ErrorCodeDescription.ERROR_ALREADY_CONNECTING, FTPReply.CANNOT_OPEN_DATA_CONNECTION
            )
            return
        }
        if (!AppCore.networkManager!!.isNetworkAvailable) {
            LogManager.error(TAG, "Connection : Network not available")
            onConnectionResult?.onFail(
                ErrorCodeDescription.ERROR_NO_INTERNET, FTPReply.CANNOT_OPEN_DATA_CONNECTION
            )
            return
        }
        if (PreferenceManager.isWifiOnly && !AppCore.networkManager!!.isCurrentNetworkIsWifi) {
            LogManager.error(TAG, "Connection : Only allowed to connect in Wi-Fi")
            onConnectionResult?.onFail(
                ErrorCodeDescription.ERROR_CONNECTION_ONLY_IN_WIFI,
                FTPReply.CANNOT_OPEN_DATA_CONNECTION
            )
            return
        }
        mConnectionInterrupted = false
        mConnectionThread = Thread(object : Runnable {
            override fun run() {
                try {
                    LogManager.info(TAG, "Will connect with : \n" + fTPServer.toString())
                    FTPLogManager.pushStatusLog("Will connect with " + fTPServer!!.server)
                    mFTPClient!!.controlEncoding = "UTF-8"
                    mFTPClient.defaultPort = fTPServer!!.port
                    mFTPClient.connect(InetAddress.getByName(fTPServer!!.server))
                    mFTPClient.soTimeout = TIMEOUT_SERVER_ANSWER // 15s
                    if (mConnectionInterrupted) {
                        mFTPClient.disconnect()
                        onConnectionResult?.onFail(
                            ErrorCodeDescription.ERROR_CONNECTION_INTERRUPTED, 426
                        )
                        return
                    }
                    mFTPClient.login(fTPServer!!.user, fTPServer!!.pass)
                    if (mConnectionInterrupted) {
                        mFTPClient.disconnect()
                        onConnectionResult?.onFail(
                            ErrorCodeDescription.ERROR_CONNECTION_INTERRUPTED, 426
                        )
                        return
                    }
                    if (!FTPReply.isPositiveCompletion(mFTPClient.replyCode) || !isLocallyConnected) {
                        FTPLogManager.pushErrorLog("Server \"" + fTPServer!!.name + "\" refused connection")
                        LogManager.error(TAG, "Server refused connection.")
                        mFTPClient.disconnect()
                        if (onConnectionResult != null) {
                            if (mFTPClient.replyCode == FTPReply.NOT_LOGGED_IN) onConnectionResult.onFail(
                                ErrorCodeDescription.ERROR_FAILED_LOGIN, mFTPClient.replyCode
                            ) else onConnectionResult.onFail(
                                ErrorCodeDescription.ERROR, mFTPClient.replyCode
                            )
                        }
                        return
                    }
                    LogManager.info(TAG, "FTPClient connected")
                    FTPLogManager.pushSuccessLog("Connected to \"" + fTPServer!!.name + "\"")
                    onConnectionResult?.onSuccess()
                } catch (iE: UnknownHostException) {
                    iE.printStackTrace()
                    if (onConnectionResult != null) {
                        if (mConnectionInterrupted) onConnectionResult.onFail(
                            ErrorCodeDescription.ERROR_CONNECTION_INTERRUPTED,
                            mFTPClient!!.replyCode
                        ) else onConnectionResult.onFail(
                            ErrorCodeDescription.ERROR_UNKNOWN_HOST, mFTPClient!!.replyCode
                        )
                    } //                  onConnectionResult.onFail(ErrorCodeDescription.ERROR_UNKNOWN_HOST, 434);
                } catch (iE: FTPConnectionClosedException) {
                    if (onConnectionResult != null) {
                        if (mConnectionInterrupted) onConnectionResult.onFail(
                            ErrorCodeDescription.ERROR_CONNECTION_INTERRUPTED,
                            mFTPClient!!.replyCode
                        ) else onConnectionResult.onFail(
                            ErrorCodeDescription.ERROR_SERVER_DENIED_CONNECTION,
                            mFTPClient!!.replyCode
                        )
                    }
                } catch (iE: Exception) {
                    iE.printStackTrace()
                    if (onConnectionResult != null) {
                        if (mConnectionInterrupted) onConnectionResult.onFail(
                            ErrorCodeDescription.ERROR_CONNECTION_INTERRUPTED,
                            mFTPClient!!.replyCode
                        ) else onConnectionResult.onFail(
                            ErrorCodeDescription.ERROR, mFTPClient!!.replyCode
                        )
                    } //                  onConnectionResult.onFail(ErrorCodeDescription.ERROR, FTPReply.UNRECOGNIZED_COMMAND);
                }
            }
        })
        mConnectionThread!!.name = "FTP Connection"
        mConnectionThread!!.start()
    }

    fun isRemotelyConnectedAsync(iCallback: OnRemotelyConnectedResult?) {
        if (iCallback == null) {
            LogManager.error(TAG, "Asking remotely connected but without callback")
            return
        }

        // New Thread necessary to don't block mHandlerConnection
        Thread({ iCallback.onResult(isRemotelyConnected) }, "FTP Noop").start()
    }

    protected val isRemotelyConnected: Boolean
        protected get() {
            if (mFTPClient == null) return false
            var lNoopResult = false
            try {
                lNoopResult = mFTPClient.sendNoOp()
                LogManager.info(TAG, "Send noop success")
            } catch (iE: IOException) {
                LogManager.error(TAG, "Send noop exception : " + iE.message)
            }
            return lNoopResult
        }
    val isLocallyConnected: Boolean
        get() {
            return mFTPClient!!.isConnected
        }
    val fTPServerId: Int
        get() {
            return fTPServer!!.dataBaseId
        }
    val isConnecting: Boolean
        get() {
            return mConnectionThread != null && mConnectionThread!!.isAlive
        }
    val isReconnecting: Boolean
        get() {
            return mReconnectThread != null && mReconnectThread!!.isAlive
        }
    abstract val isBusy: Boolean
    fun setOnConnectionLost(iOnConnectionLost: OnConnectionLost?) {
        mOnConnectionLost = iOnConnectionLost
    }

    val currentDirectoryPath: String
        get() {
            var oCurrentDirectoryPath: String = currentDirectory!!.name
            if (!oCurrentDirectoryPath.endsWith("/")) oCurrentDirectoryPath += "/"
            return oCurrentDirectoryPath
        }

    protected abstract val connectionType: Int

    private fun startHandlerThread() {
        if (mHandlerConnection == null && THREAD_STATUS_ACTIVATED) {
            val lHandlerThread: HandlerThread = HandlerThread("FTP Handler " + sInstanceNumber)
            lHandlerThread.start()
            mHandlerConnection = Handler(lHandlerThread.looper)
            val lCodeReplyUpdate: Runnable = object : Runnable {
                var lLastCode: Int = -1
                override fun run() {
                    if (lLastCode != mFTPClient!!.replyCode) {
                        lLastCode = mFTPClient.replyCode
                        LogManager.info(TAG, "code reply : " + lLastCode)
                        FTPLogManager.pushCodeReplyLog(lLastCode)
                    }
                    if (mHandlerConnection!!.looper.thread.isAlive) mHandlerConnection!!.postDelayed(
                        this, 100
                    )
                }
            }
            mHandlerConnection!!.post(lCodeReplyUpdate)
        }
    }

    enum class ErrorCodeDescription {
        ERROR,
        ERROR_UNKNOWN_HOST,
        ERROR_CONNECTION_TIMEOUT,
        ERROR_ALREADY_CONNECTED,
        ERROR_ALREADY_CONNECTING,
        ERROR_CONNECTION_ONLY_IN_WIFI,
        ERROR_CONNECTION_INTERRUPTED,
        ERROR_DIRECTORY_ALREADY_EXISTING,
        ERROR_NO_INTERNET,
        ERROR_FAILED_LOGIN,
        ERROR_NOT_REACHABLE,
        ERROR_NOT_A_DIRECTORY,
        ERROR_EXECUTE_PERMISSION_MISSED,
        ERROR_READ_PERMISSION_MISSED,
        ERROR_SERVER_DENIED_CONNECTION
    }

    open interface OnRemotelyConnectedResult {
        fun onResult(iResult: Boolean)
    }

    open interface OnConnectionResult {
        fun onSuccess()
        fun onFail(iErrorEnum: ErrorCodeDescription, iErrorCode: Int)
    }

    open interface OnConnectionLost {
        fun onConnectionLost()
    }

    open interface OnConnectionRecover {
        fun onConnectionRecover()
        fun onConnectionDenied(iErrorEnum: ErrorCodeDescription, iErrorCode: Int)
    }

    companion object {
         const val CONNECTION_TRANSFER_TYPE: Int = 1
         const val CONNECTION_SERVICES_TYPE: Int = 2
         const val TIMEOUT_SERVER_ANSWER: Int = 15000
         const val RECONNECTION_WAITING_TIME: Int = 1200
        private const val TAG: String = "FTP CONNECTION"
        private const val THREAD_STATUS_ACTIVATED: Boolean = true
        private var sInstanceNumber: Int = 0
    }
}
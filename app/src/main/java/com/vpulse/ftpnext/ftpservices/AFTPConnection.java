package com.vpulse.ftpnext.ftpservices;

import android.net.Network;
import android.os.Handler;
import android.os.HandlerThread;

import com.vpulse.ftpnext.commons.Utils;
import com.vpulse.ftpnext.core.AppCore;
import com.vpulse.ftpnext.core.LogManager;
import com.vpulse.ftpnext.core.NetworkManager;
import com.vpulse.ftpnext.core.PreferenceManager;
import com.vpulse.ftpnext.database.DataBase;
import com.vpulse.ftpnext.database.FTPServerTable.FTPServer;
import com.vpulse.ftpnext.database.FTPServerTable.FTPServerDAO;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

public abstract class AFTPConnection {

    protected static final int CONNECTION_TRANSFER_TYPE = 1;
    protected static final int CONNECTION_SERVICES_TYPE = 2;
    protected static final int TIMEOUT_SERVER_ANSWER = 15000;
    protected static final int RECONNECTION_WAITING_TIME = 1200;

    private static final String TAG = "FTP CONNECTION";
    private static final boolean THREAD_STATUS_ACTIVATED = true;

    private static int sInstanceNumber = 0;

    protected final FTPClient mFTPClient;
    protected FTPServerDAO mFTPServerDAO;
    protected FTPServer mFTPServer;
    protected FTPFile mCurrentDirectory;

    protected Handler mHandlerConnection;

    private Thread mConnectionThread;
    private Thread mReconnectThread;

    private boolean mConnectionInterrupted;
    private boolean mReconnectionInterrupted;

    private NetworkManager.OnNetworkAvailable mOnNetworkAvailableCallback;
    private NetworkManager.OnNetworkLost mOnNetworkLostCallback;
    private OnConnectionLost mOnConnectionLost;

    public AFTPConnection(FTPServer iFTPServer) {
        LogManager.info(TAG, "Constructor");
        mFTPServerDAO = DataBase.getFTPServerDAO();
        mFTPServer = iFTPServer;
        mFTPClient = new FTPClient();
        initializeNetworkMonitoring();
        sInstanceNumber++;
    }

    public AFTPConnection(int iServerId) {
        LogManager.info(TAG, "Constructor");
        mFTPServerDAO = DataBase.getFTPServerDAO();
        mFTPServer = mFTPServerDAO.fetchById(iServerId);
        mFTPClient = new FTPClient();
        initializeNetworkMonitoring();
        sInstanceNumber++;
    }

    public void destroyConnection() {
        LogManager.info(TAG, "Destroy connection");
        sInstanceNumber--;

        AppCore.getNetworkManager().unsubscribeOnNetworkAvailable(mOnNetworkAvailableCallback);
        AppCore.getNetworkManager().unsubscribeOnNetworkLost(mOnNetworkLostCallback);

        if (isConnecting())
            abortConnection();
        if (isReconnecting())
            abortReconnection();

        if (isLocallyConnected())
            disconnect();

        if (mHandlerConnection != null)
            mHandlerConnection.getLooper().quitSafely();
    }

    private void initializeNetworkMonitoring() {
        LogManager.info(TAG, "Initialize network monitoring");
        mOnNetworkAvailableCallback = new NetworkManager.OnNetworkAvailable() {
            @Override
            public void onNetworkAvailable(boolean iIsWifi, Network iNewNetwork) {
                LogManager.info(TAG, "On network available");

                if (isReconnecting()) {
                    LogManager.info(TAG, "Already reconnecting");
                    return;
                }
                if (mOnConnectionLost != null)
                    mOnConnectionLost.onConnectionLost();
            }
        };
        mOnNetworkLostCallback = new NetworkManager.OnNetworkLost() {
            @Override
            public void onNetworkLost() {
                LogManager.info(TAG, "On network lost");
                FTPLogManager.pushErrorLog("Losing network connection");

                if (isReconnecting()) {
                    LogManager.info(TAG, "Already reconnecting");
                    return;
                }
                if (mOnConnectionLost != null)
                    mOnConnectionLost.onConnectionLost();
            }
        };
        AppCore.getNetworkManager().subscribeNetworkAvailable(mOnNetworkAvailableCallback);
        AppCore.getNetworkManager().subscribeOnNetworkLost(mOnNetworkLostCallback);
    }


    public void abortReconnection() {
        LogManager.info(TAG, "Abort reconnect");
        FTPLogManager.pushStatusLog("Aborting reconnection");

        if (isReconnecting())
            mReconnectionInterrupted = true;
    }

    public void abortConnection() {
        LogManager.info(TAG, "Abort connection");
        FTPLogManager.pushStatusLog("Aborting connection");

        if (mFTPClient.isConnected()) {
            disconnect();
            return;
        }
        if (isConnecting()) {
            mConnectionInterrupted = true;
            mHandlerConnection.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        mFTPClient.abort();
                    } catch (IOException iE) {
                        iE.printStackTrace();
                    }
                }
            });
        }
    }

    public void reconnect(final OnConnectionRecover onConnectionRecover) {
        LogManager.info(TAG, "Reconnect");

        mReconnectionInterrupted = false;
        mReconnectThread = new Thread(new Runnable() {
            @Override
            public void run() {
                if (isLocallyConnected())
                    disconnect();
                while (isLocallyConnected()) {
                    Utils.sleep(RECONNECTION_WAITING_TIME);
                }

                while (!isLocallyConnected() && !mReconnectionInterrupted) {

                    if (!isConnecting()) {
                        if (isLocallyConnected())
                            disconnect();
                        while (isLocallyConnected()) {
                            Utils.sleep(RECONNECTION_WAITING_TIME);
                        }

                        connect(new OnConnectionResult() {
                            @Override
                            public void onSuccess() {
                                LogManager.info(TAG, "Reconnect success");
                                if (onConnectionRecover != null) {
                                    onConnectionRecover.onConnectionRecover();
                                }
                            }

                            @Override
                            public void onFail(ErrorCodeDescription iErrorEnum, int iErrorCode) {
                                LogManager.error(TAG, "Reconnect fail");
                                if (onConnectionRecover != null) {

                                    if (iErrorEnum == ErrorCodeDescription.ERROR_FAILED_LOGIN) {
                                        onConnectionRecover.onConnectionDenied(iErrorEnum,
                                                iErrorCode);
                                        mConnectionInterrupted = true;
                                    } else if (iErrorEnum == ErrorCodeDescription.ERROR_ALREADY_CONNECTED) {
                                        onConnectionRecover.onConnectionRecover();
                                        mConnectionInterrupted = true;
                                    }
                                }
                            }
                        });
                    }
                    LogManager.info(TAG, "Reconnection waiting...");
                    Utils.sleep(RECONNECTION_WAITING_TIME);
                }
                LogManager.info(TAG, "Reconnection leaving");
            }
        });
        mReconnectThread.setName("FTP Reconnection");
        mReconnectThread.start();
    }

    public void disconnect() {
        LogManager.info(TAG, "Disconnect");
        if (isLocallyConnected()) {
            try {
                mFTPClient.disconnect();
                FTPLogManager.pushStatusLog("Disconnection");
            } catch (IOException iE) {
                iE.printStackTrace();
            }
        } else {
            new Exception("Thread disconnection but not connected").printStackTrace();
        }
    }

    public synchronized void connect(final OnConnectionResult onConnectionResult) {
        LogManager.info(TAG, "Connect");
        if (isLocallyConnected()) {
            LogManager.error(TAG, "Trying a connection but is already connected");
            new Exception("already connected").printStackTrace();
            if (onConnectionResult != null)
                onConnectionResult.onFail(ErrorCodeDescription.ERROR_ALREADY_CONNECTED,
                        FTPReply.CANNOT_OPEN_DATA_CONNECTION);
            return;
        } else if (isConnecting()) {
            LogManager.error(TAG, "Trying a connection but is already connecting");
            new Exception("already connecting").printStackTrace();
            if (onConnectionResult != null)
                onConnectionResult.onFail(ErrorCodeDescription.ERROR_ALREADY_CONNECTING,
                        FTPReply.CANNOT_OPEN_DATA_CONNECTION);
            return;
        }

        if (!AppCore.getNetworkManager().isNetworkAvailable()) {
            LogManager.error(TAG, "Connection : Network not available");
            if (onConnectionResult != null)
                onConnectionResult.onFail(ErrorCodeDescription.ERROR_NO_INTERNET,
                        FTPReply.CANNOT_OPEN_DATA_CONNECTION);
            return;
        }

        if (PreferenceManager.isWifiOnly() && !AppCore.getNetworkManager().isCurrentNetworkIsWifi()) {
            LogManager.error(TAG, "Connection : Only allowed to connect in Wi-Fi");
            if (onConnectionResult != null)
                onConnectionResult.onFail(ErrorCodeDescription.ERROR_CONNECTION_ONLY_IN_WIFI,
                        FTPReply.CANNOT_OPEN_DATA_CONNECTION);
            return;
        }

        mConnectionInterrupted = false;
        mConnectionThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    LogManager.info(TAG, "Will connect with : \n" + mFTPServer.toString());
                    FTPLogManager.pushStatusLog("Connection with " + mFTPServer.getServer());

                    mFTPClient.setControlEncoding("UTF-8");
                    mFTPClient.setDefaultPort(mFTPServer.getPort());
                    mFTPClient.connect(InetAddress.getByName(mFTPServer.getServer()));
                    mFTPClient.setSoTimeout(TIMEOUT_SERVER_ANSWER); // 15s
                    if (mConnectionInterrupted) {
                        mFTPClient.disconnect();
                        if (onConnectionResult != null)
                            onConnectionResult.onFail(ErrorCodeDescription.ERROR_CONNECTION_INTERRUPTED,
                                    426);
                        return;
                    }

                    mFTPClient.login(mFTPServer.getUser(), mFTPServer.getPass());
                    if (mConnectionInterrupted) {
                        mFTPClient.disconnect();
                        if (onConnectionResult != null)
                            onConnectionResult.onFail(ErrorCodeDescription.ERROR_CONNECTION_INTERRUPTED,
                                    426);
                        return;
                    }

                    if (!FTPReply.isPositiveCompletion(mFTPClient.getReplyCode()) || !isLocallyConnected()) {
                        FTPLogManager.pushErrorLog("Server \"" + mFTPServer.getName() + "\" refused connection");
                        LogManager.error(TAG, "FTP server refused connection.");

                        mFTPClient.disconnect();
                        if (onConnectionResult != null) {
                            if (mFTPClient.getReplyCode() == FTPReply.NOT_LOGGED_IN)
                                onConnectionResult.onFail(ErrorCodeDescription.ERROR_FAILED_LOGIN,
                                        mFTPClient.getReplyCode());
                            else
                                onConnectionResult.onFail(ErrorCodeDescription.ERROR,
                                        mFTPClient.getReplyCode());
                        }
                        return;
                    }

                    startStatusThread();

                    FTPLogManager.pushSuccessLog("Connection \"" + mFTPServer.getName() + "\"");
                    LogManager.info(TAG, "FTPClient connected");

                    if (onConnectionResult != null)
                        onConnectionResult.onSuccess();

                } catch (UnknownHostException iE) {
                    iE.printStackTrace();
                    if (onConnectionResult != null)
                        onConnectionResult.onFail(ErrorCodeDescription.ERROR_UNKNOWN_HOST,
                                mFTPClient.getReplyCode());
//                      onConnectionResult.onFail(ErrorCodeDescription.ERROR_UNKNOWN_HOST, 434);
                } catch (Exception iE) {
                    iE.printStackTrace();
                    if (onConnectionResult != null)
                        onConnectionResult.onFail(ErrorCodeDescription.ERROR,
                                mFTPClient.getReplyCode());
//                      onConnectionResult.onFail(ErrorCodeDescription.ERROR, FTPReply.UNRECOGNIZED_COMMAND);
                }
            }
        });
        mConnectionThread.setName("FTP Connection");
        mConnectionThread.start();
    }

    public void isRemotelyConnectedAsync(final OnRemotelyConnectedResult iCallback) {
        if (iCallback == null) {
            LogManager.error(TAG, "Asking remotely connected but without callback");
            return;
        }
        LogManager.debug(TAG, "Is remotely connected async");

        // New Thread necessary to don't block mHandlerConnection
        new Thread(new Runnable() {
            @Override
            public void run() {
                iCallback.onResult(isRemotelyConnected());
            }
        }, "FTP Noop").start();
    }

    protected boolean isRemotelyConnected() {
        if (mFTPClient == null)
            return false;

        boolean lNoopResult = false;
        try {
            lNoopResult = mFTPClient.sendNoOp();
            LogManager.info(TAG, "Send noop success");
        } catch (IOException iE) {
            LogManager.error(TAG, "Send noop exception : " + iE.getMessage());
        }
        return lNoopResult;
    }

    public boolean isLocallyConnected() {
        return mFTPClient.isConnected();
    }

    public FTPServer getFTPServer() {
        return mFTPServer;
    }

    public int getFTPServerId() {
        return mFTPServer.getDataBaseId();
    }

    public FTPFile getCurrentDirectory() {
        return mCurrentDirectory;
    }


    public boolean isConnecting() {
        return mConnectionThread != null && mConnectionThread.isAlive();
    }

    public boolean isReconnecting() {
        return mReconnectThread != null && mReconnectThread.isAlive();
    }

    public abstract boolean isBusy();

    public void setOnConnectionLost(OnConnectionLost iOnConnectionLost) {
        mOnConnectionLost = iOnConnectionLost;
    }

    public String getCurrentDirectoryPath() {
        String oCurrentDirectoryPath = mCurrentDirectory.getName();
        if (!oCurrentDirectoryPath.endsWith("/"))
            oCurrentDirectoryPath += "/";

        return oCurrentDirectoryPath;
    }

    protected abstract int getConnectionType();

    private void startStatusThread() {
        if (mHandlerConnection == null && THREAD_STATUS_ACTIVATED) {

            HandlerThread lHandlerThread = new HandlerThread("FTP Handler " + sInstanceNumber);
            lHandlerThread.start();
            mHandlerConnection = new Handler(lHandlerThread.getLooper());

            final Runnable lCodeReplyUpdate = new Runnable() {
                int lLastCode = -1;

                @Override
                public void run() {
                    if (lLastCode != mFTPClient.getReplyCode()) {
                        lLastCode = mFTPClient.getReplyCode();
                        LogManager.debug(TAG, "code reply : " + lLastCode);
                        FTPLogManager.pushCodeReplyLog(lLastCode);
                    }

                    if (mHandlerConnection.getLooper().getThread().isAlive())
                        mHandlerConnection.postDelayed(this, 100);
                }
            };

            mHandlerConnection.post(lCodeReplyUpdate);
        }
    }

    public enum ErrorCodeDescription {
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
    }

    public interface OnRemotelyConnectedResult {
        void onResult(boolean iResult);
    }

    public interface OnConnectionResult {
        void onSuccess();

        void onFail(ErrorCodeDescription iErrorEnum, int iErrorCode);
    }

    public interface OnConnectionLost {
        void onConnectionLost();
    }

    public interface OnConnectionRecover {
        void onConnectionRecover();

        void onConnectionDenied(ErrorCodeDescription iErrorEnum, int iErrorCode);
    }
}
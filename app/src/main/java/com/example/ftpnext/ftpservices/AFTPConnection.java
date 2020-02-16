package com.example.ftpnext.ftpservices;

import android.net.Network;

import com.example.ftpnext.core.AppCore;
import com.example.ftpnext.core.LogManager;
import com.example.ftpnext.core.NetworkManager;
import com.example.ftpnext.database.DataBase;
import com.example.ftpnext.database.FTPServerTable.FTPServer;
import com.example.ftpnext.database.FTPServerTable.FTPServerDAO;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPClientConfig;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

// TODO : Download save when app is killed

public abstract class AFTPConnection {

    private static final String TAG = "FTP CONNECTION";

    private static final int RECONNECTION_WAITING_TIME = 700;
    private static final boolean REPLY_THREAD_STATUS_ACTIVATED = true;

    protected FTPServerDAO mFTPServerDAO;
    protected FTPServer mFTPServer;
    protected FTPClient mFTPClient;
    protected FTPFile mCurrentDirectory;

    private Thread mConnectionThread;
    private Thread mReconnectThread;
    private Thread mReplyStatusThread;

    private NetworkManager.OnNetworkAvailable mOnNetworkAvailableCallback;
    private NetworkManager.OnNetworkLost mOnNetworkLostCallback;
    private IOnConnectionLost mIOnConnectionLost;

    private boolean mAbortReconnect;

    public AFTPConnection(FTPServer iFTPServer) {
        mFTPServerDAO = DataBase.getFTPServerDAO();
        mFTPServer = iFTPServer;
        mFTPClient = new FTPClient();
        initializeNetworkMonitoring();
    }

    public AFTPConnection(int iServerId) {
        mFTPServerDAO = DataBase.getFTPServerDAO();
        mFTPServer = mFTPServerDAO.fetchById(iServerId);
        mFTPClient = new FTPClient();
        initializeNetworkMonitoring();
    }

    public void destroyConnection() {
        LogManager.info(TAG, "Destroy connection");

        AppCore.getNetworkManager().unsubscribeOnNetworkAvailable(mOnNetworkAvailableCallback);
        AppCore.getNetworkManager().unsubscribeOnNetworkLost(mOnNetworkLostCallback);
        if (isConnected())
            disconnect();

        if (mReplyStatusThread != null)
            mReplyStatusThread.interrupt();
    }

    private void initializeNetworkMonitoring() {
        LogManager.info(TAG, "Initialize network monitoring");
        mOnNetworkAvailableCallback = new NetworkManager.OnNetworkAvailable() {
            @Override
            public void onNetworkAvailable(boolean iIsWifi, Network iNewNetwork) {
                LogManager.info(TAG, "On network available");
                mFTPClient.setSocketFactory(iNewNetwork.getSocketFactory());

                if (isReconnecting()) {
                    LogManager.info(TAG, "Already reconnecting");
                    return;
                }
                if (mIOnConnectionLost != null)
                    mIOnConnectionLost.onConnectionLost();
            }
        };
        mOnNetworkLostCallback = new NetworkManager.OnNetworkLost() {
            @Override
            public void onNetworkLost() {
                LogManager.info(TAG, "On network lost");
                if (isReconnecting()) {
                    LogManager.info(TAG, "Already reconnecting");
                    return;
                }
                if (mIOnConnectionLost != null)
                    mIOnConnectionLost.onConnectionLost();
            }
        };
        AppCore.getNetworkManager().subscribeNetworkAvailable(mOnNetworkAvailableCallback);
        AppCore.getNetworkManager().subscribeOnNetworkLost(mOnNetworkLostCallback);
    }


    public void abortReconnection() {
        LogManager.info(TAG, "Abort reconnect");

        if (isReconnecting())
            mAbortReconnect = true; // To test
    }

    public void abortConnection() {
        LogManager.info(TAG, "Abort connection");

        if (mFTPClient.isConnected()) {
            disconnect();
            return;
        }
        if (isConnecting()) {
            mConnectionThread.interrupt();
        }
    }

    public void reconnect(final OnConnectionRecover onConnectionRecover) {
        LogManager.info(TAG, "Reconnect");
        mAbortReconnect = false;

        mReconnectThread = new Thread(new Runnable() {
            @Override
            public void run() {
                if (isConnected())
                    disconnect();

                while (isConnected()) {
                    try {
                        Thread.sleep(RECONNECTION_WAITING_TIME);
                    } catch (InterruptedException iE) {
                        iE.printStackTrace();
                    }
                }

                while (!isConnected() && !mAbortReconnect) {
                    if (!isConnecting()) {
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
                                LogManager.info(TAG, "Reconnect fail");
                                if (iErrorEnum == ErrorCodeDescription.ERROR_FAILED_LOGIN) {
                                    onConnectionRecover.onConnectionDenied(iErrorEnum,
                                            iErrorCode);
                                    mAbortReconnect = true;
                                }
                            }
                        });
                        try {
                            LogManager.info(TAG, "Reconnection waiting...");
                            Thread.sleep(RECONNECTION_WAITING_TIME);
                        } catch (InterruptedException iE) {
                            iE.printStackTrace();
                        }
                    }
                }
            }
        });
        mReconnectThread.start();
    }

    public void disconnect() {
        LogManager.info(TAG, "Disconnect");
        if (isConnected()) {
            try {
                mFTPClient.disconnect();
            } catch (IOException iE) {
                iE.printStackTrace();
            }
        } else {
            new Exception("Thread disconnection but not connected").printStackTrace();
        }
    }

    public void connect(final OnConnectionResult onConnectionResult) {
        LogManager.info(TAG, "Connect");
        if (isConnected()) {
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

        mConnectionThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    LogManager.info(TAG, "Will connect with : \n" + mFTPServer.toString());

                    mFTPClient.setControlEncoding("UTF-8");
                    mFTPClient.setDefaultPort(mFTPServer.getPort());
                    mFTPClient.connect(InetAddress.getByName(mFTPServer.getServer()));
                    mFTPClient.setSoTimeout(15000); // 15s
                    if (Thread.interrupted()) {
                        mFTPClient.disconnect();
                        if (onConnectionResult != null)
                            onConnectionResult.onFail(ErrorCodeDescription.ERROR_CONNECTION_INTERRUPTED,
                                    426);
                        return;
                    }

                    mFTPClient.login(mFTPServer.getUser(), mFTPServer.getPass());
                    if (Thread.interrupted()) {
                        mFTPClient.disconnect();
                        if (onConnectionResult != null)
                            onConnectionResult.onFail(ErrorCodeDescription.ERROR_CONNECTION_INTERRUPTED,
                                    426);
                        return;
                    }

                    if (!FTPReply.isPositiveCompletion(mFTPClient.getReplyCode())) {
                        LogManager.info(TAG, "FTPClient code : " + mFTPClient.getReplyCode());
                        mFTPClient.disconnect();
                        LogManager.error(TAG, "FTP server refused connection.");
                        if (onConnectionResult != null) {
                            if (mFTPClient.getReplyCode() == FTPReply.NOT_LOGGED_IN)
                                onConnectionResult.onFail(ErrorCodeDescription.ERROR_FAILED_LOGIN,
                                        mFTPClient.getReplyCode());
                            else
                                onConnectionResult.onFail(ErrorCodeDescription.ERROR,
                                        mFTPClient.getReplyCode());
                        }
                        return;
                    } else {
                        LogManager.info(TAG, "FTPClient status : " + mFTPClient.getStatus());
                        LogManager.info(TAG, "FTPClient code : " + mFTPClient.getReplyCode());
                    }

                    if (isConnected()) {
                        startReplyStatusThread();
                        LogManager.info(TAG, "FTPClient connected");
                        if (onConnectionResult != null)
                            onConnectionResult.onSuccess();
                    } else
                        LogManager.error(TAG, "FTPClient not connected");
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
        mConnectionThread.start();
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

    public boolean isConnected() {
        return mFTPClient.isConnected();
    }

    public boolean isConnecting() {
        return mConnectionThread != null && mConnectionThread.isAlive();
    }

    public boolean isReconnecting() {
        return mReconnectThread != null && mReconnectThread.isAlive();
    }

    public abstract boolean isBusy();

    public void setIOnConnectionLost(IOnConnectionLost iIOnConnectionLost) {
        mIOnConnectionLost = iIOnConnectionLost;
    }

    private void startReplyStatusThread() {
        if (mReplyStatusThread == null && REPLY_THREAD_STATUS_ACTIVATED) {
            mReplyStatusThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        int lLastCode = -1;
                        while (!Thread.interrupted()) {
                            if (lLastCode != mFTPClient.getReplyCode()) {
                                lLastCode = mFTPClient.getReplyCode();
                                LogManager.debug(TAG, "code reply : " + lLastCode);
                            }
                            Thread.sleep(100);
                        }
                    } catch (InterruptedException iE) {
                        iE.printStackTrace();
                    }
                }
            });
            mReplyStatusThread.start();
        }
    }

    public enum ErrorCodeDescription {
        ERROR,
        ERROR_UNKNOWN_HOST,
        ERROR_CONNECTION_TIMEOUT,
        ERROR_ALREADY_CONNECTED,
        ERROR_ALREADY_CONNECTING,
        ERROR_CONNECTION_INTERRUPTED,
        ERROR_DIRECTORY_ALREADY_EXISTING,
        ERROR_NO_INTERNET,
        ERROR_FAILED_LOGIN,
        ERROR_NOT_REACHABLE,
        ERROR_NOT_A_DIRECTORY,
        ERROR_EXECUTE_PERMISSION_MISSED,
        ERROR_READ_PERMISSION_MISSED,
    }

    public interface OnConnectionResult {
        void onSuccess();

        void onFail(ErrorCodeDescription iErrorEnum, int iErrorCode);
    }

    public interface IOnConnectionLost {
        void onConnectionLost();
    }

    public interface OnConnectionRecover {
        void onConnectionRecover();

        void onConnectionDenied(ErrorCodeDescription iErrorEnum, int iErrorCode);
    }
}
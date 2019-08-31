package com.example.ftpnext;

import android.net.Network;

import com.example.ftpnext.core.AppCore;
import com.example.ftpnext.core.LogManager;
import com.example.ftpnext.core.NetworkManager;
import com.example.ftpnext.database.DataBase;
import com.example.ftpnext.database.FTPServerTable.FTPServer;
import com.example.ftpnext.database.FTPServerTable.FTPServerDAO;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPListParseEngine;
import org.apache.commons.net.ftp.FTPReply;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

// TODO : Download save when app is killed

public class FTPConnection {

    private static String TAG = "FTP CONNECTION";

    private static int ITEM_FETCHED_BY_GROUP = 25;
    private static List<FTPConnection> sFTPConnectionInstances;

    private FTPServerDAO mFTPServerDAO;
    private FTPServer mFTPServer;
    private FTPClient mFTPClient;
    private String mLocalization;
    private Thread mConnectionThread;
    private Thread mReconnectThread;
    private Thread mDirectoryFetchThread;
    private NetworkManager.OnNetworkAvailable mOnNetworkAvailableCallback;
    private NetworkManager.OnNetworkLost mOnNetworkLostCallback;
    private OnConnectionLost mOnConnectionLost;
    private boolean mAbortReconnect;
    private Thread mReplyStatusThread;

    public FTPConnection(FTPServer iFTPServer) {
        if (sFTPConnectionInstances == null)
            sFTPConnectionInstances = new ArrayList<>();

        mFTPServerDAO = DataBase.getFTPServerDAO();
        mFTPServer = iFTPServer;
        sFTPConnectionInstances.add(this);
        mFTPClient = new FTPClient();
        initializeNetworkMonitoring();
    }

    public FTPConnection(int iServerId) {
        if (sFTPConnectionInstances == null)
            sFTPConnectionInstances = new ArrayList<>();

        mFTPServerDAO = DataBase.getFTPServerDAO();
        mFTPServer = mFTPServerDAO.fetchById(iServerId);
        sFTPConnectionInstances.add(this);
        mFTPClient = new FTPClient();
        initializeNetworkMonitoring();
    }

    public static FTPConnection getFTPConnection(int iServerId) {
        LogManager.info(TAG, "Get FTP connection");
        if (sFTPConnectionInstances == null)
            sFTPConnectionInstances = new ArrayList<>();

        for (FTPConnection lConnection : sFTPConnectionInstances) {
            if (lConnection.getFTPServerId() == iServerId)
                return lConnection;
        }
        return null;
    }

    public void destroyConnection() {
        LogManager.info(TAG, "Destroy connection");
        AppCore.getNetworkManager().unsubscribeOnNetworkAvailable(mOnNetworkAvailableCallback);
        AppCore.getNetworkManager().unsubscribeOnNetworkLost(mOnNetworkLostCallback);
        if (isConnected())
            disconnect();

        sFTPConnectionInstances.remove(this);
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
                if (isConnected())
                    disconnect();
                if (mOnConnectionLost != null)
                    mOnConnectionLost.onConnectionLost();
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
                if (isConnected())
                    disconnect();
                if (mOnConnectionLost != null)
                    mOnConnectionLost.onConnectionLost();
            }
        };
        AppCore.getNetworkManager().subscribeNetworkAvailable(mOnNetworkAvailableCallback);
        AppCore.getNetworkManager().subscribeOnNetworkLost(mOnNetworkLostCallback);
    }

    public void abortReconnection() {
        LogManager.info(TAG, "Abort reconnect");
    }

    public void reconnect(final OnConnectionRecover iOnConnectionRecover) {
        LogManager.info(TAG, "Reconnect");
        mAbortReconnect = false;

        mReconnectThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!isConnected() && !mAbortReconnect) {
                    if (!isConnecting()) {
                        connect(new OnConnectResult() {
                            @Override
                            public void onSuccess() {
                                LogManager.info(TAG, "Reconnect success");
                                if (iOnConnectionRecover != null)
                                    iOnConnectionRecover.onConnectionRecover();
                            }

                            @Override
                            public void onFail(CONNECTION_STATUS iErrorCode) {
                                LogManager.info(TAG, "Reconnect fail");
                                if (iErrorCode == CONNECTION_STATUS.ERROR_FAILED_LOGIN) {
                                    iOnConnectionRecover.onConnectionDenied(iErrorCode);
                                    mAbortReconnect = true;
                                }
                            }
                        });
                        try {
                            LogManager.error(TAG, "Reconnection waiting...");
                            Thread.sleep(400);
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
            if (isFetchingFolders())
                abortFetchDirectoryContent();
            try {
                mFTPClient.disconnect();

            } catch (IOException iE) {
                iE.printStackTrace();
            }
        } else {
            new Exception("Thread disconnection but not connected").printStackTrace();
        }
    }

    public void abortFetchDirectoryContent() {
        LogManager.info(TAG, "Abort fetch directory contents");

        if (isFetchingFolders()) {
            mDirectoryFetchThread.interrupt();
        }
    }

    public void fetchDirectoryContent(final String iPath, final OnFetchDirectoryResult iOnFetchDirectoryResult) {
        LogManager.info(TAG, "Fetch directory contents");
        if (!isConnected()) {
            LogManager.error(TAG, "Connection not established");

            return;
        }
        if (isFetchingFolders()) {
            LogManager.info(TAG, "Aborting current directory fetch");
            abortFetchDirectoryContent();
        }

        mDirectoryFetchThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    mFTPClient.enterLocalPassiveMode();
                    FTPListParseEngine lDirectory = mFTPClient.initiateListParsing(null, iPath);
                    mFTPClient.enterLocalActiveMode();
                    if (Thread.interrupted())
                        return;
                    if (!FTPReply.isPositiveCompletion(mFTPClient.getReplyCode())) {
                        throw new IOException(mFTPClient.getReplyString());
                    }
                    if (Thread.interrupted())
                        return;
                    iOnFetchDirectoryResult.onSuccess(lDirectory.getFiles());
                } catch (IOException iE) {
                    iE.printStackTrace();
                    if (!Thread.interrupted()) {
                        if (mFTPClient.getReplyCode() == 450)
                            iOnFetchDirectoryResult.onFail(CONNECTION_STATUS.ERROR_NOT_REACHABLE);
                        else
                            iOnFetchDirectoryResult.onFail(CONNECTION_STATUS.ERROR);
                    }
                }
            }
        });
        mDirectoryFetchThread.start();
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

    public void connect(final OnConnectResult iOnConnectResult) {
        LogManager.info(TAG, "Connect");
        if (isConnected()) {
            LogManager.error(TAG, "Trying a connection but is already connected");
            new Exception("already connected").printStackTrace();
            iOnConnectResult.onFail(CONNECTION_STATUS.ERROR_ALREADY_CONNECTED);
            return;
        } else if (isConnecting()) {
            LogManager.error(TAG, "Trying a connection but is already connecting");
            new Exception("already connecting").printStackTrace();
            iOnConnectResult.onFail(CONNECTION_STATUS.ERROR_ALREADY_CONNECTING);
            return;
        }

        LogManager.debug(TAG, "Connect : isNetworkAvailable : " +
                AppCore.getNetworkManager().isNetworkAvailable());

        if (!AppCore.getNetworkManager().isNetworkAvailable()) {
            iOnConnectResult.onFail(CONNECTION_STATUS.ERROR_NO_INTERNET);
            return;
        }

        mConnectionThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    LogManager.info(TAG, "Will connect with : \n" + mFTPServer.toString());

                    mFTPClient.setDefaultPort(mFTPServer.getPort());
                    mFTPClient.connect(InetAddress.getByName(mFTPServer.getServer()));
                    mFTPClient.setSoTimeout(15000); // 15s
                    if (Thread.interrupted()) {
                        mFTPClient.disconnect();
                        iOnConnectResult.onFail(CONNECTION_STATUS.ERROR_CONNECTION_INTERRUPTED);
                        return;
                    }

                    mFTPClient.login(mFTPServer.getUser(), mFTPServer.getPass());
                    if (Thread.interrupted()) {
                        mFTPClient.disconnect();
                        iOnConnectResult.onFail(CONNECTION_STATUS.ERROR_CONNECTION_INTERRUPTED);
                        return;
                    }

                    if (!FTPReply.isPositiveCompletion(mFTPClient.getReplyCode())) {
                        LogManager.info(TAG, "FTPClient code : " + mFTPClient.getReplyCode());
                        mFTPClient.disconnect();
                        LogManager.error(TAG, "FTP server refused connection.");
                        if (mFTPClient.getReplyCode() == FTPReply.NOT_LOGGED_IN)
                            iOnConnectResult.onFail(CONNECTION_STATUS.ERROR_FAILED_LOGIN);
                        else
                            iOnConnectResult.onFail(CONNECTION_STATUS.ERROR);
                        return;
                    } else {
                        LogManager.info(TAG, "FTPClient status : " + mFTPClient.getStatus());
                        LogManager.info(TAG, "FTPClient code : " + mFTPClient.getReplyCode());
                    }

                    if (isConnected()) {
                        if (mReplyStatusThread == null) {
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
                                            Thread.sleep(10);
                                        }
                                    } catch (InterruptedException iE) {
                                        iE.printStackTrace();
                                    }
                                }
                            });
                            mReplyStatusThread.start();
                        }
                        LogManager.info(TAG, "FTPClient connected");
                        iOnConnectResult.onSuccess();
                    } else
                        LogManager.error(TAG, "FTPClient not connected");
                } catch (UnknownHostException iE) {
                    iOnConnectResult.onFail(CONNECTION_STATUS.ERROR_UNKNOWN_HOST);
                    iE.printStackTrace();
                } catch (Exception iE) {
                    iOnConnectResult.onFail(CONNECTION_STATUS.ERROR);
                    iE.printStackTrace();
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

    public boolean isConnected() {
        return mFTPClient.isConnected();
    }

    public boolean isConnecting() {
        return !isConnected() && mConnectionThread != null && mConnectionThread.isAlive();
    }

    public boolean isReconnecting() {
        return !isConnected() && mReconnectThread != null && mReconnectThread.isAlive();
    }

    public boolean isFetchingFolders() {
        return isConnected() && mDirectoryFetchThread != null && mDirectoryFetchThread.isAlive();
    }

    public void setOnConnectionLost(OnConnectionLost iOnConnectionLost) {
        mOnConnectionLost = iOnConnectionLost;
    }

    public enum CONNECTION_STATUS {
        ERROR,
        ERROR_UNKNOWN_HOST,
        ERROR_CONNECTION_TIMEOUT,
        ERROR_ALREADY_CONNECTED,
        ERROR_ALREADY_CONNECTING,
        ERROR_CONNECTION_INTERRUPTED,
        ERROR_NO_INTERNET,
        ERROR_FAILED_LOGIN,
        ERROR_NOT_REACHABLE,
    }

    public interface OnFetchDirectoryResult {
        void onSuccess(FTPFile[] iFTPFiles);

        void onFail(CONNECTION_STATUS iErrorCode);
    }

    public interface OnConnectResult {
        void onSuccess();

        void onFail(CONNECTION_STATUS iErrorCode);
    }

    public interface OnConnectionLost {
        void onConnectionLost();
    }

    public interface OnConnectionRecover {
        void onConnectionRecover();

        void onConnectionDenied(CONNECTION_STATUS iErrorCode);
    }
}
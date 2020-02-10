package com.example.ftpnext.ftpservices;

import android.net.Network;

import com.example.ftpnext.core.AppCore;
import com.example.ftpnext.core.LoadDirection;
import com.example.ftpnext.core.LogManager;
import com.example.ftpnext.core.NetworkManager;
import com.example.ftpnext.database.DataBase;
import com.example.ftpnext.database.FTPServerTable.FTPServer;
import com.example.ftpnext.database.FTPServerTable.FTPServerDAO;
import com.example.ftpnext.database.PendingFileTable.PendingFile;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// TODO : Download save when app is killed

public class FTPConnection {

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

    public FTPConnection(FTPServer iFTPServer) {
        mFTPServerDAO = DataBase.getFTPServerDAO();
        mFTPServer = iFTPServer;
        mFTPClient = new FTPClient();
        initializeNetworkMonitoring();
    }

    public FTPConnection(int iServerId) {
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

    public void reconnect(final IOnConnectionRecover iOnConnectionRecover) {
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
                        connect(new IOnConnectionResult() {
                            @Override
                            public void onSuccess() {
                                LogManager.info(TAG, "Reconnect success");
                                if (iOnConnectionRecover != null) {
                                    iOnConnectionRecover.onConnectionRecover();
                                }
                            }

                            @Override
                            public void onFail(ErrorCodeDescription iErrorEnum, int iErrorCode) {
                                LogManager.info(TAG, "Reconnect fail");
                                if (iErrorEnum == ErrorCodeDescription.ERROR_FAILED_LOGIN) {
                                    iOnConnectionRecover.onConnectionDenied(iErrorEnum,
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


    public void connect(final IOnConnectionResult iOnConnectionResult) {
        LogManager.info(TAG, "Connect");
        if (isConnected()) {
            LogManager.error(TAG, "Trying a connection but is already connected");
            new Exception("already connected").printStackTrace();
            if (iOnConnectionResult != null)
                iOnConnectionResult.onFail(ErrorCodeDescription.ERROR_ALREADY_CONNECTED,
                        FTPReply.CANNOT_OPEN_DATA_CONNECTION);
            return;
        } else if (isConnecting()) {
            LogManager.error(TAG, "Trying a connection but is already connecting");
            new Exception("already connecting").printStackTrace();
            if (iOnConnectionResult != null)
                iOnConnectionResult.onFail(ErrorCodeDescription.ERROR_ALREADY_CONNECTING,
                        FTPReply.CANNOT_OPEN_DATA_CONNECTION);
            return;
        }

        if (!AppCore.getNetworkManager().isNetworkAvailable()) {
            LogManager.error(TAG, "Connection : Network not available");
            if (iOnConnectionResult != null)
                iOnConnectionResult.onFail(ErrorCodeDescription.ERROR_NO_INTERNET,
                        FTPReply.CANNOT_OPEN_DATA_CONNECTION);
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
                        if (iOnConnectionResult != null)
                            iOnConnectionResult.onFail(ErrorCodeDescription.ERROR_CONNECTION_INTERRUPTED,
                                    426);
                        return;
                    }

                    mFTPClient.login(mFTPServer.getUser(), mFTPServer.getPass());
                    if (Thread.interrupted()) {
                        mFTPClient.disconnect();
                        if (iOnConnectionResult != null)
                            iOnConnectionResult.onFail(ErrorCodeDescription.ERROR_CONNECTION_INTERRUPTED,
                                    426);
                        return;
                    }

                    if (!FTPReply.isPositiveCompletion(mFTPClient.getReplyCode())) {
                        LogManager.info(TAG, "FTPClient code : " + mFTPClient.getReplyCode());
                        mFTPClient.disconnect();
                        LogManager.error(TAG, "FTP server refused connection.");
                        if (iOnConnectionResult != null) {
                            if (mFTPClient.getReplyCode() == FTPReply.NOT_LOGGED_IN)
                                iOnConnectionResult.onFail(ErrorCodeDescription.ERROR_FAILED_LOGIN,
                                        mFTPClient.getReplyCode());
                            else
                                iOnConnectionResult.onFail(ErrorCodeDescription.ERROR,
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
                        if (iOnConnectionResult != null)
                            iOnConnectionResult.onSuccess();
                    } else
                        LogManager.error(TAG, "FTPClient not connected");
                } catch (UnknownHostException iE) {
                    iE.printStackTrace();
                    if (iOnConnectionResult != null)
                        iOnConnectionResult.onFail(ErrorCodeDescription.ERROR_UNKNOWN_HOST,
                                mFTPClient.getReplyCode());
//                      iOnConnectionResult.onFail(ErrorCodeDescription.ERROR_UNKNOWN_HOST, 434);
                } catch (Exception iE) {
                    iE.printStackTrace();
                    if (iOnConnectionResult != null)
                        iOnConnectionResult.onFail(ErrorCodeDescription.ERROR,
                                mFTPClient.getReplyCode());
//                      iOnConnectionResult.onFail(ErrorCodeDescription.ERROR, FTPReply.UNRECOGNIZED_COMMAND);
                }
            }
        });
        mConnectionThread.start();
    }

    public void downloadFiles(final PendingFile[] iSelection, @NotNull final AOnDownloadListener iAOnDownloadListener) {

    }

    public PendingFile[] createPendingFiles(String iEnclosureName, int iServerId, FTPFile[] iSelectedFiles, LoadDirection iLoadDirection) {
        LogManager.info(TAG, "Create pending files");
        List<PendingFile> oPendingFiles = new ArrayList<>();

        for (FTPFile lItem : iSelectedFiles) {

            if (lItem.isDirectory()) {
                FTPFile[] lFiles = new FTPFile[0];
                LogManager.debug(TAG, "folder item name : \t\t" + lItem.getName());
                LogManager.debug(TAG, "mCurrentFolder item name : \t" + mCurrentDirectory.getName());

                try {
                    LogManager.error(TAG, "list file : " + mCurrentDirectory.getName() + "/" + lItem.getName());
                    // TODO : Need a thread
                    lFiles = mFTPClient.listFiles(mCurrentDirectory.getName() + "/" + lItem.getName());
                } catch (IOException iE) {
                    iE.printStackTrace();
                }

                oPendingFiles.addAll(Arrays.asList(
                        createPendingFiles(lItem.getName(), iServerId, lFiles, iLoadDirection)));
            } else {
                oPendingFiles.add(new PendingFile(
                        iServerId,
                        iLoadDirection,
                        false,
                        mCurrentDirectory.getName() + "/" + lItem.getName(),
                        iEnclosureName
                ));
            }
        }
        return (PendingFile[]) oPendingFiles.toArray();
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
        return !isConnected() && mConnectionThread != null && mConnectionThread.isAlive();
    }

    public boolean isReconnecting() {
        return mReconnectThread != null && mReconnectThread.isAlive();
    }

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


    public interface IOnConnectionResult {
        void onSuccess();

        void onFail(ErrorCodeDescription iErrorEnum, int iErrorCode);
    }

    public interface IOnConnectionLost {
        void onConnectionLost();
    }

    public interface IOnConnectionRecover {
        void onConnectionRecover();

        void onConnectionDenied(ErrorCodeDescription iErrorEnum, int iErrorCode);
    }

    public abstract class AOnDownloadListener {

        public void onStartDownloadFile(PendingFile iPendingFile) {
            iPendingFile.setStarted(true);
            DataBase.getPendingFileDAO().update(iPendingFile);
        }

        public abstract void onTotalPendingFileProgress(int iProgress, int iTotalPendingFile);

        public abstract void onDownloadProgress(PendingFile iPendingFile, int iProgress, int iSize);

        public void oNDownloadSuccess(PendingFile iPendingFile) {
            DataBase.getPendingFileDAO().delete(iPendingFile);
        }

        public abstract void onRightAccessFail(PendingFile iPendingFile);

        public abstract void onFail(PendingFile iPendingFile);
    }
}
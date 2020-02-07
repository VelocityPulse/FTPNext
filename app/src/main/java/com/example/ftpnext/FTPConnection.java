package com.example.ftpnext;

import android.net.Network;

import com.example.ftpnext.commons.FTPFileUtils;
import com.example.ftpnext.core.AppCore;
import com.example.ftpnext.core.LogManager;
import com.example.ftpnext.core.NetworkManager;
import com.example.ftpnext.database.DataBase;
import com.example.ftpnext.database.FTPServerTable.FTPServer;
import com.example.ftpnext.database.FTPServerTable.FTPServerDAO;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

// TODO : Download save when app is killed

public class FTPConnection {

    private static final int RECONNECTION_WAITING_TIME = 700;
    private static final int DELETE_THREAD_SLEEP_TIME = 500;
    private static final int ITEM_FETCHED_BY_GROUP = 25;
    private static final String TAG = "FTP CONNECTION";
    private static final boolean REPLY_THREAD_STATUS_ACTIVATED = true;

    private static List<FTPConnection> sFTPConnectionInstances;

    private FTPServerDAO mFTPServerDAO;
    private FTPServer mFTPServer;
    private FTPClient mFTPClient;
    private FTPFile mCurrentDirectory;

    private Thread mConnectionThread;
    private Thread mReconnectThread;
    private Thread mDirectoryFetchThread;
    private Thread mReplyStatusThread;
    private Thread mCreateDirectoryThread;
    private Thread mDeleteFileThread;

    private NetworkManager.OnNetworkAvailable mOnNetworkAvailableCallback;
    private NetworkManager.OnNetworkLost mOnNetworkLostCallback;
    private OnConnectionLost mOnConnectionLost;

    private boolean mAbortReconnect;
    private boolean mPauseDeleting;
    private boolean mByPassDeletingRightErrors;
    private boolean mByPassDeletingFailErrors;
    private boolean mStartingFetchDirectory;

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
                if (mOnConnectionLost != null)
                    mOnConnectionLost.onConnectionLost();
            }
        };
        AppCore.getNetworkManager().subscribeNetworkAvailable(mOnNetworkAvailableCallback);
        AppCore.getNetworkManager().subscribeOnNetworkLost(mOnNetworkLostCallback);
    }

    public void updateWorkingDirectory(final String iNewWorkingDirectory) {
        LogManager.info(TAG, "Update working directory");
        if (!isConnected())
            return;

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    FTPFile lWorkingDir = mFTPClient.mlistFile(iNewWorkingDirectory);
                    if (lWorkingDir != null && lWorkingDir.isDirectory()) {
                        mCurrentDirectory = lWorkingDir;
                    }
                } catch (IOException iE) {
                    iE.printStackTrace();
                }
            }
        }).start();
    }

    public void abortReconnection() {
        LogManager.info(TAG, "Abort reconnect");

        if (isReconnecting())
            mAbortReconnect = true; // To test
    }

    public void abortFetchDirectoryContent() {
        LogManager.info(TAG, "Abort fetch directory contents");

        if (isFetchingFolders())
            mDirectoryFetchThread.interrupt();
    }

    public void abortDeleting() {
        LogManager.info(TAG, "Abort deleting");

        if (isDeletingFiles())
            mDeleteFileThread.interrupt();
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

    public void reconnect(final OnConnectionRecover iOnConnectionRecover) {
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
                        connect(new OnConnectResult() {
                            @Override
                            public void onSuccess() {
                                LogManager.info(TAG, "Reconnect success");
                                if (iOnConnectionRecover != null) {
                                    iOnConnectionRecover.onConnectionRecover();
                                }
                            }

                            @Override
                            public void onFail(ERROR_CODE_DESCRIPTION iErrorEnum, int iErrorCode) {
                                LogManager.info(TAG, "Reconnect fail");
                                if (iErrorEnum == ERROR_CODE_DESCRIPTION.ERROR_FAILED_LOGIN) {
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
            if (isFetchingFolders())
                abortFetchDirectoryContent();
            if (isDeletingFiles() && !isReconnecting())
                abortDeleting();
            try {
                mFTPClient.disconnect();
            } catch (IOException iE) {
                iE.printStackTrace();
            }
        } else {
            new Exception("Thread disconnection but not connected").printStackTrace();
        }
    }

    public void fetchDirectoryContent(final String iPath, final OnFetchDirectoryResult iOnFetchDirectoryResult) {
        LogManager.info(TAG, "Fetch directory contents");
        if (!isConnected()) {
            LogManager.error(TAG, "Connection not established");
            return;
        }

        if (isFetchingFolders()) {
            LogManager.info(TAG, "Canceling fetch request");
            return;
        }

        mStartingFetchDirectory = true;
        mDirectoryFetchThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Sometimes, isFetchingDirectory returned false while it was actually
                    // Trying to fetch a dir, but the thread wasn't started yet
                    mStartingFetchDirectory = false;
                    FTPFile lLeavingDirectory = mCurrentDirectory;

                    LogManager.debug(TAG, "iPath : " + iPath);
                    FTPFile lTargetDirectory = mFTPClient.mlistFile(iPath);
                    if (!lTargetDirectory.isDirectory()) { // TODO : put null security for release
                        if (iOnFetchDirectoryResult != null)
                            iOnFetchDirectoryResult.onFail(ERROR_CODE_DESCRIPTION.ERROR_NOT_A_DIRECTORY,
                                    FTPReply.FILE_UNAVAILABLE);
                        return;
                    }
                    if (!lTargetDirectory.hasPermission(FTPFile.USER_ACCESS, FTPFile.EXECUTE_PERMISSION)) {
                        if (iOnFetchDirectoryResult != null)
                            iOnFetchDirectoryResult.onFail(ERROR_CODE_DESCRIPTION.ERROR_EXECUTE_PERMISSION_MISSED,
                                    633);
                        return;
                    }
                    if (!lTargetDirectory.hasPermission(FTPFile.USER_ACCESS, FTPFile.READ_PERMISSION)) {
                        if (iOnFetchDirectoryResult != null)
                            iOnFetchDirectoryResult.onFail(ERROR_CODE_DESCRIPTION.ERROR_READ_PERMISSION_MISSED,
                                    633);
                        return;
                    }

                    if (Thread.interrupted())
                        return;

                    mFTPClient.enterLocalPassiveMode();
                    mFTPClient.changeWorkingDirectory(iPath);
                    FTPFile[] lFiles = mFTPClient.listFiles();
                    if (Thread.interrupted()) {
                        mFTPClient.changeWorkingDirectory(lLeavingDirectory.getName());
                        mCurrentDirectory = lLeavingDirectory;
                        return;
                    }
                    mCurrentDirectory = lTargetDirectory;
                    mFTPClient.enterLocalActiveMode();
                    if (Thread.interrupted()) {
                        mFTPClient.changeWorkingDirectory(lLeavingDirectory.getName());
                        mCurrentDirectory = lLeavingDirectory;
                        return;
                    }
                    if (!FTPReply.isPositiveCompletion(mFTPClient.getReplyCode())) {
                        throw new IOException(mFTPClient.getReplyString());
                    }
                    if (Thread.interrupted()) {
                        mFTPClient.changeWorkingDirectory(lLeavingDirectory.getName());
                        mCurrentDirectory = lLeavingDirectory;
                        return;
                    }
                    iOnFetchDirectoryResult.onSuccess(lFiles);

                } catch (IOException iE) {
                    iE.printStackTrace();
                    if (!Thread.interrupted()) {
                        if (mFTPClient.getReplyCode() == 450)
                            iOnFetchDirectoryResult.onFail(ERROR_CODE_DESCRIPTION.ERROR_NOT_REACHABLE,
                                    mFTPClient.getReplyCode());
                        else
                            iOnFetchDirectoryResult.onFail(ERROR_CODE_DESCRIPTION.ERROR,
                                    mFTPClient.getReplyCode());
                    }
                }
            }


        });
        mDirectoryFetchThread.start();
    }

    public void connect(final OnConnectResult iOnConnectResult) {
        LogManager.info(TAG, "Connect");
        if (isConnected()) {
            LogManager.error(TAG, "Trying a connection but is already connected");
            new Exception("already connected").printStackTrace();
            if (iOnConnectResult != null)
                iOnConnectResult.onFail(ERROR_CODE_DESCRIPTION.ERROR_ALREADY_CONNECTED,
                        FTPReply.CANNOT_OPEN_DATA_CONNECTION);
            return;
        } else if (isConnecting()) {
            LogManager.error(TAG, "Trying a connection but is already connecting");
            new Exception("already connecting").printStackTrace();
            if (iOnConnectResult != null)
                iOnConnectResult.onFail(ERROR_CODE_DESCRIPTION.ERROR_ALREADY_CONNECTING,
                        FTPReply.CANNOT_OPEN_DATA_CONNECTION);
            return;
        }

        if (!AppCore.getNetworkManager().isNetworkAvailable()) {
            if (iOnConnectResult != null)
                iOnConnectResult.onFail(ERROR_CODE_DESCRIPTION.ERROR_NO_INTERNET,
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
                        if (iOnConnectResult != null)
                            iOnConnectResult.onFail(ERROR_CODE_DESCRIPTION.ERROR_CONNECTION_INTERRUPTED,
                                    426);
                        return;
                    }

                    mFTPClient.login(mFTPServer.getUser(), mFTPServer.getPass());
                    if (Thread.interrupted()) {
                        mFTPClient.disconnect();
                        if (iOnConnectResult != null)
                            iOnConnectResult.onFail(ERROR_CODE_DESCRIPTION.ERROR_CONNECTION_INTERRUPTED,
                                    426);
                        return;
                    }

                    if (!FTPReply.isPositiveCompletion(mFTPClient.getReplyCode())) {
                        LogManager.info(TAG, "FTPClient code : " + mFTPClient.getReplyCode());
                        mFTPClient.disconnect();
                        LogManager.error(TAG, "FTP server refused connection.");
                        if (iOnConnectResult != null) {
                            if (mFTPClient.getReplyCode() == FTPReply.NOT_LOGGED_IN)
                                iOnConnectResult.onFail(ERROR_CODE_DESCRIPTION.ERROR_FAILED_LOGIN,
                                        mFTPClient.getReplyCode());
                            else
                                iOnConnectResult.onFail(ERROR_CODE_DESCRIPTION.ERROR,
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
                        if (iOnConnectResult != null)
                            iOnConnectResult.onSuccess();
                    } else
                        LogManager.error(TAG, "FTPClient not connected");
                } catch (UnknownHostException iE) {
                    iE.printStackTrace();
                    if (iOnConnectResult != null)
                        iOnConnectResult.onFail(ERROR_CODE_DESCRIPTION.ERROR_UNKNOWN_HOST,
                                mFTPClient.getReplyCode());
//                      iOnConnectResult.onFail(ERROR_CODE_DESCRIPTION.ERROR_UNKNOWN_HOST, 434);
                } catch (Exception iE) {
                    iE.printStackTrace();
                    if (iOnConnectResult != null)
                        iOnConnectResult.onFail(ERROR_CODE_DESCRIPTION.ERROR,
                                mFTPClient.getReplyCode());
//                      iOnConnectResult.onFail(ERROR_CODE_DESCRIPTION.ERROR, FTPReply.UNRECOGNIZED_COMMAND);
                }
            }
        });
        mConnectionThread.start();
    }

    public void createDirectory(final String iPath, final String iName, final OnCreateDirectoryResult iOnCreateDirectoryResult) {
        if (!isConnected()) {
            LogManager.error(TAG, "Connection not established");
            return;
        } else if (isCreatingFolder()) {
            LogManager.error(TAG, "Is already creating directory");
            return;
        }

        mCreateDirectoryThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    FTPFile lExistingFile = mFTPClient.mlistFile(iPath + "/" + iName);
                    if (lExistingFile != null && lExistingFile.isDirectory()) {
                        if (iOnCreateDirectoryResult != null)
                            iOnCreateDirectoryResult.onFail(ERROR_CODE_DESCRIPTION.ERROR_DIRECTORY_ALREADY_EXISTING,
                                    FTPReply.STORAGE_ALLOCATION_EXCEEDED);
                        return;
                    }

                    if (!mFTPClient.makeDirectory(iPath + "/" + iName))
                        throw new IOException("Creation failed.");
                    FTPFile lCreatedFile = mFTPClient.mlistFile(iPath + "/" + iName);
                    lCreatedFile.setName(iName);
                    if (iOnCreateDirectoryResult != null)
                        iOnCreateDirectoryResult.onSuccess(lCreatedFile);
                } catch (IOException iE) {
                    iE.printStackTrace();
                    if (iOnCreateDirectoryResult != null)
                        iOnCreateDirectoryResult.onFail(ERROR_CODE_DESCRIPTION.ERROR,
                                FTPReply.UNRECOGNIZED_COMMAND);
                }
            }
        });
        mCreateDirectoryThread.start();
    }

    public void deleteFile(FTPFile iFTPFile, @NotNull final OnDeleteListener iOnDeleteListener) {
        deleteFiles(new FTPFile[]{iFTPFile}, iOnDeleteListener);
    }

    public void deleteFiles(final FTPFile[] iSelection, @NotNull final OnDeleteListener lOnDeleteListener) {
        LogManager.info(TAG, "Delete files");
        if (!isConnected()) {
            LogManager.error(TAG, "Connection not established");
            return;
        } else if (isDeletingFiles()) {
            LogManager.error(TAG, "Is already deleting files");
            return;
        } else if (lOnDeleteListener == null) {
            LogManager.error(TAG, "Delete listener is null");
            new NullPointerException("Delete listener is null").printStackTrace();
            return;
        }

        mPauseDeleting = false;
        mByPassDeletingRightErrors = false;
        mByPassDeletingFailErrors = false;
        mDeleteFileThread = new Thread(new Runnable() {

            private void recursiveDeletion(FTPFile iFTPFile, int iProgress, int iTotal) throws IOException, InterruptedException {

                while (mPauseDeleting && !Thread.interrupted())
                    Thread.sleep(DELETE_THREAD_SLEEP_TIME);
                if (Thread.interrupted())
                    return;

                LogManager.info(TAG, "Recursive deletion : " + iFTPFile.getName() + " " + iFTPFile.isDirectory());

                if (iFTPFile.isDirectory()) {

                    lOnDeleteListener.onProgressDirectory(
                            iProgress,
                            iTotal,
                            iFTPFile.getName());

                    // DIRECTORY
                    if (iFTPFile.hasPermission(FTPFile.USER_ACCESS, FTPFile.READ_PERMISSION)) {

                        FTPFile[] lFiles = mFTPClient.listFiles(iFTPFile.getName());
                        int lProgress = 0;
                        for (FTPFile lFile : lFiles) {
                            lOnDeleteListener.onProgressDirectory(
                                    iProgress,
                                    iTotal,
                                    iFTPFile.getName());

                            lFile.setName(iFTPFile.getName() + "/" + lFile.getName());
                            recursiveDeletion(lFile, lProgress++, lFiles.length);

                            while (mPauseDeleting && !Thread.interrupted())
                                Thread.sleep(DELETE_THREAD_SLEEP_TIME);
                            if (Thread.interrupted())
                                return;
                        }

                        lOnDeleteListener.onProgressDirectory(
                                iProgress,
                                iTotal,
                                iFTPFile.getName());

                        lOnDeleteListener.onProgressSubDirectory(
                                0,
                                0,
                                "");

                        while (mPauseDeleting && !Thread.interrupted())
                            Thread.sleep(DELETE_THREAD_SLEEP_TIME);
                        if (Thread.interrupted())
                            return;

                        if (iFTPFile.hasPermission(FTPFile.USER_ACCESS, FTPFile.WRITE_PERMISSION)) {
                            LogManager.debug(TAG, "WILL DELETE DIR : " + iFTPFile.getName());
                            boolean lReply = mFTPClient.removeDirectory(iFTPFile.getName());

                            if (!lReply && !mByPassDeletingFailErrors)
                                lOnDeleteListener.onFail(iFTPFile); // TODO : Watch folder error is not triggered !

                        } else if (!mByPassDeletingRightErrors)
                            lOnDeleteListener.onRightAccessFail(iFTPFile);

                    } else if (!mByPassDeletingRightErrors)
                        lOnDeleteListener.onRightAccessFail(iFTPFile);

                } else if (iFTPFile.isFile()) {
                    // FILE

                    lOnDeleteListener.onProgressSubDirectory(
                            iProgress,
                            iTotal,
                            FTPFileUtils.getFileName(iFTPFile));

                    if (iFTPFile.hasPermission(FTPFile.USER_ACCESS, FTPFile.WRITE_PERMISSION)) {
                        LogManager.debug(TAG, "WILL DELETE FILE : " + iFTPFile.getName());
                        boolean lReply = mFTPClient.deleteFile(iFTPFile.getName());

                        if (!lReply)
                            lOnDeleteListener.onFail(iFTPFile);
                    } else if (!mByPassDeletingRightErrors) {
                        mPauseDeleting = true;
                        lOnDeleteListener.onRightAccessFail(iFTPFile);
                    }
                }
            }

            @Override
            public void run() {
                LogManager.info(TAG, "Thread delete files");
                try {
                    mFTPClient.enterLocalPassiveMode(); // PASSIVE MODE

                    lOnDeleteListener.onStartDelete();

                    int lProgress = 0;
                    for (FTPFile lFTPFile : iSelection) {
                        LogManager.debug(TAG, "Selected file : " + lFTPFile.getName());
                        LogManager.debug(TAG, "Absolute path : " + mCurrentDirectory.getName() + "/" + lFTPFile.getName());
                        FTPFile lAbsoluteFile = mFTPClient.mlistFile(mCurrentDirectory.getName() + "/" + lFTPFile.getName());
                        LogManager.debug(TAG, "File name : " + lAbsoluteFile.getName());

                        if (lAbsoluteFile != null) {
                            LogManager.debug(TAG, "Absolute name : " + lAbsoluteFile.getName());

                            lOnDeleteListener.onProgressDirectory(
                                    lProgress,
                                    iSelection.length,
                                    lFTPFile.getName());

                            recursiveDeletion(lAbsoluteFile, lProgress++, iSelection.length);
                        } else {
                            LogManager.error(TAG, "Error with : " + lFTPFile.toFormattedString());
                            lOnDeleteListener.onFail(lFTPFile);
                            mPauseDeleting = true;
                        }

                        lOnDeleteListener.onProgressDirectory(
                                lProgress,
                                iSelection.length,
                                lFTPFile.getName());
                    }

                    while (mPauseDeleting && !Thread.interrupted())
                        Thread.sleep(DELETE_THREAD_SLEEP_TIME);
                    if (Thread.interrupted())
                        return;

                    mFTPClient.enterLocalActiveMode(); // ACTIVE MODE
                } catch (IOException | InterruptedException iE) {
                    iE.printStackTrace();
                }

                if (Thread.interrupted())
                    return;

                lOnDeleteListener.onFinish();
            }
        });
        mDeleteFileThread.start();
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

    public boolean isFetchingFolders() {
        // Display :
//        LogManager.info(TAG, "isFetchingFolders : " + isConnected() + " && (" +
//                (mDirectoryFetchThread != null && mDirectoryFetchThread.isAlive()) + ") || " + mStartingFetchDirectory);
        return (isConnected() && (mDirectoryFetchThread != null && mDirectoryFetchThread.isAlive())) ||
                mStartingFetchDirectory;
    }

    public boolean isCreatingFolder() {
        return isConnected() && mCreateDirectoryThread != null && mCreateDirectoryThread.isAlive();
    }

    public boolean isDeletingFiles() {
        return isConnected() && mDeleteFileThread != null && mDeleteFileThread.isAlive();
    }

    public boolean isBusy() {
        // Display :
//        LogManager.debug(TAG, " " + isConnecting() + " " + isReconnecting() + " " + isFetchingFolders() + " "
//                + isCreatingFolder() + " " + isDeletingFiles());
        return isConnecting() || isReconnecting() || isFetchingFolders() || isCreatingFolder() ||
                isDeletingFiles();
    }

    public void setOnConnectionLost(OnConnectionLost iOnConnectionLost) {
        mOnConnectionLost = iOnConnectionLost;
    }

    public boolean isDeletingPaused() {
        return mPauseDeleting;
    }

    public void resumeDeleting() {
        mPauseDeleting = false;
    }

    public void pauseDeleting() {
        mPauseDeleting = true;
    }

    public void setDeletingByPassRightErrors(boolean iValue) {
        mByPassDeletingRightErrors = iValue;
    }

    public void setDeletingByPassFailErrors(boolean iValue) {
        mByPassDeletingFailErrors = iValue;
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

    public enum ERROR_CODE_DESCRIPTION {
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

    public interface OnFetchDirectoryResult {
        void onSuccess(FTPFile[] iFTPFiles);

        void onFail(ERROR_CODE_DESCRIPTION iErrorEnum, int iErrorCode);
    }

    public interface OnCreateDirectoryResult {
        void onSuccess(FTPFile iNewDirectory);

        void onFail(ERROR_CODE_DESCRIPTION iErrorEnum, int iErrorCode);
    }

    public interface OnConnectResult {
        void onSuccess();

        void onFail(ERROR_CODE_DESCRIPTION iErrorEnum, int iErrorCode);
    }

    public interface OnConnectionLost {
        void onConnectionLost();
    }

    public interface OnConnectionRecover {
        void onConnectionRecover();

        void onConnectionDenied(ERROR_CODE_DESCRIPTION iErrorEnum, int iErrorCode);
    }

    // TODO : Try to transform it in abstract
    public abstract class OnDeleteListener {
        public abstract void onStartDelete();

        public abstract void onProgressDirectory(int iDirectoryProgress, int iTotalDirectories, String iDirectoryName);

        public abstract void onProgressSubDirectory(int iSubDirectoryProgress, int iTotalSubDirectories, String iSubDirectoryName);

        public void onRightAccessFail(FTPFile iFTPFile) {
            mPauseDeleting = true;
        }

        public abstract void onFinish();

        public void onFail(FTPFile iFTPFile) {
            mPauseDeleting = true;
        }
    }
}
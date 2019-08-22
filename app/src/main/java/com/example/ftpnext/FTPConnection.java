package com.example.ftpnext;

import com.example.ftpnext.core.LogManager;
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
import java.util.concurrent.TimeoutException;

// TODO : Download save when app is killed

public class FTPConnection {

    public static int ERROR = 0;
    public static int ERROR_UNKNOWN_HOST = 1;
    public static int ERROR_CONNECTION_TIMEOUT = 2;
    public static int ERROR_ALREADY_CONNECTED = 3;

    private static String TAG = "FTP CONNECTION";

    private static int ITEM_FETCHED_BY_GROUP = 25;

    private static List<FTPConnection> sFTPConnectionInstances;
    private FTPServerDAO mFTPServerDAO;
    private FTPServer mFTPServer;
    private FTPClient mFTPClient;
    private String mLocalization;
    private boolean mIsFetchingFolders;
    private Thread mConnectionThread;
    private Thread mDirectoryFetchThread;

    public FTPConnection(FTPServer iFTPServer) {
        if (sFTPConnectionInstances == null)
            sFTPConnectionInstances = new ArrayList<>();

        mFTPServerDAO = DataBase.getFTPServerDAO();
        mFTPServer = iFTPServer;
        sFTPConnectionInstances.add(this);
        mFTPClient = new FTPClient();
    }

    public FTPConnection(int iServerId) {
        if (sFTPConnectionInstances == null)
            sFTPConnectionInstances = new ArrayList<>();

        mFTPServerDAO = DataBase.getFTPServerDAO();
        mFTPServer = mFTPServerDAO.fetchById(iServerId);
        sFTPConnectionInstances.add(this);
        mFTPClient = new FTPClient();
    }

    public static FTPConnection getFTPConnection(int iServerId) {
        if (sFTPConnectionInstances == null)
            sFTPConnectionInstances = new ArrayList<>();

        for (FTPConnection lConnection : sFTPConnectionInstances) {
            if (lConnection.getFTPServerId() == iServerId)
                return lConnection;
        }
        return null;
    }

    public static String getErrorMessage(int iErrorCode) {
        if (iErrorCode == ERROR_UNKNOWN_HOST)
            return "Cannot resolve host";
        else if (iErrorCode == ERROR_CONNECTION_TIMEOUT)
            return "Connection timeout";
        else if (iErrorCode == ERROR_ALREADY_CONNECTED)
            return "Already connected";
        return "Connection error";
    }

    public FTPConnection getFTPConnectionIfExisting(int iServerId) {
        FTPServer lFTPServer = mFTPServerDAO.fetchById(iServerId);

        if (lFTPServer == null)
            return null;
        for (FTPConnection lConnection : sFTPConnectionInstances) {
            if (lConnection.getFTPServer().getDataBaseId() == lFTPServer.getDataBaseId()) {
                return lConnection;
            }
        }
        return null;
    }

    public void disconnect() {
        LogManager.info(TAG, "Disconnect");
        if (isConnected()) {
            if (mDirectoryFetchThread != null && mDirectoryFetchThread.isAlive())
                abortFetchDirectoryContent();
            try {
                mFTPClient.disconnect();
            } catch (IOException iE) {
                iE.printStackTrace();
            }
        } else {
            new Exception("Thread disconnection but not connected").printStackTrace();
        }

        sFTPConnectionInstances.remove(this);
        // TODO test to disconnect the connection mThread here to see if it exceptions
    }

    public void abortFetchDirectoryContent() {
        LogManager.info(TAG, "Abort fetch directory contents");
        if (!isConnected()) {
            if (mDirectoryFetchThread != null && mDirectoryFetchThread.isAlive()) {
                try {
                    mFTPClient.abort();
                } catch (IOException iE) {
                    iE.printStackTrace();
                }
                mDirectoryFetchThread.interrupt();
            }
        }
    }

    public void fetchDirectoryContent(final String iPath, final OnFetchDirectoryResult iOnFetchDirectoryResult) {
        LogManager.info(TAG, "Fetch directory contents");
        if (!isConnected())
            return;
        if (mDirectoryFetchThread != null && mDirectoryFetchThread.isAlive()) {
            abortFetchDirectoryContent();
        }

        mDirectoryFetchThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
//                    LogManager.error(TAG, "1");
                    FTPListParseEngine lDirectory = mFTPClient.initiateListParsing(null, iPath);
//                    LogManager.error(TAG, "2");
                    if (Thread.interrupted())
                        return;
                    if (!FTPReply.isPositiveCompletion(mFTPClient.getReplyCode())) {
                        throw new TimeoutException(mFTPClient.getReplyString());
                    }
                    iOnFetchDirectoryResult.onSuccess(lDirectory.getFiles());
                } catch (TimeoutException iE) {
                    if (!Thread.interrupted())
                        iOnFetchDirectoryResult.onFail(ERROR);
                } catch (IOException iE) {
                    iE.printStackTrace();
                    if (!Thread.interrupted())
                        iOnFetchDirectoryResult.onFail(ERROR);
                }
            }
        });
        mDirectoryFetchThread.start();
    }

    public void Connect(final OnConnectResult iOnConnectResult) {
        LogManager.info(TAG, "Connect");
        if (isConnected()) {
            LogManager.error(TAG, "Trying a connection but is already connected");
            new Exception("already connected").printStackTrace();
            iOnConnectResult.onFail(ERROR_ALREADY_CONNECTED);
            return;
        }

        mConnectionThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    LogManager.info(TAG, "Will connect with : \n" + mFTPServer.toString());

                    mFTPClient.setDefaultPort(mFTPServer.getPort());
                    mFTPClient.connect(InetAddress.getByName(mFTPServer.getServer()));
                    mFTPClient.login(mFTPServer.getUser(), mFTPServer.getPass());

                    if (!FTPReply.isPositiveCompletion(mFTPClient.getReplyCode())) {
                        mFTPClient.disconnect();
                        LogManager.error(TAG, "FTP server refused connection."); // TODO return state
                        iOnConnectResult.onFail(ERROR);
                    }
                    LogManager.info(TAG, "FTPClient status : " + mFTPClient.getStatus());

                    iOnConnectResult.onSuccess();
                } catch (UnknownHostException iE) {
                    iOnConnectResult.onFail(ERROR_UNKNOWN_HOST);
                    iE.printStackTrace();
                } catch (Exception iE) {
                    iOnConnectResult.onFail(ERROR);
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

    public boolean isFetchingFolders() {
        return mIsFetchingFolders;
    }

    public interface OnFetchDirectoryResult {
        void onSuccess(FTPFile[] iFTPFiles);

        void onFail(int iErrorCode);
    }

    public interface OnConnectResult {
        void onSuccess();

        void onFail(int iErrorCode);
    }

}

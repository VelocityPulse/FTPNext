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
    public static int ERROR_ALREADY_CONNECTING = 4;
    public static int ERROR_CONNECTION_INTERRUPTED = 5;

    private static String TAG = "FTP CONNECTION";

    private static int ITEM_FETCHED_BY_GROUP = 25;

    private static List<FTPConnection> sFTPConnectionInstances;
    private FTPServerDAO mFTPServerDAO;
    private FTPServer mFTPServer;
    private FTPClient mFTPClient;
    private String mLocalization;
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
        else if (iErrorCode == ERROR_ALREADY_CONNECTING)
            return "Already connecting";
        else if (iErrorCode == ERROR_CONNECTION_INTERRUPTED)
            return "Connection interrupted";

        return "Connection error";
    }

    public void destroyConnection() {
        if (isConnected())
            disconnect();
        else
            sFTPConnectionInstances.remove(this);
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

        sFTPConnectionInstances.remove(this);
        // TODO test to disconnect the connection mThread here to see if it exceptions
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
                        throw new TimeoutException(mFTPClient.getReplyString());
                    }
                    if (Thread.interrupted())
                        return;
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

    public void abortConnection() {
        LogManager.info(TAG, "Abort connection");
        if (mFTPClient.isConnected()) {
            disconnect();
            return;
        }
        if (isConnecting()) {
            mConnectionThread.interrupt();
            try {
                mFTPClient.abort();
            } catch (IOException iE) {
                iE.printStackTrace();
            }
        }
    }

    public void connect(final OnConnectResult iOnConnectResult) {
        LogManager.info(TAG, "connect");
        if (isConnected()) {
            LogManager.error(TAG, "Trying a connection but is already connected");
            new Exception("already connected").printStackTrace();
            iOnConnectResult.onFail(ERROR_ALREADY_CONNECTED);
            return;
        } else if (isConnecting()) {
            LogManager.error(TAG, "Trying a connection but is already connecting");
            new Exception("already connecting").printStackTrace();
            iOnConnectResult.onFail(ERROR_ALREADY_CONNECTING);
            return;
        }

        mConnectionThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    LogManager.info(TAG, "Will connect with : \n" + mFTPServer.toString());

                    mFTPClient.setDefaultPort(mFTPServer.getPort());
                    mFTPClient.connect(InetAddress.getByName(mFTPServer.getServer()));
                    if (Thread.interrupted()) {
                        mFTPClient.disconnect();
                        iOnConnectResult.onFail(ERROR_CONNECTION_INTERRUPTED);
                        return;
                    }

                    mFTPClient.login(mFTPServer.getUser(), mFTPServer.getPass());
                    if (Thread.interrupted()) {
                        mFTPClient.disconnect();
                        iOnConnectResult.onFail(ERROR_CONNECTION_INTERRUPTED);
                        return;
                    }

                    if (!FTPReply.isPositiveCompletion(mFTPClient.getReplyCode())) {
                        mFTPClient.disconnect();
                        LogManager.error(TAG, "FTP server refused connection."); // TODO return state
                        iOnConnectResult.onFail(ERROR);
                        return;
                    }
                    LogManager.info(TAG, "FTPClient status : " + mFTPClient.getStatus());
                    LogManager.info(TAG, "FTPClient code   : " + mFTPClient.getReplyCode());

                    if (isConnected()) {
                        LogManager.info(TAG, "FTPClient connected");
                        iOnConnectResult.onSuccess();
                    } else
                        LogManager.error(TAG, "FTPClient not connected");
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

    public boolean isConnecting() {
        return !isConnected() && mConnectionThread != null && mConnectionThread.isAlive();
    }

    public boolean isFetchingFolders() {
        return isConnected() && mDirectoryFetchThread != null && mDirectoryFetchThread.isAlive();
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

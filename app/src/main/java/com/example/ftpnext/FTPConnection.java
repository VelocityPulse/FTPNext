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
import java.util.ArrayList;
import java.util.List;

// TODO : Download save when app is killed
// TODO : Add listeners for onConnect, onFail...
// TODO : Add some states status

public class FTPConnection {

    private static String TAG = "FTP CONNECTION";

    private static int ITEM_FETCHED_BY_GROUP = 25;

    private static List<FTPConnection> sFTPConnectionInstances;
    private FTPServerDAO mFTPServerDAO;
    private FTPServer mFTPServer;
    private FTPClient mFTPClient;
    private String mLocalization;

    public FTPConnection(FTPServer iFTPServer) {
        if (sFTPConnectionInstances == null)
            sFTPConnectionInstances = new ArrayList<>();

        mFTPServerDAO = DataBase.getFTPServerDAO();
        mFTPServer = iFTPServer;
        sFTPConnectionInstances.add(this);
    }

    public FTPConnection(int iServerId) {
        if (sFTPConnectionInstances == null)
            sFTPConnectionInstances = new ArrayList<>();

        mFTPServerDAO = DataBase.getFTPServerDAO();
        mFTPServer = mFTPServerDAO.fetchById(iServerId);
        sFTPConnectionInstances.add(this);
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

    public void killConnection() {
        sFTPConnectionInstances.remove(this);
        // TODO test to kill the connection mThread here to see if it exceptions
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

    public void fetchDirectoryContent(final String iPath, final OnFetchDirectoryResult iOnFetchDirectoryResult) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (mFTPClient.isConnected()) {
                    try {
                        FTPListParseEngine lDirectory = mFTPClient.initiateListParsing(null, iPath);
                        iOnFetchDirectoryResult.onSuccess(lDirectory.getFiles());
                    } catch (IOException iE) {
                        iE.printStackTrace();
                        iOnFetchDirectoryResult.onFail("Error unknown");
                    }
                }
            }
        }).start();

    }

    public void Connect(final OnConnectResult iOnConnectResult) {
        new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    LogManager.info(TAG, "Will connect with : " + mFTPServer.toString());

                    mFTPClient = new FTPClient();
                    mFTPClient.setDefaultPort(mFTPServer.getPort());
                    mFTPClient.connect(InetAddress.getByName(mFTPServer.getServer()));
                    mFTPClient.login(mFTPServer.getUser(), mFTPServer.getPass());

                    if (!FTPReply.isPositiveCompletion(mFTPClient.getReplyCode())) {
                        mFTPClient.disconnect();
                        LogManager.error(TAG, "FTP server refused connection."); // TODO return state
                        iOnConnectResult.onFail(mFTPClient.getReplyString());
                    }
                    LogManager.info(TAG, "status : " + mFTPClient.getStatus());
                    iOnConnectResult.onSuccess();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
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

    private void printReplyCode() {
        int lReply = mFTPClient.getReplyCode();
        LogManager.info(TAG, "REPLY CODE :" + lReply);
    }

    public interface OnFetchDirectoryResult {
        void onSuccess(FTPFile[] iFTPFiles);

        void onFail(String iErrorMessage);
    }

    public interface OnConnectResult {
        void onSuccess();

        void onFail(String iErrorMessage);
    }

}

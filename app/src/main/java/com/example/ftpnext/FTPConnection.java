package com.example.ftpnext;

import com.example.ftpnext.core.LogManager;
import com.example.ftpnext.database.FTPServerTable.FTPServer;
import com.example.ftpnext.database.FTPServerTable.FTPServerDAO;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

// TODO : Download save when app is killed
// TODO : Add listeners for onConnect, onFail...
// TODO : Add some states status

public class FTPConnection {

    private static String TAG = "FTP CONNECTION";

    private static List<FTPConnection> sFTPConnectionInstances;
    private FTPServerDAO mFTPServerDAO;
    private FTPServer mFTPServer;
    private FTPClient mFTPClient;

    public FTPConnection(FTPServer iFTPServer) {
        if (sFTPConnectionInstances == null)
            sFTPConnectionInstances = new ArrayList<>();

        mFTPServer = iFTPServer;
        sFTPConnectionInstances.add(this);
    }

    public FTPConnection(int iServerId) {
        if (sFTPConnectionInstances == null)
            sFTPConnectionInstances = new ArrayList<>();

        mFTPServer = mFTPServerDAO.fetchById(iServerId);
        sFTPConnectionInstances.add(this);
    }

    public void killConnection() {
        sFTPConnectionInstances.remove(this);
        // TODO test to kill the connection thread here to see if it exceptions
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

    public void ConnectToServer() {
        Thread thread = new Thread(new Runnable() {

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
                    }

                    LogManager.info(TAG, "status : " + mFTPClient.getStatus());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        thread.start();
    }

    public FTPServer getFTPServer() {
        return mFTPServer;
    }

    private void printReplyCode() {
        int lReply = mFTPClient.getReplyCode();
        LogManager.info(TAG, "REPLY CODE :" + lReply);
    }
}

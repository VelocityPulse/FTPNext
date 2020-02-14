package com.example.ftpnext.ftpservices;

import com.example.ftpnext.core.LogManager;
import com.example.ftpnext.database.DataBase;
import com.example.ftpnext.database.FTPServerTable.FTPServer;
import com.example.ftpnext.database.PendingFileTable.PendingFile;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class FTPTransfer extends AFTPConnection {

    private static final String TAG = "FTP TRANSFER";

    private static List<FTPTransfer> sFTPTransferInstances;

    private OnTransferListener mTransferListener;
    private PendingFile mCandidate;

    private Thread mTransferThread;

    public FTPTransfer(FTPServer iFTPServer) {
        super(iFTPServer);

        if (sFTPTransferInstances == null)
            sFTPTransferInstances = new ArrayList<>();
        sFTPTransferInstances.add(this);
    }

    public FTPTransfer(int iServerId) {
        super(iServerId);

        if (sFTPTransferInstances == null)
            sFTPTransferInstances = new ArrayList<>();
        sFTPTransferInstances.add(this);
    }

    public static FTPTransfer[] getFTPTransferInstance(int iServerId) {
        LogManager.info(TAG, "Get FTP transfer instance");
        if (sFTPTransferInstances == null)
            sFTPTransferInstances = new ArrayList<>();

        List<FTPTransfer> lFTPTransferList = new ArrayList<>();
        for (FTPTransfer lItem : sFTPTransferInstances) {
            if (lItem.getFTPServerId() == iServerId)
                lFTPTransferList.add(lItem);
        }
        return lFTPTransferList.toArray(new FTPTransfer[0]);
    }

    private void initializeListeners() {
        setIOnConnectionLost(new IOnConnectionLost() {
            @Override
            public void onConnectionLost() {
                if (mTransferListener != null)
                    mTransferListener.onConnectionLost(mCandidate);

                reconnect(new OnConnectionRecover() {
                    @Override
                    public void onConnectionRecover() {
                        if (mTransferListener != null)
                            mTransferListener.onConnected(mCandidate);
                    }

                    @Override
                    public void onConnectionDenied(ErrorCodeDescription iErrorEnum, int iErrorCode) {
                        if (mTransferListener != null)
                            mTransferListener.onStop();
                    }
                });
            }
        });
    }

    @Override
    public boolean isBusy() {
        return mTransferThread != null;
    }

    public void downloadFiles(final PendingFile[] iSelection, @NotNull final OnTransferListener iOnTransferListener) {
        if (mTransferThread != null) {
            LogManager.error(TAG, "Transfer not finished");
            return;
        }

        mTransferThread = new Thread(new Runnable() {
            @Override
            public void run() {

                synchronized (this) {
                    for (PendingFile lItem : iSelection) {
                        if (!lItem.isStarted()) {
                            mCandidate = lItem;
                            mCandidate.setStarted(true);
                            break;
                        }
                    }
                }

                if (!isConnected()) {
                    connect(null);
                }
                // TODO : Continue here / While on connect until it connects

                mTransferListener = null;
            }
        });

        mTransferThread.start();
    }

    public abstract class OnTransferListener {

        public abstract void onStart();

        public abstract void onConnected(PendingFile iPendingFile);

        public abstract void onConnectionLost(PendingFile iPendingFile);

        public void onStartNewFile(PendingFile iPendingFile) {
            iPendingFile.setStarted(true);
            DataBase.getPendingFileDAO().update(iPendingFile);
        }

        public abstract void onTotalPendingFileProgress(int iProgress, int iTotalPendingFile);

        public abstract void onDownloadProgress(PendingFile iPendingFile, int iProgress, int iSize);

        public void onDownloadSuccess(PendingFile iPendingFile) {
            DataBase.getPendingFileDAO().delete(iPendingFile);
        }

        public abstract void onRightAccessFail(PendingFile iPendingFile);

        public abstract void onFail(PendingFile iPendingFile);

        public abstract void onStop();
    }
}

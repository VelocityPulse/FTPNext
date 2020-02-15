package com.example.ftpnext.ftpservices;

import com.example.ftpnext.commons.Utils;
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

    public void abortDownload() {
        if (mTransferThread != null)
            mTransferThread.interrupt();
    }

    @Override
    public boolean isBusy() {
        return mTransferThread != null;
    }

    public void downloadFiles(final PendingFile[] iSelection, @NotNull final OnTransferListener iOnTransferListener) {
        // TODO : Guard of if it's not already uploading
        if (mTransferThread != null) {
            LogManager.error(TAG, "Transfer not finished");
            return;
        }

        // TODO : Interrupts
        mTransferThread = new Thread(new Runnable() {
            @Override
            public void run() {

                while (true) {

                    if (mTransferThread.isInterrupted()) {
                        mTransferThread = null;
                        return;
                    }

                    mCandidate = selectNotStartedCandidate(iSelection);

                    // Stopping all transfer activities
                    if (mCandidate == null) {
                        mTransferListener = null;
                        iOnTransferListener.onStop();
                        return;
                        // TODO : y a plus rien a DL blablabla
                    }

                    DataBase.getPendingFileDAO().update(mCandidate);
                    iOnTransferListener.onStartNewFile(mCandidate);

                    if (!isConnected()) {
                        connect(null);
                    }

                    while (!isConnected()) {
                        LogManager.info(TAG, "Download files : Waiting connection");
                        Utils.sleep(10);

                        if (mTransferThread.isInterrupted()) {
                            if (mCandidate != null && mCandidate.isStarted()) {
                                DataBase.getPendingFileDAO().update(mCandidate.setStarted(false));
                            }
                            mTransferThread = null;
                            return;
                        }
                    }



                }
            }
        });

        mTransferThread.start();
    }


    public void uploadFiles(final PendingFile[] iSelection, @NotNull final OnTransferListener iOnTransferListener) {
        // TODO : Guard of if it's not already downloading
    }

    private PendingFile selectNotStartedCandidate(PendingFile[] iSelection) {
        LogManager.info(TAG, "Select not started candidate");

        PendingFile oRet;
        synchronized (this) {
            for (PendingFile lItem : iSelection) {
                if (!lItem.isStarted()) {
                    oRet = lItem;
                    oRet.setStarted(true);
                    LogManager.info(TAG, "Leaving select not started candidate");
                    return oRet;
                }
            }
        }

        LogManager.info(TAG, "Leaving select not started candidate");
        return null;
    }

    public abstract class OnTransferListener {

        public abstract void onConnected(PendingFile iPendingFile);

        public abstract void onConnectionLost(PendingFile iPendingFile);

        /**
         * @param iPendingFile File which has been selected for download
         */
        public abstract void onStartNewFile(PendingFile iPendingFile);

        public abstract void onDownloadProgress(PendingFile iPendingFile, int iProgress, int iSize);

        public void onDownloadSuccess(PendingFile iPendingFile) {
            DataBase.getPendingFileDAO().delete(iPendingFile); // TODO : Delete this line when we know where to use that func
        }

        public abstract void onRightAccessFail(PendingFile iPendingFile);


        /**
         * @param iPendingFile File that it's impossible to download for any error
         */
        public abstract void onFail(PendingFile iPendingFile); // TODO : Maybe add a error status

        /**
         * FTPTransfer has nothing to do anymore
         */
        public abstract void onStop();
    }
}

package com.example.ftpnext.ftpservices;

import com.example.ftpnext.commons.Utils;
import com.example.ftpnext.core.LogManager;
import com.example.ftpnext.database.DataBase;
import com.example.ftpnext.database.FTPServerTable.FTPServer;
import com.example.ftpnext.database.PendingFileTable.PendingFile;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.io.CopyStreamEvent;
import org.apache.commons.net.io.CopyStreamListener;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
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

    private void initCopyStreamListener(final OnTransferListener iOnTransferListener) {
        mFTPClient.setCopyStreamListener(new CopyStreamListener() {
            @Override
            public void bytesTransferred(CopyStreamEvent event) {

            }

            @Override
            public void bytesTransferred(long iTotalBytesTransferred, int iBytesTransferred, long iStreamSize) {
                iOnTransferListener.onDownloadProgress(mCandidate, iTotalBytesTransferred, iStreamSize);

            }
        });
    }

    public void abortDownload() {
        if (mTransferThread != null) {
            mTransferThread.interrupt();

            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        mFTPClient.abort();
                    } catch (IOException iE) {
                        iE.printStackTrace();
                    }
                }
            }).start();
        }
    }

    @Override
    public boolean isBusy() {
        return mTransferThread != null;
    }

    public void downloadFiles(final PendingFile[] iSelection, @NotNull final OnTransferListener iOnTransferListener) {
        LogManager.info(TAG, "Download files");
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

                    mCandidate = selectAvailableCandidate(iSelection);

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

                    while (!isConnected() || isConnecting()) {
                        LogManager.info(TAG, "Download files : Waiting connection");
                        Utils.sleep(100);

                        if (mTransferThread.isInterrupted()) {
                            if (mCandidate != null && mCandidate.isStarted()) {
                                DataBase.getPendingFileDAO().update(mCandidate.setStarted(false));
                            }
                            mTransferThread = null;
                            return;
                        }
                    }

                    LogManager.info(TAG, "Download files : Connected");

                    initCopyStreamListener(iOnTransferListener);

                    mFTPClient.setCopyStreamListener(new CopyStreamListener() {
                        @Override
                        public void bytesTransferred(CopyStreamEvent event) {

                        }

                        @Override
                        public void bytesTransferred(long totalBytesTransferred, int bytesTransferred, long streamSize) {
                            LogManager.info(TAG,
                                    "Total transferred :\t" + totalBytesTransferred +
                                            "bytes transferred :\t" + bytesTransferred +
                                            "stream size:\t\t\t" + streamSize);

                            iOnTransferListener.onDownloadProgress(mCandidate, totalBytesTransferred, streamSize);
                        }
                    });

                    iOnTransferListener.onConnected(mCandidate);

                    try {
                        mFTPClient.enterLocalPassiveMode();

                        LogManager.info(TAG, "\nGoing to write on the local path :\n\t" +
                                mFTPServer.getAbsolutePath() + "/" +
                                mCandidate.getEnclosingName() + mCandidate.getName() +
                                "\nGoing to fetch from the server path :\n\t" +
                                mCandidate.getPath());

//                        FTPFile lFTPFile = mFTPClient.mlistFile(mCandidate.getPath());
//                        mCandidate.setSize((int) lFTPFile.getSize());

                        FTPFile[] lFTPFiles = mFTPClient.mlistDir(mCandidate.getPath());
                        if (lFTPFiles.length > 0)
                            mCandidate.setSize((int) lFTPFiles[0].getSize());
                        // TODO : File not findable


                        File lLocalFile = new File(mFTPServer.getAbsolutePath() + "/" +
                                mCandidate.getEnclosingName() + mCandidate.getName());

                        if (!lLocalFile.exists()) {
                            if (!lLocalFile.createNewFile()) {
                                LogManager.error(TAG, "Impossible to create new file");
                                mCandidate.setHasProblem(true);
                                iOnTransferListener.onFail(mCandidate);
                                continue;
                            }
                            LogManager.info(TAG, "Creation success");
                        }

                        // DOWNLOAD :
                        OutputStream lOutputStream = new BufferedOutputStream(new FileOutputStream(lLocalFile));
                        boolean lSuccess = mFTPClient.retrieveFile(mCandidate.getPath(), lOutputStream);
                        LogManager.info(TAG, "Leaving retrieve file with result : " + lSuccess);

                        mFTPClient.enterLocalActiveMode();
                    } catch (IOException iE) {
                        iE.printStackTrace();
                    }


                    // While end
                }
            }
        });

        mTransferThread.start();
    }

    public void uploadFiles(final PendingFile[] iSelection, @NotNull final OnTransferListener iOnTransferListener) {
        // TODO : Guard of if it's not already downloading
    }

    private PendingFile selectAvailableCandidate(PendingFile[] iSelection) {
        LogManager.info(TAG, "Select not started candidate");

        PendingFile oRet;
        synchronized (this) {
            for (PendingFile lItem : iSelection) {
                if (!lItem.isStarted() && !lItem.isFinished() && !lItem.hasProblem()) {
                    oRet = lItem;
                    oRet.setStarted(true);
                    LogManager.info(TAG, "Leaving selectAvailableCandidate()");
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

        public abstract void onDownloadProgress(PendingFile iPendingFile, long iProgress, long iSize);

        public abstract void onDownloadSuccess(PendingFile iPendingFile);

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

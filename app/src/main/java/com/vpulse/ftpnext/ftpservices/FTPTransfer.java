package com.vpulse.ftpnext.ftpservices;

import com.vpulse.ftpnext.commons.Utils;
import com.vpulse.ftpnext.core.LogManager;
import com.vpulse.ftpnext.database.DataBase;
import com.vpulse.ftpnext.database.FTPServerTable.FTPServer;
import com.vpulse.ftpnext.database.PendingFileTable.PendingFile;

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

    private static final long UPDATE_TRANSFER_TIMER = 200;
    private static final int TRANSFER_FINISH_BREAK = 50;

    private static List<FTPTransfer> sFTPTransferInstances;

    private OnTransferListener mTransferListener;
    private PendingFile mCandidate;

    private Thread mTransferThread;
    private boolean mIsInterrupted;

    private long mTimer;
    private long mBytesTransferred;

    private int mTurn;
    private long mSpeedAverage1;
    private long mSpeedAverage2;
    private long mSpeedAverage3;
    private long mSpeedAverage4;
    private long mSpeedAverage5;

    public FTPTransfer(FTPServer iFTPServer) {
        super(iFTPServer);

        if (sFTPTransferInstances == null)
            sFTPTransferInstances = new ArrayList<>();
        sFTPTransferInstances.add(this);
    }

    public FTPTransfer(int iServerId) {
        super(iServerId);
        LogManager.info(TAG, "Constructor");

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

                if (mCandidate != null) {
                    mCandidate.setRemainingTimeInMin(0);
                    mCandidate.setSpeedInKo(0);
                    if (mTransferListener != null)
                        mTransferListener.onConnectionLost(mCandidate);
                }

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

                mBytesTransferred += iBytesTransferred;

                long lCurrentTimeMillis = System.currentTimeMillis();
                long lElapsedTime = lCurrentTimeMillis - mTimer;

                if (lElapsedTime > UPDATE_TRANSFER_TIMER) {
                    long lImmediateSpeedInKoS = ((mBytesTransferred * 1000) / UPDATE_TRANSFER_TIMER) / 1000;

                    settingAverageSpeed(lImmediateSpeedInKoS);
                    mCandidate.setSpeedInKo(getAverageSpeed());

                    float lRemainingTime = (float) mCandidate.getSize() / (float) mCandidate.getSpeedInKo();
                    mCandidate.setRemainingTimeInMin((int) lRemainingTime);
//                    LogManager.debug(TAG, "Remaining time : " + lRemainingTime / 60 / 60 / 60 + "min");

                    mCandidate.setProgress((int) iTotalBytesTransferred);
                    iOnTransferListener.onDownloadProgress(mCandidate, iTotalBytesTransferred, iStreamSize);

                    mBytesTransferred = 0;
                    mTimer = lCurrentTimeMillis;
                }
            }
        });
    }

    public void abortTransfer() {
//        LogManager.info(TAG, "Abort transfer");
//        if (mTransferThread != null) {
//            LogManager.info(TAG, "Interrupting");
//            mTransferThread.interrupt();
        mIsInterrupted = true;
//            LogManager.debug(TAG, "After interrupt : " + mTransferThread.isInterrupted());

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
//        }
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
        mIsInterrupted = false;
        mTransferThread = new Thread(new Runnable() {
            @Override
            public void run() {

                while (!mIsInterrupted) {

                    mCandidate = selectAvailableCandidate(iSelection);

                    // Stopping all transfer activities
                    if (mCandidate == null) {
                        mTransferListener = null;
                        iOnTransferListener.onStop();
                        return;
                        // TODO : y a plus rien a DL blablabla
                    }

                    LogManager.info(TAG, "CANDIDATE : \n" + mCandidate.toString());
                    DataBase.getPendingFileDAO().update(mCandidate);
                    iOnTransferListener.onStartNewFile(mCandidate);

                    LogManager.debug(TAG, "IS INTERRUPTED : " + mIsInterrupted);
                    if (mIsInterrupted) {
                        mTransferThread = null;
                        break;
                    }

                    if (!isConnected()) {
                        LogManager.info(TAG, "Not connected");
                        connect(null);
                    }

                    while (!isConnected() || isConnecting()) {
                        LogManager.info(TAG, "Download files : Waiting connection");
                        Utils.sleep(200);

                        if (mIsInterrupted) {
                            if (mCandidate != null && mCandidate.isStarted()) {
                                // TODO : Update database on each returns
                                DataBase.getPendingFileDAO().update(mCandidate.setStarted(false));
                            }
                            mTransferThread = null;
                            break;
                        }
                    }

                    if (mIsInterrupted) {
                        mTransferThread = null;
                        break;
                    }

                    LogManager.info(TAG, "Download files : Connected");

                    initCopyStreamListener(iOnTransferListener);

                    iOnTransferListener.onConnected(mCandidate);

                    try {
                        mFTPClient.enterLocalPassiveMode();

                        String lLocalPath = mFTPServer.getAbsolutePath() + "/" +
                                mCandidate.getEnclosingName() + mCandidate.getName();
                        String lRemotePath = mCandidate.getPath();

                        LogManager.info(TAG, "\nGoing to write on the local path :\n\t" +
                                lLocalPath +
                                "\nGoing to fetch from the server path :\n\t" +
                                lRemotePath);

                        if (mIsInterrupted) {
                            mTransferThread = null;
                            break;
                        }

                        FTPFile lFTPFile = null;
                        try {
                            lFTPFile = mFTPClient.mlistFile(mCandidate.getPath());
                        } catch (Exception iE) {
                            LogManager.info(TAG, "TryCatch worth");
                            iE.printStackTrace();
                        }

                        if (lFTPFile != null)
                            mCandidate.setSize((int) lFTPFile.getSize());
                        else
                            ;// TODO : File not findable

                        File lLocalFile = new File(mFTPServer.getAbsolutePath() + "/" +
                                mCandidate.getEnclosingName() + mCandidate.getName());

                        if (!lLocalFile.exists()) {
                            LogManager.error(TAG, "File not existing");
                            if (lLocalFile.getParentFile().mkdirs())
                                LogManager.info(TAG, "mkdir success");
                            if (!lLocalFile.createNewFile()) {
                                LogManager.error(TAG, "Impossible to create new file");
                                mCandidate.setHasProblem(true);
                                iOnTransferListener.onFail(mCandidate);

                                if (mIsInterrupted)
                                    break;
                                continue;
                            } else
                                LogManager.info(TAG, "Creation success");
                        }

                        if (mIsInterrupted) {
                            mTransferThread = null;
                            break;
                        }

                        // DOWNLOAD :
                        OutputStream lOutputStream = new BufferedOutputStream(new FileOutputStream(lLocalFile));
                        boolean lSuccess = mFTPClient.retrieveFile(mCandidate.getPath(), lOutputStream);
                        LogManager.info(TAG, "Leaving retrieve file with result : " + lSuccess);

                        mCandidate.setSpeedInKo(0);
                        mCandidate.setRemainingTimeInMin(0);

                        if (mIsInterrupted) {
                            mTransferThread = null;
                            break;
                        }

                        if (lSuccess) {
                            mCandidate.setFinished(true);
                            mCandidate.setProgress(mCandidate.getSize());
                            iOnTransferListener.onDownloadProgress(mCandidate,
                                    mCandidate.getProgress(), mCandidate.getSize());
                            iOnTransferListener.onDownloadSuccess(mCandidate);
                        }

                        mFTPClient.enterLocalActiveMode();
                    } catch (IOException iE) {
                        iE.printStackTrace();
//                        Thread.currentThread().interrupt();
                        if (mIsInterrupted) {
                            mTransferThread = null;
                            break;
                        }
                    }

                    Utils.sleep(TRANSFER_FINISH_BREAK);
                    // While end
                }
                mTransferThread = null;
                disconnect();
            }
        });

        mTransferThread.start();
    }

    public void uploadFiles(final PendingFile[] iSelection, @NotNull final OnTransferListener iOnTransferListener) {
        // TODO : Guard of if it's not already downloading
    }

    private PendingFile selectAvailableCandidate(PendingFile[] iSelection) {
        synchronized (FTPTransfer.class) {

            PendingFile oRet;
            LogManager.info(TAG, "Select not started candidate");
            for (PendingFile lItem : iSelection) {
                if (!lItem.isStarted() && !lItem.isFinished() && !lItem.hasProblem()) {
                    oRet = lItem;
                    oRet.setStarted(true);
                    LogManager.info(TAG, "Leaving selectAvailableCandidate()");
                    return oRet;
                }
            }
            LogManager.info(TAG, "Leaving select not started candidate");
        }

        return null;
    }

    private void settingAverageSpeed(long iValue) {
        if (mTurn == 0)
            mSpeedAverage1 = iValue;
        else if (mTurn == 1)
            mSpeedAverage2 = iValue;
        else if (mTurn == 2)
            mSpeedAverage3 = iValue;
        else if (mTurn == 3)
            mSpeedAverage4 = iValue;

        mTurn++;
        if (mTurn == 4) {
            mSpeedAverage5 = iValue;
            mTurn = 0;
        }
    }

    private long getAverageSpeed() {
        return (mSpeedAverage1 +
                mSpeedAverage2 +
                mSpeedAverage3 +
                mSpeedAverage4 +
                mSpeedAverage5) / 5;
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

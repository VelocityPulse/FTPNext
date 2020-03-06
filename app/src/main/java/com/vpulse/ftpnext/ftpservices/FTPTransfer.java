package com.vpulse.ftpnext.ftpservices;

import com.vpulse.ftpnext.commons.Utils;
import com.vpulse.ftpnext.core.LogManager;
import com.vpulse.ftpnext.core.NetworkManager;
import com.vpulse.ftpnext.database.DataBase;
import com.vpulse.ftpnext.database.FTPServerTable.FTPServer;
import com.vpulse.ftpnext.database.PendingFileTable.PendingFile;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPFile;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class FTPTransfer extends AFTPConnection {

    private static final String TAG = "FTP TRANSFER";

    private static final long UPDATE_TRANSFER_TIMER = 200;
    private static final int TRANSFER_FINISH_BREAK = 100;

    private static List<FTPTransfer> sFTPTransferInstances;

    private OnTransferListener mOnTransferListener;
    private PendingFile mCandidate;

    private Thread mTransferThread;
    private boolean mIsTransferring;
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

    @Override
    public void destroyConnection() {
        LogManager.info(TAG, "Destroy connection");
        abortTransfer();

        super.destroyConnection();
        sFTPTransferInstances.remove(this);

    }

    private void initializeListeners(final OnTransferListener iOnTransferListener) {
        setOnConnectionLost(new OnConnectionLost() {
            @Override
            public void onConnectionLost() {

                if (mCandidate != null) {
                    mCandidate.setRemainingTimeInMin(0);
                    mCandidate.setSpeedInKo(0);
                    mCandidate.setConnected(false);
                    if (iOnTransferListener != null)
                        iOnTransferListener.onConnectionLost(mCandidate);
                }

                reconnect(new OnConnectionRecover() {
                    @Override
                    public void onConnectionRecover() {
                        mCandidate.setConnected(true);
                        if (iOnTransferListener != null)
                            iOnTransferListener.onConnected(mCandidate);
                    }

                    @Override
                    public void onConnectionDenied(ErrorCodeDescription iErrorEnum, int iErrorCode) {
                        if (iOnTransferListener != null)
                            iOnTransferListener.onStop();
                    }
                });
            }
        });
    }

    private void notifyTransferProgress(long iTotalBytesTransferred, int iBytesTransferred, long iStreamSize) {
        notifyTransferProgress(iTotalBytesTransferred, iBytesTransferred, iStreamSize, false);
    }

    private void notifyTransferProgress(long iTotalBytesTransferred, int iBytesTransferred,
                                        long iStreamSize, boolean iForceNotify) {
        mBytesTransferred += iBytesTransferred;

        long lCurrentTimeMillis = System.currentTimeMillis();
        long lElapsedTime = lCurrentTimeMillis - mTimer;

        if (lElapsedTime > UPDATE_TRANSFER_TIMER || iForceNotify) {
            mCandidate.setConnected(true);
//            LogManager.debug(TAG, "Transfer...");
            long lImmediateSpeedInKoS = ((mBytesTransferred * 1000) / UPDATE_TRANSFER_TIMER) / 1000;

            settingAverageSpeed(lImmediateSpeedInKoS);
            mCandidate.setSpeedInKo(getAverageSpeed());

            float lRemainingTime = (float) mCandidate.getSize() / (float) mCandidate.getSpeedInKo();
            mCandidate.setRemainingTimeInMin((int) lRemainingTime);

            mCandidate.setProgress((int) iTotalBytesTransferred);
            mOnTransferListener.onDownloadProgress(mCandidate, iTotalBytesTransferred, iStreamSize);

            mBytesTransferred = 0;
            mTimer = lCurrentTimeMillis;
        }
    }

    public void abortTransfer() {
        mIsInterrupted = true;
    }

    @Override
    public boolean isBusy() {
        return mTransferThread != null;
    }

    public void downloadFiles(final PendingFile[] iSelection, @NotNull final OnTransferListener iOnTransferListener) {
        mOnTransferListener = iOnTransferListener;

        LogManager.info(TAG, "Download files");
        // TODO : Guard of if it's not already uploading
        if (mTransferThread != null) {
            LogManager.error(TAG, "Transfer not finished");
            return;
        }

        mIsInterrupted = false;
        mTransferThread = new Thread(new Runnable() {
            @Override
            public void run() {

                initializeListeners(mOnTransferListener);

                while (!mIsInterrupted) {

                    // ---------------- INIT PENDING FILE
                    mCandidate = selectAvailableCandidate(iSelection);

                    // Stopping all transfer activities
                    if (mCandidate == null) {
                        mOnTransferListener.onStop();
                        break;
                    }

                    LogManager.info(TAG, "CANDIDATE : \n" + mCandidate.toString());
                    DataBase.getPendingFileDAO().update(mCandidate);
                    mOnTransferListener.onNewFileSelected(mCandidate);

                    if (mIsInterrupted)
                        break;

                    // ---------------- INIT CONNECTION
                    connectionLooper();

                    if (mIsInterrupted)
                        break;

                    mOnTransferListener.onConnected(mCandidate);

                    // ---------------- INIT NAMES
                    final String lLocalPath = mFTPServer.getAbsolutePath() + "/" +
                            mCandidate.getEnclosingName() + mCandidate.getName();
                    final String lRemotePath = mCandidate.getPath();

                    LogManager.info(TAG, "\nGoing to write on the local path :\n\t" +
                            lLocalPath +
                            "\nGoing to fetch from the server path :\n\t" +
                            lRemotePath);

//                    if (mIsInterrupted) {
//                        mTransferThread = null;
//                        break;
//                    }

                    // ---------------- INIT LOCAL FILE
                    File lLocalFile = new File(mFTPServer.getAbsolutePath() + "/" +
                            mCandidate.getEnclosingName() + mCandidate.getName());
                    try {
                        if (lLocalFile.exists())
                            lLocalFile.delete(); // TODO : Debug, remove this

                        if (!lLocalFile.exists()) {
                            if (lLocalFile.getParentFile().mkdirs())
                                LogManager.info(TAG, "mkdir success");
                            if (!lLocalFile.createNewFile()) {
                                LogManager.error(TAG, "Impossible to create new file");
                                mCandidate.setIsAnError(true);
                                mOnTransferListener.onFail(mCandidate);
                                if (mIsInterrupted)
                                    break;
                                continue;
                            } else
                                LogManager.info(TAG, "Local creation success");
                        } else {
//                            mCandidate.setProgress(0);
                            mCandidate.setProgress((int) lLocalFile.length()); // TODO : TO TEST
                        }

                    } catch (Exception iE) {
                        iE.printStackTrace();
                        mCandidate.setIsAnError(true);
                        mOnTransferListener.onFail(mCandidate);
                        continue;
                    }

                    // ---------------- INIT FTP FILE
                    mFTPClient.enterLocalPassiveMode();
                    FTPFile lFTPFile;
                    try {
                        lFTPFile = mFTPClient.mlistFile(mCandidate.getPath());
                    } catch (Exception iE) {
                        iE.printStackTrace();
                        mCandidate.setIsAnError(true);
                        mOnTransferListener.onFail(mCandidate);
                        continue;
                    }

                    if (lFTPFile == null) {
                        mCandidate.setIsAnError(true);
                        mOnTransferListener.onFail(mCandidate);
                        continue;
                    }

                    mCandidate.setSize((int) lFTPFile.getSize());

                    // ---------------- DOWNLOAD
                    boolean lFinished = false;

                    while (!lFinished) {

                        if (mIsInterrupted)
                            break;

                        connectionLooper();

                        try {
                            mFTPClient.setFileType(FTP.BINARY_FILE_TYPE);
                        } catch (IOException iE) {
                            iE.printStackTrace();
                            mCandidate.setIsAnError(true);
                            mOnTransferListener.onFail(mCandidate);
                            continue;
                        }

                        mFTPClient.enterLocalPassiveMode();

                        OutputStream lLocalStream = null;
                        InputStream lRemoteStream = null;
                        try {

                            lLocalStream = new BufferedOutputStream(new FileOutputStream(lLocalFile, true));
                            byte[] bytesArray = new byte[8192];
                            int lBytesRead;
                            int lTotalRead = (int) lLocalFile.length();
                            int lFinalSize = (int) lFTPFile.getSize();
                            mCandidate.setProgress((int) lLocalFile.length());
                            mFTPClient.setRestartOffset(mCandidate.getProgress());

                            lRemoteStream = mFTPClient.retrieveFileStream(mCandidate.getPath());
                            if (lRemoteStream == null) {
                                LogManager.error(TAG, "Remote stream null");
                                mCandidate.setIsAnError(true);
                                mOnTransferListener.onFail(mCandidate);
                                mFTPClient.disconnect();
                                break;
                            }

                            // ---------------- DOWNLAND LOOP
                            while ((lBytesRead = lRemoteStream.read(bytesArray)) != -1) {
                                mIsTransferring = true;
                                lTotalRead += lBytesRead;
                                lLocalStream.write(bytesArray, 0, lBytesRead);

                                notifyTransferProgress(lTotalRead, lBytesRead, lFinalSize);

                                if (mIsInterrupted)
                                    break;
                            }
                            notifyTransferProgress(lTotalRead, lBytesRead, lFinalSize, true);
                            mIsTransferring = false;
                            lFinished = true;

                            if (mIsInterrupted)
                                break;

                            // Closing streams necessary before complete pending command
                            closeStreams(lLocalStream, lRemoteStream);

                            try {
                                mFTPClient.completePendingCommand();
                            } catch (IOException iE) {
                                iE.printStackTrace();
                                mCandidate.setIsAnError(true);
                                mOnTransferListener.onFail(mCandidate);
                                mFTPClient.disconnect();
                                break;
                            }
                            mFTPClient.enterLocalActiveMode();

                            mCandidate.setSpeedInKo(0);
                            mCandidate.setRemainingTimeInMin(0);

                            mCandidate.setFinished(true);
                            mCandidate.setProgress(mCandidate.getSize());
                            mOnTransferListener.onDownloadProgress(mCandidate,
                                    mCandidate.getProgress(), mCandidate.getSize());
                            mOnTransferListener.onDownloadSuccess(mCandidate);

                        } catch (Exception iE) {
                            iE.printStackTrace();
                            mIsTransferring = false;
                        } finally {
                            closeStreams(lLocalStream, lRemoteStream);
                            Utils.sleep(500); // Wait the connexion update status
                        }
                    }

                    Utils.sleep(TRANSFER_FINISH_BREAK);
                    // While end
                }
                mTransferThread = null;
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
            for (PendingFile lItem : iSelection) {
                if (!lItem.isStarted() && !lItem.isFinished() && !lItem.isAnError()) {
                    oRet = lItem;
                    oRet.setStarted(true);
                    return oRet;
                }
            }
        }

        return null;
    }

    private void connectionLooper() {
        LogManager.debug(TAG, "Connection looper. Is reconnecting : " + isReconnecting());
        if (!isConnected() && !isReconnecting()) {
            LogManager.info(TAG, "Not connected");
            connect(null);
        }

        while (!isConnected() || isConnecting() || isReconnecting()) {
//            LogManager.info(TAG, "Download files : Waiting connection");
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
        LogManager.info(TAG, "Download files : Connected : " + isConnected());
    }

    private void closeStreams(OutputStream iLocalStream, InputStream iRemoteStream) {
        try {
            if (iLocalStream != null) {
                LogManager.info(TAG, "Closing local stream");
                iLocalStream.close();
            }
            if (iRemoteStream != null) {
                LogManager.info(TAG, "Closing remote stream");
                iRemoteStream.close();
            }
        } catch (IOException iEx) {
            LogManager.error(TAG, "Closing streams not working");
            iEx.printStackTrace();
        }
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

        public abstract void onNewFileSelected(PendingFile iPendingFile);

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

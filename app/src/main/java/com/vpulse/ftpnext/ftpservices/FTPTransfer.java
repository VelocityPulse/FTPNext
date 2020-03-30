package com.vpulse.ftpnext.ftpservices;

import com.vpulse.ftpnext.commons.Utils;
import com.vpulse.ftpnext.core.ExistingFileAction;
import com.vpulse.ftpnext.core.LogManager;
import com.vpulse.ftpnext.database.DataBase;
import com.vpulse.ftpnext.database.FTPServerTable.FTPServer;
import com.vpulse.ftpnext.database.PendingFileTable.PendingFile;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPFile;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class FTPTransfer extends AFTPConnection {

    private static final String TAG = "FTP TRANSFER";

    private static final long UPDATE_TRANSFER_TIMER = 75;
    private static final int TRANSFER_FINISH_BREAK = 100;
    private static final int USER_WAIT_BREAK = 300;

    private static List<FTPTransfer> sFTPTransferInstances;

    private static boolean mIsAskingActionForExistingFile;

    private OnStreamClosed mOnStreamClosed;
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

    public static void notifyExistingFileActionIsDefined() {
        mIsAskingActionForExistingFile = false;
    }

    @Override
    public void destroyConnection() {
        LogManager.info(TAG, "Destroy connection");

        mOnStreamClosed = new OnStreamClosed() {
            @Override
            public void onStreamClosed() {
                mOnStreamClosed = null;
                FTPTransfer.super.destroyConnection();
                sFTPTransferInstances.remove(FTPTransfer.this);
            }
        };

        abortTransfer();
    }

    private void initializeListeners(final OnTransferListener iOnTransferListener) {
        setOnConnectionLost(new OnConnectionLost() {
            @Override
            public void onConnectionLost() {

                if (mCandidate != null) {
                    mCandidate.setRemainingTimeInMin(0);
                    mCandidate.setSpeedInByte(0);
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
                            iOnTransferListener.onStop(FTPTransfer.this);
                        if (!mCandidate.isFinished() && mCandidate.isStarted()) {
                            mCandidate.setStarted(false);
                            mOnTransferListener.onFileUnselected(mCandidate);
                        }
                        destroyConnection();
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
        float lElapsedTime = lCurrentTimeMillis - mTimer;

        if (lElapsedTime > UPDATE_TRANSFER_TIMER || iForceNotify) {

            long lImmediateSpeedInKoS = (long) ((mBytesTransferred / lElapsedTime) * 1000);
            if (lImmediateSpeedInKoS < 0)
                lImmediateSpeedInKoS = 0;

            settingAverageSpeed(lImmediateSpeedInKoS);
            mCandidate.setSpeedInByte(getAverageSpeed());

            float lRemainingTime = (float) mCandidate.getSize() / (float) mCandidate.getSpeedInByte();
            mCandidate.setRemainingTimeInMin((int) lRemainingTime);

            mCandidate.setProgress((int) iTotalBytesTransferred);
            mOnTransferListener.onTransferProgress(mCandidate, iTotalBytesTransferred, iStreamSize);

            mBytesTransferred = 0;
            mTimer = lCurrentTimeMillis;
        }
    }

    public boolean isTransferring() {
        return mTransferThread != null && mTransferThread.isAlive();
    }

    public void abortTransfer() {
        mIsInterrupted = true;
    }

    @Override
    public boolean isBusy() {
        return mTransferThread != null;
    }

    @Override
    protected int getConnectionType() {
        return CONNECTION_TRANSFER_TYPE;
    }

    public void downloadFiles(final PendingFile[] iSelection, @NotNull final OnTransferListener iOnTransferListener) {
        LogManager.info(TAG, "Download files");

        if (isTransferring()) {
            LogManager.error(TAG, "Transfer not finished");
            return;
        }

        mIsInterrupted = false;
        mTransferThread = new Thread(new Runnable() {
            @Override
            public void run() {

                mOnTransferListener = iOnTransferListener;
                initializeListeners(mOnTransferListener);

                while (!mIsInterrupted) {

                    // ---------------- INIT PENDING FILE
                    mCandidate = selectAvailableCandidate(iSelection);

                    // Stopping all transfer activities
                    if (mCandidate == null) {
                        mOnTransferListener.onStop(FTPTransfer.this);
                        destroyConnection();
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
                    final String lLocalFullPath = mCandidate.getLocalPath() + mCandidate.getName();
                    final String lRemoteFullPath = mCandidate.getRemotePath() + mCandidate.getName();

                    LogManager.info(TAG, "\nGoing to write on the local path :\n\t" +
                            lLocalFullPath +
                            "\nGoing to fetch from the server path :\n\t" +
                            lRemoteFullPath);

                    // ---------------- INIT FTP FILE
                    mFTPClient.enterLocalPassiveMode();
                    FTPFile lRemoteFile;
                    try {
                        lRemoteFile = mFTPClient.mlistFile(lRemoteFullPath);
                    } catch (Exception iE) {
                        iE.printStackTrace();
                        Utils.sleep(USER_WAIT_BREAK); // Break the while true speed
                        continue;
                    }

                    if (lRemoteFile == null) {
                        mCandidate.setIsAnError(true);
                        mOnTransferListener.onFail(mCandidate);
                        FTPLogManager.pushErrorLog(
                                "Failed to recover remote file : \"" + lRemoteFullPath + "\"");
                        continue;
                    }

                    // ---------------- INIT LOCAL FILE
                    File lLocalFile = new File(lLocalFullPath);
                    try {
                        if (!lLocalFile.exists()) {
                            if (lLocalFile.getParentFile().mkdirs()) // TODO : Test with lLocalFile = "/"
                                LogManager.info(TAG, "mkdir success");

                            if (lLocalFile.createNewFile())
                                LogManager.info(TAG, "Local creation success");
                            else {
                                LogManager.error(TAG, "Impossible to create new file");
                                mCandidate.setIsAnError(true);
                                mOnTransferListener.onFail(mCandidate);
                                FTPLogManager.pushErrorLog(
                                        "Impossible to write on the local storage at path : \"" +
                                                lLocalFullPath + "\"");
                                if (mIsInterrupted)
                                    break;
                                continue;
                            }

                        } else {

                            while (mCandidate.getExistingFileAction() == ExistingFileAction.NOT_DEFINED &&
                                    !mIsInterrupted) {
                                existingFileLooper();
                            }

                            if (mIsInterrupted)
                                break;

                            if (!manageDownloadExistingAction(lRemoteFile, lLocalFile))
                                continue;

                            mCandidate.setProgress((int) lLocalFile.length());
                        }

                    } catch (Exception iE) {
                        iE.printStackTrace();
                        mCandidate.setIsAnError(true);
                        mOnTransferListener.onFail(mCandidate);
                        FTPLogManager.pushErrorLog(
                                "Impossible to write on the local storage at path : \"" +
                                        lLocalFullPath + "\"");
                        continue;
                    }

                    // ---------------- DOWNLOAD
                    boolean lFinished = false;

                    while (!lFinished) {

                        if (mIsInterrupted)
                            break;

                        connectionLooper();

                        if (!setBinaryFileType()) {
                            Utils.sleep(USER_WAIT_BREAK); // Break the while speed
                            continue;
                        }

                        // Re-enter in local passive mode in case of a disconnection
                        mFTPClient.enterLocalPassiveMode();

                        OutputStream lLocalStream = null;
                        InputStream lRemoteStream = null;
                        try {

                            lLocalStream = new BufferedOutputStream(new FileOutputStream(lLocalFile, true));
                            byte[] bytesArray = new byte[8192];
                            int lBytesRead;
                            int lTotalRead = (int) lLocalFile.length();
                            int lFinalSize = (int) lRemoteFile.getSize();
                            mCandidate.setSize(lFinalSize);
                            mCandidate.setProgress((int) lLocalFile.length());
                            mFTPClient.setRestartOffset(mCandidate.getProgress());

                            lRemoteStream = mFTPClient.retrieveFileStream(lRemoteFullPath);
                            if (lRemoteStream == null) {
                                LogManager.error(TAG, "Remote stream null");
                                mCandidate.setIsAnError(true);
                                mOnTransferListener.onFail(mCandidate);
                                FTPLogManager.pushErrorLog("Impossible to retrieve remote file stream : \"" +
                                        lRemoteFullPath + "\"");
                                mFTPClient.disconnect();
                                break;
                            }
                            mCandidate.setIsAnError(false);

                            FTPLogManager.pushStatusLog(
                                    "Start download of \"" + mCandidate.getName() + "\"");

                            mCandidate.setConnected(true);
                            // ---------------- DOWNLAND LOOP
                            while ((lBytesRead = lRemoteStream.read(bytesArray)) != -1) {
                                mIsTransferring = true;
                                lTotalRead += lBytesRead;
                                lLocalStream.write(bytesArray, 0, lBytesRead);

                                notifyTransferProgress(lTotalRead, lBytesRead, lFinalSize);

                                if (mIsInterrupted)
                                    break;
                            }
                            // ---------------- DOWNLAND LOOP

                            notifyTransferProgress(lTotalRead, lBytesRead, lFinalSize, true);
                            mIsTransferring = false;
                            lFinished = true;

                            if (mIsInterrupted)
                                break;

                            // Closing streams necessary before complete pending command
                            closeDownloadStreams(lLocalStream, lRemoteStream);

                            try {
                                mFTPClient.completePendingCommand();
                            } catch (IOException iE) {
                                iE.printStackTrace();
                                mCandidate.setIsAnError(true);
                                mOnTransferListener.onFail(mCandidate);
                                mFTPClient.disconnect();
                                FTPLogManager.pushErrorLog("Failed to complete the transfer : \"" +
                                        mCandidate.getName() + "\"");
                                break;
                            }
                            mFTPClient.enterLocalActiveMode();

                            finishCandidateSuccessfully();

                        } catch (Exception iE) {
                            iE.printStackTrace();
                            mIsTransferring = false;
                        } finally {
                            closeDownloadStreams(lLocalStream, lRemoteStream);
                            Utils.sleep(USER_WAIT_BREAK); // Wait the connexion update status
                        }
                        // While upload end
                    }

                    if (mIsInterrupted && !mCandidate.isFinished()) {
                        mCandidate.setStarted(false);
                        iOnTransferListener.onFileUnselected(mCandidate);
                    }

                    Utils.sleep(TRANSFER_FINISH_BREAK);
                    // While candidate end
                }
                mTransferThread = null;
            }
        });

        mTransferThread.setName("FTP Download");
        mTransferThread.start();
    }

    public void uploadFiles(final PendingFile[] iSelection, @NotNull final OnTransferListener iOnTransferListener) {
        LogManager.info(TAG, "Upload files");

        if (isTransferring()) {
            LogManager.error(TAG, "Transfer not finished");
            return;
        }

        mIsInterrupted = false;
        mTransferThread = new Thread(new Runnable() {
            @Override
            public void run() {

                mOnTransferListener = iOnTransferListener;
                initializeListeners(mOnTransferListener);

                while (!mIsInterrupted) {

                    // ---------------- INIT PENDING FILE
                    mCandidate = selectAvailableCandidate(iSelection);

                    // Stopping all transfer activities
                    if (mCandidate == null) {
                        mOnTransferListener.onStop(FTPTransfer.this);
                        destroyConnection();
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
                    final String lFullLocalPath = mCandidate.getLocalPath() + mCandidate.getName();
                    final String lFullRemotePath = mCandidate.getRemotePath() + mCandidate.getName();

                    LogManager.info(TAG, "\nGoing to write on the remote path :\n\t" +
                            lFullRemotePath +
                            "\nGoing to read from the local path :\n\t" +
                            lFullLocalPath);

                    // ---------------- INIT REMOTE FILE
                    File lLocalFile = new File(lFullLocalPath);

                    if (!lLocalFile.exists()) {
                        LogManager.error(TAG, "Local file doesn't exist");
                        mCandidate.setIsAnError(true);
                        mOnTransferListener.onFail(mCandidate);
                        FTPLogManager.pushErrorLog("Failed to retrieve local file : \"" +
                                lFullLocalPath + "\"");
                        continue;
                    }

                    mFTPClient.enterLocalPassiveMode();
                    FTPFile lRemoteFile;
                    try {
                        lRemoteFile = mFTPClient.mlistFile(lFullRemotePath);
                    } catch (Exception iE) {
                        iE.printStackTrace();
                        // Possibly socket error, retry another time.
                        continue;
                    }

                    // IF REMOTE FILE DOESN'T EXISTS
                    if (lRemoteFile == null) {
                        try {
                            if (createRecursiveDirectories(mCandidate.getRemotePath()))
                                LogManager.info(TAG, "Success creating folders");
                            else
                                throw new IOException("Impossible to create new file");

                        } catch (IOException iE) {
                            iE.printStackTrace();
                            LogManager.error(TAG, "Impossible to create new file");
                            FTPLogManager.pushErrorLog("Impossible to create path");
                            mCandidate.setIsAnError(true);
                            mOnTransferListener.onFail(mCandidate);
                            continue;
                        }
                    }
                    // ELSE IF REMOTE FILE EXISTS
                    else {
                        while (mCandidate.getExistingFileAction() == ExistingFileAction.NOT_DEFINED &&
                                !mIsInterrupted) {
                            existingFileLooper();
                        }

                        if (mIsInterrupted)
                            break;

                        if (!manageUploadExistingAction(lRemoteFile, lLocalFile))
                            continue;
                    }

                    // ---------------- UPLOAD
                    boolean lFinished = false;

                    while (!lFinished) {

                        if (mIsInterrupted)
                            break;

                        connectionLooper();

                        try {
                            lRemoteFile = mFTPClient.mlistFile(lFullRemotePath);
                        } catch (Exception iE) {
                            iE.printStackTrace();
                            // Possibly socket error, retry another time.
                            continue;
                        }

                        if (!setBinaryFileType()) {
                            Utils.sleep(USER_WAIT_BREAK); // Break the while speed
                            continue;
                        }

                        // Re-enter in local passive mode in case of a disconnection
                        mFTPClient.enterLocalPassiveMode();


                        FileInputStream lLocalStream = null;
                        OutputStream lRemoteStream = null;
                        try {
                            lLocalStream = new FileInputStream(lLocalFile);
                            byte[] bytesArray = new byte[16384];
                            int lBytesRead;
                            int lTotalRead = lRemoteFile == null ? 0 : (int) lRemoteFile.getSize();
                            int lFinalSize = (int) lLocalFile.length();
                            mCandidate.setSize(lFinalSize);
                            mCandidate.setProgress(lTotalRead);

                            lRemoteStream = mFTPClient.appendFileStream(lFullRemotePath);
                            if (lRemoteStream == null) {
                                LogManager.error(TAG, "lRemoteStream == null");
                                mCandidate.setIsAnError(true);
                                mOnTransferListener.onFail(mCandidate);
                                mFTPClient.disconnect();
                                FTPLogManager.pushErrorLog("Failed to retrieve remote file stream : \"" +
                                        lFullRemotePath + "\"");
                                break;
                            }

                            mCandidate.setConnected(true);
                            lLocalStream.skip(lTotalRead);
                            // ---------------- UPLOAD LOOP
                            while ((lBytesRead = lLocalStream.read(bytesArray)) != -1) {
                                mIsTransferring = true;
                                lTotalRead += lBytesRead;

                                lRemoteStream.write(bytesArray, 0, lBytesRead);

                                notifyTransferProgress(lTotalRead, lBytesRead, lFinalSize);

                                if (mIsInterrupted)
                                    break;
                            }
                            // ---------------- UPLOAD LOOP

                            notifyTransferProgress(lTotalRead, lBytesRead, lFinalSize, true);
                            mIsTransferring = false;
                            lFinished = true;

                            if (mIsInterrupted)
                                break;

                            // Closing streams necessary before complete pending command
                            closeUploadStreams(lLocalStream, lRemoteStream);

                            try {
                                mFTPClient.completePendingCommand();
                            } catch (IOException iE) {
                                iE.printStackTrace();
                                mCandidate.setIsAnError(true);
                                mOnTransferListener.onFail(mCandidate);
                                mFTPClient.disconnect();
                                FTPLogManager.pushErrorLog("Failed to complete the transfer : \"" +
                                        mCandidate.getName() + "\"");
                                break;
                            }
                            mFTPClient.enterLocalActiveMode();

                            finishCandidateSuccessfully();

                        } catch (Exception iE) {
                            iE.printStackTrace();
                            mIsTransferring = false;
                        } finally {
                            closeUploadStreams(lLocalStream, lRemoteStream);
                            Utils.sleep(USER_WAIT_BREAK); // Wait the connexion update status
                        }
                    }

                    // Issue : The remote file was removed after a fast wifi re connexion
                    // Or when we killed the app
                    // Answer : It appears that it is a FTP server problem and absolutely not a
                    // ftp client problem

                    Utils.sleep(TRANSFER_FINISH_BREAK);
                    // While upload end
                }
                mTransferThread = null;
                // While candidate end
            }
        });

        mTransferThread.setName("FTP Upload");
        mTransferThread.start();
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

    private void finishCandidateSuccessfully() {
        mCandidate.setSpeedInByte(0);
        mCandidate.setRemainingTimeInMin(0);
        mCandidate.setFinished(true);
        mCandidate.setProgress(mCandidate.getSize());
        mOnTransferListener.onTransferProgress(mCandidate,
                mCandidate.getProgress(), mCandidate.getSize());
        mOnTransferListener.onTransferSuccess(mCandidate);
        FTPLogManager.pushSuccessLog("Download of " + mCandidate.getName());
    }

    private void connectionLooper() {
        while (!isRemotelyConnected()) {
            if (mIsInterrupted)
                break;

            if (!isReconnecting()) {
                if (isLocallyConnected())
                    disconnect();
                while (isLocallyConnected()) {
                    Utils.sleep(RECONNECTION_WAITING_TIME);
                }

                connect(new OnConnectionResult() {
                    @Override
                    public void onSuccess() {
                    }

                    @Override
                    public void onFail(ErrorCodeDescription iErrorEnum, int iErrorCode) {
                        LogManager.error(TAG, "Connection loop failed to connect");
                        if (iErrorEnum == ErrorCodeDescription.ERROR_SERVER_DENIED_CONNECTION) {
                            FTPLogManager.pushErrorLog("Server denied connection ...");
                            mCandidate.setStarted(false);
                            mOnTransferListener.onStateUpdateRequested(mCandidate);

                            if (!mCandidate.isFinished() && mCandidate.isConnected())
                                mOnTransferListener.onFileUnselected(mCandidate);

                            if (mOnTransferListener != null)
                                mOnTransferListener.onStop(FTPTransfer.this);
                            destroyConnection();
                        }
                    }
                });
            }

            while (!isLocallyConnected() || isConnecting() || isReconnecting()) {
                Utils.sleep(200);

                if (mIsInterrupted) {
                    if (mCandidate != null && mCandidate.isStarted()) {
                        // TODO : Update database on each returns
                        DataBase.getPendingFileDAO().update(mCandidate.setStarted(false));
                    }
                    break;
                }
            }
            Utils.sleep(1000);
        }
        LogManager.info(TAG, "Is connected : true");
    }

    private void existingFileLooper() {
        LogManager.info(TAG, "Existing file looper");

        while (mIsAskingActionForExistingFile && !mIsInterrupted) {
            Utils.sleep(USER_WAIT_BREAK);
        }

        if (mIsInterrupted)
            return;

        synchronized (FTPTransfer.class) {
            if (mCandidate.getExistingFileAction() == ExistingFileAction.NOT_DEFINED &&
                    !mIsAskingActionForExistingFile) {
                mIsAskingActionForExistingFile = true;

                mOnTransferListener.onExistingFile(mCandidate);

                while (mIsAskingActionForExistingFile && !mIsInterrupted)
                    Utils.sleep(USER_WAIT_BREAK);
            }
        }
    }

    /**
     * @param iRemoteFile the remote FTPFile
     * @param iLocalFile  the local File
     * @return false if it should pass continue (pass to the next candidate). True if
     * it should pass to the upload
     */
    private boolean manageDownloadExistingAction(FTPFile iRemoteFile, File iLocalFile) {
        switch (mCandidate.getExistingFileAction()) {
            case REPLACE_FILE:
                iLocalFile.delete();
                break;
            case RESUME_FILE_TRANSFER:
                break;
            case REPLACE_IF_SIZE_IS_DIFFERENT:
                if (iLocalFile.length() != iRemoteFile.getSize())
                    iLocalFile.delete();
                break;
            case REPLACE_IF_FILE_IS_MORE_RECENT:
            case REPLACE_IF_SIZE_IS_DIFFERENT_OR_FILE_IS_MORE_RECENT:
            case IGNORE:
            default:
                mCandidate.setProgress((int) iLocalFile.length());
                mCandidate.setFinished(true);
                mOnTransferListener.onTransferSuccess(mCandidate);
                return false;
        }
        return true;
    }

    /**
     * @param iRemoteFile the remote FTPFile
     * @param iLocalFile  the local File
     * @return false if it should pass continue (pass to the next candidate). True if
     * it should pass to the upload
     */
    private boolean manageUploadExistingAction(FTPFile iRemoteFile, File iLocalFile) {
        switch (mCandidate.getExistingFileAction()) {
            case RESUME_FILE_TRANSFER:
                break;
            case REPLACE_FILE:
                try {
                    mFTPClient.deleteFile(iRemoteFile.getName());
                } catch (IOException iE) {
                    iE.printStackTrace();
                }
                break;
            case REPLACE_IF_SIZE_IS_DIFFERENT:
                if (iRemoteFile.getSize() != iLocalFile.length()) {
                    try {
                        mFTPClient.deleteFile(iRemoteFile.getName());
                    } catch (IOException iE) {
                        iE.printStackTrace();
                    }
                }
                break;
            case REPLACE_IF_FILE_IS_MORE_RECENT:
            case REPLACE_IF_SIZE_IS_DIFFERENT_OR_FILE_IS_MORE_RECENT:
            case IGNORE:
            default:
                mCandidate.setProgress((int) iLocalFile.length());
                mCandidate.setFinished(true);
                mOnTransferListener.onTransferSuccess(mCandidate);
                return false;
        }
        return true;
    }

    private boolean createRecursiveDirectories(String iFullPath) throws IOException {
        final String lOriginalWorkingDir = mFTPClient.printWorkingDirectory();

        if (iFullPath.startsWith("/"))
            iFullPath = iFullPath.substring(1);
        String[] lPathElements = iFullPath.split("/");

        if (lPathElements == null || lPathElements.length <= 0)
            return true;

        for (String lDir : lPathElements) {
            boolean lExists = mFTPClient.changeWorkingDirectory(lDir);

            if (!lExists) {
                if (mFTPClient.makeDirectory(lDir))
                    mFTPClient.changeWorkingDirectory(lDir);
                else {
                    mFTPClient.changeWorkingDirectory(lOriginalWorkingDir);
                    return false;
                }
            }
        }
        mFTPClient.changeWorkingDirectory(lOriginalWorkingDir);
        return true;
    }

    private void closeDownloadStreams(OutputStream iLocalStream, InputStream iRemoteStream) {
        LogManager.info(TAG, "Closing download streams");
        try {
            if (iLocalStream != null)
                iLocalStream.close();
            if (iRemoteStream != null)
                iRemoteStream.close();
            LogManager.info(TAG, "Closing streams success");
        } catch (IOException iEx) {
            LogManager.error(TAG, "Closing streams not working");
            iEx.printStackTrace();
        } finally {
            if (mOnStreamClosed != null)
                mOnStreamClosed.onStreamClosed();
        }
    }

    private void closeUploadStreams(FileInputStream iLocalStream, OutputStream iRemoteStream) {
        LogManager.info(TAG, "Closing upload streams");
        try {
            if (iLocalStream != null)
                iLocalStream.close();
            if (iRemoteStream != null)
                iRemoteStream.close();
            LogManager.info(TAG, "Closing streams success");
        } catch (IOException iEx) {
            LogManager.error(TAG, "Closing streams not working");
            iEx.printStackTrace();
        } finally {
            if (mOnStreamClosed != null)
                mOnStreamClosed.onStreamClosed();
        }
    }

    private boolean setBinaryFileType() {
        try {
            mFTPClient.setFileType(FTP.BINARY_FILE_TYPE);
            return true;
        } catch (IOException iE) {
            iE.printStackTrace();
            return false;
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

    public interface OnTransferListener {

        void onConnected(PendingFile iPendingFile);

        void onConnectionLost(PendingFile iPendingFile);

        void onNewFileSelected(PendingFile iPendingFile);

        void onFileUnselected(PendingFile iPendingFile);

        void onTransferProgress(PendingFile iPendingFile, long iProgress, long iSize);

        void onTransferSuccess(PendingFile iPendingFile);

        void onStateUpdateRequested(PendingFile iPendingFile);

        /**
         * Called when the file to download is already existing on the local storage.
         * You should call {@link #notifyExistingFileActionIsDefined} to make recover all the transfers
         *
         * @param iPendingFile Already existing file
         */
        void onExistingFile(PendingFile iPendingFile);

        /**
         * @param iPendingFile File that it's impossible to download for any error
         */
        void onFail(PendingFile iPendingFile); // TODO : Maybe add a error status

        /**
         * FTPTransfer has nothing to do anymore
         */
        void onStop(FTPTransfer iFTPTransfer);
    }

    private interface OnStreamClosed {

        void onStreamClosed();

    }
}

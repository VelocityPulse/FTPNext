package com.example.ftpnext.ftpservices;

import com.example.ftpnext.commons.FTPFileUtils;
import com.example.ftpnext.core.LogManager;
import com.example.ftpnext.database.FTPServerTable.FTPServer;

import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FTPServices extends FTPConnection {

    private static final String TAG = "FTP SERVICES";

    private static final int DELETE_THREAD_SLEEP_TIME = 500;
    private static final int ITEM_FETCHED_BY_GROUP = 25;

    private static List<FTPServices> sFTPServicesInstances;

    private boolean mStartingFetchDirectory;

    private Thread mDirectoryFetchThread;
    private Thread mDeleteFileThread;
    private Thread mCreateDirectoryThread;

    private boolean mPauseDeleting;
    private boolean mByPassDeletingRightErrors;
    private boolean mByPassDeletingFailErrors;

    public FTPServices(FTPServer iFTPServer) {
        super(iFTPServer);

        if (sFTPServicesInstances == null)
            sFTPServicesInstances = new ArrayList<>();
        sFTPServicesInstances.add(this);
    }

    public FTPServices(int iServerId) {
        super(iServerId);

        if (sFTPServicesInstances == null)
            sFTPServicesInstances = new ArrayList<>();
        sFTPServicesInstances.add(this);
    }

    public static FTPServices getFTPServicesInstance(int iServerId) {
        LogManager.info(TAG, "Get FTP connection");
        if (sFTPServicesInstances == null)
            sFTPServicesInstances = new ArrayList<>();

        for (FTPServices lServices : sFTPServicesInstances) {
            if (lServices.getFTPServerId() == iServerId)
                return lServices;
        }
        return null;
    }

    public void destroyConnection() {
        LogManager.info(TAG, "Destroy connection");
        super.destroyConnection();
        sFTPServicesInstances.remove(this);
    }

    public void disconnect() {
        LogManager.info(TAG, "Disconnect");

        if (isConnected()) {
            if (isFetchingFolders())
                abortFetchDirectoryContent();
            if (isDeletingFiles() && !isReconnecting())
                abortDeleting();
            super.disconnect();
        }
    }

    public void updateWorkingDirectory(final String iNewWorkingDirectory) {
        LogManager.info(TAG, "Update working directory");
        if (!isConnected())
            return;

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    FTPFile lWorkingDir = mFTPClient.mlistFile(iNewWorkingDirectory);
                    if (lWorkingDir != null && lWorkingDir.isDirectory()) {
                        mCurrentDirectory = lWorkingDir;
                    }
                } catch (IOException iE) {
                    iE.printStackTrace();
                }
            }
        }).start();
    }

    public void abortFetchDirectoryContent() {
        LogManager.info(TAG, "Abort fetch directory contents");

        if (isFetchingFolders())
            mDirectoryFetchThread.interrupt();
    }

    public void abortDeleting() {
        LogManager.info(TAG, "Abort deleting");

        if (isDeletingFiles())
            mDeleteFileThread.interrupt();
    }

    public void fetchDirectoryContent(final String iPath, @NotNull final IOnFetchDirectoryResult iOnFetchDirectoryResult) {
        LogManager.info(TAG, "Fetch directory contents");
        if (!isConnected()) {
            LogManager.error(TAG, "Connection not established");
            return;
        }

        if (isFetchingFolders()) {
            LogManager.info(TAG, "Canceling fetch request");
            return;
        }

        mStartingFetchDirectory = true;
        mDirectoryFetchThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Sometimes, isFetchingDirectory returned false while it was actually
                    // Trying to fetch a dir, but the thread wasn't started yet
                    mStartingFetchDirectory = false;
                    FTPFile lLeavingDirectory = mCurrentDirectory;

                    FTPFile lTargetDirectory = mFTPClient.mlistFile(iPath);
                    if (!lTargetDirectory.isDirectory()) { // TODO : put null security for release
                        if (iOnFetchDirectoryResult != null)
                            iOnFetchDirectoryResult.onFail(ErrorCodeDescription.ERROR_NOT_A_DIRECTORY,
                                    FTPReply.FILE_UNAVAILABLE);
                        return;
                    }
                    if (!lTargetDirectory.hasPermission(FTPFile.USER_ACCESS, FTPFile.EXECUTE_PERMISSION)) {
                        if (iOnFetchDirectoryResult != null)
                            iOnFetchDirectoryResult.onFail(ErrorCodeDescription.ERROR_EXECUTE_PERMISSION_MISSED,
                                    633);
                        return;
                    }
                    if (!lTargetDirectory.hasPermission(FTPFile.USER_ACCESS, FTPFile.READ_PERMISSION)) {
                        if (iOnFetchDirectoryResult != null)
                            iOnFetchDirectoryResult.onFail(ErrorCodeDescription.ERROR_READ_PERMISSION_MISSED,
                                    633);
                        return;
                    }

                    if (Thread.interrupted()) {
                        iOnFetchDirectoryResult.onInterrupt();
                        return;
                    }

                    mFTPClient.enterLocalPassiveMode();
                    mFTPClient.changeWorkingDirectory(iPath);
                    FTPFile[] lFiles = mFTPClient.listFiles();
                    if (Thread.interrupted()) {
                        mFTPClient.changeWorkingDirectory(lLeavingDirectory.getName());
                        mCurrentDirectory = lLeavingDirectory;
                        iOnFetchDirectoryResult.onInterrupt();
                        return;
                    }
                    mCurrentDirectory = lTargetDirectory;
                    mFTPClient.enterLocalActiveMode();
                    if (Thread.interrupted()) {
                        mFTPClient.changeWorkingDirectory(lLeavingDirectory.getName());
                        mCurrentDirectory = lLeavingDirectory;
                        iOnFetchDirectoryResult.onInterrupt();
                        return;
                    }
                    if (!FTPReply.isPositiveCompletion(mFTPClient.getReplyCode())) {
                        throw new IOException(mFTPClient.getReplyString());
                    }
                    if (Thread.interrupted()) {
                        mFTPClient.changeWorkingDirectory(lLeavingDirectory.getName());
                        mCurrentDirectory = lLeavingDirectory;
                        iOnFetchDirectoryResult.onInterrupt();
                        return;
                    }
                    iOnFetchDirectoryResult.onSuccess(lFiles);

                } catch (IOException iE) {
                    iE.printStackTrace();
                    if (!Thread.interrupted()) {
                        if (mFTPClient.getReplyCode() == 450)
                            iOnFetchDirectoryResult.onFail(ErrorCodeDescription.ERROR_NOT_REACHABLE,
                                    mFTPClient.getReplyCode());
                        else
                            iOnFetchDirectoryResult.onFail(ErrorCodeDescription.ERROR,
                                    mFTPClient.getReplyCode());
                    } else
                        iOnFetchDirectoryResult.onInterrupt();

                }
            }
        });
        mDirectoryFetchThread.start();
    }

    public void createDirectory(final String iPath, final String iName, final IOnCreateDirectoryResult iOnCreateDirectoryResult) {
        if (!isConnected()) {
            LogManager.error(TAG, "Connection not established");
            return;
        } else if (isCreatingFolder()) {
            LogManager.error(TAG, "Is already creating directory");
            return;
        }

        mCreateDirectoryThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    FTPFile lExistingFile = mFTPClient.mlistFile(iPath + "/" + iName);
                    if (lExistingFile != null && lExistingFile.isDirectory()) {
                        if (iOnCreateDirectoryResult != null)
                            iOnCreateDirectoryResult.onFail(ErrorCodeDescription.ERROR_DIRECTORY_ALREADY_EXISTING,
                                    FTPReply.STORAGE_ALLOCATION_EXCEEDED);
                        return;
                    }

                    if (!mFTPClient.makeDirectory(iPath + "/" + iName))
                        throw new IOException("Creation failed.");
                    FTPFile lCreatedFile = mFTPClient.mlistFile(iPath + "/" + iName);
                    lCreatedFile.setName(iName);
                    if (iOnCreateDirectoryResult != null)
                        iOnCreateDirectoryResult.onSuccess(lCreatedFile);
                } catch (IOException iE) {
                    iE.printStackTrace();
                    if (iOnCreateDirectoryResult != null)
                        iOnCreateDirectoryResult.onFail(ErrorCodeDescription.ERROR,
                                FTPReply.UNRECOGNIZED_COMMAND);
                }
            }
        });
        mCreateDirectoryThread.start();
    }

    public void deleteFile(FTPFile iFTPFile, @NotNull final AOnDeleteListener iAOnDeleteListener) {
        deleteFiles(new FTPFile[]{iFTPFile}, iAOnDeleteListener);
    }

    public void deleteFiles(final FTPFile[] iSelection, @NotNull final AOnDeleteListener iAOnDeleteListener) {
        LogManager.info(TAG, "Delete files");
        if (!isConnected()) {
            LogManager.error(TAG, "Connection not established");
            return;
        } else if (isDeletingFiles()) {
            LogManager.error(TAG, "Is already deleting files");
            return;
        } else if (iAOnDeleteListener == null) {
            LogManager.error(TAG, "Delete listener is null");
            new NullPointerException("Delete listener is null").printStackTrace();
            return;
        }

        mPauseDeleting = false;
        mByPassDeletingRightErrors = false;
        mByPassDeletingFailErrors = false;
        mDeleteFileThread = new Thread(new Runnable() {

            private void recursiveDeletion(FTPFile iFTPFile, int iProgress, int iTotal) throws IOException, InterruptedException {

                while (mPauseDeleting && !Thread.interrupted())
                    Thread.sleep(DELETE_THREAD_SLEEP_TIME);
                if (Thread.interrupted())
                    return;

                LogManager.info(TAG, "Recursive deletion : " + iFTPFile.getName() + " " + iFTPFile.isDirectory());

                if (iFTPFile.isDirectory()) {

                    iAOnDeleteListener.onProgressDirectory(
                            iProgress,
                            iTotal,
                            iFTPFile.getName());

                    // DIRECTORY
                    if (iFTPFile.hasPermission(FTPFile.USER_ACCESS, FTPFile.READ_PERMISSION)) {

                        FTPFile[] lFiles = mFTPClient.listFiles(iFTPFile.getName());
                        int lProgress = 0;
                        for (FTPFile lFile : lFiles) {
                            iAOnDeleteListener.onProgressDirectory(
                                    iProgress,
                                    iTotal,
                                    iFTPFile.getName());

                            lFile.setName(iFTPFile.getName() + "/" + lFile.getName());
                            recursiveDeletion(lFile, lProgress++, lFiles.length);

                            while (mPauseDeleting && !Thread.interrupted())
                                Thread.sleep(DELETE_THREAD_SLEEP_TIME);
                            if (Thread.interrupted())
                                return;
                        }

                        iAOnDeleteListener.onProgressDirectory(
                                iProgress,
                                iTotal,
                                iFTPFile.getName());

                        iAOnDeleteListener.onProgressSubDirectory(
                                0,
                                0,
                                "");

                        while (mPauseDeleting && !Thread.interrupted())
                            Thread.sleep(DELETE_THREAD_SLEEP_TIME);
                        if (Thread.interrupted())
                            return;

                        if (iFTPFile.hasPermission(FTPFile.USER_ACCESS, FTPFile.WRITE_PERMISSION)) {
                            LogManager.info(TAG, "Will delete dir : " + iFTPFile.getName());
                            boolean lReply = mFTPClient.removeDirectory(iFTPFile.getName());

                            if (!lReply && !mByPassDeletingFailErrors)
                                iAOnDeleteListener.onFail(iFTPFile); // TODO : Watch folder error is not triggered !

                        } else if (!mByPassDeletingRightErrors)
                            iAOnDeleteListener.onRightAccessFail(iFTPFile);

                    } else if (!mByPassDeletingRightErrors)
                        iAOnDeleteListener.onRightAccessFail(iFTPFile);

                } else if (iFTPFile.isFile()) {
                    // FILE

                    iAOnDeleteListener.onProgressSubDirectory(
                            iProgress,
                            iTotal,
                            FTPFileUtils.getFileName(iFTPFile));

                    if (iFTPFile.hasPermission(FTPFile.USER_ACCESS, FTPFile.WRITE_PERMISSION)) {
                        LogManager.debug(TAG, "WILL DELETE FILE : " + iFTPFile.getName());
                        boolean lReply = mFTPClient.deleteFile(iFTPFile.getName());

                        if (!lReply)
                            iAOnDeleteListener.onFail(iFTPFile);
                    } else if (!mByPassDeletingRightErrors) {
                        mPauseDeleting = true;
                        iAOnDeleteListener.onRightAccessFail(iFTPFile);
                    }
                }
            }

            @Override
            public void run() {
                LogManager.info(TAG, "Thread delete files");
                try {
                    mFTPClient.enterLocalPassiveMode(); // PASSIVE MODE

                    iAOnDeleteListener.onStartDelete();

                    int lProgress = 0;
                    for (FTPFile lFTPFile : iSelection) {
                        LogManager.debug(TAG, "Selected file : " + lFTPFile.getName());
                        LogManager.debug(TAG, "Absolute path : " + mCurrentDirectory.getName() + "/" + lFTPFile.getName());
                        FTPFile lAbsoluteFile = mFTPClient.mlistFile(mCurrentDirectory.getName() + "/" + lFTPFile.getName());
                        LogManager.debug(TAG, "File name : " + lAbsoluteFile.getName());

                        if (lAbsoluteFile != null) {
                            LogManager.debug(TAG, "Absolute name : " + lAbsoluteFile.getName());

                            iAOnDeleteListener.onProgressDirectory(
                                    lProgress,
                                    iSelection.length,
                                    lFTPFile.getName());

                            recursiveDeletion(lAbsoluteFile, lProgress++, iSelection.length);
                        } else {
                            LogManager.error(TAG, "Error with : " + lFTPFile.toFormattedString());
                            iAOnDeleteListener.onFail(lFTPFile);
                            mPauseDeleting = true;
                        }

                        iAOnDeleteListener.onProgressDirectory(
                                lProgress,
                                iSelection.length,
                                lFTPFile.getName());
                    }

                    while (mPauseDeleting && !Thread.interrupted())
                        Thread.sleep(DELETE_THREAD_SLEEP_TIME);
                    if (Thread.interrupted())
                        return;

                    mFTPClient.enterLocalActiveMode(); // ACTIVE MODE
                } catch (IOException | InterruptedException iE) {
                    iE.printStackTrace();
                }

                if (Thread.interrupted())
                    return;

                iAOnDeleteListener.onFinish();
            }
        });
        mDeleteFileThread.start();
    }

    public boolean isFetchingFolders() {
        // Display :
//        LogManager.info(TAG, "isFetchingFolders : " + isConnected() + " && (" +
//                (mDirectoryFetchThread != null && mDirectoryFetchThread.isAlive()) + ") || " + mStartingFetchDirectory);
        return (isConnected() && (mDirectoryFetchThread != null && mDirectoryFetchThread.isAlive())) ||
                mStartingFetchDirectory;
    }

    public boolean isCreatingFolder() {
        return isConnected() && mCreateDirectoryThread != null && mCreateDirectoryThread.isAlive();
    }

    public boolean isDeletingFiles() {
        return isConnected() && mDeleteFileThread != null && mDeleteFileThread.isAlive();
    }

    public boolean isDeletingPaused() {
        return mPauseDeleting;
    }

    public boolean isBusy() {
        // Display :
//        LogManager.debug(TAG, " " + isConnecting() + " " + isReconnecting() + " " + isFetchingFolders() + " "
//                + isCreatingFolder() + " " + isDeletingFiles());
        return isConnecting() || isReconnecting() || isFetchingFolders() || isCreatingFolder() ||
                isDeletingFiles();
    }

    public void resumeDeleting() {
        mPauseDeleting = false;
    }

    public void pauseDeleting() {
        mPauseDeleting = true;
    }

    public void setDeletingByPassRightErrors(boolean iValue) {
        mByPassDeletingRightErrors = iValue;
    }


    public void setDeletingByPassFailErrors(boolean iValue) {
        mByPassDeletingFailErrors = iValue;
    }

    public interface IOnFetchDirectoryResult {
        void onSuccess(FTPFile[] iFTPFiles);

        void onFail(ErrorCodeDescription iErrorEnum, int iErrorCode);

        void onInterrupt();
    }

    public interface IOnCreateDirectoryResult {
        void onSuccess(FTPFile iNewDirectory);

        void onFail(ErrorCodeDescription iErrorEnum, int iErrorCode);
    }

    public abstract class AOnDeleteListener {
        public abstract void onStartDelete();

        public abstract void onProgressDirectory(int iDirectoryProgress, int iTotalDirectories, String iDirectoryName);

        public abstract void onProgressSubDirectory(int iSubDirectoryProgress, int iTotalSubDirectories, String iSubDirectoryName);

        public void onRightAccessFail(FTPFile iFTPFile) {
            mPauseDeleting = true;
        }

        public abstract void onFinish();

        public void onFail(FTPFile iFTPFile) {
            mPauseDeleting = true;
        }
    }

}

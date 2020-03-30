package com.vpulse.ftpnext.database.PendingFileTable;

import androidx.annotation.NonNull;

import com.vpulse.ftpnext.core.ExistingFileAction;
import com.vpulse.ftpnext.core.LoadDirection;
import com.vpulse.ftpnext.core.LogManager;
import com.vpulse.ftpnext.database.ABaseTable;
import com.vpulse.ftpnext.ftpservices.FTPLogManager;

public class PendingFile extends ABaseTable {

    private static final String TAG = "DATABASE : Pending file";

    private int mServerId;
    private LoadDirection mLoadDirection;
    private boolean mStarted;
    private String mName;
    private String mRemotePath;
    private String mLocalPath;
    private boolean mFinished;
    private int mProgress;
    private ExistingFileAction mExistingFileAction;

    // Not in database :
    private int mSize;
    private boolean mIsConnected;
    private boolean mIsAnError;
    private long mSpeedInByte;
    private int mRemainingTimeInMin;

    public PendingFile() {
    }

    public PendingFile(int iServerId, LoadDirection iLoadDirection, boolean iStarted,
                       String iName, String iRemotePath, String iLocalPath,
                       ExistingFileAction iExistingFileAction) {
        mServerId = iServerId;
        mLoadDirection = iLoadDirection;
        mStarted = iStarted;
        mName = iName;
        mRemotePath = iRemotePath;
        mLocalPath = iLocalPath;
        mExistingFileAction = iExistingFileAction;
    }

    public int getServerId() {
        return mServerId;
    }

    public PendingFile setServerId(int iServerId) {
        mServerId = iServerId;
        return this;
    }

    public LoadDirection getLoadDirection() {
        return mLoadDirection;
    }

    public PendingFile setLoadDirection(LoadDirection iLoadDirection) {
        mLoadDirection = iLoadDirection;
        return this;
    }

    public boolean isStarted() {
        return mStarted;
    }

    public PendingFile setStarted(boolean iStarted) {
        mStarted = iStarted;
        return this;
    }

    public String getName() {
        return mName;
    }

    public PendingFile setName(String iName) {
        mName = iName;
        return this;
    }

    public String getRemotePath() {
        return mRemotePath;
    }

    public PendingFile setRemotePath(String iRemotePath) {
        mRemotePath = iRemotePath;
        return this;
    }

    public String getLocalPath() {
        return mLocalPath;
    }

    public PendingFile setLocalPath(String iEnclosureName) {
        mLocalPath = iEnclosureName;
        return this;
    }

    public boolean isFinished() {
        return mFinished;
    }

    public PendingFile setFinished(boolean iFinished) {
        mFinished = iFinished;
        return this;
    }

    public int getProgress() {
        return mProgress;
    }

    public PendingFile setProgress(int iProgress) {
        mProgress = iProgress;
        return this;
    }

    public ExistingFileAction getExistingFileAction() {
        return mExistingFileAction;
    }

    public void setExistingFileAction(ExistingFileAction iExistingFileAction) {
        mExistingFileAction = iExistingFileAction;
    }

    @Override
    protected void setDataBaseId(int iDataBaseId) {
        mDataBaseId = iDataBaseId;
    }

    // Getter and Setter are not in DataBase from here

    public int getSize() {
        return mSize;
    }

    public void setSize(int iSize) {
        mSize = iSize;
    }

    public boolean isAnError() {
        return mIsAnError;
    }

    public void setIsAnError(boolean iAnError) {
        mIsAnError = iAnError;
    }

    public boolean isConnected() {
        return mIsConnected;
    }

    public void setConnected(boolean iConnected) {
        mIsConnected = iConnected;
    }

    public long getSpeedInByte() {
        return mSpeedInByte;
    }

    public void setSpeedInByte(long iSpeedInByte) {
        mSpeedInByte = iSpeedInByte;
    }

    public int getRemainingTimeInMin() {
        return mRemainingTimeInMin;
    }

    public void setRemainingTimeInMin(int iRemainingTimeInMin) {
        mRemainingTimeInMin = iRemainingTimeInMin;
    }

    @NonNull
    @Override
    public String toString() {
        String oToString;

        oToString = "Database id: " + mDataBaseId +
                "\nServerId: " + mServerId +
                "\nLoadDirection: " + mLoadDirection.toString() +
                "\nStarted: " + mStarted +
                "\nRemote path:\t\t" + mRemotePath +
                "\nLocal path:\t\t" + mLocalPath +
                "\nFinished: " + mFinished +
                "\nmProgress: " + mProgress;
        return oToString;
    }

    public void updateContent(PendingFile iPendingFile) {
        if (this == iPendingFile) {
            LogManager.info(TAG, "Useless updating content");
            return;
        }

        LogManager.debug(TAG, "Not useless updating content");
        mServerId = iPendingFile.mServerId;
        mLoadDirection = iPendingFile.mLoadDirection;
        mStarted = iPendingFile.mStarted;
        mName = iPendingFile.mName;
        mRemotePath = iPendingFile.mRemotePath;
        mLocalPath = iPendingFile.mLocalPath;
        mFinished = iPendingFile.mFinished;
        mProgress = iPendingFile.mProgress;
    }
}
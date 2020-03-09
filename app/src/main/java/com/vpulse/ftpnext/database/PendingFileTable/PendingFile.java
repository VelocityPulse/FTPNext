package com.vpulse.ftpnext.database.PendingFileTable;

import android.support.annotation.NonNull;

import com.vpulse.ftpnext.core.ExistingFileAction;
import com.vpulse.ftpnext.core.LoadDirection;
import com.vpulse.ftpnext.core.LogManager;
import com.vpulse.ftpnext.database.ABaseTable;

public class PendingFile extends ABaseTable {

    private static final String TAG = "DATABASE : Pending file";

    private int mServerId;
    private LoadDirection mLoadDirection;
    private boolean mStarted;
    private String mName;
    private String mPath;
    private String mEnclosingName;
    private boolean mFinished;
    private int mProgress;
//    private ExistingFileAction mExistingFileAction;

    // Not in database :
    private int mSize;
    private boolean mIsConnected;
    private boolean mIsAnError;
    private long mSpeedInKo;
    private int remainingTimeInMin;

    public PendingFile() {
    }

    public PendingFile(int iServerId, LoadDirection iLoadDirection, boolean iStarted,
                       String iName, String iPath, String iEnclosureName
                       /*ExistingFileAction iExistingFileAction*/) {
        mServerId = iServerId;
        mLoadDirection = iLoadDirection;
        mStarted = iStarted;
        mName = iName;
        mPath = iPath;
//        mExistingFileAction = iExistingFileAction;
        if (iEnclosureName == null)
            mEnclosingName = "";
        else {
            mEnclosingName = iEnclosureName;
            if (!mEnclosingName.endsWith("/"))
                mEnclosingName += "/";
        }
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

    public String getPath() {
        return mPath;
    }

    public PendingFile setPath(String iPath) {
        mPath = iPath;
        return this;
    }

    public String getEnclosingName() {
        return mEnclosingName;
    }

    public PendingFile setEnclosureName(String iEnclosureName) {
        mEnclosingName = iEnclosureName;
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

//    public ExistingFileAction getExistingFileAction() {
//        return mExistingFileAction;
//    }
//
//    public void setExistingFileAction(ExistingFileAction iExistingFileAction) {
//        mExistingFileAction = iExistingFileAction;
//    }

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

    public long getSpeedInKo() {
        return mSpeedInKo;
    }

    public void setSpeedInKo(long iSpeedInKo) {
        mSpeedInKo = iSpeedInKo;
    }

    public int getRemainingTimeInMin() {
        return remainingTimeInMin;
    }

    public void setRemainingTimeInMin(int iRemainingTimeInMin) {
        remainingTimeInMin = iRemainingTimeInMin;
    }

    @NonNull
    @Override
    public String toString() {
        String oToString;

        oToString = "Database id: " + mDataBaseId +
                "\nServerId: " + mServerId +
                "\nLoadDirection: " + mLoadDirection.toString() +
                "\nStarted: " + mStarted +
                "\nPath:\t\t\t" + mPath +
                "\nEnclosureName:\t" + mEnclosingName +
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
        mPath = iPendingFile.mPath;
        mEnclosingName = iPendingFile.mEnclosingName;
        mFinished = iPendingFile.mFinished;
        mProgress = iPendingFile.mProgress;
    }
}
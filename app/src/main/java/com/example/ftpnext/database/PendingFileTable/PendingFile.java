package com.example.ftpnext.database.PendingFileTable;

import android.support.annotation.NonNull;

import com.example.ftpnext.core.LoadDirection;
import com.example.ftpnext.core.LogManager;
import com.example.ftpnext.database.ABaseTable;

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

    // Not in database :
    private int mSize;
    private boolean mHasProblem;

    public PendingFile() {
    }

    public PendingFile(int iServerId, LoadDirection iLoadDirection, boolean iStarted,
                       String iName, String iPath, String iEnclosureName) {
        mServerId = iServerId;
        mLoadDirection = iLoadDirection;
        mStarted = iStarted;
        mName = iName;
        mPath = iPath;
        if (iEnclosureName == null)
            mEnclosingName = "";
        else
            mEnclosingName = iEnclosureName;
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

    public PendingFile  setStarted(boolean iStarted) {
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


    /**
     * This value is set only after FTPTransfer has begun its transfer
     * @return Size of the pending file
     */
    public int getSize() {
        return mSize;
    }

    /**
     * Important : Only FTPTransfer can touch this
     */
    public void setSize(int iSize) {
        mSize = iSize;
    }

    public boolean hasProblem() {
        return mHasProblem;
    }

    /**
     * Important : Only FTPTransfer can touch this
     */
    public void setHasProblem(boolean iHasProblem) {
        mHasProblem = iHasProblem;
    }

    @Override
    protected void setDataBaseId(int iDataBaseId) {
        mDataBaseId = iDataBaseId;
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

    @NonNull
    @Override
    public String toString() {
        String oToString;

        oToString = "ServerId: " + mServerId +
                ", LoadDirection: " + mLoadDirection.toString() +
                ", Started: " + mStarted +
                "\nPath:\t\t\t" + mPath +
                "\nEnclosureName:\t" + mEnclosingName +
                "\nFinished: " + mFinished +
                "\nmProgress: " + mProgress;
        return oToString;
    }
}
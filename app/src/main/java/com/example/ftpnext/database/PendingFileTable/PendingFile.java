package com.example.ftpnext.database.PendingFileTable;

import android.support.annotation.NonNull;

import com.example.ftpnext.core.LoadDirection;
import com.example.ftpnext.database.ABaseTable;

public class PendingFile extends ABaseTable {

    private static final String TAG = "DATABASE : Pending file";

    private int mServerId;
    private LoadDirection mLoadDirection;
    private boolean mStarted;
    private String mName;
    private String mPath;
    private String mEnclosingName;

    public PendingFile() {
    }

    public PendingFile(int iServerId, LoadDirection iLoadDirection, boolean iStarted,
                       String iName, String iPath, String iEnclosureName) {
        mServerId = iServerId;
        mLoadDirection = iLoadDirection;
        mStarted = iStarted;
        mName = iName;
        mPath = iPath;
        mEnclosingName = iEnclosureName;
    }

    public int getServerId() {
        return mServerId;
    }

    public void setServerId(int iServerId) {
        mServerId = iServerId;
    }

    public LoadDirection getLoadDirection() {
        return mLoadDirection;
    }

    public void setLoadDirection(LoadDirection iLoadDirection) {
        mLoadDirection = iLoadDirection;
    }

    public boolean isStarted() {
        return mStarted;
    }

    public void setStarted(boolean iStarted) {
        mStarted = iStarted;
    }

    public String getName() {
        return mName;
    }

    public void setName(String iName) {
        mName = iName;
    }

    public String getPath() {
        return mPath;
    }

    public void setPath(String iPath) {
        mPath = iPath;
    }

    public String getEnclosingName() {
        return mEnclosingName;
    }

    public void setEnclosureName(String iEnclosureName) {
        mEnclosingName = iEnclosureName;
    }

    @Override
    protected void setDataBaseId(int iDataBaseId) {
        mDataBaseId = iDataBaseId;
    }

    @NonNull
    @Override
    public String toString() {
        String oToString;

        oToString = "ServerId: " + mServerId +
                ", LoadDirection: " + mLoadDirection.toString() +
                ", Started: " + mStarted +
                "\nPath:\t\t\t" + mPath +
                "\nEnclosureName:\t" + mEnclosingName;
        return oToString;
    }
}
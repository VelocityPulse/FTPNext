package com.example.ftpnext.database.PendingFileTable;

import android.support.annotation.NonNull;
import android.view.View;

import com.example.ftpnext.core.LoadDirection;
import com.example.ftpnext.database.ABaseTable;

public class PendingFile extends ABaseTable {

    private static final String TAG = "DATABASE : Pending file";

    private int mServerId;
    private LoadDirection mLoadDirection;
    private boolean mStarted;
    private String mPath;
    private String mEnclosureName;

    public PendingFile() {
    }

    public PendingFile(int iServerId, LoadDirection iLoadDirection, boolean iStarted, String iPath, String iEnclosureName) {
        mServerId = iServerId;
        mLoadDirection = iLoadDirection;
        mStarted = iStarted;
        mPath = iPath;
        mEnclosureName = iEnclosureName;
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

    public String getPath() {
        return mPath;
    }

    public void setPath(String iPath) {
        mPath = iPath;
    }

    public String getEnclosureName() {
        return mEnclosureName;
    }

    public void setEnclosureName(String iEnclosureName) {
        mEnclosureName = iEnclosureName;
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
                "\nEnclosureName:\t" + mEnclosureName;
        return oToString;
    }
}
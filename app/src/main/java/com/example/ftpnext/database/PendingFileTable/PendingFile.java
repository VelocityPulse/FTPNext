package com.example.ftpnext.database.PendingFileTable;

import com.example.ftpnext.core.LoadDirection;
import com.example.ftpnext.database.ABaseTable;

public class PendingFile extends ABaseTable {

    private int mServerId;
    private LoadDirection mLoadDirection = LoadDirection.DEFAULT;
    private boolean mStarted;
    private String mPath;
    private boolean mIsFolder;

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

    public boolean IsFolder() {
        return mIsFolder;
    }

    public void setFolder(boolean iIsFolder) {
        mIsFolder = iIsFolder;
    }

    @Override
    protected void setDataBaseId(int iDataBaseId) {
        mDataBaseId = iDataBaseId;
    }


}
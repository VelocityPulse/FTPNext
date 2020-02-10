package com.example.ftpnext.database.PendingFileTable;

import com.example.ftpnext.core.LoadDirection;
import com.example.ftpnext.core.LogManager;
import com.example.ftpnext.database.ABaseTable;

import org.apache.commons.net.ftp.FTPFile;

import java.util.ArrayList;
import java.util.List;

public class PendingFile extends ABaseTable {

    private static final String TAG = "DATABASE : Pending file";

    private int mServerId;
    private LoadDirection mLoadDirection;
    private boolean mStarted;
    private String mPath;
    private boolean mIsFolder;

    public PendingFile() {
    }

    public PendingFile(int iServerId, LoadDirection iLoadDirection, boolean iStarted, String iPath, boolean iIsFolder) {
        mServerId = iServerId;
        mLoadDirection = iLoadDirection;
        mStarted = iStarted;
        mPath = iPath;
        mIsFolder = iIsFolder;
    }

    public static PendingFile[] createPendingFiles(String iAbsolutePath, int iServerId, FTPFile[] iSelectedFiles, LoadDirection iLoadDirection) {
        LogManager.info(TAG, "Create pending files");
        List<PendingFile> oPendingFiles = new ArrayList<>();

        for (FTPFile lItem : iSelectedFiles) {
            oPendingFiles.add(new PendingFile(
                    iServerId,
                    iLoadDirection,
                    false,
                    iAbsolutePath + "/" + lItem.getName(),
                    lItem.isDirectory()
            ));
        }
        return (PendingFile[]) oPendingFiles.toArray();
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
package com.example.ftpnext.database.FTPServerTable;

import com.example.ftpnext.core.FTPCharacterEncoding;
import com.example.ftpnext.core.FTPType;
import com.example.ftpnext.database.ABaseTable;

public class FTPServer extends ABaseTable {

    private String mName;
    private String mServer;
    private String mUser;
    private String mPass;
    private int mPort;
    private String mLocalFolder;
    private FTPCharacterEncoding mFTPCharacterEncoding = FTPCharacterEncoding.DEFAULT;
    private FTPType mFTPType = FTPType.DEFAULT;

    public String getName() {
        return mName;
    }

    public void setName(String iName) {
        mName = iName;
    }

    public String getServer() {
        return mServer;
    }

    public void setServer(String iServer) {
        mServer = iServer;
    }

    public String getUser() {
        return mUser;
    }

    public void setUser(String iUser) {
        mUser = iUser;
    }

    public String getPass() {
        return mPass;
    }

    public void setPass(String iPass) {
        mPass = iPass;
    }

    public int getPort() {
        return mPort;
    }

    public void setPort(int iPort) {
        mPort = iPort;
    }

    public String getLocalFolder() {
        return mLocalFolder;
    }

    public void setLocalFolder(String iLocalFolder) {
        mLocalFolder = iLocalFolder;
    }

    public FTPCharacterEncoding getFTPCharacterEncoding() {
        return mFTPCharacterEncoding;
    }

    public void setFTPCharacterEncoding(FTPCharacterEncoding iFTPCharacterEncoding) {
        mFTPCharacterEncoding = iFTPCharacterEncoding;
    }

    public FTPType getFTPType() {
        return mFTPType;
    }

    public void setFTPType(FTPType iFTPType) {
        mFTPType = iFTPType;
    }

    @Override
    protected void setDataBaseId(int iDataBaseId) {
        mDataBaseId = iDataBaseId;
    }
}

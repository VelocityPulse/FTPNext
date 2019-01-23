package com.example.ftpnext.database.FTPHostTable;

import com.example.ftpnext.core.FTPCharacterEncoding;
import com.example.ftpnext.core.FTPType;
import com.example.ftpnext.database.ABaseTable;

public class FTPHost extends ABaseTable {

    private String mName;
    private String mUser;
    private String mPass;
    private String mHost;
    private int mPort;
    private String mAttributedFolder;
    private FTPCharacterEncoding mFTPCharacterEncoding;
    private FTPType mFTPType;

    public String getName() {
        return mName;
    }

    public void setName(String iName) {
        mName = iName;
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

    public String getHost() {
        return mHost;
    }

    public void setHost(String iHost) {
        mHost = iHost;
    }

    public int getPort() {
        return mPort;
    }

    public void setPort(int iPort) {
        mPort = iPort;
    }

    public String getAttributedFolder() {
        return mAttributedFolder;
    }

    public void setAttributedFolder(String iAttributedFolder) {
        mAttributedFolder = iAttributedFolder;
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

package com.vpulse.ftpnext.database.FTPServerTable;

import com.vpulse.ftpnext.commons.Utils;
import com.vpulse.ftpnext.core.FTPCharacterEncoding;
import com.vpulse.ftpnext.core.FTPType;
import com.vpulse.ftpnext.core.LogManager;
import com.vpulse.ftpnext.database.ABaseTable;

import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

// TODO : When FTPServer is deleted, delete all pending file referenced
public class FTPServer extends ABaseTable {

    private static final String TAG = "DATABASE : FTP Server";

    private String mName;
    private String mServer;
    private String mUser;
    private String mPass;
    private int mPort;
    private String mFolderName;
    private String mAbsolutePath;
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

    public String getFolderName() {
        return mFolderName;
    }

    public void setFolderName(String iFolderName) {
        mFolderName = iFolderName;
    }

    public String getAbsolutePath() {
        return mAbsolutePath;
    }

    public void setAbsolutePath(String iAbsolutePath) {
        mAbsolutePath = iAbsolutePath;
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

    public boolean isEmpty() {
        return Utils.isNullOrEmpty(mName) &&
                Utils.isNullOrEmpty(mServer) &&
                Utils.isNullOrEmpty(mUser) &&
                Utils.isNullOrEmpty(mPass) &&
                Utils.isNullOrEmpty(mFolderName) &&
                Utils.isNullOrEmpty(mAbsolutePath);
    }

    public void updateContent(FTPServer iFTPServer) {
        if (this == iFTPServer) {
            LogManager.info(TAG, "Useless updating content");
            return;
        }

        mName = iFTPServer.mName;
        mServer = iFTPServer.mServer;
        mUser = iFTPServer.mUser;
        mPass = iFTPServer.mPass;
        mPort = iFTPServer.mPort;
        mFTPType = iFTPServer.mFTPType;
        mFolderName = iFTPServer.mFolderName;
        mAbsolutePath = iFTPServer.mAbsolutePath;
    }

    @Override
    protected void setDataBaseId(int iDataBaseId) {
        mDataBaseId = iDataBaseId;
    }

    @Override
    public boolean equals(Object iObj) {
        if (iObj == null)
            return false;
        if (iObj == this)
            return true;
        if (!(iObj instanceof FTPServer))
            return false;

        FTPServer lFTPServer = (FTPServer) iObj;
        if (lFTPServer == null || (!mName.equals(lFTPServer.mName) ||
                !mServer.equals(lFTPServer.mServer) ||
                !mUser.equals(lFTPServer.mUser) ||
                !mPass.equals(lFTPServer.mPass) ||
                mPort != lFTPServer.mPort ||
                mFTPType != lFTPServer.mFTPType ||
                !mFolderName.equals(lFTPServer.mFolderName) ||
                !mAbsolutePath.equals(lFTPServer.mAbsolutePath))) {
            return false;
        }
        return true;
    }

    @NotNull
    @Override
    public String toString() {
        String oToString;

        oToString = mName + "\n";
        oToString += mServer + "\n";
        oToString += mUser + "\n";

        MessageDigest lMD = null;
        try {
            lMD = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            oToString += ">> No MD5 instance";
        }
        lMD.update(mPass.getBytes(), 0, mPass.length());
        oToString += "MD5 pass : " + new BigInteger(1, lMD.digest()).toString(16);

        oToString += mPort + "\n";
        oToString += mFTPType.name() + "\n";
        oToString += mFolderName + "\n";
        oToString += mAbsolutePath + "\n";
        return oToString;
    }
}

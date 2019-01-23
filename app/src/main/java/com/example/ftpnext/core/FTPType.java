package com.example.ftpnext.core;

public enum FTPType {

    FTP(0),

    FTPS(1),

    FTPES(2),

    DEFAULT(0);

    private final int mValue;

    FTPType(int iValue) {
        this.mValue = iValue;
    }

    public int getValue() {
        return mValue;
    }

    public static FTPType getValue(int iValue) {
        if (iValue == 0)
            return FTPType.FTP;
        else if (iValue == 1)
            return FTPType.FTPES;
        else if (iValue == 2)
            return FTPType.FTPES;
        return FTPType.DEFAULT;
    }
}
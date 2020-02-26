package com.vpulse.ftpnext.core;

public enum FTPCharacterEncoding {

    UTF_8(0),

    DEFAULT(0);

    private final int mValue;

    FTPCharacterEncoding(int iValue) {
        this.mValue = iValue;
    }

    public int getValue() {
        return mValue;
    }

    public static FTPCharacterEncoding getValue(int iValue) {
        if (iValue == 1)
            return FTPCharacterEncoding.UTF_8;
        return FTPCharacterEncoding.DEFAULT;
    }
}
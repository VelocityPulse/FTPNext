package com.example.ftpnext.core;

public enum LoadDirection {

    DOWNLOAD(0),

    UPLOAD(1),

    DEFAULT(0);

    private final int mValue;

    LoadDirection(int iValue) {
        this.mValue = iValue;
    }

    public int getValue() {
        return mValue;
    }

    public static LoadDirection getValue(int iValue) {
        if (iValue == 0)
            return LoadDirection.DOWNLOAD;
        else if (iValue == 1)
            return LoadDirection.UPLOAD;
        return LoadDirection.DEFAULT;
    }

}

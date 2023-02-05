package com.vpulse.ftpnext.core

enum class FTPType(val value: Int) {
    FTP(0),
    SFTP(1),  //    FTPES(2),
    DEFAULT(0);

    companion object {
        fun getValue(iValue: Int): FTPType {
            if (iValue == 0) return FTP else if (iValue == 1) return SFTP //        else if (iValue == 2)
            //            return FTPType.FTPES;
            return DEFAULT
        }
    }
}
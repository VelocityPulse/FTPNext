package com.vpulse.ftpnext.core

enum class LoadDirection(val value: Int) {
    DOWNLOAD(0),
    UPLOAD(1),
    DEFAULT(0);

    companion object {
        fun getValue(iValue: Int): LoadDirection {
            if (iValue == 0) return DOWNLOAD else if (iValue == 1) return UPLOAD
            return DEFAULT
        }
    }
}
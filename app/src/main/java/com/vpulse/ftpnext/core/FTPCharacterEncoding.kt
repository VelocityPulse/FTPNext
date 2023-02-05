package com.vpulse.ftpnext.core

enum class FTPCharacterEncoding(val value: Int) {
    UTF_8(0), DEFAULT(0);

    companion object {
        fun getValue(iValue: Int): FTPCharacterEncoding {
            return if (iValue == 1) UTF_8 else DEFAULT
        }
    }
}
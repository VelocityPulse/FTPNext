package com.vpulse.ftpnext.commons

import org.apache.commons.net.ftp.FTPFile
import java.io.File

object FTPFileUtils {
    fun getFileName(iFTPFile: FTPFile?): String {
        return if (iFTPFile != null && iFTPFile.name.contains(File.separator)) iFTPFile.name.substring(
            iFTPFile.name.lastIndexOf(File.separator)
        ) else iFTPFile!!.name
    }
}
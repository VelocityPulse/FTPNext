package com.vpulse.ftpnext.commons;

import org.apache.commons.net.ftp.FTPFile;

import java.io.File;

public final class FTPFileUtils {

    public static String getFileName(FTPFile iFTPFile) {
        return iFTPFile.getName().substring(iFTPFile.getName().lastIndexOf(File.separator));
    }
}

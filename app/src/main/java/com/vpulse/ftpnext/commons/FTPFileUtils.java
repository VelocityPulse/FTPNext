package com.vpulse.ftpnext.commons;

import org.apache.commons.net.ftp.FTPFile;

import java.io.File;

public class FTPFileUtils {

    public static String getFileName(FTPFile iFTPFile) {
        if (iFTPFile != null && iFTPFile.getName().contains(File.separator))
            return iFTPFile.getName().substring(iFTPFile.getName().lastIndexOf(File.separator));
        return iFTPFile.getName();
    }
}

package com.vpulse.ftpnext.core;

public class AppConstants {

    public static final String DATABASE_NAME = "ftpnext.db";
    public static final int DATABASE_VERSION = 4;

    public static final int MINIMUM_SIMULTANEOUS_DOWNLOAD = 1;
    public static final int MAXIMAL_SIMULTANEOUS_DOWNLOAD = 10;

    public static final boolean DEBUG_ENABLED = true;

    public static final int FTP_DEFAULT_PORT = 21;
    public static final int SFTP_DEFAULT_PORT = 22;

    public static final String TRANSFER_PROGRESS_GROUP_KEY = "com.vpulse.ftpnext.TRANSFER_PROGRESS";

    public static final String SERVICE_STATUS_NOTIFICATION_CHANNEL = "SERVICE_STATUS_CHANNEL";
    public static final String SERVICE_PROGRESS_NOTIFICATION_CHANNEL = "SERVICE_PROGRESS_CHANNEL";

    public static final int SERVICE_STATUS_NOTIFICATION_ID = 1;
    public static final int SUMMARY_NOTIFICATION_ID = 2;
}

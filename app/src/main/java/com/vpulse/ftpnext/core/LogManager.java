package com.vpulse.ftpnext.core;

import android.util.Log;

public class LogManager {
    public static final String UNKNOWN_TAG = "UNKNOWN_TAG";
    private static final boolean ENABLED_LOG = false;
    private static final String TAG = "CORE - Log Manager";
    private static final String TAG_PREFIX = "FTPNEXT LOG : ";

    private static boolean sSilentDebugLog = false;

    public static void setSilentDebugLog(boolean iValue) {
        sSilentDebugLog = iValue;
    }

    public static boolean error(String iMessage) {
        if (ENABLED_LOG)
            error(UNKNOWN_TAG, iMessage);
        return false;
    }

    public static boolean error(String iTAG, String iMessage) {
        if (ENABLED_LOG)
            Log.e(TAG_PREFIX, iTAG + " : " + iMessage);
        return false;
    }

    public static void info(String iMessage) {
        if (ENABLED_LOG)
            info(UNKNOWN_TAG, iMessage);
    }

    public static void info(String iTAG, String iMessage) {
        if (ENABLED_LOG)
            Log.i(TAG_PREFIX, iTAG + " : " + iMessage);
    }

    public static void debug(String iMessage) {
        if (ENABLED_LOG && !sSilentDebugLog)
            debug(UNKNOWN_TAG, iMessage);
    }

    public static void debug(String iTag, String iMessage) {
        if (ENABLED_LOG && !sSilentDebugLog)
            Log.d(TAG_PREFIX, iTag + " : " + iMessage);
    }

    public static void verbose(String iMessage) {
        if (ENABLED_LOG)
            verbose(UNKNOWN_TAG, iMessage);
    }

    public static void verbose(String iTag, String iMessage) {
        if (ENABLED_LOG)
            Log.v(TAG_PREFIX, iTag + " : " + iMessage);
    }
}

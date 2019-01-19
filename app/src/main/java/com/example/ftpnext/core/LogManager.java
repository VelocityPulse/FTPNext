package com.example.ftpnext.core;

import android.util.Log;

public class LogManager {

    public static final String UNKNOWN_TAG = "UNKNOWN_TAG";

    private static final String TAG = "CORE - Log Manager";
    private static final String TAG_PREFIX = "FTPNEXT LOG : ";
    private static final boolean ENABLED_LOG = true;

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

}

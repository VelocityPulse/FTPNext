package com.vpulse.ftpnext.core;

import android.content.Context;
import android.content.SharedPreferences;

import static com.vpulse.ftpnext.core.AppCore.APP_ADDRESS;

public class PreferenceManager {

    public static final int DEFAULT_MAX_TRANSFER_VALUE = 5;
    public static final String DEFAULT_LOGS_VALUE = "";

    private static final String TAG = "PREFERENCE MANAGER";

    private static final String PREFERENCE_FIRST_RUN = "PREFS_FIRST_RUN";
    private static final String PREFERENCE_MAX_TRANSFER = "PREFS_MAX_TRANSFER";
    private static final String PREFERENCE_LOGS = "PREFS_LOGS";

    private static PreferenceManager sSingleton;

    private static boolean mIsTheFirstRun;

    private static SharedPreferences mSharedPreferences;

    public static PreferenceManager getInstance() {
        if (sSingleton != null)
            return sSingleton;
        return (sSingleton = new PreferenceManager());
    }

    public static int getMaxTransfer() {
        if (mSharedPreferences == null) {
            LogManager.error(TAG, "Shared preferences null");
            return 0;
        }

        return mSharedPreferences.getInt(PREFERENCE_MAX_TRANSFER, DEFAULT_MAX_TRANSFER_VALUE);
    }

    public static void setMaxTransfer(int iMax) {
        if (iMax <= 0)
            iMax = 1;
        mSharedPreferences.edit().putInt(PREFERENCE_MAX_TRANSFER, iMax).apply();
    }

    public static boolean isTheFirstRun() {
        return mIsTheFirstRun;
    }

    public static String getFTPLogs() {
        LogManager.info(TAG, "Get FTP logs");
        if (mSharedPreferences == null) {
            LogManager.error(TAG, "Shared preferences null");
            return null;
        }

        return mSharedPreferences.getString(PREFERENCE_LOGS, DEFAULT_LOGS_VALUE);
    }

    public static void addFTPLog(String iLogToAdd) {
        LogManager.info(TAG, "Add FTP log logs");
        if (mSharedPreferences == null) {
            LogManager.error(TAG, "Shared preferences null");
            return;
        }

        if (iLogToAdd == null) {
            LogManager.error(TAG, "Log == null");
            return;
        }

        iLogToAdd = iLogToAdd.replaceAll("\n", " ");
        iLogToAdd = iLogToAdd.trim();
        if (iLogToAdd.length() == 0) {
            LogManager.error(TAG, "Nothing to log...");
            return;
        }
        String lLogs = mSharedPreferences.getString(PREFERENCE_LOGS, DEFAULT_LOGS_VALUE);
        lLogs += "\n" + iLogToAdd;
        mSharedPreferences.edit().putString(PREFERENCE_LOGS, lLogs).apply();

    }

    public void startPreferencesManager(Context iContext) {
        mSharedPreferences = iContext.getSharedPreferences(APP_ADDRESS, Context.MODE_PRIVATE);
        if (mSharedPreferences.getBoolean(PREFERENCE_FIRST_RUN, true)) {
            mIsTheFirstRun = true;
            mSharedPreferences.edit().putBoolean(PREFERENCE_FIRST_RUN, false).apply();
        }
    }
}

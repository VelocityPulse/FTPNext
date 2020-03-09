package com.vpulse.ftpnext.core;

import android.content.Context;
import android.content.SharedPreferences;

import static com.vpulse.ftpnext.core.AppCore.APP_ADDRESS;
import static com.vpulse.ftpnext.core.ExistingFileAction.NOT_DEFINED;

public class PreferenceManager {

    public static final boolean SILENT_LOG_CAT = true;

    // Default values :
    public static final int DEFAULT_MAX_TRANSFER_VALUE = 2;
    public static final ExistingFileAction DEFAULT_EXISTING_FILE_ACTION = NOT_DEFINED;
    public static final String DEFAULT_LOGS_VALUE = "";

    private static final String TAG = "PREFERENCE MANAGER";

    // Preferences tags :
    private static final String PREFERENCE_FIRST_RUN = "PREFS_FIRST_RUN";
    private static final String PREFERENCE_MAX_TRANSFER = "PREFS_MAX_TRANSFER";
    private static final String PREFERENCES_EXISTING_FILE_ACTION = "PREFS_EXISTING_FILE_ACTION";
    private static final String PREFERENCE_LOGS = "PREFS_LOGS";

    private static PreferenceManager sSingleton;
    private static boolean mIsTheFirstRun;

    private static SharedPreferences mSharedPreferences;

    public static PreferenceManager getInstance() {
        if (sSingleton != null)
            return sSingleton;
        return (sSingleton = new PreferenceManager());
    }

    public static ExistingFileAction getExistingFileAction() {
        if (mSharedPreferences == null) {
            LogManager.error(TAG, "Shared preferences null");
            return DEFAULT_EXISTING_FILE_ACTION;
        }
        return ExistingFileAction.getValue(
                mSharedPreferences.getInt(
                        PREFERENCES_EXISTING_FILE_ACTION, DEFAULT_EXISTING_FILE_ACTION.getValue()));
    }

    public static void setExistingFileAction(ExistingFileAction iAction) {
        if (mSharedPreferences == null) {
            LogManager.error(TAG, "Shared preferences null");
            return;
        }

        mSharedPreferences.edit().putInt(PREFERENCES_EXISTING_FILE_ACTION, iAction.getValue()).apply();
    }

    public static int getMaxTransfer() {
        if (mSharedPreferences == null) {
            LogManager.error(TAG, "Shared preferences null");
            return 0;
        }

        return mSharedPreferences.getInt(PREFERENCE_MAX_TRANSFER, DEFAULT_MAX_TRANSFER_VALUE);
    }

    public static void setMaxTransfer(int iMax) {
        if (mSharedPreferences == null) {
            LogManager.error(TAG, "Shared preferences null");
            return;
        }

        if (iMax <= 0)
            iMax = 1;
        mSharedPreferences.edit().putInt(PREFERENCE_MAX_TRANSFER, iMax).apply();
    }

    public static boolean isTheFirstRun() {
        return mIsTheFirstRun;
    }

    public static String getFTPLogs() {
        if (!SILENT_LOG_CAT)
            LogManager.info(TAG, "Get FTP logs");
        if (mSharedPreferences == null) {
            LogManager.error(TAG, "Shared preferences null");
            return null;
        }

        return mSharedPreferences.getString(PREFERENCE_LOGS, DEFAULT_LOGS_VALUE);
    }

    public static void addFTPLog(String iLogToAdd) {
        if (!SILENT_LOG_CAT)
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
        if (mSharedPreferences != null) {
            LogManager.error(TAG, "Preferences manager is already started");
            return;
        }

        mSharedPreferences = iContext.getSharedPreferences(APP_ADDRESS, Context.MODE_PRIVATE);
        if (mSharedPreferences.getBoolean(PREFERENCE_FIRST_RUN, true)) {
            mIsTheFirstRun = true;
            mSharedPreferences.edit().putBoolean(PREFERENCE_FIRST_RUN, false).apply();
        }
    }
}

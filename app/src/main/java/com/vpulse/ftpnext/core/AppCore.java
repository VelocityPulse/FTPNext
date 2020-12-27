package com.vpulse.ftpnext.core;

import android.annotation.SuppressLint;
import android.content.Context;

import com.vpulse.ftpnext.R;
import com.vpulse.ftpnext.database.DataBase;
import com.vpulse.ftpnext.database.PendingFileTable.PendingFile;
import com.vpulse.ftpnext.ftpservices.FTPLogManager;

import java.util.HashSet;
import java.util.Set;

public class AppCore {

    public static final String APP_ADDRESS = "com.vpulse.ftpnext";

    public static final float FLOATING_ACTION_BUTTON_INTERPOLATOR = 2.3F;

    private static final Set<PendingFile> mPendingFilesHistory = new HashSet<>();

    private static NetworkManager mNetworkManager = null;

    private static boolean mApplicationStarted;

    @SuppressLint("StaticFieldLeak")
    private static AppCore sSingleton;

    private AppCore() {
    }

    public static AppCore getInstance() {
        if (sSingleton != null)
            return sSingleton;
        return (sSingleton = new AppCore());
    }

    public static NetworkManager getNetworkManager() {
        return mNetworkManager;
    }

    public static boolean isApplicationStarted() {
        return mApplicationStarted;
    }

    public static int getAppTheme() {
        if (PreferenceManager.isDarkTheme())
            return R.style.AppTheme_Dark;
        return R.style.AppTheme_Light;
    }

    public static int getDialogTheme() {
        if (PreferenceManager.isDarkTheme())
            return R.style.AppTheme_Dark_Dialog;
        return R.style.AppTheme_Light_Dialog;
    }

    public void startApplication(Context iMainActivityContext) {
        // Data base
        DataBase lDataBase = DataBase.getInstance();
        lDataBase.open(iMainActivityContext);

        // Preference manager
        PreferenceManager lPreferenceManager = PreferenceManager.getInstance();
        lPreferenceManager.startPreferencesManager(iMainActivityContext);

        // Network manager
        mNetworkManager = NetworkManager.getInstance();
        mNetworkManager.startMonitoring(iMainActivityContext);

        // FTP log manager
        FTPLogManager.init(iMainActivityContext);

        mApplicationStarted = true;
    }

    public static Set<PendingFile> getPendingFilesHistory() {
        return mPendingFilesHistory;
    }

    public static void cleanPendingFileHistory() {
        PendingFile[] lPendingFiles = mPendingFilesHistory.toArray(new PendingFile[0]);

        for (PendingFile lPendingFile : lPendingFiles) {
            if (lPendingFile.isFinished())
                mPendingFilesHistory.remove(lPendingFile);
        }
    }
}
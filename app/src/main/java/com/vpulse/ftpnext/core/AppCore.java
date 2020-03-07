package com.vpulse.ftpnext.core;

import android.annotation.SuppressLint;
import android.content.Context;

import com.vpulse.ftpnext.database.DataBase;
import com.vpulse.ftpnext.ftpservices.FTPLogManager;

public class AppCore {

    public static final String APP_ADDRESS = "com.vpulse.ftpnext";

    public static final float FLOATING_ACTION_BUTTON_INTERPOLATOR = 2.3F;

    private static NetworkManager mNetworkManager = null;

    private static boolean mApplicationStarted;

    @SuppressLint("StaticFieldLeak")
    private static AppCore sSingleton;

    private Context mMainActivityContext;

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

    public void startApplication(Context iMainActivityContext) {
        if (mApplicationStarted)
            return;

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
        FTPLogManager.init();

        mApplicationStarted = true;
    }
}
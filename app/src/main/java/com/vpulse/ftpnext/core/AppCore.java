package com.vpulse.ftpnext.core;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

import com.vpulse.ftpnext.database.DataBase;

public class AppCore {

    public static final String APP_ADDRESS = "com.vpulse.ftpnext";

    public static final float FLOATING_ACTION_BUTTON_INTERPOLATOR = 2.3F;
    public static final int DEFAULT_MAX_TRANSFER_VALUE = 1;

    private static final String PREFERENCE_FIRST_RUN = "PREFS_FIRST_RUN";
    private static final String PREFERENCE_MAX_TRANSFER = "PREFS_MAX_TRANSFER";

    private static NetworkManager mNetworkManager = null;

    private static boolean mApplicationStarted;
    private static boolean mIsTheFirstRun;
    private static SharedPreferences mSharedPreferences;

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

    public static boolean isTheFirstRun() {
        return mIsTheFirstRun;
    }

    public static int getMaxTransfer() {
        return mSharedPreferences.getInt(PREFERENCE_MAX_TRANSFER, DEFAULT_MAX_TRANSFER_VALUE);
    }

    public static void setMaxTransfer(int iMax) {
        if (iMax <= 0)
            iMax = 1;
        mSharedPreferences.edit().putInt(PREFERENCE_MAX_TRANSFER, iMax).apply();
    }

    public void startApplication(Context iMainActivityContext) {
        if (mApplicationStarted)
            return;

        DataBase lDataBase = DataBase.getInstance();
        lDataBase.open(iMainActivityContext);

        mNetworkManager = NetworkManager.getInstance();
        mNetworkManager.startMonitoring(iMainActivityContext);

        mSharedPreferences = iMainActivityContext.getSharedPreferences(APP_ADDRESS, Context.MODE_PRIVATE);
        if (mSharedPreferences.getBoolean(PREFERENCE_FIRST_RUN, true)) {
            mIsTheFirstRun = true;
            mSharedPreferences.edit().putBoolean(PREFERENCE_FIRST_RUN, false).apply();
        }

        mApplicationStarted = true;
    }
}
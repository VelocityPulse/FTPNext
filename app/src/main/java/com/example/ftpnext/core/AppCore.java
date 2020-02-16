package com.example.ftpnext.core;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

import com.example.ftpnext.database.DataBase;

public class AppCore {

    public static final String APP_ADDRESS = "com.exemple.ftpnext";

    public static final float FLOATING_ACTION_BUTTON_INTERPOLATOR = 2.3F;

    private static final String PREFERENCE_FIRST_RUN = "FIRST_RUN";

    private static DataBase mDataBase = null;
    private static NetworkManager mNetworkManager = null;

    private static boolean mApplicationStarted;
    private static boolean mIsTheFirstRun;
    private static SharedPreferences mSharedPreferences;

    @SuppressLint("StaticFieldLeak")
    private static AppCore sSingleton;

    private Context mMainActivityContext;

    private AppCore() {}

    public static AppCore getInstance() {
        if (sSingleton != null)
            return sSingleton;
        return (sSingleton = new AppCore());
    }

    public void startApplication(Context iMainActivityContext) {
        if (mApplicationStarted)
            return;

        mDataBase = DataBase.getInstance();
        mDataBase.open(iMainActivityContext);

        mNetworkManager = NetworkManager.getInstance();
        mNetworkManager.startMonitoring(iMainActivityContext);

        mSharedPreferences = iMainActivityContext.getSharedPreferences(APP_ADDRESS, Context.MODE_PRIVATE);
        if (mSharedPreferences.getBoolean(PREFERENCE_FIRST_RUN, true)) {
            mIsTheFirstRun = true;
            mSharedPreferences.edit().putBoolean(PREFERENCE_FIRST_RUN, false).apply();
        }

        mApplicationStarted = true;
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


}

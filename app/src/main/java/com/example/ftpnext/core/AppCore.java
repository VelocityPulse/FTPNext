package com.example.ftpnext.core;

import android.content.Context;

import com.example.ftpnext.database.DataBase;

public class AppCore {

    private static DataBase mDataBase = null;
    private static NetworkManager mNetworkManager = null;

    private boolean mApplicationStarted;
    private Context mContext;

    public AppCore(Context iContext) {
        mContext = iContext;
    }

    public void startApplication() {

        mDataBase = DataBase.getInstance();
        mDataBase.open(mContext);

        mNetworkManager = NetworkManager.getInstance();
        mNetworkManager.startMonitoring(mContext);
    }

    public static DataBase getDataBase() {
        return mDataBase;
    }

    public static NetworkManager getNetworkManager() {
        return mNetworkManager;
    }
}

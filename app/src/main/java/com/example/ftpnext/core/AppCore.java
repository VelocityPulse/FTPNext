package com.example.ftpnext.core;

import android.content.Context;

import com.example.ftpnext.database.DataBase;

public class AppCore {

    private Context mContext;
    private DataBase mDataBase;

    public AppCore(Context iContext) {
        mContext = iContext;
    }

    public void startApplication() {
        mDataBase = DataBase.getInstance();
        mDataBase.open(mContext);
    }

}
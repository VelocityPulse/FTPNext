package com.example.ftpnext.database;

import android.content.Context;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

import com.example.ftpnext.core.AppInfo;
import com.example.ftpnext.core.LogManager;
import com.example.ftpnext.database.TableTest1.TableTest1DAO;

public class DataBase {

    public static final String DATABASE_NAME = AppInfo.DATABASE_NAME;
    public static final int DATABASE_VERSION = AppInfo.DATABASE_VERSION;

    private static String TAG = "DATABASE : Database";

    private static TableTest1DAO mTableTest1Dao = null;
    private static DataBase mSingleton = null;
    private DataBaseOpenHelper mDataBaseOpenHelper;
    private boolean mDataBaseIsOpen = false;

    private DataBase() {
    }

    public static DataBase getInstance() {
        if (mSingleton != null)
            return mSingleton;
        return (mSingleton = new DataBase());
    }

    public static TableTest1DAO getTableTest1Dao() {
        return mTableTest1Dao;
    }

    public static void initDataDirectory() {

    }

    /**
     * @param iContext Context of the app
     * @return True if DataBase is opened.
     * @throws SQLException Exception during opening database.
     */
    public boolean open(Context iContext) throws SQLException {
        if (mDataBaseIsOpen)
            return true;

        LogManager.info(TAG, "Open database");
        mDataBaseOpenHelper = new DataBaseOpenHelper(iContext);
        SQLiteDatabase lDataBase = mDataBaseOpenHelper.getWritableDatabase();

        //DAO list
        mTableTest1Dao = new TableTest1DAO(lDataBase);

        return (mDataBaseIsOpen = true);
    }

    public boolean isDataBaseIsOpen() {
        return mDataBaseIsOpen;
    }
}

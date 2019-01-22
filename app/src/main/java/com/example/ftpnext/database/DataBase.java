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

    private static TableTest1DAO mTableTest1Dao;

    private static DataBase sSingleton = null;
    private static boolean sDataBaseIsOpen = false;

    private DataBaseOpenHelper mDataBaseOpenHelper;

    private DataBase() {
    }

    public static DataBase getInstance() {
        if (sSingleton != null)
            return sSingleton;
        return (sSingleton = new DataBase());
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
        if (sDataBaseIsOpen)
            return true;

        LogManager.info(TAG, "Open database");
        mDataBaseOpenHelper = new DataBaseOpenHelper(iContext);
        SQLiteDatabase lDataBase = mDataBaseOpenHelper.getWritableDatabase();

        //DAO list
        mTableTest1Dao = new TableTest1DAO(lDataBase);

        return (sDataBaseIsOpen = true);
    }

    public static boolean isDataBaseIsOpen() {
        return sDataBaseIsOpen;
    }
}

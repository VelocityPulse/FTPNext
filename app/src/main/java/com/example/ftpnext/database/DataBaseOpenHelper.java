package com.example.ftpnext.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.AsyncTask;

import com.example.ftpnext.core.LogManager;
import com.example.ftpnext.database.TableTest1.ITableTest1Schema;

public class DataBaseOpenHelper extends SQLiteOpenHelper {

    private static final String TAG = "DATABASE : Database Open Helper";

    public DataBaseOpenHelper(Context context) {
        super(context, DataBase.DATABASE_NAME, null, DataBase.DATABASE_VERSION);
        LogManager.info(TAG, "Constructor");
    }

    @Override
    public void onCreate(SQLiteDatabase iDataBase) {
        LogManager.info(TAG, "On Create DataBase");
        iDataBase.execSQL(ITableTest1Schema.TABLE_CREATE);

        LogManager.info(TAG, "Database tables created");
    }

    @Override
    public void onUpgrade(SQLiteDatabase iDataBase, int iOldVersion, int iNewVersion) {
        LogManager.info(TAG, "Upgrading database from version " + iOldVersion + " to " + iNewVersion + " which destroys all old data");
        UpgradeTask upgradeTask = new UpgradeTask(iDataBase, iOldVersion, iNewVersion);
        upgradeTask.execute();
    }

    private static class UpgradeTask extends AsyncTask<String, Void, Boolean> {

        private static final String TAG = DataBaseOpenHelper.TAG + " UpgradeTask : ";

        private SQLiteDatabase mDataBase;

        private int mOldVersion;
        private int mNewVersion;

        UpgradeTask(SQLiteDatabase iDataBase, int iOldVersion, int iNewVersion) {
            mDataBase = iDataBase;
            mOldVersion = iOldVersion;
            mNewVersion = iNewVersion;
        }

        @Override
        protected Boolean doInBackground(String... params) {

            DataBase.getTableTest1Dao().onUpgradeTable(mOldVersion, mNewVersion);
            //todo : call all onUpgrade

            //this code explain how to update a table
/*
            List<TableTest1> lTableTest1s = DataBase.getTableTest1Dao().fetchAll();
            mDataBase.execSQL("DROP TABLE IF EXISTS " + ITableTest1Schema.TABLE);
            onCreate(mDataBase);
            if (!DataBase.getTableTest1Dao().add(lTableTest1s))
                return LogManager.error(UpgradeTask.TAG, "Adding TableTest has failed");
*/
            return true;
        }

        @Override
        protected void onPostExecute(Boolean iResult) {
            if (iResult)
                LogManager.info(TAG, "Database has been successfully upgraded");
            else
                LogManager.error(TAG, "Database upgrade failed");
        }
    }
}
package com.example.ftpnext.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.AsyncTask;

import com.example.ftpnext.core.LogManager;

import java.util.List;

public class DataBaseOpenHelper extends SQLiteOpenHelper {

    private static final String TAG = "DATABASE : Database Open Helper";

    private List<String> mTableSchemaToCreate;

    public DataBaseOpenHelper(Context context, List<String> iTableSchemaToCreate) {
        super(context, DataBase.DATABASE_NAME, null, DataBase.DATABASE_VERSION);
        mTableSchemaToCreate = iTableSchemaToCreate;
        LogManager.info(TAG, "Constructor");
    }

    @Override
    public void onCreate(SQLiteDatabase iDataBase) {

    }

    @Override
    public void onOpen(SQLiteDatabase iDataBase) {
        LogManager.info(TAG, "On open DataBase");

        for (String lTableCreate : mTableSchemaToCreate) {
            iDataBase.execSQL(lTableCreate);
        }

        LogManager.info(TAG, "DataBase tables created");
        super.onOpen(iDataBase);
    }

    @Override
    public void onUpgrade(SQLiteDatabase iDataBase, int iOldVersion, int iNewVersion) {
        LogManager.info(TAG, "Upgrading DataBase from version " + iOldVersion + " to " + iNewVersion + " which destroys all old data");
        UpgradeTask upgradeTask = new UpgradeTask(iDataBase, iOldVersion, iNewVersion);
        upgradeTask.execute();
    }

    private static class UpgradeTask extends AsyncTask<String, Void, Boolean> {

        private static final String TAG = DataBaseOpenHelper.TAG + " UpgradeTask";

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

            while (!DataBase.isDataBaseIsOpen()) {
                try {
                    LogManager.info(TAG, "Upgrade task is waiting...");
                    Thread.sleep(100L);
                } catch (InterruptedException iE) {
                    iE.printStackTrace();
                }
            }

            DataBase.getTableTest1DAO().onUpgradeTable(mOldVersion, mNewVersion);
            //instruction : call all onUpgrade

            //this code explain how to update a table
/*
            List<TableTest1> lTableTest1s = DataBase.getTableTest1DAO().fetchAll();
            mDataBase.execSQL("DROP TABLE IF EXISTS " + ITableTest1Schema.TABLE);
            onCreate(mDataBase);
            if (!DataBase.getTableTest1DAO().add(lTableTest1s))
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

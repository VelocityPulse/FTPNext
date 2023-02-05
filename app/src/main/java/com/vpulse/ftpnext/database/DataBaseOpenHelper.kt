package com.vpulse.ftpnext.database

import android.content.*
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.AsyncTask
import com.vpulse.ftpnext.core.*
import com.vpulse.ftpnext.database.DataBaseOpenHelper

class DataBaseOpenHelper constructor(
    context: Context?,
    private val mTableSchemaToCreate: List<String>
) : SQLiteOpenHelper(
    context,
    DataBase.DATABASE_NAME,
    null,
    DataBase.DATABASE_VERSION
) {

    init {
        LogManager.info(TAG, "Constructor")
    }

    override fun onCreate(iDataBase: SQLiteDatabase) {
        LogManager.info(TAG, "On create DataBase")
    }

    override fun onOpen(iDataBase: SQLiteDatabase) {
        LogManager.info(TAG, "On open DataBase")
        for (lTableCreate: String? in mTableSchemaToCreate) {
            iDataBase.execSQL(lTableCreate)
        }
        LogManager.info(TAG, "DataBase tables created")
        super.onOpen(iDataBase)
    }

    override fun onUpgrade(iDataBase: SQLiteDatabase, iOldVersion: Int, iNewVersion: Int) {
        LogManager.info(
            TAG,
            "Upgrading DataBase from version " + iOldVersion + " to " + iNewVersion
        )
        val upgradeTask: UpgradeTask = UpgradeTask(iDataBase, iOldVersion, iNewVersion)
        upgradeTask.execute()
    }

    private class UpgradeTask(
        private val mDataBase: SQLiteDatabase,
        private val mOldVersion: Int,
        private val mNewVersion: Int
    ) : AsyncTask<String?, Void?, Boolean>() {

        override fun doInBackground(vararg params: String?): Boolean {
            while (!DataBase.isDataBaseIsOpen) {
                try {
                    LogManager.info(TAG, "Upgrade task is waiting...")
                    Thread.sleep(100L)
                } catch (iE: InterruptedException) {
                    iE.printStackTrace()
                }
            }

            DataBase.Companion.tableTest1DAO!!.onUpgradeTable(mOldVersion, mNewVersion)
            DataBase.Companion.fTPServerDAO!!.onUpgradeTable(mOldVersion, mNewVersion)
            DataBase.Companion.pendingFileDAO!!.onUpgradeTable(mOldVersion, mNewVersion)
            //instruction : call all onUpgrade

            //this code explain how to update a table
//            List<TableTest1> lTableTest1s = DataBase.getTableTest1DAO().fetchAll();
//            mDataBase.execSQL("DROP TABLE IF EXISTS " + ITableTest1Schema.TABLE);
//            onCreate(mDataBase);
//            if (!DataBase.getTableTest1DAO().add(lTableTest1s))
//                return LogManager.error(UpgradeTask.TAG, "Adding TableTest has failed");
            return true
        }

        override fun onPostExecute(iResult: Boolean) {
            if (iResult) LogManager.info(
                TAG,
                "Database has been successfully upgraded"
            ) else LogManager.error(
                TAG, "Database upgrade failed"
            )
        }

        companion object {
            private val TAG: String = DataBaseOpenHelper.TAG + " UpgradeTask"
        }
    }

    companion object {
        private val TAG: String = "DATABASE : Database Open Helper"
    }
}
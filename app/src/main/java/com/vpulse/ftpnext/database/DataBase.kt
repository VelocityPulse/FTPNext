package com.vpulse.ftpnext.database

import android.content.*
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import com.vpulse.ftpnext.core.*
import com.vpulse.ftpnext.database.FTPServerTable.FTPServerDAO
import com.vpulse.ftpnext.database.FTPServerTable.IFTPServerSchema
import com.vpulse.ftpnext.database.PendingFileTable.IPendingFileSchema
import com.vpulse.ftpnext.database.PendingFileTable.PendingFileDAO
import com.vpulse.ftpnext.database.TableTest1.ITableTest1Schema
import com.vpulse.ftpnext.database.TableTest1.TableTest1DAO

class DataBase private constructor() {
    /**
     * @param iContext Context of the app
     * @return True if DataBase is opened.
     * @throws SQLException Exception during opening database.
     */
    @Throws(SQLException::class)
    fun open(iContext: Context?): Boolean {
        if (isDataBaseIsOpen) return true
        LogManager.info(TAG, "Open database")
        val lTableSchemaToCreate: MutableList<String> = ArrayList()

        // Table Create
        lTableSchemaToCreate.add(ITableTest1Schema.TABLE_CREATE)
        lTableSchemaToCreate.add(IFTPServerSchema.TABLE_CREATE)
        lTableSchemaToCreate.add(IPendingFileSchema.TABLE_CREATE)
        val mDataBaseOpenHelper: DataBaseOpenHelper =
            DataBaseOpenHelper(iContext, lTableSchemaToCreate)
        val lDataBase: SQLiteDatabase = mDataBaseOpenHelper.getWritableDatabase()

        //DAO list
        tableTest1DAO = TableTest1DAO(lDataBase)
        fTPServerDAO = FTPServerDAO(lDataBase)
        pendingFileDAO = PendingFileDAO(lDataBase)
        return true.also { isDataBaseIsOpen = it }
    }

    companion object {
        const val DATABASE_NAME: String = AppConstants.DATABASE_NAME
        const val DATABASE_VERSION: Int = AppConstants.DATABASE_VERSION

        private val TAG: String = "DATABASE : Database"

        var tableTest1DAO: TableTest1DAO? = null

        var fTPServerDAO: FTPServerDAO? = null

        var pendingFileDAO: PendingFileDAO? = null

        private var sSingleton: DataBase? = null

        var isDataBaseIsOpen: Boolean = false

        val instance: DataBase?
            get() {
                if (sSingleton != null) return sSingleton
                return (DataBase().also({ sSingleton = it }))
            }

        fun initDataDirectory() {}
    }
}
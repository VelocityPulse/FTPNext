package com.vpulse.ftpnext.database.TableTest1

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.vpulse.ftpnext.core.*
import com.vpulse.ftpnext.database.ADataAccessObject

class TableTest1DAO constructor(iDataBase: SQLiteDatabase) :
    ADataAccessObject<TableTest1>(iDataBase), ITableTest1Schema {

    init {
        LogManager.info(TAG, "Creating " + this.javaClass.getSimpleName())
    }

    public override fun fetchById(iId: Int): TableTest1? {
        return super.fetchById(
            ITableTest1Schema.TABLE,
            iId,
            ITableTest1Schema.COLUMN_DATABASE_ID,
            ITableTest1Schema.COLUMNS
        )
    }

    public override fun fetchAll(): List<TableTest1> {
        return super.fetchAll(
            ITableTest1Schema.TABLE, ITableTest1Schema.COLUMNS, ITableTest1Schema.COLUMN_DATABASE_ID
        )
    }

    public override fun add(iObject: TableTest1): Int {
        return super.add(iObject, ITableTest1Schema.TABLE)
    }

    public override fun update(iObject: TableTest1): Boolean {
        return super.update(
            iObject,
            iObject.dataBaseId,
            ITableTest1Schema.TABLE,
            ITableTest1Schema.COLUMN_DATABASE_ID
        )
    }

    public override fun deleteAll(): Boolean {
        return super.delete(ITableTest1Schema.Companion.TABLE, null, null) > 0
    }

    public override fun delete(iId: Int): Boolean {
        return super.delete(
            iId, ITableTest1Schema.TABLE, ITableTest1Schema.COLUMN_DATABASE_ID
        )
    }

    public override fun delete(iObject: TableTest1): Boolean {
        return delete(iObject.dataBaseId)
    }

    public override fun onUpgradeTable(iOldVersion: Int, iNewVersion: Int) {}

    override fun setContentValue(iObject: TableTest1) {
        mContentValues = ContentValues()
        if (iObject.dataBaseId != 0) {
            mContentValues!!.put(
                ITableTest1Schema.COLUMN_DATABASE_ID, iObject.dataBaseId
            )
        }
        mContentValues!!.put(ITableTest1Schema.COLUMN_VALUE, iObject.value)
    }

    override fun cursorToEntity(iCursor: Cursor): TableTest1 {
        val oObject = TableTest1()
        if (iCursor.getColumnIndex(ITableTest1Schema.Companion.COLUMN_DATABASE_ID) != -1) {
            oObject.dataBaseId =
                iCursor.getInt(iCursor.getColumnIndexOrThrow(ITableTest1Schema.COLUMN_DATABASE_ID))
        }
        if (iCursor.getColumnIndex(ITableTest1Schema.COLUMN_VALUE) != -1) {
            oObject.value =
                iCursor.getInt(iCursor.getColumnIndexOrThrow(ITableTest1Schema.Companion.COLUMN_VALUE))
        }

        return oObject
    }

    fun add(iTableTest1List: List<TableTest1?>?): Boolean {
        if (iTableTest1List == null) return LogManager.error(TAG, "List is null")
        for (lTableTest1: TableTest1? in iTableTest1List) {
            if (lTableTest1 == null) return LogManager.error(
                TAG, "Object in list is null. Return error."
            )
            if (add(lTableTest1, ITableTest1Schema.Companion.TABLE) == -1) {
                return false
            }
        }
        return true
    }

    companion object {

        private const val TAG: String = "DATABASE : Table Test 1 DAO"
    }
}
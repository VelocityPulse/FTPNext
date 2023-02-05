package com.vpulse.ftpnext.database

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase

abstract class ADataBaseSQLiteHelper protected constructor(protected var mDataBase: SQLiteDatabase) {
    protected fun delete(
        iTableName: String?,
        iSelection: String?,
        iSelectionArgs: Array<String?>?
    ): Int {
        return mDataBase.delete(iTableName, iSelection, iSelectionArgs)
    }

    protected fun insert(iTableName: String?, iValues: ContentValues?): Long {
        return mDataBase.insert(iTableName, null, iValues)
    }

    protected fun query(
        iTableName: String?,
        iColumns: Array<String?>?,
        iSelection: String?,
        iSelectionArgs: Array<String>?,
        iSortOrder: String?
    ): Cursor {
        return mDataBase.query(
            iTableName,
            iColumns,
            iSelection,
            iSelectionArgs,
            null,
            null,
            iSortOrder
        )
    }

    protected fun query(
        iTableName: String?,
        iColumns: Array<String?>?,
        iSelection: String?,
        iSelectionArgs: Array<String?>?,
        iSortOrder: String?,
        iLimit: String?
    ): Cursor {
        return mDataBase.query(
            iTableName,
            iColumns,
            iSelection,
            iSelectionArgs,
            null,
            null,
            iSortOrder,
            iLimit
        )
    }

    protected fun query(
        iTableName: String?,
        iColumns: Array<String?>?,
        iSelection: String?,
        iSelectionArgs: Array<String?>?,
        iGroupBy: String?,
        iHaving: String?,
        iOrderBy: String?,
        iLimit: String?
    ): Cursor {
        return mDataBase.query(
            iTableName,
            iColumns,
            iSelection,
            iSelectionArgs,
            iGroupBy,
            iHaving,
            iOrderBy,
            iLimit
        )
    }

    protected fun update(
        iTableName: String?,
        iValues: ContentValues?,
        iSelection: String?,
        iSelectionArgs: Array<String>?
    ): Int {
        return mDataBase.update(iTableName, iValues, iSelection, iSelectionArgs)
    }

    protected fun rawQuery(iSQL: String?, iSelectionArgs: Array<String?>?): Cursor {
        return mDataBase.rawQuery(iSQL, iSelectionArgs)
    }
}
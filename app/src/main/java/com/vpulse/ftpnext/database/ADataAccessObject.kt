package com.vpulse.ftpnext.database

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import com.vpulse.ftpnext.core.LogManager

abstract class ADataAccessObject<T : ABaseTable> constructor(iDataBase: SQLiteDatabase) :
    ADataBaseSQLiteHelper(iDataBase) {

    private val TAG: String = "DATABASE : Data Access Object"
    protected var mContentValues: ContentValues? = null
    protected var mCursor: Cursor? = null

    abstract fun fetchById(iId: Int): T?

    abstract fun fetchAll(): List<T>

    abstract fun add(iObject: T): Int

    abstract fun update(iObject: T): Boolean

    abstract fun deleteAll(): Boolean

    abstract fun delete(iId: Int): Boolean

    abstract fun delete(iObject: T): Boolean

    abstract fun onUpgradeTable(iOldVersion: Int, iNewVersion: Int)

    protected abstract fun setContentValue(iObject: T)

    protected abstract fun cursorToEntity(iCursor: Cursor): T

    protected fun fetchById(iTable: String?,
                            iId: Int,
                            iColumnDataBaseId: String,
                            iColumns: Array<String?>?
    ): T? {
        val lSelectionArgs: Array<String> = arrayOf(iId.toString())
        val lSelection = "$iColumnDataBaseId = ?"
        var lObject: T? = null
        mCursor = super.query(iTable, iColumns, lSelection, lSelectionArgs, iColumnDataBaseId)
        if (mCursor != null) {
            mCursor!!.moveToFirst()
            while (!mCursor!!.isAfterLast) {
                lObject = cursorToEntity(mCursor!!)
                mCursor!!.moveToNext()
            }
            mCursor!!.close()
        }
        return lObject
    }

    protected fun fetchAll(iTable: String?, iColumns: Array<String?>, iColumnDataBaseId: String
    ): List<T> {
        val lList: MutableList<T> = ArrayList()
        mCursor = super.query(iTable, iColumns, null, null, iColumnDataBaseId)
        mCursor?.let {
            mCursor!!.moveToFirst()
            while (!mCursor!!.isAfterLast) {
                lList.add(cursorToEntity(mCursor!!))
                mCursor!!.moveToNext()
            }
            mCursor!!.close()
        }
        return lList
    }

    protected fun add(iObject: T, iTable: String?): Int {
        setContentValue(iObject)
        return try {
            val lNewId: Int = super.insert(iTable, mContentValues).toInt()
            iObject!!.dataBaseId = lNewId
            lNewId
        } catch (iEx: SQLiteConstraintException) {
            LogManager.error(TAG, "Add error: " + iEx.message)
            -1
        }
    }

    protected fun delete(iId: Int, iTable: String?, iColumnDataBaseId: String): Boolean {
        val selection = " $iColumnDataBaseId =$iId"
        return super.delete(iTable, selection, null) > 0
    }

    protected fun update(iObject: T, iId: Int, iTable: String?, iColumnDataBaseId: String
    ): Boolean {
        synchronized(ADataAccessObject::class.java) {
            setContentValue(iObject)
            try {
                val lSelection = "$iColumnDataBaseId = ?"
                val lSelectionArgs: Array<String> = arrayOf(iId.toString())
                return super.update(iTable, mContentValues, lSelection, lSelectionArgs) > 0
            } catch (iEx: SQLiteConstraintException) {
                return LogManager.error(
                    TAG,
                    "Update error :\nThread : " + Thread.currentThread().id + "\n" + iEx.message + "\n" + iObject.toString()
                ) //error
            }
        }
    }
}
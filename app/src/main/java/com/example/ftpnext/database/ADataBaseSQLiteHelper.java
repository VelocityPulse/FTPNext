package com.example.ftpnext.database;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

public abstract class ADataBaseSQLiteHelper {

    protected SQLiteDatabase mDataBase;

    protected ADataBaseSQLiteHelper(SQLiteDatabase iDataBase) {
        this.mDataBase = iDataBase;
    }

    protected int delete(String iTableName, String iSelection, String[] iSelectionArgs) {
        return mDataBase.delete(iTableName, iSelection, iSelectionArgs);
    }

    protected long insert(String iTableName, ContentValues iValues) {
        return mDataBase.insert(iTableName, null, iValues);
    }

    protected Cursor query(String iTableName, String[] iColumns, String iSelection, String[] iSelectionArgs, String iSortOrder) {
        return mDataBase.query(iTableName, iColumns, iSelection, iSelectionArgs, null, null, iSortOrder);
    }

    protected Cursor query(String iTableName, String[] iColumns, String iSelection, String[] iSelectionArgs, String iSortOrder, String iLimit) {
        return mDataBase.query(iTableName, iColumns, iSelection, iSelectionArgs, null, null, iSortOrder, iLimit);
    }

    protected Cursor query(String iTableName, String[] iColumns, String iSelection, String[] iSelectionArgs, String iGroupBy, String iHaving, String iOrderBy, String iLimit) {
        return mDataBase.query(iTableName, iColumns, iSelection, iSelectionArgs, iGroupBy, iHaving, iOrderBy, iLimit);
    }

    protected int update(String iTableName, ContentValues iValues, String iSelection, String[] iSelectionArgs) {
        return mDataBase.update(iTableName, iValues, iSelection, iSelectionArgs);
    }

    protected Cursor rawQuery(String iSQL, String[] iSelectionArgs) {
        return mDataBase.rawQuery(iSQL, iSelectionArgs);
    }
}

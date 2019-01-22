package com.example.ftpnext.database;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;

import com.example.ftpnext.core.LogManager;

import java.util.ArrayList;
import java.util.List;

public abstract class ADataAccessObject<T extends ABaseTable> extends ADataBaseSQLiteHelper {

    private final String TAG = "DATABASE : Data Access Object";
    protected ContentValues mContentValues = null;
    private Cursor mCursor = null;

    public ADataAccessObject(SQLiteDatabase iDataBase) {
        super(iDataBase);
    }

    public abstract T fetchById(int iId);

    public abstract List<T> fetchAll();

    public abstract boolean add(T iObject);

    public abstract boolean update(T iObject);

    public abstract boolean deleteAll();

    public abstract boolean delete(int iId);

    public abstract void onUpgradeTable(int iOldVersion, int iNewVersion);

    protected abstract void setContentValue(T iObject);

    protected abstract T cursorToEntity(Cursor iCursor);

    protected T fetchById(String iTable, int iId, String iColumnId, String[] iColumns) {
        final String lSelectionArgs[] = {String.valueOf(iId)};
        final String lSelection = iColumnId + " = ?";
        T lObject = null;

        mCursor = super.query(iTable, iColumns, lSelection, lSelectionArgs, iColumnId);
        if (mCursor != null) {
            mCursor.moveToFirst();
            while (!mCursor.isAfterLast()) {
                lObject = cursorToEntity(mCursor);
                mCursor.moveToNext();
            }
            mCursor.close();
        }
        return lObject;
    }

    protected List<T> fetchAll(String iTable, String[] iColumns, String iColumnId) {
        List<T> lList = new ArrayList<>();

        mCursor = super.query(iTable, iColumns, null, null, iColumnId);
        if (mCursor != null) {
            mCursor.moveToFirst();
            while (!mCursor.isAfterLast()) {
                lList.add(cursorToEntity(mCursor));
                mCursor.moveToNext();
            }
            mCursor.close();
        }
        return lList;
    }

    protected boolean add(T iObject, String iTable) {
        setContentValue(iObject);

        try {
            return super.insert(iTable, mContentValues) > 0;
        } catch (SQLiteConstraintException iEx) {
            return LogManager.error(TAG, "Add error: " + iEx.getMessage()); //error
        }
    }

    protected boolean update(T iObject, int iId, String iTable, String iColumnId) {
        setContentValue(iObject);

        try {
            final String lSelection = " " + iColumnId + " = " + iId;
            return super.update(iTable, mContentValues, lSelection, null) > 0;
        } catch (SQLiteConstraintException iEx) {
            return LogManager.error(TAG, "Update error : " + iEx.getMessage()); //error
        }
    }
}

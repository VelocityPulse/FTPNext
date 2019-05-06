package com.example.ftpnext.database.TableTest1;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.ftpnext.core.LogManager;
import com.example.ftpnext.database.ADataAccessObject;

import java.util.List;

public class TableTest1DAO extends ADataAccessObject<TableTest1> implements ITableTest1Schema {

    private static final String TAG = "DATABASE : Table Test 1 DAO";

    public TableTest1DAO(SQLiteDatabase iDataBase) {
        super(iDataBase);
        LogManager.info(TAG, "Creating " + this.getClass().getSimpleName());
    }

    @Override
    public TableTest1 fetchById(int iId) {
        return super.fetchById(TABLE, iId, COLUMN_DATABASE_ID, COLUMNS);
    }

    @Override
    public List<TableTest1> fetchAll() {
        return super.fetchAll(TABLE, COLUMNS, COLUMN_DATABASE_ID);
    }

    @Override
    public int add(TableTest1 iObject) {
        if (iObject == null) {
            LogManager.error(TAG, "Object to add is null");
            return -1;
        }

        return super.add(iObject, TABLE);
    }

    @Override
    public boolean update(TableTest1 iObject) {
        if (iObject == null)
            return LogManager.error(TAG, "Object to update is null");

        return super.update(iObject, iObject.getDataBaseId(), TABLE, COLUMN_DATABASE_ID);
    }

    @Override
    public boolean deleteAll() {
        return super.delete(TABLE, null, null) > 0;
    }

    @Override
    public boolean delete(int iId) {
        return super.delete(iId, TABLE, COLUMN_DATABASE_ID);
    }

    @Override
    public boolean delete(TableTest1 iObject) {
        if (iObject == null) {
            LogManager.error(TAG, "Object to set content value is null");
            return false;
        }
        return delete(iObject.getDataBaseId());
    }

    @Override
    public void onUpgradeTable(int iOldVersion, int iNewVersion) {

    }

    @Override
    protected void setContentValue(TableTest1 iObject) {
        if (iObject == null) {
            LogManager.error(TAG, "Object to set content value is null");
            return;
        }

        mContentValues = new ContentValues();
        if (iObject.getDataBaseId() != 0) {
            mContentValues.put(COLUMN_DATABASE_ID, iObject.getDataBaseId());
        }
        mContentValues.put(COLUMN_VALUE, iObject.getValue());
    }

    @Override
    protected TableTest1 cursorToEntity(Cursor iCursor) {
        if (iCursor == null) {
            LogManager.error(TAG, "Cursor in cursorToEntity is null");
            return null;
        }

        TableTest1 oObject = new TableTest1();
        if (iCursor.getColumnIndex(COLUMN_DATABASE_ID) != -1)
            oObject.setDataBaseId(iCursor.getInt(iCursor.getColumnIndexOrThrow(COLUMN_DATABASE_ID)));
        if (iCursor.getColumnIndex(COLUMN_VALUE) != -1)
            oObject.setValue(iCursor.getInt(iCursor.getColumnIndexOrThrow(COLUMN_VALUE)));
        return oObject;
    }

    public boolean add(List<TableTest1> iTableTest1List) {
        if (iTableTest1List == null)
            return LogManager.error(TAG, "List is null");

        for (TableTest1 lTableTest1 : iTableTest1List) {
            if (lTableTest1 == null)
                return LogManager.error(TAG, "Object in list is null. Return error.");
            if (add(lTableTest1, TABLE) == -1) {
                return false;
            }
        }
        return true;
    }
}

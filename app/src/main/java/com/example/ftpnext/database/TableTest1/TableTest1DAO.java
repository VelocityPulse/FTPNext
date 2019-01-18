package com.example.ftpnext.database.TableTest1;

import android.content.ContentValues;
import android.database.sqlite.SQLiteConstraintException;

import com.example.ftpnext.core.LogManager;
import com.example.ftpnext.database.ADataAccessObject;

import java.util.List;

public class TableTest1DAO extends ADataAccessObject<TableTest1> implements ITableTest1Schema {

    private static final String TAG = "TABLETEST1 DAO";

    @Override
    public TableTest1 fetchById(int iId) {
        return null;
    }


    @Override
    public List<TableTest1> fetchAll() {
        return null;
    }

    @Override
    public boolean add(TableTest1 iObject) {
        setContentValue(iObject);
        try {
            return super.insert(TABLE, getContentValue()) > 0;
        } catch (SQLiteConstraintException ex) {
            LogManager.error(TAG, "Database ex.: " + ex.getMessage()); //error
            return false;
        }
        return false;
    }

    @Override
    public boolean update(TableTest1 iObject) {
        return false;
    }

    @Override
    public boolean deleteAll() {
        return false;
    }

    @Override
    public boolean delete(int iId) {
        return false;
    }

    @Override
    protected void setContentValue(TableTest1 iObject) {
        mInitialValues = new ContentValues();
        if (iObject.getId() != 0) {
            mInitialValues.put(COLUMN_ID, iObject.getId());
            mInitialValues.put(COLUMN_VALUE, iObject.getValue());
        }
    }



}

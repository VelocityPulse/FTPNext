package com.example.ftpnext.database;

import android.content.ContentValues;
import android.database.Cursor;

import java.util.List;

public abstract class ADataAccessObject<T> {

    public Cursor mCursor = null;
    public ContentValues mInitialValues = null;

    public ContentValues getContentValue() {
        return mInitialValues;
    }

    public abstract void setContentValue(T iObject);

    public abstract T fetchById(int iId);

    public abstract List<T> fetchAll();

    public abstract boolean add(T iObject);

    public abstract boolean update(T iObject);

    public abstract boolean deleteAll();

    public abstract boolean delete(int iId);



}

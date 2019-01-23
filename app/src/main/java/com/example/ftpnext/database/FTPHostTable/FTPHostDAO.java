package com.example.ftpnext.database.FTPHostTable;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.ftpnext.core.FTPCharacterEncoding;
import com.example.ftpnext.core.FTPType;
import com.example.ftpnext.core.LogManager;
import com.example.ftpnext.database.ADataAccessObject;

import java.util.List;

public class FTPHostDAO extends ADataAccessObject<FTPHost> implements IFTPHostSchema {

    private static final String TAG = "DATABASE : FTP Host DAO";

    public FTPHostDAO(SQLiteDatabase iDataBase) {
        super(iDataBase);
        LogManager.info(TAG, "Creating " + this.getClass().getSimpleName());
    }

    @Override
    public FTPHost fetchById(int iId) {
        return super.fetchById(TABLE, iId, COLUMN_DATABASE_ID, COLUMNS);
    }

    @Override
    public List<FTPHost> fetchAll() {
        return super.fetchAll(TABLE, COLUMNS, COLUMN_DATABASE_ID);
    }

    @Override
    public boolean add(FTPHost iObject) {
        if (iObject == null)
            return LogManager.error(TAG, "Object to add is null");

        return super.add(iObject, TABLE);
    }

    @Override
    public boolean update(FTPHost iObject) {
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
        final String selection = " " + COLUMN_DATABASE_ID + " =" + iId;
        return super.delete(TABLE, selection, null) > 0;
    }

    @Override
    public void onUpgradeTable(int iOldVersion, int iNewVersion) {

    }

    @Override
    protected void setContentValue(FTPHost iObject) {
        if (iObject == null) {
            LogManager.error(TAG, "Object to set content value is null");
            return;
        }

        mContentValues = new ContentValues();
        if (iObject.getDataBaseId() != 0) {
            mContentValues.put(COLUMN_DATABASE_ID, iObject.getDataBaseId());
        }
        mContentValues.put(COLUMN_NAME, iObject.getName());
        mContentValues.put(COLUMN_USER, iObject.getUser());
        mContentValues.put(COLUMN_PASS, iObject.getPass());
        mContentValues.put(COLUMN_HOST, iObject.getHost());
        mContentValues.put(COLUMN_PORT, iObject.getPort());
        mContentValues.put(COLUMN_ATTRIBUTED_FOLDER, iObject.getAttributedFolder());
        mContentValues.put(COLUMN_CHARACTER_ENCODING, iObject.getFTPCharacterEncoding().getValue());
        mContentValues.put(COLUMN_TYPE, iObject.getFTPType().getValue());
    }

    @Override
    protected FTPHost cursorToEntity(Cursor iCursor) {
        if (iCursor == null) {
            LogManager.error(TAG, "Cursor in cursorToEntity is null");
            return null;
        }

        FTPHost oObject = new FTPHost();
        if (iCursor.getColumnIndex(COLUMN_DATABASE_ID) != -1)
            oObject.setDataBaseId(iCursor.getInt(iCursor.getColumnIndexOrThrow(COLUMN_DATABASE_ID)));
        if (iCursor.getColumnIndex(COLUMN_NAME) != -1)
            oObject.setName(iCursor.getString(iCursor.getColumnIndexOrThrow(COLUMN_NAME)));
        if (iCursor.getColumnIndex(COLUMN_USER) != -1)
            oObject.setUser(iCursor.getString(iCursor.getColumnIndexOrThrow(COLUMN_USER)));
        if (iCursor.getColumnIndex(COLUMN_PASS) != -1)
            oObject.setPass(iCursor.getString(iCursor.getColumnIndexOrThrow(COLUMN_PASS)));
        if (iCursor.getColumnIndex(COLUMN_HOST) != -1)
            oObject.setHost(iCursor.getString(iCursor.getColumnIndexOrThrow(COLUMN_HOST)));
        if (iCursor.getColumnIndex(COLUMN_PORT) != -1)
            oObject.setPort(iCursor.getInt(iCursor.getColumnIndexOrThrow(COLUMN_PORT)));
        if (iCursor.getColumnIndex(COLUMN_ATTRIBUTED_FOLDER) != -1)
            oObject.setAttributedFolder(iCursor.getString(iCursor.getColumnIndexOrThrow(COLUMN_ATTRIBUTED_FOLDER)));
        if (iCursor.getColumnIndex(COLUMN_CHARACTER_ENCODING) != -1)
            oObject.setFTPCharacterEncoding(FTPCharacterEncoding.getValue(
                    iCursor.getInt(iCursor.getColumnIndexOrThrow(COLUMN_CHARACTER_ENCODING))));
        if (iCursor.getColumnIndex(COLUMN_TYPE) != -1)
            oObject.setFTPType(FTPType.getValue(
                    iCursor.getInt(iCursor.getColumnIndexOrThrow(COLUMN_TYPE))));

        return oObject;
    }
}

package com.vpulse.ftpnext.database.FTPServerTable;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.vpulse.ftpnext.core.FTPCharacterEncoding;
import com.vpulse.ftpnext.core.FTPType;
import com.vpulse.ftpnext.core.LogManager;
import com.vpulse.ftpnext.database.ADataAccessObject;
import com.vpulse.ftpnext.ftpservices.FTPServices;

import java.util.List;

public class FTPServerDAO extends ADataAccessObject<FTPServer> implements IFTPServerSchema {

    private static final String TAG = "DATABASE : FTP Host DAO";

    private static final int VERSION_UPDATE_ABSOLUTE_PATH_VALUE = 3;

    public FTPServerDAO(SQLiteDatabase iDataBase) {
        super(iDataBase);
        LogManager.info(TAG, "Creating " + this.getClass().getSimpleName());
    }

    // TODO test
    public FTPServer fetchByName(String iName) {
        final String[] lSelectionArgs = {iName};
        final String lSelection = COLUMN_NAME + " = ?";
        FTPServer lObject = null;

        mCursor = super.query(TABLE, COLUMN_ARRAY, lSelection, lSelectionArgs, COLUMN_NAME);
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

    @Override
    public FTPServer fetchById(int iId) {
        return super.fetchById(TABLE, iId, COLUMN_DATABASE_ID, COLUMN_ARRAY);
    }

    @Override
    public List<FTPServer> fetchAll() {
        return super.fetchAll(TABLE, COLUMN_ARRAY, COLUMN_DATABASE_ID);
    }

    @Override
    public int add(FTPServer iObject) {
        if (iObject == null) {
            LogManager.error(TAG, "Object to add is null");
            return -1;
        }

        return super.add(iObject, TABLE);
    }

    @Override
    public boolean update(FTPServer iObject) {
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
    public boolean delete(FTPServer iObject) {
        if (iObject == null) {
            LogManager.error(TAG, "Object to set content value is null");
            return false;
        }
        return delete(iObject.getDataBaseId());
    }

    @Override
    public void onUpgradeTable(int iOldVersion, int iNewVersion) {
        if (iOldVersion < VERSION_UPDATE_ABSOLUTE_PATH_VALUE) {
            List<FTPServer> lFTPServerList = fetchAll();

            for (FTPServer lItem : lFTPServerList) {
                if (!lItem.getAbsolutePath().endsWith("/")) {
                    lItem.setAbsolutePath(lItem.getAbsolutePath() + "/");
                    update(lItem);
                }
            }
        }
    }

    @Override
    protected void setContentValue(FTPServer iObject) {
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
        mContentValues.put(COLUMN_SERVER, iObject.getServer());
        mContentValues.put(COLUMN_PORT, iObject.getPort());
        mContentValues.put(COLUMN_FOLDER_NAME, iObject.getFolderName());
        mContentValues.put(COLUMN_ABSOLUTE_PATH, iObject.getAbsolutePath());
        mContentValues.put(COLUMN_CHARACTER_ENCODING, iObject.getFTPCharacterEncoding().getValue());
        mContentValues.put(COLUMN_TYPE, iObject.getFTPType().getValue());
    }

    @Override
    protected FTPServer cursorToEntity(Cursor iCursor) {
        if (iCursor == null) {
            LogManager.error(TAG, "Cursor in cursorToEntity is null");
            return null;
        }

        FTPServer oObject = new FTPServer();
        if (iCursor.getColumnIndex(COLUMN_DATABASE_ID) != -1)
            oObject.setDataBaseId(iCursor.getInt(iCursor.getColumnIndexOrThrow(COLUMN_DATABASE_ID)));
        if (iCursor.getColumnIndex(COLUMN_NAME) != -1)
            oObject.setName(iCursor.getString(iCursor.getColumnIndexOrThrow(COLUMN_NAME)));
        if (iCursor.getColumnIndex(COLUMN_USER) != -1)
            oObject.setUser(iCursor.getString(iCursor.getColumnIndexOrThrow(COLUMN_USER)));
        if (iCursor.getColumnIndex(COLUMN_PASS) != -1)
            oObject.setPass(iCursor.getString(iCursor.getColumnIndexOrThrow(COLUMN_PASS)));
        if (iCursor.getColumnIndex(COLUMN_SERVER) != -1)
            oObject.setServer(iCursor.getString(iCursor.getColumnIndexOrThrow(COLUMN_SERVER)));
        if (iCursor.getColumnIndex(COLUMN_PORT) != -1)
            oObject.setPort(iCursor.getInt(iCursor.getColumnIndexOrThrow(COLUMN_PORT)));
        if (iCursor.getColumnIndex(COLUMN_FOLDER_NAME) != 1)
            oObject.setFolderName(iCursor.getString(iCursor.getColumnIndexOrThrow(COLUMN_FOLDER_NAME)));
        if (iCursor.getColumnIndex(COLUMN_ABSOLUTE_PATH) != -1)
            oObject.setAbsolutePath(iCursor.getString(iCursor.getColumnIndexOrThrow(COLUMN_ABSOLUTE_PATH)));
        if (iCursor.getColumnIndex(COLUMN_CHARACTER_ENCODING) != -1)
            oObject.setFTPCharacterEncoding(FTPCharacterEncoding.getValue(
                    iCursor.getInt(iCursor.getColumnIndexOrThrow(COLUMN_CHARACTER_ENCODING))));
        if (iCursor.getColumnIndex(COLUMN_TYPE) != -1)
            oObject.setFTPType(FTPType.getValue(
                    iCursor.getInt(iCursor.getColumnIndexOrThrow(COLUMN_TYPE))));

        return oObject;
    }
}

package com.vpulse.ftpnext.database.PendingFileTable;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.vpulse.ftpnext.core.LoadDirection;
import com.vpulse.ftpnext.core.LogManager;
import com.vpulse.ftpnext.database.ADataAccessObject;
import com.vpulse.ftpnext.database.FTPServerTable.FTPServer;

import java.util.ArrayList;
import java.util.List;

public class PendingFileDAO extends ADataAccessObject<PendingFile> implements IPendingFileSchema {

    private static final String TAG = "DATABASE : Pending file DAO";

    public PendingFileDAO(SQLiteDatabase iDataBase) {
        super(iDataBase);
        LogManager.info(TAG, "Creating " + this.getClass().getSimpleName());
    }

    // TODO : Huge tests to do here.
    // If function bugs, also fix FTPServerDAO.fetchByName()
    public PendingFile[] fetchByServer(FTPServer iFTPServer) {
        final String[] lSelectionArgs = {String.valueOf(iFTPServer.getDataBaseId())};
        final String lSelection = COLUMN_SERVER_ID + " = ?";
        List<PendingFile> lObjectList = new ArrayList<>();

        mCursor = super.query(TABLE, COLUMNS, lSelection, lSelectionArgs, COLUMN_DATABASE_ID);
        if (mCursor != null) {
            mCursor.moveToFirst();
            while (!mCursor.isAfterLast()) {
                lObjectList.add(cursorToEntity(mCursor));
                mCursor.moveToNext();
            }
            mCursor.close();
        }
        return (PendingFile[]) lObjectList.toArray();
    }

    @Override
    public PendingFile fetchById(int iId) {
        return super.fetchById(TABLE, iId, COLUMN_DATABASE_ID, COLUMNS);
    }

    @Override
    public List<PendingFile> fetchAll() {
        return super.fetchAll(TABLE, COLUMNS, COLUMN_DATABASE_ID);
    }

    public int add(PendingFile[] iObjects) {
        if (iObjects == null || iObjects.length == 0) {
            LogManager.error(TAG, "No object to add");
            return 0;
        }

        int oErrors = 0;
        for (PendingFile lItem : iObjects) {
            oErrors += (super.add(lItem, TABLE) < 0) ? 1 : 0;
        }
        return oErrors;
    }

    @Override
    public int add(PendingFile iObject) {
        if (iObject == null) {
            LogManager.error(TAG, "Object to add is null");
            return -1;
        }

        return super.add(iObject, TABLE);
    }

    @Override
    public boolean update(PendingFile iObject) {
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
    public boolean delete(PendingFile iObject) {
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
    protected void setContentValue(PendingFile iObject) {
        if (iObject == null) {
            LogManager.error(TAG, "Object to set content value is null");
            return;
        }

        mContentValues = new ContentValues();
        if (iObject.getDataBaseId() != 0) {
            mContentValues.put(COLUMN_DATABASE_ID, iObject.getDataBaseId());
        }

        mContentValues.put(COLUMN_SERVER_ID, iObject.getServerId());
        mContentValues.put(COLUMN_LOAD_DIRECTION, iObject.getLoadDirection().getValue());
        mContentValues.put(COLUMN_STARTED, iObject.isStarted());
        mContentValues.put(COLUMN_NAME, iObject.getName());
        mContentValues.put(COLUMN_PATH, iObject.getPath());
        mContentValues.put(COLUMN_ENCLOSING_NAME, iObject.getEnclosingName());
        mContentValues.put(COLUMN_FINISHED, iObject.isFinished());
        mContentValues.put(COLUMN_PROGRESS, iObject.getProgress());
    }

    @Override
    protected PendingFile cursorToEntity(Cursor iCursor) {
        if (iCursor == null) {
            LogManager.error(TAG, "Cursor in cursorToEntity is null");
            return null;
        }

        PendingFile oObject = new PendingFile();
        if (iCursor.getColumnIndex(COLUMN_DATABASE_ID) != -1)
            oObject.setDataBaseId(iCursor.getInt(iCursor.getColumnIndexOrThrow(COLUMN_DATABASE_ID)));
        if (iCursor.getColumnIndex(COLUMN_SERVER_ID) != -1)
            oObject.setServerId(iCursor.getInt(iCursor.getColumnIndexOrThrow(COLUMN_SERVER_ID)));
        if (iCursor.getColumnIndex(COLUMN_LOAD_DIRECTION) != -1)
            oObject.setLoadDirection(LoadDirection.getValue(
                    iCursor.getColumnIndexOrThrow(COLUMN_LOAD_DIRECTION)));
        if (iCursor.getColumnIndexOrThrow(COLUMN_STARTED) != -1)
            oObject.setStarted(iCursor.getInt(iCursor.getColumnIndexOrThrow(COLUMN_STARTED)) != 0);
        if (iCursor.getColumnIndexOrThrow(COLUMN_NAME) != -1)
            oObject.setName(iCursor.getString(iCursor.getColumnIndexOrThrow(COLUMN_NAME)));
        if (iCursor.getColumnIndexOrThrow(COLUMN_PATH) != -1)
            oObject.setPath(iCursor.getString(iCursor.getColumnIndexOrThrow(COLUMN_PATH)));
        if (iCursor.getColumnIndexOrThrow(COLUMN_ENCLOSING_NAME) != -1)
            oObject.setEnclosureName(iCursor.getString(iCursor.getColumnIndexOrThrow(COLUMN_ENCLOSING_NAME)));
        if (iCursor.getColumnIndexOrThrow(COLUMN_FINISHED) != -1)
            oObject.setFinished(iCursor.getInt(iCursor.getColumnIndexOrThrow(COLUMN_FINISHED)) != 0);
        if (iCursor.getColumnIndexOrThrow(COLUMN_PROGRESS) != -1)
            oObject.setProgress(iCursor.getInt(iCursor.getColumnIndexOrThrow(COLUMN_PROGRESS)));

        return oObject;
    }
}
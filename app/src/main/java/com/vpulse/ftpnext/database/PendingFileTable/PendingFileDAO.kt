package com.vpulse.ftpnext.database.PendingFileTable

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.vpulse.ftpnext.core.ExistingFileAction
import com.vpulse.ftpnext.core.LoadDirection
import com.vpulse.ftpnext.core.LogManager
import com.vpulse.ftpnext.database.ADataAccessObject
import com.vpulse.ftpnext.database.FTPServerTable.FTPServer

class PendingFileDAO constructor(iDataBase: SQLiteDatabase) :
    ADataAccessObject<PendingFile>(iDataBase), IPendingFileSchema {

    init {
        LogManager.info(TAG, "Creating " + this.javaClass.simpleName)
    }

    // TODO : Huge tests to do here.
    // If function bugs, also fix FTPServerDAO.fetchByName()
    fun fetchByServer(iFTPServer: FTPServer): Array<PendingFile> {
        val lSelectionArgs: Array<String> = arrayOf(iFTPServer.dataBaseId.toString())
        val lSelection: String = IPendingFileSchema.Companion.COLUMN_SERVER_ID + " = ?"
        val lObjectList: MutableList<PendingFile> = ArrayList()
        mCursor = super.query(
            IPendingFileSchema.TABLE,
            IPendingFileSchema.COLUMN_ARRAY,
            lSelection,
            lSelectionArgs,
            IPendingFileSchema.COLUMN_DATABASE_ID
        )
        mCursor!!.moveToFirst()
        while (!mCursor!!.isAfterLast) {
            lObjectList.add(cursorToEntity(mCursor!!))
            mCursor!!.moveToNext()
        }
        mCursor!!.close()
        return lObjectList.toTypedArray()
    }

    override fun fetchById(iId: Int): PendingFile? {
        return super.fetchById(
            IPendingFileSchema.Companion.TABLE,
            iId,
            IPendingFileSchema.Companion.COLUMN_DATABASE_ID,
            IPendingFileSchema.Companion.COLUMN_ARRAY
        )
    }

    override fun fetchAll(): List<PendingFile> {
        return super.fetchAll(
            IPendingFileSchema.TABLE,
            IPendingFileSchema.COLUMN_ARRAY,
            IPendingFileSchema.COLUMN_DATABASE_ID
        )
    }

    fun add(iObjects: Array<PendingFile>?): Int {
        if (iObjects.isNullOrEmpty()) {
            LogManager.error(TAG, "No object to add")
            return 0
        }
        var oErrors: Int = 0
        for (lItem: PendingFile in iObjects) {
            oErrors += if ((super.add(lItem, IPendingFileSchema.Companion.TABLE) < 0)) 1 else 0
        }
        return oErrors
    }

    override fun add(iObject: PendingFile): Int {
        return super.add(iObject, IPendingFileSchema.Companion.TABLE)
    }

    override fun update(iObject: PendingFile): Boolean {
        return super.update(
            iObject,
            iObject.dataBaseId,
            IPendingFileSchema.Companion.TABLE,
            IPendingFileSchema.Companion.COLUMN_DATABASE_ID
        )
    }

    override fun deleteAll(): Boolean {
        return super.delete(IPendingFileSchema.Companion.TABLE, null, null) > 0
    }

    override fun delete(iId: Int): Boolean {
        return super.delete(
            iId, IPendingFileSchema.Companion.TABLE, IPendingFileSchema.Companion.COLUMN_DATABASE_ID
        )
    }

    override fun delete(iObject: PendingFile): Boolean {
        return delete(iObject.dataBaseId)
    }

    override fun onUpgradeTable(iOldVersion: Int, iNewVersion: Int) {
        LogManager.info(TAG, "On update table")
        if (iOldVersion < VERSION_ADD_EXISTING_FILE_ACTION) {
            mDataBase.execSQL(
                ("ALTER TABLE " + IPendingFileSchema.Companion.TABLE + " ADD " + IPendingFileSchema.Companion.COLUMN_EXISTING_FILE_ACTION + " INTEGER DEFAULT 0")
            )
        }
        if (iOldVersion < VERSION_UPDATE_PATH_AND_ENCLOSING_COLUMN_NAME) {
            mDataBase.execSQL("DROP TABLE " + IPendingFileSchema.Companion.TABLE)
            mDataBase.execSQL(IPendingFileSchema.Companion.TABLE_CREATE)
        }
        if (iOldVersion < VERSION_REMOVE_COLUMN_STARTED) {
            mDataBase.execSQL("DROP TABLE " + IPendingFileSchema.Companion.TABLE)
            mDataBase.execSQL(IPendingFileSchema.Companion.TABLE_CREATE)
        }
    }

    override fun setContentValue(iObject: PendingFile) {
        mContentValues = ContentValues()
        if (iObject.dataBaseId != 0) {
            mContentValues!!.put(
                IPendingFileSchema.Companion.COLUMN_DATABASE_ID, iObject.dataBaseId
            )
        }
        mContentValues!!.put(IPendingFileSchema.COLUMN_SERVER_ID, iObject.serverId)
        mContentValues!!.put(
            IPendingFileSchema.COLUMN_LOAD_DIRECTION, iObject.loadDirection!!.value
        )
        mContentValues!!.put(IPendingFileSchema.COLUMN_NAME, iObject.name)
        mContentValues!!.put(IPendingFileSchema.COLUMN_REMOTE_PATH, iObject.remotePath)
        mContentValues!!.put(IPendingFileSchema.COLUMN_LOCAL_PATH, iObject.localPath)
        mContentValues!!.put(IPendingFileSchema.COLUMN_FINISHED, iObject.isFinished)
        mContentValues!!.put(IPendingFileSchema.COLUMN_PROGRESS, iObject.progress)
        mContentValues!!.put(
            IPendingFileSchema.COLUMN_EXISTING_FILE_ACTION, iObject.existingFileAction!!.value
        )
    }

    override fun cursorToEntity(iCursor: Cursor): PendingFile {
        val oObject = PendingFile()
        if (iCursor.getColumnIndex(IPendingFileSchema.COLUMN_DATABASE_ID) != -1) {
            oObject.dataBaseId =
                iCursor.getInt(iCursor.getColumnIndexOrThrow(IPendingFileSchema.COLUMN_DATABASE_ID))
        }

        if (iCursor.getColumnIndex(IPendingFileSchema.COLUMN_SERVER_ID) != -1) {
            oObject.setServerId(
                iCursor.getInt(iCursor.getColumnIndexOrThrow(IPendingFileSchema.COLUMN_SERVER_ID))
            )
        }
        if (iCursor.getColumnIndex(IPendingFileSchema.COLUMN_LOAD_DIRECTION) != -1) {
            oObject.setLoadDirection(
                LoadDirection.getValue(
                    iCursor.getColumnIndexOrThrow(IPendingFileSchema.COLUMN_LOAD_DIRECTION)
                )
            )
        }
        if (iCursor.getColumnIndexOrThrow(IPendingFileSchema.COLUMN_NAME) != -1) {
            oObject.setName(
                iCursor.getString(iCursor.getColumnIndexOrThrow(IPendingFileSchema.COLUMN_NAME))
            )
        }
        if (iCursor.getColumnIndexOrThrow(IPendingFileSchema.COLUMN_REMOTE_PATH) != -1) {
            oObject.setRemotePath(
                iCursor.getString(iCursor.getColumnIndexOrThrow(IPendingFileSchema.COLUMN_REMOTE_PATH))
            )
        }
        if (iCursor.getColumnIndexOrThrow(IPendingFileSchema.COLUMN_LOCAL_PATH) != -1) {
            oObject.setLocalPath(
                iCursor.getString(iCursor.getColumnIndexOrThrow(IPendingFileSchema.COLUMN_LOCAL_PATH))
            )
        }
        if (iCursor.getColumnIndexOrThrow(IPendingFileSchema.COLUMN_FINISHED) != -1) {
            oObject.setFinished(
                iCursor.getInt(iCursor.getColumnIndexOrThrow(IPendingFileSchema.COLUMN_FINISHED)) != 0
            )
        }
        if (iCursor.getColumnIndexOrThrow(IPendingFileSchema.COLUMN_PROGRESS) != -1) {
            oObject.setProgress(
                iCursor.getInt(iCursor.getColumnIndexOrThrow(IPendingFileSchema.COLUMN_PROGRESS))
            )
        }
        if (iCursor.getColumnIndexOrThrow(IPendingFileSchema.COLUMN_EXISTING_FILE_ACTION) != -1) {
            oObject.existingFileAction = ExistingFileAction.getValue(
                iCursor.getInt(iCursor.getColumnIndexOrThrow(IPendingFileSchema.COLUMN_EXISTING_FILE_ACTION))
            )

        }
        return oObject
    }

    companion object {

        private val TAG: String = "DATABASE : Pending file DAO"
        private val VERSION_ADD_EXISTING_FILE_ACTION: Int = 2
        private val VERSION_UPDATE_PATH_AND_ENCLOSING_COLUMN_NAME: Int = 3
        private val VERSION_REMOVE_COLUMN_STARTED: Int = 4
    }
}
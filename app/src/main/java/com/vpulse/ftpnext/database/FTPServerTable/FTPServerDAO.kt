package com.vpulse.ftpnext.database.FTPServerTable

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.vpulse.ftpnext.core.FTPCharacterEncoding
import com.vpulse.ftpnext.core.FTPType
import com.vpulse.ftpnext.core.LogManager
import com.vpulse.ftpnext.database.ADataAccessObject

class FTPServerDAO constructor(iDataBase: SQLiteDatabase) : ADataAccessObject<FTPServer>(iDataBase),
    IFTPServerSchema {

    init {
        LogManager.info(TAG, "Creating " + this.javaClass.simpleName)
    }

    // TODO test
    fun fetchByName(iName: String?): FTPServer {
        val lSelectionArgs: Array<String?> = arrayOf(iName)
        val lSelection: String = IFTPServerSchema.COLUMN_NAME + " = ?"
        var lObject: FTPServer? = null
        mCursor = super.query(
            IFTPServerSchema.TABLE,
            IFTPServerSchema.COLUMN_ARRAY,
            lSelection,
            lSelectionArgs,
            IFTPServerSchema.COLUMN_NAME
        )
        if (mCursor != null) {
            mCursor!!.moveToFirst()
            while (!mCursor!!.isAfterLast) {
                lObject = cursorToEntity(mCursor)
                mCursor!!.moveToNext()
            }
            mCursor!!.close()
        }
        return lObject
    }

    override fun fetchById(iId: Int): FTPServer? {
        return super.fetchById(
            IFTPServerSchema.TABLE,
            iId,
            IFTPServerSchema.COLUMN_DATABASE_ID,
            IFTPServerSchema.COLUMN_ARRAY
        )
    }

    override fun fetchAll(): List<FTPServer> {
        return super.fetchAll(
            IFTPServerSchema.TABLE,
            IFTPServerSchema.COLUMN_ARRAY,
            IFTPServerSchema.COLUMN_DATABASE_ID
        )
    }

    override fun add(iObject: FTPServer): Int {
        return super.add(iObject, IFTPServerSchema.Companion.TABLE)
    }

    override fun update(iObject: FTPServer): Boolean {
        return super.update(
            iObject, iObject.dataBaseId, IFTPServerSchema.TABLE, IFTPServerSchema.COLUMN_DATABASE_ID
        )
    }

    override fun deleteAll(): Boolean {
        return super.delete(IFTPServerSchema.Companion.TABLE, null, null) > 0
    }

    override fun delete(iId: Int): Boolean {
        return super.delete(
            iId, IFTPServerSchema.TABLE, IFTPServerSchema.COLUMN_DATABASE_ID
        )
    }

    override fun delete(iObject: FTPServer): Boolean {
        return delete(iObject.dataBaseId)
    }

    override fun onUpgradeTable(iOldVersion: Int, iNewVersion: Int) {
        if (iOldVersion < VERSION_UPDATE_ABSOLUTE_PATH_VALUE) {
            val lFTPServerList: List<FTPServer?> = fetchAll()
            for (lItem: FTPServer? in lFTPServerList) {
                if (!lItem!!.absolutePath!!.endsWith("/")) {
                    lItem.absolutePath = lItem.absolutePath + "/"
                    update(lItem)
                }
            }
        }
    }

    override fun setContentValue(iObject: FTPServer) {
        mContentValues = ContentValues()
        if (iObject.dataBaseId != 0) {
            mContentValues!!.put(
                IFTPServerSchema.Companion.COLUMN_DATABASE_ID, iObject.dataBaseId
            )
        }
        mContentValues!!.put(IFTPServerSchema.COLUMN_NAME, iObject.name)
        mContentValues!!.put(IFTPServerSchema.COLUMN_USER, iObject.user)
        mContentValues!!.put(IFTPServerSchema.COLUMN_PASS, iObject.pass)
        mContentValues!!.put(IFTPServerSchema.COLUMN_SERVER, iObject.server)
        mContentValues!!.put(IFTPServerSchema.COLUMN_PORT, iObject.port)
        mContentValues!!.put(IFTPServerSchema.COLUMN_FOLDER_NAME, iObject.folderName)
        mContentValues!!.put(
            IFTPServerSchema.COLUMN_ABSOLUTE_PATH, iObject.absolutePath
        )
        mContentValues!!.put(
            IFTPServerSchema.COLUMN_CHARACTER_ENCODING, iObject.fTPCharacterEncoding.value
        )
        mContentValues!!.put(
            IFTPServerSchema.COLUMN_TYPE, iObject.fTPType.value
        )
    }

    override fun cursorToEntity(iCursor: Cursor): FTPServer {
        val oObject = FTPServer()
        if (iCursor.getColumnIndex(IFTPServerSchema.COLUMN_DATABASE_ID) != -1) {
            oObject.dataBaseId = iCursor.getInt(
                iCursor.getColumnIndexOrThrow(IFTPServerSchema.COLUMN_DATABASE_ID)
            )
        }
        if (iCursor.getColumnIndex(IFTPServerSchema.COLUMN_NAME) != -1) {
            oObject.name = iCursor.getString(
                iCursor.getColumnIndexOrThrow(IFTPServerSchema.COLUMN_NAME)
            )
        }
        if (iCursor.getColumnIndex(IFTPServerSchema.COLUMN_USER) != -1) {
            oObject.user = iCursor.getString(
                iCursor.getColumnIndexOrThrow(IFTPServerSchema.COLUMN_USER)
            )
        }
        if (iCursor.getColumnIndex(IFTPServerSchema.COLUMN_PASS) != -1) {
            oObject.pass = iCursor.getString(
                iCursor.getColumnIndexOrThrow(IFTPServerSchema.COLUMN_PASS)
            )
        }
        if (iCursor.getColumnIndex(IFTPServerSchema.COLUMN_SERVER) != -1) {
            oObject.server = iCursor.getString(
                iCursor.getColumnIndexOrThrow(IFTPServerSchema.COLUMN_SERVER)
            )
        }
        if (iCursor.getColumnIndex(IFTPServerSchema.COLUMN_PORT) != -1) {
            oObject.port = iCursor.getInt(
                iCursor.getColumnIndexOrThrow(IFTPServerSchema.COLUMN_PORT)
            )
        }
        if (iCursor.getColumnIndex(IFTPServerSchema.COLUMN_FOLDER_NAME) != 1) {
            oObject.folderName = iCursor.getString(
                iCursor.getColumnIndexOrThrow(IFTPServerSchema.COLUMN_FOLDER_NAME)
            )
        }
        if (iCursor.getColumnIndex(IFTPServerSchema.COLUMN_ABSOLUTE_PATH) != -1) {
            oObject.absolutePath = iCursor.getString(
                iCursor.getColumnIndexOrThrow(IFTPServerSchema.COLUMN_ABSOLUTE_PATH)
            )
        }
        if (iCursor.getColumnIndex(IFTPServerSchema.COLUMN_CHARACTER_ENCODING) != -1) {
            oObject.fTPCharacterEncoding = FTPCharacterEncoding.getValue(
                iCursor.getInt(iCursor.getColumnIndexOrThrow(IFTPServerSchema.COLUMN_CHARACTER_ENCODING))
            )

        }
        if (iCursor.getColumnIndex(IFTPServerSchema.COLUMN_TYPE) != -1) {
            oObject.fTPType = FTPType.getValue(
                iCursor.getInt(iCursor.getColumnIndexOrThrow(IFTPServerSchema.COLUMN_TYPE))
            )

        }
        return oObject
    }

    companion object {
        private const val TAG: String = "DATABASE : FTP Host DAO"
        private const val VERSION_UPDATE_ABSOLUTE_PATH_VALUE: Int = 3
    }
}
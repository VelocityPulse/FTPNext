package com.vpulse.ftpnext.commons

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.io.File

object FileUtils {
    private const val PRIMARY_VOLUME_NAME = "primary"
    var TAG = "TAG"

    ////////////////////////// SECTION 1
    fun getPathFromFolderUri(iTreeUri: Uri?, iCon: Context): String? {
        if (iTreeUri == null) return null
        var oVolumePath = getVolumePath(
            getVolumeIdFromTreeUri(
                iTreeUri
            ), iCon
        ) ?: return File.separator
        if (oVolumePath.endsWith(File.separator)) oVolumePath =
            oVolumePath.substring(0, oVolumePath.length - 1)
        var lDocumentPath = getDocumentPathFromTreeUri(iTreeUri)
        if (lDocumentPath!!.endsWith(File.separator)) lDocumentPath =
            lDocumentPath.substring(0, lDocumentPath.length - 1)
        return if (lDocumentPath.isNotEmpty()) {
            if (lDocumentPath.startsWith(File.separator)) oVolumePath + lDocumentPath else oVolumePath + File.separator + lDocumentPath
        } else oVolumePath
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun getVolumePath(iVolumeId: String?, iContext: Context): String? {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) null else try {
            val mStorageManager =
                iContext.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            val lStorageVolumeClazz = Class.forName("android.os.storage.StorageVolume")
            val lGetVolumeList = mStorageManager.javaClass.getMethod("getVolumeList")
            val lGetUuid = lStorageVolumeClazz.getMethod("getUuid")
            val lGetPath = lStorageVolumeClazz.getMethod("getPath")
            val lIsPrimary = lStorageVolumeClazz.getMethod("isPrimary")
            val lResult = lGetVolumeList.invoke(mStorageManager)
            val lIength = java.lang.reflect.Array.getLength(lResult)
            for (lI in 0 until lIength) {
                val lStorageVolumeElement = java.lang.reflect.Array.get(lResult, lI)
                val lUUID = lGetUuid.invoke(lStorageVolumeElement) as String
                val lPrimary = lIsPrimary.invoke(lStorageVolumeElement) as Boolean

                // primary volume?
                if (lPrimary && PRIMARY_VOLUME_NAME == iVolumeId) return lGetPath.invoke(
                    lStorageVolumeElement
                ) as String

                // other volumes?
                if (lUUID != null && lUUID == iVolumeId) return lGetPath.invoke(
                    lStorageVolumeElement
                ) as String
            } // not found.
            null
        } catch (iEx: Exception) {
            null
        }
    }

    private fun getVolumeIdFromTreeUri(iTreeUri: Uri): String? {
        val lDocId = DocumentsContract.getTreeDocumentId(iTreeUri)
        val lSplit = lDocId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        return if (lSplit.size > 0) lSplit[0] else null
    }

    private fun getDocumentPathFromTreeUri(iTreeUri: Uri): String? {
        val lDocId = DocumentsContract.getTreeDocumentId(iTreeUri)
        val lSplit: Array<String?> =
            lDocId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        return if (lSplit.size >= 2 && lSplit[1] != null) lSplit[1] else File.separator
    }

    ////////////////////////// SECTION 2
    /*
     * Gets the file path of the given Uri.
     */
    @SuppressLint("NewApi", "ObsoleteSdkInt")
    fun getPathFromDocumentUri(iContext: Context, iUri: Uri): String? {
        var iUri = iUri
        val lNeedToCheckUri = Build.VERSION.SDK_INT >= 19
        var lSelection: String? = null
        var lSelectionArgs: Array<String>? =
            null // Uri is different in versions after KITKAT (Android 4.4), we need to
        // deal with different Uris.
        if (lNeedToCheckUri && DocumentsContract.isDocumentUri(iContext.applicationContext, iUri)) {
            if (isExternalStorageDocument(iUri)) {
                val lDocId = DocumentsContract.getDocumentId(iUri)
                val lSplit =
                    lDocId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                return Environment.getExternalStorageDirectory().toString() + "/" + lSplit[1]
            } else if (isDownloadsDocument(iUri)) {
                val lId = DocumentsContract.getDocumentId(iUri)
                iUri = ContentUris.withAppendedId(
                    Uri.parse("content://downloads/public_downloads"), lId.toLong()
                )
            } else if (isMediaDocument(iUri)) {
                val lDocId = DocumentsContract.getDocumentId(iUri)
                val lSplit =
                    lDocId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                val lType = lSplit[0]
                if ("image" == lType) {
                    iUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                } else if ("video" == lType) {
                    iUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else if ("audio" == lType) {
                    iUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }
                lSelection = "_id=?"
                lSelectionArgs = arrayOf(lSplit[1])
            }
        }
        if ("content".equals(iUri.scheme, ignoreCase = true)) {
            val lProjection = arrayOf(MediaStore.Images.Media.DATA)
            val lCursor: Cursor?
            try {
                lCursor = iContext.contentResolver.query(
                    iUri, lProjection, lSelection, lSelectionArgs, null
                )
                val lColumnIndex = lCursor!!.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                if (lCursor.moveToFirst()) {
                    return lCursor.getString(lColumnIndex)
                }
            } catch (e: Exception) {
            }
        } else if ("file".equals(iUri.scheme, ignoreCase = true)) {
            return iUri.path
        }
        return null
    }

    /**
     * @param iUri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    fun isExternalStorageDocument(iUri: Uri): Boolean {
        return "com.android.externalstorage.documents" == iUri.authority
    }

    /**
     * @param iUri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    fun isDownloadsDocument(iUri: Uri): Boolean {
        return "com.android.providers.downloads.documents" == iUri.authority
    }

    /**
     * @param iUri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    fun isMediaDocument(iUri: Uri): Boolean {
        return "com.android.providers.media.documents" == iUri.authority
    }
}
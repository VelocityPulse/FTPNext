package com.vpulse.ftpnext.commons;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.provider.DocumentsContract;
import android.provider.MediaStore;

import androidx.annotation.Nullable;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Method;

public final class FileUtils {
    private static final String PRIMARY_VOLUME_NAME = "primary";
    static String TAG = "TAG";

    ////////////////////////// SECTION 1

    @Nullable
    public static String getPathFromFolderUri(@Nullable final Uri iTreeUri, Context iCon) {
        if (iTreeUri == null)
            return null;

        String oVolumePath = getVolumePath(getVolumeIdFromTreeUri(iTreeUri), iCon);
        if (oVolumePath == null) return File.separator;
        if (oVolumePath.endsWith(File.separator))
            oVolumePath = oVolumePath.substring(0, oVolumePath.length() - 1);

        String lDocumentPath = getDocumentPathFromTreeUri(iTreeUri);
        if (lDocumentPath.endsWith(File.separator))
            lDocumentPath = lDocumentPath.substring(0, lDocumentPath.length() - 1);

        if (lDocumentPath.length() > 0) {
            if (lDocumentPath.startsWith(File.separator))
                return oVolumePath + lDocumentPath;
            else
                return oVolumePath + File.separator + lDocumentPath;
        } else
            return oVolumePath;
    }

    @SuppressLint("ObsoleteSdkInt")
    private static String getVolumePath(final String iVolumeId, Context iContext) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
            return null;
        try {
            StorageManager mStorageManager =
                    (StorageManager) iContext.getSystemService(Context.STORAGE_SERVICE);
            Class<?> lStorageVolumeClazz = Class.forName("android.os.storage.StorageVolume");
            Method lGetVolumeList = mStorageManager.getClass().getMethod("getVolumeList");
            Method lGetUuid = lStorageVolumeClazz.getMethod("getUuid");
            Method lGetPath = lStorageVolumeClazz.getMethod("getPath");
            Method lIsPrimary = lStorageVolumeClazz.getMethod("isPrimary");
            Object lResult = lGetVolumeList.invoke(mStorageManager);

            final int lIength = Array.getLength(lResult);
            for (int lI = 0; lI < lIength; lI++) {
                Object lStorageVolumeElement = Array.get(lResult, lI);
                String lUUID = (String) lGetUuid.invoke(lStorageVolumeElement);
                Boolean lPrimary = (Boolean) lIsPrimary.invoke(lStorageVolumeElement);

                // primary volume?
                if (lPrimary && PRIMARY_VOLUME_NAME.equals(iVolumeId))
                    return (String) lGetPath.invoke(lStorageVolumeElement);

                // other volumes?
                if (lUUID != null && lUUID.equals(iVolumeId))
                    return (String) lGetPath.invoke(lStorageVolumeElement);
            }
            // not found.
            return null;
        } catch (Exception iEx) {
            return null;
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private static String getVolumeIdFromTreeUri(final Uri iTreeUri) {
        final String lDocId = DocumentsContract.getTreeDocumentId(iTreeUri);
        final String[] lSplit = lDocId.split(":");
        if (lSplit.length > 0)
            return lSplit[0];
        else return null;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private static String getDocumentPathFromTreeUri(final Uri iTreeUri) {
        final String lDocId = DocumentsContract.getTreeDocumentId(iTreeUri);
        final String[] lSplit = lDocId.split(":");
        if ((lSplit.length >= 2) && (lSplit[1] != null))
            return lSplit[1];
        else return File.separator;
    }

    ////////////////////////// SECTION 2

    /*
     * Gets the file path of the given Uri.
     */
    @SuppressLint({"NewApi", "ObsoleteSdkInt"})
    public static String getPathFromDocumentUri(Context context, Uri uri) {
        final boolean needToCheckUri = Build.VERSION.SDK_INT >= 19;
        String selection = null;
        String[] selectionArgs = null;
        // Uri is different in versions after KITKAT (Android 4.4), we need to
        // deal with different Uris.
        if (needToCheckUri && DocumentsContract.isDocumentUri(context.getApplicationContext(), uri)) {
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                return Environment.getExternalStorageDirectory() + "/" + split[1];
            } else if (isDownloadsDocument(uri)) {
                final String id = DocumentsContract.getDocumentId(uri);
                uri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), Long.parseLong(id));
            } else if (isMediaDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];
                if ("image".equals(type)) {
                    uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }
                selection = "_id=?";
                selectionArgs = new String[]{split[1]};
            }
        }
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            String[] projection = {MediaStore.Images.Media.DATA};
            Cursor cursor;
            try {
                cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null);
                int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                if (cursor.moveToFirst()) {
                    return cursor.getString(column_index);
                }
            } catch (Exception e) {
            }
        } else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }
        return null;
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    public static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

}

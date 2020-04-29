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
    public static String getPathFromDocumentUri(Context iContext, Uri iUri) {
        final boolean lNeedToCheckUri = Build.VERSION.SDK_INT >= 19;
        String lSelection = null;
        String[] lSelectionArgs = null;
        // Uri is different in versions after KITKAT (Android 4.4), we need to
        // deal with different Uris.
        if (lNeedToCheckUri && DocumentsContract.isDocumentUri(iContext.getApplicationContext(), iUri)) {
            if (isExternalStorageDocument(iUri)) {
                final String lDocId = DocumentsContract.getDocumentId(iUri);
                final String[] lSplit = lDocId.split(":");
                return Environment.getExternalStorageDirectory() + "/" + lSplit[1];
            } else if (isDownloadsDocument(iUri)) {
                final String lId = DocumentsContract.getDocumentId(iUri);
                iUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), Long.parseLong(lId));
            } else if (isMediaDocument(iUri)) {
                final String lDocId = DocumentsContract.getDocumentId(iUri);
                final String[] lSplit = lDocId.split(":");
                final String lType = lSplit[0];
                if ("image".equals(lType)) {
                    iUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(lType)) {
                    iUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(lType)) {
                    iUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }
                lSelection = "_id=?";
                lSelectionArgs = new String[]{lSplit[1]};
            }
        }
        if ("content".equalsIgnoreCase(iUri.getScheme())) {
            String[] lProjection = {MediaStore.Images.Media.DATA};
            Cursor lCursor;
            try {
                lCursor = iContext.getContentResolver().query(iUri, lProjection, lSelection, lSelectionArgs, null);
                int lColumnIndex = lCursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                if (lCursor.moveToFirst()) {
                    return lCursor.getString(lColumnIndex);
                }
            } catch (Exception e) {
            }
        } else if ("file".equalsIgnoreCase(iUri.getScheme())) {
            return iUri.getPath();
        }
        return null;
    }

    /**
     * @param iUri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    public static boolean isExternalStorageDocument(Uri iUri) {
        return "com.android.externalstorage.documents".equals(iUri.getAuthority());
    }

    /**
     * @param iUri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    public static boolean isDownloadsDocument(Uri iUri) {
        return "com.android.providers.downloads.documents".equals(iUri.getAuthority());
    }

    /**
     * @param iUri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    public static boolean isMediaDocument(Uri iUri) {
        return "com.android.providers.media.documents".equals(iUri.getAuthority());
    }

}

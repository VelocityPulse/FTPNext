package com.vpulse.ftpnext.commons;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.storage.StorageManager;
import android.provider.DocumentsContract;
import androidx.annotation.Nullable;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Method;

public final class FileUtils {
    private static final String PRIMARY_VOLUME_NAME = "primary";
    static String TAG = "TAG";

    @Nullable
    public static String getFullPathFromTreeUri(@Nullable final Uri iTreeUri, Context iCon) {
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
}

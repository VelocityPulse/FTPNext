package com.vpulse.ftpnext.commons;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import com.vpulse.ftpnext.R;

import java.util.Locale;

public class Utils {

    public static void sleep(long iMillis) {
        try {
            Thread.sleep(iMillis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static String getRealPathFromURI(Context iContext, Uri contentURI) {
        String oResult;
        Cursor lCursor = iContext.getContentResolver().query(contentURI, null, null, null, null);
        if (lCursor == null) {
            oResult = contentURI.getPath();
        } else {
            lCursor.moveToFirst();
            int idx = lCursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
            oResult = lCursor.getString(idx);
            lCursor.close();
        }
        return oResult;
    }

    public static void hideKeyboard(Activity iActivity) {
        InputMethodManager lImm = (InputMethodManager) iActivity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        //Find the currently focused view, so we can grab the correct window token from it.
        View lView = iActivity.getCurrentFocus();
        //If no view currently has focus, create a new one, just so we can grab a window token from it
        if (lView == null)
            lView = new View(iActivity);
        lImm.hideSoftInputFromWindow(lView.getWindowToken(), 0);
    }

    public static void hideKeyboard(Activity iActivity, View iView) {
        InputMethodManager lImm = (InputMethodManager) iActivity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        //Find the currently focused view, so we can grab the correct window token from it.
        if (iView == null)
            iView = new View(iActivity);
        lImm.hideSoftInputFromWindow(iView.getWindowToken(), 0);
    }

    public static boolean isNullOrEmpty(String iStr) {
        return iStr == null || iStr.trim().isEmpty();
    }

    public static AlertDialog createErrorAlertDialog(Context iContext, String iMessage) {
        return new AlertDialog.Builder(iContext)
                .setTitle("Error") // TODO string
                .setMessage(iMessage)
                .setPositiveButton("Ok", null)
                .create();
    }

    public static void dismissAlertDialogOnUIThread(Activity iActivity, final AlertDialog iAlertDialog) {
        iActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                iAlertDialog.dismiss();
            }
        });
    }

    public static void cancelAlertDialogOnUIThread(Activity iActivity, final AlertDialog iAlertDialog) {
        iActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                iAlertDialog.cancel();
            }
        });
    }

    /**
     * *                               SI     BINARY
     * *
     * *                   0:        0 B        0 B
     * *                   27:       27 B       27 B
     * *                  999:      999 B      999 B
     * *                 1000:     1.0 kB     1000 B
     * *                 1023:     1.0 kB     1023 B
     * *                 1024:     1.0 kB    1.0 KiB
     * *                 1728:     1.7 kB    1.7 KiB
     * *               110592:   110.6 kB  108.0 KiB
     * *              7077888:     7.1 MB    6.8 MiB
     * *            452984832:   453.0 MB  432.0 MiB
     * *          28991029248:    29.0 GB   27.0 GiB
     * *        1855425871872:     1.9 TB    1.7 TiB
     * *  9223372036854775807:     9.2 EB    8.0 EiB   (Long.MAX_VALUE)
     */
    public static String humanReadableByteCount(long iBytes, boolean iSi) {
        int lUnit = iSi ? 1000 : 1024;
        if (iBytes < lUnit)
            return iBytes + " B";
        int lExp = (int) (Math.log(iBytes) / Math.log(lUnit));
        String lPre = (iSi ? "kMGTPE" : "KMGTPE").charAt(lExp - 1) + (iSi ? "" : "i");
        return String.format(Locale.FRANCE, "%.1f %sB", iBytes / Math.pow(lUnit, lExp), lPre);
    }

    public static ProgressDialog initProgressDialog(Context iContext, final ProgressDialog.OnClickListener iOnClickCancelListener) {
        final ProgressDialog lProgressDialog;
        lProgressDialog = new ProgressDialog(iContext);
        lProgressDialog.setContentView(R.layout.loading_icon);
        lProgressDialog.setCancelable(true);
        lProgressDialog.setCanceledOnTouchOutside(false);
        lProgressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel", iOnClickCancelListener);
        return lProgressDialog;
    }
}

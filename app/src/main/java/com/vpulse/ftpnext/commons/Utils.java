package com.vpulse.ftpnext.commons;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.util.TypedValue;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.vpulse.ftpnext.R;

import java.io.File;
import java.util.Locale;

public class Utils {

    private static final String TAG = "UTILS";

    public static void sleep(long iMillis) {
        try {
            Thread.sleep(iMillis);
        } catch (InterruptedException iE) {
            iE.printStackTrace();
        }
    }

    public static String getFileNameFromPath(String lPath) {
        return lPath.substring(lPath.lastIndexOf(File.separator) + 1);
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

    public static void showKeyboard(Activity iActivity, View iView) {
        iView.requestFocus();
        InputMethodManager imm = (InputMethodManager) iActivity.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(iView, InputMethodManager.SHOW_IMPLICIT);
    }

    public static boolean isNullOrEmpty(String iStr) {
        return iStr == null || iStr.trim().isEmpty();
    }

    public static AlertDialog createErrorAlertDialog(Context iContext, String iMessage) {
        return createAlertDialog(iContext, "Error", iMessage,
                "Ok", null);
    }

    public static AlertDialog createErrorAlertDialog(
            Context iContext, String iMessage,
            String iPositiveButton, AlertDialog.OnClickListener iPositiveListener) {

        return createAlertDialog(iContext, "Error", iMessage,
                iPositiveButton, iPositiveListener);
    }

    public static AlertDialog createSuccessAlertDialog(Context iContext, String iMessage) {
        return createAlertDialog(iContext, "Success", iMessage,
                "Ok", null);
    }

    public static AlertDialog createSuccessAlertDialog(
            Context iContext, String iMessage,
            String iPositiveButton, AlertDialog.OnClickListener iPositiveListener) {

        return createAlertDialog(iContext, "Success", iMessage,
                iPositiveButton, iPositiveListener);
    }

    public static AlertDialog createAlertDialog(
            Context iContext,
            String iTitle, String iMessage,
            String iPositiveButton, AlertDialog.OnClickListener iPositiveListener,
            String iNegativeButton, AlertDialog.OnClickListener iNegativeListener) {

        AlertDialog.Builder lBuilder = new AlertDialog.Builder(iContext);
        lBuilder.setTitle(iTitle);
        lBuilder.setMessage(iMessage);
        lBuilder.setCancelable(false);
        lBuilder.setPositiveButton(iPositiveButton, iPositiveListener);
        lBuilder.setNegativeButton(iNegativeButton, iNegativeListener);

        return lBuilder.create();
    }

    public static AlertDialog createAlertDialog(
            Context iContext,
            String iTitle, String iMessage,
            String iPositiveButton, AlertDialog.OnClickListener iPositiveListener) {

        AlertDialog.Builder lBuilder = new AlertDialog.Builder(iContext);
        lBuilder.setTitle(iTitle);
        lBuilder.setMessage(iMessage);
        lBuilder.setCancelable(false);
        lBuilder.setPositiveButton(iPositiveButton, iPositiveListener);

        return lBuilder.create();
    }

    public static void dismissAlertDialogOnUIThread(Activity iActivity, final DialogInterface iAlertDialog) {
        iActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                iAlertDialog.dismiss();
            }
        });
    }

    public static void cancelAlertDialogOnUIThread(Activity iActivity, final DialogInterface iAlertDialog) {
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

    public static AlertDialog createProgressDialog(Context iContext, final AlertDialog.OnClickListener iOnClickCancelListener) {
        final AlertDialog.Builder lProgressDialog;
        lProgressDialog = new AlertDialog.Builder(iContext);
        lProgressDialog.setView(R.layout.loading_icon);
        lProgressDialog.setCancelable(false);
        lProgressDialog.setNegativeButton("Cancel", iOnClickCancelListener);

        return lProgressDialog.create();
    }

    public static AlertDialog createProgressDialogNoButton(Context iContext) {
        final AlertDialog.Builder lProgressDialog;
        lProgressDialog = new AlertDialog.Builder(iContext);
        lProgressDialog.setView(R.layout.loading_icon);
        lProgressDialog.setCancelable(false);

        return lProgressDialog.create();
    }

    public static AlertDialog createProgressDialog(Context iContext,
                                                   String iTitle,
                                                   final AlertDialog.OnClickListener iOnClickCancelListener) {
        final AlertDialog.Builder lProgressDialog;
        lProgressDialog = new AlertDialog.Builder(iContext);
        lProgressDialog.setView(R.layout.loading_icon);
        lProgressDialog.setTitle(iTitle);
        lProgressDialog.setCancelable(false);
        lProgressDialog.setNegativeButton("Cancel", iOnClickCancelListener);

        return lProgressDialog.create();
    }

    public static AlertDialog createProgressDialog(Context iContext,
                                                   String iTitle,
                                                   String iButtonText,
                                                   final AlertDialog.OnClickListener iOnClickCancelListener) {
        final AlertDialog.Builder lProgressDialog;
        lProgressDialog = new AlertDialog.Builder(iContext);
        lProgressDialog.setView(R.layout.loading_icon);
        lProgressDialog.setTitle(iTitle);
        lProgressDialog.setCancelable(false);
        lProgressDialog.setNegativeButton(iButtonText, iOnClickCancelListener);

        return lProgressDialog.create();
    }

    public static void showToast(final Activity iActivity, final String iToast) {
        iActivity.runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(iActivity, iToast, Toast.LENGTH_SHORT).show();
            }
        });
    }

    public static void showLongToast(final Activity iActivity, final String iToast) {
        iActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(iActivity, iToast, Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * @param iActivity The current activity
     * @param iRes      must be an attribute like R.attr.colorAccent
     * @return The color
     */
    public static int fetchCurrentThemeColor(Activity iActivity, int iRes) {
        final TypedValue value = new TypedValue();
        iActivity.getTheme().resolveAttribute(iRes, value, true);
        return value.data;
    }
}
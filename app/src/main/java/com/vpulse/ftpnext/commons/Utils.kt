package com.vpulse.ftpnext.commons

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.util.TypedValue
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import com.vpulse.ftpnext.R
import java.io.File
import java.util.*

object Utils {
    private const val TAG = "UTILS"
    fun sleep(iMillis: Long) {
        try {
            Thread.sleep(iMillis)
        } catch (iE: InterruptedException) {
            iE.printStackTrace()
        }
    }

    fun getFileNameFromPath(lPath: String): String {
        return lPath.substring(lPath.lastIndexOf(File.separator) + 1)
    }

    fun hideKeyboard(iActivity: Activity) {
        val lImm = iActivity.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        //Find the currently focused view, so we can grab the correct window token from it.
        var lView = iActivity.currentFocus
        //If no view currently has focus, create a new one, just so we can grab a window token from it
        if (lView == null) lView = View(iActivity)
        lImm.hideSoftInputFromWindow(lView!!.windowToken, 0)
    }

    fun hideKeyboard(iActivity: Activity, iView: View?) {
        var iView = iView
        val lImm = iActivity.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        //Find the currently focused view, so we can grab the correct window token from it.
        if (iView == null) iView = View(iActivity)
        lImm.hideSoftInputFromWindow(iView.windowToken, 0)
    }

    fun showKeyboard(iActivity: Activity, iView: View?) {
        iView!!.requestFocus()
        val imm = iActivity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(iView, InputMethodManager.SHOW_IMPLICIT)
    }

    fun isNullOrEmpty(iStr: String?): Boolean {
        return iStr == null || iStr.trim { it <= ' ' }.isEmpty()
    }

    fun createErrorAlertDialog(iContext: Context?, iMessage: String?): AlertDialog {
        return createAlertDialog(
            iContext, "Error", iMessage,
            "Ok", null
        )
    }

    fun createErrorAlertDialog(
        iContext: Context?, iMessage: String?,
        iPositiveButton: String?, iPositiveListener: DialogInterface.OnClickListener?
    ): AlertDialog {
        return createAlertDialog(
            iContext, "Error", iMessage,
            iPositiveButton, iPositiveListener
        )
    }

    fun createSuccessAlertDialog(iContext: Context?, iMessage: String?): AlertDialog {
        return createAlertDialog(
            iContext, "Success", iMessage,
            "Ok", null
        )
    }

    fun createSuccessAlertDialog(
        iContext: Context?, iMessage: String?,
        iPositiveButton: String?, iPositiveListener: DialogInterface.OnClickListener?
    ): AlertDialog {
        return createAlertDialog(
            iContext, "Success", iMessage,
            iPositiveButton, iPositiveListener
        )
    }

    fun createAlertDialog(
        iContext: Context?,
        iTitle: String?, iMessage: String?,
        iPositiveButton: String?, iPositiveListener: DialogInterface.OnClickListener?,
        iNegativeButton: String?, iNegativeListener: DialogInterface.OnClickListener?
    ): AlertDialog {
        val lBuilder = AlertDialog.Builder(
            iContext!!
        )
        lBuilder.setTitle(iTitle)
        lBuilder.setMessage(iMessage)
        lBuilder.setCancelable(false)
        lBuilder.setPositiveButton(iPositiveButton, iPositiveListener)
        lBuilder.setNegativeButton(iNegativeButton, iNegativeListener)
        return lBuilder.create()
    }

    fun createAlertDialog(
        iContext: Context?,
        iTitle: String?, iMessage: String?,
        iPositiveButton: String?, iPositiveListener: DialogInterface.OnClickListener?
    ): AlertDialog {
        val lBuilder = AlertDialog.Builder(
            iContext!!
        )
        lBuilder.setTitle(iTitle)
        lBuilder.setMessage(iMessage)
        lBuilder.setCancelable(false)
        lBuilder.setPositiveButton(iPositiveButton, iPositiveListener)
        return lBuilder.create()
    }

    fun dismissAlertDialogOnUIThread(iActivity: Activity, iAlertDialog: DialogInterface) {
        iActivity.runOnUiThread { iAlertDialog.dismiss() }
    }

    fun cancelAlertDialogOnUIThread(iActivity: Activity, iAlertDialog: DialogInterface?) {
        iActivity.runOnUiThread { iAlertDialog!!.cancel() }
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
    fun humanReadableByteCount(iBytes: Long, iSi: Boolean): String {
        val lUnit = if (iSi) 1000 else 1024
        if (iBytes < lUnit) return "$iBytes B"
        val lExp = (Math.log(iBytes.toDouble()) / Math.log(lUnit.toDouble())).toInt()
        val lPre = (if (iSi) "kMGTPE" else "KMGTPE")[lExp - 1].toString() + if (iSi) "" else "i"
        return String.format(
            Locale.FRANCE,
            "%.1f %sB",
            iBytes / Math.pow(lUnit.toDouble(), lExp.toDouble()),
            lPre
        )
    }

    fun createProgressDialog(
        iContext: Context?,
        iOnClickCancelListener: DialogInterface.OnClickListener?
    ): AlertDialog {
        val lProgressDialog: AlertDialog.Builder
        lProgressDialog = AlertDialog.Builder(iContext!!)
        lProgressDialog.setView(R.layout.loading_icon)
        lProgressDialog.setCancelable(false)
        lProgressDialog.setNegativeButton("Cancel", iOnClickCancelListener)
        return lProgressDialog.create()
    }

    fun createProgressDialogNoButton(iContext: Context?): AlertDialog {
        val lProgressDialog: AlertDialog.Builder
        lProgressDialog = AlertDialog.Builder(iContext!!)
        lProgressDialog.setView(R.layout.loading_icon)
        lProgressDialog.setCancelable(false)
        return lProgressDialog.create()
    }

    fun createProgressDialog(
        iContext: Context?,
        iTitle: String?,
        iOnClickCancelListener: DialogInterface.OnClickListener?
    ): AlertDialog {
        val lProgressDialog: AlertDialog.Builder
        lProgressDialog = AlertDialog.Builder(iContext!!)
        lProgressDialog.setView(R.layout.loading_icon)
        lProgressDialog.setTitle(iTitle)
        lProgressDialog.setCancelable(false)
        lProgressDialog.setNegativeButton("Cancel", iOnClickCancelListener)
        return lProgressDialog.create()
    }

    fun createProgressDialog(
        iContext: Context?,
        iTitle: String?,
        iButtonText: String?,
        iOnClickCancelListener: DialogInterface.OnClickListener?
    ): AlertDialog {
        val lProgressDialog: AlertDialog.Builder
        lProgressDialog = AlertDialog.Builder(iContext!!)
        lProgressDialog.setView(R.layout.loading_icon)
        lProgressDialog.setTitle(iTitle)
        lProgressDialog.setCancelable(false)
        lProgressDialog.setNegativeButton(iButtonText, iOnClickCancelListener)
        return lProgressDialog.create()
    }

    fun showToast(iActivity: Activity, iToast: String?) {
        iActivity.runOnUiThread { Toast.makeText(iActivity, iToast, Toast.LENGTH_SHORT).show() }
    }

    fun showLongToast(iActivity: Activity, iToast: String?) {
        iActivity.runOnUiThread { Toast.makeText(iActivity, iToast, Toast.LENGTH_LONG).show() }
    }

    /**
     * @param iActivity The current activity
     * @param iRes      must be an attribute like R.attr.colorAccent
     * @return The color
     */
    fun fetchCurrentThemeColor(iActivity: Activity?, iRes: Int): Int {
        val value = TypedValue()
        iActivity!!.theme.resolveAttribute(iRes, value, true)
        return value.data
    }
}
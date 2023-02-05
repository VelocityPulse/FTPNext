package com.vpulse.ftpnext.ftpservices

import android.annotation.SuppressLint
import android.content.*
import android.content.res.Resources.Theme
import android.util.TypedValue
import com.vpulse.ftpnext.R
import com.vpulse.ftpnext.core.*

object FTPLogManager {
    val TYPE_SUCCESS: String = "Success" // TODO : Strings... ?
    val TYPE_ERROR: String = "Error"
    val TYPE_CODE_REPLY: String = "Code reply"
    val TYPE_STATUS: String = "Status"
    private val TAG: String = "FTP LOG MANAGER"
    private var mSuccessColorString: String? = null
    private var mErrorColorString: String? = null
    private var mReplyColorString: String? = null
    private var mStatusColorString: String? = null
    private var mOnNewFTPLogList: MutableList<OnNewFTPLog>? = null
    private var mOnNewFTPLogColoredList: MutableList<OnNewFTPLogColored>? = null

    @SuppressLint("ResourceType")
    fun init(iContext: Context) {
        mOnNewFTPLogList = ArrayList()
        mOnNewFTPLogColoredList = ArrayList()
        notifyThemeChanged(iContext)
    }

    fun notifyThemeChanged(iContext: Context) {
        val lTypedValue: TypedValue = TypedValue()
        val lTheme: Theme = iContext.getTheme()
        lTheme.resolveAttribute(R.attr.logSuccessColor, lTypedValue, true)
        mSuccessColorString = Integer.toHexString(lTypedValue.data).substring(2, 8)
        lTheme.resolveAttribute(R.attr.logErrorColor, lTypedValue, true)
        mErrorColorString = Integer.toHexString(lTypedValue.data).substring(2, 8)
        lTheme.resolveAttribute(R.attr.logReplyColor, lTypedValue, true)
        mReplyColorString = Integer.toHexString(lTypedValue.data).substring(2, 8)
        lTheme.resolveAttribute(R.attr.logStatusColor, lTypedValue, true)
        mStatusColorString = Integer.toHexString(lTypedValue.data).substring(2, 8)
        mSuccessColorString = "#" + mSuccessColorString
        mErrorColorString = "#" + mErrorColorString
        mReplyColorString = "#" + mReplyColorString
        mStatusColorString = "#" + mStatusColorString
    }

    fun subscribeOnNewFTPLog(iOnNewFTPLog: OnNewFTPLog?) {
        if (iOnNewFTPLog == null) {
            LogManager.error(TAG, "Subscribe on new FTP log parameter null")
            return
        }
        mOnNewFTPLogList!!.add(iOnNewFTPLog)
    }

    fun unsubscribeOnNewFTPLog(iOnNewFTPLog: OnNewFTPLog) {
        mOnNewFTPLogList!!.remove(iOnNewFTPLog)
    }

    fun subscribeOnNewFTPLogColored(iOnNewFTPLogColored: OnNewFTPLogColored?) {
        if (iOnNewFTPLogColored == null) {
            LogManager.error(TAG, "Subscribe on new FTP log colored null")
            return
        }
        mOnNewFTPLogColoredList!!.add(iOnNewFTPLogColored)
    }

    fun unsubscribeOnNewFTPLogColored(iOnNewFTPLogColored: OnNewFTPLogColored) {
        mOnNewFTPLogColoredList!!.remove(iOnNewFTPLogColored)
    }

    fun pushSuccessLog(iLog: String) {
        var iLog: String = iLog
        if (mOnNewFTPLogList == null) {
            LogManager.error(TAG, "mOnNewFTPLogList null")
            return
        }
        iLog = TYPE_SUCCESS + ": " + iLog
        fireNewFTPLog(iLog)
        fireNewFTPLogColored(iLog)
    }

    fun pushErrorLog(iLog: String) {
        var iLog: String = iLog
        if (mOnNewFTPLogList == null) {
            LogManager.error(TAG, "mOnNewFTPLogList null")
            return
        }
        iLog = TYPE_ERROR + ": " + iLog
        fireNewFTPLog(iLog)
        fireNewFTPLogColored(iLog)
    }

    fun pushCodeReplyLog(iCodeReply: Int) {
        if (mOnNewFTPLogList == null) {
            LogManager.error(TAG, "mOnNewFTPLogList null")
            return
        } // TODO : reply code info ?
        val lLog: String = TYPE_CODE_REPLY + ": " + iCodeReply
        fireNewFTPLog(lLog)
        fireNewFTPLogColored(lLog)
    }

    fun pushStatusLog(iLog: String) {
        var iLog: String = iLog
        if (mOnNewFTPLogList == null) {
            LogManager.error(TAG, "mOnNewFTPLogList null")
            return
        }
        iLog = TYPE_STATUS + ": " + iLog
        fireNewFTPLog(iLog)
        fireNewFTPLogColored(iLog)
    }

    private fun fireNewFTPLog(iLog: String) {
        for (lCallback: OnNewFTPLog in mOnNewFTPLogList!!) lCallback.onNewFTPLog(iLog)
    }

    private fun fireNewFTPLogColored(iLog: String) {
        var iLog: String = iLog
        if (iLog.startsWith(TYPE_SUCCESS)) iLog =
            "<font color='" + mSuccessColorString + "'>" + iLog + "</font>" else if (iLog.startsWith(
                TYPE_ERROR
            )) iLog =
            "<font color='" + mErrorColorString + "'>" + iLog + "</font>" else if (iLog.startsWith(
                TYPE_CODE_REPLY
            )) iLog =
            "<font color='" + mReplyColorString + "'>" + iLog + "</font>" else if (iLog.startsWith(
                TYPE_STATUS
            )) iLog = "<font color='" + mStatusColorString + "'>" + iLog + "</font>"
        for (lCallback: OnNewFTPLogColored in mOnNewFTPLogColoredList!!) lCallback.onNewFTPLogColored(
            iLog
        )
    }

    open interface OnNewFTPLog {
        fun onNewFTPLog(iLog: String?)
    }

    open interface OnNewFTPLogColored {
        fun onNewFTPLogColored(iLog: String?)
    }
}
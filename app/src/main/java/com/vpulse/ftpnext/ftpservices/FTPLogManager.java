package com.vpulse.ftpnext.ftpservices;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.util.TypedValue;

import com.vpulse.ftpnext.R;
import com.vpulse.ftpnext.core.LogManager;

import java.util.ArrayList;
import java.util.List;

public class FTPLogManager {

    public static final String TYPE_SUCCESS = "Success"; // TODO : Strings... ?
    public static final String TYPE_ERROR = "Error";
    public static final String TYPE_CODE_REPLY = "Code reply";
    public static final String TYPE_STATUS = "Status";

    private static final String TAG = "FTP LOG MANAGER";

    private static String mSuccessColorString;
    private static String mErrorColorString;
    private static String mReplyColorString;
    private static String mStatusColorString;

    private static List<OnNewFTPLog> mOnNewFTPLogList;
    private static List<OnNewFTPLogColored> mOnNewFTPLogColoredList;

    private FTPLogManager() {
    }

    @SuppressLint("ResourceType")
    public static void init(Context iContext) {
        mOnNewFTPLogList = new ArrayList<>();
        mOnNewFTPLogColoredList = new ArrayList<>();

        notifyThemeChanged(iContext);
    }

    public static void notifyThemeChanged(Context iContext) {
        TypedValue lTypedValue = new TypedValue();
        Resources.Theme lTheme = iContext.getTheme();

        lTheme.resolveAttribute(R.attr.logSuccessColor, lTypedValue, true);
        mSuccessColorString = Integer.toHexString(lTypedValue.data).substring(2, 8);

        lTheme.resolveAttribute(R.attr.logErrorColor, lTypedValue, true);
        mErrorColorString = Integer.toHexString(lTypedValue.data).substring(2, 8);

        lTheme.resolveAttribute(R.attr.logReplyColor, lTypedValue, true);
        mReplyColorString = Integer.toHexString(lTypedValue.data).substring(2, 8);

        lTheme.resolveAttribute(R.attr.logStatusColor, lTypedValue, true);
        mStatusColorString = Integer.toHexString(lTypedValue.data).substring(2, 8);

        mSuccessColorString = "#" + mSuccessColorString;
        mErrorColorString = "#" + mErrorColorString;
        mReplyColorString = "#" + mReplyColorString;
        mStatusColorString = "#" + mStatusColorString;
    }

    public static void subscribeOnNewFTPLog(OnNewFTPLog iOnNewFTPLog) {
        if (iOnNewFTPLog == null) {
            LogManager.error(TAG, "Subscribe on new FTP log parameter null");
            return;
        }
        mOnNewFTPLogList.add(iOnNewFTPLog);
    }

    public static void unsubscribeOnNewFTPLog(OnNewFTPLog iOnNewFTPLog) {
        mOnNewFTPLogList.remove(iOnNewFTPLog);
    }

    public static void subscribeOnNewFTPLogColored(OnNewFTPLogColored iOnNewFTPLogColored) {
        if (iOnNewFTPLogColored == null) {
            LogManager.error(TAG, "Subscribe on new FTP log colored null");
            return;
        }
        mOnNewFTPLogColoredList.add(iOnNewFTPLogColored);
    }

    public static void unsubscribeOnNewFTPLogColored(OnNewFTPLogColored iOnNewFTPLogColored) {
        mOnNewFTPLogColoredList.remove(iOnNewFTPLogColored);
    }

    public static void pushSuccessLog(String iLog) {
        if (mOnNewFTPLogList == null) {
            LogManager.error(TAG, "mOnNewFTPLogList null");
            return;
        }
        iLog = TYPE_SUCCESS + ": " + iLog;
        fireNewFTPLog(iLog);
        fireNewFTPLogColored(iLog);
    }

    public static void pushErrorLog(String iLog) {
        if (mOnNewFTPLogList == null) {
            LogManager.error(TAG, "mOnNewFTPLogList null");
            return;
        }
        iLog = TYPE_ERROR + ": " + iLog;
        fireNewFTPLog(iLog);
        fireNewFTPLogColored(iLog);
    }

    public static void pushCodeReplyLog(int iCodeReply) {
        if (mOnNewFTPLogList == null) {
            LogManager.error(TAG, "mOnNewFTPLogList null");
            return;
        }
        // TODO : reply code info ?
        String lLog = TYPE_CODE_REPLY + ": " + iCodeReply;
        fireNewFTPLog(lLog);
        fireNewFTPLogColored(lLog);
    }

    public static void pushStatusLog(String iLog) {
        if (mOnNewFTPLogList == null) {
            LogManager.error(TAG, "mOnNewFTPLogList null");
            return;
        }
        iLog = TYPE_STATUS + ": " + iLog;
        fireNewFTPLog(iLog);
        fireNewFTPLogColored(iLog);
    }

    private static void fireNewFTPLog(String iLog) {
        for (OnNewFTPLog lCallback : mOnNewFTPLogList)
            lCallback.onNewFTPLog(iLog);
    }

    private static void fireNewFTPLogColored(String iLog) {

        if (iLog.startsWith(TYPE_SUCCESS))
            iLog = "<font color='" + mSuccessColorString + "'>" + iLog + "</font>";
        else if (iLog.startsWith(TYPE_ERROR))
            iLog = "<font color='" + mErrorColorString + "'>" + iLog + "</font>";
        else if (iLog.startsWith(TYPE_CODE_REPLY))
            iLog = "<font color='" + mReplyColorString + "'>" + iLog + "</font>";
        else if (iLog.startsWith(TYPE_STATUS))
            iLog = "<font color='" + mStatusColorString + "'>" + iLog + "</font>";

        for (OnNewFTPLogColored lCallback : mOnNewFTPLogColoredList)
            lCallback.onNewFTPLogColored(iLog);
    }

    public interface OnNewFTPLog {
        void onNewFTPLog(String iLog);
    }

    public interface OnNewFTPLogColored {
        void onNewFTPLogColored(String iLog);
    }
}

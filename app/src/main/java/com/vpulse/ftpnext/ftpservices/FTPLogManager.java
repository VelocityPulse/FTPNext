package com.vpulse.ftpnext.ftpservices;

import android.annotation.SuppressLint;
import android.content.Context;

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

    private static FTPLogManager sSingleton;

    private static String mSuccessColorString;
    private static String mErrorColorString;
    private static String mCodeReplyColorString;
    private static String mStatusColorString;

    private List<OnNewFTPLog> mOnNewFTPLogList;
    private List<OnNewFTPLogColored> mOnNewFTPLogColoredList;

    private FTPLogManager() {
        mOnNewFTPLogList = new ArrayList<>();
        mOnNewFTPLogColoredList = new ArrayList<>();
    }

    public static FTPLogManager getInstance() {
        if (sSingleton != null)
            return sSingleton;
        return (sSingleton = new FTPLogManager());
    }

    @SuppressLint("ResourceType")
    public static void init(Context iContext) {
        getInstance();

        mSuccessColorString = iContext.getResources().getString(R.color.log_success).substring(3, 9);
        mErrorColorString = iContext.getResources().getString(R.color.log_error).substring(3, 9);
        mCodeReplyColorString = iContext.getResources().getString(R.color.log_code_reply).substring(3, 9);
        mStatusColorString = iContext.getResources().getString(R.color.log_status).substring(3, 9);

        mSuccessColorString = "#" + mSuccessColorString;
        mErrorColorString = "#" + mErrorColorString;
        mCodeReplyColorString = "#" + mCodeReplyColorString;
        mStatusColorString = "#" + mStatusColorString;
    }

    public void subscribeOnNewFTPLog(OnNewFTPLog iOnNewFTPLog) {
        if (iOnNewFTPLog == null) {
            LogManager.error(TAG, "Subscribe on new FTP log parameter null");
            return;
        }
        mOnNewFTPLogList.add(iOnNewFTPLog);
    }

    public void unsubscribeOnNewFTPLog(OnNewFTPLog iOnNewFTPLog) {
        mOnNewFTPLogList.remove(iOnNewFTPLog);
    }

    public void subscribeOnNewFTPLogColored(OnNewFTPLogColored iOnNewFTPLogColored) {
        if (iOnNewFTPLogColored == null) {
            LogManager.error(TAG, "Subscribe on new FTP log colored null");
            return;
        }
        mOnNewFTPLogColoredList.add(iOnNewFTPLogColored);
    }

    public void unsubscribeOnNewFTPLogColored(OnNewFTPLogColored iOnNewFTPLogColored) {
        mOnNewFTPLogColoredList.remove(iOnNewFTPLogColored);
    }

    public void pushSuccessLog(String iLog) {
        if (mOnNewFTPLogList == null) {
            LogManager.error(TAG, "mOnNewFTPLogList null");
            return;
        }
        iLog = TYPE_SUCCESS + ": " + iLog;
        fireNewFTPLog(iLog);
        fireNewFTPLogColored(iLog);
    }

    public void pushErrorLog(String iLog) {
        if (mOnNewFTPLogList == null) {
            LogManager.error(TAG, "mOnNewFTPLogList null");
            return;
        }
        iLog = TYPE_ERROR + ": " + iLog;
        fireNewFTPLog(iLog);
        fireNewFTPLogColored(iLog);
    }

    public void pushCodeReplyLog(int iCodeReply) {
        if (mOnNewFTPLogList == null) {
            LogManager.error(TAG, "mOnNewFTPLogList null");
            return;
        }
        // TODO : reply code info ?
        String lLog = TYPE_CODE_REPLY + ": " + iCodeReply;
        fireNewFTPLog(lLog);
        fireNewFTPLogColored(lLog);
    }

    public void pushStatusLog(String iLog) {
        if (mOnNewFTPLogList == null) {
            LogManager.error(TAG, "mOnNewFTPLogList null");
            return;
        }
        iLog = TYPE_STATUS + ": " + iLog;
        fireNewFTPLog(iLog);
        fireNewFTPLogColored(iLog);
    }

    private void fireNewFTPLog(String iLog) {
        LogManager.info(TAG, "Fire new log");
        for (OnNewFTPLog lCallback : mOnNewFTPLogList)
            lCallback.onNewFTPLog(iLog);
    }

    private void fireNewFTPLogColored(String iLog) {
        LogManager.info(TAG, "Fire new colored log");

        if (iLog.startsWith(TYPE_SUCCESS))
            iLog = "<font color='" + mSuccessColorString + "'>" + iLog + "</font>";
        else if (iLog.startsWith(TYPE_ERROR))
            iLog = "<font color='" + mErrorColorString + "'>" + iLog + "</font>";
        else if (iLog.startsWith(TYPE_CODE_REPLY))
            iLog = "<font color='" + mCodeReplyColorString + "'>" + iLog + "</font>";
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

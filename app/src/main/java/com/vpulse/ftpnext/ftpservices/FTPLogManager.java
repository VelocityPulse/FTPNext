package com.vpulse.ftpnext.ftpservices;

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

    private List<OnNewFTPLog> mOnNewFTPLogList;

    private FTPLogManager() {
        mOnNewFTPLogList = new ArrayList<>();
    }

    public static FTPLogManager getInstance() {
        if (sSingleton != null)
            return sSingleton;
        return (sSingleton = new FTPLogManager());
    }

    public static void init() {
        getInstance();
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

    public void pushSuccessLog(String iLog) {
        if (mOnNewFTPLogList == null) {
            LogManager.error(TAG, "mOnNewFTPLogList null");
            return;
        }
        iLog = TYPE_SUCCESS + ": " + iLog;
        fireNewFTPLog(iLog);
    }

    public void pushErrorLog(String iLog) {
        if (mOnNewFTPLogList == null) {
            LogManager.error(TAG, "mOnNewFTPLogList null");
            return;
        }
        iLog = TYPE_ERROR + ": " + iLog;
        fireNewFTPLog(iLog);
    }

    public void pushCodeReplyLog(int iCodeReply) {
        if (mOnNewFTPLogList == null) {
            LogManager.error(TAG, "mOnNewFTPLogList null");
            return;
        }
        // TODO : reply code info ?
        String lLog = TYPE_SUCCESS + ": " + iCodeReply;
        fireNewFTPLog(lLog);
    }

    public void pushStatusLog(String iLog) {
        if (mOnNewFTPLogList == null) {
            LogManager.error(TAG, "mOnNewFTPLogList null");
            return;
        }
        iLog = TYPE_STATUS + ": " + iLog;
        fireNewFTPLog(iLog);
    }

    private void fireNewFTPLog(String iLog) {
        LogManager.info(TAG, "Fire new log");
        for (OnNewFTPLog lCallback : mOnNewFTPLogList)
            lCallback.onNewFTPLog(iLog);
    }

    public interface OnNewFTPLog {
        void onNewFTPLog(String iLog);
    }
}

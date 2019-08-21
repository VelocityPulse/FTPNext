package com.example.ftpnext;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.example.ftpnext.commons.Utils;
import com.example.ftpnext.database.DataBase;
import com.example.ftpnext.database.FTPServerTable.FTPServer;
import com.example.ftpnext.database.FTPServerTable.FTPServerDAO;

public class FTPNavigationActivity extends AppCompatActivity {
    private static String TAG = "FTP NAVIGATION ACTIVITY";

    public static final int ACTIVITY_REQUEST_CODE = 1;
    public static final int NO_DATABASE_ID = -1;
    public static final String KEY_DATABASE_ID = "KEY_DATABASE_ID";

    private FTPServer mFTPServer;
    private FTPConnection mFTPConnection;
    private FTPServerDAO mFTPServerDAO;

    @Override
    protected void onCreate(Bundle iSavedInstanceState) {
        super.onCreate(iSavedInstanceState);
        setContentView(R.layout.activity_ftp_navigation);

        mFTPServerDAO = DataBase.getFTPServerDAO();

        Bundle lBundle = this.getIntent().getExtras();
        int lServerId = lBundle.getInt(KEY_DATABASE_ID);
        if (lServerId != NO_DATABASE_ID) {
            mFTPServer = mFTPServerDAO.fetchById(lServerId);
        }

        mFTPServer = mFTPServerDAO.fetchById(lServerId);
        if (mFTPServer == null) {
            Utils.createErrorAlertDialog(this, "Navigation page has failed...").show();
            return;
        }
        mFTPConnection = FTPConnection.getFTPConnection(lServerId);

        mFTPConnection.getFolders();


    }

    //TODO : on destroy or onback, disconnect the server
}

package com.example.ftpnext;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;

import com.example.ftpnext.adapters.NavigationRecyclerViewAdapter;
import com.example.ftpnext.commons.Utils;
import com.example.ftpnext.database.DataBase;
import com.example.ftpnext.database.FTPServerTable.FTPServer;
import com.example.ftpnext.database.FTPServerTable.FTPServerDAO;

import org.apache.commons.net.ftp.FTPFile;

public class FTPNavigationActivity extends AppCompatActivity {
    public static final int ACTIVITY_REQUEST_CODE = 1;
    public static final int NO_DATABASE_ID = -1;
    public static final String KEY_DATABASE_ID = "KEY_DATABASE_ID";
    private static String TAG = "FTP NAVIGATION ACTIVITY";
    private FTPServer mFTPServer;
    private FTPConnection mFTPConnection;
    private FTPServerDAO mFTPServerDAO;
    private NavigationRecyclerViewAdapter mAdapter;

    @Override
    protected void onCreate(Bundle iSavedInstanceState) {
        super.onCreate(iSavedInstanceState);
        setContentView(R.layout.activity_ftp_navigation);

        initializeGUI();
        initializeAdapter();
        initialize();
    }

    private void initializeGUI() {
        Toolbar lToolBar = findViewById(R.id.toolbar);
        setSupportActionBar(lToolBar);

        // TODO add a loading popup
        // TODO add a floating button
    }

    private void initializeAdapter() {
        RecyclerView lRecyclerView = findViewById(R.id.navigation_recycler_view);
        lRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new NavigationRecyclerViewAdapter(lRecyclerView, this);
        lRecyclerView.setAdapter(mAdapter);

        DividerItemDecoration mDividerItemDecoration = new DividerItemDecoration(this, DividerItemDecoration.VERTICAL);
        lRecyclerView.addItemDecoration(mDividerItemDecoration);
    }

    private void initialize() {
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

        mFTPConnection.fetchDirectoryContent("/", new FTPConnection.OnFetchDirectoryResult() {
            @Override
            public void onSuccess(final FTPFile[] iFTPFiles) {
                // TODO hide the alert popup
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mAdapter.setData(iFTPFiles);
                    }
                });
            }

            @Override
            public void onFail(String iErrorMessage) {
                Utils.createErrorAlertDialog(FTPNavigationActivity.this, "Navigation page has failed...").show();
            }
        });

    }


    //TODO : on last destroy, disconnect
    //TODO : on disconnection, destroy all
}

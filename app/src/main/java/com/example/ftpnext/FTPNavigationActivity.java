package com.example.ftpnext;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

public class FTPNavigationActivity extends AppCompatActivity {
    public static final int ACTIVITY_REQUEST_CODE = 1;

    @Override
    protected void onCreate(Bundle iSavedInstanceState) {
        super.onCreate(iSavedInstanceState);

        setContentView(R.layout.activity_ftp_navigation);
    }

}

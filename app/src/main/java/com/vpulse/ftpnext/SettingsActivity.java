package com.vpulse.ftpnext;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.vpulse.ftpnext.commons.Utils;
import com.vpulse.ftpnext.core.AppConstants;
import com.vpulse.ftpnext.core.AppCore;
import com.vpulse.ftpnext.core.ExistingFileAction;
import com.vpulse.ftpnext.core.PreferenceManager;

public class SettingsActivity extends AppCompatActivity {

    private final static String TAG = "SETTINGS ACTIVITY";

    private final static int ACTIVITY_REQUEST_CODE_EXISTING_FILE = 10;

    private TextView mMinimumDownloadTextView;
    private TextView mMaximumDownloadTextView;
    private TextView mDownloadValueTextView;

    private TextView mExistingFileTextView;

    private SeekBar mDownloadSeekBar;
    private Switch mWifiOnlySwitch;
    private Switch mDarkThemeSwitch;

    private ViewGroup mExistingFileLayout;
    private ViewGroup mWifiOnlyLayout;
    private ViewGroup mDarkThemeLayout;
    private boolean mIsAskingWifiOnlySecurity;

    @Override
    protected void onCreate(@Nullable Bundle iSavedInstanceState) {
        super.onCreate(iSavedInstanceState);
        setTheme(AppCore.getAppTheme());
        setContentView(R.layout.activity_settings);

        initializeGUI();
        initializeViews();
        initializeViewsListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();

        initializeViewsValue();
    }

    private void initializeGUI() {
        setSupportActionBar((Toolbar) findViewById(R.id.settings_toolbar));
        getSupportActionBar().setDisplayShowTitleEnabled(false);
    }

    private void initializeViews() {
        // Find by view ID
        mMinimumDownloadTextView = findViewById(R.id.settings_text_min_download);
        mMaximumDownloadTextView = findViewById(R.id.settings_text_max_download);
        mDownloadValueTextView = findViewById(R.id.settings_download_text_value);
        mDownloadSeekBar = findViewById(R.id.settings_seek_bar);

        mExistingFileTextView = findViewById(R.id.settings_existing_file_text);

        mWifiOnlySwitch = findViewById(R.id.settings_wifi_only_switch);
        mDarkThemeSwitch = findViewById(R.id.settings_dark_theme_switch);

        mExistingFileLayout = findViewById(R.id.settings_existing_file_layout);
        mWifiOnlyLayout = findViewById(R.id.settings_wifi_only_layout);
        mDarkThemeLayout = findViewById(R.id.settings_dark_theme_layout);
    }

    private void initializeViewsValue() {
        mWifiOnlySwitch.setChecked(PreferenceManager.isWifiOnly());
        mDarkThemeSwitch.setChecked(PreferenceManager.isDarkTheme());

        mMinimumDownloadTextView.setText(String.valueOf(AppConstants.MINIMUM_SIMULTANEOUS_DOWNLOAD));
        mMaximumDownloadTextView.setText(String.valueOf(AppConstants.MAXIMAL_SIMULTANEOUS_DOWNLOAD));
        mDownloadValueTextView.setText(String.valueOf(PreferenceManager.getMaxTransfers()));
        mDownloadSeekBar.setProgress(PreferenceManager.getMaxTransfers() - 1);
        mDownloadSeekBar.setMax(AppConstants.MAXIMAL_SIMULTANEOUS_DOWNLOAD - 1);

        mExistingFileTextView.setText(
                ExistingFileAction.getTextResourceId(PreferenceManager.getExistingFileAction()));
    }

    private void initializeViewsListeners() {
        mWifiOnlyLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View iV) {
                if (mIsAskingWifiOnlySecurity)
                    return;
                mWifiOnlySwitch.setChecked(!mWifiOnlySwitch.isChecked());
                if (!mWifiOnlySwitch.isChecked()) {
                    showWifiOnlySecurityDialog();
                } else
                    PreferenceManager.setWifiOnly(true);
            }
        });

        mExistingFileLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View iV) {
                startActivityChooseExistingFile();
            }
        });

        mDarkThemeLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mDarkThemeSwitch.setChecked(!mDarkThemeSwitch.isChecked());
                Utils.createAlertDialog(
                        SettingsActivity.this,
                        "Restart required",
                        "The application needs to restart to apply the new theme",
                        "Restart",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                PreferenceManager.setDarkTheme(mDarkThemeSwitch.isChecked());
                                restartActivity();
                            }
                        },
                        "Cancel",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                mDarkThemeSwitch.setChecked(!mDarkThemeSwitch.isChecked());
                            }
                        })
                        .show();
            }
        });

        mDownloadSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar iSeekBar, int iProgress, boolean iFromUser) {
                mDownloadValueTextView.setText(String.valueOf(iProgress + 1));
                PreferenceManager.setMaxTransfers(iProgress + 1);
            }

            @Override
            public void onStartTrackingTouch(SeekBar iSeekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar iSeekBar) {
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return false;
    }

    private void showWifiOnlySecurityDialog() {
        mIsAskingWifiOnlySecurity = true;

        AlertDialog.Builder lBuilder = new AlertDialog.Builder(this)
                .setTitle("Disable Wi-Fi only") // TODO : Strings
                .setMessage("Warning : That might cause additional costs")
                .setPositiveButton("Confirm", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface iDialog, int iWhich) {
                        iDialog.dismiss();
                        PreferenceManager.setWifiOnly(false);
                    }
                })
                .setNegativeButton("cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface iDialog, int iWhich) {
                        mIsAskingWifiOnlySecurity = false;
                        PreferenceManager.setWifiOnly(true);
                        mWifiOnlySwitch.setChecked(true);
                    }
                });

        lBuilder.create().show();
    }

    private void startActivityChooseExistingFile() {
        Intent lIntent = new Intent(SettingsActivity.this, ExistingFileActionActivity.class);

        startActivityForResult(lIntent, ACTIVITY_REQUEST_CODE_EXISTING_FILE);
    }

    private void restartActivity() {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                SettingsActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        finish();
                        Intent i = getBaseContext().getPackageManager().
                                getLaunchIntentForPackage(getBaseContext().getPackageName());
                        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(i);
                    }
                });
            }
        }, 200);
    }
}
package com.vpulse.ftpnext;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.view.MenuItem;
import android.view.View;
import android.widget.RadioButton;

import com.vpulse.ftpnext.core.PreferenceManager;

import static com.vpulse.ftpnext.core.ExistingFileAction.IGNORE;
import static com.vpulse.ftpnext.core.ExistingFileAction.NOT_DEFINED;
import static com.vpulse.ftpnext.core.ExistingFileAction.RENAME_FILE;
import static com.vpulse.ftpnext.core.ExistingFileAction.REPLACE_FILE;
import static com.vpulse.ftpnext.core.ExistingFileAction.REPLACE_IF_FILE_IS_MORE_RECENT;
import static com.vpulse.ftpnext.core.ExistingFileAction.REPLACE_IF_SIZE_IS_DIFFERENT;
import static com.vpulse.ftpnext.core.ExistingFileAction.REPLACE_IF_SIZE_IS_DIFFERENT_OR_FILE_IS_MORE_RECENT;
import static com.vpulse.ftpnext.core.ExistingFileAction.RESUME_FILE_TRANSFER;

public class ExistingFileActionActivity extends AppCompatActivity {

    private RadioButton mAskEachTimeRadioButton;
    private RadioButton mReplaceFileRadioButton;
    private RadioButton mResumeDownloadRadioButton;
    private RadioButton mSizesDiffRadioButton;
    private RadioButton mIgnoreRadioButton;
    private RadioButton mMoreRecentRadioButton;
    private RadioButton mSizesDiffOrMoreRecentRadioButton;
    private RadioButton mRenameRadioButton;

    @Override
    public void onCreate(@Nullable Bundle iSavedInstanceState) {
        super.onCreate(iSavedInstanceState);

        setContentView(R.layout.activity_existing_file_action);

        initializeGUI();
        initializeViews();
    }

    @Override
    protected void onResume() {
        super.onResume();
        initializeViewValues();
    }

    private void initializeGUI() {
        getSupportActionBar().setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setCustomView(R.layout.action_bar_existing_file);
    }

    private void initializeViews() {
        mAskEachTimeRadioButton = findViewById(R.id.existing_file_radio_ask_each_time);
        mReplaceFileRadioButton = findViewById(R.id.existing_file_radio_replace_file);
        mResumeDownloadRadioButton = findViewById(R.id.existing_file_radio_resume);
        mSizesDiffRadioButton = findViewById(R.id.existing_file_radio_sizes_diff);
        mIgnoreRadioButton = findViewById(R.id.existing_file_radio_ignore);
        mMoreRecentRadioButton = findViewById(R.id.existing_file_radio_more_recent);
        mSizesDiffOrMoreRecentRadioButton = findViewById(R.id.existing_file_radio_sizes_diff_or_more_recent);
        mRenameRadioButton = findViewById(R.id.existing_file_radio_rename);
    }

    private void initializeViewValues() {
        switch (PreferenceManager.getExistingFileAction()) {
            case NOT_DEFINED:
                mAskEachTimeRadioButton.setChecked(true);
                break;
            case REPLACE_FILE:
                mReplaceFileRadioButton.setChecked(true);
                break;
            case REPLACE_IF_FILE_IS_MORE_RECENT:
                mMoreRecentRadioButton.setChecked(true);
                break;
            case REPLACE_IF_SIZE_IS_DIFFERENT:
                mSizesDiffRadioButton.setChecked(true);
                break;
            case REPLACE_IF_SIZE_IS_DIFFERENT_OR_FILE_IS_MORE_RECENT:
                mSizesDiffOrMoreRecentRadioButton.setChecked(true);
                break;
            case RESUME_FILE_TRANSFER:
                mResumeDownloadRadioButton.setChecked(true);
                break;
            case RENAME_FILE:
                mRenameRadioButton.setChecked(true);
                break;
            case IGNORE:
                mIgnoreRadioButton.setChecked(true);
                break;
        }
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

    private void resetAllRadioButtons() {
        mAskEachTimeRadioButton.setChecked(false);
        mReplaceFileRadioButton.setChecked(false);
        mResumeDownloadRadioButton.setChecked(false);
        mSizesDiffRadioButton.setChecked(false);
        mIgnoreRadioButton.setChecked(false);
        mMoreRecentRadioButton.setChecked(false);
        mSizesDiffOrMoreRecentRadioButton.setChecked(false);
        mRenameRadioButton.setChecked(false);
    }

    public void onClickAskEachTime(View view) {
        resetAllRadioButtons();
        mAskEachTimeRadioButton.setChecked(true);
        PreferenceManager.setExistingFileAction(NOT_DEFINED);
    }


    public void onClickReplaceFile(View view) {
        resetAllRadioButtons();
        mReplaceFileRadioButton.setChecked(true);
        PreferenceManager.setExistingFileAction(REPLACE_FILE);
    }

    public void onClickResumeDownload(View view) {
        resetAllRadioButtons();
        mResumeDownloadRadioButton.setChecked(true);
        PreferenceManager.setExistingFileAction(RESUME_FILE_TRANSFER);
    }

    public void onClickSizesDiff(View view) {
        resetAllRadioButtons();
        mSizesDiffRadioButton.setChecked(true);
        PreferenceManager.setExistingFileAction(REPLACE_IF_SIZE_IS_DIFFERENT);
    }

    public void onClickIgnore(View view) {
        resetAllRadioButtons();
        mIgnoreRadioButton.setChecked(true);
        PreferenceManager.setExistingFileAction(IGNORE);
    }

    public void onClickMoreRecent(View view) {
        resetAllRadioButtons();
        mMoreRecentRadioButton.setChecked(true);
        PreferenceManager.setExistingFileAction(REPLACE_IF_FILE_IS_MORE_RECENT);
    }

    public void onClickSizesDiffOrMoreRecent(View view) {
        resetAllRadioButtons();
        mSizesDiffOrMoreRecentRadioButton.setChecked(true);
        PreferenceManager.setExistingFileAction(REPLACE_IF_SIZE_IS_DIFFERENT_OR_FILE_IS_MORE_RECENT);
    }

    public void onClickRename(View view) {
        resetAllRadioButtons();
        mRenameRadioButton.setChecked(true);
        PreferenceManager.setExistingFileAction(RENAME_FILE);
    }
}

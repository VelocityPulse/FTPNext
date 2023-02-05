package com.vpulse.ftpnext

import android.os.Bundle
import android.view.*
import android.widget.RadioButton
import androidx.appcompat.app.AppCompatActivity
import com.vpulse.ftpnext.core.*

class ExistingFileActionActivity : AppCompatActivity() {
    private var mAskEachTimeRadioButton: RadioButton? = null
    private var mReplaceFileRadioButton: RadioButton? = null
    private var mResumeDownloadRadioButton: RadioButton? = null
    private var mSizesDiffRadioButton: RadioButton? = null
    private var mIgnoreRadioButton: RadioButton? = null
    private var mMoreRecentRadioButton: RadioButton? = null
    private var mSizesDiffOrMoreRecentRadioButton: RadioButton? = null
    private var mRenameRadioButton: RadioButton? = null
    public override fun onCreate(iSavedInstanceState: Bundle?) {
        super.onCreate(iSavedInstanceState)
        setTheme(AppCore.appTheme)
        setContentView(R.layout.activity_existing_file_action)
        initializeGUI()
        initializeViews()
    }

    override fun onResume() {
        super.onResume()
        initializeViewValues()
    }

    private fun initializeGUI() {
        setSupportActionBar(findViewById(R.id.existing_file_toolbar))
        supportActionBar!!.setDisplayShowTitleEnabled(false)
    }

    private fun initializeViews() {
        mAskEachTimeRadioButton = findViewById(R.id.existing_file_radio_ask_each_time)
        mReplaceFileRadioButton = findViewById(R.id.existing_file_radio_replace_file)
        mResumeDownloadRadioButton = findViewById(R.id.existing_file_radio_resume)
        mSizesDiffRadioButton = findViewById(R.id.existing_file_radio_sizes_diff)
        mIgnoreRadioButton = findViewById(R.id.existing_file_radio_ignore)
        mMoreRecentRadioButton = findViewById(R.id.existing_file_radio_more_recent)
        mSizesDiffOrMoreRecentRadioButton =
            findViewById(R.id.existing_file_radio_sizes_diff_or_more_recent)
        mRenameRadioButton = findViewById(R.id.existing_file_radio_rename)
    }

    private fun initializeViewValues() {
        when (PreferenceManager.existingFileAction) {
            ExistingFileAction.NOT_DEFINED -> mAskEachTimeRadioButton!!.isChecked = true
            ExistingFileAction.REPLACE_FILE -> mReplaceFileRadioButton!!.isChecked = true
            ExistingFileAction.REPLACE_IF_FILE_IS_MORE_RECENT -> mMoreRecentRadioButton!!.isChecked =
                true
            ExistingFileAction.REPLACE_IF_SIZE_IS_DIFFERENT -> mSizesDiffRadioButton!!.isChecked =
                true
            ExistingFileAction.REPLACE_IF_SIZE_IS_DIFFERENT_OR_FILE_IS_MORE_RECENT -> mSizesDiffOrMoreRecentRadioButton!!.isChecked =
                true
            ExistingFileAction.RESUME_FILE_TRANSFER -> mResumeDownloadRadioButton!!.isChecked = true
            ExistingFileAction.RENAME_FILE -> mRenameRadioButton!!.isChecked = true
            ExistingFileAction.IGNORE -> mIgnoreRadioButton!!.isChecked = true
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
        }
        return false
    }

    private fun resetAllRadioButtons() {
        mAskEachTimeRadioButton!!.isChecked = false
        mReplaceFileRadioButton!!.isChecked = false
        mResumeDownloadRadioButton!!.isChecked = false
        mSizesDiffRadioButton!!.isChecked = false
        mIgnoreRadioButton!!.isChecked = false
        mMoreRecentRadioButton!!.isChecked = false
        mSizesDiffOrMoreRecentRadioButton!!.isChecked = false
        mRenameRadioButton!!.isChecked = false
    }

    fun onClickAskEachTime() {
        resetAllRadioButtons()
        mAskEachTimeRadioButton!!.isChecked = true
        PreferenceManager.existingFileAction = ExistingFileAction.NOT_DEFINED
    }

    fun onClickReplaceFile() {
        resetAllRadioButtons()
        mReplaceFileRadioButton!!.isChecked = true
        PreferenceManager.existingFileAction = ExistingFileAction.REPLACE_FILE
    }

    fun onClickResumeDownload(view: View?) {
        resetAllRadioButtons()
        mResumeDownloadRadioButton!!.isChecked = true
        PreferenceManager.existingFileAction = ExistingFileAction.RESUME_FILE_TRANSFER
    }

    fun onClickSizesDiff() {
        resetAllRadioButtons()
        mSizesDiffRadioButton!!.isChecked = true
        PreferenceManager.existingFileAction = ExistingFileAction.REPLACE_IF_SIZE_IS_DIFFERENT
    }

    fun onClickIgnore() {
        resetAllRadioButtons()
        mIgnoreRadioButton!!.isChecked = true
        PreferenceManager.existingFileAction = ExistingFileAction.IGNORE
    }

    fun onClickMoreRecent() {
        resetAllRadioButtons()
        mMoreRecentRadioButton!!.isChecked = true
        PreferenceManager.existingFileAction = ExistingFileAction.REPLACE_IF_FILE_IS_MORE_RECENT
    }

    fun onClickSizesDiffOrMoreRecent() {
        resetAllRadioButtons()
        mSizesDiffOrMoreRecentRadioButton!!.isChecked = true
        PreferenceManager.existingFileAction = ExistingFileAction.REPLACE_IF_SIZE_IS_DIFFERENT_OR_FILE_IS_MORE_RECENT
    }

    fun onClickRename() {
        resetAllRadioButtons()
        mRenameRadioButton!!.isChecked = true
        PreferenceManager.existingFileAction = ExistingFileAction.RENAME_FILE
    }
}
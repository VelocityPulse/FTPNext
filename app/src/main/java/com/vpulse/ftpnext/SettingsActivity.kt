package com.vpulse.ftpnext

import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.vpulse.ftpnext.commons.Utils
import com.vpulse.ftpnext.core.AppConstants
import com.vpulse.ftpnext.core.AppCore
import com.vpulse.ftpnext.core.ExistingFileAction
import com.vpulse.ftpnext.core.PreferenceManager

class SettingsActivity : AppCompatActivity() {
    private var mMinimumDownloadTextView: TextView? = null
    private var mMaximumDownloadTextView: TextView? = null
    private var mDownloadValueTextView: TextView? = null
    private var mExistingFileTextView: TextView? = null
    private var mDownloadSeekBar: SeekBar? = null
    private var mWifiOnlySwitch: Switch? = null
    private var mDarkThemeSwitch: Switch? = null
    private var mExistingFileLayout: ViewGroup? = null
    private var mWifiOnlyLayout: ViewGroup? = null
    private var mDarkThemeLayout: ViewGroup? = null
    private var mIsAskingWifiOnlySecurity: Boolean = false
    override fun onCreate(iSavedInstanceState: Bundle?) {
        super.onCreate(iSavedInstanceState)
        setTheme(AppCore.appTheme)
        setContentView(R.layout.activity_settings)
        initializeGUI()
        initializeViews()
        initializeViewsListeners()
    }

    override fun onResume() {
        super.onResume()
        initializeViewsValue()
    }

    private fun initializeGUI() {
        setSupportActionBar(findViewById<View>(R.id.settings_toolbar) as Toolbar?)
        supportActionBar!!.setDisplayShowTitleEnabled(false)
    }

    private fun initializeViews() { // Find by view ID
        mMinimumDownloadTextView = findViewById(R.id.settings_text_min_download)
        mMaximumDownloadTextView = findViewById(R.id.settings_text_max_download)
        mDownloadValueTextView = findViewById(R.id.settings_download_text_value)
        mDownloadSeekBar = findViewById(R.id.settings_seek_bar)
        mExistingFileTextView = findViewById(R.id.settings_existing_file_text)
        mWifiOnlySwitch = findViewById(R.id.settings_wifi_only_switch)
        mDarkThemeSwitch = findViewById(R.id.settings_dark_theme_switch)
        mExistingFileLayout = findViewById(R.id.settings_existing_file_layout)
        mWifiOnlyLayout = findViewById(R.id.settings_wifi_only_layout)
        mDarkThemeLayout = findViewById(R.id.settings_dark_theme_layout)
    }

    private fun initializeViewsValue() {
        mWifiOnlySwitch!!.isChecked = PreferenceManager.isWifiOnly
        mDarkThemeSwitch!!.isChecked = PreferenceManager.isDarkTheme
        mMinimumDownloadTextView!!.text = AppConstants.MINIMUM_SIMULTANEOUS_DOWNLOAD.toString()
        mMaximumDownloadTextView!!.text = AppConstants.MAXIMAL_SIMULTANEOUS_DOWNLOAD.toString()
        mDownloadValueTextView!!.text = PreferenceManager.maxTransfers.toString()
        mDownloadSeekBar!!.progress = PreferenceManager.maxTransfers - 1
        mDownloadSeekBar!!.max = AppConstants.MAXIMAL_SIMULTANEOUS_DOWNLOAD - 1
        mExistingFileTextView!!.text = ExistingFileAction.getTextResourceId(
            PreferenceManager.existingFileAction
        ).toString()
    }

    private fun initializeViewsListeners() {
        mWifiOnlyLayout!!.setOnClickListener(object : View.OnClickListener {
            override fun onClick(iV: View) {
                if (mIsAskingWifiOnlySecurity) return
                mWifiOnlySwitch!!.isChecked = !mWifiOnlySwitch!!.isChecked
                if (!mWifiOnlySwitch!!.isChecked) {
                    showWifiOnlySecurityDialog()
                } else PreferenceManager.isWifiOnly = true
            }
        })
        mExistingFileLayout!!.setOnClickListener { startActivityChooseExistingFile() }
        mDarkThemeLayout!!.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View) {
                mDarkThemeSwitch!!.isChecked = !mDarkThemeSwitch!!.isChecked
                Utils.createAlertDialog(
                    this@SettingsActivity,
                    "Restart required",
                    "The application needs to restart to apply the new theme",
                    "Restart",
                    { dialog, which ->
                        PreferenceManager.isDarkTheme = mDarkThemeSwitch!!.isChecked
                        restartActivity()
                    },
                    "Cancel"
                ) { dialog, which -> mDarkThemeSwitch!!.isChecked = !mDarkThemeSwitch!!.isChecked }
                    .show()
            }
        })
        mDownloadSeekBar!!.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(iSeekBar: SeekBar, iProgress: Int, iFromUser: Boolean
            ) {
                mDownloadValueTextView!!.text = (iProgress + 1).toString()
                PreferenceManager.Companion.maxTransfers = iProgress + 1
            }

            override fun onStartTrackingTouch(iSeekBar: SeekBar) {}
            override fun onStopTrackingTouch(iSeekBar: SeekBar) {}
        })
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

    private fun showWifiOnlySecurityDialog() {
        mIsAskingWifiOnlySecurity = true
        val lBuilder: AlertDialog.Builder =
            AlertDialog.Builder(this).setTitle("Disable Wi-Fi only") // TODO : Strings
                .setMessage("Warning : That might cause additional costs")
                .setPositiveButton("Confirm", object : DialogInterface.OnClickListener {
                    override fun onClick(iDialog: DialogInterface, iWhich: Int) {
                        iDialog.dismiss()
                        PreferenceManager.isWifiOnly = false
                    }
                }).setNegativeButton("cancel", object : DialogInterface.OnClickListener {
                    override fun onClick(iDialog: DialogInterface, iWhich: Int) {
                        mIsAskingWifiOnlySecurity = false
                        PreferenceManager.isWifiOnly = true
                        mWifiOnlySwitch!!.isChecked = true
                    }
                })
        lBuilder.create().show()
    }

    private fun startActivityChooseExistingFile() {
        val lIntent = Intent(this@SettingsActivity, ExistingFileActionActivity::class.java)
        startActivityForResult(lIntent, ACTIVITY_REQUEST_CODE_EXISTING_FILE)
    }

    private fun restartActivity() {
        Handler().postDelayed(object : Runnable {
            override fun run() {
                runOnUiThread(object : Runnable {
                    override fun run() {
                        finish()
                        val i: Intent? =
                            baseContext.packageManager.getLaunchIntentForPackage(baseContext.packageName)
                        i!!.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(i)
                    }
                })
            }
        }, 200)
    }

    companion object {
        private val TAG: String = "SETTINGS ACTIVITY"
        private val ACTIVITY_REQUEST_CODE_EXISTING_FILE: Int = 10
    }
}
package com.vpulse.ftpnext

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.*
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.*
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.vpulse.ftpnext.commons.FileUtils
import com.vpulse.ftpnext.commons.Utils
import com.vpulse.ftpnext.core.*
import com.vpulse.ftpnext.database.DataBase
import com.vpulse.ftpnext.database.FTPServerTable.FTPServer
import com.vpulse.ftpnext.database.FTPServerTable.FTPServerDAO

// TODO : Set last local folder used
// TODO : Don't save the metadata if there's already a connection
class ConfigureServerActivity : AppCompatActivity() {
    private var mRootView: View? = null
    private var mNameEditText: TextInputEditText? = null
    private var mServerEditText: TextInputEditText? = null
    private var mUserNameEditText: TextInputEditText? = null
    private var mPasswordEditText: TextInputEditText? = null
    private var mPortEditText: TextInputEditText? = null
    private var mFolderNameEditText: TextInputEditText? = null
    private var mTypeRadioGroup: RadioGroup? = null
    private var mFolderName: String? = null
    private var mAbsolutePath: String? = null
    private var mForceBackReturn: Boolean = false
    private var mFTPServerDAO: FTPServerDAO? = null
    private var mEditedFTPServer: FTPServer? = null
    public override fun onCreate(iSavedInstanceState: Bundle?) {
        super.onCreate(iSavedInstanceState)
        setTheme(AppCore.appTheme)
        setContentView(R.layout.activity_configure_ftp_server)
        initializeGUI()
        initializeListeners()
        mFTPServerDAO = DataBase.fTPServerDAO
        mAbsolutePath = null
        val lBundle: Bundle? = intent.extras
        val lServerId: Int = lBundle!!.getInt(KEY_DATABASE_ID)
        if (lServerId != NO_DATABASE_ID) {
            mEditedFTPServer = mFTPServerDAO!!.fetchById(lServerId)
            configureEntriesForEdition(mEditedFTPServer)
        } else { // Open the keyboard :
            mRootView!!.requestFocus()
        }
    }

    override fun onBackPressed() {
        if (!mForceBackReturn) {
            handleAlertLosingEdition()
        } else {
            setResult(ACTIVITY_RESULT_ABORT)
            super.onBackPressed()
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

    private fun initializeGUI() {
        setSupportActionBar(findViewById<View>(R.id.configure_server_toolbar) as Toolbar?)
        supportActionBar!!.setDisplayShowTitleEnabled(false)
        mRootView = findViewById(R.id.activity_configure_ftp_server_scrollview)
        mNameEditText = findViewById(R.id.name_edit_text)
        mNameEditText!!.tag = findViewById(R.id.name_edit_text_layout)
        mServerEditText = findViewById(R.id.server_host_edit_text)
        mServerEditText!!.tag = findViewById(R.id.server_host_edit_text_layout)
        mUserNameEditText = findViewById(R.id.username_edit_text)
        mUserNameEditText!!.tag = findViewById(R.id.user_name_edit_text_layout)
        mPasswordEditText = findViewById(R.id.password_edit_text)
        mPasswordEditText!!.tag = findViewById(R.id.password_edit_text_layout)
        mPortEditText = findViewById(R.id.port_edit_text)
        mPortEditText!!.tag = findViewById(R.id.port_edit_text_layout)
        mFolderNameEditText = findViewById(R.id.local_folder_edit_text)
        mFolderNameEditText!!.tag = findViewById(R.id.local_folder_edit_text_layout)
        mFolderNameEditText!!.showSoftInputOnFocus = false
        mFolderNameEditText!!.setTextIsSelectable(true)
        mFolderNameEditText!!.setTextIsSelectable(false)
        mFolderNameEditText!!.setOnClickListener { iView -> onClickLocalFolder(iView) }
        mTypeRadioGroup = findViewById(R.id.type_radio_group)
    }

    private fun initializeListeners() {
        mNameEditText!!.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int
            ) {
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int
            ) {
            }

            override fun afterTextChanged(iEditable: Editable) {
                val lString: String = iEditable.toString()
                if (mFTPServerDAO!!.fetchByName(lString) != null && (mEditedFTPServer != null && !(lString == mEditedFTPServer!!.name))) {
                    if (lString != mEditedFTPServer!!.name) {
                        (mNameEditText!!.tag as TextInputLayout).error = "Name already used"
                    }
                    return
                }
                if (!Utils.isNullOrEmpty(lString)) {
                    LogManager.debug(TAG, "Editable != null passing")
                    (mNameEditText!!.tag as TextInputLayout).isErrorEnabled = false
                } else if (Utils.isNullOrEmpty(lString)) {
                    LogManager.debug(TAG, "Editable != null not useless")
                    (mNameEditText!!.tag as TextInputLayout).error = "Obligatory"
                }
            }
        })
        mServerEditText!!.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(iCharSequence: CharSequence,
                                           iStart: Int,
                                           iCount: Int,
                                           iAfter: Int
            ) {
            }

            override fun onTextChanged(iCharSequence: CharSequence,
                                       iStart: Int,
                                       iBefore: Int,
                                       iCount: Int
            ) {
            }

            override fun afterTextChanged(iEditable: Editable) {
                if (iEditable != null) {
                    val lString: String = iEditable.toString()
                    if (Utils.isNullOrEmpty(lString)) (mServerEditText!!.tag as TextInputLayout).error =
                        "Obligatory" else (mServerEditText!!.tag as TextInputLayout).isErrorEnabled =
                        false
                }
            }
        })
        mPasswordEditText!!.setTypeface(Typeface.DEFAULT)
        (mPasswordEditText!!.tag as TextInputLayout).setEndIconOnClickListener {
            val lCursorStart: Int = mPasswordEditText!!.selectionStart
            val lCursorEnd: Int = mPasswordEditText!!.selectionEnd
            if (mPasswordEditText!!.inputType != InputType.TYPE_CLASS_TEXT) mPasswordEditText!!.inputType =
                InputType.TYPE_CLASS_TEXT else mPasswordEditText!!.inputType =
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            mPasswordEditText!!.setSelection(lCursorStart, lCursorEnd)
            mPasswordEditText!!.setTypeface(Typeface.DEFAULT)
        }
    }

    private fun configureEntriesForEdition(iEditedFTPServer: FTPServer?) {
        mNameEditText!!.setText(iEditedFTPServer!!.name)
        mServerEditText!!.setText(iEditedFTPServer.server)
        mUserNameEditText!!.setText(iEditedFTPServer.user)
        mPasswordEditText!!.setText(iEditedFTPServer.pass)
        mPortEditText!!.setText(iEditedFTPServer.port.toString())
        mAbsolutePath = iEditedFTPServer.absolutePath
        mFolderName = iEditedFTPServer.folderName
        mFolderNameEditText!!.setText(iEditedFTPServer.folderName)
        when (iEditedFTPServer.fTPType) {
            FTPType.SFTP -> mTypeRadioGroup!!.check(R.id.radio_button_sftp)
            FTPType.FTP -> mTypeRadioGroup!!.check(R.id.radio_button_ftp)
            else -> mTypeRadioGroup!!.check(R.id.radio_button_ftp)
        }
    }

    private fun handleAlertLosingEdition() {
        val lFTPServer: FTPServer = FTPServer()
        lFTPServer.name = mNameEditText!!.text.toString().trim { it <= ' ' }
        lFTPServer.server = mServerEditText!!.text.toString().trim { it <= ' ' }
        lFTPServer.user = mUserNameEditText!!.text.toString().trim { it <= ' ' }
        lFTPServer.pass = mPasswordEditText!!.text.toString()
        val lPort: String = mPortEditText!!.text.toString()
        lFTPServer.port = (if ((lPort == "")) 0 else lPort.toInt())
        lFTPServer.folderName = mFolderName
        lFTPServer.absolutePath = mAbsolutePath
        lFTPServer.fTPType = radioGroupType

        // If form isn't empty && (edited != null && form hasn't been edited)
        if (!lFTPServer.isEmpty && (lFTPServer != mEditedFTPServer)) {
            AlertDialog.Builder(this).setTitle("Cancel this entry?")
                .setMessage("Are you sure to cancel this entry?")
                .setPositiveButton("yes"
                ) { _, _ ->
                    mForceBackReturn = true
                    onBackPressed()
                }.setNegativeButton("no", null).show()
        } else {
            mForceBackReturn = true
            onBackPressed()
        }
    }

    private val radioGroupType: FTPType
        private get() {
            when (mTypeRadioGroup!!.checkedRadioButtonId) {
                R.id.radio_button_ftp -> return FTPType.FTP
                R.id.radio_button_sftp -> return FTPType.SFTP
                else -> return FTPType.DEFAULT
            }
        }

    fun onClickSaveButton() {
        var lCanSave = true
        val lName: String = mNameEditText!!.text.toString().trim { it <= ' ' }
        val lServerName: String = mServerEditText!!.text.toString().trim { it <= ' ' }
        val lUserName: String = mUserNameEditText!!.text.toString().trim { it <= ' ' }
        val lPassword: String = mPasswordEditText!!.text.toString()
        val lPort: String = mPortEditText!!.text.toString()
        val lFTPType: FTPType = radioGroupType
        if (Utils.isNullOrEmpty(lName)) {
            (mNameEditText!!.tag as TextInputLayout).error = "Obligatory"
            lCanSave = false
        } else if (mFTPServerDAO!!.fetchByName(lName) != null && (mEditedFTPServer != null && !(lName == mEditedFTPServer!!.name))) {
            (mNameEditText!!.tag as TextInputLayout).error = "Name already used"
            lCanSave = false
        }
        if (Utils.isNullOrEmpty(lServerName)) {
            (mServerEditText!!.tag as TextInputLayout).error = "Obligatory"
            lCanSave = false
        }
        if (Utils.isNullOrEmpty(mAbsolutePath)) {
            (mFolderNameEditText!!.tag as TextInputLayout).error = "Obligatory"
            lCanSave = false
        }
        if (lCanSave) {
            val lNewFTPServer = FTPServer()
            lNewFTPServer.name = lName
            lNewFTPServer.server = lServerName
            lNewFTPServer.user = lUserName
            lNewFTPServer.pass = lPassword
            lNewFTPServer.fTPType = lFTPType
            lNewFTPServer.folderName = mFolderName
            lNewFTPServer.absolutePath = mAbsolutePath
            if (Utils.isNullOrEmpty(lPort)) {
                when (lFTPType) {
                    FTPType.FTP, FTPType.DEFAULT -> lNewFTPServer.port = AppConstants.FTP_DEFAULT_PORT
                    FTPType.SFTP -> lNewFTPServer.port = AppConstants.SFTP_DEFAULT_PORT
                }
            } else {
                lNewFTPServer.port = lPort.toInt()
            }
            val lIntent = Intent()
            var lId: Int
            if (mEditedFTPServer != null) {
                mEditedFTPServer!!.updateContent(lNewFTPServer)
                mFTPServerDAO!!.update(mEditedFTPServer!!)
                LogManager.info(TAG, "Updated id " + mEditedFTPServer!!.dataBaseId)
                lIntent.putExtra(KEY_DATABASE_ID, mEditedFTPServer!!.dataBaseId)
                setResult(ACTIVITY_RESULT_UPDATE_SUCCESS, lIntent)
            } else if ((mFTPServerDAO!!.add(lNewFTPServer).also({ lId = it })) != -1) {
                lIntent.putExtra(KEY_DATABASE_ID, lId)
                setResult(ACTIVITY_RESULT_ADD_SUCCESS, lIntent)
            } else setResult(ACTIVITY_RESULT_ADD_FAIL)
            finish()
        }
    }

    public override fun onActivityResult(iRequestCode: Int, iResultCode: Int, iResultData: Intent?
    ) {
        super.onActivityResult(iRequestCode, iResultCode, iResultData)
        if (iRequestCode == ACTIVITY_REQUEST_CODE_SELECT_FOLDER) {
            if (iResultCode == RESULT_OK) {
                val lTreeUri: Uri? = iResultData!!.data
                val lPickedDir: DocumentFile? = DocumentFile.fromTreeUri(this, (lTreeUri)!!)
                LogManager.info(TAG, lPickedDir!!.name)
                LogManager.info(TAG, FileUtils.getPathFromFolderUri(lTreeUri, this))
                mFolderName = lPickedDir.name
                mAbsolutePath = FileUtils.getPathFromFolderUri(lTreeUri, this)
                if (!mAbsolutePath!!.endsWith("/")) mAbsolutePath += "/"
                mFolderNameEditText!!.setText(mAbsolutePath)
            }
        } else if (iRequestCode == ACTIVITY_REQUEST_CODE_READ_EXTERNAL_STORAGE) {
            LogManager.debug(TAG, "RESULT CODE : $iResultCode")
        }
    }

    private fun onClickLocalFolder(view: View) {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_DENIED) {
            Utils.createAlertDialog(
                this,
                "Permissions required",
                "Read and Storage permissions are required to select a folder. " + "If you denied this permission before, please go in the android settings of this app to unlock the read/write permissions.",
                "Ok",
                null
            ).show()
            return
        } else if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED) {
            val lPermissions: Array<String> = arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            ActivityCompat.requestPermissions(
                this, lPermissions, ACTIVITY_REQUEST_CODE_READ_EXTERNAL_STORAGE
            )
            return
        }
        val intent: Intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        startActivityForResult(intent, ACTIVITY_REQUEST_CODE_SELECT_FOLDER)
    }

    fun onClickRadioButtonFTP(iView: View?) {
        (mPortEditText!!.tag as TextInputLayout).hint = "Default: 21"
    }

    fun onClickRadioButtonSFTP(iView: View?) {
        (mPortEditText!!.tag as TextInputLayout).hint = "Default: 22"
    }

    companion object {
        const val ACTIVITY_RESULT_ADD_SUCCESS: Int = 0
        const val ACTIVITY_RESULT_ADD_FAIL: Int = 1
        const val ACTIVITY_RESULT_ABORT: Int = 2
        const val ACTIVITY_RESULT_UPDATE_SUCCESS: Int = 3
        const val KEY_DATABASE_ID: String = "KEY_DATABASE_ID"
        const val NO_DATABASE_ID: Int = -1
        private const val ACTIVITY_REQUEST_CODE_READ_EXTERNAL_STORAGE: Int = 1
        private const val ACTIVITY_REQUEST_CODE_SELECT_FOLDER: Int = 10
        private const val TAG: String = "CONFIGURE FTP SERVER ACTIVITY"
    }
}
package com.vpulse.ftpnext;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.widget.RadioGroup;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.vpulse.ftpnext.commons.FileUtils;
import com.vpulse.ftpnext.commons.Utils;
import com.vpulse.ftpnext.core.AppInfo;
import com.vpulse.ftpnext.core.FTPType;
import com.vpulse.ftpnext.core.LogManager;
import com.vpulse.ftpnext.core.PreferenceManager;
import com.vpulse.ftpnext.database.DataBase;
import com.vpulse.ftpnext.database.FTPServerTable.FTPServer;
import com.vpulse.ftpnext.database.FTPServerTable.FTPServerDAO;

// TODO : Set last local folder used
// TODO : Don't save the metadata if there's already a connection
// TODO : Add a back arrow on the top left

// TODO : BEFORE RELEASE : Change the text of "Wait !" for smt accurate
public class ConfigureServerActivity extends AppCompatActivity {

    public static final int ACTIVITY_RESULT_ADD_SUCCESS = 0;
    public static final int ACTIVITY_RESULT_ADD_FAIL = 1;
    public static final int ACTIVITY_RESULT_ABORT = 2;
    public static final int ACTIVITY_RESULT_UPDATE_SUCCESS = 3;
    public static final String KEY_DATABASE_ID = "KEY_DATABASE_ID";
    public static final int NO_DATABASE_ID = -1;

    private static final int ACTIVITY_REQUEST_CODE_SELECT_FOLDER = 10;
    private static final String TAG = "CONFIGURE FTP SERVER ACTIVITY";
    private View mRootView;

    private TextInputEditText mNameEditText;
    private TextInputEditText mServerEditText;
    private TextInputEditText mUserNameEditText;
    private TextInputEditText mPasswordEditText;
    private TextInputEditText mPortEditText;
    private TextInputEditText mFolderNameEditText;
    private RadioGroup mTypeRadioGroup;

    private String mFolderName;
    private String mAbsolutePath;
    private boolean mForceBackReturn;

    private FTPServerDAO mFTPServerDAO;
    private FTPServer mEditedFTPServer;


    @Override
    public void onCreate(Bundle iSavedInstanceState) {
        super.onCreate(iSavedInstanceState);
        if (PreferenceManager.isDarkTheme())
            setTheme(R.style.AppTheme_Dark);
        setContentView(R.layout.activity_configure_ftp_server);

        initializeGUI();
        initializeListeners();
        mFTPServerDAO = DataBase.getFTPServerDAO();
        mAbsolutePath = null;

        Bundle lBundle = this.getIntent().getExtras();
        int lServerId = lBundle.getInt(KEY_DATABASE_ID);
        if (lServerId != NO_DATABASE_ID) {
            mEditedFTPServer = mFTPServerDAO.fetchById(lServerId);
            configureEntriesForEdition(mEditedFTPServer);
        } else {
            // Open the keyboard :
            mRootView.requestFocus();
        }
    }

    @Override
    public void onBackPressed() {
        if (!mForceBackReturn) {
            handleAlertLosingEdition();
        } else {
            setResult(ACTIVITY_RESULT_ABORT);
            super.onBackPressed();
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

    private void initializeGUI() {
        getSupportActionBar().setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_home_up);
        getSupportActionBar().setCustomView(R.layout.action_bar_configure_server);

        mRootView = findViewById(R.id.activity_configure_ftp_server_scrollview);

        mNameEditText = findViewById(R.id.name_edit_text);
        mNameEditText.setTag(findViewById(R.id.name_edit_text_layout));

        mServerEditText = findViewById(R.id.server_host_edit_text);
        mServerEditText.setTag(findViewById(R.id.server_host_edit_text_layout));

        mUserNameEditText = findViewById(R.id.username_edit_text);
        mUserNameEditText.setTag(findViewById(R.id.user_name_edit_text_layout));

        mPasswordEditText = findViewById(R.id.password_edit_text);
        mPasswordEditText.setTag(findViewById(R.id.password_edit_text_layout));

        mPortEditText = findViewById(R.id.port_edit_text);
        mPortEditText.setTag(findViewById(R.id.port_edit_text_layout));

        mFolderNameEditText = findViewById(R.id.local_folder_edit_text);
        mFolderNameEditText.setTag(findViewById(R.id.local_folder_edit_text_layout));
        mFolderNameEditText.setShowSoftInputOnFocus(false);
        mFolderNameEditText.setTextIsSelectable(true);
        mFolderNameEditText.setTextIsSelectable(false);

        mTypeRadioGroup = findViewById(R.id.type_radio_group);
    }

    private void initializeListeners() {
        mNameEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable iEditable) {
                if (iEditable != null) {
                    String lString = iEditable.toString();

                    if (mFTPServerDAO.fetchByName(lString) != null &&
                            (mEditedFTPServer != null && !lString.equals(mEditedFTPServer.getName()))) {
                        if (!lString.equals(mEditedFTPServer.getName()))
                            ((TextInputLayout) mNameEditText.getTag()).setError("Name already used");
                        return;
                    }
                    if (iEditable != null && !Utils.isNullOrEmpty(lString)) {
                        LogManager.debug(TAG, "Editable != null passing");
                        ((TextInputLayout) mNameEditText.getTag()).setErrorEnabled(false);
                    } else if (Utils.isNullOrEmpty(lString)) {
                        LogManager.debug(TAG, "Editable != null not useless");
                        ((TextInputLayout) mNameEditText.getTag()).setError("Obligatory");
                    }

                }
            }
        });

        mServerEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence iCharSequence, int iStart, int iCount, int iAfter) {

            }

            @Override
            public void onTextChanged(CharSequence iCharSequence, int iStart, int iBefore, int iCount) {

            }

            @Override
            public void afterTextChanged(Editable iEditable) {
                if (iEditable != null) {
                    String lString = iEditable.toString();

                    if (Utils.isNullOrEmpty(lString))
                        ((TextInputLayout) mServerEditText.getTag()).setError("Obligatory");
                    else
                        ((TextInputLayout) mServerEditText.getTag()).setErrorEnabled(false);
                }
            }
        });

        mPasswordEditText.setTypeface(Typeface.DEFAULT);
        ((TextInputLayout) mPasswordEditText.getTag()).setEndIconOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View iV) {
                int lCursorStart = mPasswordEditText.getSelectionStart();
                int lCursorEnd = mPasswordEditText.getSelectionEnd();

                if (mPasswordEditText.getInputType() != InputType.TYPE_CLASS_TEXT)
                    mPasswordEditText.setInputType(InputType.TYPE_CLASS_TEXT);
                else
                    mPasswordEditText.setInputType(
                            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

                mPasswordEditText.setSelection(lCursorStart, lCursorEnd);
                mPasswordEditText.setTypeface(Typeface.DEFAULT);
            }
        });
    }

    private void configureEntriesForEdition(FTPServer iEditedFTPServer) {
        mNameEditText.setText(iEditedFTPServer.getName());
        mServerEditText.setText(iEditedFTPServer.getServer());
        mUserNameEditText.setText(iEditedFTPServer.getUser());
        mPasswordEditText.setText(iEditedFTPServer.getPass());
        mPortEditText.setText(String.valueOf(iEditedFTPServer.getPort()));
        mAbsolutePath = iEditedFTPServer.getAbsolutePath();
        mFolderName = iEditedFTPServer.getFolderName();

        mFolderNameEditText.setText(iEditedFTPServer.getFolderName());

        switch (iEditedFTPServer.getFTPType()) {
            case SFTP:
                mTypeRadioGroup.check(R.id.radio_button_sftp);
                break;
            case FTP:
            default:
                mTypeRadioGroup.check(R.id.radio_button_ftp);
        }
    }

    private void handleAlertLosingEdition() {
        FTPServer lFTPServer = new FTPServer();
        lFTPServer.setName(mNameEditText.getText().toString().trim());
        lFTPServer.setServer(mServerEditText.getText().toString().trim());
        lFTPServer.setUser(mUserNameEditText.getText().toString().trim());
        lFTPServer.setPass(mPasswordEditText.getText().toString());
        String lPort = mPortEditText.getText().toString();
        lFTPServer.setPort(lPort.equals("") ? 0 : Integer.parseInt(lPort));
        lFTPServer.setFolderName(mFolderName);
        lFTPServer.setAbsolutePath(mAbsolutePath);
        lFTPServer.setFTPType(getRadioGroupType());

        // If form isn't empty && (edited != null && form hasn't been edited)
        if (!lFTPServer.isEmpty() && (lFTPServer != null && !lFTPServer.equals(mEditedFTPServer))) {
            new AlertDialog.Builder(this)
                    .setTitle("Wait !")
                    .setMessage("Are you sure to cancel this entry?")
                    .setPositiveButton("yes", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            mForceBackReturn = true;
                            onBackPressed();
                        }
                    })
                    .setNegativeButton("no", null)
                    .show();
        } else {
            mForceBackReturn = true;
            onBackPressed();
        }
    }

    private FTPType getRadioGroupType() {
        switch (mTypeRadioGroup.getCheckedRadioButtonId()) {
            case R.id.radio_button_ftp:
                return FTPType.FTP;
            case R.id.radio_button_sftp:
                return FTPType.SFTP;
            default:
                return FTPType.DEFAULT;
        }
    }

    public void OnClickSaveButton(View view) {
        boolean lCanSave = true;
        String lName = mNameEditText.getText().toString().trim();
        String lServerName = mServerEditText.getText().toString().trim();
        String lUserName = mUserNameEditText.getText().toString().trim();
        String lPassword = mPasswordEditText.getText().toString();
        String lPort = mPortEditText.getText().toString();
        FTPType lFTPType = getRadioGroupType();

        if (Utils.isNullOrEmpty(lName)) {
            ((TextInputLayout) mNameEditText.getTag()).setError("Obligatory");
            lCanSave = false;
        } else if (mFTPServerDAO.fetchByName(lName) != null &&
                (mEditedFTPServer != null && !lName.equals(mEditedFTPServer.getName()))) {
            ((TextInputLayout) mNameEditText.getTag()).setError("Name already used");
            lCanSave = false;
        }

        if (Utils.isNullOrEmpty(lServerName)) {
            ((TextInputLayout) mServerEditText.getTag()).setError("Obligatory");
            lCanSave = false;
        }

        if (Utils.isNullOrEmpty(mAbsolutePath)) {
            ((TextInputLayout) mFolderNameEditText.getTag()).setError("Obligatory");
            lCanSave = false;
        }

        if (lCanSave) {
            FTPServer lNewFTPServer = new FTPServer();
            lNewFTPServer.setName(lName);
            lNewFTPServer.setServer(lServerName);
            lNewFTPServer.setUser(lUserName);
            lNewFTPServer.setPass(lPassword);
            lNewFTPServer.setFTPType(lFTPType);
            lNewFTPServer.setFolderName(mFolderName);
            lNewFTPServer.setAbsolutePath(mAbsolutePath);

            if (Utils.isNullOrEmpty(lPort)) {
                switch (lFTPType) {
                    case FTP:
                    case DEFAULT:
                        lNewFTPServer.setPort(AppInfo.FTP_DEFAULT_PORT);
                        break;
                    case SFTP:
                        lNewFTPServer.setPort(AppInfo.SFTP_DEFAULT_PORT);
                        break;
                }
            } else
                lNewFTPServer.setPort(Integer.parseInt(lPort));

            Intent lIntent = new Intent();
            int lId;
            if (mEditedFTPServer != null) {
                mEditedFTPServer.updateContent(lNewFTPServer);
                mFTPServerDAO.update(mEditedFTPServer);
                LogManager.info(TAG, "Updated id " + mEditedFTPServer.getDataBaseId());
                lIntent.putExtra(KEY_DATABASE_ID, mEditedFTPServer.getDataBaseId());
                setResult(ACTIVITY_RESULT_UPDATE_SUCCESS, lIntent);
            } else if ((lId = mFTPServerDAO.add(lNewFTPServer)) != -1) {
                lIntent.putExtra(KEY_DATABASE_ID, lId);
                setResult(ACTIVITY_RESULT_ADD_SUCCESS, lIntent);
            } else
                setResult(ACTIVITY_RESULT_ADD_FAIL);
            finish();
        }
    }

    public void onActivityResult(int iRequestCode, int iResultCode, Intent iResultData) {
        super.onActivityResult(iRequestCode, iResultCode, iResultData);
        if (iRequestCode == ACTIVITY_REQUEST_CODE_SELECT_FOLDER) {
            if (iResultCode == RESULT_OK) {
                Uri lTreeUri = iResultData.getData();
                DocumentFile lPickedDir = DocumentFile.fromTreeUri(this, lTreeUri);

                LogManager.info(TAG, lPickedDir.getName());
                LogManager.info(TAG, FileUtils.getFullPathFromTreeUri(lTreeUri, this));

                mFolderName = lPickedDir.getName();
                mAbsolutePath = FileUtils.getFullPathFromTreeUri(lTreeUri, this);

                if (!mAbsolutePath.endsWith("/"))
                    mAbsolutePath += "/";

                mFolderNameEditText.setText(mAbsolutePath);
            }
        }
    }

    public void OnClickLocalFolder(View view) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        startActivityForResult(intent, ACTIVITY_REQUEST_CODE_SELECT_FOLDER);
    }

    public void OnClickRadioButtonFTP(View iView) {
        ((TextInputLayout) mPortEditText.getTag()).setHint("Default: 21");
    }

    public void OnClickRadioButtonSFTP(View iView) {
        ((TextInputLayout) mPortEditText.getTag()).setHint("Default: 22");
    }
}
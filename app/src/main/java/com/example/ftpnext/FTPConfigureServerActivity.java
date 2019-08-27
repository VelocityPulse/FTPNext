package com.example.ftpnext;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.TextInputEditText;
import android.support.design.widget.TextInputLayout;
import android.support.v4.provider.DocumentFile;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.transition.Slide;
import android.view.Gravity;
import android.view.Menu;
import android.view.View;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.widget.RadioGroup;

import com.example.ftpnext.commons.FileUtils;
import com.example.ftpnext.commons.Utils;
import com.example.ftpnext.core.AppInfo;
import com.example.ftpnext.core.FTPType;
import com.example.ftpnext.core.LogManager;
import com.example.ftpnext.database.DataBase;
import com.example.ftpnext.database.FTPServerTable.FTPServer;
import com.example.ftpnext.database.FTPServerTable.FTPServerDAO;

import java.io.File;

// TODO : Set last local folder used
// TODO : Don't save the metadata if there's already a connection

public class FTPConfigureServerActivity extends AppCompatActivity {

    public static final int ACTIVITY_REQUEST_CODE = 1;
    public static final int ACTIVITY_RESULT_ADD_SUCCESS = 0;
    public static final int ACTIVITY_RESULT_ADD_FAIL = 1;
    public static final int ACTIVITY_RESULT_ABORT = 2;
    public static final int ACTIVITY_RESULT_UPDATE_SUCCESS = 3;
    public static final String KEY_DATABASE_ID = "KEY_DATABASE_ID";
    public static final int NO_DATABASE_ID = -1;
    private static String TAG = "CONFIGURE FTP SERVER ACTIVITY";
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
        configureActivityIntroduce();
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
        }
    }

    // TODO on resume

    private void configureActivityIntroduce() {
        getWindow().requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS);

        Slide lSlide = new Slide();
        lSlide.setDuration(getResources().getInteger(R.integer.configure_ftp_server_animation_slide_time));
        lSlide.setInterpolator(new DecelerateInterpolator(5F));
        lSlide.setSlideEdge(Gravity.BOTTOM);
        getWindow().setEnterTransition(lSlide);
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

    private void initializeGUI() {
        Toolbar lToolBar = findViewById(R.id.toolbar);
        setSupportActionBar(lToolBar);

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
        mRootView.requestFocus();
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
                            (mEditedFTPServer != null && !lString.equals(mEditedFTPServer.getName()))) {// TODO use resources
                        if (!lString.equals(mEditedFTPServer.getName()))
                            ((TextInputLayout) mNameEditText.getTag()).setError("Name already used");
                        return;
                    }
                    if (iEditable != null && !Utils.isNullOrEmpty(lString))
                        ((TextInputLayout) mNameEditText.getTag()).setErrorEnabled(false);
                    else if (Utils.isNullOrEmpty(lString))
                        ((TextInputLayout) mNameEditText.getTag()).setError("Obligatory");

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
    }

    private void configureEntriesForEdition(FTPServer iEditedFTPServer) {
        mNameEditText.setText(iEditedFTPServer.getName());
        mServerEditText.setText(iEditedFTPServer.getServer());
        mUserNameEditText.setText(iEditedFTPServer.getUser());
        mPasswordEditText.setText(iEditedFTPServer.getPass());
        mPortEditText.setText(String.valueOf(iEditedFTPServer.getPort()));
        mAbsolutePath = iEditedFTPServer.getAbsolutePath();
        mFolderName = iEditedFTPServer.getFolderName();

        LogManager.info(TAG, iEditedFTPServer.getAbsolutePath());
        File f = new File(iEditedFTPServer.getAbsolutePath());
        mFolderNameEditText.setText(iEditedFTPServer.getFolderName());

        switch (iEditedFTPServer.getFTPType()) {
            case FTP:
                mTypeRadioGroup.check(R.id.radio_button_ftp);
                break;
            case SFTP:
                mTypeRadioGroup.check(R.id.radio_button_sftp);
                break;
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
        lFTPServer.setPort(lPort.equals("") ? 0 : Integer.valueOf(lPort));
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
                        lNewFTPServer.setPort(AppInfo.FTP_DEFAULT_PORT);
                        break;
                    case SFTP:
                        lNewFTPServer.setPort(AppInfo.SFTP_DEFAULT_PORT);
                        break;
                    case DEFAULT:
                        lNewFTPServer.setPort(AppInfo.FTP_DEFAULT_PORT);
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
        if (iResultCode == RESULT_OK) {
            Uri lTreeUri = iResultData.getData();
            DocumentFile lPickedDir = DocumentFile.fromTreeUri(this, lTreeUri);

            LogManager.error(TAG, lPickedDir.getName());
            LogManager.error(TAG, FileUtils.getFullPathFromTreeUri(lTreeUri, this));
            mFolderName = lPickedDir.getName();
            mFolderNameEditText.setText(mFolderName);
            mAbsolutePath = FileUtils.getFullPathFromTreeUri(lTreeUri, this);
        }
    }

    public void OnClickLocalFolder(View view) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        startActivityForResult(intent, 10);
    }

    public void OnClickRadioButtonFTP(View iView) {
        ((TextInputLayout) mPortEditText.getTag()).setHint("Default: 21");
    }

    public void OnClickRadioButtonSFTP(View iView) {
        ((TextInputLayout) mPortEditText.getTag()).setHint("Default: 22");
    }
}
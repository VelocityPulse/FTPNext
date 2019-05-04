package com.example.ftpnext;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.TextInputEditText;
import android.support.design.widget.TextInputLayout;
import android.support.v4.provider.DocumentFile;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.transition.Slide;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.widget.RadioGroup;

import com.example.ftpnext.commons.Utils;
import com.example.ftpnext.core.FTPType;
import com.example.ftpnext.core.LogManager;
import com.example.ftpnext.database.DataBase;
import com.example.ftpnext.database.FTPServerTable.FTPServer;
import com.example.ftpnext.database.FTPServerTable.FTPServerDAO;

// TODO : remember the last local folder used

public class ConfigureFTPServerActivity extends AppCompatActivity {

    private View mRootView;

    private TextInputEditText mNameEditText;
    private TextInputEditText mServerEditText;
    private TextInputEditText mUserNameEditText;
    private TextInputEditText mPasswordEditText;
    private TextInputEditText mPortEditText;
    private TextInputEditText mLocalFolderEditText;
    private RadioGroup mTypeRadioGroup;

    private String mFullLocalFolder;

    private FTPServerDAO mFTPServerDAO;

    @Override
    public void onCreate(Bundle iSavedInstanceState) {
        super.onCreate(iSavedInstanceState);
        configureActivityIntroduce();
        setContentView(R.layout.activity_configure_ftp_server);

        initializeGUI();
        initializeListeners();
        mFTPServerDAO = DataBase.getFTPServerDAO();
        mFullLocalFolder = null;
    }

    private void configureActivityIntroduce() {
        getWindow().requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS);

        Slide lSlide = new Slide();
        lSlide.setDuration(getResources().getInteger(R.integer.configure_ftp_server_animation_slide_time));
        lSlide.setInterpolator(new DecelerateInterpolator(5F));
        lSlide.setSlideEdge(Gravity.BOTTOM);
        getWindow().setEnterTransition(lSlide);
    }

    private void initializeGUI() {
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

        mLocalFolderEditText = findViewById(R.id.local_folder_edit_text);
        mLocalFolderEditText.setTag(findViewById(R.id.local_folder_edit_text_layout));
        mLocalFolderEditText.setShowSoftInputOnFocus(false);
        mLocalFolderEditText.setTextIsSelectable(true);
        mLocalFolderEditText.setTextIsSelectable(false);

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
                if (iEditable != null && !Utils.isNullOrEmpty(iEditable.toString()))
                    ((TextInputLayout) mNameEditText.getTag()).setErrorEnabled(false);
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
                if (iEditable != null && !Utils.isNullOrEmpty(iEditable.toString()))
                    ((TextInputLayout) mServerEditText.getTag()).setErrorEnabled(false);
            }
        });

        mPortEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence iCharSequence, int iStart, int iCount, int iAfter) {

            }

            @Override
            public void onTextChanged(CharSequence iCharSequence, int iStart, int iBefore, int iCount) {

            }

            @Override
            public void afterTextChanged(Editable iEditable) {
                if (iEditable != null && !Utils.isNullOrEmpty(iEditable.toString()))
                    ((TextInputLayout) mPortEditText.getTag()).setErrorEnabled(false);
            }
        });
    }

    public void OnClickSaveButton(View view) {
        boolean lCanSave = true;
        String lName = mNameEditText.getText().toString().trim();
        String lServerName = mServerEditText.getText().toString().trim();
        String lUserName = mUserNameEditText.getText().toString().trim();
        String lPassword = mPasswordEditText.getText().toString();
        String lPort = mPortEditText.getText().toString();

        FTPType lFTPType;
        switch (mTypeRadioGroup.getCheckedRadioButtonId()) {
            case R.id.radio_button_ftp:
                lFTPType = FTPType.FTP;
                break;
            case R.id.radio_button_sftp:
                lFTPType = FTPType.SFTP;
                break;
            default:
                lFTPType = FTPType.DEFAULT;
        }

        if (Utils.isNullOrEmpty(lName)) {
            ((TextInputLayout) mNameEditText.getTag()).setError("Obligatory");
            lCanSave = false;
        } else if (mFTPServerDAO.fetchByName(lName) != null) {
            ((TextInputLayout) mNameEditText.getTag()).setError("Name already used");
            lCanSave = false;
        }

        if (Utils.isNullOrEmpty(lServerName)) {
            ((TextInputLayout) mServerEditText.getTag()).setError("Obligatory");
            lCanSave = false;
        }

        if (Utils.isNullOrEmpty(lPort)) {
            ((TextInputLayout) mPortEditText.getTag()).setError("Obligatory");
            lCanSave = false;
        }


        if (Utils.isNullOrEmpty(mFullLocalFolder)) {
            ((TextInputLayout) mLocalFolderEditText.getTag()).setError("Obligatory");
            lCanSave = false;
        }

        if (lCanSave) {
            FTPServer lNewFTPServer = new FTPServer();
            lNewFTPServer.setName(lName);
            lNewFTPServer.setServer(lServerName);
            lNewFTPServer.setUser(lUserName);
            lNewFTPServer.setPass(lPassword);
            lNewFTPServer.setFTPType(lFTPType);
            lNewFTPServer.setLocalFolder(mFullLocalFolder);

            if (mFTPServerDAO.add(lNewFTPServer))
                finish();
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (resultCode == RESULT_OK) {
            Uri treeUri = resultData.getData();
            DocumentFile pickedDir = DocumentFile.fromTreeUri(this, treeUri);

            mLocalFolderEditText.setText(pickedDir.getName());
            mFullLocalFolder = pickedDir.getUri().getPath();

            LogManager.error(mFullLocalFolder);
        }
    }

    public void OnClickLocalFolder(View view) {

        LogManager.error("open");
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
//        intent.setType("file/*");
        startActivityForResult(intent, 10);
    }
}

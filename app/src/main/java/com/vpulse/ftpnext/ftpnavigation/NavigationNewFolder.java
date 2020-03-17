package com.vpulse.ftpnext.ftpnavigation;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.TextInputLayout;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowManager;
import android.widget.AutoCompleteTextView;

import com.vpulse.ftpnext.R;
import com.vpulse.ftpnext.commons.Utils;
import com.vpulse.ftpnext.core.LogManager;
import com.vpulse.ftpnext.ftpservices.AFTPConnection;
import com.vpulse.ftpnext.ftpservices.FTPServices;

import org.apache.commons.net.ftp.FTPFile;

import static com.vpulse.ftpnext.ftpnavigation.FTPNavigationActivity.NAVIGATION_MESSAGE_CREATE_FOLDER_FAIL;
import static com.vpulse.ftpnext.ftpnavigation.FTPNavigationActivity.NAVIGATION_MESSAGE_CREATE_FOLDER_SUCCESS;

public class NavigationNewFolder {

    private final static String TAG = "NAVIGATION NEW FOLDER";

    private final FTPNavigationActivity mContextActivity;
    private final Handler mHandler;

    private NavigationNewFolder() throws InstantiationException {
        mContextActivity = null;
        mHandler = null;
        throw new InstantiationException("Constructor not allowed");
    }

    protected NavigationNewFolder(FTPNavigationActivity iContextActivity, Handler iHandler) {
        mContextActivity = iContextActivity;
        mHandler = iHandler;
    }

    protected void onResume() {

    }

    protected void createDialogNewFolder() {
        FTPFile lEnclosingDirectory = mContextActivity.mFTPServices.getCurrentDirectory();

        new Thread(new Runnable() {
            @Override
            public void run() {
                LogManager.error(TAG, "");
            }
        }).start();

        if (lEnclosingDirectory == null) {
            LogManager.error(TAG, "Get current directory returns null");
            String lErrorMessage = "An error occurred...";
            mContextActivity.createDialogError(lErrorMessage).show();
            return;
        }

        if (!lEnclosingDirectory.hasPermission(FTPFile.USER_ACCESS, FTPFile.WRITE_PERMISSION)) {
            String lErrorMessage = "Creation has failed...\nYou don't have the permissions";
            mContextActivity.createDialogError(lErrorMessage).show();
            return;
        }

        final AlertDialog.Builder lBuilder = new AlertDialog.Builder(mContextActivity);
        lBuilder.setTitle("Create new folder"); // TODO : strings

        View lTextSection = View.inflate(mContextActivity, R.layout.dialog_create_folder, null);
        final TextInputLayout lTextInputLayout = lTextSection.findViewById(R.id.name_edit_text_layout);
        final AutoCompleteTextView lEditTextView = lTextSection.findViewById(R.id.name_edit_text);

        final String[] lNames = mContextActivity.mCurrentAdapter.getNames();
        lEditTextView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence iS, int iStart, int iCount, int iAfter) {
            }

            @Override
            public void onTextChanged(CharSequence iS, int iStart, int iBefore, int iCount) {
            }

            @Override
            public void afterTextChanged(Editable iEditable) {
                if (iEditable != null) {
                    String lString = iEditable.toString();

                    if (!Utils.isNullOrEmpty(lString.trim())) {
                        for (String lItem : lNames) {
                            if (lString.equals(lItem)) {
                                lTextInputLayout.setError("Already used"); // TODO : Strings
                                mContextActivity.mCreateFolderDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
                                return;
                            }
                        }

                        lTextInputLayout.setErrorEnabled(false);
                        mContextActivity.mCreateFolderDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true);
                    } else {
                        lTextInputLayout.setError("Obligatory"); // TODO : Strings
                        mContextActivity.mCreateFolderDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
                    }
                }
            }
        });

        lBuilder.setView(lTextSection);
        lBuilder.setCancelable(false);
        lBuilder.setNegativeButton("Cancel", null);
        lBuilder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String lName = lTextInputLayout.getEditText().getText().toString().trim();
                if (!Utils.isNullOrEmpty(lName)) {
                    dialog.dismiss();
                    createFolder(lName);
                }
            }
        });
        lBuilder.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                mContextActivity.closeFABMenu();
            }
        });

        mContextActivity.mCreateFolderDialog = lBuilder.create();
        mContextActivity.mCreateFolderDialog.show();
        mContextActivity.mCreateFolderDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
        mContextActivity.mCreateFolderDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
    }

    private void createFolder(String iName) {
        String lCurrentDirectoryPath = mContextActivity.mDirectoryPath;

        mContextActivity.mFTPServices.createDirectory(lCurrentDirectoryPath, iName,
                new FTPServices.OnCreateDirectoryResult() {
                    @Override
                    public void onSuccess(final FTPFile iNewDirectory) {
                        mHandler.sendMessage(Message.obtain(
                                mHandler,
                                NAVIGATION_MESSAGE_CREATE_FOLDER_SUCCESS,
                                iNewDirectory
                        ));
                    }

                    @Override
                    public void onFail(final AFTPConnection.ErrorCodeDescription iErrorEnum, int iErrorCode) {
                        mContextActivity.mErrorCode = iErrorCode;
                        mHandler.sendMessage(Message.obtain(
                                mHandler,
                                NAVIGATION_MESSAGE_CREATE_FOLDER_FAIL,
                                iErrorEnum
                        ));
                    }
                });
    }
}
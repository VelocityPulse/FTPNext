package com.vpulse.ftpnext.navigation

import android.app.AlertDialog
import android.content.DialogInterface
import android.os.*
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.vpulse.ftpnext.R
import com.vpulse.ftpnext.commons.Utils
import com.vpulse.ftpnext.core.*
import com.vpulse.ftpnext.ftpservices.AFTPConnection.ErrorCodeDescription
import com.vpulse.ftpnext.ftpservices.FTPServices.OnCreateDirectoryResult
import org.apache.commons.net.ftp.FTPFile

class NavigationNewFolder(iContextActivity: NavigationActivity?, iHandler: Handler?) {

    private val mContextActivity: NavigationActivity? = iContextActivity
    private val mHandler: Handler? = iHandler

    fun onResume() {}
    fun createDialogNewFolder() {
        val lEnclosingDirectory: FTPFile? = mContextActivity!!.mFTPServices!!.currentDirectory
        if (lEnclosingDirectory == null) {
            LogManager.error(TAG, "Get current directory returns null")
            val lErrorMessage = "An error occurred..."
            mContextActivity.createDialogError(lErrorMessage).show()
            return
        }
        if (!lEnclosingDirectory.hasPermission(FTPFile.USER_ACCESS, FTPFile.WRITE_PERMISSION)) {
            val lErrorMessage = "Creation has failed...\nYou don't have the permissions"
            mContextActivity.createDialogError(lErrorMessage).show()
            return
        }
        val lBuilder: AlertDialog.Builder = AlertDialog.Builder(mContextActivity)
        lBuilder.setTitle("Create new folder") // TODO : strings
        val lTextSection: View = View.inflate(mContextActivity, R.layout.dialog_create_folder, null)
        val lTextInputLayout: TextInputLayout =
            lTextSection.findViewById(R.id.name_edit_text_layout)
        val lEditTextView: TextInputEditText = lTextSection.findViewById(R.id.name_edit_text)
        val lNames: Array<String?>? = mContextActivity.mCurrentAdapter!!.names
        lEditTextView.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                iS: CharSequence,
                iStart: Int,
                iCount: Int,
                iAfter: Int
            ) {
            }

            override fun onTextChanged(
                iS: CharSequence,
                iStart: Int,
                iBefore: Int,
                iCount: Int
            ) {
            }

            override fun afterTextChanged(iEditable: Editable) {
                val lString: String = iEditable.toString()
                if (!Utils.isNullOrEmpty(lString.trim { it <= ' ' })) {
                    for (lItem: String? in lNames!!) {
                        if ((lString == lItem)) {
                            lTextInputLayout.error = "Already used" // TODO : Strings
                            mContextActivity.mCreateFolderDialog!!.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled =
                                false
                            return
                        }
                    }
                    lTextInputLayout.isErrorEnabled = false
                    mContextActivity.mCreateFolderDialog!!.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled =
                        true
                } else {
                    lTextInputLayout.error = "Obligatory" // TODO : Strings
                    mContextActivity.mCreateFolderDialog!!.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled =
                        false
                }
            }
        })
        lBuilder.setView(lTextSection)
        lBuilder.setCancelable(false)
        lBuilder.setNegativeButton("Cancel", null)
        lBuilder.setPositiveButton("OK", object : DialogInterface.OnClickListener {
            override fun onClick(dialog: DialogInterface, which: Int) {
                val lName: String =
                    lTextInputLayout.editText!!.text.toString().trim({ it <= ' ' })
                if (!Utils.isNullOrEmpty(lName)) {
                    dialog.dismiss()
                    createFolder(lName)
                }
            }
        })
        lBuilder.setOnDismissListener(object : DialogInterface.OnDismissListener {
            override fun onDismiss(dialog: DialogInterface) {
                mContextActivity.closeFABMenu()
            }
        })
        mContextActivity.mCreateFolderDialog = lBuilder.create()
        lEditTextView.requestFocus()
        mContextActivity.mCreateFolderDialog!!.window!!
            .setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        // Show must be after showing keyboard
        mContextActivity.mCreateFolderDialog!!.show()
        mContextActivity.mCreateFolderDialog!!.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled =
            false
    }

    private fun createFolder(iName: String) {
        val lCurrentDirectoryPath: String? = mContextActivity!!.mDirectoryPath
        mContextActivity.mFTPServices!!.createDirectory(lCurrentDirectoryPath, iName,
            object : OnCreateDirectoryResult {
                override fun onSuccess(iNewDirectory: FTPFile?) {
                    mHandler!!.sendMessage(
                        Message.obtain(
                            mHandler,
                            NavigationActivity.Companion.NAVIGATION_MESSAGE_CREATE_FOLDER_SUCCESS,
                            iNewDirectory
                        )
                    )
                }

                override fun onFail(iErrorEnum: ErrorCodeDescription?, iErrorCode: Int) {
                    mContextActivity.mErrorCode = iErrorCode
                    mHandler!!.sendMessage(
                        Message.obtain(
                            mHandler,
                            NavigationActivity.Companion.NAVIGATION_MESSAGE_CREATE_FOLDER_FAIL,
                            iErrorEnum
                        )
                    )
                }
            })
    }

    companion object {
        private val TAG: String = "NAVIGATION NEW FOLDER"
    }
}
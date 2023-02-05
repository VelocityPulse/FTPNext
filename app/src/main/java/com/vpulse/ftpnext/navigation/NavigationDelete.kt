package com.vpulse.ftpnext.navigation

import android.app.AlertDialog
import android.content.DialogInterface
import android.os.Handler
import android.view.View
import android.widget.CheckBox
import android.widget.ProgressBar
import android.widget.TextView
import com.vpulse.ftpnext.R
import com.vpulse.ftpnext.ftpservices.FTPServices
import org.apache.commons.net.ftp.FTPFile

class NavigationDelete(iContextActivity: NavigationActivity?, iHandler: Handler?) {

    private val mContextActivity: NavigationActivity? = iContextActivity
    private val mHandler: Handler? = iHandler

    fun onResume() {}
    fun createDialogDeleteSelection() {
        val lSelectedFiles: Array<FTPFile> = mContextActivity!!.mCurrentAdapter!!.selection
        if (lSelectedFiles.isEmpty()) {
            mContextActivity.createDialogError("Select something.").show()
        } else {
            mContextActivity.mDeletingInfoDialog =
                AlertDialog.Builder((mContextActivity)).setTitle("Deleting :") // TODO : Strings
                    .setMessage("Are you sure to delete the selection ? (" + lSelectedFiles.size + " files)")
                    .setPositiveButton("yes", object : DialogInterface.OnClickListener {
                        override fun onClick(iDialog: DialogInterface, iWhich: Int) {
                            iDialog.dismiss()
                            deleteFile(lSelectedFiles)
                        }
                    }).setNegativeButton("cancel", null).show()
        }
    }

    private fun deleteFile(iSelectedFiles: Array<FTPFile>?) {
        mHandler!!.sendEmptyMessage(NavigationActivity.NAVIGATION_ORDER_SELECTED_MODE_OFF)
        mContextActivity!!.mFTPServices!!.deleteFiles(iSelectedFiles!!,
            object : FTPServices.OnDeleteListener(mContextActivity.mFTPServices!!) {
                var mProgressDirectory: ProgressBar? = null
                var mProgressSubDirectory: ProgressBar? = null
                var mTextViewDirectory: TextView? = null
                var mTextViewSubDirectory: TextView? = null
                override fun onStartDelete() {
                    mHandler.post(Runnable {
                        val lBuilder: AlertDialog.Builder = AlertDialog.Builder(
                            (mContextActivity)
                        )
                        lBuilder.setTitle("Deleting") // TODO : strings
                        val lDialogDoubleProgress: View = View.inflate(
                            mContextActivity, R.layout.dialog_double_progress, null
                        )
                        mProgressDirectory =
                            lDialogDoubleProgress.findViewById<ProgressBar>(R.id.dialog_double_progress_progress_1)
                        mTextViewDirectory =
                            lDialogDoubleProgress.findViewById<TextView>(R.id.dialog_double_progress_text_1)
                        mProgressSubDirectory =
                            lDialogDoubleProgress.findViewById<ProgressBar>(R.id.dialog_double_progress_progress_2)
                        mTextViewSubDirectory =
                            lDialogDoubleProgress.findViewById<TextView>(R.id.dialog_double_progress_text_2)
                        lBuilder.setView(lDialogDoubleProgress)
                        lBuilder.setCancelable(false)
                        lBuilder.setNegativeButton("Cancel") { _, _ -> // TODO : Strings
                            stopDeleting()
                        }
                        mContextActivity.mDeletingInfoDialog = lBuilder.create()
                        mContextActivity.mDeletingInfoDialog!!.show()
                    })
                }

                override fun onProgressDirectory(iDirectoryProgress: Int,
                                                 iTotalDirectories: Int,
                                                 iDirectoryName: String?
                ) {
                    mHandler.post(Runnable {
                        mTextViewDirectory!!.text = iDirectoryName
                        mProgressDirectory!!.max = iTotalDirectories
                        mProgressDirectory!!.progress = iDirectoryProgress
                    })
                }

                override fun onProgressSubDirectory(iSubDirectoryProgress: Int,
                                                    iTotalSubDirectories: Int,
                                                    iSubDirectoryName: String?
                ) {
                    mHandler.post(Runnable {
                        mTextViewSubDirectory!!.text = iSubDirectoryName
                        mProgressSubDirectory!!.max = iTotalSubDirectories
                        mProgressSubDirectory!!.progress = iSubDirectoryProgress
                    })
                }

                override fun onRightAccessFail(iFTPFile: FTPFile) {
                    super.onRightAccessFail(iFTPFile)
                    mHandler.post(Runnable {
                        val lBuilder: AlertDialog.Builder = AlertDialog.Builder(
                            (mContextActivity)
                        )
                        lBuilder.setTitle("Permission error") // TODO : strings
                        val lDialogDeleteError: View = View.inflate(
                            mContextActivity, R.layout.dialog_delete_error, null
                        )
                        val lCheckBox: CheckBox =
                            lDialogDeleteError.findViewById<CheckBox>(R.id.dialog_delete_error_checkbox)
                        val lTextView: TextView =
                            lDialogDeleteError.findViewById<TextView>(R.id.dialog_delete_error_text)
                        lTextView.text = "Remember this choice : " // TODO : strings
                        lBuilder.setView(lDialogDeleteError)
                        val lMessage: String =
                            ("\nNot enough permissions to delete this " + (if (iFTPFile.isDirectory) "directory \n" else "file \n") + iFTPFile.name)
                        lBuilder.setMessage(lMessage)
                        lBuilder.setCancelable(false)
                        lBuilder.setPositiveButton(
                            "Ignore",
                            object : DialogInterface.OnClickListener {
                                override fun onClick(iDialog: DialogInterface, iWhich: Int
                                ) {
                                    iDialog.dismiss()
                                    mContextActivity.mFTPServices!!.setDeletingByPassRightErrors(
                                        lCheckBox.isChecked
                                    )
                                    mContextActivity.mFTPServices!!.resumeDeleting()
                                }
                            })
                        lBuilder.setNegativeButton(
                            "Stop",
                            object : DialogInterface.OnClickListener {
                                override fun onClick(iDialog: DialogInterface, iWhich: Int
                                ) {
                                    iDialog.dismiss()
                                    stopDeleting()
                                }
                            })
                        mContextActivity.mDeletingErrorDialog = lBuilder.create()
                        mContextActivity.mDeletingErrorDialog!!.show()
                    })
                }

                override fun onFinish() {
                    successDelete()
                }

                override fun onFail(iFTPFile: FTPFile?) {
                    super.onFail(iFTPFile)
                    mHandler.post(Runnable {
                        val lBuilder: AlertDialog.Builder = AlertDialog.Builder(
                            (mContextActivity)
                        )
                        lBuilder.setTitle("Delete error") // TODO : strings
                        val lDialogDeleteError: View = View.inflate(
                            mContextActivity, R.layout.dialog_delete_error, null
                        )
                        val lCheckBox: CheckBox =
                            lDialogDeleteError.findViewById<CheckBox>(R.id.dialog_delete_error_checkbox)
                        val lTextView: TextView =
                            lDialogDeleteError.findViewById<TextView>(R.id.dialog_delete_error_text)
                        lTextView.text = "Remember this choice : " // TODO : strings
                        lBuilder.setView(lDialogDeleteError)
                        val lMessage: String =
                            ("\nImpossible to delete the " + (if (iFTPFile!!.isDirectory) "directory \n" else "file \n") + iFTPFile.name)
                        lBuilder.setMessage(lMessage)
                        lBuilder.setCancelable(false)
                        lBuilder.setPositiveButton(
                            "Ignore",
                            object : DialogInterface.OnClickListener {
                                override fun onClick(iDialog: DialogInterface, iWhich: Int
                                ) {
                                    iDialog.dismiss()
                                    mContextActivity.mDeletingErrorDialog = null
                                    if (mContextActivity.mDeletingInfoDialog != null) mContextActivity.mDeletingInfoDialog!!.show()
                                    mContextActivity.mFTPServices!!.setDeletingByPassFailErrors(
                                        lCheckBox.isChecked
                                    )
                                    mContextActivity.mFTPServices!!.resumeDeleting()
                                }
                            })
                        lBuilder.setNegativeButton(
                            "Stop",
                            object : DialogInterface.OnClickListener {
                                override fun onClick(iDialog: DialogInterface, iWhich: Int
                                ) {
                                    iDialog.dismiss()
                                    stopDeleting()
                                }
                            })
                        if (mContextActivity.mDeletingInfoDialog != null) mContextActivity.mDeletingInfoDialog!!.hide()
                        mContextActivity.mDeletingErrorDialog = lBuilder.create()
                        mContextActivity.mDeletingErrorDialog!!.show()
                    })
                }
            })
    }

    private fun successDelete() {
        mHandler!!.sendEmptyMessage(NavigationActivity.Companion.NAVIGATION_ORDER_DISMISS_DIALOGS)
        mHandler.sendEmptyMessage(NavigationActivity.Companion.NAVIGATION_MESSAGE_DELETE_FINISHED)
    }

    private fun stopDeleting() {
        mContextActivity!!.mCurrentAdapter!!.setSelectionMode(false)
        mContextActivity.mFTPServices!!.abortDeleting()
        mHandler!!.sendEmptyMessage(NavigationActivity.Companion.NAVIGATION_ORDER_DISMISS_DIALOGS)
        mHandler.sendEmptyMessage(NavigationActivity.Companion.NAVIGATION_MESSAGE_DELETE_FINISHED)
    }

    companion object {
        private val TAG: String = "NAVIGATION DELETE"
    }
}
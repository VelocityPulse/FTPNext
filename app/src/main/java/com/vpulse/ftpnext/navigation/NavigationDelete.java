package com.vpulse.ftpnext.navigation;

import android.content.DialogInterface;
import android.os.Handler;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.vpulse.ftpnext.R;

import org.apache.commons.net.ftp.FTPFile;

import static com.vpulse.ftpnext.navigation.NavigationActivity.NAVIGATION_MESSAGE_DELETE_FINISHED;
import static com.vpulse.ftpnext.navigation.NavigationActivity.NAVIGATION_ORDER_DISMISS_DIALOGS;
import static com.vpulse.ftpnext.navigation.NavigationActivity.NAVIGATION_ORDER_REFRESH_DATA;
import static com.vpulse.ftpnext.navigation.NavigationActivity.NAVIGATION_ORDER_SELECTED_MODE_OFF;

public class NavigationDelete {

    private final static String TAG = "NAVIGATION DELETE";

    private final NavigationActivity mContextActivity;
    private final Handler mHandler;

    private NavigationDelete() throws InstantiationException {
        mContextActivity = null;
        mHandler = null;
        throw new InstantiationException("Constructor not allowed");
    }

    protected NavigationDelete(NavigationActivity iContextActivity, Handler iHandler) {
        mContextActivity = iContextActivity;
        mHandler = iHandler;
    }

    protected void onResume() {

    }

    protected void createDialogDeleteSelection() {
        final FTPFile[] lSelectedFiles = mContextActivity.mCurrentAdapter.getSelection();

        if (lSelectedFiles.length == 0)
            mContextActivity.createDialogError("Select something.").show();
        else {
            mContextActivity.mDeletingInfoDialog = new AlertDialog.Builder(mContextActivity)
                    .setTitle("Deleting :") // TODO : Strings
                    .setMessage("Are you sure to delete the selection ? (" + lSelectedFiles.length + " files)")
                    .setPositiveButton("yes", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface iDialog, int iWhich) {
                            iDialog.dismiss();
                            deleteFile(lSelectedFiles);
                        }
                    })
                    .setNegativeButton("cancel", null)
                    .show();
        }
    }

    private void deleteFile(FTPFile[] iSelectedFiles) {
        mHandler.sendEmptyMessage(NAVIGATION_ORDER_SELECTED_MODE_OFF);
        mContextActivity.mFTPServices.deleteFiles(iSelectedFiles, mContextActivity.mFTPServices.new OnDeleteListener() {

            ProgressBar mProgressDirectory;
            ProgressBar mProgressSubDirectory;
            TextView mTextViewDirectory;
            TextView mTextViewSubDirectory;

            @Override
            public void onStartDelete() {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        final AlertDialog.Builder lBuilder = new AlertDialog.Builder(mContextActivity);
                        lBuilder.setTitle("Deleting"); // TODO : strings

                        View lDialogDoubleProgress = View.inflate(mContextActivity, R.layout.dialog_double_progress, null);
                        mProgressDirectory = lDialogDoubleProgress.findViewById(R.id.dialog_double_progress_progress_1);
                        mTextViewDirectory = lDialogDoubleProgress.findViewById(R.id.dialog_double_progress_text_1);
                        mProgressSubDirectory = lDialogDoubleProgress.findViewById(R.id.dialog_double_progress_progress_2);
                        mTextViewSubDirectory = lDialogDoubleProgress.findViewById(R.id.dialog_double_progress_text_2);

                        lBuilder.setView(lDialogDoubleProgress);
                        lBuilder.setCancelable(false);
                        lBuilder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() { // TODO : Strings
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                stopDeleting();
                            }
                        });
                        mContextActivity.mDeletingInfoDialog = lBuilder.create();
                        mContextActivity.mDeletingInfoDialog.show();
                    }
                });
            }

            @Override
            public void onProgressDirectory(final int iDirectoryProgress, final int iTotalDirectories, final String iDirectoryName) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mTextViewDirectory.setText(iDirectoryName);
                        mProgressDirectory.setMax(iTotalDirectories);
                        mProgressDirectory.setProgress(iDirectoryProgress);
                    }
                });
            }

            @Override
            public void onProgressSubDirectory(final int iSubDirectoryProgress, final int iTotalSubDirectories, final String iSubDirectoryName) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mTextViewSubDirectory.setText(iSubDirectoryName);
                        mProgressSubDirectory.setMax(iTotalSubDirectories);
                        mProgressSubDirectory.setProgress(iSubDirectoryProgress);
                    }
                });
            }

            @Override
            public void onRightAccessFail(final FTPFile iFTPFile) {
                super.onRightAccessFail(iFTPFile);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        final AlertDialog.Builder lBuilder = new AlertDialog.Builder(mContextActivity);
                        lBuilder.setTitle("Permission error"); // TODO : strings
                        final View lDialogDeleteError = View.inflate(mContextActivity, R.layout.dialog_delete_error, null);
                        final CheckBox lCheckBox = lDialogDeleteError.findViewById(R.id.dialog_delete_error_checkbox);
                        final TextView lTextView = lDialogDeleteError.findViewById(R.id.dialog_delete_error_text);

                        lTextView.setText("Remember this choice : "); // TODO : strings
                        lBuilder.setView(lDialogDeleteError);
                        String lMessage =
                                "\nNot enough permissions to delete this " +
                                        (iFTPFile.isDirectory() ? "directory \n" : "file \n") +
                                        iFTPFile.getName();
                        lBuilder.setMessage(lMessage);
                        lBuilder.setCancelable(false);
                        lBuilder.setPositiveButton("Ignore", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface iDialog, int iWhich) {
                                iDialog.dismiss();
                                mContextActivity.mFTPServices.setDeletingByPassRightErrors(lCheckBox.isChecked());
                                mContextActivity.mFTPServices.resumeDeleting();
                            }
                        });

                        lBuilder.setNegativeButton("Stop", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface iDialog, int iWhich) {
                                iDialog.dismiss();
                                stopDeleting();
                            }
                        });
                        mContextActivity.mDeletingErrorDialog = lBuilder.create();
                        mContextActivity.mDeletingErrorDialog.show();
                    }
                });
            }

            @Override
            public void onFinish() {
                successDelete();
            }

            @Override
            public void onFail(final FTPFile iFTPFile) {
                super.onFail(iFTPFile);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        final AlertDialog.Builder lBuilder = new AlertDialog.Builder(mContextActivity);
                        lBuilder.setTitle("Delete error"); // TODO : strings
                        final View lDialogDeleteError = View.inflate(mContextActivity, R.layout.dialog_delete_error, null);
                        final CheckBox lCheckBox = lDialogDeleteError.findViewById(R.id.dialog_delete_error_checkbox);
                        final TextView lTextView = lDialogDeleteError.findViewById(R.id.dialog_delete_error_text);

                        lTextView.setText("Remember this choice : "); // TODO : strings
                        lBuilder.setView(lDialogDeleteError);
                        String lMessage =
                                "\nImpossible to delete the " +
                                        (iFTPFile.isDirectory() ? "directory \n" : "file \n") +
                                        iFTPFile.getName();
                        lBuilder.setMessage(lMessage);
                        lBuilder.setCancelable(false);
                        lBuilder.setPositiveButton("Ignore", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface iDialog, int iWhich) {
                                iDialog.dismiss();
                                mContextActivity.mDeletingErrorDialog = null;
                                if (mContextActivity.mDeletingInfoDialog != null)
                                    mContextActivity.mDeletingInfoDialog.show();
                                mContextActivity.mFTPServices.setDeletingByPassFailErrors(lCheckBox.isChecked());
                                mContextActivity.mFTPServices.resumeDeleting();
                            }
                        });

                        lBuilder.setNegativeButton("Stop", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface iDialog, int iWhich) {
                                iDialog.dismiss();
                                stopDeleting();
                            }
                        });
                        if (mContextActivity.mDeletingInfoDialog != null)
                            mContextActivity.mDeletingInfoDialog.hide();

                        mContextActivity.mDeletingErrorDialog = lBuilder.create();
                        mContextActivity.mDeletingErrorDialog.show();
                    }
                });
            }
        });
    }

    private void successDelete() {
        mHandler.sendEmptyMessage(NAVIGATION_ORDER_DISMISS_DIALOGS);
        mHandler.sendEmptyMessage(NAVIGATION_ORDER_REFRESH_DATA);
        mHandler.sendEmptyMessage(NAVIGATION_MESSAGE_DELETE_FINISHED);
    }

    private void stopDeleting() {
        mContextActivity.mCurrentAdapter.setSelectionMode(false);
        mContextActivity.mFTPServices.abortDeleting();
        mHandler.sendEmptyMessage(NAVIGATION_ORDER_DISMISS_DIALOGS);
        mHandler.sendEmptyMessage(NAVIGATION_ORDER_REFRESH_DATA);
        mHandler.sendEmptyMessage(NAVIGATION_MESSAGE_DELETE_FINISHED);
    }
}
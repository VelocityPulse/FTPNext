package com.vpulse.ftpnext.ftpnavigation;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.support.v4.text.HtmlCompat;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Spanned;
import android.view.MotionEvent;
import android.view.View;
import android.widget.CheckBox;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import com.vpulse.ftpnext.R;
import com.vpulse.ftpnext.adapters.NarrowTransferAdapter;
import com.vpulse.ftpnext.commons.Utils;
import com.vpulse.ftpnext.core.ExistingFileAction;
import com.vpulse.ftpnext.core.LoadDirection;
import com.vpulse.ftpnext.core.LogManager;
import com.vpulse.ftpnext.core.PreferenceManager;
import com.vpulse.ftpnext.database.DataBase;
import com.vpulse.ftpnext.database.PendingFileTable.PendingFile;
import com.vpulse.ftpnext.ftpservices.FTPLogManager;
import com.vpulse.ftpnext.ftpservices.FTPServices;
import com.vpulse.ftpnext.ftpservices.FTPTransfer;

import org.apache.commons.net.ftp.FTPFile;

import java.util.ArrayList;
import java.util.List;

import static com.vpulse.ftpnext.ftpnavigation.FTPNavigationActivity.NAVIGATION_MESSAGE_DOWNLOAD_FINISHED;
import static com.vpulse.ftpnext.ftpnavigation.FTPNavigationActivity.NAVIGATION_ORDER_DISMISS_DIALOGS;
import static com.vpulse.ftpnext.ftpnavigation.FTPNavigationActivity.NAVIGATION_ORDER_SELECTED_MODE_OFF;

public class NavigationTransfer {

    private final static String TAG = "NAVIGATION DOWNLOAD";

    private final ArrayList<FTPTransfer> mFTPTransferList;
    private final FTPNavigationActivity mContextActivity;
    private final FTPTransfer.OnTransferListener mUniversalTransferListener;
    private final Handler mHandler;

    private NarrowTransferAdapter mNarrowTransferAdapter;

    private boolean mCanAutoScrollInLogView;
    private PendingFile[] mPendingFiles;

    private NavigationTransfer() throws InstantiationException {
        mHandler = null;
        mContextActivity = null;
        mFTPTransferList = null;
        mUniversalTransferListener = null;
        throw new InstantiationException("Constructor not allowed");
    }

    protected NavigationTransfer(FTPNavigationActivity iContextActivity, Handler iHandler) {
        mHandler = iHandler;
        mContextActivity = iContextActivity;
        mFTPTransferList = new ArrayList<>();
        mUniversalTransferListener = initializeUniversalTransferListener();
    }

    protected void onResume() {
        if (mFTPTransferList.size() > 0 && mContextActivity.mIsShowingDownload) {
            mNarrowTransferAdapter.notifyDataSetChanged();
        }
    }

    protected void createDialogUploadSelection(Uri[] iUris) {
        if (iUris.length == 0)
            mContextActivity.createDialogError("Select something").show();
        else
            showUploadConfirmation(iUris);
    }

    protected void createDialogDownloadSelection() {
        final FTPFile[] lSelectedFiles = mContextActivity.mCurrentAdapter.getSelection();

        if (lSelectedFiles.length == 0)
            mContextActivity.createDialogError("Select something.").show();
        else
            showDownloadConfirmation(lSelectedFiles);
    }

    private void indexesFilesForDownload(final FTPFile[] iSelectedFiles) {
        LogManager.info(TAG, "Download file");
        mHandler.sendEmptyMessage(NAVIGATION_ORDER_SELECTED_MODE_OFF);

        mContextActivity.mFTPServices.indexingPendingFilesProcedure(iSelectedFiles, new FTPServices.OnIndexingPendingFilesListener() {

            TextView mIndexingFolderText;
            TextView mIndexingFileText;

            @Override
            public void onStart() {
                LogManager.info(TAG, "Indexing listener : On start");
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {

                        final AlertDialog.Builder lBuilder = new AlertDialog.Builder(mContextActivity);
                        View mIndexingPendingFilesView = View.inflate(mContextActivity,
                                R.layout.dialog_indexing_progress, null);

                        mIndexingFolderText = mIndexingPendingFilesView.findViewById(R.id.dialog_indexing_folder);
                        mIndexingFileText = mIndexingPendingFilesView.findViewById(R.id.dialog_indexing_file);

                        lBuilder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface iDialog, int iWhich) {
                                iDialog.dismiss();
                                mContextActivity.mFTPServices.abortIndexingPendingFiles();
                                mContextActivity.mHandler.sendEmptyMessage(NAVIGATION_MESSAGE_DOWNLOAD_FINISHED);
                            }
                        });

                        lBuilder.setCancelable(false);
                        lBuilder.setView(mIndexingPendingFilesView);
                        lBuilder.setMessage("Indexing files :"); // TODO : strings
                        mContextActivity.mIndexingPendingFilesDialog = lBuilder.create();
                        mContextActivity.mIndexingPendingFilesDialog.show();
                    }
                });
            }

            @Override
            public void onFetchingFolder(final String iPath) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mIndexingFolderText.setText(iPath);
                    }
                });
            }

            @Override
            public void onNewIndexedFile(final PendingFile iPendingFile) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mIndexingFileText.setText(iPendingFile.getName());
                    }
                });
            }

            @Override
            public void onResult(boolean isSuccess, PendingFile[] iPendingFiles) {
                LogManager.info(TAG, "Indexing : On result");
                if (!isSuccess)
                    return;

                DataBase.getPendingFileDAO().deleteAll(); // TODO : DATA BASE RESET HERE
                DataBase.getPendingFileDAO().add(iPendingFiles);

                if (mContextActivity.mIndexingPendingFilesDialog != null) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mContextActivity.mIndexingPendingFilesDialog.cancel();
                        }
                    });
                }

                DownloadFiles(iPendingFiles);
            }
        });
    }

    private PendingFile[] indexesFilesForUpload(final Uri[] iUris) {
        List<PendingFile> lPendingFileList = new ArrayList<>();

        for (Uri lItem : iUris) {
            String lLocalAbsolutePath = Utils.getRealPathFromURI(mContextActivity, lItem);
            String lNameOnly;
            String lLocalPathOnly;
            String lRemotePathOnly;

            if (lLocalAbsolutePath.contains("/")) {
                lNameOnly = lLocalAbsolutePath.substring(lLocalAbsolutePath.lastIndexOf("/") + 1);
                lLocalPathOnly = lLocalAbsolutePath.substring(0, lLocalAbsolutePath.lastIndexOf("/"));
                lLocalPathOnly += "/";
            } else {
                lNameOnly = lLocalAbsolutePath;
                lLocalPathOnly = lLocalAbsolutePath;
            }

            lRemotePathOnly = mContextActivity.mFTPServices.getCurrentDirectoryPath();

            lPendingFileList.add(new PendingFile(
                    mContextActivity.mFTPServer.getDataBaseId(),
                    LoadDirection.UPLOAD,
                    false,
                    lNameOnly,
                    lRemotePathOnly,
                    lLocalPathOnly,
                    PreferenceManager.getExistingFileAction()
            ));
        }
        return lPendingFileList.toArray(new PendingFile[0]);
    }

    private void uploadFiles(final Uri[] iUris) {
        LogManager.info(TAG, "Upload files");

        destroyAllTransferConnections();
        mPendingFiles = indexesFilesForUpload(iUris);

        createNarrowTransferDialog(mPendingFiles, LoadDirection.UPLOAD);

        int lI = -1;
        int lMaxSimultaneousDownload = PreferenceManager.getMaxTransfers();
        while (++lI < lMaxSimultaneousDownload && lI < mPendingFiles.length)
            createNewFTPTransfer(LoadDirection.UPLOAD);
    }

    private void DownloadFiles(final PendingFile[] iPendingFiles) {
        LogManager.info(TAG, "Download files");

        destroyAllTransferConnections();
        mPendingFiles = iPendingFiles;

        createNarrowTransferDialog(mPendingFiles, LoadDirection.DOWNLOAD);

        int lI = -1;
        int lMaxSimultaneousDownload = PreferenceManager.getMaxTransfers();
        while (++lI < lMaxSimultaneousDownload && lI < mPendingFiles.length)
            createNewFTPTransfer(LoadDirection.DOWNLOAD);
    }

    private void createNarrowTransferDialog(final PendingFile[] iPendingFiles, final LoadDirection iLoadDirection) {
        mHandler.post(new Runnable() {
            @SuppressLint("ClickableViewAccessibility")
            @Override
            public void run() {
                final AlertDialog.Builder lBuilder = new AlertDialog.Builder(mContextActivity);

                View lDownloadingDialogView = View.inflate(mContextActivity,
                        R.layout.dialog_download_progress, null);

                RecyclerView lNarrowTransferRecyclerView = lDownloadingDialogView.findViewById(R.id.narrow_transfer_recycler_view);
                lNarrowTransferRecyclerView.setLayoutManager(new LinearLayoutManager(mContextActivity));

                final TextView lLogView = lDownloadingDialogView.findViewById(R.id.narrow_transfer_log_view);
                final ScrollView lScrollView = lDownloadingDialogView.findViewById(R.id.narrow_transfer_scroll_view);
                lScrollView.setSmoothScrollingEnabled(true);
                mCanAutoScrollInLogView = true;

                lLogView.setOnTouchListener(new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        if (event.getAction() == MotionEvent.ACTION_DOWN)
                            mCanAutoScrollInLogView = false;
                        return false;
                    }

                });
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    lScrollView.setOnScrollChangeListener(new View.OnScrollChangeListener() {
                        @Override
                        public void onScrollChange(View iV, int iScrollX, int iScrollY, int iOldScrollX, int iOldScrollY) {
                            if (!lScrollView.canScrollVertically(1)) {
                                mCanAutoScrollInLogView = true;
                            }
                        }
                    });
                }
                final FTPLogManager.OnNewFTPLogColored lOnNewFTPLogColored = new FTPLogManager.OnNewFTPLogColored() {

                    int mCount = 0;
                    String mCompleteLog = "";

                    @Override
                    public void onNewFTPLogColored(final String iLog) {
                        mCount++;

                        if (mCount > 150)
                            mCompleteLog = mCompleteLog.substring(mCompleteLog.indexOf("<br/>") + 5);
                        mCompleteLog += "<br/>" + iLog;
                        final Spanned s = HtmlCompat.fromHtml(mCompleteLog, HtmlCompat.FROM_HTML_MODE_LEGACY);

                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                lLogView.setText(s);
                                if (mCanAutoScrollInLogView)
                                    lScrollView.fullScroll(ScrollView.FOCUS_DOWN);
                            }
                        });
                    }
                };
                FTPLogManager.subscribeOnNewFTPLogColored(lOnNewFTPLogColored);

                if (mPendingFiles.length > 1) {
                    DividerItemDecoration lDividerItemDecoration = new DividerItemDecoration(
                            mContextActivity, DividerItemDecoration.VERTICAL);
                    lNarrowTransferRecyclerView.addItemDecoration(lDividerItemDecoration);
                }

                mNarrowTransferAdapter = new NarrowTransferAdapter(iPendingFiles, mContextActivity);
                lNarrowTransferRecyclerView.setAdapter(mNarrowTransferAdapter);

                lBuilder.setNegativeButton("Cancel", null);

//                lBuilder.setNeutralButton("Background", new DialogInterface.OnClickListener() {
//                    @Override
//                    public void onClick(DialogInterface iDialog, int iWhich) {
//                        iDialog.dismiss();
//                    }
//                });

                lBuilder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        destroyAllTransferConnections();
                        mContextActivity.mIsShowingDownload = false;
                        FTPLogManager.unsubscribeOnNewFTPLogColored(lOnNewFTPLogColored);
                        mContextActivity.mHandler.sendEmptyMessage(NAVIGATION_MESSAGE_DOWNLOAD_FINISHED);
                    }
                });

                lBuilder.setCancelable(false);
                lBuilder.setView(lDownloadingDialogView);
                if (iLoadDirection == LoadDirection.DOWNLOAD)
                    lBuilder.setTitle("Downloading ..."); // TODO : strings
                else if (iLoadDirection == LoadDirection.UPLOAD)
                    lBuilder.setTitle("Uploading ...");
                mContextActivity.mDownloadingDialog = lBuilder.create();
                mContextActivity.mDownloadingDialog.show();
                mContextActivity.mIsShowingDownload = true;
            }
        });
    }

    private void createNewFTPTransfer(final LoadDirection iLoadDirection) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                final FTPTransfer lFTPTransfer = new FTPTransfer(mContextActivity.mFTPServer.getDataBaseId());
                mFTPTransferList.add(lFTPTransfer);

                if (iLoadDirection == LoadDirection.DOWNLOAD)
                    lFTPTransfer.downloadFiles(mPendingFiles, mUniversalTransferListener);
                else if (iLoadDirection == LoadDirection.UPLOAD)
                    lFTPTransfer.uploadFiles(mPendingFiles, mUniversalTransferListener);
            }
        });
    }

    private void createExistingFileDialog(final PendingFile iPendingFile) {
        final AlertDialog.Builder lBuilder = new AlertDialog.Builder(mContextActivity);

        View lAskExistingFileAction = View.inflate(mContextActivity, R.layout.dialog_existing_file_action, null);

        final RadioGroup lRadioGroup = lAskExistingFileAction.findViewById(R.id.existing_action_radio_group);
        final CheckBox lDoNotAskAgainCheckBox = lAskExistingFileAction.findViewById(R.id.existing_action_do_not_ask_again);

        lBuilder.setView(lAskExistingFileAction);
        lBuilder.setCancelable(false);
        lBuilder.setTitle("File already existing..."); // TODO : strings
        lBuilder.setMessage(iPendingFile.getName());

        lBuilder.setPositiveButton("continue", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface iDialog, int iWhich) {
                iDialog.dismiss();

                handleExistingFileAction(
                        lRadioGroup.getCheckedRadioButtonId(),
                        lDoNotAskAgainCheckBox.isChecked(),
                        iPendingFile);
            }
        });

        lBuilder.setNegativeButton("cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface iDialog, int iWhich) {
                mHandler.sendEmptyMessage(NAVIGATION_ORDER_DISMISS_DIALOGS);
                mContextActivity.mDownloadingDialog.dismiss();

                destroyAllTransferConnections();
            }
        });

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mContextActivity.mChooseExistingFileAction = lBuilder.create();
                mContextActivity.mChooseExistingFileAction.show();
            }
        });
    }

    private void handleExistingFileAction(int iCheckedButtonId, boolean iCheckedBox,
                                          PendingFile iPendingFile) {
        ExistingFileAction lExistingFileAction = ExistingFileAction.REPLACE_FILE;

        switch (iCheckedButtonId) {
            case R.id.existing_action_ignore:
                lExistingFileAction = ExistingFileAction.IGNORE;
                break;
            case R.id.existing_action_resume:
                lExistingFileAction = ExistingFileAction.RESUME_FILE_TRANSFER;
                break;
            case R.id.existing_action_replace:
                lExistingFileAction = ExistingFileAction.REPLACE_FILE;
                break;
            case R.id.existing_action_replace_if_size_diff:
                lExistingFileAction = ExistingFileAction.REPLACE_IF_SIZE_IS_DIFFERENT;
                break;
            case R.id.existing_action_replace_if_more_recent:
                lExistingFileAction = ExistingFileAction.REPLACE_IF_FILE_IS_MORE_RECENT;
                break;
            case R.id.existing_action_replace_if_size_diff_or_more_recent:
                lExistingFileAction = ExistingFileAction.REPLACE_IF_SIZE_IS_DIFFERENT_OR_FILE_IS_MORE_RECENT;
                break;
            case R.id.existing_action_rename:
                lExistingFileAction = ExistingFileAction.RENAME_FILE;
                break;
        }

        if (iCheckedBox) {
            for (PendingFile lItem : mPendingFiles) {
                if (lItem.getExistingFileAction() == ExistingFileAction.NOT_DEFINED)
                    lItem.setExistingFileAction(lExistingFileAction);
            }
        } else {
            iPendingFile.setExistingFileAction(lExistingFileAction);
        }
        FTPTransfer.notifyExistingFileActionIsDefined();
    }

    private FTPTransfer.OnTransferListener initializeUniversalTransferListener() {
        return new FTPTransfer.OnTransferListener() {
            @Override
            public void onConnected(PendingFile iPendingFile) {

            }

            @Override
            public void onConnectionLost(final PendingFile iPendingFile) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mNarrowTransferAdapter.updatePendingFileData(iPendingFile);
                    }
                });
            }

            @Override
            public void onNewFileSelected(final PendingFile iPendingFile) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mNarrowTransferAdapter.updatePendingFileData(iPendingFile);
                    }
                });
            }

            @Override
            public void onTransferProgress(final PendingFile iPendingFile, final long iProgress, final long iSize) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mNarrowTransferAdapter.updatePendingFileData(iPendingFile);
                    }
                });
            }

            @Override
            public void onTransferSuccess(final PendingFile iPendingFile) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        DataBase.getPendingFileDAO().delete(iPendingFile);
                        mNarrowTransferAdapter.addPendingFileToRemove(iPendingFile);
                    }
                });
            }

            @Override
            public void onRightAccessFail(PendingFile iPendingFile) {

            }

            @Override
            public void onExistingFile(final PendingFile iPendingFile) {
                createExistingFileDialog(iPendingFile);
            }

            @Override
            public void onFail(final PendingFile iPendingFile) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mNarrowTransferAdapter.showError(iPendingFile);
                        DataBase.getPendingFileDAO().delete(iPendingFile);
                    }
                });
            }

            @Override
            public void onStop(FTPTransfer iFTPTransfer) {
                mFTPTransferList.remove(iFTPTransfer);

                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!mContextActivity.mSuccessDialog.isShowing() &&
                                mNarrowTransferAdapter.getItemCountOmitPendingFile() == 0) {
                            mContextActivity.mDownloadingDialog.dismiss();
                            showSuccessTransfer();
                        }
                    }
                });
            }
        };
    }

    private void showSuccessTransfer() {
        String lMessage = "All files has been transferred"; // TODO : Strings

        mContextActivity.mSuccessDialog = Utils.createSuccessAlertDialog(mContextActivity, lMessage);
        mContextActivity.mSuccessDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                mContextActivity.mHandler.sendEmptyMessage(NAVIGATION_MESSAGE_DOWNLOAD_FINISHED);
            }
        });
        mContextActivity.mSuccessDialog.show();
    }

    private void destroyAllTransferConnections() {
        for (FTPTransfer lItem : mFTPTransferList)
            lItem.destroyConnection();
        mFTPTransferList.clear();
    }

    private void showUploadConfirmation(final Uri[] iUris) {
        LogManager.debug(TAG, "Show upload confirmation");
        AlertDialog.Builder lBuilder = new AlertDialog.Builder(mContextActivity)
                .setTitle("Uploading") // TODO : Strings
                .setMessage("Do you confirm the upload of " + iUris.length + " files ?")
                .setPositiveButton("yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface iDialog, int iWhich) {
                        iDialog.dismiss();
                        uploadFiles(iUris);
                    }
                })
                .setNegativeButton("cancel", null);

        mContextActivity.mIndexingPendingFilesDialog = lBuilder.create();
        mContextActivity.mIndexingPendingFilesDialog.show();
    }

    private void showDownloadConfirmation(final FTPFile[] iSelectedFiles) {
        AlertDialog.Builder lBuilder = new AlertDialog.Builder(mContextActivity)
                .setTitle("Downloading") // TODO : Strings
                .setMessage("Do you confirm the download of " + iSelectedFiles.length + " files ?")
                .setPositiveButton("yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface iDialog, int iWhich) {
                        iDialog.dismiss();
                        indexesFilesForDownload(iSelectedFiles);
                    }
                })
                .setNegativeButton("cancel", null);

        mContextActivity.mIndexingPendingFilesDialog = lBuilder.create();
        mContextActivity.mIndexingPendingFilesDialog.show();
    }
}
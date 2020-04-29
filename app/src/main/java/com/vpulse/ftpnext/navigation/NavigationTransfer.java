package com.vpulse.ftpnext.navigation;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.text.Spanned;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.text.HtmlCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.vpulse.ftpnext.R;
import com.vpulse.ftpnext.adapters.NarrowTransferAdapter;
import com.vpulse.ftpnext.commons.FileUtils;
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

import static com.vpulse.ftpnext.navigation.NavigationActivity.NAVIGATION_MESSAGE_TRANSFER_FINISHED;
import static com.vpulse.ftpnext.navigation.NavigationActivity.NAVIGATION_ORDER_DISMISS_DIALOGS;
import static com.vpulse.ftpnext.navigation.NavigationActivity.NAVIGATION_ORDER_SELECTED_MODE_OFF;

public class NavigationTransfer {

    private final static String TAG = "NAVIGATION DOWNLOAD";

    private final ArrayList<FTPTransfer> mFTPTransferList;
    private final NavigationActivity mContextActivity;
    private final FTPTransfer.OnTransferListener mUniversalTransferListener;
    private final Handler mHandler;

    private NarrowTransferAdapter mNarrowTransferAdapter;

    private boolean mCanAutoScrollInLogView;
    private PendingFile[] mPendingFiles;
    private int mPendingFileErrors;

    private NavigationTransfer() throws InstantiationException {
        mHandler = null;
        mContextActivity = null;
        mFTPTransferList = null;
        mUniversalTransferListener = null;
        throw new InstantiationException("Constructor not allowed");
    }

    protected NavigationTransfer(NavigationActivity iContextActivity, Handler iHandler) {
        mHandler = iHandler;
        mContextActivity = iContextActivity;
        mFTPTransferList = new ArrayList<>();
        mUniversalTransferListener = initializeUniversalTransferListener();
    }

    protected void onResume() {
        if (mFTPTransferList.size() > 0 && mContextActivity.mIsShowingTransfer) {
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

    private void indexFilesForDownload(final FTPFile[] iSelectedFiles) {
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
                        View lIndexingPendingFilesView = View.inflate(mContextActivity,
                                R.layout.dialog_indexing_progress, null);

                        mIndexingFolderText = lIndexingPendingFilesView.findViewById(R.id.dialog_indexing_folder);
                        mIndexingFileText = lIndexingPendingFilesView.findViewById(R.id.dialog_indexing_file);

                        lBuilder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface iDialog, int iWhich) {
                                iDialog.dismiss();
                                mContextActivity.mFTPServices.abortIndexingPendingFiles();
                                mContextActivity.mHandler.sendEmptyMessage(NAVIGATION_MESSAGE_TRANSFER_FINISHED);
                            }
                        });

                        lBuilder.setCancelable(false);
                        lBuilder.setView(lIndexingPendingFilesView);
                        lBuilder.setTitle("Indexing files :"); // TODO : strings
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

                downloadFiles(iPendingFiles);
            }
        });
    }

    private PendingFile[] indexFilesForUpload(final Uri[] iUris) {
        List<PendingFile> lPendingFileList = new ArrayList<>();

        for (Uri lItem : iUris) {
//            String lLocalAbsolutePath = Utils.getRealPathFromURI(mContextActivity, lItem);
            String lLocalAbsolutePath = FileUtils.getPathFromDocumentUri(mContextActivity, lItem);
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
        mPendingFiles = indexFilesForUpload(iUris);
        mPendingFileErrors = 0;

        createNarrowTransferDialog(mPendingFiles, LoadDirection.UPLOAD);

        int lI = -1;
        int lMaxSimultaneousDownload = PreferenceManager.getMaxTransfers();
        while (++lI < lMaxSimultaneousDownload && lI < mPendingFiles.length)
            createNewFTPTransfer(LoadDirection.UPLOAD);
    }

    private void downloadFiles(final PendingFile[] iPendingFiles) {
        LogManager.info(TAG, "Download files");

        destroyAllTransferConnections();
        mPendingFiles = iPendingFiles;
        mPendingFileErrors = 0;

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

                Button lSortButton = lDownloadingDialogView.findViewById(R.id.narrow_transfer_recycler_button_sort);

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
                mNarrowTransferAdapter.setSortButton(lSortButton);
                lNarrowTransferRecyclerView.setAdapter(mNarrowTransferAdapter);

                lBuilder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        destroyAllTransferConnections();
                        mContextActivity.mIsShowingTransfer = false;
                        FTPLogManager.unsubscribeOnNewFTPLogColored(lOnNewFTPLogColored);
                        mContextActivity.mHandler.sendEmptyMessage(NAVIGATION_MESSAGE_TRANSFER_FINISHED);
                    }
                });

//                lBuilder.setNeutralButton("Background", new DialogInterface.OnClickListener() {
//                    @Override
//                    public void onClick(DialogInterface iDialog, int iWhich) {
//                        iDialog.dismiss();
//                    }
//                });

                lBuilder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        FTPLogManager.unsubscribeOnNewFTPLogColored(lOnNewFTPLogColored);
                        destroyAllTransferConnections();
                    }
                });

                lBuilder.setCancelable(false);
                lBuilder.setView(lDownloadingDialogView);
                if (iLoadDirection == LoadDirection.DOWNLOAD)
                    lBuilder.setTitle("Downloading ..."); // TODO : strings
                else if (iLoadDirection == LoadDirection.UPLOAD)
                    lBuilder.setTitle("Uploading ...");
                mContextActivity.mTransferDialog = lBuilder.create();
                mContextActivity.mTransferDialog.show();
                mContextActivity.mIsShowingTransfer = true;
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
                mContextActivity.mTransferDialog.dismiss();

                destroyAllTransferConnections();
            }
        });

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mContextActivity.mChooseExistingFileActionDialog = lBuilder.create();
                mContextActivity.mChooseExistingFileActionDialog.show();
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
            public void onStateUpdateRequested(final PendingFile iPendingFile) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mNarrowTransferAdapter.updatePendingFileData(iPendingFile);
                    }
                });
            }

            @Override
            public void onExistingFile(final PendingFile iPendingFile) {
                createExistingFileDialog(iPendingFile);
            }

            @Override
            public void onFail(final PendingFile iPendingFile) {
                mPendingFileErrors++;

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

                if (mFTPTransferList.size() == 0) {

                    if (mPendingFileErrors == 0) {
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (!mContextActivity.mSuccessDialog.isShowing() &&
                                        mNarrowTransferAdapter.getItemCountOmitPendingFile() == 0) {
                                    mContextActivity.mTransferDialog.dismiss();
                                    showSuccessTransfer();
                                }
                            }
                        });
                    } else {
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                mContextActivity.mTransferDialog
                                        .getButton(DialogInterface.BUTTON_NEGATIVE)
                                        .setText("Finish"); // TODO : Strings
                            }
                        });
                    }
                }
            }
        };
    }

    private void showSuccessTransfer() {
        if (mContextActivity.isFinishing()) {
            LogManager.error(TAG, "Show success transfer called but isFinishing == true");
            return;
        }
        String lMessage = "All files has been transferred"; // TODO : Strings

        mContextActivity.mSuccessDialog = Utils.createSuccessAlertDialog(mContextActivity, lMessage);
        mContextActivity.mSuccessDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                destroyAllTransferConnections();
                mContextActivity.mIsShowingTransfer = false;
                mContextActivity.mHandler.sendEmptyMessage(NAVIGATION_MESSAGE_TRANSFER_FINISHED);
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
        mContextActivity.mIndexingPendingFilesDialog = Utils.createAlertDialog(
                mContextActivity,
                "Uploading", // TODO : Strings
                "Do you confirm the upload of " + iUris.length + " files ?",
                "yes",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface iDialog, int iWhich) {
                        iDialog.dismiss();
                        uploadFiles(iUris);
                    }
                },
                "cancel",
                null);

        mContextActivity.mIndexingPendingFilesDialog.show();
    }

    private void showDownloadConfirmation(final FTPFile[] iSelectedFiles) {
        mContextActivity.mIndexingPendingFilesDialog = Utils.createAlertDialog(
                mContextActivity,
                "Downloading", // TODO : Strings
                "Do you confirm the download of " + iSelectedFiles.length + " files ?",
                "yes",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface iDialog, int iWhich) {
                        iDialog.dismiss();
                        indexFilesForDownload(iSelectedFiles);
                    }
                },
                "cancel",
                null);

        mContextActivity.mIndexingPendingFilesDialog.show();
    }
}
package com.example.ftpnext.ftpservices;

import com.example.ftpnext.core.LogManager;
import com.example.ftpnext.database.DataBase;
import com.example.ftpnext.database.FTPServerTable.FTPServer;
import com.example.ftpnext.database.PendingFileTable.PendingFile;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class FTPTransfer extends AFTPConnection {

    private static final String TAG = "FTP TRANSFER";

    private static List<FTPTransfer> sFTPTransferInstances;

    private Thread mDownloadThread;

    public FTPTransfer(FTPServer iFTPServer) {
        super(iFTPServer);

        if (sFTPTransferInstances == null)
            sFTPTransferInstances = new ArrayList<>();
        sFTPTransferInstances.add(this);
    }

    public FTPTransfer(int iServerId) {
        super(iServerId);

        if (sFTPTransferInstances == null)
            sFTPTransferInstances = new ArrayList<>();
        sFTPTransferInstances.add(this);
    }

    public static FTPTransfer getFTPTransferInstance(int iServerId) {
        LogManager.info(TAG, "Get FTP transfer instance");
        if (sFTPTransferInstances == null)
            sFTPTransferInstances = new ArrayList<>();

        for (FTPTransfer lFTPTransfer : sFTPTransferInstances) {
            if (lFTPTransfer.getFTPServerId() == iServerId)
                return lFTPTransfer;
        }
        return null;
    }


    @Override
    public boolean isBusy() {
        return false;
    }

    public void downloadFiles(final PendingFile[] iSelection, @NotNull final AOnDownloadListener iAOnDownloadListener) {

//        if (isDownloading) {
//
//        }

//        mCreatePendingFilesThread

    }


    public abstract class AOnDownloadListener {

        public abstract void onStart();

        public void onStartNewFile(PendingFile iPendingFile) {
            iPendingFile.setStarted(true);
            DataBase.getPendingFileDAO().update(iPendingFile);
        }

        public abstract void onTotalPendingFileProgress(int iProgress, int iTotalPendingFile);

        public abstract void onDownloadProgress(PendingFile iPendingFile, int iProgress, int iSize);

        public void oNDownloadSuccess(PendingFile iPendingFile) {
            DataBase.getPendingFileDAO().delete(iPendingFile);
        }

        public abstract void onRightAccessFail(PendingFile iPendingFile);

        public abstract void onFail(PendingFile iPendingFile);

        public abstract void onStop();
    }
}

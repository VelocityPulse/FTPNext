package com.vpulse.ftpnext.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.service.notification.StatusBarNotification;

import androidx.annotation.Nullable;

import com.vpulse.ftpnext.MainActivity;
import com.vpulse.ftpnext.R;
import com.vpulse.ftpnext.commons.Utils;
import com.vpulse.ftpnext.core.AppConstants;
import com.vpulse.ftpnext.core.AppCore;
import com.vpulse.ftpnext.core.LoadDirection;
import com.vpulse.ftpnext.core.MessageEvent;
import com.vpulse.ftpnext.database.PendingFileTable.PendingFile;
import com.vpulse.ftpnext.ftpservices.FTPTransfer;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.util.HashSet;
import java.util.Set;

public class TransferService extends Service implements FTPTransfer.OnTransferListener {

    private static final String ACTION_CANCEL = "com.vpulse.ftpnext.action.cancel";

    private static boolean sStarted;
    private static TransferService sInstance;
    private Set<PendingFile> mPendingFileList;

    public static TransferService getInstance() {
        return sInstance;
    }

    public static boolean isStarted() {
        return sStarted;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent iIntent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sStarted = true;
        sInstance = this;
        mPendingFileList = new HashSet<>();
        EventBus.getDefault().register(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        sStarted = false;
        sInstance = null;
    }

    @Subscribe
    public void onEvent(MessageEvent iMessageEvent) {
        if (iMessageEvent == MessageEvent.NAVIGATION_ACTIVITY_CLOSING) {
            stopForeground(true);
            stopSelf();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        startForeground();

        return super.onStartCommand(intent, flags, startId);
    }

    public void pullPendingFiles() {
        mPendingFileList.addAll(AppCore.getPendingFilesHistory());
    }

    public void updateStatus() {
        if (getEndedPendingFile() == mPendingFileList.size()) {
            mPendingFileList.clear();
            stopForeground(true);
            stopSelf();
        }
    }

    private void startForeground() {
        Intent notificationIntent = new Intent(this, MainActivity.class);

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, 0);

        startForeground(1, getServiceNotification().build());
    }

    private Notification.Builder getServiceNotification() {
        pullPendingFiles();

        createChannelServiceNotification();

        Intent cancelIntent = new Intent(this, TransferServiceBroadcast.class);
        cancelIntent.setAction(ACTION_CANCEL);
        PendingIntent cancelPendingIntent =
                PendingIntent.getBroadcast(this, 0, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        int lFinishedDownload = getEndedPendingFile();

        Notification.Builder lBuild = new Notification.Builder(this)
                .setContentTitle("FTPNext Transfer in background")
                .setContentText("Download : " + lFinishedDownload + "/" + mPendingFileList.size())
                .setPriority(Notification.PRIORITY_HIGH)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .addAction(R.drawable.ic_cancel, "Cancel", cancelPendingIntent)
                .setCategory(Notification.CATEGORY_STATUS);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            lBuild.setChannelId(AppConstants.SERVICE_STATUS_NOTIFICATION_CHANNEL);
        }
        return lBuild;
    }

    private Notification.Builder getTransferNotification(PendingFile iPendingFile) {
        createChannelTransferNotification();

        int lSmallIcon = iPendingFile.getLoadDirection() == LoadDirection.DOWNLOAD ?
                R.drawable.ic_download_arrow : R.drawable.ic_upload_arrow;

        Notification.Builder lBuild = new Notification.Builder(this)
                .setGroupSummary(true)
                .setSmallIcon(R.mipmap.baseline_folder_gray)
                .setGroup(AppConstants.TRANSFER_PROGRESS_GROUP_KEY);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            lBuild.setChannelId(AppConstants.SERVICE_PROGRESS_NOTIFICATION_CHANNEL);
        }
        getNotificationManager().notify(AppConstants.SUMMARY_NOTIFICATION_ID, lBuild.build());

        lBuild = new Notification.Builder(this)
                .setContentTitle(iPendingFile.getName())
                .setContentText(Utils.humanReadableByteCount(iPendingFile.getSpeedInByte(), false))
                .setProgress(iPendingFile.getSize(), iPendingFile.getProgress(), false)
                .setSmallIcon(R.mipmap.baseline_folder_gray)
                .setPriority(Notification.PRIORITY_LOW)
                .setOngoing(true)
                .setGroup(AppConstants.TRANSFER_PROGRESS_GROUP_KEY)
                .setCategory(Notification.CATEGORY_PROGRESS);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            lBuild.setChannelId(AppConstants.SERVICE_PROGRESS_NOTIFICATION_CHANNEL);
        }
        return lBuild;
    }

    private void removeTransferNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StatusBarNotification[] lActiveNotifications = getNotificationManager().getActiveNotifications();

            for (StatusBarNotification lActiveNotification : lActiveNotifications) {
                if (lActiveNotification.getOverrideGroupKey())
            }
        }
    }

    private long getTotalSpeed() {
        long oTotalSpeed = 0;
        for (PendingFile lFile : mPendingFileList) {
            oTotalSpeed += lFile.getSpeedInByte();
        }
        return oTotalSpeed;
    }

    private int getEndedPendingFile() {
        int oFinishedDownload = 0;
        for (PendingFile lFile : mPendingFileList) {
            if (lFile.isFinished() || lFile.isAnError() || lFile.isStopped())
                oFinishedDownload++;
            else
                oFinishedDownload += 0;
        }
        return oFinishedDownload;
    }

    private void createChannelServiceNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationChannel channel = new NotificationChannel(
                    AppConstants.SERVICE_STATUS_NOTIFICATION_CHANNEL,
                    getString(R.string.service_status_notification),
                    NotificationManager.IMPORTANCE_HIGH);

//            channel.setDescription(description);

            NotificationManager lNotificationManager = getNotificationManager();
            lNotificationManager.createNotificationChannel(channel);
        }
    }

    private void createChannelTransferNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationChannel channel = new NotificationChannel(
                    AppConstants.SERVICE_PROGRESS_NOTIFICATION_CHANNEL,
                    getString(R.string.service_status_notification),
                    NotificationManager.IMPORTANCE_HIGH);

//            channel.setDescription(description);

            NotificationManager lNotificationManager = getNotificationManager();
            lNotificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onConnected(PendingFile iPendingFile) {

    }

    @Override
    public void onConnectionLost(PendingFile iPendingFile) {

    }

    @Override
    public void onTransferSuccess(PendingFile iPendingFile) {
        getNotificationManager().notify(AppConstants.SERVICE_STATUS_NOTIFICATION_ID, getServiceNotification().build());
        getNotificationManager().cancel(iPendingFile.hashCode());
    }

    @Override
    public void onStateUpdateRequested(PendingFile iPendingFile) {
        if (iPendingFile.isFinished() || iPendingFile.isAnError() || iPendingFile.isStopped())
            return;
        getNotificationManager().notify(iPendingFile.hashCode(), getTransferNotification(iPendingFile).build());
    }

    @Override
    public void onExistingFile(PendingFile iPendingFile) {

    }

    @Override
    public void onFail(PendingFile iPendingFile) {
        // setSmallIcon is not working

//        Notification.Builder lBuilder = getTransferNotification(iPendingFile);

//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//            lBuilder.setSmallIcon(
//                    Icon.createWithResource(this, R.drawable.ic_error_outline).setTint(getColor(R.color.lightError)));
//        }
//        lBuilder.setOngoing(false);

//        getNotificationManager().notify(iPendingFile.hashCode(), lBuilder.build());
        getNotificationManager().cancel(iPendingFile.hashCode());
    }

    @Override
    public void onStop(FTPTransfer iFTPTransfer) {
        if (iFTPTransfer.getCandidate() != null)
            getNotificationManager().cancel(iFTPTransfer.getCandidate().hashCode());

        updateStatus();
    }

    private NotificationManager getNotificationManager() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            return getSystemService(NotificationManager.class);
        } else {
            return (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        }
    }

    private PendingFile[] getPendingFileList() {
        return mPendingFileList.toArray(new PendingFile[0]);
    }

    public static class TransferServiceBroadcast extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null)
                return;
            switch (intent.getAction()) {
                case ACTION_CANCEL:
                    EventBus.getDefault().post(MessageEvent.CANCEL_ALL_TRANSFER_EVENT);
                    break;
                default:
            }
        }
    }
}

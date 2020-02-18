package com.example.ftpnext.adapters;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.example.ftpnext.R;
import com.example.ftpnext.core.LogManager;
import com.example.ftpnext.database.PendingFileTable.PendingFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;

public class NarrowTransferAdapter
        extends RecyclerView.Adapter<NarrowTransferAdapter.CustomItemViewAdapter> {

    private static final String TAG = "NARROW TRANSFER ADAPTER";
    private static final int MAX_REMOVE_REQUEST_IN_SEC = 15;
    private static final int REMOVE_BREAK_TIMER = 1000;
    private final List<PendingFile> mToRemovePendingFileList;
    private List<PendingFile> mPendingFileList;
    private List<CustomItemViewAdapter> mCustomItemViewAdapterList;

    private int mUpdateRequestedInSecond;
    private Timer mScheduleTimer;
    private long mTimer;

    public NarrowTransferAdapter(PendingFile[] iPendingFiles) {
        mPendingFileList = new ArrayList<>(Arrays.asList(iPendingFiles));
        mToRemovePendingFileList = new ArrayList<>();
        mCustomItemViewAdapterList = new ArrayList<>();
    }

    public NarrowTransferAdapter() {
        mPendingFileList = new ArrayList<>();
        mToRemovePendingFileList = new ArrayList<>();
        mCustomItemViewAdapterList = new ArrayList<>();
    }

    @NonNull
    @Override
    public CustomItemViewAdapter onCreateViewHolder(@NonNull ViewGroup iViewGroup, int iI) {

        LinearLayout lLayout = (LinearLayout) LayoutInflater.from(iViewGroup.getContext()).inflate(
                R.layout.list_item_narrow_transfer, iViewGroup, false);

        CustomItemViewAdapter lItem = new CustomItemViewAdapter(lLayout,
                (TextView) lLayout.findViewById(R.id.item_narrow_transfer_text),
                (TextView) lLayout.findViewById(R.id.item_narrow_transfer_speed),
                (ProgressBar) lLayout.findViewById(R.id.item_narrow_transfer_progress_bar));

        mCustomItemViewAdapterList.add(lItem);
        return lItem;
    }

    @Override
    public void onBindViewHolder(@NonNull CustomItemViewAdapter iCustomItemViewAdapter, int iPosition) {
//        LogManager.info(TAG, "On bind view holder");
        final PendingFile lPendingFile = mPendingFileList.get(iPosition);

        iCustomItemViewAdapter.mPendingFile = lPendingFile;

        if (!iCustomItemViewAdapter.mTextFileView.getText().equals(lPendingFile.getPath()))
            iCustomItemViewAdapter.mTextFileView.setText(lPendingFile.getPath());

        if (lPendingFile.isStarted()) {
            if (!iCustomItemViewAdapter.mMainLayout.isEnabled())
                iCustomItemViewAdapter.mMainLayout.setEnabled(true);

            if (iCustomItemViewAdapter.mProgressBar.getMax() != lPendingFile.getSize())
                iCustomItemViewAdapter.mProgressBar.setMax(lPendingFile.getSize());
            iCustomItemViewAdapter.mProgressBar.setProgress(lPendingFile.getProgress());

            if (!lPendingFile.isFinished()) {
                if (lPendingFile.getSpeedInKo() < 1000) {
                    String lSpeed = lPendingFile.getSpeedInKo() + " Ko/s";
                    iCustomItemViewAdapter.mTextSpeedView.setText(lSpeed);
                } else {
                    double lMoSpeed;
                    lMoSpeed = ((double) lPendingFile.getSpeedInKo()) / 1000d;
                    String lSpeed = lMoSpeed + " Mo/s";
                    iCustomItemViewAdapter.mTextSpeedView.setText(lSpeed);
                }
            } else
                iCustomItemViewAdapter.mTextSpeedView.setText("");
        } else {
            iCustomItemViewAdapter.mMainLayout.setEnabled(false);
            iCustomItemViewAdapter.mTextSpeedView.setText("");
            iCustomItemViewAdapter.mProgressBar.setProgress(lPendingFile.getProgress());
        }
    }

    @Override
    public int getItemCount() {
        return mPendingFileList.size();
    }

    public void updatePendingFileData(PendingFile iPendingFile) {
        for (CustomItemViewAdapter lItem : mCustomItemViewAdapterList) {
            if (lItem.mPendingFile == iPendingFile) {
                onBindViewHolder(lItem, mPendingFileList.indexOf(iPendingFile));
                return;
            }
        }
    }

    public void removePendingFile(PendingFile iPendingFile) {
        synchronized (mToRemovePendingFileList) {
            mToRemovePendingFileList.add(iPendingFile);
        }

        long lCurrentTimeMillis = System.currentTimeMillis();
        long lElapsedTime = lCurrentTimeMillis - mTimer;

        if (lElapsedTime > REMOVE_BREAK_TIMER) {

            synchronized (mToRemovePendingFileList) {

                int lIndex;
                for (PendingFile lItem : mToRemovePendingFileList) {
                    lIndex = mPendingFileList.indexOf(lItem);
                    mPendingFileList.remove(lItem);
                    notifyItemRemoved(lIndex);
                }

                mToRemovePendingFileList.clear();
            }

            mScheduleTimer = null;
            mTimer = lCurrentTimeMillis;
        }
    }


    static class CustomItemViewAdapter extends RecyclerView.ViewHolder {
        PendingFile mPendingFile;

        View mMainLayout;
        TextView mTextFileView;
        TextView mTextSpeedView;
        ProgressBar mProgressBar;

        public CustomItemViewAdapter(@NonNull View iMainLayout, TextView iTextFileView,
                                     TextView iTextSpeedView, ProgressBar iProgressBar) {
            super(iMainLayout);
            mMainLayout = iMainLayout;
            mTextFileView = iTextFileView;
            mTextSpeedView = iTextSpeedView;
            mProgressBar = iProgressBar;
        }
    }
}

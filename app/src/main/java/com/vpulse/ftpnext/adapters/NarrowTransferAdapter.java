package com.vpulse.ftpnext.adapters;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.vpulse.ftpnext.R;
import com.vpulse.ftpnext.core.LogManager;
import com.vpulse.ftpnext.database.PendingFileTable.PendingFile;

import java.util.ArrayList;
import java.util.List;

public class NarrowTransferAdapter
        extends RecyclerView.Adapter<NarrowTransferAdapter.CustomItemViewAdapter> {

    private static final String TAG = "NARROW TRANSFER ADAPTER";
    private static final int MAX_REMOVE_REQUEST_IN_SEC = 15;
    private static final int REMOVE_BREAK_TIMER = 1000;
    private final List<PendingFileItem> mToRemovePendingFileItemList;
    private final List<PendingFileItem> mPendingFileItemList;
    private List<CustomItemViewAdapter> mCustomItemViewAdapterList;

    private int mUpdateRequestedInSecond;
    private long mTimer;

    public NarrowTransferAdapter(PendingFile[] iPendingFiles) {
        mPendingFileItemList = new ArrayList<>();
        mToRemovePendingFileItemList = new ArrayList<>();
        mCustomItemViewAdapterList = new ArrayList<>();

        for (PendingFile lItem : iPendingFiles)
            mPendingFileItemList.add(new PendingFileItem(lItem));
    }

    public NarrowTransferAdapter() {
        mPendingFileItemList = new ArrayList<>();
        mToRemovePendingFileItemList = new ArrayList<>();
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
                (ProgressBar) lLayout.findViewById(R.id.item_narrow_transfer_progress_bar),
                (ProgressBar) lLayout.findViewById(R.id.item_narrow_transfer_loading),
                (ImageView) lLayout.findViewById(R.id.item_narrow_transfer_error));

        mCustomItemViewAdapterList.add(lItem);
        return lItem;
    }

    @Override
    public void onBindViewHolder(@NonNull CustomItemViewAdapter iCustomItemViewAdapter, int iPosition) {
        iCustomItemViewAdapter.mPendingFileItem = mPendingFileItemList.get(iPosition);
        final PendingFile lPendingFile = iCustomItemViewAdapter.mPendingFileItem.mPendingFile;

        if (lPendingFile.isAnError())
            LogManager.debug(TAG, "NEW BINDING : " + lPendingFile.isAnError());

        // Set text name
        if (!iCustomItemViewAdapter.mTextFileView.getText().equals(lPendingFile.getPath()))
            iCustomItemViewAdapter.mTextFileView.setText(lPendingFile.getPath());

        if (lPendingFile.isStarted()) {
            // Useless
//            if (!iCustomItemViewAdapter.mMainLayout.isEnabled())
//                iCustomItemViewAdapter.mMainLayout.setEnabled(true);

            // Set progress bar
            if (iCustomItemViewAdapter.mProgressBar.getMax() != lPendingFile.getSize())
                iCustomItemViewAdapter.mProgressBar.setMax(lPendingFile.getSize());
            iCustomItemViewAdapter.mProgressBar.setProgress(lPendingFile.getProgress());

            // Set speed text
            if (!lPendingFile.isFinished() && lPendingFile.isConnected()) {
                iCustomItemViewAdapter.mLoading.setVisibility(View.INVISIBLE);
                iCustomItemViewAdapter.mTextSpeedView.setVisibility(View.VISIBLE);

                if (lPendingFile.getSpeedInKo() < 1000) {
                    String lSpeed = lPendingFile.getSpeedInKo() + " Ko/s";
                    iCustomItemViewAdapter.mTextSpeedView.setText(lSpeed);
                } else {
                    double lMoSpeed;
                    lMoSpeed = ((double) lPendingFile.getSpeedInKo()) / 1000d;
                    String lSpeed = lMoSpeed + " Mo/s";
                    iCustomItemViewAdapter.mTextSpeedView.setText(lSpeed);
                }
            } else {
                iCustomItemViewAdapter.mTextSpeedView.setText("");
                iCustomItemViewAdapter.mLoading.setVisibility(View.VISIBLE);
                iCustomItemViewAdapter.mTextSpeedView.setVisibility(View.GONE);
            }
        } else {
            iCustomItemViewAdapter.mMainLayout.setEnabled(false);
            iCustomItemViewAdapter.mTextSpeedView.setText("");
            iCustomItemViewAdapter.mProgressBar.setProgress(lPendingFile.getProgress());
        }
    }

    @Override
    public int getItemCount() {
        return mPendingFileItemList.size();
    }

    public void updatePendingFileData(PendingFile iPendingFile) {
        for (CustomItemViewAdapter lItem : mCustomItemViewAdapterList) {
            if (lItem.mPendingFileItem.mPendingFile == iPendingFile) {
                onBindViewHolder(lItem, mPendingFileItemList.indexOf(lItem.mPendingFileItem));
                return;
            }
        }
    }

    public void removePendingFile(PendingFile iPendingFile) {
        synchronized (mToRemovePendingFileItemList) {
            for (PendingFileItem lItem : mPendingFileItemList) {
                if (lItem.mPendingFile == iPendingFile)
                    mToRemovePendingFileItemList.add(lItem);
            }
        }

        long lCurrentTimeMillis = System.currentTimeMillis();
        long lElapsedTime = lCurrentTimeMillis - mTimer;

        if (lElapsedTime > REMOVE_BREAK_TIMER) {

            synchronized (mToRemovePendingFileItemList) {

                int lIndex;
                for (PendingFileItem lItem : mToRemovePendingFileItemList) {
                    lIndex = mPendingFileItemList.indexOf(lItem);
                    mPendingFileItemList.remove(lItem);
                    notifyItemRemoved(lIndex);
                }

                mToRemovePendingFileItemList.clear();
            }
            mTimer = lCurrentTimeMillis;
        }
    }

    public void showErrorAndRemove(PendingFile iPendingFile) {
        LogManager.info(TAG, "Show error and remove");

        // Guard
        PendingFileItem lPendingFileItem = null;
        for (PendingFileItem lItem : mPendingFileItemList) {
            if (lItem.mPendingFile == iPendingFile)
                lPendingFileItem = lItem;
        }
        if (lPendingFileItem == null) {
            LogManager.error(TAG, "Parameter not findable in adapter list");
            return;
        }

        synchronized (mPendingFileItemList) {
            int lLastPosition = mPendingFileItemList.indexOf(lPendingFileItem);
            mPendingFileItemList.remove(lPendingFileItem);
            mPendingFileItemList.add(0, lPendingFileItem);

            notifyItemMoved(lLastPosition, 0);
        }
    }

    static class PendingFileItem {
        PendingFile mPendingFile;
        boolean mShallStartAnimationError;

        PendingFileItem(PendingFile iPendingFile) {
            mPendingFile = iPendingFile;
        }
    }


    static class CustomItemViewAdapter extends RecyclerView.ViewHolder {
        PendingFileItem mPendingFileItem;

        View mMainLayout;
        TextView mTextFileView;
        TextView mTextSpeedView;
        ProgressBar mProgressBar;
        ProgressBar mLoading;
        ImageView mImageView;

        public CustomItemViewAdapter(@NonNull View iMainLayout, TextView iTextFileView,
                                     TextView iTextSpeedView, ProgressBar iProgressBar,
                                     ProgressBar iLoading, ImageView iImageView) {
            super(iMainLayout);
            mMainLayout = iMainLayout;
            mTextFileView = iTextFileView;
            mTextSpeedView = iTextSpeedView;
            mProgressBar = iProgressBar;
            mLoading = iLoading;
            mImageView = iImageView;
        }
    }
}

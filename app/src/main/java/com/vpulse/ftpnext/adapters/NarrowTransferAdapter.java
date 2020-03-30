package com.vpulse.ftpnext.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.vpulse.ftpnext.R;
import com.vpulse.ftpnext.commons.Utils;
import com.vpulse.ftpnext.core.LogManager;
import com.vpulse.ftpnext.database.PendingFileTable.PendingFile;

import java.util.ArrayList;
import java.util.List;

// TODO : Bug of item layout when orientation changes many time (width become shorter)
public class NarrowTransferAdapter
        extends RecyclerView.Adapter<NarrowTransferAdapter.CustomItemViewHolder> {

    private static final String TAG = "NARROW TRANSFER ADAPTER";
    private static final int MAX_REMOVE_REQUEST_IN_SEC = 15;
    private static final int REMOVE_BREAK_TIMER = 1000;
    private final List<PendingFileItem> mToRemovePendingFileItemList;
    private final List<PendingFileItem> mPendingFileItemList;
    private final List<CustomItemViewHolder> mCustomItemViewHolderList;

    private Context mContext;

    private RecyclerView mRecyclerView;

    private int mUpdateRequestedInSecond;
    private long mTimer;

    public NarrowTransferAdapter(PendingFile[] iPendingFiles, Context iContext) {
        mPendingFileItemList = new ArrayList<>();
        mToRemovePendingFileItemList = new ArrayList<>();
        mCustomItemViewHolderList = new ArrayList<>();
        mContext = iContext;

        for (PendingFile lItem : iPendingFiles)
            mPendingFileItemList.add(new PendingFileItem(lItem));
    }

    public NarrowTransferAdapter(Context iContext) {
        mPendingFileItemList = new ArrayList<>();
        mToRemovePendingFileItemList = new ArrayList<>();
        mCustomItemViewHolderList = new ArrayList<>();

        mContext = iContext;
    }

    @SuppressLint("StaticFieldLeak")
    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView iRecyclerView) {
        super.onAttachedToRecyclerView(iRecyclerView);
        mRecyclerView = iRecyclerView;

        final Handler lHandler = new Handler();
        lHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mRecyclerView != null) {
                    removePendingFiles();
                    lHandler.postDelayed(this, REMOVE_BREAK_TIMER);
                }
            }
        });

        mRecyclerView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                freeAdapter();
            }
        });
    }

    private void freeAdapter() {
        mToRemovePendingFileItemList.clear();
        mPendingFileItemList.clear();
        mCustomItemViewHolderList.clear();
        mRecyclerView = null;
        mContext = null;
    }

    @NonNull
    @Override
    public CustomItemViewHolder onCreateViewHolder(@NonNull ViewGroup iViewGroup, int iI) {

        LinearLayout lLayout = (LinearLayout) LayoutInflater.from(iViewGroup.getContext()).inflate(
                R.layout.list_item_narrow_transfer, iViewGroup, false);

        CustomItemViewHolder lItem = new CustomItemViewHolder(
                lLayout,
                (TextView) lLayout.findViewById(R.id.item_narrow_transfer_text),
                (TextView) lLayout.findViewById(R.id.item_narrow_transfer_speed),
                (ProgressBar) lLayout.findViewById(R.id.item_narrow_transfer_progress_bar),
                (ProgressBar) lLayout.findViewById(R.id.item_narrow_transfer_loading),
                (ImageView) lLayout.findViewById(R.id.item_narrow_transfer_error));

        mCustomItemViewHolderList.add(lItem);
        return lItem;
    }

    @Override
    public void onBindViewHolder(@NonNull CustomItemViewHolder iCustomItemViewHolder, int iPosition) {
        final PendingFileItem lPendingFileItem = mPendingFileItemList.get(iPosition);
        final PendingFile lPendingFile = lPendingFileItem.mPendingFile;

        iCustomItemViewHolder.mPendingFileItem = lPendingFileItem;

        // Set text name
        String lName = lPendingFile.getRemotePath() + lPendingFile.getName();
        if (!iCustomItemViewHolder.mTextFileView.getText().equals(lName))
            iCustomItemViewHolder.mTextFileView.setText(lName);

        // Set progress bar
        if (iCustomItemViewHolder.mProgressBar.getMax() != lPendingFile.getSize())
            iCustomItemViewHolder.mProgressBar.setMax(lPendingFile.getSize());
        iCustomItemViewHolder.mProgressBar.setProgress(lPendingFile.getProgress());

        if (lPendingFile.isAnError()) {
            // Animation

            int lOffsetStart = (int) (lPendingFileItem.mTimeOfErrorNotified - System.currentTimeMillis());

            if (lOffsetStart < 4000) {
                Animation lFadeIn = new AlphaAnimation(0, 1);
                lFadeIn.setInterpolator(new DecelerateInterpolator());
                lFadeIn.setDuration(4000); // TODO : res
                lFadeIn.setStartOffset(lOffsetStart);
                iCustomItemViewHolder.mErrorImage.startAnimation(lFadeIn);
            }
            iCustomItemViewHolder.mTextSpeedView.setVisibility(View.INVISIBLE);
            iCustomItemViewHolder.mLoading.setVisibility(View.INVISIBLE);
            iCustomItemViewHolder.mErrorImage.setVisibility(View.VISIBLE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                LogManager.debug(TAG, "SET RED FOR " + lPendingFile.getName());
                iCustomItemViewHolder.mProgressBar.setProgressTintList(mContext.getColorStateList(R.color.error));
            }
        } else if (lPendingFile.isSelected()) {
            // Normal download update

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                iCustomItemViewHolder.mProgressBar.setProgressTintList(mContext.getColorStateList(R.color.primaryLight));
            }

            iCustomItemViewHolder.mErrorImage.clearAnimation();
            iCustomItemViewHolder.mErrorImage.setVisibility(View.INVISIBLE);

            // Set speed text
            if (!lPendingFile.isFinished() && lPendingFile.isConnected()) {
                iCustomItemViewHolder.mLoading.setVisibility(View.INVISIBLE);
                iCustomItemViewHolder.mTextSpeedView.setVisibility(View.VISIBLE);

                String lSpeed = Utils.humanReadableByteCount(lPendingFile.getSpeedInByte(), false);
                iCustomItemViewHolder.mTextSpeedView.setText(lSpeed);

            } else {
                iCustomItemViewHolder.mTextSpeedView.setText("");
                iCustomItemViewHolder.mLoading.setVisibility(View.VISIBLE);
                iCustomItemViewHolder.mTextSpeedView.setVisibility(View.INVISIBLE);
            }
        } else {
            // Not started

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                iCustomItemViewHolder.mProgressBar.setProgressTintList(mContext.getColorStateList(R.color.primaryLight));
            }

            iCustomItemViewHolder.mErrorImage.clearAnimation();
            iCustomItemViewHolder.mErrorImage.setVisibility(View.INVISIBLE);
            iCustomItemViewHolder.mLoading.setVisibility(View.INVISIBLE);
            iCustomItemViewHolder.mTextSpeedView.setVisibility(View.INVISIBLE);
            iCustomItemViewHolder.mTextSpeedView.setText("");
            iCustomItemViewHolder.mProgressBar.setProgress(lPendingFile.getProgress());
        }
    }

    /**
     * This function shouldn't be used to check if the Adapter is empty because it
     * doesn't take care about the items in the delete pending queue
     *
     * @return The direct number of item in the list.
     */
    @Override
    public int getItemCount() {
        return mPendingFileItemList.size();
    }

    /**
     * @return The number of item after the freeing of the remove pending file
     */
    public int getItemCountOmitPendingFile() {
        int oItemCount = 0;

        synchronized (mToRemovePendingFileItemList) {
            // No need to add a condition avoiding useless loop because
            // mPendingFileItemList should always stay little in this situation
            for (PendingFileItem lItem : mPendingFileItemList) {
                if (!mToRemovePendingFileItemList.contains(lItem))
                    oItemCount++;
            }
        }
        return oItemCount;
    }

    public void updatePendingFileData(PendingFile iPendingFile) {
        for (CustomItemViewHolder lItem : mCustomItemViewHolderList) {
            if (!lItem.mPendingFileItem.mHasBeenRemoved &&
                    lItem.mPendingFileItem.mPendingFile == iPendingFile &&
                    lItem.mMainLayout.getParent() != null) {
                onBindViewHolder(lItem, mPendingFileItemList.indexOf(lItem.mPendingFileItem));
                return;
            }
        }
    }

    public void addPendingFileToRemove(PendingFile iPendingFile) {
        synchronized (mToRemovePendingFileItemList) {
            for (PendingFileItem lItem : mPendingFileItemList) {
                if (lItem.mPendingFile == iPendingFile)
                    mToRemovePendingFileItemList.add(lItem);
            }
        }
    }

    private void removePendingFiles() {
        synchronized (mToRemovePendingFileItemList) {
            int lIndex;
            for (PendingFileItem lItem : mToRemovePendingFileItemList) {
                lIndex = mPendingFileItemList.indexOf(lItem);
                mPendingFileItemList.remove(lItem);
                notifyItemRemoved(lIndex);
                lItem.mHasBeenRemoved = true;
            }

            mToRemovePendingFileItemList.clear();
        }
    }

    public void showError(PendingFile iPendingFile) {
        PendingFileItem lPendingFileItem = null;
        for (PendingFileItem lItem : mPendingFileItemList) {
            if (lItem.mPendingFile == iPendingFile)
                lPendingFileItem = lItem;
        }
        if (lPendingFileItem == null) {
            LogManager.error(TAG, "Parameter not findable in adapter list");
            return;
        }

        if (lPendingFileItem.isErrorAlreadyProceed)
            return;

        int lLastPosition;
        int lNewPosition;
        synchronized (mPendingFileItemList) {
            lLastPosition = mPendingFileItemList.indexOf(lPendingFileItem);
            lNewPosition = 0;

            for (PendingFileItem lItem : mPendingFileItemList) {
                if (lItem.isErrorAlreadyProceed)
                    lNewPosition++;
                else
                    break;
            }
            lPendingFileItem.isErrorAlreadyProceed = true;

            if (lLastPosition != lNewPosition) {

                mPendingFileItemList.remove(lPendingFileItem);
                mPendingFileItemList.add(lNewPosition, lPendingFileItem);

                notifyItemMoved(lLastPosition, 0);
            }
        }
        lPendingFileItem.mTimeOfErrorNotified = (int) System.currentTimeMillis();
        notifyItemChanged(lNewPosition);

        // Scroll to 0 if it's necessary
        LinearLayoutManager layoutManager = ((LinearLayoutManager) mRecyclerView.getLayoutManager());
        int firstVisiblePosition = layoutManager.findFirstVisibleItemPosition();
        if (firstVisiblePosition == 0 && lNewPosition == 0)
            mRecyclerView.scrollToPosition(0);
    }

    public void notifyItemUnselected(PendingFile iPendingFile) {
        synchronized (mPendingFileItemList) {

            int lIndexOfPendingFile = -1;
            int lI = -1;
            int lMax = mPendingFileItemList.size();

            while (++lI < lMax) {
                if (mPendingFileItemList.get(lI).mPendingFile == iPendingFile) {
                    lIndexOfPendingFile = lI;
                    break;
                }
            }

            if (lIndexOfPendingFile == -1) {
                LogManager.error(TAG, "Picking index of PendingFileItem failed");
                return;
            }

            PendingFileItem lToReInsert = mPendingFileItemList.remove(lIndexOfPendingFile);
            lI = lMax + 1;
            while (--lI > 0) {
                if (!mPendingFileItemList.get(lI).mPendingFile.isSelected())
                    break;
            }
            mPendingFileItemList.add(lI, lToReInsert);
            notifyItemMoved(lIndexOfPendingFile, lI);
        }
    }

    public void notifyItemSelected(PendingFile iPendingFile) {
        synchronized (mPendingFileItemList) {

            int lIndexOfPendingFile = -1;
            int lI = -1;
            int lMax = mPendingFileItemList.size();

            while (++lI < lMax) {
                if (mPendingFileItemList.get(lI).mPendingFile == iPendingFile) {
                    lIndexOfPendingFile = lI;
                    break;
                }
            }

            if (lIndexOfPendingFile == -1) {
                LogManager.error(TAG, "Picking index of PendingFileItem failed");
                return;
            }

            PendingFileItem lToReInsert = mPendingFileItemList.remove(lIndexOfPendingFile);
            lI = lIndexOfPendingFile + 1;
            while (--lI > 0) {
                if (mPendingFileItemList.get(lI).mPendingFile.isSelected())
                    break;
            }
            mPendingFileItemList.add(lI, lToReInsert);
            notifyItemMoved(lIndexOfPendingFile, lI);
        }
    }

    static class PendingFileItem {
        PendingFile mPendingFile;
        boolean isErrorAlreadyProceed;
        int mTimeOfErrorNotified;
        boolean mHasBeenRemoved;

        PendingFileItem(PendingFile iPendingFile) {
            mPendingFile = iPendingFile;
        }
    }

    static class CustomItemViewHolder extends RecyclerView.ViewHolder {
        PendingFileItem mPendingFileItem;

        View mMainLayout;
        TextView mTextFileView;
        TextView mTextSpeedView;
        ProgressBar mProgressBar;
        ProgressBar mLoading;
        ImageView mErrorImage;

        public CustomItemViewHolder(@NonNull View iMainLayout, TextView iTextFileView,
                                    TextView iTextSpeedView, ProgressBar iProgressBar,
                                    ProgressBar iLoading, ImageView iErrorImage) {
            super(iMainLayout);
            mMainLayout = iMainLayout;
            mTextFileView = iTextFileView;
            mTextSpeedView = iTextSpeedView;
            mProgressBar = iProgressBar;
            mLoading = iLoading;
            mErrorImage = iErrorImage;
        }
    }
}

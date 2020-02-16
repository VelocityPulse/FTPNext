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

public class NarrowTransferAdapter
        extends RecyclerView.Adapter<NarrowTransferAdapter.CustomItemViewAdapter> {

    private static final String TAG = "NARROW TRANSFER ADAPTER";

    private List<PendingFile> mPendingFileList;

    public NarrowTransferAdapter(PendingFile[] iPendingFiles) {
        mPendingFileList = Arrays.asList(iPendingFiles);
    }

    public NarrowTransferAdapter() {
        mPendingFileList = new ArrayList<>();
    }

    @NonNull
    @Override
    public CustomItemViewAdapter onCreateViewHolder(@NonNull ViewGroup iViewGroup, int iI) {

        LinearLayout lLayout = (LinearLayout) LayoutInflater.from(iViewGroup.getContext()).inflate(
                R.layout.list_item_narrow_transfer, iViewGroup, false);

        return new CustomItemViewAdapter(lLayout,
                (TextView) lLayout.findViewById(R.id.item_narrow_transfer_text),
                (ProgressBar) lLayout.findViewById(R.id.item_narrow_transfer_progress_bar));
    }

    @Override
    public void onBindViewHolder(@NonNull CustomItemViewAdapter iCustomItemViewAdapter, int iPosition) {
        LogManager.info(TAG, "On bind view holder");
        final PendingFile lPendingFile = mPendingFileList.get(iPosition);

        // TODO : Continue on speed display

        if (!iCustomItemViewAdapter.mTextView.getText().equals(lPendingFile.getPath()))
            iCustomItemViewAdapter.mTextView.setText(lPendingFile.getPath());

        if (lPendingFile.isStarted()) {
            if (!iCustomItemViewAdapter.mMainLayout.isEnabled())
                iCustomItemViewAdapter.mMainLayout.setEnabled(true);

            if (iCustomItemViewAdapter.mProgressBar.getMax() != lPendingFile.getSize())
                iCustomItemViewAdapter.mProgressBar.setMax(lPendingFile.getSize());
            iCustomItemViewAdapter.mProgressBar.setProgress(lPendingFile.getProgress());
        } else {
            LogManager.debug(TAG, "Set enabled : false");
            iCustomItemViewAdapter.mMainLayout.setEnabled(false);
//            iCustomItemViewAdapter.mProgressBar.setProgress(0);
        }
    }

    @Override
    public int getItemCount() {
        return mPendingFileList.size();
    }

    public void updatePendingFile(PendingFile iPendingFile) {
        for (PendingFile lItem : mPendingFileList) {
            if (lItem.getDataBaseId() == iPendingFile.getDataBaseId()) {
//                lItem.updateContent(iPendingFile);

//                notifyDataSetChanged();
                notifyItemChanged(mPendingFileList.indexOf(lItem));
            }
        }
    }


    static class CustomItemViewAdapter extends RecyclerView.ViewHolder {
        View mMainLayout;
        TextView mTextView;
        ProgressBar mProgressBar;

        public CustomItemViewAdapter(@NonNull View iMainLayout,
                                     TextView iTextView, ProgressBar iProgressBar) {
            super(iMainLayout);
            mMainLayout = iMainLayout;
            mTextView = iTextView;
            mProgressBar = iProgressBar;
        }
    }
}

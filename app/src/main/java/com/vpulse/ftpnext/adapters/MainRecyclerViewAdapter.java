package com.vpulse.ftpnext.adapters;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.vpulse.ftpnext.R;
import com.vpulse.ftpnext.core.LogManager;
import com.vpulse.ftpnext.database.FTPServerTable.FTPServer;

import java.util.ArrayList;
import java.util.List;

public class MainRecyclerViewAdapter extends
        RecyclerView.Adapter<MainRecyclerViewAdapter.CustomItemViewAdapter> {

    private static final String TAG = "MAIN RECYCLER VIEW ADAPTER";
    private List<FTPServer> mItemList;
    private OnLongClickListener mLongClickListener;
    private OnClickListener mClickListener;
    private RecyclerView mRecyclerView;
    private Context mContext;

    public MainRecyclerViewAdapter(List<FTPServer> iItemList, RecyclerView iRecyclerView, Context iContext) {
        mItemList = iItemList;
        mRecyclerView = iRecyclerView;
        mContext = iContext;
    }

    public MainRecyclerViewAdapter(RecyclerView iRecyclerView, Context iContext) {
        mItemList = new ArrayList<>();
        mRecyclerView = iRecyclerView;
        mContext = iContext;
    }

    @NonNull
    @Override
    public CustomItemViewAdapter onCreateViewHolder(@NonNull ViewGroup iViewGroup, int iI) {
        LinearLayout lLayout = (LinearLayout) LayoutInflater.from(iViewGroup.getContext()).inflate(
                        R.layout.list_item_main, iViewGroup, false);

        return new CustomItemViewAdapter(lLayout,
                (TextView) lLayout.findViewById(R.id.main_recycler_item_main_text),
                (TextView) lLayout.findViewById(R.id.main_recycler_item_secondary_text));
    }

    @Override
    public void onBindViewHolder(@NonNull final CustomItemViewAdapter iCustomItemViewAdapter, int iPosition) {
        final FTPServer lServer = mItemList.get(iPosition);

        iCustomItemViewAdapter.mMainText.setText(lServer.getName());
        iCustomItemViewAdapter.mSecondaryText.setText(lServer.getServer());
        iCustomItemViewAdapter.mMainLayout.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (mLongClickListener != null)
                    mLongClickListener.onLongClick(lServer.getDataBaseId());
                return true;
            }
        });
        iCustomItemViewAdapter.mMainLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mClickListener != null)
                    mClickListener.onClick(lServer.getDataBaseId());
            }
        });

        Animation animation = AnimationUtils.loadAnimation(mRecyclerView.getContext(), R.anim.item_animation_fall_down);
        iCustomItemViewAdapter.itemView.startAnimation(animation);
    }

    @Override
    public int getItemCount() {
        return mItemList.size();
    }

    public void insertItem(FTPServer iItem) {
        if (mItemList == null) {
            LogManager.error(TAG, "Cannot insert an item if the list of items isn't initialized.");
            return;
        }
        mItemList.add(iItem);
        notifyItemRangeInserted(mItemList.size() + 1, 1);
    }

    public void insertItem(FTPServer iItem, int iPosition) {
        if (mItemList == null) {
            LogManager.error(TAG, "Cannot insert an item if the list of items isn't initialized.");
            return;
        }
        if (iPosition > mItemList.size())
            iPosition = mItemList.size();
        mItemList.add(iPosition, iItem);
        notifyItemRangeInserted(iPosition, 1);
    }

    public void updateItem(FTPServer iItem) {
        for (FTPServer lItem : mItemList) {
            if (lItem.getDataBaseId() == iItem.getDataBaseId()) {
                lItem.updateContent(iItem);
                notifyItemChanged(mItemList.indexOf(lItem));
                return;
            }
        }
    }

    public void removeItem(FTPServer iItem) {
        for (FTPServer lItem : mItemList) {
            if (lItem.getDataBaseId() == iItem.getDataBaseId()) {
                notifyItemRemoved(mItemList.indexOf(lItem));
                mItemList.remove(lItem);
                return;
            }
        }
    }

    public void removeItem(int iId) {
        for (FTPServer lItem : mItemList) {
            if (lItem.getDataBaseId() == iId) {
                notifyItemRemoved(mItemList.indexOf(lItem));
                mItemList.remove(lItem);
                return;
            }
        }
    }

    public void setData(List<FTPServer> iData) {
        if (iData == null) {
            LogManager.error(TAG, "Cannot set the data if the given parameter is null.");
            return;
        }
        mItemList = new ArrayList<>(iData);
        notifyDataSetChanged();
    }

    public void setOnLongClickListener(OnLongClickListener iListener) {
        mLongClickListener = iListener;
    }

    public void setOnClickListener(OnClickListener iListener) {
        mClickListener = iListener;
    }

    public interface OnLongClickListener {
        void onLongClick(int iServerID);
    }

    public interface OnClickListener {
        void onClick(int iServerID);
    }

    static class CustomItemViewAdapter extends RecyclerView.ViewHolder {
        View mMainLayout;
        TextView mMainText;
        TextView mSecondaryText;

        public CustomItemViewAdapter(@NonNull View iMainView, TextView iMainText,
                                     TextView iSecondaryText) {
            super(iMainView);
            mMainLayout = iMainView;
            mMainText = iMainText;
            mSecondaryText = iSecondaryText;
        }
    }
}
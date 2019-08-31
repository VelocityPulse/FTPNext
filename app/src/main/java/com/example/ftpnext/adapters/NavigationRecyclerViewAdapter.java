package com.example.ftpnext.adapters;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.ftpnext.R;
import com.example.ftpnext.commons.Utils;
import com.example.ftpnext.core.LogManager;

import org.apache.commons.net.ftp.FTPFile;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class NavigationRecyclerViewAdapter extends RecyclerView.Adapter<NavigationRecyclerViewAdapter.CustomItemViewAdapter> {

    private static final String TAG = "NAVIGATION RECYCLER VIEW ADAPTER";
    private List<FTPFile> mItemList;
    private List<CustomItemViewAdapter> mCustomItems;
    private Animation.AnimationListener mDisappearAnimationListener;
    private OnLongClickListener mLongClickListener;
    private OnClickListener mClickListener;
    private RecyclerView mRecyclerView;
    private Context mContext;
    private String mDirectoryPath;

    private NavigationRecyclerViewAdapter mNextAdapter;
    private NavigationRecyclerViewAdapter mPreviousAdapter;

    public NavigationRecyclerViewAdapter(List<FTPFile> iItemList, RecyclerView iRecyclerView, Context iContext, String iDirectoryPath, boolean iVisible) {
        mItemList = iItemList;
        mRecyclerView = iRecyclerView;
        mContext = iContext;
        mDirectoryPath = iDirectoryPath;
        mRecyclerView.setVisibility(iVisible ? View.VISIBLE : View.GONE);
        mCustomItems = new ArrayList<>();
    }

    public NavigationRecyclerViewAdapter(RecyclerView iRecyclerView, Context iContext, String iDirectoryPath, boolean iVisible) {
        mItemList = new ArrayList<>();
        mRecyclerView = iRecyclerView;
        mContext = iContext;
        mDirectoryPath = iDirectoryPath;
        mRecyclerView.setVisibility(iVisible ? View.VISIBLE : View.GONE);
        mCustomItems = new ArrayList<>();
    }

    private void initialize() {
        mDisappearAnimationListener = new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                mRecyclerView.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        };
    }

    @NonNull
    @Override
    public CustomItemViewAdapter onCreateViewHolder(@NonNull ViewGroup iViewGroup, int iI) {
        LinearLayout lLayout = (LinearLayout) LayoutInflater.
                from(iViewGroup.getContext()).inflate(R.layout.navigation_list_item, iViewGroup, false);

        CustomItemViewAdapter oViewHolder = new CustomItemViewAdapter(lLayout,
                (ImageView) lLayout.findViewById(R.id.navigation_recycler_item_left_draw),
                (TextView) lLayout.findViewById(R.id.navigation_recycler_item_main_text),
                (TextView) lLayout.findViewById(R.id.navigation_recycler_item_secondary_text),
                (TextView) lLayout.findViewById(R.id.navigation_recycler_item_third_text),
                (TextView) lLayout.findViewById(R.id.navigation_recycler_item_fourth));

        mCustomItems.add(oViewHolder);

        return oViewHolder;
    }

    @Override
    public void onBindViewHolder(@NonNull CustomItemViewAdapter iCustomItemViewAdapter, int iI) {
        final FTPFile lFTPItem = mItemList.get(iI);

        if (mClickListener != null) {
            iCustomItemViewAdapter.mMainLayout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View iV) {
                    mClickListener.onClick(lFTPItem);
                }
            });
        }
        if (mLongClickListener != null) {
            iCustomItemViewAdapter.mMainLayout.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View iV) {
                    mLongClickListener.onLongClick(lFTPItem);
                    return true;
                }
            });
        }

        if (lFTPItem.isDirectory())
            iCustomItemViewAdapter.mLeftImage.setImageResource(R.drawable.ic_outline_folder_24);
        else
            iCustomItemViewAdapter.mLeftImage.setImageResource(R.drawable.ic_outline_file_24);

        iCustomItemViewAdapter.mMainText.setText(lFTPItem.getName());
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yy hh:mm", Locale.FRANCE);
        iCustomItemViewAdapter.mSecondaryText.setText(dateFormat.format(lFTPItem.getTimestamp().getTime()));

        if (lFTPItem.hasPermission(FTPFile.USER_ACCESS, FTPFile.WRITE_PERMISSION))
            iCustomItemViewAdapter.mThirdText.setText("(Write access)");
        else if (lFTPItem.hasPermission(FTPFile.USER_ACCESS, FTPFile.READ_PERMISSION))
            iCustomItemViewAdapter.mThirdText.setText("(Read access)");
        else
            iCustomItemViewAdapter.mThirdText.setText("(No access)");

        if (lFTPItem.isDirectory())
            iCustomItemViewAdapter.mFourthText.setText("DIR");
        else
            iCustomItemViewAdapter.mFourthText.setText(Utils.humanReadableByteCount(lFTPItem.getSize(), true));

        LogManager.info(TAG, lFTPItem.getRawListing());
        // TODO continue with the listeners
    }

    @Override
    public int getItemCount() {
        return mItemList.size();
    }

    public void insertItem(FTPFile iItem) {
        if (mItemList == null) {
            LogManager.error(TAG, "Cannot insert an item if the list of items isn't initialized.");
            return;
        }
        mItemList.add(iItem);
        notifyItemRangeInserted(mItemList.size() + 1, 1);
    }

    public void insertItem(FTPFile iItem, int iPosition) {
        if (mItemList == null) {
            LogManager.error(TAG, "Cannot insert an item if the list of items isn't initialized.");
            return;
        }
        if (iPosition > mItemList.size())
            iPosition = mItemList.size();
        mItemList.add(iPosition, iItem);
        notifyItemRangeInserted(iPosition, 1);
    }

    public void updateItem(FTPFile iItem) {
        for (FTPFile lItem : mItemList) {
            if (lItem.getName().equals(iItem.getName())) {
                mItemList.add(iItem);
                notifyItemRemoved(mItemList.indexOf(lItem));
                return;
            }
        }
    }

    public void removeItem(FTPFile iItem) {
        for (FTPFile lItem : mItemList) {
            if (lItem.getName().equals(iItem.getName())) {
                notifyItemRemoved(mItemList.indexOf(lItem));
                mItemList.remove(lItem);
                return;
            }
        }
    }

    public void setData(FTPFile[] iData) {
        LogManager.info(TAG, "Set data");
        if (iData == null) {
            LogManager.error(TAG, "Cannot set the data if the given parameter is null.");
            return;
        }
        mItemList = new ArrayList<>(Arrays.asList(iData));
        notifyDataSetChanged();
    }

    public String getDirectoryPath() {
        return mDirectoryPath;
    }

    public void appearVertically() {
        Animation lAnimation = AnimationUtils.loadAnimation(mRecyclerView.getContext(), R.anim.recycler_animation_appear_vertically);
        lAnimation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                setItemsTransparent();
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                setItemsRipple();
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        mRecyclerView.startAnimation(lAnimation);
        mRecyclerView.setVisibility(View.VISIBLE);
    }

    public void appearOnRight() {
        Animation lAnimation = AnimationUtils.loadAnimation(mRecyclerView.getContext(), R.anim.recycler_animation_appear_on_right);
        lAnimation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                setItemsTransparent();
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                setItemsRipple();
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        mRecyclerView.startAnimation(lAnimation);
        mRecyclerView.setVisibility(View.VISIBLE);
    }

    public void appearOnLeft() {
        Animation lAnimation = AnimationUtils.loadAnimation(mRecyclerView.getContext(), R.anim.recycler_animation_appear_on_left);
        lAnimation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                setItemsTransparent();
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                setItemsRipple();
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });
        mRecyclerView.startAnimation(lAnimation);
        mRecyclerView.setVisibility(View.VISIBLE);
    }

    public void disappearOnRightAndDestroy(final Runnable iOnAnimationEnd) {
        Animation lAnimation = AnimationUtils.loadAnimation(mRecyclerView.getContext(), R.anim.recycler_animation_disappear_on_right);
        lAnimation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                setItemsTransparent();
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                iOnAnimationEnd.run();
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });
        mRecyclerView.startAnimation(lAnimation);
    }

    public void disappearOnLeft() {
        Animation lAnimation = AnimationUtils.loadAnimation(mRecyclerView.getContext(), R.anim.recycler_animation_disappear_on_left);
        lAnimation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                setItemsTransparent();
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                mRecyclerView.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });
        mRecyclerView.startAnimation(lAnimation);
    }

    public NavigationRecyclerViewAdapter getNextAdapter() {
        return mNextAdapter;
    }

    public void setNextAdapter(NavigationRecyclerViewAdapter iNextAdapter) {
        mNextAdapter = iNextAdapter;
    }

    public NavigationRecyclerViewAdapter getPreviousAdapter() {
        return mPreviousAdapter;
    }

    public void setPreviousAdapter(NavigationRecyclerViewAdapter iPreviousAdapter) {
        mPreviousAdapter = iPreviousAdapter;
    }

    public void setOnLongClickListener(OnLongClickListener iListener) {
        mLongClickListener = iListener;
    }

    public RecyclerView getRecyclerView() {
        return mRecyclerView;
    }

    private void setItemsTransparent() {
        for (CustomItemViewAdapter lItem : mCustomItems) {
            lItem.mMainLayout.setBackgroundResource(android.R.color.transparent);
        }
    }

    private void setItemsRipple() {
        for (CustomItemViewAdapter lItem : mCustomItems) {
            lItem.mMainLayout.setBackgroundResource(R.drawable.ripple_effect_primary);
        }
    }

    public void setOnClickListener(OnClickListener iListener) {
        mClickListener = iListener;
    }

    public interface OnLongClickListener {
        void onLongClick(FTPFile iFTPFile);
    }

    public interface OnClickListener {
        void onClick(FTPFile iFTPFile);
    }

    public class CustomItemViewAdapter extends RecyclerView.ViewHolder {
        View mMainLayout;
        ImageView mLeftImage;
        TextView mMainText;
        TextView mSecondaryText;
        TextView mThirdText;
        TextView mFourthText;

        public CustomItemViewAdapter(@NonNull View iMainView, ImageView iLeftImage, TextView iMainText, TextView iSecondaryText,
                                     TextView iThirdText, TextView iFourthText) {
            super(iMainView);
            mMainLayout = iMainView;
            mLeftImage = iLeftImage;
            mMainText = iMainText;
            mSecondaryText = iSecondaryText;
            mThirdText = iThirdText;
            mFourthText = iFourthText;
        }
    }
}

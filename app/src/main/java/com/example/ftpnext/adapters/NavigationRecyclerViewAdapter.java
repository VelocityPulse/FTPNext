package com.example.ftpnext.adapters;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Transformation;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.ftpnext.R;
import com.example.ftpnext.commons.Utils;
import com.example.ftpnext.core.LogManager;

import org.apache.commons.net.ftp.FTPFile;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class NavigationRecyclerViewAdapter extends RecyclerView.Adapter<NavigationRecyclerViewAdapter.CustomItemViewAdapter> {

    private static final String TAG = "NAVIGATION RECYCLER VIEW ADAPTER";
    private List<FTPFileItem> mFTPFileItems;
    private List<CustomItemViewAdapter> mCustomViewItems;
    private OnLongClickListener mLongClickListener;
    private OnClickListener mClickListener;
    private RecyclerView mRecyclerView;
    private FrameLayout mRecyclerSection;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private Context mContext;
    private String mDirectoryPath;
    private boolean mSelectionMode;

    private NavigationRecyclerViewAdapter mNextAdapter;
    private NavigationRecyclerViewAdapter mPreviousAdapter;

    public NavigationRecyclerViewAdapter(Context iContext, List<FTPFile> iFTPFileList, FrameLayout iRecyclerSection, RecyclerView iRecyclerView,
                                         SwipeRefreshLayout iSwipeRefreshLayout, String iDirectoryPath, boolean iVisible) {
        mContext = iContext;
        mRecyclerSection = iRecyclerSection;
        mRecyclerView = iRecyclerView;
        mSwipeRefreshLayout = iSwipeRefreshLayout;
        mDirectoryPath = iDirectoryPath;
        mSwipeRefreshLayout.setVisibility(iVisible ? View.VISIBLE : View.GONE);
        mCustomViewItems = new ArrayList<>();

        mFTPFileItems = new ArrayList<>();
        for (FTPFile lItem : iFTPFileList) {
            mFTPFileItems.add(new FTPFileItem(lItem));
        }
    }

    public NavigationRecyclerViewAdapter(Context iContext, FrameLayout iRecyclerSection, RecyclerView iRecyclerView,
                                         SwipeRefreshLayout iSwipeRefreshLayout, String iDirectoryPath, boolean iVisible) {
        mContext = iContext;
        mFTPFileItems = new ArrayList<>();
        mRecyclerSection = iRecyclerSection;
        mRecyclerView = iRecyclerView;
        mSwipeRefreshLayout = iSwipeRefreshLayout;
        mDirectoryPath = iDirectoryPath;
        mSwipeRefreshLayout.setVisibility(iVisible ? View.VISIBLE : View.GONE);
        mCustomViewItems = new ArrayList<>();
    }

    @NonNull
    @Override
    public CustomItemViewAdapter onCreateViewHolder(@NonNull ViewGroup iViewGroup, int iI) {
        LinearLayout lLayout = (LinearLayout) LayoutInflater.
                from(iViewGroup.getContext()).inflate(R.layout.navigation_list_item, iViewGroup, false);

        CustomItemViewAdapter oViewHolder = new CustomItemViewAdapter(
                lLayout,
                lLayout.findViewById(R.id.navigation_recycler_item_left_section),
                (ImageView) lLayout.findViewById(R.id.navigation_recycler_item_left_draw),
                (TextView) lLayout.findViewById(R.id.navigation_recycler_item_main_text),
                (TextView) lLayout.findViewById(R.id.navigation_recycler_item_secondary_text),
                (TextView) lLayout.findViewById(R.id.navigation_recycler_item_third_text),
                (TextView) lLayout.findViewById(R.id.navigation_recycler_item_fourth),
                (CheckBox) lLayout.findViewById(R.id.navigation_recycler_item_checkbox));
        mCustomViewItems.add(oViewHolder);

        return oViewHolder;
    }

    @Override
    public void onBindViewHolder(@NonNull CustomItemViewAdapter iCustomItemViewAdapter, int iI) {
        iCustomItemViewAdapter.mFTPFileItem = mFTPFileItems.get(iI);
        final FTPFile lFTPItem = iCustomItemViewAdapter.mFTPFileItem.mFTPFile;

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

        // TODO : Stings
        if (lFTPItem.hasPermission(FTPFile.USER_ACCESS, FTPFile.WRITE_PERMISSION) && lFTPItem.hasPermission(FTPFile.USER_ACCESS, FTPFile.READ_PERMISSION))
            iCustomItemViewAdapter.mThirdText.setText("(Read/Write)");
        else if (lFTPItem.hasPermission(FTPFile.USER_ACCESS, FTPFile.WRITE_PERMISSION))
            iCustomItemViewAdapter.mThirdText.setText("(Write)");
        else if (lFTPItem.hasPermission(FTPFile.USER_ACCESS, FTPFile.READ_PERMISSION))
            iCustomItemViewAdapter.mThirdText.setText("(Read)");
        else
            iCustomItemViewAdapter.mThirdText.setText("(No access)");

        if (lFTPItem.isDirectory())
            iCustomItemViewAdapter.mFourthText.setText("DIR");
        else
            iCustomItemViewAdapter.mFourthText.setText(Utils.humanReadableByteCount(lFTPItem.getSize(), true));

        iCustomItemViewAdapter.mCheckBox.setChecked(iCustomItemViewAdapter.mFTPFileItem.mChecked);

        if (mSelectionMode && !iCustomItemViewAdapter.mSelectedMode) {
            float lMarginLeft = 0;
            float lCheckBoxAlpha = 1;

            ViewGroup.MarginLayoutParams lSectionLayoutParams =
                    (ViewGroup.MarginLayoutParams) iCustomItemViewAdapter.mLeftSection.getLayoutParams();
            lSectionLayoutParams.leftMargin = (int) lMarginLeft;
            iCustomItemViewAdapter.mLeftSection.requestLayout();
            iCustomItemViewAdapter.mCheckBox.setAlpha(lCheckBoxAlpha);

            iCustomItemViewAdapter.mSelectedMode = mSelectionMode;
        } else if (!mSelectionMode && iCustomItemViewAdapter.mSelectedMode) {
            float lMarginLeft = mContext.getResources().getDimension(R.dimen.navigation_recycler_left_section_shift);
            float lCheckBoxAlpha = 0;

            ViewGroup.MarginLayoutParams lSectionLayoutParams =
                    (ViewGroup.MarginLayoutParams) iCustomItemViewAdapter.mLeftSection.getLayoutParams();
            lSectionLayoutParams.leftMargin = (int) lMarginLeft;
            iCustomItemViewAdapter.mLeftSection.requestLayout();
            iCustomItemViewAdapter.mCheckBox.setAlpha(lCheckBoxAlpha);

            iCustomItemViewAdapter.mSelectedMode = mSelectionMode;
        }

        LogManager.info(TAG, lFTPItem.getRawListing());
    }

    @Override
    public int getItemCount() {
        return mFTPFileItems.size();
    }

    public void insertItem(FTPFile iItem) {
        if (mFTPFileItems == null) {
            LogManager.error(TAG, "Cannot insert an item if the list of items isn't initialized.");
            return;
        }
        mFTPFileItems.add(new FTPFileItem(iItem));
        notifyItemRangeInserted(mFTPFileItems.size() + 1, 1);
    }

    public void insertItem(FTPFile iItem, int iPosition) {
        LogManager.info(TAG, "Insert item");
        if (mFTPFileItems == null) {
            LogManager.error(TAG, "Cannot insert an item if the list of items isn't initialized.");
            return;
        }
        if (iPosition > mFTPFileItems.size())
            iPosition = mFTPFileItems.size();
        mFTPFileItems.add(iPosition, new FTPFileItem(iItem));
        notifyItemRangeInserted(iPosition, 1);
    }

    public void updateItem(FTPFile iItem) {
        LogManager.info(TAG, "Update item");
        for (FTPFileItem lCustomItem : mFTPFileItems) {
            if (lCustomItem.equals(iItem)) {
                int lIndex = mFTPFileItems.indexOf(lCustomItem);
                mFTPFileItems.get(lIndex).mFTPFile = iItem;
                notifyItemChanged(lIndex);
                return;
            }
        }
    }

    public void removeItem(FTPFile iItem) {
        LogManager.info(TAG, "Remove item");
        for (FTPFileItem lItem : mFTPFileItems) {
            if (lItem.equals(iItem)) {
                mFTPFileItems.remove(lItem);
                notifyItemRemoved(mFTPFileItems.indexOf(lItem));
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
        mFTPFileItems.clear();
        for (FTPFile lItem : iData) {
            mFTPFileItems.add(new FTPFileItem(lItem));
        }
        notifyDataSetChanged();
    }

    public void switchCheckBox(FTPFile iFTPFile) {
        LogManager.info(TAG, "Switch checkbox");
        if (iFTPFile == null) {
            LogManager.error(TAG, "Given FTPFile is null");
            return;
        }
        for (CustomItemViewAdapter lItem : mCustomViewItems) {
            if (lItem.mFTPFileItem.equals(iFTPFile)) {
                lItem.mCheckBox.setChecked(!lItem.mCheckBox.isChecked());
                lItem.mFTPFileItem.mChecked = lItem.mCheckBox.isChecked();
            }
        }
    }

    public void setSelectedCheckBox(FTPFile iFTPFile, boolean iValue) {
        LogManager.info(TAG, "Set selected check box");
        if (iFTPFile == null) {
            LogManager.error(TAG, "Given FTPFile is null");
            return;
        }
        for (CustomItemViewAdapter lItem : mCustomViewItems) {
            if (lItem.mFTPFileItem.equals(iFTPFile)) {
                lItem.mCheckBox.setChecked(iValue);
                lItem.mFTPFileItem.mChecked = iValue;
            }
        }
    }

    public FTPFile[] getSelection() {
        List<FTPFile> oSelectedItems = new ArrayList<>();

        for (FTPFileItem lFTPFileItem : mFTPFileItems) {
            if (lFTPFileItem.mChecked)
                oSelectedItems.add(lFTPFileItem.mFTPFile);
        }
        return oSelectedItems.toArray(new FTPFile[0]);
    }

    public boolean isSelectedCheckBox(FTPFile iFTPFile) {
        LogManager.info(TAG, "Is selected checkbox");
        if (iFTPFile == null) {
            LogManager.error(TAG, "Given FTPFile is null");
            return false;
        }
        for (CustomItemViewAdapter lItem : mCustomViewItems) {
            if (lItem.mFTPFileItem.equals(iFTPFile))
                return lItem.mCheckBox.isChecked();
        }
        return false;
    }

    public void setSelectionMode(boolean iInSelectionMode) {
        if (mCustomViewItems.size() > 0) {

            final float lLeftSectionShift = mContext.getResources().getDimension(R.dimen.navigation_recycler_left_section_shift);

            if (iInSelectionMode && !mSelectionMode) {

                LeftSectionAnimation lLeftSectionAnimation = new LeftSectionAnimation(true, lLeftSectionShift);
                mRecyclerSection.startAnimation(lLeftSectionAnimation);
                mSelectionMode = true;
            } else if (mSelectionMode) {

                LeftSectionAnimation lLeftSectionAnimation = new LeftSectionAnimation(false, lLeftSectionShift);
                mRecyclerSection.startAnimation(lLeftSectionAnimation);
                mSelectionMode = false;

                for (FTPFileItem lFTPFileItem : mFTPFileItems)
                    lFTPFileItem.mChecked = false;
                for (CustomItemViewAdapter lCustomItemViewAdapter : mCustomViewItems)
                    lCustomItemViewAdapter.mCheckBox.setChecked(false);
            }
        }
    }

    int indexOfFTPFile(FTPFile iFTPFile) {
        int oIndex = -1;

        while (++oIndex < mFTPFileItems.size()) {
            if (mFTPFileItems.get(oIndex).equals(iFTPFile))
                return oIndex;
        }
        return -1;
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

    public String getDirectoryPath() {
        return mDirectoryPath;
    }

    public RecyclerView getRecyclerView() {
        return mRecyclerView;
    }

    public SwipeRefreshLayout getSwipeRefreshLayout() {
        return mSwipeRefreshLayout;
    }

    public boolean isInSelectionMode() {
        return mSelectionMode;
    }

    private void setItemsTransparent() {
        LogManager.info(TAG, "Set item transparent");
        for (CustomItemViewAdapter lItem : mCustomViewItems) {
            lItem.mMainLayout.setBackgroundResource(android.R.color.transparent);
        }
    }

    private void setItemsRipple() {
        LogManager.info(TAG, "Set item ripple");
        for (CustomItemViewAdapter lItem : mCustomViewItems) {
            lItem.mMainLayout.setBackgroundResource(R.drawable.ripple_effect_primary);
        }
    }



    public void appearVertically() {
        Animation lAnimation = AnimationUtils.loadAnimation(mContext, R.anim.recycler_animation_appear_vertically);
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
        mSwipeRefreshLayout.startAnimation(lAnimation);
        mSwipeRefreshLayout.setVisibility(View.VISIBLE);
    }

    public void appearOnRight() {
        Animation lAnimation = AnimationUtils.loadAnimation(mContext, R.anim.recycler_animation_appear_on_right);
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
        mSwipeRefreshLayout.startAnimation(lAnimation);
        mSwipeRefreshLayout.setVisibility(View.VISIBLE);
    }

    public void appearOnLeft() {
        Animation lAnimation = AnimationUtils.loadAnimation(mContext, R.anim.recycler_animation_appear_on_left);
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
        mSwipeRefreshLayout.startAnimation(lAnimation);
        mSwipeRefreshLayout.setVisibility(View.VISIBLE);
    }

    public void disappearOnRightAndDestroy(final Runnable iOnAnimationEnd) {
        Animation lAnimation = AnimationUtils.loadAnimation(mContext, R.anim.recycler_animation_disappear_on_right);
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
        mSwipeRefreshLayout.startAnimation(lAnimation);
    }

    public void disappearOnLeft() {
        Animation lAnimation = AnimationUtils.loadAnimation(mContext, R.anim.recycler_animation_disappear_on_left);
        lAnimation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                setItemsTransparent();
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                mSwipeRefreshLayout.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });
        mSwipeRefreshLayout.startAnimation(lAnimation);
    }

    public void setOnLongClickListener(OnLongClickListener iListener) {
        mLongClickListener = iListener;
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

    private class LeftSectionAnimation extends Animation {

        private float mLeftSectionShift;
        private boolean mIsAppearing;

        List<ViewGroup.MarginLayoutParams> mMarginLayoutParamsList;

        public LeftSectionAnimation(boolean iIsAppearing, float iLeftSectionShift) {
            mLeftSectionShift = -iLeftSectionShift;
            mIsAppearing = iIsAppearing;
            this.setDuration(mContext.getResources().getInteger(R.integer.recycler_animation_time));
            this.setInterpolator(new DecelerateInterpolator());

            mMarginLayoutParamsList = new ArrayList<>();
            for (CustomItemViewAdapter lItem : mCustomViewItems) {
                lItem.mSelectedMode = mIsAppearing;
                mMarginLayoutParamsList.add((ViewGroup.MarginLayoutParams) lItem.mLeftSection.getLayoutParams());
            }

            this.setAnimationListener(new AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                    LogManager.debug(TAG, "On animation start");
                    if (mIsAppearing) {
                        for (CustomItemViewAdapter lItem : mCustomViewItems)
                            lItem.mCheckBox.setVisibility(View.VISIBLE);
                    }
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    if (!mIsAppearing) {
                        for (CustomItemViewAdapter lItem : mCustomViewItems)
                            lItem.mCheckBox.setVisibility(View.INVISIBLE);
                    }
                }

                @Override
                public void onAnimationRepeat(Animation animation) {

                }
            });
        }

        @Override
        protected void applyTransformation(float iInterpolatedTime, Transformation iTransformation) {
            float lMarginLeft;
            float lCheckBoxAlpha;

            // Formula: (mToWidth - mFromWidth) * interpolatedTime + mFromWidth;
            if (mIsAppearing) {
                lMarginLeft = (0 - mLeftSectionShift) * iInterpolatedTime + mLeftSectionShift;
                lCheckBoxAlpha = iInterpolatedTime;
            } else {
                lMarginLeft = mLeftSectionShift * iInterpolatedTime;
                lCheckBoxAlpha = 1 - iInterpolatedTime;
            }

            for (ViewGroup.MarginLayoutParams lMarginLayoutParams : mMarginLayoutParamsList) {
                lMarginLayoutParams.leftMargin = (int) lMarginLeft;
            }

            for (CustomItemViewAdapter lItem : mCustomViewItems) {
                lItem.mLeftSection.requestLayout();
                lItem.mCheckBox.setAlpha(lCheckBoxAlpha);
            }

            super.applyTransformation(iInterpolatedTime, iTransformation);
        }
    }

    private class FTPFileItem {
        FTPFile mFTPFile;
        boolean mChecked;

        public FTPFileItem(FTPFile iFTPFile) {
            mFTPFile = iFTPFile;
        }

        @Override
        public boolean equals(Object iObj) {
            if (iObj == null)
                return false;
            if (iObj == this)
                return true;

            if (iObj instanceof FTPFile) {
                return mFTPFile.equals(iObj);
            }
            if (iObj instanceof FTPFileItem) {
                return mFTPFile.equals(((FTPFileItem) iObj).mFTPFile);
            }
            return false;
        }
    }

    protected class CustomItemViewAdapter extends RecyclerView.ViewHolder {
        FTPFileItem mFTPFileItem;

        LinearLayout mMainLayout;
        View mLeftSection;
        ImageView mLeftImage;
        TextView mMainText;
        TextView mSecondaryText;
        TextView mThirdText;
        TextView mFourthText;
        CheckBox mCheckBox;

        boolean mSelectedMode;

        public CustomItemViewAdapter(@NonNull LinearLayout iMainView, View iLeftSection, ImageView iLeftImage, TextView iMainText, TextView iSecondaryText,
                                     TextView iThirdText, TextView iFourthText, CheckBox iCheckBox) {
            super(iMainView);
            mMainLayout = iMainView;
            mLeftSection = iLeftSection;
            mLeftImage = iLeftImage;
            mMainText = iMainText;
            mSecondaryText = iSecondaryText;
            mThirdText = iThirdText;
            mFourthText = iFourthText;
            mCheckBox = iCheckBox;
        }
    }
}

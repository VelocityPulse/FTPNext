package com.vpulse.ftpnext.adapters;

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

import com.vpulse.ftpnext.R;
import com.vpulse.ftpnext.commons.Utils;
import com.vpulse.ftpnext.core.LogManager;

import org.apache.commons.net.ftp.FTPFile;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class NavigationRecyclerViewAdapter extends RecyclerView.Adapter<NavigationRecyclerViewAdapter.CustomItemViewAdapter> {

    private static final String TAG = "NAVIGATION RECYCLER VIEW ADAPTER";

    private List<FTPFileItem> mFTPFileItemList;
    private List<String> mNameList;
    private List<CustomItemViewAdapter> mCustomViewItemList;

    private OnLongClickListener mLongClickListener;
    private OnClickListener mClickListener;
    private OnFirstViewHolderCreation mOnFirstViewHolderCreation;

    private Context mContext;

    private NavigationRecyclerViewAdapter mNextAdapter;
    private NavigationRecyclerViewAdapter mPreviousAdapter;

    private RecyclerView mRecyclerView;
    private FrameLayout mRecyclerSection;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private String mDirectoryPath;

    private boolean mSelectionMode;
    private boolean mIsViewHolderCreationStarted;

    public NavigationRecyclerViewAdapter(Context iContext, List<FTPFile> iFTPFileList, FrameLayout iRecyclerSection, RecyclerView iRecyclerView,
                                         SwipeRefreshLayout iSwipeRefreshLayout, String iDirectoryPath, boolean iVisible) {
        mContext = iContext;
        mRecyclerSection = iRecyclerSection;
        mRecyclerView = iRecyclerView;
        mSwipeRefreshLayout = iSwipeRefreshLayout;
        mDirectoryPath = iDirectoryPath;
        mSwipeRefreshLayout.setVisibility(iVisible ? View.VISIBLE : View.INVISIBLE);
        mCustomViewItemList = new ArrayList<>();

        mFTPFileItemList = new ArrayList<>();
        mNameList = new ArrayList<>();
        for (FTPFile lItem : iFTPFileList) {
            mFTPFileItemList.add(new FTPFileItem(lItem));
            mNameList.add(lItem.getName());
        }
    }

    public NavigationRecyclerViewAdapter(Context iContext, FrameLayout iRecyclerSection, RecyclerView iRecyclerView,
                                         SwipeRefreshLayout iSwipeRefreshLayout, String iDirectoryPath, boolean iVisible) {
        mContext = iContext;
        mFTPFileItemList = new ArrayList<>();
        mNameList = new ArrayList<>();
        mRecyclerSection = iRecyclerSection;
        mRecyclerView = iRecyclerView;
        mSwipeRefreshLayout = iSwipeRefreshLayout;
        mDirectoryPath = iDirectoryPath;
        mSwipeRefreshLayout.setVisibility(iVisible ? View.VISIBLE : View.INVISIBLE);
        mCustomViewItemList = new ArrayList<>();
    }

    @NonNull
    @Override
    public CustomItemViewAdapter onCreateViewHolder(@NonNull ViewGroup iViewGroup, int iI) {
        if (!mIsViewHolderCreationStarted && mOnFirstViewHolderCreation != null) {
            mIsViewHolderCreationStarted = true;
            mOnFirstViewHolderCreation.onCreation();
        }

        LinearLayout lLayout = (LinearLayout) LayoutInflater.
                from(iViewGroup.getContext()).inflate(R.layout.list_item_navigation, iViewGroup, false);

        CustomItemViewAdapter oViewHolder = new CustomItemViewAdapter(
                lLayout,
                lLayout.findViewById(R.id.navigation_recycler_item_left_section),
                (ImageView) lLayout.findViewById(R.id.navigation_recycler_item_left_draw),
                (TextView) lLayout.findViewById(R.id.navigation_recycler_item_main_text),
                (TextView) lLayout.findViewById(R.id.navigation_recycler_item_secondary_text),
                (TextView) lLayout.findViewById(R.id.navigation_recycler_item_third_text),
                (TextView) lLayout.findViewById(R.id.navigation_recycler_item_fourth),
                (CheckBox) lLayout.findViewById(R.id.navigation_recycler_item_checkbox));
        mCustomViewItemList.add(oViewHolder);

        return oViewHolder;
    }

    @Override
    public void onBindViewHolder(@NonNull CustomItemViewAdapter iCustomItemViewAdapter, int iI) {
        iCustomItemViewAdapter.mFTPFileItem = mFTPFileItemList.get(iI);
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

        // TODO : Strings
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
        return mFTPFileItemList.size();
    }

    public void insertItem(FTPFile iItem) {
        LogManager.info(TAG, "Insert item");
        LogManager.info(TAG, "Name : " + iItem.getName());

        if (mFTPFileItemList == null) {
            LogManager.error(TAG, "Cannot insert an item if the list of items isn't initialized.");
            return;
        }
        mFTPFileItemList.add(new FTPFileItem(iItem));
        mNameList.add(iItem.getName());
        notifyItemRangeInserted(mFTPFileItemList.size() + 1, 1);
    }

    public void insertItem(FTPFile iItem, int iPosition) {
        LogManager.info(TAG, "Insert item at position " + iPosition);
        LogManager.info(TAG, "Name : " + iItem.getName());
        if (mFTPFileItemList == null) {
            LogManager.error(TAG, "Cannot insert an item if the list of items isn't initialized.");
            return;
        }
        if (iPosition > mFTPFileItemList.size())
            iPosition = mFTPFileItemList.size();
        mFTPFileItemList.add(iPosition, new FTPFileItem(iItem));
        mNameList.add(iPosition, iItem.getName());
        notifyItemRangeInserted(iPosition, 1);
    }

    public void updateItem(FTPFile iItem) {
        LogManager.info(TAG, "Update item");
        for (FTPFileItem lCustomItem : mFTPFileItemList) {
            if (lCustomItem.equals(iItem)) {
                int lIndex = mFTPFileItemList.indexOf(lCustomItem);
                mFTPFileItemList.get(lIndex).mFTPFile = iItem;
                mNameList.get(lIndex).replaceAll(".*", iItem.getName());
                notifyItemChanged(lIndex);
                return;
            }
        }
    }

    public void removeItem(FTPFile iItem) {
        LogManager.info(TAG, "Remove item");
        for (FTPFileItem lItem : mFTPFileItemList) {
            if (lItem.equals(iItem)) {
                mFTPFileItemList.remove(lItem);
                mNameList.remove(lItem.mFTPFile.getName());
                notifyItemRemoved(mFTPFileItemList.indexOf(lItem));
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
        mFTPFileItemList.clear();
        mNameList.clear();
        for (FTPFile lItem : iData) {
            mFTPFileItemList.add(new FTPFileItem(lItem));
            mNameList.add(lItem.getName());
        }
        notifyDataSetChanged();
    }

    public String[] getNames() {
        LogManager.info(TAG, "Get names");

        int lIndex = -1;
        int lSize = mNameList.size();
        String[] oRet = new String[lSize];

        while (++lIndex < lSize)
            oRet[lIndex] = mNameList.get(lIndex);
        return oRet;
    }

    public void switchCheckBox(FTPFile iFTPFile) {
        LogManager.info(TAG, "Switch checkbox");
        if (iFTPFile == null) {
            LogManager.error(TAG, "Given FTPFile is null");
            return;
        }
        for (CustomItemViewAdapter lItem : mCustomViewItemList) {
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
        for (CustomItemViewAdapter lItem : mCustomViewItemList) {
            if (lItem.mFTPFileItem.equals(iFTPFile)) {
                lItem.mCheckBox.setChecked(iValue);
                lItem.mFTPFileItem.mChecked = iValue;
            }
        }
    }

    public FTPFile[] getSelection() {
        List<FTPFile> oSelectedItems = new ArrayList<>();

        for (FTPFileItem lFTPFileItem : mFTPFileItemList) {
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
        for (CustomItemViewAdapter lItem : mCustomViewItemList) {
            if (lItem.mFTPFileItem.equals(iFTPFile))
                return lItem.mCheckBox.isChecked();
        }
        return false;
    }

    public void setSelectionMode(boolean iInSelectionMode) {
        if (mCustomViewItemList.size() > 0) {

            final float lLeftSectionShift = mContext.getResources().getDimension(R.dimen.navigation_recycler_left_section_shift);

            if (iInSelectionMode && !mSelectionMode) {

                LeftSectionAnimation lLeftSectionAnimation = new LeftSectionAnimation(true, lLeftSectionShift);
                mRecyclerSection.startAnimation(lLeftSectionAnimation);
                mSelectionMode = true;
            } else if (mSelectionMode) {

                LeftSectionAnimation lLeftSectionAnimation = new LeftSectionAnimation(false, lLeftSectionShift);
                mRecyclerSection.startAnimation(lLeftSectionAnimation);
                mSelectionMode = false;

                for (FTPFileItem lFTPFileItem : mFTPFileItemList)
                    lFTPFileItem.mChecked = false;
                for (CustomItemViewAdapter lCustomItemViewAdapter : mCustomViewItemList)
                    lCustomItemViewAdapter.mCheckBox.setChecked(false);
            }
        }
    }

    int indexOfFTPFile(FTPFile iFTPFile) {
        int oIndex = -1;

        while (++oIndex < mFTPFileItemList.size()) {
            if (mFTPFileItemList.get(oIndex).equals(iFTPFile))
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

    public void setOnFirstViewHolderCreation(OnFirstViewHolderCreation iAction) {
        mOnFirstViewHolderCreation = iAction;
    }

    public OnFirstViewHolderCreation getOnFirstViewHolderCreation() {
        return mOnFirstViewHolderCreation;
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
        for (CustomItemViewAdapter lItem : mCustomViewItemList) {
            lItem.mMainLayout.setBackgroundResource(android.R.color.transparent);
        }
    }

    public void setItemsClickable(boolean iClickable) {
        LogManager.info(TAG, "Set item clickable : " + iClickable);
        for (CustomItemViewAdapter lItem : mCustomViewItemList) {
            lItem.mMainLayout.setEnabled(iClickable);
        }
    }

    private void setItemsRipple() {
        LogManager.info(TAG, "Set item ripple");
        setItemsClickable(true);
        for (CustomItemViewAdapter lItem : mCustomViewItemList) {
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

    public void appearFromRight() {
        LogManager.info(TAG, "Appearing from right");
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

    public void appearFromLeft() {
        LogManager.info(TAG, "Appearing from left");
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
        LogManager.info(TAG, "Disappearing on right then destroy");
        for (CustomItemViewAdapter lItem : mCustomViewItemList) {
            lItem.mMainLayout.setOnClickListener(null);
            lItem.mMainLayout.setOnLongClickListener(null);
        }

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
        LogManager.info(TAG, "Disappearing on left");
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

    public interface OnFirstViewHolderCreation {
        void onCreation();
    }

    private static class FTPFileItem {
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

    private class LeftSectionAnimation extends Animation {

        List<ViewGroup.MarginLayoutParams> mMarginLayoutParamsList;
        private float mLeftSectionShift;
        private boolean mIsAppearing;

        public LeftSectionAnimation(boolean iIsAppearing, float iLeftSectionShift) {
            mLeftSectionShift = -iLeftSectionShift;
            mIsAppearing = iIsAppearing;
            this.setDuration(mContext.getResources().getInteger(R.integer.recycler_animation_time));
            this.setInterpolator(new DecelerateInterpolator());

            mMarginLayoutParamsList = new ArrayList<>();
            for (CustomItemViewAdapter lItem : mCustomViewItemList) {
                lItem.mSelectedMode = mIsAppearing;
                mMarginLayoutParamsList.add((ViewGroup.MarginLayoutParams) lItem.mLeftSection.getLayoutParams());
            }

            this.setAnimationListener(new AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                    LogManager.debug(TAG, "On animation start");
                    if (mIsAppearing) {
                        for (CustomItemViewAdapter lItem : mCustomViewItemList)
                            lItem.mCheckBox.setVisibility(View.VISIBLE);
                    }
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    if (!mIsAppearing) {
                        for (CustomItemViewAdapter lItem : mCustomViewItemList)
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

            for (CustomItemViewAdapter lItem : mCustomViewItemList) {
                lItem.mLeftSection.requestLayout();
                lItem.mCheckBox.setAlpha(lCheckBoxAlpha);
            }

            super.applyTransformation(iInterpolatedTime, iTransformation);
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

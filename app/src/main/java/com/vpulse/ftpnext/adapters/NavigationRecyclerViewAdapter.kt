package com.vpulse.ftpnext.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import android.view.animation.Transformation
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.vpulse.ftpnext.R
import com.vpulse.ftpnext.commons.Utils
import com.vpulse.ftpnext.core.LogManager
import org.apache.commons.net.ftp.FTPFile
import java.text.SimpleDateFormat
import java.util.*

open class NavigationRecyclerViewAdapter :
    RecyclerView.Adapter<NavigationRecyclerViewAdapter.CustomItemViewAdapter>, Filterable {
    private lateinit var mOriginalFTPFileItem: Array<FTPFileItem>
    private var mFTPFileItemList: MutableList<FTPFileItem>? = null
    private var mNameList: MutableList<String>? = null
    private var mCustomViewItemList: MutableList<CustomItemViewAdapter>
    var comparator: FTPFileComparator = AtoZComparator()
    private var mLongClickListener: OnLongClickListener? = null
    private var mClickListener: OnClickListener? = null
    var onFirstViewHolderCreation: OnFirstViewHolderCreation? = null
    private var mContext: Context
    var nextAdapter: NavigationRecyclerViewAdapter? = null
    var previousAdapter: NavigationRecyclerViewAdapter? = null
    var recyclerView: RecyclerView
        private set
    private var mRecyclerSection: FrameLayout?
    var swipeRefreshLayout: SwipeRefreshLayout
        private set
    var directoryPath: String?
        private set
    var isInSelectionMode = false
        private set
    private var mIsViewHolderCreationStarted = false

    constructor(iContext: Context,
                iRecyclerSection: FrameLayout?,
                iRecyclerView: RecyclerView,
                iSwipeRefreshLayout: SwipeRefreshLayout,
                iDirectoryPath: String?,
                iVisible: Boolean
    ) {
        mContext = iContext
        mFTPFileItemList = ArrayList()
        mNameList = ArrayList()
        mRecyclerSection = iRecyclerSection
        recyclerView = iRecyclerView
        swipeRefreshLayout = iSwipeRefreshLayout
        directoryPath = iDirectoryPath
        swipeRefreshLayout.visibility = if (iVisible) View.VISIBLE else View.INVISIBLE
        mCustomViewItemList = ArrayList()
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(iViewGroup: ViewGroup, iI: Int): CustomItemViewAdapter {
        if (!mIsViewHolderCreationStarted && onFirstViewHolderCreation != null) {
            mIsViewHolderCreationStarted = true
            onFirstViewHolderCreation!!.onCreation()
        }
        val lLayout = LayoutInflater.from(iViewGroup.context)
            .inflate(R.layout.list_item_navigation, iViewGroup, false) as LinearLayout
        val oViewHolder: CustomItemViewAdapter = CustomItemViewAdapter(
            lLayout,
            lLayout.findViewById<View>(R.id.navigation_recycler_item_left_section),
            lLayout.findViewById<ImageView>(R.id.navigation_recycler_item_left_draw),
            lLayout.findViewById<TextView>(R.id.navigation_recycler_item_main_text),
            lLayout.findViewById<TextView>(R.id.navigation_recycler_item_secondary_text),
            lLayout.findViewById<TextView>(R.id.navigation_recycler_item_third_text),
            lLayout.findViewById<TextView>(R.id.navigation_recycler_item_fourth),
            lLayout.findViewById<CheckBox>(R.id.navigation_recycler_item_checkbox)
        )
        mCustomViewItemList.add(oViewHolder)
        return oViewHolder
    }

    override fun onBindViewHolder(iCustomItemViewAdapter: CustomItemViewAdapter, iI: Int) {
        iCustomItemViewAdapter.mFTPFileItem = mFTPFileItemList!![iI]
        val lFTPItem = iCustomItemViewAdapter.mFTPFileItem!!.mFTPFile
        if (mClickListener != null) {
            iCustomItemViewAdapter.mMainLayout.setOnClickListener(View.OnClickListener {
                mClickListener!!.onClick(
                    lFTPItem
                )
            })
        }
        if (mLongClickListener != null) {
            iCustomItemViewAdapter.mMainLayout.setOnLongClickListener(object :
                View.OnLongClickListener {
                override fun onLongClick(iV: View): Boolean {
                    mLongClickListener!!.onLongClick(lFTPItem)
                    return true
                }
            })
        }
        if (lFTPItem.isDirectory) iCustomItemViewAdapter.mLeftImage.setImageResource(R.drawable.ic_outline_folder) else iCustomItemViewAdapter.mLeftImage.setImageResource(
            R.drawable.ic_file
        )
        iCustomItemViewAdapter.mMainText.text = lFTPItem.name
        val dateFormat = SimpleDateFormat("dd/MM/yy hh:mm", Locale.FRANCE)
        iCustomItemViewAdapter.mSecondaryText.text = dateFormat.format(lFTPItem.timestamp.time)

        // TODO : Strings
        if (lFTPItem.hasPermission(
                FTPFile.USER_ACCESS, FTPFile.WRITE_PERMISSION
            ) && lFTPItem.hasPermission(
                FTPFile.USER_ACCESS, FTPFile.READ_PERMISSION
            )) iCustomItemViewAdapter.mThirdText.text =
            "(Read/Write)" else if (lFTPItem.hasPermission(
                FTPFile.USER_ACCESS, FTPFile.WRITE_PERMISSION
            )) iCustomItemViewAdapter.mThirdText.text = "(Write)" else if (lFTPItem.hasPermission(
                FTPFile.USER_ACCESS, FTPFile.READ_PERMISSION
            )) iCustomItemViewAdapter.mThirdText.text =
            "(Read)" else iCustomItemViewAdapter.mThirdText.text = "(No access)"
        if (lFTPItem.isDirectory) iCustomItemViewAdapter.mFourthText.text =
            "DIR" else iCustomItemViewAdapter.mFourthText.text =
            Utils.humanReadableByteCount(lFTPItem.size, true)
        iCustomItemViewAdapter.mCheckBox.isChecked = iCustomItemViewAdapter.mFTPFileItem!!.mChecked
        if (isInSelectionMode && !iCustomItemViewAdapter.mSelectedMode) {
            val lMarginLeft = 0f
            val lCheckBoxAlpha = 1f
            val lSectionLayoutParams =
                iCustomItemViewAdapter.mLeftSection.layoutParams as MarginLayoutParams
            lSectionLayoutParams.leftMargin = lMarginLeft.toInt()
            iCustomItemViewAdapter.mLeftSection.requestLayout()
            iCustomItemViewAdapter.mCheckBox.alpha = lCheckBoxAlpha
            iCustomItemViewAdapter.mSelectedMode = isInSelectionMode
        } else if (!isInSelectionMode && iCustomItemViewAdapter.mSelectedMode) {
            val lMarginLeft =
                mContext.resources.getDimension(R.dimen.navigation_recycler_left_section_shift)
            val lCheckBoxAlpha = 0f
            val lSectionLayoutParams =
                iCustomItemViewAdapter.mLeftSection.layoutParams as MarginLayoutParams
            lSectionLayoutParams.leftMargin = lMarginLeft.toInt()
            iCustomItemViewAdapter.mLeftSection.requestLayout()
            iCustomItemViewAdapter.mCheckBox.alpha = lCheckBoxAlpha
            iCustomItemViewAdapter.mSelectedMode = isInSelectionMode
        }
        LogManager.info(TAG, lFTPItem.rawListing)
    }

    override fun getItemCount(): Int {
        return mFTPFileItemList!!.size
    }

    override fun getItemId(position: Int): Long {
        return mFTPFileItemList!![position].hashCode().toLong()
    }

    fun insertItemPersistently(iItem: FTPFile) {
        insertItem(iItem)
        mOriginalFTPFileItem = mFTPFileItemList!!.toTypedArray()
    }

    fun insertItemPersistently(iItem: FTPFile, iPosition: Int) {
        insertItem(iItem, iPosition)
        mOriginalFTPFileItem = mFTPFileItemList!!.toTypedArray()
    }

    fun removeItemPersistently(iItem: FTPFile) {
        removeItem(iItem)
        mOriginalFTPFileItem = mFTPFileItemList!!.toTypedArray()
    }

    fun insertItem(iItem: FTPFile) {
        LogManager.info(TAG, "Insert item")
        LogManager.info(TAG, "Name : " + iItem.name)
        if (mFTPFileItemList == null) {
            LogManager.error(TAG, "Cannot insert an item if the list of items isn't initialized.")
            return
        }
        mFTPFileItemList!!.add(FTPFileItem(iItem))
        mNameList!!.add(iItem.name)
        notifyItemRangeInserted(mFTPFileItemList!!.size + 1, 1)
    }

    fun insertItem(iItem: FTPFile, iPosition: Int) {
        var iPosition = iPosition
        LogManager.info(TAG, "Insert item at position $iPosition")
        LogManager.info(TAG, "Name : " + iItem.name)
        if (mFTPFileItemList == null) {
            LogManager.error(TAG, "Cannot insert an item if the list of items isn't initialized.")
            return
        }
        if (iPosition > mFTPFileItemList!!.size) iPosition = mFTPFileItemList!!.size
        mFTPFileItemList!!.add(iPosition, FTPFileItem(iItem))
        mNameList!!.add(iPosition, iItem.name)
        notifyItemRangeInserted(iPosition, 1)
    }

    fun removeItem(iItem: FTPFile) {
        LogManager.info(TAG, "Remove item")
        for (lItem: FTPFileItem in mFTPFileItemList!!) {
            if (lItem.equals(iItem)) {
                val lIndex = mFTPFileItemList!!.indexOf(lItem)
                mFTPFileItemList!!.remove(lItem)
                mNameList!!.remove(lItem.mFTPFile.name)
                if (lIndex >= 0) notifyItemRemoved(lIndex)
                return
            }
        }
    }

    fun updateItem(iItem: FTPFile) {
        LogManager.info(TAG, "Update item")
        for (lCustomItem: FTPFileItem in mFTPFileItemList!!) {
            if (lCustomItem.equals(iItem)) {
                val lIndex = mFTPFileItemList!!.indexOf(lCustomItem)
                mFTPFileItemList!![lIndex].mFTPFile = iItem
                mNameList!![lIndex].replace(".*".toRegex(), iItem.name)
                notifyItemChanged(lIndex)
                return
            }
        }
    }

    fun setData(iData: Array<FTPFile>?) {
        LogManager.info(TAG, "Set data")
        if (iData == null) {
            LogManager.error(TAG, "Cannot set the data if the given parameter is null.")
            return
        }
        if (mFTPFileItemList == null) mFTPFileItemList = ArrayList()
        if (mNameList == null) mNameList = ArrayList()
        mFTPFileItemList!!.clear()
        mNameList!!.clear()
        Arrays.sort(iData, comparator)
        for (lItem: FTPFile in iData) {
            mFTPFileItemList!!.add(FTPFileItem(lItem))
            mNameList!!.add(lItem.name)
        }
        mOriginalFTPFileItem = mFTPFileItemList!!.toTypedArray()
        notifyDataSetChanged()
    }

    fun notifyComparatorChanged() {
        val lOldItemPosition: List<FTPFileItem> = ArrayList(mFTPFileItemList)
        Arrays.sort(mOriginalFTPFileItem, comparator)
        Collections.sort(mFTPFileItemList, comparator)
        for (lItem: FTPFileItem in mFTPFileItemList!!) {
            val lOldPosition = lOldItemPosition.indexOf(lItem)
            val lNewPosition = mFTPFileItemList!!.indexOf(lItem)
            if (lNewPosition != lOldPosition) notifyItemMoved(lOldPosition, lNewPosition)
        }
        recyclerView.postDelayed({ recyclerView.smoothScrollToPosition(0) }, 300)
    }

    val names: Array<String?>
        get() {
            LogManager.info(TAG, "Get names")
            var lIndex = -1
            val lSize = mNameList!!.size
            val oRet = arrayOfNulls<String>(lSize)
            while (++lIndex < lSize) oRet[lIndex] = mNameList!![lIndex]
            return oRet
        }

    fun switchCheckBox(iFTPFile: FTPFile?) {
        LogManager.info(TAG, "Switch checkbox")
        if (iFTPFile == null) {
            LogManager.error(TAG, "Given FTPFile is null")
            return
        }
        for (lItem: CustomItemViewAdapter in mCustomViewItemList) {
            if (lItem.mFTPFileItem!!.equals(iFTPFile)) {
                lItem.mCheckBox.isChecked = !lItem.mCheckBox.isChecked
                lItem.mFTPFileItem!!.mChecked = lItem.mCheckBox.isChecked
            }
        }
    }

    fun setSelectedCheckBox(iFTPFile: FTPFile?, iValue: Boolean) {
        LogManager.info(TAG, "Set selected check box")
        if (iFTPFile == null) {
            LogManager.error(TAG, "Given FTPFile is null")
            return
        }
        for (lItem: CustomItemViewAdapter in mCustomViewItemList) {
            if (lItem.mFTPFileItem!!.equals(iFTPFile)) {
                lItem.mCheckBox.isChecked = iValue
                lItem.mFTPFileItem!!.mChecked = iValue
            }
        }
    }

    val selection: Array<FTPFile>
        get() {
            val oSelectedItems: MutableList<FTPFile> = ArrayList()
            for (lFTPFileItem: FTPFileItem in mFTPFileItemList!!) {
                if (lFTPFileItem.mChecked) oSelectedItems.add(lFTPFileItem.mFTPFile)
            }
            return oSelectedItems.toTypedArray()
        }

    fun isSelectedCheckBox(iFTPFile: FTPFile?): Boolean {
        LogManager.info(TAG, "Is selected checkbox")
        if (iFTPFile == null) {
            LogManager.error(TAG, "Given FTPFile is null")
            return false
        }
        for (lItem: CustomItemViewAdapter in mCustomViewItemList) {
            if (lItem.mFTPFileItem!!.equals(iFTPFile)) {
                return lItem.mCheckBox.isChecked
            }
        }
        return false
    }

    fun setSelectionMode(iInSelectionMode: Boolean) {
        if (mCustomViewItemList.size > 0) {
            val lLeftSectionShift =
                mContext.resources.getDimension(R.dimen.navigation_recycler_left_section_shift)
            if (iInSelectionMode && !isInSelectionMode) {
                val lLeftSectionAnimation = LeftSectionAnimation(true, lLeftSectionShift)
                mRecyclerSection!!.startAnimation(lLeftSectionAnimation)
                isInSelectionMode = true
            } else if (isInSelectionMode) {
                val lLeftSectionAnimation = LeftSectionAnimation(false, lLeftSectionShift)
                mRecyclerSection!!.startAnimation(lLeftSectionAnimation)
                isInSelectionMode = false
                for (lFTPFileItem: FTPFileItem in mFTPFileItemList!!) lFTPFileItem.mChecked = false
                for (lCustomItemViewAdapter: CustomItemViewAdapter in mCustomViewItemList) lCustomItemViewAdapter.mCheckBox.isChecked =
                    false
            }
        }
    }

    fun indexOfFTPFile(iFTPFile: FTPFile): Int {
        var oIndex = -1
        while (++oIndex < mFTPFileItemList!!.size) {
            if ((mFTPFileItemList!![oIndex].equals(iFTPFile))) return oIndex
        }
        return -1
    }

    private fun setItemsTransparent() {
        LogManager.info(TAG, "Set item transparent")
        for (lItem: CustomItemViewAdapter in mCustomViewItemList) {
            lItem.mMainLayout.setBackgroundResource(android.R.color.transparent)
        }
    }

    fun setItemsClickable(iClickable: Boolean) {
        LogManager.info(TAG, "Set item clickable : $iClickable")
        for (lItem: CustomItemViewAdapter in mCustomViewItemList) {
            lItem.mMainLayout.isEnabled = iClickable
        }
    }

    private fun setItemsRipple() {
        LogManager.info(TAG, "Set item ripple")
        setItemsClickable(true)
        for (lItem: CustomItemViewAdapter in mCustomViewItemList) {
            lItem.mMainLayout.setBackgroundResource(R.drawable.ripple_effect_primary)
        }
    }

    fun appearVertically() {
        val lAnimation =
            AnimationUtils.loadAnimation(mContext, R.anim.recycler_animation_appear_vertically)
        lAnimation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {
                setItemsTransparent()
            }

            override fun onAnimationEnd(animation: Animation) {
                setItemsRipple()
            }

            override fun onAnimationRepeat(animation: Animation) {}
        })
        swipeRefreshLayout.startAnimation(lAnimation)
        swipeRefreshLayout.visibility = View.VISIBLE
    }

    fun appearFromRight() {
        LogManager.info(TAG, "Appearing from right")
        val lAnimation =
            AnimationUtils.loadAnimation(mContext, R.anim.recycler_animation_appear_on_right)
        lAnimation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {
                setItemsTransparent()
            }

            override fun onAnimationEnd(animation: Animation) {
                setItemsRipple()
            }

            override fun onAnimationRepeat(animation: Animation) {}
        })
        swipeRefreshLayout.startAnimation(lAnimation)
        swipeRefreshLayout.visibility = View.VISIBLE
    }

    fun appearFromLeft() {
        LogManager.info(TAG, "Appearing from left")
        val lAnimation =
            AnimationUtils.loadAnimation(mContext, R.anim.recycler_animation_appear_on_left)
        lAnimation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {
                setItemsTransparent()
            }

            override fun onAnimationEnd(animation: Animation) {
                setItemsRipple()
            }

            override fun onAnimationRepeat(animation: Animation) {}
        })
        swipeRefreshLayout.startAnimation(lAnimation)
        swipeRefreshLayout.visibility = View.VISIBLE
    }

    fun disappearOnRightAndDestroy(iOnAnimationEnd: Runnable) {
        LogManager.info(TAG, "Disappearing on right then destroy")
        for (lItem: CustomItemViewAdapter in mCustomViewItemList) {
            lItem.mMainLayout.setOnClickListener(null)
            lItem.mMainLayout.setOnLongClickListener(null)
        }
        val lAnimation =
            AnimationUtils.loadAnimation(mContext, R.anim.recycler_animation_disappear_on_right)
        lAnimation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {
                setItemsTransparent()
            }

            override fun onAnimationEnd(animation: Animation) {
                iOnAnimationEnd.run()
            }

            override fun onAnimationRepeat(animation: Animation) {}
        })
        swipeRefreshLayout.startAnimation(lAnimation)
    }

    fun disappearOnLeft() {
        LogManager.info(TAG, "Disappearing on left")
        val lAnimation =
            AnimationUtils.loadAnimation(mContext, R.anim.recycler_animation_disappear_on_left)
        lAnimation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {
                setItemsTransparent()
            }

            override fun onAnimationEnd(animation: Animation) {
                swipeRefreshLayout.visibility = View.GONE
            }

            override fun onAnimationRepeat(animation: Animation) {}
        })
        swipeRefreshLayout.startAnimation(lAnimation)
    }

    fun setOnLongClickListener(iListener: OnLongClickListener?) {
        mLongClickListener = iListener
    }

    fun setOnClickListener(iListener: OnClickListener?) {
        mClickListener = iListener
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun publishResults(iConstraint: CharSequence, iResults: FilterResults) {
                val lFilteredList = iResults.values as List<FTPFileItem>
                val lFTPFileItemList: List<FTPFileItem> = ArrayList(mFTPFileItemList)
                for (lItem: FTPFileItem in mOriginalFTPFileItem) {
                    if (lFilteredList.contains(lItem)) {
                        if (!lFTPFileItemList.contains(lItem)) insertItem(lItem.mFTPFile)
                    } else removeItem(lItem.mFTPFile)
                }
            }

            override fun performFiltering(iConstraint: CharSequence): FilterResults {
                var iConstraint = iConstraint
                val lResults = FilterResults()
                val lFilteredList: MutableList<FTPFileItem> = ArrayList()
                iConstraint = iConstraint.toString().lowercase(Locale.getDefault())
                for (lItem: FTPFileItem in mOriginalFTPFileItem) {
                    if (lItem.mFTPFile.name.lowercase(Locale.getDefault())
                            .contains(iConstraint)) lFilteredList.add(lItem)
                }
                lResults.count = lFilteredList.size
                lResults.values = lFilteredList
                return lResults
            }
        }
    }

    interface OnLongClickListener {
        fun onLongClick(iFTPFile: FTPFile?)
    }

    interface OnClickListener {
        fun onClick(iFTPFile: FTPFile)
    }

    interface OnFirstViewHolderCreation {
        fun onCreation()
    }

    class FTPFileItem(var mFTPFile: FTPFile) {
        var mChecked = false
        override fun equals(iObj: Any?): Boolean {
            if (iObj == null) {
                return false
            }
            if (iObj === this) {
                return true
            }
            if (iObj is FTPFile) {
                return (mFTPFile == iObj)
            }
            return if (iObj is FTPFileItem) {
                (mFTPFile == iObj.mFTPFile)
            } else {
                false
            }
        }

        override fun toString(): String {
            return "FTPFileItem '" + mFTPFile.name + "'"
        }
    }

    private inner class LeftSectionAnimation(iIsAppearing: Boolean, iLeftSectionShift: Float) :
        Animation() {
        var mMarginLayoutParamsList: MutableList<MarginLayoutParams>
        private val mLeftSectionShift: Float
        private val mIsAppearing: Boolean

        init {
            mLeftSectionShift = -iLeftSectionShift
            mIsAppearing = iIsAppearing
            this.duration =
                mContext.resources.getInteger(R.integer.recycler_animation_time).toLong()
            this.interpolator = DecelerateInterpolator()
            mMarginLayoutParamsList = ArrayList()
            for (lItem: CustomItemViewAdapter in mCustomViewItemList) {
                lItem.mSelectedMode = mIsAppearing
                mMarginLayoutParamsList.add(lItem.mLeftSection.layoutParams as MarginLayoutParams)
            }
            setAnimationListener(object : AnimationListener {
                override fun onAnimationStart(animation: Animation) {
                    if (mIsAppearing) {
                        for (lItem: CustomItemViewAdapter in mCustomViewItemList) lItem.mCheckBox.visibility =
                            View.VISIBLE
                    }
                }

                override fun onAnimationEnd(animation: Animation) {
                    if (!mIsAppearing) {
                        for (lItem: CustomItemViewAdapter in mCustomViewItemList) lItem.mCheckBox.visibility =
                            View.INVISIBLE
                    }
                }

                override fun onAnimationRepeat(animation: Animation) {}
            })
        }

        override fun applyTransformation(iInterpolatedTime: Float, iTransformation: Transformation
        ) {
            val lMarginLeft: Float
            val lCheckBoxAlpha: Float

            // Formula: (mToWidth - mFromWidth) * interpolatedTime + mFromWidth;
            if (mIsAppearing) {
                lMarginLeft = (0 - mLeftSectionShift) * iInterpolatedTime + mLeftSectionShift
                lCheckBoxAlpha = iInterpolatedTime
            } else {
                lMarginLeft = mLeftSectionShift * iInterpolatedTime
                lCheckBoxAlpha = 1 - iInterpolatedTime
            }
            for (lMarginLayoutParams: MarginLayoutParams in mMarginLayoutParamsList) {
                lMarginLayoutParams.leftMargin = lMarginLeft.toInt()
            }
            for (lItem: CustomItemViewAdapter in mCustomViewItemList) {
                lItem.mLeftSection.requestLayout()
                lItem.mCheckBox.alpha = lCheckBoxAlpha
            }
            super.applyTransformation(iInterpolatedTime, iTransformation)
        }
    }

    abstract class FTPFileComparator() : Comparator<Any> {
        override fun compare(i1: Any, i2: Any): Int {
            if (i1 is FTPFile) {
                return compare(i1, i2 as FTPFile)
            } else if (i1 is FTPFileItem) {
                return compare(
                    i1.mFTPFile, (i2 as FTPFileItem).mFTPFile
                )
            }
            return 0
        }

        abstract fun compare(i1: FTPFile, i2: FTPFile): Int
    }

    class AtoZComparator() : FTPFileComparator() {
        override fun compare(i1: FTPFile, i2: FTPFile): Int {
            if (i1.isDirectory && !i2.isDirectory) return -1 else if (!i1.isDirectory && i2.isDirectory) return 1
            if (i1.isDirectory && i2.isDirectory) LogManager.debug("")
            return i1.name.compareTo(i2.name)
        }
    }

    class ZtoAComparator() : FTPFileComparator() {
        override fun compare(i1: FTPFile, i2: FTPFile): Int {
            if (i1.isDirectory && !i2.isDirectory) return -1 else if (!i1.isDirectory && i2.isDirectory) return 1
            return i2.name.compareTo(i1.name)
        }
    }

    class RecentComparator() : FTPFileComparator() {
        override fun compare(i1: FTPFile, i2: FTPFile): Int {
            if (i1.isDirectory && !i2.isDirectory) return -1 else if (!i1.isDirectory && i2.isDirectory) return 1
            val t1 = i1.timestamp.timeInMillis
            val t2 = i2.timestamp.timeInMillis
            return java.lang.Long.compare(t1, t2)
        }
    }

    inner class CustomItemViewAdapter(var mMainLayout: LinearLayout,
                                      var mLeftSection: View,
                                      var mLeftImage: ImageView,
                                      var mMainText: TextView,
                                      var mSecondaryText: TextView,
                                      var mThirdText: TextView,
                                      var mFourthText: TextView,
                                      var mCheckBox: CheckBox
    ) : RecyclerView.ViewHolder(mMainLayout) {
        var mFTPFileItem: FTPFileItem? = null
        var mSelectedMode = false
    }

    companion object {
        private val TAG = "NAVIGATION RECYCLER VIEW ADAPTER"
    }
}
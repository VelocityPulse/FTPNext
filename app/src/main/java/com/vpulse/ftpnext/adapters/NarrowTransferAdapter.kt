package com.vpulse.ftpnext.adapters

import android.annotation.SuppressLint
import android.app.Activity
import android.content.res.ColorStateList
import android.os.Build
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.DecelerateInterpolator
import android.widget.*
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.vpulse.ftpnext.R
import com.vpulse.ftpnext.commons.Utils
import com.vpulse.ftpnext.core.LogManager
import com.vpulse.ftpnext.database.PendingFileTable.PendingFile
import java.util.*
import kotlin.Comparator
import kotlin.collections.ArrayList

// TODO : Why not add a fading to white on the end of the name TextView (To avoid brutal cut on the display)
// TODO : Bug of item layout when orientation changes many time (width become shorter)
class NarrowTransferAdapter : RecyclerView.Adapter<NarrowTransferAdapter.CustomItemViewHolder> {
    private val mToRemovePendingFileItemList: MutableList<PendingFileItem?>
    private val mPendingFileItemList: MutableList<PendingFileItem?>
    private val mCustomItemViewHolderList: MutableList<CustomItemViewHolder>
    private var mSortButton: Button? = null
    private var mStartedAnimations = 0
    private var mActivity: Activity?
    private var mRecyclerView: RecyclerView? = null
    private val mUpdateRequestedInSecond = 0
    private val mTimer: Long = 0
    private var mHandler: Handler
    private var mSortButtonDelayer: Runnable? = null

    constructor(iPendingFiles: Array<PendingFile>, iActivity: Activity?) {
        mPendingFileItemList = ArrayList()
        mToRemovePendingFileItemList = ArrayList()
        mCustomItemViewHolderList = ArrayList()
        mHandler = Handler()
        mActivity = iActivity
        for (lItem in iPendingFiles) mPendingFileItemList.add(PendingFileItem(lItem))
        setHasStableIds(true)
    }

    constructor(iActivity: Activity?) {
        mPendingFileItemList = ArrayList()
        mToRemovePendingFileItemList = ArrayList()
        mCustomItemViewHolderList = ArrayList()
        mHandler = Handler()
        mActivity = iActivity
        setHasStableIds(true)
    }

    private fun initAnimator() {
        mSortButtonDelayer = Runnable { mSortButton!!.isEnabled = true }
        mRecyclerView!!.itemAnimator = object : DefaultItemAnimator() {
            override fun onAnimationStarted(viewHolder: RecyclerView.ViewHolder) {
                super.onAnimationStarted(viewHolder)
                mStartedAnimations++
                mSortButton!!.isEnabled = false
            }

            override fun onMoveStarting(item: RecyclerView.ViewHolder) {
                super.onMoveStarting(item)
                mStartedAnimations++
                mSortButton!!.isEnabled = false
            }

            override fun onAnimationFinished(viewHolder: RecyclerView.ViewHolder) {
                super.onAnimationFinished(viewHolder)
                mStartedAnimations--
                if (mStartedAnimations < 0) mStartedAnimations = 0
                if (mStartedAnimations >= 1) {
                    // removeCallbacksAndMessages(null) also remove all running adapter animations
                    mHandler.removeCallbacks(mSortButtonDelayer!!)
                    mHandler.postDelayed(mSortButtonDelayer!!, SORT_BUTTON_DELAY.toLong())
                }
            }
        }
    }

    override fun onAttachedToRecyclerView(iRecyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(iRecyclerView)
        mRecyclerView = iRecyclerView
        mHandler.post(object : Runnable {
            override fun run() {
                if (mRecyclerView != null) {
                    removePendingFiles()
                    mHandler.postDelayed(this, REMOVE_BREAK_TIMER.toLong())
                }
            }
        })
        mRecyclerView!!.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                freeAdapter()
            }
        })
        initAnimator()
    }

    private fun freeAdapter() {
        LogManager.info(TAG, "Free adapter")
        mToRemovePendingFileItemList.clear()
        mPendingFileItemList.clear()
        mCustomItemViewHolderList.clear()
        mRecyclerView = null
        mActivity = null
        // Do not quite safely the handler because it's the MainThread
    }

    override fun onCreateViewHolder(iViewGroup: ViewGroup, iI: Int): CustomItemViewHolder {
        val lLayout = LayoutInflater.from(iViewGroup.context).inflate(
            R.layout.list_item_narrow_transfer, iViewGroup, false
        ) as LinearLayout
        val lItem = CustomItemViewHolder(
            lLayout,
            lLayout.findViewById<View>(R.id.item_narrow_transfer_text) as TextView,
            lLayout.findViewById<View>(R.id.item_narrow_transfer_speed) as TextView,
            lLayout.findViewById<View>(R.id.item_narrow_transfer_progress_bar) as ProgressBar,
            lLayout.findViewById<View>(R.id.item_narrow_transfer_loading) as ProgressBar,
            lLayout.findViewById<View>(R.id.item_narrow_transfer_error) as ImageView
        )
        mCustomItemViewHolderList.add(lItem)
        return lItem
    }

    @SuppressLint("ResourceType")
    override fun onBindViewHolder(iCustomItemViewHolder: CustomItemViewHolder, iPosition: Int) {
        val lPendingFileItem = mPendingFileItemList[iPosition]
        val lPendingFile = lPendingFileItem!!.mPendingFile
        iCustomItemViewHolder.mPendingFileItem = lPendingFileItem

        // Set text name
        if (iCustomItemViewHolder.mTextFileView.text != lPendingFileItem.mFullName) {
            iCustomItemViewHolder.mTextFileView.text = lPendingFileItem.mFullName
        }

        // Set progress bar
        if (iCustomItemViewHolder.mProgressBar.max != lPendingFile.size) iCustomItemViewHolder.mProgressBar.max =
            lPendingFile.size
        iCustomItemViewHolder.mProgressBar.progress = lPendingFile.progress
        if (lPendingFile.isAnError) {
            // Animation
            val lOffsetStart =
                (lPendingFileItem.mTimeOfErrorNotified - System.currentTimeMillis()).toInt()
            if (lOffsetStart < 4000) {
                val lFadeIn: Animation = AlphaAnimation(0f, 1f)
                lFadeIn.interpolator = DecelerateInterpolator()
                lFadeIn.duration = 4000 // TODO : res
                lFadeIn.startOffset = lOffsetStart.toLong()
                iCustomItemViewHolder.mErrorImage.startAnimation(lFadeIn)
            }
            iCustomItemViewHolder.mTextSpeedView.visibility = View.INVISIBLE
            iCustomItemViewHolder.mLoading.visibility = View.INVISIBLE
            iCustomItemViewHolder.mErrorImage.visibility = View.VISIBLE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) iCustomItemViewHolder.mProgressBar.progressTintList =
                mActivity!!.getColorStateList(R.color.lightError)
        } else if (lPendingFile.isSelected) {
            // Normal download update
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                iCustomItemViewHolder.mProgressBar.progressTintList = ColorStateList.valueOf(
                    Utils.fetchCurrentThemeColor(mActivity, R.attr.colorAccent)
                )
            }
            iCustomItemViewHolder.mErrorImage.clearAnimation()
            iCustomItemViewHolder.mErrorImage.visibility = View.INVISIBLE

            // Set speed text
            if (!lPendingFile.isFinished && lPendingFile.isConnected) {
                iCustomItemViewHolder.mLoading.visibility = View.INVISIBLE
                iCustomItemViewHolder.mTextSpeedView.visibility = View.VISIBLE
                val lSpeed = Utils.humanReadableByteCount(lPendingFile.speedInByte, false)
                iCustomItemViewHolder.mTextSpeedView.text = lSpeed
            } else {
                iCustomItemViewHolder.mTextSpeedView.text = ""
                iCustomItemViewHolder.mLoading.visibility = View.VISIBLE
                iCustomItemViewHolder.mTextSpeedView.visibility = View.INVISIBLE
            }
        } else {
            // Not started
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                iCustomItemViewHolder.mProgressBar.progressTintList = ColorStateList.valueOf(
                    Utils.fetchCurrentThemeColor(mActivity, R.attr.colorAccent)
                )
            }
            iCustomItemViewHolder.mErrorImage.clearAnimation()
            iCustomItemViewHolder.mErrorImage.visibility = View.INVISIBLE
            iCustomItemViewHolder.mLoading.visibility = View.INVISIBLE
            iCustomItemViewHolder.mTextSpeedView.visibility = View.INVISIBLE
            iCustomItemViewHolder.mTextSpeedView.text = ""
            iCustomItemViewHolder.mProgressBar.progress = lPendingFile.progress
        }
    }

    /**
     * This function shouldn't be used to check if the Adapter is empty because it
     * doesn't take care about the items in the delete pending queue
     *
     * @return The direct number of item in the list.
     */
    override fun getItemCount(): Int {
        return mPendingFileItemList.size
    }

    /**
     * In collaboration with setHasStableIds(true) in the constructors
     * This trick allow the recycler to improve performances and be able to recognize which lines
     * are already bound in the list. Also it allows functions like notifyDataSetChanged() to
     * properly set their animations.
     */
    override fun getItemId(position: Int): Long {
        return mPendingFileItemList[position].hashCode().toLong()
    }// No need to add a condition avoiding useless loop because
    // mPendingFileItemList should always stay little in this situation
    /**
     * @return The number of item after the freeing of the remove pending file
     */
    val itemCountOmitPendingFile: Int
        get() {
            var oItemCount = 0
            synchronized(mToRemovePendingFileItemList) {
                // No need to add a condition avoiding useless loop because
                // mPendingFileItemList should always stay little in this situation
                for (lItem in mPendingFileItemList) {
                    if (!mToRemovePendingFileItemList.contains(lItem)) oItemCount++
                }
            }
            return oItemCount
        }

    fun updatePendingFileData(iPendingFile: PendingFile) {
        synchronized(mPendingFileItemList) {
            for (lItem in mCustomItemViewHolderList) {
                if (lItem.mPendingFileItem != null &&
                    !lItem.mPendingFileItem!!.mHasBeenRemoved && lItem.mPendingFileItem!!.mPendingFile === iPendingFile && lItem.mMainLayout.parent != null
                ) {
                    onBindViewHolder(lItem, mPendingFileItemList.indexOf(lItem.mPendingFileItem))
                    return
                }
            }
        }
    }

    fun addPendingFileToRemove(iPendingFile: PendingFile) {
        synchronized(mToRemovePendingFileItemList) {
            for (lItem in mPendingFileItemList) {
                if (lItem!!.mPendingFile === iPendingFile) mToRemovePendingFileItemList.add(lItem)
            }
        }
    }

    private fun removePendingFiles() {
        synchronized(mToRemovePendingFileItemList) {
            var lIndex: Int
            for (lItem in mToRemovePendingFileItemList) {
                lIndex = mPendingFileItemList.indexOf(lItem)
                mPendingFileItemList.remove(lItem)
                notifyItemRemoved(lIndex)
                lItem!!.mHasBeenRemoved = true
            }
            mToRemovePendingFileItemList.clear()
        }
    }

    fun showError(iPendingFile: PendingFile) {
        var lPendingFileItem: PendingFileItem? = null
        for (lItem in mPendingFileItemList) {
            if (lItem!!.mPendingFile === iPendingFile) lPendingFileItem = lItem
        }
        if (lPendingFileItem == null) {
            LogManager.error(TAG, "Parameter not findable in adapter list")
            return
        }
        if (lPendingFileItem.isErrorAlreadyProceed) return
        var lLastPosition: Int
        var lNewPosition: Int
        synchronized(mPendingFileItemList) {
            lLastPosition = mPendingFileItemList.indexOf(lPendingFileItem)
            lNewPosition = 0
            for (lItem in mPendingFileItemList) {
                if (lItem!!.isErrorAlreadyProceed) lNewPosition++ else break
            }
            lPendingFileItem.isErrorAlreadyProceed = true
            if (lLastPosition != lNewPosition) {
                mPendingFileItemList.remove(lPendingFileItem)
                mPendingFileItemList.add(lNewPosition, lPendingFileItem)
                notifyItemMoved(lLastPosition, 0)
            }
        }
        lPendingFileItem.mTimeOfErrorNotified = System.currentTimeMillis().toInt()
        notifyItemChanged(lNewPosition)

        // Scroll to 0 if it's necessary
        val layoutManager = mRecyclerView!!.layoutManager as LinearLayoutManager?
        val firstVisiblePosition = layoutManager!!.findFirstVisibleItemPosition()
        if (firstVisiblePosition == 0 && lNewPosition == 0) mRecyclerView!!.scrollToPosition(0)
    }

    fun notifyItemSelected(iPendingFile: PendingFile) {
        synchronized(mPendingFileItemList) {
            var lIndexOfPendingFile = -1
            var lI = -1
            val lMax = mPendingFileItemList.size
            while (++lI < lMax) {
                if (mPendingFileItemList[lI]!!.mPendingFile === iPendingFile) {
                    lIndexOfPendingFile = lI
                    break
                }
            }
            if (lIndexOfPendingFile == -1) {
                LogManager.error(TAG, "Picking index of PendingFileItem failed")
                return
            }
            val lToReInsert = mPendingFileItemList.removeAt(lIndexOfPendingFile)
            lI = lIndexOfPendingFile + 1
            while (--lI > 0) {
                if (mPendingFileItemList[lI]!!.mPendingFile.isSelected) break
            }
            mPendingFileItemList.add(lI, lToReInsert)
            notifyItemMoved(lIndexOfPendingFile, lI)
        }
    }

    /**
     * Sort items.
     * The has two step, putting all selected pending file to top, and sort by name only them.
     * It doesn't sort by name the unselected files because their order is the already the fetching
     * order.
     */
    private fun sortItems() {
        synchronized(mPendingFileItemList) {
            Collections.sort(mPendingFileItemList, Comparator { o1, o2 ->
                val lStarted1 = o1!!.mPendingFile.isSelected
                val lStarted2 = o2!!.mPendingFile.isSelected
                if (lStarted1 != lStarted2) return@Comparator if (lStarted1) -1 else 1

                // Canceling name sorting if the compare is about an unselected PendingFile
                if (!o1.mPendingFile.isSelected || !o2.mPendingFile.isSelected) 0 else o1.mFullName.compareTo(
                    o2.mFullName
                )
            })
            notifyDataSetChanged()
        }
    }

    fun setSortButton(iButton: Button?) {
        mSortButton = iButton
        mSortButton!!.setOnClickListener { sortItems() }
    }

    // TODO : Why not make it inherit from Pending file ? Cool optimisation
    internal class PendingFileItem(var mPendingFile: PendingFile) {
        var mFullName: String = mPendingFile.remotePath + mPendingFile.name
        var isErrorAlreadyProceed = false
        var mTimeOfErrorNotified = 0
        var mHasBeenRemoved = false

    }

    class CustomItemViewHolder(
        var mMainLayout: View, var mTextFileView: TextView,
        var mTextSpeedView: TextView, var mProgressBar: ProgressBar,
        var mLoading: ProgressBar, var mErrorImage: ImageView
    ) : RecyclerView.ViewHolder(mMainLayout) {
        internal var mPendingFileItem: PendingFileItem? = null
    }

    companion object {
        private const val TAG = "NARROW TRANSFER ADAPTER"
        private const val MAX_REMOVE_REQUEST_IN_SEC = 15
        private const val REMOVE_BREAK_TIMER = 1000
        private const val SORT_BUTTON_DELAY = 1000
    }
}
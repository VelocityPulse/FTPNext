package com.vpulse.ftpnext.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.vpulse.ftpnext.R
import com.vpulse.ftpnext.core.LogManager
import com.vpulse.ftpnext.database.FTPServerTable.FTPServer

class MainRecyclerViewAdapter :
    RecyclerView.Adapter<MainRecyclerViewAdapter.CustomItemViewAdapter> {
    private var mItemList: MutableList<FTPServer?>?
    private var mLongClickListener: OnLongClickListener? = null
    private var mClickListener: OnClickListener? = null
    private var mRecyclerView: RecyclerView
    private var mContext: Context

    constructor(iItemList: MutableList<FTPServer?>?, iRecyclerView: RecyclerView, iContext: Context
    ) {
        mItemList = iItemList
        mRecyclerView = iRecyclerView
        mContext = iContext
    }

    constructor(iRecyclerView: RecyclerView, iContext: Context) {
        mItemList = ArrayList()
        mRecyclerView = iRecyclerView
        mContext = iContext
    }

    override fun onCreateViewHolder(iViewGroup: ViewGroup, iI: Int): CustomItemViewAdapter {
        val lLayout = LayoutInflater.from(iViewGroup.context).inflate(
            R.layout.list_item_main, iViewGroup, false
        ) as LinearLayout
        return CustomItemViewAdapter(
            lLayout,
            lLayout.findViewById<View>(R.id.main_recycler_item_main_text) as TextView,
            lLayout.findViewById<View>(R.id.main_recycler_item_secondary_text) as TextView
        )
    }

    override fun onBindViewHolder(iCustomItemViewAdapter: CustomItemViewAdapter, iPosition: Int) {
        val lServer = mItemList!![iPosition]
        iCustomItemViewAdapter.mMainText.text = lServer!!.name
        iCustomItemViewAdapter.mSecondaryText.text = lServer.server
        iCustomItemViewAdapter.mMainLayout.setOnLongClickListener {
            if (mLongClickListener != null) mLongClickListener!!.onLongClick(lServer.dataBaseId)
            true
        }
        iCustomItemViewAdapter.mMainLayout.setOnClickListener {
            if (mClickListener != null) mClickListener!!.onClick(
                lServer.dataBaseId
            )
        }
        val animation =
            AnimationUtils.loadAnimation(mRecyclerView.context, R.anim.item_animation_fall_down)
        iCustomItemViewAdapter.itemView.startAnimation(animation)
    }

    override fun getItemCount(): Int {
        return mItemList!!.size
    }

    fun insertItem(iItem: FTPServer?) {
        if (mItemList == null) {
            LogManager.error(TAG, "Cannot insert an item if the list of items isn't initialized.")
            return
        }
        mItemList!!.add(iItem)
        notifyItemRangeInserted(mItemList!!.size + 1, 1)
    }

    fun insertItem(iItem: FTPServer?, iPosition: Int) {
        var iPosition = iPosition
        if (mItemList == null) {
            LogManager.error(TAG, "Cannot insert an item if the list of items isn't initialized.")
            return
        }
        if (iPosition > mItemList!!.size) iPosition = mItemList!!.size
        mItemList!!.add(iPosition, iItem)
        notifyItemRangeInserted(iPosition, 1)
    }

    fun updateItem(iItem: FTPServer?) {
        for (lItem in mItemList!!) {
            if (lItem!!.dataBaseId == iItem!!.dataBaseId) {
                lItem!!.updateContent(iItem)
                notifyItemChanged(mItemList!!.indexOf(lItem))
                return
            }
        }
    }

    fun removeItem(iItem: FTPServer) {
        for (lItem in mItemList!!) {
            if (lItem!!.dataBaseId == iItem.dataBaseId) {
                notifyItemRemoved(mItemList!!.indexOf(lItem))
                mItemList!!.remove(lItem)
                return
            }
        }
    }

    fun removeItem(iId: Int) {
        for (lItem in mItemList!!) {
            if (lItem!!.dataBaseId == iId) {
                notifyItemRemoved(mItemList!!.indexOf(lItem))
                mItemList!!.remove(lItem)
                return
            }
        }
    }

    fun setData(iData: List<FTPServer?>?) {
        if (iData == null) {
            LogManager.error(TAG, "Cannot set the data if the given parameter is null.")
            return
        }
        mItemList = ArrayList(iData)
        notifyDataSetChanged()
    }

    fun setOnLongClickListener(iListener: OnLongClickListener?) {
        mLongClickListener = iListener
    }

    fun setOnClickListener(iListener: OnClickListener?) {
        mClickListener = iListener
    }

    interface OnLongClickListener {
        fun onLongClick(iServerID: Int)
    }

    interface OnClickListener {
        fun onClick(iServerID: Int)
    }

    class CustomItemViewAdapter(var mMainLayout: View,
                                var mMainText: TextView,
                                var mSecondaryText: TextView
    ) : RecyclerView.ViewHolder(mMainLayout)

    companion object {
        private const val TAG = "MAIN RECYCLER VIEW ADAPTER"
    }
}
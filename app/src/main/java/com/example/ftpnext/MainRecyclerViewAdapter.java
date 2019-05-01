package com.example.ftpnext;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

public class MainRecyclerViewAdapter extends RecyclerView.Adapter<MainRecyclerViewAdapter.CustomItemViewAdapter> {

    List<String> mItemList;

    public MainRecyclerViewAdapter(List<String> lItemList) {
        mItemList = lItemList;
    }

    @NonNull
    @Override
    public CustomItemViewAdapter onCreateViewHolder(@NonNull ViewGroup iViewGroup, int iI) {

        LinearLayout v = (LinearLayout) LayoutInflater.
                from(iViewGroup.getContext()).inflate(R.layout.activity_main_item, iViewGroup, false);

        return new CustomItemViewAdapter(v, (TextView) v.findViewById(R.id.main_recycler_item_text));
    }

    @Override
    public void onBindViewHolder(@NonNull CustomItemViewAdapter iCustomItemViewAdapter, int iPosition) {
        String lValue = mItemList.get(iPosition);

        iCustomItemViewAdapter.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        });

        iCustomItemViewAdapter.mTextView.setText(lValue);
    }

    @Override
    public int getItemCount() {
        return mItemList.size();
    }

    static class CustomItemViewAdapter extends RecyclerView.ViewHolder {

        TextView mTextView;

        public CustomItemViewAdapter(@NonNull View iItemView, TextView iTextView) {
            super(iItemView);
            mTextView = iTextView;
        }
    }

}

package com.fridgecow.smartalarm;

import android.content.Context;
import android.support.wear.widget.WearableRecyclerView;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.List;

/**
 * Created by tom on 04/01/18.
 */

public class SleepDataAdapter extends WearableRecyclerView.Adapter<SleepDataAdapter.SleepViewHolder> {

    private final List<String> mData;

    public class SleepViewHolder extends WearableRecyclerView.ViewHolder {
        private SleepData mData;
        private Context mContext;
        private SleepView mView;

        public SleepViewHolder(SleepView view, Context c) {
            super(view);
            mView = view;
            mContext = c;

            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Log.d("SleepDataAdapter", "Item tapped");
                }
            });
        }

        public void attachSleepData(SleepData sleepdata){
            mData = sleepdata;
            mView.attachSleepData(sleepdata);
        }

        public void attachSleepData(String filename){
            try{
                mData = new SleepData(mContext, filename);
                mView.attachSleepData(mData);
            }catch(IOException e){
                Log.d("SleepViewHolder", "Unable to open sleepdata");
            }
        }

        public Context getContext(){
            return mContext;
        }
    }

    public SleepDataAdapter(List<String> sleepDataFilesList) {
        mData = sleepDataFilesList;
    }

    @Override
    public SleepViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        SleepView item = new SleepView(parent.getContext());
        item.setPadding(20, 5, 0, 5);
        return new SleepViewHolder(item, parent.getContext());
    }

    @Override
    public void onBindViewHolder(SleepViewHolder holder, int position) {
        holder.attachSleepData(mData.get(position));
    }

    @Override
    public int getItemCount() {
        return mData.size();
    }
}

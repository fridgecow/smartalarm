package com.fridgecow.smartalarm;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
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
    private Activity mContext;

    public class SleepViewHolder extends WearableRecyclerView.ViewHolder {
        private SleepData mData;
        private String mFileName;
        private Activity mContext;
        private SleepView mView;

        public SleepViewHolder(SleepView view, Activity c) {
            super(view);
            mView = view;
            mContext = c;

            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if(mData != null) {
                        Log.d("SleepDataAdapter", "Item tapped");

                        Intent myIntent = SleepSummaryActivity.createIntent(
                                view.getContext(),
                                mFileName);

                        mContext.startActivityForResult(myIntent, SleepSummaryActivity.VIEW);
                    }
                }
            });
        }

        public void attachSleepData(String filename){
            mFileName = filename;
            try{
                mData = new SleepData(mContext, filename);
                mView.attachSleepData(mData);
            }catch(IOException e){
                Log.d("SleepViewHolder", "Unable to open sleepdata, deleting it");
                mContext.deleteFile(filename);
                mView.setVisibility(View.GONE);
            }
        }

        public Activity getContext(){
            return mContext;
        }
    }

    public SleepDataAdapter(Activity context, List<String> sleepDataFilesList) {
        mContext = context;
        mData = sleepDataFilesList;
    }

    @Override
    public SleepViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        SleepView item = new SleepView(parent.getContext());
        item.setPadding(20, 5, 20, 5);
        return new SleepViewHolder(item, mContext);
    }

    @Override
    public void onBindViewHolder(SleepViewHolder holder, int position) {
        holder.attachSleepData(mData.get(getItemCount() - position - 1));
    }

    @Override
    public int getItemCount() {
        return mData.size();
    }
}

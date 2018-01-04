package com.fridgecow.smartalarm;

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

    public class SleepViewHolder extends WearableRecyclerView.ViewHolder {
        private SleepData mData;
        private String mFileName;
        private Context mContext;
        private SleepView mView;

        public SleepViewHolder(SleepView view, Context c) {
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

                        view.getContext().startActivity(myIntent);
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
        item.setPadding(20, 5, 20, 5);
        return new SleepViewHolder(item, parent.getContext());
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

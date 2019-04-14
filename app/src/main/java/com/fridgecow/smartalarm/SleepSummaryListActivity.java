package com.fridgecow.smartalarm;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.wear.widget.CurvingLayoutCallback;
import android.support.wear.widget.WearableLinearLayoutManager;
import android.support.wear.widget.WearableRecyclerView;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;

import com.fridgecow.smartalarm.datarepresentation.SleepSummaryDataAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SleepSummaryListActivity extends WearableActivity {

    private static final String TAG = SleepSummaryListActivity.class.getSimpleName();
    private WearableRecyclerView mWearableRecyclerView;

    private SleepSummaryDataAdapter mAdapter;

    private List<String> mSleepFiles;
    private WearableLinearLayoutManager mLayoutManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sleep_list_summary);

        mWearableRecyclerView = findViewById(R.id.recycler_view);
        mWearableRecyclerView.setEdgeItemsCenteringEnabled(true);

        // Get all sleep summaries
        mSleepFiles = new ArrayList<>();
        for(String file : fileList()){
            if(file.startsWith(TrackerService.SUMMARY_PREFIX) ){
                Log.d(TAG, file);
                mSleepFiles.add(file);
            }
        }
        Collections.sort(mSleepFiles, Collections.reverseOrder());

        mAdapter = new SleepSummaryDataAdapter(this, mSleepFiles);
        mLayoutManager = new WearableLinearLayoutManager(this, new CurvingLayoutCallback(this));
        mWearableRecyclerView.setLayoutManager(mLayoutManager);
        mWearableRecyclerView.setItemAnimator(new DefaultItemAnimator());
        mWearableRecyclerView.setAdapter(mAdapter);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data){
        if(requestCode == SleepSummaryActivity.VIEW){
            if(resultCode == RESULT_OK){
                if(data != null && data.getStringExtra("file") != null){
                    String file = data.getStringExtra("file");

                    // Remove file
                    int index = mSleepFiles.indexOf(file);
                    if(index >= 0) {
                        mSleepFiles.remove(index);
                        mAdapter.notifyItemRemoved(index);
                        mAdapter.notifyItemRangeChanged(index, mSleepFiles.size());
                    }
                }
            }
        }
    }
}

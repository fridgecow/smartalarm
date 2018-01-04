package com.fridgecow.smartalarm;

import android.os.Bundle;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.wear.widget.CurvingLayoutCallback;
import android.support.wear.widget.WearableLinearLayoutManager;
import android.support.wear.widget.WearableRecyclerView;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.widget.TextView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SleepSummaryActivity extends WearableActivity {

    private static final String TAG = SleepSummaryActivity.class.getSimpleName();
    private WearableRecyclerView mWearableRecyclerView;

    private SleepDataAdapter mAdapter;

    private List<String> mSleepFiles;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sleep_summary);

        mWearableRecyclerView = findViewById(R.id.recycler_view);


        mWearableRecyclerView.setEdgeItemsCenteringEnabled(true);

        mSleepFiles = new ArrayList<>();

        //Get all sleep summaries
        List<String> files = Arrays.asList(fileList());
        for(String file : files){
            if(file.startsWith(TrackerService.SUMMARY_PREFIX) ){
                Log.d(TAG, file);
                mSleepFiles.add(file);
            }
        }

        mAdapter = new SleepDataAdapter(mSleepFiles);
        mWearableRecyclerView.setLayoutManager(
                new WearableLinearLayoutManager(this, new CurvingLayoutCallback(this)));
        mWearableRecyclerView.setItemAnimator(new DefaultItemAnimator());
        mWearableRecyclerView.setAdapter(mAdapter);

        // Enables Always-on
        //setAmbientEnabled();
    }
}

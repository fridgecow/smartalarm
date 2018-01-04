package com.fridgecow.smartalarm;

import android.os.Bundle;
import android.support.v7.widget.RecyclerView;
import android.support.wear.widget.WearableRecyclerView;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.widget.TextView;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class SleepSummaryActivity extends WearableActivity {

    private static final String TAG = SleepSummaryActivity.class.getSimpleName();
    private TextView mTextView;
    private WearableRecyclerView mWearableRecyclerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sleep_summary);

        mTextView = findViewById(R.id.text);
        mWearableRecyclerView = findViewById(R.id.recycler_view);

        mWearableRecyclerView.setEdgeItemsCenteringEnabled(true);

        //Get all sleep summaries
        List<String> files = Arrays.asList(fileList());
        for(String file : files){
            if(file.startsWith(TrackerService.SUMMARY_PREFIX) ){
                Log.d(TAG, file);

                //Load it in (for now just log)
                try {
                    SleepData summary = new SleepData(this, file);
                    Log.d(TAG, file+" summary: Start: "+summary.getStart()+" End: "+summary.getEnd()+" Regions: "+summary.size());
                }catch(IOException e){
                    Log.d(TAG, "Failed to read summary");
                }
            }
        }

        // Enables Always-on
        setAmbientEnabled();
    }
}

package com.fridgecow.smartalarm;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.fridgecow.smartalarm.datarepresentation.DataRegion;
import com.fridgecow.smartalarm.datarepresentation.SleepSummaryData;
import com.fridgecow.smartalarm.views.SleepView;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class SleepSummaryActivity extends WearableActivity {
    private static final String TAG = "SleepSummaryActivity";

    public static final int VIEW = 0;
    public static final String DELETED = "deleted";
    public static String PREF_FILE;
    public static String PREF_DATA;

    private SleepSummaryData mData;
    private String mFile;

    private SleepView mSleepView;
    private Button mDeleteButton;
    private Button mExportButton;

    TrackerService mService;
    boolean mBound = false;

    /* Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            TrackerService.LocalBinder binder = (TrackerService.LocalBinder) service;
            mService = binder.getService();
            mBound = true;
            Log.d(TAG,"Summary Activity bound to tracking service.");
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;

            Log.d(TAG, "Summary Activity unbound from tracking service");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sleep_summary);

        mSleepView = findViewById(R.id.sleepView);
        mDeleteButton = findViewById(R.id.summary_delete_button);
        mExportButton = findViewById(R.id.summary_export_button);

        loadIntentExtras();

        mDeleteButton.setOnClickListener(view ->
            new AlertDialog.Builder(view.getContext())
                .setTitle("Confirm Deletion")
                .setMessage("Do you really want to delete?")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(android.R.string.yes, (dialog, whichButton) -> {
                    deleteFile(mFile);
                    Toast.makeText(view.getContext(), "Deleted :(", Toast.LENGTH_SHORT).show();

                    Intent ret = new Intent(DELETED);
                    ret.putExtra("file", mFile);
                    setResult(RESULT_OK, ret);
                    finish();
                })
                .setNegativeButton(android.R.string.no, null).show()
        );

        bindService(new Intent(this, TrackerService.class), mConnection, 0);
        mExportButton.setOnClickListener(view -> {
            if(mBound){
                mService.exportData(mData);
            }
        });

        mSleepView.attachSleepData(mData);

        if(mData.size() == 0) return;

        // Parse regions and generate necessary statistics
        double totalTime = mData.getEnd() - mData.getStart();
        double smartTime = mData.get(mData.size()-1).getStart() - mData.get(0).getEnd();
        double wakeTime = 0;
        for(DataRegion d : mData){
            if(d.getLabel().equals(SleepSummaryData.WAKEREGION)){
                wakeTime += Math.min(d.getEnd(), mData.getEnd()) - Math.max(d.getStart(), mData.getStart());
            }else if(d.getLabel().equals(SleepSummaryData.REMREGION)){
                findViewById(R.id.rem_legend).setVisibility(View.VISIBLE);
            }
        }
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.US);
        timeFormat.setTimeZone(TimeZone.getDefault());

        TextView currentMetric = findViewById(R.id.detectedsleeptime);
        Date sleepDate = new Date((long) mData.get(0).getEnd());
        currentMetric.setText(timeFormat.format(sleepDate));

        currentMetric = findViewById(R.id.sleepefficiencytotal);
        currentMetric.setText(Math.round(1000 -wakeTime*1000 / totalTime)/10.0 + "%");

        currentMetric = findViewById(R.id.sleepefficiency);
        double smartWakeTime = wakeTime
                - (mData.get(mData.size()-1).getEnd() - mData.get(mData.size()-1).getStart())
                - (mData.get(0).getEnd() - mData.get(0).getStart());
        double fslwEfficiency = Math.round(1000 - smartWakeTime*1000/smartTime)/10.0;
        if(fslwEfficiency > 100 || fslwEfficiency < 0){
            currentMetric.setText("N/A");
        }else {
            currentMetric.setText(fslwEfficiency+"%");
        }
        currentMetric = findViewById(R.id.detectedwaketime);
        Date wakeDate = new Date((long) mData.get(mData.size() - 1).getStart());
        currentMetric.setText(timeFormat.format(wakeDate));
    }

    private void loadIntentExtras(){
        mFile = getIntent().getStringExtra(PREF_FILE);

        if(mFile == null || mFile.isEmpty()){
            finish();
        }

        try {
            mData = new SleepSummaryData(this, mFile);
        }catch(IOException e){
            deleteFile(mFile);
            Toast.makeText(this, "Sleep data is corrupt", Toast.LENGTH_SHORT).show();
            finish();
        }

        if(mData.size() == 0){
            // Delete the file, and finish(), restoring to good state
            deleteFile(mFile);
            Toast.makeText(this, "Not enough data to summarise", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    public static Intent createIntent(Context context, String file){
        final Intent launcherIntent = new Intent(context, SleepSummaryActivity.class);

        launcherIntent.putExtra(PREF_FILE, file);

        return launcherIntent;
    }

    @Override
    public void onDestroy(){
        super.onDestroy();

        if(mBound) {
            try {
                unbindService(mConnection);
            } catch (IllegalArgumentException e) {
                Log.d(TAG, "Service not bound");
            }
        }
    }
}

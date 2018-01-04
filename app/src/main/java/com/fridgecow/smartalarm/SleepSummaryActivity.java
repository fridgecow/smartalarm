package com.fridgecow.smartalarm;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.wearable.activity.WearableActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class SleepSummaryActivity extends WearableActivity {
    public static String PREF_FILE;
    public static String PREF_DATA;

    private SleepData mData;
    private String mFile;

    private SleepView mSleepView;
    private Button mDeleteButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sleep_summary);

        mSleepView = findViewById(R.id.sleepView);
        mDeleteButton = findViewById(R.id.summary_delete_button);

        loadIntentExtras();

        mDeleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                new AlertDialog.Builder(view.getContext())
                        .setTitle("Delete Confirm")
                        .setMessage("Do you really want to delete?")
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                deleteFile(mFile);
                                Toast.makeText(view.getContext(), "Deleted :(", Toast.LENGTH_SHORT).show();
                                finish();
                            }})
                        .setNegativeButton(android.R.string.no, null).show();
            }
        });

        mSleepView.attachSleepData(mData);

        //Parse regions and generate necessary statistics
        double totalTime = mData.getEnd() - mData.getStart();
        double smartTime = mData.get(mData.size()-1).getStart() - mData.get(0).getEnd();
        double wakeTime = 0;
        for(DataRegion d : mData){
            if(d.getLabel().equals(SleepData.WAKEREGION)){
                wakeTime += d.getEnd() - d.getStart();
            }
        }
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.US);
        timeFormat.setTimeZone(TimeZone.getDefault());

        TextView currentMetric = findViewById(R.id.detectedsleeptime);
        Date sleepDate = new Date((long) mData.get(0).getEnd());
        currentMetric.setText(timeFormat.format(sleepDate));

        currentMetric = findViewById(R.id.sleepefficiencytotal);
        currentMetric.setText(Math.round(wakeTime*10 / totalTime)/10.0 + "%");

        currentMetric = findViewById(R.id.sleepefficiency);
        double smartWakeTime = wakeTime
                - (mData.get(mData.size()-1).getEnd() - mData.get(mData.size()-1).getStart())
                - (mData.get(0).getEnd() - mData.get(0).getStart());
        currentMetric.setText(Math.round(smartWakeTime*10/smartTime)/10.0 + "%");

        currentMetric = findViewById(R.id.detectedwaketime);
        Date wakeDate = new Date((long) mData.get(mData.size() - 1).getStart());
        currentMetric.setText(timeFormat.format(wakeDate));
    }

    private void loadIntentExtras(){
        mFile = getIntent().getStringExtra(PREF_FILE);

        if(mFile == null || mFile.isEmpty()){
            throw new IllegalArgumentException("Missing Sleep file in Intent.");
        }

        try {
            mData = new SleepData(this, mFile);
        }catch(IOException e){
            throw new IllegalArgumentException("Sleep file invalid");
        }
    }

    public static Intent createIntent(Context context, String file){
        final Intent launcherIntent = new Intent(context, SleepSummaryActivity.class);

        launcherIntent.putExtra(PREF_FILE, file);

        return launcherIntent;
    }
}

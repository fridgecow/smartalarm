package com.fridgecow.smartalarm;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class MainActivity extends WearableActivity {
    private static final String TAG = MainActivity.class.getSimpleName();

    private TextView mTextView;
    private ImageButton mStopButton;
    private GraphView mGraphView;
    private Button mExportButton;
    private LinearLayout mGraphActions;
    private TextView mGraphNoData;
    private ImageButton mSettingsButton;
    private ImageButton mResetButton;

    private LineGraphSeries<DataPoint> mAccelData;
    private LineGraphSeries<DataPoint> mHRData;

    private TrackerService mService;
    private boolean mBound;
    private boolean mBinding = false;

    /* Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            TrackerService.LocalBinder binder = (TrackerService.LocalBinder) service;
            mService = binder.getService();
            mBinding = false;
            mBound = true;
            Log.d(TAG,"Bound to tracking service");

            //Update graphics
            updateViews();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBinding = false;
            mBound = false;

            Log.d(TAG, "Unbound from tracking service");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTextView = findViewById(R.id.text);
        mStopButton = findViewById(R.id.button2);
        mResetButton = findViewById(R.id.button_reset);
        mExportButton = findViewById(R.id.button4);
        mGraphView = findViewById(R.id.graph);
        mGraphActions = findViewById(R.id.graph_actions);
        mSettingsButton = findViewById(R.id.button_settings);
        mGraphNoData = findViewById(R.id.nodata);

        //Start tracking service and bind to it
        Intent service = new Intent(this, TrackerService.class);
        startService(service);
        mBinding = true;
        bindService(service, mConnection, 0);

        mSettingsButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                Intent settings = new Intent(view.getContext(), SettingsActivity.class);
                startActivity(settings);
            }
        });


        //TODO: Set these up after bound, and eliminate mBound checks.
        mExportButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(mBound){
                    mService.exportData();
                }
            }
        });

        mStopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(mBound){
                    mService.playPause();
                    updateViews();
                }
            }
        });

        mResetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(mBound) {
                    if(mService.isRunning()){
                        mService.stop();
                        updateViews();
                    } else {
                        mService.reset();
                        updateGraph();
                    }
                }
            }
        });

        final View.OnLongClickListener ToolTipShower = new View.OnLongClickListener(){

            @Override
            public boolean onLongClick(View view) {
                final ImageButton ib = (ImageButton) view;
                Toast.makeText(view.getContext(), ib.getContentDescription(), Toast.LENGTH_SHORT).show();
                return true;
            }
        };
        mResetButton.setOnLongClickListener(ToolTipShower);
        mStopButton.setOnLongClickListener(ToolTipShower);
        mSettingsButton.setOnLongClickListener(ToolTipShower);


        //Style graph
        mAccelData = new LineGraphSeries<>();
        mHRData = new LineGraphSeries<>();

        mGraphView.addSeries(mAccelData);
        mGraphView.getSecondScale().addSeries(mHRData);

        mHRData.setColor(Color.RED);

        mGraphView.getGridLabelRenderer().setHorizontalLabelsVisible(false);
        mGraphView.getGridLabelRenderer().setVerticalLabelsVisible(false);
        mGraphView.getGridLabelRenderer().setSecondScaleLabelVerticalWidth(0);

        mGraphView.getViewport().setYAxisBoundsManual(true);
        mGraphView.getViewport().setMinY(0);
        mGraphView.getViewport().setXAxisBoundsManual(true);

        mGraphView.getSecondScale().setMinY(0);
        mGraphView.getSecondScale().setMaxY(100);
    }

    @Override
    public void onDestroy(){
        super.onDestroy();

        if(mBound || mBinding) {
            try {
                //Stop the service if it's not doing anything
                if(!mService.isRunning()) {
                    stopService(new Intent(this, TrackerService.class));
                }

                unbindService(mConnection);
            } catch (IllegalArgumentException e) {
                Log.d(TAG, "Service not bound");
            }
        }
    }

    private void updateGraph(){
        if(!mBound) return;

        mAccelData.resetData(mService.getSleepMotion());
        mHRData.resetData(mService.getSleepHR());

        //Is there actually any data?
        if(!mAccelData.isEmpty()) {
            mGraphActions.setVisibility(View.VISIBLE);
            mGraphNoData.setVisibility(View.GONE);

            //Set Graph range
            mGraphView.getViewport().setMaxY(mAccelData.getHighestValueY());
            mGraphView.getViewport().setMinX(mAccelData.getLowestValueX());
            mGraphView.getViewport().setMaxX(mAccelData.getHighestValueX());

            mGraphView.getSecondScale().setMaxY(mHRData.getHighestValueY());
        }else{
            mGraphActions.setVisibility(View.GONE);
            mGraphNoData.setVisibility(View.VISIBLE);
        }
    }

    private void updateViews(){
        updateGraph();

        if(mBound){
            if (mService.isRunning()) {
                mStopButton.setImageResource(android.R.drawable.ic_media_pause);
                mResetButton.setImageResource(android.R.drawable.alert_light_frame);
                mTextView.setVisibility(View.GONE);
            } else {
                mStopButton.setImageResource(android.R.drawable.ic_media_play);
                mResetButton.setImageResource(R.drawable.ic_cc_clear);
            }
        }
    }
}

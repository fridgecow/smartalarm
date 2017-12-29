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
import android.widget.LinearLayout;
import android.widget.TextView;

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
    private static final String TAG = TrackerService.class.getSimpleName();

    private RequestQueue mQueue;

    private TextView mTextView;
    private Button mStopButton;
    private GraphView mGraphView;
    private Button mExportButton;
    private LinearLayout mGraphActions;
    private Button mSettingsButton;

    private LineGraphSeries<DataPoint> mAccelData;
    private LineGraphSeries<DataPoint> mHRData;

    private TrackerService mService;
    private boolean mBound;
    private boolean mBinding = false;

    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            TrackerService.LocalBinder binder = (TrackerService.LocalBinder) service;
            mService = binder.getService();
            mBinding = false;
            mBound = true;
            Log.d(TAG,"Bound to tracking service");

            updateGraph();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBinding = false;
            mBound = false;

            Log.d(TAG, "Unbound from tracking service");
        }
    };
    private Button mResetButton;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //mQueue = Volley.newRequestQueue(this);

        mTextView = (TextView) findViewById(R.id.text);
        mStopButton = (Button) findViewById(R.id.button2);
        mResetButton = (Button) findViewById(R.id.button_reset);
        mExportButton = (Button) findViewById(R.id.button4);
        mGraphView = (GraphView) findViewById(R.id.graph);
        mGraphActions = (LinearLayout) findViewById(R.id.graph_actions);
        mSettingsButton = (Button) findViewById(R.id.button_settings);

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


        mExportButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(mBound){
                    mService.exportData();
                }
                /*
                Intent service = new Intent(view.getContext(), TrackerService.class);
                service.putExtra("task", "export");
                startService(service);
                */
            }
        });

        mStopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(mBound){
                    if(mService.playPause()){
                        mStopButton.setText(R.string.button_stop);
                    }else{
                        mStopButton.setText(R.string.button_start);
                    }
                }
            }
        });

        mResetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                /*
                Intent service = new Intent(view.getContext(), TrackerService.class);
                service.putExtra("task", "reset");
                startService(service);*/
                if(mBound) {
                    mService.reset();
                    updateGraph();
                }
            }
        });

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
    protected void onResume(){
        super.onResume();

        /*
        if(!mBound){
            mBinding = true;
            bindService(new Intent(this, TrackerService.class), mConnection, 0);
        }*/

        updateGraph();

        if(mBound) {
            if (mService.isRunning()) {
                mStopButton.setText(R.string.button_stop);
            } else {
                mStopButton.setText(R.string.button_start);
            }
        }
    }
    @Override
    public void onDestroy(){
        super.onDestroy();

        if(mBound || mBinding) {
            try {
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
            //Set Graph range
            mGraphView.getViewport().setMaxY(mAccelData.getHighestValueY());
            mGraphView.getViewport().setMinX(mAccelData.getLowestValueX());
            mGraphView.getViewport().setMaxX(mAccelData.getHighestValueX());

            mGraphView.getSecondScale().setMaxY(mHRData.getHighestValueY());
        }else{
            mGraphActions.setVisibility(View.GONE);
        }
    }
}

package com.fridgecow.smartalarm;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.jjoe64.graphview.DefaultLabelFormatter;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

public class MainActivity extends WearableActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int PERMISSION_REQUEST_SENSOR = 1;

    private TextView mTextView;
    private ImageButton mStopButton;
    private GraphView mGraphView;
    private Button mExportButton;
    private LinearLayout mGraphActions;
    private TextView mGraphNoData;
    private ImageButton mSettingsButton;
    private ImageButton mResetButton;
    private Button mSummaryButton;

    private LineGraphSeries<DataPoint> mAccelData;
    private LineGraphSeries<DataPoint> mHRData;

    private TrackerService mService;
    private boolean mBound;
    private boolean mBinding = false;

    private SharedPreferences mPreferences;

    /* Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            TrackerService.LocalBinder binder = (TrackerService.LocalBinder) service;
            mService = binder.getService();
            mBinding = false;
            mBound = true;
            Log.d(TAG,"Bound to tracking service.");

            // Update graphics
            updateViews();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBinding = false;
            mBound = false;

            Log.d(TAG, "Unbound from tracking service");
        }
    };

    private void startAndBindTrackingService(){
        if (mBound || mBinding) return;

        // Start tracking service and bind to it
        Intent service = new Intent(this, TrackerService.class);
        service.putExtra("task", "start");
        startService(service);

        mBinding = true;
        bindService(service, mConnection, 0);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        // Check if this is the first time launching this version
        if(mPreferences.getString("last_version", "").equals("")){
            // Completely new user
            mPreferences.edit().putString("last_version", getString(R.string.versionName)).apply();

            Intent onboarding = new Intent("changelog");
            onboarding.putExtra("first", true);
            startActivity(onboarding);
        }if(!mPreferences.getString("last_version", "").equals(getString(R.string.versionName))){
            mPreferences.edit().putString("last_version", getString(R.string.versionName)).apply();

            startActivity(new Intent("changelog"));
        }

        startAndBindTrackingService();

        mTextView = findViewById(R.id.text);
        mStopButton = findViewById(R.id.button2);
        mResetButton = findViewById(R.id.button_reset);
        mExportButton = findViewById(R.id.button4);
        mGraphView = findViewById(R.id.graph);
        mGraphActions = findViewById(R.id.graph_actions);
        mSettingsButton = findViewById(R.id.button_settings);
        mGraphNoData = findViewById(R.id.nodata);
        mSummaryButton = findViewById(R.id.view_summaries);

        mSettingsButton.setOnClickListener(view -> {
            Intent settings = new Intent(view.getContext(), SettingsActivity.class);
            startActivity(settings);
        });

        mSummaryButton.setOnClickListener(view -> view.getContext().startActivity(new Intent(view.getContext(), SleepSummaryListActivity.class)));

        // TODO: Set these up after bound, and eliminate mBound checks.
        mExportButton.setOnClickListener(view -> {
            if(!mBound) {
                startAndBindTrackingService();
                return;
            }

            mService.exportData();
        });

        mStopButton.setOnClickListener(view -> {
            if(!mBound) {
                startAndBindTrackingService();
                return;
            }

            // Check for required permissions
            if(mPreferences.getBoolean("hrm_use", true)) {
                int permissionCheck = ContextCompat.checkSelfPermission(view.getContext(),
                        Manifest.permission.BODY_SENSORS);
                if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(getActivity(),
                            new String[]{Manifest.permission.BODY_SENSORS},
                            PERMISSION_REQUEST_SENSOR);
                }
            }

            mService.playPause();
            updateViews();
        });

        mResetButton.setOnClickListener(view -> {
            if(!mBound) {
                startAndBindTrackingService();
                return;
            }

            if(mService.isRunning() || mService.isPaused()){
                mService.stop();
                updateViews();
            } else {
                mService.reset();
                updateGraph();
            }
        });

        final View.OnLongClickListener ToolTipShower = view -> {
            final ImageButton ib = (ImageButton) view;
            Toast.makeText(view.getContext(), ib.getContentDescription(), Toast.LENGTH_SHORT).show();
            return true;
        };

        mResetButton.setOnLongClickListener(ToolTipShower);
        mStopButton.setOnLongClickListener(ToolTipShower);
        mSettingsButton.setOnLongClickListener(ToolTipShower);

        // Style graph
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
        mGraphView.getSecondScale().setLabelFormatter(new DefaultLabelFormatter(){
            @Override
            public String formatLabel(double value, boolean isValueX){
                if(isValueX){
                    return super.formatLabel(value, true);
                }else{
                    return ""; // Show nothing
                }
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_SENSOR: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Do nothing, for now
                }
            }
        }
    }

    @Override
    public void onResume(){
        super.onResume();

        updateViews();
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

        // Is there actually any data?
        if(mAccelData.isEmpty()) {
            mGraphActions.setVisibility(View.GONE);
            mGraphNoData.setVisibility(View.VISIBLE);
            return;
        }

        mGraphActions.setVisibility(View.VISIBLE);
        mGraphNoData.setVisibility(View.GONE);

        // Set Graph range
        mGraphView.getViewport().setMaxY(mAccelData.getHighestValueY());
        mGraphView.getViewport().setMinX(mAccelData.getLowestValueX());
        mGraphView.getViewport().setMaxX(mAccelData.getHighestValueX());

        mGraphView.getSecondScale().setMaxY(mHRData.getHighestValueY());
    }

    private void updateViews(){
        updateGraph();

        if(mBound){
            if (mService.isRunning()) {
                mStopButton.setImageResource(android.R.drawable.ic_media_pause);
                mResetButton.setImageResource(R.drawable.ic_stop);
                mTextView.setVisibility(View.GONE);
            }else{
                mStopButton.setImageResource(android.R.drawable.ic_media_play);

                if(mService.isPaused()){
                    mResetButton.setImageResource(R.drawable.ic_stop);
                }else {
                    mResetButton.setImageResource(R.drawable.ic_cc_clear);
                }
            }
        }

        // Only show summary button if there are summaries
        if(fileList().length > 2){ // There are 2 offline stores
            mSummaryButton.setVisibility(View.VISIBLE);
        }else{
            mSummaryButton.setVisibility(View.GONE);
        }
    }

    private Activity getActivity(){
        return this;
    }
}

package com.fridgecow.smartalarm;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.fridgecow.smartalarm.datarepresentation.SleepData;
import com.fridgecow.smartalarm.datarepresentation.SleepSummaryData;
import com.fridgecow.smartalarm.interfaces.CSVable;
import com.jjoe64.graphview.series.DataPoint;

import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

import preference.TimePreference;

/**
 * Created by tom on 23/12/17.
 */

public class TrackerService extends Service implements SensorEventListener, AlarmManager.OnAlarmListener {
    private static final String TAG = TrackerService.class.getSimpleName();
    private static final int ONGOING_NOTIFICATION_ID = 10;
    private static final int NOTIFICATION_INTENT = 10;
    private static final int PERMISSION_REQUEST_SENSOR = 1;

    public static final String NOTIFICATION_CHANNEL_ID = "sleeptracker";

    private Context mContext = this;

    private static final String TRIGGER_AWAKE = "awake";
    private static final String TRIGGER_ASLEEP = "asleep";
    private static final String TRIGGER_ALARM = "alarm";
    private static final String TRIGGER_TRACKINGSTART = "tracking";

    private static final int API_EMAILEXPORT = 0;
    private static final int API_EMAILCONFIRM = 1;
    private static final int API_EMAILSUMMARYEXPORT = 2;

    // Things that need to be in configuration
    public static final String SUMMARY_PREFIX = "summary-";

    private SensorManager mSensorManager;
    private SharedPreferences mPreferences;

    private boolean mAccelData = false;
    private double[] mAccelLast = {0.0, 0.0, 0.0}; // Keeps previous sensor value

    private double mSleepMotionMean = -1;
    private int mHRPointsSinceTracked = 0;
    private int mHRPointsToTrack = 0;
    private boolean mHRJustTurnedOn;

    private boolean mSleeping;

    private Calendar mSmartAlarm;

    private SleepData mSleepData;

    private NotificationCompat.Builder mNotification;
    private NotificationManager mNotificationManager;
    private RequestQueue mQueue;

    private boolean mRunning = false;
    private boolean mPaused = false;

    private SharedPreferences.OnSharedPreferenceChangeListener mPreferenceListener;

    private void recordHRFor(int points) {
        if (mHRPointsToTrack == 0) {
            mHRPointsToTrack = points;
            mHRPointsSinceTracked = 0;
            mHRJustTurnedOn = true;
        } else {
            mHRPointsToTrack = Math.max(mHRPointsToTrack, points);
        }

        // Register sensor
        if (mPreferences.getBoolean("hrm_use", true)) {
            Log.d(TAG, "Tracking HR for " + points + " points");
            Sensor hr = mSensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);

            int hrmPoll = Integer.parseInt(mPreferences.getString("hrm_polling_rate", "3"));
            mSensorManager.registerListener(
                    this,
                    hr,
                    hrmPoll
            );
        }
    }

    private void stopHRRecord() {
        // De-register sensor
        Log.d(TAG, "Stopped tracking HR");

        Sensor hr = mSensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
        mSensorManager.unregisterListener(this, hr);
    }

    @Override
    public void onCreate(){
        // Initialise values
        mSleepData = new SleepData(this);
        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mQueue = Volley.newRequestQueue(this);

        // Fill SleepMotion
        try {
            mSleepData.readIn();
        }catch(IOException e){
            Log.d(TAG, "Problem reading offline store");
        }

        // Allow preferences to be changed on-the-fly
        mPreferenceListener = (sharedPreferences, key) -> {
            if (key.equals("smartalarm_time") || key.equals("smartalarm_use")) {
                configureAlarm();
            } else if (key.equals("autostart_time") || key.equals("autostart_use")) {
                configureAutostart();
            } else if (key.equals("email") || key.equals("resend-confirm")) {
                confirmEmail();
            }
        };
        mPreferences.registerOnSharedPreferenceChangeListener(mPreferenceListener);
    }

    private Handler mTrackingHandler = new Handler();
    private Runnable mTrackingRunnable = new Runnable() {

        @Override
        public void run() {
            recordLoop();

            int pollRate = mPreferences.getInt("datapoint_rate", 1);
            mTrackingHandler.postDelayed(this, pollRate * 60 * 1000);
        }
    };

    @Override
    public void onAlarm() {
        // Start tracking
        play();
    }


    // Public interface to bind to this service
    public class LocalBinder extends Binder {
        TrackerService getService() {
            // Return this instance of TrackerService so clients can call public methods
            return TrackerService.this;
        }
    }

    private final IBinder mBinder = new LocalBinder();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(intent == null || intent.getStringExtra("task") == null) {
            recordLoop();
            return START_STICKY;
        }

        String task = intent.getStringExtra("task");
        Log.d(TAG, task);

        switch (task) {
            case "reset":
                reset();
                break;
            case "export":
                exportData();
                break;
            case "playpause":
                playPause();
                break;
            case "alarm":
                // Try and create Smart Alarm object if required
                if(mSmartAlarm == null){
                    configureAlarm();
                }

                // Start tracking if it's currently stopped
                play();

                // Check if alarm should go off
                if(mSmartAlarm != null){
                    Date now = Calendar.getInstance().getTime();

                    int window = mPreferences.getInt("smartalarm_window", 30);

                    // Check alarm range
                    if(now.after(mSmartAlarm.getTime())){
                        activateAlarm();
                    }else if(mSmartAlarm.getTime().getTime() - now.getTime() <= window*60*1000){
                        Log.d(TAG, "In alarm range");

                        if(!mSleepData.getSleepingAt(mSleepData.getDataLength()-1)){
                            // Light sleep or not sleeping, activate alarm
                            activateAlarm();
                        }
                    }
                }
                break;
        }

        return START_STICKY;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            // Calculate magnitude
            if(mAccelData) {
                // Calculate magnitude of difference between
                // now and previous.
                double value = 0.0;
                for(int i = 0; i < mAccelLast.length; i++){
                    value += Math.pow(mAccelLast[i] - event.values[i], 2);
                    mAccelLast[i] = event.values[i];
                }
                mSleepData.processAccelSensor(value);
            }else{
                // Set previous
                for(int i = 0; i < mAccelLast.length; i++){
                    mAccelLast[i] = event.values[i];
                }

                mAccelData = true;
            }
        }else if(event.sensor.getType() == Sensor.TYPE_HEART_RATE){
            mSleepData.processHRSensor(event.values[0]);
        }
    }

    private void activateAlarm() {
        if (mPreferences.getBoolean("smartalarm_use", true)) {
            Intent alarmIntent = new Intent(this, AlarmActivity.class);
            alarmIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(alarmIntent);

            triggerIFTTT(TRIGGER_ALARM);

            // Reconfigure alarm for the next day
            Date now = Calendar.getInstance().getTime();
            if (now.before(mSmartAlarm.getTime())) {
                configureAlarm(true);
            } else {
                configureAlarm(false);
            }

            // We're done
            stop();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public void pause(){
        if (!mRunning) {
            return;
        }
        mRunning = false;
        mPaused = true;

        // Unbind service listeners
        mSensorManager.unregisterListener(this);

        // Stop minute-by-minute tracking
        if (!mPreferences.getBoolean("datapoint_forceaccurate", true)) {
            mTrackingHandler.removeCallbacks(mTrackingRunnable);
        } else {
            Intent intent = new Intent(this, TrackerService.class);
            PendingIntent pendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_NO_CREATE);
            if (pendingIntent != null) {
                AlarmManager alarmManager = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
                alarmManager.cancel(pendingIntent);
            }
        }

        // Set notification
        Intent appIntent = new Intent(this, MainActivity.class);
        PendingIntent appPending = PendingIntent.getActivity(this, 0, appIntent, 0);

        Intent playPauseIntent = new Intent(this, TrackerService.class);
        playPauseIntent.putExtra("task", "playpause");
        PendingIntent ppPending =
                PendingIntent.getService(this, NOTIFICATION_INTENT, playPauseIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Action.WearableExtender actionExtender = new NotificationCompat.Action.WearableExtender()
                .setHintDisplayActionInline(true);

        NotificationCompat.Action ppAction = new NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_play,
                "Play",
                ppPending).extend(actionExtender).build();

        mNotification = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Sleep Tracking Paused")
                .setSmallIcon(R.mipmap.ic_launcher_foreground) // (icon is required)
                .setContentIntent(appPending)
                .addAction(ppAction);

        mNotificationManager.notify(ONGOING_NOTIFICATION_ID, mNotification.setContentText("Get back to bed!").build());

        // Clear foreground
        stopForeground(false);
    }

    @Override
    public void onDestroy() {
        // Write out to file
        try {
            mSleepData.writeOut();
        } catch (IOException e) {
            Log.d(TAG, "Failed to write out to offline store.");
        }

        mPreferences.unregisterOnSharedPreferenceChangeListener(mPreferenceListener);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
        // pass
    }

    /* Class API */

    public DataPoint[] getSleepMotion() {
        return mSleepData.getSleepMotionArray();
    }

    public DataPoint[] getSleepHR() {
        return mSleepData.getSleepHRArray();
    }

    public void exportData() {
        exportData(mSleepData);
    }

    public void exportData(CSVable data) {
        Map<String, String> params = new HashMap<>();
        params.put("csv", data.getCSV());
        params.put("tz", TimeZone.getDefault().getID()); // Help server interpret timestamp

        apiCall(API_EMAILEXPORT, params);
    }

    public void play(){
        if (mRunning) {
            return;
        }

        mRunning = true;

        if (!mPaused) { // Perform a reset
            reset();
        }
        mPaused = false;
        mSleeping = false;

        // Register sensors
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        Sensor accel = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        Sensor hr = mSensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);

        int accPoll = Integer.parseInt(mPreferences.getString("acc_polling_rate", "3"));
        mSensorManager.registerListener(
                this,
                accel,
                accPoll
        );

        if (mPreferences.getBoolean("hrm_use", true)) {
            Log.d(TAG, "Using HRM");

            // Check permissions
            int permissionCheck = ContextCompat.checkSelfPermission(this,
                    Manifest.permission.BODY_SENSORS);

            if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                if (!mPreferences.getBoolean("hrm_smart", true)) {
                    // Register the sensor unconditionally
                    int hrmPoll = Integer.parseInt(mPreferences.getString("hrm_polling_rate", "3"));
                    mSensorManager.registerListener(
                            this,
                            hr,
                            hrmPoll
                    );
                } else {
                    // Get some initial data
                    recordHRFor(3);
                }
            }
        }

        // Configure Smart Alarm
        configureAlarm();

        // Run in foreground
        Intent launchAppIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent =
                PendingIntent.getActivity(this, 0, launchAppIntent, 0);

        Intent playPauseIntent = new Intent(this, TrackerService.class);
        playPauseIntent.putExtra("task", "playpause");
        PendingIntent ppPending =
                PendingIntent.getService(this, NOTIFICATION_INTENT, playPauseIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Action.WearableExtender actionExtender = new NotificationCompat.Action.WearableExtender()
                .setHintDisplayActionInline(true);

        NotificationCompat.Action ppAction = new NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_pause,
                "Pause",
                ppPending).extend(actionExtender).build();


        // Set foreground notification
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotification = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Sleep Tracking Enabled")
                .setSmallIcon(R.mipmap.ic_launcher_foreground) // (icon is required)
                .setContentIntent(pendingIntent)
                .addAction(ppAction);

        Notification notification = mNotification.setContentText("You're not sleeping").build();
        startForeground(ONGOING_NOTIFICATION_ID, notification);

        // Ping ourselves every datapoint_rate minutes
        if (!mPreferences.getBoolean("datapoint_forceaccurate", true)) {
            mTrackingHandler.post(mTrackingRunnable);
        } else {
            PendingIntent pingIntent = PendingIntent.getService(this, 0, new Intent(this, TrackerService.class), 0);
            AlarmManager alarmManager = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
            int pollRate = mPreferences.getInt("datapoint_rate", 1);
            alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), pollRate * 60000, pingIntent);
        }
        triggerIFTTT(TRIGGER_TRACKINGSTART);
    }

    public void triggerIFTTT(final String type) {
        // Get maker key
        final String ifttt_key = mPreferences.getString("ifttt_key", "");
        if (ifttt_key.equals("")) {
            return;
        }

        String url = "https://maker.ifttt.com/trigger/smartalarm_" + type + "/with/key/" + ifttt_key;

        StringRequest postRequest = new StringRequest(Request.Method.POST, url,
                response -> Log.d(TAG, "IFTTT: " + response),
                error -> Log.d(TAG, "Error with IFTTT! " + error.getMessage())
        ) {
            @Override
            protected Map<String, String> getParams() {
                // Put different params based on type
                // No params necessary right now!

                return new HashMap<>();
            }
        };
        mQueue.add(postRequest);

    }

    public boolean playPause(){
        if(mRunning){
            pause();
        }else{
            play();
        }

        return mRunning;
    }

    public boolean isRunning(){
        return mRunning;
    }

    public boolean isPaused() { return mPaused; }
    public void stop(){
        if(mRunning){
            pause();
        }
        mPaused = false;

        if(mSleepData.getDataLength() > 0) {
            // Auto export?
            if (mPreferences.getBoolean("auto_export", true)) {
                exportData();
            }

            // Proccess sleep data
            new SleepProcessor().execute(mSleepData);
        }

        // Stop service (from user perspective)
        stopForeground(true);
    }

    public void reset(){
        // Empty offline store and mAccelData etc
        mSleepData.reset();
        mAccelData = false;

    }

    public void configureAlarm(){
        configureAlarm(false);
    }

    public void configureAlarm(boolean nextAlarm){
        // Set up smartalarm
        if(mPreferences.getBoolean("smartalarm_use", true)){
            // Get next alarm
            mSmartAlarm = TimePreference.parseTime(mPreferences.getInt("smartalarm_time", 700), true);

            // If nextAlarm is true, force the alarm to be on the day after
            if(nextAlarm){
                mSmartAlarm.set(Calendar.DAY_OF_YEAR, mSmartAlarm.get(Calendar.DAY_OF_YEAR)+1);
            }

            // Ensure alarm delivery by setting wakeups during the window

            // Find time to start polling alarm
            Date now = Calendar.getInstance().getTime();
            int window = mPreferences.getInt("smartalarm_window", 30);
            Calendar smartWindow = (Calendar) mSmartAlarm.clone();
            smartWindow.set(Calendar.MINUTE, smartWindow.get(Calendar.MINUTE) - window);

            long pollingStart = smartWindow.getTimeInMillis();

            if(smartWindow.before(now)){
                pollingStart = System.currentTimeMillis();
            }

            // Setup recurring alert
            AlarmManager alarmManager = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);

            Intent alarmIntent = new Intent(this, TrackerService.class);
            alarmIntent.putExtra("task", "alarm");

            // Clear current alerts, if they exist
            PendingIntent pendingIntent = PendingIntent.getService(this, 5, alarmIntent, PendingIntent.FLAG_NO_CREATE);
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent);
            }

            // Set up the new alart
            PendingIntent alarmPendingIntent = PendingIntent.getService(
                    this,
                    5,
                    alarmIntent,
                    0);
            alarmManager.setRepeating(
                    AlarmManager.RTC_WAKEUP,
                    pollingStart,
                    60000,
                    alarmPendingIntent);

            Log.d(TAG, "Alarm set for "+mSmartAlarm.getTime()+", polling will start "+(new Date(pollingStart)));
        }else{
            mSmartAlarm = null;

            // Clear current alerts, if they exist
            AlarmManager alarmManager = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
            Intent alarmIntent = new Intent(this, TrackerService.class);
            alarmIntent.putExtra("task", "alarm");
            PendingIntent pendingIntent = PendingIntent.getService(this, 5, alarmIntent, PendingIntent.FLAG_NO_CREATE);
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent);
            }
        }
    }

    public void configureAutostart(){
        if(mPreferences.getBoolean("autostart_use", false)){
            // Set up alarm for tracking

            long startTime = TimePreference.parseTime(
                    mPreferences.getInt("autostart_time", 1900),
                    true
            ).getTime().getTime();

            AlarmManager alarmManager = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
            alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    startTime,
                    "SmartAlarm",
                    this,
                    null
            );
        }else{
            // Clear alarm for tracking
            AlarmManager alarmManager = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
            alarmManager.cancel(this);
        }
    }

    public void confirmEmail(){
        apiCall(API_EMAILCONFIRM, null);
    }

    private void apiCall(int type, final Map<String, String> params){
        final String baseUrl = mPreferences.getString("export_server", "https://smartalarm.fridgecow.com");
        final String email = mPreferences.getString("email", "");
        String url = baseUrl.replace(';', ':') + "/v1/";

        if(email.equals("")){
            Toast.makeText(mContext, "Error: no email provided.", Toast.LENGTH_SHORT).show();
            return;
        }

        switch (type) {
            case API_EMAILCONFIRM:
                url += "add/" + email;
                break;
            case API_EMAILEXPORT:
            case API_EMAILSUMMARYEXPORT:
                url += "csv/" + email;
                break;
        }

        StringRequest postRequest = new StringRequest(Request.Method.POST, url,
                response -> Toast.makeText(mContext, response, Toast.LENGTH_SHORT).show(),
                error -> Toast.makeText(mContext, "Error: "+error.getMessage(), Toast.LENGTH_SHORT).show()
        ){
            @Override
            protected Map<String, String> getParams() {
                return params;
            }
        };

        mQueue.add(postRequest);
    }

    private void recordLoop(){
        if (!mRunning) {
            Log.d(TAG, "Tracker Paused, not collecting data");
            return;
        }

        if (!mAccelData) {
            return;
        }

        Log.d(TAG, "Tracking Runnable doing work");

        mSleepData.recordPoint();

        //Deal with Smart HR tracking
        if (mPreferences.getBoolean("hrm_smart", true)) {
            if (mHRPointsToTrack > 0) {
                mHRPointsToTrack--;
                mHRJustTurnedOn = false;

                if (mHRPointsToTrack == 0) {
                    stopHRRecord();
                }
            }

            mHRPointsSinceTracked++;
            Log.d(TAG, mHRPointsSinceTracked + " points since HR tracked");

            if (mHRPointsSinceTracked >= 10) {
                if (!mSleeping) { // Interesting times - track every 10 minutes for 5 minutes
                    recordHRFor(5);
                } else { // Track every 10 minutes for 1 minute
                    recordHRFor(5);
                }
            }
        }

        // Check if sleeping
        boolean sleeping = mSleepData.getSleepingAt(mSleepData.getDataLength() - 1);
        if (sleeping) {
            if (!mSleeping) {
                mSleeping = true;
                triggerIFTTT(TRIGGER_ASLEEP);
                mNotificationManager.notify(ONGOING_NOTIFICATION_ID, mNotification.setContentText("You're asleep!").build());

                // Record for 2 minutes
                if (mPreferences.getBoolean("hrm_smart", true)) {
                    recordHRFor(2);
                }
            }
        } else if (mSleeping) {
            mSleeping = false;
            mNotificationManager.notify(ONGOING_NOTIFICATION_ID, mNotification.setContentText("Good morning!").build());
            triggerIFTTT(TRIGGER_AWAKE);

            // Record for 2 minutes
            if (mPreferences.getBoolean("hrm_smart", true)) {
                recordHRFor(2);
            }
        }

        // Reset
        mAccelData = false;
    }

    private class SleepProcessor extends AsyncTask<SleepData, Integer, SleepSummaryData> {
        @Override
        protected SleepSummaryData doInBackground(SleepData[] lists) {
            // For now, only deal with the first SleepData
            SleepData data = lists[0];
            return new SleepSummaryData(data);
        }

        @Override
        protected void onPostExecute(SleepSummaryData result) {
            // Store somewhere useful
            // For now, "useful" is more csv files
            if (result.size() > 0) {
                try {
                    result.writeOut(TrackerService.this, SUMMARY_PREFIX + ((long) result.getEnd()));
                    Toast.makeText(getApplicationContext(), "Summary Complete", Toast.LENGTH_SHORT).show();
                } catch (IOException e) {
                    Log.d(TAG, "Unable to open file for output");
                }
            } else {
                Toast.makeText(getApplicationContext(), "Not enough data to summarise", Toast.LENGTH_SHORT).show();
            }
        }
    }
}

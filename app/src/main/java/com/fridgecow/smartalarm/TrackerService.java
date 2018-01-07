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
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.jjoe64.graphview.series.DataPoint;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.Timer;

import preference.TimePreference;

/**
 * Created by tom on 23/12/17.
 */

public class TrackerService extends Service implements SensorEventListener, AlarmManager.OnAlarmListener {
    private static final String TAG = TrackerService.class.getSimpleName();
    private static final int ONGOING_NOTIFICATION_ID = 10;
    private static final int NOTIFICATION_INTENT = 10;
    private static final int PERMISSION_REQUEST_SENSOR = 1;

    private Context mContext = this;

    private static final String TRIGGER_AWAKE = "awake";
    private static final String TRIGGER_ASLEEP = "asleep";
    private static final String TRIGGER_ALARM = "alarm";
    private static final String TRIGGER_TRACKINGSTART = "tracking";

    private static final int API_EMAILEXPORT = 0;
    private static final int API_EMAILCONFIRM = 1;

    //Things that need to be in configuration
    private static final String OFFLINE_ACC = "sleepdata.csv";
    private static final String OFFLINE_HRM = "sleephrm.csv";
    public static final String SUMMARY_PREFIX = "summary-";
    private static final double AWAKE_THRESH = 150.0; //Found by trial

    private SensorManager mSensorManager;
    private SharedPreferences mPreferences;

    private double mAccelMax = 0.0; //Temporary maxima
    private boolean mAccelData = false;
    private double[] mAccelLast = {0.0, 0.0, 0.0}; //Keeps previous sensor value
    private List<DataPoint> mSleepMotion; //Time -> max motion
    private boolean mSleeping;
    private int mSleepCount;
    private int mAwakeCount = 0;
    private double mSleepMotionMean = -1;

    private Calendar mSmartAlarm;

    private double mHRMax = 0.0;
    private List<DataPoint> mSleepHR; //Time -> max HR

    private NotificationCompat.Builder mNotification;
    private NotificationManager mNotificationManager;
    private RequestQueue mQueue;

    private boolean mRunning = false;
    private boolean mPaused = false;

    private SharedPreferences.OnSharedPreferenceChangeListener mPreferenceListener;

    private class SleepProcessor extends AsyncTask<List<DataPoint>, Integer, SleepData>{

        //Actigraphy constants - make these configurable!
        private final double P = 0.001;
        private final double[] W = {106.0, 54.0, 58.0, 76.0, 230.0, 74.0, 67.0};

        @Override
        protected SleepData doInBackground(List<DataPoint>[] lists) {
            //For now, only deal with the first list - accelerometer data
            List<DataPoint> activity = lists[0];

            //Do actigraphy algorithm
            //https://github.com/fridgecow/smartalarm/wiki/Sleep-Detection

            SleepData data = new SleepData(activity.get(0).getX(), activity.get(activity.size()-1).getX());
            boolean sleeping = false;
            int lastIndex = 0;
            for(int i = 4; i < activity.size() - 2; i++){
                //Do weighted sum into 'D'
                double D = 0;
                for(int j = 0; j < W.length; j++){
                    double a = activity.get(i+j-4).getY();
                    if(a > 300){
                        a = 300;
                    }

                    D += a*W[j];
                }
                D *= P;

                //Log.d(TAG, "Discriminant: "+D);

                if(D < 1) { //Sleep
                    if(!sleeping){
                        //End a region
                        data.add(new DataRegion(activity.get(lastIndex), activity.get(i-1), SleepData.WAKEREGION));
                        sleeping = true;
                    }
                }else { //Wakefulness
                    if(sleeping){
                        //Start a region
                        lastIndex = i;
                        sleeping = false;
                    }
                }

                publishProgress(100*i / activity.size());
            }

            //Deal with last section
            if(!sleeping){
                data.add(new DataRegion(activity.get(lastIndex), activity.get(activity.size()-1), SleepData.WAKEREGION));
            }

            return data;
        }

        @Override
        protected void onPostExecute(SleepData result){
            //Store somewhere useful
            //For now, "useful" is more csv files
            //Put this in SleepData object?
            if(result.size() > 0) {
                try {
                    result.writeOut(TrackerService.this, SUMMARY_PREFIX + ((long) result.getEnd()));
                    Toast.makeText(getApplicationContext(), "Summary Complete", Toast.LENGTH_SHORT).show();
                } catch (IOException e) {
                    Log.d(TAG, "Unable to open file for output");
                }
            }else{
                Toast.makeText(getApplicationContext(), "Not enough data to summarise", Toast.LENGTH_SHORT).show();
            }
        }
    }
    @Override
    public void onAlarm() {
        //Start tracking
        play();
    }


    //Public interface to bind to this service
    public class LocalBinder extends Binder {
        TrackerService getService() {
            // Return this instance of TrackerService so clients can call public methods
            return TrackerService.this;
        }
    }
    private final IBinder mBinder = new LocalBinder();

    @Override
    public void onCreate(){
        //Initialise values
        mSleepMotion = new ArrayList<>();
        mSleepHR = new ArrayList<>();
        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mQueue = Volley.newRequestQueue(this);

        //Fill SleepMotion
        try {
            readData(OFFLINE_ACC, mSleepMotion);
            readData(OFFLINE_HRM, mSleepHR);
        }catch(FileNotFoundException e){
            Log.d(TAG, "No offline store found - starting from scratch");
        }catch(IOException e){
            Log.d(TAG, "Problem reading offline store");
        }

        //Allow preferences to be changed on-the-fly
        mPreferenceListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                if(key.equals("smartalarm_time")){
                    configureAlarm();
                }else if(key.equals("autostart_time") || key.equals("autostart_use")){
                    configureAutostart();
                }else if(key.equals("email")){
                    confirmEmail();
                }
            }
        };
        mPreferences.registerOnSharedPreferenceChangeListener(mPreferenceListener);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(intent == null || intent.getStringExtra("task") == null) {
            if(mRunning){
                Log.d(TAG, "No task found - collecting maximum");
                if(mAccelData) {
                    //Get a maximum of all data collected
                    long time = System.currentTimeMillis();
                    mSleepMotion.add(new DataPoint(time, mAccelMax));
                    mSleepHR.add(new DataPoint(time, mHRMax));

                    //Check if sleeping
                    if(mAccelMax < AWAKE_THRESH){
                        if(!mSleeping) {
                            mSleepCount++;
                            if(mSleepCount > 10){ //10 minutes - should be in config!
                                mSleeping = true;
                                mAwakeCount = 0;

                                triggerIFTTT(TRIGGER_ASLEEP);

                                //Update notification
                                mNotificationManager.notify(ONGOING_NOTIFICATION_ID, mNotification.setContentText("You're asleep!").build());
                            }

                        }
                    }else if(mSleeping){
                        mSleepCount = 0;
                        mAwakeCount++;
                        if(mAwakeCount > 1) { //Allow 1 false-positive
                            mSleeping = false;
                            mNotificationManager.notify(ONGOING_NOTIFICATION_ID, mNotification.setContentText("Good morning!").build());

                            triggerIFTTT(TRIGGER_AWAKE);
                        }
                    }

                    //If smart alarm, check if "light sleep"
                    if(mSmartAlarm != null){
                        Date now = Calendar.getInstance().getTime();

                        int window = mPreferences.getInt("smartalarm_window", 30);

                        //Check alarm range
                        if(now.after(mSmartAlarm.getTime())){
                            activateAlarm();
                        }else if(mSmartAlarm.getTime().getTime() - now.getTime() <= window*60*1000){
                            Log.d(TAG, "In alarm range");

                            if(mSleepMotionMean < 0) {
                                //Get mean accel motion
                                double total = 0.0;
                                for (DataPoint d : mSleepMotion) {
                                    total += d.getY();
                                }

                                mSleepMotionMean = total / mSleepMotion.size();
                            }

                            if(mAccelMax > mSleepMotionMean){
                                //Light sleep, activate alarm
                                activateAlarm();
                            }
                        }
                    }
                    //Reset
                    Log.d(TAG, "Max Acc:" + mAccelMax + ", HR: "+mHRMax);

                    mHRMax = 0.0;
                    mAccelMax = 0.0;
                    mAccelData = false;
                }
            }else{
                Log.d(TAG, "Tracker Paused, not collecting data");
            }
        }else{
            String task = intent.getStringExtra("task");
            Log.d(TAG, task);
            if(task.equals("reset")){
                reset();
            }else if(task.equals("export")){
                exportData();
            }else if(task.equals("playpause")){
                playPause();
            }
        }

        return START_STICKY;
    }

    private void activateAlarm() {
        Intent alarmIntent = new Intent(this, AlarmActivity.class);
        startActivity(alarmIntent);

        triggerIFTTT(TRIGGER_ALARM);

        //We're done
        stop();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            //Calculate magnitude
            if(mAccelData) {
                //Calculate magnitude of difference between
                //now and previous. If it's the biggest, update
                //mAccelMax
                double value = 0.0;
                for(int i = 0; i < mAccelLast.length; i++){
                    value += Math.pow(mAccelLast[i] - event.values[i], 2);
                    mAccelLast[i] = event.values[i];
                }

                if(value > mAccelMax){
                    mAccelMax = value;
                }
            }else{
                //Set previous
                for(int i = 0; i < mAccelLast.length; i++){
                    mAccelLast[i] = event.values[i];
                }

                mAccelData = true;
            }
        }else if(event.sensor.getType() == Sensor.TYPE_HEART_RATE){
            //Collect HR info
            if(event.values[0] > mHRMax){
                mHRMax = event.values[0];
            }
        }
    }

    @Override
    public void onDestroy(){
        //Write out to file
        try {
            saveData(OFFLINE_ACC, mSleepMotion);
            saveData(OFFLINE_HRM, mSleepHR);
        }catch(IOException e){
            Log.d(TAG,"Failed to write out to offline store.");
        }

        mPreferences.unregisterOnSharedPreferenceChangeListener(mPreferenceListener);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
        //pass
    }

    /* Class API */

    public DataPoint[] getSleepMotion(){
        return mSleepMotion.toArray(new DataPoint[mSleepMotion.size()]);
    }

    public DataPoint[] getSleepHR(){
        return mSleepHR.toArray(new DataPoint[mSleepHR.size()]);
    }

    public void saveData(String file, List<DataPoint> list) throws IOException{
        final BufferedWriter DataStore = new BufferedWriter(new OutputStreamWriter(openFileOutput(file, Context.MODE_APPEND)));
        Log.d(TAG, "Writing to offline store");
        for (DataPoint d : list) {
            DataStore.write(d.getX() + "," + d.getY() + "\n");
        }
        DataStore.close();
    }

    public void readData(String file, List<DataPoint> list) throws IOException{
        FileInputStream fis = openFileInput(file);
        BufferedReader br = new BufferedReader(new InputStreamReader(fis));

        Log.d(TAG, "Reading from offline store");

        String line = "";
        while ((line = br.readLine()) != null) {
            String[] data = line.split(",");
            list.add(new DataPoint(Double.parseDouble(data[0]), Double.parseDouble(data[1])));
        }

        Collections.sort(list, new Comparator<DataPoint>() {
            @Override
            public int compare(DataPoint dataPoint, DataPoint t1) {
                if(dataPoint.getX() < t1.getX()){
                    return -1;
                }else if(dataPoint.getX() == t1.getX()){
                    return 0;
                }else{
                    return 1;
                }
            }
        });

        br.close();
    }

    public void exportData() {
        //Get email address
        final String email = mPreferences.getString("email", "");
        if(email.equals("")) {
            Toast.makeText(this, "Please input an email address", Toast.LENGTH_SHORT).show();
        }else {
            Map<String, String> params = new HashMap<>();

            params.put("email", email);

            //Loop through datapoints to get CSV data
            StringBuilder csv = new StringBuilder("Unix Time,Motion,Heart Rate\n");
            for (int i = 0; i < mSleepMotion.size(); i++) {
                DataPoint d = mSleepMotion.get(i);
                if (mPreferences.getBoolean("hrm_use", true) && i < mSleepHR.size()) {
                    DataPoint h = mSleepHR.get(i);
                    csv.append(d.getX()).append(",").append(d.getY())
                               .append(",").append(h.getY()).append("\n");
                } else {
                    csv.append(d.getX()).append(",").append(d.getY()).append("\n");
                }
            }

            params.put("csv", csv.toString());

            //Put TimeZone so that server knows how to interpret UTC
            params.put("tz", TimeZone.getDefault().getID());

            apiCall(API_EMAILEXPORT, params);
        }
    }

    public void pause(){
        if(mRunning) {
            mRunning = false;
            mPaused = true;

            //Unbind service listeners
            mSensorManager.unregisterListener(this);

            //Stop minute-by-minute tracking
            Intent intent = new Intent(this, TrackerService.class);
            PendingIntent pendingIntent = PendingIntent.getService(this,  0, intent, PendingIntent.FLAG_NO_CREATE);
            if(pendingIntent != null) {
                AlarmManager alarmManager = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
                alarmManager.cancel(pendingIntent);
            }

            //Set notification
            Intent appIntent = new Intent(this, MainActivity.class);
            PendingIntent appPending = PendingIntent.getActivity(this,  0, appIntent, 0);

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

            mNotification = new NotificationCompat.Builder(this, "sleeptracking")
                    .setContentTitle("Sleep Tracking Paused")
                    .setSmallIcon(R.mipmap.ic_launcher_foreground) //(icon is required)
                    .setContentIntent(appPending)
                    .addAction(ppAction);

            mNotificationManager.notify(ONGOING_NOTIFICATION_ID, mNotification.setContentText("Get back to bed!").build());

            //Clear foreground
            stopForeground(false);
        }
    }

    public void play(){
        if(!mRunning){
            mRunning = true;

            if(!mPaused){ //Perform a reset
                reset();
            }
            mPaused = false;

            //Register sensors
            mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

            Sensor accel = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            Sensor hr = mSensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);

            int accPoll = Integer.parseInt(mPreferences.getString("acc_polling_rate", "3"));
            mSensorManager.registerListener(
                    this,
                    accel,
                    accPoll
            );
            Log.d(TAG, "Acc Polling Rate "+accPoll);


            if(mPreferences.getBoolean("hrm_use", true)) {
                Log.d(TAG, "Using HRM");

                //Check permissions
                int permissionCheck = ContextCompat.checkSelfPermission(this,
                        Manifest.permission.BODY_SENSORS);

                if(permissionCheck == PackageManager.PERMISSION_GRANTED){
                    int hrmPoll = Integer.parseInt(mPreferences.getString("acc_polling_rate", "3"));
                    mSensorManager.registerListener(
                            this,
                            hr,
                            hrmPoll
                    );
                }
            }

            //Configure Smart Alarm
            configureAlarm();

            //Run in foreground
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


            //Set foreground notification
            mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            mNotification = new NotificationCompat.Builder(this, "sleeptracking")
                    .setContentTitle("Sleep Tracking Enabled")
                    .setSmallIcon(R.mipmap.ic_launcher_foreground) //(icon is required)
                    .setContentIntent(pendingIntent)
                    .addAction(ppAction);

            Notification notification = mNotification.setContentText("You're not sleeping").build();
            startForeground(ONGOING_NOTIFICATION_ID, notification);

            //Ping ourselves every datapoint_rate minutes
            PendingIntent pingIntent = PendingIntent.getService(this,  0, new Intent(this, TrackerService.class), 0);
            AlarmManager alarmManager = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
            int pollRate = mPreferences.getInt("datapoint_rate", 1);
            alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), pollRate*60000, pingIntent);

            triggerIFTTT(TRIGGER_TRACKINGSTART);
        }
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

        if(mSleepMotion.size() > 0) {
            //Auto export?
            if (mPreferences.getBoolean("auto_export", true)) {
                exportData();
            }

            //Proccess sleep data
            new SleepProcessor().execute(mSleepMotion);
        }

        //Stop service (from user perspective)
        stopForeground(true);
    }

    public void reset(){
        //Empty offline store and mAccelData etc
        try{
            //List<DataPoint> emptyList = new ArrayList<>();
            final BufferedWriter DataStore = new BufferedWriter(new OutputStreamWriter(openFileOutput(OFFLINE_ACC, 0)));
            DataStore.close();

            final BufferedWriter DataStore2 = new BufferedWriter(new OutputStreamWriter(openFileOutput(OFFLINE_HRM, 0)));
            DataStore2.close();

        }catch(IOException e){
            Log.d(TAG, "Failed to reset");
        }

        mSleepMotion = new ArrayList<>();
        mSleepHR = new ArrayList<>();
        mAccelData = false;
        mAccelMax = 0.0;
        mHRMax = 0.0;
    }

    public void configureAlarm(){
        //Set up smartalarm
        if(mPreferences.getBoolean("smartalarm_use", true)){
            mSmartAlarm = TimePreference.parseTime(mPreferences.getInt("smartalarm_time", 700), true);
            Log.d(TAG, "Alarm set for "+mSmartAlarm.getTime());
        }
    }

    public void configureAutostart(){
        if(mPreferences.getBoolean("autostart_use", false)){
            //Set up alarm for tracking

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
            //Clear alarm for tracking
            AlarmManager alarmManager = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
            alarmManager.cancel(this);
        }
    }

    private void confirmEmail(){
        final String email = mPreferences.getString("email", "");
        if(!email.equals("")){
            Map<String, String> params = new HashMap<>();
            params.put("email", email);
            params.put("add", "");

            apiCall(API_EMAILCONFIRM, params);
        }
    }

    private void apiCall(int type, final Map<String, String> params){
        String url = "https://www.fridgecow.com/smartalarm/email.php";

        StringRequest postRequest = new StringRequest(Request.Method.POST, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        Toast.makeText(mContext, response, Toast.LENGTH_SHORT).show();
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Toast.makeText(mContext, "Error! "+error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
        ){
            @Override
            protected Map<String, String> getParams() {
                return params;
            }
        };

        mQueue.add(postRequest);
    }

    public void triggerIFTTT(final String type){
        //Get maker key
        final String ifttt_key = mPreferences.getString("ifttt_key", "");
        if(ifttt_key.equals("")) {
            return;
        }

        String url = "https://maker.ifttt.com/trigger/smartalarm_"+type+"/with/key/"+ifttt_key;

        StringRequest postRequest = new StringRequest(Request.Method.POST, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        // response
                        Log.d(TAG, "IFTTT: "+response);
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        // error
                        Log.d(TAG, "Error with IFTTT! "+error.getMessage());
                    }
                }
        ) {
            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<String, String>();

                //Put different params based on type
                //No params necessary right now!

                return params;
            }
        };
        mQueue.add(postRequest);

    }
}

package com.fridgecow.smartalarm;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Created by tom on 23/12/17.
 */

public class TrackerService extends Service implements SensorEventListener {
    private static final String TAG = TrackerService.class.getSimpleName();
    private static final int ONGOING_NOTIFICATION_ID = 10;

    //Things that need to be in configuration
    private static final String OFFLINE_STORE = "sleepdata.csv";
    private static final double AWAKE_THRESH = 150.0; //Found by trial

    private SensorManager mSensorManager;
    private SharedPreferences mPreferences;

    private double mAccelMax = 0.0; //Temporary maxima
    private boolean mAccelData = false;
    private double[] mAccelLast = {0.0, 0.0, 0.0}; //Keeps previous sensor value
    private List<DataPoint> mSleepMotion; //Time -> max motion
    private BufferedWriter mDataStore;
    private boolean mSleeping;
    private int mSleepCount;
    private int mAwakeCount = 0;

    private Calendar mSmartAlarm;

    private double mHRMax = 0.0;
    private List<DataPoint> mSleepHR; //Time -> max HR

    private NotificationCompat.Builder mNotification;
    private NotificationManager mNotificationManager;
    private RequestQueue mQueue;


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
            FileInputStream fis = openFileInput(OFFLINE_STORE);
            BufferedReader br = new BufferedReader(new InputStreamReader(fis));

            Log.d(TAG, "Reading from offline store");

            String line = "";
            while ((line = br.readLine()) != null) {
                String[] data = line.split(",");
                mSleepMotion.add(new DataPoint(Double.parseDouble(data[0]), Double.parseDouble(data[1])));
            }
            Collections.sort(mSleepMotion, new Comparator<DataPoint>() {
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
        }catch(FileNotFoundException e){
            Log.d(TAG, "No offline store found - starting from scratch");
        }catch(IOException e){
            Log.d(TAG, "Problem reading offline store");
        }

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
            int hrmPoll = Integer.parseInt(mPreferences.getString("acc_polling_rate", "3"));
            mSensorManager.registerListener(
                    this,
                    hr,
                    hrmPoll
            );
        }

        //Set up smartalarm
        if(mPreferences.getBoolean("smartalarm_use", true)){
            mSmartAlarm = Calendar.getInstance();
            mSmartAlarm.set(Calendar.HOUR_OF_DAY, mPreferences.getInt("smartalarm_hr", 7));
            mSmartAlarm.set(Calendar.MINUTE, mPreferences.getInt("smartalarm_min", 0));
            mSmartAlarm.set(Calendar.SECOND, 0);

            //If we're after the alarm, set the alarm for tomorrow
            Date now = Calendar.getInstance().getTime();
            if(now.after(mSmartAlarm.getTime())){
                mSmartAlarm.set(Calendar.DAY_OF_YEAR, mSmartAlarm.get(Calendar.DAY_OF_YEAR) + 1);
            }

            Log.d(TAG, "Alarm set for "+mSmartAlarm.getTime());
        }

        //Run in foreground
        Intent launchAppIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent =
                PendingIntent.getActivity(this, 0, launchAppIntent, 0);

        //Set foreground notification
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotification = new NotificationCompat.Builder(this, "sleeptracking")
                .setContentTitle("Sleep Tracking Enabled")
                .setSmallIcon(R.mipmap.ic_launcher_foreground) //(icon is required)
                .setContentIntent(pendingIntent);
        Notification notification = mNotification.setContentText("You're not sleeping").build();
        startForeground(ONGOING_NOTIFICATION_ID, notification);

        //Ping ourselves every datapoint_rate minutes
        PendingIntent pingIntent = PendingIntent.getService(this,  0, new Intent(this, TrackerService.class), 0);
        AlarmManager alarmManager = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);

        int pollRate = mPreferences.getInt("datapoint_rate", 1);
        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), pollRate*60000, pingIntent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(intent.getStringExtra("task") == null){
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
                    }
                }

                //If smart alarm, check if "light sleep"
                if(mSmartAlarm != null){
                    Date now = Calendar.getInstance().getTime();

                    //Check alarm range
                    if(now.after(mSmartAlarm.getTime())){
                        activateAlarm();
                    }if(mSmartAlarm.getTime().getTime() - now.getTime() <= 30*60*1000){
                        Log.d(TAG, "In alarm range");

                        //Get mean accel motion
                        double total = 0.0;
                        for(DataPoint d : mSleepMotion){
                            total+=d.getY();
                        }
                        double mean = total / mSleepMotion.size();

                        if(mAccelMax > mean){
                            //Light sleep, activate alarm
                            Log.d(TAG, "This is light sleep, activating alarm");
                            activateAlarm();
                        }
                    }
                }
                //Reset
                Log.d(TAG, "Max Ac of " + mAccelMax + " found");
                Log.d(TAG, "Max HR of "+ mHRMax + " found");

                mHRMax = 0.0;
                mAccelMax = 0.0;
                mAccelData = false;


            }
        }else{
            String task = intent.getStringExtra("task");
            Log.d(TAG, task);
            if(task.equals("reset")){
                //Empty offline store and mAccelData etc
                try{
                    mDataStore = new BufferedWriter(new OutputStreamWriter(openFileOutput(OFFLINE_STORE, 0)));
                    mDataStore.close();
                }catch(IOException e){
                    Log.d(TAG, "Failed to reset");
                }

                mSleepMotion = new ArrayList<>();
                mAccelData = false;
                mAccelMax = 0.0;
            }else if(task.equals("export")){
                String url = "https://www.fridgecow.com/smartalarm/index.php";

                //Get email address
                final String email = mPreferences.getString("email", "");
                if(email.equals("")){
                    Toast.makeText(this, "Please input an email address", Toast.LENGTH_SHORT);
                }else {
                    StringRequest postRequest = new StringRequest(Request.Method.POST, url,
                            new Response.Listener<String>() {
                                @Override
                                public void onResponse(String response) {
                                    // response
                                    Log.d("Response", response);
                                }
                            },
                            new Response.ErrorListener() {
                                @Override
                                public void onErrorResponse(VolleyError error) {
                                    // error
                                    Log.d("Error.Response", error.getMessage());
                                }
                            }
                    ) {
                        @Override
                        protected Map<String, String> getParams() {
                            Map<String, String> params = new HashMap<String, String>();

                            params.put("email", email);

                            //Loop through datapoints to get CSV data
                            StringBuilder csv = new StringBuilder("Unix Time,Motion,Heart Rate\n");
                            for (int i = 0; i < mSleepMotion.size(); i++) {
                                DataPoint d = mSleepMotion.get(i);
                                if (mPreferences.getBoolean("hrm_use", true)) {
                                    DataPoint h = mSleepHR.get(i);
                                    csv.append(d.getX() + "," + d.getY() + "," + h.getY() + "\n");
                                } else {
                                    csv.append(d.getX() + "," + d.getY() + "\n");
                                }
                            }

                            params.put("csv", csv.toString());
                            return params;
                        }
                    };
                    mQueue.add(postRequest);
                }
            }
        }

        return START_STICKY;
    }

    private void activateAlarm() {
        Intent alarmIntent = new Intent(this, AlarmActivity.class);
        startActivity(alarmIntent);

        //Stop tracking
        stopForeground(true);
        stopSelf();
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
        Intent intent = new Intent(this, TrackerService.class);

        //Stop minute-by-minute tracking
        PendingIntent pendingIntent = PendingIntent.getService(this,  0, intent, PendingIntent.FLAG_NO_CREATE);
        if(pendingIntent != null) {
            AlarmManager alarmManager = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
            alarmManager.cancel(pendingIntent);
        }

        //Write out to file
        try {
            mDataStore = new BufferedWriter(new OutputStreamWriter(openFileOutput(OFFLINE_STORE, Context.MODE_APPEND)));
            Log.d(TAG, "Writing to offline store");
            for (DataPoint d : mSleepMotion) {
                mDataStore.write(d.getX() + "," + d.getY() + "\n");
            }
            mDataStore.close();
        }catch(IOException e){
            Log.d(TAG,"Failed to write out to offline store.");
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
        //pass
    }

    public DataPoint[] getSleepMotion(){
        return mSleepMotion.toArray(new DataPoint[mSleepMotion.size()]);
    }

    public DataPoint[] getSleepHR(){
        return mSleepHR.toArray(new DataPoint[mSleepHR.size()]);
    }
}

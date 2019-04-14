package com.fridgecow.smartalarm;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.widget.Button;

public class AlarmActivity extends WearableActivity {

    private SharedPreferences mPreferences;
    private Vibrator mVibrator;

    private Button mDone;
    private Button mSnooze;

    private boolean mFinished = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alarm);

        mFinished = false;

        mDone = findViewById(R.id.button3);
        mSnooze = findViewById(R.id.button5);

        mDone.setOnClickListener(view -> dismiss());
        mSnooze.setOnClickListener(view -> snooze());

        mPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        mVibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        long[] vibrationPattern = {0, 500, 50, 300};
        mVibrator.vibrate(vibrationPattern, 0);

        // Enables Always-on
        setAmbientEnabled();
    }

    @Override
    protected void onPause(){
        super.onPause();

        Log.d("AlarmActivity","onPause()");

        if(!mFinished){
            Log.d("AlarmActivity", "Preventing leaving");
            ((ActivityManager) getApplicationContext().getSystemService(Context.ACTIVITY_SERVICE)).moveTaskToFront(getTaskId(), 0);
        }
    }

    @Override
    protected void onUserLeaveHint() {
        Log.d("AlarmActivity", "onUserLeaveHint()");

        // Snooze or Dismiss depending on preference
        if(!mFinished) {
            if (mPreferences.getBoolean("smartalarm_dismiss_action", true)) {
                Log.d("AlarmActivity", "Snoozing");
                snooze();
            } else {
                Log.d("AlarmActivity", "Dismissing");
                dismiss();
            }
        }else{
            mVibrator.cancel();
            finish();
        }
    }

    private void snooze(){
        Intent alarmIntent = new Intent(getApplicationContext(), AlarmActivity.class);

        PendingIntent snoozeIntent = PendingIntent.getActivity(getApplicationContext(),  0, alarmIntent, 0);
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        int snoozeTime = mPreferences.getInt("smartalarm_snooze", 5)*60*1000;

        alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + snoozeTime, snoozeIntent);

        // Finish for now
        mVibrator.cancel();
        mFinished = true;
        finish();
    }

    private void dismiss(){
        Log.d("AlarmActivity", "Button dismiss");
        mVibrator.cancel();
        mFinished = true;
        finish();
    }
}

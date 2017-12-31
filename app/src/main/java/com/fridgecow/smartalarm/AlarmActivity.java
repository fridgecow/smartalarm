package com.fridgecow.smartalarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.support.wearable.activity.WearableActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class AlarmActivity extends WearableActivity {

    private TextView mTime;
    private Button mDone;
    private Button mSnooze;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alarm);

        mDone = (Button) findViewById(R.id.button3);
        mSnooze = findViewById(R.id.button5);

        mDone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                vibrator.cancel();

                finish();
            }
        });
        mSnooze.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent alarmIntent = new Intent(view.getContext(), AlarmActivity.class);

                PendingIntent snoozeIntent = PendingIntent.getActivity(view.getContext(),  0, alarmIntent, 0);
                AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(view.getContext());
                int snoozeTime = prefs.getInt("smartalarm_snooze", 5)*60*1000;

                alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + snoozeTime, snoozeIntent);

                //Finish for now
                Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                vibrator.cancel();

                finish();
            }
        });

        Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        long[] vibrationPattern = {0, 500, 50, 300};
        final int indexInPatternToRepeat = 0;
        vibrator.vibrate(vibrationPattern, indexInPatternToRepeat);

        // Enables Always-on
        setAmbientEnabled();
    }
}

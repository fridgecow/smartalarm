package com.fridgecow.smartalarm;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.wearable.activity.WearableActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.util.Calendar;

import preference.TimePreference;

public class TimeInputActivity extends WearableActivity {

    private static final String PREF_KEY = "key";
    private static final String PREF_ICON = "icon";
    private static final String PREF_MIN = "min";
    private static final String PREF_MAX = "max";
    private static final String PREF_VAL = "val";
    private static final String PREF_TITLE = "title";

    private String mKey;
    private String mTitle;
    private int mIcon;
    private Calendar mValue;

    private TextView mHourText;
    private TextView mMinuteText;
    private TextView mTitleView;
    private CircularInputView mCircularInput;

    private boolean mEditingHours = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_time_input);

        mHourText = findViewById(R.id.timeinput_hours);
        mMinuteText = findViewById(R.id.timeinput_minutes);
        mTitleView = findViewById(R.id.number_title);
        mCircularInput = findViewById(R.id.circInput);

        loadIntentExtras();

        mTitleView.setText(mTitle);
        setEditingHours(true);

        mHourText.setText(String.format("%02d", mValue.get(Calendar.HOUR_OF_DAY)));
        mMinuteText.setText(String.format("%02d", mValue.get(Calendar.MINUTE)));

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        mCircularInput.setOnChangeListener(new CircularInputView.onChangeListener(){
            @Override
            public void onChange(int number){
                if(mEditingHours) {
                    mHourText.setText(String.format("%02d", number));
                    mValue.set(Calendar.HOUR_OF_DAY, number);
                }else{
                    mMinuteText.setText(String.format("%02d", number));
                    mValue.set(Calendar.MINUTE, number);
                }

                if(mKey == null || mKey.isEmpty()) {
                    Intent _result = new Intent();
                    _result.putExtra("value", mValue);
                    setResult(RESULT_OK, _result);
                }else{
                    int time = TimePreference.parseCalendar(mValue);
                    prefs.edit().putInt(mKey, time).apply();
                }

            }
        });

        mHourText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setEditingHours(true);
            }
        });

        mMinuteText.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view){
                setEditingHours(false);
            }
        });
    }

    private void setEditingHours(boolean editHours){
        mEditingHours = editHours;

        if(editHours){
            mCircularInput.setMin(0);
            mCircularInput.setMax(23);
            mCircularInput.setValue(mValue.get(Calendar.HOUR_OF_DAY));

            mHourText.setTextColor(Color.WHITE);
            mMinuteText.setTextColor(Color.GRAY);
        }else{
            mCircularInput.setMin(0);
            mCircularInput.setMax(59);
            mCircularInput.setValue(mValue.get(Calendar.MINUTE));

            mHourText.setTextColor(Color.GRAY);
            mMinuteText.setTextColor(Color.WHITE);
        }
    }
    private void loadIntentExtras(){
        mKey = getIntent().getStringExtra(PREF_KEY);
        mIcon = getIntent().getIntExtra(PREF_ICON, 0);
        mTitle = getIntent().getStringExtra(PREF_TITLE);
        mValue = (Calendar) getIntent().getSerializableExtra(PREF_VAL);
    }

    public static Intent createIntent(Context context, String key, String title, int icon, Calendar time){
        final Intent launcherIntent = new Intent(context, TimeInputActivity.class);

        launcherIntent.putExtra(PREF_KEY, key);
        launcherIntent.putExtra(PREF_ICON, icon);
        launcherIntent.putExtra(PREF_TITLE, title);
        launcherIntent.putExtra(PREF_VAL, time);
        return launcherIntent;
    }
}

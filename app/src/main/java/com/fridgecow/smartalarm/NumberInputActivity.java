package com.fridgecow.smartalarm;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.wearable.activity.WearableActivity;
import android.text.InputFilter;
import android.text.Spanned;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class NumberInputActivity extends WearableActivity {

    private static final String PREF_KEY = "key";
    private static final String PREF_ICON = "icon";
    private static final String PREF_MIN = "min";
    private static final String PREF_MAX = "max";
    private static final String PREF_VAL = "val";


    private EditText mEditText;
    private Button mDoneButton;

    private String mKey;
    private String mTitle;
    private int mIcon;
    private int mMin;
    private int mMax;
    private int mValue;

    private TextView mTextView;
    private CircularInputView mCircularInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_number_input);

        mTextView = findViewById(R.id.textView);
        mCircularInput = findViewById(R.id.circInput);

        loadIntentExtras();

        mCircularInput.setMin(mMin);
        mCircularInput.setMax(mMax);
        mCircularInput.setValue(mValue);

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        mCircularInput.setOnChangeListener(new CircularInputView.onChangeListener(){
            @Override
            public void onChange(int number){
                mTextView.setText(Integer.toString(number));
                prefs.edit().putInt(mKey, number).apply();
            }
        });
    }

    private void loadIntentExtras(){
        mKey = getIntent().getStringExtra(PREF_KEY);
        mIcon = getIntent().getIntExtra(PREF_ICON, 0);
        mTitle = getIntent().getStringExtra(PREF_KEY);
        mMin = getIntent().getIntExtra(PREF_MIN, Integer.MIN_VALUE);
        mMax = getIntent().getIntExtra(PREF_MAX, Integer.MAX_VALUE);
        mValue = getIntent().getIntExtra(PREF_VAL, 0);

        if(mKey == null || mKey.isEmpty()){
            throw new IllegalArgumentException("Missing preference key in Intent.");
        }
    }

    public static Intent createIntent(Context context, String key, String title, int icon, int val, int min, int max){
        final Intent launcherIntent = new Intent(context, NumberInputActivity.class);

        launcherIntent.putExtra(PREF_KEY, key);
        launcherIntent.putExtra(PREF_ICON, icon);
        launcherIntent.putExtra(PREF_ICON, title);
        launcherIntent.putExtra(PREF_VAL, val);
        launcherIntent.putExtra(PREF_MIN, min);
        launcherIntent.putExtra(PREF_MAX, max);

        return launcherIntent;
    }
}

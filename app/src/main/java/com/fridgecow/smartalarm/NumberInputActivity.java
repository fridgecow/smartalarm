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

public class NumberInputActivity extends WearableActivity {

    private static final String PREF_KEY = "key";
    private static final String PREF_ICON = "icon";
    private static final String PREF_MIN = "min";
    private static final String PREF_MAX = "max";
    private static final String PREF_VAL = "val";


    private EditText mEditText;
    private Button mDoneButton;

    private String mKey;
    private int mIcon;
    private int mMin;
    private int mMax;
    private int mValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_number_input);

        //Initialize variables
        mEditText = (EditText) findViewById(R.id.number_input);
        mDoneButton = (Button) findViewById(R.id.done_button);

        //Get arguments
        loadIntentExtras();

        mEditText.setText(Integer.toString(mValue));


        //Implement Listeners
        mDoneButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(view.getContext());

                prefs.edit().putInt(mKey, Integer.parseInt(mEditText.getText().toString())).apply();
                finish();
            }
        });
        mEditText.setFilters(new InputFilter[]{new InputFilter() {
            @Override
            public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
                // Compute new string after edition
                StringBuilder builder = new StringBuilder(dest);
                if (dstart > (dest.length() - 1)) {
                    builder.append(source);
                } else {
                    builder.replace(dstart, dend, source.toString().substring(start, end));
                }

                String newString = builder.toString();

                // Only valid when input is will be complete as intermediate input
                // may be temporally invalid
                try {
                    int newValue = Integer.parseInt(newString);
                    if ((mMin <= newValue) && (newValue <= mMax)) {
                        Log.d("SmartAlarm", mMin + "," + mMax);
                        return null; // Accept
                    } else {
                        return ""; // Reject
                    }
                } catch (NumberFormatException e) {
                    return ""; // Reject input
                }
            }
        }});
    }

    private void loadIntentExtras(){
        mKey = getIntent().getStringExtra(PREF_KEY);
        mIcon = getIntent().getIntExtra(PREF_ICON, 0);

        //Log.d("SmartAlarm", getIntent().getExtras().toString());

        mMin = getIntent().getIntExtra(PREF_MIN, Integer.MIN_VALUE);
        mMax = getIntent().getIntExtra(PREF_MAX, Integer.MAX_VALUE);
        mValue = getIntent().getIntExtra(PREF_VAL, 0);

        if(mKey == null || mKey.isEmpty()){
            throw new IllegalArgumentException("Missing preference key in Intent.");
        }
    }

    public static Intent createIntent(Context context, String key, int icon, int val, int min, int max){
        final Intent launcherIntent = new Intent(context, NumberInputActivity.class);

        launcherIntent.putExtra(PREF_KEY, key);
        launcherIntent.putExtra(PREF_ICON, icon);
        launcherIntent.putExtra(PREF_VAL, val);
        launcherIntent.putExtra(PREF_MIN, min);
        launcherIntent.putExtra(PREF_MAX, max);

        return launcherIntent;
    }
}

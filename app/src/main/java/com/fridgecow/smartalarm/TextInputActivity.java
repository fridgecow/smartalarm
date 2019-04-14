package com.fridgecow.smartalarm;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.wearable.activity.WearableActivity;
import android.widget.Button;
import android.widget.EditText;

public class TextInputActivity extends WearableActivity {

    private static final String PREF_KEY = "key";
    private static final String PREF_ICON = "icon";
    private static final String PREF_VAL = "val";


    private EditText mEditText;
    private Button mDoneButton;

    private String mKey;
    private int mIcon;
    private String mValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_text_input);

        // Initialize variables
        mEditText = findViewById(R.id.number_input);
        mDoneButton = findViewById(R.id.done_button);

        // Get arguments
        loadIntentExtras();

        mEditText.setText(mValue);

        // Implement Listeners
        mDoneButton.setOnClickListener(view -> {
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(view.getContext());

            prefs.edit().putString(mKey, mEditText.getText().toString()).apply();
            finish();
        });
    }

    private void loadIntentExtras(){
        mKey = getIntent().getStringExtra(PREF_KEY);
        mIcon = getIntent().getIntExtra(PREF_ICON, 0);
        mValue = getIntent().getStringExtra(PREF_VAL);

        if(mValue == null){
            mValue = "";
        }

        if(mKey == null || mKey.isEmpty()){
            throw new IllegalArgumentException("Missing preference key in Intent.");
        }
    }

    public static Intent createIntent(Context context, String key, int icon, String val){
        final Intent launcherIntent = new Intent(context, TextInputActivity.class);

        launcherIntent.putExtra(PREF_KEY, key);
        launcherIntent.putExtra(PREF_ICON, icon);
        launcherIntent.putExtra(PREF_VAL, val);

        return launcherIntent;
    }
}

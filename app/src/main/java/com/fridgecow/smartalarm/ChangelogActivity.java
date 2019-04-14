package com.fridgecow.smartalarm;

import android.os.Bundle;
import android.support.wearable.activity.WearableActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class ChangelogActivity extends WearableActivity {

    private Button mButton;
    private TextView onBoarding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_changelog);

        mButton = findViewById(R.id.changelog_done);
        onBoarding = findViewById(R.id.onboarding);

        mButton.setOnClickListener(view -> finish());

        if(getIntent().getBooleanExtra("first", false)){
            onBoarding.setVisibility(View.VISIBLE);
        }else{
            onBoarding.setVisibility(View.GONE);
        }

        // Enables Always-on
        setAmbientEnabled();
    }
}

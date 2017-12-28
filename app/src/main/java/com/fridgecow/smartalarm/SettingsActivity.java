package com.fridgecow.smartalarm;

import android.os.Bundle;

import preference.MyPreferenceParser;
import preference.WearPreferenceActivity;

public class SettingsActivity extends WearPreferenceActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.pref_general, new MyPreferenceParser());
    }
}

package preference;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.renderscript.ScriptGroup;
import android.support.annotation.NonNull;
import android.support.wear.widget.BoxInsetLayout;
import android.support.wearable.activity.WearableActivity;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.fridgecow.smartalarm.NumberInputActivity;

import preference.WearPreference;

/**
 * Created by tom on 27/12/17.
 */

public class NumberPreference extends WearPreference {
    //private static final int EDIT_ID = 75;
    private InputMethodManager mIMM;
    private Context mContext;

    private int mDefault = 0;
    private int mMin = Integer.MIN_VALUE;
    private int mMax = Integer.MAX_VALUE;

    public NumberPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        //Get XML args
        final int minResId = attrs.getAttributeResourceValue(NAMESPACE_ANDROID, "min", -1);
        if(minResId != -1){
            mMin = context.getResources().getInteger(minResId);
        }else {
            mMin = attrs.getAttributeIntValue(NAMESPACE_ANDROID, "min", mMin);
        }

        final int maxResId = attrs.getAttributeResourceValue(NAMESPACE_ANDROID, "max", -1);
        if(minResId != -1){
            mMax = context.getResources().getInteger(maxResId);
        }else {
            mMax = attrs.getAttributeIntValue(NAMESPACE_ANDROID, "max", mMax);
        }

        final int defResId = attrs.getAttributeResourceValue(NAMESPACE_ANDROID, "defaultValue", -1);
        if(defResId != -1){
            mDefault = context.getResources().getInteger(defResId);
        }else{
            mDefault = attrs.getAttributeIntValue(NAMESPACE_ANDROID, "defaultValue", mDefault);
        }
    }

    public void onPreferenceClick(@NonNull final Context context) {
        final Intent inputMethod = NumberInputActivity.createIntent(
                context,
                getKey(),
                getTitle(context).toString(),
                getIcon(context),
                getCurrentValue(context),
                mMin,
                mMax
        );

        context.startActivity(inputMethod);
    }

    @Override public CharSequence getSummary(@NonNull final Context context) {
        return Integer.toString(getCurrentValue(context));
    }

    private int getCurrentValue(@NonNull final Context context){
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

        try {
            return preferences.getInt(getKey(), mDefault);
        }catch(ClassCastException e){
            //A string was put here instead of an int - forget about it!
            return mDefault;
        }
    }
}

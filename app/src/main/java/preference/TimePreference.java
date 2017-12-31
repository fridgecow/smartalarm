package preference;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.util.AttributeSet;

import com.fridgecow.smartalarm.MainActivity;
import com.fridgecow.smartalarm.NumberInputActivity;
import com.fridgecow.smartalarm.TimeInputActivity;

import java.util.Calendar;
import java.util.Date;

/**
 * Created by tom on 31/12/17.
 */

public class TimePreference extends WearPreference {
    private static final String TAG = TimePreference.class.getSimpleName();
    private int mDefault = 700;
    private Calendar mTime;

    public TimePreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        //Get XML args
        final int defResId = attrs.getAttributeResourceValue(NAMESPACE_ANDROID, "defaultValue", -1);
        if(defResId != -1){
            mDefault = context.getResources().getInteger(defResId);
        }else{
            mDefault = attrs.getAttributeIntValue(NAMESPACE_ANDROID, "defaultValue", mDefault);
        }

        mTime = parseTime(getCurrentValue(context));
    }

    @Override
    void onPreferenceClick(@NonNull Context context) {
        mTime = parseTime(getCurrentValue(context));
        
        //Launch for hours
        Intent hourInput = TimeInputActivity.createIntent(
                context,
                getKey(),
                (String) getTitle(context),
                getIcon(context),
                mTime
        );
        context.startActivity(hourInput);
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

    public static int parseCalendar(Calendar c){
        return c.get(Calendar.HOUR_OF_DAY)*100 + c.get(Calendar.MINUTE);
    }

    public static Calendar parseTime(int time){
        return parseTime(time, true);
    }

    public static Calendar parseTime(int time, boolean inTheFuture){
        int hour = (time / 100)%24;
        int minute = (time % 100) % 60;

        Calendar ret = Calendar.getInstance();
        ret.set(Calendar.HOUR_OF_DAY, hour);
        ret.set(Calendar.MINUTE, minute);
        ret.set(Calendar.SECOND, 0);

        //If we're after the alarm, set the alarm for tomorrow
        if(inTheFuture) {
            Date now = Calendar.getInstance().getTime();
            if (now.after(ret.getTime())) {
                ret.set(Calendar.DAY_OF_YEAR, ret.get(Calendar.DAY_OF_YEAR) + 1);
            }
        }

        return ret;
    }

    @Override public CharSequence getSummary(@NonNull final Context context) {
        mTime = parseTime(getCurrentValue(context));

        return String.format("%02d", mTime.get(Calendar.HOUR_OF_DAY))
                + ":"
                + String.format("%02d", mTime.get(Calendar.MINUTE));
    }
}

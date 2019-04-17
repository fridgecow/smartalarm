package preference;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.util.AttributeSet;

import com.fridgecow.smartalarm.TextInputActivity;

/**
 * Created by tom on 27/12/17.
 */

public class TextPreference extends WearPreference {
    private String mDefault = "";

    public TextPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        // Get XML args
        final int defResId = attrs.getAttributeResourceValue(NAMESPACE_ANDROID, "defaultValue", -1);
        if(defResId != -1){
            mDefault = context.getResources().getString(defResId);
        }else{
            mDefault = attrs.getAttributeValue(NAMESPACE_ANDROID, "defaultValue");
            if(mDefault == null){
                mDefault = "";
            }
        }
    }

    public void onPreferenceClick(@NonNull final Context context) {
        final Intent inputMethod = TextInputActivity.createIntent(
                context,
                getKey(),
                getIcon(context),
                getCurrentValue(context),
                mDefault
        );

        context.startActivity(inputMethod);
    }

    @Override public CharSequence getSummary(@NonNull final Context context) {
        return getCurrentValue(context);
    }

    private String getCurrentValue(@NonNull final Context context){
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        return preferences.getString(getKey(), mDefault);
    }
}

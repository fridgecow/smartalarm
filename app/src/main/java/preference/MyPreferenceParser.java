package preference;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.AttributeSet;

/**
 * Created by tom on 27/12/17.
 */

public class MyPreferenceParser extends XmlPreferenceParser {
    protected WearPreference parsePreference(@NonNull Context context, @NonNull String preferenceType, @NonNull final AttributeSet attrs) {
        if(preferenceType.equals("NumberPreference")){
            return new NumberPreference(context, attrs);
        } else if(preferenceType.equals("TextPreference")) {
            return new TextPreference(context, attrs);
        }else if(preferenceType.equals("TimePreference")) {
            return new TimePreference(context, attrs);
        }else if(preferenceType.equals("intent")){
            return new IntentPreference(context, attrs);
        }else{
            return null;
        }
    }
}

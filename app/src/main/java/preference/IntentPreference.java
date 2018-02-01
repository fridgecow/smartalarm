package preference;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.util.AttributeSet;

/**
 * Created by tom on 10/01/18.
 */

public class IntentPreference extends WearPreference {
    String mIntent;
    Boolean mService = false;

    public IntentPreference(Context context, AttributeSet attrs){
        super(context, attrs);
        int defResId = attrs.getAttributeResourceValue(NAMESPACE_ANDROID, "action", -1);
        if(defResId != -1){
            mIntent = context.getResources().getString(defResId);
        }else{
            mIntent = attrs.getAttributeValue(NAMESPACE_ANDROID, "action");
        }

        if(getKey().equals("service")){
            mService = true;
        }
    }

    @Override
    void onPreferenceClick(@NonNull Context context) {
        if(!mService) {
            context.startActivity(new Intent(mIntent));
        }else{
            context.startService(new Intent(mIntent));
        }
    }
}
